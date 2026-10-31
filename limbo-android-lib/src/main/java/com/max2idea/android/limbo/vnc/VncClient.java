package com.max2idea.android.limbo.vnc;

import android.graphics.Bitmap;
import android.util.Log;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Small RFB (VNC) client used by the built-in viewer. It speaks RFB 3.8/3.3,
 * supports the None and VNC authentication security types, requests Raw and
 * DesktopSize encodings and sends pointer/keyboard events.
 */
public class VncClient {
    private static final String TAG = "VncClient";

    /** Read timeout while connecting and during the RFB handshake. */
    private static final int HANDSHAKE_SO_TIMEOUT_MS = 20000;
    /** Read timeout during the framebuffer loop so a half-open TCP connection
     * cannot park the client thread forever. */
    private static final int FRAMEBUFFER_SO_TIMEOUT_MS = 60000;
    /** Longer timeout while the VM is still starting and may be slow to answer. */
    private static final int STARTUP_SO_TIMEOUT_MS = 60000;

    public interface Listener {
        void onConnected(int width, int height);

        void onFramebuffer(Bitmap bitmap, boolean sizeChanged);

        void onError(String message, boolean fatal);

        void onDisconnected();
    }

    private final String host;
    private final int port;
    private final String password;
    private final Listener listener;

    private Socket socket;
    private DataInputStream in;
    private OutputStream out;
    private Thread thread;
    private volatile boolean running;
    private volatile boolean sizeChanged;

    private Bitmap bitmap;
    private int width;
    private int height;
    private byte[] pixelBuffer;
    private int[] argbBuffer;

    public VncClient(String host, int port, String password, Listener listener) {
        this.host = host;
        this.port = port;
        this.password = password;
        this.listener = listener;
    }

    public void start() {
        if (thread != null) {
            return;
        }
        running = true;
        VncThread worker = new VncThread(new Runnable() {
            @Override
            public void run() {
                try {
                    connectAndRun();
                } catch (Exception e) {
                    if (running) {
                        Log.w(TAG, "VNC error", e);
                        listener.onError(describe(e), true);
                    }
                } finally {
                    closeQuietly();
                    running = false;
                    listener.onDisconnected();
                }
            }
        }, "VncClient");
        thread = worker;
        worker.setDaemon(true);
        worker.start();
    }

    public void stop() {
        running = false;
        closeQuietly();
        Thread t = thread;
        thread = null;
        if (t != null && t != Thread.currentThread()) {
            t.interrupt();
        }
    }

