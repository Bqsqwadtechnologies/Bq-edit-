package com.bqsqwad.proedit.editor

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.Transformer
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
                    .setStartPositionMs(clip.startMs)
                    .apply { clip.endMs?.let { setEndPositionMs(it) } }
                    .build()
            )
            .build()
        val builder = EditedMediaItem.Builder(mediaItem)
        if (clip.rotationDegrees != 0f || clip.scale != 1f) {
            builder.setEffects(
                androidx.media3.transformer.Effects(
                    emptyList(),
                    listOf(
                        ScaleAndRotateTransformation.Builder()
                            .setScale(clip.scale, clip.scale)
                            .setRotationDegrees(clip.rotationDegrees)
                            .build()
                    )
                )
            )
        }
        return builder.build()
    }

    fun export(clip: BqClip, onStarted: (File) -> Unit, onCompleted: (File) -> Unit, onError: (ExportException) -> Unit) {
        val output = File(context.cacheDir, "bq_export_${UUID.randomUUID()}.mp4")
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult) {
                    onCompleted(output)
                }
                override fun onError(composition: androidx.media3.transformer.Composition, exportResult: androidx.media3.transformer.ExportResult, exportException: ExportException) {
                    onError(exportException)
                }
            })
            .build()
        onStarted(output)
        transformer.start(editedMediaItem(clip), output.absolutePath)
    }
}
