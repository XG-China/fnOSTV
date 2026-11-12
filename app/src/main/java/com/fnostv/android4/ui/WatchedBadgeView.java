package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * 「已观看」角标：半透明黑圆 + 白色对勾，纯 Canvas 绘制。
 *
 * <p>原来用 TextView 显示 U+2713，Android 4.4 的中文字体没有这个字形，
 * 结果只剩一个黑圈（用户反馈「标记已观看是黑色圈圈」）。
 */
final class WatchedBadgeView extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path check = new Path();

    WatchedBadgeView(Context context) {
        super(context);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(0xCC000000);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setColor(Color.WHITE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float cx = w / 2f;
        float cy = h / 2f;
        float radius = Math.min(w, h) / 2f;
        canvas.drawCircle(cx, cy, radius, fill);

        stroke.setStrokeWidth(Math.max(2f, radius * 0.22f));
        check.reset();
        check.moveTo(w * 0.30f, h * 0.52f);
        check.lineTo(w * 0.44f, h * 0.66f);
        check.lineTo(w * 0.71f, h * 0.36f);
        canvas.drawPath(check, stroke);
    }
}
