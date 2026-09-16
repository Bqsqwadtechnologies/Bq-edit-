package com.bqsqwad.proedit.editor

import android.graphics.PointF
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** On-device vision primitives for the BQ Superpower FX pipeline. */
class BqVisionTracker {
    private val poseDetector = PoseDetection.getClient(
        PoseDetectorOptions.Builder().setDetectorMode(PoseDetectorOptions.STREAM_MODE).build()
    )
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder().setDetectorMode(ObjectDetectorOptions.STREAM_MODE).enableMultipleObjects().build()
    )

    suspend fun detectPose(image: InputImage, frameTimeUs: Long): BqPersonPose? = suspendCancellableCoroutine { cont ->
        poseDetector.process(image)
            .addOnSuccessListener { pose -> cont.resume(pose.toBqPose(frameTimeUs)) }
            .addOnFailureListener { error -> cont.resumeWithException(error) }
        cont.invokeOnCancellation { poseDetector.close() }
    }

    suspend fun detectObjects(image: InputImage, frameTimeUs: Long): List<BqTrackedObject> = suspendCancellableCoroutine { cont ->
        objectDetector.process(image)
            .addOnSuccessListener { objects -> cont.resume(objects.map { it.toBqObject(frameTimeUs) }) }
            .addOnFailureListener { error -> cont.resumeWithException(error) }
        cont.invokeOnCancellation { objectDetector.close() }
    }

    fun close() {
        poseDetector.close()
        objectDetector.close()
    }
}

private fun Pose.toBqPose(frameTimeUs: Long): BqPersonPose? {
    val points = landmarks.associate { landmark ->
        landmark.landmarkType.toString() to BqPoint(landmark.position.x, landmark.position.y)
    }
    if (points.isEmpty()) return null
    val xs = points.values.map { it.x }
    val ys = points.values.map { it.y }
    val box = BqTrackedBox(xs.min(), ys.min(), xs.max(), ys.max())
    return BqPersonPose(frameTimeUs, box, points, 1f)
}

private fun DetectedObject.toBqObject(frameTimeUs: Long): BqTrackedObject {
    val r = boundingBox
    val label = labels.firstOrNull()?.text ?: "object"
    return BqTrackedObject(
        trackingId = trackingId ?: -1,
        label = label,
        frameTimeUs = frameTimeUs,
        box = BqTrackedBox(r.left.toFloat(), r.top.toFloat(), r.right.toFloat(), r.bottom.toFloat()),
        confidence = labels.maxOfOrNull { it.confidence } ?: 0f
    )
}
