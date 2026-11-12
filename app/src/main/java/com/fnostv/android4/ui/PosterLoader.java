package com.fnostv.android4.ui;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.util.LruCache;
import android.view.View;
import android.webkit.CookieManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.fnostv.android4.net.FnosFileEntry;
import com.fnostv.android4.net.FnosRestClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

final class PosterLoader {
    private static final int MAX_DECODE_WIDTH = 720;
    private static final int MAX_DECODE_HEIGHT = 720;
    /**
     * 缓存按"字节数"计量：详情页 backdrop 单张可解码到 1600×1600（≈10MB），
     * 按"张数"计数会把上百 MB 的位图留在堆上，Android 4.4 电视盒必然 OOM。
     */
    private static final int CACHE_BYTES = Math.min(24 * 1024 * 1024,
            Math.max(4 * 1024 * 1024, (int) (Runtime.getRuntime().maxMemory() / 6)));
    private final LruCache<String, Bitmap> cache = new LruCache<String, Bitmap>(CACHE_BYTES) {
        @Override
        protected int sizeOf(String key, Bitmap value) {
            return bitmapBytes(value);
        }
    };
    private String authorizationToken = "";

    private static int bitmapBytes(Bitmap bitmap) {
        if (bitmap == null) {
            return 0;
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            return bitmap.getAllocationByteCount();
        }
        return bitmap.getRowBytes() * bitmap.getHeight();
    }

    void setAuthorizationToken(String token) {
        authorizationToken = token == null ? "" : token;
    }

    void load(String baseUrl, FnosFileEntry entry, FrameLayout frame, ImageView image, TextView fallback) {
        load(baseUrl, entry, frame, image, fallback, null);
    }

    void load(String baseUrl, FnosFileEntry entry, FrameLayout frame, ImageView image, TextView fallback, View loadedOverlay) {
        String url = entry == null ? "" : FnosRestClient.posterImageUrl(baseUrl, entry.posterPath, 400);
        startLoad(url, MAX_DECODE_WIDTH, MAX_DECODE_HEIGHT, frame, image, fallback, loadedOverlay, false);
    }

    /** 详情页通用图片（backdrop / logo）：路径取自 item 接口的 backdrops、logos 字段。 */
    void loadPath(String baseUrl, String path, int width, int maxDecode, FrameLayout frame, ImageView image, TextView fallback) {
        startLoad(FnosRestClient.posterImageUrl(baseUrl, path, width), maxDecode, maxDecode, frame, image, fallback, null, false);
    }

    /** 圆形头像（演职人员）。 */
    void loadCircle(String baseUrl, String path, int width, FrameLayout frame, ImageView image, TextView fallback) {
        startLoad(FnosRestClient.posterImageUrl(baseUrl, path, width), 240, 240, frame, image, fallback, null, true);
    }

    private void startLoad(String url, int maxWidth, int maxHeight, FrameLayout frame, ImageView image,
                           TextView fallback, View loadedOverlay, boolean circle) {
        frame.setTag(url);
        image.setImageDrawable(null);
        image.setVisibility(View.GONE);
        fallback.setVisibility(View.VISIBLE);
        if (loadedOverlay != null) {
            loadedOverlay.setVisibility(View.GONE);
        }
        if (url.length() == 0) {
            return;
        }
        Bitmap cached = cache.get(url);
        if (cached != null) {
            applyBitmap(image, cached, circle);
            image.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
            if (loadedOverlay != null) {
                loadedOverlay.setVisibility(View.VISIBLE);
            }
            return;
        }
        new PosterTask(url, authorizationToken, frame, image, fallback, loadedOverlay, maxWidth, maxHeight, circle)
                .executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private static void applyBitmap(ImageView image, Bitmap bitmap, boolean circle) {
        if (circle) {
            image.setImageDrawable(new CircleBitmapDrawable(bitmap));
        } else {
            image.setImageBitmap(bitmap);
        }
    }

    private final class PosterTask extends AsyncTask<Void, Void, Bitmap> {
        private final String url;
        private final String authorizationToken;
        private final FrameLayout frame;
        private final ImageView image;
        private final TextView fallback;
        private final View loadedOverlay;
        private final int maxWidth;
        private final int maxHeight;
        private final boolean circle;

        PosterTask(String url, String authorizationToken, FrameLayout frame, ImageView image, TextView fallback,
                   View loadedOverlay, int maxWidth, int maxHeight, boolean circle) {
            this.url = url;
            this.authorizationToken = authorizationToken == null ? "" : authorizationToken;
            this.frame = frame;
            this.image = image;
            this.fallback = fallback;
            this.loadedOverlay = loadedOverlay;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.circle = circle;
        }

        @Override
        protected Bitmap doInBackground(Void... params) {
            HttpURLConnection connection = null;
            InputStream stream = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setRequestProperty("Accept", "image/*");
                String cookie = CookieManager.getInstance().getCookie(url);
                if (cookie != null && cookie.length() > 0) {
                    connection.setRequestProperty("Cookie", cookie);
                }
                if (authorizationToken.length() > 0) {
                    connection.setRequestProperty("Authorization", authorizationToken);
                }
                if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                    return null;
                }
                stream = connection.getInputStream();
                byte[] bytes = readAllBytes(stream);
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = BitmapSampleSize.forBounds(
                        bounds.outWidth,
                        bounds.outHeight,
                        maxWidth,
                        maxHeight);
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            } catch (RuntimeException ignored) {
                return null;
            } catch (Exception ignored) {
                return null;
            } finally {
                try {
                    if (stream != null) {
                        stream.close();
                    }
                } catch (Exception ignored) {
                }
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }

        private byte[] readAllBytes(InputStream stream) throws Exception {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) > 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }

        @Override
        protected void onPostExecute(Bitmap bitmap) {
            if (bitmap == null || !url.equals(frame.getTag())) {
                return;
            }
            cache.put(url, bitmap);
            applyBitmap(image, bitmap, circle);
            image.setVisibility(View.VISIBLE);
            fallback.setVisibility(View.GONE);
            if (loadedOverlay != null) {
                loadedOverlay.setVisibility(View.VISIBLE);
            }
        }
    }
}
