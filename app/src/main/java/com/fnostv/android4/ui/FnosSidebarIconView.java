package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

/**
 * 网页侧栏图标：直接还原网页 SVG 的 fill path（24×24），保证和网页一致。
 */
final class FnosSidebarIconView extends View {
    static final String TYPE_HOME = "home";
    static final String TYPE_FAVORITE = "favorite";
    static final String TYPE_LIBRARY = "library";
    static final String TYPE_ALL = "all";
    static final String TYPE_MOVIE = "movie";
    static final String TYPE_TV = "tv";
    static final String TYPE_LIVE = "live";
    static final String TYPE_OTHER = "other";

    /** 首页（房子）。 */
    private static final String PATH_HOME =
            "M11.386 1.21c.36-.28.867-.28 1.228 0l9 7c.244.19.386.482.386.79v11a3 3 0 01-3 3H5"
                    + "a3 3 0 01-3-3V9c0-.308.142-.6.386-.79l9-7zM4 9.49V20a1 1 0 001 1h14a1 1 0 001-1"
                    + "V9.49l-8-6.223-8 6.222z";
    /** 收藏（心）。 */
    private static final String PATH_FAVORITE =
            "M20.662 8.996a4.316 4.316 0 00-7.847-2.482l-.818 1.161-.817-1.16a4.316 4.316 0 00"
                    + "-7.848 2.481c0 2.273 1.36 4.57 3.274 6.505 1.777 1.794 3.886 3.124 5.391 3.704"
                    + " 1.505-.58 3.614-1.91 5.39-3.704 1.916-1.934 3.275-4.232 3.275-6.505zm2 0c0 3.042"
                    + "-1.781 5.82-3.853 7.912-2.082 2.102-4.628 3.688-6.497 4.31l-.315.104-.315-.105"
                    + "c-1.868-.62-4.415-2.207-6.496-4.31-2.072-2.092-3.854-4.87-3.854-7.911A6.316 6.316 0"
                    + "017.647 2.68c1.687 0 3.217.664 4.35 1.74a6.316 6.316 0 0110.666 4.576z";
    /** 电影 / 动画电影（胶片盘）。 */
    private static final String PATH_MOVIE =
            "M12 1c6.075 0 11 4.925 11 11 0 3.722-1.85 7.01-4.68 9H22a1 1 0 110 2H12C5.925 23 1 18.075"
                    + " 1 12S5.925 1 12 1zm0 2a9 9 0 100 18 9 9 0 000-18zm0 11a2 2 0 110 4 2 2 0 010-4z"
                    + "m-4-4a2 2 0 110 4 2 2 0 010-4zm8 0a2 2 0 110 4 2 2 0 010-4zm-4-4a2 2 0 110 4 2 2 0"
                    + " 010-4z";
    /** 全部（四宫格）。 */
    private static final String PATH_ALL =
            "M2 4a2 2 0 012-2h5a2 2 0 012 2v5a2 2 0 01-2 2H4a2 2 0 01-2-2V4zm7 0H4v5h5V4zm4 0a2 2 0 012"
                    + "-2h5a2 2 0 012 2v5a2 2 0 01-2 2h-5a2 2 0 01-2-2V4zm7 0h-5v5h5V4zM2 15a2 2 0 012"
                    + "-2h5a2 2 0 012 2v5a2 2 0 01-2 2H4a2 2 0 01-2-2v-5zm7 0H4v5h5v-5zm4 0a2 2 0 012-2h5"
                    + "a2 2 0 012 2v5a2 2 0 01-2 2h-5a2 2 0 01-2-2v-5zm7 0h-5v5h5v-5z";
    /** 动画 / 电视剧 / 少儿（带天线的电视）。 */
    private static final String PATH_TV =
            "M6.293 1.293a1 1 0 011.414 0L12 5.586l4.293-4.293a1 1 0 111.414 1.414L14.414 6H20a3 3 0"
                    + "013 3v11a3 3 0 01-3 3H4a3 3 0 01-3-3V9a3 3 0 013-3h5.586L6.293 2.707a1 1 0 010-1.414z"
                    + "M4 8a1 1 0 00-1 1v11a1 1 0 001 1h16a1 1 0 001-1V9a1 1 0 00-1-1H4z";
    /** 电视直播（显示器 + 底座）。 */
    private static final String PATH_LIVE =
            "M4 4a1 1 0 00-1 1v10a1 1 0 001 1h16a1 1 0 001-1V5a1 1 0 00-1-1H4zM1 5a3 3 0 013-3h16a3 3"
                    + " 0 013 3v10a3 3 0 01-3 3H4a3 3 0 01-3-3V5zm5 16a1 1 0 011-1h10a1 1 0 110 2H7a1 1 0"
                    + "01-1-1z";
    /** 文件夹（其他 / MV / 未知媒体库）。 */
    private static final String PATH_FOLDER =
            "M1.879 3.379A3 3 0 014 2.5h3.93a3 3 0 012.502 1.345l.813 1.205a1 1 0 00.845.45H20a3 3 0"
                    + "013 3v10a3 3 0 01-3 3H4a3 3 0 01-3-3v-13a3 3 0 01.879-2.121zM4 4.5a1 1 0 00-1 1v13"
                    + "a1 1 0 001 1h16a1 1 0 001-1v-10a1 1 0 00-1-1h-7.896a3 3 0 01-2.526-1.345L8.765 4.95"
                    + "a1 1 0 00-.835-.45H4z";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final float padding;

    FnosSidebarIconView(Context context, String type) {
        super(context);
        String data;
        if (TYPE_HOME.equals(type)) {
            data = PATH_HOME;
        } else if (TYPE_FAVORITE.equals(type)) {
            data = PATH_FAVORITE;
        } else if (TYPE_MOVIE.equals(type) || TYPE_LIBRARY.equals(type)) {
            data = TYPE_LIBRARY.equals(type) ? PATH_FOLDER : PATH_MOVIE;
        } else if (TYPE_ALL.equals(type)) {
            data = PATH_ALL;
        } else if (TYPE_TV.equals(type)) {
            data = PATH_TV;
        } else if (TYPE_LIVE.equals(type)) {
            data = PATH_LIVE;
        } else {
            data = PATH_FOLDER;
        }
        SvgPath.parse(data, path);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(FnosTheme.COLOR_TEXT_60);
        padding = context.getResources().getDisplayMetrics().density * 0.5f;
    }

    void setIconColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) {
            return;
        }
        float s = Math.min(w, h) - padding * 2;
        float scale = s / 24f;
        canvas.save();
        canvas.translate((w - s) / 2f, (h - s) / 2f);
        canvas.scale(scale, scale);
        canvas.drawPath(path, paint);
        canvas.restore();
    }
}
