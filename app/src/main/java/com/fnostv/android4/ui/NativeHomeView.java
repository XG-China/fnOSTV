package com.fnostv.android4.ui;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fnostv.android4.net.FnosFileEntry;

import java.util.ArrayList;
import java.util.List;

public final class NativeHomeView {
    public interface Listener {
        void onHomeAction(String action);

        /** 首页卡片：直接打开这个条目（继续观看/最新等），不再绕到「查看全部」列表。 */
        void onHomeEntrySelected(FnosFileEntry entry);
    }

    public static final String ACTION_HOME = "home";
    public static final String ACTION_FAVORITES = "favorites";
    public static final String ACTION_FILES = "files";
    public static final String ACTION_MEDIA = "media";
    public static final String ACTION_ALL = "all";
    public static final String ACTION_MOVIES = "movies";
    public static final String ACTION_TV = "tv";
    public static final String ACTION_OTHER = "other";
    public static final String ACTION_SEARCH = "search";
    public static final String ACTION_USER = "user";
    public static final String ACTION_SETTINGS = "settings";
    public static final String ACTION_RECENT = "recent";
    public static final String ACTION_LIVE = "live";
    /** 媒体库条目 action 前缀，如 "library:<guid>"。 */
    public static final String ACTION_LIBRARY_PREFIX = "library:";
    public static final String ACTION_USER_PREFERENCE = "settings-preference";
    public static final String ACTION_USER_APPEARANCE = "settings-appearance";
    public static final String ACTION_ABOUT = "about";
    public static final String ACTION_LOGOUT = "logout";
    /** 服务端诊断（排查其它 NAS 读不到媒体库）。 */
    public static final String ACTION_DIAGNOSE = "diagnose";

    private final Context context;
    private final Listener listener;
    private final PosterLoader posterLoader = new PosterLoader();
    private FrameLayout view;
    /** 首页第一张内容卡：进首页时把光标放在内容上，而不是侧栏「首页/收藏」。 */
    private View firstContentCard;
    /** action -> 侧栏行，用来做「当前选中」高亮与焦点恢复。 */
    private final java.util.Map<String, NavItem> navItems = new java.util.HashMap<String, NavItem>();
    private String activeNavAction = ACTION_HOME;
    private LinearLayout userMenuView;
    private TextView favoriteCountView;
    private TextView libraryCountView;
    private TextView allCountView;
    private TextView movieCountView;
    private TextView tvCountView;
    private TextView liveCountView;
    private TextView otherCountView;
    private LinearLayout libraryNavContainer;
    private LinearLayout libraryCardsRow;
    private LinearLayout posterSectionContainer;
    private List<HomePosterSection> posterSections = new ArrayList<HomePosterSection>();
    private String posterBaseUrl = "";
    private String username = "--";
    private String sourceName = "--";
    private boolean admin;

