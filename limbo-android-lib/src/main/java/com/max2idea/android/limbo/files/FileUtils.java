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
package com.max2idea.android.limbo.files;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.webkit.MimeTypeMap;

import androidx.documentfile.provider.DocumentFile;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.dialog.DialogUtils;
import com.max2idea.android.limbo.machine.Machine.FileType;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboActivity;
import com.max2idea.android.limbo.main.LimboApplication;
import com.max2idea.android.limbo.main.LimboSettingsManager;
import com.max2idea.android.limbo.toast.ToastUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;

/**
 * @author dev
 */
public class FileUtils {
    private final static String TAG = "FileUtils";
    private static final Object fdsLock = new Object();
    private static HashMap<Integer, FileInfo> fds = new HashMap<Integer, FileInfo>();

    public static String getNativeLibDir(Context context) {
        return context.getApplicationInfo().nativeLibraryDir;
    }

    public static String getFullPathFromDocumentFilePath(String filePath) {

        filePath = filePath.replaceAll("%3A", "^3A");
        int index = filePath.lastIndexOf("^3A");
        if (index > 0)
            filePath = filePath.substring(index + 3);
        if (!filePath.startsWith("/"))
            filePath = "/" + filePath;

        //remove any spaces encoded by the ASF
        try {
            filePath = URLDecoder.decode(filePath, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        return filePath;
    }

    public static String getFilenameFromPath(String filePath) {
        filePath = filePath.replaceAll("%2F", "/");
        filePath = filePath.replaceAll("%3A", "/");
        filePath = filePath.replaceAll("\\^2F", "/");
        filePath = filePath.replaceAll("\\^3A", "/");


        int index = filePath.lastIndexOf("/");
        if (index > 0)
            return filePath.substring(index + 1);
        return filePath;
    }

    public static String decodeDocumentFilePath(String filePath) {
        if (filePath != null && filePath.startsWith("/content//")) {
            filePath = filePath.replace("/content//", "content://");
            filePath = filePath.replaceAll("\\^\\^\\^", "%");
        }
        return filePath;
    }

    /**
     * Encode the path % chars to ^
     *
     * @param filePath
     * @return
     */
    public static String encodeDocumentFilePath(String filePath) {
        if (filePath != null && filePath.startsWith("content://")) {
            filePath = filePath.replace("content://", "/content//");
            filePath = filePath.replaceAll("%", "\\^\\^\\^");
            ;

        }
        return filePath;
    }

    public static void saveFileContents(String filePath, String contents) {
        // TODO: we assume that the contents are of small size so we keep in an array
        byteArrayToFile(contents.getBytes(), new File(filePath));
    }

    public static void byteArrayToFile(byte[] byteData, File filePath) {

        try {
            FileOutputStream fos = new FileOutputStream(filePath);
            fos.write(byteData);
            fos.close();

        } catch (FileNotFoundException ex) {
            System.out.println("FileNotFoundException : " + ex);
        } catch (IOException ioe) {
            System.out.println("IOException : " + ioe);
        }

    }

    public static InputStream getStreamFromFilePath(String filePath) throws FileNotFoundException {
        if (filePath.startsWith("content://")) {
            Uri uri = Uri.parse(filePath);
            String mode = "rw";
            ParcelFileDescriptor pfd = LimboApplication.getInstance().getContentResolver().openFileDescriptor(uri, mode);
            return new FileInputStream(pfd.getFileDescriptor());
        } else {
            return new FileInputStream(filePath);
        }
    }

    public static void closeFileDescriptor(String filePath) throws IOException {
        if (filePath.startsWith("content://")) {
            Uri uri = Uri.parse(filePath);
            String mode = "rw";
            ParcelFileDescriptor pfd = LimboApplication.getInstance().getContentResolver().openFileDescriptor(uri, mode);
            pfd.close();
        }
    }

    public static String getFileContents(String filePath) {
        File file = new File(filePath);
        if (!file.exists())
            return "";
        StringBuilder builder = new StringBuilder("");
        try {
            FileInputStream stream = new FileInputStream(file);
            byte[] buff = new byte[32768];
            int bytesRead = 0;
            while ((bytesRead = stream.read(buff, 0, buff.length)) > 0) {
                //FIXME: log file can be too large appending might fail with out of memory
                builder.append(new String(buff, 0, bytesRead));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String contents = builder.toString();
        return contents;
    }

    public static boolean fileValid(String path) {
        if (path == null || path.equals(""))
            return true;
        if (path.startsWith("content://") || path.startsWith("/content/")) {
            int fd = get_fd(path);
            if (fd <= 0)
                return false;
        } else {
            File file = new File(path);
            file.setWritable(true);
            return file.exists();
        }
        return true;
    }

    //TODO: we should pass the modes from the backend and translate them
    // instead of blindly using "rw". ie ISOs should be read only.
    public static int get_fd(String path) {
        synchronized (fdsLock) {
            int fd = 0;
            if (path == null)
                return 0;
            if (path.startsWith("/content//") || path.startsWith("content://")) {
                String npath = decodeDocumentFilePath(path);
                try {
                    Uri uri = Uri.parse(npath);
                    String mode = "rw";
                    if (path.toLowerCase().endsWith(".iso"))
                        mode = "r";
                    ParcelFileDescriptor pfd = LimboApplication.getInstance().getContentResolver().openFileDescriptor(uri, mode);
                    fd = pfd.getFd();
                    fds.put(fd, new FileInfo(path, npath, pfd));
                    Log.d(TAG, "Opening Content Uri: " + npath + ", FD: " + fd);
                } catch (Exception e) {
                    String msg = LimboApplication.getInstance().getString(R.string.CouldNotOpenDocFile) + " "
                            + FileUtils.getFullPathFromDocumentFilePath(npath)
                            + "\n" + LimboApplication.getInstance().getString(R.string.PleaseReassingYourDiskFiles);
                    ToastUtils.toastLong(LimboApplication.getInstance(), msg);
                    e.printStackTrace();
                }
            } else {
                try {
                    int mode = ParcelFileDescriptor.MODE_READ_WRITE;
                    if (path.toLowerCase().endsWith(".iso"))
                        mode = ParcelFileDescriptor.MODE_READ_ONLY;
                    File file = new File(path);
                    if (!file.exists())
                        file.createNewFile();
                    ParcelFileDescriptor pfd = ParcelFileDescriptor.open(file, mode);
                    fd = pfd.getFd();
                    fds.put(fd, new FileInfo(path, path, pfd));
                    Log.d(TAG, "Opening File: " + path + ", FD: " + fd);
                } catch (Exception e) {
                    Log.e(TAG, "Could not open File: " + path + ", FD: " + fd);
                    if (Config.debug)
                        e.printStackTrace();
                }
            }
            return fd;
        }
    }

    public static void close_fds() {
        synchronized (fds) {
            Integer[] fds = FileUtils.fds.keySet().toArray(new Integer[FileUtils.fds.keySet().size()]);
            for (int i = 0; i < fds.length; i++) {
                FileUtils.close_fd(fds[i]);
            }
        }
    }

    /**
     * Closing File Descriptors
     *
     * @param fd File Descriptro to be closed
     * @return Returns 0 if fd is closed successfully otherwise -1
     */
    public static int close_fd(int fd) {
        if (!Config.closeFileDescriptors) {
            return 0;
        }
        synchronized (fds) {
            if (FileUtils.fds.containsKey(fd)) {
                FileInfo info = FileUtils.fds.get(fd);
                try {
                    ParcelFileDescriptor pfd = info.pfd;
                    if(Config.syncFilesOnClose) {
                        try {
                            pfd.getFileDescriptor().sync();
                        } catch (IOException e) {
                            if (Config.debug) {
                                Log.w(TAG, "Syncing DocumentFile: " + info.path + ": " + fd + " : " + e);
                                e.printStackTrace();
                            }
                        }
                    }
                    pfd.close();
                    FileUtils.fds.remove(fd);
                    return 0;
                } catch (IOException e) {
                    Log.e(TAG, "Error Closing DocumentFile: " + info.path + ": " + fd + " : " + e);
                    if (Config.debug)
                        e.printStackTrace();
                }
            } else {
                ParcelFileDescriptor pfd = null;

                try {
                    String path = "";
                    FileInfo info = FileUtils.fds.get(fd);
                    if (info != null) {
                        pfd = info.pfd;
                        path = info.path;
                    }
                    if (pfd == null)
                        pfd = ParcelFileDescriptor.fromFd(fd);
                    if(Config.syncFilesOnClose) {
                        try {
                            pfd.getFileDescriptor().sync();
                        } catch (IOException e) {
                            if (Config.debug) {
                                Log.w(TAG, "Error Syncing File: " + path + ": " + fd + " : " + e);
                                e.printStackTrace();
                            }
                        }
                    }
                    pfd.close();
                    return 0;
                } catch (Exception e) {
                    Log.e(TAG, "Error Closing File FD: " + fd + " : " + e);
                    if (Config.debug)
                        e.printStackTrace();
                }
            }
            return -1;
        }
    }


    // State of the opt-in runtime logcat capture (see Config.enableRuntimeLogcatCapture).
    // Volatile: started from the service thread, read/stopped from the VM exit path.
    private static volatile Process logcatProcess = null;
    private static volatile Thread loggerThread = null;
    private static final Object logLock = new Object();

    /**
     * The log file is append-only now (nothing clears it, so the previous run stays
     * readable), so guard against unbounded growth: reads of the whole file happen
     * in-memory (getFileContents) and already carry an OOM FIXME.
     */
    private static boolean logFileOverCap(File logFile) {
        try {
            if (Config.logMaxBytes > 0 && logFile.exists() && logFile.length() > Config.logMaxBytes) {
                Log.w(TAG, "log file " + logFile + " is over " + Config.logMaxBytes
                        + " bytes, skipping further writes (delete the file to reset)");
                return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * One-shot marker so the "view log" screen explains why the log no longer grows
     * while a VM runs. Appends only, and only when a previous log file already
     * exists — it never creates, truncates or deletes anything.
     */
    private static void writeLogDisabledMarker() {
        String path = Config.logFilePath;
        if (path == null)
            return;
        File logFile = new File(path);
        if (!logFile.exists())
            return;
        FileOutputStream os = null;
        try {
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            os = new FileOutputStream(logFile, true);
            os.write(("\n--- " + stamp + ": runtime logcat capture is disabled"
                    + " (Config.enableRuntimeLogcatCapture=false), showing the previous run ---\n")
                    .getBytes("UTF-8"));
        } catch (Exception e) {
            Log.w(TAG, "writeLogDisabledMarker: " + e);
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /**
     * Starts the runtime logcat -> file capture, but ONLY when the user opted in via
     * Config.enableRuntimeLogcatCapture. It is OFF by default: the capture kept a
     * resident "logcat v main" child process plus a thread that flushed every single
     * line to disk for the whole VM session, which is a measurable CPU / IO / battery
     * cost while the guest is running.
     *
     * Disabled behaviour: return immediately and touch nothing, so the previous run's
     * log file stays readable / exportable (readLogContents, exportLogDirect,
     * Logger.viewLimboLog all keep working).
     * Enabled behaviour: lines are APPENDED instead of deleting the old file first,
     * so no log is lost either way, and every resource (Process / reader / stream)
     * is released in a finally block.
     */
    public static void startLogging() {
        if (!Config.enableRuntimeLogcatCapture) {
            Log.d(TAG, "startLogging: runtime logcat capture disabled, keeping previous log file");
            writeLogDisabledMarker();
            return;
        }
        final String logPath = Config.logFilePath;
        if (logPath == null) {
            Log.w(TAG, "Log file is not ready");
            return;
        }
        synchronized (logLock) {
            if (loggerThread != null && loggerThread.isAlive()) {
                Log.d(TAG, "startLogging: capture already running");
                return;
            }
            Thread t = new Thread(new Runnable() {
                public void run() {
                    Process process = null;
                    FileOutputStream os = null;
                    BufferedReader bufferedReader = null;
                    try {
                        File logFile = new File(logPath);
                        if (logFileOverCap(logFile))
                            return;
                        File dir = logFile.getParentFile();
                        if (dir != null && !dir.exists())
                            dir.mkdirs();
                        // Append mode: never destroy the previous log.
                        os = new FileOutputStream(logFile, true);
                        process = Runtime.getRuntime().exec("logcat v main");
                        // Publish the handle before reading so stopLogging() can end us.
                        logcatProcess = process;
                        bufferedReader = new BufferedReader(
                                new InputStreamReader(process.getInputStream()));
                        byte[] nl = "\n".getBytes("UTF-8");
                        String line;
                        int sinceCapCheck = 0;
                        while ((line = bufferedReader.readLine()) != null) {
                            os.write(line.getBytes("UTF-8"));
                            os.write(nl);
                            // No per-line flush: let the FileOutputStream buffer, and flush
                            // on exit. The old os.flush() per line was the main IO cost.
                            if (++sinceCapCheck >= 512) {
                                sinceCapCheck = 0;
                                os.flush();
                                // Stop instead of growing the file forever (see logFileOverCap).
                                if (logFileOverCap(logFile))
                                    break;
                            }
                        }
                    } catch (Exception e) {
                        Log.w(TAG, "logcat capture ended: " + e);
                    } finally {
                        if (bufferedReader != null) {
                            try {
                                bufferedReader.close();
                            } catch (IOException ignored) {
                            }
                        }
                        if (os != null) {
                            try {
                                os.flush();
                            } catch (IOException ignored) {
                            }
                            try {
                                os.close();
                            } catch (IOException ignored) {
                            }
                        }
                        if (process != null) {
                            try {
                                process.destroy();
                            } catch (Exception ignored) {
                            }
                        }
                        logcatProcess = null;
                        loggerThread = null;
                    }
                }
            });
            t.setName("LimboLogger");
            t.setDaemon(true);
            loggerThread = t;
            t.start();
        }
    }

    /**
     * Stops a running capture (no-op when it was never started, i.e. when the feature
     * is disabled). Called from the VM exit path so the child process and thread do
     * not outlive the service.
     */
    public static void stopLogging() {
        Process p = logcatProcess;
        Thread t = loggerThread;
        if (p != null) {
            try {
                p.destroy();
            } catch (Exception ignored) {
            }
        }
        if (t != null && t != Thread.currentThread() && t.isAlive()) {
            try {
                t.interrupt();
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * One-shot diagnostic tail: when the continuous runtime capture is OFF, dump the
     * tail of the logcat buffer to the log file once, when the VM stops. This keeps
     * "view log" / "export log" useful for the run that just ended (QEMU errors, the
     * LimboMaps dump, native crashes) without a resident logcat process or per-line
     * flushes for the whole session.
     *
     * Bounded by line count (Config.finalLogcatDumpLines) and byte size, appends
     * only, and is skipped when the continuous capture already recorded everything.
     */
    public static void dumpFinalLogcatSnapshot() {
        if (!Config.enableFinalLogcatDump || Config.enableRuntimeLogcatCapture)
            return;
        String path = Config.logFilePath;
        if (path == null)
            return;
        Process process = null;
        BufferedReader reader = null;
        FileOutputStream os = null;
        try {
            File logFile = new File(path);
            if (logFileOverCap(logFile))
                return;
            File dir = logFile.getParentFile();
            if (dir != null && !dir.exists())
                dir.mkdirs();
            // -d: dump the ring buffer and exit, -t: only the newest N lines.
            process = Runtime.getRuntime().exec(new String[]{"logcat", "-d", "-t",
                    String.valueOf(Config.finalLogcatDumpLines)});
            reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            os = new FileOutputStream(logFile, true);
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date());
            os.write(("\n--- " + stamp + ": logcat tail (capture disabled; last "
                    + Config.finalLogcatDumpLines + " lines at VM stop) ---\n").getBytes("UTF-8"));
            byte[] nl = "\n".getBytes("UTF-8");
            String line;
            long written = 0;
            while ((line = reader.readLine()) != null) {
                byte[] b = line.getBytes("UTF-8");
                if (written + b.length > Config.finalLogcatDumpMaxBytes) {
                    os.write("--- ... truncated ... ---".getBytes("UTF-8"));
                    os.write(nl);
                    break;
                }
                os.write(b);
                os.write(nl);
                written += b.length + 1;
            }
            os.flush();
        } catch (Exception e) {
            Log.w(TAG, "dumpFinalLogcatSnapshot: " + e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException ignored) {
                }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
            if (process != null) {
                try {
                    process.destroy();
                } catch (Exception ignored) {
                }
            }
        }
    }

    public static String LoadFile(Context context, String fileName, boolean loadFromRawFolder) throws IOException {
        // Create a InputStream to read the file into
        InputStream iS;
        if (loadFromRawFolder) {
            // get the resource id from the file name
            int rID = context.getResources().getIdentifier(LimboApplication.getInstance().getClass().getPackage().getName() + ":raw/" + fileName,
                    null, null);
            // get the file as a stream
            iS = context.getResources().openRawResource(rID);
        } else {
            // get the file as a stream
            iS = context.getResources().getAssets().open(fileName);
        }

        ByteArrayOutputStream oS = new ByteArrayOutputStream();
        byte[] buffer = new byte[iS.available()];
        int bytesRead = 0;
        while ((bytesRead = iS.read(buffer)) > 0) {
            oS.write(buffer);
        }
        oS.close();
        iS.close();

        // return the output stream as a String
        return oS.toString();
    }

    public static String getExtensionFromFilename(String fileName) {
        if (fileName == null)
            return "";

        int index = fileName.lastIndexOf(".");
        if (index >= 0) {
            return fileName.substring(index + 1);
        } else
            return "";
    }

    public static int getIconForFile(String file) {
        file = file.toLowerCase();
        String ext = FileUtils.getExtensionFromFilename(file).toLowerCase();

        if (ext.equals("img") || ext.equals("qcow")
                || ext.equals("qcow2") || ext.equals("vmdk") || ext.equals("vdi") || ext.equals("cow")
                || ext.equals("dmg") || ext.equals("bochs") || ext.equals("vpc")
                || ext.equals("vhd") || ext.equals("fs"))
            return R.drawable.harddisk;
        else if (ext.equals("iso"))
            return R.drawable.cd;
        else if (ext.equals("ima"))
            return R.drawable.floppy;
        else if (ext.equals("csv"))
            return R.drawable.importvms;
        else if (file.contains("kernel") || file.contains("vmlinuz") || file.contains("initrd"))
            return R.drawable.sysfile;
        else
            return R.drawable.close;

    }


    /**
     * Reads the current limbo log. Returns "" (never null) when the log file was
     * never set up or does not exist yet, so callers can tell "empty" from "error"
     * instead of crashing on a null path.
     */
    public static String readLogContents() {
        if (Config.logFilePath == null) {
            Log.w(TAG, "readLogContents: log file is not ready");
            return "";
        }
        try {
            return getFileContents(Config.logFilePath);
        } catch (Exception e) {
            Log.e(TAG, "readLogContents: could not read " + Config.logFilePath + ": " + e);
            return "";
        }
    }

    /** Machine name for the exported log header, or "" when nothing is loaded. */
    private static String getCurrentMachineNameForLog() {
        try {
            // getMachineName() dereferences the machine executor, which throws when
            // no machine has been loaded yet, so guard and degrade to "(none)".
            if (MachineController.getInstance().getMachine() == null)
                return "";
            String name = MachineController.getInstance().getMachineName();
            return name != null ? name : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String getQemuVersionForLog() {
        try {
            String version = LimboApplication.getQemuVersionString();
            if (version != null && version.trim().length() > 0)
                return version.trim();
        } catch (Exception ignored) {
        }
        return "unknown";
    }

    /**
     * Prepends a small header (QEMU version / machine / timestamp) so a log that
     * users paste into a bug report says which build produced it.
     */
    public static String buildLogExportText(String contents) {
        if (contents == null)
            contents = "";
        String machine = getCurrentMachineNameForLog();
        String header = "Limbo log\n"
                + "QEMU version: " + getQemuVersionForLog() + "\n"
                + "Machine: " + (machine.length() > 0 ? machine : "(none)") + "\n"
                + "Exported at: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()) + "\n"
                + "----------------------------------------\n";
        return header + contents;
    }

    /** Canonical location users are told about: /sdcard/Download/limbolog.txt */
    public static String getLogExportPath() {
        return new File(new File(Environment.getExternalStorageDirectory(),
                Environment.DIRECTORY_DOWNLOADS), Config.destLogFilename).getAbsolutePath();
    }

    /**
     * Primary log export path: writes the log straight to the public Downloads
     * folder as /sdcard/Download/limbolog.txt, no SAF / storage permission needed.
     *
     * On API 29+ it goes through MediaStore (Downloads collection) which needs no
     * permission; it reuses an existing limbolog.txt entry so repeated exports
     * overwrite instead of piling up "limbolog (1).txt". If any of that fails it
     * falls back to a plain FileOutputStream, and the result is only reported as
     * successful when the bytes really landed.
     *
     * @return the human readable path on success, null on failure (error is logged)
     */
    public static String exportLogToDownloadDirect(Context context, String contents) {
        if (context == null)
            return null;
        if (contents == null)
            contents = "";
        byte[] bytes = contents.getBytes();

        if (android.os.Build.VERSION.SDK_INT >= 29 && writeLogViaMediaStore(context, bytes))
            return getLogExportPath();

        // Legacy / fallback path: /sdcard/Download is writable for this app
        // (WRITE_EXTERNAL_STORAGE + requestLegacyExternalStorage + targetSdk 28).
        File dest = new File(getLogExportPath());
        FileOutputStream fos = null;
        try {
            File dir = dest.getParentFile();
            if (dir != null && !dir.exists())
                dir.mkdirs();
            fos = new FileOutputStream(dest);
            fos.write(bytes);
            fos.flush();
        } catch (Exception e) {
            Log.e(TAG, "exportLogToDownloadDirect: file write to " + dest + " failed: " + e);
            return null;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException ignored) {
                }
            }
        }
        // Only claim success if the file is actually there and non-empty.
        if (dest.exists() && dest.length() > 0)
            return dest.getAbsolutePath();
        Log.e(TAG, "exportLogToDownloadDirect: " + dest + " was not written");
        return null;
    }

    /** Returns true only when the log bytes were actually written via MediaStore. */
    private static boolean writeLogViaMediaStore(Context context, byte[] bytes) {
        Uri uri = null;
        OutputStream os = null;
        try {
            // Reuse an existing entry so repeated exports overwrite the same file.
            uri = findExistingDownloadEntry(context, Config.destLogFilename);
            if (uri == null) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, Config.destLogFilename);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain");
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/");
                uri = context.getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            }
            if (uri == null)
                return false;
            os = context.getContentResolver().openOutputStream(uri, "wt");
            if (os == null)
                return false;
            os.write(bytes);
            os.flush();
            return true;
        } catch (Exception e) {
            Log.w(TAG, "writeLogViaMediaStore failed: " + e);
            return false;
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    /** Looks up an existing Downloads entry by display name, or null. */
    private static Uri findExistingDownloadEntry(Context context, String displayName) {
        android.database.Cursor cursor = null;
        try {
            String selection = android.provider.MediaStore.MediaColumns.DISPLAY_NAME + "=?";
            cursor = context.getContentResolver().query(
                    android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    new String[]{android.provider.MediaStore.MediaColumns._ID},
                    selection, new String[]{displayName}, null);
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(
                        android.provider.MediaStore.MediaColumns._ID));
                return android.content.ContentUris.withAppendedId(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, id);
            }
        } catch (Exception e) {
            Log.w(TAG, "findExistingDownloadEntry: " + e);
        } finally {
            if (cursor != null) {
                try {
                    cursor.close();
                } catch (Exception ignored) {
                }
            }
        }
        return null;
    }

    public static Uri saveLogFileSDCard(Activity activity, Uri destDir) {
        return saveLogFileSDCard(activity, destDir, null);
    }

    public static Uri saveLogFileSDCard(Activity activity, Uri destDir, String contents) {
        DocumentFile destFileF;
        OutputStream os = null;
        Uri uri = null;

        try {
            String logFileContents = contents != null ? contents : readLogContents();
            DocumentFile dir = DocumentFile.fromTreeUri(activity, destDir);
            if (dir == null)
                throw new Exception("Could not get log path directory");

            //Create the file if doesn't exist
            destFileF = dir.findFile(Config.destLogFilename);
            if (destFileF == null) {
                // Some vendors have no MimeTypeMap entry for "txt"; passing a null
                // mime type makes createFile() throw, which used to surface only as
                // a short toast -> users saw "nothing exported".
                String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension("txt");
                if (mimeType == null)
                    mimeType = "text/plain";
                destFileF = dir.createFile(mimeType, Config.destLogFilename);
            }
            if (destFileF == null)
                throw new Exception("Could not create " + Config.destLogFilename);

            //Write to the dest
            os = activity.getContentResolver().openOutputStream(destFileF.getUri());
            os.write(logFileContents.getBytes());

            //success
            uri = destFileF.getUri();

        } catch (Exception ex) {
            ToastUtils.toastShort(activity, activity.getString(R.string.FailedToSaveLogFile) + ex.getMessage());
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
        return uri;
    }

    public static Uri exportFileContents(Activity activity,
                                         Uri destDir, String destFile, byte[] contents) {

        DocumentFile destFileF = null;
        OutputStream os = null;
        Uri uri = null;

        try {
            DocumentFile dir = DocumentFile.fromTreeUri(activity, destDir);

            //Create the file if doesn't exist
            destFileF = dir.findFile(destFile);
            if (destFileF == null) {
                destFileF = dir.createFile(MimeTypeMap.getSingleton().getMimeTypeFromExtension("csv"), destFile);
            } else {
                ToastUtils.toastShort(activity, activity.getString(R.string.FileExistsChooseAnotherFilename));
                return null;
            }

            //Write to the dest
            os = activity.getContentResolver().openOutputStream(destFileF.getUri());
            os.write(contents);

            //success
            uri = destFileF.getUri();

        } catch (Exception ex) {
            ToastUtils.toastShort(activity, activity.getString(R.string.FailedToExportFile) +": " + destFile + ", " + activity.getString(R.string.Error) + ":" + ex.getMessage());
        } finally {
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

        }
        return uri;
    }

    public static String saveLogFileLegacy(Activity activity,
                                           String destLogFilePath) {
        return saveLogFileLegacy(activity, destLogFilePath, null);
    }

    public static String saveLogFileLegacy(Activity activity,
                                           String destLogFilePath, String contents) {

        String filePath = null;
        File destFileF = new File(destLogFilePath, Config.destLogFilename);

        try {
            String logFileContents = contents != null ? contents : readLogContents();
            FileUtils.saveFileContents(destFileF.getAbsolutePath(), logFileContents);

            //success
            filePath = destFileF.getAbsolutePath();

        } catch (Exception ex) {
            ToastUtils.toastShort(activity, activity.getString(R.string.FailedSaveLogFile) + ": " + destFileF.getAbsolutePath() + ", " + activity.getString(R.string.Error)+ ": " + ex.getMessage());
        } finally {
        }
        return filePath;
    }


    public static String getFileUriFromIntent(Activity activity, Intent data, boolean write) {
        if (data == null)
            return null;

        Uri uri = data.getData();
        DocumentFile pickedFile = DocumentFile.fromSingleUri(activity, uri);
        String file = uri.toString();
        if (!file.contains("com.android.externalstorage.documents")) {
            showFileNotSupported(activity);
            return null;
        }
        activity.grantUriPermission(activity.getPackageName(), uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (write)
            activity.grantUriPermission(activity.getPackageName(), uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        activity.grantUriPermission(activity.getPackageName(), uri, Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

        int takeFlags = data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION;
        if (write)
            takeFlags = takeFlags | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

        activity.getContentResolver().takePersistableUriPermission(uri, takeFlags);
        return file;
    }

    public static String getDirPathFromIntent(Activity activity, Intent data) {
        if (data == null)
            return null;
        Bundle b = data.getExtras();
        if (b == null)
            return null;
        String file = b.getString("currDir");
        return file;
    }


    public static String getFilePathFromIntent(Activity activity, Intent data) {
        if (data == null)
            return null;
        Bundle b = data.getExtras();
        if (b == null)
            return null;
        String file = b.getString("file");
        return file;
    }

    public static FileType getFileTypeFromIntent(Activity activity, Intent data) {
        if (data == null)
            return null;
        Bundle b = data.getExtras();
        if (b == null)
            return null;
        FileType fileType = (FileType) b.getSerializable("fileType");
        return fileType;
    }

    public static void showFileNotSupported(Activity context) {
        DialogUtils.UIAlert(context, context.getString(R.string.Error), context.getString(R.string.FilePathNotSupportedWarning));
    }

    /**
     * Primary export entry point: dumps the current log to
     * /sdcard/Download/limbolog.txt without going through SAF. Shows a toast with
     * the real path on success, and an explicit "log is empty" toast when there is
     * nothing to export (instead of silently "succeeding").
     *
     * @return the file path on success, null otherwise
     */
    public static String exportLogDirect(Activity activity) {
        if (activity == null)
            return null;
        String contents = readLogContents();
        if (contents == null || contents.trim().length() == 0) {
            ToastUtils.toastLong(activity, activity.getString(R.string.log_export_empty));
            return null;
        }
        String path = exportLogToDownloadDirect(activity, buildLogExportText(contents));
        if (path == null) {
            ToastUtils.toastLong(activity, activity.getString(R.string.log_export_failed,
                    getLogExportPath()));
            return null;
        }
        ToastUtils.toastLong(activity, activity.getString(R.string.log_export_saved, path));
        return path;
    }

    /**
     * Secondary export entry point used after the user picked a directory through
     * SAF or through the legacy file manager. Never fails silently: every failure
     * path ends in a toast.
     */
    public static void saveLogToFile(final Activity activity, final String logFileDestDir) {
        if (activity == null || logFileDestDir == null)
            return;
        new Thread(new Runnable() {
            public void run() {
                String contents = readLogContents();
                if (contents == null || contents.trim().length() == 0) {
                    ToastUtils.toastLong(activity, activity.getString(R.string.log_export_empty));
                    return;
                }
                String exportText = FileUtils.buildLogExportText(contents);

                String displayName = null;
                try {
                    if (logFileDestDir.startsWith("content://")) {
                        Uri exportDirUri = Uri.parse(logFileDestDir);
                        Uri fileCreatedUri = FileUtils.saveLogFileSDCard(activity,
                                exportDirUri, exportText);
                        if (fileCreatedUri != null) {
                            // getFullPathFromDocumentFilePath() cannot resolve every
                            // provider, so fall back to the uri itself rather than
                            // dropping the "saved" feedback (the old silent failure).
                            String full = FileUtils.getFullPathFromDocumentFilePath(fileCreatedUri.toString());
                            displayName = (full != null && full.trim().length() > 0)
                                    ? full : fileCreatedUri.toString();
                        }
                    } else {
                        displayName = FileUtils.saveLogFileLegacy(activity, logFileDestDir, exportText);
                    }
                } catch (Exception ex) {
                    Log.e(TAG, "saveLogToFile: " + ex);
                }

                if (displayName == null) {
                    // Previously this was a silent no-op (the "saved" toast was only
                    // shown when displayName != null), which users perceived as
                    // "nothing happens".
                    ToastUtils.toastLong(activity,
                            activity.getString(R.string.log_export_failed, logFileDestDir));
                } else {
                    ToastUtils.toastLong(activity,
                            activity.getString(R.string.log_export_saved, displayName));
                }
            }
        }).start();
    }

    public static String convertFilePath(String text, int position) {
        try {
            text = FileUtils.getFullPathFromDocumentFilePath(text);
        } catch (Exception ex) {
            if (Config.debug)
                ex.printStackTrace();
        }

        return text;
    }

    public static class FileInfo {
        public String path;
        public String npath;
        public ParcelFileDescriptor pfd;

        public FileInfo(String path, String npath, ParcelFileDescriptor pfd) {
            this.npath = npath;
            this.path = path;
            this.pfd = pfd;
        }
    }

    public static String createImgFromTemplate(Context context, String templateImage, String destImage, FileType imgType) {

        String imagesDir = LimboSettingsManager.getImagesDir(context);
        String displayName = null;
        String filePath = null;
        if (imagesDir.startsWith("content://")) {
            Uri imagesDirUri = Uri.parse(imagesDir);
            Uri fileCreatedUri = FileInstaller.installImageTemplateToSDCard(context, templateImage,
                    imagesDirUri, "hdtemplates", destImage);
            if (fileCreatedUri != null) {
                displayName = FileUtils.getFullPathFromDocumentFilePath(fileCreatedUri.toString());
                filePath = fileCreatedUri.toString();
            }
        } else {
            filePath = FileInstaller.installImageTemplateToExternalStorage(context, templateImage, imagesDir, "hdtemplates", destImage);
            displayName = filePath;
        }
        if (displayName != null) {
            ToastUtils.toastShort(context, context.getString(R.string.ImageCreated) + ": " + displayName);
            return filePath;
        }
        return null;
    }
}
