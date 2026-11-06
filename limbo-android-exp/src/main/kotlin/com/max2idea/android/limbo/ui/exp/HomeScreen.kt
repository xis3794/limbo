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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.max2idea.android.limbo.ui.exp.components.ExpBottomBarTab
import com.max2idea.android.limbo.ui.exp.components.ExpCountCard
import com.max2idea.android.limbo.ui.exp.components.ExpFloatingBottomBar
import com.max2idea.android.limbo.ui.exp.components.ExpMachineCard
import com.max2idea.android.limbo.ui.exp.components.ExpStatusCard
import com.max2idea.android.limbo.ui.exp.components.ExpTopBar
import com.max2idea.android.limbo.ui.exp.components.MonitorGlyph
import com.max2idea.android.limbo.ui.exp.components.PlusGlyph
import com.max2idea.android.limbo.ui.exp.components.SlidersGlyph
import com.max2idea.android.limbo.ui.exp.data.MachineListUiState
import com.max2idea.android.limbo.ui.exp.data.MachineRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Height of the floating bottom bar, mirrored from [ExpFloatingBottomBar]. */
private val BottomBarHeight = 64.dp

/**
 * Static home screen for the experimental Miuix UI.
 *
 * Compares against `docs/team/npatch-miuix-style-spec.md`:
 * §2 top bar, §3 page padding, §4 status card, §5 the two-up counters and the
 * machine rows, §6 the floating bottom bar.
 *
 * The screen renders from [MachineListUiState]; the sample implementation of
 * that state (`useSampleData = true`) feeds the placeholder rows so the visuals
 * can be reviewed before the real machine database is wired in.
 */
@Composable
fun HomeScreen(
    state: MachineListUiState,
    modifier: Modifier = Modifier,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            ExpTopBar(title = stringResource(R.string.exp_miuix_title))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
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
                        runningMachineName = state.runningName,
                        titleRunning = stringResource(R.string.exp_miuix_status_running),
                        titleIdle = stringResource(R.string.exp_miuix_status_idle),
                        summaryIdle = stringResource(R.string.exp_miuix_status_idle_summary),
                        modifier = Modifier.padding(horizontal = 12.dp),
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
                            modifier = Modifier.weight(1f),
                        )
                        ExpCountCard(
                            title = stringResource(R.string.exp_miuix_count_running),
                            count = state.runningCount,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item { Spacer(Modifier.height(4.dp)) }

                if (state.rows.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.exp_miuix_empty),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 32.dp),
                        )
                    }
                } else {
                    items(state.rows, key = { row -> row.name }) { row ->
                        MachineRowItem(
                            row = row,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    }
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
                onSelected = { selectedTab = it },
            )
        }
    }
}

@Composable
private fun MachineRowItem(row: MachineRow, modifier: Modifier = Modifier) {
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
        modifier = modifier,
    )
}
