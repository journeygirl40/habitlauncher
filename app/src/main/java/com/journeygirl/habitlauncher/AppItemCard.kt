package com.journeygirl.habitlauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.time.Duration
import java.time.ZonedDateTime

@Composable
fun AppItemCard(
    app: TrackedApp,
    now: ZonedDateTime,
    onIconLaunchClick: (TrackedApp) -> Unit,
    onCardSettingsClick: (TrackedApp) -> Unit,
    notifyLeadMinutes: Long   // ★ 追加

) {
    val remaining = remember(app.resetTime, now) { remainingUntilReset(app.resetTime, now) }
    val isDone = app.launchedFlagToday
    val isUrgent = !isDone && remaining <= Duration.ofMinutes(notifyLeadMinutes) // ★ 置換

    val timerColor = when {
        isDone -> MaterialTheme.colorScheme.primary
        isUrgent -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isDone) it.alpha(0.5f) else it }
            .clickable { onCardSettingsClick(app) },   // ★ カード＝設定を開く
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(app.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "↻ Reset ${app.resetTime}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val rightText = if (isDone) "Done" else formatDurationShort(remaining)
            Text(text = rightText, style = MaterialTheme.typography.bodyMedium, color = timerColor)

            Spacer(Modifier.width(12.dp))

            if (app.icon != null) {
                Image(
                    bitmap = app.icon.toBitmap(64, 64).asImageBitmap(),
                    contentDescription = "${app.name} を起動",
                    modifier = Modifier
                        .size(40.dp)
                        .clickable { onIconLaunchClick(app) } // ← enabledを外して常に押せるように
                )
            }
        }
    }
}
