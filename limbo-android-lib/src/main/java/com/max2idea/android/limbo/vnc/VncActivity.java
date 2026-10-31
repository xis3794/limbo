package com.max2idea.android.limbo.vnc;

import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.util.Log;
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
import com.max2idea.android.limbo.keyboard.KeyMapping;
import com.max2idea.android.limbo.machine.MachineController;

import java.io.IOException;

/**
 * Built-in VNC viewer. Connects to the local QEMU VNC server, auto-detecting the
 * display port and using the VNC password configured in the app settings.
 *
 * <p>Ports are probed with real RFB connections (never bare TCP connects, which
 * make QEMU start a handshake that is then aborted and can poison the server),
 * with limited backoff retries for transient failures and no retries for fatal
 * ones such as authentication or protocol errors.</p>
 */
public class VncActivity extends AppCompatActivity implements VncClient.Listener {
    private static final int PORT_FROM = 5900;
    private static final int PORT_TO = 5910;
    private static final String HOST = "127.0.0.1";
    /** Overall budget for locating the listener, so a missing server cannot
     *  block the viewer for minutes. */
    private static final long PORT_SCAN_BUDGET_MS = 35000;
    /** Per-candidate handshake timeout. A local QEMU answers in milliseconds;
     *  this only has to cover a listener that is not ready yet. */
    private static final long PROBE_TIMEOUT_MS = 4000;

    /** Silent listener for handshake probes: failures are surfaced via exceptions. */
    private static final VncClient.Listener NULL_LISTENER = new VncClient.Listener() {
        @Override
        public void onConnected(int width, int height) {
        }

        @Override
        public void onFramebuffer(Bitmap bitmap, boolean sizeChanged) {
        }

        @Override
        public void onError(String message, boolean fatal) {
        }

        @Override
        public void onDisconnected() {
        }
    };

    private VncView view;
    private TextView stateView;
    private TextView errorText;
    private View errorBox;

