package com.max2idea.android.limbo.ui.exp.bridge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plain-Java, Compose-free view of the machine list for the experimental UI.
 *
 * <p>This class is the only contract between {@code :limbo-android-exp} (Kotlin
 * + Compose + Miuix) and {@code :limbo-android-lib} (Java). The dependency
 * direction stays one-way: the library module never references Compose, and the
 * Compose module only ever sees plain Java types from here.
 *
 * <p>Design rules, deliberately kept conservative:
 * <ul>
 *   <li>No native code, no JNI. Everything goes through
 *       {@link com.max2idea.android.limbo.machine.MachineController} and its
 *       database helper.</li>
 *   <li>No Android UI objects in the return values — only strings, ints and
 *       immutable value snapshots — so the Compose side can call these from any
 *       dispatcher it likes.</li>
 *   <li>Every call is null-safe and swallows the "service not started yet"
 *       failure modes: the legacy UI wraps
 *       {@code MachineController#getMachineName()} in a try/catch because it
 *       dereferences the currently loaded machine, and the same hazard exists
 *       here.</li>
 * </ul>
 */
public final class ExpUiBridge {

    private ExpUiBridge() {
    }

    /** Immutable snapshot of one row in the machine list. */
    public static final class MachineSnapshot {
        public final String name;
        public final String arch;
        public final int cpuNum;
        public final int memoryMb;
        /** "SDL" / "VNC" / "GTK", already normalised by Config. */
        public final String backend;
        public final boolean running;

        MachineSnapshot(String name, String arch, int cpuNum, int memoryMb,
                        String backend, boolean running) {
            this.name = name;
            this.arch = arch;
            this.cpuNum = cpuNum;
            this.memoryMb = memoryMb;
            this.backend = backend;
            this.running = running;
        }

        @Override
        public String toString() {
            return "MachineSnapshot{" + name + ", " + arch + ", " + cpuNum + "c/"
                    + memoryMb + "MB, " + backend + ", running=" + running + '}';
        }
    }

    // ------------------------------------------------------------------ reads

    /**
     * Names of every stored machine, in database order.
     *
     * <p>Never returns {@code null}; a failure to reach the database yields an
     * empty list so the Compose list can render its empty state instead of
     * crashing.
     */
    public static List<String> getMachineNames() {
        try {
            ArrayList<String> names =
                    com.max2idea.android.limbo.machine.MachineController.getInstance()
                            .getStoredMachines();
            if (names == null) {
                return Collections.emptyList();
            }
            return Collections.unmodifiableList(new ArrayList<>(names));
        } catch (RuntimeException e) {
            return Collections.emptyList();
        }
    }

    /**
     * Whether a machine is currently running.
     *
     * <p>Mirrors {@code MachineController#isRunning()} but never throws.
     */
    public static boolean isRunning() {
        try {
            return com.max2idea.android.limbo.machine.MachineController.getInstance()
                    .isRunning();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Name of the running machine, or {@code null} when nothing is running.
     *
     * <p>{@code MachineController#getMachineName()} dereferences the currently
     * loaded machine, which is null while the service is still starting up; the
     * legacy experimental activity guards that with a try/catch and so do we.
     */
    public static String getRunningMachineName() {
        try {
            return com.max2idea.android.limbo.machine.MachineController.getInstance()
                    .getMachineName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Full snapshot for one machine, or {@code null} when it no longer exists. */
    public static MachineSnapshot getMachine(String name) {
        if (name == null) {
            return null;
        }
        try {
            com.max2idea.android.limbo.machine.Machine machine =
                    com.max2idea.android.limbo.machine.MachineController.getInstance()
                            .getMachineConfig(name);
            if (machine == null) {
                return null;
            }
            return new MachineSnapshot(
                    machine.getName(),
                    machine.getArch(),
                    machine.getCpuNum(),
                    machine.getMemory(),
                    com.max2idea.android.limbo.main.Config.normalizeUI(machine.getUI()),
                    name.equals(getRunningMachineName()));
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Snapshots for every stored machine; never {@code null}. */
    public static List<MachineSnapshot> getMachines() {
        List<String> names = getMachineNames();
        ArrayList<MachineSnapshot> result = new ArrayList<>(names.size());
        for (String name : names) {
            MachineSnapshot snapshot = getMachine(name);
            if (snapshot != null) {
                result.add(snapshot);
            }
        }
        return Collections.unmodifiableList(result);
    }

    /** How many machines are stored. */
    public static int getMachineCount() {
        return getMachineNames().size();
    }

    /** How many machines are running (0 or 1 — Limbo runs one VM at a time). */
    public static int getRunningCount() {
        return isRunning() ? 1 : 0;
    }

    // ----------------------------------------------------------------- writes

    /**
     * Asks the running machine to shut down.
     *
     * <p>{@code MachineController#requestStop()} only posts a shutdown request to
     * QEMU and is explicitly documented as safe from any thread, so the Compose
     * side may call this straight from a click handler. Returns {@code false}
     * when there is nothing to stop.
     */
    public static boolean stopRunningMachine() {
        try {
            com.max2idea.android.limbo.machine.MachineController controller =
                    com.max2idea.android.limbo.machine.MachineController.getInstance();
            if (!controller.isRunning()) {
                return false;
            }
            controller.requestStop();
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Builds the intent that opens a machine in the legacy editor / VM display.
     *
     * <p>Deliberately mirrors what the existing experimental activity does: the
     * concrete launcher activity is {@code Config.clientClass} (each architecture
     * module contributes its own subclass of {@code LimboActivity}), with a
     * package-launch fallback for the case where the static has not been set yet.
     * Returns {@code null} when no target activity can be resolved, so the caller
     * can show its own message instead of crashing.
     *
     * @param name      machine name to open
     * @param autostart whether to boot the machine immediately
     */
    public static android.content.Intent createMachineIntent(
            android.content.Context context, String name, boolean autostart) {
        if (context == null || name == null) {
            return null;
        }
        Class<?> target = com.max2idea.android.limbo.main.Config.clientClass;
        android.content.Intent intent;
        if (target != null) {
            intent = new android.content.Intent(context, target);
        } else {
            intent = context.getPackageManager()
                    .getLaunchIntentForPackage(context.getPackageName());
            if (intent == null) {
                return null;
            }
            intent.setFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                    | android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP);
        }
        intent.putExtra(com.max2idea.android.limbo.main.LimboActivity.EXTRA_MACHINE, name);
        if (autostart) {
            intent.putExtra(com.max2idea.android.limbo.main.LimboActivity.EXTRA_START, true);
        }
        return intent;
    }
}
