/*
Copyright (C) Max Kastanas 2012

 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 *
 */
package com.max2idea.android.limbo.machine;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.WifiLock;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.files.FileUtils;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboActivity;
import com.max2idea.android.limbo.main.LimboApplication;
import com.max2idea.android.limbo.main.LimboSettingsManager;
import com.max2idea.android.limbo.network.NetworkUtils;
import com.max2idea.android.limbo.toast.ToastUtils;

/** MachineService is responsible only for owning and starting the thread that executes the jni part
 * This implementation is needed for Android to make sure that the process is started from a
 * foreground service so it will remain alive but with its thread running in the background.
 * All other communications with the native process will happen via MachineController -> MachineExecutor.
  */
public class MachineService extends Service {
    public static final int notifID = 1000;
    private static final String TAG = "MachineService";
    private static MachineService service;
    public Thread limboThread;
    private NotificationCompat.Builder builder;
    private Notification mNotification;
    private WifiLock mWifiLock;
    private WakeLock mWakeLock;

    public static MachineService getService() {
        return service;
    }
    @Override
    public void onCreate() {
        Log.d(TAG, "Creating Service");
        service = this;
    }

    @Override
    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        final String action = intent.getAction();
        final Bundle b = intent.getExtras();

        if (limboThread != null)
            return START_NOT_STICKY;

        if (action.equals(Config.ACTION_START)) {
            if (MachineController.getInstance().getMachine() == null)
                return START_NOT_STICKY;

            String text = MachineController.getInstance().getMachine().getName() + ": VM Running";
            if (MachineController.getInstance().isVNCEnabled()) {
                text += " - " + service.getString(R.string.vncServer);
                text += ": " + NetworkUtils.getVNCAddress(service) + ":" + Config.defaultVNCPort;
            }
            // we need to start the service in the foreground
            setUpAsForeground(text);

            // start logging
            FileUtils.startLogging();

            // start the thread in the background
            ThreadGroup group = new ThreadGroup("threadGroup");
            //FIXME: For now we set an abnormally very high stack size to overcome an issue with the
            // SDL audio failing with StackOverflowError when resuming the vm.
            limboThread = new Thread(group, new Runnable() {
                public void run() {
                    startVM();
                }
            }, "LimboThread", Config.stackSize);
            if (LimboSettingsManager.getPrio(this))
                limboThread.setPriority(Thread.MAX_PRIORITY);
            limboThread.start();
        }