    public NativeHomeView(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public View create() {
        view = new FrameLayout(context);
        view.setBackgroundColor(FnosTheme.COLOR_APP_BG);
        view.setVisibility(View.GONE);

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        view.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        shell.addView(sidebar(), new LinearLayout.LayoutParams(dp(260), ViewGroup.LayoutParams.MATCH_PARENT));
        View sidebarDivider = new View(context);
        sidebarDivider.setBackgroundColor(FnosTheme.COLOR_HAIRLINE);
        shell.addView(sidebarDivider, new LinearLayout.LayoutParams(1, ViewGroup.LayoutParams.MATCH_PARENT));
        shell.addView(content(), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        userMenuView = userMenu();
        userMenuView.setVisibility(View.GONE);
        FrameLayout.LayoutParams menuParams = new FrameLayout.LayoutParams(
                dp(190), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        menuParams.topMargin = dp(62);
        menuParams.rightMargin = dp(90);
        view.addView(userMenuView, menuParams);
        return view;
    }

    public View getView() {
        return view;
    }

    public void show() {
        if (view != null) {
            view.setVisibility(View.VISIBLE);
        }
        focusFirstContent();
        if (view != null) {
            view.post(new Runnable() {
                @Override
                public void run() {
                    focusFirstContent();
                }
            });
        }
    }

    /** 光标落在内容上；侧栏只保留选中高亮。 */
    private void focusFirstContent() {
        if (firstContentCard == null || view == null || view.getVisibility() != View.VISIBLE) {
            return;
        }
        if (view.findFocus() != null) {
            return;
        }
        firstContentCard.requestFocus();
    }

    public void hide() {
        if (view != null) {
            view.setVisibility(View.GONE);
        }
    }

    public boolean isVisible() {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    public void updateCounts(int favoriteCount, int libraryCount, int allCount, int movieCount, int tvCount, int otherCount, int recentCount) {
        setCount(favoriteCountView, favoriteCount);
        setCount(libraryCountView, libraryCount);
        setCount(allCountView, allCount);
        setCount(movieCountView, movieCount);
        setCount(tvCountView, tvCount);
        setCount(otherCountView, otherCount);
    }

    public void setPosterBaseUrl(String baseUrl) {
        posterBaseUrl = baseUrl == null ? "" : baseUrl;
        refreshPosterCards();
    }

    public void setPosterAuthorizationToken(String token) {
        posterLoader.setAuthorizationToken(token);
        refreshPosterCards();
    }

    public void updatePosterSections(List<HomePosterSection> sections) {
        posterSections = sections == null ? new ArrayList<HomePosterSection>() : new ArrayList<HomePosterSection>(sections);
        renderPosterSections();
    }

    public void updateUser(String username, String sourceName, boolean admin) {
        // 用户菜单只剩“退出登录”，不再展示用户名/来源，这里保持接口兼容即可。
    }

    public void hideUserMenu() {
        if (userMenuView != null) {
            userMenuView.setVisibility(View.GONE);
        }
    }

    private static final class NavItem {
        final View row;
        final TextView text;
        final FnosSidebarIconView icon;
        final String action;

        NavItem(View row, TextView text, FnosSidebarIconView icon, String action) {
            this.row = row;
            this.text = text;
            this.icon = icon;
            this.action = action;
        }
    }

    private LinearLayout sidebar() {
        LinearLayout sidebar = new LinearLayout(context);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(18), dp(26), dp(18), dp(24));
        sidebar.setBackgroundColor(FnosTheme.COLOR_SIDEBAR);

        LinearLayout brand = new LinearLayout(context);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.addView(new FnosLogoMarkView(context), new LinearLayout.LayoutParams(dp(34), dp(34)));
        TextView logo = new TextView(context);
        logo.setText("fnOSTV");
        logo.setTextColor(FnosTheme.COLOR_TEXT);
        logo.setTextSize(17);
        logo.setTypeface(null, 1);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        logo.setPadding(dp(10), 0, 0, 0);
        brand.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        sidebar.addView(brand, navParams(0, 26));

        sidebar.addView(navItem(FnosSidebarIconView.TYPE_HOME, "首页", ACTION_HOME, null), navParams(0, 8));
        favoriteCountView = countView();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_FAVORITE, "收藏", ACTION_FAVORITES, favoriteCountView), navParams(0, 30));

        sidebar.addView(section("媒体库"), navParams(0, 8));
        libraryNavContainer = new LinearLayout(context);
        libraryNavContainer.setOrientation(LinearLayout.VERTICAL);
        sidebar.addView(libraryNavContainer, rowParams(0, 28));
        libraryCountView = countView();

        sidebar.addView(section("分类"), navParams(0, 8));
        allCountView = countView();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_ALL, "全部", ACTION_ALL, allCountView), navParams(0, 8));
        movieCountView = countView();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_MOVIE, "电影", ACTION_MOVIES, movieCountView), navParams(0, 8));
        tvCountView = countView();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_TV, "电视节目", ACTION_TV, tvCountView), navParams(0, 8));
        liveCountView = countView();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_LIVE, "电视直播", ACTION_LIVE, liveCountView), navParams(0, 8));
        otherCountView = countView();
        sidebar.addView(navItem(FnosSidebarIconView.TYPE_OTHER, "其他", ACTION_OTHER, otherCountView), navParams(0, 8));
        return sidebar;
    }

    /** 按 REST mediadb/list 动态渲染侧栏媒体库条目（与网页侧栏一致）。 */
    public void updateLibraries(List<FnosFileEntry> libraries, java.util.Map<String, Integer> counts) {
        if (libraryNavContainer == null || libraries == null) {
            return;
        }
        libraryNavContainer.removeAllViews();
        java.util.Iterator<String> keys = navItems.keySet().iterator();
        while (keys.hasNext()) {
            if (keys.next().startsWith(ACTION_LIBRARY_PREFIX)) {
                keys.remove();
            }
        }
        for (int i = 0; i < libraries.size(); i++) {
            FnosFileEntry library = libraries.get(i);
            TextView count = countView();
            int value = counts == null ? 0 : (counts.get(library.path) == null ? 0 : counts.get(library.path));
            count.setText(String.valueOf(Math.max(0, value)));
            libraryNavContainer.addView(navItem(
                    libraryIconType(library),
                    library.name,
                    ACTION_LIBRARY_PREFIX + library.path,
                    count),
                    navParams(0, i == libraries.size() - 1 ? 0 : 2));
        }
        renderLibraryCards(libraries, counts);
    }

    /** 网页侧栏按媒体库 category 换图标。 */
    private static String libraryIconType(FnosFileEntry library) {
        String category = library == null || library.type == null ? "" : library.type.toLowerCase();
        if (category.indexOf("movie") >= 0) {
            return FnosSidebarIconView.TYPE_MOVIE;
        }
        if (category.indexOf("tv") >= 0) {
            return FnosSidebarIconView.TYPE_TV;
        }
        return FnosSidebarIconView.TYPE_OTHER;
    }

    public void updateLiveCount(int liveCount) {
        setCount(liveCountView, liveCount);
    }

    private LinearLayout content() {
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(44), 0, dp(60), 0);
        content.setBackgroundColor(FnosTheme.COLOR_APP_BG);

        LinearLayout top = new LinearLayout(context);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(context);
        title.setText("首页");
        title.setTextColor(FnosTheme.COLOR_TEXT);
        title.setTextSize(22);
        title.setTypeface(null, 1);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        top.addView(iconButton(FnosActionIconButton.TYPE_SEARCH, ACTION_SEARCH), iconParams());
        top.addView(iconButton(FnosActionIconButton.TYPE_USER, ACTION_USER), iconParams());
        top.addView(iconButton(FnosActionIconButton.TYPE_SETTINGS, ACTION_SETTINGS), iconParams());
        content.addView(top, rowHeightParams(0, 0, 78));

        View hairline = new View(context);
        hairline.setBackgroundColor(FnosTheme.COLOR_HAIRLINE);
        content.addView(hairline, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        ScrollView scroll = new ScrollView(context);
        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        // 与顶栏分割线拉开距离，避免第一个分区标题贴住分隔线
        body.setPadding(0, dp(24), 0, dp(24));
        scroll.addView(body, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        body.addView(sectionTitle("媒体库"), rowParams(0, 18));
        libraryCardsRow = new LinearLayout(context);
        libraryCardsRow.setOrientation(LinearLayout.HORIZONTAL);
        // 首页媒体库区按网页端还原，不再显示「影视大全」卡片。
        body.addView(horizontalRow(libraryCardsRow), rowParams(0, 12));

        posterSectionContainer = new LinearLayout(context);
        posterSectionContainer.setOrientation(LinearLayout.VERTICAL);
        body.addView(posterSectionContainer, rowParams(0, 0));
        renderPosterSections();
        return content;
    }

    private View navItem(String iconType, String label, final String action, TextView countView) {
        final LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), 0, dp(12), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_SIDEBAR, 10, context));

        final FnosSidebarIconView iconView = new FnosSidebarIconView(context, iconType);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(22), dp(22)));

        final TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(FnosTheme.COLOR_TEXT_60);
        text.setTextSize(15);
        text.setPadding(dp(10), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        if (countView != null) {
            row.addView(countView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }

        final NavItem item = new NavItem(row, text, iconView, action);
        navItems.put(action, item);
        applyNavStyle(item, false);
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                applyNavStyle(item, hasFocus);
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideUserMenu();
                listener.onHomeAction(action);
            }
        });
        return row;
    }

    /** 焦点态 > 选中态（蓝色）> 普通态。 */
    private void applyNavStyle(NavItem item, boolean focused) {
        boolean active = item.action.equals(activeNavAction);
        if (focused) {
            item.row.setBackgroundDrawable(FocusStyler.glowBackground(context, FnosTheme.COLOR_CARD, 10));
            item.text.setTextColor(FnosTheme.COLOR_TEXT);
            item.icon.setIconColor(FnosTheme.COLOR_TEXT);
        } else if (active) {
            item.row.setBackgroundDrawable(FnosTheme.rounded(0x1A2F7CFF, 10, context));
            item.text.setTextColor(FnosTheme.COLOR_PRIMARY);
            item.icon.setIconColor(FnosTheme.COLOR_PRIMARY);
        } else {
            item.row.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_SIDEBAR, 10, context));
            item.text.setTextColor(FnosTheme.COLOR_TEXT_60);
            item.icon.setIconColor(FnosTheme.COLOR_TEXT_60);
        }
    }

    /** 当前所在栏目（用于侧栏高亮，页面切换时调用）。 */
    public void setActiveAction(String action) {
        activeNavAction = action == null ? "" : action;
        for (NavItem item : navItems.values()) {
            applyNavStyle(item, item.row.hasFocus());
        }
    }

    private TextView section(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(FnosTheme.COLOR_TEXT_DIM);
        view.setTextSize(12);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setPadding(dp(12), 0, 0, 0);
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextColor(FnosTheme.COLOR_TEXT);
        view.setTextSize(17);
        view.setTypeface(null, 1);
        return view;
    }

    private TextView sectionLink(String text, final String action) {
        TextView view = sectionTitle(text);
        view.setFocusable(true);
        view.setClickable(true);
        view.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onHomeAction(action);
            }
        });
        return view;
    }

    private FnosMediaCardView mediaCard(String text, final String action, int shape) {
        final FnosMediaCardView card = new FnosMediaCardView(context, shape);
        card.setSummary(text);
        card.configure(posterLoader, posterBaseUrl);
        FocusStyler.applyCard(card);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onHomeAction(action);
            }
        });
        return card;
    }

    private void refreshPosterCards() {
        if (libraryCardsRow != null) {
            for (int i = 0; i < libraryCardsRow.getChildCount(); i++) {
                View child = libraryCardsRow.getChildAt(i);
                if (child instanceof FnosMediaCardView) {
                    FnosMediaCardView card = (FnosMediaCardView) child;
                    card.configure(posterLoader, posterBaseUrl);
                    Object tag = card.getTag();
                    if (tag instanceof FnosFileEntry) {
                        card.bindEntry((FnosFileEntry) tag, false);
                    }
                }
            }
        }
        renderPosterSections();
    }

    /** 首页"媒体库"卡行：每个媒体库一张 245×177 卡（与网页一致）。 */
    private void renderLibraryCards(List<FnosFileEntry> libraries, java.util.Map<String, Integer> counts) {
        if (libraryCardsRow == null) {
            return;
        }
        libraryCardsRow.removeAllViews();
        for (int i = 0; i < libraries.size(); i++) {
            FnosFileEntry library = libraries.get(i);
            FnosMediaCardView card = mediaCard(library.name, ACTION_LIBRARY_PREFIX + library.path, FnosMediaCardView.SHAPE_LIBRARY);
            card.setTag(library);
            int count = counts == null ? 0 : (counts.get(library.path) == null ? 0 : counts.get(library.path));
            card.setSummary(count > 0 ? library.name + "  " + count : library.name);
            card.bindEntry(library, false);
            libraryCardsRow.addView(card, cardParams(245, 177, i > 0 ? 20 : 0, 0));
            if (i == 0) {
                firstContentCard = card;
            }
        }
    }

    private void renderPosterSections() {
        focusFirstContent();
        if (posterSectionContainer == null) {
            return;
        }
        posterSectionContainer.removeAllViews();
        if (posterSections.size() == 0) {
            return;
        }
        for (int i = 0; i < posterSections.size(); i++) {
            HomePosterSection section = posterSections.get(i);
            // 首屏第一个分区（继续观看）与媒体库卡片之间留出更大的间距
            posterSectionContainer.addView(sectionLink(section.title, section.action), rowParams(i == 0 ? 14 : 32, 16));
            posterSectionContainer.addView(horizontalRow(sectionRow(section)), rowParams(0, 8));
        }
    }

    /** 首页每一行都放进横向滚动容器：遥控器左右移动会把焦点项滚进可视区。 */
    private View horizontalRow(View content) {
        HorizontalScrollView scroll = new HorizontalScrollView(context);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setFocusable(false);
        scroll.setPadding(0, dp(4), dp(48), dp(4));
        scroll.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    private View sectionRow(HomePosterSection section) {
        boolean continueShape = section != null && ACTION_RECENT.equals(section.action);
        int shape = continueShape ? FnosMediaCardView.SHAPE_CONTINUE : FnosMediaCardView.SHAPE_POSTER;
        int cardWidth = continueShape ? 261 : 158;
        int cardHeight = continueShape ? 209 : 288;
        int gap = continueShape ? 16 : 20;
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        List<FnosFileEntry> entries = section == null ? new ArrayList<FnosFileEntry>() : section.visibleEntries();
        if (entries.size() == 0) {
            return row;
        }
        final String sectionAction = section.action;
        for (int i = 0; i < entries.size(); i++) {
            final FnosFileEntry entry = entries.get(i);
            FnosMediaCardView card = mediaCard(entry.name, sectionAction, shape);
            card.bindEntry(entry, false);
            // 卡片点击 = 打开这个条目；行尾「查看全部」卡片才进入分区列表页
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    listener.onHomeEntrySelected(entry);
                }
            });
            row.addView(card, cardParams(cardWidth, cardHeight, i > 0 ? gap : 0, 0));
        }
        if (section.hasMore) {
            row.addView(moreCard(section.title, sectionAction), cardParams(cardWidth, cardHeight, gap, 0));
        }
        return row;
    }

    /** 行尾“查看更多”卡片：玻璃底 + 箭头，替代原来只有文字的深色块。 */
    private View moreCard(String sectionTitle, final String action) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackgroundDrawable(FnosTheme.glass(context, 12));
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(hasFocus
                        ? FnosTheme.glassFocused(context, 12)
                        : FnosTheme.glass(context, 12));
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onHomeAction(action);
            }
        });

        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextColor(FnosTheme.COLOR_TEXT_80);
        arrow.setTextSize(38);
        arrow.setGravity(Gravity.CENTER);
        card.addView(arrow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        TextView label = new TextView(context);
        label.setText("查看全部");
        label.setTextColor(FnosTheme.COLOR_TEXT_60);
        label.setTextSize(13);
        label.setGravity(Gravity.CENTER);
        card.addView(label);

        if (sectionTitle != null && sectionTitle.length() > 0) {
            TextView hint = new TextView(context);
            hint.setText(sectionTitle);
            hint.setTextColor(FnosTheme.COLOR_TEXT_DIM);
            hint.setTextSize(11);
            hint.setGravity(Gravity.CENTER);
            hint.setSingleLine(true);
            hint.setEllipsize(android.text.TextUtils.TruncateAt.END);
            hint.setPadding(dp(10), dp(4), dp(10), 0);
            card.addView(hint);
        }
        return card;
    }

    private View iconButton(String iconType, final String action) {
        FnosActionIconButton button = new FnosActionIconButton(context, iconType);
        FocusStyler.applyButton(button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (ACTION_USER.equals(action)) {
                    toggleUserMenu();
                } else {
                    hideUserMenu();
                    listener.onHomeAction(action);
                }
            }
        });
        return button;
    }

    private LinearLayout userMenu() {
        LinearLayout menu = new LinearLayout(context);
        menu.setOrientation(LinearLayout.VERTICAL);
        menu.setPadding(dp(8), dp(8), dp(8), dp(8));
        menu.setBackgroundDrawable(FnosTheme.glass(context, 12));
        refreshUserMenu(menu);
        return menu;
    }

    private void refreshUserMenu() {
        if (userMenuView != null) {
            refreshUserMenu(userMenuView);
        }
    }

    private void refreshUserMenu(LinearLayout menu) {
        menu.removeAllViews();
        // 用户菜单：服务端诊断（排查其它 NAS 读不到媒体库）+ 退出登录
        menu.addView(menuItem("!", "服务端诊断", ACTION_DIAGNOSE),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
        menu.addView(menuItem("↪", "退出登录", ACTION_LOGOUT),
                new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)));
    }


    private View menuItem(String icon, String label, final String action) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(12), 0);
        row.setFocusable(true);
        row.setClickable(true);
        FocusStyler.apply(row, FnosTheme.COLOR_CARD, FnosTheme.COLOR_CARD_FOCUSED, 4);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hideUserMenu();
                listener.onHomeAction(action);
            }
        });
        TextView iconView = new TextView(context);
        iconView.setText(icon);
        iconView.setTextColor(FnosTheme.COLOR_TEXT);
        iconView.setTextSize(15);
        iconView.setGravity(Gravity.CENTER);
        row.addView(iconView, new LinearLayout.LayoutParams(dp(22), ViewGroup.LayoutParams.MATCH_PARENT));
        TextView text = new TextView(context);
        text.setText(label);
        text.setTextColor(FnosTheme.COLOR_TEXT);
        text.setTextSize(14);
        text.setPadding(dp(8), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }


    private void toggleUserMenu() {
        if (userMenuView == null) {
            return;
        }
        userMenuView.setVisibility(userMenuView.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
        if (userMenuView.getVisibility() == View.VISIBLE) {
            userMenuView.requestFocus();
        }
    }

    private TextView countView() {
        TextView view = new TextView(context);
        view.setText("0");
        view.setTextColor(FnosTheme.COLOR_TEXT);
        view.setTextSize(13);
        return view;
    }

    private void setCount(TextView view, int count) {
        if (view != null) {
            view.setText(String.valueOf(Math.max(0, count)));
        }
    }

    private LinearLayout.LayoutParams navParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(40));
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams rowParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams rowHeightParams(int top, int bottom, int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(height));
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private LinearLayout.LayoutParams cardParams(int width, int height, int leftMargin, int bottomMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(width), dp(height));
        params.leftMargin = dp(leftMargin);
        params.bottomMargin = dp(bottomMargin);
        return params;
    }



    private LinearLayout.LayoutParams iconParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(36));
        params.leftMargin = dp(10);
        return params;
    }

    private int dp(int value) {
        return FnosTheme.dp(context, value);
    }


}
