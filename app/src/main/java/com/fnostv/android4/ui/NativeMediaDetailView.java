package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.MediaDetailInfo;
import com.fnostv.android4.net.MediaPerson;

import java.util.ArrayList;
import java.util.List;

/**
 * 影视详情页，按官方网页 /v/movie/&lt;guid&gt; 实测结构 1:1 还原：
 * backdrop（全宽 448、object-cover）+ 底部 45% 渐变 + logo（左下、距底 30）+ 进度行
 * + 播放按钮行 + 右侧刮削信息行 / 字幕音轨行 + 简介（3 行截断 + 更多）+ 演职人员横滚。
 */
public final class NativeMediaDetailView {
    public interface Listener {
        void onDetailPlayRequested(MediaDetailState state);

        void onDetailFavoriteToggled(MediaDetailState state);

        void onDetailBackRequested();

        void onDetailSourceSelected(MediaDetailState state, int sourceIndex);

        /** 点击演职人员 → 网页端 /person/:guid 人物页。 */
        void onDetailPersonSelected(MediaPerson person);
    }

    /** 浅色主题下详情页正文使用深色文字，避免白字贴白底看不见。 */
    private int textPrimary() {
        return FnosTheme.isLightTheme() ? FnosTheme.COLOR_TEXT : 0xFFFFFFFF;
    }

    private int textSecondary() {
        return FnosTheme.isLightTheme() ? FnosTheme.COLOR_TEXT_80 : 0xCCFFFFFF;
    }

    private int textMuted() {
        return FnosTheme.isLightTheme() ? FnosTheme.COLOR_TEXT_MUTED : 0x99FFFFFF;
    }

    private int circleBackground() {
        return FnosTheme.isLightTheme() ? 0x14000000 : 0x0AFFFFFF;
    }

    private int circleBackgroundFocused() {
        return FnosTheme.isLightTheme() ? 0x26000000 : 0x33FFFFFF;
    }

    private int separatorColor() {
        return FnosTheme.isLightTheme() ? 0x14000000 : 0x1AFDFDFD;
    }

    private static final int COLOR_AVATAR_PLACEHOLDER = 0x33FFFFFF;
    /** 网页分隔符 rgba(253,253,253,0.1)。 */
    private static final int COLOR_SEPARATOR = 0x1AFDFDFD;
    /** 网页评分色 rgb(242,194,20)。 */
    private static final int COLOR_SCORE = 0xFFF2C214;
    /** 网页进度条底色 rgba(204,214,224,0.3)。 */
    private static final int COLOR_TRACK = 0x4DCCD6E0;
    /** 网页圆形按钮底色 rgba(255,255,255,0.04)。 */
    private static final int COLOR_CIRCLE = 0x0AFFFFFF;
    private static final int COLOR_CIRCLE_FOCUSED = 0x33FFFFFF;
    private static final int COLOR_PLAY_FOCUSED = 0xFF3D8BFF;
    private static final int COLOR_HERO = 0xFF19191A;

    private static final int HERO_HEIGHT_DP = 448;
    private static final int GRADIENT_HEIGHT_DP = 202;
    private static final int CONTENT_PADDING_DP = 46;
    private static final int CAST_PADDING_DP = 44;
    private static final int AVATAR_SIZE_DP = 90;
    private static final int CAST_CARD_WIDTH_DP = 120;
    private static final int CAST_CARD_HEIGHT_DP = 145;
    private static final int PROGRESS_TRACK_WIDTH_DP = 240;

    private final Context context;
    private final Listener listener;
    private final PosterLoader posterLoader = new PosterLoader();

    private ScrollView view;
    private FrameLayout backdropFrame;
    private ImageView backdropImage;
    private TextView backdropFallback;
    private FrameLayout logoFrame;
    private ImageView logoImage;
    private TextView logoFallback;
    private LinearLayout progressRow;
    private View progressFill;
    private TextView remainingView;
    private LinearLayout metaRow;
    private LinearLayout subtitleRow;
    private TextView playButton;
    private TextView favoriteButton;
    private TextView sourceButton;
    private TextView backButton;
    private TextView overviewView;
    private TextView moreButton;
    private LinearLayout castBlock;
    private LinearLayout castList;
    private TextView statusView;

    private MediaDetailState state;
    private String posterBaseUrl = "";
    private boolean expanded;

