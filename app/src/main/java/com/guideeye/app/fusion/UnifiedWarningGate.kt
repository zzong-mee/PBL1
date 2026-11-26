package com.guideeye.app.fusion

/** Experimental warning identity matching; sensor overlap is not proof of identity. */
class UnifiedWarningGate {
    data class Candidate(
        val danger: Boolean, val observed: Long, val message: String,
        val trackId: Long? = null, val classId: Int? = null,
        val sensor: Int? = null, val zones: Set<Int> = emptySet(),
        val direction: ObjectDirection.Direction? = null
    )
    private data class Record(val candidate: Candidate, val announced: Long)
    private val records = mutableListOf<Record>()
    private var lastAt = -1L
    private var lastDanger = false

    fun reset() { records.clear(); lastAt = -1L; lastDanger = false }
    fun resetVision() { records.removeAll { it.candidate.trackId != null } }

    private fun same(a: Candidate, b: Candidate): Boolean {
        if (a.trackId != null && b.trackId != null)
            return a.trackId == b.trackId && a.classId == b.classId
        if (a.sensor == null || a.sensor != b.sensor || a.zones.isEmpty() || b.zones.isEmpty()) return false
        return a.zones.intersect(b.zones).size * 2 >= minOf(a.zones.size,b.zones.size)
    }

    fun offer(candidates: List<Candidate>, now: Long): Candidate? {
        records.removeAll { now-it.announced > 5000 }
        val fresh = candidates.filter { now-it.observed in 0..750 }
            .sortedByDescending { it.danger }
        val highest = fresh.firstOrNull()?.danger ?: return null
        for (c in fresh.filter { it.danger == highest }) {
            val matching = records.filter { same(c,it.candidate) }
            val escalation = c.danger && matching.isNotEmpty() && matching.none { it.candidate.danger }
            if (matching.any { c.observed <= it.candidate.observed }) continue
            if (!escalation && matching.any { now-it.announced < 5000 }) continue
            // Avoid repeatedly interrupting speech for different objects.
            if (!escalation && !(c.danger && !lastDanger) && lastAt >= 0 && now-lastAt < 1200) continue
            records.removeAll { same(c,it.candidate) }
            records.add(Record(c,now))
            lastAt = now
            lastDanger = c.danger
            return c
        }
        return null
    }
}
