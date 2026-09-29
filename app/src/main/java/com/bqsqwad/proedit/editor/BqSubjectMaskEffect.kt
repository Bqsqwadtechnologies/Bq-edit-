package com.bqsqwad.proedit.editor

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import java.io.IOException
import java.nio.ByteBuffer

/** Media3 GPU compositor stage for a tracked subject. */
@UnstableApi
class BqSubjectMaskEffect(private val mask: BqSubjectMaskFrame) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): androidx.media3.effect.GlShaderProgram {
        if (useHdr) throw VideoFrameProcessingException("BQ subject mask currently expects SDR input")
        return Program(context, mask)
    }

    private class Program(context: Context, private val mask: BqSubjectMaskFrame) : BaseGlShaderProgram(false, 1) {
        private val program = try {
            GlProgram(context, com.bqsqwad.proedit.R.raw.bq_vertex_es2, com.bqsqwad.proedit.R.raw.bq_subject_mask_es2)
        } catch (e: IOException) {
            throw VideoFrameProcessingException(e)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }

        private var maskTextureId = 0

        init {
            program.setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
            val identity = GlUtil.create4x4IdentityMatrix()
            program.setFloatsUniform("uTransformationMatrix", identity)
            program.setFloatsUniform("uTexTransformationMatrix", identity)
            maskTextureId = createMaskTexture(mask)
        }

        override fun configure(inputWidth: Int, inputHeight: Int): Size = Size(inputWidth, inputHeight)

        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
                program.setSamplerTexIdUniform("uMaskSampler", maskTextureId, 1)
                program.setFloatUniform("uFeather", mask.feather.coerceIn(0f, 1f))
                program.bindAttributesAndUniforms()
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e, presentationTimeUs)
            }
        }

        override fun release() {
            try {
                super.release()
                if (maskTextureId != 0) {
                    GLES20.glDeleteTextures(1, intArrayOf(maskTextureId), 0)
                    maskTextureId = 0
                }
                program.delete()
            } catch (e: GlUtil.GlException) {
                throw VideoFrameProcessingException(e)
            }
        }

        private fun createMaskTexture(mask: BqSubjectMaskFrame): Int {
            val id = IntArray(1)
            GLES20.glGenTextures(1, id, 0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, id[0])
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
            val bytes = ByteArray(mask.confidence.size) { i -> (mask.confidence[i].coerceIn(0f, 1f) * 255f).toInt().toByte() }
            GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_LUMINANCE,
                mask.width, mask.height, 0, GLES20.GL_LUMINANCE,
                GLES20.GL_UNSIGNED_BYTE, ByteBuffer.wrap(bytes)
            )
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
            GlUtil.checkGlError()
            return id[0]
        }
    }
}
