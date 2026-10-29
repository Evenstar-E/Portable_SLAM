# Third-Party Notices

当前文件记录 M0 检测 APK 直接使用的组件；完整传递依赖清单和随包文本将在构建后根据 Gradle 依赖解析结果核对，不能把本页当作最终发布审计。

## ARCore 1.57.0

- 组件：`com.google.ar:core:1.57.0`
- 来源：Google 官方 Maven 仓库
- 许可：Google ARCore SDK 对应的 Google 软件许可条款，不按开源许可证处理。
- 官方条款与下载信息：https://developers.google.com/ar/develop/downloads

## Kotlin 2.0.21

- 组件：Kotlin Android Plugin / Kotlin Standard Library
- 许可：Apache License 2.0
- 项目：https://github.com/JetBrains/kotlin

## Android Gradle Plugin 8.7.3

- 组件：`com.android.application`
- 许可：Apache License 2.0
- 来源：Google 官方 Maven 仓库

## Gradle 8.9

- 组件：Gradle Build Tool
- 许可：Apache License 2.0
- 项目：https://gradle.org/

## JUnit 4.13.2（仅测试）

- 组件：`junit:junit:4.13.2`
- 许可：Eclipse Public License 1.0
- 项目：https://junit.org/junit4/

## 后续组件

RTAB-Map 尚未接入。接入后必须补充其 BSD-3-Clause 正文、上游版权信息，以及实际打包的 OpenCV、PCL、Boost、GTSAM/g2o、VTK、SQLite、Eigen 等组件通知；不使用的可选组件不应写成已打包依赖。
