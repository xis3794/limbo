package com.max2idea.android.limbo.ui.exp;

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
    /** MiuixIndication press alpha delta applied while the surface is pressed. */
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

    /** A flat rounded rectangle with a hairline stroke (used by the light theme). */
    static GradientDrawable roundedRect(int color, float radiusPx, int strokeColor,
                                        int strokeWidthPx) {
        GradientDrawable drawable = roundedRect(color, radiusPx);
        if (strokeWidthPx > 0 && (strokeColor >>> 24) != 0) {
            drawable.setStroke(strokeWidthPx, strokeColor);
        }
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
     * Attaches Miuix's Sink press feedback (scale 0.94, alpha −0.10) to a view.
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

    /** Scales a colour's alpha channel to {@code factor} (0..1). */
    static int alpha(int color, float factor) {
        int a = Math.round(factor * 255f);
        if (a < 0) {
            a = 0;
        } else if (a > 255) {
            a = 255;
        }
        return (color & 0x00FFFFFF) | (a << 24);
    }

    /** Whether {@link View#setElevation} / rounded clipping is available (API 21+). */
    static boolean hasElevation() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }
}
