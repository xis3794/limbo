package com.max2idea.android.limbo.ui.exp.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/**
 * Limbo's brand colour, taken from the design tokens in
 * `docs/team/npatch-miuix-style-spec.md` §1 (`primary` light = #3482FF,
 * dark = #277AF7).
 */
private val LimboLightPrimary = Color(0xFF3482FF)
private val LimboDarkPrimary = Color(0xFF277AF7)

/**
 * Wraps [MiuixTheme] with a [ThemeController] seeded from Limbo's primary
 * colour, so the experimental UI keeps the app's blue accent instead of the
 * default HyperOS palette.
 *
 * Dark mode follows the system setting, matching the spec's requirement that
 * both token sets be supplied.
 */
@Composable
fun LimboMiuixTheme(
    isDarkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val controller = remember(isDarkTheme) {
        if (isDarkTheme) {
            ThemeController(
                ColorSchemeMode.Dark,
                keyColor = LimboDarkPrimary,
            )
        } else {
            ThemeController(
                ColorSchemeMode.Light,
                keyColor = LimboLightPrimary,
            )
        }
    }
    MiuixTheme(controller = controller, content = content)
}
