package com.max2idea.android.limbo.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.qemu.QemuVersionManager;
import com.max2idea.android.limbo.ui.AppBackgroundView;
import com.max2idea.android.limbo.ui.SteplessSlider;

/** Consistent, app-styled replacement for the old preference screen. */
public class AppSettingsActivity extends AppCompatActivity {

    private interface ValueUpdater {
        void update(String value);
    }

    private static class ValueRow {
        LinearLayout row;
        TextView value;
    }

    private SharedPreferences prefs;
    private LinearLayout container;
    private int mutedColor;
    private int textColor;
    private int accentColor;
    private TextView vncPasswordValue;
    private ValueRow qemuVersionRow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_app_settings);

        prefs = PreferenceManager.getDefaultSharedPreferences(this);
        mutedColor = ContextCompat.getColor(this, R.color.limbo_text_muted);
        textColor = ContextCompat.getColor(this, R.color.limbo_text);
        accentColor = ContextCompat.getColor(this, R.color.limbo_accent);

        Toolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.global_settings);
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

        AppBackgroundView background = findViewById(R.id.settings_background);
        if (background != null) {
            String wallpaper = UiPrefs.getWallpaper(this);
            background.setWallpaperUri(wallpaper == null ? null : Uri.parse(wallpaper));
        }

        container = findViewById(R.id.settings_container);
        buildUi();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (qemuVersionRow != null) {
            qemuVersionRow.value.setText(QemuVersionManager.getActive(this).displayName);
        }
    }

    private void buildUi() {
        final Resources res = getResources();

        // Files
        LinearLayout files = addSection(R.string.section_files);
        addSwitch(files, R.drawable.ic_folder,
                R.string.EnableLegacyFileManager, R.string.EnableLegacyFileManagerSummary,
                "EnableLegacyFileManager", false, null);

        // QEMU builds
        LinearLayout qemu = addSection(R.string.section_qemu);
        qemuVersionRow = addValue(qemu, R.drawable.ic_machine_box,
                R.string.qemu_versions,
                QemuVersionManager.getActive(this).displayName);
        qemuVersionRow.row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(AppSettingsActivity.this, QemuVersionsActivity.class));
            }
        });

        // Display
        LinearLayout display = addSection(R.string.section_display);
        final String[] orientationValues = res.getStringArray(R.array.orientationValues);
        final String[] orientationLabels = res.getStringArray(R.array.orientationLabels);
        final ValueRow orientationRow = addValue(display, R.drawable.ic_nav_screen,
                R.string.orientation, labelAt(orientationValues, orientationLabels,
                        prefs.getString("orientationPref", "0")));
        orientationRow.row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showListDialog(R.string.orientation, R.array.orientationLabels,
                        R.array.orientationValues, "orientationPref",
                        prefs.getString("orientationPref", "0"), new ValueUpdater() {
                            @Override
                            public void update(String value) {
                                orientationRow.value.setText(labelAt(orientationValues,
                                        orientationLabels, value));
                            }
                        });
            }
        });

        addSwitch(display, R.drawable.ic_tune,
                R.string.AlwaysToolbar, R.string.AlwaysShowToolbar,
                "AlwaysShowMenuToolbar", false, null);
        addSwitch(display, R.drawable.ic_tab_display,
                R.string.fullscreen, R.string.fullscreen_summary,
                "ShowFullscreen", true, null);
        if (Config.enableImmersiveMode) {
            addSwitch(display, R.drawable.ic_ui,
                    R.string.ImmersiveMode, R.string.EnableImmersiveMode,
                    "immersiveMode", false, null);
        }

        final String[] mapperValues = res.getStringArray(R.array.keyMapperValues);
        final String[] mapperLabels = res.getStringArray(R.array.keyMapperLabels);
        final ValueRow mapperRow = addValue(display, R.drawable.ic_ui,
                R.string.KeyMapperLayout, labelAt(mapperValues, mapperLabels,
                        prefs.getString("keyMapperSize", "3")));
        mapperRow.row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showListDialog(R.string.KeyMapperLayout, R.array.keyMapperLabels,
                        R.array.keyMapperValues, "keyMapperSize",
                        prefs.getString("keyMapperSize", "3"), new ValueUpdater() {
                            @Override
                            public void update(String value) {
                                mapperRow.value.setText(labelAt(mapperValues, mapperLabels, value));
                            }
                        });
            }
        });

        addSwitch(display, R.drawable.ic_machine,
                R.string.PreventMouseOutOfBounds, R.string.PreventMouseDescr,
                "preventMouseOutOfBounds", false, null);
        addSlider(display, R.drawable.ic_ui,
                R.string.keyPressDelay, R.string.keyPressDelaySummary, "keyPressDelay", 100);
        addSlider(display, R.drawable.ic_machine,
                R.string.mouseButtonDelay, R.string.mouseButtonDelaySummary, "mouseButtonDelay", 100);

        // Advanced
        LinearLayout advanced = addSection(R.string.section_advanced);
        addSwitch(advanced, R.drawable.ic_tune,
                R.string.HighPriority, R.string.RunVMThreadHighPriority,
                "HighPrio", false, null);
        addSwitch(advanced, R.drawable.ic_info,
                R.string.IgnoreBreakpointInvalidation, R.string.IgnoreBreakpointInvalidationSummary,
                "ignoreBreakpointInvalidation", false, null);

        // Network
        LinearLayout network = addSection(R.string.section_network);
        addSwitch(network, R.drawable.ic_tab_network,
                R.string.EnableQMPServer, R.string.EnableQMPServerSummary,
                "enableQMP", true, null);
        addSwitch(network, R.drawable.ic_tab_network,
                R.string.AllowExternalQMPConnections, R.string.AllowExternalQMPConnectionsSummary,
                "enableExternalQMP", false, null);
        addSwitch(network, R.drawable.ic_nav_settings,
                R.string.EnableVNCPassword, R.string.SetVNCPassword,
                "enableVNCPassword", false, new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        if (isChecked) {
                            promptVncPass(buttonView, null);
                        }
                    }
                });
        final ValueRow vncRow = addValue(network, R.drawable.ic_nav_settings,
                R.string.SetVNCPassword,
                LimboSettingsManager.getVNCPass(this).isEmpty() ? "—" : "••••••");
        vncPasswordValue = vncRow.value;
        vncRow.row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptVncPass(null, vncRow);
            }
        });
        addSwitch(network, R.drawable.ic_nav_screen,
                R.string.AllowExternalVNCConnections, R.string.AllowExternalVNCSummary,
                "enableExternalVNC", false, null);

        // Disk
        LinearLayout disk = addSection(R.string.section_disk);
        final ValueRow cacheRow = addValue(disk, R.drawable.ic_tab_storage,
                R.string.diskCache, LimboSettingsManager.getDiskCache(this));
        cacheRow.row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showListDialog(R.string.diskCache, R.array.diskCacheLabels,
                        R.array.diskCacheValues, "diskCachePref",
                        prefs.getString("diskCachePref", "default"), new ValueUpdater() {
                            @Override
                            public void update(String value) {
                                cacheRow.value.setText(value);
                            }
                        });
            }
        });

        // Audio
        if (Build.VERSION.SDK_INT >= 26) {
            LinearLayout audio = addSection(R.string.section_audio);
            addSwitch(audio, R.drawable.ic_audio,
                    R.string.EnableAaudio, R.string.EnableAaudioSummary,
                    "enableAaudio", false, null);
        }

        // Updates
        if (Config.enableSoftwareUpdates) {
            LinearLayout updates = addSection(R.string.section_updates);
            addSwitch(updates, R.drawable.ic_install,
                    R.string.CheckSoftwareUpdates, R.string.CheckSoftwareUpdatesSummary,
                    "updateVersionPrompt", Config.defaultCheckNewVersion, null);
        }
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
        label.setLayoutParams(labelParams);
        container.addView(label);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.bg_glass_card);
        card.setClipToOutline(true);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = dp(16);
        card.setLayoutParams(cardParams);
        container.addView(card);
        return card;
    }

    private LinearLayout addRow(LinearLayout card, int iconRes, CharSequence title, CharSequence summary) {
        if (card.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.limbo_stroke));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dp.leftMargin = dp(52);
            divider.setLayoutParams(dp);
            card.addView(divider);
        }
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(56));
        row.setPadding(dp(16), dp(9), dp(14), dp(9));
        row.setBackgroundResource(R.drawable.bg_row);

        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(mutedColor);
        row.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(14);
        texts.setLayoutParams(textParams);

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(titleView);

        if (summary != null && summary.length() > 0) {
            TextView summaryView = new TextView(this);
            summaryView.setText(summary);
            summaryView.setTextColor(mutedColor);
            summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            summaryView.setMaxLines(3);
            LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            summaryParams.topMargin = dp(1);
            summaryView.setLayoutParams(summaryParams);
            texts.addView(summaryView);
        }
        row.addView(texts);
        card.addView(row);
        return row;
    }

    private void addSwitch(LinearLayout card, int iconRes, int titleRes, int summaryRes,
                           final String key, boolean defaultValue,
                           CompoundButton.OnCheckedChangeListener extra) {
        LinearLayout row = addRow(card, iconRes, getString(titleRes), getString(summaryRes));
        SwitchMaterial toggle = new SwitchMaterial(this);
        toggle.setMinWidth(0);
        toggle.setMinHeight(0);
        row.addView(toggle, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        toggle.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                prefs.edit().putBoolean(key, isChecked).apply();
                if (extra != null) {
                    extra.onCheckedChanged(buttonView, isChecked);
                }
            }
        });
    }

    private ValueRow addValue(LinearLayout card, int iconRes, int titleRes, String value) {
        LinearLayout row = addRow(card, iconRes, getString(titleRes), null);
        TextView valueView = new TextView(this);
        valueView.setText(value);
        valueView.setTextColor(accentColor);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        valueView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.rightMargin = dp(6);
        row.addView(valueView, params);

        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(mutedColor);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(20), dp(20)));

        ValueRow holder = new ValueRow();
        holder.row = row;
        holder.value = valueView;
        return holder;
    }

    private void addSlider(LinearLayout card, int iconRes, int titleRes, int summaryRes,
                           final String key, int defaultValue) {
        if (card.getChildCount() > 0) {
            View divider = new View(this);
            divider.setBackgroundColor(ContextCompat.getColor(this, R.color.limbo_stroke));
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            dp.leftMargin = dp(52);
            divider.setLayoutParams(dp);
            card.addView(divider);
        }
        LinearLayout block = new LinearLayout(this);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(16), dp(12), dp(16), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(iconRes);
        icon.setColorFilter(mutedColor);
        header.addView(icon, new LinearLayout.LayoutParams(dp(22), dp(22)));

        LinearLayout texts = new LinearLayout(this);
        texts.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = dp(14);
        texts.setLayoutParams(textParams);
        TextView titleView = new TextView(this);
        titleView.setText(titleRes);
        titleView.setTextColor(textColor);
        titleView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        titleView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        texts.addView(titleView);
        TextView summaryView = new TextView(this);
        summaryView.setText(summaryRes);
        summaryView.setTextColor(mutedColor);
        summaryView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        summaryView.setMaxLines(3);
        texts.addView(summaryView);
        header.addView(texts);

        final TextView valueView = new TextView(this);
        valueView.setTextColor(accentColor);
        valueView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        valueView.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        valueView.setBackgroundResource(R.drawable.bg_value_chip);
        valueView.setPadding(dp(9), dp(3), dp(9), dp(3));
        header.addView(valueView);
        block.addView(header);

        final SteplessSlider slider = new SteplessSlider(this);
        LinearLayout.LayoutParams sliderParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(44));
        sliderParams.topMargin = dp(4);
        slider.setLayoutParams(sliderParams);
        slider.setRange(20, 100, 10);
        int initial = 100;
        try {
            initial = Integer.parseInt(prefs.getString(key, String.valueOf(defaultValue)));
        } catch (Exception ignored) {
        }
        initial = Math.max(20, Math.min(100, initial));
        slider.setValue(initial);
        valueView.setText(initial + " ms");
        slider.setOnValueChangedListener(new SteplessSlider.OnValueChangedListener() {
            @Override
            public void onValueChanged(int value, boolean fromUser) {
                valueView.setText(value + " ms");
            }

            @Override
            public void onValueCommitted(int value) {
                prefs.edit().putString(key, String.valueOf(value)).apply();
            }
        });
        block.addView(slider);
        card.addView(block);
    }

    private void showListDialog(int titleRes, int labelsRes, int valuesRes,
                                final String key, String current, final ValueUpdater updater) {
        final String[] labels = getResources().getStringArray(labelsRes);
        final String[] values = getResources().getStringArray(valuesRes);
        int index = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                index = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, index, new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        prefs.edit().putString(key, values[which]).apply();
                        if (updater != null) {
                            updater.update(values[which]);
                        }
                        dialog.dismiss();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private String labelAt(String[] values, String[] labels, String current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                return labels[i];
            }
        }
        return labels.length > 0 ? labels[0] : "";
    }

    private void promptVncPass(final CompoundButton enableSwitch, final ValueRow row) {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD
                | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setHint(R.string.Password);
        input.setSingleLine(true);
        int padding = dp(20);
        FrameLayout wrapper = new FrameLayout(this);
        wrapper.setPadding(padding, dp(8), padding, 0);
        wrapper.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this)
                .setTitle(R.string.SetVNCPassword)
                .setView(wrapper)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String pass = input.getText().toString().trim();
                    if (pass.isEmpty()) {
                        Toast.makeText(this, R.string.passwordCannotBeEmpty, Toast.LENGTH_SHORT).show();
                        if (enableSwitch != null) {
                            enableSwitch.setChecked(false);
                        }
                    } else {
                        LimboSettingsManager.setVNCPass(this, pass);
                        if (vncPasswordValue != null) {
                            vncPasswordValue.setText("••••••");
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    if (enableSwitch != null) {
                        enableSwitch.setChecked(false);
                    }
                })
                .show();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
