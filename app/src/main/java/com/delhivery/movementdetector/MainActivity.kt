package com.delhivery.movementdetector

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {
    
    private var movementDetectionService: MovementDetectionService? = null
    private var isServiceBound = false
    
    private lateinit var statusText: TextView
    private lateinit var confidenceText: TextView
    private lateinit var startStopButton: Button
    private lateinit var clearLogButton: Button
    private lateinit var movementRecyclerView: RecyclerView
    private lateinit var movementAdapter: MovementAdapter
    
    private val movementHistory = mutableListOf<MovementData>()
    
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MovementDetectionService.MovementDetectionBinder
            movementDetectionService = binder.getService()
            isServiceBound = true
            
            // Set up callback for movement updates
            movementDetectionService?.onMovementDetected = { movementData ->
                runOnUiThread {
                    updateUI(movementData)
                    addMovementToHistory(movementData)
                }
            }
            
            // Load existing movement history
            loadMovementHistory()
            updateStartStopButton()
        }
        
        override fun onServiceDisconnected(name: ComponentName?) {
            movementDetectionService = null
            isServiceBound = false
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Permissions required for movement detection", Toast.LENGTH_LONG).show()
        }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        initializeViews()
        setupRecyclerView()
        checkAndRequestPermissions()
    }
    
    private fun initializeViews() {
        statusText = findViewById(R.id.statusText)
        confidenceText = findViewById(R.id.confidenceText)
        startStopButton = findViewById(R.id.startStopButton)
        clearLogButton = findViewById(R.id.clearLogButton)
        movementRecyclerView = findViewById(R.id.movementRecyclerView)
        
        startStopButton.setOnClickListener {
            if (isServiceRunning()) {
                stopMovementDetection()
            } else {
                startMovementDetection()
            }
        }
        
        clearLogButton.setOnClickListener {
            clearMovementHistory()
        }
    }
    
    private fun setupRecyclerView() {
        movementAdapter = MovementAdapter(movementHistory)
        movementRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = movementAdapter
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }
        
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.BODY_SENSORS)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) 
                != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
    
    private fun startMovementDetection() {
        val intent = Intent(this, MovementDetectionService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        updateStartStopButton()
    }
    
    private fun stopMovementDetection() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        
        val intent = Intent(this, MovementDetectionService::class.java)
        stopService(intent)
        updateStartStopButton()
        
        statusText.text = "Movement Detection Stopped"
        confidenceText.text = ""
    }
    
    private fun isServiceRunning(): Boolean {
        return isServiceBound && movementDetectionService != null
    }
    
    private fun updateStartStopButton() {
        startStopButton.text = if (isServiceRunning()) "Stop Detection" else "Start Detection"
    }
    
    private fun updateUI(movementData: MovementData) {
        val movementName = movementData.movementType.name.replace("_", " ")
        statusText.text = "Current Activity: $movementName"
        confidenceText.text = "Confidence: ${(movementData.confidence * 100).toInt()}%"
    }
    
    private fun addMovementToHistory(movementData: MovementData) {
        movementHistory.add(0, movementData) // Add to beginning
        
        // Keep only last 100 items for UI performance
        if (movementHistory.size > 100) {
            movementHistory.removeAt(movementHistory.size - 1)
        }
        
        movementAdapter.notifyItemInserted(0)
        movementRecyclerView.scrollToPosition(0)
    }
    
    private fun loadMovementHistory() {
        movementDetectionService?.getCurrentMovementData()?.let { data ->
            movementHistory.clear()
            movementHistory.addAll(data.reversed()) // Show newest first
            movementAdapter.notifyDataSetChanged()
        }
    }
    
    private fun clearMovementHistory() {
        movementDetectionService?.clearMovementHistory()
        movementHistory.clear()
        movementAdapter.notifyDataSetChanged()
        Toast.makeText(this, "Movement history cleared", Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
}

class MovementAdapter(private val movements: List<MovementData>) : 
    RecyclerView.Adapter<MovementAdapter.MovementViewHolder>() {
    
    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    
    class MovementViewHolder(itemView: android.view.View) : RecyclerView.ViewHolder(itemView) {
        val timeText: TextView = itemView.findViewById(R.id.timeText)
        val movementText: TextView = itemView.findViewById(R.id.movementText)
        val confidenceText: TextView = itemView.findViewById(R.id.confidenceText)
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): MovementViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_movement, parent, false)
        return MovementViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: MovementViewHolder, position: Int) {
        val movement = movements[position]
        
        holder.timeText.text = dateFormat.format(Date(movement.timestamp))
        holder.movementText.text = movement.movementType.name.replace("_", " ")
        holder.confidenceText.text = "${(movement.confidence * 100).toInt()}%"
        
        // Set background color based on movement type
        val backgroundColor = when (movement.movementType) {
            MovementType.WALKING_STRAIGHT -> android.graphics.Color.parseColor("#E8F5E8")
            MovementType.CLIMBING_STAIRS -> android.graphics.Color.parseColor("#FFF3E0")
            MovementType.DESCENDING_STAIRS -> android.graphics.Color.parseColor("#FFF8E1")
            MovementType.IN_ELEVATOR -> android.graphics.Color.parseColor("#E3F2FD")
            MovementType.STATIONARY -> android.graphics.Color.parseColor("#F5F5F5")
            MovementType.UNKNOWN -> android.graphics.Color.parseColor("#FFEBEE")
        }
        holder.itemView.setBackgroundColor(backgroundColor)
    }
    
    override fun getItemCount(): Int = movements.size
}
