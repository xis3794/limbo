package com.max2idea.android.limbo.main;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.qemu.QemuVersion;
import com.max2idea.android.limbo.qemu.QemuVersionManager;
import com.max2idea.android.limbo.ui.AppBackgroundView;
import com.max2idea.android.limbo.ui.UiDialogs;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Settings screen for installing and switching downloadable QEMU builds. */
public class QemuVersionsActivity extends AppCompatActivity {
    private static final int REQ_PICK_ARCHIVE = 9201;

    private LinearLayout container;
    private int mutedColor;
    private int textColor;
    private int accentColor;
    private int dangerColor;
    private boolean catalogLoading;
    private String catalogError;
    private List<QemuVersionManager.CatalogEntry> catalog = new ArrayList<>();
    private android.app.AlertDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qemu_versions);

        mutedColor = ContextCompat.getColor(this, R.color.limbo_text_muted);
        textColor = ContextCompat.getColor(this, R.color.limbo_text);
        accentColor = ContextCompat.getColor(this, R.color.limbo_accent);
        dangerColor = ContextCompat.getColor(this, R.color.limbo_danger);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.qemu_versions);
        toolbar.setNavigationIcon(R.drawable.ic_back);
        if (toolbar.getNavigationIcon() != null) {
            toolbar.getNavigationIcon().setTint(ContextCompat.getColor(this, R.color.limbo_text));
        }
        toolbar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        AppBackgroundView background = findViewById(R.id.qemu_background);
        if (background != null) {
            String wallpaper = UiPrefs.getWallpaper(this);
            background.setWallpaperUri(wallpaper == null ? null : Uri.parse(wallpaper));
        }

        container = findViewById(R.id.qemu_container);
        buildUi();
        loadCatalog();
    }

    private void buildUi() {
        container.removeAllViews();
        QemuVersion active = QemuVersionManager.getActive(this);

        // Active version card
        LinearLayout header = addSection(R.string.qemu_active_version);
        LinearLayout headCard = new LinearLayout(this);
        headCard.setOrientation(LinearLayout.VERTICAL);
        headCard.setBackgroundResource(R.drawable.bg_glass_card);
        headCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        container.addView(headCard);
        TextView activeName = new TextView(this);
        activeName.setText(active.displayName);
        activeName.setTextColor(textColor);
        activeName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        activeName.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        headCard.addView(activeName);
        TextView activeInfo = new TextView(this);
        activeInfo.setText(active.version + " · " + getString(active.builtin
                ? R.string.qemu_builtin : R.string.qemu_installed_badge)
                + (active.isBuiltin() ? "" : " · " + humanSize(QemuVersionManager.directorySize(active.dir))));
        activeInfo.setTextColor(mutedColor);
        activeInfo.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        infoParams.topMargin = dp(4);
        activeInfo.setLayoutParams(infoParams);
        headCard.addView(activeInfo);
        TextView note = new TextView(this);
        note.setText(R.string.qemu_restart_note);
        note.setTextColor(mutedColor);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        noteParams.topMargin = dp(10);
        note.setLayoutParams(noteParams);
        headCard.addView(note);

        // Installed versions
        addSection(R.string.qemu_installed);
        for (QemuVersion version : QemuVersionManager.getInstalled(this)) {
            addInstalledRow(version, active);
        }

        // Local import
        Button install = new Button(this);
        install.setText(R.string.qemu_install_from_file);
        install.setAllCaps(false);
        install.setTextColor(ContextCompat.getColor(this, R.color.limbo_text));
        install.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        install.setBackgroundResource(R.drawable.bg_btn_secondary);
        install.setStateListAnimator(null);
        install.setMinHeight(0);
        install.setMinWidth(0);
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        installParams.topMargin = dp(2);
        installParams.bottomMargin = dp(16);
        install.setLayoutParams(installParams);
        install.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pickArchive();
            }
        });
        container.addView(install);

        // Downloadable catalog
        addSection(R.string.qemu_available);
        if (catalogLoading) {
            addInfoRow(getString(R.string.qemu_catalog_loading), null);
        } else if (catalogError != null) {
            addInfoRow(getString(R.string.qemu_catalog_failed, catalogError), getString(R.string.qemu_retry),
                    new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            loadCatalog();
                        }
                    });
        } else if (catalog.isEmpty()) {
            addInfoRow(getString(R.string.qemu_no_packages), null);
        } else {
            for (QemuVersionManager.CatalogEntry entry : catalog) {
                addCatalogRow(entry, active);
            }
        }
    }

    private void addInstalledRow(final QemuVersion version, final QemuVersion active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        card.setPadding(dp(18), dp(14), dp(18), dp(12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        TextView name = new TextView(this);
        name.setText(version.displayName);
        name.setTextColor(textColor);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(name);

        TextView summary = new TextView(this);
        summary.setText(version.version + " · "
                + getString(version.builtin ? R.string.qemu_builtin : R.string.qemu_installed_badge)
                + (version.dir != null ? " · " + humanSize(QemuVersionManager.directorySize(version.dir)) : ""));
        summary.setTextColor(mutedColor);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(3);
        summary.setLayoutParams(summaryParams);
        card.addView(summary);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(8);
        actions.setLayoutParams(actionParams);

        if (version.id.equals(active.id)) {
            TextView activeChip = chip(getString(R.string.qemu_active));
            actions.addView(activeChip);
        } else {
            TextView use = textButton(getString(R.string.qemu_use), accentColor);
            use.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmSwitch(version);
                }
            });
            actions.addView(use);
        }
        if (!version.builtin) {
            TextView delete = textButton(getString(R.string.qemu_delete), dangerColor);
            delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    confirmDelete(version);
                }
            });
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            deleteParams.leftMargin = dp(14);
            actions.addView(delete, deleteParams);
        }
        card.addView(actions);
        container.addView(card);
    }

    private void addCatalogRow(final QemuVersionManager.CatalogEntry entry, QemuVersion active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        card.setPadding(dp(18), dp(14), dp(18), dp(12));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);

        TextView name = new TextView(this);
        name.setText(entry.displayName);
        name.setTextColor(textColor);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(name);

        TextView summary = new TextView(this);
        summary.setText(entry.version + (entry.size > 0 ? " · " + humanSize(entry.size) : ""));
        summary.setTextColor(mutedColor);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        summaryParams.topMargin = dp(3);
        summary.setLayoutParams(summaryParams);
        card.addView(summary);

        if (entry.description != null && entry.description.length() > 0) {
            TextView desc = new TextView(this);
            desc.setText(entry.description);
            desc.setTextColor(mutedColor);
            desc.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = dp(4);
            desc.setLayoutParams(descParams);
            card.addView(desc);
        }

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = dp(8);
        actions.setLayoutParams(actionParams);

        QemuVersion installed = findInstalled(entry);
        if (installed != null) {
            actions.addView(chip(getString(R.string.qemu_installed_badge)));
        } else if (!entry.published) {
            TextView notPublished = new TextView(this);
            notPublished.setText(R.string.qemu_not_published);
            notPublished.setTextColor(mutedColor);
            notPublished.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            actions.addView(notPublished);
        } else {
            TextView download = textButton(getString(R.string.qemu_download), accentColor);
            download.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    downloadAndInstall(entry);
                }
            });
            actions.addView(download);
        }
        card.addView(actions);
        container.addView(card);
    }

    private QemuVersion findInstalled(QemuVersionManager.CatalogEntry entry) {
        for (QemuVersion v : QemuVersionManager.getInstalled(this)) {
            if (v.id.equals(entry.id)) {
                return v;
            }
        }
        return null;
    }

    private void confirmSwitch(final QemuVersion version) {
        if (MachineController.getInstance().isRunning()) {
            Toast.makeText(this, R.string.qemu_stop_first, Toast.LENGTH_SHORT).show();
            return;
        }
        UiDialogs.prompt(this,
                getString(R.string.qemu_versions),
                getString(R.string.qemu_switch_confirm, version.displayName),
                getString(R.string.cancel),
                getString(R.string.qemu_use),
                new Runnable() {
                    @Override
                    public void run() {
                        QemuVersionManager.setActive(QemuVersionsActivity.this, version.id);
                        restartApp();
                    }
                });
    }

    private void confirmDelete(final QemuVersion version) {
        UiDialogs.prompt(this,
                getString(R.string.qemu_delete),
                getString(R.string.qemu_delete_confirm),
                getString(R.string.cancel),
                getString(R.string.qemu_delete),
                new Runnable() {
                    @Override
                    public void run() {
                        QemuVersionManager.delete(QemuVersionsActivity.this, version.id);
                        buildUi();
                    }
                });
    }

    private void pickArchive() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES,
                new String[]{"application/x-xz", "application/octet-stream"});
        try {
            startActivityForResult(intent, REQ_PICK_ARCHIVE);
        } catch (Exception e) {
            Toast.makeText(this, R.string.qemu_install_failed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_PICK_ARCHIVE && resultCode == RESULT_OK
                && data != null && data.getData() != null) {
            importArchive(data.getData());
        }
    }

    private void importArchive(final Uri uri) {
        final android.app.AlertDialog dialog = showProgress(R.string.qemu_installing);
        new Thread(new Runnable() {
            @Override
            public void run() {
                File cache = new File(getCacheDir(), "qemu-import.tar.xz");
                String error = null;
                try (InputStream in = getContentResolver().openInputStream(uri);
                     OutputStream out = new FileOutputStream(cache)) {
                    if (in == null) {
                        throw new Exception("无法读取文件");
                    }
                    byte[] buffer = new byte[64 * 1024];
                    int n;
                    while ((n = in.read(buffer)) > 0) {
                        out.write(buffer, 0, n);
                    }
                    QemuVersionManager.install(QemuVersionsActivity.this, cache, null);
                } catch (Exception e) {
                    error = e.getMessage() != null ? e.getMessage() : e.toString();
                } finally {
                    cache.delete();
                }
                final String failure = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (failure == null) {
                            Toast.makeText(QemuVersionsActivity.this,
                                    R.string.qemu_install_done, Toast.LENGTH_SHORT).show();
                            buildUi();
                        } else {
                            showError(failure);
                        }
                    }
                });
            }
        }, "QemuImport").start();
    }

    private void downloadAndInstall(final QemuVersionManager.CatalogEntry entry) {
        final android.app.AlertDialog dialog = showProgress(R.string.qemu_downloading);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                String name = entry.displayName;
                try {
                    File archive = QemuVersionManager.download(QemuVersionsActivity.this, entry, null);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            dialog.setMessage(getString(R.string.qemu_installing));
                        }
                    });
                    QemuVersion installed = QemuVersionManager.install(
                            QemuVersionsActivity.this, archive, null);
                    name = installed.displayName;
                    archive.delete();
                } catch (Exception e) {
                    error = e.getMessage() != null ? e.getMessage() : e.toString();
                }
                final String failure = error;
                final String installedName = name;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        dialog.dismiss();
                        if (failure == null) {
                            Toast.makeText(QemuVersionsActivity.this,
                                    getString(R.string.qemu_install_done, installedName),
                                    Toast.LENGTH_SHORT).show();
                            buildUi();
                        } else {
                            showError(failure);
                        }
                    }
                });
            }
        }, "QemuDownload").start();
    }

    private void loadCatalog() {
        if (catalogLoading) {
            return;
        }
        catalogLoading = true;
        catalogError = null;
        buildUi();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                List<QemuVersionManager.CatalogEntry> entries = new ArrayList<>();
                try {
                    entries = QemuVersionManager.fetchCatalog();
                } catch (Exception e) {
                    error = e.getMessage() != null ? e.getMessage() : e.toString();
                }
                final String failure = error;
                final List<QemuVersionManager.CatalogEntry> result = entries;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        catalogLoading = false;
                        catalogError = failure;
                        catalog = result;
                        buildUi();
                    }
                });
            }
        }, "QemuCatalog").start();
    }

    private android.app.AlertDialog showProgress(int messageRes) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.HORIZONTAL);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setPadding(dp(22), dp(18), dp(22), dp(18));
        ProgressBar bar = new ProgressBar(this);
        bar.setIndeterminate(true);
        if (Build.VERSION.SDK_INT >= 21) {
            bar.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(accentColor));
        }
        box.addView(bar, new LinearLayout.LayoutParams(dp(22), dp(22)));
        TextView text = new TextView(this);
        text.setText(messageRes);
        text.setTextColor(textColor);
        text.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = dp(16);
        box.addView(text, textParams);
        progressDialog = new android.app.AlertDialog.Builder(this)
                .setView(box)
                .setCancelable(false)
                .show();
        return progressDialog;
    }

    private void showError(String message) {
        UiDialogs.prompt(this,
                getString(R.string.qemu_versions),
                getString(R.string.qemu_install_failed, message),
                getString(R.string.cancel),
                getString(R.string.qemu_retry),
                new Runnable() {
                    @Override
                    public void run() {
                        loadCatalog();
                    }
                });
    }

    private void addInfoRow(String text, String action) {
        addInfoRow(text, action, null);
    }

    private void addInfoRow(String text, String action, View.OnClickListener listener) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(10);
        card.setLayoutParams(cardParams);
        TextView message = new TextView(this);
        message.setText(text);
        message.setTextColor(mutedColor);
        message.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        message.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        card.addView(message);
        if (action != null) {
            TextView button = textButton(action, accentColor);
            if (listener != null) {
                button.setOnClickListener(listener);
            }
            card.addView(button);
        }
        container.addView(card);
    }

    private LinearLayout addSection(int titleRes) {
        TextView label = new TextView(this);
        label.setText(titleRes);
        label.setTextColor(mutedColor);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = dp(6);
        labelParams.topMargin = dp(14);
        labelParams.bottomMargin = dp(8);
        container.addView(label, labelParams);
        return container;
    }

    private TextView chip(String text) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(accentColor);
        chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        chip.setBackgroundResource(R.drawable.bg_chip_accent);
        chip.setPadding(dp(12), dp(5), dp(12), dp(5));
        return chip;
    }

    private TextView textButton(String text, int color) {
        TextView button = new TextView(this);
        button.setText(text);
        button.setTextColor(color);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setPadding(dp(4), dp(6), dp(4), dp(6));
        button.setOnClickListener(null);
        return button;
    }

    private void restartApp() {
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
                int flags = PendingIntent.FLAG_CANCEL_CURRENT;
                if (Build.VERSION.SDK_INT >= 23) {
                    flags |= PendingIntent.FLAG_IMMUTABLE;
                }
                PendingIntent pending = PendingIntent.getActivity(this, 0, launch, flags);
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                if (am != null) {
                    am.set(AlarmManager.ELAPSED_REALTIME,
                            SystemClock.elapsedRealtime() + 700, pending);
                }
            }
        } catch (Exception ignored) {
        }
        finishAffinity();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                android.os.Process.killProcess(android.os.Process.myPid());
            }
        }, 120);
    }

    private String humanSize(long bytes) {
        if (bytes <= 0) {
            return "—";
        }
        if (bytes < 1024 * 1024) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024f);
        }
        return String.format(Locale.US, "%.1f MB", bytes / (1024f * 1024f));
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