    public NativeMediaDetailView(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public View create() {
        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(FnosTheme.COLOR_APP_BG);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);

        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.addView(heroView(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(HERO_HEIGHT_DP)));
        content.addView(actionsBlock(), rowParams(16, 0));
        content.addView(overviewBlock(), rowParams(12, 0));
        content.addView(castBlockView(), rowParams(12, 0));
        content.addView(statusView(), rowParams(12, 40));
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        this.view = scroll;
        return scroll;
    }

    public void show(MediaDetailState state, String posterBaseUrl) {
        this.state = state;
        this.posterBaseUrl = posterBaseUrl == null ? "" : posterBaseUrl;
        this.expanded = false;
        if (view != null) {
            view.setVisibility(View.VISIBLE);
            view.scrollTo(0, 0);
        }
        render();
        if (playButton != null) {
            playButton.requestFocus();
        }
    }

    public void update(MediaDetailState state) {
        this.state = state;
        render();
    }

    public void hide() {
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    public boolean isVisible() {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return isVisible() && handleKey(keyCode, event);
    }

    public void setPosterAuthorizationToken(String token) {
        posterLoader.setAuthorizationToken(token);
        render();
    }

    // ---------- 结构 ----------

    private View heroView() {
        FrameLayout hero = new FrameLayout(context);
        hero.setBackgroundColor(COLOR_HERO);

        backdropFrame = new FrameLayout(context);
        backdropFrame.setBackgroundColor(COLOR_HERO);
        backdropImage = new ImageView(context);
        backdropImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        backdropImage.setVisibility(View.GONE);
        backdropFrame.addView(backdropImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        backdropFallback = new TextView(context);
        backdropFallback.setVisibility(View.GONE);
        backdropFrame.addView(backdropFallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        hero.addView(backdropFrame, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        // 网页底部 45% 渐变：0deg #19191A 0% → 18% → .92 → .64 → 0
        View gradient = new View(context);
        gradient.setBackgroundDrawable(new GradientDrawable(
                GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xFF19191A, 0xFF19191A, 0xEB19191A, 0xA319191A, 0x0019191A}));
        hero.addView(gradient, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(GRADIENT_HEIGHT_DP),
                Gravity.BOTTOM));

        LinearLayout logoBar = new LinearLayout(context);
        logoBar.setOrientation(LinearLayout.HORIZONTAL);
        logoBar.setGravity(Gravity.BOTTOM);
        FrameLayout.LayoutParams logoBarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        logoBarParams.leftMargin = dp(CONTENT_PADDING_DP);
        logoBarParams.rightMargin = dp(CONTENT_PADDING_DP);
        logoBarParams.bottomMargin = dp(30);
        hero.addView(logoBar, logoBarParams);

        logoFrame = new FrameLayout(context);
        logoImage = new ImageView(context);
        logoImage.setScaleType(ImageView.ScaleType.FIT_START);
        logoImage.setVisibility(View.GONE);
        logoFrame.addView(logoImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.LEFT | Gravity.BOTTOM));
        logoFallback = new TextView(context);
        logoFallback.setTextColor(Color.WHITE);
        logoFallback.setTextSize(34);
        logoFallback.setTypeface(Typeface.DEFAULT_BOLD);
        logoFallback.setMaxLines(2);
        logoFallback.setEllipsize(TextUtils.TruncateAt.END);
        logoFallback.setGravity(Gravity.BOTTOM | Gravity.LEFT);
        logoFallback.setVisibility(View.GONE);
        logoFrame.addView(logoFallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.LEFT | Gravity.BOTTOM));
        logoBar.addView(logoFrame, new LinearLayout.LayoutParams(dp(700), dp(90)));

        return hero;
    }

    private View actionsBlock() {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(CONTENT_PADDING_DP), 0, dp(CONTENT_PADDING_DP), 0);

