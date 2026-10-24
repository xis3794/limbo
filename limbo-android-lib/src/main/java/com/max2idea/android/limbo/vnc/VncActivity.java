package com.max2idea.android.limbo.vnc;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.limbo.emu.lib.R;
import com.max2idea.android.limbo.machine.MachineController;
import com.max2idea.android.limbo.main.LimboSettingsManager;

import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Built-in VNC viewer. Connects to the local QEMU VNC server, auto-detecting the
 * display port and using the VNC password configured in the app settings.
 */
public class VncActivity extends AppCompatActivity implements VncClient.Listener {
    private static final int PORT_FROM = 5900;
    private static final int PORT_TO = 5910;

    private VncView view;
    private TextView stateView;
    private TextView errorText;
    private View errorBox;

    private VncClient client;
    private volatile boolean finishing;
    private boolean errorVisible;
    private boolean retryScheduled;
    private int passwordRetries;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setupFullscreen();
        setContentView(R.layout.activity_vnc);

        view = findViewById(R.id.vnc_view);
        stateView = findViewById(R.id.vnc_state);
        errorText = findViewById(R.id.vnc_error_text);
        errorBox = findViewById(R.id.vnc_error);

        TextView title = findViewById(R.id.vnc_title);
        String name = MachineController.getInstance().isRunning()
                ? MachineController.getInstance().getMachineName() : null;
        title.setText(name != null ? name : getString(R.string.app_name));

        findViewById(R.id.vnc_close).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.vnc_retry).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                passwordRetries = 0;
                connect();
            }
        });
        findViewById(R.id.vnc_cancel).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        findViewById(R.id.vnc_keyboard).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptText();
            }
        });
        findViewById(R.id.vnc_esc).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendKey(0xFF1B);
            }
        });
        findViewById(R.id.vnc_tab).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendKey(0xFF09);
            }
        });
        findViewById(R.id.vnc_cad).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCtrlAltDel();
            }
        });
        findViewById(R.id.vnc_arrows).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                promptNavigationKeys();
            }
        });

        stateView.setText(R.string.vnc_connecting);
        connect();
    }

    private void setupFullscreen() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void connect() {
        if (finishing) {
            return;
        }
        if (!MachineController.getInstance().isRunning()) {
            showError(getString(R.string.vnc_not_running));
            return;
        }
        hideError();
        stateView.setText(R.string.vnc_connecting);
        final String password = LimboSettingsManager.getVNCEnablePassword(this)
                ? LimboSettingsManager.getVNCPass(this) : null;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int port = findPort();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (finishing) {
                            return;
                        }
                        if (port <= 0) {
                            showError(getString(R.string.vnc_no_server));
                            return;
                        }
                        if (client != null) {
                            client.stop();
                        }
                        client = new VncClient("127.0.0.1", port, password, VncActivity.this);
                        view.setClient(client);
                        client.start();
                    }
                });
            }
        }, "VncPortScan").start();
    }

    private int findPort() {
        // QEMU opens the VNC listener a moment after the VM start, so poll for it.
        for (int attempt = 0; attempt < 16; attempt++) {
            for (int port = PORT_FROM; port <= PORT_TO; port++) {
                Socket socket = new Socket();
                try {
                    socket.connect(new InetSocketAddress("127.0.0.1", port), 250);
                    return port;
                } catch (Exception ignored) {
                } finally {
                    try {
                        socket.close();
                    } catch (Exception ignored) {
                    }
                }
            }
            if (finishing || Thread.currentThread().isInterrupted()) {
                return -1;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                return -1;
            }
        }
        return -1;
    }

    @Override
    public void onConnected(int width, int height) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                hideError();
                stateView.setText(getString(R.string.vnc_connected, width, height));
            }
        });
    }

    @Override
    public void onFramebuffer(Bitmap bitmap, boolean sizeChanged) {
        view.refresh();
    }

    @Override
    public void onError(final String message, boolean fatal) {
        if (finishing) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (message == null || message.isEmpty()) {
                    return;
                }
                if (message.contains("Authentication") && passwordRetries < 1) {
                    // QEMU applies the configured VNC password a couple of seconds
                    // after the VM starts; retry once before reporting a failure.
                    passwordRetries++;
                    retryScheduled = true;
                    stateView.setText(R.string.vnc_connecting);
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            retryScheduled = false;
                            connect();
                        }
                    }, 2500);
                    return;
                }
                String text = message.contains("Authentication")
                        ? getString(R.string.vnc_auth_failed)
                        : getString(R.string.vnc_connection_failed, message);
                showError(text);
            }
        });
    }

    @Override
    public void onDisconnected() {
        if (finishing) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (!errorVisible && !retryScheduled) {
                    stateView.setText(R.string.vnc_disconnected);
                }
            }
        });
    }

    private void showError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (finishing) {
                    return;
                }
                errorVisible = true;
                errorText.setText(message);
                errorBox.setVisibility(View.VISIBLE);
                stateView.setText(R.string.vnc_disconnected);
            }
        });
    }

    private void hideError() {
        errorVisible = false;
        errorBox.setVisibility(View.GONE);
    }

    private void sendKey(int keysym) {
        if (client == null) {
            return;
        }
        client.sendKey(keysym, true);
        client.sendKey(keysym, false);
    }

    private void sendCtrlAltDel() {
        if (client == null) {
            return;
        }
        client.sendKey(0xFFE3, true); // Control_L
        client.sendKey(0xFFE9, true); // Alt_L
        client.sendKey(0xFFFF, true); // Delete
        client.sendKey(0xFFFF, false);
        client.sendKey(0xFFE9, false);
        client.sendKey(0xFFE3, false);
    }

    private void promptNavigationKeys() {
        final String[] labels = {"Home", "End", "Page Up", "Page Down", "Insert", "Delete", "↑", "↓", "←", "→"};
        final int[] keysyms = {0xFF50, 0xFF57, 0xFF55, 0xFF56, 0xFF63, 0xFFFF,
                0xFF52, 0xFF54, 0xFF51, 0xFF53};
        new AlertDialog.Builder(this)
                .setTitle(R.string.vnc_keyboard)
                .setItems(labels, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendKey(keysyms[which]);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptText() {
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setHint(R.string.vnc_key_prompt);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, pad / 2, pad, pad / 2);
        container.addView(input);
        new AlertDialog.Builder(this)
                .setTitle(R.string.vnc_keyboard)
                .setView(container)
                .setPositiveButton(R.string.vnc_send, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        sendText(input.getText().toString());
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void sendText(String text) {
        if (client == null || text == null) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int keysym;
            if (c == '\n' || c == '\r') {
                keysym = 0xFF0D;
            } else if (c == '\b') {
                keysym = 0xFF08;
            } else if (c >= 32 && c <= 126) {
                keysym = c;
            } else {
                keysym = 0x01000000 | c;
            }
            client.sendKey(keysym, true);
            client.sendKey(keysym, false);
        }
    }

    @Override
    protected void onDestroy() {
        finishing = true;
        if (client != null) {
            client.stop();
            client = null;
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
