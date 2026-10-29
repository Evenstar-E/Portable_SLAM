package com.portableslam.slam.device

import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DepthBufferStatsTest {
    @Test
    fun unsigned16_respectsRowAndPixelStride() {
        val rowStride = 10
        val pixelStride = 4
        val buffer = ByteBuffer.allocate(rowStride * 2)
        write16(buffer, index = 0, value = 1000)
        write16(buffer, index = 4, value = 0)
        write16(buffer, index = 10, value = 2000)
        write16(buffer, index = 14, value = 3000)

        val stats = DepthBufferStats.readUnsigned16(
            width = 2,
            height = 2,
            source = buffer,
            rowStride = rowStride,
            pixelStride = pixelStride,
        )

        assertEquals(4, stats.sampledPixels)
        assertEquals(3, stats.validPixels)
        assertEquals(0.75, stats.coverage, 0.0)
        assertEquals(2000.0, stats.meanValidValue!!, 0.0)
    }

    @Test
    fun unsigned16_isLittleEndian() {
        val buffer = ByteBuffer.allocate(2)
        buffer.put(0, 0x34)
        buffer.put(1, 0x12)

        val stats = DepthBufferStats.readUnsigned16(1, 1, buffer, 2, 2)

        assertEquals(0x1234, stats.validValueSum.toInt())
    }

    @Test
    fun unsigned8_allZeroHasNoMean() {
        val buffer = ByteBuffer.allocate(4)

        val stats = DepthBufferStats.readUnsigned8(2, 2, buffer, 2, 1)

        assertEquals(0, stats.validPixels)
        assertNull(stats.meanValidValue)
    }

    @Test
    fun unsigned8_respectsPaddingAndRemainsUnsigned() {
        val buffer = ByteBuffer.allocate(12)
        buffer.put(3, 128.toByte())
        buffer.put(6, 255.toByte())
        buffer.put(9, 1)

        val stats = DepthBufferStats.readUnsigned8(2, 2, buffer, 6, 3)

        assertEquals(4, stats.sampledPixels)
        assertEquals(3, stats.validPixels)
        assertEquals(128.0, stats.meanValidValue!!, 0.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBufferShorterThanStrides() {
        DepthBufferStats.readUnsigned16(
            width = 2,
            height = 2,
            source = ByteBuffer.allocate(4),
            rowStride = 4,
            pixelStride = 2,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsTruncatedFinalDepthPixel() {
        DepthBufferStats.readUnsigned16(
            width = 1,
            height = 1,
            source = ByteBuffer.allocate(1),
            rowStride = 2,
            pixelStride = 2,
        )
    }

    private fun write16(buffer: ByteBuffer, index: Int, value: Int) {
        buffer.put(index, (value and 0xff).toByte())
        buffer.put(index + 1, ((value ushr 8) and 0xff).toByte())
    }
}
