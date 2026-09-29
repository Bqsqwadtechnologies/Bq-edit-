package com.bqsqwad.proedit.editor

/** Immutable hand-off between editor state and the eventual GPU/compositor/export stages. */
data class BqRenderPlan(
    val transition: BqTransitionSpec? = null,
    val blend: List<BqBlendLayer> = emptyList(),
    val cornerPin: BqCornerPin? = null,
    val rotoscope: BqRotoscopeRefinement? = null,
    val motionBlur: BqMotionBlur? = null,
    val particles: List<BqParticleEmitter> = emptyList(),
    val powerPresets: List<BqPowerPreset> = emptyList(),
    val compositions: List<BqFxComposition> = emptyList(),
    val proxy: BqProxySettings? = null,
    val masks: List<BqMask> = emptyList()
)