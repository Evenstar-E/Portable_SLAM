# M0 实施记录

## 已确认环境

- Windows 11，项目磁盘空间充足。
- 初始未发现系统 Java、ADB、Android SDK、Gradle、CMake、Ninja 或 Docker。
- `winget` 软件源出现证书主机名不匹配；未关闭证书校验，改用官方 ZIP 和 SHA-256。
- Android Studio 2026.2.2 / JBR 25.0.4 已校验解压；Gradle 使用独立 Microsoft OpenJDK 17.0.21.0.2。
- SDK Command Line Tools 22.0、Platform Tools 37.0.1、Platform 35 r02、Build Tools 35.0.0 已安装并验证。
- WSL 尚未形成可用 Linux 构建环境；M0 不依赖 WSL。

## 工具链与代码问题处理

- 旧 `sdkmanager --licenses` 的 PowerShell 管道没有真正接受许可，包被跳过；读取日志后改用新版 `android.exe sdk install --no-metrics`，并逐一验证 `adb.exe`、`android.jar`、`aapt2.exe`。
- 首次 Kotlin 编译失败：`ImageFormat` 包名错误及 inline Lambda 限制。修复后构建成功。
- AGP 曾隐式安装 Build Tools 34；项目已显式锁定 `buildToolsVersion = "35.0.0"`。
- ARCore 1.57 的 `transformDisplayUvCoords` 弃用警告已通过 `transformCoordinates2d(VIEW_NORMALIZED -> TEXTURE_NORMALIZED)` 消除。
- Lint 发现的备份规则、图标、过度绘制和导出文案问题已修复；竖屏与 arm64-only 是明确产品范围，相关警告仅局部抑制。

## 当前验证结果

| 项目 | 状态 |
|---|---|
| 检测 App 源码 | 编译通过 |
| JVM 单元测试 | 10/10 通过，0 skipped/failures/errors |
| Android Lint | 通过，`No issues found` |
| Debug APK | 已生成；权限与 ABI 经 AAPT 验证 |
| clean 后完整复验 | 已通过；测试任务使用 Gradle 缓存中的已通过结果 |
| 最终交付 APK | [PortableSlam-ARCoreProbe-v0.1.0.apk](../dist/PortableSlam-ARCoreProbe-v0.1.0.apk)，SHA-256 `78e1950c61de116ecce95c7a2dc2bad97f2bf21d37e05cf7a2da33a323ed2b3b` |
| 一加 Ace 2 真机检测 | 未执行；ADB 当前无已连接设备 |
| ARCore/Depth go-no-go | 未判定 |
| RTAB-Map 集成 | 未开始，等待真机门槛 |

成功命令：

```powershell
.\tools\build-probe.ps1 -GradleArgs @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
```

构建仍有 SDK XML version 4 vs AGP 8.7.3 的工具版本提示，但没有编译或 Lint 错误。该提示如实保留，不通过升级整套依赖来掩盖。

后续必须用实际 clean 构建和真机结果更新此表；未执行项不得写成通过。
