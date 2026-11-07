package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import com.max2idea.android.limbo.ui.exp.theme.ExpWarningColor
import com.max2idea.android.limbo.ui.exp.theme.expCardColors

/**
 * The machine-status summary card at the top of the home screen.
 *
 * Layout follows the NPatch status card (`HomeScreen.kt:182-223` in the
 * reference tree): a 44dp circular badge at 16% of the accent colour holding a
 * glyph, then a 17sp bold title over a 13sp summary line.
 *
 * Two details were wrong in the first pass and are fixed here:
 *
 *  * the **title is the state itself** — the running machine's name, or the
 *    "nothing is running" line. NPatch puts the status text in the title slot
 *    and the detail in the summary, not the other way round.
 *  * the title is **`onSurface`**, not `primary`. In NPatch only the warning
 *    state tints the title; the normal state is plain text, and colouring it
 *    blue made the card compete with the bottom bar for the screen's single
 *    accent.
 *
 * §7 of the gap list: the idle state is the card's "attention" state, so it
 * gets the warning triangle in [ExpWarningColor] rather than a grey monitor.
 * The title itself stays `onSurface` even then — tinting a whole sentence of
 * body-level copy orange reads as an error banner, which this is not.
 *
 * The card is clickable so it can jump back to the running display; the
 * callback stays optional so the component can be previewed on its own.
 */
@Composable
fun ExpStatusCard(
    isRunning: Boolean,
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = if (isRunning) {
        MiuixTheme.colorScheme.primary
    } else {
        ExpWarningColor
    }
    val badgeColor = accent.copy(alpha = if (isRunning) 0.16f else 0.14f)

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
                    WarningGlyph(color = accent, size = 24.dp)
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
                Text(
                    text = summary,
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
            }
        }
    }

    val inside = PaddingValues(horizontal = 20.dp, vertical = 20.dp)
    if (onClick != null) {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = expCardColors(),
            onClick = onClick,
            insideMargin = inside,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Sink,
            content = { content() },
        )
    } else {
        Card(
            modifier = modifier.fillMaxWidth(),
            colors = expCardColors(),
            insideMargin = inside,
            content = { content() },
        )
    }
}
