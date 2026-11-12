package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.view.KeyEvent;
import android.widget.FrameLayout;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.MediaSeriesHeader;

import java.util.ArrayList;
import java.util.List;

/**
 * 剧集页（网页 /v/tv/:guid 与 /v/tv/season/:guid 的 1:1 还原）。
 *
 * <ul>
 *   <li>剧页：大背景 + 标题/评分/年份 + 简介 + 继续播放按钮 + 「季」海报卡片行</li>
 *   <li>季页：左侧季海报 + 右侧剧名/第 N 季/评分/年份 + 继续播放按钮 + 集数方格（每页 30 集）</li>
 * </ul>
 */
public final class NativeSeriesView {
    public interface Listener {
        void onSeriesSeasonSelected(FnosFileEntry season);

        void onSeriesEpisodeSelected(FnosFileEntry episode);

        void onSeriesBackRequested();
    }

    private static final int PAGE_SIZE = 30;
    private static final int EPISODES_PER_ROW = 15;

    private final Context context;
    private final Listener listener;
    private final PosterLoader posterLoader = new PosterLoader();

    private FrameLayout root;
    private ScrollView scroll;
    private LinearLayout content;
    private String posterBaseUrl = "";

    private MediaSeriesHeader header = MediaSeriesHeader.empty();
    private List<FnosFileEntry> seasons = new ArrayList<FnosFileEntry>();
    private List<FnosFileEntry> episodes = new ArrayList<FnosFileEntry>();
    private String currentSeasonGuid = "";
    private String currentEpisodeGuid = "";
    private boolean episodesPage;
    private int pageIndex;
    /** false=集数方格，true=剧集卡片（缩略图）。 */
    private boolean thumbnailMode;
    /** 剧页：继续观看的那一集（决定「▶ 第 N 季 第 M 集」）。 */
    private FnosFileEntry resumeEntry;
    /** 剧页大背景（16:9 剧照）。 */
    private String backdropPath = "";
    /** 进入页面后要聚焦的控件。 */
    private View initialFocus;
    private GridView gridIndex;
    /** 按下键时从切换控件回到的内容（集数方格 / 第一张视频卡）。 */
    private View contentFocus;
    /** 右上角视图切换控件（集数 / 视频）。 */
    private ModeSwitchView modeSwitch;

    public NativeSeriesView(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public View create() {
        root = new FrameLayout(context);
        root.setBackgroundColor(FnosTheme.COLOR_APP_BG);
        root.setVisibility(View.GONE);

        scroll = new ScrollView(context);
        scroll.setBackgroundColor(FnosTheme.COLOR_APP_BG);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(48));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return root;
    }

    public void setPosterBaseUrl(String baseUrl) {
        posterBaseUrl = baseUrl == null ? "" : baseUrl;
    }

    public void setPosterAuthorizationToken(String token) {
        posterLoader.setAuthorizationToken(token);
    }

