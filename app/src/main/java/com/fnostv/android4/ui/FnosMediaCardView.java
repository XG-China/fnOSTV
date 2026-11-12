package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.util.AppPrefs;

/**
 * 与网页 /v 端 1:1 的媒体卡片：
 * POSTER   158x288（图 156x235 圆角 8，评分徽标 40x24，底部渐变 76，标题 15，年份 13）
 * CONTINUE 288x209（图 288x162 圆角 10，标题 15，副标题 12）
 * LIBRARY  245x177（边框圆角 10，图 235x132 圆角 6，名称 15 加粗居中）
 */
public final class FnosMediaCardView extends LinearLayout {
    public static final int SHAPE_POSTER = 0;
    public static final int SHAPE_CONTINUE = 1;
    public static final int SHAPE_LIBRARY = 2;
    /** 海报墙（网页端 common.menu.listViewPosterWall）：密集小海报，不带标题。 */
    public static final int SHAPE_WALL = 3;
    /** 列表（网页端 common.menu.listViewList）：左侧竖版缩略图 + 右侧标题信息。 */
    public static final int SHAPE_LIST = 4;
    /** 剧集卡片（网页端季页 EpisodeCard）：16:9 剧照 + 「第 N 集. 标题」+ 两行简介 + 时长。 */
    public static final int SHAPE_EPISODE = 5;

    private final int shape;
    private final FrameLayout poster;
    private final ImageView image;
    private final TextView fallback;
    private final TextView score;
    private final TextView title;
    private final TextView subtitle;
    /** 剧集卡片专用：两行简介 + 时长。 */
    private TextView overview;
    private TextView duration;
    /** 季页剧集卡片按列宽缩放后的 16:9 剧照宽度（0 = 默认 260dp）。 */
    private int episodePosterWidth;
    /** 分辨率角标（海报右下，网页同款），开关见设置-外观-卡片样式。 */
    private TextView resolutionBadge;
    /** “已观看”角标（海报左下圆勾），开关见设置-外观-卡片样式。 */
    private WatchedBadgeView watchedBadge;
    private String summary = "";

    /** 焦点描边画在内容之上（避免被海报盖住）。 */
    private final Paint focusPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF focusRect = new RectF();
    private final android.graphics.Path focusPath = new android.graphics.Path();
    private android.graphics.Shader focusShader;
    private float focusShaderHeight = -1f;

