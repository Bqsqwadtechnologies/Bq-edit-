package com.bqsqwad.proedit.editor

data class SuperpowerEffect(
    val id: String,
    val name: String,
    val description: String,
    val kind: BqSuperpowerFxKind,
    val requiresPersonTracking: Boolean = false,
    val requiresSurfaceTracking: Boolean = false
)

/** Catalog only describes real compositing modules; execution is performed by the shared effect pipeline. */
object BqSuperpowerFxCatalog {
    val effects = listOf(
        SuperpowerEffect("suit-transform", "Suit Transformation", "Tracked appearance replacement layer", BqSuperpowerFxKind.SUIT_TRANSFORM, true),
        SuperpowerEffect("energy-transform", "Energy Transformation", "Tracked energy overlay and compositing", BqSuperpowerFxKind.ENERGY_TRANSFORM, true),
        SuperpowerEffect("power-up", "Power Up", "Animated glow and energy build-up", BqSuperpowerFxKind.POWER_UP, true),
        SuperpowerEffect("size-change", "Person Scale", "Tracked perspective-aware size transform", BqSuperpowerFxKind.SIZE_CHANGE, true),
        SuperpowerEffect("vanish", "Vanish", "Tracked mask/disappearance layer", BqSuperpowerFxKind.DISAPPEAR, true),
        SuperpowerEffect("speed", "Super Speed", "Selected-person speed treatment and motion trail", BqSuperpowerFxKind.SUPER_SPEED, true),
        SuperpowerEffect("flight", "Flight", "Tracked levitation transform layer", BqSuperpowerFxKind.FLY, true),
        SuperpowerEffect("teleport", "Teleport Ring", "Tracked ring transition layer", BqSuperpowerFxKind.TELEPORT_RING, true),
        SuperpowerEffect("bubble", "Energy Bubble", "Tracked subject bubble layer", BqSuperpowerFxKind.ENERGY_BUBBLE, true),
        SuperpowerEffect("force-wave", "Force Wave", "Fictional energy wave compositing", BqSuperpowerFxKind.FORCE_WAVE, true),
        SuperpowerEffect("electric", "Electric Aura", "Tracked electrical energy around a subject", BqSuperpowerFxKind.ELECTRIC_AURA, true),
        SuperpowerEffect("eye-laser", "Eye Laser", "Tracked directional beam with target anchor", BqSuperpowerFxKind.EYE_LASER, true),
        SuperpowerEffect("hand-beam", "Hand Energy Beam", "Tracked hand-anchored beam", BqSuperpowerFxKind.HAND_BEAM, true),
        SuperpowerEffect("glow", "Glow / Aura", "Tracked glow compositing layer", BqSuperpowerFxKind.GLOW_AURA, true),
        SuperpowerEffect("lightning", "Lightning", "Tracked lightning overlay", BqSuperpowerFxKind.LIGHTNING, true),
        SuperpowerEffect("trail", "Motion Trail", "Tracked temporal trail layer", BqSuperpowerFxKind.MOTION_TRAIL, true),
        SuperpowerEffect("particles", "Energy Particles", "Tracked particle emitter layer", BqSuperpowerFxKind.ENERGY_PARTICLES, true),
        SuperpowerEffect("impact", "Impact", "Non-graphic fictional impact effect", BqSuperpowerFxKind.IMPACT, true),
        SuperpowerEffect("wall-change", "Wall Changer", "Perspective-locked replacement surface", BqSuperpowerFxKind.ENERGY_TRANSFORM, requiresSurfaceTracking = true),
        SuperpowerEffect("wall-add", "Wall Additions", "Perspective-locked image/object layer", BqSuperpowerFxKind.GLOW_AURA, requiresSurfaceTracking = true)
    )
}
