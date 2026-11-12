package com.fnostv.android4.player;

import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.view.SurfaceHolder;

/**
 * 系统播放器（MediaPlayer → MTK CmpbPlayer 硬解 + 视频层）。
 *
 * <p>实测（AQUOS 60UD30A / Android 4.4.3）：4K HEVC MKV 与 4K H.264 MP4 都能实时解码出画，
 * 画面由 MTK 视频层直接显示（adb screencap 抓不到视频内容，只能看到 UI，属正常现象）。
 *
 * <p>局限：不支持 HLS 鉴权分片、不支持外挂字幕、不支持倍速（4.4 没有 PlaybackParams），
 * 所以只用于「原画直连」，转码流和 VLC 专属容器仍然回落到 VLC。
 */
public final class SystemPlayerEngine implements PlayerEngine {
    private static final int MEDIA_INFO_VIDEO_RENDERING_START = 3;
    private static final int MEDIA_INFO_BUFFERING_START = 701;
    private static final int MEDIA_INFO_BUFFERING_END = 702;

    private final Context context;
    private Listener listener;
    private MediaPlayer player;
    private SurfaceHolder holder;
    private boolean prepared;
    private boolean released;

    public SystemPlayerEngine(Context context) {
        this.context = context;
    }

    @Override
    public String name() {
        return "系统硬解";
    }

    @Override
    public void setListener(Listener listener) {
        this.listener = listener;
    }

    @Override
    public void attachSurface(SurfaceHolder holder, int width, int height) {
        this.holder = holder;
        if (player != null) {
            try {
                player.setDisplay(holder);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void resizeSurface(int width, int height) {
        // SurfaceView 自己负责尺寸，MediaPlayer 会跟着缩放
    }

    @Override
    public void setFillMode(boolean fillMode) {
        if (player == null) {
            return;
        }
        try {
            player.setVideoScalingMode(fillMode
                    ? MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                    : MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT);
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public void detachSurface() {
        if (player != null) {
            try {
                player.setDisplay(null);
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void prepare(String url, PlaybackOptions options) throws Exception {
        released = false;
        prepared = false;
        final MediaPlayer mp = new MediaPlayer();
        player = mp;
        mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
        if (holder != null) {
            mp.setDisplay(holder);
        }
        mp.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
            @Override
            public void onPrepared(MediaPlayer mediaPlayer) {
                prepared = true;
                setFillMode(true);
                if (listener != null) {
                    listener.onPrepared(mediaPlayer.getVideoWidth(), mediaPlayer.getVideoHeight(),
                            Math.max(0, mediaPlayer.getDuration()));
                    listener.onLog("系统播放器就绪 " + mediaPlayer.getVideoWidth() + "x"
                            + mediaPlayer.getVideoHeight());
                }
            }
        });
        mp.setOnVideoSizeChangedListener(new MediaPlayer.OnVideoSizeChangedListener() {
            @Override
            public void onVideoSizeChanged(MediaPlayer mediaPlayer, int width, int height) {
                if (listener != null) {
                    listener.onVideoSizeChanged(width, height);
                }
            }
        });
        mp.setOnInfoListener(new MediaPlayer.OnInfoListener() {
            @Override
            public boolean onInfo(MediaPlayer mediaPlayer, int what, int extra) {
                if (listener == null) {
                    return false;
                }
                if (what == MEDIA_INFO_VIDEO_RENDERING_START) {
                    listener.onFirstFrame();
                } else if (what == MEDIA_INFO_BUFFERING_START) {
                    listener.onBufferingStart(extra);
                } else if (what == MEDIA_INFO_BUFFERING_END) {
                    listener.onBufferingEnd(extra);
                }
                return false;
            }
        });
        mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
            @Override
            public void onCompletion(MediaPlayer mediaPlayer) {
                if (listener != null) {
                    listener.onCompletion();
                }
            }
        });
        mp.setOnErrorListener(new MediaPlayer.OnErrorListener() {
            @Override
            public boolean onError(MediaPlayer mediaPlayer, int what, int extra) {
                if (listener != null) {
                    listener.onError("系统播放器错误 " + what + "/" + extra);
                }
                return true;
            }
        });
        mp.setDataSource(url);
        mp.prepareAsync();
    }

    @Override
    public void start() {
        if (player != null) {
            try {
                player.start();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void pause() {
        if (player != null && prepared) {
            try {
                player.pause();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void stop() {
        if (player != null && prepared) {
            try {
                player.stop();
            } catch (RuntimeException ignored) {
            }
        }
    }

    @Override
    public void seekTo(int positionMs) {
        if (player != null && prepared) {
            try {
                player.seekTo(Math.max(0, positionMs));
            } catch (RuntimeException ignored) {
            }
        }
    }

    /** Android 4.4 没有 PlaybackParams，系统播放器只能 1.0 倍速。 */
    @Override
    public boolean setSpeed(float speed) {
        return Math.abs(speed - 1.0f) < 0.01f;
    }

    @Override
    public boolean isPlaying() {
        if (player == null) {
            return false;
        }
        try {
            return player.isPlaying();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    @Override
    public int getDuration() {
        if (player == null || !prepared) {
            return 0;
        }
        try {
            return Math.max(0, player.getDuration());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    @Override
    public int getCurrentPosition() {
        if (player == null || !prepared) {
            return 0;
        }
        try {
            return Math.max(0, player.getCurrentPosition());
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    @Override
    public void release() {
        released = true;
        prepared = false;
        if (player != null) {
            try {
                player.reset();
            } catch (RuntimeException ignored) {
            }
            try {
                player.release();
            } catch (RuntimeException ignored) {
            }
            player = null;
        }
    }
}
