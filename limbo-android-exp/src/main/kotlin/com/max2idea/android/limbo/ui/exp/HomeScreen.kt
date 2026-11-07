package com.max2idea.android.limbo.ui.exp

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.max2idea.android.limbo.ui.exp.components.ExpBottomBarTab
import com.max2idea.android.limbo.ui.exp.components.ExpConfirmDialog
import com.max2idea.android.limbo.ui.exp.components.ExpCountCard
import com.max2idea.android.limbo.ui.exp.components.ExpFloatingBottomBar
import com.max2idea.android.limbo.ui.exp.components.ExpInfoCard
import com.max2idea.android.limbo.ui.exp.components.ExpInfoItem
import com.max2idea.android.limbo.ui.exp.components.ExpMachineCard
import com.max2idea.android.limbo.ui.exp.components.ExpPromptDialog
import com.max2idea.android.limbo.ui.exp.components.ExpStatusCard
import com.max2idea.android.limbo.ui.exp.components.ExpTopBar
import com.max2idea.android.limbo.ui.exp.components.MonitorGlyph
import com.max2idea.android.limbo.ui.exp.components.PlusGlyph
import com.max2idea.android.limbo.ui.exp.components.SlidersGlyph
import com.max2idea.android.limbo.ui.exp.data.MachineListUiState
import com.max2idea.android.limbo.ui.exp.data.MachineRow
import com.max2idea.android.limbo.ui.exp.bridge.ExpUiBridge
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Height of the floating bottom bar, mirrored from [ExpFloatingBottomBar]. */
private val BottomBarHeight = 64.dp

/**
 * Home screen for the experimental Miuix UI.
 *
 * Compares against `docs/team/npatch-miuix-style-spec.md`:
 * §2 top bar, §3 page padding, §4 status card, §5 the two-up counters and the
 * machine rows, §6 the floating bottom bar.
 *
 * All user actions are delegated to [actions] so the screen itself stays a pure
 * function of its inputs and can be rendered in a preview.
 */
