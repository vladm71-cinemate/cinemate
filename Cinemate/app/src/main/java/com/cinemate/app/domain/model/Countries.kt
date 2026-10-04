package com.cinemate.app.domain.model

/**
 * Страна для фильтра каталога.
 * code      — ISO 3166-1 для TMDb (with_origin_country).
 * languages — основные языки страны (для клиентского фильтра фильмов).
 * Локализованное название берётся из ресурсов (arrays.xml country_names)
 * — порядок стран здесь и в массивах должен совпадать!
 */
data class CountryRegion(
    val code: String,
    val languages: Set<String>
)

object Countries {

    /** Порядок строго соответствует массиву country_names в arrays.xml! */
    val ALL: List<CountryRegion> = listOf(
        CountryRegion("US", setOf("en")),
        CountryRegion("GB", setOf("en")),
        CountryRegion("RU", setOf("ru")),
        CountryRegion("FR", setOf("fr")),
        CountryRegion("DE", setOf("de")),
        CountryRegion("IT", setOf("it")),
        CountryRegion("ES", setOf("es")),
        CountryRegion("IN", setOf("hi", "ta", "te", "ml", "kn", "bn")),
        CountryRegion("JP", setOf("ja")),
        CountryRegion("KR", setOf("ko")),
        CountryRegion("CN", setOf("zh", "cn")),
        CountryRegion("HK", setOf("zh", "cn", "yue")),
        CountryRegion("CA", setOf("en", "fr")),
        CountryRegion("AU", setOf("en")),
        CountryRegion("BR", setOf("pt")),
        CountryRegion("MX", setOf("es")),
        CountryRegion("AR", setOf("es")),
        CountryRegion("SE", setOf("sv")),
        CountryRegion("NO", setOf("no", "nb")),
        CountryRegion("DK", setOf("da")),
        CountryRegion("FI", setOf("fi")),
        CountryRegion("PL", setOf("pl")),
        CountryRegion("TR", setOf("tr")),
        CountryRegion("TH", setOf("th")),
        CountryRegion("IR", setOf("fa")),
        CountryRegion("EG", setOf("ar")),
        CountryRegion("AE", setOf("ar")),
        CountryRegion("SA", setOf("ar")),
        CountryRegion("UA", setOf("uk")),
        CountryRegion("BY", setOf("be", "ru"))
    )

    /** Быстрый поиск страны по ISO-коду. */
    fun byCode(code: String): CountryRegion? = ALL.find { it.code == code }

    /**
     * Все языки выбранных стран одним набором —
     * для клиентского фильтра фильмов по original_language.
     */
    fun languagesOf(codes: Set<String>): Set<String> =
        codes.mapNotNull { byCode(it) }.flatMap { it.languages }.toSet()
}