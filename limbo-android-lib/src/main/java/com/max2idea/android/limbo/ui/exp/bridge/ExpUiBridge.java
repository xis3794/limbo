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
     * Whether {@code name} can be opened from the machine list right now.
     *
     * <p>Limbo can only show one guest at a time, so opening a different machine
     * while one is already running is refused by the legacy activity with a
     * "stop the machine first" toast. The Compose screen asks here first so it can
     * show the same message instead of silently doing nothing.
     */
    public static boolean canOpenMachine(String name) {
        if (name == null) {
            return false;
        }
        String running = getRunningMachineName();
        return running == null || running.equals(name);
    }

    /**
     * Creates a named machine through the same dispatcher action the legacy
     * screen uses.
     *
     * <p>Returns {@code false} when the name is empty, already taken, or the
     * dispatcher is not reachable yet; the caller shows its own message. The
     * machine is created with the arch/CPU/memory defaults the legacy flow
     * applies, because the Compose screen has no editor form yet.
     */
    public static boolean createMachine(String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim().replace("\"", "");
        if (trimmed.isEmpty() || getMachineNames().contains(trimmed)) {
            return false;
        }
        try {
            com.max2idea.android.limbo.main.ViewListener listener =
                    com.max2idea.android.limbo.main.LimboApplication.getViewListener();
            if (listener == null) {
                return false;
            }
            listener.onAction(
                    com.max2idea.android.limbo.machine.MachineAction.CREATE_VM, trimmed);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Deletes a machine, refusing while it is the one that is running.
     *
     * <p>Same guard as the legacy long-press flow: deleting the running machine
     * would pull the configuration out from under QEMU.
     */
    public static boolean deleteMachine(String name) {
        if (name == null) {
            return false;
        }
        String running = getRunningMachineName();
        if (running != null && running.equals(name)) {
            return false;
        }
        try {
            com.max2idea.android.limbo.machine.Machine machine =
                    com.max2idea.android.limbo.machine.MachineController.getInstance()
                            .getMachineConfig(name);
            if (machine == null) {
                return false;
            }
            com.max2idea.android.limbo.main.ViewListener listener =
                    com.max2idea.android.limbo.main.LimboApplication.getViewListener();
            if (listener == null) {
                return false;
            }
            listener.onAction(com.max2idea.android.limbo.machine.MachineAction.DELETE_VM, machine);
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

    /**
     * Builds the intent that returns to the display of the running machine.
     *
     * <p>Mirrors the legacy screen exactly: a VNC machine reopens the VNC client,
     * a GTK machine the GTK host (only while the engine and the OS support it),
     * everything else the SDL window. All three activities are declared in the
     * library manifest, which is why this cannot be built on the Compose side —
     * those classes are library-internal.
     *
     * <p>Returns {@code null} when nothing is running.
     */
    public static android.content.Intent createRunningDisplayIntent(
            android.content.Context context) {
        if (context == null) {
            return null;
        }
        String runningName = getRunningMachineName();
        if (runningName == null) {
            return null;
        }
        String ui = null;
        try {
            com.max2idea.android.limbo.machine.Machine machine =
                    com.max2idea.android.limbo.machine.MachineController.getInstance()
                            .getMachineConfig(runningName);
            if (machine != null) {
                ui = com.max2idea.android.limbo.main.Config.normalizeUI(machine.getUI());
            }
        } catch (RuntimeException e) {
            ui = null;
        }
        if (ui == null) {
            try {
                if (com.max2idea.android.limbo.machine.MachineController.getInstance()
                        .isVNCEnabled()) {
                    ui = com.max2idea.android.limbo.main.Config.UI_VNC;
                }
            } catch (RuntimeException e) {
                ui = null;
            }
        }
        Class<?> target;
        if (com.max2idea.android.limbo.main.Config.UI_VNC.equals(ui)) {
            target = com.max2idea.android.limbo.vnc.VncActivity.class;
        } else if (com.max2idea.android.limbo.main.Config.UI_GTK.equals(ui)
                && com.max2idea.android.limbo.main.LimboGtkActivity.isUsable()) {
            target = com.max2idea.android.limbo.main.LimboGtkActivity.class;
        } else {
            target = com.max2idea.android.limbo.main.LimboSDLActivity.class;
        }
        return new android.content.Intent(context, target);
    }

    /**
     * Builds the intent that opens the application settings screen.
     *
     * <p>The Compose module has no reference to the legacy settings activity, so
     * the bridge hands it over pre-resolved.
     */
    public static android.content.Intent createSettingsIntent(
            android.content.Context context) {
        if (context == null) {
            return null;
        }
        return new android.content.Intent(context,
                com.max2idea.android.limbo.main.AppSettingsActivity.class);
    }

    // ------------------------------------------------------------------
    // Read-only build/environment facts for the InfoCard (§30).
    //
    // NPatch's home screen ends with an InfoCard listing API version, NPatch
    // version and framework version. The Limbo equivalent is the app version,
    // the QEMU engine version and the host ABI. All three already live as
    // static state on LimboApplication, so this is a plain wrapper -- but it
    // still returns an empty string instead of null so the Kotlin side can
    // render the rows without null checks.
    // ------------------------------------------------------------------

    /** Human-readable app version (the manifest {@code versionName}). */
    public static String getAppVersionName() {
        try {
            String value =
                    com.max2idea.android.limbo.main.LimboApplication.getLimboVersionString();
            return value == null ? "" : value;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Human-readable QEMU engine version, e.g. {@code 11.0.0}. */
    public static String getEngineVersionName() {
        try {
            String value =
                    com.max2idea.android.limbo.main.LimboApplication.getQemuVersionString();
            return value == null ? "" : value;
        } catch (RuntimeException e) {
            return "";
        }
    }

    /** Primary ABI the native libraries were packaged for. */
    public static String getHostAbi() {
        try {
            return android.os.Build.SUPPORTED_ABIS[0];
        } catch (RuntimeException e) {
            return "";
        }
    }
}
