package com.bqsqwad.proedit.editor

import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlProgram
import androidx.media3.effect.GlShaderProgram

@UnstableApi
class BqSubjectMaskEffect(private val mask: BqSubjectMaskFrame) : GlEffect {
    override fun toGlShaderProgram(context: android.content.Context, useHdr: Boolean): GlShaderProgram =
        Program(context, useHdr, mask)
    private class Program(context: android.content.Context, useHdr: Boolean, private val mask: BqSubjectMaskFrame) : BaseGlShaderProgram(useHdr, 1) {
        private val program = GlProgram(context, com.bqsqwad.proedit.R.raw.bq_subject_mask_vertex, com.bqsqwad.proedit.R.raw.bq_subject_mask_fragment)
        override fun configure(inputWidth: Int, inputHeight: Int) = android.util.Size(inputWidth, inputHeight)
        override fun drawFrame(inputTexId: Int, presentationTimeUs: Long, outputTexId: Int, outputWidth: Int, outputHeight: Int) {
            try {
                program.use()
                program.setSamplerTexIdUniform("uTex", inputTexId)
                program.setFloatUniform("uMaskAverage", mask.confidence.average().toFloat())
                program.setFloatUniform("uThreshold", mask.feather.coerceIn(0f,1f))
                GLES20.glViewport(0,0,outputWidth,outputHeight)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4)
                androidx.media3.effect.util.GlUtil.checkGlError()
            } catch(e: Exception) { throw VideoFrameProcessingException(e) }
        }
    }
}