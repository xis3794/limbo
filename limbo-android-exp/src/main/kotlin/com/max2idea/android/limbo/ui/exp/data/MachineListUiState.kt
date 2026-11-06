package com.max2idea.android.limbo.ui.exp.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.max2idea.android.limbo.ui.exp.bridge.ExpUiBridge

/**
 * State holder for the machine list.
 *
 * Not an `androidx.lifecycle.ViewModel` on purpose: the experimental module has
 * no view-model dependency, the data source is a synchronous database read, and
 * the activity is recreated rarely enough that [refresh] on composition is
 * sufficient. Keeping it a plain [Stable] class also makes the whole screen
 * previewable without an Activity.
 */
@Stable
class MachineListUiState internal constructor(
    private val useSampleData: Boolean,
) {
    var rows by mutableStateOf(
        if (useSampleData) SampleMachines.rows else emptyList(),
    )
        private set

    var runningName by mutableStateOf(
        if (useSampleData) SampleMachines.rows.firstOrNull { it.running }?.name else null,
    )
        private set

    val machineCount: Int get() = rows.size
    val runningCount: Int get() = if (runningName != null) 1 else 0
    val isRunning: Boolean get() = runningName != null

    /**
     * Re-reads the machine list through the plain-Java bridge.
     *
     * [ExpUiBridge] already swallows the "machine service not started yet"
     * failures and returns empty collections instead of throwing, so this is
     * safe to call from the main thread.
     */
    fun refresh() {
        if (useSampleData) return
        rows = ExpUiBridge.getMachines().map { snapshot ->
            MachineRow(
                name = snapshot.name,
                arch = snapshot.arch,
                cpuNum = snapshot.cpuNum,
                memoryMb = snapshot.memoryMb,
                backend = snapshot.backend,
                running = snapshot.running,
            )
        }
        runningName = ExpUiBridge.getRunningMachineName()
    }
}

/**
 * Remembers a [MachineListUiState] and, when it is backed by the real bridge,
 * reads the machine list once when it enters composition.
 */
@Composable
fun rememberMachineListUiState(useSampleData: Boolean = true): MachineListUiState {
    val state = remember(useSampleData) { MachineListUiState(useSampleData) }
    LaunchedEffect(state) { state.refresh() }
    return state
}
