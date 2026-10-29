# Portable SLAM

Android 视觉惯性 SLAM 与三维重建研究项目。当前阶段是 **M0：一加 Ace 2 的 ARCore/Depth 真机能力检测**，尚未集成 RTAB-Map，也尚未完成 SLAM 或三维重建。

## 当前检测 APK 的功能

- 读取设备型号、Android/API、ABI、RAM 和 ARCore 版本。
- 检查 ARCore 可用性，并引导安装/更新系统 ARCore 组件。
- 创建真实 `ArSession`，检查并启用 `DepthMode.AUTOMATIC`。
- 显示 ARCore 相机预览，检查连续 `TRACKING`、CPU 相机内参和时间戳。
- 每 500 ms 检查 Full Depth16、Raw Depth16 和 Raw Confidence 的有效像素覆盖率。
- 区分新 Raw Depth 与按当前相机位姿重投影的 Raw Depth。
- 导出仅含数值指标的 JSON 报告；不保存图像，不申请网络权限。

通过条件：真实会话支持 AUTOMATIC Depth，连续跟踪至少 5 秒，Full Depth、Raw Depth、Raw Confidence 各取得至少 5 个非零有效样本，并出现至少 2 个不同时间戳的新 Raw Depth。通过该检测只是进入后续集成的门槛，不代表 SLAM 精度或重建质量已经验收。

## 技术栈

- Kotlin / Android Views / GLSurfaceView / OpenGL ES
- ARCore 1.57.0
- minSdk 26、target/compile SDK 35、arm64-v8a
- AGP 8.7.3、Kotlin 2.0.21、Gradle 8.9、JDK 17

## 构建

环境准备和项目专用工具目录见 [docs/SETUP.md](docs/SETUP.md)。配置本机 SDK 路径后，在 Windows PowerShell 中运行：

```powershell
.\tools\build-probe.ps1
```

该脚本优先使用各电脑的 `JAVA_HOME` / `ANDROID_HOME` 和 `local.properties`，再回退到原开发机的项目内工具目录；等价直接命令为 `.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest`（需先配置环境）。

Debug APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。独立交付副本为 [PortableSlam-ARCoreProbe-v0.1.0.apk](dist/PortableSlam-ARCoreProbe-v0.1.0.apk)，SHA-256：`78e1950c61de116ecce95c7a2dc2bad97f2bf21d37e05cf7a2da33a323ed2b3b`。不要把模拟器运行结果当作 ARCore/Depth 真机验证。

## 在其他电脑上使用

只安装 APK 不需要开发环境；重新编译只需 Git、JDK 17 和指定 Android SDK，不需要复制原电脑的 `.toolchains`。详见 [其他电脑配置指南](docs/SETUP_OTHER_COMPUTER.md)。

仓库地址：`git@github.com:Evenstar-E/Portable_SLAM.git`（也支持 `https://github.com/Evenstar-E/Portable_SLAM.git`）。

## 真机步骤

1. 在一加 Ace 2 上开启开发者选项和 USB 调试，连接电脑并信任调试主机。
2. 安装 Debug APK，或由 Android Studio 部署。
3. 打开 App，允许相机权限；按提示安装/更新 Google Play Services for AR（ARCore）。
4. 点击开始检测，在纹理丰富的室内区域缓慢移动手机，避免白墙和快速运动。
5. 等待连续跟踪和三类深度样本达到门槛，点击“导出诊断报告”。
6. 保留 JSON 报告用于判断能否进入 RTAB-Map 集成。如果 ARCore 或 Depth 明确不支持，按已确认方案更换兼容参考机。

详细步骤见 [docs/DEVICE_PROBE.md](docs/DEVICE_PROBE.md)。

## 隐私与权限

当前产品检测包只申请相机权限。ARCore 安装/更新由系统组件处理，可能需要网络；本 App 不包含 `INTERNET` 权限、账号、分析上报或云端上传。诊断报告只有用户主动通过系统文件选择器导出时才离开 App 私有内存。

## 项目状态

- [x] ARCore/Depth 检测 App 源码
- [x] 本机 SDK 配置、Debug APK 构建、10 项 JVM 测试与 Lint
- [x] clean 后完整构建复验
- [ ] 一加 Ace 2 真机检测报告
- [ ] 官方 RTAB-Map Android 基线构建与验证
- [ ] 产品 SLAM/地图/重建功能

后续技术边界与验收标准以[已批准的完整规划](docs/PLAN.md)为准。
