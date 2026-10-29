package com.portableslam.slam.device

import org.junit.Assert.assertEquals
import org.junit.Test

class GateEvaluatorTest {
    @Test
    fun unsupportedDepthIsBlockedEvenWhenTrackingWorks() {
        val result = evaluate(depthAutomaticSupported = false)

        assertEquals(ProbeStatus.BLOCKED, result.status)
    }

    @Test
    fun waitsForAllRequiredData() {
        val result = evaluate(
            rawDepthValidSamples = 2,
            confidenceValidSamples = 0,
            newRawDepthSamples = 1,
        )

        assertEquals(ProbeStatus.WAITING_FOR_MOTION, result.status)
    }

    @Test
    fun reprojectedDepthAloneDoesNotPass() {
        val result = evaluate(newRawDepthSamples = 1)

        assertEquals(ProbeStatus.WAITING_FOR_MOTION, result.status)
    }

    @Test
    fun passesWithCompleteSamples() {
        val result = evaluate()

        assertEquals(ProbeStatus.PASS, result.status)
    }

    private fun evaluate(
        depthAutomaticSupported: Boolean? = true,
        continuousTrackingMs: Long = 5_000,
        fullDepthValidSamples: Int = 5,
        rawDepthValidSamples: Int = 5,
        confidenceValidSamples: Int = 5,
        newRawDepthSamples: Int = 2,
    ): GateEvaluation = GateEvaluator.evaluate(
        depthAutomaticSupported = depthAutomaticSupported,
        continuousTrackingMs = continuousTrackingMs,
        fullDepthValidSamples = fullDepthValidSamples,
        rawDepthValidSamples = rawDepthValidSamples,
        confidenceValidSamples = confidenceValidSamples,
        newRawDepthSamples = newRawDepthSamples,
    )
}
