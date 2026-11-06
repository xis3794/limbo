/*
Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package com.max2idea.android.limbo.main;

import com.max2idea.android.limbo.links.LinksManager;

import java.util.LinkedHashMap;

/**
 * Configuration
 */
public class Config {

    // Constants
    public static final int SDL_MOUSE_LEFT = 1;
    public static final int SDL_MOUSE_MIDDLE = 2;
    public static final int SDL_MOUSE_RIGHT = 3;
    public static final int SETTINGS_RETURN_CODE = 1000;
    public static final int FILEMAN_RETURN_CODE = 1002;

    public static final int SDL_REQUEST_CODE = 1007;
    public static final int SDL_QUIT_RESULT_CODE = 1009;

    public static final int OPEN_IMAGE_FILE_REQUEST_CODE = 2001;
    public static final int OPEN_IMAGE_FILE_ASF_REQUEST_CODE = 2002;

    public static final int OPEN_IMAGE_DIR_REQUEST_CODE = 2003;
    public static final int OPEN_IMAGE_DIR_ASF_REQUEST_CODE = 2004;

    public static final int OPEN_SHARED_DIR_REQUEST_CODE = 2005;
    public static final int OPEN_SHARED_DIR_ASF_REQUEST_CODE = 2006;

    public static final int OPEN_EXPORT_DIR_REQUEST_CODE = 2007;
    public static final int OPEN_EXPORT_DIR_ASF_REQUEST_CODE = 2008;

    public static final int OPEN_IMPORT_FILE_REQUEST_CODE = 2009;
    public static final int OPEN_IMPORT_FILE_ASF_REQUEST_CODE = 2010;

    public static final int OPEN_LOG_FILE_DIR_REQUEST_CODE = 2011;
    public static final int OPEN_LOG_FILE_DIR_ASF_REQUEST_CODE = 2012;

    public static final int OPEN_IMPORT_BIOS_FILE_REQUEST_CODE = 2013;
    public static final int OPEN_IMPORT_BIOS_FILE_ASF_REQUEST_CODE = 2014;

    public static final int STATUS_NULL = -1;
    public static final int STATUS_CREATED = 1000;
    public static final int STATUS_PAUSED = 1001;
    public static final String ACTION_START = "com.max2idea.android.limbo.action.STARTVM";

    // GUI Options
    public static final boolean enable_SDL = true;
    public static final int MAX_CPU_NUM = 8;

    // delay
    static int keyDelay = 100;
    static int mouseButtonDelay = 100;

    // App config
    public static final String APP_NAME = "Limbo Emulator";

    public static final String defaultDNSServer = "8.8.8.8";
    // App Config
    public static final String downloadLink = "https://github.com/limboemu/limbo/wiki/Downloads";
    public static final String guidesLink = "https://github.com/limboemu/limbo/wiki/Guides";
    public static final String kvmLink = "https://github.com/limboemu/limbo/wiki/KVM";
    public static final String faqLink = "https://github.com/limboemu/limbo/wiki/FAQ";
    public static final String toolsLink = "https://github.com/limboemu/limbo/wiki/Tools";
    public static final String newVersionLink = "https://raw.githubusercontent.com/limboemu/limbo/master/VERSION";
    public static final String otherOSLink = "https://github.com/limboemu/limbo/wiki/Other-Operating-Systems";

    public static final boolean enableKeyboardLayoutOption = true;
    public static final boolean enableMouseOption = true;

    // Debug
    public static final boolean debug = false;
    public static final boolean debugQmp = false;
    public static final boolean debugStrictMode = false;

    public static final int EXIT_SUCCESS = 1;
    public static final int EXIT_UNKNOWN = 2;


    public static boolean enableSDLSound = true;

    // stack size to remove an issue with SDL Audio
    public static long stackSize = 10 * 1024 * 1024;
    // native alternative to audio track
    public static String aaudioLibName = "libcompat-SDL2-addons.so";

    // if you don't want to enable software updates set to false
    public static boolean enableSoftwareUpdates = true;

    //TODO: enable immersive mode at some point in time
    public static boolean enableImmersiveMode = false;

    //TODO: add in settings
    public static boolean legacyDrives = false;
    public static boolean enableDefaultDevices = false;
    public static boolean syncFilesOnClose = true;

    public enum Arch {
        x86, x86_64, arm, arm64, ppc, ppc64, sparc, sparc64
    }

    //Enable if you build with KVM support, needes android-21 platform
    public static boolean enableKVM = false;
    public static boolean enableAVX = true;

    // ------------------------------------------------------------------
    // Display backends
    //
    // UI_VNC is the headless remote console; UI_SDL and UI_GTK are local
    // windows. The value is persisted in the TEXT UI column of the machine
    // table, so adding a backend never needs a schema migration.
    // ------------------------------------------------------------------
    public static final String UI_VNC = "VNC";
    public static final String UI_SDL = "SDL";
    public static final String UI_GTK = "GTK";

