package com.max2idea.android.limbo.vnc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import com.max2idea.android.limbo.keyboard.KeyMapping;

/** Draws the remote framebuffer letterboxed and maps touches back to VM pixels. */
public class VncView extends View {
    private VncClient client;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private int lastMask;
    private long downTime;
    private int lastPointerX;
    private int lastPointerY;
    /** Set while the IME is up so we swallow the touch that dismissed it. */
    private boolean imeVisible;

    public VncView(Context context) {
        super(context);
        init();
    }

    /*
     * XML layouts instantiate custom views through the (Context, AttributeSet)
     * constructor. Without it LayoutInflater throws
     *   InflateException: Binary XML file line #9: Error inflating class
     *   com.max2idea.android.limbo.vnc.VncView
     * and the process dies as soon as VncActivity does setContentView(), which
     * is exactly what "tap 进入画面 with Display=VNC" used to do.
     */
    public VncView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public VncView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        setBackgroundColor(Color.BLACK);
        // The view itself is the IME target, so it has to be able to take the
        // focus. setFocusableInTouchMode is what makes requestFocus() work
        // while the activity is in touch mode.
        setFocusable(true);
        setFocusableInTouchMode(true);
    }

    public void setClient(VncClient client) {
        this.client = client;
    }

    public void refresh() {
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (client == null) {
            return;
        }
        synchronized (client) {
            Bitmap bitmap = client.peekBitmap();
            if (bitmap == null || bitmap.isRecycled()) {
                return;
            }
            computeDst(getWidth(), getHeight(), bitmap.getWidth(), bitmap.getHeight());
            canvas.drawBitmap(bitmap, null, dst, paint);
        }
    }

    private void computeDst(int viewW, int viewH, int bmpW, int bmpH) {
        if (bmpW <= 0 || bmpH <= 0) {
            return;
        }
        float scale = Math.min((float) viewW / bmpW, (float) viewH / bmpH);
        float w = bmpW * scale;
        float h = bmpH * scale;
        float left = (viewW - w) / 2f;
        float top = (viewH - h) / 2f;
        dst.set(left, top, left + w, top + h);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (client == null || client.getWidth() <= 0) {
            return true;
        }
        // While the soft keyboard is up, the first touch on the framebuffer
        // dismisses it instead of also being sent to the guest: otherwise every
        // attempt to close the keyboard would click inside the remote desktop.
        if (imeVisible && event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            hideIme();
            return true;
        }
        float x = (event.getX() - dst.left) / Math.max(dst.width(), 1f) * client.getWidth();
        float y = (event.getY() - dst.top) / Math.max(dst.height(), 1f) * client.getHeight();
        int bx = Math.max(0, Math.min(client.getWidth() - 1, Math.round(x)));
        int by = Math.max(0, Math.min(client.getHeight() - 1, Math.round(y)));

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downTime = System.currentTimeMillis();
                lastPointerX = bx;
                lastPointerY = by;
                lastMask = 1;
                client.sendPointer(bx, by, lastMask);
                postDelayed(longPressRunnable, 520);
                break;
            case MotionEvent.ACTION_MOVE:
                lastPointerX = bx;
                lastPointerY = by;
                client.sendPointer(bx, by, lastMask);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                removeCallbacks(longPressRunnable);
                client.sendPointer(bx, by, 0);
                lastMask = 0;
                break;
        }
        return true;
    }

    private final Runnable longPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (lastMask != 0 && System.currentTimeMillis() - downTime >= 500) {
                // Two-finger tap equivalent: a right click at the last position.
                client.sendPointer(lastPointerX, lastPointerY, 4);
                client.sendPointer(lastPointerX, lastPointerY, 0);
                lastMask = 0;
            }
        }
    };

    /* ------------------------------------------------------------------ */
    /* Soft keyboard (IME) support                                          */
    /* ------------------------------------------------------------------ */

    /**
     * Brings up the system soft keyboard and makes this view the IME target, so
     * that every keystroke is forwarded to the remote machine as a VNC key
     * event ("type while you type").
     *
     * <p>Must be called from the UI thread.
     */
    public void showIme() {
        if (!isFocusable() || !isFocusableInTouchMode()) {
            setFocusable(true);
            setFocusableInTouchMode(true);
        }
        requestFocus();
        InputMethodManager imm = inputMethodManager();
        if (imm == null) {
            return;
        }
        if (imm.isActive(this)) {
            // Already up, nothing to do.
            imeVisible = true;
            return;
        }
        imm.showSoftInput(this, InputMethodManager.SHOW_FORCED);
        imeVisible = true;
    }

    /**
     * Hides the system soft keyboard.
     *
     * <p>Must be called from the UI thread.
     */
    public void hideIme() {
        InputMethodManager imm = inputMethodManager();
        if (imm != null) {
            imm.hideSoftInputFromWindow(getWindowToken(), 0);
        }
        imeVisible = false;
    }

    /** @return true when we believe the soft keyboard is currently shown. */
    public boolean isImeVisible() {
        return imeVisible;
    }

    private InputMethodManager inputMethodManager() {
        Context context = getContext();
        if (context == null) {
            return null;
        }
        return (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public boolean onCheckIsTextEditor() {
        return true;
    }

    /**
     * The IME talks to the view through an InputConnection. We override it so
     * that the typed text and the key events are converted into VNC key events
     * instead of being inserted into an editable buffer.
     */
    @Override
    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
        if (outAttrs != null) {
            outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT
                    | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
            outAttrs.imeOptions = EditorInfo.IME_ACTION_NONE
                    | EditorInfo.IME_FLAG_NO_FULLSCREEN;
        }
        return new VncInputConnection(this);
    }

    /**
     * Sends one keysym press/release pair to the remote machine.
     */
    void sendKeysym(int keysym) {
        if (client == null || keysym == 0) {
            return;
        }
        client.sendKey(keysym, true);
        client.sendKey(keysym, false);
    }

    /**
     * Sends an Android key event (down and/or up) to the remote machine using
     * the shared key mapping.
     */
    void sendAndroidKeyEvent(KeyEvent event) {
        if (client == null || event == null) {
            return;
        }
        int keysym = KeyMapping.keysymFor(event.getKeyCode());
        if (keysym == 0) {
            return;
        }
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN) {
            client.sendKey(keysym, true);
        } else if (action == KeyEvent.ACTION_UP) {
            client.sendKey(keysym, false);
        }
    }

    /**
     * InputConnection that forwards everything the IME produces to the remote
     * machine as VNC key events.
     */
    private static final class VncInputConnection extends BaseInputConnection {
        private final VncView view;

        VncInputConnection(VncView view) {
            super(view, true);
            this.view = view;
        }

        @Override
        public boolean commitText(CharSequence text, int newCursorPosition) {
            if (text == null) {
                return true;
            }
            for (int i = 0; i < text.length(); i++) {
                view.sendKeysym(KeyMapping.keysymForChar(text.charAt(i)));
            }
            return true;
        }

        @Override
        public boolean deleteSurroundingText(int beforeLength, int afterLength) {
            // The remote machine has no cursor we can move, so a deletion is
            // simply a backspace.
            int count = Math.max(beforeLength, afterLength);
            for (int i = 0; i < count; i++) {
                view.sendKeysym(KeyMapping.KEYSYM_BACKSPACE);
            }
            return true;
        }

        @Override
        public boolean sendKeyEvent(KeyEvent event) {
            view.sendAndroidKeyEvent(event);
            return true;
        }

        @Override
        public boolean performContextMenuAction(int id) {
            return false;
        }

        @Override
        public boolean beginBatchEdit() {
            return true;
        }

        @Override
        public boolean endBatchEdit() {
            return true;
        }

        @Override
        public boolean setComposingText(CharSequence text, int newCursorPosition) {
            // Composing text is previewed by the IME itself; the final text
            // arrives through commitText(). Forwarding the preview as well
            // would type every character twice.
            return true;
        }
    }
}
