package com.bqsqwad.proedit.editor

/** Frame-by-frame anchors used by person/object/surface effects. */
data class BqPoint(val x: Float, val y: Float)

data class BqTrackedBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val center: BqPoint get() = BqPoint((left + right) / 2f, (top + bottom) / 2f)
}

data class BqPersonPose(
    val frameTimeUs: Long,
    val body: BqTrackedBox,
    val landmarks: Map<String, BqPoint>,
    val confidence: Float
)

data class BqTrackedObject(
    val trackingId: Int,
    val label: String,
    val frameTimeUs: Long,
    val box: BqTrackedBox,
    val confidence: Float
)

data class BqSurfaceTrack(
    val frameTimeUs: Long,
    val corners: List<BqPoint>,
    val confidence: Float
) {
    init { require(corners.size == 4) { "A surface track needs four corners" } }
}

data class BqFxSelection(
    val effectId: String,
    val targetPersonId: Int? = null,
    val targetObjectId: Int? = null,
    val targetSurfaceId: Int? = null,
    val anchor: BqPoint? = null,
    val startMs: Long = 0L,
    val endMs: Long? = null
)
