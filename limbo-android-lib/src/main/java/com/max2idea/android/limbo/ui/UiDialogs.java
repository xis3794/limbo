package com.max2idea.android.limbo.ui;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.limbo.emu.lib.R;

/**
 * Small M3 style prompt card shared by the home screen and the log viewer.
 * Replaces the unstyled platform AlertDialogs so every dialog follows the
 * app surface, typography and pill buttons.
 */
public final class UiDialogs {

    private UiDialogs() {
    }

    public static Dialog prompt(Activity activity, String title, String message,
                                String negativeText, String positiveText,
                                final Runnable onPositive) {
        final Dialog dialog = new Dialog(activity, R.style.Limbo_Dialog_Plain);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);

        View content = LayoutInflater.from(activity).inflate(R.layout.dialog_prompt, null);
        dialog.setContentView(content);

        TextView titleView = (TextView) content.findViewById(R.id.dialog_title);
        TextView messageView = (TextView) content.findViewById(R.id.dialog_message);
        Button negative = (Button) content.findViewById(R.id.dialog_negative);
        Button positive = (Button) content.findViewById(R.id.dialog_positive);

        titleView.setText(title);
        messageView.setText(message);

        negative.setText(negativeText);
        negative.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        positive.setText(positiveText);
        positive.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
                if (onPositive != null) {
                    onPositive.run();
                }
            }
        });

        dialog.setCanceledOnTouchOutside(true);
        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            int width = activity.getResources().getDisplayMetrics().widthPixels - dp(activity, 40);
            // Keep the card readable on tablets/landscape (M3 max width).
            width = Math.min(width, dp(activity, 480));
            window.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT);
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.dimAmount = 0.45f;
            window.setAttributes(lp);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        return dialog;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
