package com.max2idea.android.limbo.ui.exp;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;

/**
 * Small helpers that build the flat HyperOS/Miuix shapes with plain
 * {@link GradientDrawable}s, plus the "Sink" press feedback.
 *
 * <p>The spec forbids MaterialCardView's elevation/shadow styling, so every card
 * background is a plain rounded rectangle drawn here.
 */
final class ExpDrawables {

    /** PressFeedbackType.Sink: the whole surface scales down to this factor. */
    private static final float SINK_SCALE = 0.94f;
    /** MiuixIndication press alpha delta applied on top of the surface alpha. */
    private static final float PRESS_ALPHA_DELTA = 0.10f;

    private ExpDrawables() {
    }

    /** A flat rounded rectangle. */
    static GradientDrawable roundedRect(int color, float radiusPx) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radiusPx);
        return drawable;
    }

    /** A flat rounded rectangle with a hairline stroke (light theme only). */
    static GradientDrawable roundedRect(int color, float radiusPx, int strokeColor,
                                        int strokeWidthPx) {
        GradientDrawable drawable = roundedRect(color, radiusPx);
        drawable.setStroke(strokeWidthPx, strokeColor);
        return drawable;
    }

    /** A flat circle. */
    static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    /** A pill: a rounded rectangle whose radius is half of {@code heightPx}. */
    static GradientDrawable pill(int color, int heightPx) {
        return roundedRect(color, heightPx / 2f);
    }

    /**
     * Attaches Miuix's Sink press feedback (scale 0.94 + alpha −0.10) to a view.
     *
     * <p>This has to be explicit: Miuix cards default to {@code showIndication = false}
     * and NPatch passes {@code showIndication = true} at every call site, so a card
     * without this listener would give no feedback at all.
     *
     * @return the same view, for chaining
     */
    static View sink(final View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        v.animate().scaleX(SINK_SCALE).scaleY(SINK_SCALE)
                                .setDuration(90).start();
                        v.setAlpha(1f - PRESS_ALPHA_DELTA);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        v.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
                        v.setAlpha(1f);
                        break;
                    default:
                        break;
                }
                // Returning false keeps the click listener working.
                return false;
            }
        });
        return view;
    }

    /** Sets the window/inset background colour of the activity. */
    static void setStatusBarColor(android.view.Window window, int color) {
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setStatusBarColor(color);
        }
    }

    static void setNavigationBarColor(android.view.Window window, int color) {
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            window.setNavigationBarColor(color);
        }
    }

    /** Light bar icons on a dark background (and vice versa) where the API allows it. */
    @SuppressWarnings("deprecation")
    static void setLightSystemBars(android.view.Window window, boolean lightBackground) {
        if (window == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = window.getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (lightBackground) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (lightBackground) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                } else {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        }
    }

    /** Full-screen transparent highlight used on the pill bar tabs. */
    static GradientDrawable transparent() {
        return roundedRect(Color.TRANSPARENT, 0f);
    }

    static int alpha(int color, float factor) {
        int a = Math.round(factor * 255f);
        if (a < 0) {
            a = 0;
        } else if (a > 255) {
            a = 255;
        }
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Convenience: dp from a context, used where no ExpTheme is at hand. */
    static int dp(Context context, float value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