    /**
     * Maps any stored or unknown value onto a known backend. Rows written
     * before GTK existed only contain VNC or SDL, and a missing/blank column
     * used to mean "SDL", so both keep their old meaning.
     */
    public static String normalizeUI(String ui) {
        if (ui == null) {
            return UI_SDL;
        }
        String trimmed = ui.trim();
        if (UI_VNC.equalsIgnoreCase(trimmed)) {
            return UI_VNC;
        }
        if (UI_GTK.equalsIgnoreCase(trimmed)) {
            return UI_GTK;
        }
        return UI_SDL;
    }

    /**
     * Whether the QEMU engine in use was built with the GTK display backend.
     * Resolved at start up by DisplayBackendProbe (the presence of
     * libgtk-4.so next to libqemu-system-*.so). This is a fact about the
     * build, not a user preference.
     */
    public static boolean engineHasGtk = false;

    /**
     * Whether the QEMU engine was built with CONFIG_OPENGL and virglrenderer.
     * Also a build fact, resolved by DisplayBackendProbe.
     */
    public static boolean engineHasVirgl = false;

    /**
     * User preference: offer GTK windows at all. Defaulted from the engine
     * capability and persisted in settings, so a user can keep GTK available
     * without it becoming a per-machine surprise.
     */
    public static boolean enableGtk = false;

    /** User preference: offer GPU accelerated display options. */
    public static boolean enableVirgl = false;

    /**
     * Set when GDK's android backend could not be initialised in this process
     * (libgtk-4.so missing, dlopen failed, no JNI VM...). GTK then has no
     * display to open at all, QEMU's gtk_init() fails before the guest ever
     * boots, and on Android gtk_init() calls exit(1) - it takes the whole app
     * down. Once this is set the app stops offering GTK and starts every
     * machine with SDL instead, for the rest of the process.
     */
    public static boolean gtkInitFailed = false;

    /**
     * GTK's GDK android backend needs API 31+ (the AMotionEvent/AKeyEvent JNI
     * helpers are only exported by libandroid.so from API 31 on), so an
     * engine built with GTK still cannot be used on older devices.
     */
    public static boolean isGtkUsable() {
        return enableGtk && engineHasGtk && !gtkInitFailed
                && android.os.Build.VERSION.SDK_INT >= 31;
    }

    /** Whether virtio-gpu-gl devices and ,gl=on are available to the user. */
    public static boolean isVirglUsable() {
        return enableVirgl && engineHasVirgl;
    }

    public static String storagedir = null;
    //Some OSes don't like emulated multi cores for QEMU 2.9.1 you can disable here
    /// thought there is also the Disable TSC feature so you don't have to do it here
    public static boolean enableSMPOnlyOnKVM = false;
    //set to true if you need to debug native library loading
    public static boolean loadNativeLibsEarly = false;
    //XXX: QEMU 3.1.0 needs the libraries to be loaded from the main thread
    public static boolean loadNativeLibsMainThread = true;
    public static String wakeLockTag = "limbo:wakelock";
    public static String wifiLockTag = "limbo:wifilock";

    //XXX set scaling to linear it's a tad slower but it's worth it
    public static int SDLHintScale = 1;

    /**
     * How the GTK display maps the guest framebuffer onto the phone screen.
     *
     * <p>Handed to QEMU as {@code limbo_gtk_scale_mode} (see
     * {@code gd_update_scale()} in the GTK4 patch) so that both the cairo and
     * the GL drawing paths share one policy:
     * <ul>
     *   <li>0 - stretch: fill the whole surface, ignoring the aspect ratio
     *       (no black bars, but circles become ellipses);</li>
     *   <li>1 - fit: keep the aspect ratio and letterbox, the default;</li>
     *   <li>2 - 1:1: show the guest at its native pixel size, centred, which
     *       is the only mode where the guest's pixels stay perfectly sharp;</li>
     *   <li>3 - fit width: scale so the width fills the screen and crop the
     *       vertical overflow, which removes the bars on a wide guest while
     *       keeping the aspect ratio.</li>
     * </ul>
     * Persisted in the settings screen; changing it takes effect on the next
     * VM start.
     */
    public static int GTKScaleMode = 1;
    public static final int GTK_SCALE_STRETCH = 0;
    public static final int GTK_SCALE_FIT = 1;
    public static final int GTK_SCALE_ONE_TO_ONE = 2;
    public static final int GTK_SCALE_FIT_WIDTH = 3;