        // 进度行：240x4 轨道 + "剩余 X 小时 X 分钟"
        progressRow = new LinearLayout(context);
        progressRow.setOrientation(LinearLayout.HORIZONTAL);
        progressRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout track = new FrameLayout(context);
        track.setBackgroundDrawable(FnosTheme.rounded(COLOR_TRACK, 2, context));
        progressFill = new View(context);
        progressFill.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PRIMARY, 2, context));
        track.addView(progressFill, new FrameLayout.LayoutParams(dp(2), ViewGroup.LayoutParams.MATCH_PARENT));
        progressRow.addView(track, new LinearLayout.LayoutParams(
                dp(PROGRESS_TRACK_WIDTH_DP), dp(4)));

        remainingView = new TextView(context);
        remainingView.setTextSize(12);
        remainingView.setTextColor(textSecondary());
        LinearLayout.LayoutParams remainingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        remainingParams.leftMargin = dp(8);
        progressRow.addView(remainingView, remainingParams);
        block.addView(progressRow, rowParams(0, 0));

        // 操作行：播放按钮 + 圆形按钮 + 右侧刮削信息 / 字幕音轨
        LinearLayout actionRow = new LinearLayout(context);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER_VERTICAL);

        playButton = playButtonView();
        favoriteButton = circleButton("收藏");
        sourceButton = circleButton("单源");
        backButton = circleButton("返回");
        actionRow.addView(playButton, new LinearLayout.LayoutParams(dp(150), dp(54)));
        actionRow.addView(favoriteButton, circleParams());
        actionRow.addView(sourceButton, circleParams());
        actionRow.addView(backButton, circleParams());

        View spacer = new View(context);
        actionRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout rightColumn = new LinearLayout(context);
        rightColumn.setOrientation(LinearLayout.VERTICAL);
        rightColumn.setGravity(Gravity.RIGHT);

        metaRow = new LinearLayout(context);
        metaRow.setOrientation(LinearLayout.HORIZONTAL);
        metaRow.setGravity(Gravity.RIGHT);
        rightColumn.addView(metaRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        subtitleRow = new LinearLayout(context);
        subtitleRow.setOrientation(LinearLayout.HORIZONTAL);
        subtitleRow.setGravity(Gravity.RIGHT);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = dp(12);
        rightColumn.addView(subtitleRow, subtitleParams);

        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        rightParams.leftMargin = dp(40);
        actionRow.addView(rightColumn, rightParams);

        block.addView(actionRow, rowParams(8, 0));

        favoriteButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDetailFavoriteToggled(state);
            }
        });
        sourceButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSource();
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDetailBackRequested();
            }
        });
        return block;
    }

    private View overviewBlock() {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);
        block.setPadding(dp(CONTENT_PADDING_DP), 0, dp(CONTENT_PADDING_DP), 0);

        overviewView = new TextView(context);
        overviewView.setTextSize(19);
        overviewView.setTextColor(textSecondary());
        overviewView.setLineSpacing(dp(9), 1f);
        overviewView.setMaxLines(4);
        overviewView.setEllipsize(TextUtils.TruncateAt.END);
        block.addView(overviewView, rowParams(0, 0));

        moreButton = new TextView(context);
        moreButton.setText("更多");
        moreButton.setTextSize(17);
        moreButton.setTextColor(FnosTheme.COLOR_PRIMARY);
        moreButton.setGravity(Gravity.RIGHT);
        moreButton.setPadding(dp(8), dp(4), dp(8), dp(4));
        moreButton.setFocusable(true);
        moreButton.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(hasFocus ? FnosTheme.rounded(0x1AFFFFFF, 6, context) : null);
            }
        });
        moreButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                expanded = true;
                render();
            }
        });
        LinearLayout.LayoutParams moreParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        moreParams.topMargin = dp(4);
        block.addView(moreButton, moreParams);
        return block;
    }

    private View castBlockView() {
        LinearLayout block = new LinearLayout(context);
        block.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(context);
        title.setText("演职人员");
        title.setTextSize(19);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setTextColor(textPrimary());
        title.setPadding(dp(CAST_PADDING_DP), 0, dp(CONTENT_PADDING_DP), 0);
        block.addView(title, rowParams(0, 8));

        // 演职人员：按屏幕宽度自动换行（之前是横向滚动，右边总有一截看不到）
        castList = new LinearLayout(context);
        castList.setOrientation(LinearLayout.VERTICAL);
        castList.setPadding(dp(CAST_PADDING_DP), 0, dp(CONTENT_PADDING_DP), 0);
        block.addView(castList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        this.castBlock = block;
        return block;
    }

    private View statusView() {
        TextView status = new TextView(context);
        status.setTextSize(15);
        status.setTextColor(FnosTheme.COLOR_TEXT_DIM);
        status.setPadding(dp(CONTENT_PADDING_DP), 0, dp(CONTENT_PADDING_DP), 0);
        status.setVisibility(View.GONE);
        this.statusView = status;
        return status;
    }

    // ---------- 渲染 ----------

    private void render() {
        if (view == null) {
            return;
        }
        MediaDetailInfo info = state == null ? MediaDetailInfo.empty() : state.detailInfo;
        FnosFileEntry entry = state == null ? null : state.entry;
        String title = info.title.length() > 0 ? info.title : (entry == null ? "" : entry.name);

        renderHero(info, title);
        renderProgress();
        renderMeta(info);
        renderActions();
        renderOverview(info);
        renderCast(info.persons);

        String status = statusText();
        statusView.setText(status);
        statusView.setVisibility(status.length() > 0 ? View.VISIBLE : View.GONE);
    }

    private void renderHero(MediaDetailInfo info, String title) {
        if (info.backdropPath.length() > 0 && posterBaseUrl.length() > 0) {
            posterLoader.loadPath(posterBaseUrl, info.backdropPath, 1600, 1600,
                    backdropFrame, backdropImage, backdropFallback);
        } else {
            backdropFrame.setTag("");
            backdropImage.setImageDrawable(null);
            backdropImage.setVisibility(View.GONE);
        }

        if (info.logoPath.length() > 0 && posterBaseUrl.length() > 0) {
            posterLoader.loadPath(posterBaseUrl, info.logoPath, 700, 720,
                    logoFrame, logoImage, logoFallback);
        } else {
            logoFrame.setTag("");
            logoImage.setImageDrawable(null);
            logoImage.setVisibility(View.GONE);
            logoFallback.setText(title.length() == 0 ? "未命名视频" : title);
            logoFallback.setVisibility(View.VISIBLE);
        }
    }

    private void renderProgress() {
        boolean hasProgress = state != null && state.hasProgress();
        progressRow.setVisibility(hasProgress ? View.VISIBLE : View.GONE);
        if (!hasProgress) {
            return;
        }
        remainingView.setText(state.remainingLabel());
        int full = dp(PROGRESS_TRACK_WIDTH_DP);
        int fill = (int) (full * state.progressRatio());
        if (fill < dp(2)) {
            fill = dp(2);
        }
        ViewGroup.LayoutParams params = progressFill.getLayoutParams();
        params.width = fill;
        progressFill.setLayoutParams(params);
    }

    private void renderMeta(MediaDetailInfo info) {
        metaRow.removeAllViews();
        if (info.voteAverage.length() > 0) {
            addSlot(metaRow, chip(info.voteAverage + " 分", COLOR_SCORE));
        }
        if (info.contentRating.length() > 0) {
            addSlot(metaRow, chip(info.contentRating, textSecondary()));
        }
        if (info.year.length() > 0) {
            addSlot(metaRow, chip(info.year, textSecondary()));
        }
        if (info.durationLabel.length() > 0) {
            addSlot(metaRow, chip(info.durationLabel, textSecondary()));
        }
        if (info.genresText.length() > 0) {
            addSlot(metaRow, chip(info.genresText, textSecondary()));
        }
        if (info.country.length() > 0) {
            addSlot(metaRow, chip(info.country, textSecondary()));
        }
        if (info.techBadges.size() > 0) {
            addSlot(metaRow, badgeGroup(info.techBadges));
        }
        if (info.libraryName.length() > 0) {
            addSlot(metaRow, chip(info.libraryName, textSecondary()));
        }
    }

    private void renderActions() {
        boolean progress = state != null && state.hasProgress();
        playButton.setText(progress ? "继续播放" : "播放");
        favoriteButton.setText(state != null && state.favorite ? "已收藏" : "收藏");
        int sourceCount = state == null ? 0 : state.sources().size();
        sourceButton.setText(sourceCount > 1 ? "播放源" : "单源");
        sourceButton.setAlpha(sourceCount > 1 ? 1f : 0.4f);

        subtitleRow.removeAllViews();
        subtitleRow.setVisibility(state == null ? View.GONE : View.VISIBLE);
        if (state == null) {
            return;
        }
        MediaDetailInfo info = state.detailInfo;
        String subtitleText = info.subtitleLabel.length() > 0
                ? info.subtitleLabel
                : (state.hasSubtitle ? "字幕" : "无字幕");
        String audioText = info.audioLabel.length() > 0 ? info.audioLabel : "未知音频";
        subtitleRow.addView(subtitleLabel(subtitleText));
        LinearLayout.LayoutParams audioParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        audioParams.leftMargin = dp(20);
        subtitleRow.addView(subtitleLabel(audioText, audioParams));
    }

    private void renderOverview(MediaDetailInfo info) {
        String overview = info.overview;
        overviewView.setText(overview);
        boolean hasOverview = overview.length() > 0;
        overviewView.setVisibility(hasOverview ? View.VISIBLE : View.GONE);
        if (!hasOverview) {
            moreButton.setVisibility(View.GONE);
            return;
        }
        if (expanded) {
            overviewView.setMaxLines(Integer.MAX_VALUE);
            overviewView.setEllipsize(null);
            moreButton.setVisibility(View.GONE);
            return;
        }
        overviewView.setMaxLines(3);
        overviewView.setEllipsize(TextUtils.TruncateAt.END);
        moreButton.setVisibility(overview.length() > 100 ? View.VISIBLE : View.GONE);
    }

    private void renderCast(List<MediaPerson> persons) {
        castList.removeAllViews();
        boolean show = persons.size() > 0;
        castBlock.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            return;
        }
        int perRow = castPerRow();
        LinearLayout row = null;
        for (int i = 0; i < persons.size(); i++) {
            if (i % perRow == 0) {
                row = new LinearLayout(context);
                row.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowParams.bottomMargin = i + perRow < persons.size() ? dp(8) : 0;
                castList.addView(row, rowParams);
            }
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    dp(CAST_CARD_WIDTH_DP), dp(CAST_CARD_HEIGHT_DP));
            cardParams.leftMargin = i % perRow == 0 ? 0 : dp(8);
            row.addView(personCard(persons.get(i)), cardParams);
        }
    }

    /** 一行能放几个演职人员（按内容区宽度算，留出侧栏与左右 padding）。 */
    private int castPerRow() {
        int screen = context.getResources().getDisplayMetrics().widthPixels;
        int available = screen - dp(260) - dp(CAST_PADDING_DP) - dp(CONTENT_PADDING_DP);
        int unit = dp(CAST_CARD_WIDTH_DP) + dp(8);
        return Math.max(4, (available + dp(8)) / unit);
    }

    private View personCard(final MediaPerson person) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        // 网页端演职人员卡片可点开 /person/:guid 人物页；
        // 没有 guid 时也要可聚焦，否则整页没法用遥控器上下滚。
        {
            card.setFocusable(true);
            card.setClickable(person.personGuid.length() > 0);
            card.setBackgroundDrawable(FnosTheme.rounded(COLOR_CIRCLE, 8, context));
            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackgroundDrawable(FnosTheme.rounded(
                            hasFocus ? COLOR_CIRCLE_FOCUSED : COLOR_CIRCLE, 8, context));
                }
            });
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onDetailPersonSelected(person);
                }
            });
        }

        FrameLayout avatarFrame = new FrameLayout(context);
        avatarFrame.setBackgroundDrawable(FnosTheme.rounded(COLOR_AVATAR_PLACEHOLDER, 45, context));
        ImageView avatarImage = new ImageView(context);
        avatarImage.setVisibility(View.GONE);
        avatarFrame.addView(avatarImage, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        TextView avatarFallback = new TextView(context);
        avatarFallback.setVisibility(View.GONE);
        avatarFrame.addView(avatarFallback, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(
                dp(AVATAR_SIZE_DP), dp(AVATAR_SIZE_DP));
        avatarParams.bottomMargin = dp(10);
        card.addView(avatarFrame, avatarParams);

        TextView name = new TextView(context);
        name.setText(person.name);
        name.setTextSize(17);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextColor(textPrimary());
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        name.setGravity(Gravity.CENTER);
        card.addView(name, new LinearLayout.LayoutParams(dp(CAST_CARD_WIDTH_DP), dp(20)));

        TextView role = new TextView(context);
        role.setText(person.roleLabel);
        role.setTextSize(15);
        role.setTextColor(textMuted());
        role.setSingleLine(true);
        role.setEllipsize(TextUtils.TruncateAt.END);
        role.setGravity(Gravity.CENTER);
        card.addView(role, new LinearLayout.LayoutParams(dp(CAST_CARD_WIDTH_DP), dp(16)));

        if (posterBaseUrl.length() > 0 && person.profilePath.length() > 0) {
            posterLoader.loadCircle(posterBaseUrl, person.profilePath, 180,
                    avatarFrame, avatarImage, avatarFallback);
        }
        return card;
    }

    // ---------- 小组件 ----------

    private TextView playButtonView() {
        TextView button = new TextView(context);
        button.setText("播放");
        button.setTextSize(16);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setTextColor(Color.WHITE);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PRIMARY, 27, context));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(FnosTheme.rounded(
                        hasFocus ? COLOR_PLAY_FOCUSED : FnosTheme.COLOR_PRIMARY, 27, context));
            }
        });
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onDetailPlayRequested(state);
            }
        });
        return button;
    }

    private TextView circleButton(String text) {
        TextView button = new TextView(context);
        button.setText(text);
        button.setTextSize(16);
        button.setTextColor(textPrimary());
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackgroundDrawable(FnosTheme.rounded(circleBackground(), 27, context));
        button.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(FnosTheme.rounded(
                        hasFocus ? circleBackgroundFocused() : circleBackground(), 27, context));
            }
        });
        return button;
    }

    private TextView chip(String text, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(16);
        view.setTextColor(color);
        view.setSingleLine(true);
        view.setMaxWidth(dp(420));
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private View badgeGroup(List<String> badges) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < badges.size(); i++) {
            TextView badge = new TextView(context);
            badge.setText(badges.get(i));
            badge.setTextSize(12);
            badge.setTextColor(textMuted());
            badge.setPadding(dp(6), dp(2), dp(6), dp(2));
            badge.setBackgroundDrawable(FnosTheme.rounded(0x1AFFFFFF, 3, context));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) {
                params.leftMargin = dp(12);
            }
            group.addView(badge, params);
        }
        return group;
    }

    private TextView subtitleLabel(String text) {
        return subtitleLabel(text, null);
    }

    private TextView subtitleLabel(String text, LinearLayout.LayoutParams params) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(14);
        view.setTextColor(textSecondary());
        if (params != null) {
            view.setLayoutParams(params);
        }
        return view;
    }

    private TextView separatorView() {
        TextView separator = new TextView(context);
        separator.setText("/");
        separator.setTextSize(16);
        separator.setTextColor(separatorColor());
        return separator;
    }

    private void addSlot(LinearLayout row, View slot) {
        if (row.getChildCount() > 0) {
            row.addView(separatorView(), chipSlotParams());
        }
        row.addView(slot, chipSlotParams());
    }

    private LinearLayout.LayoutParams chipSlotParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        return params;
    }

    private LinearLayout.LayoutParams circleParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(54), dp(54));
        params.leftMargin = dp(8);
        return params;
    }

    private String statusText() {
        if (state == null) {
            return "";
        }
        if (state.loadingDetail) {
            return "详情加载中…";
        }
        if (state.detailError.length() > 0) {
            return state.detailError;
        }
        if (state.loadingSources) {
            return "播放源准备中…";
        }
        if (state.errorMessage.length() > 0) {
            return state.errorMessage;
        }
        return "";
    }

    // ---------- 按键 ----------

    private boolean handleKey(int keyCode, KeyEvent event) {
        if (event == null || event.getAction() != KeyEvent.ACTION_DOWN || state == null) {
            return false;
        }
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            listener.onDetailBackRequested();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            listener.onDetailFavoriteToggled(state);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE || keyCode == KeyEvent.KEYCODE_SPACE) {
            listener.onDetailPlayRequested(state);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            view.smoothScrollBy(0, -dp(120));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            view.smoothScrollBy(0, dp(120));
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            moveFocus(-1);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            moveFocus(1);
            return true;
        }
        return false;
    }

    private void moveFocus(int direction) {
        List<View> targets = focusTargets();
        if (targets.isEmpty()) {
            return;
        }
        int current = 0;
        for (int i = 0; i < targets.size(); i++) {
            if (targets.get(i).isFocused()) {
                current = i;
                break;
            }
        }
        int next = current + direction;
        if (next < 0) {
            next = targets.size() - 1;
        }
        if (next >= targets.size()) {
            next = 0;
        }
        targets.get(next).requestFocus();
    }

    private List<View> focusTargets() {
        List<View> targets = new ArrayList<View>();
        targets.add(playButton);
        targets.add(favoriteButton);
        targets.add(sourceButton);
        targets.add(backButton);
        if (moreButton.getVisibility() == View.VISIBLE) {
            targets.add(moreButton);
        }
        return targets;
    }

    private void cycleSource() {
        if (state == null || state.sources().size() <= 1) {
            return;
        }
        int next = (state.selectedSourceIndex() + 1) % state.sources().size();
        listener.onDetailSourceSelected(state, next);
    }

    private LinearLayout.LayoutParams rowParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private int dp(int value) {
        return FnosTheme.dp(context, value);
    }
}
