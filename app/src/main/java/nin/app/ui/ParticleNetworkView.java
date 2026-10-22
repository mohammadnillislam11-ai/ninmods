package nin.app.ui;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * High-performance blurred Pink/Magenta particle network animation background.
 * Draws an interactive glowing cyber constellation field in rich neon pink & magenta
 * with hardware-accelerated isolated view-level blur.
 * Foreground views remain 100% sharp and unaffected.
 */
public class ParticleNetworkView extends View {

    // ═══════════════════════════════════════════════════════════════
    // TUNING CONFIGURATION - VIBRANT PINK / MAGENTA CYBER GLOW
    // ═══════════════════════════════════════════════════════════════
    public static final int BG_COLOR = 0xFF0B0312;          // Deep near-black with subtle purple/pink base
    public static final float LINK_DISTANCE_DP = 110.0f;     // Connection threshold distance
    public static final float MIN_SPEED_DP = 0.12f;          // Minimum particle speed per frame
    public static final float MAX_SPEED_DP = 0.65f;          // Maximum particle speed per frame
    public static final float BLUR_RADIUS_DP = 16.0f;        // Rich deep pink frosted bokeh blur radius
    public static final float MIN_RADIUS_DP = 2.4f;          // Particle minimum radius
    public static final float MAX_RADIUS_DP = 4.6f;          // Particle maximum radius
    public static final float LINE_STROKE_DP = 1.2f;         // Connecting line stroke width
    public static final int MIN_PARTICLES = 16;              // Lower bound
    public static final int MAX_PARTICLES = 26;              // Optimal bound
    public static final float AREA_PER_PARTICLE = 55000.0f;  // Balanced constellation distribution
    public static final int MAX_DRAW_LINES = 120;            // Max batch line count

