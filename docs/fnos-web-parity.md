# fnOS 影视（Web）→ fnOSTV Android 4 TV 对齐开发文档

> 目标：把飞牛影视网页版（`http://<nas>:5666/v`）的**路由结构、接口调用、刮削展示逻辑**，
> 1:1 搬到 Android 4 TV 客户端 `com.fnostv.android4`。
>
> 本文所有结论均来自对线上真实站点的逆向验证（前端 chunk 源码 + 真实接口响应体），
> 不是猜测。文中标注「已验证」= 已用真实响应确认；标注「推断」= 仅从 chunk 逻辑推导。

---

## 0. 基础信息

| 项 | 值 |
| --- | --- |
| Web 根路径 | `/v` |
| API 前缀（大陆） | `/v/api/v1`（主）、`/v/api/v2`（v2 通道） |
| API 前缀（国际） | `/v/api/intl/v1`、`/v/api/intl/v2` |
| 客户端标识头 | `X-Trim-Client: web` |
| 客户端版本头 | `X-Trim-Client-Version: 631` |
| 鉴权头 | `Authorization: <token>`（token 来自 `Trim-MC-token` Cookie） |
| 签名头 | `authx: nonce=..&timestamp=..&sign=..` |
| 服务端版本 | `sys/version` → `0.9.8`，媒体服务 `0.8.42`（已验证） |
| i18n 资源 | `/v/locales/<lang>/<ns>.json`，命名空间 `media`（zh-CN 45KB，已验证） |
| 语言存储键 | `trim.media/language` |

### 0.1 签名算法（已验证）

```
sign = md5( SALT + "_" + pathname + "_" + nonce + "_" + timestamp + "_" + md5(body) + "_" + API_KEY )
```

* `SALT` = `NDzZTVxnRKP8Z0jXg1VAMonaG8akvh`
* `API_KEY` = `16CCEB3D-AB42-077D-36A1-F355324E4237`
* `pathname` = **不含** `/v/api/v1` 前缀的路径，例如 `/item/list`
* `body` = 请求体原文；GET 无查询参数时为空串
* GET 带查询串时：先按 key 排序、再整体 URL-decode，拼成 `k=v&k2=v2` 作为 body 参与签名
* `nonce` 为 6 位数字串，`timestamp` 为毫秒

TV 端实现位置：`net/FnosRestClient.java#authx / #signatureBody`。

### 0.2 登录

| 通道 | 接口 | 密码格式 |
| --- | --- | --- |
| Web v1 | `POST /v/api/v1/login` | 明文 |
| TV v2 | `POST /v/api/v2/user/loginByPassword` | `sha256(明文)` 小写 hex |

登录成功服务端下发 `Trim-MC-token` Cookie，后续请求放 `Authorization` 头。

---

## 1. Web 路由地图（已验证，来自 chunk 路由表）

| 路由 | 页面 | Web 标题 | 对应 TV 屏幕 |
| --- | --- | --- | --- |
| `/` | 首页 | 首页 | `NativeHomeView` |
| `/favorite` | 收藏 | 收藏 | `NativeFileBrowserView`（标签页模式） |
| `/list/all` | 全部 | 全部 | `NativeFileBrowserView` |
| `/list/movie` | 电影 | 电影 | `NativeFileBrowserView` |
| `/list/tv` | 电视节目 | 电视节目 | `NativeFileBrowserView` |
| `/list/live` | 电视直播 | 电视直播 | `NativeFileBrowserView` |
| `/list/other` | 其他 | 其他 | `NativeFileBrowserView` |
| `/library/:guid` | 媒体库 | 媒体库名 | `NativeFileBrowserView` |
| `/library-files/:guid` | 文件夹视图 | 文件夹视图 | `NativeFileBrowserView`（文件模式） |
| `/tv/:guid` | 电视剧 | 剧名 | `NativeFileBrowserView`（剧集：头图 + 分季卡片） |
| `/tv/season/:guid` | 季 | 季名 | `NativeFileBrowserView`（单季集数 + 分季切换） |
| `/tv/episode/:guid` | 单集 | 集名 | `NativeMediaDetailView` |
| `/movie/:guid` | 影片 | 片名 | `NativeMediaDetailView` |
| `/other/:guid` | 其他 | 标题 | `NativeMediaDetailView` |
| `/person/:guid` | 人物 | 人物名 | `NativeMediaDetailView`（人物模式） |
| `/live` | 直播 | 电视直播 | `NativeFileBrowserView` |
| `/search/*` | 搜索 | 搜索 | 搜索页（待补） |
| `/settings/account` | 帐号 | 帐号 | `SettingsActivity` |
| `/settings/preference` | 播放偏好 | 播放偏好 | `SettingsActivity` |
| `/settings/appearance` | 外观 | 外观 | `SettingsActivity` |
| `/settings/library` | 媒体库（管理员） | — | **按需求删除** |
| `/settings/users` | 用户（管理员） | — | **按需求删除** |
| `/settings/server` | 服务器（管理员） | — | **按需求删除** |
| `/settings/task` | 任务（管理员） | — | **按需求删除** |

