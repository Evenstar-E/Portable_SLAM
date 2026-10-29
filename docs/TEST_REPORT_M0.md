# M0 构建与测试报告

## 已执行并通过

执行命令：

```powershell
.\tools\build-probe.ps1 -GradleArgs @(':app:assembleDebug', ':app:testDebugUnitTest', ':app:lintDebug')
```

结果：`BUILD SUCCESSFUL`。

## JVM 单元测试

| 测试类 | 数量 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `DepthBufferStatsTest` | 6 | 0 | 0 | 0 |
| `GateEvaluatorTest` | 4 | 0 | 0 | 0 |
| 合计 | 10 | 0 | 0 | 0 |

覆盖内容：DEPTH16 小端读取、非紧密 row/pixel stride、截断像素拒绝、8 位无符号置信度、零覆盖、Depth 不支持阻断、完整样本通过，以及仅靠重投影不能通过。

测试报告位于 [testDebugUnitTest](../app/build/test-results/testDebugUnitTest/)。

## Android Lint

`:app:lintDebug` 成功，文本报告为 `No issues found`。报告位于 [lint-results-debug.txt](../app/build/reports/lint-results-debug.txt)。

## APK 静态检查

- 包名：`com.portableslam.slam.probe`
- versionName：`0.1.0-probe`
- minSdk 26 / targetSdk 35
- native-code：`arm64-v8a`
- 权限：仅 `android.permission.CAMERA`
- 无 `INTERNET`、位置、麦克风或宽泛存储权限
- `allowBackup=false`，并配置 cloud-backup/device-transfer 排除规则

最终交付 APK：[PortableSlam-ARCoreProbe-v0.1.0.apk](../dist/PortableSlam-ARCoreProbe-v0.1.0.apk)。

- 文件大小：1,194,634 bytes
- SHA-256：`78e1950c61de116ecce95c7a2dc2bad97f2bf21d37e05cf7a2da33a323ed2b3b`
- 校验文件：[SHA256SUMS.txt](../dist/SHA256SUMS.txt)

## clean 复验

已执行 `:app:clean :app:assembleDebug :app:testDebugUnitTest :app:lintDebug` 并成功。测试任务使用 Gradle 缓存中的已通过结果，Lint 报告仍为 `No issues found`。

## 尚未执行或限制

- clean 后完整复验：已通过，见上一节。
- 真机 instrumentation：未执行。
- 一加 Ace 2 ARCore/Depth：未执行；ADB 当前无设备。
- SLAM、回环、地图、重建：尚未集成，不在本报告声称范围内。
- 构建日志有 SDK XML version 4 与 AGP 8.7.3 的版本提示；当前未导致任务失败。
