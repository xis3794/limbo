package com.max2idea.android.limbo.ui.exp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limbo.emu.lib.R;

/**
 * The floating capsule bar from NPatch ({@code FloatingGlassBottomBar}).
 *
 * <p>Metric reference from {@code FloatingGlassBottomBar.kt}:
 * <ul>
 *   <li>container: height 64dp, 4dp inner padding, {@code CircleShape}
 *       (native: corner radius = height / 2)</li>
 *   <li>each tab: height 56dp, min width 76dp, icon above an 11sp label,
 *       1dp vertical spacing, centred</li>
 *   <li>selection indicator: {@code primary.copy(alpha = 0.15f)} pill — not a
 *       solid primary block; the selected content colour is {@code primary}</li>
 *   <li>unselected content: {@code onSurfaceVariantSummary}; label is
 *       SemiBold when selected, Normal otherwise</li>
 * </ul>
 *
 * <p>The bar sits horizontally centred 12dp above the window bottom. NPatch uses
 * {@code padding(bottom = 12.dp + navBarsBottomPadding)}; this activity is not
 * edge-to-edge at the bottom (only LAYOUT_FULLSCREEN for the status bar is set),
 * so the content already stops above the navigation bar and the inset is zero.
 *
 * <p>This screen has two action items rather than navigation tabs, so there is no
 * persistent selection: the indicator only lights up while the item is pressed.
 */
final class ExpBottomBar extends FrameLayout {

    /** Called when one of the two items is tapped. */
    interface Listener {
        void onNewMachine();

        void onSettings();
    }

    /** primary.copy(alpha = 0.15f) — the selected-tab plate. */
    private static final float INDICATOR_ALPHA = 0.15f;

    ExpBottomBar(Context context, final ExpTheme theme, final Listener listener) {
        super(context);

        int barHeight = theme.dp(ExpTheme.BOTTOM_BAR_HEIGHT_DP);
        int padding = theme.dp(ExpTheme.BOTTOM_BAR_PADDING_DP);

        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        // Spec 6.3: #F2FFFFFF / #F2242424 container, CircleShape (= half height radius).
        bar.setBackground(ExpDrawables.roundedRect(theme.bottomBar,
                barHeight / 2f, theme.dark ? 0 : theme.outline, theme.dp(1)));
        bar.setPadding(padding, padding, padding, padding);
        // API 21+ (our minSdk) supports elevation; the spec asks for an 8dp lift.
        bar.setElevation(theme.dpf(8f));

        View newTab = buildTab(context, theme, R.drawable.ic_plus, R.string.exp_add,
                new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onNewMachine();
                    }
                });
        View settingsTab = buildTab(context, theme, R.drawable.ic_nav_settings,
                R.string.exp_settings, new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onSettings();
                    }
                });

        LinearLayout.LayoutParams newParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, theme.dp(56));
        newParams.setMargins(0, 0, theme.dp(2), 0);
        bar.addView(newTab, newParams);

        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, theme.dp(56));
        settingsParams.setMargins(theme.dp(2), 0, 0, 0);
        bar.addView(settingsTab, settingsParams);

        LayoutParams barParams = new LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, barHeight);
        barParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        barParams.bottomMargin = theme.dp(ExpTheme.BOTTOM_BAR_MARGIN_DP);
        addView(bar, barParams);
    }

    /** Total height the bar occupies at the bottom of the content area. */
    static int occupiedHeight(ExpTheme theme) {
        return theme.dp(ExpTheme.BOTTOM_BAR_HEIGHT_DP)
                + theme.dp(ExpTheme.BOTTOM_BAR_MARGIN_DP);
    }

    private static View buildTab(Context context, final ExpTheme theme, int iconRes, int labelRes,
                                 OnClickListener click) {
        final FrameLayout tab = new FrameLayout(context);
        tab.setMinimumWidth(theme.dp(ExpTheme.BOTTOM_BAR_TAB_MIN_WIDTH_DP));
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setOnClickListener(click);

        // Indicator plate (primary @ 15%), hidden until the tab is pressed.
        final View indicator = new View(context);
        indicator.setBackground(ExpDrawables.pill(
                ExpDrawables.alpha(theme.primary, INDICATOR_ALPHA), theme.dp(56)));
        FrameLayout.LayoutParams indicatorParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, theme.dp(56));
        indicatorParams.gravity = Gravity.CENTER;
        indicator.setLayoutParams(indicatorParams);
        indicator.setAlpha(0f);
        tab.addView(indicator);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams contentParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        content.setLayoutParams(contentParams);

        final ImageView icon = new ImageView(context);
        int iconSize = theme.dp(24);
        icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
        icon.setImageResource(iconRes);
        icon.setColorFilter(theme.onSurfaceVariantSummary, PorterDuff.Mode.SRC_IN);
        content.addView(icon);

        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, theme.dp(1)));
        content.addView(gap);

        final TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f);
        label.setTypeface(theme.medium);
        label.setTextColor(theme.onSurfaceVariantSummary);
        label.setText(labelRes);
        label.setSingleLine(true);
        label.setGravity(Gravity.CENTER);
        content.addView(label);
        tab.addView(content);

        // Pressed == the "selected" look from the spec: primary@15% plate plus
        // primary content. This screen has no persistent tab selection, so the
        // highlight only lives for the duration of the touch.
        tab.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                boolean pressed;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        pressed = true;
                        break;
                    case MotionEvent.ACTION_CANCEL:
                    case MotionEvent.ACTION_UP:
                        pressed = false;
                        break;
                    default:
                        // Keep the current state for MOVE etc.
                        return false;
                }
                indicator.setAlpha(pressed ? 1f : 0f);
                int contentColor = pressed ? theme.primary : theme.onSurfaceVariantSummary;
                icon.setColorFilter(contentColor, PorterDuff.Mode.SRC_IN);
                label.setTextColor(contentColor);
                return false;
            }
        });
        return tab;
    }
}
