package com.fnostv.android4.player;

import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.view.SurfaceHolder;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.IVLCVout;


public final class VlcPlayerEngine implements PlayerEngine {
    private final Context context;
    private LibVLC libVlc;
    private MediaPlayer player;
    private Listener listener;
    private SurfaceHolder surfaceHolder;
    private int surfaceWidth;
    private int surfaceHeight;
    private boolean prepared;
    private boolean fillMode;

    public VlcPlayerEngine(Context context) {
        this.context = context.getApplicationContext();
        try {
            libVlc = new LibVLC(this.context);
        } catch (Throwable ex) {
            libVlc = null;
        }
    }

    @Override
    public String name() {
        return "VLC";
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void attachSurface(SurfaceHolder holder, int width, int height) {
        surfaceHolder = holder;
        surfaceWidth = Math.max(0, width);
        surfaceHeight = Math.max(0, height);
        if (player != null && holder != null) {
            IVLCVout out = player.getVLCVout();
            out.detachViews();
            if (surfaceWidth > 0 && surfaceHeight > 0) {
                out.setWindowSize(surfaceWidth, surfaceHeight);
            }
            out.setVideoSurface(holder.getSurface(), holder);
            out.attachViews();
            applyDisplayMode();
        }
    }

    @Override
    public void resizeSurface(int width, int height) {
        surfaceWidth = Math.max(0, width);
        surfaceHeight = Math.max(0, height);
        if (player != null && surfaceWidth > 0 && surfaceHeight > 0) {
            player.getVLCVout().setWindowSize(surfaceWidth, surfaceHeight);
            applyDisplayMode();
        }
    }

    @Override
    public void setFillMode(boolean fillMode) {
        this.fillMode = fillMode;
        applyDisplayMode();
    }

    @Override
    public void detachSurface() {
        surfaceHolder = null;
        if (player != null) {
            player.getVLCVout().detachViews();
        }
    }

    @Override
    public void prepare(String url, PlaybackOptions playbackOptions) {
        PlaybackOptions playback = playbackOptions == null ? PlaybackOptions.forUrl(url, false) : playbackOptions;
        if (libVlc == null) {
            try {
                libVlc = new LibVLC(context);
            } catch (Throwable ignored) {
                throw new RuntimeException("VLC init failed");
            }
        }
        if (player == null) {
            try {
                player = new MediaPlayer(libVlc);
            } catch (Throwable ignored) {
                player = null;
            }
        } else {
            try {
                player.release();
            } catch (Throwable ignored) {
            }
            try {
                player = new MediaPlayer(libVlc);
            } catch (Throwable ignored) {
                player = null;
            }
        }
        if (player == null) {
            throw new RuntimeException("VLC player init failed");
        }
        // Android 4.4 的 MediaCodec/OMX 有兼容性缺陷，直接用软解；更高版本才允许硬解。
        boolean hardwareDecoder = playback.useHardwareDecoder()
                && Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT;
        // Android 4.4 避免直通音频导致音频模块失败：固定 PCM 立体声输出。
        try {
            player.setAudioOutput("android_audiotrack");
            player.setAudioOutputDevice("stereo");
            player.setAudioDigitalOutputEnabled(false);
        } catch (Throwable ignored) {
        }
        player.setEventListener(new MediaPlayer.EventListener() {
            @Override
            public void onEvent(MediaPlayer.Event event) {
                handleEvent(event);
            }
        });
        if (surfaceHolder != null) {
            attachSurface(surfaceHolder, surfaceWidth, surfaceHeight);
        }
        try {
            Media media = new Media(libVlc, Uri.parse(url));
            try {
                media.setHWDecoderEnabled(hardwareDecoder, false);
            } catch (Throwable ignored) {
            }
            // HLS 分片 4 秒，给足缓冲；3 秒在 WiFi 抖动时就是「一卡一卡」
            media.addOption(":network-caching=" + Math.min(playback.networkCachingMs, 6000));
            media.addOption(":file-caching=" + Math.min(playback.fileCachingMs, 2000));
            media.addOption(":live-caching=" + Math.max(500, Math.min(playback.networkCachingMs, 2000) / 2));
            media.addOption(":clock-jitter=0");
            media.addOption(":clock-synchro=0");
            // 只有「流畅模式」才牺牲画质（跳过去块滤波 + 不丢帧）。
            // 默认模式必须保留去块滤波，否则高码率 H.264 会一块一块的（用户反馈的画面乱码）。
            if (playback.profileMode == PlaybackOptions.PROFILE_FLUENT) {
                media.addOption(":no-drop-late-frames");
                media.addOption(":no-skip-frames");
                media.addOption(":avcodec-skiploopfilter=48");
            }
            media.addOption(hardwareDecoder ? ":codec=mediacodec,iomx,all" : ":codec=avcodec");
            media.addOption(":http-reconnect");
            media.addOption(":aout=android_audiotrack");
            player.setMedia(media);
            try {
                media.release();
            } catch (Throwable ignored) {
            }
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
        try {
            player.play();
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void start() {
        if (player != null) {
            player.play();
        }
    }

    @Override
    public void pause() {
        if (player != null) {
            player.pause();
        }
    }

    @Override
    public void stop() {
        if (player != null) {
            player.stop();
        }
    }

    @Override
    public void seekTo(int positionMs) {
        if (player != null) {
            player.setTime(Math.max(0, positionMs));
        }
    }

    @Override
    public boolean setSpeed(float speed) {
        if (player == null) {
            return speed == 1.0f;
        }
        try {
            player.setRate(speed);
            return true;
        } catch (RuntimeException ex) {
            return speed == 1.0f;
        }
    }

    @Override
    public boolean isPlaying() {
        try {
            return player != null && player.isPlaying();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public int getDuration() {
        try {
            return player == null ? 0 : (int) Math.max(0, player.getLength());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    @Override
    public int getCurrentPosition() {
        try {
            return player == null ? 0 : (int) Math.max(0, player.getTime());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    @Override
    public void release() {
        if (player != null) {
            try {
                player.getVLCVout().detachViews();
                player.stop();
            } catch (RuntimeException ignored) {
            }
            player.release();
            player = null;
        }
        if (libVlc != null) {
            libVlc.release();
            libVlc = null;
        }
        prepared = false;
    }

    private void handleEvent(MediaPlayer.Event event) {
        if (listener == null || event == null) {
            return;
        }
        if (event.type == MediaPlayer.Event.Opening) {
            listener.onBufferingStart(0);
            return;
        }
        if (event.type == MediaPlayer.Event.Buffering) {
            return;
        }
        if (event.type == MediaPlayer.Event.Playing) {
            logAudioState();
            if (!prepared) {
                prepared = true;
                listener.onPrepared(0, 0, getDuration());
            } else {
                listener.onBufferingEnd(0);
            }
            listener.onFirstFrame();
            return;
        }
        if (event.type == MediaPlayer.Event.Paused || event.type == MediaPlayer.Event.Stopped) {
            listener.onLog("event type=" + event.type);
            return;
        }
        if (event.type == MediaPlayer.Event.EncounteredError) {
            listener.onError("VLC 播放器错误");
            return;
        }
        if (event.type == MediaPlayer.Event.EndReached) {
            // 片尾自然结束：用于剧集/同目录多视频自动连播。
            listener.onCompletion();
            return;
        }
        if (event.type == MediaPlayer.Event.TimeChanged
                || event.type == MediaPlayer.Event.PositionChanged) {
            // libvlc 播放期间每 ~250ms 一次的进度心跳（267/268）。
            // 属于正常事件，不要打日志（否则日志被刷屏，也看不出真正的问题）。
            return;
        }
        listener.onLog("event type=" + event.type);
    }

    /** 播放开始时记录音轨信息，便于排查"有画面没声音"。 */
    private void logAudioState() {
        if (player == null || listener == null) {
            return;
        }
        try {
            MediaPlayer.TrackDescription[] tracks = player.getAudioTracks();
            StringBuilder text = new StringBuilder("audio tracks=");
            text.append(tracks == null ? 0 : tracks.length);
            text.append(" passthrough=").append(player.canDoPassthrough());
            if (tracks != null) {
                for (MediaPlayer.TrackDescription track : tracks) {
                    if (track == null || track.id < 0) {
                        continue;
                    }
                    text.append(" [").append(track.id).append(']').append(track.name);
                }
            }
            listener.onLog(text.toString());
        } catch (RuntimeException ignored) {
        }
    }

    private void applyDisplayMode() {
        if (player == null) {
            return;
        }
        if (fillMode && surfaceWidth > 0 && surfaceHeight > 0) {
            player.setAspectRatio(surfaceWidth + ":" + surfaceHeight);
        } else {
            player.setAspectRatio(null);
        }
        player.setScale(0);
    }
}
