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
        val effect=BqEffectLayer(
            "fx","Glow",BqEffectKind.GLOW,
            parameters=mapOf("amount" to BqFloatParameter(4f,listOf(BqKeyframe(250,2f,BqInterpolation.EASE_IN_OUT))))
        )
        val stack=BqAdjustmentStack(
            effects=listOf(effect),
            colorGrade=BqColorGrade(exposure=.2f,contrast=.3f,highlights=.1f,shadows=-.1f,whites=.05f,blacks=-.05f,saturation=.4f,temperature=.2f,tint=-.1f),
            enabled=false
        )
        val stateWithStack=state.copy(adjustmentStacks=mapOf("clip" to stack))
        val restored=BqAdvancedStateCodec.fromJson(BqAdvancedStateCodec.toJson(stateWithStack))
        assertEquals(BqTransitionKind.FADE,restored.transitions["a"]?.kind)
        assertEquals(500,restored.transitions["a"]?.durationMs)
        assertEquals(BqBlendMode.SCREEN,restored.blendLayers.single().mode)
        assertTrue(restored.cornerPins["clip"]?.enabled == true)
        assertEquals(12,restored.motionBlur["clip"]?.samples)
        assertEquals(55f,restored.particleEmitters.single().rate)
        assertEquals(640,restored.proxy.width)
        assertEquals(1234,restored.audioMarkers.single().timeMs)
        assertEquals(false,restored.adjustmentStacks["clip"]?.enabled)
        assertEquals(BqEffectKind.GLOW,restored.adjustmentStacks["clip"]?.effects?.single()?.kind)
        assertEquals(2f,restored.adjustmentStacks["clip"]?.effects?.single()?.parameters?.get("amount")?.keyframes?.single()?.value)
        assertEquals(.4f,restored.adjustmentStacks["clip"]?.colorGrade?.saturation)
    }
}