        // Don't restart if killed
        return START_NOT_STICKY;
    }

    private void startVM() {
        //XXX: wait till logging starts capturing
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Log.d(TAG, "Starting VM: " + MachineController.getInstance().getMachine().getName());
        // Dump full /proc/self/maps to logcat BEFORE loading native libs.
        // This gives us the exact base address of libc, libqemu, glib, SDL etc.
        // at the moment of the crash, so we can resolve backtrace PCs offline.
        // Do this in a normal thread (logcat is safe here, unlike in a signal handler).
        dumpMapsToLogcat();
        // QEMU uses libSDL2 (display + the sdl audio backend) and SDL needs its
        // Java side registered before any native SDL call. In VNC mode that
        // registration never happened (no SDL activity is started), which used
        // to kill the process silently as soon as QEMU touched SDL.
        LimboApplication.prepareSdlBridge();
        setupLocks();

        // notify we started
        MachineController.getInstance().onServiceStarted();

        //set the exit code before we start
        LimboSettingsManager.setExitCode(service, Config.EXIT_UNKNOWN);

        String res = MachineController.getInstance().start();

        // If the vm exits with a use requested shutdown
        // TODO: create int return codes instead of strings
        if (res != null) {
            if (!res.equals("VM shutdown")) {
                ToastUtils.toastLong(service, res);
                Log.e(TAG, res);
                // Also persist the QEMU error to a file: logcat gets rotated
                // away very quickly on this device.
                try {
                    java.io.FileOutputStream fos = new java.io.FileOutputStream(
                            "/sdcard/Download/limbo_vm_error.txt", true);
                    fos.write(("[" + new java.util.Date() + "] " + res + "\n").getBytes());
                    fos.close();
                } catch (Throwable ignore) {
                }
            } else {
                Log.d(TAG, res);
                //set the exit code
                LimboSettingsManager.setExitCode(service, Config.EXIT_SUCCESS);
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            ToastUtils.toastLong(service, res);
        }

        cleanUp();
        stopService();

        Log.d(TAG, "Exiting Limbo");
        // IMPORTANT: do NOT use System.exit(0).
        // System.exit() synchronously runs __cxa_finalize, i.e. the static
        // destructors of every loaded library. Those destructors call
        // pthread_mutex_destroy() on libhwui.so / libgrallocutils.so globals
        // while the UI & render threads of this process are still running, so
        // they immediately hit
        //   "FORTIFY: pthread_mutex_lock called on a destroyed mutex"
        // and the process aborts with SIGABRT.
        // SIGKILLing ourselves still unloads the native libs (which is why
        // this exit exists) but runs no destructors, so nothing races.
        android.os.Process.killProcess(android.os.Process.myPid());
    }

    public void cleanUp() {
        //XXX flush and close all file descriptors if we haven't already
        FileUtils.close_fds();
    }

    /** Dump /proc/self/maps to logcat (tag "LimboMaps"), split into ~3KB chunks.
     *  Called from a normal thread before loading QEMU, so logcat is safe.
     *  Gives us libc + app lib base addresses to resolve crash backtraces. */
    private void dumpMapsToLogcat() {
        try {
            java.io.BufferedReader br = new java.io.BufferedReader(
                    new java.io.FileReader("/proc/self/maps"));
            StringBuilder sb = new StringBuilder();
            String line;
            int chunk = 0;
            while ((line = br.readLine()) != null) {
                // Output ALL lines (not just .so r-xp) so we can find
                // anonymous mmap regions containing the crash mutex address
                sb.append(line).append('\n');
                if (sb.length() > 3000) {
                    Log.i("LimboMaps", "--- maps chunk " + chunk + " ---");
                    Log.i("LimboMaps", sb.toString());
                    sb.setLength(0);
                    chunk++;
                }
            }
            if (sb.length() > 0) {
                Log.i("LimboMaps", "--- maps chunk " + chunk + " ---");
                Log.i("LimboMaps", sb.toString());
            }
            br.close();
            Log.i("LimboMaps", "--- maps dump complete ---");
        } catch (Throwable t) {
            Log.e(TAG, "dumpMapsToLogcat failed: " + t);
        }
    }


    private void setUpAsForeground(String text) {
        if (MachineController.getInstance().getMachine() == null) {
            Log.w(TAG, "No Machine selected");
            return;
        }
        Intent intent = new Intent(service.getApplicationContext(), Config.clientClass);
        PendingIntent pi = PendingIntent.getActivity(service.getApplicationContext(), 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(Config.notificationChannelID, Config.notificationChannelName, NotificationManager.IMPORTANCE_NONE);
            NotificationManager notifService = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            notifService.createNotificationChannel(chan);
            builder = new NotificationCompat.Builder(service, Config.notificationChannelID);
        } else
            builder = new NotificationCompat.Builder(service, "");
        mNotification = builder.setContentIntent(pi).setContentTitle(getString(R.string.app_name)).setContentText(text)
                .setSmallIcon(R.drawable.limbo)
                .setLargeIcon(BitmapFactory.decodeResource(service.getResources(), R.drawable.limbo)).build();
        mNotification.tickerText = text;
        mNotification.flags |= Notification.FLAG_ONGOING_EVENT;
        service.startForeground(notifID, mNotification);
    }

    public void updateServiceNotification(String text) {
        if (builder != null) {
            builder.setContentText(text);
            mNotification = builder.build();
            NotificationManager mNotificationManager = (NotificationManager)
                    service.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(notifID, mNotification);
        }
    }

    private void setupLocks() {
        WifiManager wm = (WifiManager) service.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mWifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, Config.wifiLockTag);
        mWifiLock.setReferenceCounted(false);

        PowerManager pm = (PowerManager) service.getApplicationContext().getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Config.wakeLockTag);
        mWakeLock.setReferenceCounted(false);
    }

    public void onDestroy() {
        Log.d(TAG, "Service destroyed");
        super.onDestroy();
    }

    private void releaseLocks() {
        if (mWifiLock != null && mWifiLock.isHeld()) {
            Log.d(TAG, "Release Wifi lock...");
            mWifiLock.release();
        }

        if (mWakeLock != null && mWakeLock.isHeld()) {
            Log.d(TAG, "Release Wake lock...");
            mWakeLock.release();
        }
    }

    private void stopService() {
        releaseLocks();
        if (service != null) {
            service.stopForeground(true);
            service.stopSelf();
        }
    }

    public void onTaskRemoved(Intent intent) {
        LimboSettingsManager.setExitCode(this, Config.EXIT_SUCCESS);
    }

    public interface OnStatusChangedListener {
        void onStatusChanged(MachineService service, Machine machine, MachineController.MachineStatus status);
    }
}
