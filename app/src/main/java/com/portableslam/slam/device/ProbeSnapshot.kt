package com.portableslam.slam.device

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

enum class ProbeStatus {
    IDLE,
    CHECKING,
    WAITING_FOR_MOTION,
    PASS,
    BLOCKED,
    ERROR,
}

data class DeviceSnapshot(
    val manufacturer: String,
    val model: String,
    val device: String,
    val androidRelease: String,
    val apiLevel: Int,
    val supportedAbis: List<String>,
    val totalRamMb: Long,
    val appVersion: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("manufacturer", manufacturer)
        .put("model", model)
        .put("device", device)
        .put("androidRelease", androidRelease)
        .put("apiLevel", apiLevel)
        .put("supportedAbis", JSONArray(supportedAbis))
        .put("totalRamMb", totalRamMb)
        .put("appVersion", appVersion)
}

data class CameraSnapshot(
    val width: Int,
    val height: Int,
    val timestampNs: Long,
    val fx: Float,
    val fy: Float,
    val cx: Float,
    val cy: Float,
    val intrinsicsWidth: Int,
    val intrinsicsHeight: Int,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("width", width)
        .put("height", height)
        .put("timestampNs", timestampNs)
        .put("fx", fx.toDouble())
        .put("fy", fy.toDouble())
        .put("cx", cx.toDouble())
        .put("cy", cy.toDouble())
        .put("intrinsicsWidth", intrinsicsWidth)
        .put("intrinsicsHeight", intrinsicsHeight)
}

data class GateEvaluation(
    val status: ProbeStatus,
    val detail: String,
)

object GateEvaluator {
    const val REQUIRED_CONTINUOUS_TRACKING_MS = 5_000L
    const val REQUIRED_VALID_SAMPLES = 5
    const val REQUIRED_NEW_RAW_DEPTH_SAMPLES = 2

    fun evaluate(
        depthAutomaticSupported: Boolean?,
        continuousTrackingMs: Long,
        fullDepthValidSamples: Int,
        rawDepthValidSamples: Int,
        confidenceValidSamples: Int,
        newRawDepthSamples: Int,
    ): GateEvaluation {
        if (depthAutomaticSupported == false) {
            return GateEvaluation(
                ProbeStatus.BLOCKED,
                "实际 ARCore 会话不支持 DepthMode.AUTOMATIC；完整重建门槛不通过",
            )
        }
        if (depthAutomaticSupported == null) {
            return GateEvaluation(ProbeStatus.CHECKING, "尚未完成 Depth 能力检查")
        }

        val missing = mutableListOf<String>()
        if (continuousTrackingMs < REQUIRED_CONTINUOUS_TRACKING_MS) {
            missing += "连续跟踪 ${continuousTrackingMs / 1000.0}/5 秒"
        }
        if (fullDepthValidSamples < REQUIRED_VALID_SAMPLES) {
            missing += "Full Depth 有效样本 $fullDepthValidSamples/5"
        }
        if (rawDepthValidSamples < REQUIRED_VALID_SAMPLES) {
            missing += "Raw Depth 有效样本 $rawDepthValidSamples/5"
        }
        if (confidenceValidSamples < REQUIRED_VALID_SAMPLES) {
            missing += "Confidence 有效样本 $confidenceValidSamples/5"
        }
        if (newRawDepthSamples < REQUIRED_NEW_RAW_DEPTH_SAMPLES) {
            missing += "不同时间戳的新 Raw Depth $newRawDepthSamples/2"
        }

        return if (missing.isEmpty()) {
            GateEvaluation(ProbeStatus.PASS, "ARCore、跟踪、Full/Raw Depth 和 Confidence 真机门槛已通过")
        } else {
            GateEvaluation(
                ProbeStatus.WAITING_FOR_MOTION,
                "继续缓慢移动并观察有纹理区域：${missing.joinToString("；")}",
            )
        }
    }
}

