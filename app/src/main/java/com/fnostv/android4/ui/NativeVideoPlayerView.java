package com.fnostv.android4.ui;

import android.content.Context;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.FnosPlaybackSource;
import com.fnostv.android4.player.NumberSeekShortcut;
import com.fnostv.android4.player.PlayerEngine;
import com.fnostv.android4.player.PlaybackOptions;
import com.fnostv.android4.player.PlaybackStrategy;
import com.fnostv.android4.player.SystemPlayerEngine;
import com.fnostv.android4.player.VlcPlayerEngine;
import com.fnostv.android4.util.AppPrefs;
import com.fnostv.android4.util.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class NativeVideoPlayerView {
    public interface Listener {
        void onNativeVideoError(FnosFileEntry entry, String url, String reason);

        /** 播放自然结束（片尾），用于剧集/同目录多视频自动连播下一集。 */
        void onPlaybackFinished(FnosFileEntry entry);

        /** 转码流拖动：需要刷新 HLS 列表后重建播放器跳到目标位置。 */
        void onTranscodeSeekRequested(FnosFileEntry entry, int targetMs);

        /** 播放器内选集：点了列表里的某一集。 */
        void onPlaylistItemSelected(FnosFileEntry entry);
    }

    private static final int SEEK_STEP_MS = 10000;
    private static final int LARGE_SEEK_STEP_MS = 30000;
    private static final int CONTROL_HIDE_DELAY_MS = 5000;
    private static final int PROGRESS_INTERVAL_MS = 1000;
    private static final int PREPARE_TIMEOUT_MS = 20000;
    /** 系统播放器（MediaPlayer）超过这个时间还没出画面就换回 VLC。 */
    private static final int SYSTEM_FIRST_FRAME_TIMEOUT_MS = 25000;
    /** 直播流掉线后的自动重连次数。 */
    private static final int LIVE_MAX_RETRIES = 4;
    private static final int SEEK_COMMIT_DELAY_MS = 260;
    private static final int SEEK_SETTLE_DELAY_MS = 1200;
    /** 转码/HLS 跳转后需要重新起流，最长允许 6 秒追上目标位置。 */
    private static final int SEEK_SETTLE_MAX_RETRIES = 12;
    private static final int MODE_FIT = 0;
    private static final int MODE_FILL = 1;
    private static final float[] SPEEDS = new float[]{1.0f, 1.25f, 1.5f, 2.0f, 0.75f};

    private final Context context;
    private final Listener listener;
    private final Handler handler = new Handler();
    private final List<FnosPlaybackSource> playbackSources = new ArrayList<FnosPlaybackSource>();
    private FrameLayout view;
    private PlaybackSurfaceView videoView;
    private ProgressBar loadingView;
    private TextView titleView;
    private LinearLayout controlBar;
    private LinearLayout topBarView;
    private SeekBar seekBar;
    private TextView timeView;
    private PlayPauseView playStateView;
    private TextView engineView;
    private final List<TextView> chipViews = new ArrayList<TextView>();
    private int chipFocusIndex = -1;
    private LinearLayout infoPanel;
    private boolean infoOpen;
    private TextView speedView;
    private TextView pictureView;
    private TextView resolutionView;
    private TextView statusView;
    private TextView hintView;
    private SurfaceHolder surfaceHolder;
    private FnosFileEntry currentEntry;
    private String currentUrl = "";
    private PlayerEngine currentPlayer;
    private PlaybackOptions playbackOptions;
    private boolean surfaceReady;
    private boolean dragging;
    private boolean seeking;
    private boolean suppressErrors;
    private boolean prepared;
    private boolean preferHardwareCodec;
    /** 播放失败后只自动重试一次，避免无限重试把电视卡死。 */
    private boolean retriedPlayback;
    /** 系统播放器在本片失败过一次后，本片固定改用 VLC。 */
    private boolean forceVlc;
    private boolean firstFrameSeen;
    private long prepareStartedAtMs;
    private int bufferingCount;
    private int speedIndex;
    private int sourceIndex;
    private int pendingSeekMs = -1;
    private int pendingSeekRequestMs = -1;
    private int pictureMode = MODE_FIT;
    private int videoWidth;
    private int videoHeight;
    /** 播放器内选集：当前季/目录的完整列表。 */
    private final List<FnosFileEntry> playlist = new ArrayList<FnosFileEntry>();
    private String playlistCurrentPath = "";
    private LinearLayout playlistPanel;
    private HorizontalScrollView playlistScroll;
    private LinearLayout playlistRow;
    private boolean playlistOpen;
    /** 选集卡片的缩略图引用：只在可见/聚焦时加载，避免整季几十张一起解码。 */
    private static final class ThumbRef {
        final FnosFileEntry entry;
        final FrameLayout frame;
        final ImageView image;
        final TextView placeholder;
        boolean loaded;

        ThumbRef(FnosFileEntry entry, FrameLayout frame, ImageView image, TextView placeholder) {
            this.entry = entry;
            this.frame = frame;
            this.image = image;
            this.placeholder = placeholder;
        }
    }

    private final List<ThumbRef> thumbRefs = new ArrayList<ThumbRef>();
    private final PosterLoader posterLoader = new PosterLoader();
    private String playlistPosterBaseUrl = "";
    /** 列表里第一张可用剧照：某些剧集没有自己的剧照时用它兜底。 */
    private String playlistFallbackPoster = "";
    /** 后台补图进度（打开选集面板后按批加载，避免一次性几十个请求）。 */
    private int thumbPreloadIndex;
    /** 最近一次有效的总时长（seek 期间 VLC 可能短暂返回 0）。 */
    private int lastKnownDurationMs;
    private int seekSettleRetries;
    /**
     * 跳转后 VLC 在 HLS 重新起流期间可能长时间返回 0 或旧值，
     * 这里用「目标位置 + 实际流逝时间」兜底，避免进度条和时间归零/乱跳。
     */
    private int seekFallbackMs = -1;
    private long seekFallbackAtMs;
    /** 当前是否服务器转码（HLS）播放。 */
    private boolean transcodedPlayback;
    /** 直播流（IPTV / LiveChannel）：不显示进度条，时间显示「直播中」。 */
    private boolean liveStream;
    private int liveRetries;
    /** 直播菜单模式：播放器只出画面，不显示自己的控制条。 */
    private boolean menuMode;
    /** 本次运行是否强制走 VLC（电视刚重启过时的安全模式）。 */
    public static volatile boolean safeMode = false;
    /** 重建播放器后要跳到的位置；-1 表示不需要。 */
    private int restartSeekTargetMs = -1;
    /** 重建/跳转进行中，用于合并连续按键。 */
    private boolean restartInFlight;
    private int pendingRestartMs = -1;
    private int restartRetries;
    private int restartSeekRetries;
    /** 远距离跳转前的位置：目标还没转好时退回这里，避免停在片头附近。 */
    private int seekOriginMs = -1;
    /** 目标超出已转码范围，被截到最远可播位置。 */
    private boolean restartClamped;

    private final Runnable progressRunnable = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            if (isVisible()) {
                handler.postDelayed(this, PROGRESS_INTERVAL_MS);
            }
        }
    };
    private final Runnable hideControlsRunnable = new Runnable() {
        @Override
        public void run() {
            hideControls();
        }
    };
    private final Runnable hideHintRunnable = new Runnable() {
        @Override
        public void run() {
            if (hintView != null) {
                hintView.setVisibility(View.GONE);
            }
        }
    };
    private final Runnable prepareTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (isVisible() && !prepared && !suppressErrors) {
                notifyPlaybackError("播放器准备超时");
            }
        }
    };
    /** 播够 90 秒没出事，就把「最后播放」标记清掉（避免下次误判）。 */
    private final Runnable clearPlayMarkerRunnable = new Runnable() {
        @Override
        public void run() {
            com.fnostv.android4.util.AppPrefs.putString(context,
                    com.fnostv.android4.util.AppPrefs.KEY_LAST_PLAY_NAME, "");
            com.fnostv.android4.util.AppPrefs.putString(context,
                    com.fnostv.android4.util.AppPrefs.KEY_LAST_PLAY_URL, "");
        }
    };
    private final Runnable hideInfoRunnable = new Runnable() {
        @Override
        public void run() {
            hidePlaybackInfo();
        }
    };
    private final Runnable seekCommitRunnable = new Runnable() {
        @Override
        public void run() {
            commitPendingSeek();
        }
    };
    private final Runnable seekSettledRunnable = new Runnable() {
        @Override
        public void run() {
            int target = pendingSeekMs;
            if (target >= 0) {
                int current = position();
                if (Math.abs(current - target) > 5000 && seekSettleRetries < SEEK_SETTLE_MAX_RETRIES) {
                    seekSettleRetries++;
                    // HLS 重新起流后 VLC 有时会把时间归零，隔 2 秒补一次 seek 能把它拉回来
                    if (seekSettleRetries == 4 && currentPlayer != null) {
                        try {
                            currentPlayer.seekTo(target);
                        } catch (RuntimeException ignored) {
                        }
                    }
                    handler.postDelayed(this, 500);
                    return;
                }
                if (Math.abs(current - target) > 5000) {
                    // 追了 6 秒还没到：以目标为基准继续走时间轴，别让界面归零
                    seekFallbackMs = target;
                    seekFallbackAtMs = SystemClock.elapsedRealtime();
                }
            }
            seekSettleRetries = 0;
            pendingSeekMs = -1;
            seeking = false;
            if (prepared) {
                loadingView.setVisibility(View.GONE);
            }
            updateProgress();
        }
    };

    public NativeVideoPlayerView(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public View create() {
        view = new FrameLayout(context);
        view.setBackgroundColor(Color.BLACK);
        view.setVisibility(View.GONE);
        // 播放页自己持有焦点：避免背景列表/侧栏抢走 OK 键
        view.setFocusable(true);
        view.setFocusableInTouchMode(true);

        videoView = new PlaybackSurfaceView(context);
        videoView.setResizeMode(pictureMode);
        videoView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                surfaceHolder = holder;
                surfaceReady = true;
                if (currentPlayer != null) {
                    currentPlayer.attachSurface(holder, videoView.getWidth(), videoView.getHeight());
                    currentPlayer.setFillMode(pictureMode == MODE_FILL);
                } else if (currentUrl.length() > 0 && view.getVisibility() == View.VISIBLE) {
                    startPlayer();
                }
            }

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                surfaceHolder = holder;
                if (currentPlayer != null) {
                    currentPlayer.resizeSurface(width, height);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {
                surfaceReady = false;
                surfaceHolder = null;
                if (currentPlayer != null) {
                    currentPlayer.detachSurface();
                }
            }
        });
        view.addView(videoView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                Gravity.CENTER));

        // ── 顶部标题栏（返回箭头 + 标题），与网页播放器一致 ──────────────
        LinearLayout topBar = new LinearLayout(context);
        topBarView = topBar;
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(0x99000000);
        topBar.setPadding(dp(22), 0, dp(22), 0);
        topBar.setMinimumHeight(dp(52));

        TextView backView = new TextView(context);
        backView.setText("←");
        backView.setTextColor(Color.WHITE);
        backView.setTextSize(20);
        backView.setGravity(Gravity.CENTER);
        backView.setPadding(0, 0, dp(10), 0);
        backView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        });
        topBar.addView(backView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        titleView = new TextView(context);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(15);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        topBar.addView(titleView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        view.addView(topBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP));

        // ── 底部控制条：进度条 + 播放键 + 时间 + 右侧标签 ────────────────
        controlBar = new LinearLayout(context);
        controlBar.setOrientation(LinearLayout.VERTICAL);
        controlBar.setBackgroundColor(0xCC000000);
        controlBar.setPadding(0, dp(4), 0, dp(8));

        LinearLayout controlRow = new LinearLayout(context);
        controlRow.setOrientation(LinearLayout.HORIZONTAL);
        controlRow.setGravity(Gravity.CENTER_VERTICAL);
        controlRow.setPadding(dp(20), 0, dp(20), dp(8));

        playStateView = new PlayPauseView(context);
        controlRow.addView(playStateView, new LinearLayout.LayoutParams(dp(30), dp(30)));

        timeView = controlText("00:00 / 00:00");
        timeView.setTextSize(14);
        timeView.setTextColor(0xFFE6EAF0);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        timeParams.leftMargin = dp(18);
        controlRow.addView(timeView, timeParams);

        View spacer = new View(context);
        controlRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));

        speedView = controlChip("倍速 1.0x");
        pictureView = controlChip("画面 适应");
        resolutionView = controlChip("清晰度 原画");
        engineView = controlChip("硬解 · 系统");
        engineView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePlaybackInfo();
            }
        });
        speedView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleSpeed();
            }
        });
        pictureView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cyclePictureMode();
            }
        });
        resolutionView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cycleQuality();
            }
        });
        chipViews.clear();
        chipViews.add(engineView);
        chipViews.add(speedView);
        chipViews.add(pictureView);
        chipViews.add(resolutionView);
        controlRow.addView(engineView, chipParams());
        controlRow.addView(speedView, chipParams());
        controlRow.addView(pictureView, chipParams());
        controlRow.addView(resolutionView, chipParams());

        statusView = controlText(playbackStatusLabel());
        statusView.setTextColor(0xFFBFD8FF);
        statusView.setTextSize(12);

        seekBar = new SeekBar(context);
        seekBar.setMax(1000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser) {
                    updateTimeLabel(progressToPosition(progress), duration());
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                dragging = true;
                showControls();
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                dragging = false;
                requestSeekTo(progressToPosition(bar.getProgress()));
                showControlsTemporarily();
            }
        });

        hintView = new TextView(context);
        hintView.setTextColor(0xFFE6EDF3);
        hintView.setTextSize(13);
        hintView.setSingleLine(true);
        hintView.setText("上键：工具栏（播放信息/倍速/画面/清晰度），左右快退快进，确认暂停，下键选集，菜单倍速");

        seekBar.setPadding(dp(18), 0, dp(18), 0);
        controlBar.addView(seekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(26)));
        controlBar.addView(controlRow, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)));
        view.addView(controlBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM));

        FrameLayout.LayoutParams hintParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        hintParams.bottomMargin = dp(120);
        hintView.setBackgroundDrawable(FnosTheme.rounded(0xCC10151C, 10, context));
        hintView.setPadding(dp(16), dp(8), dp(16), dp(8));
        view.addView(hintView, hintParams);

        loadingView = new ProgressBar(context);
        view.addView(loadingView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));

        // ── 播放信息面板（点播放类型按钮 / 长按确认呼出）──────────────
        infoPanel = new LinearLayout(context);
        infoPanel.setOrientation(LinearLayout.VERTICAL);
        infoPanel.setBackgroundDrawable(FnosTheme.rounded(0xE610151C, 12, context));
        infoPanel.setPadding(dp(20), dp(14), dp(20), dp(14));
        infoPanel.setVisibility(View.GONE);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                dp(430), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.RIGHT);
        infoParams.topMargin = dp(66);
        infoParams.rightMargin = dp(26);
        view.addView(infoPanel, infoParams);

        // ── 选集面板（控制栏上方，按「下」呼出）──────────────
        playlistPanel = new LinearLayout(context);
        playlistPanel.setOrientation(LinearLayout.VERTICAL);
        playlistPanel.setBackgroundDrawable(FnosTheme.rounded(0xE610151C, 12, context));
        playlistPanel.setPadding(dp(18), dp(10), dp(18), dp(12));
        playlistPanel.setVisibility(View.GONE);

        TextView playlistTitle = new TextView(context);
        playlistTitle.setText("选集");
        playlistTitle.setTextColor(0x99FFFFFF);
        playlistTitle.setTextSize(13);
        playlistTitle.setPadding(dp(2), 0, 0, dp(6));
        playlistPanel.addView(playlistTitle);

        playlistScroll = new PlaylistScrollView(context);
        playlistScroll.setHorizontalScrollBarEnabled(false);
        playlistScroll.setClipToPadding(false);
        ((PlaylistScrollView) playlistScroll).setOnScrollListener(new Runnable() {
            @Override
            public void run() {
                handler.removeCallbacks(loadVisibleThumbsRunnable);
                handler.postDelayed(loadVisibleThumbsRunnable, 80);
            }
        });
        playlistRow = new LinearLayout(context);
        playlistRow.setOrientation(LinearLayout.HORIZONTAL);
        playlistScroll.addView(playlistRow, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        playlistPanel.addView(playlistScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(214)));

        FrameLayout.LayoutParams playlistParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        playlistParams.leftMargin = dp(22);
        playlistParams.rightMargin = dp(22);
        playlistParams.bottomMargin = dp(74);
        view.addView(playlistPanel, playlistParams);
        return view;
    }

    public void play(FnosFileEntry entry, String url) {
        play(entry, url, 0L);
    }

    /** startPositionMs &gt; 0 时从上次观看位置续播（与网页端 ts 行为一致）。 */
    public void play(FnosFileEntry entry, String url, long startPositionMs) {
        List<FnosPlaybackSource> sources = new ArrayList<FnosPlaybackSource>();
        sources.add(new FnosPlaybackSource("原画", url));
        play(entry, sources, startPositionMs);
    }

    public void play(FnosFileEntry entry, List<FnosPlaybackSource> sources) {
        play(entry, sources, 0L);
    }

    public void play(FnosFileEntry entry, List<FnosPlaybackSource> sources, long startPositionMs) {
        play(entry, sources, startPositionMs, false);
    }

    public void play(FnosFileEntry entry, List<FnosPlaybackSource> sources, long startPositionMs, boolean transcoded) {
        // 只有真正的 HLS 转码流才算「转码」：直连播放也会带一个进度上报用的空会话
        transcodedPlayback = transcoded && isTranscodeUrl(
                sources == null || sources.size() == 0 ? "" : sources.get(0).url);
        // 只更新“当前集”，卡片等面板真正打开时再建（52 集时省一次整表重建）
        playlistCurrentPath = entry == null ? "" : entry.path;
        restartInFlight = false;
        pendingRestartMs = -1;
        restartSeekTargetMs = -1;
        restartSeekRetries = 0;
        restartRetries = 0;
        currentEntry = entry;
        playbackSources.clear();
        if (sources != null) {
            for (int i = 0; i < sources.size(); i++) {
                FnosPlaybackSource source = sources.get(i);
                if (source != null && source.isValid()) {
                    playbackSources.add(source);
                }
            }
        }
        if (playbackSources.size() == 0) {
            playbackSources.add(new FnosPlaybackSource("原画", ""));
        }
        sourceIndex = 0;
        currentUrl = currentSource().url;
        playbackOptions = buildPlaybackOptions(currentUrl);
        pendingSeekMs = startPositionMs > 0L ? (int) Math.min(startPositionMs, Integer.MAX_VALUE) : -1;
        pendingSeekRequestMs = -1;
        seeking = false;
        suppressErrors = false;
        prepared = false;
        preferHardwareCodec = entry != null && entry.prefersHardwarePlayback() && hardwareDecoderAllowed();
        liveStream = entry != null && entry.isLive();
        liveRetries = 0;
        // 记录最后播放的条目：电视如果意外重启，下次启动时用它判断要不要进安全模式
        com.fnostv.android4.util.AppPrefs.putString(context,
                com.fnostv.android4.util.AppPrefs.KEY_LAST_PLAY_NAME,
                entry == null ? "" : entry.name);
        com.fnostv.android4.util.AppPrefs.putString(context,
                com.fnostv.android4.util.AppPrefs.KEY_LAST_PLAY_URL, currentUrl);
        com.fnostv.android4.util.AppPrefs.putLong(context,
                com.fnostv.android4.util.AppPrefs.KEY_LAST_PLAY_AT, System.currentTimeMillis());
        retriedPlayback = false;
        forceVlc = false;
        firstFrameSeen = false;
        prepareStartedAtMs = 0L;
        bufferingCount = 0;
        speedIndex = 0;
        videoWidth = 0;
        videoHeight = 0;
        lastKnownDurationMs = 0;
        seekSettleRetries = 0;
        seekFallbackMs = -1;
        titleView.setText(entry == null ? "" : entry.name);
        loadingView.setVisibility(View.VISIBLE);
        view.setVisibility(View.VISIBLE);
        view.setKeepScreenOn(true);
        view.requestFocus();
        enterImmersiveMode();
        videoView.setResizeMode(pictureMode);
        releasePlayer(false);
        if (surfaceReady) {
            startPlayer();
        }
        handler.removeCallbacks(progressRunnable);
        handler.post(progressRunnable);
        handler.removeCallbacks(prepareTimeoutRunnable);
        handler.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
        updateControlText();
        showControlsTemporarily();
    }

    /**
     * 播放器内选集：传入当前季/目录的完整列表。
     *
     * @param baseUrl 海报地址（一般就是登录的服务器地址）
     * @param token   海报鉴权 token
     */
    public void setPlaylist(List<FnosFileEntry> entries, String currentPath, String baseUrl, String token) {
        playlist.clear();
        if (entries != null) {
            for (int i = 0; i < entries.size(); i++) {
                FnosFileEntry entry = entries.get(i);
                if (entry != null && !entry.directory && entry.isVideo()) {
                    playlist.add(entry);
                }
            }
        }
        playlistCurrentPath = currentPath == null ? "" : currentPath;
        playlistPosterBaseUrl = baseUrl == null ? "" : baseUrl;
        posterLoader.setAuthorizationToken(token == null ? "" : token);
        // 有些剧集条目没有自己的剧照：用列表里第一张可用的，避免整排空白灰块
        playlistFallbackPoster = "";
        for (int i = 0; i < playlist.size(); i++) {
            String path = playlist.get(i).posterPath;
            if (path != null && path.length() > 0) {
                playlistFallbackPoster = path;
                break;
            }
        }
        if (playlistFallbackPoster.length() == 0 && currentEntry != null
                && currentEntry.posterPath != null) {
            playlistFallbackPoster = currentEntry.posterPath;
        }
        if (playlistPanel != null) {
            if (playlistOpen) {
                rebuildPlaylist();
            } else {
                playlistRow.removeAllViews();
            }
        }
    }

    public boolean isVisible() {
        return view != null && view.getVisibility() == View.VISIBLE;
    }

    /** 播放偏好里的“硬件解码优先”；关闭后一律软解（用于兼容硬解 OMX 有毛病的盒子）。 */
    private boolean hardwareDecoderAllowed() {
        return AppPrefs.bool(context, AppPrefs.KEY_HW_DECODER, true);
    }

    /** 当前播放位置（毫秒）。 */
    public int currentPositionMs() {
        return position();
    }

    /** 当前媒体总时长（毫秒）。 */
    public int currentDurationMs() {
        return duration();
    }

    public boolean toggle() {
        if (!isVisible() || currentPlayer == null) {
            return false;
        }
        if (currentPlayer.isPlaying()) {
            currentPlayer.pause();
            showHint("已暂停");
        } else {
            currentPlayer.start();
            showHint("继续播放");
        }
        updateControlText();
        showControlsTemporarily();
        return true;
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isVisible()) {
            return false;
        }
        // 选集面板打开时：方向键/OK 全部交给面板里的卡片，只有返回/上下键收起
        if (playlistOpen) {
            if (keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                closePlaylist();
                return true;
            }
            return false;
        }
        // 工具栏按钮聚焦时：左右切换按钮、确认执行、上下/返回退出
        if (chipFocusIndex >= 0) {
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                moveChipFocus(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                moveChipFocus(1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER) {
                activateChip(chipFocusIndex);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_BACK
                    || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                clearChipFocus();
                return true;
            }
        }
        if (keyCode == KeyEvent.KEYCODE_BACK && infoOpen) {
            hidePlaybackInfo();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_INFO) {
            togglePlaybackInfo();
            return true;
        }
        if ((keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                && event.getRepeatCount() == 1) {
            // 长按确认键：看播放信息（短按仍然是暂停/播放）
            showPlaybackInfo();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || keyCode == KeyEvent.KEYCODE_SPACE) {
            return toggle();
        }
        int numberSeekPercent = NumberSeekShortcut.percentForKey(keyCode);
        if (numberSeekPercent >= 0) {
            seekToPercent(numberSeekPercent);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT
                || keyCode == KeyEvent.KEYCODE_MEDIA_REWIND) {
            seekBy(keyCode == KeyEvent.KEYCODE_MEDIA_REWIND ? -LARGE_SEEK_STEP_MS : -SEEK_STEP_MS);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                || keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD) {
            seekBy(keyCode == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD ? LARGE_SEEK_STEP_MS : SEEK_STEP_MS);
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            // 上键：聚焦底部工具栏（播放信息 / 倍速 / 画面 / 清晰度）
            focusChipRow();
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            if (playlist.size() > 1) {
                openPlaylist();
            } else {
                cycleQuality();
            }
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            cycleSpeed();
            return true;
        }
        showControlsTemporarily();
        return false;
    }

    public void hide() {
        hidePlaybackInfo();
        stopThumbPreload();
        if (playlistPanel != null) {
            playlistOpen = false;
            playlistPanel.setVisibility(View.GONE);
        }
        if (view == null || view.getVisibility() != View.VISIBLE) {
            return;
        }
        handler.removeCallbacks(progressRunnable);
        handler.removeCallbacks(hideControlsRunnable);
        handler.removeCallbacks(prepareTimeoutRunnable);
        handler.removeCallbacks(seekCommitRunnable);
        handler.removeCallbacks(seekSettledRunnable);
        suppressErrors = true;
        releasePlayer(true);
        view.setVisibility(View.GONE);
        view.setKeepScreenOn(false);
        currentEntry = null;
        currentUrl = "";
        playbackSources.clear();
        sourceIndex = 0;
        pendingSeekMs = -1;
        pendingSeekRequestMs = -1;
        prepared = false;
        seeking = false;
    }

    private void startPlayer() {
        if (currentUrl.length() == 0 || !surfaceReady) {
            return;
        }
        try {
            final PlayerEngine player = createPlayerEngine();
            currentPlayer = player;
            try {
                player.attachSurface(surfaceHolder, videoView.getWidth(), videoView.getHeight());
            } catch (Throwable ignored) {
            }
            try {
                player.setFillMode(pictureMode == MODE_FILL);
            } catch (Throwable ignored) {
            }
            player.setListener(new PlayerEngine.Listener() {
                @Override
                public void onPrepared(int width, int height, int durationMs) {
                    handler.removeCallbacks(prepareTimeoutRunnable);
                    prepared = true;
                    videoWidth = width;
                    videoHeight = height;
                    videoView.setVideoSize(videoWidth, videoHeight);
                    loadingView.setVisibility(View.GONE);
                    player.start();
                    if (pendingSeekMs > 0) {
                        player.seekTo(pendingSeekMs);
                        pendingSeekMs = -1;
                    }
                    applyPlaybackSpeed(SPEEDS[speedIndex]);
                    updateProgress();
                    updateControlText();
                    showControlsTemporarily();
                }

                @Override
                public void onVideoSizeChanged(int width, int height) {
                    videoWidth = width;
                    videoHeight = height;
                    videoView.setVideoSize(videoWidth, videoHeight);
                    updateControlText();
                }
                @Override
                public void onBufferingStart(int extra) {
                    bufferingCount++;
                    loadingView.setVisibility(View.VISIBLE);
                    Logger.w(player.name() + " buffering start file=" + fileName()
                            + " count=" + bufferingCount
                            + " source=" + currentSource().label
                            + " pos=" + position()
                            + " extra=" + extra);
                    if (bufferingCount == 3 && prepared && !seeking && pendingSeekRequestMs < 0 && playbackOptions != null
                            && playbackOptions.profileMode != PlaybackOptions.PROFILE_FLUENT) {
                        retryWithFluentPlayback("缓冲频繁，切换流畅模式");
                    }
                }

                @Override
                public void onBufferingEnd(int extra) {
                    loadingView.setVisibility(View.GONE);
                }

                @Override
                public void onFirstFrame() {
                    long startupMs = prepareStartedAtMs <= 0L
                            ? 0L
                            : Math.max(0L, System.currentTimeMillis() - prepareStartedAtMs);
                    if (restartInFlight) {
                        applyRestartSeek();
                    }
                    if (!firstFrameSeen) {
                        firstFrameSeen = true;
                        showHint("首帧 " + startupMs + "ms · " + playbackStatusLabel());
                        updateControlText();
                        showControlsTemporarily();
                    }
                }

                @Override
                public void onError(String reason) {
                    loadingView.setVisibility(View.GONE);
                    if (suppressErrors || currentUrl.length() == 0) {
                        return;
                    }
                    notifyPlaybackError(reason);
                }

                @Override
                public void onCompletion() {
                    if (suppressErrors || currentEntry == null || currentUrl.length() == 0) {
                        return;
                    }
                    final FnosFileEntry finished = currentEntry;
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            if (listener != null && isVisible() && !suppressErrors) {
                                listener.onPlaybackFinished(finished);
                            }
                        }
                    });
                }

                @Override
                public void onLog(String message) {
                }
            });
            prepareStartedAtMs = System.currentTimeMillis();
            showHint("正在启动 " + player.name() + " · " + playbackStatusLabel());
            updateControlText();
            guardSystemFirstFrame(player);
            try {
                player.prepare(currentUrl, playbackOptions);
            } catch (Throwable ex) {
                throw ex;
            }
        } catch (Exception ex) {
            loadingView.setVisibility(View.GONE);
            notifyPlaybackError("播放器初始化失败：" + ex.getMessage());
        }
    }

    private PlayerEngine createPlayerEngine() {
        if (shouldUseSystemPlayer()) {
            return new SystemPlayerEngine(context);
        }
        return new VlcPlayerEngine(context);
    }

    /**
     * 是否用系统播放器（MTK 硬解）。只用于「原画直连」：
     * 转码出来的 HLS（file:// 列表）、VLC 专属容器、以及设置里关掉硬解的情况继续用 VLC。
     */    /** 转码 HLS 列表的特征：本地缓存的 file:// m3u8（直连地址是 http 的 /media/range）。 */
    private static boolean isTranscodeUrl(String url) {
        if (url == null) {
            return false;
        }
        String lower = url.toLowerCase(java.util.Locale.US);
        return lower.startsWith("file://") && lower.indexOf(".m3u8") >= 0;
    }

    private boolean shouldUseSystemPlayer() {
        // safeMode：刚重启过（怀疑直连把电视拖崩），本次运行一律用 VLC
        if (safeMode || forceVlc || !hardwareDecoderAllowed()
                || currentEntry == null || currentUrl.length() == 0) {
            return false;
        }
        // 设置里可以一键关掉（个别机型起停 4K 会拖垮整机，需要退路）
        if (!AppPrefs.bool(context, AppPrefs.KEY_DIRECT_HW_PLAY, true)) {
            return false;
        }
        if (currentEntry.isLive()) {
            // 直播统一走 VLC：m3u8 / rtsp / udp 组播都由 libVLC 处理
            return false;
        }
        String url = currentUrl.toLowerCase(Locale.US);
        if (url.startsWith("file://") || url.indexOf(".m3u8") >= 0) {
            return false;
        }
        // RM/RMVB/AVI/WMV/FLV/VOB 这些容器系统解不了，继续用 VLC
        return currentEntry.prefersHardwarePlayback();
    }

    private void retryWithSoftwareCodec(String reason) {
        retriedPlayback = true;
        preferHardwareCodec = false;
        playbackOptions = playbackOptions == null
                ? buildPlaybackOptions(currentUrl).withSoftwareDecoder()
                : playbackOptions.withSoftwareDecoder();
        restartPlayback(reason);
    }

    /**
     * 系统播放器保底：只在「已经 prepared 但 25 秒都看不到首帧」时换回 VLC。
     * 注意不能太激进——视频层还占着时立刻起第二个解码器，MTK 电视可能会整机重启。
     */
    private void guardSystemFirstFrame(final PlayerEngine engine) {
        if (!(engine instanceof SystemPlayerEngine)) {
            return;
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (!isVisible() || currentPlayer != engine || firstFrameSeen || suppressErrors) {
                    return;
                }
                if (!prepared) {
                    return;
                }
                forceVlc = true;
                restartPlayback("系统播放器无画面，改用 VLC");
            }
        }, SYSTEM_FIRST_FRAME_TIMEOUT_MS);
    }

    private void retryWithFluentPlayback(String reason) {
        playbackOptions = PlaybackStrategy.onFrequentBuffering(playbackOptions);
        bufferingCount = 0;
        int savedPosition = position();
        pendingSeekMs = savedPosition > 0 ? savedPosition : pendingSeekMs;
        restartPlayback(reason);
    }

    /** 用当前 playbackOptions 重新起播；错误重试与参数降级共用。 */
    private void restartPlayback(String reason) {
        Logger.w(engineName() + " restart file=" + fileName() + " reason=" + reason
                + " options=" + optionsLabel());
        prepared = false;
        firstFrameSeen = false;
        loadingView.setVisibility(View.VISIBLE);
        showHint(reason);
        // 系统播放器释放后视频层需要一点时间回收，立刻再起 VLC 容易把电视拖垮
        boolean wasSystem = currentPlayer instanceof SystemPlayerEngine;
        releasePlayer(true);
        handler.removeCallbacks(deferredStartRunnable);
        if (surfaceReady) {
            if (wasSystem) {
                handler.postDelayed(deferredStartRunnable, 900);
            } else {
                startPlayer();
            }
        }
        handler.removeCallbacks(prepareTimeoutRunnable);
        handler.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
    }

    private final Runnable deferredStartRunnable = new Runnable() {
        @Override
        public void run() {
            if (isVisible() && currentPlayer == null && currentUrl.length() > 0) {
                startPlayer();
            }
        }
    };

    private void releasePlayer(boolean clearCurrent) {
        if (currentPlayer == null) {
            return;
        }
        try {
            currentPlayer.detachSurface();
            currentPlayer.stop();
        } catch (RuntimeException ignored) {
        }
        try {
            currentPlayer.release();
        } catch (RuntimeException ignored) {
        }
        currentPlayer = null;
    }

    private void notifyPlaybackError(String reason) {
        handler.removeCallbacks(prepareTimeoutRunnable);
        Logger.w(engineName() + " playback error file=" + fileName() + " format=" + formatLabel()
                + " hw=" + preferHardwareCodec
                + " options=" + optionsLabel()
                + " source=" + currentSource().label
                + " reason=" + reason);
        if (!retriedPlayback) {
            if (currentPlayer instanceof SystemPlayerEngine) {
                // 系统播放器打不开这个片源：本片改用 VLC（保留硬解尝试）
                forceVlc = true;
                restartPlayback("系统解码失败，改用 VLC");
                return;
            }
            // 首次失败：切软解再试一次（硬解在部分老机型上会失败），仍失败才回调错误。
            retryWithSoftwareCodec("播放失败，正在重试");
            return;
        }
        // 直播流经常会掉线：自动重连几次，而不是直接退出播放器
        if (currentEntry != null && currentEntry.isLive() && liveRetries < LIVE_MAX_RETRIES) {
            liveRetries++;
            retriedPlayback = false;
            forceVlc = true;
            showHint("直播断开，正在重连（" + liveRetries + "/" + LIVE_MAX_RETRIES + "）");
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (isVisible()) {
                        restartPlayback("直播重连");
                    }
                }
            }, 2500L);
            return;
        }
        suppressErrors = true;
        releasePlayer(true);
        listener.onNativeVideoError(currentEntry, currentUrl, reason);
    }

    private FnosPlaybackSource currentSource() {
        if (playbackSources.size() == 0) {
            return new FnosPlaybackSource("原画", currentUrl);
        }
        int index = Math.max(0, Math.min(sourceIndex, playbackSources.size() - 1));
        return playbackSources.get(index);
    }

    private String fileName() {
        return currentEntry == null ? "" : currentEntry.name;
    }

    private String formatLabel() {
        return currentEntry == null ? "video" : currentEntry.formatLabel();
    }

    private String engineName() {
        return currentPlayer == null ? "Player" : currentPlayer.name();
    }

    private String optionsLabel() {
        return playbackOptions == null ? "" : playbackOptions.describe();
    }

    private PlaybackOptions buildPlaybackOptions(String url) {
        boolean preferHardware = currentEntry != null && currentEntry.prefersHardwarePlayback();
        long size = currentEntry == null ? 0L : currentEntry.size;
        return PlaybackStrategy.initialOptions(url, fileName(), size, preferHardware);
    }

    private TextView controlChip(String text) {
        TextView chip = controlText(text);
        chip.setTextSize(13);
        chip.setTextColor(0xFFC7CFDA);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        chip.setBackgroundDrawable(playlistCardBackground(false));
        return chip;
    }

    /** 工具栏按钮：获得焦点时描边高亮（遥控器上下左右可以选中）。 */
    private void applyChipFocus(int index) {
        chipFocusIndex = index;
        for (int i = 0; i < chipViews.size(); i++) {
            TextView chip = chipViews.get(i);
            chip.setBackgroundDrawable(playlistCardBackground(i == index));
            chip.setTextColor(i == index ? Color.WHITE : 0xFFC7CFDA);
        }
    }

    private void focusChipRow() {
        showControls();
        if (chipViews.size() == 0) {
            return;
        }
        applyChipFocus(chipFocusIndex >= 0 ? chipFocusIndex : 0);
    }

    private void clearChipFocus() {
        applyChipFocus(-1);
    }

    private void moveChipFocus(int delta) {
        if (chipViews.size() == 0) {
            return;
        }
        int next = chipFocusIndex + delta;
        if (next < 0) {
            next = 0;
        }
        if (next >= chipViews.size()) {
            next = chipViews.size() - 1;
        }
        applyChipFocus(next);
    }

    /** 激活当前聚焦的按钮：0=播放信息 1=倍速 2=画面 3=清晰度。 */
    private void activateChip(int index) {
        if (index == 0) {
            togglePlaybackInfo();
            return;
        }
        if (index == 1) {
            cycleSpeed();
            return;
        }
        if (index == 2) {
            cyclePictureMode();
            return;
        }
        if (index == 3) {
            cycleQuality();
        }
    }

    private LinearLayout.LayoutParams chipParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.leftMargin = dp(10);
        return params;
    }

    private TextView controlText(String text) {
        TextView control = new TextView(context);
        control.setText(text);
        control.setTextColor(Color.WHITE);
        control.setTextSize(14);
        control.setSingleLine(true);
        control.setPadding(0, 0, dp(20), dp(4));
        return control;
    }

    private void seekBy(int deltaMs) {
        // 连续按键时：优先用还没提交的请求，其次用正在结算的目标，最后才用播放器位置
        int base = pendingSeekRequestMs >= 0 ? pendingSeekRequestMs
                : (pendingSeekMs >= 0 ? pendingSeekMs : position());
        int target = Math.max(0, base + deltaMs);
        int total = duration();
        if (total > 0) {
            target = Math.min(total, target);
        }
        requestSeekTo(target);
        int seconds = Math.abs(deltaMs) / 1000;
        showHint(deltaMs > 0 ? "快进 " + seconds + " 秒" : "快退 " + seconds + " 秒");
        showControlsTemporarily();
    }

    private void seekToPercent(int percent) {
        int target = NumberSeekShortcut.targetMs(duration(), percent);
        if (target < 0) {
            showHint("暂时无法跳转");
            showControlsTemporarily();
            return;
        }
        requestSeekTo(target);
        showHint(NumberSeekShortcut.hintForPercent(percent));
        showControlsTemporarily();
    }

    private void requestSeekTo(int targetMs) {
        pendingSeekRequestMs = Math.max(0, targetMs);
        seekFallbackMs = -1;
        seeking = true;
        loadingView.setVisibility(View.VISIBLE);
        int duration = duration();
        if (duration > 0) {
            seekBar.setProgress(toSeekBarProgress(pendingSeekRequestMs, duration));
        }
        updateTimeLabel(pendingSeekRequestMs, duration);
        handler.removeCallbacks(seekCommitRunnable);
        handler.removeCallbacks(seekSettledRunnable);
        handler.postDelayed(seekCommitRunnable, SEEK_COMMIT_DELAY_MS);
    }

    private void commitPendingSeek() {
        if (pendingSeekRequestMs < 0) {
            return;
        }
        int targetMs = pendingSeekRequestMs;
        pendingSeekRequestMs = -1;
        restartRetries = 0;
        if (transcodedPlayback && currentEntry != null
                && Math.abs(targetMs - position()) > 15000) {
            seekOriginMs = Math.max(0, position());
            requestTranscodeRestart(targetMs);
            return;
        }
        try {
            if (currentPlayer != null) {
                currentPlayer.seekTo(targetMs);
            }
            pendingSeekMs = targetMs;
            handler.removeCallbacks(seekSettledRunnable);
            handler.postDelayed(seekSettledRunnable, SEEK_SETTLE_DELAY_MS);
        } catch (RuntimeException ignored) {
            seeking = false;
            if (prepared) {
                loadingView.setVisibility(View.GONE);
            }
            showHint("暂时无法跳转");
        }
    }

    /** 转码流：请求刷新列表并重建播放器（合并连续按键，只处理最新目标）。 */
    private void requestTranscodeRestart(int targetMs) {
        pendingRestartMs = targetMs;
        if (restartInFlight) {
            return;
        }
        restartInFlight = true;
        seeking = true;
        loadingView.setVisibility(View.VISIBLE);
        updateTimeLabel(targetMs, duration());
        handler.removeCallbacks(seekCommitRunnable);
        handler.removeCallbacks(seekSettledRunnable);
        listener.onTranscodeSeekRequested(currentEntry, targetMs);
    }

    /** 新列表准备好：换播放器并跳到目标位置。 */
    public void restartTranscoded(String url, String label, int seekToMs, boolean clamped) {
        restartClamped = clamped;
        if (url == null || url.length() == 0) {
            onTranscodeSeekFailed();
            return;
        }
        playbackSources.clear();
        playbackSources.add(new FnosPlaybackSource(
                label == null || label.length() == 0 ? "转码" : label, url));
        sourceIndex = 0;
        currentUrl = url;
        playbackOptions = buildPlaybackOptions(url);
        pendingSeekMs = -1;
        pendingSeekRequestMs = -1;
        seekFallbackMs = -1;
        restartSeekTargetMs = seekToMs;
        restartSeekRetries = 0;
        prepared = false;
        firstFrameSeen = false;
        retriedPlayback = false;
        bufferingCount = 0;
        videoWidth = 0;
        videoHeight = 0;
        seeking = true;
        releasePlayer(true);
        if (surfaceReady) {
            startPlayer();
        }
        handler.removeCallbacks(prepareTimeoutRunnable);
        handler.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
        updateControlText();
    }

    /** 目标位置服务器还没转出来：不跳转、保持当前位置，避免看起来像「归 0」。 */
    public void onTranscodeSeekUnavailable() {
        restartInFlight = false;
        pendingRestartMs = -1;
        restartSeekTargetMs = -1;
        restartSeekRetries = 0;
        pendingSeekMs = -1;
        seekFallbackMs = -1;
        seeking = false;
        if (prepared) {
            loadingView.setVisibility(View.GONE);
        }
        showHint("该位置还在转码，请稍后再拖");
        updateProgress();
    }

    /** 重建失败/列表还没转到位：恢复界面。 */
    public void onTranscodeSeekFailed() {
        restartInFlight = false;
        pendingRestartMs = -1;
        restartSeekTargetMs = -1;
        restartRetries = 0;
        pendingSeekMs = -1;
        seekFallbackMs = -1;
        seeking = false;
        if (prepared) {
            loadingView.setVisibility(View.GONE);
        }
        showHint("跳转失败，请重试");
        updateProgress();
    }

    /**
     * 新播放器出第一帧后再 seek。
     *
     * <p>VLC 在 onPrepared 里立刻 setTime 有很大概率被丢掉（表现就是跳完归 0），
     * 所以等首帧出来再跳，并隔 0.9 秒校验一次，没到位就补一次。
     */
    private void applyRestartSeek() {
        final int target = restartSeekTargetMs;
        if (target <= 0 || currentPlayer == null) {
            finishTranscodeRestart();
            return;
        }
        try {
            currentPlayer.seekTo(target);
        } catch (RuntimeException ignored) {
        }
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (restartSeekTargetMs != target) {
                    return;
                }
                if (Math.abs(position() - target) > 5000 && restartSeekRetries < 3) {
                    restartSeekRetries++;
                    try {
                        if (currentPlayer != null) {
                            currentPlayer.seekTo(target);
                        }
                    } catch (RuntimeException ignored) {
                    }
                    handler.postDelayed(this, 900);
                    return;
                }
                restartSeekRetries = 0;
                // 延后一点，避免被「首帧」提示盖掉
                handler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        finishTranscodeRestart();
                    }
                }, 1500);
            }
        }, 900);
    }

    /** 新播放器出第一帧后收尾。 */
    private void finishTranscodeRestart() {
        if (!restartInFlight) {
            return;
        }
        restartInFlight = false;
        seeking = false;
        if (prepared) {
            loadingView.setVisibility(View.GONE);
        }
        int landedTarget = restartSeekTargetMs;
        restartSeekTargetMs = -1;
        restartSeekRetries = 0;
        restartRetries = 0;
        int queued = pendingRestartMs;
        pendingRestartMs = -1;
        if (queued >= 0 && Math.abs(position() - queued) > 5000) {
            requestTranscodeRestart(queued);
            return;
        }
        int landed = position();
        if (landedTarget > 0 && landedTarget - landed > 20000) {
            // 服务器还没转到这里：退回原来的位置，别让用户以为归零了
            int origin = seekOriginMs;
            seekOriginMs = -1;
            restartClamped = false;
            showHint("该位置还在转码，请稍后再拖");
            if (origin >= 0 && Math.abs(landed - origin) > 5000) {
                try {
                    if (currentPlayer != null) {
                        currentPlayer.seekTo(origin);
                    }
                } catch (RuntimeException ignored) {
                }
            }
            updateProgress();
            return;
        }
        seekOriginMs = -1;
        if (restartClamped) {
            restartClamped = false;
            showHint("已跳到最远可播位置 " + formatTime(landed));
        }
        updateProgress();
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEEDS.length;
        float speed = SPEEDS[speedIndex];
        if (currentPlayer instanceof SystemPlayerEngine && Math.abs(speed - 1.0f) > 0.01f) {
            // 系统播放器（4.4 无 PlaybackParams）不支持倍速：本片改用 VLC 后按目标倍速起播
            forceVlc = true;
            int savedPosition = position();
            pendingSeekMs = savedPosition > 0 ? savedPosition : pendingSeekMs;
            restartPlayback("倍速播放，改用 VLC");
            updateControlText();
            showControlsTemporarily();
            return;
        }
        if (applyPlaybackSpeed(speed)) {
            showHint("倍速 " + formatSpeed(speed));
        } else {
            speedIndex = 0;
            showHint("当前播放器不支持倍速播放");
        }
        updateControlText();
        showControlsTemporarily();
    }

    private boolean applyPlaybackSpeed(float speed) {
        if (currentPlayer == null) {
            return speed == 1.0f;
        }
        return currentPlayer.setSpeed(speed);
    }

    private void cyclePictureMode() {
        pictureMode = pictureMode == MODE_FILL ? MODE_FIT : MODE_FILL;
        videoView.setResizeMode(pictureMode);
        if (currentPlayer != null) {
            currentPlayer.setFillMode(pictureMode == MODE_FILL);
            currentPlayer.resizeSurface(videoView.getWidth(), videoView.getHeight());
        }
        updateControlText();
        showHint(pictureMode == MODE_FILL ? "画面铺满" : "画面适应");
        showControlsTemporarily();
    }

    /** 重建选集卡片：16:9 剧照 + 第 N 集标题 + 两行简介 + 时长（同网页视频卡片）。 */
    private void rebuildPlaylist() {
        if (playlistRow == null) {
            return;
        }
        try {
            rebuildPlaylistInternal();
        } catch (RuntimeException ex) {
            Logger.w("playlist render failed: " + ex);
        }
    }

    private void rebuildPlaylistInternal() {
        playlistRow.removeAllViews();
        thumbRefs.clear();
        final int cardWidth = dp(200);
        final int thumbHeight = Math.round(cardWidth * 9f / 16f);
        for (int i = 0; i < playlist.size(); i++) {
            final FnosFileEntry entry = playlist.get(i);
            final boolean current = entry.path.equals(playlistCurrentPath);

            LinearLayout card = new LinearLayout(context);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setFocusable(true);
            card.setClickable(true);
            card.setPadding(dp(5), dp(5), dp(5), dp(8));
            card.setBackgroundDrawable(playlistCardBackground(false));

            RoundedFrameLayout thumb = new RoundedFrameLayout(context, 8);
            thumb.setBackgroundDrawable(FnosTheme.rounded(0xFF1C2129, 8, context));
            ImageView image = new ImageView(context);
            image.setScaleType(ImageView.ScaleType.CENTER_CROP);
            thumb.addView(image, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            TextView placeholder = new TextView(context);
            placeholder.setTextSize(13);
            placeholder.setTextColor(0x66FFFFFF);
            placeholder.setGravity(Gravity.CENTER);
            thumb.addView(placeholder, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            final ThumbRef thumbRef = new ThumbRef(entry, thumb, image, placeholder);
            thumbRefs.add(thumbRef);

            String resolution = entry.resolution == null ? "" : entry.resolution.trim();
            if (resolution.length() > 0) {
                TextView badge = new TextView(context);
                String label = resolution.toUpperCase(java.util.Locale.US);
                if (label.endsWith("P") && label.length() > 2) {
                    label = label.substring(0, label.length() - 1);
                }
                badge.setText(label);
                badge.setTextColor(Color.WHITE);
                badge.setTextSize(11);
                badge.setBackgroundDrawable(FnosTheme.rounded(0x66000000, 4, context));
                badge.setPadding(dp(5), dp(1), dp(5), dp(1));
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.BOTTOM | Gravity.RIGHT);
                badgeParams.setMargins(0, 0, dp(6), dp(6));
                thumb.addView(badge, badgeParams);
            }

            card.addView(thumb, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, thumbHeight));

            TextView title = new TextView(context);
            title.setText("第 " + Math.max(0, entry.episodeNumber()) + " 集. "
                    + (entry.name == null ? "" : entry.name));
            title.setTextColor(current ? 0xFF3D8BFF : Color.WHITE);
            title.setTextSize(15);
            title.setSingleLine(true);
            title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            titleParams.topMargin = dp(7);
            card.addView(title, titleParams);

            TextView overview = new TextView(context);
            overview.setText(entry.overview == null ? "" : entry.overview);
            overview.setTextColor(0x99FFFFFF);
            overview.setTextSize(12);
            overview.setMaxLines(2);
            overview.setEllipsize(android.text.TextUtils.TruncateAt.END);
            overview.setLineSpacing(dp(2), 1f);
            LinearLayout.LayoutParams overviewParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            overviewParams.topMargin = dp(3);
            card.addView(overview, overviewParams);

            TextView duration = new TextView(context);
            String length = entry.durationLabel();
            duration.setText(length);
            duration.setVisibility(length.length() > 0 ? View.VISIBLE : View.GONE);
            duration.setTextColor(0x66FFFFFF);
            duration.setTextSize(11);
            LinearLayout.LayoutParams durationParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            durationParams.topMargin = dp(4);
            card.addView(duration, durationParams);

            card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    v.setBackgroundDrawable(playlistCardBackground(hasFocus));
                    if (hasFocus) {
                        loadThumb(thumbRef);
                        loadThumbNear(thumbRef);
                    }
                }
            });
            card.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closePlaylist();
                    listener.onPlaylistItemSelected(entry);
                }
            });

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(cardWidth,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            params.leftMargin = i == 0 ? 0 : dp(12);
            playlistRow.addView(card, params);
        }
    }

    private void loadThumb(ThumbRef ref) {
        if (ref == null || ref.loaded) {
            return;
        }
        String path = ref.entry.posterPath;
        if (path == null || path.length() == 0) {
            // 该集没有自己的剧照：退回列表里第一张可用剧照
            path = playlistFallbackPoster;
        }
        if (path == null || path.length() == 0) {
            return;
        }
        ref.loaded = true;
        posterLoader.loadPath(playlistPosterBaseUrl, path, 240, 140,
                ref.frame, ref.image, ref.placeholder);
    }

    /** 顺带把前后各一张也加载出来，滑动时不会一片空白。 */
    private void loadThumbNear(ThumbRef ref) {
        int index = thumbRefs.indexOf(ref);
        if (index >= 0) {
            if (index > 0) {
                loadThumb(thumbRefs.get(index - 1));
            }
            if (index + 1 < thumbRefs.size()) {
                loadThumb(thumbRefs.get(index + 1));
            }
        }
    }

    /** 只加载当前横向可视区域内的卡片（滑动时实时补图）。 */
    private void loadVisibleThumbs() {
        if (!playlistOpen || playlistScroll == null || playlistRow == null) {
            return;
        }
        int scrollX = playlistScroll.getScrollX();
        int width = playlistScroll.getWidth();
        if (width <= 0) {
            return;
        }
        int count = Math.min(thumbRefs.size(), playlistRow.getChildCount());
        for (int i = 0; i < count; i++) {
            View card = playlistRow.getChildAt(i);
            if (card == null) {
                continue;
            }
            if (card.getRight() >= scrollX - dp(24) && card.getLeft() <= scrollX + width + dp(24)) {
                loadThumb(thumbRefs.get(i));
            }
        }
    }

    /** 打开面板后分批把整季的图都补上（每批 3 张），避免一次性压垮 4.4 电视盒。 */
    private final Runnable thumbPreloadRunnable = new Runnable() {
        @Override
        public void run() {
            if (!playlistOpen) {
                return;
            }
            int loaded = 0;
            while (thumbPreloadIndex < thumbRefs.size() && loaded < 3) {
                loadThumb(thumbRefs.get(thumbPreloadIndex));
                thumbPreloadIndex++;
                loaded++;
            }
            if (thumbPreloadIndex < thumbRefs.size()) {
                handler.postDelayed(this, 150);
            }
        }
    };

    private final Runnable loadVisibleThumbsRunnable = new Runnable() {
        @Override
        public void run() {
            loadVisibleThumbs();
        }
    };

    private void startThumbPreload() {
        handler.removeCallbacks(thumbPreloadRunnable);
        handler.removeCallbacks(loadVisibleThumbsRunnable);
        thumbPreloadIndex = 0;
        handler.postDelayed(loadVisibleThumbsRunnable, 60);
        handler.postDelayed(thumbPreloadRunnable, 400);
    }

    private void stopThumbPreload() {
        handler.removeCallbacks(thumbPreloadRunnable);
        handler.removeCallbacks(loadVisibleThumbsRunnable);
    }

    private android.graphics.drawable.Drawable playlistCardBackground(boolean focused) {
        android.graphics.drawable.GradientDrawable drawable = new android.graphics.drawable.GradientDrawable();
        drawable.setColor(focused ? 0x14FFFFFF : Color.TRANSPARENT);
        drawable.setCornerRadius(dp(10));
        if (focused) {
            drawable.setStroke(dp(2), 0xFF3D8BFF);
        }
        return drawable;
    }

    private void openPlaylist() {
        if (playlist.size() == 0 || playlistPanel == null || playlistRow == null) {
            return;
        }
        rebuildPlaylist();
        playlistOpen = true;
        playlistPanel.setVisibility(View.VISIBLE);
        showControlsTemporarily();
        // 焦点落到当前集（HorizontalScrollView 会自动滚到它）
        int focusIndex = 0;
        for (int i = 0; i < playlist.size(); i++) {
            if (playlist.get(i).path.equals(playlistCurrentPath)) {
                focusIndex = i;
                break;
            }
        }
        // 只预载当前集附近几张，其余滑到再加载
        for (int i = Math.max(0, focusIndex - 2); i < Math.min(thumbRefs.size(), focusIndex + 3); i++) {
            loadThumb(thumbRefs.get(i));
        }
        if (playlistRow.getChildCount() > focusIndex) {
            playlistRow.getChildAt(focusIndex).requestFocus();
        }
        startThumbPreload();
    }

    private void closePlaylist() {
        playlistOpen = false;
        stopThumbPreload();
        if (playlistPanel != null) {
            playlistPanel.setVisibility(View.GONE);
        }
        if (view != null) {
            view.requestFocus();
        }
        showControlsTemporarily();
    }

    public boolean isPlaylistOpen() {
        return playlistOpen;
    }

    private void cycleQuality() {
        if (playbackSources.size() <= 1) {
            showHint("当前视频没有多码率播放源");
            showControlsTemporarily();
            return;
        }
        int savedPosition = position();
        sourceIndex = (sourceIndex + 1) % playbackSources.size();
        currentUrl = currentSource().url;
        playbackOptions = buildPlaybackOptions(currentUrl);
        pendingSeekMs = savedPosition;
        pendingSeekRequestMs = -1;
        seeking = false;
        handler.removeCallbacks(seekCommitRunnable);
        handler.removeCallbacks(seekSettledRunnable);
        prepared = false;
        firstFrameSeen = false;
        retriedPlayback = false;
        preferHardwareCodec = currentEntry != null && currentEntry.prefersHardwarePlayback() && hardwareDecoderAllowed();
        videoWidth = 0;
        videoHeight = 0;
        loadingView.setVisibility(View.VISIBLE);
        showHint("切换清晰度 " + currentSource().label);
        releasePlayer(true);
        if (surfaceReady) {
            startPlayer();
        }
        handler.removeCallbacks(prepareTimeoutRunnable);
        handler.postDelayed(prepareTimeoutRunnable, PREPARE_TIMEOUT_MS);
        updateControlText();
        showControlsTemporarily();
    }

    private void updateProgress() {
        if (!isVisible() || dragging || seeking) {
            return;
        }
        int duration = duration();
        int position = position();
        if (duration > 0) {
            seekBar.setProgress(toSeekBarProgress(position, duration));
        }
        updateTimeLabel(position, duration);
        updateControlText();
    }

    private void updateTimeLabel(int position, int duration) {
        timeView.setText(formatTime(position) + " / " + formatTime(duration));
    }

    private void updateControlText() {
        boolean playing = currentPlayer != null && currentPlayer.isPlaying();
        playStateView.setPlaying(playing);
        if (engineView != null) {
            engineView.setText(engineChipLabel());
        }
        speedView.setText("倍速 " + formatSpeed(SPEEDS[speedIndex]));
        pictureView.setText(pictureMode == MODE_FILL ? "画面 铺满" : "画面 适应");
        String label = currentSource().label;
        if (videoWidth > 0 && videoHeight > 0) {
            label += " " + videoWidth + "x" + videoHeight;
        }
        resolutionView.setText("清晰度 " + label);
        if (statusView != null) {
            statusView.setText(playbackStatusLabel());
        }
    }

    /** 底部「播放类型」按钮上的短标签。 */
    private String engineChipLabel() {
        if (transcodedPlayback) {
            return "转码 · HLS";
        }
        if (currentPlayer instanceof SystemPlayerEngine) {
            return "硬解 · 系统";
        }
        if (currentPlayer == null) {
            return "播放 · --";
        }
        return preferHardwareCodec ? "硬解 · VLC" : "软解 · VLC";
    }

    /** 播放类型（中文，给播放信息面板用）。 */
    private String engineTypeText() {
        if (currentPlayer == null) {
            return "未开始";
        }
        if (currentPlayer instanceof SystemPlayerEngine) {
            return "系统播放器（电视芯片）";
        }
        return "VLC 播放器";
    }

    /** 解码方式（中文）。 */
    private String decodeModeText() {
        if (transcodedPlayback) {
            return "服务器转码（1080P H.264）";
        }
        if (currentPlayer instanceof SystemPlayerEngine) {
            return "硬件解码（MTK 硬解）";
        }
        if (currentPlayer == null) {
            return "未知";
        }
        return preferHardwareCodec ? "硬件解码（VLC 硬解）" : "软件解码（CPU 软解）";
    }

    private String playStateText() {
        if (currentPlayer == null) {
            return "未开始";
        }
        if (!prepared) {
            return "准备中";
        }
        if (currentPlayer.isPlaying()) {
            return "播放中";
        }
        return "已暂停";
    }

    private void togglePlaybackInfo() {
        if (infoOpen) {
            hidePlaybackInfo();
        } else {
            showPlaybackInfo();
        }
    }

    private void showPlaybackInfo() {
        if (infoPanel == null) {
            return;
        }
        infoOpen = true;
        rebuildInfoPanel();
        infoPanel.setVisibility(View.VISIBLE);
        showControls();
        handler.removeCallbacks(hideInfoRunnable);
        handler.postDelayed(hideInfoRunnable, 15000);
    }

    private void hidePlaybackInfo() {
        infoOpen = false;
        handler.removeCallbacks(hideInfoRunnable);
        if (infoPanel != null) {
            infoPanel.setVisibility(View.GONE);
        }
    }

    private void rebuildInfoPanel() {
        infoPanel.removeAllViews();
        TextView title = new TextView(context);
        title.setText("播放信息");
        title.setTextColor(Color.WHITE);
        title.setTextSize(16);
        title.setPadding(0, 0, 0, dp(6));
        infoPanel.addView(title);

        addInfoRow("播放类型", engineTypeText());
        addInfoRow("解码方式", decodeModeText());
        addInfoRow("片源", currentSource().label
                + (transcodedPlayback ? "（服务器转码）" : "（原画直连）"));
        addInfoRow("分辨率", resolutionText());
        String format = formatLabel();
        if ("video".equalsIgnoreCase(format) || format.length() == 0) {
            format = transcodedPlayback ? "HLS 转码流" : "HTTP 原画直连";
        }
        addInfoRow("格式 / 通道", format);
        addInfoRow("倍速", formatSpeed(SPEEDS[speedIndex]));
        addInfoRow("画面", pictureMode == MODE_FILL ? "铺满" : "适应");
        addInfoRow("进度", formatTime(position()) + " / " + formatTime(duration()));
        addInfoRow("状态", playStateText());
        if (currentPlayer instanceof SystemPlayerEngine || playbackOptions == null) {
            addInfoRow("缓冲", "由电视系统控制");
        } else {
            addInfoRow("缓冲", playbackOptions.cacheLabel()
                    + " " + playbackOptions.networkCachingMs + "ms");
        }
    }

    /** 分辨率文案：VLC/HLS 有时拿不到宽高，退回片源标签（如 1080P 转码）。 */
    private String resolutionText() {
        if (videoWidth > 0 && videoHeight > 0) {
            return videoWidth + " × " + videoHeight;
        }
        String label = currentSource().label;
        if (label != null && label.length() > 0) {
            return label.replace(" 转码", "");
        }
        return "未知";
    }

    private void addInfoRow(String label, String value) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(3), 0, dp(3));

        TextView key = new TextView(context);
        key.setText(label);
        key.setTextColor(0xFF8FA0B5);
        key.setTextSize(13);
        key.setSingleLine(true);
        row.addView(key, new LinearLayout.LayoutParams(dp(84),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView text = new TextView(context);
        text.setText(value == null ? "" : value);
        text.setTextColor(0xFFE6EAF0);
        text.setTextSize(13);
        text.setSingleLine(false);
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        infoPanel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private String playbackStatusLabel() {        String engine = currentPlayer == null ? "--" : currentPlayer.name();
        if (currentPlayer instanceof SystemPlayerEngine) {
            // 系统播放器没有 VLC 那些解码/缓存参数，显示硬解信息即可
            return "内核 " + engine + " · MTK 硬解 · 原画直连";
        }
        if (playbackOptions == null) {
            return "内核 " + engine;
        }
        return "内核 " + engine
                + " · " + playbackOptions.decoderLabel()
                + " · " + playbackOptions.profileLabel()
                + " · " + playbackOptions.cacheLabel()
                + " " + playbackOptions.networkCachingMs + "ms";
    }

    private int duration() {
        // 同一部片子一旦拿到过总时长就沿用：重新起流后的 HLS 时长是「剩余时长」，
        // 直接用会把进度条缩短。
        if (lastKnownDurationMs > 0) {
            return lastKnownDurationMs;
        }
        int value = 0;
        try {
            value = currentPlayer == null ? 0 : (int) Math.max(0, currentPlayer.getDuration());
        } catch (RuntimeException ignored) {
        }
        if (value > 0) {
            lastKnownDurationMs = value;
        }
        return lastKnownDurationMs;
    }

    private int position() {
        int real = 0;
        try {
            real = currentPlayer == null ? 0 : (int) Math.max(0, currentPlayer.getCurrentPosition());
        } catch (RuntimeException ignored) {
        }
        if (seekFallbackMs >= 0) {
            long since = SystemClock.elapsedRealtime() - seekFallbackAtMs;
            if (since > 8000L) {
                // 兜底最多撑 20 秒：再对不上就以播放器为准，避免进度条越跑越偏
                seekFallbackMs = -1;
                return real;
            }
            int elapsed = 0;
            try {
                if (currentPlayer != null && currentPlayer.isPlaying()) {
                    elapsed = (int) Math.min(Integer.MAX_VALUE, since);
                }
            } catch (RuntimeException ignored) {
            }
            int estimated = seekFallbackMs + Math.max(0, elapsed);
            if (lastKnownDurationMs > 0) {
                estimated = Math.min(lastKnownDurationMs, estimated);
            }
            // 播放器时间和兜底估算接近 → 说明已经追上，交回播放器
            if (real > 0 && Math.abs(real - estimated) <= 15000) {
                seekFallbackMs = -1;
                return real;
            }
            return estimated;
        }
        return real;
    }

    private int progressToPosition(int progress) {
        int duration = duration();
        return duration <= 0 ? 0 : (int) ((long) duration * Math.max(0, Math.min(1000, progress)) / 1000L);
    }

    private int toSeekBarProgress(int position, int duration) {
        return duration <= 0 ? 0 : (int) Math.max(0L, Math.min(1000L, (long) position * 1000L / (long) duration));
    }

    private String formatTime(int ms) {
        int totalSeconds = Math.max(0, ms / 1000);
        int seconds = totalSeconds % 60;
        int minutes = totalSeconds / 60 % 60;
        int hours = totalSeconds / 3600;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private String formatSpeed(float speed) {
        if (Math.abs(speed - Math.round(speed)) < 0.01f) {
            return String.format(Locale.US, "%.1fx", speed);
        }
        return String.format(Locale.US, "%.2fx", speed);
    }

    private void showControls() {
        if (menuMode) {
            // 直播菜单模式下由菜单负责 UI，播放器不再显示自己的控制条
            controlBar.setVisibility(View.GONE);
            if (topBarView != null) {
                topBarView.setVisibility(View.GONE);
            }
            titleView.setVisibility(View.GONE);
            return;
        }
        controlBar.setVisibility(View.VISIBLE);
        if (topBarView != null) {
            topBarView.setVisibility(View.VISIBLE);
        }
        titleView.setVisibility(View.VISIBLE);
    }

    /** 直播菜单模式：播放器只出画面，控制条/标题/提示全部隐藏。 */
    public void setMenuMode(boolean enabled) {
        menuMode = enabled;
        if (enabled) {
            controlBar.setVisibility(View.GONE);
            if (topBarView != null) {
                topBarView.setVisibility(View.GONE);
            }
            titleView.setVisibility(View.GONE);
            loadingView.setVisibility(View.GONE);
            if (hintView != null) {
                hintView.setVisibility(View.GONE);
            }
            hidePlaybackInfo();
        }
    }

    private void hideControls() {
        if (chipFocusIndex >= 0) {
            // 工具栏正在用遥控器操作，别自动收起
            return;
        }
        if (isVisible() && !seeking && currentPlayer != null && currentPlayer.isPlaying()) {
            clearChipFocus();
            controlBar.setVisibility(View.GONE);
            if (topBarView != null) {
                topBarView.setVisibility(View.GONE);
            }
            titleView.setVisibility(View.GONE);
        }
    }

    private void showControlsTemporarily() {
        showControls();
        handler.removeCallbacks(hideControlsRunnable);
        handler.postDelayed(hideControlsRunnable, CONTROL_HIDE_DELAY_MS);
    }

    private void showHint(String message) {
        if (hintView == null) {
            return;
        }
        if (message == null || message.length() == 0) {
            hintView.setVisibility(View.GONE);
            return;
        }
        hintView.setText(message);
        hintView.setVisibility(View.VISIBLE);
        handler.removeCallbacks(hideHintRunnable);
        handler.postDelayed(hideHintRunnable, 1500);
    }

    /** 对外暴露（MainActivity 自动连播时使用）。 */
    public void showHintPublic(String message) {
        showHint(message);
    }

    private void enterImmersiveMode() {
        if (!(context instanceof android.app.Activity)) {
            return;
        }
        Window window = ((android.app.Activity) context).getWindow();
        window.setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (Build.VERSION.SDK_INT >= 19) {
            view.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    /** 选集横向列表：滑动时通知外面补加载可见卡片的缩略图。 */
    private static final class PlaylistScrollView extends HorizontalScrollView {
        private Runnable onScrollListener;

        PlaylistScrollView(Context context) {
            super(context);
        }

        void setOnScrollListener(Runnable listener) {
            this.onScrollListener = listener;
        }

        @Override
        protected void onScrollChanged(int l, int t, int oldl, int oldt) {
            super.onScrollChanged(l, t, oldl, oldt);
            if (onScrollListener != null) {
                onScrollListener.run();
            }
        }
    }

    private static final class PlaybackSurfaceView extends SurfaceView {        private int resizeMode = MODE_FILL;
        private int videoWidth;
        private int videoHeight;

        PlaybackSurfaceView(Context context) {
            super(context);
        }

        void setResizeMode(int resizeMode) {
            this.resizeMode = resizeMode;
            requestLayout();
        }

        void setVideoSize(int videoWidth, int videoHeight) {
            this.videoWidth = videoWidth;
            this.videoHeight = videoHeight;
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);
            setMeasuredDimension(width, height);
        }
    }
}
