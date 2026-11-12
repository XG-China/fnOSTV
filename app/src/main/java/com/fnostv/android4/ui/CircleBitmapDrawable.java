package com.fnostv.android4.ui;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

/** 圆形头像：把位图按 center-crop 裁到圆内，兼容低版本 API。 */
final class CircleBitmapDrawable extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final Rect source = new Rect();
    private final RectF destination = new RectF();
    private final Bitmap bitmap;

    CircleBitmapDrawable(Bitmap bitmap) {
        this.bitmap = bitmap;
    }

    @Override
    public void draw(Canvas canvas) {
        if (bitmap == null || bitmap.isRecycled()) {
            return;
        }
        Rect bounds = getBounds();
        if (bounds.width() <= 0 || bounds.height() <= 0) {
            return;
        }
        float radius = Math.min(bounds.width(), bounds.height()) / 2f;
        clip.reset();
        clip.addCircle(bounds.exactCenterX(), bounds.exactCenterY(), radius, Path.Direction.CW);
        int save = canvas.save();
        canvas.clipPath(clip);
        float scale = Math.max(
                (float) bounds.width() / (float) bitmap.getWidth(),
                (float) bounds.height() / (float) bitmap.getHeight());
        float width = bitmap.getWidth() * scale;
        float height = bitmap.getHeight() * scale;
        float left = bounds.exactCenterX() - width / 2f;
        float top = bounds.exactCenterY() - height / 2f;
        source.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
        destination.set(left, top, left + width, top + height);
        canvas.drawBitmap(bitmap, source, destination, paint);
        canvas.restoreToCount(save);
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
