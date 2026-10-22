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
    public static final int LINE_BASE_COLOR = 0x00F76BF7;   // Neon Pink line base (#F76BF7)
    public static final float LINK_DISTANCE_DP = 140.0f;     // Connection threshold distance
    public static final float MIN_SPEED_DP = 0.12f;          // Minimum particle speed per frame
    public static final float MAX_SPEED_DP = 0.75f;          // Maximum particle speed per frame
    public static final float BLUR_RADIUS_DP = 18.0f;        // Rich deep pink frosted bokeh blur radius
    public static final float MIN_RADIUS_DP = 2.2f;          // Particle minimum radius
    public static final float MAX_RADIUS_DP = 4.8f;          // Particle maximum radius
    public static final float LINE_STROKE_DP = 1.2f;         // Connecting line stroke width
    public static final int MIN_PARTICLES = 22;              // Lower bound for small screens
    public static final int MAX_PARTICLES = 65;              // Upper bound to prevent overdraw
    public static final float AREA_PER_PARTICLE = 20000.0f;  // Screen area per particle ratio

    // ═══════════════════════════════════════════════════════════════
    // STATE & PRE-ALLOCATED OBJECTS (ZERO ALLOCATIONS IN onDraw)
    // ═══════════════════════════════════════════════════════════════
    private final Paint bgPaint = new Paint();
    private final Paint ambientGlowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint particlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();

    private float density = 1.0f;
    private float linkDistancePx;
    private float linkDistanceSq;
    private float minSpeedPx;
    private float maxSpeedPx;
    private float blurRadiusPx;
    private float lineStrokePx;

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
        int color;
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
        blurRadiusPx = Math.max(1.0f, BLUR_RADIUS_DP * density);
        lineStrokePx = Math.max(0.5f, LINE_STROKE_DP * density);

        bgPaint.setColor(BG_COLOR);
        bgPaint.setStyle(Paint.Style.FILL);

        ambientGlowPaint.setStyle(Paint.Style.FILL);

        particlePaint.setStyle(Paint.Style.FILL);

        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(lineStrokePx);

        // Enable hardware acceleration
        setLayerType(LAYER_TYPE_HARDWARE, null);

        // Apply isolated view-level blur (Applies ONLY to this view's canvas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Api31BlurHelper.applyBlur(this, blurRadiusPx);
        } else {
            // Fallback for API < 31: MaskFilter on particle paint
            try {
                particlePaint.setMaskFilter(new BlurMaskFilter(blurRadiusPx, BlurMaskFilter.Blur.NORMAL));
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

        float[] hsv = new float[3];
        for (int i = 0; i < count; i++) {
            Particle p = particles[i];
            p.radiusPx = (MIN_RADIUS_DP + random.nextFloat() * (MAX_RADIUS_DP - MIN_RADIUS_DP)) * density;
            p.x = p.radiusPx + random.nextFloat() * (width - 2 * p.radiusPx);
            p.y = p.radiusPx + random.nextFloat() * (height - 2 * p.radiusPx);

            float speed = minSpeedPx + random.nextFloat() * (maxSpeedPx - minSpeedPx);
            double angle = random.nextDouble() * 2.0 * Math.PI;
            p.vx = (float) (speed * Math.cos(angle));
            p.vy = (float) (speed * Math.sin(angle));

            // Vibrant Pink / Magenta / Neon Fuchsia (Hue 285 to 335)
            hsv[0] = 285.0f + random.nextFloat() * (335.0f - 285.0f);
            hsv[1] = 0.65f + random.nextFloat() * 0.35f; // High vibrancy
            hsv[2] = 0.90f + random.nextFloat() * 0.10f; // Glowing bright
            int baseColor = Color.HSVToColor(hsv);

            // Alpha: 0.35 to 0.75 for rich glowing pink constellation
            float alphaFloat = 0.35f + random.nextFloat() * 0.40f;
            int alpha = (int) (alphaFloat * 255.0f);
            p.color = (alpha << 24) | (baseColor & 0x00FFFFFF);
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

        // 1. Draw base canvas background
        canvas.drawRect(0, 0, viewWidth, viewHeight, bgPaint);

        // 2. Draw soft ambient pink aura
        canvas.drawRect(0, 0, viewWidth, viewHeight, ambientGlowPaint);

        // 3. Draw connecting network lines in vibrant Pink/Magenta (fading alpha 0.22 -> 0)
        for (int i = 0; i < particleCount; i++) {
            Particle p1 = particles[i];
            for (int j = i + 1; j < particleCount; j++) {
                Particle p2 = particles[j];
                float dx = p1.x - p2.x;
                float dy = p1.y - p2.y;
                float distSq = dx * dx + dy * dy;

                if (distSq < linkDistanceSq) {
                    float dist = (float) Math.sqrt(distSq);
                    float fraction = 1.0f - (dist / linkDistancePx);
                    int alpha = (int) (fraction * 0.32f * 255.0f);
                    if (alpha > 0) {
                        // Color: rgba(247, 107, 247, alpha) - Neon Pink
                        linePaint.setColor((alpha << 24) | LINE_BASE_COLOR);
                        canvas.drawLine(p1.x, p1.y, p2.x, p2.y, linePaint);
                    }
                }
            }
        }

        // 4. Draw glowing pink particles
        for (int i = 0; i < particleCount; i++) {
            Particle p = particles[i];
            particlePaint.setColor(p.color);
            canvas.drawCircle(p.x, p.y, p.radiusPx, particlePaint);
        }

        // 5. Update and request next vsync frame
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
