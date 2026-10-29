# 开发环境

本项目在 Windows 11 上构建。M0 使用项目目录内的 Microsoft OpenJDK 17、Android SDK 和 Gradle Wrapper，不要求系统全局安装 Java 或 Gradle。Android Studio ZIP 用于 IDE 和 SDK 工具引导，但其 JBR 25 不用于运行锁定的 Gradle 8.9。

## 项目专用工具目录

`.toolchains/` 仅保存本机工具、下载包、SDK 和 Gradle 缓存，不进入版本控制：

- `.toolchains/jdk17/`：Microsoft OpenJDK 17.0.21.0.2，运行 Gradle。
- `.toolchains/android-studio/`：官方 Windows ZIP 解压后的 Android Studio 与 JBR 25。
- `.toolchains/android-sdk/`：SDK Platform 35、Build Tools、Platform Tools、Command Line Tools。
- `.toolchains/gradle-home/`：项目 Wrapper 和依赖缓存。
- `.toolchains/downloads/`：按软件分子目录保存且已校验 SHA-256 的官方下载包。

不要使用关闭 TLS 校验、忽略包哈希或来源不明的 ARCore/SDK/JDK 镜像。实际来源和哈希见 [VERSIONS.md](VERSIONS.md)。

## SDK 组件

M0 使用：

- `platforms/android-35`
- `build-tools/35.0.0`
- `platform-tools`（当前安装 37.0.1）
- `cmdline-tools;latest`

`local.properties` 是本机文件，不提交：

```properties
sdk.dir=D:/portbable_slam/.toolchains/android-sdk
```

如果使用其他 SDK 位置，应改成自己的绝对路径。新版 Android CLI 安装包时使用斜杠格式，例如 `android --no-metrics --sdk=<SDK路径> sdk install platform-tools`；安装后必须验证可执行文件存在，不能只依据进程退出码。

## 真机连接

使用项目内 ADB：

```powershell
D:\portbable_slam\.toolchains\android-sdk\platform-tools\adb.exe devices -l
```

手机上需要开启开发者选项、USB 调试，并接受电脑的 RSA 调试授权。未连接、未经授权或离线设备不能作为有效测试目标。

## 构建与测试

推荐使用封装了项目 JDK/SDK 环境的脚本：

```powershell
.\tools\build-probe.ps1
```

默认执行 `:app:assembleDebug` 和 `:app:testDebugUnitTest`。也可传入其他 Gradle 任务，例如：

```powershell
.\tools\build-probe.ps1 -GradleArgs ':app:testDebugUnitTest'
.\tools\build-probe.ps1 -GradleArgs ':app:connectedDebugAndroidTest'
```

当前 M0 只有 JVM 单元测试；没有真机 instrumentation 测试时，应在测试报告中明确写“未执行”，不能把未运行写成通过。

后续 RTAB-Map native 构建会单独锁定 NDK、CMake、上游提交和依赖镜像，不在 M0 提前下载整套原生依赖。
