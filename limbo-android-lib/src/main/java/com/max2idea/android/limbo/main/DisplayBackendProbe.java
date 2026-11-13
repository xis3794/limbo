/*
 Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package com.max2idea.android.limbo.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.preference.PreferenceManager;
import android.util.Log;

import com.max2idea.android.limbo.files.FileUtils;

import java.io.File;

/**
 * Detects which optional display features the QEMU build shipped in this APK
 * actually supports, and flips {@link Config#enableGtk} /
 * {@link Config#enableVirgl} accordingly.
 *
 * <p>Hard coding either flag in Java would desynchronise them from the native
 * build: CI builds with and without the GTK4 stack and can build with or
 * without CONFIG_OPENGL, and a stale "true" makes the app offer a display
 * backend that QEMU then rejects at start up (the VM just dies).
 *
 * <p>GTK's GDK android backend is compiled into libgtk-4.so, which is what
 * QEMU's gtk display links against, so the presence of that library next to
 * libqemu-system-*.so is the honest signal. virglrenderer likewise ships as
 * libvirglrenderer.so and is what -Dopengl=virtio-gpu-gl needs.
 *
 * <p>This runs once, from LimboApplication.onCreate(), before any spinner is
 * populated.
 */
public final class DisplayBackendProbe {
    private static final String TAG = "DisplayBackendProbe";

    private static final String GTK_LIB = "libgtk-4.so";
    private static final String EPOXY_LIB = "libepoxy.so";
    private static final String VIRGL_LIB = "libvirglrenderer.so";

    /** Preference keys; also read by AppSettingsActivity's switches. */
    static final String PREF_GTK = "useGtkBackend";
    static final String PREF_VIRGL = "useGpuAcceleration";

    private DisplayBackendProbe() {
    }

    /** Resolves both engine capability flags; safe to call more than once. */
    public static void refresh(Context context) {
        if (context == null) {
            return;
        }
        boolean gtk = libraryPresent(context, GTK_LIB);
        boolean virgl = libraryPresent(context, VIRGL_LIB) && libraryPresent(context, EPOXY_LIB);
        Config.engineHasGtk = gtk;
        Config.engineHasVirgl = virgl;

        // The switches live in the default preference file. A build that
        // cannot do the feature forces it off (there is nothing to offer);
        // otherwise the stored preference wins and defaults to enabled.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Config.enableGtk = gtk && prefs.getBoolean(PREF_GTK, true);
        Config.enableVirgl = virgl && prefs.getBoolean(PREF_VIRGL, true);
        // Scaling is read here as well so a VM start always sees the stored
        // value even when the settings screen was never opened.
        Config.GTKScaleMode = LimboSettingsManager.getGtkScaleMode(context);

        Log.d(TAG, "engine backends: gtk=" + gtk + " virgl=" + virgl
                + " -> enabled gtk=" + Config.enableGtk + " virgl=" + Config.enableVirgl
                + " gtkScaleMode=" + Config.GTKScaleMode);
    }

    /**
     * Whether {@code name} ships with this APK.
     *
     * <p>Checks the resolved native library directory first because that is
     * where the loader will look, then falls back to the APK's lib directory
     * entry list, which also covers builds where the library is not extracted
     * (compressed .so in the APK).
     */
    private static boolean libraryPresent(Context context, String name) {
        try {
            String dir = FileUtils.getNativeLibDir(context);
            if (dir != null && new File(dir, name).isFile()) {
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "nativeLibraryDir check failed for " + name + ": " + t);
        }
        try {
            ApplicationInfo info = context.getApplicationInfo();
            if (info.nativeLibraryDir != null
                    && new File(info.nativeLibraryDir, name).isFile()) {
                return true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "applicationInfo check failed for " + name + ": " + t);
        }
        // Last resort: read the APK's own zip entry list. A build with
        // android:extractNativeLibs="false" keeps the .so inside the APK and
        // leaves nativeLibraryDir empty, so both probes above come back empty
        // even though the library does ship. Without this fallback the GTK and
        // virgl flags would be silently forced off on such a build.
        try {
            String apk = context.getApplicationInfo().sourceDir;
            if (apk != null) {
                java.util.zip.ZipFile zip = new java.util.zip.ZipFile(apk);
                try {
                    java.util.zip.ZipEntry entry = zip.getEntry("lib/" + apkAbi() + "/" + name);
                    if (entry == null) {
                        // A split APK or an ABI folder we did not guess: accept
                        // any ABI that carries the library.
                        java.util.Enumeration<? extends java.util.zip.ZipEntry> all =
                                zip.entries();
                        while (all.hasMoreElements()) {
                            java.util.zip.ZipEntry e = all.nextElement();
                            String n = e.getName();
                            if (n.startsWith("lib/") && n.endsWith("/" + name)) {
                                return true;
                            }
                        }
                    } else {
                        return true;
                    }
                } finally {
                    zip.close();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "apk entry check failed for " + name + ": " + t);
        }
        return false;
    }

    /** The ABI folder this device would load from, e.g. {@code arm64-v8a}. */
    private static String apkAbi() {
        try {
            String[] abis = android.os.Build.SUPPORTED_ABIS;
            if (abis != null && abis.length > 0 && abis[0] != null) {
                return abis[0];
            }
        } catch (Throwable ignored) {
        }
        return android.os.Build.CPU_ABI;
    }
}
