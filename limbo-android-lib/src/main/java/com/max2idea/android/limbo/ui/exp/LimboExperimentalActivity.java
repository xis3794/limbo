package com.max2idea.android.limbo.ui.exp;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.machine.Machine;
import com.max2idea.android.limbo.machine.MachineAction;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.main.Config;
import com.max2idea.android.limbo.main.LimboActivity;
import com.max2idea.android.limbo.main.LimboApplication;

import java.util.ArrayList;

/** A standalone experimental machine list, isolated from the legacy UI. */
public class LimboExperimentalActivity extends AppCompatActivity {
    private static final int BACKGROUND = Color.parseColor("#0B0F14");
    private static final int CARD_STROKE = Color.parseColor("#22303C");
    private static final int RUNNING = Color.parseColor("#33D6C0");
    private static final int[] BADGE_COLORS = {
            Color.parseColor("#28596B"),
            Color.parseColor("#52547D"),
            Color.parseColor("#6B4E68"),
            Color.parseColor("#35645B"),
            Color.parseColor("#6A583D"),
            Color.parseColor("#3E5F82")
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private MachineController machineController;
    private MachineAdapter adapter;
    private RecyclerView machineList;
    private TextView emptyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BACKGROUND);
        getWindow().setNavigationBarColor(BACKGROUND);
        setContentView(R.layout.exp_activity_machines);

        machineController = MachineController.getInstance();
        machineList = findViewById(R.id.exp_machine_list);
        emptyView = findViewById(R.id.exp_empty);
        machineList.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MachineAdapter();
        machineList.setAdapter(adapter);

