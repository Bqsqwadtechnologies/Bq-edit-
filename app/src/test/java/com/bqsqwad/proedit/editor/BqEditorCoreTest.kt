package com.bqsqwad.proedit.editor

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class BqEditorCoreTest {
    private val uri: Uri = mock(Uri::class.java)

    @Test fun splitProducesTwoNonOverlappingClips() {
        val clip = BqClip("a", uri, "clip", startMs = 0, endMs = 10_000)
        val pair = BqTimelineOps.split(clip, 4_000)
        assertNotNull(pair)
        assertEquals(4_000L, pair!!.first.endMs)
        assertEquals(4_000L, pair.second.startMs)
        assertEquals(10_000L, pair.second.endMs)
        assertTrue(pair.first.id != pair.second.id)
    }

    @Test fun invalidSplitIsRejected() {
        val clip = BqClip("a", uri, "clip", 0, 10_000)
        assertNull(BqTimelineOps.split(clip, 0))
        assertNull(BqTimelineOps.split(clip, 10_000))
    }

    @Test fun trimKeepsClipInsideRequestedRange() {
        val clip = BqClip("a", uri, "clip", 0, 10_000)
        val trimmed = BqTimelineOps.trim(clip, 2_000, 7_500)
        assertNotNull(trimmed)
        assertEquals(2_000L, trimmed!!.startMs)
        assertEquals(7_500L, trimmed.endMs)
    }

    @Test fun invalidTrimIsRejected() {
        val clip = BqClip("a", uri, "clip", 0, 10_000)
        assertNull(BqTimelineOps.trim(clip, 7_500, 2_000))
        assertNull(BqTimelineOps.trim(clip, 0, 10_001))
    }

    @Test fun keyframeInterpolationIsLinear() {
        val keys = listOf(BqKeyframe(0, 0f), BqKeyframe(1_000, 10f))
        assertEquals(5f, BqInterpolationEngine.sample(keys, 500, 0f), 0.001f)
    }

    @Test fun easedKeyframeInterpolationIsBounded() {
        val keys = listOf(BqKeyframe(0, 0f, BqInterpolation.EASE_IN_OUT), BqKeyframe(1_000, 10f))
        val value = BqInterpolationEngine.sample(keys, 500, 0f)
        assertTrue(value in 0f..10f)
    }

    @Test fun validatorFindsInvalidClip() {
        val bad = BqProject(clips = listOf(BqClip("a", uri, "bad", startMs = 10_000, endMs = 1_000)))
        assertTrue(BqProjectValidator.validate(bad).isNotEmpty())
    }
    @Test fun snapUsesNearestCandidateWithinThreshold() {
        assertEquals(1_020L, BqTimelineOps.snap(1_000L, listOf(500L, 1_020L), 50L))
        assertEquals(1_000L, BqTimelineOps.snap(1_000L, listOf(1_200L), 50L))
    }

    @Test fun projectValidatorRejectsUnsupportedFps() {
        assertTrue(BqProjectValidator.validate(BqProject(fps = 0)).any { it.contains("frame rate") })
    }

}
