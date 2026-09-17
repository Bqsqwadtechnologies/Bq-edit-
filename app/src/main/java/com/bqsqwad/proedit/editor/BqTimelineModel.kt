package com.bqsqwad.proedit.editor

/** Timeline primitives. ProjectModel owns clips; timeline tracks describe lanes and visibility. */
enum class BqTrackType { VIDEO, OVERLAY, AUDIO, TEXT, IMAGE, EFFECT }

data class BqTransformKeyframes(
    val positionX: List<BqKeyframe<Float>> = emptyList(),
    val positionY: List<BqKeyframe<Float>> = emptyList(),
    val scale: List<BqKeyframe<Float>> = emptyList(),
    val rotation: List<BqKeyframe<Float>> = emptyList(),
    val opacity: List<BqKeyframe<Float>> = emptyList()
)

data class BqTimelineItem(
    val id: String,
    val trackId: String,
    val sourceUri: android.net.Uri? = null,
    val startMs: Long,
    val durationMs: Long,
    val transform: BqTransformKeyframes = BqTransformKeyframes(),
    val effectIds: List<String> = emptyList()
)

data class BqTimelineTrack(
    val id: String,
    val type: BqTrackType,
    val name: String,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val visible: Boolean = true,
    val items: List<BqTimelineItem> = emptyList()
)

object BqTimelineOps {
    fun split(clip: BqClip, atMs: Long): Pair<BqClip, BqClip>? {
        val end = clip.endMs ?: return null
        if (atMs <= clip.startMs || atMs >= end) return null
        val left = clip.copy(id = java.util.UUID.randomUUID().toString(), endMs = atMs)
        val right = clip.copy(id = java.util.UUID.randomUUID().toString(), startMs = atMs)
        return left to right
    }

    fun trim(clip: BqClip, startMs: Long, endMs: Long): BqClip? {
        if (startMs < 0 || endMs <= startMs) return null
        if (clip.endMs != null && endMs > clip.endMs) return null
        return clip.copy(startMs = startMs, endMs = endMs)
    }

    fun duplicate(clip: BqClip): BqClip = clip.copy(id = java.util.UUID.randomUUID().toString(), name = "${clip.name} copy")

    fun snap(positionMs: Long, candidates: List<Long>, thresholdMs: Long = 120L): Long {
        val nearest = candidates.minByOrNull { kotlin.math.abs(it - positionMs) } ?: return positionMs
        return if (kotlin.math.abs(nearest - positionMs) <= thresholdMs) nearest else positionMs
    }
}
