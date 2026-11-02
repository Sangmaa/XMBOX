# 离线下载（手机版）

本分支在 mobile flavor 上新增「下载到本地」能力，用于断网环境提前缓存剧集。

## 使用方式

1. 进入点播详情页 → 播放任意一集。
2. 播放器顶部控制栏新增下载按钮（↓）：下载当前集。
3. 剧集列表标题行新增「下载」文字按钮：打开选集弹窗，可全选/全不选，批量加入下载队列。
4. 设置 → 下载管理：显示进度百分比、已下载大小与实时速度（如“已下载 6.5MB 656KB/s”），可暂停/继续、重试、删除；点击已完成条目直接离线播放，长按删除记录与文件。HLS 分片支持断点续传（重开任务会复用已下载分片）；进程被杀后残留的“下载中”记录会自动转为“已暂停”，点继续即可续传。
5. 主界面右上角（收藏、历史图标右侧）新增下载图标，可直接进入下载管理。

## 文件位置

- 优先写入公共目录：`/sdcard/Download/XMBOX/<剧名>/<集名>.mp4`
- 无存储写权限（或 Android 11+ 未授予「所有文件访问」）时自动回退到应用私有目录：
  `Android/data/com.fongmi.android.tv/files/Download/<剧名>/<集名>.mp4`
- 直链文件保留原有扩展名；HLS（m3u8）下载完成后合并为 `.mp4`（内容为 MPEG-TS 流，ExoPlayer/VLC 可直接播放，若需其他播放器兼容可用 ffmpeg 重新封装）。
- 同名同集重复下载会覆盖原文件，下载记录随之更新。

## 实现要点

- 自建 OkHttp 下载引擎（`app/src/main/java/com/fongmi/android/tv/download/`）：
  - 直链：Range 断点续传（`.part` 临时文件）。
  - HLS：解析 master/media playlist（含 AES-128、EXT-X-MAP、BYTERANGE），分段落盘到 `.parts/` 后按顺序合并。
  - 识别：Content-Type、URL 路径/查询参数含 m3u8、或响应体以 `#EXTM3U` 开头都按 HLS 处理；直链下载过程中收到播放列表会自动切换到 HLS 引擎。
  - 分片若是「图片伪装」容器（JPEG/PNG 头部 + 尾部 MPEG-TS 数据，抖音/快手系 CDN 常见），自动剥离图片头后按 TS 合并，可正常播放；只有纯图片占位分片才会报错。
- 下载地址优先使用播放器当前已解析的地址（避免重新解析得到失效签名/不同 CDN）。
- 前台服务 `DownloadService` + 通知进度；任务状态持久化在 Room 表 `Download`（数据库版本 35，迁移 `MIGRATION_34_35`）。
- 地址解析复用播放器的 `SiteViewModel.playerContent` 逻辑（`DownloadResolver`），支持 spider(type 3)、type 4、push_agent 与普通站点。
  - 解析失败自动重试（最多 3 次）；`parse=1`/`jx=1` 的站点先走播放器的 `ParseJob`，失败再退回内置 WebView 嗅探（与播放器同机制），因此火火影视这类需要解析的源也能批量下载。
- 离线播放复用 `VideoActivity.start(..., "push_agent", "file://" + path, ...)`。

## 已知限制

- 需要嗅探（parse=1）的站点已支持（ParseJob + WebView 兜底），但解析服务本身不稳定时仍可能失败，点重试即可。
- 火火影视（hhplayer）这类源的分片较大，单集可能 500MB~800MB（TS 未重新封装）。
- DRM（widevine）内容无法下载。
- 下载管理页与入口仅在手机版（mobile）提供，TV/平板版未接入。
- 若剧集 URL 带有效期签名，过期后需重新解析再下载。
- 纯图片占位分片（无 TS 负载）无法下载，会明确报错。
- 旧版本遗留的损坏记录会按文件大小/文件头自动标记为「文件已损坏，请重新下载」，点重试即可。

## 构建

```bash
export JAVA_HOME=<JDK17>
export ANDROID_HOME=<Android SDK>
./gradlew :app:assembleMobileArm64_v8aDebug
# 产物：app/build/outputs/apk/mobileArm64_v8a/debug/mobile-arm64_v8a.apk
./gradlew :app:testMobileArm64_v8aDebugUnitTest
```
