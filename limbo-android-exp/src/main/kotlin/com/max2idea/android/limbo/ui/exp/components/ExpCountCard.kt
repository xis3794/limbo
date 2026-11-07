package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.max2idea.android.limbo.ui.exp.theme.expCardColors

/**
 * One half of the two-up counter row.
 *
 * Spec §5: 15sp medium caption in `onSurfaceVariantSummary`, 26sp semibold
 * number in `onSurface`, and a 18dp chevron in the primary-agnostic muted
 * colour. Both cards sit in a `Row` with `weight(1f)` and a 12dp gap.
 */
@Composable
fun ExpCountCard(
    title: String,
    count: Int,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                )
                Text(
                    text = count.toString(),
                    fontSize = 26.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                )
            }
            ChevronGlyph(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                size = 18.dp,
            )
        }
    }

    val inside = PaddingValues(16.dp)
    if (onClick != null) {
        Card(
            modifier = modifier,
            colors = expCardColors(),
            onClick = onClick,
            insideMargin = inside,
            showIndication = true,
            pressFeedbackType = PressFeedbackType.Sink,
            content = { content() },
        )
    } else {
        Card(
            modifier = modifier,
            colors = expCardColors(),
            insideMargin = inside,
            content = { content() },
        )
    }
}
