package com.max2idea.android.limbo.ui.exp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Entry point of the experimental Compose/Miuix UI.
 *
 * Step 3 of the migration only proves that Compose + Miuix compile and render
 * inside the app; the real HomeScreen is built in step 4.
 *
 * Guarded by the *existing* [ExpPrefs.experimentalUi] switch rather than a new
 * preference, so the app never ends up with two competing experimental toggles
 * and the default (legacy) path stays untouched.
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
            MiuixTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "Hello",
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
