package com.delhivery.movementdetector

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class MovementDataLogger {
    private val movementHistory = ConcurrentLinkedQueue<MovementData>()
    private val maxHistorySize = 1000
    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    companion object {
        private const val TAG = "MovementDataLogger"
        private const val LOG_FILE_NAME = "movement_log.csv"
    }
    
    fun initialize(context: Context) {
        try {
            logFile = File(context.filesDir, LOG_FILE_NAME)
            
            // Create CSV header if file doesn't exist
            if (!logFile!!.exists()) {
                writeToFile("Timestamp,DateTime,MovementType,Confidence,AccX,AccY,AccZ,GyroX,GyroY,GyroZ\n")
            }
            
            Log.d(TAG, "Movement logger initialized. Log file: ${logFile!!.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize movement logger", e)
        }
    }
    
    fun logMovement(movementData: MovementData) {
        // Add to in-memory history
        movementHistory.offer(movementData)
        
        // Maintain history size
        while (movementHistory.size > maxHistorySize) {
            movementHistory.poll()
        }
        
        // Write to file
        writeMovementToFile(movementData)
        
        Log.d(TAG, "Logged movement: ${movementData.movementType} (${movementData.confidence})")
    }
    
    private fun writeMovementToFile(movementData: MovementData) {
        try {
            val dateTime = dateFormat.format(Date(movementData.timestamp))
            val csvLine = "${movementData.timestamp}," +
                    "$dateTime," +
                    "${movementData.movementType}," +
                    "${movementData.confidence}," +
                    "${movementData.accelerometerData[0]}," +
                    "${movementData.accelerometerData[1]}," +
                    "${movementData.accelerometerData[2]}," +
                    "${movementData.gyroscopeData[0]}," +
                    "${movementData.gyroscopeData[1]}," +
                    "${movementData.gyroscopeData[2]}\n"
            
            writeToFile(csvLine)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write movement to file", e)
        }
    }
    
    private fun writeToFile(content: String) {
        try {
            logFile?.let { file ->
                FileWriter(file, true).use { writer ->
                    writer.write(content)
                    writer.flush()
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to write to log file", e)
        }
    }
    
    fun getRecentMovements(count: Int): List<MovementData> {
        val result = mutableListOf<MovementData>()
        val iterator = movementHistory.iterator()
        
        // Get the last 'count' items
        val totalSize = movementHistory.size
        var skipCount = maxOf(0, totalSize - count)
        
        while (iterator.hasNext()) {
            val movement = iterator.next()
            if (skipCount > 0) {
                skipCount--
            } else {
                result.add(movement)
            }
        }
        
        return result
    }
    
    fun getAllMovements(): List<MovementData> {
        return movementHistory.toList()
    }
    
    fun clearHistory() {
        movementHistory.clear()
        
        // Clear the log file
        try {
            logFile?.let { file ->
                FileWriter(file, false).use { writer ->
                    writer.write("Timestamp,DateTime,MovementType,Confidence,AccX,AccY,AccZ,GyroX,GyroY,GyroZ\n")
                    writer.flush()
                }
            }
            Log.d(TAG, "Movement history cleared")
        } catch (e: IOException) {
            Log.e(TAG, "Failed to clear log file", e)
        }
    }
    
    fun getLogFilePath(): String? {
        return logFile?.absolutePath
    }
    
    fun getMovementStats(): MovementStats {
        val movements = movementHistory.toList()
        val totalCount = movements.size
        
        if (totalCount == 0) {
            return MovementStats(0, emptyMap(), 0L, 0L)
        }
        
        val typeCounts = movements.groupingBy { it.movementType }.eachCount()
        val startTime = movements.minOfOrNull { it.timestamp } ?: 0L
        val endTime = movements.maxOfOrNull { it.timestamp } ?: 0L
        
        return MovementStats(totalCount, typeCounts, startTime, endTime)
    }
    
    fun close() {
        // Perform any cleanup if needed
        Log.d(TAG, "Movement logger closed")
    }
}

data class MovementStats(
    val totalCount: Int,
    val typeDistribution: Map<MovementType, Int>,
    val startTime: Long,
    val endTime: Long
)
