package com.max2idea.android.limbo.ui.exp.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Static top bar for the experimental home screen.
 *
 * Matches `docs/team/npatch-miuix-style-spec.md` §2: collapsed height 52dp,
 * the title is centred, and it uses `title3` (20sp). The translucent scrolled
 * background and the 32sp large title are part of the scrolling top bar in
 * NPatch; this milestone only ships the collapsed form, so the bar is simply
 * transparent — the content behind it never scrolls under it in the current
 * layout.
 */
@Composable
fun ExpTopBar(
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(52.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = title,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 26.dp),
            maxLines = 1,
        )
    }
}
