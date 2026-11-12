# fnOSTV 开发说明

本文档面向后续接手开发的人：说明工程结构、关键流程、扩展点，以及构建/调试/发版方式。
阅读前建议先看 [README](../README.md) 和 [使用说明](USER_GUIDE.md)。

---

## 1. 工程与工具链

| 项 | 值 |
| --- | --- |
| 语言 | Java（无 Kotlin / Compose / AndroidX） |
| 构建 | Gradle 6.7.1 + AGP 4.2.2 |
| JDK | 11（`scripts/env.ps1` 指向 `.tooling`） |
| compileSdk / targetSdk | 28 |
| minSdk | 17（重点验证 19 / Android 4.4） |
| 依赖 | `okhttp 3.12.13`、`org.videolan.android:libvlc-all:3.1.12`（`transitive=false`） |
| ABI | 仅 `armeabi-v7a`（见 `app/build.gradle` 的 `abiFilters`） |
| 压缩 | R8 `minifyEnabled` + `shrinkResources`（debug 关混淆，release 全开） |

依赖刻意保持极简：OkHttp 负责 HTTP/WebSocket，LibVLC 负责解码，其余全部手写。

### 常用脚本

| 脚本 | 作用 |
| --- | --- |
| `scripts/setup-android-env.cmd` | 下载/配置 `.tooling` 内的 JDK、Gradle、Android SDK |
| `scripts/build-debug.cmd` | 生成 `app/build/outputs/apk/debug/app-debug.apk` |
| `scripts/install-debug.cmd` | `adb install -r` 并启动 App（需先 `adb connect`） |
| `scripts/build-release.cmd` | 生成 release 包（缺 keystore 会自动创建） |
| `scripts/create-release-keystore.cmd` | 生成 `signing/fnostv-release.jks` + `keystore.properties` |
| `scripts/logcat-app.cmd` | 只看本应用日志（tag `FnOSTV`） |
| `scripts/watch-errors.cmd` | 监听 FATAL / ANR |
| `scripts/start-emulator.cmd` | 启动 Android 4.4 x86 模拟器（仅开发自测） |
| `scripts/generate-icons.py` | 用 Pillow 重新生成启动图标与 TV banner |

> 构建必须通过脚本（或先 `. .\scripts\env.ps1`）执行，因为 `gradle`/`adb` 来自 `.tooling`。

---

## 2. 包结构与职责

```text
com.fnostv.android4
├── MainActivity             总调度：生命周期、首页/列表/详情/播放切换、数据加载线程
├── SettingsActivity         登录页 + 设置页宿主
├── config/
│   ├── ServerProfile        服务器地址/账号；baseUrl 归一化（自动补协议）
│   ├── ProfileStore         配置持久化（SharedPreferences）
│   ├── ProfileValidator     地址/端口校验
│   └── ProfileValidation    校验结果
├── net/
│   ├── FnosRpcClient        RPC：WebSocket 登录、加密、签名、目录/下载直链
│   ├── FnosRestClient       影视 REST：媒体库/条目/详情/收藏/继续观看/转码
│   ├── HttpClients          OkHttp 构造（可选信任自签名证书）
│   ├── FnosCrypto           RSA/AES/sha256 等协议加密
│   ├── FnosSession(Store)   登录会话与持久化
│   ├── FnosFileEntry/List   统一文件/媒体条目模型
│   ├── FavoriteStore        本地收藏
│   └── RecentPlaybackStore  本地继续观看
├── media/
│   ├── MediaCenterGateway   影视节点取数（只走 REST，不再回退文件树）
│   ├── MediaLibraryClassifier  名称/路径推断电影/剧集
│   └── MediaIndexStore      本机媒体索引
├── player/
│   ├── PlayerEngine         播放器接口（prepare/start/pause/seek/speed/surface…）
│   ├── SystemPlayerEngine   MediaPlayer（MTK 硬解 + 视频层），用于原画直连
│   ├── VlcPlayerEngine      LibVLC，用于转码 HLS / 特殊容器 / 兜底
│   ├── PlaybackStrategy     按片源选择解码/缓存/流畅档
│   └── PlaybackOptions      策略结果（不可变配置对象）
├── tv/
│   ├── RemoteKeyHandler     遥控器按键 → RemoteActions
│   ├── RemoteActions        宿主需实现的动作集合
│   └── BackNavigationState  返回键优先级状态机
├── ui/                      全部界面与自绘组件（见第 4 节）
└── util/                    AppPrefs（本地偏好）、Constants、Logger（tag=FnOSTV）
```

---

## 3. 关键流程

### 3.1 启动与登录

