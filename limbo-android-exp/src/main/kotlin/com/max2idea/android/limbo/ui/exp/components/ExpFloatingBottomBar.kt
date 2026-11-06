package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * One tab of the floating bottom bar.
 *
 * Icons are drawn by a caller-supplied composable rather than an [ImageVector]
 * so this milestone does not need the `miuix-icons` or `material-icons`
 * artifacts — [ExpFloatingBottomBar] only knows about the label and the glyph.
 */
data class ExpBottomBarTab(
    val label: String,
    val glyph: @Composable (color: Color) -> Unit,
)

/**
 * The floating, capsule-shaped bottom bar.
 *
 * Values come straight from `docs/team/npatch-miuix-style-spec.md` §6 and from
 * the measured constants in NPatch's `FloatingGlassBottomBar.kt`:
 *
 * * container: 64dp tall, `CircleShape`, 4dp inner padding, full width minus the
 *   12dp page margin on either side, bottom-centred;
 * * tab: 56dp tall, 76dp minimum width, `weight(1f)`;
 * * selection indicator: a `primary` capsule at 15% alpha behind the tab;
 * * label: 11sp / 14sp line height, `primary` when selected,
 *   `onSurfaceVariantSummary` otherwise, semibold when selected;
 * * press feedback: the whole tab scales to 0.94 while held.
 */
@Composable
fun ExpFloatingBottomBar(
    tabs: List<ExpBottomBarTab>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = MiuixTheme.colorScheme.surfaceContainer
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(CircleShape)
            .background(containerColor, CircleShape)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            ExpBottomBarTabItem(
                tab = tab,
                selected = index == selectedIndex,
                onClick = { onSelected(index) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun ExpBottomBarTabItem(
    tab: ExpBottomBarTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primary = MiuixTheme.colorScheme.primary
    val contentColor = if (selected) primary else MiuixTheme.colorScheme.onSurfaceVariantSummary

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 150),
        label = "exp-bottom-bar-tab-scale",
    )

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 76.dp)
            .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        // Selection pill sits behind the content, at 15% of the primary colour.
        Box(
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .fillMaxHeight()
                .fillMaxWidth()
                .clip(CircleShape)
                .background(
                    color = if (selected) primary.copy(alpha = 0.15f) else Color.Transparent,
                    shape = CircleShape,
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .clip(CircleShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ),
            verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            tab.glyph(contentColor)
            Text(
                text = tab.label,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = contentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
