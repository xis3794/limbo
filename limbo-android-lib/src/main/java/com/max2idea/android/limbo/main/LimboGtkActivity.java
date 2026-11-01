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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
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
