package com.max2idea.android.limbo.ui.exp

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.max2idea.android.limbo.main.AppSettingsActivity
import com.max2idea.android.limbo.ui.exp.bridge.ExpUiBridge
import com.max2idea.android.limbo.ui.exp.data.MachineListUiState

/**
 * Dialog state for the home screen.
 *
 * §32/§33 of the gap list replaced the two `android.app.AlertDialog`s with
 * dialogs drawn from the page's own primitives. Compose dialogs have to be
 * declared inside the composition, so the old "build an AlertDialog in a
 * Context extension and call `show()`" shape no longer works: the actions now
 * only flip a field in this holder and [HomeScreen] renders the matching
 * dialog from it.
 */
@Stable
class HomeDialogsState {
    /** Name of the machine awaiting a delete confirmation, or null. */
    var pendingDelete: String? by mutableStateOf(null)

    /** Whether the "new machine" prompt is showing. */
    var newPromptVisible: Boolean by mutableStateOf(false)

    /** What the user has typed into the new-machine prompt. */
    var newName: String by mutableStateOf("")

    /** Inline validation message for the prompt, or null when the input is fine. */
    var newNameError: String? by mutableStateOf(null)
}

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
        val dialogs = HomeDialogsState()
        HomeActions(
            onStatusCardClick = {
                if (state.isRunning) {
                    context.openRunningDisplay()
                } else {
                    toast(context, context.getString(R.string.exp_miuix_status_idle_summary))
                }
            },
            onMachineClick = { name -> context.openMachine(state, name) },
            onMachineLongClick = { name -> context.askDelete(state, dialogs, name) },
            dialogs = dialogs,
            onNewMachine = { context.showNewPrompt(dialogs) },
            onSettings = { context.openSettings() },
            onDeleteConfirm = {
                val name = dialogs.pendingDelete
                dialogs.pendingDelete = null
                if (name != null && !context.applyDelete(state, name)) {
                    toast(context, context.getString(R.string.exp_miuix_stop_first))
                }
            },
            onDeleteDismiss = { dialogs.pendingDelete = null },
            onNewNameChange = { value ->
                dialogs.newName = value
                dialogs.newNameError = null
            },
            onNewConfirm = { context.applyNewMachine(state, dialogs) },
            onNewDismiss = {
                dialogs.newPromptVisible = false
                dialogs.newName = ""
                dialogs.newNameError = null
            },
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

/** Long press → raise the delete confirmation, with the running machine protected. */
internal fun Context.askDelete(
    state: MachineListUiState,
    dialogs: HomeDialogsState,
    name: String,
) {
    if (!state.canOpen(name)) {
        toast(this, getString(R.string.exp_miuix_stop_first))
        return
    }
    dialogs.pendingDelete = name
}

/** Runs the confirmed delete; returns false when the bridge refused. */
internal fun Context.applyDelete(state: MachineListUiState, name: String): Boolean {
    if (!ExpUiBridge.deleteMachine(name)) return false
    state.reload()
    return true
}

/** Bottom-bar "new" tab → raise the name prompt. */
internal fun Context.showNewPrompt(dialogs: HomeDialogsState) {
    dialogs.newName = ""
    dialogs.newNameError = null
    dialogs.newPromptVisible = true
}

/**
 * Validates and applies the prompted name.
 *
 * The two rejections are the ones the legacy prompt showed, in the same order;
 * they stay inside the dialog now instead of escaping to a toast, so the user
 * does not lose what they typed.
 */
internal fun Context.applyNewMachine(state: MachineListUiState, dialogs: HomeDialogsState) {
    val name = dialogs.newName.trim().replace("\"", "")
    when {
        name.isEmpty() -> {
            dialogs.newNameError = getString(R.string.exp_miuix_new_name_required)
            return
        }
        state.rows.any { row -> row.name == name } -> {
            dialogs.newNameError = getString(R.string.exp_miuix_new_name_taken)
            return
        }
    }
    if (!ExpUiBridge.createMachine(name)) {
        dialogs.newNameError = getString(R.string.exp_miuix_new_name_taken)
        return
    }
    dialogs.newPromptVisible = false
    dialogs.newName = ""
    dialogs.newNameError = null
    state.reload()
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
