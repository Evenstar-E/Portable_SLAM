# 版本与来源锁定

本文件记录 M0 的直接工具和依赖。后续 native 版本另列，不自动升级。

## Android Studio / IDE JBR

- Android Studio Rabbit 2 / 2026.2.2，内置 JBR 25.0.4；JBR 25 仅用于 IDE/SDK 工具引导，不运行 Gradle 8.9。
- 官方 ZIP：https://dl.google.com/dl/android/studio/ide-zips/2026.2.2.8/android-studio-rabbit2-windows.zip
- 官方页面 SHA-256：`a05e6742d5fd92a090ee6697c3f75706db735decf5a8543f3bbaf2514c0fd123`
- 状态：已校验后解压，实际 `java -version` 已确认。

## 项目构建 JDK

- Microsoft OpenJDK 17.0.21.0.2 LTS Windows x64 ZIP。
- 官方链接：https://aka.ms/download-jdk/microsoft-jdk-17.0.21.0.2-windows-x64.zip
- 官方 SHA-256：`49154292866386b2f4f09a3ecf3be07ab80a419149c84ec1d4419b9eec9e4b64`
- 状态：已校验后解压，实际 `java -version` 为 17.0.21.0.2。

## Android SDK Command Line Tools

- 包：`commandlinetools-win-15859902_latest.zip`
- 官方 ZIP：https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip
- 官方页面 SHA-256：`90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a`
- 状态：已校验后解压；`sdkmanager --version` 为 22.0。
- 注意：新版工具提示 sdkmanager deprecated，安装使用 `android.exe sdk` 并带 `--no-metrics`。

## Gradle / Android 插件

- Gradle 8.9 bin ZIP：https://services.gradle.org/distributions/gradle-8.9-bin.zip
- 官方 SHA-256：`d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`
- 状态：已校验并生成 Wrapper；Wrapper 配置也包含同一 `distributionSha256Sum`。
- Android Gradle Plugin：8.7.3，Google Maven。
- Kotlin Android Plugin：2.0.21，Gradle Plugin Portal / Maven Central。
- JVM/字节码目标：17。

## Android / ARCore

- compileSdk / targetSdk：35；minSdk：26；ABI：arm64-v8a。
- ARCore：`com.google.ar:core:1.57.0`，Google Maven。
- SDK Platform：`platform-35_r02`，`android.jar` 已验证存在。
- Build Tools：35.0.0，`aapt2.exe` 已验证存在。
- Platform Tools / ADB：37.0.1-15733141，`adb --version` 已验证。

## 安全约束

- 不关闭 TLS 证书校验，不忽略哈希，不使用来源不明的镜像。
- 下载包按软件放在独立目录，构建脚本保存在源码 `tools/` 或项目配置中，不把下载目录当作脚本工作目录。
- 版本升级一次只改一组依赖并重新验证；M0 不提前升级或下载 RTAB-Map 整套 native 依赖。
