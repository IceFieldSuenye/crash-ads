# NOTICE

## 1. AI 生成说明

本项目的界面、业务代码与文档由 AI 编码智能体生成。详见 [AI_NOTICE.md](AI_NOTICE.md)。

## 2. 第三方开源组件

本应用使用以下开源组件，均以 **Apache License 2.0** 授权。许可证全文见项目根目录 [LICENSE](LICENSE)，应用内亦可在「关于 → 查看 Apache-2.0 全文」查看。

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| AndroidX Core KTX | 1.19.1 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/core |
| AndroidX Activity Compose | 1.12.4 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/activity |
| AndroidX Lifecycle | 2.11.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/lifecycle |
| Jetpack Compose (BOM) | 2026.09.00 | Apache-2.0 | https://developer.android.com/jetpack/compose |
| Compose Material 3 | 随 Compose BOM | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-material3 |
| Compose Material Icons | 随 Compose BOM | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/compose-material |
| AndroidX DataStore Preferences | 1.3.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx/releases/datastore |
| Kotlin Coroutines | 1.10.2 | Apache-2.0 | https://github.com/Kotlin/kotlinx.coroutines |
| Kotlin Standard Library | 2.4.21 | Apache-2.0 | https://github.com/JetBrains/kotlin |

> 上述组件均通过 Google Maven / Maven Central 分发，构建时由 Gradle 自动下载，不随源码仓库附带。

## 3. 机器学习组件

| 组件 | 版本 | 许可证 | 项目地址 |
| --- | --- | --- | --- |
| ML Kit Text Recognition (Chinese) | 16.0.1 | Google ML Kit Terms of Service | https://developers.google.com/ml-kit/terms |

> OCR 识别完全在设备本地运行，模型随应用打包，不上传任何图片或文本。
