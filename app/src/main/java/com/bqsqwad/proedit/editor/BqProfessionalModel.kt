package com.bqsqwad.proedit.editor

import android.net.Uri

/** Shared non-destructive editing model. All editor features persist as project state. */
enum class BqMediaType { VIDEO, IMAGE, AUDIO }
enum class BqInterpolation { LINEAR, EASE_IN_OUT }
enum class BqEffectKind { BRIGHTNESS, CONTRAST, SATURATION, EXPOSURE, TEMPERATURE, TINT, SHARPEN, BLUR, VIGNETTE, GLOW, COLOR }
enum class BqMaskShape { RECTANGLE, ELLIPSE, POLYGON }
enum class BqBlendMode { NORMAL, ADD, SCREEN, MULTIPLY, OVERLAY }
enum class BqTransitionKind { CUT, FADE, CROSS_DISSOLVE, DIP_BLACK, DIP_WHITE, SLIDE, PUSH, ZOOM, BLUR }
enum class BqSuperpowerFxKind {
    SUIT_TRANSFORM, ENERGY_TRANSFORM, POWER_UP, SIZE_CHANGE, DISAPPEAR, SUPER_SPEED,
    FLY, TELEPORT_RING, ENERGY_BUBBLE, FORCE_WAVE, ELECTRIC_AURA, EYE_LASER,
    HAND_BEAM, GLOW_AURA, LIGHTNING, MOTION_TRAIL, ENERGY_PARTICLES, IMPACT
}

data class BqMediaAsset(
    val id: String,
    val uri: Uri,
    val name: String,
    val type: BqMediaType,
    val durationMs: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val rotation: Int = 0,
    val sizeBytes: Long = 0L,
    val missing: Boolean = false
)

data class BqKeyframe<T>(val timeMs: Long, val value: T, val interpolation: BqInterpolation = BqInterpolation.LINEAR)

data class BqFloatParameter(
    val value: Float,
    val keyframes: List<BqKeyframe<Float>> = emptyList()
)

data class BqTransform(
    val x: Float = 0f,
    val y: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,
    val opacity: Float = 1f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f,
    val keyframes: BqTransformKeyframes = BqTransformKeyframes()
)

data class BqEffectLayer(
    val id: String,
    val name: String,
    val kind: BqEffectKind,
    val enabled: Boolean = true,
    val parameters: Map<String, BqFloatParameter> = emptyMap()
)

data class BqMask(
    val id: String,
    val shape: BqMaskShape,
    val points: List<BqPoint> = emptyList(),
    val feather: Float = 0f,
    val invert: Boolean = false,
    val opacity: Float = 1f,
    val blendMode: BqBlendMode = BqBlendMode.NORMAL,
    val keyframes: List<BqKeyframe<List<BqPoint>>> = emptyList()
)

data class BqChromaKey(
    val enabled: Boolean = false,
    val colorArgb: Long = 0xFF00FF00,
    val similarity: Float = 0.4f,
    val softness: Float = 0.1f,
    val spillSuppression: Float = 0f
)

data class BqTrackingBinding(
    val selectedPersonId: Int? = null,
    val selectedObjectId: Int? = null,
    val selectedSurfaceId: Int? = null,
    val anchor: BqPoint? = null,
    val confidenceThreshold: Float = 0.35f,
    val manualFallback: List<BqKeyframe<BqPoint>> = emptyList()
)

data class BqFxLayer(
    val id: String,
    val kind: BqSuperpowerFxKind,
    val enabled: Boolean = true,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val tracking: BqTrackingBinding = BqTrackingBinding(),
    val parameters: Map<String, BqFloatParameter> = emptyMap(),
    val masks: List<BqMask> = emptyList()
)

data class BqWallSurface(
    val id: String,
    val assetUri: Uri? = null,
    val corners: List<BqPoint> = emptyList(),
    val tracking: List<BqSurfaceTrack> = emptyList(),
    val feather: Float = 0f,
    val opacity: Float = 1f,
    val blendMode: BqBlendMode = BqBlendMode.NORMAL
)

