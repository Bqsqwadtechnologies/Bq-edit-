package com.bqsqwad.proedit.editor

/** Data model for real VFX modules. Rendering/tracking engines attach to these effect definitions. */
data class SuperpowerEffect(
    val id: String,
    val name: String,
    val description: String,
    val requiresPersonTracking: Boolean = false,
    val requiresSurfaceTracking: Boolean = false
)

object BqSuperpowerFxCatalog {
    val effects = listOf(
        SuperpowerEffect("speed", "Super Speed", "Accelerated motion and speed-ramp effect for one selected person", true),
        SuperpowerEffect("vanish", "Vanish", "Mask and tracked disappearance of one selected person", true),
        SuperpowerEffect("electric-hand", "Electric Hand", "Tracked hand-anchored electrical energy", true),
        SuperpowerEffect("laser", "Eye / Hand Laser", "Directional tracked beam with editable target point", true),
        SuperpowerEffect("flight", "Flight", "Tracked levitation and flight compositing", true),
        SuperpowerEffect("ring-transform", "Ring Transformation", "Ring-triggered transformation and teleport-style transition", true),
        SuperpowerEffect("energy-wave", "Energy Wave", "Fictional non-graphic energy burst compositing", true),
        SuperpowerEffect("suit-transform", "Superhero Suit Transformation", "Transform a selected person's appearance into an uploaded or preset suit", true),
        SuperpowerEffect("body-scale", "Person Scale", "Tracked perspective-aware enlargement or reduction of a selected person", true),
        SuperpowerEffect("body-stature", "Body Stature Transform", "Tracked silhouette transformation with adjustable proportions", true),
        SuperpowerEffect("object-remove", "Object Removal", "Remove a selected visual object using tracked masking and background reconstruction"),
        SuperpowerEffect("object-add", "Object Add", "Add an image, video, graphic or VFX layer with tracked placement"),
        SuperpowerEffect("wall-change", "Wall / Room Changer", "Replace or edit a wall surface with perspective-locked tracking", requiresSurfaceTracking = true),
        SuperpowerEffect("wall-add", "Wall / Room Additions", "Place new visual elements onto tracked room surfaces", requiresSurfaceTracking = true)
    )
}
