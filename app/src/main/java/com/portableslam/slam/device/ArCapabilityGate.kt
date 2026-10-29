package com.portableslam.slam.device

import android.app.Activity
import android.media.Image
import android.graphics.ImageFormat
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.util.concurrent.atomic.AtomicReference

class ArCapabilityGate(
    private val activity: Activity,
    deviceSnapshot: DeviceSnapshot,
) {
    private val sessionLock = Any()
    private val snapshotRef = AtomicReference(ProbeSnapshot(device = deviceSnapshot))
    private var session: Session? = null
    private var resumed = false
    private var cameraTextureId = -1
    private var lastImageAnalysisNs = 0L
    private var trackingStartedNs = 0L
    private var previousTrackingState: TrackingState? = null
    private var lastRawDepthTimestampNs: Long? = null
    private var displayRotation = 0
    private var displayWidth = 0
    private var displayHeight = 0

    val snapshot: ProbeSnapshot
        get() = snapshotRef.get()

    fun setAvailability(availability: String, arcoreVersion: String) {
        update {
            it.copy(
                availability = availability,
                arcoreVersion = arcoreVersion,
                statusDetail = "ARCore 可用性：$availability",
            )
        }
    }

    fun markChecking(detail: String) {
        update { it.copy(status = ProbeStatus.CHECKING, statusDetail = detail) }
    }

    fun markBlocked(detail: String, error: String? = null) {
        update {
            it.copy(
                status = ProbeStatus.BLOCKED,
                statusDetail = detail,
                errors = if (error == null) it.errors else (it.errors + error).takeLast(MAX_ERRORS),
            )
        }
    }

    fun start() {
        synchronized(sessionLock) {
            closeLocked()
            resetMeasurements()
            update {
                it.copy(
                    status = ProbeStatus.CHECKING,
                    statusDetail = "正在创建 ARCore 会话并检查 Depth…",
                    sessionReady = false,
                    depthAutomaticSupported = null,
                    trackingState = "UNKNOWN",
                    errors = emptyList(),
                )
            }

            try {
                val newSession = Session(activity)
                val depthSupported = newSession.isDepthModeSupported(Config.DepthMode.AUTOMATIC)
                val config = newSession.config
                config.depthMode = if (depthSupported) {
                    Config.DepthMode.AUTOMATIC
                } else {
                    Config.DepthMode.DISABLED
                }
                newSession.configure(config)
                if (displayWidth > 0 && displayHeight > 0) {
                    newSession.setDisplayGeometry(displayRotation, displayWidth, displayHeight)
                }
                session = newSession
                update {
                    it.copy(
                        sessionReady = true,
                        depthAutomaticSupported = depthSupported,
                        statusDetail = if (depthSupported) {
                            "Depth 已启用，等待相机跟踪和有效深度…"
                        } else {
                            "此实际会话不支持 DepthMode.AUTOMATIC"
                        },
                    )
                }
                newSession.resume()
                resumed = true
                evaluateGate()
            } catch (exception: Exception) {
                fail("无法启动 ARCore 会话", exception)
                closeLocked()
            }
        }
    }

    fun resume() {
        synchronized(sessionLock) {
            val current = session ?: return
            if (resumed) return
            try {
                current.resume()
                resumed = true
                previousTrackingState = null
                trackingStartedNs = 0L
                update { it.copy(statusDetail = "会话已恢复，正在重新确认跟踪…") }
            } catch (exception: Exception) {
                fail("恢复 ARCore 会话失败", exception)
            }
        }
    }

    fun pause() {
        synchronized(sessionLock) {
            val current = session ?: return
            if (!resumed) return
            try {
                current.pause()
            } catch (exception: Exception) {
                addError("暂停 ARCore 会话失败", exception)
            } finally {
                resumed = false
                previousTrackingState = null
                trackingStartedNs = 0L
                update {
                    it.copy(
                        trackingState = "PAUSED",
                        continuousTrackingMs = 0,
                        status = ProbeStatus.CHECKING,
                        statusDetail = "会话已暂停；返回应用后会重新检测",
                    )
                }
            }
        }
    }

    fun close() {
        synchronized(sessionLock) { closeLocked() }
    }

    fun setDisplayGeometry(rotation: Int, width: Int, height: Int) {
        synchronized(sessionLock) {
            displayRotation = rotation
            displayWidth = width
            displayHeight = height
            try {
                session?.setDisplayGeometry(rotation, width, height)
            } catch (exception: Exception) {
                addError("设置显示几何失败", exception)
            }
        }
    }

    /** Called only on the GL thread; the frame never escapes the session lock. */
    fun updateFrame(
        textureId: Int,
        textureCoordinates: java.nio.FloatBuffer,
        transformedTextureCoordinates: java.nio.FloatBuffer,
    ): Boolean {
        synchronized(sessionLock) {
            val current = session ?: return false
            if (!resumed) return false

            return try {
                if (cameraTextureId != textureId) {
                    current.setCameraTextureName(textureId)
                    cameraTextureId = textureId
                }
                val frame = current.update()
                processTracking(frame)
                val now = System.nanoTime()
                if (now - lastImageAnalysisNs >= IMAGE_ANALYSIS_INTERVAL_NS) {
                    lastImageAnalysisNs = now
                    analyzeImages(frame)
                }
                evaluateGate()
                frame.transformCoordinates2d(
                    Coordinates2d.VIEW_NORMALIZED,
                    textureCoordinates,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    transformedTextureCoordinates,
                )
                true
            } catch (exception: Exception) {
                addError("ARCore 帧更新失败", exception)
                false
            }
        }
    }

    private fun processTracking(frame: Frame) {
        val state = frame.camera.trackingState
        val now = System.nanoTime()
        var trackedFrames = snapshot.trackedFrameCount
        var continuousMs = 0L
        var poseValues: List<Float>? = null

        if (state == TrackingState.TRACKING) {
            if (previousTrackingState != TrackingState.TRACKING || trackingStartedNs == 0L) {
                trackingStartedNs = now
            }
            trackedFrames++
            continuousMs = (now - trackingStartedNs) / 1_000_000L
            val pose = frame.camera.pose
            poseValues = (pose.translation.toList() + pose.rotationQuaternion.toList())
        } else {
            trackingStartedNs = 0L
        }
        previousTrackingState = state

        update {
            it.copy(
                trackingState = state.name,
                trackedFrameCount = trackedFrames,
                continuousTrackingMs = continuousMs,
                lastPose = poseValues ?: it.lastPose,
            )
        }
    }

    private fun analyzeImages(frame: Frame) {
        readCameraImage(frame)
        if (snapshot.depthAutomaticSupported != true) return

        val fullDepth = readDepthImage("Full Depth") { frame.acquireDepthImage16Bits() }
        val rawDepth = readDepthImage("Raw Depth") { frame.acquireRawDepthImage16Bits() }
        val confidence = readConfidenceImage { frame.acquireRawDepthConfidenceImage() }

        if (rawDepth != null && confidence != null &&
            (rawDepth.width != confidence.width || rawDepth.height != confidence.height)
        ) {
            addError(
                "Raw Depth 与 Confidence 尺寸不一致：" +
                    "${rawDepth.width}x${rawDepth.height} vs ${confidence.width}x${confidence.height}",
            )
        }

        update { current ->
            val newRawTimestamp = rawDepth?.timestampNs
            val isNewRawDepth = newRawTimestamp != null && newRawTimestamp != lastRawDepthTimestampNs
            current.copy(
                fullDepth = fullDepth ?: current.fullDepth,
                rawDepth = rawDepth ?: current.rawDepth,
                rawConfidence = confidence ?: current.rawConfidence,
                fullDepthValidSamples = current.fullDepthValidSamples +
                    if ((fullDepth?.validPixels ?: 0) > 0) 1 else 0,
                rawDepthValidSamples = current.rawDepthValidSamples +
                    if ((rawDepth?.validPixels ?: 0) > 0) 1 else 0,
                confidenceValidSamples = current.confidenceValidSamples +
                    if ((confidence?.validPixels ?: 0) > 0) 1 else 0,
                newRawDepthSamples = current.newRawDepthSamples + if (isNewRawDepth) 1 else 0,
                reprojectedRawDepthSamples = current.reprojectedRawDepthSamples +
                    if (newRawTimestamp != null && !isNewRawDepth) 1 else 0,
            )
        }
        rawDepth?.let { lastRawDepthTimestampNs = it.timestampNs }
    }

    private fun readCameraImage(frame: Frame) {
        var image: Image? = null
        try {
            image = frame.acquireCameraImage()
            if (image.format != ImageFormat.YUV_420_888) {
                addError("相机图像格式异常：${image.format}")
                return
            }
            val intrinsics = frame.camera.imageIntrinsics
            val focal = intrinsics.focalLength
            val principal = intrinsics.principalPoint
            val dimensions = intrinsics.imageDimensions
            val camera = CameraSnapshot(
                width = image.width,
                height = image.height,
                timestampNs = image.timestamp,
                fx = focal[0],
                fy = focal[1],
                cx = principal[0],
                cy = principal[1],
                intrinsicsWidth = dimensions[0],
                intrinsicsHeight = dimensions[1],
            )
            update { it.copy(camera = camera) }
        } catch (_: NotYetAvailableException) {
            // Camera CPU image can legitimately lag the first tracking frames.
        } catch (exception: Exception) {
            addError("读取 RGB 相机图像失败", exception)
        } finally {
            image?.close()
        }
    }

    private fun readDepthImage(label: String, acquire: () -> Image): ImageSnapshot? {
        var image: Image? = null
        return try {
            image = acquire()
            if (image.format != ImageFormat.DEPTH16) {
                addError("$label 格式异常：${image.format}")
                null
            } else {
                ImageMetricsReader.readDepth(image)
            }
        } catch (_: NotYetAvailableException) {
            null
        } catch (exception: Exception) {
            addError("读取 $label 失败", exception)
            null
        } finally {
            image?.close()
        }
    }

    private fun readConfidenceImage(acquire: () -> Image): ImageSnapshot? {
        var image: Image? = null
        return try {
            image = acquire()
            if (image.format != ImageFormat.Y8) {
                addError("Raw Confidence 格式异常：${image.format}")
                null
            } else {
                ImageMetricsReader.readConfidence(image)
            }
        } catch (_: NotYetAvailableException) {
            null
        } catch (exception: Exception) {
            addError("读取 Raw Confidence 失败", exception)
            null
        } finally {
            image?.close()
        }
    }

    private fun evaluateGate() {
        val current = snapshot
        val result = GateEvaluator.evaluate(
            depthAutomaticSupported = current.depthAutomaticSupported,
            continuousTrackingMs = current.continuousTrackingMs,
            fullDepthValidSamples = current.fullDepthValidSamples,
            rawDepthValidSamples = current.rawDepthValidSamples,
            confidenceValidSamples = current.confidenceValidSamples,
            newRawDepthSamples = current.newRawDepthSamples,
        )
        if (current.status != ProbeStatus.ERROR) {
            update { it.copy(status = result.status, statusDetail = result.detail) }
        }
    }

    private fun resetMeasurements() {
        lastImageAnalysisNs = 0L
        trackingStartedNs = 0L
        previousTrackingState = null
        lastRawDepthTimestampNs = null
        cameraTextureId = -1
        val device = snapshot.device
        val availability = snapshot.availability
        val arcoreVersion = snapshot.arcoreVersion
        snapshotRef.set(
            ProbeSnapshot(
                device = device,
                availability = availability,
                arcoreVersion = arcoreVersion,
            ),
        )
    }

    private fun closeLocked() {
        val current = session
        session = null
        resumed = false
        cameraTextureId = -1
        try {
            current?.close()
        } catch (exception: Exception) {
            addError("关闭 ARCore 会话失败", exception)
        }
        update { it.copy(sessionReady = false) }
    }

    private fun fail(message: String, exception: Exception) {
        val detail = "$message：${exception.javaClass.simpleName}: ${exception.message ?: "无详情"}"
        update {
            it.copy(
                status = ProbeStatus.ERROR,
                statusDetail = detail,
                errors = (it.errors + detail).takeLast(MAX_ERRORS),
            )
        }
    }

    private fun addError(message: String, exception: Exception? = null) {
        val detail = if (exception == null) {
            message
        } else {
            "$message：${exception.javaClass.simpleName}: ${exception.message ?: "无详情"}"
        }
        update { it.copy(errors = (it.errors + detail).takeLast(MAX_ERRORS)) }
    }

    private fun update(transform: (ProbeSnapshot) -> ProbeSnapshot) {
        snapshotRef.updateAndGet { previous -> transform(previous) }
    }

    companion object {
        private const val IMAGE_ANALYSIS_INTERVAL_NS = 500_000_000L
        private const val MAX_ERRORS = 10
    }
}
