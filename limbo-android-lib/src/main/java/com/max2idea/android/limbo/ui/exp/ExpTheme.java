package com.max2idea.android.limbo.ui.exp;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Typeface;

import com.limbo.emu.lib.R;

/**
 * Resolved colour and type tokens for the experimental (NPatch / Miuix style) UI.
 *
 * <p>Every value comes from docs/team/npatch-miuix-style-spec.md section 1 and 3,
 * which was extracted from Miuix 0.9.4 {@code theme/Colors.kt} and
 * {@code theme/TextStyles.kt}. Nothing here may be tuned by feel: the spec is the
 * single visual baseline.
 *
 * <p>The correct set (light or dark) is picked from
 * {@code Configuration.uiMode & UI_MODE_NIGHT_MASK} as required, so both variants
 * live in {@code values/colors_exp.xml} instead of a {@code values-night} copy.
 */
public final class ExpTheme {

    // ---- shape / spacing tokens (spec section 2) ----
    /** CardDefaults.CornerRadius. */
    public static final float CARD_RADIUS_DP = 16f;
    /** AppItem insideMargin. */
    public static final float CARD_INSIDE_MARGIN_DP = 12f;
    /** StatusCard insideMargin. */
    public static final float STATUS_CARD_INSIDE_MARGIN_DP = 20f;
    /** CountCard insideMargin. */
    public static final float COUNT_CARD_INSIDE_MARGIN_DP = 16f;
    /** Horizontal card margin. */
    public static final float CARD_SIDE_MARGIN_DP = 12f;
    /** Gap between cards. */
    public static final float CARD_GAP_DP = 8f;
    /** List item icon size. */
    public static final float ITEM_ICON_DP = 48f;
    /** List item icon corner radius. */
    public static final float ITEM_ICON_RADIUS_DP = 12f;
    /** StatusCard circular icon plate. */
    public static final float STATUS_ICON_PLATE_DP = 44f;
    /** StatusCard glyph inside the plate. */
    public static final float STATUS_ICON_DP = 26f;
    /** TopAppBarDefaults.CollapsedHeight. */
    public static final float TOP_BAR_HEIGHT_DP = 52f;
    /** TopAppBarDefaults.TitlePadding. */
    public static final float TITLE_PADDING_DP = 26f;
    /** TopAppBarDefaults.ActionIconPadding (icon edge, not touch target). */
    public static final float ACTION_ICON_PADDING_DP = 16f;
    /** Floating bottom bar container height. */
    public static final float BOTTOM_BAR_HEIGHT_DP = 64f;
    /** Floating bottom bar inner padding. */
    public static final float BOTTOM_BAR_PADDING_DP = 4f;
    /** Floating bottom bar tab minimum width. */
    public static final float BOTTOM_BAR_TAB_MIN_WIDTH_DP = 76f;
    /** Floating bottom bar distance from the window content bottom. */
    public static final float BOTTOM_BAR_MARGIN_DP = 12f;
    /** Minimum touch target. */
    public static final float MIN_TOUCH_TARGET_DP = 48f;

    /** Relative luminance of the scheme: true when the dark token set applies. */
    public final boolean dark;

    // ---- colour tokens ----
    public final int primary;
    public final int onPrimary;
    public final int background;
    public final int surface;
    public final int surfaceContainer;
    public final int onSurface;
    public final int onSurfaceSecondary;
    public final int onSurfaceVariantSummary;
    public final int onSurfaceVariantActions;
    public final int onSurfaceContainerVariant;
    public final int outline;
    public final int dividerLine;
    public final int secondaryContainer;
    public final int tertiaryContainer;
    public final int onTertiaryContainer;
    public final int error;
    public final int warning;
    public final int topBarScrim;
    /** Floating capsule bar container colour (`#F2FFFFFF` / `#F2242424`, spec 6.3). */
    public final int bottomBar;

    // ---- type ----
    /** Miuix {@code title1} (32sp) — the collapsing large title. */
    public final Typeface title1;
    /** Miuix {@code title3} (20sp) — the collapsed title, Medium weight. */
    public final Typeface title3;
    /** "sans-serif-medium": the closest native Medium/SemiBold. */
    public final Typeface medium;
    /** Plain bold. */
    public final Typeface bold;

    /** Soft colours for the machine icon tiles (spec 6.4). */
    public final int[] tiles;

    private final float density;

