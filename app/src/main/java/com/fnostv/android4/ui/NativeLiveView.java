package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.fnostv.android4.net.IptvPlaylist;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 电视直播菜单（叠在播放器之上，1:1 还原电视直播风格）：
 *
 * <pre>
 * ┌─────────┬──────────────┬──────────────────────────┐
 * │ 频道分类 │  频道列表     │       （播放器画面）      │
 * │ 央视频道 │ 1 CCTV1      │                          │
 * │ 卫视频道 │ 2 CCTV2      │                          │
 * └─────────┴──────────────┴──────────────────────────┘
 * </pre>
 *
 * 菜单背景半透明，右侧不遮挡播放器；右上角显示时间。
 */
public final class NativeLiveView {
    public interface Listener {
        void onLiveChannelSelected(IptvPlaylist.Channel channel);
    }

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler();
    private final SimpleDateFormat clockFormat = new SimpleDateFormat("HH:mm:ss", Locale.CHINA);
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("M月d日 EEEE", Locale.CHINA);

    private FrameLayout root;
    private LinearLayout groupColumn;
    private LinearLayout channelColumn;
    private TextView channelTitle;
    private TextView clockView;
    private TextView toastView;
    private ScrollView channelScroll;

    private final List<IptvPlaylist.Channel> channels = new ArrayList<IptvPlaylist.Channel>();
    private Map<String, List<IptvPlaylist.Channel>> groups =
            new LinkedHashMap<String, List<IptvPlaylist.Channel>>();
    private String currentGroup = "";
    private int currentChannelIndex = -1;
    private final List<TextView> groupRows = new ArrayList<TextView>();
    private final List<android.widget.LinearLayout> channelRows = new ArrayList<android.widget.LinearLayout>();
    private final List<TextView> channelLabels = new ArrayList<TextView>();