data class BqCaption(
    val id: String,
    val text: String,
    val startMs: Long,
    val endMs: Long,
    val fontFamily: String = "sans-serif",
    val sizeSp: Float = 24f,
    val colorArgb: Long = 0xFFFFFFFF,
    val strokeArgb: Long = 0xFF000000,
    val strokeWidth: Float = 2f,
    val x: Float = 0.5f,
    val y: Float = 0.85f
)

data class BqAudioSettings(
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val ducking: Float = 0f
)

data class BqTransition(
    val kind: BqTransitionKind = BqTransitionKind.CUT,
    val durationMs: Long = 0L,
    val parameter: Float = 0f
)

data class BqColorGrade(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val saturation: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val lutUri: Uri? = null
)

data class BqClip(
    val id: String,
    val uri: Uri,
    val name: String,
    val startMs: Long = 0L,
    val endMs: Long? = null,
    val speed: Float = 1f,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val x: Float = 0f,
    val y: Float = 0f,
    val volume: Float = 1f,
    val trackId: String = "video-1",
    val mediaType: BqMediaType = BqMediaType.VIDEO,
    val effects: List<BqEffectLayer> = emptyList(),
    val fxLayers: List<BqFxLayer> = emptyList(),
    val masks: List<BqMask> = emptyList(),
    val chromaKey: BqChromaKey = BqChromaKey(),
    val colorGrade: BqColorGrade = BqColorGrade(),
    val transform: BqTransform = BqTransform(),
    val audio: BqAudioSettings = BqAudioSettings()
)

data class BqProject(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "Untitled Project",
    val version: Int = 3,
    val clips: List<BqClip> = emptyList(),
    val assets: List<BqMediaAsset> = emptyList(),
    val tracks: List<BqTimelineTrack> = emptyList(),
    val captions: List<BqCaption> = emptyList(),
    val walls: List<BqWallSurface> = emptyList(),
    val selectedClipId: String? = null,
    val playheadMs: Long = 0L,
    val aspectRatio: String = "16:9",
    val fps: Int = 30,
    val backgroundArgb: Long = 0xFF000000,
    val markersMs: List<Long> = emptyList(),
    val lastSavedAt: Long = System.currentTimeMillis(),
    val advanced: BqAdvancedEditorState = BqAdvancedEditorState()
)

data class BqExportConfig(
    val width: Int = 1920,
    val height: Int = 1080,
    val fps: Int = 30,
    val bitrate: Int = 12_000_000,
    val videoMime: String = "video/avc",
    val audioMime: String = "audio/mp4a-latm",
    val fileNamePrefix: String = "BQ_Pro_Edit"
)

object BqInterpolationEngine {
    fun sample(keys: List<BqKeyframe<Float>>, timeMs: Long, fallback: Float): Float {
        if (keys.isEmpty()) return fallback
        val sorted = keys.sortedBy { it.timeMs }
        val before = sorted.lastOrNull { it.timeMs <= timeMs } ?: return sorted.first().value
        val after = sorted.firstOrNull { it.timeMs >= timeMs } ?: return before.value
        if (before.timeMs == after.timeMs) return before.value
        val t = ((timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs)).coerceIn(0f, 1f)
        val eased = if (before.interpolation == BqInterpolation.EASE_IN_OUT) t * t * (3f - 2f * t) else t
        return before.value + (after.value - before.value) * eased
    }
}

object BqProjectValidator {
    fun validate(project: BqProject): List<String> = buildList {
        if (project.name.isBlank()) add("Project name is empty")
        project.clips.forEach { clip ->
            if (clip.startMs < 0) add("Clip ${clip.name}: invalid start")
            if (clip.endMs != null && clip.endMs <= clip.startMs) add("Clip ${clip.name}: end must be after start")
            if (clip.speed !in 0.25f..4f) add("Clip ${clip.name}: speed must be between 0.25x and 4x")
            if (clip.scale <= 0f) add("Clip ${clip.name}: scale must be positive")
        }
        if (project.fps !in 1..120) add("Unsupported project frame rate")
    }
}


