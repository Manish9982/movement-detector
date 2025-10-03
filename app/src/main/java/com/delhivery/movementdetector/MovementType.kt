package com.delhivery.movementdetector

enum class MovementType {
    WALKING_STRAIGHT,
    CLIMBING_STAIRS,
    DESCENDING_STAIRS,
    IN_ELEVATOR,
    STATIONARY,
    UNKNOWN
}

data class MovementData(
    val timestamp: Long,
    val movementType: MovementType,
    val confidence: Float,
    val accelerometerData: FloatArray,
    val gyroscopeData: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MovementData

        if (timestamp != other.timestamp) return false
        if (movementType != other.movementType) return false
        if (confidence != other.confidence) return false
        if (!accelerometerData.contentEquals(other.accelerometerData)) return false
        if (!gyroscopeData.contentEquals(other.gyroscopeData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + movementType.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + accelerometerData.contentHashCode()
        result = 31 * result + gyroscopeData.contentHashCode()
        return result
    }
}
