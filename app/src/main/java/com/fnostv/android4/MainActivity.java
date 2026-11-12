package com.fnostv.android4;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.fnostv.android4.config.ProfileStore;
import com.fnostv.android4.config.ServerProfile;
import com.fnostv.android4.media.MediaIndexStore;
import com.fnostv.android4.media.MediaCenterGateway;
import com.fnostv.android4.media.MediaCenterLoad;
import com.fnostv.android4.media.MediaLibraryClassifier;
import com.fnostv.android4.net.FavoriteStore;
import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.FnosFileList;
import com.fnostv.android4.net.FnosMediaCounts;
import com.fnostv.android4.net.FnosPlaybackSource;
import com.fnostv.android4.net.FnosRestClient;
import com.fnostv.android4.net.FnosRpcClient;
import com.fnostv.android4.net.FnosRpcException;
import com.fnostv.android4.net.FnosSession;
import com.fnostv.android4.net.FnosSessionStore;
import com.fnostv.android4.net.HttpClients;
import com.fnostv.android4.net.IptvPlaylist;
import com.fnostv.android4.net.MediaDetailInfo;
import com.fnostv.android4.net.MediaPerson;
import com.fnostv.android4.net.MediaSeriesHeader;
import com.fnostv.android4.net.RecentPlaybackStore;
import com.fnostv.android4.tv.BackNavigationState;
import com.fnostv.android4.tv.RemoteActions;
import com.fnostv.android4.tv.RemoteKeyHandler;
import com.fnostv.android4.ui.FnosTheme;
import com.fnostv.android4.ui.HomePosterSection;
import com.fnostv.android4.ui.HomePosterSections;
import com.fnostv.android4.ui.MediaDetailState;
import com.fnostv.android4.ui.NativeFileBrowserView;
import com.fnostv.android4.ui.NativeHomeView;
import com.fnostv.android4.ui.NativeLiveView;
import com.fnostv.android4.ui.NativeMediaDetailView;
import com.fnostv.android4.ui.NativeSeriesView;
import com.fnostv.android4.ui.NativeVideoPlayerView;
import com.fnostv.android4.ui.StatusOverlay;
import com.fnostv.android4.util.AppPrefs;
import com.fnostv.android4.util.Constants;
import com.fnostv.android4.util.Logger;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class MainActivity extends Activity implements RemoteActions, NativeHomeView.Listener,
        NativeFileBrowserView.Listener, NativeMediaDetailView.Listener, NativeVideoPlayerView.Listener,
        NativeSeriesView.Listener {
    private static final int BROWSER_MODE_FILES = 0;
    private static final int BROWSER_MODE_RECENT = 1;
    private static final int BROWSER_MODE_MEDIA = 2;
    private static final int BROWSER_MODE_FAVORITES = 3;
    private static final int BROWSER_MODE_CATEGORY = 4;
    private static final int BROWSER_MODE_SEARCH = 5;
    private static final int BROWSER_MODE_PERSON = 6;
    /** 人物页路由前缀：person:<personGuid>（网页端 /person/:guid）。 */
    private static final String MEDIA_NODE_PERSON = "person:";

    /** 影视库剧集导航节点：电视剧 = "tv:<tvGuid>"，季 = "tv:<tvGuid>|season:<seasonGuid>"。 */
    private static final String MEDIA_NODE_TV = "tv:";
    private static final String MEDIA_NODE_SEASON = "season:";

    private FrameLayout root;
    private NativeHomeView nativeHomeView;
    private NativeFileBrowserView fileBrowserView;
    private NativeMediaDetailView mediaDetailView;
    private NativeVideoPlayerView nativeVideoPlayerView;
    private NativeLiveView nativeLiveView;
    /** 正在播放的直播地址（重复点同一个台不再重新起流）。 */
    private String currentLiveUrl = "";
    private NativeSeriesView nativeSeriesView;
    /** 海报请求用的鉴权 token（剧集页也要用）。 */
    private String posterAuthToken = "";
    private StatusOverlay statusOverlay;
    private RemoteKeyHandler remoteKeyHandler;
    /** 服务端诊断浮层（排查媒体库读不到时截图给开发者看）。 */
    private TextView diagnosticsText;
    private View diagnosticsView;
    private ProfileStore store;
    private FnosSessionStore sessionStore;
    private RecentPlaybackStore recentPlaybackStore;
    private FavoriteStore favoriteStore;
    private MediaIndexStore mediaIndexStore;
    private ServerProfile profile;
    private boolean settingsOpen;
    private boolean nativeAuthRunning;
    private String currentFilePath = "";
    private int browserMode = BROWSER_MODE_FILES;
    private MediaDetailState mediaDetailState;
    /** 从「继续观看」进入剧集页时要高亮的剧集 guid。 */
    private String pendingEpisodeGuid = "";
    /** 从「继续观看」进剧页时要继续播放的那一集（也可当剧页大背景）。 */
    private FnosFileEntry pendingResumeEntry;

    /** 共享的影视 REST 客户端（避免并发重复登录）。 */
    private FnosRestClient restClient;
    private ServerProfile restClientProfile;
    private final Object restClientLock = new Object();
    /** REST mediadb/list 返回的库列表（侧栏/首页媒体库卡）。 */
    private List<FnosFileEntry> homeLibraries;
    /** 服务器端收藏（与本地收藏合并展示）。 */
    private List<FnosFileEntry> serverFavorites = new ArrayList<FnosFileEntry>();

    /** 当前展示的播放列表（用于剧集/同目录多视频自动连播）。 */
    private List<FnosFileEntry> playbackQueue = new ArrayList<FnosFileEntry>();
    /** 当前季页的完整剧集列表（用来做自动连播）。 */
    private List<FnosFileEntry> currentSeasonEpisodes = new ArrayList<FnosFileEntry>();
    /** QUEUE_NONE / QUEUE_EPISODES / QUEUE_DIRECTORY。 */
    private static final int QUEUE_NONE = 0;
    private static final int QUEUE_EPISODES = 1;
    private static final int QUEUE_DIRECTORY = 2;
    private int playbackQueueKind = QUEUE_NONE;

    /** 上次应用的主题签名（深浅/日升日落切换后需重建界面）。 */
    private String appliedThemeSignature = "";
    /** 上次应用的卡片样式签名（评分/已观看/分辨率开关）。 */
    private String appliedCardSignature = "";

    private String cardStyleSignature() {
        return (AppPrefs.bool(this, AppPrefs.KEY_CARD_SCORE, true) ? "1" : "0")
                + (AppPrefs.bool(this, AppPrefs.KEY_CARD_WATCHED, false) ? "1" : "0")
                + (AppPrefs.bool(this, AppPrefs.KEY_CARD_RESOLUTION, true) ? "1" : "0");
    }
    /** 打开单个库时的标题（loadMediaCenter 结果标题为默认值时覆盖）。 */
    private String pendingLibraryTitle = "";
    /** 剧集导航节点的显示标题（返回上级时恢复表头）。 */
    private final java.util.Map<String, String> mediaNodeTitles = new java.util.HashMap<String, String>();
    /** 剧集导航节点的上级节点（返回上级时精确回退，避免落到真实目录）。 */
    private final java.util.Map<String, String> mediaParentNodes = new java.util.HashMap<String, String>();
    /**
     * 影视区导航栈。
     *
     * <p>网页端是单页应用，「返回」= 回到上一个路由（/library/:guid → /list/all → /），
     * 永远不可能退到真实文件目录。TV 端用这个栈复刻同样的行为。
     */
    private final java.util.ArrayDeque<NavFrame> mediaNavStack = new java.util.ArrayDeque<NavFrame>();
    /** 当前屏幕的分类参数（BROWSER_MODE_CATEGORY 时有效）。 */
    private String currentCategory = "";
    /** 网页端列表工具条「排序」状态（服务端排序，与网页端同字段）。 */
    private String listSortColumn = FnosRestClient.SORT_CREATE_TIME;
    private String listSortType = FnosRestClient.SORT_DESC;
    /**
     * 文件夹页排序（网页端文件夹页是独立的一套布局配置：
     * 默认 sort_title「标题」+ ASC「升序」，与列表页的添加日期/降序不同）。
     */
    private String folderSortColumn = FnosRestClient.SORT_TITLE;
    private String folderSortType = FnosRestClient.SORT_ASC;
    /** 网页端列表工具条「筛选」状态：all / movie / tv / live / other。 */
    private String listTypeFilter = "all";
    /** 网页端收藏页分类标签："" = 全部，其余为 Movie / TV / Episode / LiveChannel / Person。 */
    private String favoriteTabType = "";
    /** 当前搜索关键词（返回时恢复）。 */
    private String currentSearchKeyword = "";
    /** 当前屏幕标题（返回时恢复表头）。 */
    private String currentScreenTitle = "";
    /** 转码 HLS 会话保活间隔：官方 web 约每 12 秒上报一次进度，会话才不会被回收。 */
    private static final long TRANSCODE_KEEPALIVE_INTERVAL_MS = 12000L;
    /** 转码会话有效期（服务器约 60 秒无保活即回收）；预解析的会话超过这个时间就重新解析。 */
    private static final long TRANSCODE_SESSION_TTL_MS = 45000L;
    private final Handler playbackKeepAliveHandler = new Handler();
    private FnosRestClient.TranscodeSession activeTranscodeSession;
    private final Runnable playbackKeepAliveRunnable = new Runnable() {
        @Override
        public void run() {
            final FnosRestClient.TranscodeSession session = activeTranscodeSession;
            if (session == null) {
                return;
            }
            if (!nativeVideoPlayerView.isVisible()) {
                activeTranscodeSession = null;
                return;
            }
            final long positionSeconds = nativeVideoPlayerView.currentPositionMs() / 1000L;
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        newRestClient().reportPlaybackProgress(
                                session, positionSeconds, session.durationSeconds, "");
                    } catch (FnosRpcException ex) {
                        Logger.w("Play/record keepalive failed: " + ex.getMessage());
                    } catch (RuntimeException ex) {
                        Logger.w("Play/record keepalive crashed: " + ex.getMessage());
                    }
                }
            }, "fnos-play-record").start();
            playbackKeepAliveHandler.postDelayed(this, TRANSCODE_KEEPALIVE_INTERVAL_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        appliedThemeSignature = FnosTheme.applyFromPrefs(this);
        appliedCardSignature = cardStyleSignature();
        syncPlaybackPrefs();
        store = new ProfileStore(this);
        sessionStore = new FnosSessionStore(this);
        recentPlaybackStore = new RecentPlaybackStore(this);
        favoriteStore = new FavoriteStore(this);
        mediaIndexStore = new MediaIndexStore(this);
        buildLayout();
        remoteKeyHandler = new RemoteKeyHandler(this);
        loadProfileOrSettings();
    }

    /**
     * 电视刚重启过（启动不到 3 分钟）且上次正在播放：多半是「直连硬解」把整机拖崩的。
     * 本次运行强制 VLC + 全转码，先保证能正常看，用户可在设置里恢复。
     */
    private void applyRebootGuard() {
        long elapsed = android.os.SystemClock.elapsedRealtime();
        String lastName = AppPrefs.string(this, AppPrefs.KEY_LAST_PLAY_NAME, "");
        if (elapsed > 3L * 60L * 1000L || lastName.length() == 0) {
            return;
        }
        NativeVideoPlayerView.safeMode = true;
        FnosRestClient.directHardwareEnabled = false;
        AppPrefs.putString(this, AppPrefs.KEY_LAST_PLAY_NAME, "");
        AppPrefs.putString(this, AppPrefs.KEY_LAST_PLAY_URL, "");
        Logger.w("Reboot guard: last play was " + lastName + ", safe mode on");
        showStatus("检测到上次播放后电视重启过\n本次已切换为安全模式（VLC 解码）\n可在 设置 → 原画直连 重新打开");
    }

    /** 设置里的「原画直连」开关同步给网络层（关闭时一律走服务器转码）。 */
    private void syncPlaybackPrefs() {
        FnosRestClient.directHardwareEnabled = AppPrefs.bool(this, AppPrefs.KEY_DIRECT_HW_PLAY, true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterFullScreen();
        syncPlaybackPrefs();
        String signature = FnosTheme.applyFromPrefs(this);
        if (!signature.equals(appliedThemeSignature)) {
            appliedThemeSignature = signature;
            recreate();
            return;
        }
        // 卡片样式（评分/已观看/分辨率）改变后需要重建列表卡片才会生效。
        String cardSignature = cardStyleSignature();
        if (!cardSignature.equals(appliedCardSignature)) {
            appliedCardSignature = cardSignature;
            recreate();
            return;
        }
        // 从设置页返回时刷新媒体库列表（服务端可能新增/删除了媒体库）。
        if (nativeHomeView != null && nativeHomeView.isVisible()
                && profile != null && profile.isReady()) {
            updateHomeCounts();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Constants.REQUEST_SETTINGS) {
            settingsOpen = false;
            // 首次配置还没填服务器就取消：直接退出，避免在“设置→取消”之间来回循环。
            if (resultCode == RESULT_CANCELED && !store.load().isReady()) {
                finish();
                return;
            }
            loadProfileOrSettings();
        }
    }

    /**
     * 播放器按键必须在视图树之前处理：
     * 播放时背景里的卡片/侧栏仍可能持有焦点，OK 键会先被它们消费成“点击”，
     * 结果就是按 OK 暂停时直接退出播放页。这里统一由 Activity 优先分发。
     */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        // 直播菜单叠在播放器之上：菜单显示时按键交给菜单（焦点/选台），不要让播放器抢走
        if (nativeLiveView != null && nativeLiveView.isVisible()) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0
                && nativeVideoPlayerView != null && nativeVideoPlayerView.isVisible()
                && nativeVideoPlayerView.onKeyDown(event.getKeyCode(), event)) {
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mediaDetailView != null && mediaDetailView.onKeyDown(keyCode, event)) {
            return true;
        }
        if (remoteKeyHandler.onKeyDown(keyCode, event)) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        if (!goBack()) {
            super.onBackPressed();
        }
    }

    private void buildLayout() {
        root = new FrameLayout(this);
        root.setBackgroundColor(FnosTheme.COLOR_APP_BG);

        nativeHomeView = new NativeHomeView(this, this);
        root.addView(nativeHomeView.create(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fileBrowserView = new NativeFileBrowserView(this, this);
        root.addView(fileBrowserView.create(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mediaDetailView = new NativeMediaDetailView(this, this);
        FrameLayout.LayoutParams mediaDetailParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        // 与网页一致：详情页只覆盖内容区，左侧 260 侧栏（首页/文件浏览器）保持可见。
        mediaDetailParams.leftMargin = dp(260);
        root.addView(mediaDetailView.create(), mediaDetailParams);
        nativeSeriesView = new NativeSeriesView(this, this);
        root.addView(nativeSeriesView.create(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        nativeVideoPlayerView = new NativeVideoPlayerView(this, this);
        root.addView(nativeVideoPlayerView.create(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        nativeLiveView = new NativeLiveView(this, new NativeLiveView.Listener() {
            @Override
            public void onLiveChannelSelected(IptvPlaylist.Channel channel) {
                playLiveChannel(channel);
            }
        });
        root.addView(nativeLiveView.create(), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        statusOverlay = new StatusOverlay(this);
        root.addView(statusOverlay.getView(), StatusOverlay.layoutParams());

        setContentView(root);
    }

    private void loadProfileOrSettings() {
        profile = store.load();
        fileBrowserView.setPosterBaseUrl(profile.baseUrl);
        fileBrowserView.setPosterAuthorizationToken("");
        nativeHomeView.setPosterBaseUrl(profile.baseUrl);
        nativeHomeView.setPosterAuthorizationToken("");
        if (nativeSeriesView != null) {
            nativeSeriesView.setPosterBaseUrl(profile.baseUrl);
        }
        mediaDetailView.setPosterAuthorizationToken("");
        nativeHomeView.hide();
        fileBrowserView.hide();
        mediaDetailView.hide();
        nativeVideoPlayerView.hide();
        if (!profile.isReady()) {
            showStatus("首次使用请配置飞牛服务地址");
            openSettings();
            return;
        }
        statusOverlay.hide();
        if (profile.username.length() == 0 || profile.password.length() == 0) {
            openSettings("原生模式需要填写 fnOS 管理员账号和密码");
            return;
        }
        startNativeAuthentication();
    }

    private void startNativeAuthentication() {
        if (nativeAuthRunning) {
            return;
        }
        nativeAuthRunning = true;
        showStatus("正在连接 fnOS 原生 RPC 服务...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final NativeAuthResult result = authenticateNative();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        nativeAuthRunning = false;
                        if (result.success) {
                            // 设置页偏好变化后若返回，主题已在 onResume 同步；此处确保计数
                            updateHomeCounts();
                            showNativeHome();
                        } else {
                            handleServerConnectionFailure(result.message);
                        }
                    }
                });
            }
        }, "fnos-native-auth").start();
    }

    private NativeAuthResult authenticateNative() {
        try {
            FnosSession session = sessionStore.load();
            if (session.hasToken()) {
                try {
                    FnosRpcClient authClient = new FnosRpcClient(profile, sessionStore.getOrCreateDeviceId());
                    if (authClient.authToken(session)) {
                        return NativeAuthResult.success();
                    }
                } catch (FnosRpcException ex) {
                    Logger.w("Native RPC token auth failed, retrying login: " + ex.getMessage());
                }
                sessionStore.clear();
            }
            FnosRpcClient loginClient = new FnosRpcClient(profile, sessionStore.getOrCreateDeviceId());
            try {
                FnosSession newSession = loginClient.login();
                sessionStore.save(newSession);
                return NativeAuthResult.success();
            } catch (FnosRpcException rpcError) {
                // 有的 fnOS 版本会拒绝 RPC 登录（如 131072），但网页同款 REST 登录可用；
                // 媒体库/播放/收藏都走 REST，先用 REST 会话把 App 带进去。
                FnosRestClient rest = newRestClient();
                if (rest.tryRestLogin()) {
                    Logger.w("RPC login rejected, fallback to REST session: " + rpcError.getMessage());
                    sessionStore.save(new FnosSession(rest.authorizationToken(), "", "",
                            profile.username, "", ""));
                    return NativeAuthResult.success();
                }
                throw rpcError;
            }
        } catch (FnosRpcException ex) {
            sessionStore.clear();
            Logger.w("Native RPC login failed: " + ex.getMessage());
            return NativeAuthResult.failure("原生登录失败：" + explainLoginError(ex.getMessage()));
        } catch (RuntimeException ex) {
            sessionStore.clear();
            Logger.w("Native RPC login crashed: " + ex.getMessage());
            return NativeAuthResult.failure("原生登录异常：" + ex.getMessage());
        }
    }

    @Override
    public boolean openSettings() {
        return openSettings(null, null);
    }

    private boolean openSettings(String errorMessage) {
        return openSettings(errorMessage, null);
    }

    private boolean openSettings(String errorMessage, String page) {
        if (settingsOpen) {
            return true;
        }
        settingsOpen = true;
        Intent intent = new Intent(this, SettingsActivity.class);
        if (errorMessage != null && errorMessage.length() > 0) {
            intent.putExtra(Constants.EXTRA_SETTINGS_ERROR_MESSAGE, errorMessage);
        }
        if (page != null && page.length() > 0) {
            intent.putExtra(Constants.EXTRA_SETTINGS_PAGE, page);
        }
        startActivityForResult(intent, Constants.REQUEST_SETTINGS);
        return true;
    }

    private void showNativeHome() {
        // 回到首页 = 网页端回到 "/" 路由，导航栈清空。
        mediaNavStack.clear();
        currentCategory = "";
        currentScreenTitle = "";
        playbackQueueKind = QUEUE_NONE;
        playbackQueue.clear();
        statusOverlay.hide();
        fileBrowserView.hide();
        mediaDetailView.hide();
        nativeVideoPlayerView.hide();
        nativeHomeView.updateUser(profile == null ? "" : profile.username, profile == null ? "" : profile.username, true);
        updateHomeCounts();
        nativeHomeView.show();
        setActiveNav(NativeHomeView.ACTION_HOME);
    }

    /** 记录当前屏幕，随后即可跳到新屏幕；「返回」时按此栈回退。 */
    private void pushNavFrame() {
        mediaNavStack.push(new NavFrame(browserMode, currentFilePath, currentScreenTitle, currentCategory,
                currentSearchKeyword, favoriteTabType, null));
    }

    /**
     * 记录当前列表页 + 已打开的详情页。
     *
     * <p>网页端从详情页点演职人员会跳到 /person/:guid，「返回」先回到详情页
     * （/item/:guid）再回到列表页，所以详情页也要占一帧导航栈。
     */
    private void pushDetailNavFrame(FnosFileEntry detailEntry) {
        mediaNavStack.push(new NavFrame(browserMode, currentFilePath, currentScreenTitle, currentCategory,
                currentSearchKeyword, favoriteTabType, detailEntry));
    }

    /**
     * 回退到导航栈中的上一页（网页端等价路由）。
     *
     * @return true 表示已处理返回。
     */
    private boolean restoreNavFrame() {
        if (mediaNavStack.isEmpty()) {
            showNativeHome();
            return true;
        }
        NavFrame frame = mediaNavStack.pop();
        browserMode = frame.mode;
        currentFilePath = frame.path;
        currentScreenTitle = frame.title;
        currentCategory = frame.category;
        currentSearchKeyword = frame.keyword;
        favoriteTabType = frame.favoriteTab;
        restoreScreenFor(frame);
        if (frame.detail != null) {
            // 详情页是浮层：先恢复列表，再把详情页重新盖在上面。
            openMediaDetail(frame.detail);
        }
        return true;
    }

    /** 按帧里的路由重新打开对应页面。 */
    private void restoreScreenFor(NavFrame frame) {
        switch (frame.mode) {
            case BROWSER_MODE_MEDIA:
                openMediaCenter(frame.path);
                return;
            case BROWSER_MODE_CATEGORY:
                openCategory(frame.title, frame.category);
                return;
            case BROWSER_MODE_FAVORITES:
                openFavorites();
                return;
            case BROWSER_MODE_RECENT:
                openRecentPlayback();
                return;
            case BROWSER_MODE_SEARCH:
                openSearchResults(frame.keyword);
                return;
            case BROWSER_MODE_PERSON:
                openPerson(personGuidOf(frame.path), frame.title);
                return;
            default:
                showNativeHome();
        }
    }

    private void showServerDiagnostics() {
        if (diagnosticsView == null) {
            ScrollView scroll = new ScrollView(this);
            scroll.setBackgroundColor(0xF20B0E13);
            scroll.setFillViewport(true);
            diagnosticsText = new TextView(this);
            diagnosticsText.setTextColor(0xFFE9EEF5);
            diagnosticsText.setTextSize(14);
            diagnosticsText.setPadding(dp(34), dp(24), dp(34), dp(40));
            diagnosticsText.setTextIsSelectable(true);
            scroll.addView(diagnosticsText, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            diagnosticsView = scroll;
            root.addView(diagnosticsView, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        diagnosticsView.setVisibility(View.VISIBLE);
        diagnosticsText.setText("正在读取服务端数据…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final StringBuilder report = new StringBuilder();
                try {
                    FnosRestClient rest = newRestClient();
                    report.append(rest.diagnoseMedia());
                } catch (FnosRpcException ex) {
                    report.append("ERR: ").append(ex.getMessage());
                } catch (RuntimeException ex) {
                    report.append("ERR: ").append(ex.getMessage());
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (diagnosticsText != null) {
                            diagnosticsText.setText(report.toString());
                        }
                    }
                });
            }
        }, "fnos-diagnose").start();
    }

    private void hideServerDiagnostics() {
        if (diagnosticsView != null) {
            diagnosticsView.setVisibility(View.GONE);
        }
    }

    private boolean isDiagnosticsVisible() {
        return diagnosticsView != null && diagnosticsView.getVisibility() == View.VISIBLE;
    }

    @Override
    public void onHomeAction(String action) {
        if (NativeHomeView.ACTION_HOME.equals(action)) {
            showNativeHome();
            return;
        }
        if (NativeHomeView.ACTION_FAVORITES.equals(action)) {
            pushNavFrame();
            openFavorites();
            return;
        }
        if (NativeHomeView.ACTION_ALL.equals(action)) {
            openAllItems();
            return;
        }
        if (NativeHomeView.ACTION_MOVIES.equals(action)) {
            pushNavFrame();
            openCategory("电影", NativeHomeView.ACTION_MOVIES);
            return;
        }
        if (NativeHomeView.ACTION_TV.equals(action)) {
            pushNavFrame();
            openCategory("电视节目", NativeHomeView.ACTION_TV);
            return;
        }
        if (NativeHomeView.ACTION_OTHER.equals(action)) {
            pushNavFrame();
            openCategory("其他", NativeHomeView.ACTION_OTHER);
            return;
        }
        if (NativeHomeView.ACTION_SEARCH.equals(action)) {
            openSearchDialog();
            return;
        }
        if (NativeHomeView.ACTION_USER.equals(action)) {
            openSettings(null, "preference");
            return;
        }
        if (NativeHomeView.ACTION_USER_PREFERENCE.equals(action)) {
            openSettings(null, "preference");
            return;
        }
        if (NativeHomeView.ACTION_USER_APPEARANCE.equals(action)) {
            openSettings(null, "appearance");
            return;
        }
        if (NativeHomeView.ACTION_ABOUT.equals(action)) {
            openSettings(null, "about");
            return;
        }
        if (NativeHomeView.ACTION_LOGOUT.equals(action)) {
            sessionStore.clear();
            openSettings("已退出登录，请重新登录。");
            return;
        }
        if (NativeHomeView.ACTION_SETTINGS.equals(action)) {
            openSettings();
            return;
        }
        if (NativeHomeView.ACTION_DIAGNOSE.equals(action)) {
            showServerDiagnostics();
            return;
        }
        if (NativeHomeView.ACTION_FILES.equals(action)) {
            openFileBrowser("");
            return;
        }
        if (NativeHomeView.ACTION_RECENT.equals(action)) {
            pushNavFrame();
            openRecentPlayback();
            return;
        }
        if (NativeHomeView.ACTION_MEDIA.equals(action)) {
            openAllItems();
            return;
        }
        if (NativeHomeView.ACTION_LIVE.equals(action)) {
            pushNavFrame();
            openCategory("电视直播", NativeHomeView.ACTION_LIVE);
            return;
        }
        if (action != null && action.startsWith(NativeHomeView.ACTION_LIBRARY_PREFIX)) {
            String guid = action.substring(NativeHomeView.ACTION_LIBRARY_PREFIX.length());
            openLibrary(guid);
            return;
        }
        showStatus("暂不支持该入口：" + action);
    }

    /** 侧栏高亮：首页与列表页两个侧栏同步。 */
    private void setActiveNav(String action) {
        if (nativeHomeView != null) {
            nativeHomeView.setActiveAction(action);
        }
        if (fileBrowserView != null) {
            fileBrowserView.setActiveAction(action);
        }
    }

    /** 侧栏里的分类项（全部/电影/电视节目/电视直播/其他）。 */
    private static boolean isNavCategory(String category) {
        return NativeHomeView.ACTION_ALL.equals(category)
                || NativeHomeView.ACTION_MOVIES.equals(category)
                || NativeHomeView.ACTION_TV.equals(category)
                || NativeHomeView.ACTION_LIVE.equals(category)
                || NativeHomeView.ACTION_OTHER.equals(category);
    }

    /** 媒体中心路径 → 侧栏 action（空=/list/all，媒体库 guid=对应媒体库，其它返回空表示保持原高亮）。 */
    private String navActionForPath(String path) {
        if (path == null || path.length() == 0) {
            return NativeHomeView.ACTION_ALL;
        }
        if (homeLibraries != null) {
            for (int i = 0; i < homeLibraries.size(); i++) {
                if (homeLibraries.get(i).path.equals(path)) {
                    return NativeHomeView.ACTION_LIBRARY_PREFIX + path;
                }
            }
        }
        return "";
    }

    /** 打开单个媒体库（网页侧栏/首页媒体库卡）。 */
    private void openLibrary(String guid) {
        String title = libraryTitle(guid);
        if (title.length() > 0) {
            mediaNodeTitles.put(guid, title);
        }
        pendingLibraryTitle = title;
        pushNavFrame();
        openMediaCenter(guid);
    }

    /** 打开「全部」列表（网页 /list/all）。 */
    private void openAllItems() {
        pushNavFrame();
        openMediaCenter("");
    }

    private String libraryTitle(String guid) {
        if (homeLibraries != null) {
            for (int i = 0; i < homeLibraries.size(); i++) {
                FnosFileEntry entry = homeLibraries.get(i);
                if (entry.path.equals(guid)) {
                    return entry.name;
                }
            }
        }
        return "";
    }

    /** 媒体库内进入目录：电视剧 → 剧/季页面，目录 → 子条目页（均走 REST，不进真实目录）。 */
    private void openMediaEntry(FnosFileEntry entry) {
        String parent = currentFilePath == null ? "" : currentFilePath;
        if (mediaTypeContains(entry, "tv")) {
            // 网页端 /tv/:guid。只有一个季时，剧卡片直接链到 /tv/season/:guid（single_child_guid），
            // 这里保持同样的捷径；多季时先显示季卡片网格。
            String seasonGuid = entry.singleChildGuid == null ? "" : entry.singleChildGuid.trim();
            String node = seasonGuid.length() > 0
                    ? MEDIA_NODE_TV + entry.path + "|" + MEDIA_NODE_SEASON + seasonGuid
                    : MEDIA_NODE_TV + entry.path;
            mediaNodeTitles.put(node, entry.name);
            mediaParentNodes.put(node, parent);
            pushNavFrame();
            openMediaCenter(node);
            return;
        }
        if (mediaTypeContains(entry, "season")) {
            String tvGuid = tvGuidOf(parent);
            String node = MEDIA_NODE_TV + tvGuid + "|" + MEDIA_NODE_SEASON + entry.path;
            mediaNodeTitles.put(node, entry.name);
            mediaParentNodes.put(node, parent);
            pushNavFrame();
            openMediaCenter(node);
            return;
        }
        // 目录（网页 /folder/:guid）：用 parent_guid 取直接子条目，仍然是影视中心结构。
        // 不能复用媒体库的 ancestor_guid 取法——那对文件夹恒返回 0 条，
        // 会退化成空白的「影视大全」页（用户反馈：点进视频没进入真的视频页面）。
        String node = MediaCenterGateway.NODE_FOLDER + entry.path;
        mediaNodeTitles.put(node, entry.name);
        mediaParentNodes.put(node, parent);
        pushNavFrame();
        openMediaCenter(node);
    }

    private boolean mediaTypeContains(FnosFileEntry entry, String value) {
        return entry != null && entry.type.toLowerCase().indexOf(value) >= 0;
    }

    /** 从剧集节点路径中取出电视剧 guid。 */
    private String tvGuidOf(String path) {
        if (path == null || !path.startsWith(MEDIA_NODE_TV)) {
            return "";
        }
        String rest = path.substring(MEDIA_NODE_TV.length());
        int bar = rest.indexOf('|');
        return bar >= 0 ? rest.substring(0, bar) : rest;
    }

    /** 从剧集节点路径中取出季 guid（无季节点时返回空串）。 */
    private String seasonGuidOf(String path) {
        if (path == null) {
            return "";
        }
        int index = path.indexOf(MEDIA_NODE_SEASON);
        if (index < 0) {
            return "";
        }
        String rest = path.substring(index + MEDIA_NODE_SEASON.length());
        int bar = rest.indexOf('|');
        return bar >= 0 ? rest.substring(0, bar) : rest;
    }

    /** 剧集节点返回上一级由导航栈负责（见 {@link #restoreNavFrame()}）。 */

    @Override
    public void onFileEntrySelected(FnosFileEntry entry) {
        if (entry.directory && (browserMode == BROWSER_MODE_MEDIA
                || browserMode == BROWSER_MODE_CATEGORY)) {
            // 网页端分类页（/list/all|movie|tv|live|other）里点目录/剧集，
            // 同样是影视中心的结构页，绝不能掉进真实文件系统目录树。
            openMediaEntry(entry);
            return;
        }
        if (entry.directory && browserMode == BROWSER_MODE_RECENT) {
            return;
        }
        if (entry.directory) {
            openFileBrowser(entry.path);
            return;
        }
        // 剧集（Episode）不直接进单片详情：网页端点继续观看会先进入该剧的剧集页。
        if (mediaTypeContains(entry, "episode") && entry.parentGuid.length() > 0) {
            openEpisodeSeason(entry);
            return;
        }
        openMediaDetail(entry);
    }

    /** 从剧集条目打开它所属的那一季（季页），并把当前集高亮出来。 */
    private void openEpisodeSeason(FnosFileEntry entry) {
        String tvGuid = entry.ancestorGuid.length() > 0 ? entry.ancestorGuid : tvGuidOf(currentFilePath);
        if (tvGuid.length() == 0) {
            openMediaDetail(entry);
            return;
        }
        // 继续观看：直接进当前集所属的那一季（季页），不是整部剧的剧页
        String node = MEDIA_NODE_TV + tvGuid + "|" + MEDIA_NODE_SEASON + entry.parentGuid;
        String title = entry.tvTitle.length() > 0 ? entry.tvTitle : entry.name;
        mediaNodeTitles.put(node, title);
        mediaParentNodes.put(node, currentFilePath);
        pendingEpisodeGuid = entry.path;
        pendingResumeEntry = entry;
        pushNavFrame();
        openMediaCenter(node);
    }

    @Override
    public void onHomeEntrySelected(FnosFileEntry entry) {
        if (entry == null) {
            return;
        }
        if (mediaTypeContains(entry, "episode") && entry.parentGuid.length() > 0) {
            openEpisodeSeason(entry);
            return;
        }
        if (entry.directory) {
            openMediaEntry(entry);
            return;
        }
        openMediaDetail(entry);
    }

    @Override
    public void onSeriesSeasonSelected(FnosFileEntry season) {
        String tvGuid = season.ancestorGuid.length() > 0 ? season.ancestorGuid : tvGuidOf(currentFilePath);
        String node = MEDIA_NODE_TV + tvGuid + "|" + MEDIA_NODE_SEASON + season.path;
        mediaNodeTitles.put(node, season.seasonLabel());
        mediaParentNodes.put(node, currentFilePath);
        nativeSeriesView.hide();
        pushNavFrame();
        openMediaCenter(node);
    }

    @Override
    public void onSeriesEpisodeSelected(FnosFileEntry episode) {
        if (episode == null) {
            return;
        }
        // 用整季剧集列表做连播队列，播完自动下一集
        setPlaybackQueue(currentSeasonEpisodes, QUEUE_EPISODES);
        resolveAndPlaySources(episode);
    }

    @Override
    public void onSeriesBackRequested() {
        goBack();
    }

    @Override
    public void onFileFavoriteToggled(FnosFileEntry entry) {
        boolean added = favoriteStore.toggle(entry);
        updateHomeCounts();
        Toast.makeText(this, added ? "已加入收藏：" + entry.name : "已取消收藏：" + entry.name, Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean isFileFavorite(FnosFileEntry entry) {
        return favoriteStore.isFavorite(entry);
    }

    @Override
    public void onBrowserAction(String action) {
        // 网页侧栏导航也走同一条路由逻辑，统一进导航栈。
        pushNavFrame();
        onHomeAction(action);
    }

    @Override
    public void onBrowserSortChanged(String sortColumn, String sortType) {
        String column = sortColumn == null || sortColumn.length() == 0
                ? FnosRestClient.SORT_CREATE_TIME : sortColumn;
        String type = sortType == null || sortType.length() == 0
                ? FnosRestClient.SORT_DESC : sortType;
        if (isFolderPage()) {
            folderSortColumn = column;
            folderSortType = type;
        } else {
            listSortColumn = column;
            listSortType = type;
        }
        reloadCurrentList();
    }

    /** 当前是否是网页端文件夹页（/folder/:guid）：排序配置与列表页不同。 */
    private boolean isFolderPage() {
        return browserMode == BROWSER_MODE_MEDIA
                && currentFilePath != null
                && currentFilePath.startsWith(MediaCenterGateway.NODE_FOLDER);
    }

    @Override
    public void onBrowserTypeFilterChanged(String category) {
        listTypeFilter = category == null || category.length() == 0 ? "all" : category;
        reloadCurrentList();
    }

    @Override
    public void onBrowserFavoriteTabChanged(String tabType) {
        favoriteTabType = tabType == null ? "" : tabType;
        reloadCurrentList();
    }

    /** 网页端「筛选」类型 → item/list 的 tags.type 分类。 */
    /**
     * Toolbar state for the current screen: the filter chip mirrors the sidebar
     * category and is only shown on item/list backed screens (the favorite page
     * has its own category tabs; search / person / recent have no type filter).
     */
    private void syncListToolbar() {
        if (fileBrowserView == null) {
            return;
        }
        fileBrowserView.setTypeFilter(listTypeFilter);
        boolean supportsFilter = browserMode == BROWSER_MODE_MEDIA
                || browserMode == BROWSER_MODE_CATEGORY;
        fileBrowserView.setFilterChipVisible(supportsFilter);
        // 文件夹页与列表页是两套排序配置（网页端 layout.list.* 与文件夹页独立）。
        if (isFolderPage()) {
            fileBrowserView.setSortState(folderSortColumn, folderSortType);
        } else {
            fileBrowserView.setSortState(listSortColumn, listSortType);
        }
    }

    /** Show the loaded list and sync the toolbar (filter chip / favorite tabs). */
    private void showBrowserList(MediaLoadResult result) {
        showBrowserList(result == null ? "" : result.title, result);
    }

    private void showBrowserList(String title, MediaLoadResult result) {
        if (result == null || result.list == null) {
            return;
        }
        syncListToolbar();
        if (result.seriesHeader != null) {
            // 网页端剧 / 季页：独立页面（1:1 版式）
            fileBrowserView.hide();
            mediaDetailView.hide();
            nativeHomeView.hide();
            String focusEpisode = pendingEpisodeGuid;
            pendingEpisodeGuid = "";
            if (nativeSeriesView != null) {
                nativeSeriesView.setPosterBaseUrl(profile == null ? "" : profile.baseUrl);
                nativeSeriesView.setPosterAuthorizationToken(posterAuthToken);
            }
            FnosFileEntry resumeEntry = pendingResumeEntry;
            pendingResumeEntry = null;
            String backdrop = resumeEntry != null && resumeEntry.posterPath.length() > 0
                    ? resumeEntry.posterPath : "";
            if (result.episodes) {
                currentSeasonEpisodes = new ArrayList<FnosFileEntry>(result.list.entries);
                nativeSeriesView.showEpisodes(title, result.seriesHeader, result.list.entries,
                        result.seasons, result.currentSeasonGuid, focusEpisode);
            } else {
                nativeSeriesView.showSeasons(title, result.seriesHeader, result.list.entries,
                        focusEpisode, resumeEntry, backdrop);
            }
            return;
        }
        if (nativeSeriesView != null) {
            nativeSeriesView.hide();
        }
        // 媒体库/分类里如果整库都是直播频道，直接用直播菜单 UI（左侧分类 + 右侧频道、背后播放）
        if (isLiveChannelList(result.list.entries)) {
            showLiveFromEntries(result.list.entries, title);
            return;
        }
        fileBrowserView.showCustom(title, result.subtitle, result.list.entries, result.sortEntries);
    }

    /** 列表里绝大多数是直播频道 → 认为这是直播库。 */
    private boolean isLiveChannelList(List<FnosFileEntry> entries) {
        if (entries == null || entries.size() == 0) {
            return false;
        }
        int live = 0;
        for (int i = 0; i < entries.size(); i++) {
            FnosFileEntry entry = entries.get(i);
            if (entry != null && entry.isLive()) {
                live++;
            }
        }
        return live * 2 >= entries.size();
    }

    /** 把服务器直播库的条目转成直播菜单数据并显示。 */
    private void showLiveFromEntries(List<FnosFileEntry> entries, String title) {
        List<IptvPlaylist.Channel> channels = new ArrayList<IptvPlaylist.Channel>();
        for (int i = 0; i < entries.size(); i++) {
            FnosFileEntry entry = entries.get(i);
            if (entry == null || !entry.isLive()) {
                continue;
            }
            // 分组按频道名识别（媒体库名字只是入口，不参与分类）
            channels.add(IptvPlaylist.Channel.forServerItem(entry.name, entry.path, ""));
        }
        if (channels.size() == 0) {
            showStatus("这个库里没有可播放的直播频道");
            return;
        }
        fileBrowserView.hide();
        mediaDetailView.hide();
        nativeHomeView.hide();
        if (nativeSeriesView != null) {
            nativeSeriesView.hide();
        }
        nativeLiveView.setChannels(channels);
        nativeLiveView.show();
        nativeVideoPlayerView.setMenuMode(true);
        nativeLiveView.playFirstChannel();
        currentScreenTitle = "电视直播";
    }

    private String effectiveCategory(String base) {
        if (listTypeFilter == null || listTypeFilter.length() == 0 || "all".equals(listTypeFilter)) {
            return base == null || base.length() == 0 ? "all" : base;
        }
        return listTypeFilter;
    }

    /**
     * Sidebar category -> toolbar filter. On the web the sidebar category and the
     * list toolbar filter are the same dimension (category key: all / movie / tv /
     * other / live / favorite), so switching category in the sidebar must resync
     * the filter chip, otherwise the list keeps the previous filter.
     */
    private void applyCategoryFilter(String category) {
        String normalized = category == null ? "" : category.trim().toLowerCase();
        if ("movie".equals(normalized) || "movies".equals(normalized)) {
            listTypeFilter = "movie";
        } else if ("tv".equals(normalized)) {
            listTypeFilter = "tv";
        } else if ("live".equals(normalized) || "livechannel".equals(normalized)) {
            listTypeFilter = "live";
        } else if ("other".equals(normalized) || "video".equals(normalized)) {
            listTypeFilter = "other";
        } else {
            listTypeFilter = "all";
        }
    }

    /** 人物页路由里的 guid。 */
    private String personGuidOf(String path) {
        if (path == null || !path.startsWith(MEDIA_NODE_PERSON)) {
            return "";
        }
        return path.substring(MEDIA_NODE_PERSON.length());
    }

    /** 网页端 /person/:guid 人物页：展示该演职人员的作品。 */
    private void openPerson(final String personGuid, final String personName) {
        openPerson(personGuid, personName, true);
    }

    private void openPerson(final String personGuid, final String personName, final boolean applyFilter) {
        if (personGuid == null || personGuid.length() == 0) {
            return;
        }
        if (applyFilter) {
            listTypeFilter = "all";
        }
        browserMode = BROWSER_MODE_PERSON;
        currentFilePath = MEDIA_NODE_PERSON + personGuid;
        currentCategory = "";
        final String title = personName == null || personName.length() == 0 ? "演职人员" : personName;
        currentScreenTitle = title;
        nativeHomeView.hide();
        closeMediaDetail();
        fileBrowserView.setMode(NativeFileBrowserView.MODE_NORMAL);
        showStatus("正在加载作品...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MediaLoadResult result = loadPersonWorks(personGuid, title);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusOverlay.hide();
                        showBrowserList(result);
                        setPlaybackQueue(result.list.entries, QUEUE_NONE);
                    }
                });
            }
        }, "fnos-rest-person").start();
    }

    /** 演职人员作品：POST /person/item/list。 */
    private MediaLoadResult loadPersonWorks(String personGuid, String title) {
        try {
            FnosFileList list = newRestClient().personItems(personGuid);
            return MediaLoadResult.success(title,
                    list.entries.size() == 0 ? "暂无作品" : "共 " + list.entries.size() + " 部作品",
                    list, false);
        } catch (FnosRpcException ex) {
            Logger.w("REST person failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("REST person crashed: " + ex.getMessage());
        }
        return MediaLoadResult.success(title, "暂无作品",
                new FnosFileList("person", new ArrayList<FnosFileEntry>()), false);
    }

    @Override
    public void onDetailPersonSelected(MediaPerson person) {
        if (person == null || person.personGuid.length() == 0) {
            return;
        }
        // 网页端：详情页 → /person/:guid，「返回」先回详情页（/item/:guid）。
        FnosFileEntry detailEntry = mediaDetailState == null ? null : mediaDetailState.entry;
        pushDetailNavFrame(detailEntry);
        openPerson(person.personGuid, person.name);
    }

    /** 工具条变化后按新的排序/筛选重新拉取当前屏幕。 */
    private void reloadCurrentList() {
        if (fileBrowserView != null) {
            fileBrowserView.setSortState(isFolderPage() ? folderSortColumn : listSortColumn,
                    isFolderPage() ? folderSortType : listSortType);
        }
        switch (browserMode) {
            case BROWSER_MODE_MEDIA:
                openMediaCenter(currentFilePath, false);
                break;
            case BROWSER_MODE_CATEGORY:
                openCategory(currentScreenTitle, currentCategory, false);
                break;
            case BROWSER_MODE_FAVORITES:
                openFavorites(false);
                break;
            case BROWSER_MODE_RECENT:
                openRecentPlayback(false);
                break;
            case BROWSER_MODE_SEARCH:
                openSearchResults(currentSearchKeyword, false);
                break;
            case BROWSER_MODE_PERSON:
                openPerson(personGuidOf(currentFilePath), currentScreenTitle, false);
                break;
            default:
                openAllItems();
                break;
        }
    }

    @Override
    public void onDetailPlayRequested(MediaDetailState state) {
        if (state == null || state.entry == null) {
            return;
        }
        long startMs = state.hasProgress() ? state.positionSeconds * 1000L : 0L;
        List<FnosPlaybackSource> sources = state.sourcesForPlayback();
        // 详情页一打开就会预解析播放源（其中可能包含服务器转码会话）；
        // 转码会话约 60 秒没有 /play/record 保活就失效，所以停留太久后再按播放必须重新解析，
        // 否则拿到的是过期会话，分片 410、直接播不出来。
        if (sources.size() > 0 && !isStaleTranscodeSession(state.transcode)) {
            mediaDetailView.hide();
            playResolvedSources(state.entry, sources, startMs, state.transcode);
            return;
        }
        if (sources.size() == 0) {
            String url = state.entry.playbackUrl();
            if (url.length() > 0) {
                mediaDetailView.hide();
                playResolvedUrl(state.entry, url, startMs);
                return;
            }
        }
        resolveAndPlaySources(state.entry);
    }

    /** 预解析出来的转码会话是否已过期（保留安全余量，服务器 TTL 约 60 秒）。 */
    private static boolean isStaleTranscodeSession(FnosRestClient.TranscodeSession session) {
        // 直连播放的进度上报载体没有 play_link，不涉及会话过期。
        return session != null && session.playLink.length() > 0
                && System.currentTimeMillis() - session.createdAtMs > TRANSCODE_SESSION_TTL_MS;
    }

    @Override
    public void onDetailFavoriteToggled(MediaDetailState state) {
        if (state == null || state.entry == null) {
            return;
        }
        onFileFavoriteToggled(state.entry);
        state.favorite = favoriteStore.isFavorite(state.entry);
        mediaDetailView.update(state);
    }

    @Override
    public void onDetailBackRequested() {
        closeMediaDetail();
    }

    @Override
    public void onDetailSourceSelected(MediaDetailState state, int sourceIndex) {
        if (state == null) {
            return;
        }
        state.selectSource(sourceIndex);
        mediaDetailView.update(state);
    }

    @Override
    public void onNativeVideoError(FnosFileEntry entry, String url, String reason) {
        Logger.w("Native video playback failed: " + reason
                + " file=" + (entry == null ? "" : entry.name)
                + " format=" + (entry == null ? "" : entry.formatLabel()));
        if (entry != null && entry.isLive()) {
            // 直播频道失效很常见：留在选台菜单里，换个台就行，不要跳到外部播放器
            nativeVideoPlayerView.hide();
            statusOverlay.hide();
            if (nativeLiveView != null && nativeLiveView.isVisible()) {
                nativeLiveView.showToast("该频道暂时无法播放，换个频道试试");
                nativeLiveView.postRefocus();
            }
            return;
        }
        nativeVideoPlayerView.hide();
        openExternalPlayer(entry, url, "内置播放器无法播放"
                + (reason == null || reason.length() == 0 ? "" : "（" + reason + "）")
                + "，正在尝试外部播放器");
    }

    @Override
    public void onTranscodeSeekRequested(final FnosFileEntry entry, final int targetMs) {
        final FnosRestClient.TranscodeSession session = activeTranscodeSession;
        if (entry == null || session == null) {
            nativeVideoPlayerView.onTranscodeSeekFailed();
            return;
        }
        final Runnable fail = new Runnable() {
            @Override
            public void run() {
                nativeVideoPlayerView.onTranscodeSeekFailed();
            }
        };
        new Thread(new Runnable() {
            @Override
            public void run() {
                FnosRestClient.PlaylistRefresh refresh;
                try {
                    FnosRestClient rest = newRestClient();
                    refresh = rest.refreshTranscodePlaylist(session);
                } catch (FnosRpcException ex) {
                    Logger.w("Transcode seek refresh failed: " + ex.getMessage());
                    runOnUiThread(fail);
                    return;
                } catch (RuntimeException ex) {
                    Logger.w("Transcode seek refresh crashed: " + ex.getMessage());
                    runOnUiThread(fail);
                    return;
                }
                if (refresh.url.length() == 0) {
                    runOnUiThread(fail);
                    return;
                }
                // 目标离服务器已转码的位置太远：不跳（否则会落到片头附近，看起来像归零）
                if (refresh.headMs > 0 && targetMs > refresh.headMs + 30000) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            nativeVideoPlayerView.onTranscodeSeekUnavailable();
                        }
                    });
                    return;
                }
                final boolean clamped = refresh.headMs > 0 && targetMs > refresh.headMs - 2000;
                final int target = clamped ? Math.max(0, refresh.headMs - 2000) : targetMs;
                final String label = session.resolution.length() > 0
                        ? session.resolution + "P 转码" : "转码";
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        nativeVideoPlayerView.restartTranscoded(refresh.url, label, target, clamped);
                    }
                });
            }
        }, "fnos-transcode-seek").start();
    }

    /** 防止同一次播放结束被回调两次（会跳过一集/行为异常）。 */
    private String lastFinishedPath = "";
    private long lastFinishedAtMs;

    @Override
    public void onPlaylistItemSelected(FnosFileEntry entry) {
        if (entry == null) {
            return;
        }
        resolveAndPlaySources(entry);
    }

    @Override
    public void onPlaybackFinished(FnosFileEntry entry) {
        long now = System.currentTimeMillis();
        if (entry != null && entry.path.equals(lastFinishedPath) && now - lastFinishedAtMs < 5000) {
            return;
        }
        lastFinishedPath = entry == null ? "" : entry.path;
        lastFinishedAtMs = now;
        FnosFileEntry next = nextPlaybackEntry(entry);
        Logger.w("auto next: " + (entry == null ? "null" : entry.name)
                + " -> " + (next == null ? "end" : next.name));
        if (next != null) {
            nativeVideoPlayerView.showHintPublic("正在播放下一集：" + next.name);
            resolveAndPlaySources(next);
            return;
        }
        nativeVideoPlayerView.hide();
        stopTranscodeKeepAlive();
        if (nativeSeriesView != null && nativeSeriesView.isVisible()) {
            nativeSeriesView.refocus();
        }
    }

    private void setPlaybackQueue(List<FnosFileEntry> entries, int kind) {
        playbackQueue.clear();
        if (entries != null) {
            playbackQueue.addAll(entries);
        }
        playbackQueueKind = kind;
    }

    private int queueKindForFileList(List<FnosFileEntry> entries) {
        if (entries == null || entries.size() < 2) {
            return QUEUE_NONE;
        }
        int videos = 0;
        for (int i = 0; i < entries.size(); i++) {
            FnosFileEntry entry = entries.get(i);
            if (entry != null && entry.isVideo() && !entry.directory) {
                videos++;
            }
        }
        return videos >= 2 ? QUEUE_DIRECTORY : QUEUE_NONE;
    }

    private int queueKindForMediaCenter(String currentPath, List<FnosFileEntry> entries) {
        if (currentPath == null) {
            return QUEUE_NONE;
        }
        // 只有季页（tv:<guid>|season:<guid>）才是可连播的剧集列表；
        // 剧页展示的是季卡片，没有可播放内容。
        if (currentPath.startsWith(MEDIA_NODE_TV) && currentPath.indexOf("|" + MEDIA_NODE_SEASON) >= 0) {
            if (countPlayableVideos(entries) >= 1) {
                return QUEUE_EPISODES;
            }
            return QUEUE_NONE;
        }
        return QUEUE_NONE;
    }

    private int countPlayableVideos(List<FnosFileEntry> entries) {
        int count = 0;
        if (entries == null) {
            return 0;
        }
        for (int i = 0; i < entries.size(); i++) {
            FnosFileEntry entry = entries.get(i);
            if (entry != null && entry.isVideo() && !entry.directory) {
                count++;
            }
        }
        return count;
    }

    private FnosFileEntry nextPlaybackEntry(FnosFileEntry entry) {
        if (entry == null || !AppPrefs.bool(this, AppPrefs.KEY_AUTO_NEXT, true)) {
            return null;
        }
        // 优先用连播队列；队列为空时退回当前季的完整列表（例如从「继续观看」直接进播放）
        List<FnosFileEntry> queue = playbackQueue.size() > 0 ? playbackQueue : currentSeasonEpisodes;
        if (queue.isEmpty()) {
            return null;
        }
        int index = indexOfEntry(queue, entry);
        if (index >= 0) {
            for (int i = index + 1; i < queue.size(); i++) {
                FnosFileEntry candidate = queue.get(i);
                if (isPlayableEntry(candidate) && !sameEntry(candidate, entry)) {
                    return candidate;
                }
            }
        }
        // 队列里找不到（或已经是最后一集）：按集号找「比当前大的最小集」
        int current = normalizedEpisode(entry);
        if (current > 0) {
            FnosFileEntry best = null;
            for (int i = 0; i < currentSeasonEpisodes.size(); i++) {
                FnosFileEntry candidate = currentSeasonEpisodes.get(i);
                if (!isPlayableEntry(candidate) || sameEntry(candidate, entry)) {
                    continue;
                }
                int number = normalizedEpisode(candidate);
                if (number > current && (best == null || number < normalizedEpisode(best))) {
                    best = candidate;
                }
            }
            return best;
        }
        return null;
    }

    private int indexOfEntry(List<FnosFileEntry> queue, FnosFileEntry entry) {
        for (int i = 0; i < queue.size(); i++) {
            if (sameEntry(queue.get(i), entry)) {
                return i;
            }
        }
        return -1;
    }

    /** 只按 path（guid）判断是不是同一集；name 在多集里可能重复，不能用来匹配。 */
    private static boolean sameEntry(FnosFileEntry left, FnosFileEntry right) {
        if (left == null || right == null) {
            return false;
        }
        if (left.path.length() > 0 && right.path.length() > 0) {
            return left.path.equals(right.path);
        }
        return left == right;
    }

    private static boolean isPlayableEntry(FnosFileEntry entry) {
        return entry != null && !entry.directory && entry.isVideo();
    }

    private static int normalizedEpisode(FnosFileEntry entry) {
        return entry == null ? -1 : entry.episodeNumber();
    }

    @Override
    public boolean goBack() {
        if (isDiagnosticsVisible()) {
            hideServerDiagnostics();
            return true;
        }
        // 全屏提示/加载层：返回键先把它关掉，避免「提示卡住 + 返回直接退出」的情况
        if (statusOverlay != null && statusOverlay.isVisible()) {
            statusOverlay.hide();
            return true;
        }
        // 播放器在最上层，先关播放器（回到剧集页），再退剧集页
        if (nativeVideoPlayerView != null && nativeVideoPlayerView.isVisible()) {
            nativeVideoPlayerView.hide();
            if (nativeLiveView != null && nativeLiveView.isVisible()) {
                // 直播：返回后回到频道列表
                return true;
            }
            if (nativeSeriesView != null && nativeSeriesView.isVisible()) {
                nativeSeriesView.refocus();
            }
            return true;
        }
        if (nativeLiveView != null && nativeLiveView.isVisible()) {
            nativeLiveView.hide();
            nativeVideoPlayerView.setMenuMode(false);
            nativeVideoPlayerView.hide();
            showNativeHome();
            return true;
        }
        if (nativeSeriesView != null && nativeSeriesView.isVisible()) {
            nativeSeriesView.hide();
            return restoreNavFrame();
        }
        BackNavigationState state = BackNavigationState.choose(
                nativeVideoPlayerView.isVisible(),
                mediaDetailView.isVisible(),
                fileBrowserView.isVisible(),
                nativeHomeView.isVisible());
        if (state == BackNavigationState.PLAYER) {
            nativeVideoPlayerView.hide();
            return true;
        }
        if (state == BackNavigationState.DETAIL) {
            closeMediaDetail();
            return true;
        }
        if (state == BackNavigationState.BROWSER) {
            // 网页端等价：路由回退（/library/:guid → /list/all → /）。
            // 绝不回退到真实文件目录树——所有列表/详情类路由都走导航栈。
            if (browserMode == BROWSER_MODE_MEDIA
                    || browserMode == BROWSER_MODE_CATEGORY
                    || browserMode == BROWSER_MODE_FAVORITES
                    || browserMode == BROWSER_MODE_RECENT
                    || browserMode == BROWSER_MODE_SEARCH
                    || browserMode == BROWSER_MODE_PERSON) {
                return restoreNavFrame();
            }
            // 真实文件浏览（网页端没有该路由）只允许回到首页。
            showNativeHome();
            return true;
        }
        return false;
    }

    @Override
    public boolean togglePlayback() {
        return nativeVideoPlayerView.toggle();
    }

    private void showStatus(String message) {
        statusOverlay.show(message);
    }

    /** 连接服务器失败：提示并回到设置页重新填写地址/账号。 */
    private void handleServerConnectionFailure(String message) {
        Logger.w(message);
        showStatus(message + "\n正在返回登录设置页");
        openSettings(message);
    }

    private void openFileBrowser(final String path) {
        browserMode = BROWSER_MODE_FILES;
        currentFilePath = path == null ? "" : path;
        nativeHomeView.hide();
        mediaDetailView.hide();
        showStatus("正在加载目录...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final FileLoadResult result = loadFileList(currentFilePath);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result.success) {
                            statusOverlay.hide();
                            fileBrowserView.show(result.list.path, result.list.entries);
                            setPlaybackQueue(result.list.entries, queueKindForFileList(result.list.entries));
                            setPlaybackQueue(result.list.entries, queueKindForFileList(result.list.entries));
                        } else {
                            showStatus(result.message);
                            nativeHomeView.show();
                        }
                    }
                });
            }
        }, "fnos-file-list").start();
    }

    private void openRecentPlayback() {
        openRecentPlayback(true);
    }

    private void openRecentPlayback(final boolean applyFilter) {
        if (applyFilter) {
            listTypeFilter = "all";
        }
        browserMode = BROWSER_MODE_RECENT;
        currentFilePath = "";
        currentCategory = "";
        fileBrowserView.setMode(NativeFileBrowserView.MODE_NORMAL);
        currentScreenTitle = "继续观看";
        nativeHomeView.hide();
        mediaDetailView.hide();
        showStatus("正在加载继续观看...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MediaLoadResult result = loadRecentPlayback();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusOverlay.hide();
                        showBrowserList(result);
                        setPlaybackQueue(result.list.entries, QUEUE_NONE);
                    }
                });
            }
        }, "fnos-rest-recent").start();
    }

    private void openFavorites() {
        openFavorites(true);
    }

    private void openFavorites(final boolean applyFilter) {
        if (applyFilter) {
            listTypeFilter = "all";
        }
        setActiveNav(NativeHomeView.ACTION_FAVORITES);
        browserMode = BROWSER_MODE_FAVORITES;
        currentFilePath = "";
        currentCategory = "";
        fileBrowserView.setMode(NativeFileBrowserView.MODE_FAVORITES);
        currentScreenTitle = "收藏";
        nativeHomeView.hide();
        mediaDetailView.hide();
        showStatus("正在加载收藏...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MediaLoadResult result = loadFavorites();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusOverlay.hide();
                        showBrowserList(result);
                        setPlaybackQueue(result.list.entries, QUEUE_NONE);
                    }
                });
            }
        }, "fnos-rest-favorites").start();
    }

    private void openCategory(final String title, final String category) {
        openCategory(title, category, true);
    }

    private void openCategory(final String title, final String category, final boolean applyFilter) {
        if (applyFilter) {
            applyCategoryFilter(category);
        }
        if (isNavCategory(category)) {
            setActiveNav(category);
        }
        if (NativeHomeView.ACTION_LIVE.equals(category)) {
            String iptv = AppPrefs.string(this, AppPrefs.KEY_IPTV_URL, "").trim();
            if (iptv.length() > 0) {
                openIptvLive(title, iptv);
                return;
            }
        }
        browserMode = BROWSER_MODE_CATEGORY;
        currentFilePath = "";
        currentCategory = category == null ? "" : category;
        fileBrowserView.setMode(NativeFileBrowserView.MODE_NORMAL);
        // 分类页用海报网格（网页端 /list/* 默认布局）；文件夹页才用列表。
        fileBrowserView.applyPageChrome(false);
        currentScreenTitle = title == null ? "" : title;
        nativeHomeView.hide();
        mediaDetailView.hide();
        showStatus("正在加载" + title + "...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MediaLoadResult result = loadCategory(title, category);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusOverlay.hide();
                        showBrowserList(result);
                        setPlaybackQueue(result.list.entries, QUEUE_NONE);
                    }
                });
            }
        }, "fnos-rest-category").start();
    }

    private void openSearchDialog() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint("输入片名或路径关键词");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF8C96A3);
        input.setSelectAllOnFocus(true);
        new AlertDialog.Builder(this)
                .setTitle("搜索")
                .setView(input)
                .setPositiveButton("搜索", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        openSearchResults(input.getText().toString());
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openSearchResults(final String keyword) {
        openSearchResults(keyword, true);
    }

    private void openSearchResults(final String keyword, final boolean applyFilter) {
        if (applyFilter) {
            listTypeFilter = "all";
        }
        browserMode = BROWSER_MODE_SEARCH;
        currentFilePath = "";
        currentCategory = "";
        currentSearchKeyword = keyword == null ? "" : keyword;
        currentScreenTitle = "搜索";
        nativeHomeView.hide();
        mediaDetailView.hide();
        fileBrowserView.setMode(NativeFileBrowserView.MODE_NORMAL);
        showStatus("正在搜索...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MediaLoadResult result = loadSearch(currentSearchKeyword);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusOverlay.hide();
                        showBrowserList(result);
                        setPlaybackQueue(result.list.entries, QUEUE_NONE);
                    }
                });
            }
        }, "fnos-rest-search").start();
    }

    /** 网页端搜索：GET /search/list?q=（服务端返回裸数组），失败时回退到本地记录。 */
    private MediaLoadResult loadSearch(String keyword) {
        try {
            FnosFileList list = newRestClient().searchItems(keyword);
            return MediaLoadResult.success("搜索",
                    list.entries.size() == 0 ? "无搜索结果" : "关键词：" + keyword,
                    list, false);
        } catch (FnosRpcException ex) {
            Logger.w("REST search failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("REST search crashed: " + ex.getMessage());
        }
        List<FnosFileEntry> entries = filterSearch(knownMediaEntries(), keyword);
        return MediaLoadResult.success("搜索", "关键词：" + keyword, new FnosFileList("search", entries), false);
    }

    private void openMediaCenter(final String path) {
        openMediaCenter(path, true);
    }

    private void openMediaCenter(final String path, final boolean applyFilter) {
        if (applyFilter) {
            listTypeFilter = "all";
        }
        browserMode = BROWSER_MODE_MEDIA;
        currentFilePath = path == null ? "" : path;
        currentCategory = "";
        String navAction = navActionForPath(currentFilePath);
        if (navAction.length() > 0) {
            setActiveNav(navAction);
        }
        fileBrowserView.setMode(NativeFileBrowserView.MODE_NORMAL);
        // 网页端文件夹页（/folder/:guid）默认列表布局：里面是长文件名列表。
        fileBrowserView.applyPageChrome(currentFilePath.startsWith(MediaCenterGateway.NODE_FOLDER));
        String nodeTitle = mediaNodeTitles.get(currentFilePath);
        if (nodeTitle != null && nodeTitle.length() > 0) {
            pendingLibraryTitle = nodeTitle;
        }
        currentScreenTitle = nodeTitle != null && nodeTitle.length() > 0
                ? nodeTitle
                : (currentFilePath.length() == 0 ? "全部" : "媒体库");
        nativeHomeView.hide();
        mediaDetailView.hide();
        showStatus("正在加载影视入口...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final MediaLoadResult result = loadMediaCenter(currentFilePath);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result.success) {
                            statusOverlay.hide();
                            String title = result.title;
                            if (pendingLibraryTitle.length() > 0) {
                                title = pendingLibraryTitle;
                                pendingLibraryTitle = "";
                            }
                            showBrowserList(title, result);
                            setPlaybackQueue(result.list.entries, queueKindForMediaCenter(currentFilePath, result.list.entries));
                        } else {
                            pendingLibraryTitle = "";
                            showStatus(result.message);
                            nativeHomeView.show();
                        }
                    }
                });
            }
        }, "fnos-media-center").start();
    }

    private MediaLoadResult loadMediaCenter(String path) {
        String value = path == null ? "" : path;
        if (value.startsWith(MEDIA_NODE_TV)) {
            MediaLoadResult series = loadSeriesNode(value);
            if (series != null) {
                return series;
            }
        }
        MediaCenterLoad result = newMediaCenterGateway().load(path);
        if (result.success) {
            return MediaLoadResult.success(result.title, result.subtitle, result.list, result.sortEntries);
        }
        Logger.w("Media center load failed: " + result.message);
        return MediaLoadResult.failure(result.message);
    }

    /**
     * 剧 / 季页面节点（对应网页端 /tv/:guid 与 /tv/season/:guid）。
     *
     * <p>季页：hero 头部 + 剧集卡片；剧页：hero 头部 + 季卡片网格。
     * 头部数据来自季条目详情（官方 v1 没有 /tv/:guid 接口）。
     */
    private MediaLoadResult loadSeriesNode(String path) {
        String seasonGuid = seasonGuidOf(path);
        String tvGuid = tvGuidOf(path);
        try {
            if (seasonGuid.length() > 0) {
                FnosRestClient client = newRestClient();
                FnosFileList episodeList = client.episodeItems(seasonGuid);
                List<FnosFileEntry> episodes = sortedByEpisode(episodeList.entries);
                MediaSeriesHeader header = client.seriesHeader(seasonGuid);
                List<FnosFileEntry> seasons = new ArrayList<FnosFileEntry>();
                if (tvGuid.length() > 0) {
                    seasons = loadSeriesSeasons(client, tvGuid);
                }
                // 从「继续观看」进来时 ancestor_guid 可能是媒体库 guid，改用季条目的 parent_guid 重试
                if (seasons.isEmpty() && header.tvGuid.length() > 0 && !header.tvGuid.equals(tvGuid)) {
                    seasons = loadSeriesSeasons(client, header.tvGuid);
                }
                if (header.seasonCount <= 0) {
                    header = new MediaSeriesHeader(header.title, header.seasonTitle, header.overview,
                            header.posterPath, header.year, header.score,
                            seasons.size(), header.episodeCount, header.type);
                }
                return MediaLoadResult.series(
                        header.title.length() > 0 ? header.title : "剧集",
                        episodes.size() == 0 ? "暂无剧集" : "",
                        new FnosFileList("episode", episodes),
                        header, seasons, seasonGuid, true);
            }
            if (tvGuid.length() == 0) {
                return null;
            }
            FnosRestClient client = newRestClient();
            String showGuid = tvGuid;
            List<FnosFileEntry> seasons = loadSeriesSeasons(client, showGuid);
            // 从「继续观看」进来时 ancestor_guid 是媒体库/剧的混合字段，拿季列表会 403；
            // 用当前集的 parent_guid 问一下季详情，里面才有真正的剧 guid。
            if (seasons.isEmpty() && pendingResumeEntry != null
                    && pendingResumeEntry.parentGuid != null && pendingResumeEntry.parentGuid.length() > 0) {
                String resolvedTvGuid = "";
                try {
                    resolvedTvGuid = client.seriesHeader(pendingResumeEntry.parentGuid).tvGuid;
                } catch (FnosRpcException ex) {
                    Logger.w("Show tv guid resolve failed: " + ex.getMessage());
                } catch (RuntimeException ex) {
                    Logger.w("Show tv guid resolve crashed: " + ex.getMessage());
                }
                if (resolvedTvGuid.length() > 0) {
                    showGuid = resolvedTvGuid;
                    seasons = loadSeriesSeasons(client, showGuid);
                }
            }

            // 单季剧（且是继续观看进来的）：没有「多季」可言，直接落到季页并高亮当前集
            if (seasons.size() == 1 && pendingEpisodeGuid.length() > 0) {
                FnosFileEntry only = seasons.get(0);
                FnosFileList episodeList = client.episodeItems(only.path);
                List<FnosFileEntry> episodes = sortedByEpisode(episodeList.entries);
                MediaSeriesHeader episodeHeader = client.seriesHeader(only.path);
                if (episodeHeader.seasonCount <= 0) {
                    episodeHeader = new MediaSeriesHeader(episodeHeader.title, episodeHeader.seasonTitle,
                            episodeHeader.overview, episodeHeader.posterPath, episodeHeader.year,
                            episodeHeader.score, 1, episodes.size(), episodeHeader.type);
                }
                return MediaLoadResult.series(
                        episodeHeader.title.length() > 0 ? episodeHeader.title : "剧集",
                        episodes.size() == 0 ? "暂无剧集" : "",
                        new FnosFileList("episode", episodes),
                        episodeHeader, seasons, only.path, true);
            }

            // 剧页：TV 条目本身带 backdrops / logos（网页就是用它当背景和标题 logo）
            MediaSeriesHeader header = MediaSeriesHeader.empty();
            if (showGuid.length() > 0) {
                try {
                    header = client.seriesHeader(showGuid);
                } catch (FnosRpcException ex) {
                    Logger.w("Show header failed: " + ex.getMessage());
                } catch (RuntimeException ex) {
                    Logger.w("Show header crashed: " + ex.getMessage());
                }
            }
            if (header.posterPath.length() == 0 && seasons.size() > 0) {
                try {
                    header = client.seriesHeader(seasons.get(0).path);
                } catch (FnosRpcException ex) {
                    Logger.w("Season header failed: " + ex.getMessage());
                } catch (RuntimeException ex) {
                    Logger.w("Season header crashed: " + ex.getMessage());
                }
            }
            MediaSeriesHeader showHeader = new MediaSeriesHeader(header.title, "", header.overview,
                    header.posterPath, header.year, header.score, seasons.size(), 0, "TV",
                    showGuid, header.backdropPath, header.logoPath);
            return MediaLoadResult.series(
                    showHeader.title.length() > 0 ? showHeader.title : "电视剧",
                    seasons.size() == 0 ? "暂无季" : "",
                    new FnosFileList("season", seasons),
                    showHeader, seasons, "", false);
        } catch (FnosRpcException ex) {
            Logger.w("Series node load failed: " + ex.getMessage());
            return MediaLoadResult.failure("剧集加载失败：" + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("Series node load crashed: " + ex.getMessage());
            return MediaLoadResult.failure("剧集加载失败：" + ex.getMessage());
        }
    }

    /** 取某部剧的季列表；失败返回空列表（页面仍可显示季页）。 */
    private List<FnosFileEntry> loadSeriesSeasons(FnosRestClient client, String tvGuid) {
        try {
            return sortedBySeason(client.seasonItems(tvGuid).entries);
        } catch (FnosRpcException ex) {
            Logger.w("Season list failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("Season list crashed: " + ex.getMessage());
        }
        return new ArrayList<FnosFileEntry>();
    }

    /** 剧集按集号升序，未知集（-1）排最后。 */
    private static List<FnosFileEntry> sortedByEpisode(List<FnosFileEntry> entries) {
        List<FnosFileEntry> values = entries == null
                ? new ArrayList<FnosFileEntry>() : new ArrayList<FnosFileEntry>(entries);
        Collections.sort(values, new Comparator<FnosFileEntry>() {
            @Override
            public int compare(FnosFileEntry left, FnosFileEntry right) {
                int leftEpisode = left == null ? Integer.MAX_VALUE : normalized(left.episodeNumber());
                int rightEpisode = right == null ? Integer.MAX_VALUE : normalized(right.episodeNumber());
                if (leftEpisode != rightEpisode) {
                    return leftEpisode < rightEpisode ? -1 : 1;
                }
                String leftName = left == null ? "" : left.name;
                String rightName = right == null ? "" : right.name;
                return leftName.compareToIgnoreCase(rightName);
            }
        });
        return values;
    }

    /** 季按季号升序，未知季（-1）排最后。 */
    private static List<FnosFileEntry> sortedBySeason(List<FnosFileEntry> entries) {
        List<FnosFileEntry> values = entries == null
                ? new ArrayList<FnosFileEntry>() : new ArrayList<FnosFileEntry>(entries);
        Collections.sort(values, new Comparator<FnosFileEntry>() {
            @Override
            public int compare(FnosFileEntry left, FnosFileEntry right) {
                int leftSeason = left == null ? Integer.MAX_VALUE : normalized(left.seasonNumber());
                int rightSeason = right == null ? Integer.MAX_VALUE : normalized(right.seasonNumber());
                if (leftSeason != rightSeason) {
                    return leftSeason < rightSeason ? -1 : 1;
                }
                String leftName = left == null ? "" : left.name;
                String rightName = right == null ? "" : right.name;
                return leftName.compareToIgnoreCase(rightName);
            }
        });
        return values;
    }

    /** -1（未知）视作最大，排在最后。 */
    private static int normalized(int number) {
        return number < 0 ? Integer.MAX_VALUE : number;
    }

    private MediaCenterGateway newMediaCenterGateway() {
        // 只走影视 REST（网页同款）：媒体库列表 + item/list 条目。
        // 刻意不提供真实文件系统回退，避免「返回」退到 /volX/... 目录树。
        return new MediaCenterGateway(
                new MediaCenterGateway.RestProvider() {
                    @Override
                    public FnosFileList libraries() throws FnosRpcException {
                        return newRestClient().mediaLibraries();
                    }

                    @Override
                    public FnosFileList items(String path, String category, int pageSize) throws FnosRpcException {
                        return newRestClient().mediaItemsAll(path == null ? "" : path,
                                effectiveCategory(category), pageSize, listSortColumn, listSortType);
                    }

                    @Override
                    public FnosFileList folderItems(String folderGuid, int pageSize) throws FnosRpcException {
                        return newRestClient().folderItems(folderGuid, pageSize,
                                effectiveCategory("all"), folderSortColumn, folderSortType);
                    }
                });
    }

    private MediaLoadResult loadCategory(String title, String category) {
        try {
            FnosFileList list = newRestClient().mediaItemsAll("", effectiveCategory(category), 50,
                    listSortColumn, listSortType);
            return MediaLoadResult.success(title, list.entries.size() == 0 ? "暂无内容" : "fnOS 影视服务", list, false);
        } catch (FnosRpcException ex) {
            Logger.w("REST category failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("REST category crashed: " + ex.getMessage());
        }
        List<FnosFileEntry> entries = filterCategory(knownMediaEntries(), category);
        return MediaLoadResult.success(title, entries.size() == 0 ? "暂无内容，先从影视大全或文件库播放/收藏媒体" : "来自最近播放和收藏", new FnosFileList(category, entries), false);
    }

    /**
     * 电视直播页：三栏布局（频道分类 / 频道列表 / 频道信息），数据来自 IPTV 播放列表。
     */
    private void openIptvLive(final String title, final String url) {
        browserMode = BROWSER_MODE_CATEGORY;
        currentCategory = NativeHomeView.ACTION_LIVE;
        currentFilePath = "";
        nativeHomeView.hide();
        fileBrowserView.hide();
        mediaDetailView.hide();
        if (nativeSeriesView != null) {
            nativeSeriesView.hide();
        }
        showStatus("正在加载直播源…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<IptvPlaylist.Channel> list = fetchIptvChannels(url);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        statusOverlay.hide();
                        if (list.isEmpty()) {
                            showStatus("直播源里没有可用频道\n请检查播单地址或文件内容");
                            return;
                        }
                        nativeLiveView.setChannels(list);
                        nativeLiveView.show();
                        nativeVideoPlayerView.setMenuMode(true);
                        // 菜单后面先播第一台，画面立刻出来
                        nativeLiveView.playFirstChannel();
                        currentScreenTitle = title == null ? "电视直播" : title;
                    }
                });
            }
        }, "fnos-iptv").start();
    }

    /** 读直播源：支持 http(s) 与本地文件（file:// 或 /sdcard/...）。 */
    private List<IptvPlaylist.Channel> fetchIptvChannels(String url) {
        try {
            String body;
            if (url.startsWith("file://") || url.startsWith("/")) {
                String path = url.startsWith("file://") ? url.substring("file://".length()) : url;
                java.io.FileInputStream in = new java.io.FileInputStream(path);
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) > 0) {
                    out.write(buffer, 0, read);
                }
                in.close();
                body = new String(out.toByteArray(), "UTF-8");
            } else {
                okhttp3.OkHttpClient client = HttpClients.builder(true).build();
                okhttp3.Request request = new okhttp3.Request.Builder().url(url).build();
                okhttp3.Response response = client.newCall(request).execute();
                body = response.body() == null ? "" : response.body().string();
                response.close();
            }
            return IptvPlaylist.parse(body);
        } catch (Throwable ex) {
            Logger.w("IPTV playlist failed: " + ex.getMessage());
            return new ArrayList<IptvPlaylist.Channel>();
        }
    }

    /** 直播页里点某个频道：不经过详情页，直接起播。 */
    private void playLiveChannel(final IptvPlaylist.Channel channel) {
        if (channel == null) {
            return;
        }
        // 服务器媒体库的直播条目：先 /play/info 解析出真实地址
        if (channel.guid != null && channel.guid.length() > 0) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    String resolved = "";
                    try {
                        FnosRestClient.PlaybackDirect direct =
                                newRestClient().playbackDirectForItem(channel.guid);
                        resolved = direct == null ? "" : direct.url;
                    } catch (Throwable ex) {
                        Logger.w("Live channel resolve failed: " + ex.getMessage());
                    }
                    final String url = resolved;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (url.length() == 0) {
                                if (nativeLiveView != null) {
                                    nativeLiveView.showToast("该频道暂时无法播放");
                                }
                                return;
                            }
                            playLiveUrl(channel.name, url);
                        }
                    });
                }
            }, "fnos-live-resolve").start();
            return;
        }
        playLiveUrl(channel.name, channel.url);
    }

    private void playLiveUrl(String name, String url) {
        if (url == null || url.length() == 0) {
            return;
        }
        if (url.equals(currentLiveUrl) && nativeVideoPlayerView.isVisible()) {
            // 重复点同一个台：不重新起流
            return;
        }
        currentLiveUrl = url;
        FnosFileEntry entry = new FnosFileEntry(name, url, false, 0L, "LiveChannel", url);
        List<FnosPlaybackSource> sources = new ArrayList<FnosPlaybackSource>();
        sources.add(new FnosPlaybackSource("直播", url));
        recentPlaybackStore.remember(entry);
        nativeVideoPlayerView.setPlaylist(new ArrayList<FnosFileEntry>(), entry.path, "", "");
        nativeVideoPlayerView.play(entry, sources, 0L, false);
        // 播放器起播会 requestFocus，把焦点抢回去；菜单需要重新拿回焦点继续选台
        if (nativeLiveView != null) {
            nativeLiveView.postRefocus();
        }
    }

    private MediaLoadResult loadFavorites() {        List<FnosFileEntry> entries = new ArrayList<FnosFileEntry>();
        String label = "我的收藏";
        try {
            FnosFileList list = newRestClient().favoriteItems(favoriteTabType, listSortColumn, listSortType);
            serverFavorites = list.entries;
            appendFavorites(entries, list.entries);
        } catch (FnosRpcException ex) {
            Logger.w("REST favorites failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("REST favorites crashed: " + ex.getMessage());
        }
        // 本机收藏（在文件列表/详情页收藏的视频）与服务器收藏合并展示，
        // 否则刚收藏的条目会因为服务器端还没有记录而“消失”。
        // 选了具体分类标签时只展示服务端分类结果（本地收藏没有分类元数据）。
        if (favoriteTabType == null || favoriteTabType.length() == 0) {
            appendFavorites(entries, favoriteStore.list());
        }
        return MediaLoadResult.success("收藏",
                entries.size() == 0 ? "暂无收藏，文件列表中按右键可收藏" : label,
                new FnosFileList("favorite", entries), false);
    }

    /** 服务器收藏 + 本机收藏合并（按路径去重，服务器条目优先）。 */
    private List<FnosFileEntry> mergedFavorites() {
        List<FnosFileEntry> merged = new ArrayList<FnosFileEntry>();
        appendFavorites(merged, serverFavorites);
        appendFavorites(merged, favoriteStore.list());
        return merged;
    }

    private void appendFavorites(List<FnosFileEntry> target, List<FnosFileEntry> source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            FnosFileEntry entry = source.get(i);
            if (entry == null) {
                continue;
            }
            String key = favoriteKey(entry);
            boolean exists = false;
            for (int j = 0; j < target.size(); j++) {
                if (favoriteKey(target.get(j)).equals(key)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                target.add(entry);
            }
        }
    }

    private String favoriteKey(FnosFileEntry entry) {
        if (entry.path != null && entry.path.length() > 0) {
            return entry.path;
        }
        return entry.mediaUrl == null ? entry.name : entry.mediaUrl;
    }

    private MediaLoadResult loadRecentPlayback() {
        try {
            FnosFileList list = newRestClient().recentItems();
            return MediaLoadResult.success("继续观看", list.entries.size() == 0 ? "暂无播放记录" : "fnOS 继续观看", list, false);
        } catch (FnosRpcException ex) {
            Logger.w("REST recent failed: " + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("REST recent crashed: " + ex.getMessage());
        }
        List<FnosFileEntry> entries = recentPlaybackStore.list();
        return MediaLoadResult.success("最近播放", entries.size() == 0 ? "暂无播放记录" : "本机播放记录", new FnosFileList("recent", entries), false);
    }

    private FileLoadResult loadFileList(String path) {
        try {
            FnosSession session = sessionStore.load();
            if (!session.hasToken()) {
                return FileLoadResult.failure("登录会话已失效");
            }
            FnosRpcClient client = new FnosRpcClient(profile, sessionStore.getOrCreateDeviceId());
            return FileLoadResult.success(client.listDir(session, path));
        } catch (FnosRpcException ex) {
            Logger.w("Native file list failed: " + ex.getMessage());
            return FileLoadResult.failure("文件库加载失败：" + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("Native file list crashed: " + ex.getMessage());
            return FileLoadResult.failure("文件库加载异常：" + ex.getMessage());
        }
    }

    private FnosRestClient newRestClient() throws FnosRpcException {
        if (profile == null || !profile.isReady()) {
            throw new FnosRpcException("服务配置不可用");
        }
        // 影视 REST 客户端共享一个实例（内部缓存 token 与类型表）：
        // 每次请求都新建会并发触发多次登录，服务端会以 code=-15 Password Incorrect 拒绝。
        synchronized (restClientLock) {
            if (restClient == null || !sameServer(restClientProfile, profile)) {
                restClient = new FnosRestClient(profile, getCacheDir());
                restClientProfile = profile;
            }
            return restClient;
        }
    }

    private boolean sameServer(ServerProfile left, ServerProfile right) {
        if (left == null || right == null) {
            return false;
        }
        return left.baseUrl.equals(right.baseUrl)
                && left.username.equals(right.username)
                && left.password.equals(right.password);
    }

    private void updateHomeCounts() {
        // 媒体库数量以服务端 mediadb/list 为准（homeLibraries 由 REST 刷新），
        // 不能用本地种子列表，否则服务端增删媒体库不会反映到界面。
        int libraryCount = homeLibraries == null ? 0 : homeLibraries.size();
        List<FnosFileEntry> known = knownMediaEntries();
        List<FnosFileEntry> favorites = mergedFavorites();
        List<FnosFileEntry> recent = recentPlaybackStore.list();
        // 首页卡片全部来自服务端：这里只预留计数，不再渲染本地兜底卡片。
        nativeHomeView.updateCounts(
                favorites.size(),
                libraryCount,
                known.size(),
                filterCategory(known, NativeHomeView.ACTION_MOVIES).size(),
                filterCategory(known, NativeHomeView.ACTION_TV).size(),
                filterCategory(known, NativeHomeView.ACTION_OTHER).size(),
                recent.size());
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FnosRestClient client = newRestClient();
                    // 各接口独立容错：不同 fnOS 版本有的接口可能 404/501，
                    // 不能让一个接口失败就把整个首页/媒体库变成空。
                    FnosMediaCounts counts = new FnosMediaCounts(0, 0, 0, 0, 0, 0);
                    try {
                        counts = client.mediaCounts();
                    } catch (FnosRpcException ex) {
                        Logger.w("media counts failed: " + ex.getMessage());
                    } catch (RuntimeException ex) {
                        Logger.w("media counts crashed: " + ex.getMessage());
                    }
                    FnosFileList libraries = new FnosFileList("mediaLibraries", new ArrayList<FnosFileEntry>());
                    try {
                        libraries = client.mediaLibraries();
                    } catch (FnosRpcException ex) {
                        Logger.w("media libraries failed: " + ex.getMessage());
                    } catch (RuntimeException ex) {
                        Logger.w("media libraries crashed: " + ex.getMessage());
                    }
                    FnosFileList recentList = new FnosFileList("recent", new ArrayList<FnosFileEntry>());
                    try {
                        recentList = client.recentItems();
                    } catch (FnosRpcException ex) {
                        Logger.w("recent items failed: " + ex.getMessage());
                    } catch (RuntimeException ex) {
                        Logger.w("recent items crashed: " + ex.getMessage());
                    }
                    FnosFileList favoriteList = new FnosFileList("favorite", new ArrayList<FnosFileEntry>());
                    try {
                        favoriteList = client.favoriteItems();
                    } catch (FnosRpcException ex) {
                        Logger.w("favorite items failed: " + ex.getMessage());
                    } catch (RuntimeException ex) {
                        Logger.w("favorite items crashed: " + ex.getMessage());
                    }
                    List<HomePosterSection> librarySections = new ArrayList<HomePosterSection>();
                    try {
                        librarySections = loadHomeLibrarySections(client, libraries.entries);
                    } catch (RuntimeException ex) {
                        Logger.w("home library sections crashed: " + ex.getMessage());
                    }
                    final FnosMediaCounts safeCounts = counts;
                    final FnosFileList safeLibraries = libraries;
                    final FnosFileList safeRecent = recentList;
                    final FnosFileList safeFavorites = favoriteList;
                    final List<HomePosterSection> safeSections = librarySections;
                    final String posterToken = client.authorizationToken();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            homeLibraries = safeLibraries.entries;
                            serverFavorites = safeFavorites.entries;
                            nativeHomeView.setPosterAuthorizationToken(posterToken);
                            posterAuthToken = posterToken;
                            if (nativeSeriesView != null) {
                                nativeSeriesView.setPosterBaseUrl(profile.baseUrl);
                                nativeSeriesView.setPosterAuthorizationToken(posterToken);
                            }
                            fileBrowserView.setPosterAuthorizationToken(posterToken);
                            mediaDetailView.setPosterAuthorizationToken(posterToken);
                            fileBrowserView.updateLibraries(safeLibraries.entries, safeCounts.libraryCounts);
                            nativeHomeView.updateLibraries(safeLibraries.entries, safeCounts.libraryCounts);
                            fileBrowserView.updateLiveCount(safeCounts.liveCount);
                            nativeHomeView.updateLiveCount(safeCounts.liveCount);
                            int favoriteTotal = mergedFavorites().size();
                            nativeHomeView.updateCounts(
                                    favoriteTotal,
                                    safeCounts.libraryCount,
                                    safeCounts.totalCount,
                                    safeCounts.movieCount,
                                    safeCounts.tvCount,
                                    safeCounts.otherCount,
                                    safeRecent.entries.size());
                            fileBrowserView.updateCounts(
                                    favoriteTotal,
                                    safeCounts.totalCount,
                                    safeCounts.movieCount,
                                    safeCounts.tvCount,
                                    safeCounts.otherCount);
                            nativeHomeView.updatePosterSections(HomePosterSections.forLibraries(
                                    safeRecent.entries, mergedFavorites(), safeSections));
                        }
                    });
                } catch (FnosRpcException ex) {
                    Logger.w("REST home counts failed: " + ex.getMessage());
                } catch (RuntimeException ex) {
                    Logger.w("REST home counts crashed: " + ex.getMessage());
                }
            }
        }, "fnos-rest-home-counts").start();
    }

    /** 每个媒体库取 12 条，构造与网页首页一致的按库分区。 */
    private List<HomePosterSection> loadHomeLibrarySections(FnosRestClient client, List<FnosFileEntry> libraries) {
        List<HomePosterSection> sections = new ArrayList<HomePosterSection>();
        if (libraries == null || libraries.size() == 0) {
            return sections;
        }
        for (int i = 0; i < libraries.size(); i++) {
            FnosFileEntry library = libraries.get(i);
            try {
                FnosFileList items = client.mediaItems(library.path, NativeHomeView.ACTION_ALL, 12);
                HomePosterSection section = HomePosterSections.forLibrary(library.name, library.path, items.entries);
                if (section.entries.size() > 0) {
                    sections.add(section);
                }
            } catch (FnosRpcException ex) {
                Logger.w("REST home library section failed: " + library.name + " " + ex.getMessage());
            } catch (RuntimeException ex) {
                Logger.w("REST home library section crashed: " + library.name + " " + ex.getMessage());
            }
        }
        return sections;
    }

    private List<FnosFileEntry> knownMediaEntries() {
        List<FnosFileEntry> values = new ArrayList<FnosFileEntry>();
        appendUnique(values, mediaIndexStore.list());
        appendUnique(values, recentPlaybackStore.list());
        appendUnique(values, favoriteStore.list());
        return values;
    }

    private void appendUnique(List<FnosFileEntry> target, List<FnosFileEntry> source) {
        if (source == null) {
            return;
        }
        for (int i = 0; i < source.size(); i++) {
            FnosFileEntry entry = source.get(i);
            if (entry != null && entry.isVideo() && !containsPath(target, entry.path)) {
                target.add(entry);
            }
        }
    }

    private boolean containsPath(List<FnosFileEntry> entries, String path) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).path.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private List<FnosFileEntry> filterCategory(List<FnosFileEntry> entries, String category) {
        List<FnosFileEntry> filtered = new ArrayList<FnosFileEntry>();
        for (int i = 0; i < entries.size(); i++) {
            FnosFileEntry entry = entries.get(i);
            if (NativeHomeView.ACTION_MOVIES.equals(category) && isMovie(entry)) {
                filtered.add(entry);
            } else if (NativeHomeView.ACTION_TV.equals(category) && isTv(entry)) {
                filtered.add(entry);
            } else if (NativeHomeView.ACTION_OTHER.equals(category) && !isMovie(entry) && !isTv(entry)) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private List<FnosFileEntry> filterSearch(List<FnosFileEntry> entries, String keyword) {
        String value = keyword == null ? "" : keyword.toLowerCase();
        List<FnosFileEntry> filtered = new ArrayList<FnosFileEntry>();
        for (int i = 0; i < entries.size(); i++) {
            FnosFileEntry entry = entries.get(i);
            if (value.length() == 0
                    || entry.name.toLowerCase().indexOf(value) >= 0
                    || entry.path.toLowerCase().indexOf(value) >= 0) {
                filtered.add(entry);
            }
        }
        return filtered;
    }

    private boolean isMovie(FnosFileEntry entry) {
        if (entry == null || !entry.isVideo()) {
            return false;
        }
        if (hasMediaType(entry, "movie")) {
            return true;
        }
        return MediaLibraryClassifier.inferCategory(entry.name, entry.path).equals("movie");
    }

    private boolean isTv(FnosFileEntry entry) {
        if (entry == null) {
            return false;
        }
        if (hasMediaType(entry, "tv") || hasMediaType(entry, "episode") || hasMediaType(entry, "season")) {
            return true;
        }
        return MediaLibraryClassifier.inferCategory(entry.name, entry.path).equals("tv");
    }

    /** 影视库条目的服务端类型（如 video/rest/Movie、TV）优先于路径启发式判断。 */
    private boolean hasMediaType(FnosFileEntry entry, String value) {
        String type = entry == null ? "" : entry.type.toLowerCase();
        return type.indexOf(value) >= 0;
    }

    private void openMediaDetail(final FnosFileEntry entry) {
        if (entry == null || entry.directory) {
            return;
        }
        if (entry.isLive()) {
            playLive(entry);
            return;
        }
        if (!entry.isVideo()) {
            showStatus("暂不支持打开该文件\n" + entry.name);
            return;
        }
        statusOverlay.hide();
        mediaDetailState = new MediaDetailState(entry, favoriteStore.isFavorite(entry));
        mediaDetailView.show(mediaDetailState, profile == null ? "" : profile.baseUrl);
        if (canResolveDetailInfo(entry)) {
            resolveDetailInfo(entry);
        }
        String url = entry.playbackUrl();
        if (url.length() > 0) {
            List<FnosPlaybackSource> direct = new ArrayList<FnosPlaybackSource>();
            direct.add(new FnosPlaybackSource("原画", url));
            mediaDetailState.setSources(direct);
            mediaDetailView.update(mediaDetailState);
            return;
        }
        resolveDetailSources(entry);
    }

    /** 电视直播：不经过详情页，直接用播放器拉起频道流（VLC 处理 m3u8/rtsp/udp）。 */
    private void playLive(FnosFileEntry entry) {
        String url = entry.playbackUrl();
        if (url == null || url.length() == 0) {
            showStatus("该频道没有可用的直播地址");
            return;
        }
        statusOverlay.hide();
        closeMediaDetail();
        List<FnosPlaybackSource> sources = new ArrayList<FnosPlaybackSource>();
        sources.add(new FnosPlaybackSource("直播", url));
        nativeVideoPlayerView.setPlaylist(new ArrayList<FnosFileEntry>(), entry.path, "", "");
        nativeVideoPlayerView.play(entry, sources, 0L, false);
    }

    private void closeMediaDetail() {
        if (mediaDetailView != null) {
            mediaDetailView.hide();
        }
        mediaDetailState = null;
    }

    private boolean canResolveDetailInfo(FnosFileEntry entry) {
        if (entry == null || entry.path.length() == 0) {
            return false;
        }
        String path = entry.path.toLowerCase();
        return path.indexOf("/") < 0
                && !path.startsWith("http://")
                && !path.startsWith("https://");
    }

    private void resolveDetailInfo(final FnosFileEntry entry) {
        if (mediaDetailState == null || mediaDetailState.entry != entry) {
            return;
        }
        mediaDetailState.setLoadingDetail(true);
        mediaDetailView.update(mediaDetailState);
        new Thread(new Runnable() {
            @Override
            public void run() {
                MediaDetailInfo loaded = null;
                String error = "";
                try {
                    loaded = newRestClient().itemDetail(entry.path);
                } catch (FnosRpcException ex) {
                    Logger.w("Media detail metadata failed: " + ex.getMessage());
                    error = "详情信息暂不可用";
                } catch (RuntimeException ex) {
                    Logger.w("Media detail metadata crashed: " + ex.getMessage());
                    error = "详情信息暂不可用";
                }
                final MediaDetailInfo result = loaded;
                final String message = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mediaDetailState == null || mediaDetailState.entry != entry) {
                            return;
                        }
                        if (result != null) {
                            mediaDetailState.setDetailInfo(result);
                        } else {
                            mediaDetailState.setDetailError(message);
                        }
                        mediaDetailView.update(mediaDetailState);
                    }
                });
            }
        }, "fnos-detail-metadata").start();
    }

    private void resolveDetailSources(final FnosFileEntry entry) {
        if (mediaDetailState == null || mediaDetailState.entry != entry) {
            return;
        }
        mediaDetailState.setLoadingSources(true);
        mediaDetailView.update(mediaDetailState);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final PlaybackSourcesResult result = resolvePlaybackSources(entry);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mediaDetailState == null || mediaDetailState.entry != entry) {
                            return;
                        }
                        if (result.success) {
                            mediaDetailState.setSources(result.sources, result.transcode);
                            mediaDetailState.setProgress(
                                    result.positionSeconds, result.durationSeconds, result.hasSubtitle);
                        } else {
                            mediaDetailState.setError(result.message);
                        }
                        mediaDetailView.update(mediaDetailState);
                    }
                });
            }
        }, "fnos-detail-sources").start();
    }

    private void playResolvedUrl(FnosFileEntry entry, String url) {
        playResolvedUrl(entry, url, 0L);
    }

    private void playResolvedUrl(FnosFileEntry entry, String url, long startPositionMs) {
        statusOverlay.hide();
        closeMediaDetail();
        recentPlaybackStore.remember(entry);
        stopTranscodeKeepAlive();
        if (entry.canTryNativePlayback()) {
            nativeVideoPlayerView.play(entry, url, startPositionMs);
            return;
        }
        openExternalPlayer(entry, url, "当前格式 " + entry.formatLabel()
                + " 超出 Android 4 内置播放器兼容范围，正在尝试外部播放器");
    }

    private void playResolvedSources(FnosFileEntry entry, List<FnosPlaybackSource> sources) {
        playResolvedSources(entry, sources, 0L, null);
    }

    private void playResolvedSources(FnosFileEntry entry, List<FnosPlaybackSource> sources, long startPositionMs) {
        playResolvedSources(entry, sources, startPositionMs, null);
    }

    private void playResolvedSources(FnosFileEntry entry, List<FnosPlaybackSource> sources, long startPositionMs,
                                     FnosRestClient.TranscodeSession transcode) {
        statusOverlay.hide();
        closeMediaDetail();
        recentPlaybackStore.remember(entry);
        startTranscodeKeepAlive(transcode);
        if (entry.canTryNativePlayback()) {
            List<FnosFileEntry> playlist;
            if (playbackQueue.size() > 1) {
                playlist = playbackQueue;
            } else if (mediaTypeContains(entry, "episode")) {
                playlist = currentSeasonEpisodes;
            } else {
                // 电影/单文件：清掉上一部剧留下的选集列表
                playlist = new ArrayList<FnosFileEntry>();
            }
            nativeVideoPlayerView.setPlaylist(playlist, entry.path,
                    profile == null ? "" : profile.baseUrl, posterAuthToken);
            nativeVideoPlayerView.play(entry, sources, startPositionMs,
                    transcode != null && transcode.url != null && transcode.url.length() > 0);
            return;
        }
        FnosPlaybackSource first = sources == null || sources.size() == 0 ? null : sources.get(0);
        openExternalPlayer(entry, first == null ? "" : first.url, "当前格式 " + entry.formatLabel()
                + " 超出 Android 4 内置播放器兼容范围，正在尝试外部播放器");
    }

    /**
     * 转码 HLS 会话建立约 60 秒后会失效（分片 410），官方 web 靠播放期间每十几秒一次
     * 的 /play/record 保活；这里用同一个上报接口维持会话，顺带把观看进度写回服务器。
     */
    private void startTranscodeKeepAlive(FnosRestClient.TranscodeSession session) {
        playbackKeepAliveHandler.removeCallbacks(playbackKeepAliveRunnable);
        activeTranscodeSession = session;
        if (session == null) {
            return;
        }
        playbackKeepAliveHandler.post(playbackKeepAliveRunnable);
    }

    private void stopTranscodeKeepAlive() {
        playbackKeepAliveHandler.removeCallbacks(playbackKeepAliveRunnable);
        activeTranscodeSession = null;
    }

    private void resolveAndPlaySources(final FnosFileEntry entry) {
        showStatus("正在准备播放源...\n" + entry.name);
        new Thread(new Runnable() {
            @Override
            public void run() {
                final PlaybackSourcesResult result = resolvePlaybackSources(entry);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (result.success) {
                            playResolvedSources(entry, result.sources, result.positionSeconds * 1000L,
                                    result.transcode);
                        } else {
                            showStatus(result.message);
                        }
                    }
                });
            }
        }, "fnos-playback-sources").start();
    }

    private PlaybackSourcesResult resolvePlaybackSources(FnosFileEntry entry) {
        // 官方 web 同款 REST 直链优先（play/info → media/range），无需 WebSocket RPC；
        // 失败再回退 RPC file.download（本地文件、旧会话等场景）。
        if (isRestItemGuid(entry.path)) {
            try {
                FnosRestClient rest = newRestClient();
                FnosRestClient.PlaybackDirect direct = rest.playbackDirectForItem(entry.path);
                if (direct.url.length() > 0) {
                    List<FnosPlaybackSource> sources = new ArrayList<FnosPlaybackSource>();
                    sources.add(new FnosPlaybackSource(direct.label, direct.url));
                    return PlaybackSourcesResult.success(
                            sources, direct.positionSeconds, direct.durationSeconds, direct.hasSubtitle,
                            direct.transcode);
                }
            } catch (FnosRpcException ex) {
                Logger.w("REST playback url failed, fallback RPC: " + ex.getMessage());
            } catch (RuntimeException ex) {
                Logger.w("REST playback url crashed, fallback RPC: " + ex.getMessage());
            }
        }
        try {
            FnosSession session = sessionStore.load();
            if (!session.hasToken()) {
                return PlaybackSourcesResult.failure("登录会话已失效");
            }
            FnosRpcClient client = new FnosRpcClient(profile, sessionStore.getOrCreateDeviceId());
            return PlaybackSourcesResult.success(client.playbackSources(session, entry));
        } catch (FnosRpcException ex) {
            Logger.w("Native playback sources failed: " + ex.getMessage());
            return PlaybackSourcesResult.failure("播放源准备失败：" + ex.getMessage());
        } catch (RuntimeException ex) {
            Logger.w("Native playback sources crashed: " + ex.getMessage());
            return PlaybackSourcesResult.failure("播放源准备异常：" + ex.getMessage());
        }
    }

    /** REST 条目的 path 就是 32 位 guid（无路径分隔符、非 URL）。 */
    private boolean isRestItemGuid(String path) {
        if (path == null) {
            return false;
        }
        String value = path.trim();
        if (value.length() != 32 || value.indexOf('/') >= 0 || value.indexOf('\\') >= 0) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean hex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (!hex) {
                return false;
            }
        }
        return true;
    }

    private void openExternalPlayer(FnosFileEntry entry, String url, String fallbackMessage) {
        if (url == null || url.length() == 0) {
            showStatus(fallbackMessage + "\n但缺少可用播放地址");
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(Uri.parse(url), entry == null ? "video/*" : entry.mimeType());
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            if (entry != null) {
                intent.putExtra("title", entry.name);
            }
            if (intent.resolveActivity(getPackageManager()) == null) {
                showStatus(fallbackMessage + "\n未找到外部播放器，请安装 VLC、MX Player 或系统兼容播放器");
                return;
            }
            startActivity(intent);
        } catch (ActivityNotFoundException ex) {
            showStatus(fallbackMessage + "\n未找到可播放该视频的外部应用");
        } catch (RuntimeException ex) {
            Logger.w("External player failed: " + ex.getMessage());
            showStatus(fallbackMessage + "\n外部播放器启动失败");
        }
    }

    /** 全屏沉浸：隐藏状态栏/导航栏（Android 4.4 起支持 sticky immersive）。 */
    private void enterFullScreen() {
        int flags = View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 把服务端晦涩错误码翻译成能照着做的提示。 */
    static String explainLoginError(String message) {
        String text = message == null ? "" : message;
        if (text.indexOf("131072") >= 0) {
            return "服务器拒绝登录（131072）。多为 fnOS 版本差异，或该账号开启了两步验证/登录保护；"
                    + "请在网页端确认能登录，并在「设置 → 安全性」关闭二次验证后重试。";
        }
        return text;
    }

    private static final class NativeAuthResult {
        final boolean success;
        final String message;

        private NativeAuthResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }

        static NativeAuthResult success() {
            return new NativeAuthResult(true, "");
        }

        static NativeAuthResult failure(String message) {
            return new NativeAuthResult(false, message);
        }
    }

    private static final class FileLoadResult {
        final boolean success;
        final FnosFileList list;
        final String message;

        private FileLoadResult(boolean success, FnosFileList list, String message) {
            this.success = success;
            this.list = list;
            this.message = message;
        }

        static FileLoadResult success(FnosFileList list) {
            return new FileLoadResult(true, list, "");
        }

        static FileLoadResult failure(String message) {
            return new FileLoadResult(false, null, message);
        }
    }

    /**
     * 导航栈中的一帧：等价于网页端的一个路由。
     *
     * <p>mode 对应网页路由：
     * {@link #BROWSER_MODE_MEDIA} → {@code /library/:guid}、{@code /tv/:guid}；
     * {@link #BROWSER_MODE_CATEGORY} → {@code /list/all|movie|tv|live|other}；
     * {@link #BROWSER_MODE_FAVORITES} → {@code /favorite}；
     * {@link #BROWSER_MODE_RECENT} → 首页「继续观看」。
     */
    private static final class NavFrame {
        final int mode;
        final String path;
        final String title;
        final String category;
        final String keyword;
        /** 收藏页分类标签（全部 / 电影 / 电视节目 / 单集 / 电视直播 / 人物）。 */
        final String favoriteTab;
        /** 该帧对应的详情页（可空）。 */
        final FnosFileEntry detail;

        NavFrame(int mode, String path, String title, String category) {
            this(mode, path, title, category, "");
        }

        NavFrame(int mode, String path, String title, String category, String keyword) {
            this(mode, path, title, category, keyword, "", null);
        }

        NavFrame(int mode, String path, String title, String category, String keyword,
                String favoriteTab, FnosFileEntry detail) {
            this.mode = mode;
            this.path = path == null ? "" : path;
            this.title = title == null ? "" : title;
            this.category = category == null ? "" : category;
            this.keyword = keyword == null ? "" : keyword;
            this.favoriteTab = favoriteTab == null ? "" : favoriteTab;
            this.detail = detail;
        }
    }

    private static final class MediaLoadResult {
        final boolean success;
        final String title;
        final String subtitle;
        final FnosFileList list;
        final boolean sortEntries;
        final String message;
        /** 非空表示剧 / 季页面（hero 头部 + 季卡片 / 剧集卡片）。 */
        final MediaSeriesHeader seriesHeader;
        /** 剧 / 季页可切换的季列表。 */
        final List<FnosFileEntry> seasons;
        /** 季页当前季 guid（剧页为空串）。 */
        final String currentSeasonGuid;
        /** true = 季页（剧集卡片），false = 剧页（季卡片）。 */
        final boolean episodes;

        private MediaLoadResult(boolean success, String title, String subtitle, FnosFileList list,
                                boolean sortEntries, String message,
                                MediaSeriesHeader seriesHeader, List<FnosFileEntry> seasons,
                                String currentSeasonGuid, boolean episodes) {
            this.success = success;
            this.title = title;
            this.subtitle = subtitle;
            this.list = list;
            this.sortEntries = sortEntries;
            this.message = message;
            this.seriesHeader = seriesHeader;
            this.seasons = seasons == null ? new ArrayList<FnosFileEntry>() : seasons;
            this.currentSeasonGuid = currentSeasonGuid == null ? "" : currentSeasonGuid;
            this.episodes = episodes;
        }

        static MediaLoadResult success(String title, String subtitle, FnosFileList list, boolean sortEntries) {
            return new MediaLoadResult(true, title, subtitle, list, sortEntries, "",
                    null, null, "", false);
        }

        static MediaLoadResult series(String title, String subtitle, FnosFileList list,
                                      MediaSeriesHeader header, List<FnosFileEntry> seasons,
                                      String currentSeasonGuid, boolean episodes) {
            return new MediaLoadResult(true, title, subtitle, list, false, "",
                    header, seasons, currentSeasonGuid, episodes);
        }

        static MediaLoadResult failure(String message) {
            return new MediaLoadResult(false, "", "", null, true, message,
                    null, null, "", false);
        }
    }

    private static final class PlaybackSourcesResult {
        final boolean success;
        final List<FnosPlaybackSource> sources;
        final String message;
        final long positionSeconds;
        final long durationSeconds;
        final boolean hasSubtitle;
        final FnosRestClient.TranscodeSession transcode;

        private PlaybackSourcesResult(boolean success, List<FnosPlaybackSource> sources, String message,
                                      long positionSeconds, long durationSeconds, boolean hasSubtitle,
                                      FnosRestClient.TranscodeSession transcode) {
            this.success = success;
            this.sources = sources == null ? new ArrayList<FnosPlaybackSource>() : sources;
            this.message = message;
            this.positionSeconds = positionSeconds;
            this.durationSeconds = durationSeconds;
            this.hasSubtitle = hasSubtitle;
            this.transcode = transcode;
        }

        static PlaybackSourcesResult success(List<FnosPlaybackSource> sources) {
            return new PlaybackSourcesResult(true, sources, "", 0L, 0L, false, null);
        }

        static PlaybackSourcesResult success(List<FnosPlaybackSource> sources, long positionSeconds,
                                             long durationSeconds, boolean hasSubtitle) {
            return new PlaybackSourcesResult(true, sources, "", positionSeconds, durationSeconds, hasSubtitle, null);
        }

        static PlaybackSourcesResult success(List<FnosPlaybackSource> sources, long positionSeconds,
                                             long durationSeconds, boolean hasSubtitle,
                                             FnosRestClient.TranscodeSession transcode) {
            return new PlaybackSourcesResult(true, sources, "", positionSeconds, durationSeconds, hasSubtitle, transcode);
        }

        static PlaybackSourcesResult failure(String message) {
            return new PlaybackSourcesResult(false, null, message, 0L, 0L, false, null);
        }
    }

}
