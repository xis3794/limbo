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
package com.max2idea.android.limbo.log;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.text.InputType;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.dialog.DialogUtils;
import com.max2idea.android.limbo.main.LimboApplication;
import com.max2idea.android.limbo.main.LimboFileManager;
import com.max2idea.android.limbo.files.FileUtils;
import com.max2idea.android.limbo.ui.UiDialogs;
import com.max2idea.android.limbo.machine.Machine.FileType;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.toast.ToastUtils;

import java.io.File;
import java.io.IOException;
import java.util.Scanner;

/** Custom logger for android so users can view the log if something went wrong
 *
 */
public class Logger {
    public static final String TAG = "Logger";

    public static Spannable formatAndroidLog(String contents) {
        Scanner scanner = null;
        Spannable formattedString = new SpannableString(contents);
        if (contents.length() == 0)
            return formattedString;

        try {
            scanner = new Scanner(contents);
            int counter = 0;
            ForegroundColorSpan colorSpan;
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                //FIXME: some devices don't have standard format for the log
                if (line.startsWith("E/") || line.contains(" E ")) {
                    colorSpan = new ForegroundColorSpan(Color.parseColor("#F78181"));
                } else if (line.startsWith("W/") || line.contains(" W ")) {
                    colorSpan = new ForegroundColorSpan(Color.parseColor("#81A3F7"));
                } else if (line.startsWith("D/") || line.contains(" D ")) {
                    colorSpan = new ForegroundColorSpan(Color.parseColor("#B1FFD7"));
                } else {
                    colorSpan = null;
                }
                if (colorSpan != null) {
                    formattedString.setSpan(colorSpan, counter, counter + line.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                counter += line.length() + 1;
            }

        } catch (Exception ex) {
            Log.w(TAG, "Could not format limbo log: " + ex.getMessage());
        } finally {
            if (scanner != null) {
                try {
                    scanner.close();
                } catch (Exception ex) {
                    if (Config.debug)
                        ex.printStackTrace();
                }
            }

        }
        return formattedString;
    }

    public static void promptShowLog(final Activity activity) {
        UiDialogs.prompt(activity,
                activity.getString(R.string.ShowLog),
                activity.getString(R.string.LogWarning),
                activity.getString(R.string.Cancel),
                activity.getString(R.string.view_log_action),
                new Runnable() {
                    @Override
                    public void run() {
                        viewLimboLog(activity);
                    }
                });
    }

    public static void UIAlertLog(final Activity activity, String title, Spannable body) {

        final AlertDialog alertDialog;
        alertDialog = new AlertDialog.Builder(activity).create();
        alertDialog.setTitle(title);
        TextView textView = new TextView(activity);
        textView.setPadding(20, 20, 20, 20);
        textView.setText(body);
        // The log view is drawn on a black background; without an explicit text
        // color the theme's (dark) text color makes most lines invisible, which
        // is exactly what PR #1 reported ("部分日志与背景颜色相同").
        textView.setBackgroundColor(Color.BLACK);
        textView.setTextColor(Color.WHITE);
        textView.setTextSize(12);
        textView.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        textView.setSingleLine(false);
        ScrollView view = new ScrollView(activity);
        view.addView(textView);
        alertDialog.setView(view);
        alertDialog.setCanceledOnTouchOutside(false);
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, activity.getString(R.string.Ok),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                alertDialog.dismiss();
            }
        });
        alertDialog.setButton(DialogInterface.BUTTON_NEUTRAL, activity.getString(R.string.CopyTo),
                new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                // The old code fired ACTION_OPEN_DOCUMENT_TREE straight away and the
                // result was dropped without any feedback on the home screen (see
                // LimboActivity.onActivityResult), so "Copy To" looked broken.
                alertDialog.dismiss();
                promptExportLog(activity);
            }
        });
        alertDialog.show();
    }

    /**
     * Export chooser. The first entry writes the log straight to
     * /sdcard/Download/limbolog.txt with no SAF / permission involved, which is the
     * path that is guaranteed to work on this device; the second keeps the old
     * "pick a directory" flow as a secondary option.
     */
    public static void promptExportLog(final Activity activity) {
        if (activity == null)
            return;
        final String[] items = new String[]{
                activity.getString(R.string.log_export_direct),
                activity.getString(R.string.CopyTo)
        };
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.log_export_dialog_title);
        builder.setItems(items, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == 0) {
                    FileUtils.exportLogDirect(activity);
                } else {
                    ToastUtils.toastShort(activity, activity.getString(R.string.ChooseDirToSaveLogFile));
                    LimboFileManager.browse(activity, FileType.LOG_DIR, Config.OPEN_LOG_FILE_DIR_REQUEST_CODE);
                }
            }
        });
        builder.show();
    }

    public static void viewLimboLog(final Activity activity) {

        // Config.logFilePath is null until setupLogFile() runs (it is called from the
        // architecture-specific LimboEmuActivity). Passing a null path crashed inside
        // new File(null) -> NPE, so the log dialog never even showed up.
        String contents = FileUtils.readLogContents();

        if (contents.length() > 50 * 1024)
            contents = contents.substring(0, 25 * 1024)
                            + "\n.....\n" +
                            contents.substring(contents.length() - 25 * 1024);

        final Spannable contentsFormatted = formatAndroidLog(contents);

        // An empty log renders as a blank black dialog, which reads as "the app is
        // broken". Say explicitly that nothing has been logged yet.
        if (contents.trim().length() == 0)
            ToastUtils.toastLong(activity, activity.getString(R.string.log_export_empty));

        activity.runOnUiThread(new Runnable() {
            @Override
            public void run() {

                if (Config.viewLogInternally) {
                    UIAlertLog(activity, activity.getString(R.string.LimboLog), contentsFormatted);
                } else {
                    try {
                        Intent intent = new Intent(Intent.ACTION_EDIT);
                        File file = new File(Config.logFilePath);
                        Uri uri = Uri.fromFile(file);
                        intent.setDataAndType(uri, "text/plain");
                        activity.startActivity(intent);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        // fallback
                        UIAlertLog(activity, activity.getString(R.string.LimboLog), contentsFormatted);
                    }
                }
            }
        });
    }

    public static void setupLogFile(String filePath) {
        Config.logFilePath = LimboApplication.getInstance().getCacheDir() + filePath;
    }
}
