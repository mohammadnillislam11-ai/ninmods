package com.ninmods.main.graphics;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLES3JNIView extends GLSurfaceView implements GLSurfaceView.Renderer {

    /* The native library must be loaded before ANY native method of this class
     * is called. It used to be loaded only by NativeBridge's static block, so
     * the ESP surface silently died with an UnsatisfiedLinkError whenever the
     * GL surface was created before NativeBridge was touched. Loading it here
     * as well is idempotent (System.loadLibrary is a no-op when the library is
     * already loaded in this process). */
    static {
        try {
            System.loadLibrary("ninmods");
        } catch (UnsatisfiedLinkError e) {
            Log.e("GLES3JNIView", "Failed to load libninmods.so - the ESP surface cannot render", e);
        }
    }

    public GLES3JNIView(Context context) {
        super(context);
        initView();
    }

    public GLES3JNIView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    private void initView() {
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderMediaOverlay(true);
        setFocusable(false);
        setFocusableInTouchMode(false);
        setClickable(false);
        setLongClickable(false);
        setFilterTouchesWhenObscured(false);
        setEGLContextClientVersion(3);
        setRenderer(this);
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        if (event == null) return false;
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        return nativeTouchEvent(action, x, y);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent event) {
        return super.dispatchTouchEvent(event);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        float density = 2.4f;
        try {
            density = getResources().getDisplayMetrics().density;
        } catch (Throwable ignored) {}
        nativeInit(density);
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        nativeResize(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        nativeStep();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        nativeShutdown();
    }

    public static native void nativeInit(float density);
    public static native void nativeResize(int width, int height);
    public static native void nativeStep();
    public static native void nativeShutdown();
    public static native boolean nativeTouchEvent(int action, float x, float y);
}
