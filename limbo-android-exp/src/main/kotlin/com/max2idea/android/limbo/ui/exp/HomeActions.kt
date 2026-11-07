package com.max2idea.android.limbo.ui.exp

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.max2idea.android.limbo.main.AppSettingsActivity
import com.max2idea.android.limbo.ui.exp.bridge.ExpUiBridge
import com.max2idea.android.limbo.ui.exp.data.MachineListUiState

/**
 * Wires the pure [HomeScreen] to the legacy Java side.
 *
 * Everything that needs an Activity, a dialog or a legacy Intent lives here so
 * [HomeScreen] itself stays a function of its inputs. The split mirrors what the
 * old Java experimental screen (`LimboExperimentalActivity`) did inline, only
 * without holding any framework objects inside the composables.
 *
 * Every action goes through [ExpUiBridge] instead of talking to the machine
 * service directly, so the Compose module keeps its single, plain-Java contract
 * with `:limbo-android-lib`.
 */
@Composable
fun rememberHomeActions(state: MachineListUiState): HomeActions {
    val context = LocalContext.current
    return remember(state, context) {
        HomeActions(
            onStatusCardClick = {
                if (state.isRunning) {
                    context.openRunningDisplay()
                } else {
                    toast(context, context.getString(R.string.exp_miuix_status_idle_summary))
                }
            },
            onMachineClick = { name -> context.openMachine(state, name) },
            onMachineLongClick = { name -> context.confirmDelete(state, name) },
        )
    }
}

/** Opens the running machine's display the same way the legacy home screen does. */
internal fun Context.openRunningDisplay() {
    val intent = ExpUiBridge.createRunningDisplayIntent(this)
    if (intent == null) {
        toast(this, getString(R.string.exp_miuix_no_target))
        return
    }
    startActivity(intent)
}

/**
 * Opens one machine in the legacy editor / display, refusing while a *different*
 * machine owns the display.
 *
 * The policy is the same one the legacy screen enforces ("stop the machine
 * first"); keeping it in one place stops the two screens from drifting apart.
 */
internal fun Context.openMachine(state: MachineListUiState, name: String) {
    if (!state.canOpen(name)) {
        toast(this, getString(R.string.exp_miuix_stop_first))
        return
    }
    val intent = ExpUiBridge.createMachineIntent(this, name, false)
    if (intent == null) {
        toast(this, getString(R.string.exp_miuix_no_target))
        return
    }
    startActivity(intent)
}

/** Long press → delete confirmation, with the running machine protected. */
internal fun Context.confirmDelete(state: MachineListUiState, name: String) {
    if (!state.canOpen(name)) {
        toast(this, getString(R.string.exp_miuix_stop_first))
        return
    }
    AlertDialog.Builder(this)
        .setTitle(R.string.exp_miuix_delete_title)
        .setMessage(getString(R.string.exp_miuix_delete_message, name))
        .setPositiveButton(R.string.exp_miuix_delete_confirm) { _, _ ->
            if (ExpUiBridge.deleteMachine(name)) {
                state.reload()
            } else {
                toast(this, getString(R.string.exp_miuix_stop_first))
            }
        }
        .setNegativeButton(R.string.exp_miuix_delete_cancel, null)
        .show()
}

/** Bottom-bar "new" tab → name prompt, then create through the dispatcher. */
internal fun Context.promptNewMachine(state: MachineListUiState) {
    val input = EditText(this).apply {
        hint = getString(R.string.exp_miuix_new_hint)
        setSingleLine(true)
    }
    AlertDialog.Builder(this)
        .setTitle(R.string.exp_miuix_new_title)
        .setView(input)
        .setPositiveButton(R.string.exp_miuix_new_create) { _, _ ->
            val name = input.text.toString().trim().replace("\"", "")
            when {
                // The two rejections the legacy prompt shows, in the same order.
                name.isEmpty() ->
                    toast(this, getString(R.string.exp_miuix_new_name_required))
                state.rows.any { row -> row.name == name } ->
                    toast(this, getString(R.string.exp_miuix_new_name_taken))
                ExpUiBridge.createMachine(name) ->
                    state.reload()
                else ->
                    toast(this, getString(R.string.exp_miuix_new_name_taken))
            }
        }
        .setNegativeButton(R.string.exp_miuix_delete_cancel, null)
        .show()
}

/** Bottom-bar "settings" tab. */
internal fun Context.openSettings() {
    val intent = ExpUiBridge.createSettingsIntent(this)
    if (intent == null) {
        // Should not happen (the activity ships in the library manifest), but
        // falling back keeps the tab from silently doing nothing.
        startActivity(Intent(this, AppSettingsActivity::class.java))
        return
    }
    startActivity(intent)
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}
