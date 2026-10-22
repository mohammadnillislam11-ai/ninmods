package nin.app.graphics;

import android.content.Context;
import android.graphics.PixelFormat;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GLES3JNIView extends GLSurfaceView implements GLSurfaceView.Renderer {

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
        setZOrderOnTop(true);
        setEGLContextClientVersion(3);
        setRenderer(this);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        nativeInit();
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

    public static native void nativeInit();
    public static native void nativeResize(int width, int height);
    public static native void nativeStep();
    public static native void nativeShutdown();
}