---

## 2. Web 界面结构（已验证）

### 2.1 侧栏（媒体区）

```
首页
收藏                     0
媒体库                    ← 区块标题
  电影                    33
  动画电影                196
  MV                       1
  动画                      6
  电视剧                    1
  少儿                     25
分类                      ← 区块标题
  全部                   262
  电影                   229
  电视节目                30
  电视直播                 0
  其他                     3
```

要点：

1. **管理员同样看到媒体库行的数量**（实测 33/196/1/6/1/25）。
2. 「媒体库」是**区块标题**，其下每一行是一个媒体库（不是「影视大全」单行）。
3. 「分类」区块固定 5 项：全部 / 电影 / 电视节目 / 电视直播 / 其他。
4. 计数来源：`GET /mediadb/sum` → `{ <libGuid>: n, favorite, live, movie, total, tv, video }`。

### 2.2 首页主区（已验证渲染顺序）

```
首页                      ← 页面标题
媒体库                    ← 区块标题 → 媒体库卡片横排
继续观看                  ← 区块标题 → 海报 + 类型标签 + 「第 3 季 · 第 9 集」
（海报墙）                ← 评分 + 年份
```

* 「继续观看」条目类型标签：`电影` / `第 N 季 · 第 M 集` / `未知季 · 第 N 集`。
* 数据来源：`GET /play/list`（含 `ts`=续播秒数、`media_guid`）。
* 首页海报墙排序：`sort_column=create_time`、`sort_type=DESC`。

### 2.3 列表页工具条（推断，来自 i18n key 与 chunk）

| i18n key | 文案 | 说明 |
| --- | --- | --- |
| `layout.list.filter.filterButton` | 筛选 | 打开筛选面板 |
| `layout.list.filter.resetButton` | 重置 | 清空筛选条件 |
| `layout.list.filter.total` | 共 N 条 | 结果计数 |
| `layout.list.filter.tagMap` | 标签 | 类型/题材筛选 |
| `layout.list.filter.decade` | 年代 | 年代筛选 |
| `layout.list.filter.watched` | 观看状态 | 已看/未看 |
| `layout.list.filter.recognitionStatus` | 识别状态 | 刮削状态 |
| `layout.list.filter.type.movie/tv/liveChannel` | 电影/电视节目/电视直播 | 资源类型 |
| `layout.list.sort.sortField.createTime\|favoriteTime\|releaseDate\|title\|voteAverage` | 添加时间/收藏时间/上映时间/标题/评分 | 排序字段 |
| `layout.list.sort.sortType.asc\|desc` | 升序/降序 | 排序方向 |
| `layout.list.viewType.defaultLabel` | 视图 | 视图切换 |
| `common.menu.listViewList\|listViewPosterHorizontal\|listViewPosterVertical\|listViewPosterWall` | 列表/横向海报/纵向海报/海报墙 | 4 种视图 |
| `layout.list.favoriteTabs.all\|movie\|tv\|episode\|liveChannel\|person` | 全部/电影/电视节目/剧集/直播频道/人物 | 收藏页标签 |

### 2.4 电视剧页（已验证：剧集页 + 季页两级结构）

Web 电视剧是**两级页面**，不是「跨季平铺」：

1. `GET /tv/:guid` —— 剧集页
   * 顶部头图：海报 + 剧名 + `共 N 季 · 年份`（单季时显示 `第 1 季 · N 集`）+ 评分 + 简介。
   * 下方为**分季卡片网格**：每张卡片显示 `第 N 季` 与 `N 集`。
   * 只有一季时，卡片直接指向该季页（`single_child_guid` 快捷方式）。
