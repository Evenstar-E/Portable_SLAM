# ARCore / Depth 真机检测

## 为什么先检测

一加 Ace 2 常见中国型号为 PHK110，但 App 会读取实际 `Build.MODEL` 和 `Build.DEVICE`，不依赖商品名推断。Google 官方设备清单未明确列出该精确型号时，相近的 OnePlus 11R 5G、Ace 2 Pro 或 PHP110 都不能替代真机结果。

ARCore 认证和 Depth API 是两项不同能力。只有设备清单或系统安装成功，都不能证明实际会话能获得深度。

## 安装检测 APK

交付文件：[PortableSlam-ARCoreProbe-v0.1.0.apk](../dist/PortableSlam-ARCoreProbe-v0.1.0.apk)。

可以将 APK 复制到手机后侧载，也可以在手机已开启 USB 调试并授权电脑后执行：

```powershell
.\.toolchains\android-sdk\platform-tools\adb.exe install -r ".\dist\PortableSlam-ARCoreProbe-v0.1.0.apk"
```

安装前可用 [SHA256SUMS.txt](../dist/SHA256SUMS.txt) 核对文件完整性。当前 APK 仅支持 arm64-v8a。

## 操作步骤

1. 安装并打开 **Portable SLAM 设备检测**。
2. 点击“开始检测”，允许相机权限。
3. 如果系统提示安装/更新 ARCore，请通过系统提供的可信渠道完成，然后返回 App。
4. 将手机对准有纹理、光照正常、相对静止的室内场景，保持约 0.5–5 m 距离并缓慢移动。
5. 等待状态变为“真机门槛已通过”，或记录明确的不支持原因。
6. 点击“导出诊断报告”，用系统文件选择器保存 JSON。

## 通过门槛

- `depthAutomaticSupported = true`
- `continuousTrackingMs >= 5000`
- `fullDepthValidSamples >= 5`
- `rawDepthValidSamples >= 5`
- `confidenceValidSamples >= 5`
- `newRawDepthSamples >= 2`（至少两个不同时间戳的新 Raw Depth，排除只靠重投影通过）

有效样本表示对应深度/置信图至少包含一个非零像素；门槛较低，只用于判断数据链路是否存在，不代表精度合格。正式 SLAM 阶段仍需 5 分钟扫描、回环、重定位、网格和资源测试。

## 常见状态

- **UNKNOWN_CHECKING**：ARCore 正在查询可用性，App 会短暂重试；超时后可手动重新检测。
- **SUPPORTED_NOT_INSTALLED / SUPPORTED_APK_TOO_OLD**：需要安装或更新 ARCore。
- **UNSUPPORTED_DEVICE_NOT_CAPABLE**：系统明确报告设备不支持 ARCore，当前路线在此设备上停止。
- **Depth AUTOMATIC = false**：实际会话不支持 Depth，完整重建门槛不通过。
- **NotYetAvailable / 有效样本不足**：通常表示设备尚未移动、纹理不足或跟踪未建立，不等同于硬件不支持；改善场景后重新检测。
- **连续跟踪反复归零**：检查弱光、遮挡、快速运动、低纹理和系统相机占用。

## 报告内容

JSON 包含设备与版本、ARCore 可用性、Depth 支持、连续跟踪、相机内参、图像尺寸、时间戳、有效像素覆盖率、Raw Depth 新帧/重投影计数和错误信息。

报告不包含 RGB 图像、深度图像、地图、账号或位置。导出由用户主动完成，App 不会自动上传。