enum class BqCurveEasing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, HOLD, BEZIER }

data class BqTransitionSpec(
    val id: String = java.util.UUID.randomUUID().toString(),
    val kind: BqTransitionKind = BqTransitionKind.CUT,
    val durationMs: Long = 0L,
    val easing: BqCurveEasing = BqCurveEasing.LINEAR,
    val amount: Float = 1f
)

data class BqBlendLayer(
    val id: String = java.util.UUID.randomUUID().toString(),
    val sourceClipId: String,
    val mode: BqBlendMode = BqBlendMode.NORMAL,
    val opacity: Float = 1f,
    val enabled: Boolean = true
)

data class BqCornerPin(
    val enabled: Boolean = false,
    val topLeft: BqPoint = BqPoint(0f, 0f),
    val topRight: BqPoint = BqPoint(1f, 0f),
    val bottomRight: BqPoint = BqPoint(1f, 1f),
    val bottomLeft: BqPoint = BqPoint(0f, 1f),
    val keyframes: List<BqKeyframe<List<BqPoint>>> = emptyList()
)

data class BqRotoscopeRefinement(
    val enabled: Boolean = false,
    val points: List<BqPoint> = emptyList(),
    val feather: Float = 0f,
    val expansion: Float = 0f,
    val invert: Boolean = false
)

data class BqMotionBlur(
    val enabled: Boolean = false,
    val shutterAngle: Float = 180f,
    val samples: Int = 8
)

data class BqParticleEmitter(
    val id: String = java.util.UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    val rate: Float = 40f,
    val lifetimeMs: Long = 800L,
    val size: Float = 1f,
    val speed: Float = 1f,
    val turbulence: Float = 0f,
    val gravity: Float = 0f
)

data class BqPowerPreset(
    val id: String,
    val name: String,
    val kind: BqSuperpowerFxKind,
    val parameters: Map<String, Float> = emptyMap()
)

data class BqFxComposition(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String = "FX Composition",
    val layers: List<String> = emptyList(),
    val enabled: Boolean = true
)

data class BqProxySettings(
    val enabled: Boolean = false,
    val width: Int = 960,
    val height: Int = 540,
    val videoMime: String = "video/avc",
    val bitrate: Int = 2_500_000
)

data class BqExportPreset(
    val id: String,
    val name: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val bitrate: Int
)

data class BqAudioMarker(
    val timeMs: Long,
    val label: String = "Beat"
)

data class BqProjectVersion(
    val id: String = java.util.UUID.randomUUID().toString(),
    val createdAt: Long = System.currentTimeMillis(),
    val label: String = "Version",
    val projectId: String
)

data class BqAdjustmentStack(
    val effects: List<BqEffectLayer> = emptyList(),
    val colorGrade: BqColorGrade = BqColorGrade(),
    val enabled: Boolean = true
)

data class BqAdvancedEditorState(
    val transitions: Map<String, BqTransitionSpec> = emptyMap(),
    val blendLayers: List<BqBlendLayer> = emptyList(),
    val cornerPins: Map<String, BqCornerPin> = emptyMap(),
    val rotoscopes: Map<String, BqRotoscopeRefinement> = emptyMap(),
    val motionBlur: Map<String, BqMotionBlur> = emptyMap(),
    val particleEmitters: List<BqParticleEmitter> = emptyList(),
    val powerPresets: List<BqPowerPreset> = emptyList(),
    val compositions: List<BqFxComposition> = emptyList(),
    val proxy: BqProxySettings = BqProxySettings(),
    val exportPresets: List<BqExportPreset> = emptyList(),
    val audioMarkers: List<BqAudioMarker> = emptyList(),
    val versions: List<BqProjectVersion> = emptyList(),
    val adjustmentStacks: Map<String, BqAdjustmentStack> = emptyMap()
)
