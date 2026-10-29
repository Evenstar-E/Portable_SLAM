package com.portableslam.slam

import android.Manifest
import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Surface
import android.view.View
import android.widget.Button
import android.widget.TextView
import com.google.ar.core.ArCoreApk
import com.portableslam.slam.device.ArCapabilityGate
import com.portableslam.slam.device.CameraPreviewRenderer
import com.portableslam.slam.device.DeviceSnapshot
import com.portableslam.slam.device.ProbeStatus
import java.io.FileNotFoundException

class MainActivity : Activity() {
    private lateinit var gate: ArCapabilityGate
    private lateinit var glSurface: GLSurfaceView
    private lateinit var statusText: TextView
    private lateinit var reportText: TextView
    private lateinit var previewText: TextView
    private lateinit var startButton: Button
    private lateinit var exportButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var detectionStarted = false
    private var installInProgress = false
    private var availabilityRetries = 0
    private var pendingReportJson: String? = null

    private val uiUpdater = object : Runnable {
        override fun run() {
            val snapshot = gate.snapshot
            statusText.text = snapshot.statusDetail
            statusText.setTextColor(
                when (snapshot.status) {
                    ProbeStatus.PASS -> Color.rgb(115, 210, 176)
                    ProbeStatus.BLOCKED, ProbeStatus.ERROR -> Color.rgb(255, 138, 128)
                    else -> Color.WHITE
                },
            )
            reportText.text = snapshot.toDisplayText()
            previewText.visibility = if (snapshot.camera == null) View.VISIBLE else View.GONE
            exportButton.isEnabled = detectionStarted
            handler.postDelayed(this, UI_REFRESH_INTERVAL_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        pendingReportJson = savedInstanceState?.getString(STATE_PENDING_REPORT)

        statusText = findViewById(R.id.status_text)
        reportText = findViewById(R.id.report_text)
        previewText = findViewById(R.id.preview_text)
        startButton = findViewById(R.id.start_button)
        exportButton = findViewById(R.id.export_button)
        glSurface = findViewById(R.id.gl_surface)

        gate = ArCapabilityGate(this, createDeviceSnapshot())
        val renderer = CameraPreviewRenderer(
            gate = gate,
            displayRotation = ::currentDisplayRotation,
            onFirstFrame = {
                runOnUiThread { previewText.visibility = View.GONE }
            },
        )
        glSurface.setEGLContextClientVersion(3)
        glSurface.preserveEGLContextOnPause = true
        glSurface.setRenderer(renderer)
        glSurface.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        startButton.setOnClickListener { beginDetection() }
        exportButton.setOnClickListener { chooseExportDestination() }
    }

    override fun onResume() {
        super.onResume()
        glSurface.onResume()
        handler.post(uiUpdater)
        if (detectionStarted) {
            if (installInProgress) {
                installInProgress = false
                checkArCoreAvailability()
            } else {
                gate.resume()
            }
        }
    }

    override fun onPause() {
        handler.removeCallbacks(uiUpdater)
        glSurface.onPause()
        gate.pause()
        super.onPause()
    }

    override fun onDestroy() {
        detectionStarted = false
        handler.removeCallbacksAndMessages(null)
        gate.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_PENDING_REPORT, pendingReportJson)
        super.onSaveInstanceState(outState)
    }

    private fun beginDetection() {
        detectionStarted = true
        installInProgress = false
        availabilityRetries = 0
        exportButton.isEnabled = true
        startButton.isEnabled = false
        startButton.setText(R.string.retry_detection)
        previewText.visibility = View.VISIBLE
        previewText.setText(R.string.waiting_preview)
        gate.close()
        gate.markChecking("正在检查相机权限…")

        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), REQUEST_CAMERA_PERMISSION)
        } else {
            checkArCoreAvailability()
        }
    }

    private fun checkArCoreAvailability() {
        if (!detectionStarted) return
        gate.markChecking("正在检查 ARCore 可用性…")
        try {
            val availability = ArCoreApk.getInstance().checkAvailability(this)
            gate.setAvailability(availability.name, readArCoreVersion())
            when (availability) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    startButton.isEnabled = true
                    gate.start()
                }

                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED,
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                -> requestArCoreInstallation()

                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    startButton.isEnabled = true
                    gate.markBlocked(
                        "ARCore 报告此设备不支持；完整 SLAM/重建路线在此真机上不可继续",
                        "ArCoreApk: UNSUPPORTED_DEVICE_NOT_CAPABLE",
                    )
                }

                ArCoreApk.Availability.UNKNOWN_CHECKING -> {
                    availabilityRetries++
                    if (availabilityRetries <= MAX_AVAILABILITY_RETRIES) {
                        handler.postDelayed(
                            { checkArCoreAvailability() },
                            AVAILABILITY_RETRY_DELAY_MS,
                        )
                    } else {
                        startButton.isEnabled = true
                        gate.markBlocked("ARCore 可用性查询超时，请检查系统 ARCore 组件后重试")
                    }
                }

                else -> {
                    startButton.isEnabled = true
                    gate.markBlocked("ARCore 可用性未知（${availability.name}），请稍后重试")
                }
            }
        } catch (exception: Exception) {
            startButton.isEnabled = true
            gate.markBlocked(
                "ARCore 可用性检查失败：${exception.javaClass.simpleName}: ${exception.message ?: "无详情"}",
                exception.stackTraceToString(),
            )
        }
    }

    private fun requestArCoreInstallation() {
        try {
            gate.markChecking("需要安装或更新 Google Play Services for AR（ARCore）…")
            val status = ArCoreApk.getInstance().requestInstall(this, true)
            if (status == ArCoreApk.InstallStatus.INSTALL_REQUESTED) {
                installInProgress = true
                startButton.isEnabled = true
                gate.markChecking("请在系统界面完成 ARCore 安装/更新；返回后会自动重新检查")
            } else {
                startButton.isEnabled = true
                gate.start()
            }
        } catch (exception: Exception) {
            startButton.isEnabled = true
            gate.markBlocked(
                "无法安装或更新 ARCore：${exception.javaClass.simpleName}: ${exception.message ?: "无详情"}",
                exception.stackTraceToString(),
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CAMERA_PERMISSION) return

        startButton.isEnabled = true
        if (grantResults.singleOrNull() == PackageManager.PERMISSION_GRANTED) {
            checkArCoreAvailability()
            return
        }

        val permanentlyDenied = !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        val detail = if (permanentlyDenied) {
            "相机权限被拒绝且不再询问；请在系统应用设置中允许相机后重试"
        } else {
            "相机权限被拒绝；没有相机权限无法创建 ARCore 会话"
        }
        gate.markBlocked(detail, detail)
    }

    private fun chooseExportDestination() {
        pendingReportJson = gate.snapshot.toJson(System.currentTimeMillis()).toString(2)
        val safeModel = Build.MODEL.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "arcore-probe-$safeModel-${System.currentTimeMillis()}.json")
        }
        startActivityForResult(intent, REQUEST_EXPORT_REPORT)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_EXPORT_REPORT || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val report = pendingReportJson ?: return

        try {
            val written = contentResolver.openOutputStream(uri, "w")?.bufferedWriter(Charsets.UTF_8)?.use {
                it.write(report)
                true
            } ?: false
            if (!written) throw FileNotFoundException("系统未提供可写输出流")
            statusText.text = getString(R.string.export_success, uri.toString())
        } catch (exception: Exception) {
            val detail = "${exception.javaClass.simpleName}: ${exception.message ?: "无详情"}"
            statusText.text = getString(R.string.export_failure, detail)
        }
    }

    private fun createDeviceSnapshot(): DeviceSnapshot {
        val activityManager = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val appVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionName ?: "未知"
        } catch (_: PackageManager.NameNotFoundException) {
            "未知"
        }
        return DeviceSnapshot(
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            device = Build.DEVICE,
            androidRelease = Build.VERSION.RELEASE,
            apiLevel = Build.VERSION.SDK_INT,
            supportedAbis = Build.SUPPORTED_ABIS.toList(),
            totalRamMb = memoryInfo.totalMem / (1024L * 1024L),
            appVersion = appVersion,
        )
    }

    private fun readArCoreVersion(): String {
        return try {
            val info = if (Build.VERSION.SDK_INT >= 33) {
                packageManager.getPackageInfo(
                    ARCORE_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0),
                )
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(ARCORE_PACKAGE_NAME, 0)
            }
            info.versionName ?: "已安装，版本未知"
        } catch (_: PackageManager.NameNotFoundException) {
            "未安装或当前 ROM 无法读取"
        }
    }

    private fun currentDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= 30) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
    }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 41
        private const val REQUEST_EXPORT_REPORT = 42
        private const val ARCORE_PACKAGE_NAME = "com.google.ar.core"
        private const val STATE_PENDING_REPORT = "pending_report"
        private const val MAX_AVAILABILITY_RETRIES = 20
        private const val AVAILABILITY_RETRY_DELAY_MS = 250L
        private const val UI_REFRESH_INTERVAL_MS = 500L
    }
}