    public FnosMediaCardView(Context context, int shape) {
        super(context);
        this.shape = shape;
        setOrientation(VERTICAL);
        setGravity(Gravity.CENTER_HORIZONTAL);
        setFocusable(true);
        setClickable(true);

        poster = new RoundedFrameLayout(context, 10, 0);
        image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setVisibility(View.GONE);

        fallback = new TextView(context);
        fallback.setTextColor(FnosTheme.COLOR_TEXT_80);
        fallback.setTextSize(14);
        fallback.setGravity(Gravity.CENTER);
        fallback.setPadding(dp(10), dp(10), dp(10), dp(12));

        score = new TextView(context);
        score.setTextColor(FnosTheme.COLOR_SCORE);
        score.setTextSize(16);
        score.setTypeface(null, 1);
        score.setGravity(Gravity.CENTER);
        score.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_SCORE_BG, 6, context));
        score.setVisibility(View.GONE);

        resolutionBadge = new TextView(context);
        resolutionBadge.setTextColor(Color.WHITE);
        resolutionBadge.setTextSize(11);
        resolutionBadge.setGravity(Gravity.CENTER);
        resolutionBadge.setPadding(dp(6), dp(2), dp(6), dp(2));
        resolutionBadge.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_SCORE_BG, 4, context));
        resolutionBadge.setVisibility(View.GONE);

        watchedBadge = new WatchedBadgeView(context);
        watchedBadge.setVisibility(View.GONE);

        title = new TextView(context);
        title.setTextColor(FnosTheme.COLOR_TEXT);
        title.setGravity(Gravity.CENTER);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);

        subtitle = new TextView(context);
        subtitle.setTextColor(FnosTheme.COLOR_TEXT_60);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setSingleLine(true);
        subtitle.setEllipsize(android.text.TextUtils.TruncateAt.END);

        if (shape == SHAPE_LIBRARY) {
            setBackgroundDrawable(FnosTheme.glass(context, 12));
            setPadding(dp(8), dp(8), dp(8), dp(6));
            poster.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, 0, context));
            poster.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            poster.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(poster, new LinearLayout.LayoutParams(dp(229), dp(126)));
            title.setTextColor(FnosTheme.COLOR_TEXT);
            title.setTextSize(15);
            title.setTypeface(null, 1);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
            titleParams.topMargin = dp(10);
            addView(title, titleParams);
            subtitle.setVisibility(GONE);
            setMinimumHeight(dp(177));
        } else if (shape == SHAPE_WALL) {
            // 海报墙：只有海报本体，网格更密
            setPadding(dp(6), dp(16), dp(6), dp(6));
            poster.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, 0, context));
            poster.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            poster.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams wallScoreParams = new FrameLayout.LayoutParams(dp(34), dp(20));
            wallScoreParams.leftMargin = dp(6);
            wallScoreParams.topMargin = dp(6);
            poster.addView(score, wallScoreParams);
            addView(poster, new LinearLayout.LayoutParams(dp(92), dp(134)));
            title.setVisibility(GONE);
            subtitle.setVisibility(GONE);
            setMinimumWidth(dp(104));
            setMinimumHeight(dp(156));
        } else if (shape == SHAPE_LIST) {
            // 列表：左侧海报缩略图，右侧标题 + 副标题
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setPadding(dp(10), dp(10), dp(16), dp(10));
            poster.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, 0, context));
            poster.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            poster.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            addView(poster, new LinearLayout.LayoutParams(dp(64), dp(96)));
            LinearLayout textBox = new LinearLayout(context);
            textBox.setOrientation(VERTICAL);
            textBox.setGravity(Gravity.CENTER_VERTICAL);
            title.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            title.setTextColor(FnosTheme.COLOR_TEXT);
            title.setTextSize(16);
            title.setTypeface(null, 1);
            subtitle.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            subtitle.setTextColor(FnosTheme.COLOR_TEXT_60);
            subtitle.setTextSize(13);
            textBox.addView(title, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            textBox.addView(subtitle, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            LinearLayout.LayoutParams textBoxParams = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            textBoxParams.leftMargin = dp(16);
            addView(textBox, textBoxParams);
            setMinimumHeight(dp(116));
        } else if (shape == SHAPE_EPISODE) {
            // 季页剧集卡片（网页 /season/:guid）：16:9 剧照 + 集标题 + 两行简介 + 时长。
            setOrientation(VERTICAL);
            setGravity(Gravity.LEFT);
            poster.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, 0, context));
            poster.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            poster.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View episodeGradient = new View(context);
            episodeGradient.setBackgroundDrawable(topBottomGradient());
            poster.addView(episodeGradient, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52), Gravity.BOTTOM));
            FrameLayout.LayoutParams episodeWatchedParams = new FrameLayout.LayoutParams(dp(24), dp(24));
            episodeWatchedParams.leftMargin = dp(8);
            episodeWatchedParams.bottomMargin = dp(8);
            episodeWatchedParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            poster.addView(watchedBadge, episodeWatchedParams);
            addView(poster, new LinearLayout.LayoutParams(dp(244), dp(137)));

            title.setTextColor(FnosTheme.COLOR_TEXT);
            title.setTextSize(15);
            title.setGravity(Gravity.LEFT);
            LinearLayout.LayoutParams episodeTitleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(23));
            episodeTitleParams.topMargin = dp(8);
            addView(title, episodeTitleParams);

            overview = new TextView(context);
            overview.setTextColor(FnosTheme.COLOR_TEXT_60);
            overview.setTextSize(12);
            overview.setGravity(Gravity.LEFT);
            overview.setMaxLines(2);
            overview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            overview.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            overviewParams.topMargin = dp(3);
            addView(overview, overviewParams);

            duration = new TextView(context);
            duration.setTextColor(FnosTheme.COLOR_TEXT_DIM);
            duration.setTextSize(12);
            duration.setGravity(Gravity.LEFT);
            LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(18));
            durationParams.topMargin = dp(3);
            addView(duration, durationParams);
            subtitle.setVisibility(GONE);
            setMinimumWidth(dp(260));
            setPadding(dp(6), dp(16), dp(6), dp(6));
        } else if (shape == SHAPE_CONTINUE) {
            poster.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, 0, context));
            poster.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            poster.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View continueGradient = new View(context);
            continueGradient.setBackgroundDrawable(topBottomGradient());
            poster.addView(continueGradient, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52), Gravity.BOTTOM));
            FrameLayout.LayoutParams continueWatched = new FrameLayout.LayoutParams(dp(24), dp(24));
            continueWatched.leftMargin = dp(10);
            continueWatched.bottomMargin = dp(8);
            continueWatched.gravity = Gravity.BOTTOM | Gravity.LEFT;
            poster.addView(watchedBadge, continueWatched);
            addView(poster, new LinearLayout.LayoutParams(dp(249), dp(140)));
            title.setTextColor(FnosTheme.COLOR_TEXT);
            title.setTextSize(15);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(23));
            titleParams.topMargin = dp(8);
            addView(title, titleParams);
            subtitle.setTextSize(12);
            addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));
            setMinimumWidth(dp(288));
        } else {
            poster.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, 0, context));
            poster.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            poster.addView(fallback, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View gradient = new View(context);
            gradient.setBackgroundDrawable(topBottomGradient());
            poster.addView(gradient, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(76), Gravity.BOTTOM));
            FrameLayout.LayoutParams scoreParams = new FrameLayout.LayoutParams(dp(40), dp(24));
            scoreParams.leftMargin = dp(10);
            scoreParams.topMargin = dp(10);
            poster.addView(score, scoreParams);
            FrameLayout.LayoutParams watchedParams = new FrameLayout.LayoutParams(dp(24), dp(24));
            watchedParams.leftMargin = dp(10);
            watchedParams.bottomMargin = dp(8);
            watchedParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            poster.addView(watchedBadge, watchedParams);
            FrameLayout.LayoutParams resolutionParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            resolutionParams.rightMargin = dp(8);
            resolutionParams.bottomMargin = dp(8);
            resolutionParams.gravity = Gravity.BOTTOM | Gravity.RIGHT;
            poster.addView(resolutionBadge, resolutionParams);
            addView(poster, new LinearLayout.LayoutParams(dp(146), dp(217)));
            title.setTextColor(FnosTheme.COLOR_TEXT);
            title.setTextSize(15);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(23));
            titleParams.topMargin = dp(8);
            addView(title, titleParams);
            subtitle.setTextSize(13);
            addView(subtitle, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(18)));
            setMinimumWidth(dp(158));
        }
    }

    /** 当前卡片形状（换布局时用来丢弃旧视图）。 */
    public int shape() {
        return shape;
    }

    public void setSummary(String summary) {
        this.summary = summary == null ? "" : summary;
        renderText(null);
    }

    /**
     * 季页剧集卡片按列宽缩放：剧照保持 16:9，文字与剧照同宽。
     *
     * <p>网页端剧集卡片固定 260×146；大屏电视上按可用宽度等比放大，
     * 避免右侧留出明显空白。
     */
    public void setEpisodePosterWidth(int widthPx) {
        if (shape != SHAPE_EPISODE || widthPx <= 0 || widthPx == episodePosterWidth) {
            return;
        }
        episodePosterWidth = widthPx;
        int innerWidth = Math.max(1, widthPx - getPaddingLeft() - getPaddingRight());
        int heightPx = Math.round(innerWidth * 9f / 16f);
        poster.setLayoutParams(new LinearLayout.LayoutParams(innerWidth, heightPx));
        setMinimumWidth(widthPx);
    }

    @Override
    protected void onFocusChanged(boolean gainFocus, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        // 焦点框（玻璃渐变描边）画在最上层，封面/角标都盖不住它。
        if (!isFocused()) {
            return;
        }
        float stroke = FnosTheme.dp(getContext(), 2);
        if (focusShader == null || focusShaderHeight != getHeight()) {
            focusShaderHeight = getHeight();
            focusShader = new android.graphics.LinearGradient(0, 0, 0, Math.max(1f, getHeight()),
                    0xFFD8ECFF, 0xE64C92FF, android.graphics.Shader.TileMode.CLAMP);
        }
        float radius = FnosTheme.dp(getContext(), 12);
        float[] radii = new float[]{radius, radius, radius, radius, 0f, 0f, 0f, 0f};
        focusPaint.setStyle(Paint.Style.STROKE);
        focusPaint.setShader(focusShader);
        focusPaint.setStrokeWidth(stroke);
        float inset = stroke / 2f;
        focusRect.set(inset, inset, getWidth() - inset, getHeight() - inset);
        focusPath.reset();
        focusPath.addRoundRect(focusRect, radii, android.graphics.Path.Direction.CW);
        canvas.drawPath(focusPath, focusPaint);
    }

    public void bindEntry(FnosFileEntry entry, boolean favorite) {        renderText(entry);
        fallback.setText(fallbackText(entry, favorite));
        if (posterLoader != null) {
            posterLoader.load(posterBaseUrl, entry, poster, image, fallback);
        }
    }

    private PosterLoader posterLoader;
    private String posterBaseUrl = "";

    public void configure(PosterLoader loader, String baseUrl) {
        this.posterLoader = loader;
        this.posterBaseUrl = baseUrl == null ? "" : baseUrl;
    }

    private void renderText(FnosFileEntry entry) {
        title.setText(displayTitle(entry));
        subtitle.setText(displaySubtitle(entry));
        if (shape == SHAPE_EPISODE) {
            if (overview != null) {
                overview.setText(entry == null ? "" : entry.overview);
                overview.setVisibility(entry != null && entry.overview.length() > 0 ? VISIBLE : GONE);
            }
            if (duration != null) {
                duration.setText(entry == null ? "" : entry.durationLabel());
                duration.setVisibility(entry != null && entry.durationLabel().length() > 0 ? VISIBLE : GONE);
            }
            score.setVisibility(GONE);
            resolutionBadge.setVisibility(GONE);
            watchedBadge.setVisibility(entry != null && entry.watched
                    && AppPrefs.bool(getContext(), AppPrefs.KEY_CARD_WATCHED, false) ? VISIBLE : GONE);
            return;
        }
        if ((shape == SHAPE_POSTER || shape == SHAPE_WALL) && entry != null && entry.score.length() > 0
                && AppPrefs.bool(getContext(), AppPrefs.KEY_CARD_SCORE, true)) {
            score.setText(entry.score);
            score.setVisibility(VISIBLE);
        } else {
            score.setVisibility(GONE);
        }
        if (shape == SHAPE_POSTER && entry != null && !entry.directory) {
            String resolution = entry.resolution.length() > 0 ? entry.resolution : entry.formatLabel();
            if (AppPrefs.bool(getContext(), AppPrefs.KEY_CARD_RESOLUTION, true) && resolution.length() > 0) {
                resolutionBadge.setText(resolution.toUpperCase(java.util.Locale.US));
                resolutionBadge.setVisibility(VISIBLE);
            } else {
                resolutionBadge.setVisibility(GONE);
            }
            watchedBadge.setVisibility(
                    entry.watched && AppPrefs.bool(getContext(), AppPrefs.KEY_CARD_WATCHED, false)
                            ? VISIBLE : GONE);
        } else if (shape == SHAPE_CONTINUE && entry != null) {
            resolutionBadge.setVisibility(GONE);
            watchedBadge.setVisibility(
                    entry.watched && AppPrefs.bool(getContext(), AppPrefs.KEY_CARD_WATCHED, false)
                            ? VISIBLE : GONE);
        } else {
            resolutionBadge.setVisibility(GONE);
            watchedBadge.setVisibility(GONE);
        }
    }

    private String displayTitle(FnosFileEntry entry) {
        if (entry != null) {
            String type = entry.type == null ? "" : entry.type.toLowerCase();
            if (type.indexOf("season") >= 0) {
                return entry.seasonLabel();
            }
            if (type.indexOf("episode") >= 0) {
                return entry.episodeDisplayTitle();
            }
            if (entry.name.length() > 0) {
                return entry.name;
            }
        }
        return firstLine(summary);
    }

    private String displaySubtitle(FnosFileEntry entry) {
        if (entry == null) {
            return "";
        }
        String type = entry.type == null ? "" : entry.type.toLowerCase();
        if (type.indexOf("season") >= 0) {
            // 网页同款：共 52 集 · 2018
            String count = entry.episodeCount > 0 ? "共 " + entry.episodeCount + " 集" : "";
            if (count.length() > 0 && entry.year.length() > 0) {
                return count + " · " + entry.year;
            }
            return count.length() > 0 ? count : entry.year;
        }
        if (shape == SHAPE_EPISODE) {
            return "";
        }
        if (shape == SHAPE_POSTER) {
            return entry.year;
        }
        if (shape == SHAPE_CONTINUE) {
            return categoryLabel(entry);
        }
        return "";
    }

    private String fallbackText(FnosFileEntry entry, boolean favorite) {
        if (entry == null || entry.name.length() == 0) {
            return summary;
        }
        if (entry.posterPath.length() > 0) {
            return summary;
        }
        String badge = entry.directory ? "目录" : entry.formatLabel();
        if (favorite) {
            badge = "* " + badge;
        }
        return entry.name + "\n" + badge;
    }

    private static String categoryLabel(FnosFileEntry entry) {
        String type = entry.type == null ? "" : entry.type.toLowerCase();
        if (type.indexOf("movie") >= 0) {
            return "电影";
        }
        if (type.indexOf("tv") >= 0 || type.indexOf("episode") >= 0) {
            return "电视节目";
        }
        return entry.formatLabel();
    }

    private static String firstLine(String value) {
        String text = value == null ? "" : value;
        int index = text.indexOf('\n');
        return index < 0 ? text : text.substring(0, index);
    }

    private GradientDrawable topBottomGradient() {
        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x00000000, 0xB3000000});
        return drawable;
    }

    private int dp(int value) {
        return FnosTheme.dp(getContext(), value);
    }
}
