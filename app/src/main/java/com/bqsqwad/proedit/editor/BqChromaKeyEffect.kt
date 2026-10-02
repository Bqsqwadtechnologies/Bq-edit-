@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.bqsqwad.proedit.editor

import android.content.Context
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import android.opengl.GLES20
import java.io.IOException

@UnstableApi
class BqChromaKeyEffect(
    private val keyColorArgb: Long,
    private val similarity: Float,
    private val softness: Float,
    private val spillSuppression: Float
) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): androidx.media3.effect.GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("BQ chroma key currently expects SDR input")
        return Program(context, keyColorArgb, similarity, softness, spillSuppression)
    }

    private class Program(
        context: Context,
        keyColorArgb: Long,
        similarity: Float,
        softness: Float,
        spillSuppression: Float
    ) : BaseGlShaderProgram(false, 1) {
        private val program = try {
            GlProgram(context, com.bqsqwad.proedit.R.raw.bq_vertex_es2, com.bqsqwad.proedit.R.raw.bq_chroma_es2)
        } catch (e: IOException) {
            throw VideoFrameProcessingException(e)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        init {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            val r = ((keyColorArgb shr 16) and 0xFF) / 255f
            val g = ((keyColorArgb shr 8) and 0xFF) / 255f
            val b = (keyColorArgb and 0xFF) / 255f
            program.setFloatsUniform("uKeyColor", floatArrayOf(r, g, b))
            program.setFloatUniform("uSimilarity", similarity.coerceIn(0f, 1f))
            program.setFloatUniform("uSoftness", softness.coerceIn(0.001f, 1f))
            program.setFloatUniform("uSpill", spillSuppression.coerceIn(0f, 1f))
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            try {
                super.release()
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }
    }
}
