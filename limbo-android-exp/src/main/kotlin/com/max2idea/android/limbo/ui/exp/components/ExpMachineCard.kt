package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType

/**
 * The six soft accent colours the machine avatar cycles through.
 *
 * Indexed by a stable hash of the machine name so a given machine always gets
 * the same colour, matching the spec's "柔和色 (soft colour)" requirement rather
 * than a flat grey placeholder.
 */
private val MachineAvatarColors = listOf(
    Color(0xFF5B8DEF), // blue
    Color(0xFF4CAF93), // teal
    Color(0xFFB076E0), // purple
    Color(0xFFE08A4B), // orange
    Color(0xFFD96A8A), // pink
    Color(0xFF6FA84E), // green
)

private fun avatarColorFor(name: String): Color {
    if (name.isEmpty()) return MachineAvatarColors[0]
    var hash = 0
    for (ch in name) {
        hash = hash * 31 + ch.code
    }
    val index = ((hash % MachineAvatarColors.size) + MachineAvatarColors.size) % MachineAvatarColors.size
    return MachineAvatarColors[index]
}

/**
 * One machine row.
 *
 * Spec §5: 16dp corner radius (inherited from [CardDefaults]), a 48dp rounded
 * square avatar with a 12dp corner holding the first character in 22sp bold
 * white, then the 16sp medium name over the 13sp meta line, and finally either
 * the "running" pill or a chevron.
 *
 * Tap opens the machine, long-press is reserved for the delete confirmation in
 * a later step — both are exposed as nullable callbacks so this milestone can
 * render the row without wiring any action yet.
 */
@Composable
fun ExpMachineCard(
    name: String,
    meta: String,
    running: Boolean,
    runningLabel: String,
    onClick: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val avatarColor = avatarColorFor(name)
    val initial = name.trim().take(1).uppercase().ifEmpty { "?" }
    val inside = PaddingValues(12.dp)

    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(color = avatarColor, shape = RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initial,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = meta,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }

            if (running) {
                Box(
                    modifier = Modifier
                        .background(
                            color = MiuixTheme.colorScheme.tertiaryContainer,
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = runningLabel,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onTertiaryContainer,
                        maxLines = 1,
                    )
                }
            } else {
                ChevronGlyph(
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    size = 16.dp,
                )
            }
        }
    }

    when {
        onClick != null && onLongPress != null -> Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(),
            onClick = onClick,
            onLongPress = onLongPress,
            insideMargin = inside,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Sink,
            content = { content() },
        )

        onClick != null -> Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(),
            onClick = onClick,
            insideMargin = inside,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Sink,
            content = { content() },
        )

        else -> Card(
            modifier = modifier.fillMaxWidth(),
            colors = CardDefaults.defaultColors(),
            insideMargin = inside,
            content = { content() },
        )
    }
}
