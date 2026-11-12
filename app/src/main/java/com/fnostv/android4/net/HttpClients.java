package com.fnostv.android4.net;

import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import okhttp3.OkHttpClient;

/** 统一的 HTTP 客户端构造：需要时允许自签名/异常证书（NAS 常见）。 */
public final class HttpClients {
    private HttpClients() {
    }

    public static OkHttpClient.Builder builder(boolean trustAllCertificates) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        if (trustAllCertificates) {
            try {
                applyTrustAll(builder);
            } catch (Throwable ignored) {
                // 极端情况下退回系统默认证书校验
            }
        }
        return builder;
    }

    private static void applyTrustAll(OkHttpClient.Builder builder) throws Exception {
        final X509TrustManager trustManager = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{trustManager}, new SecureRandom());
        SSLSocketFactory socketFactory = context.getSocketFactory();
        builder.sslSocketFactory(socketFactory, trustManager);
        builder.hostnameVerifier(new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        });
    }
}
