package com.ninmods.main.esp;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.Log;
import android.view.View;

import com.ninmods.main.core.NativeBridge;

/**
 * ESPView
 * -------
 * Native Canvas ESP Rendering Engine (the drawing runs 100% in the native layer).
 *
 * Runs an asynchronous 60 FPS render loop and invokes C++ DrawESP via JNI
 * to render boxes, lines, health bars, FOV circles, and distance labels onto the Canvas.
 */
public class ESPView extends View implements Runnable {
    private static final String TAG = "ESPView";

    private Paint mStrokePaint;
    private Paint mStrokePaint2;
    private Paint mFilledPaint;
    private Paint mFilledPaint2;
    private Paint mTextPaint;
    private Paint mTextPaint2;

    private Thread mThread;
    private volatile boolean mRunning = false;
    private final int mFps = 60;
    private final long mSleepTime = 1000 / mFps;

    public ESPView(Context context) {
        super(context, null, 0);
        initializePaints();
        setFocusable(false);
        setFocusableInTouchMode(false);
        setBackgroundColor(Color.TRANSPARENT);
    }

    public synchronized void start() {
        if (mRunning) return;
        mRunning = true;
        mThread = new Thread(this, "ESPViewRenderThread");
        mThread.start();
    }

    public synchronized void stop() {
        mRunning = false;
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (canvas != null && getVisibility() == VISIBLE) {
            clearCanvas(canvas);
            int width = getWidth();
            int height = getHeight();
            if (width <= 0) width = getResources().getDisplayMetrics().widthPixels;
            if (height <= 0) height = getResources().getDisplayMetrics().heightPixels;

            // Invoke the native ESP drawing (the native draw pipeline)
            NativeBridge.drawEspNative(this, canvas, width, height);
        }
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        while (mRunning && !Thread.currentThread().isInterrupted()) {
            try {
                long t1 = System.currentTimeMillis();
                postInvalidate();
                long td = System.currentTimeMillis() - t1;
                long sleep = Math.max(10, mSleepTime - td);
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                break;
            } catch (Throwable t) {
                Log.e(TAG, "Render loop error: " + t.getMessage());
            }
        }
    }

    public void initializePaints() {
        mStrokePaint = new Paint();
        mStrokePaint.setAntiAlias(true);
        mStrokePaint.setStyle(Paint.Style.STROKE);

        mStrokePaint2 = new Paint();
        mStrokePaint2.setStyle(Paint.Style.STROKE);
        mStrokePaint2.setAntiAlias(true);

        mFilledPaint = new Paint();
        mFilledPaint.setStyle(Paint.Style.FILL);
        mFilledPaint.setAntiAlias(true);

        mFilledPaint2 = new Paint();
        mFilledPaint2.setStyle(Paint.Style.FILL);
        mFilledPaint2.setAntiAlias(true);
        mFilledPaint2.setColor(Color.rgb(0, 0, 0));

        mTextPaint = new Paint();
        mTextPaint.setAntiAlias(true);
        mTextPaint.setTextAlign(Paint.Align.CENTER);

        mTextPaint2 = new Paint();
        mTextPaint2.setAntiAlias(true);
        mTextPaint2.setTextAlign(Paint.Align.CENTER);
    }

    public void clearCanvas(Canvas cvs) {
        cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    // --- JNI native callbacks ---

    public void DrawLine(Canvas cvs, int a, int r, int g, int b, float lineWidth, float fromX, float fromY, float toX, float toY) {
        mStrokePaint.setColor(Color.rgb(r, g, b));
        mStrokePaint.setAlpha(a);
        mStrokePaint.setStrokeWidth(lineWidth);
        cvs.drawLine(fromX, fromY, toX, toY, mStrokePaint);
    }

    public void DrawCircle(Canvas cvs, int a, int r, int g, int b, float stroke, float posX, float posY, float radius) {
        mStrokePaint2.setColor(Color.rgb(r, g, b));
        mStrokePaint2.setAlpha(a);
        mStrokePaint2.setStrokeWidth(stroke);
        cvs.drawCircle(posX, posY, radius, mStrokePaint2);
    }

    public void DrawFilledCircle(Canvas cvs, int a, int r, int g, int b, float posX, float posY, float radius) {
        mFilledPaint.setColor(Color.rgb(r, g, b));
        mFilledPaint.setAlpha(a);
        cvs.drawCircle(posX, posY, radius, mFilledPaint);
    }

    public void DrawName(Canvas cvs, int a, int r, int g, int b, String nametxt, int teamid, float posX, float posY, float size) {
        mTextPaint.setARGB(a, r, g, b);
        mTextPaint2.setColor(Color.argb(0x9A, 0, 0, 0));
        cvs.drawRect(posX - 120 - (size + 13), posY - 18 - size, posX + 50 + (size + 15), posY + 10, mTextPaint2);
        mTextPaint.setColor(Color.argb(255, 0, 255, 0));
        cvs.drawRect(posX - 135 - size, posY - 15 - size, posX - 128 + size, posY + 6, mTextPaint);
        mTextPaint.setTextSize(15 + size);
        mTextPaint.setColor(Color.WHITE);
        cvs.drawText(nametxt, posX, posY, mTextPaint);
        cvs.drawText(" " + teamid, posX - 134, posY, mTextPaint);
    }

    public void DrawFilledRect(Canvas cvs, int a, int r, int g, int b, float x, float y, float width, float height) {
        mFilledPaint.setColor(Color.rgb(r, g, b));
        mFilledPaint.setAlpha(a);
        cvs.drawRect(x, y, x + width, y + height, mFilledPaint);
    }

    public void DrawText(Canvas cvs, int a, int r, int g, int b, String txt, float posX, float posY, float size) {
        mTextPaint.setColor(Color.rgb(r, g, b));
        mTextPaint.setAlpha(a);
        mTextPaint.setTextSize(size);
        cvs.drawText(txt, posX, posY, mTextPaint);
    }
}