    /**
     * Runs only the RFB handshake (version, security, authentication) and
     * stops the client once it completed. Used by VncActivity to probe for a
     * listening QEMU VNC server without leaving a full session behind; the
     * server sees a well-formed handshake followed by a disconnect.
     *
     * @return true when the server completed the handshake (authentication
     *         included), false on timeout
     * @throws VncConnectionPolicy.AuthFailureException server rejected the password
     * @throws VncConnectionPolicy.ProtocolException      protocol/security negotiation failed
     * @throws IOException                                other handshake failure
     */
    public boolean startAndAwaitHandshake(long timeoutMs) throws IOException {
        final IOException[] failure = new IOException[1];
        final CountDownLatch done = new CountDownLatch(1);
        startHandshakeOnly(new Listener() {
            @Override
            public void onConnected(int width, int height) {
                done.countDown();
            }

            @Override
            public void onFramebuffer(Bitmap bitmap, boolean sizeChanged) {
            }

            @Override
            public void onError(String message, boolean fatal) {
                failure[0] = toHandshakeFailure(message, fatal);
                done.countDown();
            }

            @Override
            public void onDisconnected() {
                done.countDown();
            }
        });
        try {
            if (!done.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                return false;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            stop();
        }
        if (failure[0] != null) {
            throw failure[0];
        }
        return true;
    }

    private static IOException toHandshakeFailure(String message, boolean fatal) {
        if (message == null) {
            message = "Handshake failed";
        }
        if (VncConnectionPolicy.isFatalMessage(message)
                || message.startsWith("No supported security type")) {
            return VncConnectionPolicy.authFailed(message);
        }
        if (fatal) {
            return VncConnectionPolicy.fatal(message);
        }
        return new IOException(message);
    }

    private void startHandshakeOnly(final Listener handshakeListener) {
        if (thread != null) {
            return;
        }
        running = true;
        VncThread worker = new VncThread(new Runnable() {
            @Override
            public void run() {
                boolean aborted = false;
                try {
                    socket = new Socket();
                    socket.setTcpNoDelay(true);
                    socket.setSoTimeout(HANDSHAKE_SO_TIMEOUT_MS);
                    socket.connect(new InetSocketAddress(host, port), 10000);
                    in = new DataInputStream(socket.getInputStream());
                    out = socket.getOutputStream();
                    int minor = handshake();
                    int security = negotiateSecurity(minor);
                    if (security == 2) {
                        vncAuth();
                    }
                    readSecurityResult(minor, security);
                    ((VncThread) Thread.currentThread()).markHandshakeCompleted();
                } catch (Exception e) {
                    aborted = true;
                    if (running) {
                        Log.w(TAG, "VNC probe error", e);
                        reportHandshakeFailure(handshakeListener, e);
                    }
                } finally {
                    closeQuietly();
                    running = false;
                    if (!aborted) {
                        handshakeListener.onConnected(0, 0);
                    } else if (Thread.currentThread().isInterrupted()) {
                        handshakeListener.onDisconnected();
                    }
                }
            }
        }, "VncProbe");
        thread = worker;
        worker.setDaemon(true);
        worker.start();
    }

    private void reportHandshakeFailure(Listener handshakeListener, Exception e) {
        if (e instanceof VncConnectionPolicy.AuthFailureException) {
            handshakeListener.onError(describe(e), true);
        } else if (e instanceof VncConnectionPolicy.ProtocolException) {
            handshakeListener.onError(describe(e), true);
        } else {
            handshakeListener.onError(describe(e), false);
        }
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception ignored) {
        }
        socket = null;
        in = null;
        out = null;
    }

    private void connectAndRun() throws Exception {
        socket = new Socket();
        socket.setTcpNoDelay(true);
        socket.setSoTimeout(HANDSHAKE_SO_TIMEOUT_MS);
        socket.connect(new InetSocketAddress(host, port), 10000);
        in = new DataInputStream(socket.getInputStream());
        out = socket.getOutputStream();

        int minor = handshake();
        int security = negotiateSecurity(minor);
        if (security == 2) {
            vncAuth();
        }
        readSecurityResult(minor, security);
        ((VncThread) Thread.currentThread()).markHandshakeCompleted();

        // ClientInit: shared desktop
        out.write(1);
        out.flush();

        readServerInit();

        // Ask for a simple, byte-suitable format: 32bpp little-endian BGRX.
        sendSetPixelFormat();
        sendSetEncodings();

        // Never block forever on a stalled server: a read timeout lets the
        // frame loop exit instead of parking the thread on a half-open socket.
        socket.setSoTimeout(FRAMEBUFFER_SO_TIMEOUT_MS);

        listener.onConnected(width, height);

        requestUpdate(false);
        loop();
    }

