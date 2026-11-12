package com.fnostv.android4.ui;

import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.fnostv.android4.config.ServerProfile;

/**
 * 首屏登录 / 服务器连接页。
 *
 * 只需输入 “IP:端口” 或 “域名:端口”，协议由“使用 HTTPS”开关决定，不用手写 http://。
 * 布局刻意做得紧凑并靠上，避免电视软键盘（约占下半屏）挡住登录按钮。
 */
public final class SettingsForm {
    public interface Listener {
        /** 保存并登录。 */
        void onLoginRequested(ServerProfile profile);

        void onCancelRequested();
    }

    private final Context context;
    private final Listener listener;
    private EditText urlInput;
    private EditText usernameInput;
    private EditText passwordInput;
    private CheckBox httpsInput;
    private CheckBox trustSslInput;
    private CheckBox autoLoginInput;
    private Button loginButton;

    public SettingsForm(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    public View create(ServerProfile profile) {
        return create(profile, null);
    }

    public View create(ServerProfile profile, String errorMessage) {
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(FnosTheme.COLOR_APP_BG);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(40), dp(24), dp(40), dp(22));
        panel.setBackgroundDrawable(FnosTheme.glass(context, 22));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(
                dp(470), ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        panelParams.topMargin = dp(56);
        root.addView(panel, panelParams);

        // ── 品牌 ───────────────────────────────────────────────
        LinearLayout brand = new LinearLayout(context);
        brand.setGravity(Gravity.CENTER);
        brand.setOrientation(LinearLayout.HORIZONTAL);
        brand.addView(new FnosLogoMarkView(context), new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView logo = new TextView(context);
        logo.setText("fnOSTV");
        logo.setTextColor(FnosTheme.COLOR_TEXT);
        logo.setTextSize(24);
        logo.setTypeface(null, 1);
        logo.setGravity(Gravity.CENTER_VERTICAL);
        logo.setPadding(dp(10), 0, 0, 0);
        brand.addView(logo, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        panel.addView(brand, rowParams(0, 4));

        TextView subtitle = new TextView(context);
        subtitle.setText("连接你的飞牛 NAS · 输入 IP 或域名加端口");
        subtitle.setTextColor(FnosTheme.COLOR_TEXT_MUTED);
        subtitle.setTextSize(12);
        subtitle.setGravity(Gravity.CENTER);
        panel.addView(subtitle, rowParams(0, 14));

        if (errorMessage != null && errorMessage.length() > 0) {
            TextView error = new TextView(context);
            error.setText(errorMessage);
            error.setTextColor(FnosTheme.isLightTheme() ? 0xFFB4232A : 0xFFFFD8D8);
            error.setTextSize(12);
            error.setPadding(dp(12), dp(8), dp(12), dp(8));
            error.setBackgroundDrawable(FnosTheme.rounded(0xFF4A1F26, 10, context));
            panel.addView(error, rowParams(0, 12));
        }

        // ── 表单 ───────────────────────────────────────────────
        urlInput = input("服务器地址，如 10.0.0.40:5666", false);
        urlInput.setText(profile.hostPort());
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        panel.addView(urlInput, rowParams(0, 4));

        LinearLayout toggles = new LinearLayout(context);
        toggles.setOrientation(LinearLayout.HORIZONTAL);
        toggles.setGravity(Gravity.CENTER_VERTICAL);
        httpsInput = checkbox("使用 HTTPS", profile.useHttps());
        toggles.addView(httpsInput, new LinearLayout.LayoutParams(0, dp(32), 1));
        trustSslInput = checkbox("信任证书异常", profile.trustSslErrors);
        toggles.addView(trustSslInput, new LinearLayout.LayoutParams(0, dp(32), 1));
        panel.addView(toggles, rowParams(0, 6));

        usernameInput = input("用户名", false);
        usernameInput.setText(profile.username);
        panel.addView(usernameInput, rowParams(0, 10));

        passwordInput = input("密码", true);
        passwordInput.setText(profile.password);
        panel.addView(passwordInput, rowParams(0, 14));

        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = button("取消", false);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onCancelRequested();
            }
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(46), 1));
        loginButton = button("登录", true);
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onLoginRequested(readProfile());
            }
        });
        LinearLayout.LayoutParams loginParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        loginParams.leftMargin = dp(12);
        actions.addView(loginButton, loginParams);
        panel.addView(actions, rowParams(0, 0));

        autoLoginInput = new CheckBox(context);
        autoLoginInput.setChecked(profile.autoLogin);
        autoLoginInput.setVisibility(View.GONE);

        if (profile.hostPort().length() == 0) {
            urlInput.requestFocus();
        } else if (profile.password.length() == 0) {
            passwordInput.requestFocus();
        } else {
            // 已有配置：默认聚焦“登录”，避免一进页面就弹软键盘。
            loginButton.requestFocus();
        }
        return root;
    }

    /** 登录中：禁用按钮并给出提示，避免遥控器连按重复登录。 */
    public void setBusy(boolean busy, String message) {
        if (loginButton != null) {
            loginButton.setEnabled(!busy);
            loginButton.setAlpha(busy ? 0.6f : 1f);
            loginButton.setText(busy ? (message == null ? "正在登录…" : message) : "登录");
        }
    }

    public void focusServerUrl() {
        if (urlInput != null) {
            urlInput.requestFocus();
        }
    }

    private ServerProfile readProfile() {
        String raw = urlInput == null ? "" : urlInput.getText().toString().trim();
        boolean https = httpsInput != null && httpsInput.isChecked();
        // 兼容用户直接粘贴完整地址的情况
        if (raw.startsWith("https://")) {
            https = true;
            raw = raw.substring(8);
        } else if (raw.startsWith("http://")) {
            raw = raw.substring(7);
        }
        while (raw.endsWith("/")) {
            raw = raw.substring(0, raw.length() - 1);
        }
        return new ServerProfile(
                (https ? "https://" : "http://") + raw,
                usernameInput.getText().toString(),
                passwordInput.getText().toString(),
                autoLoginInput == null || autoLoginInput.isChecked(),
                trustSslInput != null && trustSslInput.isChecked());
    }

    private EditText input(String hint, boolean password) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setHint(hint);
        input.setHintTextColor(FnosTheme.COLOR_TEXT_DIM);
        input.setTextColor(FnosTheme.COLOR_TEXT);
        input.setTextSize(15);
        input.setSelectAllOnFocus(true);
        input.setPadding(dp(14), 0, dp(14), 0);
        input.setMinHeight(dp(44));
        FocusStyler.applyField(input);
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        } else {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        }
        return input;
    }

    private CheckBox checkbox(String text, boolean checked) {
        CheckBox checkBox = new CheckBox(context);
        checkBox.setText(text);
        checkBox.setTextColor(FnosTheme.COLOR_TEXT_80);
        checkBox.setTextSize(12);
        checkBox.setChecked(checked);
        checkBox.setPadding(dp(8), 0, dp(8), 0);
        FocusStyler.applyCheck(checkBox);
        return checkBox;
    }

    private Button button(String text, boolean primary) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(15);
        button.setTextColor(FnosTheme.COLOR_TEXT);
        button.setAllCaps(false);
        button.setMinHeight(dp(46));
        button.setBackgroundDrawable(FnosTheme.rounded(
                primary ? FnosTheme.COLOR_PRIMARY : FnosTheme.COLOR_CARD, 12, context));
        FocusStyler.apply(button,
                primary ? FnosTheme.COLOR_PRIMARY : FnosTheme.COLOR_CARD,
                primary ? FnosTheme.COLOR_PRIMARY_FOCUSED : FnosTheme.COLOR_CARD_FOCUSED, 12);
        return button;
    }

    private LinearLayout.LayoutParams rowParams(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(top);
        params.bottomMargin = dp(bottom);
        return params;
    }

    private int dp(int value) {
        return FnosTheme.dp(context, value);
    }
}
