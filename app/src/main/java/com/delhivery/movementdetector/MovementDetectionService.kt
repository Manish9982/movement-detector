package com.delhivery.movementdetector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

class MovementDetectionService : Service(), SensorEventListener {
    
    private val binder = MovementDetectionBinder()
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    
    private val classifier = MovementClassifier()
    private val dataLogger = MovementDataLogger()
    
    private var currentAccelerometerData = FloatArray(3)
    private var currentGyroscopeData = FloatArray(3)
    private var lastClassificationTime = 0L
    private val classificationInterval = 1000L // Classify every 1 second
    
    // Callback for activity updates
    var onMovementDetected: ((MovementData) -> Unit)? = null
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "movement_detection_channel"
    }
    
    inner class MovementDetectionBinder : Binder() {
        fun getService(): MovementDetectionService = this@MovementDetectionService
    }
    
    override fun onCreate() {
        super.onCreate()
        
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        
        dataLogger.initialize(this)
        
        // Acquire wake lock to keep service running
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MovementDetector::WakeLock"
        )
        
        createNotificationChannel()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification("Movement Detection Active"))
        startSensorListening()
        wakeLock?.acquire()
        
        return START_STICKY
    }
    
    override fun onBind(intent: Intent?): IBinder {
        return binder
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopSensorListening()
        wakeLock?.release()
        dataLogger.close()
    }
    
    private fun startSensorListening() {
        accelerometer?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI // ~20Hz
            )
        }
        
        gyroscope?.let { sensor ->
            sensorManager.registerListener(
                this,
                sensor,
                SensorManager.SENSOR_DELAY_UI // ~20Hz
            )
        }
    }
    
    private fun stopSensorListening() {
        sensorManager.unregisterListener(this)
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event?.let { sensorEvent ->
            when (sensorEvent.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    currentAccelerometerData = sensorEvent.values.clone()
                }
                Sensor.TYPE_GYROSCOPE -> {
                    currentGyroscopeData = sensorEvent.values.clone()
                }
            }
            
            // Add data to classifier
            classifier.addSensorData(currentAccelerometerData, currentGyroscopeData)
            
            // Perform classification periodically
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastClassificationTime >= classificationInterval) {
                performClassification()
                lastClassificationTime = currentTime
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }
    
    private fun performClassification() {
        val (movementType, confidence) = classifier.classifyMovement()
        
        val movementData = MovementData(
            timestamp = System.currentTimeMillis(),
            movementType = movementType,
            confidence = confidence,
            accelerometerData = currentAccelerometerData.clone(),
            gyroscopeData = currentGyroscopeData.clone()
        )
        
        // Log the detected movement
        dataLogger.logMovement(movementData)
        
        // Update notification
        updateNotification(movementType, confidence)
        
        // Notify listeners
        onMovementDetected?.invoke(movementData)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Movement Detection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows current movement detection status"
            }
            
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(contentText: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Movement Detector")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .build()
    }
    
    private fun updateNotification(movementType: MovementType, confidence: Float) {
        val contentText = "${movementType.name.replace("_", " ")} (${(confidence * 100).toInt()}%)"
        val notification = createNotification(contentText)
        
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // Check notification permission for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == 
                android.content.pm.PackageManager.PERMISSION_GRANTED) {
                notificationManager.notify(NOTIFICATION_ID, notification)
            }
        } else {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }
    
    fun getCurrentMovementData(): List<MovementData> {
        return dataLogger.getRecentMovements(50) // Get last 50 movements
    }
    
    fun clearMovementHistory() {
        dataLogger.clearHistory()
    }
}
