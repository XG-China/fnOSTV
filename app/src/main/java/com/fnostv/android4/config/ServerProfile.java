package com.fnostv.android4.config;

import org.json.JSONException;
import org.json.JSONObject;

public final class ServerProfile {
    public final int schemaVersion;
    public final String baseUrl;
    public final String username;
    public final String password;
    public final boolean autoLogin;
    public final boolean trustSslErrors;

    public ServerProfile(String baseUrl, String username, String password, boolean autoLogin, boolean trustSslErrors) {
        this(1, baseUrl, username, password, autoLogin, trustSslErrors);
    }

    private ServerProfile(int schemaVersion, String baseUrl, String username, String password, boolean autoLogin, boolean trustSslErrors) {
        this.schemaVersion = schemaVersion;
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.username = username == null ? "" : username.trim();
        this.password = password == null ? "" : password;
        this.autoLogin = autoLogin;
        this.trustSslErrors = trustSslErrors;
    }

    public boolean isReady() {
        return ProfileValidator.validate(this).isValid();
    }

    /** 服务器地址去掉协议前缀，供设置页展示（例如 10.0.0.40:5666）。 */
    public String hostPort() {
        int index = baseUrl.indexOf("://");
        return index < 0 ? baseUrl : baseUrl.substring(index + 3);
    }

    /** 当前地址是否使用 HTTPS。 */
    public boolean useHttps() {
        return baseUrl.startsWith("https://");
    }

    public JSONObject toJson() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("schemaVersion", schemaVersion);
        object.put("baseUrl", baseUrl);
        object.put("username", username);
        object.put("password", password);
        object.put("autoLogin", autoLogin);
        object.put("trustSslErrors", trustSslErrors);
        return object;
    }

    public static ServerProfile fromJson(String value) {
        if (value == null || value.length() == 0) {
            return empty();
        }
        try {
            JSONObject object = new JSONObject(value);
            return new ServerProfile(
                    object.optInt("schemaVersion", 1),
                    object.optString("baseUrl"),
                    object.optString("username"),
                    object.optString("password"),
                    object.optBoolean("autoLogin", true),
                    object.optBoolean("trustSslErrors", false));
        } catch (JSONException ignored) {
            return empty();
        }
    }

    public static ServerProfile empty() {
        return new ServerProfile("", "", "", true, false);
    }

    private static String normalizeBaseUrl(String raw) {
        if (raw == null) {
            return "";
        }
        String url = raw.trim();
        if (url.length() == 0) {
            return "";
        }
        // 允许只输入 "IP:端口" 或 "域名:端口"：默认补 http://，显式写了 https:// 则保留。
        if (url.startsWith("https://")) {
            url = "https://" + stripSlashes(url.substring(8));
        } else if (url.startsWith("http://")) {
            url = "http://" + stripSlashes(url.substring(7));
        } else {
            url = "http://" + stripSlashes(url);
        }
        return url;
    }

    private static String stripSlashes(String value) {
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