    // ═══════════════════════════════════════════════════════════════
    // STATE & PRE-ALLOCATED OBJECTS (ZERO ALLOCATIONS IN onDraw)
    // ═══════════════════════════════════════════════════════════════
    private final Paint ambientGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] particlePaints = new Paint[3];
    private final float[] linesBuffer = new float[MAX_DRAW_LINES * 4];
    private final Random random = new Random();

    private float density = 1.0f;
    private float linkDistancePx;
    private float linkDistanceSq;
    private float minSpeedPx;
    private float maxSpeedPx;
    private float lineStrokePx;
    private float blurRadiusPx;

    private int viewWidth = 0;
    private int viewHeight = 0;
    private boolean isRunning = false;

    private Particle[] particles = new Particle[0];
    private int particleCount = 0;

    private static final class Particle {
        float x;
        float y;
        float vx;
        float vy;
        float radiusPx;
        int paintIndex;
    }

    public ParticleNetworkView(Context context) {
        super(context);
        init();
    }

    public ParticleNetworkView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public ParticleNetworkView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        density = getResources().getDisplayMetrics().density;
        linkDistancePx = LINK_DISTANCE_DP * density;
        linkDistanceSq = linkDistancePx * linkDistancePx;
        minSpeedPx = MIN_SPEED_DP * density;
        maxSpeedPx = MAX_SPEED_DP * density;
        lineStrokePx = Math.max(0.5f, LINE_STROKE_DP * density);
        blurRadiusPx = Math.max(1.0f, BLUR_RADIUS_DP * density);

        ambientGlowPaint.setStyle(Paint.Style.FILL);

        // Pre-configured glowing line paint (Neon Pink with soft 26% alpha)
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(lineStrokePx);
        linePaint.setColor(0x42F76BF7);

        // Pre-configured 3-tier particle glow palette
        // 0: Neon Fuchsia Pink (#F76BF7)
        particlePaints[0] = createPaint(0xAAF76BF7);
        // 1: Electric Magenta (#E637E6)
        particlePaints[1] = createPaint(0xAAE637E6);
        // 2: Deep Cyber Violet (#D050FF)
        particlePaints[2] = createPaint(0xAAD050FF);

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // Apply isolated view-level blur (Applies ONLY to this background animation view)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Api31BlurHelper.applyBlur(this, blurRadiusPx);
        } else {
            try {
                BlurMaskFilter filter = new BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL);
                particlePaints[0].setMaskFilter(filter);
                particlePaints[1].setMaskFilter(filter);
                particlePaints[2].setMaskFilter(filter);
            } catch (Throwable ignored) {}
        }
    }

    private static class Api31BlurHelper {
        static void applyBlur(View view, float blurRadiusPx) {
            try {
                view.setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP));
            } catch (Throwable ignored) {}
        }
    }

    private Paint createPaint(int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.FILL);
        p.setColor(color);
        return p;
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        viewWidth = w;
        viewHeight = h;
        if (w > 0 && h > 0) {
            // Soft ambient pink glow centered behind the upper screen area
            float cx = w * 0.5f;
            float cy = h * 0.32f;
            float radius = Math.max(w, h) * 0.65f;
            RadialGradient glow = new RadialGradient(
                    cx, cy, radius,
                    new int[]{0x2BF76BF7, 0x12E637E6, 0x00000000},
                    new float[]{0.0f, 0.45f, 1.0f},
                    Shader.TileMode.CLAMP
            );
            ambientGlowPaint.setShader(glow);

            initParticles(w, h);
        }
    }

    private void initParticles(int width, int height) {
        float area = (float) width * (float) height;
        int count = (int) (area / AREA_PER_PARTICLE);
        if (count < MIN_PARTICLES) count = MIN_PARTICLES;
        if (count > MAX_PARTICLES) count = MAX_PARTICLES;

        particleCount = count;
        if (particles.length != count) {
            particles = new Particle[count];
            for (int i = 0; i < count; i++) {
                particles[i] = new Particle();
            }
        }

        for (int i = 0; i < count; i++) {
            Particle p = particles[i];
            p.radiusPx = (MIN_RADIUS_DP + random.nextFloat() * (MAX_RADIUS_DP - MIN_RADIUS_DP)) * density;
            p.x = p.radiusPx + random.nextFloat() * (width - 2 * p.radiusPx);
            p.y = p.radiusPx + random.nextFloat() * (height - 2 * p.radiusPx);

            float speed = minSpeedPx + random.nextFloat() * (maxSpeedPx - minSpeedPx);
            double angle = random.nextDouble() * 2.0 * Math.PI;
            p.vx = (float) (speed * Math.cos(angle));
            p.vy = (float) (speed * Math.sin(angle));
            p.paintIndex = random.nextInt(3);
        }
    }

    private void updateParticles() {
        if (viewWidth <= 0 || viewHeight <= 0) return;
        for (int i = 0; i < particleCount; i++) {
            Particle p = particles[i];
            p.x += p.vx;
            p.y += p.vy;

            // Bounce off screen boundaries
            if (p.x - p.radiusPx < 0) {
                p.x = p.radiusPx;
                p.vx = Math.abs(p.vx);
            } else if (p.x + p.radiusPx > viewWidth) {
                p.x = viewWidth - p.radiusPx;
                p.vx = -Math.abs(p.vx);
            }

            if (p.y - p.radiusPx < 0) {
                p.y = p.radiusPx;
                p.vy = Math.abs(p.vy);
            } else if (p.y + p.radiusPx > viewHeight) {
                p.y = viewHeight - p.radiusPx;
                p.vy = -Math.abs(p.vy);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (viewWidth <= 0 || viewHeight <= 0 || particleCount == 0) return;

        // 1. Instant GPU clear color backdrop (0ms)
        canvas.drawColor(BG_COLOR);

        // 2. Ambient soft pink cyber aura
        canvas.drawRect(0, 0, viewWidth, viewHeight, ambientGlowPaint);

        // 3. Batch connecting lines in vibrant Pink/Magenta (Fast bounding-box rejection + single GPU drawLines call)
        int lineCount = 0;
        for (int i = 0; i < particleCount; i++) {
            Particle p1 = particles[i];
            for (int j = i + 1; j < particleCount; j++) {
                Particle p2 = particles[j];
                float dx = p1.x - p2.x;
                if (dx > linkDistancePx || dx < -linkDistancePx) continue;
                float dy = p1.y - p2.y;
                if (dy > linkDistancePx || dy < -linkDistancePx) continue;

                float distSq = dx * dx + dy * dy;
                if (distSq < linkDistanceSq && lineCount < MAX_DRAW_LINES) {
                    int idx = lineCount * 4;
                    linesBuffer[idx] = p1.x;
                    linesBuffer[idx + 1] = p1.y;
                    linesBuffer[idx + 2] = p2.x;
                    linesBuffer[idx + 3] = p2.y;
                    lineCount++;
                }
            }
        }

        if (lineCount > 0) {
            canvas.drawLines(linesBuffer, 0, lineCount * 4, linePaint);
        }

        // 4. Draw glowing pink particles
        for (int i = 0; i < particleCount; i++) {
            Particle p = particles[i];
            canvas.drawCircle(p.x, p.y, p.radiusPx, particlePaints[p.paintIndex]);
        }

        // 5. Update physics and request next vsync frame
        if (isRunning) {
            updateParticles();
            postInvalidateOnAnimation();
        }
    }

    public void start() {
        if (!isRunning) {
            isRunning = true;
            postInvalidateOnAnimation();
        }
    }

    public void stop() {
        isRunning = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (getVisibility() == VISIBLE && getWindowVisibility() == VISIBLE) {
            start();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stop();
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (visibility == VISIBLE && getVisibility() == VISIBLE) {
            start();
        } else {
            stop();
        }
    }

    @Override
    protected void onVisibilityChanged(View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && getWindowVisibility() == VISIBLE) {
            start();
        } else {
            stop();
        }
    }
}
