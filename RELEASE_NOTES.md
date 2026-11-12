# 发布版本信息

## 当前版本：1.0.0（versionCode 2）

- 包名：`com.fnostv.android4`
- 版本号：`1.0.0` / versionCode `2`
- 最低系统：Android 4.2 / API 17（重点适配 Android 4.4 电视）
- 编译与目标：API 28
- 主线：原生轻量客户端（无 WebView / Kotlin / Compose / AndroidX）
- 成品：`docs/fnOSTV-1.0.0.apk`（release 签名，约 16MB，仅 armeabi-v7a）

## 已实现功能

- 登录：`IP:端口` / `域名:端口`，支持 HTTPS 与自签名证书；RPC 被拒时自动走 REST 登录
- 首页：媒体库卡片、继续观看、各媒体库分区（均来自服务端），行内左右滚动
- 分类 / 收藏 / 搜索 / 继续观看
- 剧集页：与网页 `/v/tv/:guid`、季页 1:1（背景、季海报、简介、评分/清晰度角标）
- 详情页：元数据、评分、年份、简介、剧集、播放源切换、收藏
- 播放器：
  - 双引擎：原画直连走**电视硬解**（4K HEVC/H.264），转码流与特殊容器走 **LibVLC**
  - 自动回退：系统播放器失败 → VLC；无首帧兜底；倍速自动切 VLC
  - 播放类型按钮（`硬解 · 系统` / `硬解 · VLC` / `软解 · VLC` / `转码 · HLS`）
  - 播放信息面板（中文：播放类型、解码方式、片源、分辨率、格式/通道、进度、状态、缓冲）
  - 横向选集面板（按「下」呼出，缩略图可视即加载、滑动补图、当前集高亮）
  - 自动连播、片尾处理、已看完从头播、拖拽进度、转码拖动重建会话
- 电视直播（IPTV）：
  - 设置里填 m3u / txt 播单地址（http(s) 或本地文件）
  - 电视直播风格三栏菜单：左侧频道分类、右侧频道列表，**背景是正在播放的直播画面**
  - 播单无分组时按频道名自动归类；直播掉线自动重连；频道失效提示换台
- 遥控器：
  - 确认暂停 / 左右快退快进 / 下键选集 / 菜单倍速 / 数字键按百分比跳转
  - **上键聚焦底部工具栏**（左右切换按钮、确认执行、返回退出）
  - **长按确认**或遥控「信息」键直接打开播放信息
- 设置：播放偏好（自动连播、硬件解码优先、原画直连）、外观（深色/浅色/日升日落、卡片角标）、关于
- 收藏与服务端收藏合并展示；本地继续观看记录

## 播放器实现

```text
NativeVideoPlayerView
└── PlayerEngine
    ├── SystemPlayerEngine   MediaPlayer → MTK 硬解（原画直连）
    └── VlcPlayerEngine      LibVLC（转码 HLS / 特殊容器 / 兜底）
```

引擎选择规则：

- 原画直连（http 直链）且格式系统可解 → 系统硬解
- 转码 HLS（本地 m3u8）、RMVB/AVI/FLV、设置关闭硬解 → VLC
- **4K HEVC + MP4** → 强制服务器转码（该组合会让 MTK 固件重启）
- 10bit 及以上 HEVC → 服务器转码

## 构建

```powershell
scripts\setup-android-env.ps1     # 首次：准备本地 SDK/JDK/Gradle
scripts\build-debug.ps1           # debug APK
scripts\build-release.ps1         # 已签名 release APK
scripts\install-debug.ps1         # adb 安装（需先 adb connect）
scripts\package-source.ps1        # 打包源码（含签名）
```

## 已知限制

- 仅打包 `armeabi-v7a`；arm64 设备需自行加回 ABI
- 4K HEVC 的 **MP4** 无法原画直连（MTK 固件问题），自动降为 1080P 转码；转成 MKV 后可原画
- 4K H.264 转码流在 4.4 上软解跟不上，因此转码分辨率固定 1080P
- 服务端收藏的增删在网页端完成
- 服务器管理类功能（媒体库、任务计划、用户管理）不在 TV 端提供
