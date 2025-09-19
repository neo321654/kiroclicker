package com.autoclicker.android.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Bitmap
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import android.os.Build
import android.graphics.Path
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.autoclicker.android.model.ClickConfig
import com.autoclicker.android.model.AutoClickState
import com.autoclicker.android.model.MatchResult
import com.autoclicker.android.utils.ImageMatcher
import com.autoclicker.android.utils.OpenCVImageMatcher
import org.opencv.android.Utils
import org.opencv.core.Mat
import java.nio.ByteBuffer
import kotlinx.coroutines.*
import android.graphics.BitmapFactory
import java.io.File
import kotlin.coroutines.suspendCoroutine
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

class AutoClickService : AccessibilityService() {
    
    companion object {
        private const val TAG = "AutoClickService"
        
        // Service instance for communication
        private var instance: AutoClickService? = null
        
        fun getInstance(): AutoClickService? = instance
        
        fun isServiceRunning(): Boolean = instance != null
        
        // Broadcast actions
        const val ACTION_STATE_CHANGED = "com.autoclicker.android.STATE_CHANGED"
        const val ACTION_CLICK_COUNT_UPDATED = "com.autoclicker.android.CLICK_COUNT_UPDATED"
        const val ACTION_START_AUTO_CLICK = "com.autoclicker.android.START_AUTO_CLICK"
        const val ACTION_STOP_AUTO_CLICK = "com.autoclicker.android.STOP_AUTO_CLICK"
        
        // Broadcast extras
        const val EXTRA_STATE = "state"
        const val EXTRA_CLICK_COUNT = "click_count"
        const val EXTRA_ERROR_MESSAGE = "error_message"
        
        // Notification
        private const val NOTIFICATION_CHANNEL_ID = "auto_click_service"
        private const val NOTIFICATION_ID = 1001
    }
    
    private var isServiceConnected = false
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        
        Log.d(TAG, "AutoClickService connected")
        
        // Set service instance
        instance = this
        isServiceConnected = true
        
        // Initialize notification and broadcast components
        initializeNotificationChannel()
        initializeBroadcastReceiver()
        
        // Configure service info
        val info = AccessibilityServiceInfo().apply {
            // Set event types we want to listen to
            eventTypes = AccessibilityEvent.TYPES_ALL_MASK
            
            // Set feedback type
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            
            // Set flags
            flags = AccessibilityServiceInfo.DEFAULT or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY
            
            // Set notification timeout
            notificationTimeout = 100
        }
        
        // Apply the configuration
        serviceInfo = info
        
