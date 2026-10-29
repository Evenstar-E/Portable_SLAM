package com.portableslam.slam.device

import android.media.Image
import java.nio.ByteBuffer

data class BufferStats(
    val sampledPixels: Int,
    val validPixels: Int,
    val validValueSum: Double,
) {
    val coverage: Double
        get() = if (sampledPixels == 0) 0.0 else validPixels.toDouble() / sampledPixels

    val meanValidValue: Double?
        get() = if (validPixels == 0) null else validValueSum / validPixels
}

data class ImageSnapshot(
    val format: Int,
    val width: Int,
    val height: Int,
    val timestampNs: Long,
    val rowStride: Int,
    val pixelStride: Int,
    val sampledPixels: Int,
    val validPixels: Int,
    val coverage: Double,
    val meanValidValue: Double?,
)

/** Reads unsigned depth/confidence pixels without assuming tightly packed rows. */
object DepthBufferStats {
    fun readUnsigned16(
        width: Int,
        height: Int,
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
    ): BufferStats {
        require(pixelStride >= 2) { "DEPTH16 pixelStride must be at least 2" }
        return read(width, height, source, rowStride, pixelStride, valueSizeBytes = 2) { buffer, index ->
            val low = buffer.get(index).toInt() and 0xff
            val high = buffer.get(index + 1).toInt() and 0xff
            (high shl 8) or low
        }
    }

    fun readUnsigned8(
        width: Int,
        height: Int,
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
    ): BufferStats {
        require(pixelStride >= 1) { "Confidence pixelStride must be positive" }
        return read(width, height, source, rowStride, pixelStride, valueSizeBytes = 1) { buffer, index ->
            buffer.get(index).toInt() and 0xff
        }
    }

    private inline fun read(
        width: Int,
        height: Int,
        source: ByteBuffer,
        rowStride: Int,
        pixelStride: Int,
        valueSizeBytes: Int,
        readValue: (ByteBuffer, Int) -> Int,
    ): BufferStats {
        require(width > 0 && height > 0) { "Image dimensions must be positive" }
        require(rowStride >= width * pixelStride) { "rowStride is smaller than one pixel row" }

        val buffer = source.duplicate()
        buffer.rewind()
        val lastIndex = (height - 1) * rowStride + (width - 1) * pixelStride
        require(lastIndex + valueSizeBytes <= buffer.limit()) {
            "Image buffer is shorter than its strides require"
        }

        var validPixels = 0
        var validValueSum = 0.0
        for (y in 0 until height) {
            val rowStart = y * rowStride
            for (x in 0 until width) {
                val value = readValue(buffer, rowStart + x * pixelStride)
                if (value > 0) {
                    validPixels++
                    validValueSum += value
                }
            }
        }
        return BufferStats(width * height, validPixels, validValueSum)
    }
}

object ImageMetricsReader {
    fun readDepth(image: Image): ImageSnapshot = read(image, unsigned16 = true)

    fun readConfidence(image: Image): ImageSnapshot = read(image, unsigned16 = false)

    private fun read(image: Image, unsigned16: Boolean): ImageSnapshot {
        val plane = image.planes.single()
        val stats = if (unsigned16) {
            DepthBufferStats.readUnsigned16(
                image.width,
                image.height,
                plane.buffer,
                plane.rowStride,
                plane.pixelStride,
            )
        } else {
            DepthBufferStats.readUnsigned8(
                image.width,
                image.height,
                plane.buffer,
                plane.rowStride,
                plane.pixelStride,
            )
        }
        return ImageSnapshot(
            format = image.format,
            width = image.width,
            height = image.height,
            timestampNs = image.timestamp,
            rowStride = plane.rowStride,
            pixelStride = plane.pixelStride,
            sampledPixels = stats.sampledPixels,
            validPixels = stats.validPixels,
            coverage = stats.coverage,
            meanValidValue = stats.meanValidValue,
        )
    }
}
