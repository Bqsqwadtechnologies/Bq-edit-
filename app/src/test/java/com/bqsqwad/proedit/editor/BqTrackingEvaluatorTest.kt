package com.bqsqwad.proedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BqTrackingEvaluatorTest {
    @Test fun manualFallbackInterpolatesAcrossTimeline() {
        val binding = BqTrackingBinding(
            manualFallback = listOf(
                BqKeyframe(0L, BqPoint(0f, 0f)),
                BqKeyframe(1000L, BqPoint(1f, 1f))
            )
        )
        val point = BqTrackingEvaluator.sample(binding, 500L)
        assertEquals(.5f, point?.x ?: -1f, .001f)
        assertEquals(.5f, point?.y ?: -1f, .001f)
    }

    @Test fun confidenceThresholdIsRespected() {
        val binding = BqTrackingBinding(confidenceThreshold = .7f)
        assertFalse(BqTrackingEvaluator.accepted(binding, .69f))
        assertTrue(BqTrackingEvaluator.accepted(binding, .7f))
    }

    @Test fun fxTimelineWindowIsRespected() {
        val fx = BqFxLayer("fx", BqSuperpowerFxKind.ELECTRIC_AURA, startMs = 100L, endMs = 300L)
        assertFalse(BqTrackingEvaluator.active(fx, 99L))
        assertTrue(BqTrackingEvaluator.active(fx, 100L))
        assertFalse(BqTrackingEvaluator.active(fx, 300L))
    }
}
