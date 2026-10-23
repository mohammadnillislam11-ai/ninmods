package com.ninmods.main.graphics;

import com.ninmods.main.overlay.Floater;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.util.Log;
import android.view.View;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ESPView extends View implements Runnable {

    Paint mStrokePaint;
    Paint mStrokePaint2;
    Paint mFilledPaint;
    Paint mFilledPaint2;
    Paint mTextPaint;
    Paint mTextPaint2;
    private Thread mThread;
    private volatile boolean isRunning = true;
    private static final int FPS = 60;
    private final long sleepTime = 1000 / FPS;
    Date time;
    SimpleDateFormat formatter;

    public ESPView(Context context) {
        super(context, null, 0);
        InitializePaints();
        setFocusableInTouchMode(false);
        setBackgroundColor(Color.TRANSPARENT);
        time = new Date();
        formatter = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        isRunning = true;
        mThread = new Thread(this, "ESPViewRenderThread");
        mThread.start();
    }

    public void stop() {
        isRunning = false;
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (canvas != null && getVisibility() == VISIBLE) {
            ClearCanvas(canvas);
            time.setTime(System.currentTimeMillis());
            int DEVICE_WIDTH = Floater.Width();
            int DEVICE_HEIGHT = Floater.Height();
            Floater.PxbftKZXivSr(this, canvas, DEVICE_WIDTH, DEVICE_HEIGHT);
        }
    }

    @Override
    public void run() {
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
        while (isRunning && !Thread.currentThread().isInterrupted()) {
            try {
                long t1 = System.currentTimeMillis();
                postInvalidate();
                long td = System.currentTimeMillis() - t1;
                long toSleep = sleepTime - td;
                if (toSleep > 0) {
                    Thread.sleep(toSleep);
                } else {
                    Thread.sleep(5);
                }
            } catch (InterruptedException it) {
                break;
            } catch (Exception e) {
                Log.e("ESPView", "Render error: " + e.getMessage());
            }
        }
    }

    public void InitializePaints() {
        mStrokePaint = new Paint();
        mStrokePaint.setAntiAlias(true);

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

    public void ClearCanvas(Canvas cvs) {
        cvs.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);
    }

    public void DrawLine(Canvas cvs, int a, int r, int g, int b, float lineWidth, float fromX, float fromY, float toX, float toY) {
        mStrokePaint.setColor(Color.rgb(r, g, b));
        mStrokePaint.setAlpha(a);
        mStrokePaint.setStrokeWidth(lineWidth);
        cvs.drawLine(fromX, fromY, toX, toY, mStrokePaint);
    }

    public void DrawCircle(Canvas cvs, int a, int r, int g, int b, float posX, float posY, float radius, boolean filled) {
        if (filled) {
            mFilledPaint.setColor(Color.rgb(r, g, b));
            mFilledPaint.setAlpha(a);
            cvs.drawCircle(posX, posY, radius, mFilledPaint);
        } else {
            mStrokePaint.setColor(Color.rgb(r, g, b));
            mStrokePaint.setAlpha(a);
            mStrokePaint.setStyle(Paint.Style.STROKE);
            cvs.drawCircle(posX, posY, radius, mStrokePaint);
        }
    }

    public void DrawBox(Canvas cvs, int a, int r, int g, int b, float lineWidth, float fromX, float fromY, float toX, float toY, boolean filled) {
        if (filled) {
            mFilledPaint.setColor(Color.rgb(r, g, b));
            mFilledPaint.setAlpha(a);
            cvs.drawRect(fromX, fromY, toX, toY, mFilledPaint);
        } else {
            mStrokePaint.setColor(Color.rgb(r, g, b));
            mStrokePaint.setAlpha(a);
            mStrokePaint.setStrokeWidth(lineWidth);
            mStrokePaint.setStyle(Paint.Style.STROKE);
            cvs.drawRect(fromX, fromY, toX, toY, mStrokePaint);
        }
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
        if (getRight() > 1920 || getBottom() > 1920)
            mTextPaint.setTextSize(4 + size);
        else if (getRight() == 1920 || getBottom() == 1920)
            mTextPaint.setTextSize(2 + size);
        else
            mTextPaint.setTextSize(size);

        cvs.drawText(txt, posX, posY, mTextPaint);
    }
}
