package com.bqsqwad.proedit.editor

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.effect.AlphaScale
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.GaussianBlur
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Effects
import androidx.media3.transformer.Transformer
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import java.io.File
import java.util.UUID

@UnstableApi
class BqMediaEngine(private val context: Context) {
    @Volatile private var activeTransformer: Transformer? = null

    fun mediaItem(clip: BqClip): MediaItem =
        MediaItem.Builder()
            .setUri(clip.uri)
            .setImageDurationMs(
                ((clip.endMs ?: (clip.startMs + DEFAULT_IMAGE_DURATION_MS)) - clip.startMs)
                    .coerceAtLeast(1_000L)
            )
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startMs.coerceAtLeast(0L))
                    .apply {
                        clip.endMs?.let { setEndPositionMs(it.coerceAtLeast(clip.startMs + 1L)) }
                    }
                    .build()
            )
            .build()

    fun inspect(uri: Uri, fallbackName: String = "Media"): BqMediaAsset {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            BqMediaAsset(
                UUID.randomUUID().toString(),
                uri,
                fallbackName,
                if (width > 0 || height > 0) BqMediaType.VIDEO else BqMediaType.AUDIO,
                duration,
                width,
                height,
                rotation
            )
        } catch (_: Exception) {
            BqMediaAsset(UUID.randomUUID().toString(), uri, fallbackName, BqMediaType.VIDEO, missing = true)
        } finally {
            retriever.release()
        }
    }

    fun videoEffects(clip: BqClip): List<androidx.media3.common.Effect> = buildVideoEffects(clip)

    fun editedMediaItem(clip: BqClip): EditedMediaItem {
        val mediaItem = mediaItem(clip)
        val videoEffects = buildVideoEffects(clip)
        val audioProcessors = buildAudioProcessors(clip)
        val effects = Effects(audioProcessors, videoEffects)

        return EditedMediaItem.Builder(mediaItem)
            .setEffects(effects)
            .setRemoveAudio(clip.audio.muted)
            .setRemoveVideo(clip.mediaType == BqMediaType.AUDIO)
            .setSpeed(object : SpeedProvider {
                override fun getSpeed(timeUs: Long): Float = clip.speed.coerceIn(0.25f, 4f)
                override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
            })
            .build()
    }

    private fun buildVideoEffects(clip: BqClip): List<androidx.media3.common.Effect> {
        val effects = mutableListOf<androidx.media3.common.Effect>()

        val scaleX = (clip.scale * clip.transform.scaleX).coerceIn(0.1f, 8f)
        val scaleY = (clip.scale * clip.transform.scaleY).coerceIn(0.1f, 8f)
        val rotation = (clip.rotationDegrees + clip.transform.rotation) % 360f
        val translateX = (clip.x + clip.transform.x).coerceIn(-2f, 2f)
        val translateY = (clip.y + clip.transform.y).coerceIn(-2f, 2f)

        if (scaleX != 1f || scaleY != 1f || rotation != 0f) {
            effects += ScaleAndRotateTransformation.Builder()
                .setScale(scaleX, scaleY)
                .setRotationDegrees(rotation)
                .build()
        }
        if (translateX != 0f || translateY != 0f) {
            effects += MatrixTransformation { _ ->
                android.graphics.Matrix().apply {
                    postTranslate(translateX, translateY)
                }
            }
        }
        if (clip.transform.opacity < 0.999f) {
            effects += AlphaScale(clip.transform.opacity.coerceIn(0f, 1f))
        }

        clip.effects.filter { it.enabled }.forEach { effect ->
            val amount = effect.parameters["amount"]?.value
            when (effect.kind) {
                BqEffectKind.BRIGHTNESS -> effects += Brightness((amount ?: 0.15f).coerceIn(-1f, 1f))
                BqEffectKind.CONTRAST -> effects += Contrast((amount ?: 0.20f).coerceIn(-1f, 1f))
                BqEffectKind.SATURATION -> effects += HslAdjustment.Builder()
                    .adjustSaturation((amount ?: 20f).coerceIn(-100f, 100f))
                    .build()
                BqEffectKind.EXPOSURE -> effects += Brightness((amount ?: 0.10f).coerceIn(-1f, 1f))
                BqEffectKind.TEMPERATURE -> effects += HslAdjustment.Builder()
                    .adjustHue((amount ?: 8f).coerceIn(-180f, 180f))
                    .build()
                BqEffectKind.TINT -> effects += HslAdjustment.Builder()
                    .adjustHue((amount ?: 12f).coerceIn(-180f, 180f))
                    .build()
                BqEffectKind.BLUR -> effects += GaussianBlur((amount ?: 4f).coerceIn(0.1f, 40f))
                BqEffectKind.GLOW -> effects += GaussianBlur((amount ?: 2f).coerceIn(0.1f, 20f))
                else -> Unit
            }
        }
        return effects
    }

    private fun buildAudioProcessors(clip: BqClip): List<androidx.media3.common.audio.AudioProcessor> {
        val gain = clip.volume.coerceIn(0f, 2f) * clip.audio.volume.coerceIn(0f, 2f)
        if (clip.audio.muted || gain >= 0.999f && clip.audio.fadeInMs <= 0L && clip.audio.fadeOutMs <= 0L) return emptyList()

        val provider = DefaultGainProvider.Builder(gain.coerceIn(0f, 1f))
            .apply {
                if (clip.audio.fadeInMs > 0L) {
                    addFadeAt(0L, clip.audio.fadeInMs * 1_000L, DefaultGainProvider.FADE_IN_LINEAR)
                }
                clip.audio.fadeOutMs.takeIf { it > 0L }?.let { fade ->
                    val end = (clip.endMs ?: Long.MAX_VALUE / 1_000L)
                    val start = (end - fade).coerceAtLeast(0L)
                    addFadeAt(start * 1_000L, fade * 1_000L, DefaultGainProvider.FADE_OUT_LINEAR)
                }
            }
            .build()

        return listOf(GainProcessor(provider))
    }

    fun exportProject(
        project: BqProject,
        config: BqExportConfig = BqExportConfig(),
        onStarted: (File) -> Unit = {},
        onProgress: (Float) -> Unit = {},
        onCompleted: (File) -> Unit,
        onError: (ExportException) -> Unit
    ) {
        val problems = BqProjectValidator.validate(project)
        if (problems.isNotEmpty()) {
            return onError(
                ExportException.createForUnexpected(
                    IllegalArgumentException(problems.joinToString("; "))
                )
            )
        }

        val clips = project.clips.filter {
            it.mediaType == BqMediaType.VIDEO ||
                it.mediaType == BqMediaType.IMAGE ||
                it.mediaType == BqMediaType.AUDIO
        }
        if (clips.isEmpty()) {
            return onError(
                ExportException.createForUnexpected(
                    IllegalArgumentException("Project has no exportable media")
                )
            )
        }

        val output = File(
            context.cacheDir,
            "${config.fileNamePrefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.mp4"
        )

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(config.videoMime)
            .setAudioMimeType(config.audioMime)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    activeTransformer = null
                    onProgress(1f)
                    onCompleted(output)
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    activeTransformer = null
                    output.delete()
                    onError(exportException)
                }
            })
            .build()

        activeTransformer = transformer
        onStarted(output)

        runCatching {
            val sequence = EditedMediaItemSequence.withAudioAndVideoFrom(clips.map(::editedMediaItem))
            transformer.start(Composition.Builder(sequence).build(), output.absolutePath)
        }.onFailure {
            activeTransformer = null
            output.delete()
            onError(
                ExportException.createForUnexpected(
                    it as? Exception ?: RuntimeException(it.message)
                )
            )
        }
    }

    fun cancelExport() {
        activeTransformer?.cancel()
        activeTransformer = null
    }

    companion object {
        private const val DEFAULT_IMAGE_DURATION_MS = 5_000L
    }
}
