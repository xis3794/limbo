package com.max2idea.android.limbo.main;

import android.app.Activity;
import android.content.Intent;
import android.content.UriPermission;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.StaggeredGridLayoutManager;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.help.Help;
import com.max2idea.android.limbo.install.Installer;
import com.max2idea.android.limbo.log.Logger;
import com.max2idea.android.limbo.machine.Machine;
import com.max2idea.android.limbo.machine.MachineAction;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.machine.MachineProperty;
import com.max2idea.android.limbo.ui.AppBackgroundView;
import com.max2idea.android.limbo.ui.GlassBlurView;
import com.max2idea.android.limbo.ui.UiDialogs;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Home screen: machine library (staggered cards), running session and app settings.
 */
public class HomeController implements
        MachineController.OnMachineStatusChangeListener,
        MachineController.OnEventListener {

    public static final int REQ_WALLPAPER = 9101;
    public static final int REQ_MACHINE_ICON = 9102;

    private static final int TAB_MACHINES = 0;
    private static final int TAB_SCREEN = 1;
    private static final int TAB_SETTINGS = 2;

    private final LimboActivity activity;

    private AppBackgroundView background;
    private TextView title;
    private TextView subtitle;
    private ImageButton addButton;
    private RecyclerView grid;
    private GlassBlurView navGlass;
    private View screenTab;
    private View settingsTab;

    private View screenDot;
    private TextView screenState;
    private TextView screenMachine;
    private TextView screenMeta;
    private TextView screenRam;
    private TextView screenCores;
    private TextView screenDisplay;
    private View screenEnter;
    private View screenStop;

    private TextView themeValue;
    private TextView wallpaperValue;
    private TextView aboutValue;

    private final View[] navRoots = new View[3];
    private final ImageView[] navIcons = new ImageView[3];
    private final View[] navPills = new View[3];
    private final TextView[] navLabels = new TextView[3];

    private MachineGridAdapter adapter;
    private int tab = TAB_MACHINES;
    private String pendingIconMachine;

    public HomeController(LimboActivity activity) {
        this.activity = activity;
    }

    public void setup() {
        background = activity.findViewById(R.id.home_background);
        title = activity.findViewById(R.id.home_title);
        subtitle = activity.findViewById(R.id.home_subtitle);
        addButton = activity.findViewById(R.id.home_add);
        grid = activity.findViewById(R.id.machine_grid);
        navGlass = activity.findViewById(R.id.home_nav_glass);
        screenTab = activity.findViewById(R.id.screen_tab);
        settingsTab = activity.findViewById(R.id.settings_tab);

        screenDot = activity.findViewById(R.id.screen_dot);
        screenState = activity.findViewById(R.id.screen_state);
        screenMachine = activity.findViewById(R.id.screen_machine);
        screenMeta = activity.findViewById(R.id.screen_meta);
        screenRam = activity.findViewById(R.id.screen_ram_value);
        screenCores = activity.findViewById(R.id.screen_cores_value);
        screenDisplay = activity.findViewById(R.id.screen_display_value);
        screenEnter = activity.findViewById(R.id.screen_enter);
        screenStop = activity.findViewById(R.id.screen_stop);

        themeValue = activity.findViewById(R.id.theme_value);
        wallpaperValue = activity.findViewById(R.id.wallpaper_value);
        aboutValue = activity.findViewById(R.id.about_value);

        navRoots[0] = activity.findViewById(R.id.nav_machines);
        navRoots[1] = activity.findViewById(R.id.nav_screen);
        navRoots[2] = activity.findViewById(R.id.nav_settings);
        navIcons[0] = activity.findViewById(R.id.nav_ic_machines);
        navIcons[1] = activity.findViewById(R.id.nav_ic_screen);
        navIcons[2] = activity.findViewById(R.id.nav_ic_settings);
        navPills[0] = activity.findViewById(R.id.nav_pill_machines);
        navPills[1] = activity.findViewById(R.id.nav_pill_screen);
        navPills[2] = activity.findViewById(R.id.nav_pill_settings);
        navLabels[0] = activity.findViewById(R.id.nav_tx_machines);
        navLabels[1] = activity.findViewById(R.id.nav_tx_screen);
        navLabels[2] = activity.findViewById(R.id.nav_tx_settings);

        for (int i = 0; i < navRoots.length; i++) {
            final int index = i;
            if (navRoots[i] != null) {
                navRoots[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                        selectTab(index);
                    }
                });
            }
        }

        if (addButton != null) {
            addButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    promptNewMachine();
                }
            });
        }

        adapter = new MachineGridAdapter(new MachineGridAdapter.Listener() {
            @Override
            public void onOpen(MachineGridAdapter.Item item) {
                openConfig(item.name);
            }

            @Override
            public void onMore(MachineGridAdapter.Item item) {
                showMachineMenu(item);
            }

            @Override
            public void onStart(MachineGridAdapter.Item item) {
                openConfig(item.name, true);
            }

            @Override
            public void onAdd() {
                promptNewMachine();
            }
        });
        grid.setLayoutManager(new StaggeredGridLayoutManager(2, StaggeredGridLayoutManager.VERTICAL));
        grid.setAdapter(adapter);

        final View homeContent = activity.findViewById(R.id.home_content);
        final View navShell = activity.findViewById(R.id.home_nav_shell);
        final View navRow = activity.findViewById(R.id.home_nav);
        if (navShell != null && navRow != null) {
            navRow.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    int width = right - left;
                    if (width > 0 && navShell.getLayoutParams().width != width) {
                        navShell.getLayoutParams().width = width;
                        navShell.requestLayout();
                    }
                }
            });
        }
        if (navGlass != null) {
            navGlass.setCornerRadiusDp(38f);
            navGlass.setTarget(homeContent);
            grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    navGlass.refresh();
                }
            });
            ViewTreeObserver.OnScrollChangedListener scrollListener =
                    new ViewTreeObserver.OnScrollChangedListener() {
                        @Override
                        public void onScrollChanged() {
                            navGlass.refresh();
                        }
                    };
            ((android.view.View) screenTab).getViewTreeObserver()
                    .addOnScrollChangedListener(scrollListener);
            ((android.view.View) settingsTab).getViewTreeObserver()
                    .addOnScrollChangedListener(scrollListener);
            navGlass.postDelayed(new Runnable() {
                @Override
                public void run() {
                    navGlass.refresh();
                }
            }, 450);
        }

        bindRows();
        bindScreenActions();

        if (background != null) {
            String wallpaper = UiPrefs.getWallpaper(activity);
            background.setWallpaperUri(wallpaper == null ? null : Uri.parse(wallpaper));
        }
        updateThemeValue();
        updateWallpaperValue();
        updateAboutValue();

        selectTab(TAB_MACHINES);
        refreshMachines();
        refreshScreen();
    }

    private void bindRows() {
        activity.findViewById(R.id.row_theme).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showThemeDialog();
            }
        });
        activity.findViewById(R.id.row_wallpaper).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showWallpaperDialog();
            }
        });
        activity.findViewById(R.id.row_global_settings).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activity.startActivity(new Intent(activity, AppSettingsActivity.class));
            }
        });
        activity.findViewById(R.id.row_install).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Installer.installFiles(activity, true);
            }
        });
        activity.findViewById(R.id.row_log).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Logger.promptShowLog(activity);
            }
        });
        activity.findViewById(R.id.row_help).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Help.showHelp(activity);
            }
        });
        activity.findViewById(R.id.row_about).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAboutDialog();
            }
        });
    }

    private void bindScreenActions() {
        screenEnter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (MachineController.getInstance().isRunning()) {
                    activity.startActivity(new Intent(activity, LimboSDLActivity.class));
                }
            }
        });
        screenStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptStopMachine();
            }
        });
    }

    private boolean isMachinePaused() {
        Machine machine = MachineController.getInstance().getMachine();
        return !MachineController.getInstance().isRunning()
                && machine != null && machine.getPaused() == 1;
    }

    private void promptStopMachine() {
        final MachineController controller = MachineController.getInstance();
        final boolean running = controller.isRunning();
        final boolean paused = isMachinePaused();
        if (!running && !paused) {
            Toast.makeText(activity, R.string.vmNotRunning, Toast.LENGTH_SHORT).show();
            return;
        }
        if (paused) {
            UiDialogs.prompt(activity,
                    activity.getString(R.string.discardVMState),
                    activity.getString(R.string.discardVMInstructions),
                    activity.getString(R.string.cancel),
                    activity.getString(R.string.screen_discard),
                    new Runnable() {
                        @Override
                        public void run() {
                            LimboApplication.getViewListener().onFieldChange(MachineProperty.PAUSED, 0);
                            delayedScreenRefresh(600);
                        }
                    });
            return;
        }
        final boolean sdl = !controller.isVNCEnabled();
        UiDialogs.prompt(activity,
                activity.getString(R.string.dialog_stop_title),
                activity.getString(R.string.ShutdownVMWarning),
                activity.getString(R.string.cancel),
                activity.getString(R.string.screen_stop),
                new Runnable() {
                    @Override
                    public void run() {
                        stopRunningMachine(sdl);
                    }
                });
    }

    private void stopRunningMachine(boolean sdl) {
        Toast.makeText(activity, R.string.screen_stopping, Toast.LENGTH_SHORT).show();
        // Try the direct shutdown request first: it is handled as soon as the QEMU
        // main loop wakes up.
        MachineController.getInstance().requestStop();
        if (!sdl) {
            return;
        }
        // In SDL mode QEMU renders through the SDL surface; while that surface is
        // in the background the display thread can block the QEMU main loop, so the
        // request above may never be processed. Bring the surface back and issue
        // the stop from the SDL activity in that case.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                if (activity.isFinishing() || activity.isDestroyed()) {
                    return;
                }
                if (MachineController.getInstance().isRunning()) {
                    LimboSDLActivity.pendingStopImmediate = true;
                    activity.startActivity(new Intent(activity, LimboSDLActivity.class));
                }
            }
        }, 4000);
    }

    private void delayedScreenRefresh(long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshScreen();
            }
        }, delay);
    }

    public void onResume() {
        refreshMachines();
        refreshScreen();
        if (navGlass != null) {
            navGlass.postDelayed(new Runnable() {
                @Override
                public void run() {
                    navGlass.refresh();
                }
            }, 300);
        }
        updateThemeValue();
        updateWallpaperValue();
    }

    public void onDestroy() {
        try {
            MachineController.getInstance().removeOnStatusChangeListener(this);
            MachineController.getInstance().removeOnEventListener(this);
        } catch (Exception ignored) {
        }
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        final Uri uri = data.getData();
        try {
            activity.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }
        if (requestCode == REQ_WALLPAPER) {
            UiPrefs.setWallpaper(activity, uri.toString());
            if (background != null) {
                background.setWallpaperUri(uri);
            }
            updateWallpaperValue();
        } else if (requestCode == REQ_MACHINE_ICON && pendingIconMachine != null) {
            UiPrefs.setMachineIcon(activity, pendingIconMachine, uri.toString());
            pendingIconMachine = null;
            refreshMachines();
        }
    }

    private void selectTab(int index) {
        tab = index;
        String[] titles = {
                activity.getString(R.string.nav_machines),
                activity.getString(R.string.nav_screen),
                activity.getString(R.string.nav_settings)
        };
        String[] subs = {
                activity.getString(R.string.home_machines_sub),
                activity.getString(R.string.home_screen_sub),
                activity.getString(R.string.home_settings_sub)
        };
        title.setText(titles[index]);
        subtitle.setText(subs[index]);
        addButton.setVisibility(index == TAB_MACHINES ? View.VISIBLE : View.GONE);
        grid.setVisibility(index == TAB_MACHINES ? View.VISIBLE : View.GONE);
        screenTab.setVisibility(index == TAB_SCREEN ? View.VISIBLE : View.GONE);
        settingsTab.setVisibility(index == TAB_SETTINGS ? View.VISIBLE : View.GONE);

        final int activeColor = ContextCompat.getColor(activity, R.color.limbo_accent);
        final int inactiveColor = ContextCompat.getColor(activity, R.color.limbo_text_muted);
        for (int i = 0; i < navRoots.length; i++) {
            final boolean active = i == index;
            if (navPills[i] != null) {
                navPills[i].setBackgroundResource(active ? R.drawable.bg_nav_active : 0);
            }
            if (navIcons[i] != null) {
                navIcons[i].setColorFilter(active ? activeColor : inactiveColor);
                navIcons[i].animate().scaleX(active ? 1.08f : 1f)
                        .scaleY(active ? 1.08f : 1f).setDuration(160).start();
            }
            if (navLabels[i] != null) {
                navLabels[i].setTextColor(active ? activeColor : inactiveColor);
            }
        }
        if (navGlass != null) {
            navGlass.postDelayed(new Runnable() {
                @Override
                public void run() {
                    navGlass.refresh();
                }
            }, 120);
        }
        if (index == TAB_SCREEN) {
            refreshScreen();
        } else if (index == TAB_MACHINES) {
            refreshMachines();
        }
    }

    private void refreshMachines() {
        final ArrayList<String> names = MachineController.getInstance().getStoredMachines();
        final String running = MachineController.getInstance().isRunning()
                ? MachineController.getInstance().getMachineName() : null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<MachineGridAdapter.Item> items = new ArrayList<>();
                for (String name : names) {
                    Machine machine = MachineController.getInstance().getMachineConfig(name);
                    String arch = machine != null ? machine.getArch() : null;
                    if (arch == null || arch.isEmpty()) {
                        arch = String.valueOf(LimboApplication.arch);
                    }
                    int memory = machine != null ? machine.getMemory() : 0;
                    int cores = machine != null ? machine.getCpuNum() : 0;
                    String meta = activity.getString(R.string.machine_meta,
                            UiUtils.formatMemory(memory), cores);
                    items.add(new MachineGridAdapter.Item(name, meta,
                            arch.toUpperCase(Locale.US),
                            UiPrefs.getMachineIcon(activity, name),
                            name.equals(running)));
                }
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        adapter.submit(items);
                        if (navGlass != null) {
                            navGlass.refresh();
                        }
                        if (tab == TAB_MACHINES) {
                            subtitle.setText(items.isEmpty()
                                    ? activity.getString(R.string.home_machines_sub)
                                    : activity.getString(R.string.machine_count_hint, items.size()));
                        }
                    }
                });
            }
        }, "limbo-machines").start();
    }

    private void refreshScreen() {
        final boolean running = MachineController.getInstance().isRunning();
        Machine machine = MachineController.getInstance().getMachine();
        final boolean paused = !running && machine != null && machine.getPaused() == 1;
        String name = machine != null ? machine.getName() : null;

        if (running) {
            screenState.setText(R.string.screen_running);
            screenState.setTextColor(ContextCompat.getColor(activity, R.color.limbo_success));
            screenDot.setBackgroundResource(R.drawable.bg_status_dot);
        } else if (paused) {
            screenState.setText(R.string.screen_paused);
            screenState.setTextColor(ContextCompat.getColor(activity, R.color.limbo_warning));
            screenDot.setBackgroundResource(R.drawable.bg_status_dot_paused);
        } else {
            screenState.setText(R.string.screen_not_running);
            screenState.setTextColor(ContextCompat.getColor(activity, R.color.limbo_text_dim));
            screenDot.setBackgroundResource(R.drawable.bg_status_dot_off);
        }

        if (name == null) {
            screenMachine.setText(R.string.screen_no_machine);
            screenMeta.setText("");
            screenRam.setText("—");
            screenCores.setText("—");
            screenDisplay.setText("—");
        } else {
            screenMachine.setText(name);
            int memory = machine != null ? machine.getMemory() : 0;
            int cores = machine != null ? machine.getCpuNum() : 0;
            String display = machine != null && machine.getEnableVNC() == 1 ? "VNC" : "SDL";
            screenMeta.setText(activity.getString(R.string.machine_meta,
                    UiUtils.formatMemory(memory), cores));
            screenRam.setText(UiUtils.formatMemory(memory));
            screenCores.setText(String.valueOf(cores));
            screenDisplay.setText(display);
        }

        boolean sdl = running && (machine == null || machine.getEnableVNC() != 1);
        screenEnter.setEnabled(sdl);
        screenEnter.setAlpha(sdl ? 1f : 0.5f);
        screenStop.setEnabled(running || paused);
        screenStop.setAlpha(running || paused ? 1f : 0.5f);
        if (screenStop instanceof android.widget.Button) {
            ((android.widget.Button) screenStop).setText(paused
                    ? R.string.screen_discard : R.string.screen_stop);
        }
    }

    private void openConfig(String machineName) {
        openConfig(machineName, false);
    }

    private void openConfig(String machineName, boolean start) {
        MachineController controller = MachineController.getInstance();
        if (controller.isRunning()
                && !machineName.equals(controller.getMachineName())) {
            Toast.makeText(activity, R.string.stop_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(activity, activity.getClass());
        intent.putExtra(LimboActivity.EXTRA_MACHINE, machineName);
        intent.putExtra(LimboActivity.EXTRA_START, start);
        activity.startActivity(intent);
    }

    private void showMachineMenu(MachineGridAdapter.Item item) {
        final String[] options = {
                activity.getString(R.string.change_icon),
                activity.getString(R.string.delete_vm)
        };
        new AlertDialog.Builder(activity)
                .setTitle(item.name)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pendingIconMachine = item.name;
                        pickImage(REQ_MACHINE_ICON);
                    } else {
                        confirmDelete(item.name);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void confirmDelete(final String name) {
        if (MachineController.getInstance().isRunning()
                && name.equals(MachineController.getInstance().getMachineName())) {
            Toast.makeText(activity, R.string.stop_first, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(activity)
                .setTitle(R.string.delete_vm)
                .setMessage(R.string.delete_vm_message)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    Machine machine = MachineController.getInstance().getMachineConfig(name);
                    if (machine != null) {
                        LimboApplication.getViewListener().onAction(MachineAction.DELETE_VM, machine);
                        delayedRefresh(700);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void promptNewMachine() {
        final EditText input = new EditText(activity);
        input.setHint(R.string.machine_name_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        int padding = UiUtils.dp(activity, 20);
        FrameLayout container = new FrameLayout(activity);
        container.setPadding(padding, UiUtils.dp(activity, 8), padding, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(activity)
                .setTitle(R.string.new_machine)
                .setView(container)
                .setPositiveButton(R.string.create, (dialog, which) -> {
                    String name = input.getText().toString().trim().replace("\"", "");
                    if (name.isEmpty()) {
                        Toast.makeText(activity, R.string.machine_name_hint, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    LimboApplication.getViewListener().onAction(MachineAction.CREATE_VM, name);
                    delayedRefresh(800);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showThemeDialog() {
        final String[] options = {
                activity.getString(R.string.theme_system),
                activity.getString(R.string.theme_light),
                activity.getString(R.string.theme_dark)
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_title_theme)
                .setSingleChoiceItems(options, UiPrefs.getThemeMode(activity), (dialog, which) -> {
                    UiPrefs.setThemeMode(activity, which);
                    UiPrefs.applyTheme(activity);
                    updateThemeValue();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showWallpaperDialog() {
        final boolean custom = UiPrefs.getWallpaper(activity) != null;
        final String[] options = custom
                ? new String[]{activity.getString(R.string.choose_image), activity.getString(R.string.reset_bg)}
                : new String[]{activity.getString(R.string.choose_image)};
        new AlertDialog.Builder(activity)
                .setTitle(R.string.dialog_title_bg)
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        pickImage(REQ_WALLPAPER);
                    } else {
                        UiPrefs.setWallpaper(activity, null);
                        if (background != null) {
                            background.setWallpaperUri(null);
                        }
                        updateWallpaperValue();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void pickImage(int requestCode) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        try {
            activity.startActivityForResult(intent, requestCode);
        } catch (Exception e) {
            Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
            fallback.setType("image/*");
            activity.startActivityForResult(fallback, requestCode);
        }
    }

    private void showAboutDialog() {
        String message = Config.APP_NAME + " " + LimboApplication.getLimboVersionString()
                + "\nQEMU " + LimboApplication.getQemuVersionString()
                + "\n" + activity.getString(R.string.app_name);
        new AlertDialog.Builder(activity)
                .setTitle(R.string.about)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private void updateThemeValue() {
        int mode = UiPrefs.getThemeMode(activity);
        themeValue.setText(mode == UiPrefs.THEME_LIGHT ? R.string.theme_light
                : mode == UiPrefs.THEME_DARK ? R.string.theme_dark
                : R.string.theme_system);
    }

    private void updateWallpaperValue() {
        wallpaperValue.setText(UiPrefs.getWallpaper(activity) == null
                ? R.string.default_bg : R.string.custom_bg);
    }

    private void updateAboutValue() {
        aboutValue.setText("Limbo " + LimboApplication.getLimboVersionString());
    }

    private void delayedRefresh(long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                refreshMachines();
            }
        }, delay);
    }

    // ===== MachineController listeners =====

    @Override
    public void onMachineStatusChanged(Machine machine, MachineController.MachineStatus status, Object o) {
        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                refreshMachines();
                refreshScreen();
            }
        });
    }

    @Override
    public void onEvent(Machine machine, MachineController.Event event, Object o) {
        if (event == MachineController.Event.MachineCreated) {
            delayedRefresh(300);
            final String created = o instanceof String ? (String) o : null;
            if (created != null) {
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        openConfig(created);
                    }
                }, 550);
            }
        } else if (event == MachineController.Event.MachineCreateFailed) {
            final String message = o instanceof Integer
                    ? activity.getString((Integer) o) : String.valueOf(o);
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(activity, message, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
