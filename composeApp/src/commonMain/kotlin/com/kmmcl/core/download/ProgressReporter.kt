package com.kmmcl.core.download

/**
 * Lightweight progress reporter supporting multi-phase weighted progress.
 *
 * Reference: HMCL/PCL multi-step progress with per-phase weighting.
 *
 * Usage:
 *   val reporter = ProgressReporter(onProgress = { text, fraction -> ... })
 *   reporter.startPhase("下载客户端", weight = 0.25f)     // phase 0: 0.00-0.25
 *   reporter.reportStep(done, total, "下载中...")          // step within phase
 *   reporter.endPhase()                                   // jump to max of phase
 *   reporter.startPhase("下载依赖库", weight = 0.50f)     // phase 1: 0.25-0.75
 */
class ProgressReporter(
    private val onProgress: (String, Float) -> Unit,
) {
    private var phaseIndex = 0
    private var phaseStart = 0f
    private var phaseEnd = 0f

    fun startPhase(label: String, weight: Float) {
        phaseStart = if (phaseIndex == 0) 0f else phaseEnd
        phaseEnd = (phaseStart + weight).coerceAtMost(1f)
        phaseIndex++
    }

    fun reportStep(done: Int, total: Int, label: String) {
        val stepFraction = if (total > 0) done.toFloat() / total else 0f
        val globalFraction = phaseStart + stepFraction * (phaseEnd - phaseStart)
        onProgress(label, globalFraction.coerceIn(0f, 1f))
    }

    fun reportFraction(fraction: Float, label: String) {
        val globalFraction = phaseStart + fraction * (phaseEnd - phaseStart)
        onProgress(label, globalFraction.coerceIn(0f, 1f))
    }

    fun endPhase() {
        onProgress("完成", phaseEnd)
    }
}
