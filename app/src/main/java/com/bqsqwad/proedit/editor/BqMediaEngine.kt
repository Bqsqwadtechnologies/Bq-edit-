package com.bqsqwad.proedit.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.UUID

@UnstableApi
class BqMediaEngine(private val context: Context) {
    @Volatile private var activeTransformer: Transformer? = null

    fun mediaItem(uri: Uri): MediaItem = MediaItem.fromUri(uri)

    fun inspect(uri: Uri, fallbackName: String = "Media"): BqMediaAsset {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            BqMediaAsset(UUID.randomUUID().toString(), uri, fallbackName, if (width > 0 || height > 0) BqMediaType.VIDEO else BqMediaType.AUDIO, duration, width, height, rotation)
        } catch (_: Exception) {
            BqMediaAsset(UUID.randomUUID().toString(), uri, fallbackName, BqMediaType.VIDEO, missing = true)
        } finally { retriever.release() }
    }

    fun editedMediaItem(clip: BqClip): EditedMediaItem {
        val mediaItem = MediaItem.Builder().setUri(clip.uri).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(clip.startMs.coerceAtLeast(0L))
                .apply { clip.endMs?.let { setEndPositionMs(it.coerceAtLeast(clip.startMs + 1L)) } }
                .build()
        ).build()
        val builder = EditedMediaItem.Builder(mediaItem)
            .setSpeed(object : SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = clip.speed.coerceIn(0.25f, 4f)
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
            })
        if (clip.rotationDegrees != 0f || clip.scale != 1f) {
            builder.setEffects(Effects(emptyList(), listOf(
                ScaleAndRotateTransformation.Builder()
                    .setScale(clip.scale.coerceIn(0.1f, 8f), clip.scale.coerceIn(0.1f, 8f))
                    .setRotationDegrees(clip.rotationDegrees)
                    .build()
            )))
        }
        return builder.build()
    }

    fun exportProject(project: BqProject, config: BqExportConfig = BqExportConfig(), onStarted: (File) -> Unit = {}, onProgress: (Float) -> Unit = {}, onCompleted: (File) -> Unit, onError: (ExportException) -> Unit) {
        val problems = BqProjectValidator.validate(project)
        if (problems.isNotEmpty()) return onError(ExportException.createForUnexpected(IllegalArgumentException(problems.joinToString("; "))))
        val clips = project.clips.filter { it.mediaType == BqMediaType.VIDEO || it.mediaType == BqMediaType.IMAGE }
        if (clips.isEmpty()) return onError(ExportException.createForUnexpected(IllegalArgumentException("Project has no exportable video clips")))
        val output = File(context.cacheDir, "${config.fileNamePrefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.mp4")
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) { activeTransformer = null; onProgress(1f); onCompleted(output) }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) { activeTransformer = null; output.delete(); onError(exportException) }
            }).build()
        activeTransformer = transformer
        onStarted(output)
        val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(clips.map(::editedMediaItem))
        transformer.start(Composition.Builder(sequence).build(), output.absolutePath)
    }

    fun cancelExport() { activeTransformer?.cancel(); activeTransformer = null }
}
