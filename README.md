# 闪退广告（crash-ads）

利用 Android 无障碍服务自动识别并跳过开屏、插屏、奖励视频广告的工具。完全离线，不申请网络权限。

- 最低支持：Android 10（API 29）
- 目标版本：Android 16（API 37）
- 包名：`hey.skipad`
- 语言：Kotlin + Jetpack Compose
- 作者：IceFieldSuenye

## 功能

| 模块 | 说明 |
| --- | --- |
| 开屏跳过 | 文案、控件 ID、倒计时数字、坐标兜底多路识别 |
| 图片识别 | Android 11+ 截屏 + ML Kit 中文 OCR，纯图片按钮也能点 |
| 奖励保护 | 悬浮球确认、继续观看撤回、强制浏览弹窗退出、结束卡片关闭 |
| 视频静音 | 自动识别音量图标并静音 |
| 信息流 | 抖音等平台「广告」标签自动上滑 |
| 通知 | 静默跳过提示 + 可暂停的常驻运行通知 |
| 日志统计 | 每次命中都有记录，可查看详情 |
| 完全离线 | 无 INTERNET 权限，数据仅存本机 |

## 构建

需要 JDK 17+ 与 Android SDK（compileSdk 37）。

```bash
# 依赖仓库使用阿里云镜像（见 settings.gradle.kts）
./gradlew :app:assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`

**关于签名**：发布用的密钥库与密码（`app/release.jks`、`keystore.properties`）不随仓库分发，已在 `.gitignore` 中排除。仓库中直接构建会自动回退到 debug 签名；如需正式签名，请自备密钥库并创建 `app/release.jks` 与根目录 `keystore.properties`：

```properties
storePassword=***
keyAlias=***
keyPassword=***
```

## 使用

1. 安装 APK；
2. 打开应用，按提示开启无障碍服务「闪退广告」；
3. 建议同时开启电池白名单与后台运行权限，避免服务被系统回收；
4. 可选：授予悬浮窗权限以启用奖励广告悬浮球。

## 许可

本项目以 Apache License 2.0 发布，详见 [LICENSE](LICENSE)。

使用的开源组件：AndroidX、Jetpack Compose、Kotlin Coroutines、DataStore、ML Kit（Apache-2.0）。

## 免责声明

本项目仅供学习与个人使用。广告识别为启发式规则，可能存在误判；请遵守各应用的用户协议与相关法律法规，因使用本工具产生的任何后果由使用者自行承担。

应用界面与代码由 AI 编码智能体（DeepSeek Harness）生成，经人工配置与构建。
