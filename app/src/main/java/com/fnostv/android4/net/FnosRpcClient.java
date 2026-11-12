package com.fnostv.android4.net;

import com.fnostv.android4.config.ServerProfile;
import com.fnostv.android4.util.Logger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okio.ByteString;

public final class FnosRpcClient {
    private static final int OPEN_TIMEOUT_SECONDS = 10;
    private static final int REQUEST_TIMEOUT_SECONDS = 15;
    private static final String BACK_ID_SEED = "0000000000000000";
    private static final String SOCKET_MAIN = "main";
    private static final String SOCKET_FILE = "file";

    private final OkHttpClient httpClient;
    private final ServerProfile profile;
    private final String deviceId;
    private final String socketType;
    private final Map<String, PendingCall> pendingCalls = new HashMap<String, PendingCall>();
    private WebSocket webSocket;
    private CountDownLatch openLatch;
    private FnosRpcException openError;
    private int requestId;
    private String backId = BACK_ID_SEED;
    private String si = "";
    private String publicKey = "";
    private byte[] loginAesKey;
    private byte[] loginIv;

    public FnosRpcClient(ServerProfile profile, String deviceId) {
        this(profile, deviceId, SOCKET_MAIN);
    }

    private FnosRpcClient(ServerProfile profile, String deviceId, String socketType) {
        this.profile = profile;
        this.deviceId = deviceId;
        this.socketType = socketType;
        httpClient = HttpClients.builder(profile.trustSslErrors)
                .readTimeout(0, TimeUnit.MILLISECONDS)
                .connectTimeout(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
    }

    public void connect() throws FnosRpcException {
        openLatch = new CountDownLatch(1);
        Request request = new Request.Builder()
                .url(webSocketUrl(profile.baseUrl, socketType))
                .header("Origin", origin(profile.baseUrl))
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 4.4.2; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/87.0 Safari/537.36")
                .build();
        webSocket = httpClient.newWebSocket(request, new Listener());
        awaitOpen();
    }

    public FnosSession login() throws FnosRpcException {
        connect();
        try {
            loadPublicKey();
            String reqId = nextReqId();
            JSONObject payload = loginPayload(reqId);
            JSONObject encrypted = encryptForLogin(payload);
            JSONObject response = sendEncrypted(reqId, encrypted, REQUEST_TIMEOUT_SECONDS);
            String secretHex = response.optString("secret");
            if (secretHex.length() > 0) {
                secretHex = FnosCrypto.aesDecryptBase64ToBase64(secretHex, loginAesKey, loginIv);
            }
            FnosSession session = new FnosSession(
                    response.optString("token"),
                    response.optString("longToken"),
                    secretHex,
                    profile.username,
                    response.optString("machineId"),
                    response.optString("uid"));
            if (!session.hasToken()) {
                throw new FnosRpcException("登录成功响应缺少 token 或 secret");
            }
            return session;
        } finally {
            close();
        }
    }

    public boolean authToken(FnosSession session) throws FnosRpcException {
        if (session == null || !session.hasToken()) {
            return false;
        }
        connect();
        try {
            loadSessionId(true);
            authenticateToken(session, true);
            return true;
        } finally {
            close();
        }
    }

    public FnosFileList listDir(FnosSession session, String path) throws FnosRpcException {
        FnosRpcClient client = new FnosRpcClient(profile, deviceId, SOCKET_FILE);
        return client.listDirOnFileSocket(session, path);
    }

    public List<FnosPlaybackSource> playbackSources(FnosSession session, FnosFileEntry entry) throws FnosRpcException {
        if (entry == null) {
            throw new FnosRpcException("播放文件为空");
        }
        String directUrl = entry.playbackUrl();
        if (directUrl.length() > 0) {
            List<FnosPlaybackSource> sources = new ArrayList<FnosPlaybackSource>();
            sources.add(new FnosPlaybackSource("原画", directUrl));
            return sources;
        }
        FnosRpcClient client = new FnosRpcClient(profile, deviceId, SOCKET_FILE);
        return client.playbackSourcesOnFileSocket(session, entry);
    }

    public FnosFileList mediaCenterEntries(FnosSession session) throws FnosRpcException {
        FnosRpcClient client = new FnosRpcClient(profile, deviceId, SOCKET_MAIN);
        return client.mediaCenterEntriesOnMainSocket(session);
    }

    public void close() {
        if (webSocket != null) {
            webSocket.close(1000, "done");
            webSocket = null;
        }
        httpClient.dispatcher().executorService().shutdown();
    }

    private void awaitOpen() throws FnosRpcException {
        try {
            if (!openLatch.await(OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new FnosRpcException("连接 fnOS RPC 超时");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new FnosRpcException("连接 fnOS RPC 被中断", ex);
        }
        if (openError != null) {
            throw openError;
        }
    }

    private void loadPublicKey() throws FnosRpcException {
        loadSessionId(true);
    }

    private void loadSessionId(boolean withPublicKey) throws FnosRpcException {
        try {
            JSONObject request = new JSONObject();
            request.put("req", withPublicKey ? "util.crypto.getRSAPub" : "util.getSI");
            JSONObject response = send(request, REQUEST_TIMEOUT_SECONDS);
            publicKey = response.optString("pub", publicKey);
            si = response.optString("si");
            if ((withPublicKey && publicKey.length() == 0) || si.length() == 0) {
                throw new FnosRpcException("fnOS RPC 握手缺少公钥或会话标识");
            }
        } catch (JSONException ex) {
            throw new FnosRpcException("构造握手请求失败", ex);
        }
    }

    private void authenticateToken(FnosSession session, boolean main) throws FnosRpcException {
        try {
            JSONObject request = new JSONObject();
            request.put("req", "user.authToken");
            request.put("token", session.token);
            if (main) {
                request.put("main", true);
            }
            request.put("si", si);
            send(signRequest(request, session.secretHex), REQUEST_TIMEOUT_SECONDS);
        } catch (JSONException ex) {
            throw new FnosRpcException("构造 token 验证请求失败", ex);
        }
    }

    private FnosFileList listDirOnFileSocket(FnosSession session, String path) throws FnosRpcException {
        connect();
        try {
            loadSessionId(false);
            authenticateToken(session, false);
            JSONObject request = new JSONObject();
            request.put("req", "file.ls");
            if (path != null && path.length() > 0) {
                request.put("path", path);
            }
            JSONObject response = send(signRequest(request, session.secretHex), REQUEST_TIMEOUT_SECONDS);
            return parseFileList(path, session.uid, response);
        } catch (JSONException ex) {
            throw new FnosRpcException("构造目录列表请求失败", ex);
        } finally {
            close();
        }
    }

    private String downloadUrlOnFileSocket(FnosSession session, String path) throws FnosRpcException {
        if (path == null || path.length() == 0) {
            throw new FnosRpcException("文件路径为空");
        }
        connect();
        try {
            loadSessionId(false);
            authenticateToken(session, false);
            JSONObject request = new JSONObject();
            request.put("req", "file.download");
            request.put("files", new JSONArray().put(path));
            JSONObject response = send(signRequest(request, session.secretHex), REQUEST_TIMEOUT_SECONDS);
            return parseDownloadUrl(response);
        } catch (JSONException ex) {
            throw new FnosRpcException("构造下载直链请求失败", ex);
        } finally {
            close();
        }
    }

    private List<FnosPlaybackSource> playbackSourcesOnFileSocket(FnosSession session, FnosFileEntry entry) throws FnosRpcException {
        if (entry.path.length() == 0) {
            throw new FnosRpcException("文件路径为空");
        }
        connect();
        try {
            loadSessionId(false);
            authenticateToken(session, false);
            JSONObject request = new JSONObject();
            request.put("req", "file.download");
            request.put("files", new JSONArray().put(entry.path));
            JSONObject response = send(signRequest(request, session.secretHex), REQUEST_TIMEOUT_SECONDS);
            List<FnosPlaybackSource> sources = parsePlaybackSources(response);
            if (sources.size() == 0) {
                sources.add(new FnosPlaybackSource("原画", parseDownloadUrl(response)));
            }
            return sources;
        } catch (JSONException ex) {
            throw new FnosRpcException("构造播放源请求失败", ex);
        } finally {
            close();
        }
    }

    private FnosFileList mediaCenterEntriesOnMainSocket(FnosSession session) throws FnosRpcException {
        connect();
        try {
            loadSessionId(false);
            authenticateToken(session, true);
            String[] candidates = {
                    "app.mediaCenter.home",
                    "app.mediaCenter.index",
                    "app.mediaCenter.list",
                    "app.mediaCenter.recent",
                    "mediaCenter.home",
                    "mediaCenter.list"
            };
            FnosRpcException lastError = null;
            for (int i = 0; i < candidates.length; i++) {
                try {
                    JSONObject request = new JSONObject();
                    request.put("req", candidates[i]);
                    JSONObject response = send(signRequest(request, session.secretHex), REQUEST_TIMEOUT_SECONDS);
                    FnosFileList entries = parseMediaCenterList(response);
                    if (entries.entries.size() > 0) {
                        return entries;
                    }
                } catch (JSONException ex) {
                    lastError = new FnosRpcException("构造影视中心请求失败", ex);
                } catch (FnosRpcException ex) {
                    lastError = ex;
                }
            }
            if (lastError != null) {
                throw lastError;
            }
            throw new FnosRpcException("影视中心接口未返回可用条目");
        } finally {
            close();
        }
    }

    private FnosFileList parseFileList(String path, String uid, JSONObject response) {
        List<FnosFileEntry> entries = new ArrayList<FnosFileEntry>();
        JSONArray files = response.optJSONArray("files");
        if (files == null) {
            files = response.optJSONArray("data");
        }
        if (files != null) {
            for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.optJSONObject(i);
                if (file != null) {
                    entries.add(FnosFileEntry.fromJson(file, path, uid));
                }
            }
        }
        return new FnosFileList(path, entries);
    }

    private String parseDownloadUrl(JSONObject response) throws FnosRpcException {
        JSONArray downloads = response.optJSONArray("download");
        JSONObject first = downloads == null || downloads.length() == 0 ? null : downloads.optJSONObject(0);
        if (first != null) {
            int errno = first.optInt("errno", 0);
            if (errno != 0) {
                throw new FnosRpcException("文件下载准备失败：" + errno);
            }
            String url = absoluteUrl(downloadCandidate(first));
            if (url.length() > 0) {
                return url;
            }
        }
        String url = absoluteUrl(downloadCandidate(response));
        if (url.length() > 0) {
            return url;
        }
        throw new FnosRpcException("fnOS 未返回文件下载直链");
    }

    private List<FnosPlaybackSource> parsePlaybackSources(JSONObject response) {
        List<FnosPlaybackSource> sources = new ArrayList<FnosPlaybackSource>();
        collectPlaybackSources(response, sources);
        List<FnosPlaybackSource> unique = new ArrayList<FnosPlaybackSource>();
        for (int i = 0; i < sources.size(); i++) {
            FnosPlaybackSource source = sources.get(i);
            if (source.isValid() && !containsSource(unique, source.url)) {
                unique.add(source);
            }
        }
        return unique;
    }

    private void collectPlaybackSources(Object value, List<FnosPlaybackSource> sources) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            String url = absoluteUrl(downloadCandidate(object));
            if (url.length() > 0) {
                sources.add(new FnosPlaybackSource(sourceLabel(object), url));
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                Object child = object.opt(keys.next());
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    collectPlaybackSources(child, sources);
                }
            }
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                collectPlaybackSources(array.opt(i), sources);
            }
        }
    }

    private String sourceLabel(JSONObject object) {
        String[] keys = {"quality", "resolution", "definition", "label", "name", "title", "type"};
        for (int i = 0; i < keys.length; i++) {
            String value = object.optString(keys[i]);
            if (value != null && value.length() > 0 && !isHttpUrl(value)) {
                return normalizeSourceLabel(value);
            }
        }
        return "原画";
    }

    private String normalizeSourceLabel(String value) {
        String lower = value.toLowerCase();
        if (lower.indexOf("origin") >= 0 || lower.indexOf("source") >= 0 || lower.indexOf("raw") >= 0) {
            return "原画";
        }
        if (lower.indexOf("1080") >= 0) {
            return "1080p";
        }
        if (lower.indexOf("720") >= 0) {
            return "720p";
        }
        if (lower.indexOf("480") >= 0) {
            return "480p";
        }
        return value;
    }

    private boolean containsSource(List<FnosPlaybackSource> sources, String url) {
        for (int i = 0; i < sources.size(); i++) {
            if (sources.get(i).url.equals(url)) {
                return true;
            }
        }
        return false;
    }

    private FnosFileList parseMediaCenterList(JSONObject response) {
        List<FnosFileEntry> entries = new ArrayList<FnosFileEntry>();
        collectMediaEntries(response, entries);
        return new FnosFileList("mediaCenter", entries);
    }

    private void collectMediaEntries(Object value, List<FnosFileEntry> entries) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            FnosFileEntry entry = mediaEntryFromJson(object);
            if (entry != null && !containsEntry(entries, entry.path)) {
                entries.add(entry);
            }
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                Object child = object.opt(keys.next());
                if (child instanceof JSONObject || child instanceof JSONArray) {
                    collectMediaEntries(child, entries);
                }
            }
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            for (int i = 0; i < array.length(); i++) {
                collectMediaEntries(array.opt(i), entries);
            }
        }
    }

    private FnosFileEntry mediaEntryFromJson(JSONObject object) {
        String name = firstString(object, new String[]{"name", "title", "videoName", "movieName"});
        String path = firstString(object, new String[]{"path", "filePath", "srcPath", "realPath"});
        String url = absoluteUrl(downloadCandidate(object));
        if (name.length() == 0 || (path.length() == 0 && url.length() == 0)) {
            return null;
        }
        boolean directory = object.optInt("dir", 0) == 1
                || object.optBoolean("dir")
                || object.optBoolean("folder")
                || object.optBoolean("isFolder");
        return new FnosFileEntry(
                name,
                path.length() > 0 ? path : url,
                directory,
                object.optLong("size", 0L),
                firstString(object, new String[]{"type", "mime", "mimeType"}),
                url);
    }

    private String firstString(JSONObject object, String[] keys) {
        for (int i = 0; i < keys.length; i++) {
            String value = object.optString(keys[i]);
            if (value != null && value.length() > 0) {
                return value;
            }
        }
        return "";
    }

    private boolean containsEntry(List<FnosFileEntry> entries, String path) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).path.equals(path)) {
                return true;
            }
        }
        return false;
    }

    private String downloadCandidate(JSONObject object) {
        String[] keys = {"url", "uri", "downloadUrl", "href", "link", "src", "location"};
        for (int i = 0; i < keys.length; i++) {
            String value = object.optString(keys[i]);
            if (value.length() > 0) {
                return value;
            }
        }
        return "";
    }

    private String absoluteUrl(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            return value;
        }
        if (value.startsWith("/")) {
            return origin(profile.baseUrl) + value;
        }
        return "";
    }

    private boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private JSONObject loginPayload(String reqId) throws FnosRpcException {
        try {
            JSONObject request = new JSONObject();
            request.put("reqid", reqId);
            request.put("req", "user.login");
            request.put("user", profile.username);
            request.put("password", profile.password);
            request.put("stay", false);
            request.put("deviceType", "Browser");
            request.put("deviceName", "Android-TV WebView");
            request.put("did", deviceId);
            request.put("si", si);
            return request;
        } catch (JSONException ex) {
            throw new FnosRpcException("构造登录请求失败", ex);
        }
    }

    private JSONObject encryptForLogin(JSONObject payload) throws FnosRpcException {
        try {
            loginAesKey = FnosCrypto.randomAscii(32).getBytes("UTF-8");
            loginIv = FnosCrypto.randomBytes(16);
            JSONObject encrypted = new JSONObject();
            encrypted.put("req", "encrypted");
            encrypted.put("iv", android.util.Base64.encodeToString(loginIv, android.util.Base64.NO_WRAP));
            encrypted.put("rsa", FnosCrypto.rsaEncryptBase64(publicKey, new String(loginAesKey, "UTF-8")));
            encrypted.put("aes", FnosCrypto.aesEncryptBase64(payload.toString(), loginAesKey, loginIv));
            return encrypted;
        } catch (Exception ex) {
            if (ex instanceof FnosRpcException) {
                throw (FnosRpcException) ex;
            }
            throw new FnosRpcException("构造加密登录请求失败", ex);
        }
    }

    private SignedRequest signRequest(JSONObject request, String secretHex) throws FnosRpcException {
        if (request.optString("reqid").length() == 0) {
            try {
                request.put("reqid", nextReqId());
            } catch (JSONException ex) {
                throw new FnosRpcException("构造请求编号失败", ex);
            }
        }
        String body = request.toString();
        return new SignedRequest(request.optString("reqid"), FnosCrypto.hmacSha256Base64(body, secretHex) + body);
    }

    private JSONObject send(JSONObject request, int timeoutSeconds) throws FnosRpcException {
        String reqId = request.optString("reqid");
        if (reqId.length() == 0) {
            reqId = nextReqId();
            try {
                request.put("reqid", reqId);
            } catch (JSONException ex) {
                throw new FnosRpcException("构造请求编号失败", ex);
            }
        }
        PendingCall call = new PendingCall();
        synchronized (pendingCalls) {
            pendingCalls.put(reqId, call);
        }
        webSocket.send(request.toString());
        return call.await(reqId, timeoutSeconds);
    }

    private JSONObject send(SignedRequest request, int timeoutSeconds) throws FnosRpcException {
        String reqId = request.reqId;
        PendingCall call = new PendingCall();
        synchronized (pendingCalls) {
            pendingCalls.put(reqId, call);
        }
        webSocket.send(request.signedPayload);
        return call.await(reqId, timeoutSeconds);
    }

    private JSONObject sendEncrypted(String reqId, JSONObject encrypted, int timeoutSeconds) throws FnosRpcException {
        PendingCall call = new PendingCall();
        synchronized (pendingCalls) {
            pendingCalls.put(reqId, call);
        }
        webSocket.send(encrypted.toString());
        return call.await(reqId, timeoutSeconds);
    }

    private String nextReqId() {
        long seconds = System.currentTimeMillis() / 1000L;
        requestId = (requestId + 1) & 0xFFFF;
        return leftPad(Long.toHexString(seconds), 8) + backId + leftPad(Integer.toHexString(requestId), 4);
    }

    private String leftPad(String value, int length) {
        StringBuilder builder = new StringBuilder(value);
        while (builder.length() < length) {
            builder.insert(0, '0');
        }
        return builder.toString();
    }

    private String webSocketUrl(String baseUrl, String type) {
        String url = baseUrl;
        if (url.startsWith("https://")) {
            url = "wss://" + url.substring("https://".length());
        } else if (url.startsWith("http://")) {
            url = "ws://" + url.substring("http://".length());
        }
        return url + "/websocket?type=" + type;
    }

    private String origin(String baseUrl) {
        int pathStart = baseUrl.indexOf('/', baseUrl.indexOf("://") + 3);
        return pathStart > 0 ? baseUrl.substring(0, pathStart) : baseUrl;
    }

    private final class Listener extends WebSocketListener {
        @Override
        public void onOpen(WebSocket webSocket, Response response) {
            openLatch.countDown();
        }

        @Override
        public void onMessage(WebSocket webSocket, String text) {
            try {
                JSONObject message = new JSONObject(text);
                if ("pong".equals(message.optString("res"))) {
                    return;
                }
                if (message.has("backId")) {
                    backId = message.optString("backId", backId);
                }
                String reqId = message.optString("reqid");
                if (reqId.length() == 0) {
                    return;
                }
                PendingCall call;
                synchronized (pendingCalls) {
                    call = pendingCalls.get(reqId);
                }
                if (call != null) {
                    String result = message.optString("result");
                    if (result.length() > 0 && !"doing".equals(result)) {
                        synchronized (pendingCalls) {
                            pendingCalls.remove(reqId);
                        }
                        call.complete(message);
                    } else {
                        call.update(message);
                    }
                }
            } catch (JSONException ex) {
                Logger.w("RPC message parse failed: " + ex.getMessage());
            }
        }

        @Override
        public void onMessage(WebSocket webSocket, ByteString bytes) {
        }

        @Override
        public void onFailure(WebSocket webSocket, Throwable throwable, Response response) {
            String message = throwable == null ? "未知网络错误" : throwable.getMessage();
            openError = new FnosRpcException("fnOS RPC 连接失败：" + message, throwable);
            openLatch.countDown();
            synchronized (pendingCalls) {
                Iterator<Map.Entry<String, PendingCall>> iterator = pendingCalls.entrySet().iterator();
                while (iterator.hasNext()) {
                    iterator.next().getValue().fail(openError);
                    iterator.remove();
                }
            }
        }
    }

    private static final class PendingCall {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final JSONArray partialFiles = new JSONArray();
        private final JSONArray partialDownload = new JSONArray();
        private JSONObject response;
        private FnosRpcException error;

        void update(JSONObject value) {
            appendFiles(value.optJSONArray("files"));
            appendDownload(value.optJSONArray("download"));
        }

        void complete(JSONObject value) {
            appendFiles(value.optJSONArray("files"));
            appendDownload(value.optJSONArray("download"));
            if (partialFiles.length() > 0 && !value.has("files")) {
                try {
                    value.put("files", partialFiles);
                } catch (JSONException ignored) {
                }
            }
            if (partialDownload.length() > 0 && !value.has("download")) {
                try {
                    value.put("download", partialDownload);
                } catch (JSONException ignored) {
                }
            }
            response = value;
            latch.countDown();
        }

        private void appendFiles(JSONArray files) {
            if (files == null) {
                return;
            }
            for (int i = 0; i < files.length(); i++) {
                partialFiles.put(files.opt(i));
            }
        }

        private void appendDownload(JSONArray downloads) {
            if (downloads == null) {
                return;
            }
            for (int i = 0; i < downloads.length(); i++) {
                partialDownload.put(downloads.opt(i));
            }
        }

        void fail(FnosRpcException value) {
            error = value;
            latch.countDown();
        }

        JSONObject await(String reqId, int timeoutSeconds) throws FnosRpcException {
            try {
                if (!latch.await(timeoutSeconds, TimeUnit.SECONDS)) {
                    throw new FnosRpcException("RPC 请求超时：" + reqId);
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new FnosRpcException("RPC 请求被中断：" + reqId, ex);
            }
            if (error != null) {
                throw error;
            }
            if (response == null) {
                throw new FnosRpcException("RPC 响应为空：" + reqId);
            }
            String result = response.optString("result");
            if (!"success".equals(result) && !"succ".equals(result)) {
                String message = response.optString("errmsg");
                if (message.length() == 0) {
                    message = response.has("errno")
                            ? "RPC 请求失败：" + response.optString("errno")
                            : "RPC 请求失败";
                }
                throw new FnosRpcException(message);
            }
            return response;
        }
    }

    private static final class SignedRequest {
        final String reqId;
        final String signedPayload;

        SignedRequest(String reqId, String signedPayload) {
            this.reqId = reqId;
            this.signedPayload = signedPayload;
        }
    }
}