    /** Human readable labels for {@link #GTKScaleMode}, index == mode. */
    public static final String[] GTK_SCALE_MODE_LABELS = {
            "Stretch to fill", "Fit screen (keep ratio)", "1:1 (no scaling)",
            "Fill width, crop height"
    };
    /**
     * Frame interval in milliseconds handed to QEMU's
     * {@code gui_refresh_interval_default} for the GTK display, or 0 to keep
     * the built-in default.
     *
     * <p>The Android GDK backend never reports a monitor refresh rate, so
     * {@code gd_update_monitor_refresh_rate()} in {@code ui/gtk.c} always
     * falls back to {@code GUI_REFRESH_INTERVAL_DEFAULT} (30 ms, about 33
     * fps) and never reaches the idle interval either. On the GtkGLArea path
     * a frame is only a composited texture, so a 60 Hz cadence is cheap and
     * removes the sluggishness next to the SDL and VNC screens. The software
     * cairo path keeps 0: it re-samples the whole framebuffer on the CPU, and
     * more frames only make it hotter, not smoother.
     *
     * <p>Computed by {@code VMExecutor#addDisplayOptions} every time a VM is
     * started, it is not a user setting.
     */
    public static int GTKFrameIntervalMs = 0;

    public static boolean viewLogInternally = true;
    //XXX some archs don't support floppy or sd card
    public static boolean enableEmulatedFloppy = true;
    public static boolean enableEmulatedSDCard;
    public static String destLogFilename = "limbolog.txt";
    public static String notificationChannelID = "limbo";
    public static String notificationChannelName = "limbo";
    public static boolean showToast = false;
    public static boolean closeFileDescriptors = true;

    // Continuous logcat capture (FileUtils.startLogging) spawns a resident
    // "logcat v main" child process for the whole VM session and writes every
    // line to the log file. That is a real CPU / IO / battery cost while the
    // guest is running, so it is OFF by default now.
    // Turning it on only restores the opt-in debug capture: the previous log
    // file is never deleted or truncated while the feature is off, so
    // "view log" / "export log" keep showing the last captured run.
    public static boolean enableRuntimeLogcatCapture = false;

    // One-shot dump of /proc/self/maps at VM start (tag "LimboMaps").
    // Cheap (runs once, before QEMU loads) and it is what lets us resolve a
    // crash backtrace to libc / libqemu / SDL base addresses offline, so it
    // stays on even when the continuous capture above is disabled.
    public static boolean enableStartupMapsDump = true;

    // When the continuous capture above is off, dump a bounded tail of the logcat
    // buffer once, when the VM stops (FileUtils.dumpFinalLogcatSnapshot). This is
    // what keeps "view log" / "export log" useful for the run that just ended
    // without paying for a resident logcat process.
    public static boolean enableFinalLogcatDump = true;
    // Bounds for the one-shot tail dump: keep the log file usable by
    // Logger.viewLimboLog (which truncates around 50 KB) and cheap on IO.
    public static int finalLogcatDumpLines = 800;
    public static int finalLogcatDumpMaxBytes = 256 * 1024;

    // The log file is appended to and never cleared (so the previous run stays
    // readable). Stop writing once it exceeds this size instead of growing forever:
    // Logger / FileUtils.getFileContents read the whole file into memory and already
    // have an OOM FIXME. Delete cache/limbo/*-log.txt by hand to start over.
    public static long logMaxBytes = 8 * 1024 * 1024;
    //XXX: qemu vvfat is buggy so we disable
    public static boolean enableSharedFolder = false;

    public static String machineFolder = "machines/";
    public static String logFilePath = null;

    public static String stateFilename = "vm.state";
    //QMP
    public static String QMPServer = "127.0.0.1";
    public static int QMPPort = 4444;
    public static int MAX_DISPLAY_REFRESH_RATE = 100; //Hz
    // VNC Defaults
    public static String defaultVNCHost = "127.0.0.1";
    //It seems that new versions of qemu expect a relative number
    //  so we stop using absolute port numbers
    public static final int defaultVNCPort = 1;

    //Keyboard Layout
    public static String defaultKeyboardLayout = "en-us";

    //a little nicer ui
    public static boolean collapseSections = false;

    public static boolean enableToggleKeyboard = false;

    //override this at the app level it dependes on the host arch
    public static boolean enableMTTCG = true;

    public static LinkedHashMap<String, LinksManager.LinkInfo> osImages = new LinkedHashMap<>();
    public static boolean processMouseHistoricalEvents = false;

    //Change to true in prod if you want to be notified by default for new versions
    public static boolean defaultCheckNewVersion = false;

    //enable tracing
    // make sure you have access to the dir/files below
    public static boolean enableTracingLog = false;
    public static final String traceDir = "/sdcard/limbo/tmp/trace";
    public static final String traceEventsFile = "/sdcard/limbo/tmp/events";
    public static final String traceLogFile = "/sdcard/limbo/log.txt";

    // override translation block size
    public static boolean overrideTbSize;
    public static String tbSize = "32M";

    // Class that starts when user presses notification
    public static Class<?> clientClass = null;

}
