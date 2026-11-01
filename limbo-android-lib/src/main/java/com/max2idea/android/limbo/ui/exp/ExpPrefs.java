package com.max2idea.android.limbo.ui.exp;

import android.content.Context;
import android.preference.PreferenceManager;

/** Preferences used only by the experimental machine list. */
public final class ExpPrefs {
    private static final String KEY_EXPERIMENTAL_UI = "experimentalUi";
    private static final String KEY_EXPERIMENTAL_GPU = "experimentalGpu";

    private ExpPrefs() {
    }

    public static boolean experimentalUi(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_EXPERIMENTAL_UI, false);
    }

    public static void setExperimentalUi(Context context, boolean enabled) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putBoolean(KEY_EXPERIMENTAL_UI, enabled)
                .apply();
    }

    public static boolean gpuHints(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(KEY_EXPERIMENTAL_GPU, false);
    }
}
