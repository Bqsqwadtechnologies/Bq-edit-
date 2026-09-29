package com.bqsqwad.proedit.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BqAdvancedStateCodecTest {
    @Test fun roundTripPreservesCoreAdvancedState() {
        val state=BqAdvancedEditorState(
            transitions=mapOf("a" to BqTransitionSpec("a",BqTransitionKind.FADE,500,BqCurveEasing.EASE_IN_OUT,.8f)),
            blendLayers=listOf(BqBlendLayer("b","clip",BqBlendMode.SCREEN,.7f)),
            cornerPins=mapOf("clip" to BqCornerPin(enabled=true)),
            motionBlur=mapOf("clip" to BqMotionBlur(true,240f,12)),
            particleEmitters=listOf(BqParticleEmitter("p",true,55f,900,1.2f,1.5f,.2f,.1f)),
            proxy=BqProxySettings(true,640,360,"video/avc",1200000),
            audioMarkers=listOf(BqAudioMarker(1234,"kick"))
        )
        val restored=BqAdvancedStateCodec.fromJson(BqAdvancedStateCodec.toJson(state))
        assertEquals(BqTransitionKind.FADE,restored.transitions["a"]?.kind)
        assertEquals(500,restored.transitions["a"]?.durationMs)
        assertEquals(BqBlendMode.SCREEN,restored.blendLayers.single().mode)
        assertTrue(restored.cornerPins["clip"]?.enabled == true)
        assertEquals(12,restored.motionBlur["clip"]?.samples)
        assertEquals(55f,restored.particleEmitters.single().rate)
        assertEquals(640,restored.proxy.width)
        assertEquals(1234,restored.audioMarkers.single().timeMs)
    }
}