1. `MainActivity.onCreate` → 读 `ProfileStore`；没配置就拉起 `SettingsActivity`。
2. `SettingsActivity` 登录表单只让用户填 **地址（不含协议）**，协议由「使用 HTTPS」决定：
   - `ServerProfile.normalizeBaseUrl()` 自动补 `http://` 或 `https://`，去掉尾部 `/`
   - `ProfileValidator` 校验端口范围、空格等
   - 需要自签名证书时勾选「信任证书异常」→ `HttpClients.builder(true)` 全信任
3. `FnosRpcClient.login()` 走 WebSocket + 加密登录，成功后写 `FnosSessionStore`。
4. 返回 `MainActivity` 后进入首页；首页数据全部通过 `FnosRestClient` 拉取。

### 3.2 首页数据

`MainActivity.updateHomeCounts()`：

- 先只刷新侧栏计数（不做本地兜底卡片）
- 后台线程依次取：`mediaCounts()`、`mediaLibraries()`、`recentItems()`、`favoriteItems()`，
  再按媒体库各取 12 条构建分区
- 回到主线程：更新侧栏、媒体库卡片、继续观看、媒体库分区、收藏数

分区模型见 `ui/HomePosterSection(s)`；行尾「查看全部」卡片由 `NativeHomeView.moreCard()` 生成。
每一行都在 `HorizontalScrollView` 内，遥控器左右移动会自动滚动到焦点项。

### 3.3 列表 / 详情

- `NativeFileBrowserView` 负责列表（列表/横版/竖版/海报墙四种布局）、筛选/排序/收藏
- `NativeMediaDetailView` 负责详情（海报、元数据、播放源、收藏、播放）
- `MediaDetailState` 承载详情页状态；`MediaCenterGateway` 负责节点取数

### 3.4 播放

```text
NativeVideoPlayerView
└── PlayerEngine（接口）
    ├── SystemPlayerEngine（MediaPlayer → MTK 硬解，原画直连）
    └── VlcPlayerEngine（LibVLC，转码 HLS / 特殊容器 / 兜底）
```

- 引擎选择：原画 http 直链且格式系统可解 → 系统硬解；转码 HLS、RMVB/AVI 等、设置关闭硬解 → VLC
- 失败处理：系统播放器报错 → 本片换 VLC；VLC 再失败 → 软解重试一次；25 秒无首帧 → 换 VLC
  （切换前会延迟 900ms 释放视频层，避免 MTK 固件被连续起停拖崩）
- **4K HEVC + MP4** 会让这台 MTK 电视重启，转码策略里强制转 1080P（详见 CHANGELOG）
- 片源策略：`PlaybackStrategy.initialOptions()` → `PlaybackOptions`
  - HLS 转码流：短缓存低延迟
  - 局域网低风险 MP4：2500ms 网络缓存
  - 高风险（mkv/ts/HEVC/4K/超大文件）：加大缓存 + 关闭跳帧
- Android 4.4 的 VLC 侧固定软解（`setHWDecoderEnabled(false)`，`codec=avcodec`）；
  4K/HEVC 原画由 `SystemPlayerEngine` 走电视芯片硬解
- Android 5.0+：设置里开启「硬件解码优先」时才尝试 `mediacodec,iomx,all`
- 首次播放失败会自动重试；连续 3 次缓冲会自动切「流畅」档
- 音频固定 `android_audiotrack` + stereo，避免 4.4 上直通导致无声

播放器界面与按键：
- 顶部「← 标题」栏 + 底部进度条/播放键/时间/工具栏（播放类型/倍速/画面/清晰度）
- **上键聚焦工具栏**：左右切换按钮、确认执行、返回退出；聚焦时工具栏不自动收起
- **长按确认**或「信息」键打开「播放信息」面板（中文，15 秒自动收起）
- 播放中按键在 `MainActivity.dispatchKeyEvent()` 阶段优先交给播放器，避免焦点落在背景控件上被吞掉
- 播放页根视图 `focusable` 并在起播时 `requestFocus()`，控制条 5 秒无操作自动隐藏

### 3.5 返回键

`BackNavigationState.choose(playerVisible, detailVisible, browserVisible, homeVisible)`：
播放器 → 详情 → 列表 → 系统，逐层退出。

### 3.6 主题与偏好

- `AppPrefs`：`theme_mode`（dark/light/schedule）、`theme_sunrise/sunset`、
  `auto_next`、`hw_decoder`、`card_score/card_watched/card_resolution`
- `FnosTheme.applyFromPrefs()` 在 Activity 建视图前调用，返回签名串
- `MainActivity.onResume()` 比较 **主题签名** 与 **卡片样式签名**，变化则 `recreate()`，
  保证设置项返回后立即生效

---

## 4. UI 组件（全部手写 View）