    public NativeLiveView(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public View create() {
        root = new FrameLayout(context);
        root.setFocusable(true);
        root.setVisibility(View.GONE);

        // 左侧一级菜单：频道分类
        LinearLayout left = new LinearLayout(context);
        left.setOrientation(LinearLayout.VERTICAL);
        left.setBackgroundColor(0xE60B0F14);
        left.setPadding(dp(10), dp(18), dp(10), dp(18));
        FrameLayout.LayoutParams leftParams = new FrameLayout.LayoutParams(
                dp(190), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        root.addView(left, leftParams);

        TextView leftTitle = new TextView(context);
        leftTitle.setText("频道分类");
        leftTitle.setTextColor(0x80FFFFFF);
        leftTitle.setTextSize(13);
        leftTitle.setPadding(dp(10), 0, 0, dp(10));
        left.addView(leftTitle);

        ScrollView groupScroll = new ScrollView(context);
        groupScroll.setVerticalScrollBarEnabled(false);
        groupColumn = new LinearLayout(context);
        groupColumn.setOrientation(LinearLayout.VERTICAL);
        groupScroll.addView(groupColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        left.addView(groupScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 二级菜单：频道列表
        LinearLayout middle = new LinearLayout(context);
        middle.setOrientation(LinearLayout.VERTICAL);
        middle.setBackgroundColor(0xD90B0F14);
        middle.setPadding(dp(12), dp(18), dp(12), dp(18));
        FrameLayout.LayoutParams middleParams = new FrameLayout.LayoutParams(
                dp(360), ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START);
        middleParams.leftMargin = dp(190);
        root.addView(middle, middleParams);

        channelTitle = new TextView(context);
        channelTitle.setText("央视频道");
        channelTitle.setTextColor(0x80FFFFFF);
        channelTitle.setTextSize(13);
        channelTitle.setPadding(dp(10), 0, 0, dp(10));
        middle.addView(channelTitle);

        channelScroll = new ScrollView(context);
        channelScroll.setVerticalScrollBarEnabled(false);
        channelColumn = new LinearLayout(context);
        channelColumn.setOrientation(LinearLayout.VERTICAL);
        channelScroll.addView(channelColumn, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        middle.addView(channelScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        // 右上角时钟（叠在播放器上）
        LinearLayout clockBox = new LinearLayout(context);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        FrameLayout.LayoutParams clockParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.RIGHT);
        clockParams.topMargin = dp(22);
        clockParams.rightMargin = dp(30);
        root.addView(clockBox, clockParams);

        TextView date = new TextView(context);
        date.setTextColor(0xCCFFFFFF);
        date.setTextSize(12);
        date.setGravity(Gravity.END);
        clockBox.addView(date);
        dateView = date;

        clockView = new TextView(context);
        clockView.setTextColor(Color.WHITE);
        clockView.setTextSize(20);
        clockView.setGravity(Gravity.END);
        clockBox.addView(clockView);

        // 轻提示（频道失效之类），不挡画面、不抢焦点，3 秒自动消失
        toastView = new TextView(context);
        toastView.setTextColor(Color.WHITE);
        toastView.setTextSize(15);
        toastView.setGravity(Gravity.CENTER);
        toastView.setPadding(dp(20), dp(10), dp(20), dp(10));
        toastView.setBackgroundDrawable(FnosTheme.rounded(0xCC101820, 10, context));
        toastView.setVisibility(View.GONE);
        FrameLayout.LayoutParams toastParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        toastParams.bottomMargin = dp(40);
        root.addView(toastView, toastParams);

        return root;
    }

    /** 底部小提示：不占满屏幕、不拦按键。 */
    public void showToast(String message) {
        if (toastView == null) {
            return;
        }
        toastView.setText(message == null ? "" : message);
        toastView.setVisibility(View.VISIBLE);
        handler.removeCallbacks(toastHideRunnable);
        handler.postDelayed(toastHideRunnable, 3500L);
    }

    private final Runnable toastHideRunnable = new Runnable() {
        @Override
        public void run() {
            if (toastView != null) {
                toastView.setVisibility(View.GONE);
            }
        }
    };

    private TextView dateView;

    /** 直播源解析结果：按 group-title 分组。 */
    public void setChannels(List<IptvPlaylist.Channel> list) {
        channels.clear();
        if (list != null) {
            channels.addAll(list);
        }
        currentChannelIndex = -1;
        groups = orderGroups(IptvPlaylist.group(channels));
        buildGroups();
        String saved = com.fnostv.android4.util.AppPrefs.string(context,
                com.fnostv.android4.util.AppPrefs.KEY_IPTV_GROUP, "");
        String first = groups.containsKey(saved) ? saved
                : (groups.isEmpty() ? "" : groups.keySet().iterator().next());
        selectGroup(first, false);
    }

    /** 分类显示顺序：央视 / 卫视 / 电影 / 数字 / 儿童 / 地方 / 综艺 / 纪录 / 其他。 */
    private Map<String, List<IptvPlaylist.Channel>> orderGroups(
            Map<String, List<IptvPlaylist.Channel>> raw) {
        Map<String, List<IptvPlaylist.Channel>> ordered =
                new LinkedHashMap<String, List<IptvPlaylist.Channel>>();
        for (String key : IptvPlaylist.CATEGORY_ORDER) {
            if (raw.containsKey(key)) {
                ordered.put(key, raw.get(key));
            }
        }
        for (String key : raw.keySet()) {
            if (!ordered.containsKey(key)) {
                ordered.put(key, raw.get(key));
            }
        }
        return ordered;
    }

    private void buildGroups() {
        groupColumn.removeAllViews();
        groupRows.clear();
        int index = 0;
        for (String name : groups.keySet()) {
            final String groupName = name;
            TextView row = groupRow(name, index++);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // 只切分类，不自动起播（避免一换分类就重新拉流）
                    selectGroup(groupName, true);
                }
            });
            row.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        focusChannel(Math.max(0, currentChannelIndex));
                        return true;
                    }
                    return false;
                }
            });
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    if (hasFocus && !groupName.equals(currentGroup)) {
                        selectGroup(groupName, false);
                    }
                    v.setBackgroundDrawable(rowBackground(hasFocus));
                }
            });
            groupRows.add(row);
            groupColumn.addView(row, rowParams(0, 4));
        }
    }

    private void selectGroup(String name, boolean focusFirst) {
        currentGroup = name == null ? "" : name;
        com.fnostv.android4.util.AppPrefs.putString(context,
                com.fnostv.android4.util.AppPrefs.KEY_IPTV_GROUP, currentGroup);
        String[] names = groups.keySet().toArray(new String[0]);
        for (int i = 0; i < groupRows.size() && i < names.length; i++) {
            boolean active = names[i].equals(currentGroup);
            groupRows.get(i).setTextColor(active ? 0xFF3D8BFF : Color.WHITE);
            groupRows.get(i).setTypeface(null, active ? 1 : 0);
        }
        channelTitle.setText(currentGroup.length() == 0 ? "频道" : currentGroup);
        if (channelScroll != null) {
            channelScroll.scrollTo(0, 0);
        }
        rebuildChannels();
        if (focusFirst) {
            focusChannel(0);
        }
    }

    private void rebuildChannels() {
        channelColumn.removeAllViews();
        channelRows.clear();
        channelLabels.clear();
        List<IptvPlaylist.Channel> list = groups.get(currentGroup);
        if (list == null) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            final IptvPlaylist.Channel channel = list.get(i);
            final int index = i;
            LinearLayout row = channelRow(i + 1, channel, i == currentChannelIndex);
            row.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    markCurrent(index);
                    listener.onLiveChannelSelected(channel);
                }
            });
            row.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackgroundDrawable(rowBackground(hasFocus));
                }
            });
            row.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    if (event.getAction() == KeyEvent.ACTION_DOWN
                            && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                        int group = groupIndex();
                        if (group >= 0 && group < groupRows.size()) {
                            groupRows.get(group).requestFocus();
                        }
                        return true;
                    }
                    return false;
                }
            });
            channelRows.add(row);
            channelColumn.addView(row, rowParams(0, 3));
        }
        if (currentChannelIndex >= channelLabels.size()) {
            currentChannelIndex = -1;
        }
        wireColumnFocus();
    }

    /** 左右方向键在两栏之间移动：分类 → 频道、频道 → 当前分类。 */
    private void wireColumnFocus() {
        if (groupRows.isEmpty()) {
            return;
        }
        int group = Math.min(Math.max(0, groupIndex()), groupRows.size() - 1);
        int groupId = groupRows.get(group).getId();
        if (!channelRows.isEmpty()) {
            int firstChannelId = channelRows.get(0).getId();
            for (int i = 0; i < channelRows.size(); i++) {
                channelRows.get(i).setNextFocusLeftId(groupId);
            }
            for (int i = 0; i < groupRows.size(); i++) {
                groupRows.get(i).setNextFocusRightId(firstChannelId);
            }
        }
    }

    private void playFirstIfIdle() {
        List<IptvPlaylist.Channel> list = groups.get(currentGroup);
        if (list != null && !list.isEmpty()) {
            markCurrent(0);
            listener.onLiveChannelSelected(list.get(0));
        }
    }

    /** 播放第一台的频道（打开直播页时调用，让画面在菜单后面先播起来）。 */
    public void playFirstChannel() {
        playFirstIfIdle();
    }

    private void markCurrent(int index) {
        if (index < 0 || index >= channelLabels.size()) {
            currentChannelIndex = -1;
            return;
        }
        currentChannelIndex = index;
        for (int i = 0; i < channelLabels.size(); i++) {
            channelLabels.get(i).setTextColor(i == index ? 0xFF3D8BFF : Color.WHITE);
            channelLabels.get(i).setTypeface(null, i == index ? 1 : 0);
        }
    }

    /** 当前频道下标可能来自上一个分类（切组后越界），统一夹到合法范围。 */
    /** 当前分类在左侧列表里的下标（找不到返回 0）。 */
    private int groupIndex() {
        int index = 0;
        for (String name : groups.keySet()) {
            if (name.equals(currentGroup)) {
                return index;
            }
            index++;
        }
        return 0;
    }

    private int safeCurrentIndex() {        if (channelRows.isEmpty()) {
            return -1;
        }
        int index = currentChannelIndex;
        if (index < 0) {
            index = 0;
        }
        if (index >= channelRows.size()) {
            index = channelRows.size() - 1;
        }
        return index;
    }

    private void focusChannel(int index) {
        if (index >= 0 && index < channelRows.size()) {
            requestFocusLater(channelRows.get(index));
        }
    }

    /** 列表刚重建时还没 layout，直接 requestFocus 会失败，等一帧再要焦点。 */
    private void requestFocusLater(final View target) {
        if (target == null) {
            return;
        }
        target.post(new Runnable() {
            @Override
            public void run() {
                if (isVisible()) {
                    target.requestFocus();
                }
            }
        });
    }

    public void show() {
        if (root != null) {
            root.setVisibility(View.VISIBLE);
        }
        startClock();
        int index = safeCurrentIndex();
        if (index >= 0) {
            requestFocusLater(channelRows.get(index));
        } else if (!groupRows.isEmpty()) {
            requestFocusLater(groupRows.get(0));
        }
    }

    /** 播放器起播后会把焦点抢回去，这里把焦点还给当前频道行。 */
    public void postRefocus() {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isVisible()) {
                    return;
                }
                int index = safeCurrentIndex();
                if (index >= 0) {
                    channelRows.get(index).requestFocus();
                }
                startClock();
            }
        }, 400L);
    }

    public void hide() {
        stopClock();
        if (root != null) {
            root.setVisibility(View.GONE);
        }
    }

    public boolean isVisible() {
        return root != null && root.getVisibility() == View.VISIBLE;
    }

    private void startClock() {
        handler.removeCallbacks(clockRunnable);
        handler.post(clockRunnable);
    }

    private void stopClock() {
        handler.removeCallbacks(clockRunnable);
    }

    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            Date now = new Date();
            clockView.setText(clockFormat.format(now));
            dateView.setText(dateFormat.format(now));
            handler.postDelayed(this, 1000L);
        }
    };

    private TextView groupRow(String name, int index) {
        TextView row = new TextView(context);
        row.setText(name);
        row.setTextSize(16);
        row.setTextColor(Color.WHITE);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(12), dp(8), dp(12));
        row.setFocusable(true);
        row.setId(View.generateViewId());
        row.setBackgroundDrawable(rowBackground(false));
        return row;
    }

    private LinearLayout channelRow(int number, IptvPlaylist.Channel channel, boolean current) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setFocusable(true);
        row.setId(View.generateViewId());
        row.setBackgroundDrawable(rowBackground(false));
        row.setPadding(dp(10), dp(9), dp(8), dp(9));

        // 台标：按频道名生成的彩色方块（离线可用，不依赖外网图片）
        TextView badge = new TextView(context);
        badge.setText(shortLabel(channel.name));
        badge.setTextSize(13);
        badge.setTextColor(Color.WHITE);
        badge.setTypeface(null, 1);
        badge.setGravity(Gravity.CENTER);
        badge.setBackgroundDrawable(FnosTheme.rounded(colorFor(channel.name), 6, context));
        row.addView(badge, new LinearLayout.LayoutParams(dp(46), dp(28)));

        TextView label = new TextView(context);
        label.setText(pad(number) + "   " + channel.name);
        label.setTextSize(17);
        label.setTextColor(current ? 0xFF3D8BFF : Color.WHITE);
        label.setTypeface(null, current ? 1 : 0);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        labelParams.leftMargin = dp(10);
        row.addView(label, labelParams);
        channelLabels.add(label);
        return row;
    }

    /** 台标文字：CCTV-1 → C1，其它取前两个字符。 */
    private static String shortLabel(String name) {
        String n = name == null ? "" : name.trim().toUpperCase(Locale.US);
        int cctv = n.indexOf("CCTV");
        if (cctv >= 0) {
            StringBuilder digits = new StringBuilder();
            for (int i = cctv + 4; i < n.length(); i++) {
                char c = n.charAt(i);
                if (c >= '0' && c <= '9') {
                    digits.append(c);
                } else if (digits.length() > 0) {
                    break;
                }
            }
            return digits.length() > 0 ? "C" + digits : "CCTV";
        }
        if (n.startsWith("CGTN")) {
            return "CGTN";
        }
        String plain = name == null ? "" : name.trim();
        return plain.length() <= 2 ? plain : plain.substring(0, 2);
    }

    /** 台标底色：按名字哈希取一个固定颜色。 */
    private static int colorFor(String name) {
        int[] colors = {0xFF2E64B8, 0xFF2F8F5B, 0xFFB44A3C, 0xFF6A4FB3,
                0xFFB07A2A, 0xFF2A8A9C, 0xFF8C3B70, 0xFF44576B};
        int hash = name == null ? 0 : name.hashCode();
        return colors[Math.abs(hash) % colors.length];
    }

    /** 焦点行高亮（直播菜单用半透明白底）。 */
    private android.graphics.drawable.Drawable rowBackground(boolean focused) {
        return FnosTheme.rounded(focused ? 0x33FFFFFF : 0x14FFFFFF, 8, context);
    }

    private static String pad(int number) {
        return number < 10 ? " " + number : String.valueOf(number);
    }

    private LinearLayout.LayoutParams rowParams(int leftMargin, int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(leftMargin);
        params.topMargin = dp(topMargin);
        return params;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