2. `GET /tv/season/:guid` —— 季页
   * 顶部同样的头图。
   * 集数网格：每张卡片 `第 N 集. 标题` + 两行简介 + 时长（`formattedDuration`，如「41 分钟」）+ 已看角标。
   * 多季时显示分季切换条，可在季之间跳转。

数据来源：`GET /season/list/<tvGuid>` 取季列表，`GET /episode/list/<seasonGuid>` 取该季集列表；
头图信息（剧名/简介/海报/评分）取自该季条目 `GET /item/<seasonGuid>`（`GET /item/<tvGuid>` 返回 `null`）。
集顺序按 `episode_number` 数值升序，未知集号（`-1`）排最后。

TV 端对应实现：`MainActivity#loadSeriesNode` 组装 `MediaLoadResult`，
`NativeFileBrowserView#showSeries` 渲染头图 + 分季卡片（剧集页）或头图 + 集数网格 + 分季切换条（季页）。

### 2.5 设置页侧栏

```
帐号
  修改密码
  播放偏好
  外观
影视服务器（管理员）      ← 按需求 ④ 删除
  媒体库
  用户
  服务器
  任务
```

---

## 3. 接口地图（v1，已验证存在性与响应结构）

### 3.1 媒体库 / 条目

| 接口 | 方法 | 说明 | 响应要点 |
| --- | --- | --- | --- |
| `/mediadb/list` | GET | 媒体库列表 | `data[]`: `guid,title,poster,posters[],category,view_type,poster_type,refresh_disabled` |
| `/mediadb/sum` | GET | 各类计数 | `{libGuid:n, favorite, live, movie, total, tv, video}` |
| `/mdb/list` | GET | 媒体库详情列表 | `data[]` 含 `guid,name,category,dir_list[],lan,posters[]` |
| `/mdb/:guid` | GET | 媒体库详情 | `guid,name,category,dir_list[],lan,posters[],skip_filesize,prefer_local_nfo` |
| `/item/list` | POST | 条目分页 | `data{mdb_name,mdb_category,top_dir,dir,total,list[],jump_list}` |
| `/item/:guid` | GET | 条目详情（刮削） | 条目全字段 + 演员/导演 |
| `/item/watched` | POST | 标记已看 | — |
| `/item/favorite` | PUT / DELETE | 收藏/取消收藏 | — |
| `/item/watched`(DELETE) | DELETE | 清除已看 | — |
| `/item/:guid/playing` | POST | 上报播放进度 | — |
| `/item/refresh` | POST | 重新刮削 | — |
| `/favorite/list` | POST | 收藏列表 | `data{total,list[]}` |
| `/media/itemfile/:guid` | GET | 文件夹视图 | 本机返回 `null`（该版本未实现） |
| `/search/list` | GET | 搜索 | 参数未知，返回 `Invalid Params`（本机搜索索引为空） |

`/item/list` 请求体（已验证可用）：

```json
{
  "ancestor_guid": "<媒体库或目录 guid>",
  "tags": { "type": ["Movie"] },
  "sort_type": "DESC",
  "sort_column": "create_time",
  "exclude_grouped_video": 1,
  "page": 1,
  "page_size": 50
}
```

> **分页**：`data.total` 是全量条数，`page_size` 可放大。网页端按需分页；
> TV 端为「一次拉全」需求，必须循环 `page` 直到累计数量 ≥ `total`
> （历史 bug：只取第 1 页 50 条，导致库被截断）。

条目（`item/list` / `favorite/list` / `play/list`）字段全集（已验证）：

```
guid, lan, imdb_id, trim_id, tv_title, parent_guid, parent_title,
title, type(Movie|TV|Season|Episode|Directory|LiveChannel|Video),
poster, poster_width, poster_height, poster_list[], backdrops[],
vote_average, release_date, runtime, duration, overview,
genres[], production_countries[], content_ratings[],
is_favorite, watched, watched_ts, ts,
season_number, episode_number, number_of_seasons, number_of_episodes,
local_number_of_seasons, local_number_of_episodes, status,
ancestor_guid, ancestor_name, ancestor_category,
path, file_name, media_stream{resolutions[],audio_type,color_range_type},
actor_list[], director_list[], single_child_guid
```

### 3.2 剧集

