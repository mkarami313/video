package com.appoup.videotomp3.view;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;

/** Audio waveform with two draggable handles for selecting a trim range. */
public class WaveformView extends View {

    public interface OnSelectionChanged {
        void onSelection(float startFrac, float endFrac);
    }

    private float[] amps;
    private float startFrac = 0f;
    private float endFrac = 1f;
    private OnSelectionChanged listener;

    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint barSelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF tmp = new RectF();

    private int primary = 0xFF5B3BD4;
    private int dragging = 0;  // 0 none, 1 start, 2 end
    private final float handleW = dp(10);

    public WaveformView(Context c) { super(c); init(); }
    public WaveformView(Context c, AttributeSet a) { super(c, a); init(); }
    public WaveformView(Context c, AttributeSet a, int s) { super(c, a, s); init(); }

    private void init() {
        barPaint.setColor(0xFFCBC4DD);
        barSelPaint.setColor(primary);
        dimPaint.setColor(0x33000000);
        handlePaint.setColor(primary);
        textPaint.setColor(0xFF8A8597);
        textPaint.setTextSize(dp(13));
        textPaint.setTextAlign(Paint.Align.CENTER);
    }

    public void setPrimaryColor(int color) {
        primary = color;
        barSelPaint.setColor(color);
        handlePaint.setColor(color);
        invalidate();
    }

    public void setAmplitudes(float[] a) {
        this.amps = a;
        invalidate();
    }

    public void setSelection(float start, float end) {
        startFrac = clamp(start);
        endFrac = clamp(end);
        invalidate();
    }

    public void setListener(OnSelectionChanged l) { this.listener = l; }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        float mid = h / 2f;

        if (amps == null || amps.length == 0) {
            canvas.drawText("…", w / 2f, mid + dp(5), textPaint);
            return;
        }

        int n = amps.length;
        float barGap = dp(1.5f);
        float barW = Math.max(dp(1.5f), (float) w / n - barGap);
        float sx = startFrac * w;
        float ex = endFrac * w;

        for (int i = 0; i < n; i++) {
            float x = (float) i / n * w;
            float amp = amps[i];
            float bh = Math.max(dp(2), amp * (h * 0.44f));
            boolean inSel = x >= sx && x <= ex;
            tmp.set(x, mid - bh, x + barW, mid + bh);
            canvas.drawRoundRect(tmp, barW / 2, barW / 2, inSel ? barSelPaint : barPaint);
        }

        // dim outside selection
        if (sx > 0) canvas.drawRect(0, 0, sx, h, dimPaint);
        if (ex < w) canvas.drawRect(ex, 0, w, h, dimPaint);

        // handles
        tmp.set(sx - handleW / 2, 0, sx + handleW / 2, h);
        canvas.drawRoundRect(tmp, handleW / 2, handleW / 2, handlePaint);
        tmp.set(ex - handleW / 2, 0, ex + handleW / 2, h);
        canvas.drawRoundRect(tmp, handleW / 2, handleW / 2, handlePaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (amps == null) return false;
        int w = getWidth();
        float x = e.getX();
        float sx = startFrac * w;
        float ex = endFrac * w;
        switch (e.getAction()) {
            case MotionEvent.ACTION_DOWN:
                getParent().requestDisallowInterceptTouchEvent(true);
                dragging = Math.abs(x - sx) <= Math.abs(x - ex) ? 1 : 2;
                updateDrag(x, w);
                return true;
            case MotionEvent.ACTION_MOVE:
                updateDrag(x, w);
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                getParent().requestDisallowInterceptTouchEvent(false);
                dragging = 0;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void updateDrag(float x, int w) {
        float frac = clamp(x / w);
        if (dragging == 1) {
            startFrac = Math.min(frac, endFrac - 0.01f);
            if (startFrac < 0) startFrac = 0;
        } else if (dragging == 2) {
            endFrac = Math.max(frac, startFrac + 0.01f);
            if (endFrac > 1) endFrac = 1;
        }
        if (listener != null) listener.onSelection(startFrac, endFrac);
        invalidate();
    }

    private static float clamp(float f) {
        if (f < 0) return 0; if (f > 1) return 1; return f;
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
