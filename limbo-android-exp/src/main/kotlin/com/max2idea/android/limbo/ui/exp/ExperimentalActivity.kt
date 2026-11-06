package com.max2idea.android.limbo.ui.exp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.max2idea.android.limbo.ui.exp.data.rememberMachineListUiState
import com.max2idea.android.limbo.ui.exp.theme.LimboMiuixTheme
import top.yukonga.miuix.kmp.basic.Surface

/**
 * Entry point of the experimental Compose/Miuix UI.
 *
 * Guarded by the *existing* [ExpPrefs.experimentalUi] switch rather than a new
 * preference, so the app never ends up with two competing experimental toggles
 * and the default (legacy) path stays untouched.
 *
 * Until the settings entry point is flipped over (a later step) this activity is
 * only reachable explicitly, and [rememberMachineListUiState] renders the
 * placeholder rows so the static screen can be reviewed before the real machine
 * database is wired in.
 */
class ExperimentalActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nothing should reach this activity with the switch off; bail out
        // instead of showing a Compose surface the user never opted into.
        if (!ExpPrefs.experimentalUi(this)) {
            finish()
            return
        }

        setContent {
            LimboMiuixTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(state = rememberMachineListUiState())
                }
            }
        }
    }
}