        FloatingActionButton addButton = findViewById(R.id.exp_add);
        addButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                promptNewMachine();
            }
        });

        MaterialButton legacyButton = findViewById(R.id.exp_back_legacy);
        legacyButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                returnToLegacyUi();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshMachines();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private void refreshMachines() {
        ArrayList<String> names = machineController.getStoredMachines();
        adapter.setMachines(names);
        boolean empty = names == null || names.isEmpty();
        emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        machineList.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private void delayedRefresh(long delayMs) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isFinishing() && !isDestroyed()) {
                    refreshMachines();
                }
            }
        }, delayMs);
    }

    private void openMachine(String name, boolean autostart) {
        // LimboActivity has no <activity> entry of its own: every architecture
        // ships a concrete launcher subclass (com.limbo.emu.main.LimboEmuActivity
        // for x86, ...) and LimboActivity.onCreate() is where they all converge.
        // Starting LimboActivity.class directly throws ActivityNotFoundException.
        // Config.clientClass is the registered launcher class the legacy home
        // screen itself uses, so reuse it and fall back to our own package's
        // launch intent when even that is unset.
        Class<?> target = Config.clientClass;
        if (target == null) {
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null && launch.getComponent() != null) {
                launch.putExtra(LimboActivity.EXTRA_MACHINE, name);
                if (autostart) {
                    launch.putExtra(LimboActivity.EXTRA_START, true);
                }
                launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(launch);
                return;
            }
            Toast.makeText(this, R.string.exp_config_unavailable, Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, target);
        intent.putExtra(LimboActivity.EXTRA_MACHINE, name);
        if (autostart) {
            intent.putExtra(LimboActivity.EXTRA_START, true);
        }
        startActivity(intent);
    }

    private void confirmDelete(final String name) {
        if (machineController.isRunning()
                && name.equals(machineController.getMachineName())) {
            Toast.makeText(this, R.string.stop_first, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.exp_delete_title)
                .setMessage(getString(R.string.exp_delete_message, name))
                .setPositiveButton(R.string.exp_delete, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Machine machine = machineController.getMachineConfig(name);
                        if (machine != null) {
                            LimboApplication.getViewListener().onAction(
                                    MachineAction.DELETE_VM, machine);
                            delayedRefresh(700);
                        }
                    }
                })
                .setNegativeButton(R.string.exp_cancel, null)
                .show();
    }

    private void promptNewMachine() {
        final EditText input = new EditText(this);
        input.setHint(R.string.exp_machine_name_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT);

        int horizontalPadding = dp(20);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(horizontalPadding, dp(8), horizontalPadding, 0);
        container.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT));

        new AlertDialog.Builder(this)
                .setTitle(R.string.exp_new_machine)
                .setView(container)
                .setPositiveButton(R.string.exp_create, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String name = input.getText().toString().trim().replace("\"", "");
                        if (name.isEmpty()) {
                            Toast.makeText(LimboExperimentalActivity.this,
                                    R.string.exp_machine_name_hint, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        LimboApplication.getViewListener().onAction(
                                MachineAction.CREATE_VM, name);
                        delayedRefresh(800);
                    }
                })
                .setNegativeButton(R.string.exp_cancel, null)
                .show();
    }

    private void returnToLegacyUi() {
        ExpPrefs.setExperimentalUi(this, false);
        Intent launchIntent = getPackageManager().getLaunchIntentForPackage(getPackageName());
        if (launchIntent != null) {
            launchIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(launchIntent);
        }
        finish();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static GradientDrawable circle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private final class MachineAdapter extends RecyclerView.Adapter<MachineViewHolder> {
        private ArrayList<String> names = new ArrayList<>();

        void setMachines(ArrayList<String> storedMachines) {
            names = storedMachines == null ? new ArrayList<String>() : storedMachines;
            notifyDataSetChanged();
        }

        @Override
        public MachineViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.exp_item_machine, parent, false);
            return new MachineViewHolder(view);
        }

        @Override
        public void onBindViewHolder(MachineViewHolder holder, int position) {
            final String name = names.get(position);
            Machine machine = machineController.getMachineConfig(name);
            String displayName = name;
            if (machine != null && !TextUtils.isEmpty(machine.getName())) {
                displayName = machine.getName();
            }
            if (TextUtils.isEmpty(displayName)) {
                displayName = getString(R.string.exp_unnamed_machine);
            }

            holder.name.setText(displayName);
            if (machine == null) {
                holder.meta.setText(R.string.exp_config_unavailable);
            } else {
                String arch = TextUtils.isEmpty(machine.getArch())
                        ? getString(R.string.exp_unknown_arch) : machine.getArch();
                holder.meta.setText(getString(R.string.exp_machine_meta,
                        arch, machine.getCpuNum(), machine.getMemory()));
            }

            String badgeText = displayName.substring(0, 1).toUpperCase();
            holder.badge.setText(badgeText);
            int colorIndex = (displayName.hashCode() & 0x7fffffff) % BADGE_COLORS.length;
            holder.badge.setBackground(circle(BADGE_COLORS[colorIndex]));
            holder.runningDot.setBackground(circle(RUNNING));

            boolean running = machineController.isRunning()
                    && name.equals(machineController.getMachineName());
            holder.card.setStrokeColor(running ? RUNNING : CARD_STROKE);
            holder.runningGroup.setVisibility(running ? View.VISIBLE : View.GONE);

            holder.card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openMachine(name, false);
                }
            });
            holder.card.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    confirmDelete(name);
                    return true;
                }
            });
            holder.start.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    openMachine(name, true);
                }
            });
        }

        @Override
        public int getItemCount() {
            return names.size();
        }
    }

    private static final class MachineViewHolder extends RecyclerView.ViewHolder {
        final MaterialCardView card;
        final TextView badge;
        final TextView name;
        final TextView meta;
        final View runningGroup;
        final View runningDot;
        final MaterialButton start;

        MachineViewHolder(View itemView) {
            super(itemView);
            card = (MaterialCardView) itemView;
            badge = itemView.findViewById(R.id.exp_badge);
            name = itemView.findViewById(R.id.exp_name);
            meta = itemView.findViewById(R.id.exp_meta);
            runningGroup = itemView.findViewById(R.id.exp_running_group);
            runningDot = itemView.findViewById(R.id.exp_running_dot);
            start = itemView.findViewById(R.id.exp_start);
        }
    }
}