        Log.d(TAG, "AutoClickService configuration applied")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // We don't need to handle accessibility events for our use case
        // This service is primarily used for gesture dispatch and screenshot capture
    }
    
    override fun onInterrupt() {
        Log.d(TAG, "AutoClickService interrupted")
        
        // Stop any ongoing auto-click operations
        stopAutoClick()
        
        // Clean up screenshot resources
        cleanupScreenshotResources()
        
        // Clean up broadcast receiver
        cleanupBroadcastReceiver()
        
        // Clear service instance
        instance = null
        isServiceConnected = false
    }
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "AutoClickService destroyed")
        
        // Clean up resources
        stopAutoClick()
        cleanupScreenshotResources()
        cleanupBroadcastReceiver()
        autoClickScope.cancel()
        instance = null
        isServiceConnected = false
    }
    
    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "AutoClickService unbound")
        
        // Clean up when service is unbound
        stopAutoClick()
        cleanupScreenshotResources()
        cleanupBroadcastReceiver()
        instance = null
        isServiceConnected = false
        
        return super.onUnbind(intent)
    }
    
    /**
     * Cleans up screenshot-related resources
     */
    private fun cleanupScreenshotResources() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            
            imageReader?.close()
            imageReader = null
            
            mediaProjection?.stop()
            mediaProjection = null
            
            Log.d(TAG, "Screenshot resources cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up screenshot resources", e)
        }
    }
    
    // Screenshot related variables
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaProjection: MediaProjection? = null
    private val handler = Handler(Looper.getMainLooper())
    
    // Auto-click related variables
    private var autoClickJob: Job? = null
    private var currentState: AutoClickState = AutoClickState.Idle
    private var currentConfig: ClickConfig? = null
    private var clickCount: Int = 0
    private val imageMatcher: ImageMatcher = OpenCVImageMatcher()
    private val autoClickScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // Notification and broadcast management
    private lateinit var notificationManager: NotificationManager
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private var controlReceiver: BroadcastReceiver? = null
    
    // Check if service is properly connected and has required capabilities
    fun isReady(): Boolean {
        return isServiceConnected && 
               serviceInfo != null &&
               canPerformGestures() &&
               hasScreenshotPermission()
    }
    
    /**
     * Takes a screenshot of the current screen
     * Requires API 28+ and accessibility service to be enabled
     */
    @RequiresApi(Build.VERSION_CODES.P)
    fun takeScreenshot(callback: (Bitmap?) -> Unit) {
        if (!isReady()) {
            Log.e(TAG, "Service not ready for screenshot")
            callback(null)
            return
        }
        
        try {
            // Use the new takeScreenshot API available from API 28+
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                applicationContext.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                        try {
                            val bitmap = Bitmap.wrapHardwareBuffer(
                                screenshotResult.hardwareBuffer,
                                screenshotResult.colorSpace
                            )
                            
                            // Convert hardware bitmap to software bitmap for processing
                            val softwareBitmap = bitmap?.copy(Bitmap.Config.ARGB_8888, false)
                            
                            Log.d(TAG, "Screenshot taken successfully: ${softwareBitmap?.width}x${softwareBitmap?.height}")
                            callback(softwareBitmap)
                            
                            // Clean up
                            screenshotResult.hardwareBuffer.close()
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing screenshot result", e)
                            callback(null)
                        }
                    }
                    
                    override fun onFailure(errorCode: Int) {
                        Log.e(TAG, "Screenshot failed with error code: $errorCode")
                        callback(null)
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error taking screenshot", e)
            callback(null)
        }
    }
    
    /**
     * Converts a Bitmap to OpenCV Mat format for image processing
     */
    fun bitmapToMat(bitmap: Bitmap): Mat? {
        return try {
            val mat = Mat()
            Utils.bitmapToMat(bitmap, mat)
            Log.d(TAG, "Bitmap converted to Mat: ${mat.rows()}x${mat.cols()}")
            mat
        } catch (e: Exception) {
            Log.e(TAG, "Error converting bitmap to Mat", e)
            null
        }
    }
    
    /**
     * Converts OpenCV Mat back to Bitmap
     */
    fun matToBitmap(mat: Mat): Bitmap? {
        return try {
            val bitmap = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(mat, bitmap)
            Log.d(TAG, "Mat converted to Bitmap: ${bitmap.width}x${bitmap.height}")
            bitmap
        } catch (e: Exception) {
            Log.e(TAG, "Error converting Mat to bitmap", e)
            null
        }
    }
    
    /**
     * Gets screen dimensions for screenshot processing
     */
    private fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val display = windowManager.defaultDisplay
        val metrics = DisplayMetrics()
        display.getRealMetrics(metrics)
        return Pair(metrics.widthPixels, metrics.heightPixels)
    }
    
    /**
     * Checks if screenshot permissions are available
     */
    fun hasScreenshotPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            serviceInfo?.capabilities?.and(AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0
        } else {
            false
        }
    }
    
    /**
     * Performs a click at the specified coordinates
     * @param x X coordinate for the click
     * @param y Y coordinate for the click
     * @param callback Callback to handle success/failure
     */
    fun performClick(x: Int, y: Int, callback: ((Boolean) -> Unit)? = null) {
        if (!isReady()) {
            Log.e(TAG, "Service not ready for performing clicks")
            callback?.invoke(false)
            return
        }
        
        if (!canPerformGestures()) {
            Log.e(TAG, "Service cannot perform gestures")
            callback?.invoke(false)
            return
        }
        
        try {
            Log.d(TAG, "Performing click at coordinates: ($x, $y)")
            
            // Create a path for the click gesture
            val clickPath = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            // Create gesture stroke
            val gestureStroke = GestureDescription.StrokeDescription(
                clickPath,
                0, // Start time
                100 // Duration in milliseconds
            )
            
            // Create gesture description
            val gestureDescription = GestureDescription.Builder()
                .addStroke(gestureStroke)
                .build()
            
            // Dispatch the gesture
            val success = dispatchGesture(
                gestureDescription,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "Click gesture completed successfully at ($x, $y)")
                        callback?.invoke(true)
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "Click gesture was cancelled at ($x, $y)")
                        callback?.invoke(false)
                    }
                },
                null // Use main thread handler
            )
            
            if (!success) {
                Log.e(TAG, "Failed to dispatch click gesture at ($x, $y)")
                callback?.invoke(false)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing click at ($x, $y)", e)
            callback?.invoke(false)
        }
    }
    
    /**
     * Performs a long click at the specified coordinates
     * @param x X coordinate for the long click
     * @param y Y coordinate for the long click
     * @param durationMs Duration of the long click in milliseconds
     * @param callback Callback to handle success/failure
     */
    fun performLongClick(x: Int, y: Int, durationMs: Long = 1000, callback: ((Boolean) -> Unit)? = null) {
        if (!isReady()) {
            Log.e(TAG, "Service not ready for performing long clicks")
            callback?.invoke(false)
            return
        }
        
        try {
            Log.d(TAG, "Performing long click at coordinates: ($x, $y) for ${durationMs}ms")
            
            // Create a path for the long click gesture
            val longClickPath = Path().apply {
                moveTo(x.toFloat(), y.toFloat())
            }
            
            // Create gesture stroke with longer duration
            val gestureStroke = GestureDescription.StrokeDescription(
                longClickPath,
                0, // Start time
                durationMs // Duration in milliseconds
            )
            
            // Create gesture description
            val gestureDescription = GestureDescription.Builder()
                .addStroke(gestureStroke)
                .build()
            
            // Dispatch the gesture
            val success = dispatchGesture(
                gestureDescription,
                object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.d(TAG, "Long click gesture completed successfully at ($x, $y)")
                        callback?.invoke(true)
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.w(TAG, "Long click gesture was cancelled at ($x, $y)")
                        callback?.invoke(false)
                    }
                },
                null
            )
            
            if (!success) {
                Log.e(TAG, "Failed to dispatch long click gesture at ($x, $y)")
                callback?.invoke(false)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error performing long click at ($x, $y)", e)
            callback?.invoke(false)
        }
    }
    
    /**
     * Validates if the given coordinates are within screen bounds
     */
    fun isValidClickCoordinates(x: Int, y: Int): Boolean {
        val (screenWidth, screenHeight) = getScreenDimensions()
        return x >= 0 && x < screenWidth && y >= 0 && y < screenHeight
    }
    
    /**
     * Checks if the service can perform gestures
     */
    fun canPerformGestures(): Boolean {
        return isServiceConnected && 
               serviceInfo?.capabilities?.and(AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0
    }
    
    /**
     * Starts the auto-click process with the given configuration
     * @param config ClickConfig containing all necessary parameters
     */
    fun startAutoClick(config: ClickConfig) {
        Log.d(TAG, "Starting auto-click with config: ${config.name}")
        
        if (!isReady()) {
            Log.e(TAG, "Service not ready for auto-click")
            updateState(AutoClickState.Error("Service not ready"))
            return
        }
        
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            Log.e(TAG, "Auto-click requires API 28+")
            updateState(AutoClickState.Error("Requires Android 9.0 or higher"))
            return
        }
        
        // Stop any existing auto-click process
        stopAutoClick()
        
        // Validate configuration
        if (!validateConfig(config)) {
            Log.e(TAG, "Invalid configuration")
            updateState(AutoClickState.Error("Invalid configuration"))
            return
        }
        
        // Initialize state
        currentConfig = config
        clickCount = 0
        updateState(AutoClickState.Searching)
        
        // Start the auto-click loop
        autoClickJob = autoClickScope.launch {
            try {
                runAutoClickLoop(config)
            } catch (e: CancellationException) {
                Log.d(TAG, "Auto-click cancelled")
                updateState(AutoClickState.Idle)
            } catch (e: Exception) {
                Log.e(TAG, "Error in auto-click loop", e)
                updateState(AutoClickState.Error("Auto-click failed: ${e.message}"))
            }
        }
    }
    
    /**
     * Stops the auto-click process
     */
    fun stopAutoClick() {
        Log.d(TAG, "Stopping auto-click")
        
        autoClickJob?.cancel()
        autoClickJob = null
        currentConfig = null
        
        updateState(AutoClickState.Idle)
    }
    
    /**
     * Main auto-click loop implementation
     * Cycle: capture screen → find template → click → wait
     */
    private suspend fun runAutoClickLoop(config: ClickConfig) {
        Log.d(TAG, "Starting auto-click loop")
        
        // Load template image
        val templateBitmap = loadTemplateBitmap(config.templateImagePath)
        if (templateBitmap == null) {
            updateState(AutoClickState.Error("Failed to load template image"))
            return
        }
        
        while (autoClickJob?.isActive == true) {
            // Check if we've reached the repeat limit
            if (config.repeatCount > 0 && clickCount >= config.repeatCount) {
                Log.d(TAG, "Reached repeat limit: $clickCount")
                updateState(AutoClickState.Completed(clickCount))
                break
            }
            
            // Update state to searching
            updateState(AutoClickState.Searching)
            
            // Capture screenshot
            val screenshot = captureScreenshotSuspend()
            if (screenshot == null) {
                Log.w(TAG, "Failed to capture screenshot, retrying...")
                delay(1000) // Wait before retry
                continue
            }
            
            // Find template in screenshot
            val matchResult = imageMatcher.findTemplate(
                screenshot, 
                templateBitmap, 
                config.threshold
            )
            
            matchResult.fold(
                onSuccess = { result ->
                    if (result.found && result.location != null) {
                        // Template found, perform click
                        val clickX = result.location.x + config.clickX
                        val clickY = result.location.y + config.clickY
                        
                        if (isValidClickCoordinates(clickX, clickY)) {
                            updateState(AutoClickState.Clicking)
                            
                            // Perform click and wait for completion
                            val clickSuccess = performClickSuspend(clickX, clickY)
                            
                            if (clickSuccess) {
                                clickCount++
                                Log.d(TAG, "Click performed successfully at ($clickX, $clickY). Count: $clickCount")
                                
                                // Broadcast click count update
                                onClickCountUpdated()
                                
                                // Update state to waiting
                                updateState(AutoClickState.Waiting)
                                
                                // Wait for the specified interval
                                delay(config.intervalMs)
                            } else {
                                Log.w(TAG, "Click failed at ($clickX, $clickY)")
                                delay(1000) // Wait before retry
                            }
                        } else {
                            Log.w(TAG, "Invalid click coordinates: ($clickX, $clickY)")
                            delay(1000) // Wait before retry
                        }
                    } else {
                        // Template not found, continue searching
                        Log.d(TAG, "Template not found (confidence: ${result.confidence}), continuing search...")
                        delay(500) // Short delay before next search
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Error in template matching", error)
                    delay(1000) // Wait before retry
                }
            )
        }
        
        Log.d(TAG, "Auto-click loop completed")
    }
    
    /**
     * Validates the click configuration
     */
    private fun validateConfig(config: ClickConfig): Boolean {
        return try {
            // Check if template image file exists
            val templateFile = File(config.templateImagePath)
            if (!templateFile.exists()) {
                Log.e(TAG, "Template image file does not exist: ${config.templateImagePath}")
                return false
            }
            
            // Validate interval
            if (config.intervalMs < 100) {
                Log.e(TAG, "Interval too short: ${config.intervalMs}ms")
                return false
            }
            
            // Validate threshold
            if (config.threshold < 0.0 || config.threshold > 1.0) {
                Log.e(TAG, "Invalid threshold: ${config.threshold}")
                return false
            }
            
            // Validate repeat count
            if (config.repeatCount < -1) {
                Log.e(TAG, "Invalid repeat count: ${config.repeatCount}")
                return false
            }
            
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error validating config", e)
            false
        }
    }
    
    /**
     * Loads template bitmap from file path
     */
    private fun loadTemplateBitmap(imagePath: String): Bitmap? {
        return try {
            val file = File(imagePath)
            if (file.exists()) {
                BitmapFactory.decodeFile(imagePath)
            } else {
                Log.e(TAG, "Template image file not found: $imagePath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading template bitmap", e)
            null
        }
    }
    
    /**
     * Suspending version of screenshot capture
     */
    private suspend fun captureScreenshotSuspend(): Bitmap? = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                takeScreenshot { bitmap ->
                    continuation.resume(bitmap)
                }
            } else {
                continuation.resume(null)
            }
        }
    }
    
    /**
     * Suspending version of click performance
     */
    private suspend fun performClickSuspend(x: Int, y: Int): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { continuation ->
            performClick(x, y) { success ->
                continuation.resume(success)
            }
        }
    }
    
    /**
     * Updates the current auto-click state and broadcasts the change
     */
    private fun updateState(newState: AutoClickState) {
        currentState = newState
        Log.d(TAG, "State updated to: $newState")
        
        // Broadcast state change
        broadcastStateChange(newState)
        
        // Update notification
        updateNotification(newState)
    }
    
    /**
     * Gets the current auto-click state
     */
    fun getCurrentState(): AutoClickState = currentState
    
    /**
     * Gets the current click count
     */
    fun getClickCount(): Int = clickCount
    
    /**
     * Checks if auto-click is currently running
     */
    fun isAutoClickRunning(): Boolean = autoClickJob?.isActive == true
    
    /**
     * Initializes the notification channel for the service
     */
    private fun initializeNotificationChannel() {
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Auto Click Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows the status of auto-click operations"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
        
        Log.d(TAG, "Notification channel initialized")
    }
    
    /**
     * Initializes the broadcast receiver for service control
     */
    private fun initializeBroadcastReceiver() {
        localBroadcastManager = LocalBroadcastManager.getInstance(this)
        
        controlReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    ACTION_START_AUTO_CLICK -> {
                        // Handle start command from UI
                        Log.d(TAG, "Received start command via broadcast")
                    }
                    ACTION_STOP_AUTO_CLICK -> {
                        // Handle stop command from UI
                        Log.d(TAG, "Received stop command via broadcast")
                        stopAutoClick()
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(ACTION_START_AUTO_CLICK)
            addAction(ACTION_STOP_AUTO_CLICK)
        }
        
        localBroadcastManager.registerReceiver(controlReceiver!!, filter)
        Log.d(TAG, "Broadcast receiver initialized")
    }
    
    /**
     * Cleans up the broadcast receiver
     */
    private fun cleanupBroadcastReceiver() {
        controlReceiver?.let { receiver ->
            try {
                localBroadcastManager.unregisterReceiver(receiver)
                controlReceiver = null
                Log.d(TAG, "Broadcast receiver cleaned up")
            } catch (e: Exception) {
                Log.e(TAG, "Error cleaning up broadcast receiver", e)
            }
        }
    }
    
    /**
     * Broadcasts state changes to interested components
     */
    private fun broadcastStateChange(state: AutoClickState) {
        try {
            val intent = Intent(ACTION_STATE_CHANGED).apply {
                putExtra(EXTRA_STATE, state.javaClass.simpleName)
                
                when (state) {
                    is AutoClickState.Error -> {
                        putExtra(EXTRA_ERROR_MESSAGE, state.message)
                    }
                    is AutoClickState.Completed -> {
                        putExtra(EXTRA_CLICK_COUNT, state.clickCount)
                    }
                    else -> {
                        putExtra(EXTRA_CLICK_COUNT, clickCount)
                    }
                }
            }
            
            localBroadcastManager.sendBroadcast(intent)
            Log.d(TAG, "State change broadcasted: ${state.javaClass.simpleName}")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting state change", e)
        }
    }
    
    /**
     * Broadcasts click count updates
     */
    private fun broadcastClickCountUpdate() {
        try {
            val intent = Intent(ACTION_CLICK_COUNT_UPDATED).apply {
                putExtra(EXTRA_CLICK_COUNT, clickCount)
            }
            
            localBroadcastManager.sendBroadcast(intent)
            Log.d(TAG, "Click count update broadcasted: $clickCount")
        } catch (e: Exception) {
            Log.e(TAG, "Error broadcasting click count update", e)
        }
    }
    
    /**
     * Updates the service notification based on current state
     */
    private fun updateNotification(state: AutoClickState) {
        try {
            val notificationText = when (state) {
                is AutoClickState.Idle -> "Auto-click service ready"
                is AutoClickState.Searching -> "Searching for template..."
                is AutoClickState.Clicking -> "Performing click ($clickCount)"
                is AutoClickState.Waiting -> "Waiting for next click ($clickCount)"
                is AutoClickState.Error -> "Error: ${state.message}"
                is AutoClickState.Completed -> "Completed: ${state.clickCount} clicks"
            }
            
            val stopIntent = Intent(ACTION_STOP_AUTO_CLICK)
            val stopPendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            
            val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setContentTitle("Auto Click Service")
                .setContentText(notificationText)
                .setSmallIcon(android.R.drawable.ic_media_play) // Using system icon for now
                .setOngoing(state !is AutoClickState.Idle && state !is AutoClickState.Error && state !is AutoClickState.Completed)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .apply {
                    if (isAutoClickRunning()) {
                        addAction(
                            android.R.drawable.ic_media_pause,
                            "Stop",
                            stopPendingIntent
                        )
                    }
                }
                .build()
            
            notificationManager.notify(NOTIFICATION_ID, notification)
            Log.d(TAG, "Notification updated: $notificationText")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating notification", e)
        }
    }
    
    /**
     * Sends a broadcast when click count is updated
     */
    private fun onClickCountUpdated() {
        broadcastClickCountUpdate()
    }
}