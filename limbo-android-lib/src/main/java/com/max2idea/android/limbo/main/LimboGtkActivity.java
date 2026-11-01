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

import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import com.max2idea.android.limbo.machine.MachineAction;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.toast.ToastUtils;

import org.gtk.android.ToplevelActivity;

/**
 * Host activity for the GTK display backend.
 *
 * <p>GTK draws into a surface owned by an {@link ToplevelActivity} subclass:
 * the GDK android backend hands an identifier to the activity, the activity
 * attaches a {@code SurfaceView} for it and forwards the resulting touch and
 * key events back into GTK. QEMU's {@code -display gtk} then renders into that
 * window.
 *
 * <p>Only started for machines whose backend is GTK (see
 * {@code LimboActivity#startSDL()}) and only when the engine in use and the
 * device can actually run GTK ({@link Config#isGtkUsable()}).
 */
public class LimboGtkActivity extends ToplevelActivity {
    private static final String TAG = "LimboGtkActivity";

    /**
     * Set when this activity is opened only to issue a shutdown request for an
     * already running VM (the HomeController "stop immediately" path). The SDL
     * host carries the equivalent intent in
     * {@code LimboSDLActivity.pendingStopImmediate}; GTK needs its own copy.
     */
    private boolean stopRequestedFromHome;

    /** Called from the home dashboard before launching this host to stop a VM. */
    private static boolean sPendingStopFromHome;

    /** Arms a shutdown for the next {@link #onCreate} of this host. */
    public static void requestStopOnNextOpen() {
        sPendingStopFromHome = true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        stopRequestedFromHome = sPendingStopFromHome;
        sPendingStopFromHome = false;
        // The GDK android backend keeps the last activity it was initialised
        // with. When a VM is started a second time in the same process, a
        // finished activity would still be cached and GDK would spawn a stock
        // ToplevelActivity instead of claiming this one, so rebind every time.
        LimboGtk.init(this);

        super.onCreate(savedInstanceState);

        Log.d(TAG, "GTK host activity created");

        // The VM is already running (or paused): this activity only hosts the
        // window, exactly like LimboSDLActivity does for SDL.
        if (MachineController.getInstance().isRunning()) {
            notifyAction(MachineAction.UPDATE_NOTIFICATION,
                    getString(com.limbo.emu.lib.R.string.VMRunning));
            // Stop requested from the home dashboard while a GTK window was in
            // the background: QEMU's main loop can be blocked by a suspended
            // display thread, so the shutdown request issued earlier may never
            // be processed. Now that the window is back in the foreground, ask
            // again from here. (SDL carries the same intent in the static
            // LimboSDLActivity.pendingStopImmediate, which this host never
            // reads, so it must not rely on that flag.)
            if (stopRequestedFromHome) {
                stopRequestedFromHome = false;
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                        new Runnable() {
                            @Override
                            public void run() {
                                stopVmFromHome();
                            }
                        }, 1200);
            }
            // If the machine is paused, GTK's window still comes up; the guest is
            // resumed by the regular continue path, not from here.
            return;
        }

        // Nothing is running, so this activity must start the VM - the SDL host
        // does the same from runSDLMain() and without it "GTK" only produced an
        // empty window while the guest never booted.
        //
        // The shutdown guard has to be honoured for the same reason it exists in
        // SDL: QEMU is a separate liblimbo.so process, so Android recreates this
        // activity after every run. Without the guard, shutting the guest down
        // would immediately reboot it.
        if (LimboSettingsManager.consumeVmShutdownByUser(this)) {
            Log.d(TAG, "VM was shut down by the user, not starting it again");
            finish();
            return;
        }

        // The start is dispatched off the main thread: onAction() reaches
        // MachineService and the QEMU thread, which must not be created on the
        // UI thread. Starting it also records EXIT_UNKNOWN so a native crash is
        // reported on the next launch (same as SDL/VNC).
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.i(TAG, "Starting VM with GTK display");
                    notifyAction(MachineAction.START_VM, null);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to start VM: " + t);
                }
            }
        }, "limbo-gtk-start").start();
    }

    private void notifyAction(MachineAction action, Object value) {
        ViewListener listener = LimboApplication.getViewListener();
        if (listener != null) {
            listener.onAction(action, value);
        }
    }

    /**
     * Shutdown requested from the home dashboard. Mirrors
     * {@code LimboSDLActivity#stopVmFromHome}: QEMU only posts a shutdown
     * request, so it is safe to call from the UI thread, and a force-close
     * prompt follows if the guest does not react.
     */
    private void stopVmFromHome() {
        if (!MachineController.getInstance().isRunning()) {
            return;
        }
        ToastUtils.toastShort(this, getString(com.limbo.emu.lib.R.string.screen_stopping));
        MachineController.getInstance().requestStop();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        if (MachineController.getInstance().isRunning()) {
                            Log.w(TAG, "Guest did not stop after the shutdown request");
                        }
                    }
                }, 12000);
    }

    @Override
    protected void onDestroy() {
        Log.d(TAG, "GTK host activity destroyed");
        super.onDestroy();
    }

    /** Whether this device can run the GTK backend at all. */
    public static boolean isUsable() {
        return Config.isGtkUsable() && Build.VERSION.SDK_INT >= 31;
    }
}
