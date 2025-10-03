package com.delhivery.movementdetector

import kotlin.math.*

class MovementClassifier {
    private val accelerometerBuffer = mutableListOf<FloatArray>()
    private val gyroscopeBuffer = mutableListOf<FloatArray>()
    private val bufferSize = 50 // Store last 50 readings (~2.5 seconds at 20Hz)
    
    fun addSensorData(accelerometer: FloatArray, gyroscope: FloatArray) {
        accelerometerBuffer.add(accelerometer.clone())
        gyroscopeBuffer.add(gyroscope.clone())
        
        if (accelerometerBuffer.size > bufferSize) {
            accelerometerBuffer.removeAt(0)
        }
        if (gyroscopeBuffer.size > bufferSize) {
            gyroscopeBuffer.removeAt(0)
        }
    }
    
    fun classifyMovement(): Pair<MovementType, Float> {
        if (accelerometerBuffer.size < 20) {
            return Pair(MovementType.UNKNOWN, 0.0f)
        }
        
        val features = extractFeatures()
        return classifyFromFeatures(features)
    }
    
    private fun extractFeatures(): MovementFeatures {
        val accMagnitudes = accelerometerBuffer.map { 
            sqrt((it[0] * it[0] + it[1] * it[1] + it[2] * it[2]).toDouble()) 
        }
        val gyroMagnitudes = gyroscopeBuffer.map { 
            sqrt((it[0] * it[0] + it[1] * it[1] + it[2] * it[2]).toDouble()) 
        }
        
        // Calculate statistical features
        val accMean = accMagnitudes.average()
        val accStd = calculateStandardDeviation(accMagnitudes, accMean)
        val accVariance = accStd * accStd
        
        val gyroMean = gyroMagnitudes.average()
        val gyroStd = calculateStandardDeviation(gyroMagnitudes, gyroMean)
        
        // Calculate vertical acceleration features (assuming Y-axis is vertical)
        val verticalAcc = accelerometerBuffer.map { it[1].toDouble() }
        val verticalAccMean = verticalAcc.average()
        val verticalAccStd = calculateStandardDeviation(verticalAcc, verticalAccMean)
        
        // Calculate step frequency (zero crossings in acceleration)
        val stepFrequency = calculateStepFrequency(accMagnitudes)
        
        // Calculate tilt angle (for elevator detection)
        val tiltAngle = calculateAverageTiltAngle()
        
        return MovementFeatures(
            accMean = accMean.toFloat(),
            accStd = accStd.toFloat(),
            accVariance = accVariance.toFloat(),
            gyroMean = gyroMean.toFloat(),
            gyroStd = gyroStd.toFloat(),
            verticalAccMean = verticalAccMean.toFloat(),
            verticalAccStd = verticalAccStd.toFloat(),
            stepFrequency = stepFrequency,
            tiltAngle = tiltAngle
        )
    }
    
    private fun classifyFromFeatures(features: MovementFeatures): Pair<MovementType, Float> {
        var maxConfidence = 0.0f
        var predictedType = MovementType.UNKNOWN
        
        // Stationary detection
        if (features.accStd < 0.5f && features.gyroStd < 0.2f) {
            return Pair(MovementType.STATIONARY, 0.9f)
        }
        
        // Elevator detection - low gyroscope activity, minimal step frequency, smooth vertical movement
        val elevatorConfidence = calculateElevatorConfidence(features)
        if (elevatorConfidence > maxConfidence) {
            maxConfidence = elevatorConfidence
            predictedType = MovementType.IN_ELEVATOR
        }
        
        // Stairs detection - higher vertical acceleration variance, moderate step frequency
        val stairsConfidence = calculateStairsConfidence(features)
        if (stairsConfidence > maxConfidence) {
            maxConfidence = stairsConfidence
            predictedType = if (features.verticalAccMean > 9.8f) {
                MovementType.CLIMBING_STAIRS
            } else {
                MovementType.DESCENDING_STAIRS
            }
        }
        
        // Walking detection - regular step pattern, moderate acceleration variance
        val walkingConfidence = calculateWalkingConfidence(features)
        if (walkingConfidence > maxConfidence) {
            maxConfidence = walkingConfidence
            predictedType = MovementType.WALKING_STRAIGHT
        }
        
        return Pair(predictedType, maxConfidence)
    }
    
