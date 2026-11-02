# 发行版签名与构建说明

本项目发行版（release）使用自带 keystore 签名，APK 体积比 debug 版小约 30%（debug 含调试信息且不裁剪）。

## 签名材料

| 文件 | 说明 |
| --- | --- |
| `keystore/release.jks` | 发行签名证书（JKS，RSA 2048，有效期 30 年） |
| `gradle.properties.local` | store/key 密码与别名（已被 .gitignore 忽略） |

当前凭据（**请自行备份，勿公开**）：

- alias：`xmbox`
- storePassword / keyPassword：保存在本地 `gradle.properties.local` 中（已 gitignore，不随仓库公开）

> 以后发布更新必须使用同一 keystore，否则无法覆盖安装，用户需要卸载重装。
> 建议把 `keystore/release.jks` 和 `gradle.properties.local` 单独备份到安全位置。

## 构建命令

```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleMobileArm64_v8aRelease
```

产物：`app/build/outputs/apk/mobileArm64_v8a/release/mobile-arm64_v8a.apk`

## 与 debug 版的区别

| 项 | debug | release |
| --- | --- | --- |
| 体积（arm64 手机版） | 约 50–52 MB | 约 35–37 MB |
| R8 混淆/裁剪 | 关闭 | 开启 |
| 资源裁剪 | 关闭 | 开启 |
| 调试信息 | 有 | 无 |
| 签名 | Android Debug | `CN=XMBOX Offline` |

两种签名不兼容，从 debug 版切换需要先卸载（会清空应用内数据）；已下载到 `/sdcard/Download/XMBOX/` 的视频文件不会被卸载删除。