| 接口 | 方法 | 说明 | 响应要点 |
| --- | --- | --- | --- |
| `/tv/:guid` | GET | 电视剧详情 | **本机未实现**：`{"message":"Not Implemented, please update."}` → 必须改用下面两个接口 |
| `/season/list/:tvGuid` | GET | 季列表 | `data` 是**数组**；`guid,title("Season 1"),season_number,parent_guid,poster`；`season_number=-1` 且 `title=""` 表示「未知季」 |
| `/episode/list/:seasonGuid` | GET | 集列表 | `data` 是**数组**；`guid,title("小公主米娅 第100话 4K....mp4"),season_number,episode_number,parent_guid` |

### 3.3 播放 / 转码

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/play/list` | GET | 继续观看（`ts`=续播秒，`media_guid`） |
| `/play/info` | POST | 取播放信息 |
| `/play/play` | POST | 起播/转码 |
| `/play/record` | POST | 播放保活（**TTL ≈ 60s，需每 ~12s 一次**） |
| `/play/quality` | POST | 切清晰度 |
| `/play/setConfigByItem` | POST | 按条目设置播放配置 |
| `/media` | POST | `query` / `getPlayLink` / `quit` |
| `/media/p` | POST | `resetQuality` / `resetAudio` / `resetSubtitle` / `quit` / `checkPlayLink` / `transcodeStatis` |

转码参数（已验证可用）：

```
video_encoder=h264, resolution=1080, bitrate=10000000, audio_encoder=aac, channels=2
```

返回的 HLS 播放列表需要在本地改写：相对分片地址 → `绝对地址?authorization=<token>`。

### 3.4 其它

| 接口 | 方法 | 说明 |
| --- | --- | --- |
| `/stream/list/:guid` | GET | `data{files[],video_streams[],audio_streams[],subtitle_streams[]}` |
| `/person/list/:guid` | POST | `data{total,list[{person_guid,name,job(Director|Actor),role,order,biography,profile_path,gender}]}` |
| `/person/:guid` | GET | 人物详情 |
| `/person/item/list` | POST | 人物作品 |
| `/tag/list` | GET | `genres[],resolutions[],color_range[],audio_type[],locate[],decades[],recognition_status[]` |
| `/tag/genres` | GET | `[{id,value}]` |
| `/server/info` `/server/gpu/list` `/server/pools` `/server/allVols` | GET | 服务器信息 |
| `/sys/config` `/sys/version` `/sys/options` | GET | 系统信息 |
| `/task/running` `/task/schedule/list` `/task/uselessList` | GET | 任务 |
| `/user/info` | GET | 当前用户 |
| `/user/passwd` | POST | 改密码 |
| `/subtitle/search` `/subtitle/download` `/subtitle/predownload` | POST | 字幕 |
| `/scrap/search` `/scrap/rescrap` | POST | 刮削 |

---

## 4. Web → TV 实施计划（按依赖顺序）

### 阶段 A：导航骨架（解决「返回显示真实目录」「结构不同」）

**A1. 引入显式导航栈**
`MainActivity` 新增 `Deque<MediaNavEntry>`（`path` + `title` + `mode`）。
所有「打开列表/详情」的入口都 `push`，`goBack()` 只做 `pop`；
栈空时回首页。**删除** `mediaParentPath()` 里 `parentPath(value)` 的文件系统回退。

**A2. 媒体条目身份统一为 guid**
`FnosRestClient.isMediaItemType()` 需包含 `directory`，
否则 `type=Directory` 的条目会退化成真实文件路径，
点进去就变成「真实目录」。这正是「返回一直显示真实目录」的根因。

**A3. 去掉真实文件系统回退**
`MediaCenterGateway.load()` 在媒体模式下**不得**调用 `loadFiles()`；
REST 失败就显示「无数据」。真实文件浏览（`/library-files/:guid`）走独立入口。

**A4. 侧栏对齐**
- 「媒体库」区块 → 逐库渲染（名称 + 计数），删除占位「影视大全」行。
- 「分类」区块 → 全部/电影/电视节目/电视直播/其他 + 计数。

### 阶段 B：内容页对齐

**B1. 列表页工具条**：排序字段（添加时间/收藏时间/上映时间/标题/评分）× 升降序 + 视图切换（列表/横向海报/纵向海报/海报墙）。
**B2. 收藏页标签**：全部/电影/电视节目/剧集/直播频道/人物。
**B3. 电视剧页**：剧集页（头图 + 分季卡片）→ 季页（头图 + 集数网格 + 分季切换条），集卡片标题「第 N 集. 标题」。
**B4. 人物页**：`/person/:guid` → 演职人员 + 作品列表。
**B5. 直播页**：`/live` → 频道列表（本机 `live` 计数为 0，保留空态文案「暂无频道」）。
**B6. 搜索页**：`/search/*`。

### 阶段 C：播放

**C1. 自动续播**：走 `/play/list` 的 `ts`。
**C2. 硬件解码优先**：能直解就直解；不能则 `POST /play/play` 转 H.264/AAC。
**C3. 自动下一集**：
* 电视剧（`tv:<guid>|all`）→ 队列 = 全剧集，按季/集排序；
* 目录型多文件列表 → 队列 = 该列表；
* 电影 / 混合网格 → **不自动连播**。

### 阶段 D：设置

**D1.** 删除「影视服务器」区块及其 4 个页面。
**D2.** 保留「帐号」（修改密码）/「播放偏好」/「外观」。
**D3.** 外观主题：跟随系统 → **日升日落**（`MODE_SCHEDULE`，按 sunrise/sunset 切换）。

---

## 5. 已知服务端限制（实现时必须绕开）

| 限制 | 表现 | 应对 |
| --- | --- | --- |
| `GET /tv/:guid` | `{"message":"Not Implemented, please update."}` | 用 `/season/list` + `/episode/list` |
| `GET /media/itemfile/:guid` | `null` | 文件夹视图降级为普通列表 |
| `GET /search/list` | `Invalid Params` | 搜索页降级为本地过滤 |
| `/season/list`、`/episode/list` | `data` 是**数组**而非 `{list:[]}` | 解析需兼容两种形态（`dataArray()`） |
| `season_number = -1` | 「未知季」 | UI 显示「未知季」 |
| `episode_number = -1` | 集号未知 | UI 显示「未知集」 |
| 管理员侧栏 | 媒体库行 hover 出菜单而非计数 | **实测管理员也显示计数**，按实测实现 |

---

## 6. i18n 关键文案（zh-CN，已验证）

| key | 文案 |
| --- | --- |
| `layout.dataLayout.noData` | 无数据 |
| `layout.dataLayout.loadFailed` | 加载失败 |
| `layout.dataLayout.returnHome` | 返回首页 |
| `layout.dataLayout.noLiveChannels` | 暂无频道 |
| `layout.details.castAndCrew.title` | 演职人员 |
| `layout.details.fileInfo.title` | 文件信息 |
| `layout.details.fileInfo.size` | 文件大小： |
| `layout.details.fileInfo.location` | 文件位置： |
| `layout.details.watchedProgress.remaining` | 剩余 |
| `layout.details.playlist.switchToCardView` | 切换到卡片视图 |
| `layout.details.playlist.switchToButtonView` | 切换到按钮视图 |
| `layout.subheading.season.number` | 第 N 季 |
| `layout.subheading.season.episodes` | N 集 |
| `layout.subheading.season.unknown` | 未知季 |
| `layout.subheading.episode.number` | 第 N 集 |
| `layout.subheading.episode.unknown` | 未知集 |
| `layout.subheading.tv.seasons` | N 季 |
| `layout.subheading.tv.episodes` | N 集 |
| `layout.list.continueWatching` | 继续观看 |
| `layout.list.viewType.defaultLabel` | 视图 |
| `common.libraryCategory.movie/tv/live/mix/others` | 电影/电视节目/直播/混合/其他 |
| `common.resourceType.*` | 目录/剧集/直播频道/影片/其他/人物/季/电视剧/视频 |

---

## 7. 验收清单

- [ ] 侧栏与 Web 一致：首页 / 收藏(count) / 媒体库区块(逐库+计数) / 分类区块(5 项+计数)
- [ ] 首页区块顺序：媒体库 → 继续观看 → 海报墙
- [ ] 媒体库 / 分类列表能拉到**全量**条目（>50 条不截断）
- [ ] 电视剧为两级页面：剧集页（头图 + 分季卡片）→ 季页（头图 + 集数网格 + 分季切换条）
- [ ] 媒体库列表与计数完全来自服务端（`/mediadb/list` + `/mediadb/sum`），服务端增删库后重启/回前台即同步
- [ ] 任意层级「返回」都回到 Web 对应页面，**绝不出现真实目录树**
- [ ] 电影点开是详情页（刮削元数据齐全），播放正常
- [ ] 电视剧/多文件目录自动连播；电影不自动连播
- [ ] 设置中无「影视服务器」区块；外观主题含「日升日落」
- [ ] 收藏页有 6 个标签页
- [ ] 列表页有排序 + 视图切换
