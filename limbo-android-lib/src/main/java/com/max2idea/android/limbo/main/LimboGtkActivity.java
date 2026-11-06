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

import android.content.Intent;
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

    /** Whether {@link LimboGtk#init} bound GDK to this activity in this instance. */
    private boolean gdkReady;

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
        //
        // This has to happen before super.onCreate(): ToplevelActivity.onCreate()
        // calls into GDK. Its result decides whether GTK can be used at all:
        // without a successful gdk_android_initialize() the QEMU side has no
        // display to open, and gtk_init() calls exit(1) on Android, which takes
        // the whole application down (the "start hangs, screen goes black"
        // report). Failing over to SDL is the only safe outcome.
        boolean gdkReady = LimboGtk.init(this);
        // The field is what onBackPressed() reads: after a failed init the
        // activity delegates to the superclass so Android's own back handling
        // finishes it normally.
        this.gdkReady = gdkReady;
        // Reset on success as well: ToplevelActivity.onCreate() reads this flag
        // to decide whether its natives exist, and a previous failed attempt in
        // the same process must not poison a later one.
        ToplevelActivity.gdkUnavailable = !gdkReady;
        if (!gdkReady) {
            Log.e(TAG, "GDK android backend unavailable, falling back to the SDL display");
        }

        super.onCreate(savedInstanceState);

        if (!gdkReady) {
            startSdlFallback();
            return;
        }

        // Sustained performance mode asks the platform not to throttle the CPU
        // and GPU while a long-running foreground workload is on screen. The
        // SDL host requests it too (LimboSDLActivity#setupScreen); without it
        // Android drops clocks after a few minutes and the guest slows down
        // exactly the way it was reported ("the VM suddenly becomes very
        // slow"). GTK renders the whole guest framebuffer through the same
        // surface, so it needs the same hint.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            android.os.PowerManager pm = (android.os.PowerManager)
                    getApplicationContext().getSystemService(android.content.Context.POWER_SERVICE);
            if (pm != null && pm.isSustainedPerformanceModeSupported()) {
                getWindow().setSustainedPerformanceMode(true);
            }
        }

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
     * GDK could not be initialised, so a GTK window can never appear in this
     * process. Hand the job to the SDL host instead of starting a VM whose
     * {@code gtk_init()} would call {@code exit(1)} and kill the application.
     *
     * <p>{@link Config#gtkInitFailed} is already set, so
     * {@code Config.isGtkUsable()} is false and {@code VMExecutor} emits
     * {@code -display sdl} for this (and any later) machine.
     */
    private void startSdlFallback() {
        ToastUtils.toastLong(this, getString(com.limbo.emu.lib.R.string.gtk_init_failed_sdl));
        Intent intent = new Intent(this, LimboSDLActivity.class);
        // LimboActivity launched this host with startActivityForResult; pass the
        // reply target on so the SDL host's result still reaches it.
        if (getCallingActivity() != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        }
        startActivity(intent);
        finish();
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

    /**
     * Leave the GTK screen.
     *
     * <p>{@link ToplevelActivity#onBackPressed()} only forwards the press to GDK,
     * which turns it into a {@code close-request} on the GTK window. QEMU answers
     * that with {@code qmp_quit()}, so a back press would shut the guest down and
     * - when the option is missing - do nothing at all. Either way the user stays
     * stuck on the GTK screen, which is the reported "GTK cannot be exited".
     *
     * <p>The SDL host's back press does not stop the VM either, it just returns to
     * the application ({@code LimboSDLActivity#onBackPressed}). This mirrors that:
     * back finishes the display activity, the guest keeps running, and the running
     * machine can be re-entered from the dashboard status card.
     */
    @Override
    public void onBackPressed() {
        if (!gdkReady) {
            super.onBackPressed();
            return;
        }
        if (MachineController.getInstance().isRunning()) {
            ToastUtils.toastShort(this,
                    getString(com.limbo.emu.lib.R.string.gtk_back_leaves_running));
        }
        finish();
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
