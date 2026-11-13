# fnOSTV Android（TV 原生客户端）

> **项目来源**：本项目基于 [**XDORG-N1/fnOSTV**](https://github.com/XDORG-N1/fnOSTV) **二次开发**而来（MIT 协议）。
> 原始项目版权归 **XDORG-N1** 所有；本仓库在其基础上针对 Android 4.4 电视做了适配与扩展
> （双引擎播放器、IPTV 电视直播、选集/播放信息面板、稳定性修复等），改动明细见 [docs/CHANGELOG.md](docs/CHANGELOG.md)。
> 使用、分发或再次二开时，请保留原项目的 MIT 许可文本与署名。

面向 **Android 4.4 电视/盒子**的飞牛影视（fnOS）轻量原生客户端。界面全部用原生 View 手写，
不依赖 WebView、Kotlin、Compose 或 AndroidX；播放为双引擎（电视硬解 + LibVLC 兜底），遥控器全程可操作。

- 包名：`com.fnostv.android4`
- 版本：`1.0.1`（versionCode 3）
- 最低系统：**Android 4.2 / API 17**（重点适配 Android 4.4 电视）
- 编译/目标：API 28
- 体积：release APK ≈ 16MB（只打包 `armeabi-v7a`，主要体积是 `libvlc.so`）
- 协议：MIT

---

## 界面预览

| 首页 | 剧集页 |
| --- | --- |
| ![首页](docs/screenshots/home.png) | ![剧集页](docs/screenshots/series.png) |

| 播放器（HUD） | 播放信息面板 |
| --- | --- |
| ![播放器](docs/screenshots/player.png) | ![播放信息](docs/screenshots/player-info.png) |

- 首页：媒体库、继续观看、各分区横向卡片，全部来自服务端
- 剧集页：对齐网页 `/v/tv/season/:guid`，季海报 + 简介 + 评分/清晰度角标 + 集数方格
- 播放器：底部工具栏（播放类型 / 倍速 / 画面 / 清晰度），上键可用遥控器聚焦
- 播放信息：播放类型、解码方式、片源、分辨率、格式/通道、进度、状态、缓冲（全中文）

---

## 功能一览

- 登录：直接填 `IP:端口` 或 `域名:端口`，可勾选 **HTTPS** 与 **信任自签名证书**
- 首页：媒体库卡片 + 继续观看 + 各媒体库分区（**全部来自服务端**，一行可左右滚动，行尾有「查看全部」）
- 分类：全部 / 电影 / 电视节目 / 电视直播 / 其他、收藏、搜索
- 详情页：海报、评分、年份、简介、剧集/版本、播放源切换、收藏
- 播放：原画直连走电视硬解（4K HEVC/H.264），转码流与特殊容器走 LibVLC；失败自动回退，自动重试
- 播放器：播放类型按钮（硬解/软解/转码）、播放信息面板（长按确认或工具栏进入）、横向选集面板
- 选集缩略图：可视区域即时加载，滑动实时补图，缺失剧照自动兜底
- 电视直播：支持 IPTV 播单（m3u / txt，http(s) 或本地文件），左栏固定分类（央视频道/卫视频道/电影频道/数字频道/儿童频道/地方频道/综艺频道/纪录频道）、右栏频道列表（带台标），背后直接播放；选台/掉线自动重连
- 稳定性：电视刚重启过会自动进入「安全模式」（改用 VLC 解码），避免同一片源反复把电视拖崩
- 设置：播放偏好（自动连播、硬件解码优先）、外观（深色/浅色/日升日落 + 卡片角标）、关于
- 遥控器：暂停/快进快退/倍速/画面模式/清晰度/数字键跳转；上键聚焦工具栏，长按确认看播放信息

## 快速开始（开发者）

```powershell
# 1) 首次准备本地 SDK/JDK/Gradle（写入 .tooling/）
scripts\setup-android-env.cmd

# 2) 生成 debug APK
scripts\build-debug.cmd

# 3) 安装到电视/盒子（adb 需先 connect）
adb connect 10.0.0.40:5555
scripts\install-debug.cmd

# 4) 看日志
scripts\logcat-app.cmd
```

Release 包：

```powershell
scripts\create-release-keystore.cmd   # 只在第一次执行，生成 signing/fnostv-release.jks
scripts\build-release.cmd
```

打包源码（发给别人/存档，可随时继续开发）：

```powershell
scripts\package-source.cmd
# 产物：docs\fnOSTV-source-<日期>.zip（约 0.3MB，含源码/脚本/文档/签名密钥，不含 SDK 与构建产物）
```

当前仓库内已放好最新源码包与成品 APK：`docs/fnOSTV-source-20261105.zip`、`docs/fnOSTV-1.0.1.apk`。

> 包里**包含** `signing/`（debug/release keystore）、`keystore.properties`、`local.properties`，
> 解压即可直接出签名包；请勿把该压缩包公开分发。

> `.tooling/`、`local.properties`、`keystore.properties`、`signing/`、`app/build/` 都是本地产物，
> 已在 `.gitignore` 中排除，不会进入源码包。

## 文档

| 文档 | 内容 |
| --- | --- |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | 电视端使用说明：安装、登录、遥控器按键、常见问题 |
| [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) | 开发说明：架构、目录、关键流程、扩展方式、构建与调试 |
| [docs/CHANGELOG.md](docs/CHANGELOG.md) | 近期重构与行为变更记录 |
| [docs/fnOSTV-source-20261105.zip](docs/fnOSTV-source-20261105.zip) | 源码压缩包（可直接解压继续开发） |
| [docs/fnOSTV-1.0.1.apk](docs/fnOSTV-1.0.1.apk) | 已签名的发布版 APK |
| [docs/fnos-interface-map.md](docs/fnos-interface-map.md) | fnOS 接口梳理（历史资料） |
| [RELEASE_NOTES.md](RELEASE_NOTES.md) | 当前版本能力与已知限制 |
| [OPEN_SOURCE.md](OPEN_SOURCE.md) / [NOTICE](NOTICE) | 开源使用与第三方组件声明 |

## 目录结构（简述）

```text
app/src/main/java/com/fnostv/android4
├── MainActivity.java        首页/列表/详情/播放的总调度
├── SettingsActivity.java    登录与设置
├── config/                  服务器配置、校验、持久化
├── net/                     RPC(WebSocket)、REST、会话、收藏/最近播放
├── media/                   影视中心网关、分类器、索引存储
├── player/                  播放器抽象、系统硬解引擎、VLC 引擎、播放策略
├── tv/                      遥控器按键分发与返回栈
├── ui/                      全部原生界面与主题组件
└── util/                    偏好、常量、日志
```

## 已知限制

- 仅打包 `armeabi-v7a`；如需在 arm64 设备上运行请自行加回 ABI（会显著增大体积）。
- 4K HEVC 的 **MP4** 直连会让 MTK 固件重启，已自动改为 1080P 转码；MKV 封装的 4K HEVC 可原画直连。
- 4K H.264 转码流在 4.4 上软解跟不上，因此转码分辨率固定 1080P。
- 收藏以本地为主，并与服务端收藏合并展示；服务端收藏的增删目前在网页端完成。
- 服务器管理类功能（媒体库管理、任务计划、用户管理）不在 TV 端提供。

## 开源协议

MIT License，详见 [LICENSE](LICENSE)。第三方组件（LibVLC / OkHttp 等）声明见 [NOTICE](NOTICE)。