data class ProbeSnapshot(
    val status: ProbeStatus = ProbeStatus.IDLE,
    val statusDetail: String = "尚未开始",
    val device: DeviceSnapshot,
    val availability: String = "NOT_CHECKED",
    val arcoreVersion: String = "未安装或无法读取",
    val sessionReady: Boolean = false,
    val depthAutomaticSupported: Boolean? = null,
    val trackingState: String = "UNKNOWN",
    val trackedFrameCount: Int = 0,
    val continuousTrackingMs: Long = 0,
    val camera: CameraSnapshot? = null,
    val fullDepth: ImageSnapshot? = null,
    val rawDepth: ImageSnapshot? = null,
    val rawConfidence: ImageSnapshot? = null,
    val fullDepthValidSamples: Int = 0,
    val rawDepthValidSamples: Int = 0,
    val confidenceValidSamples: Int = 0,
    val newRawDepthSamples: Int = 0,
    val reprojectedRawDepthSamples: Int = 0,
    val lastPose: List<Float>? = null,
    val errors: List<String> = emptyList(),
) {
    fun toDisplayText(): String = buildString {
        appendLine(statusDetail)
        appendLine()
        appendLine("设备：${device.manufacturer} ${device.model} (${device.device})")
        appendLine("系统：Android ${device.androidRelease} / API ${device.apiLevel}")
        appendLine("ABI：${device.supportedAbis.joinToString()}")
        appendLine("ARCore：$arcoreVersion")
        appendLine("可用性：$availability；Depth AUTOMATIC：${depthAutomaticSupported?.toString() ?: "未检查"}")
        appendLine("会话：${if (sessionReady) "已创建" else "未创建"}")
        appendLine("跟踪：$trackingState；连续 ${formatSeconds(continuousTrackingMs)}；累计帧 $trackedFrameCount")
        camera?.let {
            appendLine("RGB：${it.width}x${it.height}；内参 ${it.intrinsicsWidth}x${it.intrinsicsHeight}")
            appendLine("fx/fy/cx/cy：${formatFloat(it.fx)} / ${formatFloat(it.fy)} / ${formatFloat(it.cx)} / ${formatFloat(it.cy)}")
        }
        appendLine("Full Depth：${depthLine(fullDepth)}")
        appendLine("Raw Depth：${depthLine(rawDepth)}")
        appendLine("Confidence：${depthLine(rawConfidence)}")
        appendLine("Raw 新深度/重投影样本：$newRawDepthSamples / $reprojectedRawDepthSamples")
        if (errors.isNotEmpty()) {
            appendLine("最近错误：${errors.last()}")
        }
    }

    fun toJson(createdAtEpochMs: Long): JSONObject {
        return JSONObject()
            .put("schemaVersion", 1)
            .put("createdAtEpochMs", createdAtEpochMs)
            .put("status", status.name)
            .put("statusDetail", statusDetail)
            .put("device", device.toJson())
            .put("availability", availability)
            .put("arcoreVersion", arcoreVersion)
            .put("sessionReady", sessionReady)
            .putNullable("depthAutomaticSupported", depthAutomaticSupported)
            .put("trackingState", trackingState)
            .put("trackedFrameCount", trackedFrameCount)
            .put("continuousTrackingMs", continuousTrackingMs)
            .putNullable("camera", camera?.toJson())
            .putNullable("fullDepth", fullDepth?.toJson())
            .putNullable("rawDepth", rawDepth?.toJson())
            .putNullable("rawConfidence", rawConfidence?.toJson())
            .put("fullDepthValidSamples", fullDepthValidSamples)
            .put("rawDepthValidSamples", rawDepthValidSamples)
            .put("confidenceValidSamples", confidenceValidSamples)
            .put("newRawDepthSamples", newRawDepthSamples)
            .put("reprojectedRawDepthSamples", reprojectedRawDepthSamples)
            .putNullable("lastPose", lastPose?.let { JSONArray(it) })
            .put("errors", JSONArray(errors))
    }

    private fun depthLine(snapshot: ImageSnapshot?): String {
        if (snapshot == null) return "暂无有效图像"
        val mean = snapshot.meanValidValue?.let { String.format(Locale.US, "%.1f", it) } ?: "-"
        return "${snapshot.width}x${snapshot.height}；有效覆盖率 ${formatPercent(snapshot.coverage)}；有效均值 $mean；时间戳 ${snapshot.timestampNs}"
    }

    private fun formatPercent(value: Double): String =
        String.format(Locale.US, "%.1f%%", value * 100.0)

    private fun formatSeconds(valueMs: Long): String =
        String.format(Locale.US, "%.1f 秒", valueMs / 1000.0)

    private fun formatFloat(value: Float): String = String.format(Locale.US, "%.2f", value)
}

private fun ImageSnapshot.toJson(): JSONObject = JSONObject()
    .put("format", format)
    .put("width", width)
    .put("height", height)
    .put("timestampNs", timestampNs)
    .put("rowStride", rowStride)
    .put("pixelStride", pixelStride)
    .put("sampledPixels", sampledPixels)
    .put("validPixels", validPixels)
    .put("coverage", coverage)
    .putNullable("meanValidValue", meanValidValue)

private fun JSONObject.putNullable(name: String, value: Any?): JSONObject =
    put(name, value ?: JSONObject.NULL)
