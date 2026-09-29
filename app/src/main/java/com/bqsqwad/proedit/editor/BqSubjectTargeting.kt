package com.bqsqwad.proedit.editor

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.SegmentationMask
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
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
