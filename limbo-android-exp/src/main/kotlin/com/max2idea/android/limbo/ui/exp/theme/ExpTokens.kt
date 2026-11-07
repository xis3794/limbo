package com.max2idea.android.limbo.ui.exp.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.basic.CardColors
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Design tokens the Miuix palette does not expose.
 *
 * `docs/team/exp-ui-gaps.md` §22 lists the six dead colour tokens of the old
 * Java theme; this file is where the two that the Compose screen actually needs
 * are re-introduced, alongside the small amount of surface logic the NPatch
 * reference gets from its blur/shadow stack.
 */

/**
 * NPatch's warning accent (`warning` in the old `ExpTheme`, `#E65100`).
 *
 * Miuix ships no warning/danger token at all — `Colors` has `error` but that is
 * a hard red, while NPatch uses a burnt orange for "attention, but nothing is
 * broken" states such as "no machine is running".
 */
val ExpWarningColor = Color(0xFFE65100)

/**
 * Whether the active Miuix palette is a dark one.
 *
 * Derived from the background luminance instead of reading the controller, so
 * the helper works under any `MiuixTheme` (including a forced light theme on a
 * dark system) without the screen having to thread an `isDark` flag through
 * every component.
 */
@Composable
fun expIsDarkTheme(): Boolean = MiuixTheme.colorScheme.background.luminance() < 0.5f

/**
 * Card colours for the page background used by the home screen.
 *
 * §1 of the gap list: `background` and `surfaceContainer` are the *same* colour
 * in both palettes, so a card painted with the default Miuix colour vanishes
 * into the page. NPatch separates the two with blur, a shadow and a hairline
 * outline; we cannot blur, so on dark palettes the card is lifted one surface
 * step to `surfaceContainerHighest` and keeps the default `surfaceContainer`
 * on light palettes.
 */
@Composable
fun expCardColors(): CardColors {
    val scheme = MiuixTheme.colorScheme
    return if (expIsDarkTheme()) {
        CardDefaults.defaultColors(color = scheme.surfaceContainerHighest)
    } else {
        CardDefaults.defaultColors()
    }
}

/**
 * Hairline for the floating bottom bar (§15).
 *
 * The bar is 40 % opaque and we cannot blur what is behind it, so without an
 * outline the capsule dissolves into the list. NPatch gets the same separation
 * from its backdrop blur; the hairline is our stand-in.
 */
@Composable
fun expBottomBarHairline(): Color {
    return if (expIsDarkTheme()) {
        Color.White.copy(alpha = 0.20f)
    } else {
        Color.Black.copy(alpha = 0.10f)
    }
}
