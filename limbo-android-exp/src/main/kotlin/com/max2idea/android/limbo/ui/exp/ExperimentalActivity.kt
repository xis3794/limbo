package com.max2idea.android.limbo.ui.exp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
 * The screen renders the real machine database through
 * [com.max2idea.android.limbo.ui.exp.bridge.ExpUiBridge]; every side effect
 * (open a machine, create one, delete one, open the running display, open the
 * settings) lives in `HomeActions.kt` and goes through that same bridge.
 */
class ExperimentalActivity : ComponentActivity() {

    /**
     * Bumped on every [onResume] so the Compose tree re-reads the machine list.
     *
     * A guest can be started or stopped while this screen is in the background
     * (the VM display is a separate activity), so the list has to be refreshed
     * when the user comes back rather than only once on creation.
     */
    private var resumeTick by mutableIntStateOf(0)

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
                val state = rememberMachineListUiState()
                val context = LocalContext.current
                val actions = rememberHomeActions(state)
                val tick = resumeTick
                LaunchedEffect(tick) {
                    if (tick > 0) {
                        state.refresh()
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        state = state,
                        actions = actions,
                        onNewMachine = { context.promptNewMachine(state) },
                        onSettings = { context.openSettings() },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}