@Composable
fun HomeScreen(
    state: MachineListUiState,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    // §29: tapping the "running" counter used to do nothing. It now filters the
    // list down to the running machine; tapping the total counter clears it.
    var runningOnly by remember { mutableStateOf(false) }
    val visibleRows = if (runningOnly) state.rows.filter { it.running } else state.rows

    val labels = listOf(
        stringResource(R.string.exp_miuix_all_machines),
        stringResource(R.string.exp_miuix_tab_new),
        stringResource(R.string.exp_miuix_tab_settings),
    )
    val tabs = remember(labels) {
        listOf(
            ExpBottomBarTab(
                label = labels[0],
                glyph = { color -> MonitorGlyph(color = color, size = 22.dp) },
            ),
            ExpBottomBarTab(
                label = labels[1],
                glyph = { color -> PlusGlyph(color = color, size = 22.dp) },
            ),
            ExpBottomBarTab(
                label = labels[2],
                glyph = { color -> SlidersGlyph(color = color, size = 22.dp) },
            ),
        )
    }

    // §6: the status card's title is the state itself — the running machine's
    // name — and the summary carries the machine's own specs (`SDL · 4 核 ·
    // 2048 MB`), not a fixed sentence with the name glued in front of it.
    val unknownArch = stringResource(R.string.exp_miuix_unknown_arch)
    val runningRow = state.rows.firstOrNull { it.running }
    val statusTitle = if (state.isRunning) {
        runningRow?.name?.takeIf { it.isNotEmpty() }
            ?: state.runningName
            ?: stringResource(R.string.exp_miuix_status_running)
    } else {
        stringResource(R.string.exp_miuix_status_idle)
    }
    val statusSummary = if (state.isRunning) {
        if (runningRow != null) {
            stringResource(
                R.string.exp_miuix_machine_meta,
                runningRow.backend.ifEmpty { unknownArch },
                runningRow.cpuNum,
                runningRow.memoryMb,
            )
        } else {
            stringResource(R.string.exp_miuix_status_running)
        }
    } else {
        stringResource(R.string.exp_miuix_status_idle_summary)
    }

    val infoItems = listOf(
        ExpInfoItem(
            title = stringResource(R.string.exp_miuix_info_app),
            value = ExpUiBridge.getAppVersionName(),
        ),
        ExpInfoItem(
            title = stringResource(R.string.exp_miuix_info_engine),
            value = ExpUiBridge.getEngineVersionName(),
        ),
        ExpInfoItem(
            title = stringResource(R.string.exp_miuix_info_abi),
            value = ExpUiBridge.getHostAbi(),
        ),
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExpTopBar(title = stringResource(R.string.exp_miuix_title))

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .scrollEndHaptic(),
                contentPadding = PaddingValues(
                    top = 12.dp,
                    // 24dp page bottom + the floating bar plus its 12dp inset.
                    bottom = 24.dp + BottomBarHeight + 16.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    ExpStatusCard(
                        isRunning = state.isRunning,
                        title = statusTitle,
                        summary = statusSummary,
                        modifier = Modifier.padding(horizontal = 12.dp),
                        onClick = actions.onStatusCardClick,
                    )
                }

                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        ExpCountCard(
                            title = stringResource(R.string.exp_miuix_count_machines),
                            count = state.machineCount,
                            onClick = { runningOnly = false },
                            modifier = Modifier.weight(1f),
                        )
                        ExpCountCard(
                            title = stringResource(R.string.exp_miuix_count_running),
                            count = state.runningCount,
                            onClick = { runningOnly = true },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item { Spacer(Modifier.height(4.dp)) }

                if (visibleRows.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                if (runningOnly) {
                                    R.string.exp_miuix_empty_running
                                } else {
                                    R.string.exp_miuix_empty
                                },
                            ),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }
                } else {
                    items(visibleRows, key = { row -> row.name }) { row ->
                        MachineRowItem(
                            row = row,
                            onClick = { actions.onMachineClick(row.name) },
                            onLongPress = { actions.onMachineLongClick(row.name) },
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
                }

                // §30: NPatch's page ends on an info card, which is the third
                // beat (status → list → info) our list was missing.
                item {
                    ExpInfoCard(
                        items = infoItems,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(top = 8.dp),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            ExpFloatingBottomBar(
                tabs = tabs,
                selectedIndex = selectedTab,
                // §16: only the list tab is a navigation target. "新建" and
                // "设置" are actions, so they must not leave a lasting
                // selection behind — clicking them previously pinned the bar on
                // "设置" forever after coming back.
                onSelected = { index ->
                    when (index) {
                        0 -> selectedTab = 0
                        1 -> actions.onNewMachine()
                        2 -> actions.onSettings()
                    }
                },
            )
        }

        val dialogs = actions.dialogs
        val pendingDelete = dialogs.pendingDelete
        if (pendingDelete != null) {
            ExpConfirmDialog(
                title = stringResource(R.string.exp_miuix_delete_title),
                message = stringResource(R.string.exp_miuix_delete_message, pendingDelete),
                confirmLabel = stringResource(R.string.exp_miuix_delete_confirm),
                dismissLabel = stringResource(R.string.exp_miuix_delete_cancel),
                onConfirm = actions.onDeleteConfirm,
                onDismiss = actions.onDeleteDismiss,
            )
        }

        if (dialogs.newPromptVisible) {
            ExpPromptDialog(
                title = stringResource(R.string.exp_miuix_new_title),
                hint = stringResource(R.string.exp_miuix_new_hint),
                value = dialogs.newName,
                onValueChange = actions.onNewNameChange,
                error = dialogs.newNameError,
                confirmLabel = stringResource(R.string.exp_miuix_new_create),
                dismissLabel = stringResource(R.string.exp_miuix_delete_cancel),
                onConfirm = actions.onNewConfirm,
                onDismiss = actions.onNewDismiss,
            )
        }
    }
}

/**
 * The user actions the home screen can raise.
 *
 * Kept as an explicit value object (instead of passing callbacks down from the
 * item rows) so the screen has one place where the side effects live and the
 * composable stays testable. [dialogs] carries the modal state the screen
 * renders; the callbacks only mutate it or run the resulting side effect.
 */
data class HomeActions(
    val onStatusCardClick: () -> Unit,
    val onMachineClick: (String) -> Unit,
    val onMachineLongClick: (String) -> Unit,
    val dialogs: HomeDialogsState,
    val onNewMachine: () -> Unit,
    val onSettings: () -> Unit,
    val onDeleteConfirm: () -> Unit,
    val onDeleteDismiss: () -> Unit,
    val onNewNameChange: (String) -> Unit,
    val onNewConfirm: () -> Unit,
    val onNewDismiss: () -> Unit,
)

@Composable
private fun MachineRowItem(
    row: MachineRow,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val unknownArch = stringResource(R.string.exp_miuix_unknown_arch)
    val unnamed = stringResource(R.string.exp_miuix_unnamed)

    val meta = stringResource(
        R.string.exp_miuix_machine_meta,
        row.arch.ifEmpty { unknownArch },
        row.cpuNum,
        row.memoryMb,
    )
    ExpMachineCard(
        name = row.name.ifEmpty { unnamed },
        meta = meta,
        running = row.running,
        runningLabel = stringResource(R.string.exp_miuix_badge_running),
        onClick = onClick,
        onLongPress = onLongPress,
        modifier = modifier,
    )
}
