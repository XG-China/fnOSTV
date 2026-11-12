package com.fnostv.android4.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fnostv.android4.net.FnosSettingsSummary;
import com.fnostv.android4.util.AppPrefs;

import java.util.Locale;

/**
 * TV 端设置页：只保留真实生效的偏好（播放 / 外观 / 关于）。
 * 服务器管理（媒体库、用户、任务计划、改密码）在服务器网页端进行。
 */
public final class NativeSettingsView {
    public interface Listener {
        void onCloseSettings();

        void onEditAccount();

        /** 主题/卡片样式变化需要重建界面重新着色。 */
        void onThemeChanged();
    }

    private static final String PAGE_PREFERENCE = "preference";
    private static final String PAGE_APPEARANCE = "appearance";
    private static final String PAGE_ABOUT = "about";

    private final Context context;
    private final Listener listener;
    private LinearLayout sidebar;
    private LinearLayout content;
    private TextView titleView;
    private FnosSettingsSummary summary = FnosSettingsSummary.empty();
    private String serverAddress = "";
    private String status = "";
    private String page = PAGE_PREFERENCE;

    public NativeSettingsView(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void setInitialPage(String page) {
        if (isKnownPage(page)) {
            this.page = page;
        }
    }

    /** 侧栏展示的服务器地址，例如 10.0.0.40:5666。 */
    public void setServerAddress(String address) {
        this.serverAddress = address == null ? "" : address;
    }

    public View create(String status, FnosSettingsSummary summary) {
        this.status = status == null ? "" : status;
        if (summary != null) {
            this.summary = summary;
        }

        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(FnosTheme.COLOR_APP_BG);

        LinearLayout shell = new LinearLayout(context);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(shell, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        sidebar = new LinearLayout(context);
        sidebar.setOrientation(LinearLayout.VERTICAL);
        sidebar.setPadding(dp(18), dp(24), dp(18), dp(20));
        sidebar.setBackgroundColor(FnosTheme.COLOR_SIDEBAR);
        shell.addView(sidebar, new LinearLayout.LayoutParams(dp(236), ViewGroup.LayoutParams.MATCH_PARENT));

        LinearLayout main = new LinearLayout(context);
        main.setOrientation(LinearLayout.VERTICAL);
        shell.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));

        titleView = text("", 22, FnosTheme.COLOR_TEXT);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setTypeface(null, 1);
        titleView.setPadding(dp(40), 0, dp(28), 0);
        main.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(76)));

        View divider = new View(context);
        divider.setBackgroundColor(FnosTheme.COLOR_HAIRLINE);
        main.addView(divider, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        ScrollView scroll = new ScrollView(context);
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(40), dp(26), dp(40), dp(40));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        main.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        render();
        return root;
    }

    public void refresh(String status, FnosSettingsSummary summary) {
        this.status = status == null ? "" : status;
        if (summary != null) {
            this.summary = summary;
        }
        render();
    }

    private void render() {
        if (sidebar == null || content == null) {
            return;
        }
        renderSidebar();
        content.removeAllViews();
        titleView.setText(titleForPage());
        if (status.length() > 0) {
            content.addView(statusBanner(status), rowParams(0, 18));
        }
        if (PAGE_APPEARANCE.equals(page)) {
            renderAppearance();
        } else if (PAGE_ABOUT.equals(page)) {
            renderAbout();
        } else {
            renderPreference();
        }
    }

    private boolean isKnownPage(String value) {
        return PAGE_PREFERENCE.equals(value)
                || PAGE_APPEARANCE.equals(value)
                || PAGE_ABOUT.equals(value);
    }

    private void renderSidebar() {
        sidebar.removeAllViews();
        LinearLayout brand = new LinearLayout(context);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = text("←", 22, FnosTheme.COLOR_TEXT);
        back.setGravity(Gravity.CENTER);
        back.setFocusable(true);
        back.setClickable(true);
        FocusStyler.apply(back, FnosTheme.COLOR_SIDEBAR, FnosTheme.COLOR_CARD, 10);
        back.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onCloseSettings();
            }
        });
        brand.addView(back, new LinearLayout.LayoutParams(dp(40), dp(40)));
        brand.addView(new FnosLogoMarkView(context), new LinearLayout.LayoutParams(dp(30), dp(30)));
        TextView logo = text("fnOSTV", 16, FnosTheme.COLOR_TEXT);
        logo.setTypeface(null, 1);
        logo.setPadding(dp(8), 0, 0, 0);
        brand.addView(logo, new LinearLayout.LayoutParams(0, dp(40), 1));
        sidebar.addView(brand, rowParams(0, 26));

        sidebar.addView(section("设置"), rowParams(0, 6));
        sidebar.addView(tab("播放偏好", PAGE_PREFERENCE), navParams(0, 6));
        sidebar.addView(tab("外观", PAGE_APPEARANCE), navParams(0, 6));
        sidebar.addView(tab("关于", PAGE_ABOUT), navParams(0, 22));

        View spacer = new View(context);
        sidebar.addView(spacer, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        if (serverAddress.length() > 0) {
            TextView address = muted(serverAddress, 12);
            address.setPadding(dp(14), 0, 0, dp(4));
            sidebar.addView(address);
        }
        TextView version = muted("服务版本 " + summary.serviceVersion, 12);
        version.setPadding(dp(14), 0, 0, 0);
        sidebar.addView(version);
    }

    private View tab(String label, final String targetPage) {
        boolean active = targetPage.equals(page);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), 0, dp(12), 0);
        row.setFocusable(true);
        row.setClickable(true);
        row.setBackgroundDrawable(FnosTheme.rounded(active ? FnosTheme.COLOR_CARD : FnosTheme.COLOR_SIDEBAR, 10, context));
        row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                v.setBackgroundDrawable(hasFocus
                        ? FocusStyler.glowBackground(context, FnosTheme.COLOR_CARD, 10)
                        : FnosTheme.rounded(targetPage.equals(page)
                                ? FnosTheme.COLOR_CARD : FnosTheme.COLOR_SIDEBAR, 10, context));
            }
        });
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                page = targetPage;
                render();
            }
        });

        View bar = new View(context);
        bar.setBackgroundColor(active ? FnosTheme.COLOR_PRIMARY : Color.TRANSPARENT);
        row.addView(bar, new LinearLayout.LayoutParams(dp(3), dp(18)));

        TextView text = text(label, 15, active ? FnosTheme.COLOR_TEXT : FnosTheme.COLOR_TEXT_60);
        text.setPadding(dp(12), 0, 0, 0);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        return row;
    }

    private void renderPreference() {
        content.addView(accountCard(), rowParams(0, 26));
        content.addView(sectionTitle("播放"), rowParams(0, 6));
        content.addView(muted("改动立即生效。", 13), rowParams(0, 16));
        content.addView(preferenceSwitch(
                "自动连播下一集",
                "剧集、同一目录多个视频按顺序自动播放下一个；电影不连播。",
                AppPrefs.KEY_AUTO_NEXT, true), rowParams(0, 14));
        content.addView(preferenceSwitch(
                "硬件解码优先",
                "Android 5.0 及以上优先硬解；4.4 电视固定软解以保证稳定。",
                AppPrefs.KEY_HW_DECODER, true), rowParams(0, 14));
        content.addView(preferenceSwitch(
                "原画直连（电视硬解）",
                "4K / H.265 用电视芯片解码，画面最清晰、不占 CPU；"
                        + "如果播放时电视重启或黑屏，关掉它会改用服务器转码。",
                AppPrefs.KEY_DIRECT_HW_PLAY, true), rowParams(0, 14));
        content.addView(iptvRow(), rowParams(0, 0));
    }

    /** 电视直播源（IPTV m3u/txt）设置行：点击弹框输入地址。 */
    private View iptvRow() {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(20), dp(14), dp(20), dp(14));
        group.setBackgroundDrawable(FnosTheme.glass(context, 14));
        group.setFocusable(true);
        group.setClickable(true);

        TextView title = text("电视直播源（IPTV）", 15, FnosTheme.COLOR_TEXT);
        group.addView(title);
        final TextView value = muted("", 12);
        group.addView(value, rowParams(0, 4));
        refreshIptvValue(value);

        group.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showIptvDialog(value);
            }
        });
        return group;
    }

    private void refreshIptvValue(TextView value) {
        String url = AppPrefs.string(context, AppPrefs.KEY_IPTV_URL, "");
        value.setText(url.length() == 0
                ? "未设置：点此填入 m3u / txt 直播源地址（含 .m3u8 频道列表）"
                : url);
    }

    private void showIptvDialog(final TextView value) {
        final android.widget.EditText input = new android.widget.EditText(context);
        input.setSingleLine(false);
        input.setText(AppPrefs.string(context, AppPrefs.KEY_IPTV_URL, ""));
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF8C96A3);
        input.setHint("https://.../iptv.m3u 或 tv.txt");
        input.setSelectAllOnFocus(false);

        new android.app.AlertDialog.Builder(context)
                .setTitle("电视直播源地址")
                .setMessage("填 m3u / txt 播放列表地址（支持 m3u8、rtsp、udp 组播源）。留空则显示服务器自带的频道列表。")
                .setView(input)
                .setPositiveButton("保存", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        String url = input.getText() == null ? "" : input.getText().toString().trim();
                        AppPrefs.putString(context, AppPrefs.KEY_IPTV_URL, url);
                        refreshIptvValue(value);
                    }
                })
                .setNeutralButton("清除", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface dialog, int which) {
                        AppPrefs.putString(context, AppPrefs.KEY_IPTV_URL, "");
                        refreshIptvValue(value);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View accountCard() {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(20), dp(16), dp(20), dp(16));
        card.setBackgroundDrawable(FnosTheme.glass(context, 14));
        card.addView(avatar(summary.userInitial, dp(46)), new LinearLayout.LayoutParams(dp(46), dp(46)));

        LinearLayout texts = new LinearLayout(context);
        texts.setOrientation(LinearLayout.VERTICAL);
        texts.setPadding(dp(14), 0, 0, 0);
        TextView name = text(summary.username, 17, FnosTheme.COLOR_TEXT);
        name.setTypeface(null, 1);
        texts.addView(name);
        TextView hint = muted(serverAddress.length() > 0 ? serverAddress : "已连接", 12);
        texts.addView(hint);
        card.addView(texts, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button edit = button("切换账号");
        edit.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onEditAccount();
            }
        });
        card.addView(edit, new LinearLayout.LayoutParams(dp(112), dp(38)));
        return card;
    }

    private View preferenceSwitch(String title, String description, final String key, boolean fallback) {
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(dp(20), dp(14), dp(20), dp(14));
        group.setBackgroundDrawable(FnosTheme.glass(context, 14));
        group.addView(settingSwitch(title, AppPrefs.bool(context, key, fallback), new ModernSwitch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ModernSwitch view, boolean checked) {
                AppPrefs.putBool(context, key, checked);
            }
        }), rowParams(0, 4));
        group.addView(muted(description, 12), rowParams(0, 0));
        return group;
    }

    private void renderAppearance() {
        String mode = AppPrefs.string(context, AppPrefs.KEY_THEME_MODE, FnosTheme.MODE_DARK);
        content.addView(sectionTitle("主题模式"), rowParams(0, 16));
        LinearLayout themes = new LinearLayout(context);
        themes.setOrientation(LinearLayout.HORIZONTAL);
        themes.addView(themeCard("日升日落", FnosTheme.MODE_SCHEDULE, true, false), new LinearLayout.LayoutParams(dp(160), dp(176)));
        LinearLayout.LayoutParams lightParams = new LinearLayout.LayoutParams(dp(160), dp(176));
        lightParams.leftMargin = dp(18);
        themes.addView(themeCard("浅色", FnosTheme.MODE_LIGHT, false, false), lightParams);
        LinearLayout.LayoutParams darkParams = new LinearLayout.LayoutParams(dp(160), dp(176));
        darkParams.leftMargin = dp(18);
        themes.addView(themeCard("深色", FnosTheme.MODE_DARK, false, true), darkParams);
        content.addView(themes, rowParams(0, 18));

        if (FnosTheme.MODE_SCHEDULE.equals(mode)) {
            content.addView(timeRow("日出时间", AppPrefs.KEY_THEME_SUNRISE, FnosTheme.DEFAULT_SUNRISE, 4, 11),
                    fixedWidthParams(360, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 8));
            content.addView(timeRow("日落时间", AppPrefs.KEY_THEME_SUNSET, FnosTheme.DEFAULT_SUNSET, 15, 23),
                    fixedWidthParams(360, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 28));
        } else {
            content.addView(muted("选择“日升日落”后可设定日出/日落时间：白天浅色、夜间深色。", 12), rowParams(0, 28));
        }

        content.addView(sectionTitle("卡片样式"), rowParams(0, 14));
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(20), dp(20), dp(14));
        panel.setBackgroundDrawable(FnosTheme.glass(context, 16));
        panel.addView(posterPreview(), centeredParams(150, 222, 0, 18));
        panel.addView(divider(), rowParams(0, 12));
        panel.addView(settingSwitch("评分", AppPrefs.bool(context, AppPrefs.KEY_CARD_SCORE, true), cardSwitch(AppPrefs.KEY_CARD_SCORE)), rowParams(0, 10));
        panel.addView(settingSwitch("“已观看” 标记", AppPrefs.bool(context, AppPrefs.KEY_CARD_WATCHED, false), cardSwitch(AppPrefs.KEY_CARD_WATCHED)), rowParams(0, 10));
        panel.addView(settingSwitch("分辨率", AppPrefs.bool(context, AppPrefs.KEY_CARD_RESOLUTION, true), cardSwitch(AppPrefs.KEY_CARD_RESOLUTION)), rowParams(0, 0));
        content.addView(panel, fixedWidthParams(480, ViewGroup.LayoutParams.WRAP_CONTENT, 0, 0));
    }

    private void renderAbout() {
        content.addView(aboutRow("应用", "fnOSTV for Android 4"), rowParams(0, 10));
        content.addView(aboutRow("版本", summary.version), rowParams(0, 10));
        content.addView(aboutRow("服务器", serverAddress.length() > 0 ? serverAddress : "--"), rowParams(0, 10));
        content.addView(aboutRow("用户", summary.username), rowParams(0, 10));
        content.addView(muted("服务器端的媒体库、用户与任务计划请在飞牛网页端管理。", 12), rowParams(16, 0));
    }

    private View aboutRow(String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(14), dp(20), dp(14));
        row.setBackgroundDrawable(FnosTheme.glass(context, 12));
        TextView key = text(label, 14, FnosTheme.COLOR_TEXT_MUTED);
        row.addView(key, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView valueView = text(value == null || value.length() == 0 ? "--" : value, 15, FnosTheme.COLOR_TEXT);
        row.addView(valueView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private ModernSwitch.OnCheckedChangeListener cardSwitch(final String key) {
        return new ModernSwitch.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(ModernSwitch view, boolean checked) {
                AppPrefs.putBool(context, key, checked);
                listener.onThemeChanged();
            }
        };
    }

    /** 日出/日落时间行：标签与时间框靠在一起，不再被推到最右边。 */
    private View timeRow(final String title, final String key, final String fallback, int fromHour, int toHour) {
        final String[] items = hours(fromHour, toHour);
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(10), dp(18), dp(10));
        row.setBackgroundDrawable(FnosTheme.glass(context, 12));
        row.addView(text(title, 15, FnosTheme.COLOR_TEXT),
                new LinearLayout.LayoutParams(dp(82), ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView box = valueBox(AppPrefs.string(context, key, fallback));
        box.setFocusable(true);
        box.setClickable(true);
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHourPicker(title, key, box, items);
            }
        });
        FocusStyler.apply(box, FnosTheme.COLOR_CARD, FnosTheme.COLOR_CARD_FOCUSED, 8);
        LinearLayout.LayoutParams boxParams = new LinearLayout.LayoutParams(dp(96), dp(36));
        boxParams.leftMargin = dp(14);
        row.addView(box, boxParams);
        return row;
    }

    private String[] hours(int fromHour, int toHour) {
        int count = Math.max(1, toHour - fromHour + 1);
        String[] values = new String[count];
        for (int i = 0; i < count; i++) {
            values[i] = String.format(Locale.US, "%02d:00", fromHour + i);
        }
        return values;
    }

    private void showHourPicker(String title, final String key, final TextView box, final String[] items) {
        int checked = 0;
        String current = AppPrefs.string(context, key, FnosTheme.DEFAULT_SUNRISE);
        for (int i = 0; i < items.length; i++) {
            if (items[i].equals(current)) {
                checked = i;
            }
        }
        new AlertDialog.Builder(context)
                .setTitle(title)
                .setSingleChoiceItems(items, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        AppPrefs.putString(context, key, items[which]);
                        box.setText(items[which]);
                        listener.onThemeChanged();
                        dialog.dismiss();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private View themeCard(String label, final String mode, boolean splitPreview, boolean darkPreview) {
        final boolean active = mode.equals(AppPrefs.string(context, AppPrefs.KEY_THEME_MODE, FnosTheme.MODE_DARK));
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundDrawable(active
                ? FnosTheme.glassFocused(context, 14)
                : FnosTheme.glass(context, 14));
        card.setFocusable(true);
        card.setClickable(true);
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                // 焦点用更粗的边框（3dp），已选中用 2dp，两者可区分
                int strokeDp = hasFocus ? 3 : 2;
                v.setBackgroundDrawable(hasFocus || active
                        ? FnosTheme.glassFocused(context, 14, strokeDp)
                        : FnosTheme.glass(context, 14));
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AppPrefs.putString(context, AppPrefs.KEY_THEME_MODE, mode);
                listener.onThemeChanged();
            }
        });

        int soft = darkPreview ? 0xFF2A323B : 0xFFE1E7F0;
        LinearLayout preview = new LinearLayout(context);
        preview.setPadding(dp(22), dp(18), dp(22), dp(16));
        preview.setBackgroundColor(darkPreview ? 0xFF11161C : 0xFFF2F4F7);
        LinearLayout left = previewColumn(soft);
        preview.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        rightParams.leftMargin = dp(14);
        // 日升日落卡片：左浅右深，直观表达“白天浅色、夜间深色”。
        preview.addView(previewColumn(splitPreview ? (darkPreview ? 0xFFE1E7F0 : 0xFF28313A) : soft), rightParams);
        card.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        TextView name = text((active ? "●  " : "○  ") + label, 14, active ? FnosTheme.COLOR_PRIMARY : FnosTheme.COLOR_TEXT_80);
        name.setPadding(dp(22), 0, 0, 0);
        name.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)));
        return card;
    }

    private LinearLayout previewColumn(int color) {
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        TextView image = new TextView(context);
        image.setBackgroundDrawable(FnosTheme.rounded(color, 8, context));
        column.addView(image, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(62)));
        TextView line = new TextView(context);
        line.setBackgroundDrawable(FnosTheme.rounded(color, 4, context));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(14));
        params.topMargin = dp(10);
        column.addView(line, params);
        return column;
    }

    private View posterPreview() {
        FrameLayout poster = new FrameLayout(context);
        poster.setBackgroundDrawable(FnosTheme.rounded(0xFF3D6E9C, 10, context));
        TextView title = text("TRIM", 26, Color.WHITE);
        title.setGravity(Gravity.CENTER);
        poster.addView(title, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        TextView score = text("9.2", 14, 0xFFFFE66D);
        score.setGravity(Gravity.CENTER);
        score.setBackgroundDrawable(FnosTheme.rounded(0xCC1B2029, 8, context));
        FrameLayout.LayoutParams scoreParams = new FrameLayout.LayoutParams(dp(44), dp(26), Gravity.TOP | Gravity.LEFT);
        scoreParams.leftMargin = dp(10);
        scoreParams.topMargin = dp(10);
        poster.addView(score, scoreParams);
        TextView badge = text("4K", 11, Color.WHITE);
        badge.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        badge.setPadding(0, 0, dp(10), dp(10));
        poster.addView(badge, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        return poster;
    }

    private View settingSwitch(String label, boolean checked, ModernSwitch.OnCheckedChangeListener onCheckedChangeListener) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView text = text(label, 15, FnosTheme.COLOR_TEXT);
        row.addView(text, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        ModernSwitch toggle = new ModernSwitch(context);
        toggle.setChecked(checked);
        toggle.setOnCheckedChangeListener(onCheckedChangeListener);
        row.addView(toggle, new LinearLayout.LayoutParams(dp(52), dp(30)));
        return row;
    }

    private Button button(String text) {
        Button button = new Button(context);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setTextColor(FnosTheme.COLOR_TEXT);
        FocusStyler.apply(button, FnosTheme.COLOR_CARD_FOCUSED, FnosTheme.COLOR_PRIMARY, 10);
        return button;
    }

    private TextView avatar(String text, int size) {
        TextView avatar = text(text, 16, Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_PRIMARY, size / 2, context));
        return avatar;
    }

    private TextView section(String text) {
        TextView view = muted(text, 12);
        view.setPadding(dp(14), 0, 0, 0);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private TextView sectionTitle(String text) {
        TextView view = text(text, 18, FnosTheme.COLOR_TEXT);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTypeface(null, 1);
        return view;
    }

    private TextView statusBanner(String text) {
        TextView view = text(text, 13, FnosTheme.COLOR_TEXT_80);
        view.setPadding(dp(14), dp(10), dp(14), dp(10));
        view.setBackgroundDrawable(FnosTheme.rounded(FnosTheme.COLOR_CARD, 10, context));
        return view;
    }

    private TextView muted(String text, int size) {
        return text(text, size, FnosTheme.COLOR_TEXT_MUTED);
    }

    private TextView text(String text, int size, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private View divider() {
        View view = new View(context);
        view.setBackgroundColor(FnosTheme.COLOR_HAIRLINE);
        return view;
    }

    private TextView valueBox(String value) {
        TextView view = text(value, 15, FnosTheme.COLOR_TEXT);
        view.setGravity(Gravity.CENTER);
        view.setBackgroundDrawable(FnosTheme.glass(context, 8));
        return view;
    }

    private String titleForPage() {
        if (PAGE_APPEARANCE.equals(page)) {
            return "外观";
        }
        if (PAGE_ABOUT.equals(page)) {
            return "关于";
        }
        return "播放偏好";
    }

    private LinearLayout.LayoutParams rowParams(int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(topDp);
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private LinearLayout.LayoutParams navParams(int topDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(42));
        params.topMargin = dp(topDp);
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private LinearLayout.LayoutParams fixedWidthParams(int widthDp, int heightDp, int leftDp, int bottomDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(widthDp), dp(heightDp));
        params.leftMargin = dp(leftDp);
        params.bottomMargin = dp(bottomDp);
        return params;
    }

    private LinearLayout.LayoutParams centeredParams(int widthDp, int heightDp, int leftDp, int bottomDp) {
        LinearLayout.LayoutParams params = fixedWidthParams(widthDp, heightDp, leftDp, bottomDp);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        return params;
    }

    private int dp(int value) {
        return FnosTheme.dp(context, value);
    }
}
