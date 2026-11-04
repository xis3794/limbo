package com.max2idea.android.limbo.ui.exp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.limbo.emu.lib.R;

/**
 * Transparent, collapsing top bar that mirrors Miuix's {@code TopAppBar}.
 *
 * <p>Measured behaviour taken from {@code miuix .../basic/TopAppBar.kt}:
 * <ul>
 *   <li>collapsed height = statusBar + {@code TopAppBarDefaults.CollapsedHeight} (52dp)</li>
 *   <li>the large title is a 32sp ({@code title1}) label <em>below</em> that 52dp
 *       band, so the expanded height is statusBar + 52dp + title block + 4dp</li>
 *   <li>the bar is transparent until the content is scrolled (spec 6.2), then a
 *       translucent scrim (#F2FFFFFF / #F2242424) fades in over that band only</li>
 *   <li>the large title fades out as {@code 1 - 3 * fraction}; the small 20sp
 *       ({@code title3}) title fades in once {@code 3 * fraction >= 1}</li>
 * </ul>
 *
 * <p>The view keeps the expanded height for its whole lifetime so scrolling never
 * triggers a {@code requestLayout()} on the content; only the scrim and the two
 * labels animate. The list is padded by the expanded height, so content slides
 * underneath the translucent band exactly as it does in NPatch.
 */
final class ExpTopBar extends FrameLayout {

    /** Height of the large title block below the collapsed band (32sp + line box). */
    private static final float LARGE_TITLE_BLOCK_DP = 40f;
    /** TopAppBarDefaults.LargeTitleBottomPadding. */
    private static final float LARGE_TITLE_BOTTOM_PADDING_DP = 4f;
    /** Vertical travel of the small title while it fades in (Miuix: 20dp). */
    private static final float SMALL_TITLE_TRAVEL_DP = 20f;

    private final ExpTheme theme;
    private final TextView largeTitle;
    private final TextView collapsedTitle;
    private final View scrim;
    private final int collapsedHeight;
    private final int expandedHeight;

    ExpTopBar(Context context, ExpTheme theme, int statusBarHeight, OnClickListener addClick) {
        super(context);
        this.theme = theme;

        collapsedHeight = statusBarHeight + theme.dp(ExpTheme.TOP_BAR_HEIGHT_DP);
        expandedHeight = collapsedHeight
                + theme.dp(LARGE_TITLE_BLOCK_DP + LARGE_TITLE_BOTTOM_PADDING_DP);

        setClipChildren(true);

        // Scrim: only the collapsed band, transparent at the top of the page.
        scrim = new View(context);
        scrim.setBackgroundColor(theme.topBarScrim);
        scrim.setAlpha(0f);
        LayoutParams scrimParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, collapsedHeight);
        addView(scrim, scrimParams);

        // The 52dp band below the status bar: small title + "+" action.
        LinearLayout band = new LinearLayout(context);
        band.setOrientation(LinearLayout.HORIZONTAL);
        band.setGravity(Gravity.CENTER_VERTICAL);
        band.setPadding(theme.dp(ExpTheme.TITLE_PADDING_DP), 0,
                theme.dp(ExpTheme.ACTION_ICON_PADDING_DP), 0);
        LayoutParams bandParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, theme.dp(ExpTheme.TOP_BAR_HEIGHT_DP));
        bandParams.topMargin = statusBarHeight;
        addView(band, bandParams);

        collapsedTitle = new TextView(context);
        collapsedTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f);
        collapsedTitle.setTypeface(theme.title3);
        collapsedTitle.setTextColor(theme.onSurface);
        collapsedTitle.setText(R.string.exp_title);
        collapsedTitle.setSingleLine(true);
        collapsedTitle.setAlpha(0f);
        band.addView(collapsedTitle, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageView addButton = new ImageView(context);
        addButton.setImageResource(R.drawable.ic_plus);
        addButton.setColorFilter(theme.onSurface, PorterDuff.Mode.SRC_IN);
        addButton.setContentDescription(context.getString(R.string.exp_add));
        // 48dp touch target around the 24dp glyph (spec 8.5).
        int target = theme.dp(ExpTheme.MIN_TOUCH_TARGET_DP);
        addButton.setLayoutParams(new LinearLayout.LayoutParams(target, target));
        addButton.setPadding(theme.dp(12), theme.dp(12), theme.dp(12), theme.dp(12));
        addButton.setClickable(true);
        addButton.setFocusable(true);
        addButton.setOnClickListener(addClick);
        band.addView(addButton);

        largeTitle = new TextView(context);
        largeTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f);
        largeTitle.setTypeface(theme.title1);
        largeTitle.setTextColor(theme.onSurface);
        largeTitle.setText(R.string.exp_title);
        largeTitle.setSingleLine(true);
        largeTitle.setGravity(Gravity.CENTER_VERTICAL);
        largeTitle.setPadding(theme.dp(ExpTheme.TITLE_PADDING_DP), 0,
                theme.dp(ExpTheme.TITLE_PADDING_DP), 0);
        LayoutParams largeParams = new LayoutParams(
                LayoutParams.MATCH_PARENT, theme.dp(LARGE_TITLE_BLOCK_DP));
        largeParams.topMargin = collapsedHeight;
        addView(largeTitle, largeParams);

        LayoutParams selfParams = new LayoutParams(LayoutParams.MATCH_PARENT, expandedHeight);
        selfParams.gravity = Gravity.TOP;
        setLayoutParams(selfParams);
    }

    /** Height of the bar when expanded; the list is padded by exactly this much. */
    int getExpandedHeight() {
        return expandedHeight;
    }

    /** Height of the band that stays on screen once the page is scrolled. */
    int getCollapsedHeight() {
        return collapsedHeight;
    }

    /**
     * Applies the scroll-driven collapse.
     *
     * @param scrollOffsetPx vertical scroll offset of the list, {@code >= 0}
     */
    void onScrolled(int scrollOffsetPx) {
        int range = expandedHeight - collapsedHeight;
        float fraction = range <= 0 ? 1f : (float) scrollOffsetPx / (float) range;
        if (fraction < 0f) {
            fraction = 0f;
        } else if (fraction > 1f) {
            fraction = 1f;
        }

        // Large title: fades out three times faster than the bar collapses.
        float largeAlpha = 1f - Math.min(fraction * 3f, 1f);
        largeTitle.setAlpha(largeAlpha);
        largeTitle.setTranslationY(-theme.dpf(LARGE_TITLE_BLOCK_DP) * 0.5f * fraction);

        // Small title: appears only once the large one is gone.
        float smallAlpha = Math.min(fraction * 3f, 1f);
        collapsedTitle.setAlpha(smallAlpha);
        collapsedTitle.setTranslationY(theme.dpf(SMALL_TITLE_TRAVEL_DP) * (1f - smallAlpha));

        // Scrim: absent at the top, fully in after ~24dp of scrolling.
        float scrimFraction = (float) scrollOffsetPx / (float) Math.max(1, theme.dp(24f));
        if (scrimFraction > 1f) {
            scrimFraction = 1f;
        } else if (scrimFraction < 0f) {
            scrimFraction = 0f;
        }
        scrim.setAlpha(scrimFraction);
    }
}
