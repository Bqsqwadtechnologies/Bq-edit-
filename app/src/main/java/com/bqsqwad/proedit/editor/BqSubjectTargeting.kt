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
