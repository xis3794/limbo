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
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;

/**
 * Initialises the GTK4 "android" GDK backend so QEMU can use {@code -display gtk}.
 *
 * <p>GDK's android backend has to be told the JNI environment, the application
 * class loader and the host activity before QEMU's gtk display can open a
 * window. {@code libgtk-4.so} only exposes that entry point as a C function
 * ({@code gdk_android_initialize}), and it registers its JNI natives against
 * classes in {@code org.gtk.android}, so this bridge calls into a tiny native
 * shim that dlopen()s GTK and invokes it.
 *
 * <p>GTK is single threaded: {@code gtk_init()} and the main loop must happen
 * on the thread QEMU runs its UI on, so nothing here initialises GTK itself.
 * It only teaches GDK which activity owns the window; QEMU's
 * {@code ui/gtk.c} does the rest on its own thread.
 */
public final class LimboGtk {
    private static final String TAG = "LimboGtk";

    private static boolean sNativeAvailable;

    static {
        boolean available = false;
        try {
            // liblimbo.so carries the small shim; it dlopen()s libgtk-4.so at
            // runtime so a build without the GTK stack still loads fine.
            System.loadLibrary("limbo");
            available = true;
        } catch (Throwable t) {
            Log.w(TAG, "GTK bridge library unavailable: " + t);
        }
        sNativeAvailable = available;
    }

    private LimboGtk() {
    }

    private static native boolean nativeHasInit();

    private static native boolean nativeInit(Object classLoader, Object activity);

    private static native void nativeSetEnv(String key, String value);

    /**
     * Whether this build can actually bind the GDK android backend. Checks the
     * native shim once; a build without GTK reports false so the caller can
     * fall back to SDL instead of failing at VM start.
     */
    public static boolean isAvailable() {
        if (!sNativeAvailable) {
            return false;
        }
        try {
            return nativeHasInit();
        } catch (Throwable t) {
            Log.w(TAG, "nativeHasInit() failed: " + t);
            sNativeAvailable = false;
            return false;
        }
    }

    /**
     * Binds the GDK android backend to {@code context}.
     *
     * <p>Must run on the Android main thread, before the VM starts.
     *
     * @return true when GDK accepted the binding
     */
    public static synchronized boolean init(Context context) {
        if (context == null) {
            return false;
        }
        if (!sNativeAvailable) {
            Log.w(TAG, "GTK backend is not part of this build");
            Config.gtkInitFailed = true;
            return false;
        }
        installBlackTheme(context);
        try {
            Object classLoader = context.getClassLoader();
            boolean ok = nativeInit(classLoader, context);
            if (!ok) {
                Log.e(TAG, "gdk_android_initialize() failed");
            }
            Config.gtkInitFailed = !ok;
            return ok;
        } catch (Throwable t) {
            Log.e(TAG, "GTK init crashed: " + t);
            Config.gtkInitFailed = true;
            return false;
        }
    }

    /**
     * GTK reads {@code $XDG_CONFIG_HOME/gtk-4.0/gtk.css} during gtk_init().
     * QEMU's GTK window otherwise uses Adwaita's white window background,
     * which flashes white around the guest image; paint it black instead.
     */
    private static void installBlackTheme(Context context) {
        try {
            File configDir = new File(context.getFilesDir(), "gtk-config");
            File cssDir = new File(configDir, "gtk-4.0");
            if (!cssDir.isDirectory() && !cssDir.mkdirs()) {
                Log.w(TAG, "Cannot create GTK config dir " + cssDir);
                return;
            }

            File css = new File(cssDir, "gtk.css");
            String cssText = "window, window.background, .background,\n"
                    + "notebook, notebook > stack, notebook > stack > box,\n"
                    + "drawingarea, glarea {\n"
                    + "  background-color: #000000;\n"
                    + "  color: #ffffff;\n"
                    + "}\n";
            byte[] bytes = cssText.getBytes("UTF-8");
            boolean write = !(css.isFile() && css.length() == bytes.length);
            if (write) {
                FileOutputStream out = new FileOutputStream(css);
                try {
                    out.write(bytes);
                } finally {
                    out.close();
                }
            }

            nativeSetEnv("XDG_CONFIG_HOME", configDir.getAbsolutePath());
        } catch (Throwable t) {
            Log.w(TAG, "Failed to install GTK black theme: " + t);
        }
    }
}
