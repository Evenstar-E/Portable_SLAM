# 在其他电脑上使用

## 只安装并试用 APK：不需要开发环境

1. 从仓库下载 [PortableSlam-ARCoreProbe-v0.1.0.apk](../dist/PortableSlam-ARCoreProbe-v0.1.0.apk) 并传到一加 Ace 2。
2. 对照 [SHA256SUMS.txt](../dist/SHA256SUMS.txt) 校验文件。
3. 在手机上允许安装未知来源应用，然后安装并打开。
4. 按 App 提示安装/更新 Google Play Services for AR（ARCore）。这一步可能需要网络，具体可用渠道取决于手机 ROM 和地区。

不需要 JDK、Android SDK、Gradle、WSL、Docker、NDK 或 CMake。只有想通过电脑执行 `adb install` 时，才需要单独安装 Android Platform Tools。

## 从源码重新编译：Windows 最简配置

### 必需软件

- Git。
- **JDK 17**，例如 Microsoft OpenJDK 17 或 Eclipse Temurin 17。
- Android SDK：
  - Platform `android-35`
  - Build Tools `35.0.0`
  - Platform Tools（用于 ADB）

可以用 Android Studio 的 SDK Manager 安装这些组件。新版 Android Studio 自带 JBR 25 时，IDE 可以继续使用它，但 **Gradle 8.9 的 Java 必须单独指向 JDK 17**。

首次构建需要联网，由 Wrapper 下载 Gradle 8.9 并校验 SHA-256，再从 Google Maven/Maven Central 下载 ARCore 和编译依赖。

### 不需要复制的内容

不要从原电脑复制 `.toolchains/`、`.gradle/`、`local.properties` 或构建输出。仓库没有包含这些本机文件。M0 也不需要 WSL、Docker、NDK、CMake 或 RTAB-Map 原生依赖。

### 获取源码

```powershell
git clone git@github.com:Evenstar-E/Portable_SLAM.git
cd Portable_SLAM
```

如果电脑没有配置 GitHub SSH key，可改用：

```powershell
git clone https://github.com/Evenstar-E/Portable_SLAM.git
```

### 配置本机路径

设置 `JAVA_HOME` 和 `ANDROID_HOME` 为**你这台电脑**的实际路径，例如：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Microsoft\jdk-17'
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
```

这些命令只对当前 PowerShell 窗口生效。也可以在项目根目录新建 `local.properties` 指定 SDK（使用正斜杠）：

```properties
sdk.dir=C:/Users/你的用户名/AppData/Local/Android/Sdk
```

`local.properties` 被 `.gitignore` 排除，不会上传。环境变量优先于该文件。

### 构建和测试

```powershell
.\tools\build-probe.ps1
```

脚本按以下顺序选择环境：

1. `JAVA_HOME` / `ANDROID_HOME` / `ANDROID_SDK_ROOT`。
2. `local.properties` 中的 `sdk.dir`。
3. 原开发机的项目内 `.toolchains` 回退路径（新电脑通常没有，因此应配置前两项）。

也可以直接运行：

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

直接运行 Wrapper 时，需要自己确保 `JAVA_HOME`、`ANDROID_HOME` 和 SDK 许可已配置。

## 安装重新编译的 APK

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices -l
& "$env:ANDROID_HOME\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"
```

如果手机已安装仓库中的 Debug APK，而新电脑使用不同 Debug 签名，系统可能拒绝覆盖安装。研究原型可先卸载旧 `com.portableslam.slam.probe` 再安装；卸载会清除该检测 App 的本地状态，但不会删除你已经通过系统文件选择器导出的 JSON。

## 手机端门槛

无论在哪台电脑构建，最终都必须在真机上验证 ARCore、连续跟踪、Full/Raw Depth 和 Raw Confidence。模拟器或其他电脑编译成功不能代替一加 Ace 2 的兼容性结论。
