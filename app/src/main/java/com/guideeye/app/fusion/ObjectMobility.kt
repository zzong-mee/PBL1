package com.guideeye.app.fusion

/** Semantic mobility potential, not observed motion or proof of safety.
 * IDs follow the existing YOLOX COCO 80-class order.
 * Vehicles include rider/driver-operated vehicles by design.
 */
object ObjectMobility {
    enum class Type { TYPE_1, TYPE_2, UNKNOWN }

    fun classify(classId: Int): Type = when (classId) {
        in 0..8, in 14..23 -> Type.TYPE_1
        in 9..13, in 24..79 -> Type.TYPE_2
        else -> Type.UNKNOWN
    }
}
