package com.guideeye.app.fusion

/**
 * Rate-limits independently confirmed ToF approach warnings.
 * Does not infer direction, object identity or pedestrian path without calibration.
 */
class IndependentTofWarningGate(
    private val cooldownMs: Long = 5000,
    private val maxGapMs: Long = 800
) {
    private var lastAnnouncedAtMs: Long? = null
    private var lastObservedAtMs: Long? = null
    fun reset() { lastAnnouncedAtMs = null; lastObservedAtMs = null }

    fun offer(risks: List<IndependentTofRiskEngine.Risk>, nowMs: Long): IndependentTofRiskEngine.Risk? {
        val candidate = risks.firstOrNull {
            nowMs >= it.observedAtMs && nowMs - it.observedAtMs <= maxGapMs
        } ?: run { lastObservedAtMs = null; return null }
        val previous = lastObservedAtMs
        if (previous != null && candidate.observedAtMs <= previous) return null
        lastObservedAtMs = candidate.observedAtMs
        if (lastAnnouncedAtMs != null && nowMs - lastAnnouncedAtMs!! < cooldownMs) return null
        lastAnnouncedAtMs = nowMs
        return candidate
    }
}
