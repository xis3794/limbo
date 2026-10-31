package com.max2idea.android.limbo.vnc;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Draws the remote framebuffer letterboxed and maps touches back to VM pixels. */
public class VncView extends View {
    private VncClient client;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final RectF dst = new RectF();
    private int lastMask;
    private long downTime;
    private int lastPointerX;
    private int lastPointerY;

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
}
