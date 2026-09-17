package com.bqsqwad.proedit.editor

import kotlin.math.abs

/** Lightweight temporal association used between ML Kit detections; manual keyframes remain the fallback. */
class BqTemporalTracker(private val maxDistancePx: Float = 180f) {
    private val last = linkedMapOf<Int, BqTrackedBox>()
    private var nextSyntheticId = 1000

    fun associate(detections: List<BqTrackedObject>): List<BqTrackedObject> {
        val result = detections.map { detection ->
            if (detection.trackingId >= 0) {
                last[detection.trackingId] = detection.box
                detection
            } else {
                val center = detection.box.center
                val match = last.entries.minByOrNull { (_, box) ->
                    abs(box.center.x - center.x) + abs(box.center.y - center.y)
                }
                val id = if (match != null && abs(match.value.center.x - center.x) + abs(match.value.center.y - center.y) <= maxDistancePx) match.key else nextSyntheticId++
                last[id] = detection.box
                detection.copy(trackingId = id)
            }
        }
        val live = result.map { it.trackingId }.toSet()
        last.keys.retainAll(live)
        return result
    }

    fun reset() = last.clear()
}
