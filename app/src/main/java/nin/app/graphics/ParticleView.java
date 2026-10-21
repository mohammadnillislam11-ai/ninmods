package nin.app.graphics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import java.util.ArrayList;
import java.util.Random;

import nin.app.R;
import nin.app.core.AppColors;

public class ParticleView extends View {
    private ArrayList<Particle> particles;
    private Paint paint;
    private Random random;
    private int width, height;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Runnable updateRunnable = new Runnable() {
        @Override
        public void run() {
            updateParticles();
            invalidate();
            handler.postDelayed(this, 16); // ~60 FPS
        }
    };
    private boolean isRunning = false;

    public ParticleView(Context context) {
        super(context);
        init();
    }

    private void init() {
        particles = new ArrayList<>();
        paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        random = new Random();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        width = w;
        height = h;
        createParticles(60); // 60 particles for smooth performance
    }

    private void createParticles(int count) {
        int[] colors = {
                AppColors.get(getContext(), R.color.particle_glow_1, AppColors.PARTICLE_GLOW_1),
                AppColors.get(getContext(), R.color.particle_glow_2, AppColors.PARTICLE_GLOW_2),
                AppColors.get(getContext(), R.color.white, AppColors.WHITE)
        };
        for (int i = 0; i < count; i++) {
            particles.add(new Particle(
                    random.nextInt(width),
                    random.nextInt(height),
                    0.8f + random.nextFloat() * 2.5f,
                    2 + random.nextInt(6),
                    40 + random.nextInt(110),
                    colors[random.nextInt(colors.length)]
            ));
        }
    }

    private void updateParticles() {
        for (Particle p : particles) {
            p.y += p.speed;
            if (p.y > height) {
                p.y = -p.size;
                p.x = random.nextInt(width);
            }
            // Gentle horizontal drift
            p.x += (random.nextFloat() - 0.5f) * 0.6f;
            if (p.x < 0) p.x = width;
            if (p.x > width) p.x = 0;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        for (Particle p : particles) {
            paint.setColor(p.color);
            paint.setAlpha(p.alpha);
            paint.setStyle(Paint.Style.FILL);
            canvas.drawCircle(p.x, p.y, p.size, paint);
        }
    }

    public void startAnimation() {
        if (!isRunning) {
            isRunning = true;
            handler.post(updateRunnable);
        }
    }

    public void stopAnimation() {
        isRunning = false;
        handler.removeCallbacks(updateRunnable);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    private static class Particle {
        float x, y, speed, size;
        int alpha, color;
        Particle(float x, float y, float speed, float size, int alpha, int color) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.size = size;
            this.alpha = alpha;
            this.color = color;
        }
    }
}
