package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * The machine-status summary card at the top of the home screen.
 *
 * Spec §4: 44dp circular icon badge holding a 26dp glyph, a 17sp bold title and
 * a 13sp summary line. When a machine runs, the badge and title use the primary
 * colour and the summary shows the machine name; otherwise the badge falls back
 * to the muted surface colour and the copy explains how to start one.
 *
 * The card is clickable so that later the same component can open the running
 * display; for this static milestone the callback is optional.
 */
@Composable
fun ExpStatusCard(
    isRunning: Boolean,
    runningMachineName: String?,
    titleRunning: String,
    titleIdle: String,
    summaryIdle: String,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val accent = if (isRunning) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    val badgeColor = if (isRunning) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.12f)
    }

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(color = badgeColor, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (isRunning) {
                    CheckGlyph(color = accent, size = 26.dp)
                } else {
                    MonitorGlyph(color = accent, size = 24.dp)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = if (isRunning) titleRunning else titleIdle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = if (isRunning) runningMachineName.orEmpty() else summaryIdle,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }

    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(),
            onClick = onClick,
            insideMargin = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 20.dp,
            ),
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Sink,
            content = { content() },
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(),
            insideMargin = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 20.dp,
                vertical = 20.dp,
            ),
            content = { content() },
        )
    }
}
