package com.max2idea.android.limbo.ui.exp;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.machine.Machine;
import com.max2idea.android.limbo.machine.MachineAction;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.main.AppSettingsActivity;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboActivity;
import com.max2idea.android.limbo.main.LimboApplication;
import com.max2idea.android.limbo.main.LimboGtkActivity;
import com.max2idea.android.limbo.main.LimboSDLActivity;
import com.max2idea.android.limbo.vnc.VncActivity;

import java.util.ArrayList;

/**
 * NPatch / Miuix style machine list for Limbo.
 *
 * <p>The visual structure is a 1:1 mapping of NPatch's {@code HomeScreen}
 * (docs/team/npatch-miuix-style-spec.md section 5):
 * <pre>
 * transparent collapsing top bar ("虚拟机" 32sp title, "+" action)
 *   -> status card (running machine, or "nothing running")
 *   -> two count cards (machines / running)
 *   -> "全部虚拟机" section title
 *   -> one card per machine (48dp tile, two text lines, running badge)
 * floating capsule bottom bar ("新建" / "设置")
 * </pre>
 *
 * <p>Every view is built in code from plain {@link View}s: no XML layout, no
 * Kotlin / declarative UI framework, no new dependency, and only API 21..29
 * symbols.
 */
public class LimboExperimentalActivity extends AppCompatActivity
        implements ExpMachineListAdapter.Listener, ExpBottomBar.Listener,
        MachineController.OnMachineStatusChangeListener {

    /** The repository refreshes the list asynchronously after these edits. */
    private static final long REFRESH_DELAY_DELETE_MS = 700;
    private static final long REFRESH_DELAY_CREATE_MS = 800;
    /** Spec section 2: content keeps 12dp below the top bar and 24dp above the bar. */
    private static final float CONTENT_TOP_EXTRA_DP = 12f;
    private static final float CONTENT_BOTTOM_EXTRA_DP = 24f;

    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isFinishing() && !isDestroyed()) {
                refreshMachines();
            }
        }
    };

    private ExpTheme theme;
    private MachineController machineController;

    private FrameLayout root;
    private ExpTopBar topBar;
    private RecyclerView list;
    private ExpMachineListAdapter adapter;

    private int statusBarHeight;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        machineController = MachineController.getInstance();
        theme = ExpTheme.from(this);

        setupWindow();

        statusBarHeight = readStatusBarHeight();

        setContentView(buildContentView());
        machineController.addOnStatusChangeListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // The running machine can change while another activity is on top.
        refreshMachines();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (machineController != null) {
            machineController.removeOnStatusChangeListener(this);
        }
        super.onDestroy();
    }

    // ---------------------------------------------------------------- window

    /**
     * Draws the page edge-to-edge under a transparent status bar so the top bar
     * can be genuinely transparent like NPatch's. The navigation bar keeps the
     * page background, so no bottom inset has to be added.
     */
    @SuppressWarnings("deprecation")
    private void setupWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
        // Both setters exist from API 21 on, which is this module's minSdk.
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(theme.background);
        View decor = window.getDecorView();
        int flags = decor.getSystemUiVisibility();
        flags |= View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        // Dark system-bar glyphs are API 23 (status bar) / API 26 (navigation bar).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!theme.dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!theme.dark) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        decor.setSystemUiVisibility(flags);
    }

    /**
     * Status-bar height from the framework dimen, so no window-insets API is
     * needed at all (that keeps the code valid from API 21 through compileSdk 29).
     */
    private int readStatusBarHeight() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) {
            int height = getResources().getDimensionPixelSize(id);
            if (height > 0) {
                return height;
            }
        }
        return theme.dp(24f);
    }

    // ---------------------------------------------------------------- layout

    private View buildContentView() {
        root = new FrameLayout(this);
        root.setBackgroundColor(theme.background);

        list = new RecyclerView(this);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setClipToPadding(false);
        list.setOverScrollMode(View.OVER_SCROLL_NEVER);
        adapter = new ExpMachineListAdapter(this, theme, this);
        list.setAdapter(adapter);
        root.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        topBar = new ExpTopBar(this, theme, statusBarHeight, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptNewMachine();
            }
        });
        root.addView(topBar);

        ExpBottomBar bottomBar = new ExpBottomBar(this, theme, this);
        root.addView(bottomBar);

        list.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                topBar.onScrolled(recyclerView.computeVerticalScrollOffset());
            }
        });

        root.post(new Runnable() {
            @Override
            public void run() {
                updateListPadding();
            }
        });
        return root;
    }

    /**
     * Top padding clears the expanded top bar, bottom padding clears the floating
     * capsule (spec section 2: innerPadding + 24dp + bar height).
     */
    private void updateListPadding() {
        if (list == null || topBar == null) {
            return;
        }
        int top = topBar.getExpandedHeight() + theme.dp(CONTENT_TOP_EXTRA_DP);
        int bottom = ExpBottomBar.occupiedHeight(theme) + theme.dp(CONTENT_BOTTOM_EXTRA_DP);
        list.setPadding(0, top, 0, bottom);
    }

    // ------------------------------------------------------------------ data

    private void refreshMachines() {
        ArrayList<String> names = machineController.getStoredMachines();
        if (names == null) {
            names = new ArrayList<String>();
        }

        boolean running = machineController.isRunning();
        String runningName = running ? safeRunningName() : null;
        if (running && TextUtils.isEmpty(runningName)) {
            // The service reports Running but no machine is loaded yet; treat the
            // state as "not running" so the card does not claim an unnamed VM.
            running = false;
        }

        ArrayList<ExpMachineListAdapter.Entry> entries =
                new ArrayList<ExpMachineListAdapter.Entry>(names.size());
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            Machine machine = machineController.getMachineConfig(name);
            String displayName = name;
            if (machine != null && !TextUtils.isEmpty(machine.getName())) {
                displayName = machine.getName();
            }
            if (TextUtils.isEmpty(displayName)) {
                displayName = getString(R.string.exp_unnamed_machine);
            }
            String meta;
            if (machine == null) {
                meta = getString(R.string.exp_config_unavailable);
            } else {
                meta = getString(R.string.exp_machine_meta,
                        displayArch(machine), machine.getCpuNum(), machine.getMemory());
            }
            boolean machineRunning = running && name != null && name.equals(runningName);
            entries.add(new ExpMachineListAdapter.Entry(name, displayName, meta, machineRunning));
        }

        String statusTitle = null;
        String statusMeta = null;
        if (running) {
            Machine runningMachine = machineController.getMachineConfig(runningName);
            statusTitle = TextUtils.isEmpty(runningName)
                    ? getString(R.string.exp_unnamed_machine) : runningName;
            // Spec 5: the status card summary reads "显示后端 · N 核 · N MB"
            // (e.g. "SDL · 4 核 · 2048 MB"); the adapter prefixes the machine name.
            statusMeta = runningMachine == null
                    ? getString(R.string.exp_config_unavailable)
                    : getString(R.string.exp_status_meta, displayBackend(runningMachine),
                    runningMachine.getCpuNum(), runningMachine.getMemory());
        }

        adapter.setData(entries, running, statusTitle, statusMeta);
    }

    /**
     * {@link MachineController#getMachineName()} dereferences the currently loaded
     * machine, which is null while the service is still starting up.
     */
    private String safeRunningName() {
        try {
            return machineController.getMachineName();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String displayArch(Machine machine) {
        return TextUtils.isEmpty(machine.getArch())
                ? getString(R.string.exp_unknown_arch) : machine.getArch();
    }

    /** "SDL" / "VNC" / "GTK" for the status card summary. */
    private String displayBackend(Machine machine) {
        return Config.normalizeUI(machine.getUI());
    }

    private void delayedRefresh(long delayMs) {
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, delayMs);
    }

    // ------------------------------------------------------------ list events

    @Override
    public void onStatusCardClick() {
        if (!machineController.isRunning()) {
            Toast.makeText(this, R.string.exp_status_idle_summary, Toast.LENGTH_SHORT).show();
            return;
        }
        openRunningDisplay();
    }

    @Override
    public void onTotalCountClick() {
        scrollToList();
    }

    @Override
    public void onRunningCountClick() {
        scrollToList();
    }

    @Override
    public void onEmptyCreateClick() {
        promptNewMachine();
    }

    @Override
    public void onMachineClick(String machineName) {
        openMachine(machineName, false);
    }

    @Override
    public void onMachineStartClick(String machineName) {
        openMachine(machineName, true);
    }

    @Override
    public void onMachineLongClick(String machineName) {
        confirmDelete(machineName);
    }

    /** Row 0 is the header (status card + count cards), row 1 the first machine. */
    private void scrollToList() {
        if (list == null || adapter.getItemCount() <= 1) {
            return;
        }
        list.smoothScrollToPosition(1);
    }

    // ---------------------------------------------------------- bottom bar

    @Override
    public void onNewMachine() {
        promptNewMachine();
    }

    @Override
    public void onSettings() {
        startActivity(new Intent(this, AppSettingsActivity.class));
    }

    // ------------------------------------------------------ machine status

    @Override
    public void onMachineStatusChanged(Machine machine, MachineController.MachineStatus status,
                                       Object o) {
        // Status callbacks can arrive from the QEMU thread and may land while the
        // list is computing a layout, so always post instead of refreshing inline.
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed()) {
                    refreshMachines();
                }
            }
        });
    }

    // -------------------------------------------------------------- actions

    /** Opens the machine editor, or the VM display when {@code autostart} is set. */
    private void openMachine(String name, boolean autostart) {
        MachineController controller = MachineController.getInstance();
        String currentName = controller.isRunning() ? safeRunningName() : null;
        if (controller.isRunning() && currentName != null && !name.equals(currentName)) {
            // A different machine owns the display already; the editor cannot open.
            Toast.makeText(this, R.string.stop_first, Toast.LENGTH_SHORT).show();
            return;
        }

        // LimboActivity has no <activity> entry of its own: every architecture
        // ships a concrete launcher subclass and LimboActivity.onCreate() is where
        // they all converge. Config.clientClass is that registered launcher class,
        // so piggy-back on it instead of hard-coding LimboActivity.class, which
        // would throw ActivityNotFoundException.
        Class<?> target = Config.clientClass;
        if (target == null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch == null) {
                Toast.makeText(this, R.string.exp_config_unavailable, Toast.LENGTH_SHORT).show();
                return;
            }
            launch.putExtra(LimboActivity.EXTRA_MACHINE, name);
            if (autostart) {
                launch.putExtra(LimboActivity.EXTRA_START, true);
            }
            launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launch);
            return;
        }

        Intent intent = new Intent(this, target);
        intent.putExtra(LimboActivity.EXTRA_MACHINE, name);
        if (autostart) {
            intent.putExtra(LimboActivity.EXTRA_START, true);
        }
        startActivity(intent);
    }

    /**
     * Jumps to the display of the machine that is currently running.
     *
     * <p>Same choice the legacy home screen makes: VNC machines open the VNC
     * client, GTK machines the GTK host (only when the engine and OS support it),
     * everything else the SDL window. All three activities are declared in the
     * library manifest.
     */
    private void openRunningDisplay() {
        String runningName = safeRunningName();
        Machine runningMachine = runningName == null
                ? null : machineController.getMachineConfig(runningName);
        String ui = Config.UI_SDL;
        if (runningMachine != null) {
            ui = Config.normalizeUI(runningMachine.getUI());
        } else if (machineController.isVNCEnabled()) {
            ui = Config.UI_VNC;
        }
        if (Config.UI_VNC.equals(ui)) {
            startActivity(new Intent(this, VncActivity.class));
        } else if (Config.UI_GTK.equals(ui) && Config.isGtkUsable()) {
            startActivity(new Intent(this, LimboGtkActivity.class));
        } else {
            startActivity(new Intent(this, LimboSDLActivity.class));
        }
    }

    // -------------------------------------------------------------- dialogs

    private void promptNewMachine() {
        final EditText input = new EditText(this);
        input.setHint(R.string.exp_machine_name_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        // The dialog takes its colours from the activity theme, so the input keeps
        // the theme colours instead of the custom dark/light tokens.

        int horizontalPadding = theme.dp(20f);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(horizontalPadding, theme.dp(8f), horizontalPadding, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.exp_new_machine)
                .setView(container)
                .setPositiveButton(R.string.exp_create, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim().replace("\"", "");
                        if (name.length() == 0) {
                            Toast.makeText(LimboExperimentalActivity.this,
                                    R.string.exp_name_required, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        ArrayList<String> stored = machineController.getStoredMachines();
                        if (stored != null && stored.contains(name)) {
                            Toast.makeText(LimboExperimentalActivity.this,
                                    R.string.exp_name_taken, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        LimboApplication.getViewListener().onAction(
                                MachineAction.CREATE_VM, name);
                        delayedRefresh(REFRESH_DELAY_CREATE_MS);
                    }
                })
                .setNegativeButton(R.string.exp_cancel, null)
                .show();
    }

    private void confirmDelete(final String name) {
        if (machineController.isRunning() && name != null
                && name.equals(safeRunningName())) {
            Toast.makeText(this, R.string.stop_first, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.exp_delete_title)
                .setMessage(getString(R.string.exp_delete_message, name))
                .setPositiveButton(R.string.exp_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Machine machine = machineController.getMachineConfig(name);
                        if (machine == null) {
                            return;
                        }
                        LimboApplication.getViewListener().onAction(
                                MachineAction.DELETE_VM, machine);
                        delayedRefresh(REFRESH_DELAY_DELETE_MS);
                    }
                })
                .setNegativeButton(R.string.exp_cancel, null)
                .show();
    }
}