| 组件 | 作用 |
| --- | --- |
| `FnosTheme` | 深浅两套调色板、圆角/描边/玻璃/混色工具 |
| `GlassDrawable` | 玻璃拟态背景：竖向渐变填充 + 渐变描边（可设上下不同圆角） |
| `FocusStyler` | 统一焦点样式（玻璃底 + 3dp 高亮边框），及 chip 选中/聚焦叠加规则 |
| `RoundedFrameLayout` | 子 View 圆角裁剪（封面图：上圆角、下直角） |
| `ModernSwitch` | 自绘胶囊开关（4.4 自带 Switch 在电视主题下会渲染成灰块） |
| `FnosMediaCardView` | 媒体卡片（海报/继续观看/媒体库/海报墙/列表/剧集），焦点框自绘在内容之上 |
| `FnosSidebarIconView` / `FnosActionIconButton` | 线性小图标 |
| `PosterLoader` | 海报异步加载 + 尺寸采样 + 内存缓存 |
| `StatusOverlay` | 居中状态提示 |

> 所有界面都用代码构建，没有 XML layout（`res/layout` 里的文件来自 LibVLC AAR）。

**改样式的入口**：颜色改 `FnosTheme`；通用焦点改 `FocusStyler`；卡片布局改 `FnosMediaCardView`
对应 `SHAPE_*` 分支；首页布局改 `NativeHomeView`。

---

## 5. 扩展指南

### 加一个首页分区
1. 在 `MainActivity.updateHomeCounts()` 的 REST 回调里组装 `HomePosterSection`（标题、action、条目、显示上限）
2. 在 `NativeHomeView.onHomeAction` 对应 action 里打开列表页
3. 注意 `HomePosterSections.forLibraries()` 已有「继续观看 + 各库 + 我的收藏」的顺序约定

### 加一个设置项
1. `AppPrefs` 加 key 与读写
2. `NativeSettingsView` 对应页加 `preferenceSwitch` / `settingSwitch`
3. 若影响已建好的界面，加入 `MainActivity.cardStyleSignature()` 或在 `onResume` 中触发重建

### 换/加播放器
实现 `PlayerEngine` 并在 `NativeVideoPlayerView.createPlayerEngine()` 返回；播放参数走
`PlaybackOptions`，不要在各处散落硬编码。

### 新增 REST 接口
在 `FnosRestClient` 里加方法，统一走 `post/get`（内部已处理 token 与 `authx` 签名），
解析失败抛 `FnosRpcException`，UI 侧按现有 try/catch 模式处理。

---

## 6. 调试

- 日志 tag：`FnOSTV`（`Logger`），`scripts/logcat-app.cmd` 直接过滤
- 播放排查关键字：`VLC prepared` / `buffering` / `restart` / `options=decoder=...`
- 电视上崩溃抓取：

```powershell
adb logcat -d | findstr /C:"FATAL" /C:"AndroidRuntime" /C:"FnOSTV"
```

- `adb shell screencap -p /sdcard/s.png` + `adb pull` 可快速截图核对界面

## 7. 测试

仓库内是**纯 `main()` 断言用例**（不依赖 JUnit，避免额外依赖）：

```powershell
. .\scripts\env.ps1
gradle --no-daemon :app:compileDebugUnitTestJavaWithJavac   # 只验证编译
# 或用 IDE 直接运行各测试类的 main()
```

覆盖：播放策略、媒体中心网关、REST 解析、返回栈、海报采样等。

## 8. 发版清单

1. 改 `app/build.gradle` 的 `versionCode` / `versionName`
2. `scripts/build-debug.cmd` + 电视实机验证（登录、首页、播放、遥控器）
3. `scripts/build-release.cmd`
4. 若 ABI/依赖有变，确认 APK 体积与 `lib/` 内容
5. 更新 `RELEASE_NOTES.md` / `docs/CHANGELOG.md`
6. `scripts/package-source.cmd` 重新生成 `docs/fnOSTV-source-<日期>.zip`

---

## 9. 源码归档

`scripts/package-source.cmd` 会把以下内容打成 `docs/fnOSTV-source-<日期>.zip`：

- `app/src`（Java 源码 + 资源）、`app/build.gradle`、两个 proguard 文件
- 根目录 `build.gradle`、`settings.gradle`、`.gitignore`
- `scripts/`（构建/安装/打包脚本）、`docs/`（全部文档）
- `README.md`、`RELEASE_NOTES.md`、`OPEN_SOURCE.md`、`NOTICE`、`LICENSE`

不包含：`.tooling/`、`app/build/`、`signing/`、`local.properties`、`keystore.properties`、历史源码包。
解压后执行 `scripts\setup-android-env.cmd` 即可恢复完整构建环境。

---

## 10. 约定

- 不使用 AndroidX/Kotlin；新增依赖要评估对 4.4 与 APK 体积的影响
- 所有网络请求都在后台线程，UI 更新回主线程
- 对 `player`/`libVlc` 的调用一律包 `try/catch`，旧设备上任何异常都不应导致崩溃
- 新增界面请复用 `FnosTheme` / `FocusStyler`，保持焦点高亮与圆角一致
- 注释写“为什么”，不要写“是什么”