    private VncClient client;
    /** Bumped on every new connection attempt; stale clients' callbacks are ignored. */
    private volatile long generation;
    private volatile boolean finishing;
    private boolean errorVisible;
    private boolean retryScheduled;
    private boolean authRetryPending;

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
                // Bring up the system soft keyboard and forward every keystroke
                // straight to the guest instead of buffering text in a dialog.
                // Long press keeps the old "send a whole block of text" entry.
                if (view.isImeVisible()) {
                    view.hideIme();
                } else {
                    view.showIme();
                }
            }
        });
        findViewById(R.id.vnc_keyboard).setOnLongClickListener(
                new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        view.hideIme();
                        promptText();
                        return true;
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
        // Use the same decision the VM made when it built the -vnc argument:
        // when the active QEMU build has no crypto backend, the server runs
        // without authentication, so offering a password here would make the
        // viewer request the VNC security type and fail the handshake.
        final String password = VncSupport.effectivePassword(this);
        final long gen = VncConnectionPolicy.nextGeneration();
        generation = gen;
        new Thread(new Runnable() {
            @Override
            public void run() {
                final int port = findPort(password);
                if (port <= 0) {
                    postNoServer(gen);
                    return;
                }
                startClient(gen, port, password);
            }
        }, "VncPortScan").start();
    }

    /**
     * Finds a VNC port with a bounded overall budget.
     *
     * <p>Each candidate is probed with a real RFB handshake, never a bare TCP
     * connect: QEMU treats a bare connect as a client, starts the RFB handshake
     * and then has to abort it when the socket closes, which used to make the
     * following real connection fail intermittently.</p>
     *
     * <p>The budget matters: the previous version probed all eleven candidate
     * ports on every attempt with a 15s handshake timeout each, so a machine
     * with no listener could block the viewer for minutes. Now the configured
     * port is tried first, each probe is short, and the whole search shares one
     * deadline.</p>
     */
    private int findPort(String password) {
        final long deadline = System.currentTimeMillis() + PORT_SCAN_BUDGET_MS;
        IOException lastTransient = null;
        int attempts = 0;
        while (attempts < VncConnectionPolicy.MAX_ATTEMPTS && !finishing
                && !Thread.currentThread().isInterrupted()
                && System.currentTimeMillis() < deadline) {
            attempts++;
            for (int port = PORT_FROM; port <= PORT_TO; port++) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0 || finishing || Thread.currentThread().isInterrupted()) {
                    break;
                }
                try {
                    handshakeProbe(HOST, port, password, Math.min(left, PROBE_TIMEOUT_MS));
                    return port;
                } catch (VncConnectionPolicy.AuthFailureException e) {
                    // The listener is up and requires a password: the real
                    // connection would hit the same auth path, so let the
                    // normal client flow report it.
                    return port;
                } catch (VncConnectionPolicy.ProtocolException e) {
                    // Service present but not usable: do not hammer it.
                    lastTransient = e;
                } catch (IOException e) {
                    lastTransient = e;
                }
            }
            if (attempts < VncConnectionPolicy.MAX_ATTEMPTS) {
                long left = deadline - System.currentTimeMillis();
                if (left <= 0) {
                    break;
                }
                VncConnectionPolicy.sleep(
                        Math.min(VncConnectionPolicy.backoffMs(attempts), left));
            }
        }
        Log.i("VncActivity", "No VNC listener on " + HOST + " ("
                + PORT_FROM + "-" + PORT_TO + ") after " + attempts
                + " attempts" + (lastTransient != null
                ? ", last error: " + lastTransient : ""));
        return -1;
    }

    /**
     * Runs the RFB handshake (version, security, authentication) and
     * disconnects before ClientInit, so the server sees a complete,
     * well-formed session that it can clean up normally.
     */
    private void handshakeProbe(String host, int port, String password, long timeoutMs)
            throws IOException {
        VncClient probe = new VncClient(host, port, password, NULL_LISTENER);
        try {
            probe.startAndAwaitHandshake(timeoutMs);
        } finally {
            probe.stop();
        }
    }

    private void postNoServer(final long gen) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (finishing || gen != generation) {
                    return;
                }
                showError(getString(R.string.vnc_no_server));
            }
        });
    }

    private void startClient(final long gen, final int port, final String password) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (finishing || gen != generation) {
                    return;
                }
                VncClient old = client;
                client = new VncClient(HOST, port, password, VncActivity.this);
                view.setClient(client);
                client.start();
                if (old != null) {
                    old.stop();
                }
            }
        });
    }

    @Override
    public void onConnected(int width, int height) {
        if (finishing) {
            return;
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (finishing) {
                    return;
                }
                hideError();
                stateView.setText(getString(R.string.vnc_connected, width, height));
            }
        });
    }

    @Override
    public void onFramebuffer(Bitmap bitmap, boolean sizeChanged) {
        if (finishing) {
            return;
        }
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
                if (finishing) {
                    return;
                }
                if (message == null || message.isEmpty()) {
                    return;
                }
                if (VncConnectionPolicy.isFatalMessage(message) && !authRetryPending) {
                    // QEMU applies the configured VNC password a couple of
                    // seconds after the VM starts; retry once before reporting.
                    authRetryPending = true;
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
                String text = VncConnectionPolicy.isFatalMessage(message)
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
                if (finishing) {
                    return;
                }
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
        // Every key a physical keyboard has and a touch screen cannot express.
        // The keysyms come from the shared table so VNC and SDL stay in sync.
        final String[] labels = {
                "Home", "End", "Page Up", "Page Down", "Insert", "Delete",
                "↑", "↓", "←", "→",
                "Esc", "Tab", "Backspace", "Enter",
                "F1", "F2", "F3", "F4", "F5", "F6",
                "F7", "F8", "F9", "F10", "F11", "F12",
                "Caps Lock", "Num Lock", "Scroll Lock", "Print Screen", "Pause",
                "Ctrl", "Alt", "Shift", "Win"
        };
        final int[] keysyms = {
                KeyMapping.KEYSYM_HOME, KeyMapping.KEYSYM_END,
                KeyMapping.KEYSYM_PAGE_UP, KeyMapping.KEYSYM_PAGE_DOWN,
                KeyMapping.KEYSYM_INSERT, KeyMapping.KEYSYM_DELETE,
                KeyMapping.KEYSYM_UP, KeyMapping.KEYSYM_DOWN,
                KeyMapping.KEYSYM_LEFT, KeyMapping.KEYSYM_RIGHT,
                KeyMapping.KEYSYM_ESCAPE, KeyMapping.KEYSYM_TAB,
                KeyMapping.KEYSYM_BACKSPACE, KeyMapping.KEYSYM_RETURN,
                // F1..F12 are contiguous (0xFFBE..0xFFC9) in X11 keysyms.
                KeyMapping.KEYSYM_F1, KeyMapping.KEYSYM_F1 + 1, KeyMapping.KEYSYM_F1 + 2,
                KeyMapping.KEYSYM_F1 + 3, KeyMapping.KEYSYM_F1 + 4, KeyMapping.KEYSYM_F1 + 5,
                KeyMapping.KEYSYM_F1 + 6, KeyMapping.KEYSYM_F1 + 7, KeyMapping.KEYSYM_F1 + 8,
                KeyMapping.KEYSYM_F1 + 9, KeyMapping.KEYSYM_F1 + 10, KeyMapping.KEYSYM_F1 + 11,
                KeyMapping.KEYSYM_CAPS_LOCK, KeyMapping.KEYSYM_NUM_LOCK,
                KeyMapping.KEYSYM_SCROLL_LOCK, KeyMapping.KEYSYM_PRINT,
                KeyMapping.KEYSYM_PAUSE,
                KeyMapping.KEYSYM_CONTROL_L, KeyMapping.KEYSYM_ALT_L,
                KeyMapping.KEYSYM_SHIFT_L, KeyMapping.KEYSYM_SUPER_L
        };
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
