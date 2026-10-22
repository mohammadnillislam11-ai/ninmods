package nin.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;

import nin.app.core.AppColors;

/**
 * Premium Tactical Graphic Body Background.
 * Renders a rich cyber-matrix mesh with soft ambient glow, tactical grid lines,
 * and intersection point markers, replacing plain solid black backgrounds with
 * a stunning tactical gaming interface aesthetic.
 */
public class TacticalGraphicBackground extends Drawable {

    private final Context context;
    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accentPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int gridStep;
    private int dotRadius;

    public TacticalGraphicBackground(Context context) {
        this.context = context;
        init();
    }

    private void init() {
        gridStep = dp(34);
        dotRadius = Math.max(1, dp(1.2f));

        // Background dark gradient base
        bgPaint.setColor(0xFF09040F);

        // Ambient cyber glow
        glowPaint.setStyle(Paint.Style.FILL);

        // Tactical subtle grid lines
        gridPaint.setColor(0x14E637E6); // ~8% opacity magenta
        gridPaint.setStrokeWidth(dp(0.8f));
        gridPaint.setStyle(Paint.Style.STROKE);

        // Grid intersection dots
        dotPaint.setColor(0x35E637E6); // ~20% opacity magenta
        dotPaint.setStyle(Paint.Style.FILL);

        // Subtle corner geometric ticks
        accentPaint.setColor(0x28F76BF7);
        accentPaint.setStrokeWidth(dp(1.2f));
        accentPaint.setStyle(Paint.Style.STROKE);
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        super.onBoundsChange(bounds);
        if (bounds.width() > 0 && bounds.height() > 0) {
            float cx = bounds.width() / 2.0f;
            float cy = bounds.height() * 0.22f; // Glow concentrated behind header/logo
            float radius = Math.max(bounds.width(), bounds.height()) * 0.65f;

            RadialGradient glow = new RadialGradient(
                    cx, cy, radius,
                    new int[]{0x2DE637E6, 0x141A0626, 0x00000000},
                    new float[]{0.0f, 0.45f, 1.0f},
                    Shader.TileMode.CLAMP
            );
            glowPaint.setShader(glow);
        }
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) return;

        // 1. Base Dark Surface
        canvas.drawRect(bounds, bgPaint);

        // 2. Ambient Aura Glow behind logo area
        canvas.drawRect(bounds, glowPaint);

        // 3. Cyber Matrix Grid Lines
        int width = bounds.width();
        int height = bounds.height();

        for (int x = gridStep; x < width; x += gridStep) {
            canvas.drawLine(x, 0, x, height, gridPaint);
        }

        for (int y = gridStep; y < height; y += gridStep) {
            canvas.drawLine(0, y, width, y, gridPaint);
        }

        // 4. Subtle Tactical Intersection Markers
        int stepCount = 0;
        for (int x = gridStep; x < width; x += gridStep) {
            for (int y = gridStep; y < height; y += gridStep) {
                if ((stepCount % 2) == 0) {
                    canvas.drawCircle(x, y, dotRadius, dotPaint);
                }
                stepCount++;
            }
        }

        // 5. Corner Tactical Brackets
        float bracketSize = dp(20);
        float pad = dp(14);

        // Top-Left
        canvas.drawLine(pad, pad, pad + bracketSize, pad, accentPaint);
        canvas.drawLine(pad, pad, pad, pad + bracketSize, accentPaint);

        // Top-Right
        canvas.drawLine(width - pad, pad, width - pad - bracketSize, pad, accentPaint);
        canvas.drawLine(width - pad, pad, width - pad, pad + bracketSize, accentPaint);

        // Bottom-Left
        canvas.drawLine(pad, height - pad, pad + bracketSize, height - pad, accentPaint);
        canvas.drawLine(pad, height - pad, pad, height - pad - bracketSize, accentPaint);

        // Bottom-Right
        canvas.drawLine(width - pad, height - pad, width - pad - bracketSize, height - pad, accentPaint);
        canvas.drawLine(width - pad, height - pad, width - pad, height - pad - bracketSize, accentPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        bgPaint.setAlpha(alpha);
        glowPaint.setAlpha(alpha);
        gridPaint.setAlpha(alpha);
        dotPaint.setAlpha(alpha);
        accentPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        bgPaint.setColorFilter(colorFilter);
        glowPaint.setColorFilter(colorFilter);
        gridPaint.setColorFilter(colorFilter);
        dotPaint.setColorFilter(colorFilter);
        accentPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    private int dp(float value) {
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                value,
                context.getResources().getDisplayMetrics()
        );
    }
}
