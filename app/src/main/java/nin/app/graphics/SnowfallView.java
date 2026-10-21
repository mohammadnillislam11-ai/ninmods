package nin.app.graphics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SnowfallView extends View {

    private static class Snowflake {
        float x;
        float y;
        float radius;
        float speed;
        float drift;
        float alpha;
    }

    private final Paint snowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final List<Snowflake> snowflakes = new ArrayList<>();
    private boolean running;

    public SnowfallView(Context context) {
        super(context);
        snowPaint.setColor(nin.app.core.AppColors.get(context, nin.app.R.color.snow_particle, nin.app.core.AppColors.SNOW_PARTICLE));
        snowPaint.setStyle(Paint.Style.FILL);
    }

    public void start() {
        running = true;
        invalidate();
    }

    public void stop() {
        running = false;
    }

    private void ensureSnowflakes() {
        if (!snowflakes.isEmpty()) return;
        int width = Math.max(getWidth(), 1);
        int height = Math.max(getHeight(), 1);
        for (int i = 0; i < 32; i++) {
            Snowflake flake = new Snowflake();
            flake.x = random.nextInt(width);
            flake.y = random.nextInt(height);
            flake.radius = 1.6f + random.nextFloat() * 3.4f;
            flake.speed = 0.9f + random.nextFloat() * 2.2f;
            flake.drift = -0.6f + random.nextFloat() * 1.2f;
            flake.alpha = 0.45f + random.nextFloat() * 0.45f;
            snowflakes.add(flake);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!running) return;
        ensureSnowflakes();

        int width = Math.max(getWidth(), 1);
        int height = Math.max(getHeight(), 1);

        for (Snowflake flake : snowflakes) {
            flake.y += flake.speed;
            flake.x += flake.drift;

            if (flake.y > height + flake.radius) {
                flake.y = -flake.radius;
                flake.x = random.nextInt(width);
            }

            if (flake.x < -flake.radius) flake.x = width + flake.radius;
            if (flake.x > width + flake.radius) flake.x = -flake.radius;

            snowPaint.setAlpha((int) (flake.alpha * 255));
            canvas.drawCircle(flake.x, flake.y, flake.radius, snowPaint);
        }
        postInvalidateOnAnimation();
    }
}
