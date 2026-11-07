package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.max2idea.android.limbo.ui.exp.theme.expCardColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One label/value pair rendered by [ExpInfoCard]. */
data class ExpInfoItem(
    val title: String,
    val value: String,
)

/**
 * The build-information card at the bottom of the home screen.
 *
 * Spec §30: NPatch's home screen is a three-beat page — status card, machine
 * list, info card — and the missing third beat is a large part of why our list
 * reads as "a plain settings screen" rather than as NPatch. NPatch's `InfoCard`
 * stacks `InfoText` blocks, each a 17sp medium `onSurface` title over a 14sp
 * `onSurfaceVariantSummary` value, 2dp inside a pair, 10dp between pairs and
 * 16dp of card padding.
 *
 * The three rows map NPatch's "API Version / NPatch Version / Framework
 * Version" onto Limbo's own build facts: app version, QEMU engine version and
 * the ABI the native libraries were packaged for.
 */
@Composable
fun ExpInfoCard(
    items: List<ExpInfoItem>,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = expCardColors(),
        insideMargin = PaddingValues(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.forEach { item ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = item.title,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                    )
                    Text(
                        text = item.value,
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
