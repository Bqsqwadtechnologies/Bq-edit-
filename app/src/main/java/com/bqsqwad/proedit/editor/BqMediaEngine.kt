package com.bqsqwad.proedit.editor

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import androidx.media3.effect.ScaleAndRotateTransformation
import java.io.File
import java.util.UUID

@UnstableApi
class BqMediaEngine(private val context: Context) {
    fun mediaItem(uri: Uri): MediaItem = MediaItem.fromUri(uri)

    fun editedMediaItem(clip: BqClip): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs.coerceAtLeast(0L))
                    .apply { clip.endMs?.let { setEndPositionMs(it.coerceAtLeast(clip.startMs + 1L)) } }
                    .build()
            )
            .build()

        val builder = EditedMediaItem.Builder(mediaItem)
            .setSpeed(object : SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = clip.speed.coerceIn(0.25f, 4f)
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
            })

        if (clip.rotationDegrees != 0f || clip.scale != 1f) {
            builder.setEffects(
                Effects(
                    emptyList(),
                    listOf(
                        ScaleAndRotateTransformation.Builder()
                            .setScale(clip.scale.coerceIn(0.1f, 8f), clip.scale.coerceIn(0.1f, 8f))
                            .setRotationDegrees(clip.rotationDegrees)
                            .build()
                    )
                )
            )
        }
        return builder.build()
    }

    fun export(clip: BqClip, onStarted: (File) -> Unit, onCompleted: (File) -> Unit, onError: (ExportException) -> Unit) {
        exportItems(listOf(clip), onStarted, onCompleted, onError)
    }

    fun exportProject(project: BqProject, onStarted: (File) -> Unit, onCompleted: (File) -> Unit, onError: (ExportException) -> Unit) {
        exportItems(project.clips, onStarted, onCompleted, onError)
    }

    private fun exportItems(
        clips: List<BqClip>,
        onStarted: (File) -> Unit,
        onCompleted: (File) -> Unit,
        onError: (ExportException) -> Unit
    ) {
        if (clips.isEmpty()) return

        val output = File(context.cacheDir, "bq_export_${UUID.randomUUID()}.mp4")
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    onCompleted(output)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    onError(exportException)
                }
            })
            .build()

        onStarted(output)

        val items = clips.map(::editedMediaItem)
        val videoSequence = EditedMediaItemSequence.withAudioAndVideoFrom(items)
        val composition = Composition.Builder(videoSequence).build()
        transformer.start(composition, output.absolutePath)
    }
}