    public boolean isVisible() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }

    public void hide() {
        if (root != null) {
            root.setVisibility(View.GONE);
        }
    }

    /** 从播放器/详情页返回时，把焦点还给剧集页里的集数方格或视频卡片。 */
    public void refocus() {
        View target = contentFocus != null ? contentFocus : initialFocus;
        if (target != null && target.getVisibility() == View.VISIBLE) {
            target.requestFocus();
        }
    }

    /** 剧页：季卡片列表。 */
    public void showSeasons(String title, MediaSeriesHeader seriesHeader,
                            List<FnosFileEntry> seasonEntries, String resumeEpisodeGuid,
                            FnosFileEntry resumeEntry, String backdropPath) {
        this.header = seriesHeader == null ? MediaSeriesHeader.empty() : seriesHeader;
        this.seasons = seasonEntries == null ? new ArrayList<FnosFileEntry>() : new ArrayList<FnosFileEntry>(seasonEntries);
        this.episodesPage = false;
        this.resumeEntry = resumeEntry;
        this.backdropPath = backdropPath == null ? "" : backdropPath;
        this.currentEpisodeGuid = resumeEpisodeGuid == null ? "" : resumeEpisodeGuid;
        this.pageIndex = 0;
        if (title != null && title.length() > 0 && header.title.length() == 0) {
            header = new MediaSeriesHeader(title, header.seasonTitle, header.overview, header.posterPath,
                    header.year, header.score, header.seasonCount, header.episodeCount, header.type);
        }
        render();
        show();
    }

    /** 季页：剧集方格。 */
    public void showEpisodes(String title, MediaSeriesHeader seriesHeader, List<FnosFileEntry> episodeEntries,
                             List<FnosFileEntry> seasonEntries, String seasonGuid, String focusEpisodeGuid) {
        this.header = seriesHeader == null ? MediaSeriesHeader.empty() : seriesHeader;
        this.episodes = episodeEntries == null ? new ArrayList<FnosFileEntry>() : new ArrayList<FnosFileEntry>(episodeEntries);
        this.seasons = seasonEntries == null ? new ArrayList<FnosFileEntry>() : new ArrayList<FnosFileEntry>(seasonEntries);
        this.currentSeasonGuid = seasonGuid == null ? "" : seasonGuid;
        this.currentEpisodeGuid = focusEpisodeGuid == null ? "" : focusEpisodeGuid;
        this.episodesPage = true;
        this.pageIndex = pageOf(focusEpisodeGuid);
        // 封面统一使用「季」的竖版海报（剧集缩略图是 16:9，放进竖版框会很难看）
        String seasonPoster = "";
        for (int i = 0; i < this.seasons.size(); i++) {
            FnosFileEntry season = this.seasons.get(i);
            if (season.path.equals(this.currentSeasonGuid) && season.posterPath.length() > 0) {
                seasonPoster = season.posterPath;
                break;
            }
            if (seasonPoster.length() == 0 && season.posterPath.length() > 0) {
                seasonPoster = season.posterPath;
            }
        }
        if (seasonPoster.length() > 0) {
            header = new MediaSeriesHeader(header.title, header.seasonTitle, header.overview,
                    seasonPoster, header.year, header.score, header.seasonCount,
                    header.episodeCount, header.type);
        }

        render();
        show();
    }

    private void show() {
        if (root != null) {
            root.setVisibility(View.VISIBLE);
            if (initialFocus != null) {
                initialFocus.requestFocus();
            }
        }
    }

    private void render() {
        if (content == null) {
            return;
        }
        content.removeAllViews();
        initialFocus = null;
        contentFocus = null;
        if (episodesPage) {
            // 季页：普通返回栏
            content.addView(backBar(), rowParams(0, 10));
            renderEpisodePage();
        } else {
            // 剧页：返回箭头在背景图上，不再额外加一条返回栏
            renderSeriesPage();
        }
        scroll.scrollTo(0, 0);
    }

    // ───────────────────────── 剧页 ─────────────────────────

    private void renderSeriesPage() {
        content.addView(seriesHero(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(520)));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        Button play = new Button(context);
        play.setText("▶  " + seriesPlayLabel());
        play.setAllCaps(false);
        play.setTextSize(17);
        play.setTextColor(Color.WHITE);
        play.setSingleLine(true);
        play.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PRIMARY, 27, context));
        play.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(FnosTheme.rounded(
                        hasFocus ? FnosTheme.COLOR_PRIMARY_FOCUSED : FnosTheme.COLOR_PRIMARY, 27, context));
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (resumeEntry != null) {
                    listener.onSeriesEpisodeSelected(resumeEntry);
                } else if (!seasons.isEmpty()) {
                    listener.onSeriesSeasonSelected(seasons.get(0));
                }
            }
        });
        actions.addView(play, new LinearLayout.LayoutParams(dp(226), dp(50)));
        if (initialFocus == null) {
            initialFocus = play;
        }

        View spacer = new View(context);
        actions.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        String metaText = seriesMetaText();
        if (metaText.length() > 0) {
            TextView meta = new TextView(context);
            meta.setText(metaText);
            meta.setTextColor(FnosTheme.COLOR_TEXT_60);
            meta.setTextSize(15);
            meta.setGravity(Gravity.RIGHT);
            actions.addView(meta, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        content.addView(actions, rowParams(16, 0));

        if (header.overview != null && header.overview.length() > 0) {
            TextView overview = new TextView(context);
            overview.setText(header.overview);
            overview.setTextColor(FnosTheme.COLOR_TEXT_80);
            overview.setTextSize(18);
            overview.setMaxLines(3);
            overview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            overview.setLineSpacing(dp(4), 1f);
            content.addView(overview, rowParams(12, 0));
        }

        if (seasons.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("暂无季信息");
            empty.setTextColor(FnosTheme.COLOR_TEXT_DIM);
            empty.setTextSize(16);
            content.addView(empty, rowParams(20, 24));
        } else {
            content.addView(seasonPosters(), rowParams(20, 24));
        }
    }

    /** 剧页大背景：16:9 剧照铺满，底部渐隐到页面底色，左下角剧名 + 左上角返回。 */
    private View seriesHero() {
        FrameLayout hero = new FrameLayout(context);
        hero.setBackgroundColor(FnosTheme.COLOR_APP_BG);

        ImageView backdrop = new ImageView(context);
        backdrop.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(backdrop, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView fallback = new TextView(context);
        fallback.setText("");
        hero.addView(fallback);

        String imagePath = header.backdropPath.length() > 0 ? header.backdropPath
                : (backdropPath.length() > 0 ? backdropPath : header.posterPath);
        if (imagePath.length() > 0) {
            posterLoader.loadPath(posterBaseUrl, imagePath, 1600, 900, hero, backdrop, fallback);
        }

        View gradient = new View(context);
        int pageBg = FnosTheme.COLOR_APP_BG;
        gradient.setBackgroundDrawable(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{pageBg,
                        (0xFA << 24) | (pageBg & 0xFFFFFF),
                        (0xB3 << 24) | (pageBg & 0xFFFFFF),
                        (0x00 << 24) | (pageBg & 0xFFFFFF)}));
        hero.addView(gradient, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(260), Gravity.BOTTOM));

        TextView back = new TextView(context);
        back.setText("←");
        back.setTextColor(Color.WHITE);
        back.setTextSize(24);
        back.setGravity(Gravity.CENTER);
        back.setFocusable(true);
        back.setClickable(true);
        back.setBackgroundDrawable(FnosTheme.rounded(0x66000000, 22, context));
        back.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(FnosTheme.rounded(
                        hasFocus ? 0xCC0066FF : 0x66000000, 22, context));
            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSeriesBackRequested();
            }
        });
        FrameLayout.LayoutParams backParams = new FrameLayout.LayoutParams(
                dp(44), dp(44), Gravity.TOP | Gravity.LEFT);
        backParams.setMargins(dp(44), dp(30), 0, 0);
        hero.addView(back, backParams);

        TextView title = new TextView(context);
        title.setText(header.title);
        title.setTextColor(Color.WHITE);
        title.setTextSize(40);
        title.setTypeface(null, 1);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        FrameLayout.LayoutParams titleParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.LEFT);
        titleParams.setMargins(dp(44), 0, dp(44), dp(22));
        hero.addView(title, titleParams);

        // 网页剧页用的是剧名 logo（item.logos）；没有就保留文字标题。
        // 注意：PosterLoader 用 frame.getTag() 做请求归属校验，logo 必须有自己的容器，
        // 不能和背景图共用 hero，否则背景图回调会被丢弃（表现为背景一片黑）。
        if (header.logoPath.length() > 0) {
            FrameLayout logoFrame = new FrameLayout(context);
            ImageView logo = new ImageView(context);
            logo.setAdjustViewBounds(true);
            logo.setScaleType(ImageView.ScaleType.FIT_START);
            logoFrame.addView(logo, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
            FrameLayout.LayoutParams logoParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(130), Gravity.BOTTOM | Gravity.LEFT);
            logoParams.setMargins(dp(44), 0, dp(44), dp(18));
            hero.addView(logoFrame, logoParams);
            posterLoader.loadPath(posterBaseUrl, header.logoPath, 700, 260, logoFrame, logo, title);
        }

        return hero;
    }

    /** 「▶ 第 1 季 第 7 集」：有继续观看的剧集就用它，否则退回落第一季。 */
    private String seriesPlayLabel() {
        if (resumeEntry != null) {
            int seasonNumber = Math.max(0, resumeEntry.seasonNumber());
            int episodeNumber = Math.max(0, resumeEntry.episodeNumber());
            return seasonNumber > 0
                    ? "第 " + seasonNumber + " 季 第 " + episodeNumber + " 集"
                    : "第 " + episodeNumber + " 集";
        }
        if (!seasons.isEmpty()) {
            return seasons.get(0).seasonLabel();
        }
        return "播放";
    }

    /** 右上角元信息：评分 / 年份 / 季数。 */
    private String seriesMetaText() {
        StringBuilder text = new StringBuilder();
        if (header.score != null && header.score.length() > 0) {
            text.append(header.score).append(" 分");
        }
        if (header.year != null && header.year.length() > 0) {
            if (text.length() > 0) {
                text.append("   ·   ");
            }
            text.append(header.year);
        }
        int seasonCount = seasons.size() > 0 ? seasons.size() : header.seasonCount;
        if (seasonCount > 0) {
            if (text.length() > 0) {
                text.append("   ·   ");
            }
            text.append("共 ").append(seasonCount).append(" 季");
        }
        return text.toString();
    }

    private View seasonPosters() {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        if (seasons.isEmpty()) {
            return block;
        }
        // 一屏放得下：7 张一行，超过就换行，不做横向滚动
        final int perRow = 6;
        LinearLayout row = null;
        for (int i = 0; i < seasons.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = i + perRow < seasons.size() ? dp(16) : 0;
                block.addView(row, rowParams);
            }
            View card = seasonPoster(seasons.get(i));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(200), dp(300));
            params.leftMargin = i % perRow == 0 ? 0 : dp(20);
            row.addView(card, params);
        }
        return block;
    }

    /** 季海报卡：竖版海报 + 左上评分 + 右下清晰度（同网页剧页）。 */
    private View seasonPoster(final FnosFileEntry season) {
        FrameLayout card = new FrameLayout(context);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(4), dp(4), dp(4), dp(4));
        card.setBackgroundDrawable(cardBackground(false));
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(cardBackground(hasFocus));
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSeriesSeasonSelected(season);
            }
        });

        RoundedFrameLayout poster = new RoundedFrameLayout(context, 10);
        poster.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, context));
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView fallback = new TextView(context);
        fallback.setText(season.seasonLabel());
        fallback.setTextColor(FnosTheme.COLOR_TEXT_60);
        fallback.setTextSize(20);
        fallback.setGravity(Gravity.CENTER);
        poster.addView(fallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (season.posterPath != null && season.posterPath.length() > 0) {
            posterLoader.loadPath(posterBaseUrl, season.posterPath, 520, 780, poster, image, fallback);
        }

        if (season.score != null && season.score.length() > 0) {
            TextView score = new TextView(context);
            score.setText(season.score);
            score.setTextColor(Color.WHITE);
            score.setTextSize(15);
            score.setTypeface(null, 1);
            score.setGravity(Gravity.CENTER);
            score.setBackgroundDrawable(FnosTheme.rounded(0xCC000000, 6, context));
            score.setPadding(dp(8), dp(2), dp(8), dp(3));
            FrameLayout.LayoutParams scoreParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP | Gravity.LEFT);
            scoreParams.setMargins(dp(10), dp(10), 0, 0);
            poster.addView(score, scoreParams);
        }
        if (season.resolution != null && season.resolution.length() > 0) {
            TextView res = new TextView(context);
            String label = season.resolution.toUpperCase(java.util.Locale.US);
            res.setText(label);
            res.setTextColor(Color.WHITE);
            res.setTextSize(13);
            res.setGravity(Gravity.CENTER);
            res.setBackgroundDrawable(FnosTheme.rounded(0x99000000, 5, context));
            res.setPadding(dp(7), dp(2), dp(7), dp(2));
            FrameLayout.LayoutParams resParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.RIGHT);
            resParams.setMargins(0, 0, dp(10), dp(10));
            poster.addView(res, resParams);
        }

        card.addView(poster, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return card;
    }

    // ───────────────────────── 季页 ─────────────────────────

    private void renderEpisodePage() {
        content.addView(episodeHeader(), rowParams(0, 18));
        content.addView(pickerBar(), rowParams(0, 14));
        content.addView(thumbnailMode ? episodeCards() : episodeGrid(), rowParams(0, 24));
    }

    private View episodeHeader() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.TOP);

        FrameLayout poster = new RoundedFrameLayout(context, 10);
        poster.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, context));
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        poster.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView fallback = new TextView(context);
        fallback.setText(header.title);
        fallback.setTextColor(FnosTheme.COLOR_TEXT_60);
        fallback.setGravity(Gravity.CENTER);
        fallback.setTextSize(13);
        poster.addView(fallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        if (header.posterPath.length() > 0) {
            posterLoader.loadPath(posterBaseUrl, header.posterPath, 420, 620, poster, image, fallback);
        }
        row.addView(poster, new LinearLayout.LayoutParams(dp(240), dp(352)));

        LinearLayout info = new LinearLayout(context);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        infoParams.leftMargin = dp(28);
        row.addView(info, infoParams);

        TextView title = new TextView(context);
        title.setText(header.title);
        title.setTextColor(FnosTheme.COLOR_TEXT);
        title.setTextSize(40);
        title.setTypeface(null, 1);
        title.setSingleLine(true);
        info.addView(title);

        TextView season = new TextView(context);
        season.setText(header.seasonTitle);
        season.setTextColor(FnosTheme.COLOR_TEXT);
        season.setTextSize(20);
        season.setPadding(0, dp(8), 0, 0);
        info.addView(season);

        LinearLayout meta = new LinearLayout(context);
        meta.setGravity(Gravity.CENTER_VERTICAL);
        meta.setPadding(0, dp(10), 0, 0);
        if (header.score.length() > 0) {
            TextView score = new TextView(context);
            score.setText(header.score + " 分");
            score.setTextColor(FnosTheme.COLOR_SCORE);
            score.setTextSize(17);
            meta.addView(score);
        }
        if (header.year.length() > 0) {
            TextView year = new TextView(context);
            year.setText((header.score.length() > 0 ? " / " : "") + header.year);
            year.setTextColor(FnosTheme.COLOR_TEXT_MUTED);
            year.setTextSize(17);
            meta.addView(year);
        }
        info.addView(meta);

        Button play = new Button(context);
        play.setText("▶  " + currentEpisodeLabel());
        play.setAllCaps(false);
        play.setTextSize(17);
        play.setTextColor(Color.WHITE);
        play.setMinHeight(dp(44));
        play.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PRIMARY, 22, context));
        play.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(FnosTheme.rounded(
                        hasFocus ? FnosTheme.COLOR_PRIMARY_FOCUSED : FnosTheme.COLOR_PRIMARY, 22, context));
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                FnosFileEntry episode = currentEpisode();
                if (episode != null) {
                    listener.onSeriesEpisodeSelected(episode);
                }
            }
        });
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(236), dp(56));
        playParams.topMargin = dp(16);
        info.addView(play, playParams);

        TextView overview = new TextView(context);
        overview.setText(header.overview);
        overview.setTextColor(FnosTheme.COLOR_TEXT_80);
        overview.setTextSize(18);
        overview.setMaxLines(4);
        overview.setMaxWidth(dp(1150));
        overview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overview.setLineSpacing(dp(5), 1f);
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overviewParams.topMargin = dp(14);
        info.addView(overview, overviewParams);

        return row;
    }

    private String currentEpisodeLabel() {
        FnosFileEntry episode = currentEpisode();
        if (episode == null) {
            return "播放";
        }
        return "第 " + Math.max(0, episode.episodeNumber()) + " 集";
    }

    private FnosFileEntry currentEpisode() {
        if (currentEpisodeGuid.length() > 0) {
            for (int i = 0; i < episodes.size(); i++) {
                if (currentEpisodeGuid.equals(episodes.get(i).path)) {
                    return episodes.get(i);
                }
            }
        }
        return episodes.isEmpty() ? null : episodes.get(0);
    }

    /** 「第 N 季 ▾ + 1-30 / 31-52 + 视图按钮」。 */
    private View pickerBar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView seasonChip = new TextView(context);
        seasonChip.setText(seasonChipLabel());
        seasonChip.setTextColor(FnosTheme.COLOR_TEXT);
        seasonChip.setTextSize(16);
        seasonChip.setGravity(Gravity.CENTER);
        seasonChip.setPadding(dp(14), dp(8), dp(14), dp(8));
        seasonChip.setBackgroundDrawable(pillBackground(false));
        seasonChip.setFocusable(true);
        seasonChip.setClickable(true);
        seasonChip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(pillBackground(hasFocus));
            }
        });
        seasonChip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switchToNextSeason();
            }
        });
        bar.addView(seasonChip, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(36)));

        int pageCount = pageCount();
        for (int i = 0; i < pageCount; i++) {
            final int target = i;
            int from = i * PAGE_SIZE + 1;
            int to = Math.min(episodes.size(), (i + 1) * PAGE_SIZE);
            String label = from + " - " + to;
            if (pageCount > 1 && i == pageCount - 1) {
                label = from + " - " + episodes.size();
            }
            TextView tab = new TextView(context);
            tab.setText(label);
            tab.setTextSize(17);
            tab.setGravity(Gravity.CENTER);
            tab.setPadding(dp(16), dp(8), dp(16), dp(8));
            tab.setTypeface(null, i == pageIndex ? 1 : 0);
            tab.setTextColor(i == pageIndex ? FnosTheme.COLOR_PRIMARY : FnosTheme.COLOR_TEXT_60);
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setBackgroundDrawable(FnosTheme.rounded(0x00000000, 8, context));
            tab.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus) {
                        v.setBackgroundDrawable(tileBackgroundFocused(false));
                    } else {
                        v.setBackgroundDrawable(FnosTheme.rounded(0x00000000, 8, context));
                    }
                }
            });
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    pageIndex = target;
                    render();
                }
            });
            LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            tabParams.leftMargin = dp(18);
            bar.addView(tab, tabParams);
            if (i == 0) {
                bar.addView(dividerText("/"), tabParams);
            }
        }

        View spacer = new View(context);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        // 网页右上角：68×36 的胶囊里两段图标（显示器 = 视频卡片，四宫格 = 集数方格）
        modeSwitch = new ModeSwitchView();
        LinearLayout.LayoutParams switchParams = new LinearLayout.LayoutParams(dp(68), dp(36));
        switchParams.leftMargin = dp(12);
        bar.addView(modeSwitch, switchParams);
        return bar;
    }

    private View dividerText(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(FnosTheme.COLOR_TEXT_DIM);
        view.setTextSize(15);
        return view;
    }

    private String seasonChipLabel() {
        int seasonNumber = -1;
        for (int i = 0; i < seasons.size(); i++) {
            if (seasons.get(i).path.equals(currentSeasonGuid)) {
                seasonNumber = seasons.get(i).seasonNumber();
                break;
            }
        }
        if (seasonNumber < 0) {
            FnosFileEntry episode = currentEpisode();
            seasonNumber = episode == null ? 1 : episode.seasonNumber();
        }
        return MediaSeriesHeader.seasonLabel(seasonNumber) + "  ▾";
    }

    private void switchToNextSeason() {
        if (seasons.size() <= 1) {
            return;
        }
        int index = 0;
        for (int i = 0; i < seasons.size(); i++) {
            if (seasons.get(i).path.equals(currentSeasonGuid)) {
                index = i;
                break;
            }
        }
        index = (index + 1) % seasons.size();
        listener.onSeriesSeasonSelected(seasons.get(index));
    }

    /**
     * 网页右上角的视图切换：68×36 的胶囊，内部分两段（各 32×32），
     * 左 = 显示器图标（视频卡片），右 = 四宫格图标（集数方格），选中段有滑块高亮。
     */
    private final class ModeSwitchView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        ModeSwitchView() {
            super(context);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClickable(true);
            setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.invalidate();
                }
            });
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            float radius = dp(6);
            float border = Math.max(1f, dp(1));
            RectF track = new RectF(border / 2f, border / 2f, w - border / 2f, h - border / 2f);

            // 轨道
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(FnosTheme.isLightTheme()
                    ? (hasFocus() ? 0x14000000 : 0x0A000000)
                    : (hasFocus() ? 0xFF26262B : 0xFF1E1E20));
            canvas.drawRoundRect(track, radius, radius, paint);

            // 滑块：左段=显示器（视频），右段=四宫格（集数）
            float thumbSize = h - dp(4);
            float thumbLeft = dp(2) + (thumbnailMode ? 0f : (w - dp(4) - thumbSize));
            RectF thumb = new RectF(thumbLeft, dp(2), thumbLeft + thumbSize, dp(2) + thumbSize);
            paint.setColor(hasFocus()
                    ? (FnosTheme.isLightTheme() ? 0x332F7CFF : 0xFF30405C)
                    : (FnosTheme.isLightTheme() ? 0x1F000000 : 0xFF333338));
            canvas.drawRoundRect(thumb, radius, radius, paint);

            // 轨道描边（聚焦时变蓝）
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(border);
            paint.setColor(hasFocus() ? FnosTheme.COLOR_PRIMARY
                    : (FnosTheme.isLightTheme() ? 0x22000000 : 0xFF2E2E31));
            canvas.drawRoundRect(track, radius, radius, paint);

            float slot = (w - dp(4)) / 2f;
            drawMonitor(canvas, dp(2) + slot / 2f, h / 2f, thumbnailMode);
            drawGrid(canvas, dp(2) + slot + slot / 2f, h / 2f, !thumbnailMode);
        }

        /** 显示器图标：一块圆角屏幕 + 下面一条底座线。 */
        private void drawMonitor(Canvas canvas, float cx, float cy, boolean active) {
            float half = dp(10);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpf(1.6f));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(FnosTheme.isLightTheme()
                    ? (active ? 0xFF1B1F24 : 0x991B1F24)
                    : (active ? 0xFFFFFFFF : 0xCCFFFFFF));
            RectF screen = new RectF(cx - half, cy - dpf(8.4f), cx + half, cy + dpf(2.4f));
            canvas.drawRoundRect(screen, dpf(2.4f), dpf(2.4f), paint);
            canvas.drawLine(cx - dp(4), cy + dpf(7.4f), cx + dp(4), cy + dpf(7.4f), paint);
        }

        /** 四宫格图标：2×2 的圆角方块。 */
        private void drawGrid(Canvas canvas, float cx, float cy, boolean active) {
            float box = dp(7);
            float gap = dp(3);
            float ox = cx - (box * 2 + gap) / 2f;
            float oy = cy - (box * 2 + gap) / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpf(1.6f));
            paint.setColor(FnosTheme.isLightTheme()
                    ? (active ? 0xFF1B1F24 : 0x991B1F24)
                    : (active ? 0xFFFFFFFF : 0xCCFFFFFF));
            for (int row = 0; row < 2; row++) {
                for (int col = 0; col < 2; col++) {
                    float left = ox + col * (box + gap);
                    float top = oy + row * (box + gap);
                    canvas.drawRoundRect(new RectF(left, top, left + box, top + box),
                            dpf(1.6f), dpf(1.6f), paint);
                }
            }
        }

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                setMode(!thumbnailMode);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (contentFocus != null && contentFocus.getVisibility() == View.VISIBLE) {
                    contentFocus.requestFocus();
                    return true;
                }
            }
            return super.onKeyDown(keyCode, event);
        }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent event) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                // 已在 onKeyDown 处理，避免再触发一次 performClick
                return true;
            }
            return super.onKeyUp(keyCode, event);
        }

        private void setMode(boolean thumbnails) {
            if (thumbnailMode == thumbnails) {
                return;
            }
            thumbnailMode = thumbnails;
            render();
            final ModeSwitchView created = modeSwitch;
            if (created != null) {
                created.post(new Runnable() {
                    @Override
                    public void run() {
                        created.requestFocus();
                    }
                });
            }
        }
    }

    /** 视频视图：剧照 + 标题 + 两行简介 + 时长（网页 /v/tv/season 的「视频」布局）。 */
    private View episodeCards() {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);

        final int gap = dp(20);
        int available = context.getResources().getDisplayMetrics().widthPixels - dp(88);
        final int perRow = 6;
        final int cardWidth = Math.max(dp(180), (available - gap * (perRow - 1)) / perRow);
        final int thumbHeight = Math.round((cardWidth - dp(12)) * 9f / 16f);

        int start = pageIndex * PAGE_SIZE;
        int end = Math.min(episodes.size(), start + PAGE_SIZE);
        LinearLayout row = null;
        int inRow = 0;
        for (int i = start; i < end; i++) {
            if (inRow == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = dp(18);
                block.addView(row, rowParams);
            }
            final FnosFileEntry episode = episodes.get(i);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.leftMargin = inRow == 0 ? 0 : gap;
            row.addView(episodeCard(episode, thumbHeight, i - start < perRow), cardParams);
            inRow++;
            if (inRow >= perRow) {
                inRow = 0;
            }
        }
        return block;
    }

    private View episodeCard(final FnosFileEntry episode, int thumbHeight, final boolean firstRow) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setFocusable(true);
        card.setClickable(true);
        card.setPadding(dp(6), dp(6), dp(6), dp(10));
        card.setBackgroundDrawable(cardBackground(false));
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(cardBackground(hasFocus));
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                currentEpisodeGuid = episode.path;
                listener.onSeriesEpisodeSelected(episode);
            }
        });
        card.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP
                        && firstRow && modeSwitch != null) {
                    modeSwitch.requestFocus();
                    return true;
                }
                return false;
            }
        });
        if (contentFocus == null) {
            contentFocus = card;
        }

        RoundedFrameLayout thumb = new RoundedFrameLayout(context, 10);
        thumb.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PLACEHOLDER, 10, 0, context));
        ImageView image = new ImageView(context);
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        thumb.addView(image, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        TextView placeholder = new TextView(context);
        placeholder.setTextColor(FnosTheme.COLOR_TEXT_DIM);
        placeholder.setTextSize(13);
        placeholder.setGravity(Gravity.CENTER);
        thumb.addView(placeholder, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        if (episode.posterPath != null && episode.posterPath.length() > 0) {
            posterLoader.loadPath(posterBaseUrl, episode.posterPath, 720, 420, thumb, image, placeholder);
        }

        String resolution = episode.resolution == null ? "" : episode.resolution.trim();
        if (resolution.length() > 0) {
            TextView badge = new TextView(context);
            String label = resolution.toUpperCase(java.util.Locale.US);
            if (label.endsWith("P") && label.length() > 2) {
                label = label.substring(0, label.length() - 1);
            }
            badge.setText(label);
            badge.setTextColor(Color.WHITE);
            badge.setTextSize(13);
            badge.setBackgroundDrawable(FnosTheme.rounded(0x66000000, 5, context));
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.RIGHT);
            badgeParams.setMargins(0, 0, dp(8), dp(8));
            thumb.addView(badge, badgeParams);
        }

        card.addView(thumb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, thumbHeight));

        TextView title = new TextView(context);
        title.setText("第 " + Math.max(0, episode.episodeNumber()) + " 集. " + safeName(episode));
        title.setTextColor(FnosTheme.COLOR_TEXT);
        title.setTextSize(22);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(10);
        card.addView(title, titleParams);

        TextView overview = new TextView(context);
        overview.setText(episode.overview == null ? "" : episode.overview);
        overview.setTextColor(FnosTheme.COLOR_TEXT_60);
        overview.setTextSize(19);
        overview.setMaxLines(2);
        overview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        overview.setLineSpacing(dp(4), 1f);
        LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        overviewParams.topMargin = dp(4);
        card.addView(overview, overviewParams);

        TextView duration = new TextView(context);
        duration.setText(durationText(episode.durationSeconds));
        duration.setTextColor(FnosTheme.COLOR_TEXT_DIM);
        duration.setTextSize(17);
        LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        durationParams.topMargin = dp(6);
        card.addView(duration, durationParams);

        return card;
    }

    /** 集名（去掉扩展名）；为空时退回「第 N 集」。 */
    private String safeName(FnosFileEntry episode) {
        String name = episode.name == null ? "" : episode.name.trim();
        int dot = name.lastIndexOf('.');
        if (dot > 0 && name.length() - dot <= 5) {
            name = name.substring(0, dot);
        }
        if (name.length() == 0) {
            name = episode.tvTitle == null ? "" : episode.tvTitle.trim();
        }
        return name;
    }

    /** 与网页一致：6 分钟 57 秒 / 1 小时 3 分钟。 */
    private String durationText(long seconds) {
        if (seconds <= 0) {
            return "";
        }
        long minutes = seconds / 60;
        long rest = seconds % 60;
        if (minutes >= 60) {
            long hours = minutes / 60;
            long mins = minutes % 60;
            return mins > 0 ? hours + " 小时 " + mins + " 分钟" : hours + " 小时";
        }
        return minutes + " 分钟 " + rest + " 秒";
    }

    /** 视频卡片背景：默认透明，聚焦时蓝色描边（网页卡片本身没有底色）。 */
    private android.graphics.drawable.Drawable cardBackground(boolean focused) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(focused ? 0x0FFFFFFF : Color.TRANSPARENT);
        drawable.setCornerRadius(dp(12));
        if (focused) {
            drawable.setStroke(dp(2), 0xFF3D8BFF);
        }
        return drawable;
    }

    private View episodeGrid() {
        // 宽度按屏幕算：15 列，列间距 10dp，铺满内容区
        final int gap = dp(10);
        int available = context.getResources().getDisplayMetrics().widthPixels - dp(88);
        final int slotWidth = Math.max(dp(52), (available - gap * (EPISODES_PER_ROW - 1)) / EPISODES_PER_ROW);
        final int slotHeight = Math.max(dp(44), Math.round(slotWidth * 0.88f));

        GridView grid = new GridView(context);
        grid.setNumColumns(EPISODES_PER_ROW);
        grid.setColumnWidth(slotWidth);
        grid.setHorizontalSpacing(gap);
        grid.setVerticalSpacing(gap);
        grid.setStretchMode(GridView.NO_STRETCH);
        grid.setClipToPadding(false);
        grid.setPadding(0, dp(4), 0, dp(6));
        grid.setDrawSelectorOnTop(true);
        grid.setSelector(tileBackgroundFocused(false));
        grid.setCacheColorHint(Color.TRANSPARENT);
        grid.setBackgroundColor(Color.TRANSPARENT);

        final int start = pageIndex * PAGE_SIZE;
        final int count = Math.max(0, Math.min(episodes.size(), start + PAGE_SIZE) - start);
        grid.setAdapter(new android.widget.BaseAdapter() {
            @Override
            public int getCount() {
                return count;
            }

            @Override
            public Object getItem(int position) {
                return episodes.get(start + position);
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                FnosFileEntry episode = episodes.get(start + position);
                // 复用 convertView：滚动/翻页时不再每次 new TextView + drawable
                TextView tile = convertView instanceof TextView ? (TextView) convertView : null;
                if (tile == null) {
                    tile = new TextView(context);
                    tile.setTextSize(20);
                    tile.setTypeface(null, 1);
                    tile.setGravity(Gravity.CENTER);
                    tile.setLayoutParams(new AbsListView.LayoutParams(slotWidth, slotHeight));
                    tile.setBackgroundDrawable(tileBackground(false));
                }
                tile.setText(String.valueOf(Math.max(0, episode.episodeNumber())));
                boolean current = episode.path.equals(currentEpisodeGuid);
                tile.setTextColor(current ? FnosTheme.COLOR_PRIMARY : FnosTheme.COLOR_TEXT);
                return tile;
            }
        });

        grid.setOnItemClickListener(new android.widget.AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(android.widget.AdapterView<?> parent, View view, int position, long id) {
                FnosFileEntry episode = episodes.get(start + position);
                currentEpisodeGuid = episode.path;
                listener.onSeriesEpisodeSelected(episode);
            }
        });
        grid.setOnKeyListener(new View.OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                }
                if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                    int position = grid.getSelectedItemPosition();
                    if (position >= 0 && position < count) {
                        FnosFileEntry episode = episodes.get(start + position);
                        currentEpisodeGuid = episode.path;
                        listener.onSeriesEpisodeSelected(episode);
                        return true;
                    }
                }
                int selected = grid.getSelectedItemPosition();
                // 触摸模式进来时 selection 可能是 -1，也要能把焦点交给视图切换
                if (keyCode == KeyEvent.KEYCODE_DPAD_UP && (selected < 0 || selected < EPISODES_PER_ROW)
                        && modeSwitch != null) {
                    modeSwitch.requestFocus();
                    return true;
                }
                return false;
            }
        });
        gridIndex = grid;
        contentFocus = grid;

        int focusIndex = 0;
        for (int i = 0; i < count; i++) {
            if (episodes.get(start + i).path.equals(currentEpisodeGuid)) {
                focusIndex = i;
                break;
            }
        }
        grid.setSelection(focusIndex);
        grid.requestFocus();
        initialFocus = grid;

        // ScrollView 里 GridView 的 wrap_content 只量一行，这里给它算好的高度
        int rows = Math.max(1, (count + EPISODES_PER_ROW - 1) / EPISODES_PER_ROW);
        int gridHeight = rows * slotHeight + (rows - 1) * gap + dp(10);
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.addView(grid, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gridHeight));
        return box;
    }

    // ───────────────────────── 公共 ─────────────────────────

    private int pageOf(String episodeGuid) {
        if (episodeGuid == null || episodeGuid.length() == 0) {
            return 0;
        }
        for (int i = 0; i < episodes.size(); i++) {
            if (episodeGuid.equals(episodes.get(i).path)) {
                return i / PAGE_SIZE;
            }
        }
        return 0;
    }

    private int pageCount() {
        return Math.max(1, (episodes.size() + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    /** 季页返回：半透明圆底 + 白箭头（和剧页一致，深色页面下也看得见）。 */
    private View backBar() {
        LinearLayout bar = new LinearLayout(context);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        TextView back = new TextView(context);
        back.setText("←");
        back.setTextColor(Color.WHITE);
        back.setTextSize(24);
        back.setGravity(Gravity.CENTER);
        back.setFocusable(true);
        back.setClickable(true);
        back.setBackgroundDrawable(FnosTheme.rounded(0x66000000, 22, context));
        back.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(FnosTheme.rounded(
                        hasFocus ? 0xCC0066FF : 0x66000000, 22, context));
            }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSeriesBackRequested();
            }
        });
        bar.addView(back, new LinearLayout.LayoutParams(dp(44), dp(44)));
        return bar;
    }
    private android.graphics.drawable.Drawable tileBackground(boolean card) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        boolean light = FnosTheme.isLightTheme();
        drawable.setColor(light ? (card ? 0x0D000000 : 0xFFF2F4F8) : (card ? 0x14FFFFFF : 0xFF1B1B1D));
        drawable.setCornerRadius(dp(card ? 12 : 10));
        drawable.setStroke(dp(1), light ? 0x1A000000 : 0x2EFFFFFF);
        return drawable;
    }

    private android.graphics.drawable.Drawable pillBackground(boolean focused) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(focused ? 0x223D8BFF : Color.TRANSPARENT);
        drawable.setCornerRadius(dp(18));
        drawable.setStroke(focused ? dp(2) : dp(1),
                focused ? FnosTheme.COLOR_PRIMARY : (FnosTheme.isLightTheme() ? 0x33000000 : 0x33FFFFFF));
        return drawable;
    }

    private android.graphics.drawable.Drawable tileBackgroundFocused(boolean card) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(card ? 0x223D8BFF : 0x1A3D8BFF);
        drawable.setCornerRadius(dp(card ? 12 : 10));
        drawable.setStroke(dp(2), 0xFF3D8BFF);
        return drawable;
    }

    private LinearLayout.LayoutParams rowParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(44);
        params.rightMargin = dp(44);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private int dp(int value) {
        return FnosTheme.dp(context, value);
    }

    private float dpf(float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }
}
