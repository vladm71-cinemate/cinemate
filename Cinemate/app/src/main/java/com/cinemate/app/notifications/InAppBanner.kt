package com.cinemate.app.notifications

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import com.cinemate.app.R
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * VM баннера. Хранит ссылку на NotificationCenter и пробрасывает
 * и чтение сообщений, и удаление (крестик) в Compose.
 */
@HiltViewModel
class BannerViewModel @Inject constructor(
    private val notificationCenter: NotificationCenter
) : ViewModel() {
    val messages: StateFlow<List<String>> = notificationCenter.messages

    /** Тап по крестику — убрать сообщение из баннера. */
    fun dismiss(message: String) {
        notificationCenter.dismiss(message)
    }
}

/**
 * Баннер внутри приложения: показывает релизы/новые серии всем,
 * включая тех, кто отказал в системных уведомлениях (по ТЗ).
 */
@Composable
fun InAppBanner() {
    val viewModel: BannerViewModel = hiltViewModel()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    if (messages.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        messages.forEach { message ->
            BannerItem(
                message = message,
                onDismiss = { viewModel.dismiss(message) }
            )
        }
    }
}

@Composable
private fun BannerItem(
    message: String,
    onDismiss: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primary,
        shadowElevation = 4.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.NotificationsActive,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.cd_close),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}