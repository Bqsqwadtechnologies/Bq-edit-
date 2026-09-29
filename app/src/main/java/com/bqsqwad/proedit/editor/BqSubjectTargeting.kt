package com.bqsqwad.proedit.editor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class BqSubjectPart {
    WHOLE_PERSON, HEAD, FACE, LEFT_EAR, RIGHT_EAR, LEFT_HAND, RIGHT_HAND, LEFT_FOOT, RIGHT_FOOT, TORSO
}

data class BqSubjectTarget(
    val subjectId: Int,
    val part: BqSubjectPart = BqSubjectPart.WHOLE_PERSON,
    val confidenceThreshold: Float = 0.45f,
    val feather: Float = 0.08f,
    val expansion: Float = 0f
)

/** A frame-addressable mask produced by the segmentation stage and consumed by the GPU compositor. */
data class BqSubjectMaskFrame(
    val timeUs: Long,
    val width: Int,
    val height: Int,
    val confidence: FloatArray,
    val subjectId: Int,
    val part: BqSubjectPart,
    val feather: Float,
    val expansion: Float
)

object BqSubjectMaskBuilder {
    fun fromSegmentation(
        mask: SegmentationMask,
        timeUs: Long,
        target: BqSubjectTarget,
        subjectBox: BqTrackedBox? = null
    ): BqSubjectMaskFrame {
        val width = mask.width
        val height = mask.height
        val values = FloatArray(width * height)
        var sum = 0f
        for (i in values.indices) {
            val v = mask.buffer.getFloat(i * 4).coerceIn(0f, 1f)
            values[i] = if (subjectBox == null) v else {
                val x = (i % width).toFloat() / width
                val y = (i / width).toFloat() / height
                if (x in subjectBox.left..subjectBox.right && y in subjectBox.top..subjectBox.bottom) v else 0f
            }
            sum += values[i]
        }
        val confidence = if (values.isEmpty()) 0f else sum / values.size
        return BqSubjectMaskFrame(timeUs, width, height, values, target.subjectId, target.part, target.feather, target.expansion)
    }
}

class BqSubjectMaskAnalyzer {
    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder().setDetectorMode(SelfieSegmenterOptions.STREAM_MODE).build()
    )
    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )

    fun analyze(bitmap: Bitmap, timeUs: Long, target: BqSubjectTarget, subjectBox: BqTrackedBox?): BqSubjectMaskFrame {
        val segmentation = Tasks.await(segmenter.process(InputImage.fromBitmap(bitmap, 0)))
        val pose = Tasks.await(poseDetector.process(InputImage.fromBitmap(bitmap, 0)))
        val width = segmentation.width
        val height = segmentation.height
        val values = FloatArray(width * height)
        val landmarks = pose.allPoseLandmarks.associateBy { it.landmarkType }
        fun point(type: Int) = landmarks[type]?.let { BqPoint(it.position.x / bitmap.width, it.position.y / bitmap.height) }
        val head = point(PoseLandmark.NOSE)
        val leftHand = point(PoseLandmark.LEFT_WRIST)
        val rightHand = point(PoseLandmark.RIGHT_WRIST)
        val leftFoot = point(PoseLandmark.LEFT_ANKLE)
        val rightFoot = point(PoseLandmark.RIGHT_ANKLE)
        val leftEar = point(PoseLandmark.LEFT_EAR)
        val rightEar = point(PoseLandmark.RIGHT_EAR)
        val leftShoulder = point(PoseLandmark.LEFT_SHOULDER)
        val rightShoulder = point(PoseLandmark.RIGHT_SHOULDER)
        val leftHip = point(PoseLandmark.LEFT_HIP)
        val rightHip = point(PoseLandmark.RIGHT_HIP)
        fun dist(x: Float, y: Float, p: BqPoint) = kotlin.math.hypot(x - p.x, y - p.y)
        for (i in values.indices) {
            val raw = segmentation.buffer.getFloat(i * 4).coerceIn(0f, 1f)
            val x = (i % width).toFloat() / width
            val y = (i / width).toFloat() / height
            val inTrack = subjectBox?.let { x in it.left..it.right && y in it.top..it.bottom } ?: true
            var partWeight = 1f
            when (target.part) {
                BqSubjectPart.WHOLE_PERSON -> Unit
                BqSubjectPart.HEAD, BqSubjectPart.FACE -> head?.let { partWeight = (1f - dist(x,y,it) / 0.16f).coerceIn(0f,1f) } ?: run { partWeight = if (y < 0.3f) 1f else 0f }
                BqSubjectPart.LEFT_EAR -> leftEar?.let { partWeight = (1f - dist(x,y,it) / 0.08f).coerceIn(0f,1f) }
                BqSubjectPart.RIGHT_EAR -> rightEar?.let { partWeight = (1f - dist(x,y,it) / 0.08f).coerceIn(0f,1f) }
                BqSubjectPart.LEFT_HAND -> leftHand?.let { partWeight = (1f - dist(x,y,it) / 0.12f).coerceIn(0f,1f) }
                BqSubjectPart.RIGHT_HAND -> rightHand?.let { partWeight = (1f - dist(x,y,it) / 0.12f).coerceIn(0f,1f) }
                BqSubjectPart.LEFT_FOOT -> leftFoot?.let { partWeight = (1f - dist(x,y,it) / 0.12f).coerceIn(0f,1f) }
                BqSubjectPart.RIGHT_FOOT -> rightFoot?.let { partWeight = (1f - dist(x,y,it) / 0.12f).coerceIn(0f,1f) }
                BqSubjectPart.TORSO -> {
                    val top = listOfNotNull(leftShoulder?.y, rightShoulder?.y).minOrNull() ?: 0.25f
                    val bottom = listOfNotNull(leftHip?.y, rightHip?.y).maxOrNull() ?: 0.75f
                    partWeight = if (y in top..bottom) 1f else 0f
                }
            }
            values[i] = if (inTrack && raw >= target.confidenceThreshold) raw * partWeight else 0f
        }
        return BqSubjectMaskFrame(timeUs,width,height,values,target.subjectId,target.part,target.feather,target.expansion)
    }

    fun close() { segmenter.close(); poseDetector.close() }
}

class BqSubjectSegmenter {
    private val segmenter = Segmentation.getClient(
        SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.STREAM_MODE)
            .build()
    )

    suspend fun segment(bitmap: Bitmap): SegmentationMask =
        suspendCancellableCoroutine { cont ->
            segmenter.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { cont.resume(it) }
                .addOnFailureListener { cont.resumeWithException(it) }
        }

    fun close() = segmenter.close()
}
