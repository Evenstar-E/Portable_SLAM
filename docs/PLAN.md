# Portable SLAM：Android 视觉惯性 SLAM 与三维重建 App

## Context

当前工作目录为空，没有既有代码、构建系统或许可证约束。目标是从零做一个个人/研究用 Android App：使用普通手机摄像头和 IMU，在室内完成实时 6DoF 定位、轨迹与稀疏地图、回环、持久地图，以及稠密点云和扫描后纹理网格重建。先验证单房间，再扩展室内大范围。

已确认采用 **ARCore + RTAB-Map**，不从零自研 VIO，也不引入 GPLv3 的 ORB-SLAM3。完整功能只承诺在 ARCore 且支持 Depth API 的真机上运行，不要求 ToF/LiDAR。全部数据本地处理，无账号、无云后端、不上架。

## 1. 已确认的产品边界

- 平台：Android，`arm64-v8a`，`minSdk 26`；中文界面、竖屏扫描、米制单位，产品 applicationId 与官方基线不同。
- 输入：ARCore 管理的摄像头 + IMU；应用不另开 Camera2，也不重复采集 IMU。
- 算法边界：ARCore 提供连续、具有 metric scale 的视觉惯性里程计；RTAB-Map 负责 RGB-D 关键帧、稀疏视觉特征、词袋回环、几何验证、位姿图优化和持久地图。
- 地图：保存、关闭、重开、跨会话重定位、继续扫描；首版不做人工多地图拼接。
- 扫描中：相机预览、原始/全局修正轨迹、关键帧与稀疏特征、低分辨率点云、跟踪/深度/队列状态。
- 扫描后：基于同一份优化位姿快照生成点云和纹理网格；导出 PLY、完整 OBJ 材质包、稀疏地图、双轨迹、RTAB-Map 数据库和元数据。
- 首版不做：iOS、室外精度承诺、所有 Android 机型兼容、实时高精细纹理网格、厘米级精度保证、多设备协同、云上传、路径导航。

## 2. 第一道门槛：一加 Ace 2 真机检测

用户设备为一加 Ace 2，通常对应中国型号 PHK110，但必须以设备实际返回值为准。Google 官方清单目前未列出“一加 Ace 2 / PHK110”；官方列出的 OnePlus 11R 5G、一加 Ace 2 Pro、PHP110 等相近型号不能作为兼容证据。

先交付轻量 ARCore 能力检测 APK，再集成大型原生依赖：

1. 记录 `Build.MODEL/DEVICE`、Android/API、ABI、RAM、ARCore 版本和实际相机配置。
2. 检查 CAMERA 权限与 `ArCoreApk.checkAvailability()`，处理未安装、需更新、查询中和明确不支持；ARCore 安装/更新可能需要系统网络。
3. 创建实际 `ArSession`，检查 `isDepthModeSupported(AUTOMATIC)` 并配置 `DepthMode.AUTOMATIC`，不能只依据设备清单。
4. 引导用户缓慢移动，验证 `TRACKING`、CPU 彩色图、Depth、Raw Depth、Confidence、内参与时间戳；区分“暂未获得深度”和“设备不支持”。
5. 生成本地诊断报告，由用户主动导出，不自动上传。

**Go：**实际会话稳定跟踪且能取得所需深度数据，才进入完整集成。**No-go：**ARCore 或 Depth 任一关键能力不满足，按已确认决策更换官方明确兼容的参考机；不静默降级成无重建版本，也不把模拟器结果当作通过。

## 3. 架构：官方基线 + 固定 fork + 薄 Kotlin 产品壳

### 3.1 实施策略

1. 以 RTAB-Map 官方提交 `7699f9a2156d4da8c14a5234ff9742822a3558d1` 为基线候选，M0 验证后锁定；后续不自动跟随 `master`。
2. 先原样构建官方 Android App，与产品 APK 同机对照，验证 ARCore、Depth、回环、数据库和网格链路。
3. 产品采用固定版本 fork：保留官方 C++ 核心、ARCore 原生采集、JNI、Java 扫描 Activity 和 OpenGL ES 渲染；新增 Kotlin 首页、地图库、能力检查、状态机、地图仓库与导出协调器。
4. 不为 UI 风格重写扫描/渲染，不另造 SLAM。JNI 类 `com.introlab.rtabmap.RTABMapLib` 保持原包名，避免无意义改写所有 JNI 符号。
5. 产品使用 Gradle/Android Studio；不复用旧 Ant APK 打包，但复用官方 native 依赖构建、CMake 源文件组织及宿主机 `rtabmap-res_tool` 预构建步骤。
6. 官方依赖栈较旧：产品以 **ARCore 1.57.0** 为定点验证版本，只先升级 ARCore，不同时升级 PCL、Boost、VTK、OpenCV 等整套依赖。NDK、AGP、compile/target SDK 以 spike 通过的组合写入版本锁。

