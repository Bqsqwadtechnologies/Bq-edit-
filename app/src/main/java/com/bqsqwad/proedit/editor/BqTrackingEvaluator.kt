package com.bqsqwad.proedit.editor

/** Deterministic timeline evaluator for tracked anchors and manual fallback keyframes. */
object BqTrackingEvaluator {
    fun sample(binding: BqTrackingBinding, timeMs: Long): BqPoint? {
        if (binding.manualFallback.isEmpty()) return binding.anchor
        val keys = binding.manualFallback.sortedBy { it.timeMs }
        val before = keys.lastOrNull { it.timeMs <= timeMs } ?: return keys.first().value
        val after = keys.firstOrNull { it.timeMs >= timeMs } ?: return before.value
        if (before.timeMs == after.timeMs) return before.value
        val t = ((timeMs - before.timeMs).toFloat() / (after.timeMs - before.timeMs)).coerceIn(0f, 1f)
        return BqPoint(
            before.value.x + (after.value.x - before.value.x) * t,
            before.value.y + (after.value.y - before.value.y) * t
        )
    }

    fun accepted(binding: BqTrackingBinding, confidence: Float): Boolean =
        confidence.isFinite() && confidence >= binding.confidenceThreshold.coerceIn(0f, 1f)

    fun active(fx: BqFxLayer, timeMs: Long): Boolean =
        fx.enabled && timeMs >= fx.startMs && (fx.endMs == null || timeMs < fx.endMs)
}
