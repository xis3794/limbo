package com.max2idea.android.limbo.ui.exp;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.limbo.emu.lib.R;

import java.util.ArrayList;

/**
 * Machine list for the experimental NPatch/Miuix style UI.
 *
 * <p>The structure mirrors NPatch's HomeScreen exactly (spec section 5):
 * StatusCard -> Row(CountCard x2) -> section title -> one AppItem card per
 * machine. When the list is empty only the status card survives, followed by the
 * empty state hint and a primary create button.
 *
 * <p>All three row kinds share one adapter because {@code ConcatAdapter} does not
 * exist in recyclerview 1.1.0.
 */
final class ExpMachineListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_EMPTY = 1;
    private static final int TYPE_MACHINE = 2;

    private static final float TITLE_SP = 17f;
    private static final float SUMMARY_SP = 13f;

    /** One stored machine, already resolved against the machine database. */
    static final class Entry {
        final String name;
        final String displayName;
        final String meta;
        final boolean running;

        Entry(String name, String displayName, String meta, boolean running) {
            this.name = name;
            this.displayName = displayName;
            this.meta = meta;
            this.running = running;
        }
    }

    /** Callbacks the hosting activity has to implement. */
    interface Listener {
        void onStatusCardClick();

        void onTotalCountClick();

        void onRunningCountClick();

        void onEmptyCreateClick();

        void onMachineClick(String machineName);

        void onMachineStartClick(String machineName);

        void onMachineLongClick(String machineName);
    }

    private final Context context;
    private final ExpTheme theme;
    private final Listener listener;

    private ArrayList<Entry> entries = new ArrayList<Entry>();
    private boolean running;
    private String runningName;
    private String runningMeta;

    ExpMachineListAdapter(Context context, ExpTheme theme, Listener listener) {
        this.context = context;
        this.theme = theme;
        this.listener = listener;
    }

    /** Replaces the whole model; every argument may be null/empty. */
    void setData(ArrayList<Entry> newEntries, boolean newRunning,
                 String newRunningName, String newRunningMeta) {
        entries = newEntries == null ? new ArrayList<Entry>() : newEntries;
        running = newRunning;
        runningName = newRunningName;
        runningMeta = newRunningMeta;
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        // The header always exists: the status card stays visible when empty.
        return entries.isEmpty() ? 2 : 1 + entries.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0) {
            return TYPE_HEADER;
        }
        return entries.isEmpty() ? TYPE_EMPTY : TYPE_MACHINE;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        if (viewType == TYPE_HEADER) {
            return HeaderHolder.create(context);
        }
        if (viewType == TYPE_EMPTY) {
            return EmptyHolder.create(context, theme);
        }
        return MachineHolder.create(context, theme);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof HeaderHolder) {
            bindHeader((HeaderHolder) holder);
        } else if (holder instanceof MachineHolder) {
            bindMachine((MachineHolder) holder, entries.get(position - 1));
        } else {
            bindEmpty((EmptyHolder) holder);
        }
    }

    // ------------------------------------------------------------------ header

    private void bindHeader(HeaderHolder holder) {
        LinearLayout container = holder.container;
        container.removeAllViews();
        container.addView(buildStatusCard());
        if (entries.isEmpty()) {
            return;
        }
        container.addView(buildCountRow());
        container.addView(buildSectionTitle());
    }

    private View buildStatusCard() {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(ExpDrawables.roundedRect(theme.surfaceContainer,
                theme.dpf(ExpTheme.CARD_RADIUS_DP), lightThemeStroke(), theme.dp(1)));
        int inside = theme.dp(ExpTheme.STATUS_CARD_INSIDE_MARGIN_DP);
        card.setPadding(inside, inside, inside, inside);
        card.setMinimumHeight(theme.dp(84));
        // RecyclerView rows carry RecyclerView.LayoutParams; the header container
        // is a plain LinearLayout, so it re-maps these to LinearLayout.LayoutParams.
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        int side = theme.dp(ExpTheme.CARD_SIDE_MARGIN_DP);
        cardParams.setMargins(side, 0, side, theme.dp(12));
        card.setLayoutParams(cardParams);
        ExpDrawables.sink(card);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onStatusCardClick();
            }
        });

        // StatusCard: 44dp circular plate with a 26dp glyph inside.
        FrameLayout plate = new FrameLayout(context);
        int plateSize = theme.dp(ExpTheme.STATUS_ICON_PLATE_DP);
        plate.setLayoutParams(new LinearLayout.LayoutParams(plateSize, plateSize));
        plate.setBackground(ExpDrawables.circle(running
                ? theme.translucent(theme.primary, 0.16f)
                : theme.secondaryContainer));

        ImageView glyph = new ImageView(context);
        int glyphSize = theme.dp(ExpTheme.STATUS_ICON_DP);
        glyph.setLayoutParams(new FrameLayout.LayoutParams(glyphSize, glyphSize, Gravity.CENTER));
        glyph.setImageResource(running ? R.drawable.ic_power : R.drawable.ic_machine_box);
        glyph.setColorFilter(running ? theme.primary : theme.onSurfaceVariantSummary,
                PorterDuff.Mode.SRC_IN);
        plate.addView(glyph);
        card.addView(plate);

        // StatusCard keeps a 16dp gap between the plate and the text block.
        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(theme.dp(16), 1));
        card.addView(gap);

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP);
        title.setTypeface(theme.bold);
        title.setTextColor(running ? theme.primary : theme.onSurface);
        title.setText(running ? R.string.exp_status_running : R.string.exp_status_idle);
        title.setMaxLines(1);
        title.setEllipsize(TextUtils.TruncateAt.END);
        texts.addView(title);

        View titleGap = new View(context);
        titleGap.setLayoutParams(new LinearLayout.LayoutParams(1, theme.dp(4)));
        texts.addView(titleGap);

        TextView summary = new TextView(context);
        summary.setTextSize(TypedValue.COMPLEX_UNIT_SP, SUMMARY_SP);
        summary.setTextColor(theme.onSurfaceVariantSummary);
        summary.setMaxLines(1);
        summary.setEllipsize(TextUtils.TruncateAt.END);
        if (running) {
            String name = TextUtils.isEmpty(runningName)
                    ? context.getString(R.string.exp_unnamed_machine) : runningName;
            String meta = TextUtils.isEmpty(runningMeta)
                    ? context.getString(R.string.exp_config_unavailable) : runningMeta;
            summary.setText(name + " · " + meta);
        } else {
            summary.setText(R.string.exp_status_idle_summary);
        }
        texts.addView(summary);
        card.addView(texts);
        return card;
    }

    private View buildCountRow() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        int side = theme.dp(ExpTheme.CARD_SIDE_MARGIN_DP);
        row.setPadding(side, 0, side, 0);
        int halfGap = theme.dp(6); // two cards + 2 x 6dp = the 12dp group gap

        View total = buildCountCard(R.string.exp_count_total, String.valueOf(entries.size()),
                true);
        LinearLayout.LayoutParams totalParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        totalParams.setMargins(0, 0, halfGap, 0);
        total.setLayoutParams(totalParams);
        row.addView(total);

        View runningCard = buildCountCard(R.string.exp_count_running, running ? "1" : "0", false);
        LinearLayout.LayoutParams runningParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        runningParams.setMargins(halfGap, 0, 0, 0);
        runningCard.setLayoutParams(runningParams);
        row.addView(runningCard);
        return row;
    }

    private View buildCountCard(int titleRes, String count, final boolean total) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(ExpDrawables.roundedRect(theme.surfaceContainer,
                theme.dpf(ExpTheme.CARD_RADIUS_DP), lightThemeStroke(), theme.dp(1)));
        int inside = theme.dp(ExpTheme.COUNT_CARD_INSIDE_MARGIN_DP);
        card.setPadding(inside, inside, inside, inside);
        ExpDrawables.sink(card);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (total) {
                    listener.onTotalCountClick();
                } else {
                    listener.onRunningCountClick();
                }
            }
        });

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView label = new TextView(context);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        label.setTypeface(theme.medium);
        label.setTextColor(theme.onSurfaceVariantSummary);
        label.setText(titleRes);
        label.setMaxLines(1);
        texts.addView(label);

        TextView value = new TextView(context);
        value.setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f);
        value.setTypeface(theme.bold);
        value.setTextColor(theme.onSurface);
        value.setText(count);
        value.setMaxLines(1);
        texts.addView(value);
        card.addView(texts);

        ImageView chevron = new ImageView(context);
        int chevronSize = theme.dp(18);
        chevron.setLayoutParams(new LinearLayout.LayoutParams(chevronSize, chevronSize));
        chevron.setImageResource(R.drawable.ic_chevron_right);
        chevron.setColorFilter(ExpDrawables.alpha(theme.onSurface, 0.4f), PorterDuff.Mode.SRC_IN);
        card.addView(chevron);
        return card;
    }

    private View buildSectionTitle() {
        TextView title = new TextView(context);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, TITLE_SP);
        title.setTypeface(theme.medium);
        title.setTextColor(theme.onSurface);
        title.setText(R.string.exp_all_machines);
        int side = theme.dp(ExpTheme.CARD_SIDE_MARGIN_DP);
        title.setPadding(side, theme.dp(12), side, theme.dp(8));
        title.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return title;
    }

    // ------------------------------------------------------------------- empty

    private void bindEmpty(EmptyHolder holder) {
        LinearLayout container = holder.container;
        container.removeAllViews();

        TextView hint = new TextView(context);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, SUMMARY_SP);
        hint.setTextColor(theme.onSurfaceVariantSummary);
        hint.setGravity(Gravity.CENTER);
        hint.setText(R.string.exp_empty_summary);
        hint.setLineSpacing(theme.dpf(4f), 1f);
        hint.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        container.addView(hint);

        View gap = new View(context);
        gap.setLayoutParams(new LinearLayout.LayoutParams(1, theme.dp(20)));
        container.addView(gap);

        TextView action = new TextView(context);
        action.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f);
        action.setTypeface(theme.medium);
        action.setTextColor(theme.onPrimary);
        action.setText(R.string.exp_empty_action);
        action.setGravity(Gravity.CENTER);
        action.setMinHeight(theme.dp(ExpTheme.MIN_TOUCH_TARGET_DP));
        action.setMinWidth(theme.dp(ExpTheme.MIN_TOUCH_TARGET_DP));
        int padH = theme.dp(24);
        action.setPadding(padH, theme.dp(12), padH, theme.dp(12));
        action.setBackground(ExpDrawables.pill(theme.primary,
                theme.dp(ExpTheme.MIN_TOUCH_TARGET_DP)));
        // Wrap content, not the MATCH_PARENT width a vertical LinearLayout defaults to.
        action.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ExpDrawables.sink(action);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onEmptyCreateClick();
            }
        });
        container.addView(action);
    }

    // ----------------------------------------------------------------- machine

    private void bindMachine(final MachineHolder holder, final Entry entry) {
        // Recycled rows keep the press transform from the previous owner, so reset it.
        holder.card.setScaleX(1f);
        holder.card.setScaleY(1f);
        holder.card.setAlpha(1f);
        holder.name.setText(entry.displayName);
        holder.meta.setText(entry.meta);
        holder.icon.setText(firstCharacter(entry.displayName));
        holder.icon.setBackground(ExpDrawables.roundedRect(theme.tileColor(entry.name),
                theme.dpf(ExpTheme.ITEM_ICON_RADIUS_DP)));

        holder.badge.setVisibility(entry.running ? View.VISIBLE : View.GONE);
        holder.start.setVisibility(entry.running ? View.GONE : View.VISIBLE);

        holder.card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onMachineClick(entry.name);
            }
        });
        holder.card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onMachineLongClick(entry.name);
                return true;
            }
        });
        holder.start.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onMachineStartClick(entry.name);
            }
        });
    }

    private static String firstCharacter(String displayName) {
        if (TextUtils.isEmpty(displayName)) {
            return "?";
        }
        String trimmed = displayName.trim();
        if (trimmed.length() == 0) {
            return "?";
        }
        return trimmed.substring(0, 1).toUpperCase();
    }

    /** Cards are flat; the light theme only gets a hairline outline. */
    private int lightThemeStroke() {
        return theme.dark ? 0 : theme.outline;
    }

    // ----------------------------------------------------------------- holders

    private static final class HeaderHolder extends RecyclerView.ViewHolder {
        final LinearLayout container;

        private HeaderHolder(LinearLayout container) {
            super(container);
            this.container = container;
        }

        static HeaderHolder create(Context context) {
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new HeaderHolder(container);
        }
    }

    private static final class EmptyHolder extends RecyclerView.ViewHolder {
        final LinearLayout container;

        private EmptyHolder(LinearLayout container) {
            super(container);
            this.container = container;
        }

        static EmptyHolder create(Context context, ExpTheme theme) {
            LinearLayout container = new LinearLayout(context);
            container.setOrientation(LinearLayout.VERTICAL);
            container.setGravity(Gravity.CENTER_HORIZONTAL);
            int side = theme.dp(32);
            container.setPadding(side, theme.dp(40), side, theme.dp(24));
            container.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new EmptyHolder(container);
        }
    }

    /**
     * One AppItem card: 48dp tile + 12dp gap + two text lines + trailing slot.
     * The trailing slot holds either the "运行中" badge or the start pill.
     */
    private static final class MachineHolder extends RecyclerView.ViewHolder {
        final LinearLayout card;
        final TextView icon;
        final TextView name;
        final TextView meta;
        final TextView badge;
        final FrameLayout start;

        private MachineHolder(LinearLayout card, TextView icon, TextView name, TextView meta,
                              TextView badge, FrameLayout start) {
            super(card);
            this.card = card;
            this.icon = icon;
            this.name = name;
            this.meta = meta;
            this.badge = badge;
            this.start = start;
        }

        static MachineHolder create(Context context, ExpTheme theme) {
            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.HORIZONTAL);
            card.setGravity(Gravity.CENTER_VERTICAL);
            int inside = theme.dp(ExpTheme.CARD_INSIDE_MARGIN_DP);
            card.setPadding(inside, inside, inside, inside);
            card.setMinimumHeight(theme.dp(72));
            card.setBackground(ExpDrawables.roundedRect(theme.surfaceContainer,
                    theme.dpf(ExpTheme.CARD_RADIUS_DP), theme.dark ? 0 : theme.outline,
                    theme.dp(1)));
            ExpDrawables.sink(card);
            card.setClickable(true);
            card.setLongClickable(true);
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            int side = theme.dp(ExpTheme.CARD_SIDE_MARGIN_DP);
            params.setMargins(side, 0, side, theme.dp(ExpTheme.CARD_GAP_DP));
            card.setLayoutParams(params);

            TextView icon = new TextView(context);
            int iconSize = theme.dp(ExpTheme.ITEM_ICON_DP);
            icon.setLayoutParams(new LinearLayout.LayoutParams(iconSize, iconSize));
            icon.setGravity(Gravity.CENTER);
            icon.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f);
            icon.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            icon.setTextColor(0xFFFFFFFF);
            icon.setMaxLines(1);
            card.addView(icon);

            View gap = new View(context);
            gap.setLayoutParams(new LinearLayout.LayoutParams(theme.dp(12), 1));
            card.addView(gap);

            LinearLayout center = new LinearLayout(context);
            center.setOrientation(LinearLayout.VERTICAL);
            center.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView name = new TextView(context);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f);
            name.setTypeface(theme.medium);
            name.setTextColor(theme.onSurface);
            name.setMaxLines(1);
            name.setEllipsize(TextUtils.TruncateAt.END);
            center.addView(name);

            View titleGap = new View(context);
            titleGap.setLayoutParams(new LinearLayout.LayoutParams(1, theme.dp(2)));
            center.addView(titleGap);

            TextView meta = new TextView(context);
            meta.setTextSize(TypedValue.COMPLEX_UNIT_SP, SUMMARY_SP);
            meta.setTextColor(theme.onSurfaceVariantSummary);
            meta.setMaxLines(1);
            meta.setEllipsize(TextUtils.TruncateAt.END);
            center.addView(meta);
            card.addView(center);

            FrameLayout trailing = new FrameLayout(context);
            LinearLayout.LayoutParams trailingParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, theme.dp(ExpTheme.MIN_TOUCH_TARGET_DP));
            trailingParams.setMarginStart(theme.dp(8));
            trailing.setLayoutParams(trailingParams);

            // "运行中" badge: tertiaryContainer pill, onTertiaryContainer text, 12sp.
            TextView badge = new TextView(context);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f);
            badge.setTextColor(theme.onTertiaryContainer);
            badge.setText(R.string.exp_badge_running);
            // Height pinned to 24dp so the pill radius below is exactly half of it.
            badge.setMinHeight(theme.dp(24));
            badge.setGravity(Gravity.CENTER_VERTICAL);
            badge.setBackground(ExpDrawables.pill(theme.tertiaryContainer, theme.dp(24)));
            badge.setPadding(theme.dp(8), theme.dp(3), theme.dp(8), theme.dp(3));
            badge.setMaxLines(1);
            badge.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL | Gravity.END));
            badge.setVisibility(View.GONE);
            trailing.addView(badge);

            // Start pill, shown for machines that are not running.
            FrameLayout start = new FrameLayout(context);
            start.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
                    Gravity.CENTER_VERTICAL | Gravity.END));
            start.setMinimumWidth(theme.dp(56));
            start.setClickable(true);
            start.setFocusable(true);
            TextView startLabel = new TextView(context);
            startLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, SUMMARY_SP);
            startLabel.setTypeface(theme.medium);
            startLabel.setTextColor(theme.primary);
            startLabel.setText(R.string.exp_start);
            // Height pinned to 32dp so the pill radius below is exactly half of it.
            startLabel.setMinHeight(theme.dp(32));
            startLabel.setGravity(Gravity.CENTER);
            startLabel.setBackground(ExpDrawables.pill(ExpDrawables.alpha(theme.primary, 0.12f),
                    theme.dp(32)));
            startLabel.setPadding(theme.dp(12), theme.dp(6), theme.dp(12), theme.dp(6));
            startLabel.setMaxLines(1);
            startLabel.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));
            start.addView(startLabel);
            trailing.addView(start);
            card.addView(trailing);

            return new MachineHolder(card, icon, name, meta, badge, start);
        }
    }
}
