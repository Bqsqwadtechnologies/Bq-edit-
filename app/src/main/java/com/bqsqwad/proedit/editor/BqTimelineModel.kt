package com.bqsqwad.proedit.editor

import android.net.Uri

enum class BqTrackType { VIDEO, OVERLAY, AUDIO, TEXT, IMAGE, EFFECT }

data class BqKeyframe<T>(val timeMs: Long, val value: T)

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
    val sourceUri: Uri? = null,
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

fun <T> List<BqKeyframe<T>>.sampleAt(timeMs: Long): T? {
    if (isEmpty()) return null
    val before = lastOrNull { it.timeMs <= timeMs }
    val after = firstOrNull { it.timeMs >= timeMs }
    return when {
        before == null -> after?.value
        after == null -> before.value
        timeMs - before.timeMs <= after.timeMs - timeMs -> before.value
        else -> after.value
    }
}
