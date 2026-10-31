package com.max2idea.android.limbo.vnc;

import android.util.Log;

import java.io.EOFException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.PortUnreachableException;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import com.max2idea.android.limbo.vnc.VncClient.VncThread;

/**
 * Connection policy for the built-in VNC viewer:
 * - limited retries with exponential backoff while the VM is still starting,
 * - errors classified as transient (port not ready / refused / timeout) or
 *   fatal (authentication failure / protocol mismatch) — fatal errors never retry,
 * - a connection generation counter so stale clients cannot disturb the active one.
 */
final class VncConnectionPolicy {
    private static final String TAG = "VncConnectionPolicy";

    static final int MAX_ATTEMPTS = 6;
    static final long BASE_BACKOFF_MS = 400;
    static final long MAX_BACKOFF_MS = 4000;

    private static long generationCounter;

    private VncConnectionPolicy() {
    }

    /** Bumps and returns the current connection generation. */
    static synchronized long nextGeneration() {
        return ++generationCounter;
    }

    /** Classify an exception thrown while connecting. */
    static boolean isFatal(Throwable t) {
        return findFatal(t) != null;
    }

    /**
     * Classify an error message (as delivered to the listener).
     * Matches "Authentication failed: ..." from the RFB handshake, avoiding
     * substring checks on unrelated text.
     */
    static boolean isFatalMessage(String message) {
        if (message == null) {
            return false;
        }
        return message.startsWith("Authentication failed");
    }

    /**
     * Whether another attempt is allowed after this failure.
     * Retries once for auth failures when the VM has just started (the VNC
     * password is applied a couple of seconds after boot), never otherwise.
     */
    static boolean mayRetry(boolean fatal, int attempt, boolean allowAuthRetry) {
        if (fatal) {
            return allowAuthRetry && attempt <= 1;
        }
        return attempt < MAX_ATTEMPTS;
    }

    /** Backoff delay before the given (1-based) retry attempt. */
    static long backoffMs(int attempt) {
        long delay = BASE_BACKOFF_MS;
        for (int i = 1; i < attempt; i++) {
            delay *= 2;
        }
        return Math.min(delay, MAX_BACKOFF_MS);
    }

    static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    static String describe(Throwable t) {
        if (t == null) {
            return "";
        }
        if (t instanceof SocketTimeoutException) {
            return "Timed out";
        }
        String msg = t.getMessage();
        return msg != null ? msg : t.toString();
    }

    private static Throwable findFatal(Throwable t) {
        Throwable cur = t;
        Throwable fatal = null;
        while (cur != null) {
            if (isFatalType(cur)) {
                fatal = cur;
                break;
            }
            cur = cur.getCause();
        }
        return fatal;
    }

    private static boolean isFatalType(Throwable t) {
        if (t instanceof AuthFailureException) {
            return true;
        }
        if (t instanceof ProtocolException) {
            return true;
        }
        if (t instanceof EOFException) {
            // Peer closed the connection. Fatal once the RFB handshake has
            // completed (a real session was lost); transient while the QEMU
            // listener is still coming up, because a not-yet-ready server can
            // accept and immediately close.
            return handshakeCompleted();
        }
        if (t instanceof SocketTimeoutException) {
            return false;
        }
        if (t instanceof ConnectException
                || t instanceof NoRouteToHostException
                || t instanceof PortUnreachableException) {
            return false;
        }
        if (t instanceof SocketException) {
            String msg = t.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase();
                if (m.contains("refused") || m.contains("reset")
                        || m.contains("unreachable") || m.contains("timed out")) {
                    return false;
                }
            }
        }
        if (t instanceof IOException) {
            String msg = t.getMessage();
            if (msg != null && msg.startsWith("Authentication failed")) {
                return true;
            }
            // Mid-session IO errors / unknown server messages: fail rather than
            // spin a reconnect loop against a half-broken session.
            return true;
        }
        return false;
    }

    /** Thrown by the RFB handshake when the server rejects authentication. */
    static class AuthFailureException extends IOException {
        AuthFailureException(String detail) {
            super(detail);
        }
    }

    /** Thrown by the RFB handshake when the server speaks an unsupported protocol. */
    static class ProtocolException extends IOException {
        ProtocolException(String detail) {
            super(detail);
        }
    }

    private static boolean handshakeCompleted() {
        Thread thread = Thread.currentThread();
        return thread instanceof VncThread
                && ((VncThread) thread).isHandshakeCompleted();
    }

    /** Build a non-retryable protocol/security failure. */
    static ProtocolException fatal(String message) {
        return new ProtocolException(message);
    }

    /** Build a non-retryable authentication failure (wrong password). */
    static AuthFailureException authFailed(String reason) {
        return new AuthFailureException(reason != null && !reason.isEmpty()
                ? reason : "Authentication failed");
    }

    static void logRetry(int attempt, Throwable t) {
        Log.i(TAG, "Connect attempt " + attempt + " failed (" + describe(t)
                + "), backing off; transient=" + !isFatal(t));
    }
}
