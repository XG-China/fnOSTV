package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/**
 * 玻璃拟态背景：竖向渐变填充 + 渐变描边。
 *
 * Android 4.4 没有模糊/阴影，用半透明渐变 + 顶部更亮的描边来模拟玻璃质感，
 * 聚焦态则换成蓝调渐变边框，比纯色描边更柔和。
 */
public final class GlassDrawable extends Drawable {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int topColor;
    private final int bottomColor;
    private final int strokeTop;
    private final int strokeBottom;
    private final int shadowColor;
    private final float radiusPx;
    private final float strokePx;
    private final float shadowPx;

    private Shader fillShader;
    private Shader strokeShader;
    private float shaderTop = -1f;
    private float shaderBottom = -1f;

    /** 各角圆角（顺序：左上、右上、右下、左下），为空时使用统一 radiusPx。 */
    private float[] radiiPx;
    private final Path path = new Path();

    public GlassDrawable(Context context, int topColor, int bottomColor,
            int strokeTop, int strokeBottom, int radiusDp, int strokeDp) {
        this(context, topColor, bottomColor, strokeTop, strokeBottom, radiusDp, strokeDp, 0, 0);
    }

    /**
     * @param shadowColor 边框外侧的暗色描边（让亮边框在浅色封面上也看得清），0 表示不画
     * @param shadowDp    暗色描边宽度
     */
    public GlassDrawable(Context context, int topColor, int bottomColor,
            int strokeTop, int strokeBottom, int radiusDp, int strokeDp,
            int shadowColor, int shadowDp) {
        this.topColor = topColor;
        this.bottomColor = bottomColor;
        this.strokeTop = strokeTop;
        this.strokeBottom = strokeBottom;
        this.shadowColor = shadowColor;
        this.radiusPx = FnosTheme.dp(context, radiusDp);
        this.strokePx = FnosTheme.dp(context, strokeDp);
        this.shadowPx = FnosTheme.dp(context, shadowDp);
        strokePaint.setStyle(Paint.Style.STROKE);
    }

    /** 设置逐角圆角（px，顺序：左上、右上、右下、左下）。 */
    public GlassDrawable cornerRadiiPx(float topPx, float bottomPx) {
        this.radiiPx = new float[]{topPx, topPx, topPx, topPx, bottomPx, bottomPx, bottomPx, bottomPx};
        return this;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        float inset = strokePx / 2f;
        rect.set(bounds.left + inset, bounds.top + inset, bounds.right - inset, bounds.bottom - inset);
        if (rect.width() <= 0 || rect.height() <= 0) {
            return;
        }
        rebuildShaders();

        if (fillShader != null) {
            fillPaint.setShader(fillShader);
            drawShape(canvas, rect, fillPaint);
        }
        if (shadowPx > 0 && Color.alpha(shadowColor) > 0) {
            float shadowInset = shadowPx / 2f;
            RectF shadowRect = new RectF(rect);
            shadowRect.inset(shadowInset, shadowInset);
            strokePaint.setShader(null);
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(shadowPx);
            strokePaint.setColor(shadowColor);
            drawShape(canvas, shadowRect, strokePaint);
        }
        if (strokeShader != null) {
            strokePaint.setShader(strokeShader);
            strokePaint.setStrokeWidth(strokePx);
            drawShape(canvas, rect, strokePaint);
        }
    }

    private void drawShape(Canvas canvas, RectF target, Paint paint) {
        if (target.width() <= 0 || target.height() <= 0) {
            return;
        }
        if (radiiPx == null) {
            float radius = Math.min(radiusPx, Math.min(target.width(), target.height()) / 2f);
            canvas.drawRoundRect(target, radius, radius, paint);
            return;
        }
        path.reset();
        path.addRoundRect(target, radiiPx, Path.Direction.CW);
        canvas.drawPath(path, paint);
    }

    private void rebuildShaders() {
        if (shaderTop == rect.top && shaderBottom == rect.bottom) {
            return;
        }
        shaderTop = rect.top;
        shaderBottom = rect.bottom;
        if (Color.alpha(topColor) > 0 || Color.alpha(bottomColor) > 0) {
            fillShader = new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                    topColor, bottomColor, Shader.TileMode.CLAMP);
        } else {
            fillShader = null;
        }
        if (strokePx > 0 && (Color.alpha(strokeTop) > 0 || Color.alpha(strokeBottom) > 0)) {
            strokeShader = new LinearGradient(rect.left, rect.top, rect.left, rect.bottom,
                    strokeTop, strokeBottom, Shader.TileMode.CLAMP);
        } else {
            strokeShader = null;
        }
    }

    @Override
    public void setAlpha(int alpha) {
        // 由颜色自带透明度控制
    }

    @Override
    public void setColorFilter(android.graphics.ColorFilter colorFilter) {
        fillPaint.setColorFilter(colorFilter);
        strokePaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return android.graphics.PixelFormat.TRANSLUCENT;
    }
}