当前电脑只有 Git/WSL 命令可用，未发现 Java、ADB、Android SDK、Gradle、CMake、Ninja 或 Docker。实施先安装 Android Studio（embedded JDK）、SDK、匹配 NDK/CMake/LLDB；原生依赖优先沿用上游构建链，必要时使用 WSL2 Linux。所有构建使用 Wrapper 和锁定版本，不依赖本机隐藏文件。

### 3.2 目标项目结构与关键文件

| 路径 | 职责 |
|---|---|
| [app/](app/) | Kotlin Application、中文首页、地图库、能力检查、扫描状态、设置和导出界面 |
| [rtabmap-android/](rtabmap-android/) | 官方 Java/JNI/GL 的 Android Library 包装与 Gradle native 构建 |
| [third_party/rtabmap/](third_party/rtabmap/) | 固定提交的 RTAB-Map fork 与必要原生补丁 |
| [tools/](tools/) | 可复现 native 构建、导出验证和测试数据脚本 |
| [docs/](docs/) | 架构、坐标、地图格式、兼容设备、性能基线和版本锁 |
| [tests/](tests/) | 共用测试数据与验证工具 |

首版新建代表文件：

- [settings.gradle.kts](settings.gradle.kts)、[rtabmap-android/build.gradle.kts](rtabmap-android/build.gradle.kts)、[rtabmap-android/src/main/cpp/CMakeLists.txt](rtabmap-android/src/main/cpp/CMakeLists.txt)。
- [ArCapabilityGate.kt](app/src/main/java/com/portableslam/device/ArCapabilityGate.kt)：设备与 ARCore/Depth 门槛。
- [ScanState.kt](app/src/main/java/com/portableslam/scan/ScanState.kt)、[ScanSessionController.kt](app/src/main/java/com/portableslam/scan/ScanSessionController.kt)：唯一扫描控制状态机。
- [MapRepository.kt](app/src/main/java/com/portableslam/maps/MapRepository.kt)、[MapManifest.kt](app/src/main/java/com/portableslam/maps/MapManifest.kt)：数据库、备份、修订号、缓存和版本管理。
- [ExportCoordinator.kt](app/src/main/java/com/portableslam/export/ExportCoordinator.kt)：冻结图快照、后处理、进度、取消与导出验证。

重点修改的上游文件：

