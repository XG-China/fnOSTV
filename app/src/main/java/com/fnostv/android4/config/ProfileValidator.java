package com.fnostv.android4.config;

public final class ProfileValidator {
    private ProfileValidator() {
    }

    /**
     * 校验服务器地址。设置页只让用户输入 "IP/域名:端口"，
     * 协议由 “使用 HTTPS” 开关决定，这里额外兜底处理手输协议的情况。
     */
    public static ProfileValidation validate(ServerProfile profile) {
        if (profile == null || profile.baseUrl.length() == 0) {
            return new ProfileValidation(ProfileValidation.EMPTY_BASE_URL, "请填写服务器地址，例如 10.0.0.40:5666");
        }
        if (!profile.baseUrl.startsWith("http://") && !profile.baseUrl.startsWith("https://")) {
            return new ProfileValidation(ProfileValidation.UNSUPPORTED_SCHEME, "服务器地址仅支持 HTTP 或 HTTPS");
        }
        String host = profile.hostPort();
        int pathStart = host.indexOf('/');
        if (pathStart >= 0) {
            host = host.substring(0, pathStart);
        }
        if (host.length() == 0) {
            return new ProfileValidation(ProfileValidation.EMPTY_BASE_URL, "请填写服务器地址，例如 10.0.0.40:5666");
        }
        if (host.indexOf(' ') >= 0) {
            return new ProfileValidation(ProfileValidation.UNSUPPORTED_SCHEME, "服务器地址不能包含空格");
        }
        int colon = host.lastIndexOf(':');
        if (colon >= 0) {
            String port = host.substring(colon + 1);
            try {
                int value = Integer.parseInt(port);
                if (value < 1 || value > 65535) {
                    return new ProfileValidation(ProfileValidation.UNSUPPORTED_SCHEME, "端口需要在 1-65535 之间");
                }
            } catch (NumberFormatException ex) {
                return new ProfileValidation(ProfileValidation.UNSUPPORTED_SCHEME, "端口格式不正确，例如 10.0.0.40:5666");
            }
        }
        return new ProfileValidation(ProfileValidation.OK, "");
    }
}