    private int handshake() throws IOException {
        byte[] version = new byte[12];
        readFully(version, 0, 12);
        String serverVersion = new String(version, StandardCharsets.US_ASCII);
        int minor;
        try {
            minor = Integer.parseInt(serverVersion.substring(8, 11));
        } catch (Exception e) {
            minor = 8;
        }
        if (minor <= 3) {
            out.write("RFB 003.003\n".getBytes(StandardCharsets.US_ASCII));
            minor = 3;
        } else if (minor <= 7) {
            out.write("RFB 003.007\n".getBytes(StandardCharsets.US_ASCII));
        } else {
            out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII));
            minor = 8;
        }
        out.flush();
        return minor;
    }

    private int negotiateSecurity(int minor) throws IOException {
        if (minor >= 8) {
            int count = in.readUnsignedByte();
            if (count == 0) {
                throw VncConnectionPolicy.fatal(readReason());
            }
            byte[] types = new byte[count];
            readFully(types, 0, count);
            boolean none = false;
            boolean vnc = false;
            for (byte type : types) {
                if (type == 1) {
                    none = true;
                } else if (type == 2) {
                    vnc = true;
                }
            }
            int chosen;
            if (password != null && password.length() > 0 && vnc) {
                chosen = 2;
            } else if (none) {
                chosen = 1;
            } else if (vnc) {
                chosen = 2;
            } else {
                throw VncConnectionPolicy.fatal("No supported security type");
            }
            out.write(chosen);
            out.flush();
            return chosen;
        } else {
            int type = readInt32();
            if (type == 0) {
                throw VncConnectionPolicy.fatal(readReason());
            }
            return type;
        }
    }

    private void vncAuth() throws Exception {
        byte[] challenge = new byte[16];
        readFully(challenge, 0, 16);
        Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(vncKey(password), "DES"));
        byte[] response = cipher.doFinal(challenge);
        out.write(response);
        out.flush();
    }

    private void readSecurityResult(int minor, int security) throws IOException {
        if (minor < 8 && security == 1) {
            return;
        }
        int result = readInt32();
        if (result != 0) {
            String reason = "";
            try {
                reason = readReason();
            } catch (Exception ignored) {
            }
            throw VncConnectionPolicy.authFailed(reason);
        }
    }

    private void readServerInit() throws IOException {
        width = readInt16();
        height = readInt16();
        skip(16);
        int nameLength = readInt32();
        byte[] name = new byte[nameLength];
        readFully(name, 0, nameLength);
        Log.d(TAG, "Server init " + width + "x" + height + " " + new String(name, StandardCharsets.UTF_8));
        createBitmap(width, height);
    }

    private void sendSetPixelFormat() throws IOException {
        out.write(0); // SetPixelFormat
        out.write(new byte[3]);
        // 32 bpp, depth 24, little endian, true colour, max 255, red@16 green@8 blue@0
        byte[] pf = new byte[]{
                32, 24, 0, 1,
                0, (byte) 255, 0, (byte) 255, 0, (byte) 255,
                16, 8, 0, 0, 0, 0
        };
        out.write(pf);
        out.flush();
    }

    private void sendSetEncodings() throws IOException {
        out.write(2); // SetEncodings
        out.write(0);
        writeInt16(2);
        writeInt32(0);       // Raw
        writeInt32(-223);    // DesktopSize
        out.flush();
    }

    private void requestUpdate(boolean incremental) throws IOException {
        synchronized (this) {
            if (out == null) {
                return;
            }
            out.write(3); // FramebufferUpdateRequest
            out.write(incremental ? 1 : 0);
            writeInt16(0);
            writeInt16(0);
            writeInt16(width);
            writeInt16(height);
            out.flush();
        }
    }

    private void loop() throws IOException {
        while (running) {
            int type;
            try {
                type = in.readUnsignedByte();
            } catch (EOFException e) {
                return;
            } catch (SocketTimeoutException e) {
                if (running) {
                    throw new IOException("Connection stalled (no data from server)", e);
                }
                return;
            }
            switch (type) {
                case 0:
                    readFramebufferUpdate();
                    requestUpdate(true);
                    break;
                case 1:
                    skip(1);
                    readInt16(); // first colour
                    int colours = readInt16();
                    skip(6 * colours);
                    break;
                case 2:
                    break;
                case 3:
                    readServerCutText();
                    break;
                default:
                    // Not retryable: the server speaks something we do not
                    // understand, reconnecting would loop on the same message.
                    throw VncConnectionPolicy.fatal("Unsupported server message " + type);
            }
        }
    }

    private void readFramebufferUpdate() throws IOException {
        skip(1);
        int rects = readInt16();
        boolean changed = false;
        synchronized (this) {
            ensureBuffers();
            for (int r = 0; r < rects; r++) {
                int x = readInt16();
                int y = readInt16();
                int w = readInt16();
                int h = readInt16();
                int encoding = readInt32();
                if (encoding == 0) {
                    if (w <= 0 || h <= 0) {
                        continue;
                    }
                    int bytes = w * h * 4;
                    if (pixelBuffer.length < bytes) {
                        pixelBuffer = new byte[bytes];
                    }
                    readFully(pixelBuffer, 0, bytes);
                    if (argbBuffer.length < w * h) {
                        argbBuffer = new int[w * h];
                    }
                    int p = 0;
                    for (int i = 0; i < w * h; i++) {
                        int b = pixelBuffer[p] & 0xff;
                        int g = pixelBuffer[p + 1] & 0xff;
                        int rr = pixelBuffer[p + 2] & 0xff;
                        argbBuffer[i] = 0xff000000 | (rr << 16) | (g << 8) | b;
                        p += 4;
                    }
                    bitmap.setPixels(argbBuffer, 0, w, x, y, w, h);
                    changed = true;
                } else if (encoding == -223) {
                    if (w > 0 && h > 0) {
                        createBitmap(w, h);
                        sizeChanged = true;
                        changed = true;
                    }
                } else {
                    // Skip the rectangle instead of tearing the connection
                    // down: some servers send encodings we never requested.
                    Log.w(TAG, "Skipping rectangle with unsupported encoding "
                            + encoding + " (" + w + "x" + h + " at " + x + "," + y + ")");
                    switch (encoding) {
                        case 1: // CopyRect
                            skip(4);
                            break;
                        case 2: // RRE
                            skip(4);
                            int subRects = readInt32();
                            skip(12 + subRects * 12);
                            break;
                        case 16: // ZRLE
                            int zrleLen = readInt32();
                            skip(zrleLen);
                            break;
                        case -32: // Cursor
                            skip(w * h * 4 + ((w + 7) / 8) * 4 * ((h + 7) / 8));
                            break;
                        case -239: // DesktopName
                            int nameLen = readInt32();
                            skip(nameLen);
                            break;
                        default:
                            // Unknown or variable-length layout (Hextile, TRLE,
                            // tight, ...): the stream is no longer parseable,
                            // fail instead of corrupting the next rectangle.
                            throw VncConnectionPolicy.fatal(
                                    "Unsupported encoding " + encoding);
                    }
                }
            }
        }
        if (changed) {
            listener.onFramebuffer(bitmap, sizeChanged);
            sizeChanged = false;
        }
    }

    private void readServerCutText() throws IOException {
        skip(3);
        int length = readInt32();
        if (length > 0) {
            byte[] text = new byte[length];
            readFully(text, 0, length);
        }
    }

    private void createBitmap(int w, int h) {
        if (bitmap == null || bitmap.getWidth() != w || bitmap.getHeight() != h) {
            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            argbBuffer = new int[w * h];
            pixelBuffer = new byte[w * h * 4];
        }
        width = w;
        height = h;
    }

    private void ensureBuffers() {
        if (bitmap == null) {
            createBitmap(Math.max(width, 1), Math.max(height, 1));
        }
        if (pixelBuffer == null) {
            pixelBuffer = new byte[Math.max(1, width * height * 4)];
        }
        if (argbBuffer == null) {
            argbBuffer = new int[Math.max(1, width * height)];
        }
    }

    public synchronized void sendPointer(final int x, final int y, final int mask) {
        if (out == null) {
            return;
        }
        try {
            out.write(5); // PointerEvent
            out.write(mask);
            writeInt16(clamp(x, 65535));
            writeInt16(clamp(y, 65535));
            out.flush();
        } catch (Exception e) {
            listener.onError("Lost connection", true);
        }
    }

    public synchronized void sendKey(final int keysym, final boolean down) {
        if (out == null) {
            return;
        }
        try {
            out.write(4); // KeyEvent
            out.write(down ? 1 : 0);
            out.write(0);
            out.write(0);
            writeInt32(keysym);
            out.flush();
        } catch (Exception e) {
            listener.onError("Lost connection", true);
        }
    }

    public synchronized Bitmap peekBitmap() {
        return bitmap;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean isRunning() {
        return running;
    }

    private void writeInt16(int v) throws IOException {
        out.write((v >> 8) & 0xff);
        out.write(v & 0xff);
    }

    private void writeInt32(int v) throws IOException {
        out.write((v >> 24) & 0xff);
        out.write((v >> 16) & 0xff);
        out.write((v >> 8) & 0xff);
        out.write(v & 0xff);
    }

    private int readInt16() throws IOException {
        int hi = in.readUnsignedByte();
        int lo = in.readUnsignedByte();
        return (hi << 8) | lo;
    }

    private int readInt32() throws IOException {
        int b1 = in.readUnsignedByte();
        int b2 = in.readUnsignedByte();
        int b3 = in.readUnsignedByte();
        int b4 = in.readUnsignedByte();
        return (b1 << 24) | (b2 << 16) | (b3 << 8) | b4;
    }

    private void readFully(byte[] dst, int off, int len) throws IOException {
        int read = 0;
        while (read < len) {
            int n = in.read(dst, off + read, len - read);
            if (n < 0) {
                throw new EOFException();
            }
            read += n;
        }
    }

    private void skip(int n) throws IOException {
        int left = n;
        while (left > 0) {
            int skipped = (int) in.skip(left);
            if (skipped <= 0) {
                in.readByte();
                skipped = 1;
            }
            left -= skipped;
        }
    }

    private String readReason() throws IOException {
        int length = readInt32();
        if (length <= 0 || length > 4096) {
            // Malformed or absent reason (e.g. a 3.3 server that sent only the
            // 4-byte failure code): never consume bytes beyond the result, the
            // stream would be desynchronised for the caller.
            return "";
        }
        byte[] buf = new byte[length];
        readFully(buf, 0, length);
        return new String(buf, StandardCharsets.UTF_8);
    }

    private static int clamp(int v, int max) {
        return v < 0 ? 0 : Math.min(v, max);
    }

    static byte[] vncKey(String password) {
        byte[] key = new byte[8];
        if (password == null) {
            return key;
        }
        byte[] pw = password.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < 8; i++) {
            int b = i < pw.length ? pw[i] & 0xff : 0;
            key[i] = (byte) reverseBits(b);
        }
        return key;
    }

    static int reverseBits(int b) {
        int r = 0;
        for (int i = 0; i < 8; i++) {
            r = (r << 1) | ((b >> i) & 1);
        }
        return r & 0xff;
    }

    /**
     * Worker thread whose handshake progress {@link VncConnectionPolicy}
     * inspects to classify EOFs from a still-starting QEMU listener.
     */
    static final class VncThread extends Thread {
        private volatile boolean handshakeCompleted;

        VncThread(Runnable target, String name) {
            super(target, name);
        }

        void markHandshakeCompleted() {
            handshakeCompleted = true;
        }

        boolean isHandshakeCompleted() {
            return handshakeCompleted;
        }
    }

    private static String describe(Exception e) {
        if (e instanceof VncConnectionPolicy.AuthFailureException) {
            // Marker prefix read by VncActivity to recognise auth failures
            // without matching substrings in translated/user-supplied text.
            return "Authentication failed: " + e.getMessage();
        }
        return e.getMessage() != null ? e.getMessage() : e.toString();
    }
}