- [CameraARCore.cpp](https://github.com/introlab/rtabmap/blob/master/app/android/jni/CameraARCore.cpp) 及头文件：Raw Depth16/Confidence、stride、时间戳和资源释放。
- [RTABMapApp.cpp](https://github.com/introlab/rtabmap/blob/master/app/android/jni/RTABMapApp.cpp)：置信度接线、双轨迹、稀疏地图、可靠保存结果、图修订快照、导出与缓存失效。
- `jni_interface.cpp`：新 JNI、空 handle 防护、明确错误码；包括官方深度回调入口的生命周期防护。
- [RTABMapActivity.java](https://github.com/introlab/rtabmap/blob/master/app/android/src/com/introlab/rtabmap/RTABMapActivity.java) 与 `RTABMapLib.java`：保留扫描/GL，逐步委托产品状态机，删除云端和无关驱动路径。

产品 Manifest：AR Required + Depth feature，仅申请相机及合规前台任务所需权限；无 `INTERNET`、位置、麦克风或宽泛存储权限，`allowBackup=false`。地图放应用私有目录，导入/导出使用 SAF；关闭 Tango、AREngine、Sketchfab、GPS、云上传、nonfree/SURF、ROS 和外接相机驱动。

## 4. 数据流、坐标与线程

```text
Camera + IMU
  -> ARCore VIO：当前 6DoF odom / TrackingState / 内参
  -> 同一 ArFrame：YUV RGB + Raw Depth16 + Raw Confidence + timestamps
  -> 深拷贝、有效性检查、有界背压队列
  -> RTAB-Map RGB-D 关键帧 -> 词袋回环 + 几何验证 -> 图优化
  -> optimized poses / mapToOdom / SQLite map.db
  -> 实时体素降采样点云
  -> 停止后冻结 graphRevision -> 全局点云 / organized 纹理网格 / 导出
```

### 线程边界

- **主线程：**UI、权限、生命周期和进度；禁止图像转换、数据库、网格或长文件操作。
- **GL 线程：**按官方约束更新 ARCore、获取当前帧和渲染；禁止等待数据库/导出。
- **采集线程：**深拷贝并提交 RGB-D/odom；禁止直接更新 UI。
- **RTAB-Map 线程：**关键帧、回环、优化和数据库；禁止 GL 操作。
- **单线程控制执行器：**串行执行打开、开始、暂停、停止、保存，避免生命周期竞争。
- **单线程导出执行器：**最终优化和导出；与继续建图互斥。

RGB-D 队列必须有界；处理不过来时继续 ARCore 跟踪与渲染，跳过普通建图帧并计数，不能阻塞 GL 或无限堆积。预览位姿只保留最新值，但回环/优化/数据库事件不能按预览帧丢弃。native handle 只能在 GL、采集和控制操作全部停止后销毁；所有回调检查状态并切到主线程更新 UI。

### 坐标和时间

- 复用官方 ARCore/OpenGL↔RTAB-Map 世界及 optical 坐标转换，不在 Kotlin/C++ 各写一套矩阵。
- 实时全局位姿按 `M_T_C = M_T_O × O_T_C` 组合；`M_T_O` 是最新 `mapToOdom`。原始 odom 与优化轨迹分别保存，回环后不能仍只显示未修正 ARCore 轨迹。
- 深度 `CV_16UC1` 使用毫米，导出坐标使用米；四元数顺序、坐标轴和时间基准写入元数据，PLY/OBJ 使用同一地图坐标快照。
- 传感器配准使用 ARCore 时间域，不使用 `System.currentTimeMillis()`。当前 `SensorData` 的位姿和时间戳对应当前跟踪相机帧；墙钟只用于文件名。
- Raw Depth 重复时间戳表示旧深度已按**当前相机位姿**重投影：该时间戳用于判断是否产生新深度，不把图像强行塞回历史位姿。建图关键帧只接受有新深度的帧；重投影帧可用于预览，但不计作新深度覆盖。
- 跨重启不假设 ARCore 时间戳全局唯一；数据库关联依赖节点/会话 ID。专项测试如有冲突，对整个会话施加固定偏移，保持帧间差不变。

## 5. RGB-D 与重建管线

### 5.1 产品默认：Raw Depth16 + 配套 Confidence

官方基线先验证其现有平滑 Depth 路径；产品随后定点接入：

- `ArFrame_acquireRawDepthImage16Bits`：16 位、毫米、沿相机主轴距离，0 为无效。
- `ArFrame_acquireRawDepthConfidenceImage`：与 Raw Depth 同尺寸的 `CV_8UC1`，0–255；保留原值，不做无意义的 0–100 换算。
- 按 `rowStride/pixelStride` 深拷贝，所有 `ArImage` 在成功和异常路径都及时释放；不得混用旧接口的位打包解码与新 16Bits 接口。
- 将 Confidence 通过 `setRGBDImage` 接入 `SensorData.depthConfidenceRaw()`，补 JNI 参数入口，并验证数据库写入、关闭、重开后数值不变。
- RTAB-Map 点云函数使用 `confidenceThr`：0 关闭过滤，其他值要求像素置信度 `>=` 阈值。初始阈值 **128/255** 是工程起点，不声称为官方固定推荐；与深度范围一起经 A/B 实测锁定。
- 初始深度范围 **0.5–5 m**；剔除零值、非有限值、低置信度和低覆盖帧。低纹理时提示“深度不足”，置信度过滤不能在白墙上凭空生成深度。
- 深度与 RGB 不假定同分辨率；使用对应相机模型和深度尺寸，做已知边缘配准测试，不能只凭缩放内参就宣称对齐。

如果 spike 证明 Raw Depth/Confidence 的数据库往返或最终网格质量不合格，首版固定回退到同一 ARCore 1.57.0 下的**平滑 Depth16**，将 Confidence 移出首版；元数据和测试报告必须记录实际模式，不再引入第三套深度算法。

### 5.2 关键帧、预览与后处理

- 相机/里程计目标 30 fps；mapping 输入初始上限 10 Hz，RTAB-Map 检测率从官方移动默认约 1 Hz 起步，并按运动和新深度时间戳选关键帧。
- 沿用 RTAB-Map 现有视觉词袋、几何验证和移动构建已验证的 GTSAM 配置；首版不换描述子或优化器，只锁定参数。
- 扫描中只显示体素降采样点云、关键帧/回环边和轨迹；不反复生成全分辨率纹理网格。回环后按优化图重新装配，不能继续显示旧漂移结果。
- 扫描结束后先停止采集并完成数据库一致性处理，再做必要补充回环/最终优化，冻结节点、约束和优化位姿的 `graphRevision`。PLY 与 OBJ 必须来自同一快照，导出期间禁止继续建图。
- 复用官方 `organizedFastMesh`、`denseMeshPostProcessing`、`createTextureMesh`、`assembleTextureMesh`、增益补偿和写出逻辑；首版不引入 TSDF，也不默认使用会补出非真实表面的 Poisson。

### 5.3 导出与存储

- **cloud.ply**：优化后的彩色稠密点云。
- **mesh.zip**：`mesh.obj`、`mesh.mtl`、全部纹理和说明，使用相对路径；ZIP 是材质打包方式，不替代 OBJ。
- **sparse.ply、keyframes.csv、links.csv**：三维视觉特征、优化关键帧与图连接。M1 必须确认数据库保留/可恢复 3D words/keypoints，不能用稠密点云冒充稀疏特征图。
- **trajectory_odom.txt、trajectory_optimized.txt**：TUM 风格时间戳和位姿，单位秒/米。
- **map.db、manifest.json**：原地图、设备、版本、参数、坐标/时间约定、实际深度模式、mapRevision/graphRevision 和导出清单。

所有导出先写临时目录，验证点/面/索引/UV/材质/纹理和元数据后原子改名；失败只清理临时输出，不删除原地图。地图私有存储，用户通过 SAF 显式导出/导入，不申请全盘存储权限。

## 6. 扫描状态与地图生命周期

主状态：

`设备检查 -> 权限/地图打开 -> 就绪 -> 相机启动 -> 扫描 -> 停止采集 -> 数据库完成 -> 后处理 -> 查看`

并行维护：用户/系统暂停、ARCore `TRACKING/PAUSED/STOPPED`、深度 `AVAILABLE/NOT_YET/LOW_COVERAGE`、跨会话 `RELOCALIZING/ALIGNED/UNLINKED`、可恢复错误和不支持状态。按钮只发送事件，收到 native 成功结果后才能显示“已保存/已导出”。

- **新建：**自动创建 mapId、独立目录和文件型数据库；`clearDatabase=true` 只允许用于刚创建且确认不存在的路径。
- **跟踪丢失：**立即停止新关键帧，不插值跨越跟踪空洞；保留已提交地图，恢复 `TRACKING` 后继续。
- **后台/来电：**暂停相机，不后台扫描；回前台重新确认跟踪/重定位。Activity 重建不能损坏数据库。
- **停止：**停止帧提交 ->  drain 有界队列 -> 停线程 -> checkpoint/关闭 -> 验证保存 -> 后处理。官方 `save()` 的 void 返回不能直接当成功；需错误码、数据库快速检查和可重开验证。
- **重开查看：**`clearDatabase=false`，只读显示优化地图，不自动开相机。
- **继续建图：**新 ARCore 世界与旧地图没有已知变换，先进入 `RELOCALIZING`，引导回到旧区域；只有 RTAB-Map 接受跨会话几何约束后才显示 `ALIGNED`。未连接会话明确标为 UNLINKED，不能悄悄当作同一地图。
- **修订与缓存：**每次继续建图递增 mapRevision，旧点云、OBJ、优化网格标记过期；不能依赖上游在所有路径自动失效缓存。
- **备份/恢复/迁移：**继续建图或迁移前在数据库关闭时保留上一会话备份；恢复/迁移只操作副本，验证后再切换，未来版本数据库明确拒绝。
- **进程被杀/空间不足：**允许丢未提交扫描，但必须保住最后已提交地图和上一备份；导出可从保存地图重做。

## 7. 性能与资源起始预算（均须真机校准）

参考场景：20–30 m² 单房间、约 10 m 闭合路径、5 分钟扫描；另做 10–15 分钟压力测试。

| 项目 | 起始目标/预算 |
|---|---|
| 相机/里程计 | 30 fps |
| 预览 | 目标 30 fps；10 分钟测试中低于 20 fps 的时间占比目标小于 5% |
| mapping / RTAB-Map | 输入上限 10 Hz；检测约 1 Hz 起步，更新 P95 目标不超过 800 ms |
| 点云预览 | 更新目标至少 5 Hz；驻留点数先从 30 万封顶 |
| 扫描内存 | 初始 PSS 预算约 1.5 GB，按参考机 RAM 和实测修订；无持续增长/无限队列 |
| 后处理 | 5 分钟扫描的后处理目标不超过 5 分钟；峰值内存单独记录 |
| 默认导出 | 从约 2–5 百万点、最多约 100 万多边形、最多 2 张 2048 纹理开始校准 |
| 存储 | 首次扫描预留至少 2 GB；30 秒后按真实 DB/分钟和导出工作集动态预估，保留 20% 余量 |
| 热稳定 | 连续 10 分钟无崩溃/严重热状态；必要时先降预览密度和 mapping 频率 |

这些不是上游能力承诺。记录 FPS、P95 延迟、丢帧、队列、Java/native PSS、DB 增长、温升/降频、后处理时间和输出大小，实测后锁定默认质量预设。后处理可取消，离开界面时仅通过合规前台任务继续，不持有后台相机。

大范围阶段再调 RTAB-Map WM/LTM 的 `Rtabmap/TimeThr`、`Rtabmap/MemoryThr`、关键帧修剪、预览 LOD 和分块导出；限制在线工作集而非删除历史地图，不把单房间内存结论外推到多房间。

## 8. 分阶段实施与 go/no-go

1. **M0 环境与设备门槛**：安装工具，完成 ARCore probe，在 Ace 2 输出诊断；不兼容则换参考机。锁定 RTAB-Map 基线、依赖镜像 digest 和构建参数。
2. **M1a 官方基线**：原样构建官方 App，验证 Depth 扫描、真实回环、保存/杀进程重开、继续建图、PLY 和纹理 OBJ，记录性能基线。
3. **M1b 产品 native spike**：建立最小 Gradle App/Library；验证 ARCore 1.57.0、Raw Depth16/Confidence、stride/时间戳、Confidence 数据库往返、稀疏特征提取和宿主资源工具。必要时按第 5.1 节回退平滑 Depth16。
4. **M2 可信扫描链路**：中文 UI、权限/生命周期、状态机、跟踪/深度提示、mapToOdom 修正、双轨迹、稀疏关键帧与低分辨率点云。
5. **M3 持久地图**：地图库、可靠保存、SAF 导入/导出、重开、跨会话重定位、继续扫描、修订/备份/恢复。
6. **M4 重建与格式**：冻结图快照、点云、organized 纹理 OBJ、PLY、稀疏地图和双轨迹导出，支持进度/取消/失败重试。
7. **M5 加固与验收**：异常矩阵、性能/温升/内存、数据库迁移、许可清单；交付源码、APK、文档和测试报告。
8. **后续大场景**：单房间验收后再做多房间长时扫描、重复走廊错误回环、WM/LTM、LOD 和分块后处理。

### M0/M1 必须全部满足才展开产品功能

- 官方基线和最小产品 Gradle 包均可从干净目录重复构建，版本与依赖已锁定。
- 参考真机至少连续 5 分钟获得有效跟踪和所选深度数据，无持续 `RESOURCE_EXHAUSTED`、图像泄漏、stride/单位错误或无限队列。
- 若使用 Raw Confidence：确认其进入 `SensorData`、实际影响点云，并能随数据库重开恢复；否则按既定方案回退，不伪装已支持。
- ARCore 6DoF、RTAB-Map 关键帧/稀疏特征、真实回环、保存/重开和跨会话继续建图成立；未重定位时不会错误宣称已对齐。
- 手机端 PLY 可解析，OBJ/MTL/UV/纹理完整，来自同一优化图快照，数据库仍可重开。
- 跟踪丢失、后台、权限拒绝和存储不足不损坏数据库；恢复/迁移不覆盖唯一原件。
- 任一核心链路不成立时暂停 UI 美化和扩展功能，先修设备、构建或 native 基线。

## 9. Verification 与验收

### 自动化与回归

- 原生测试：坐标/四元数、毫米到米、内参与深度尺寸、时间戳/重投影、Confidence `>=` 阈值与 0 关闭过滤、stride、PLY/OBJ/轨迹。
- Android 测试：状态机、权限、重复按钮、迟到 JNI 回调、导出取消、空间不足、修订/缓存失效、数据库版本逻辑。
- 真机 instrumentation：能力检查、生命周期、保存/重开、图像资源释放、连续多次扫描后的资源回落。
- 建立调试专用、默认关闭的同步 RGB-D 关键帧记录/回放，保存 RGB、Depth16、Confidence、内参、当前位姿及两类时间戳，用于固定数据回归；不把 ARCore Playback 未经验证的深度能力当完整回归方案。
- 实施后运行 `gradlew.bat :app:assembleDebug :app:testDebugUnitTest`、原生 `ctest` 和 `gradlew.bat :app:connectedDebugAndroidTest`；保存实际命令、日志与结果，未执行/未通过项必须明确报告。

### 单房间实测

环境：纹理良好、静态为主、0.5–5 m、正常光照、缓慢运动；路径、实体起点和房间尺寸用卷尺/标记建立参考。做 **5 次独立 5 分钟扫描**，报告每次、中位数和最差结果，不只挑最好一次。

- 功能目标：至少 4/5 完成回环、保存/重开、跨会话重定位/继续扫描和全部导出，无 crash/ANR/OOM。
- 精度目标：回到实体起点时，**回环优化前**终点位置闭合差不超过实际行程 2%；最终模型的已知房间尺寸误差不超过 5%。目标至少 4/5 达到，未达不宣称通过。
- 指标解释：终点闭合差不是 ATE，也不代表全局轨迹或所有重建表面的精度；首版不承诺厘米级。
- 重定位：在已扫描且纹理充分区域目标 10 秒内恢复正确约束；继续扫描后不能出现两个未说明的参考系。另在相似但未建图区域检查错误重定位/回环。
- 性能：按第 7 节记录，未达目标时调参并如实报告，不能把目标写成实测结果。
- 文件：PLY/OBJ 在 CloudCompare/MeshLab 可打开，材质相对路径完整，尺度/轴方向正确；稀疏图和双轨迹可解析，清单修订一致。

### 异常矩阵

覆盖权限拒绝/不再询问、ARCore 未安装/过旧、Depth 不支持、静止无深度、白墙低纹理、弱光、快速运动/模糊、遮挡与跟踪丢失、正确/疑似错误回环、后台/来电/锁屏、Activity 重建、低存储、温升降频、后处理取消、强制杀进程、连续 10 次扫描导出、数据库截断/损坏、老版本迁移和未来版本拒绝。每种情况都必须可恢复或给出明确错误，不能静默生成错误地图。

## 10. 交付物

- 可安装 Android APK（个人/研究侧载）与完整源码。
- Ace 2 或替代参考机的能力报告、兼容性说明和真实测试报告。
- 中文 README、环境/版本锁、构建与真机部署说明、架构/坐标/数据格式和参数文档。
- 示例地图及 PLY、OBJ 材质包、稀疏地图和双轨迹导出。
- 第三方许可证与版权声明；RTAB-Map 按 BSD-3-Clause 使用，关闭 nonfree，业务代码不引入 ORB-SLAM3 GPLv3。

## 参考资料

- [RTAB-Map 官方仓库](https://github.com/introlab/rtabmap) 与 [基线候选提交](https://github.com/introlab/rtabmap/commit/7699f9a2156d4da8c14a5234ff9742822a3558d1)
- [官方 Android RTABMapApp.cpp](https://github.com/introlab/rtabmap/blob/master/app/android/jni/RTABMapApp.cpp) 与 [CameraARCore.cpp](https://github.com/introlab/rtabmap/blob/master/app/android/jni/CameraARCore.cpp)
- [RTAB-Map SensorData](https://github.com/introlab/rtabmap/blob/master/corelib/include/rtabmap/core/SensorData.h) 与 [BSD-3-Clause LICENSE](https://github.com/introlab/rtabmap/blob/master/LICENSE)
- [RTAB-Map 大规模/长期在线 SLAM 论文](https://arxiv.org/html/2403.06341v1)
- [ARCore Depth 概述](https://developers.google.com/ar/develop/java/depth/overview)、[Depth 开发指南](https://developers.google.com/ar/develop/java/depth/developer-guide) 与 [Raw Depth](https://developers.google.com/ar/develop/java/depth/raw-depth)
- [ARCore Native Frame API](https://developers.google.com/ar/reference/c/group/frame) 与 [ARCore Android SDK Releases](https://github.com/google-ar/arcore-android-sdk/releases)
- [ARCore 官方支持设备清单](https://developers.google.com/ar/devices)
- [ARCore Recording and Playback](https://developers.google.com/ar/develop/recording-and-playback)
