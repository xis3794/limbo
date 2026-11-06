package com.max2idea.android.limbo.ui.exp.data

/**
 * One row of the machine list, in the shape the Compose UI wants.
 *
 * Deliberately a plain Kotlin data class rather than the library's `Machine`
 * entity: the experimental module must be able to render from
 * [com.max2idea.android.limbo.ui.exp.bridge.ExpUiBridge.MachineSnapshot] (an
 * immutable Java value object) or from placeholder rows, without ever holding a
 * live framework object.
 */
data class MachineRow(
    val name: String,
    val arch: String,
    val cpuNum: Int,
    val memoryMb: Int,
    val backend: String,
    val running: Boolean,
)

/**
 * Placeholder rows for the static milestone of the migration.
 *
 * The screen is wired to [com.max2idea.android.limbo.ui.exp.bridge.ExpUiBridge]
 * already, but until the settings entry point is flipped over (a later step) the
 * activity renders these rows so the visual work can be reviewed without a
 * populated machine database. Switch [rememberMachineListUiState] to
 * `useSampleData = false` to see the real list.
 */
object SampleMachines {
    val rows: List<MachineRow> = listOf(
        MachineRow(
            name = "Windows 11",
            arch = "x86_64",
            cpuNum = 4,
            memoryMb = 4096,
            backend = "VNC",
            running = true,
        ),
        MachineRow(
            name = "Ubuntu 24.04",
            arch = "x86_64",
            cpuNum = 2,
            memoryMb = 2048,
            backend = "GTK",
            running = false,
        ),
        MachineRow(
            name = "Debian arm64",
            arch = "arm64",
            cpuNum = 2,
            memoryMb = 1024,
            backend = "SDL",
            running = false,
        ),
    )
}