    private fun calculateElevatorConfidence(features: MovementFeatures): Float {
        var confidence = 0.0f
        
        // Low gyroscope activity (elevator moves smoothly)
        if (features.gyroStd < 0.3f) confidence += 0.3f
        
        // Low step frequency (no walking in elevator)
        if (features.stepFrequency < 0.5f) confidence += 0.3f
        
        // Smooth acceleration (elevator has consistent movement)
        if (features.accStd < 1.0f && features.accStd > 0.2f) confidence += 0.2f
        
        // Vertical movement pattern
        if (abs(features.verticalAccMean - 9.8f) < 1.0f) confidence += 0.2f
        
        return confidence
    }
    
    private fun calculateStairsConfidence(features: MovementFeatures): Float {
        var confidence = 0.0f
        
        // Higher vertical acceleration variance (up/down motion)
        if (features.verticalAccStd > 1.5f) confidence += 0.3f
        
        // Moderate step frequency
        if (features.stepFrequency in 1.0f..2.5f) confidence += 0.3f
        
        // Higher overall acceleration variance
        if (features.accVariance > 2.0f) confidence += 0.2f
        
        // Some gyroscope activity (body rotation while climbing)
        if (features.gyroStd > 0.2f && features.gyroStd < 1.0f) confidence += 0.2f
        
        return confidence
    }
    
    private fun calculateWalkingConfidence(features: MovementFeatures): Float {
        var confidence = 0.0f
        
        // Regular step frequency
        if (features.stepFrequency in 1.5f..3.0f) confidence += 0.4f
        
        // Moderate acceleration variance
        if (features.accVariance in 1.0f..4.0f) confidence += 0.3f
        
        // Some gyroscope activity (natural body movement)
        if (features.gyroStd > 0.1f && features.gyroStd < 0.8f) confidence += 0.2f
        
        // Vertical acceleration close to gravity
        if (abs(features.verticalAccMean - 9.8f) < 2.0f) confidence += 0.1f
        
        return confidence
    }
    
    private fun calculateStandardDeviation(values: List<Double>, mean: Double): Double {
        val variance = values.map { (it - mean) * (it - mean) }.average()
        return sqrt(variance)
    }
    
    private fun calculateStepFrequency(magnitudes: List<Double>): Float {
        if (magnitudes.size < 10) return 0.0f
        
        val mean = magnitudes.average()
        var crossings = 0
        
        for (i in 1 until magnitudes.size) {
            if ((magnitudes[i-1] - mean) * (magnitudes[i] - mean) < 0) {
                crossings++
            }
        }
        
        // Convert to steps per second (assuming 20Hz sampling rate)
        return (crossings / 2.0f) / (magnitudes.size / 20.0f)
    }
    
    private fun calculateAverageTiltAngle(): Float {
        if (accelerometerBuffer.isEmpty()) return 0.0f
        
        val angles = accelerometerBuffer.map { acc ->
            val magnitude = sqrt((acc[0] * acc[0] + acc[1] * acc[1] + acc[2] * acc[2]).toDouble())
            if (magnitude > 0) {
                acos(acc[1] / magnitude) * 180.0 / PI
            } else {
                0.0
            }
        }
        
        return angles.average().toFloat()
    }
}

data class MovementFeatures(
    val accMean: Float,
    val accStd: Float,
    val accVariance: Float,
    val gyroMean: Float,
    val gyroStd: Float,
    val verticalAccMean: Float,
    val verticalAccStd: Float,
    val stepFrequency: Float,
    val tiltAngle: Float
)
