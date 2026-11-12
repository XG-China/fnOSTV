package com.fnostv.android4;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.fnostv.android4.config.ProfileStore;
import com.fnostv.android4.config.ProfileValidation;
import com.fnostv.android4.config.ProfileValidator;
import com.fnostv.android4.config.ServerProfile;
import com.fnostv.android4.net.FnosRestClient;
import com.fnostv.android4.net.FnosRpcClient;
import com.fnostv.android4.net.FnosRpcException;
import com.fnostv.android4.net.FnosSession;
import com.fnostv.android4.net.FnosSessionStore;
import com.fnostv.android4.net.FnosSettingsSummary;
import com.fnostv.android4.ui.FnosTheme;
import com.fnostv.android4.ui.NativeSettingsView;
import com.fnostv.android4.ui.SettingsForm;
import com.fnostv.android4.util.Constants;
import com.fnostv.android4.util.Logger;

import org.json.JSONObject;

/**
 * TV 端设置：服务器连接（登录）与账号内的播放偏好、外观。
 * 服务器管理相关页面（媒体库/用户/任务计划）只在服务器网页端提供。
 */
public final class SettingsActivity extends Activity implements SettingsForm.Listener, NativeSettingsView.Listener {
    private ProfileStore store;
    private FnosSessionStore sessionStore;
    private SettingsForm settingsForm;
    private NativeSettingsView nativeSettingsView;
    private FnosSettingsSummary settingsSummary = FnosSettingsSummary.empty();
    private boolean accountEditorOpen;
    private boolean firstConfiguration;
    private boolean loginRunning;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        // 主题（深色/浅色/日升日落）要在建视图前应用。
        FnosTheme.applyFromPrefs(this);
        store = new ProfileStore(this);
        sessionStore = new FnosSessionStore(this);
        settingsForm = new SettingsForm(this, this);
        nativeSettingsView = new NativeSettingsView(this, this);
        nativeSettingsView.setInitialPage(getIntent().getStringExtra(Constants.EXTRA_SETTINGS_PAGE));

        String error = getIntent().getStringExtra(Constants.EXTRA_SETTINGS_ERROR_MESSAGE);
        firstConfiguration = !store.load().isReady();
        if (error != null || firstConfiguration) {
            showAccountEditor(error);
        } else {
            showNativeSettings("");
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            // 正在输入时：第一次返回只收起键盘/移开焦点，再按一次才退出页面。
            android.view.View focus = getCurrentFocus();
            if (focus instanceof android.widget.EditText) {
                android.view.inputmethod.InputMethodManager imm =
                        (android.view.inputmethod.InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(focus.getWindowToken(), 0);
                focus.clearFocus();
                return true;
            }
            onCancelRequested();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onLoginRequested(ServerProfile profile) {
        ProfileValidation validation = ProfileValidator.validate(profile);
        if (!validation.isValid()) {
            Toast.makeText(this, validation.getMessage(), Toast.LENGTH_SHORT).show();
            settingsForm.focusServerUrl();
            return;
        }
        if (loginRunning) {
            return;
        }
        loginRunning = true;
        store.save(profile);
        settingsForm.setBusy(true, "正在登录…");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String error = null;
                try {
                    FnosSession session = new FnosRpcClient(profile, sessionStore.getOrCreateDeviceId()).login();
                    sessionStore.save(session);
                } catch (FnosRpcException exception) {
                    // RPC 登录被拒（例如 131072）时，回退到网页同款 REST 登录：
                    // 媒体库、播放、收藏都走 REST，REST 能登就能用。
                    FnosRestClient rest = new FnosRestClient(profile, getCacheDir());
                    if (rest.tryRestLogin()) {
                        Logger.w("RPC login rejected, using REST session: " + exception.getMessage());
                        sessionStore.save(new FnosSession(rest.authorizationToken(), "", "rest",
                                profile.username, "", ""));
                    } else {
                        error = exception.getMessage() == null ? exception.toString() : exception.getMessage();
                        if (error.indexOf("131072") >= 0) {
                            error = "服务器拒绝登录（131072）。多为 fnOS 版本差异，或账号开启了两步验证/登录保护；"
                                    + "请先在网页端确认能登录，并关闭二次验证后重试。";
                        }
                    }
                } catch (Throwable throwable) {
                    error = throwable.toString();
                }
                final String message = error;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loginRunning = false;
                        settingsForm.setBusy(false, null);
                        if (message == null) {
                            accountEditorOpen = false;
                            setResult(RESULT_OK);
                            finish();
                            return;
                        }
                        Logger.w("login failed: " + message);
                        showAccountEditor("登录失败：" + message);
                    }
                });
            }
        }, "fnos-login").start();
    }

    @Override
    public void onCancelRequested() {
        if (accountEditorOpen && store.load().isReady()) {
            accountEditorOpen = false;
            showNativeSettings("");
            return;
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onCloseSettings() {
        setResult(RESULT_OK);
        finish();
    }

    @Override
    public void onEditAccount() {
        showAccountEditor(null);
    }

    /** 主题/卡片样式变化：重建设置页以整页重新着色。 */
    @Override
    public void onThemeChanged() {
        recreate();
    }

    private void showAccountEditor(String errorMessage) {
        accountEditorOpen = true;
        setContentView(settingsForm.create(store.load(), errorMessage));
    }

    private void showNativeSettings(String status) {
        accountEditorOpen = false;
        ServerProfile profile = store.load();
        nativeSettingsView.setServerAddress(profile.hostPort());
        setContentView(nativeSettingsView.create(status, settingsSummary));
        loadSettingsSummary();
    }

    private void refreshNativeSettings(String status) {
        nativeSettingsView.refresh(status, settingsSummary);
    }

    /** 设置页只需用户信息与版本号（页脚）。 */
    private void loadSettingsSummary() {
        final ServerProfile profile = store.load();
        if (!profile.isReady()) {
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    FnosRestClient client = new FnosRestClient(profile);
                    JSONObject userInfo = client.userInfo();
                    JSONObject version = client.version();
                    final FnosSettingsSummary loaded = FnosSettingsSummary.fromResponses(
                            userInfo,
                            null,
                            version,
                            null,
                            null,
                            null,
                            null);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            settingsSummary = loaded;
                            refreshNativeSettings("");
                        }
                    });
                } catch (FnosRpcException ex) {
                    Logger.w("Settings summary load failed: " + ex.getMessage());
                } catch (RuntimeException ex) {
                    Logger.w("Settings summary crashed: " + ex.getMessage());
                }
            }
        }, "fnos-settings-summary").start();
    }
}
