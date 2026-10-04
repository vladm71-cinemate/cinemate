package com.cinemate.app.data.remote

import com.cinemate.app.data.local.TokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.io.DataInputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

/** Таймаут UDP-запроса plain-DNS, мс (top-level: виден и UdpDns). */
private const val UDP_TIMEOUT_MS = 3000

/**
 * Менеджер DNS: обход DNS-блокировок провайдера — целиком из приложения.
 *
 * Режимы: "auto" | "custom" | "system" (см. TokenStore).
 *
 * Быстрый старт: первый же успешный резолв в фейловере ЗАПОМИНАЕТСЯ
 * (dnsActive) — следующий запуск начинается с проверенного сервера,
 * без перебора и таймаутов. Если он умер — перебор продолжится,
 * новый рабочий снова запомнится.
 */
@Singleton
class DnsManager @Inject constructor(
    private val tokenStore: TokenStore
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        val BUILTIN_DOH = listOf(
            "https://dns.google/resolve",
            "https://cloudflare-dns.com/dns-query"
        )

        data class BuiltinPlain(val primary: String, val secondary: String, val service: String)

        val BUILTIN_PLAIN = listOf(
            BuiltinPlain("76.76.2.11", "76.76.10.11", "ControlD"),
            BuiltinPlain("8.8.8.8", "8.8.4.4", "Google"),
            BuiltinPlain("9.9.9.9", "149.112.112.112", "Quad9 (блокирует вредоносные)"),
            BuiltinPlain("94.140.14.14", "94.140.15.15", "AdGuard DNS"),
            BuiltinPlain("185.222.222.222", "45.11.45.11", "DNS.SB"),
            BuiltinPlain("194.242.2.2", "193.138.218.74", "Mullvad")
        )

        val BUILTIN_PLAIN_IPS = BUILTIN_PLAIN.flatMap { listOf(it.primary, it.secondary) }

        /** Известные IP DoH-серверов: bootstrap мимо системного DNS. */
        val DOH_BOOTSTRAP: List<InetAddress> = listOf("8.8.8.8", "8.8.4.4", "1.1.1.1", "1.0.0.1")
            .map { InetAddress.getByName(it) }

        private const val TEST_DOMAIN = "api.themoviedb.org"

        /** Идентификатор системного DNS в отчёте теста (не кандидат на подмену). */
        const val SYSTEM_DNS_ID = "system"

        private val STUB_PREFIXES = listOf(
            "10.",
            "172.16.", "172.17.", "172.18.", "172.19.",
            "172.20.", "172.21.", "172.22.", "172.23.",
            "172.24.", "172.25.", "172.26.", "172.27.",
            "172.28.", "172.29.", "172.30.", "172.31.",
            "192.168.", "127."
        )
    }

    fun isDoh(address: String): Boolean =
        address.trim().startsWith("http://") || address.trim().startsWith("https://")

    fun labelFor(address: String): String {
        BUILTIN_PLAIN.forEach { b ->
            if (address == b.primary || address == b.secondary) return "${b.service} (${address})"
        }
        BUILTIN_DOH.forEach { d ->
            if (address == d) return "$d (DoH)"
        }
        return address
    }

    fun buildDnsFor(address: String, bootstrapClient: OkHttpClient): Dns? {
        return try {
            if (isDoh(address)) {
                val dohUrl = if (address.endsWith("/resolve")) address
                else address.trimEnd('/') + "/resolve"
                DnsOverHttps.Builder()
                    .client(bootstrapClient)
                    .url(dohUrl.toHttpUrl())
                    .bootstrapDnsHosts(*DOH_BOOTSTRAP.toTypedArray())
                    .build()
            } else {
                UdpDns(address.trim())
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Список DNS по режиму: auto = все, custom = только юзерские. */
    suspend fun allDns(): List<String> {
        val custom = parseDnsList(tokenStore.dnsList.first())
        val mode = tokenStore.dnsMode.first()
        return if (mode == "custom") {
            custom.distinct()
        } else {
            (custom + BUILTIN_DOH + BUILTIN_PLAIN_IPS).distinct()
        }
    }

    private fun parseDnsList(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = org.json.JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                arr.optString(i).trim().takeIf(String::isNotBlank)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun filterStubs(ips: List<InetAddress>): List<InetAddress> {
        val real = mutableListOf<InetAddress>()
        for (ip in ips) {
            val host = ip.hostAddress ?: continue
            var isStub = false
            for (prefix in STUB_PREFIXES) {
                if (host.startsWith(prefix)) { isStub = true; break }
            }
            if (!isStub) real.add(ip)
        }
        return real
    }

    /** Тест одного DNS: реальный запрос на указанный сервер. null = не ответил/заглушка. */
    suspend fun testDns(address: String): List<InetAddress>? = withContext(Dispatchers.IO) {
        try {
            if (isDoh(address)) {
                val dohUrl = if (address.endsWith("/resolve")) address
                else address.trimEnd('/') + "/resolve"
                val doh = DnsOverHttps.Builder()
                    .client(OkHttpClient.Builder().build())
                    .url(dohUrl.toHttpUrl())
                    .bootstrapDnsHosts(*DOH_BOOTSTRAP.toTypedArray())
                    .build()
                val real = filterStubs(doh.lookup(TEST_DOMAIN))
                if (real.isNotEmpty()) real else null
            } else {
                val real = filterStubs(UdpDns(address.trim()).lookup(TEST_DOMAIN))
                if (real.isNotEmpty()) real else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Тест всех DNS (по текущему режиму): отчёт с таймингами. */
    suspend fun testAllDns(): List<DnsTestResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<DnsTestResult>()

        val startSys = System.currentTimeMillis()
        val sysIps = try { filterStubs(InetAddress.getAllByName(TEST_DOMAIN).toList()) }
        catch (e: Exception) { emptyList<InetAddress>() }
        val sysMs = System.currentTimeMillis() - startSys
        results.add(
            if (sysIps.isNotEmpty())
                DnsTestResult(SYSTEM_DNS_ID, true, "системный",
                    "✅ ${sysIps.firstOrNull()?.hostAddress} (${sysMs} мс)")
            else
                DnsTestResult(SYSTEM_DNS_ID, false, "системный",
                    "❌ заглушка или нет ответа (${sysMs} мс)")
        )

        for (dns in allDns()) {
            val start = System.currentTimeMillis()
            val type = if (isDoh(dns)) "DoH" else "IP"
            val ips = testDns(dns)
            val ms = System.currentTimeMillis() - start
            if (ips != null) {
                results.add(DnsTestResult(dns, true, type, "✅ ${ips.firstOrNull()?.hostAddress} (${ms} мс)"))
            } else {
                results.add(DnsTestResult(dns, false, type, "❌ заглушка или нет ответа (${ms} мс)"))
            }
        }
        results
    }

    /** Первый живой DNS (в рамках режима). */
    suspend fun findWorkingDns(): String? = withContext(Dispatchers.IO) {
        for (dns in allDns()) {
            if (testDns(dns) != null) return@withContext dns
        }
        null
    }

    /**
     * Dns с фейловером по списку режима.
     * Первый успешный резолв сохраняется как активный (dnsActive) —
     * при следующем запуске перебор начнётся с него (быстрый старт).
     * null = системный резолвер (режим system / пустой список).
     */
    suspend fun buildDnsWithFallback(
        bootstrapClient: OkHttpClient? = null,
        systemDns: Dns? = null
    ): Dns? {
        val mode = tokenStore.dnsMode.first()
        if (mode == "system") return null

        val active = tokenStore.dnsActive.first()
        val all = allDns()
        if (all.isEmpty()) return null

        val ordered = mutableListOf<String>()
        if (active != null) all.firstOrNull { it == active }?.let { ordered.add(it) }
        for (d in all) if (ordered.none { it == d }) ordered.add(d)

        val failed = HashSet<String>()
        var remembered = false

        return object : Dns {
            override fun lookup(hostname: String): List<InetAddress> {
                for (dns in ordered) {
                    if (dns in failed) continue
                    try {
                        val dnsObj = buildDnsFor(dns, bootstrapClient ?: OkHttpClient.Builder().build())
                        if (dnsObj == null) {
                            synchronized(failed) { failed.add(dns) }
                            continue
                        }
                        val result = dnsObj.lookup(hostname)
                        if (result.isNotEmpty()) {
                            if (!remembered) {
                                remembered = true
                                val found = dns
                                scope.launch {
                                    runCatching { tokenStore.saveDnsActive(found) }
                                }
                            }
                            return result
                        }
                        synchronized(failed) { failed.add(dns) }
                    } catch (e: UnknownHostException) {
                        synchronized(failed) { failed.add(dns) }
                    } catch (e: Exception) {
                        synchronized(failed) { failed.add(dns) }
                    }
                }
                throw UnknownHostException("all custom DNS failed for $hostname")
            }
        }
    }
}

/** Результат теста одного DNS. */
data class DnsTestResult(
    val address: String,
    val ok: Boolean,
    val type: String,
    val detail: String
)

/**
 * Настоящий plain-DNS: UDP-запрос прямо на указанный сервер (порт 53).
 */
class UdpDns(private val server: String) : Dns {

    override fun lookup(hostname: String): List<InetAddress> {
        val serverAddr = InetAddress.getByName(server)
        val id = (Math.random() * 65536).toInt()

        val qname = ArrayList<Byte>()
        for (label in hostname.split('.')) {
            require(label.isNotEmpty()) { "пустая метка в $hostname" }
            require(label.length <= 63) { "слишком длинная метка в $hostname" }
            qname.add(label.length.toByte())
            label.toByteArray(Charsets.US_ASCII).forEach { qname.add(it) }
        }
        qname.add(0)

        val query = ArrayList<Byte>(12 + qname.size + 4)
        fun putShort(v: Int) {
            query.add(((v ushr 8) and 0xFF).toByte())
            query.add((v and 0xFF).toByte())
        }
        putShort(id); putShort(0x0100); putShort(1); putShort(0); putShort(0); putShort(0)
        query.addAll(qname)
        putShort(1); putShort(1)

        DatagramSocket().use { socket ->
            socket.soTimeout = UDP_TIMEOUT_MS
            val send = query.map { it.toByte() }.toByteArray()
            socket.send(DatagramPacket(send, send.size, InetSocketAddress(serverAddr, 53)))

            val buf = ByteArray(1024)
            val recv = DatagramPacket(buf, buf.size)
            socket.receive(recv)

            val d = DataInputStream(recv.data.inputStream())
            d.readShort()
            val flags = d.readShort().toInt() and 0xFFFF
            if (flags and 0x000F != 0) throw UnknownHostException("$hostname: rcode ${flags and 0x000F}")
            d.readShort()
            val anCount = d.readShort().toInt()
            d.readShort(); d.readShort()

            while (true) {
                val len = d.readUnsignedByte()
                if (len == 0) break
                d.skipBytes(len)
            }
            d.skipBytes(4)

            val ips = mutableListOf<InetAddress>()
            repeat(anCount) {
                var len = d.readUnsignedByte()
                if (len and 0xC0 == 0xC0) d.readUnsignedByte()
                else { while (len != 0) { d.skipBytes(len); len = d.readUnsignedByte() } }
                val type = d.readShort().toInt()
                d.skipBytes(2); d.skipBytes(4)
                val rdLength = d.readShort().toInt()
                if (type == 1 && rdLength == 4) {
                    val b = ByteArray(4)
                    d.readFully(b)
                    ips.add(InetAddress.getByAddress(b))
                } else d.skipBytes(rdLength)
            }
            if (ips.isEmpty()) throw UnknownHostException("$hostname: нет A-записей")
            return ips
        }
        throw UnknownHostException(hostname)
    }
}
