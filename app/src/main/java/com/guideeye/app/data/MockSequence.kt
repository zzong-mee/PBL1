package com.guideeye.app.data

/** Deterministic samples for reproducible UI checks, unrelated to real sensor readings. */
class MockSequence {
    private val samples = listOf(
        DistanceReading(2.0f, 1.5f), DistanceReading(1.2f, 2.3f),
        DistanceReading(0.5f, 1.0f), DistanceReading(1.8f, 0.7f)
    )
    private var index = 0
    fun next(): DistanceReading = samples[index].also { index = (index + 1) % samples.size }
}