    private ExpTheme(Resources res) {
        Configuration configuration = res.getConfiguration();
        dark = (configuration.uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        density = res.getDisplayMetrics().density;

        if (dark) {
            primary = res.getColor(R.color.exp_dark_primary);
            onPrimary = res.getColor(R.color.exp_dark_on_primary);
            background = res.getColor(R.color.exp_dark_background);
            surface = res.getColor(R.color.exp_dark_surface);
            surfaceContainer = res.getColor(R.color.exp_dark_surface_container);
            onSurface = res.getColor(R.color.exp_dark_on_surface);
            onSurfaceSecondary = res.getColor(R.color.exp_dark_on_surface_secondary);
            onSurfaceVariantSummary = res.getColor(R.color.exp_dark_on_surface_variant_summary);
            onSurfaceVariantActions = res.getColor(R.color.exp_dark_on_surface_variant_actions);
            onSurfaceContainerVariant =
                    res.getColor(R.color.exp_dark_on_surface_container_variant);
            outline = res.getColor(R.color.exp_dark_outline);
            dividerLine = res.getColor(R.color.exp_dark_divider_line);
            secondaryContainer = res.getColor(R.color.exp_dark_secondary_container);
            tertiaryContainer = res.getColor(R.color.exp_dark_tertiary_container);
            onTertiaryContainer = res.getColor(R.color.exp_dark_on_tertiary_container);
            error = res.getColor(R.color.exp_dark_error);
            warning = res.getColor(R.color.exp_dark_warning);
            topBarScrim = res.getColor(R.color.exp_dark_top_bar_scrim);
            bottomBar = res.getColor(R.color.exp_dark_bottom_bar);
        } else {
            primary = res.getColor(R.color.exp_light_primary);
            onPrimary = res.getColor(R.color.exp_light_on_primary);
            background = res.getColor(R.color.exp_light_background);
            surface = res.getColor(R.color.exp_light_surface);
            surfaceContainer = res.getColor(R.color.exp_light_surface_container);
            onSurface = res.getColor(R.color.exp_light_on_surface);
            onSurfaceSecondary = res.getColor(R.color.exp_light_on_surface_secondary);
            onSurfaceVariantSummary =
                    res.getColor(R.color.exp_light_on_surface_variant_summary);
            onSurfaceVariantActions =
                    res.getColor(R.color.exp_light_on_surface_variant_actions);
            onSurfaceContainerVariant =
                    res.getColor(R.color.exp_light_on_surface_container_variant);
            outline = res.getColor(R.color.exp_light_outline);
            dividerLine = res.getColor(R.color.exp_light_divider_line);
            secondaryContainer = res.getColor(R.color.exp_light_secondary_container);
            tertiaryContainer = res.getColor(R.color.exp_light_tertiary_container);
            onTertiaryContainer = res.getColor(R.color.exp_light_on_tertiary_container);
            error = res.getColor(R.color.exp_light_error);
            warning = res.getColor(R.color.exp_light_warning);
            topBarScrim = res.getColor(R.color.exp_light_top_bar_scrim);
            bottomBar = res.getColor(R.color.exp_light_bottom_bar);
        }

        // Miuix TopAppBar renders the large title with the default weight and the
        // collapsed title with Medium; both sizes come from TextStyles.kt.
        title1 = Typeface.create("sans-serif", Typeface.NORMAL);
        title3 = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);
        bold = Typeface.create("sans-serif", Typeface.BOLD);

        tiles = new int[]{
                res.getColor(R.color.exp_tile_1),
                res.getColor(R.color.exp_tile_2),
                res.getColor(R.color.exp_tile_3),
                res.getColor(R.color.exp_tile_4),
                res.getColor(R.color.exp_tile_5),
                res.getColor(R.color.exp_tile_6)
        };
    }

    /** Reads the tokens for the uiMode that is currently active. */
    public static ExpTheme from(Context context) {
        return new ExpTheme(context.getResources());
    }

    /** Converts dp to whole pixels with the current density. */
    public int dp(float value) {
        return Math.round(value * density);
    }

    /** Converts dp to a float pixel value (for drawable radii / stroke widths). */
    public float dpf(float value) {
        return value * density;
    }

    /** Returns {@code color} with its alpha scaled to {@code factor} (0..1). */
    public int translucent(int color, float factor) {
        int alpha = Math.round(factor * 255f);
        if (alpha < 0) {
            alpha = 0;
        } else if (alpha > 255) {
            alpha = 255;
        }
        return (color & 0x00FFFFFF) | (alpha << 24);
    }

    /** Soft tile colour for a machine name: stable across launches. */
    public int tileColor(String machineName) {
        if (machineName == null || machineName.length() == 0) {
            return tiles[0];
        }
        int index = (machineName.hashCode() & 0x7fffffff) % tiles.length;
        return tiles[index];
    }
}
