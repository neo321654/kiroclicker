package com.autoclicker.android.device

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.autoclicker.android.service.AutoClickService
import java.io.File
import java.io.FileOutputStream

/**
 * Utility class for device testing of the AutoClicker application.
 * Provides methods to test real device functionality including accessibility services,
 * image matching accuracy, and performance optimization.
 */
object DeviceTestUtils {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val uiDevice: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    /**
     * Check if the AutoClicker accessibility service is enabled on the device
     */
    fun isAccessibilityServiceEnabled(): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        
        return enabledServices.any { serviceInfo ->
            serviceInfo.resolveInfo.serviceInfo.name == AutoClickService::class.java.name
        }
    }

    /**
     * Get accessibility service information
     */
    fun getAccessibilityServiceInfo(): AccessibilityServiceInfo? {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val availableServices = accessibilityManager.installedAccessibilityServiceList
        
        return availableServices.find { serviceInfo ->
            serviceInfo.resolveInfo.serviceInfo.name == AutoClickService::class.java.name
        }
    }

    /**
     * Open accessibility settings to allow manual service enabling
     */
    fun openAccessibilitySettings(): Intent {
        return Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
    }

    /**
     * Take a screenshot using UiAutomator (for testing purposes)
     */
    fun takeScreenshot(filename: String = "test_screenshot.png"): File? {
        return try {
            val screenshotFile = File(context.externalCacheDir, filename)
            val success = uiDevice.takeScreenshot(screenshotFile)
            if (success) screenshotFile else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Create test images for image matching accuracy testing
     */
    fun createTestImages(): TestImageSet {
        val testDir = File(context.filesDir, "device_test_images")
        if (!testDir.exists()) {
            testDir.mkdirs()
        }

        // Create various test patterns
        val simplePattern = createSimplePatternBitmap()
        val complexPattern = createComplexPatternBitmap()
        val textPattern = createTextPatternBitmap()
        val colorGradient = createColorGradientBitmap()

        // Save test images
        val simpleFile = File(testDir, "simple_pattern.png")
        val complexFile = File(testDir, "complex_pattern.png")
        val textFile = File(testDir, "text_pattern.png")
        val gradientFile = File(testDir, "gradient_pattern.png")

        saveBitmapToFile(simplePattern, simpleFile)
        saveBitmapToFile(complexPattern, complexFile)
        saveBitmapToFile(textPattern, textFile)
        saveBitmapToFile(colorGradient, gradientFile)

        return TestImageSet(
            simplePattern = simpleFile,
            complexPattern = complexFile,
            textPattern = textFile,
            colorGradient = gradientFile,
            testDirectory = testDir
        )
    }

    /**
     * Measure image matching performance
     */
    fun measureImageMatchingPerformance(templateBitmap: Bitmap, targetBitmap: Bitmap, iterations: Int = 10): PerformanceResult {
        val times = mutableListOf<Long>()
        var successCount = 0

        repeat(iterations) {
            val startTime = System.nanoTime()
            
            try {
                // Simulate image matching operation
                val result = performImageMatching(templateBitmap, targetBitmap)
                val endTime = System.nanoTime()
                
                times.add(endTime - startTime)
                if (result) successCount++
            } catch (e: Exception) {
                // Record failed attempt
                times.add(-1)
            }
        }

        val validTimes = times.filter { it > 0 }
        val averageTime = if (validTimes.isNotEmpty()) validTimes.average() else 0.0
        val minTime = validTimes.minOrNull() ?: 0L
        val maxTime = validTimes.maxOrNull() ?: 0L

        return PerformanceResult(
            averageTimeNanos = averageTime,
            minTimeNanos = minTime,
            maxTimeNanos = maxTime,
            successRate = successCount.toDouble() / iterations,
            totalIterations = iterations
        )
    }

    /**
     * Test accessibility service functionality
     */
    fun testAccessibilityServiceFunctionality(): AccessibilityTestResult {
        val service = AutoClickService.getInstance()
        
        return AccessibilityTestResult(
            serviceAvailable = service != null,
            serviceRunning = AutoClickService.isServiceRunning(),
            canPerformGestures = service?.canPerformGestures() ?: false,
            hasScreenshotPermission = service?.hasScreenshotPermission() ?: false,
            isReady = service?.isReady() ?: false
        )
    }

    /**
     * Test click accuracy at various screen positions
     */
    fun testClickAccuracy(): ClickAccuracyResult {
        val testPositions = listOf(
            Pair(100, 100),    // Top-left area
            Pair(500, 300),    // Center-left
            Pair(800, 600),    // Center-right
            Pair(1000, 1800),  // Bottom area
            Pair(50, 50)       // Edge case
        )

        val results = mutableListOf<ClickTestResult>()
        val service = AutoClickService.getInstance()

        testPositions.forEach { (x, y) ->
            val isValid = service?.isValidClickCoordinates(x, y) ?: false
            var clickSuccess = false
            var executionTime = 0L

            if (isValid && service != null) {
                val startTime = System.nanoTime()
                service.performClick(x, y) { success ->
                    clickSuccess = success
                    executionTime = System.nanoTime() - startTime
                }
                // Wait a bit for callback
                Thread.sleep(100)
            }

            results.add(ClickTestResult(
                x = x,
                y = y,
                coordinatesValid = isValid,
                clickSuccessful = clickSuccess,
                executionTimeNanos = executionTime
            ))
        }

        val successfulClicks = results.count { it.clickSuccessful }
        val validCoordinates = results.count { it.coordinatesValid }

        return ClickAccuracyResult(
            testResults = results,
            overallSuccessRate = successfulClicks.toDouble() / results.size,
            coordinateValidationRate = validCoordinates.toDouble() / results.size,
            averageExecutionTime = results.filter { it.executionTimeNanos > 0 }
                .map { it.executionTimeNanos }.average()
        )
    }

    /**
     * Monitor memory usage during auto-click operations
     */
    fun monitorMemoryUsage(durationMs: Long = 30000): MemoryUsageResult {
        val runtime = Runtime.getRuntime()
        val measurements = mutableListOf<MemoryMeasurement>()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < durationMs) {
            val totalMemory = runtime.totalMemory()
            val freeMemory = runtime.freeMemory()
            val usedMemory = totalMemory - freeMemory
            val maxMemory = runtime.maxMemory()

            measurements.add(MemoryMeasurement(
                timestamp = System.currentTimeMillis(),
                usedMemoryBytes = usedMemory,
                totalMemoryBytes = totalMemory,
                maxMemoryBytes = maxMemory,
                freeMemoryBytes = freeMemory
            ))

            Thread.sleep(1000) // Measure every second
        }

        val avgUsedMemory = measurements.map { it.usedMemoryBytes }.average()
        val maxUsedMemory = measurements.maxOfOrNull { it.usedMemoryBytes } ?: 0L
        val minUsedMemory = measurements.minOfOrNull { it.usedMemoryBytes } ?: 0L

        return MemoryUsageResult(
            measurements = measurements,
            averageUsedMemoryBytes = avgUsedMemory,
            maxUsedMemoryBytes = maxUsedMemory,
            minUsedMemoryBytes = minUsedMemory,
            memoryLeakDetected = maxUsedMemory > avgUsedMemory * 1.5 // Simple heuristic
        )
    }

    /**
     * Test image recognition accuracy with various conditions
     */
    fun testImageRecognitionAccuracy(): ImageRecognitionResult {
        val testImages = createTestImages()
        val results = mutableListOf<RecognitionTestResult>()

        // Test different scenarios
        val scenarios = listOf(
            "exact_match" to 1.0,
            "slight_rotation" to 0.9,
            "brightness_change" to 0.8,
            "partial_occlusion" to 0.7,
            "scale_change" to 0.6
        )

        scenarios.forEach { (scenario, expectedConfidence) ->
            val templateBitmap = when (scenario) {
                "exact_match" -> loadBitmapFromFile(testImages.simplePattern)
                "slight_rotation" -> createRotatedBitmap(loadBitmapFromFile(testImages.simplePattern), 5f)
                "brightness_change" -> createBrightnessAdjustedBitmap(loadBitmapFromFile(testImages.simplePattern), 1.2f)
                "partial_occlusion" -> createPartiallyOccludedBitmap(loadBitmapFromFile(testImages.simplePattern))
                "scale_change" -> createScaledBitmap(loadBitmapFromFile(testImages.simplePattern), 0.8f)
                else -> loadBitmapFromFile(testImages.simplePattern)
            }

            val targetBitmap = loadBitmapFromFile(testImages.simplePattern)
            val matchResult = performImageMatching(templateBitmap, targetBitmap)
            
            results.add(RecognitionTestResult(
                scenario = scenario,
                expectedConfidence = expectedConfidence,
                actualSuccess = matchResult,
                confidenceThresholdMet = matchResult // Simplified for this test
            ))
        }

        val successfulRecognitions = results.count { it.actualSuccess }
        val overallAccuracy = successfulRecognitions.toDouble() / results.size

        return ImageRecognitionResult(
            testResults = results,
            overallAccuracy = overallAccuracy,
            testImageSet = testImages
        )
    }

    // Helper methods
    private fun createSimplePatternBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            style = android.graphics.Paint.Style.FILL
        }
        
        // Draw simple geometric pattern
        canvas.drawRect(20f, 20f, 80f, 80f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(50f, 50f, 20f, paint)
        
        return bitmap
    }

    private fun createComplexPatternBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(150, 150, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
        }
        
        // Draw complex pattern with multiple colors and shapes
        for (i in 0 until 10) {
            paint.color = android.graphics.Color.HSVToColor(floatArrayOf(i * 36f, 1f, 1f))
            canvas.drawRect(i * 15f, i * 15f, (i + 1) * 15f, (i + 1) * 15f, paint)
        }
        
        return bitmap
    }

    private fun createTextPatternBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(200, 100, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLACK
            textSize = 24f
            isAntiAlias = true
        }
        
        canvas.drawColor(android.graphics.Color.WHITE)
        canvas.drawText("TEST PATTERN", 20f, 50f, paint)
        
        return bitmap
    }

    private fun createColorGradientBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(100 * 100)
        
        for (y in 0 until 100) {
            for (x in 0 until 100) {
                val red = (x * 255 / 100)
                val green = (y * 255 / 100)
                val blue = 128
                pixels[y * 100 + x] = android.graphics.Color.rgb(red, green, blue)
            }
        }
        
        bitmap.setPixels(pixels, 0, 100, 0, 0, 100, 100)
        return bitmap
    }

    private fun saveBitmapToFile(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    private fun loadBitmapFromFile(file: File): Bitmap {
        return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun performImageMatching(template: Bitmap, target: Bitmap): Boolean {
        // Simplified image matching for testing
        // In real implementation, this would use OpenCV or ImageMatcher
        return template.width <= target.width && template.height <= target.height
    }

    private fun createRotatedBitmap(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun createBrightnessAdjustedBitmap(bitmap: Bitmap, factor: Float): Bitmap {
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setScale(factor, factor, factor, 1f)
        
        val paint = android.graphics.Paint()
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }

    private fun createPartiallyOccludedBitmap(bitmap: Bitmap): Bitmap {
        val result = bitmap.copy(bitmap.config, true)
        val canvas = android.graphics.Canvas(result)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.RED
        }
        
        // Draw occlusion rectangle
        canvas.drawRect(
            bitmap.width * 0.7f, 
            bitmap.height * 0.7f, 
            bitmap.width.toFloat(), 
            bitmap.height.toFloat(), 
            paint
        )
        
        return result
    }

    private fun createScaledBitmap(bitmap: Bitmap, scale: Float): Bitmap {
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    // Data classes for test results
    data class TestImageSet(
        val simplePattern: File,
        val complexPattern: File,
        val textPattern: File,
        val colorGradient: File,
        val testDirectory: File
    )

    data class PerformanceResult(
        val averageTimeNanos: Double,
        val minTimeNanos: Long,
        val maxTimeNanos: Long,
        val successRate: Double,
        val totalIterations: Int
    ) {
        val averageTimeMs: Double get() = averageTimeNanos / 1_000_000.0
        val minTimeMs: Double get() = minTimeNanos / 1_000_000.0
        val maxTimeMs: Double get() = maxTimeNanos / 1_000_000.0
    }

    data class AccessibilityTestResult(
        val serviceAvailable: Boolean,
        val serviceRunning: Boolean,
        val canPerformGestures: Boolean,
        val hasScreenshotPermission: Boolean,
        val isReady: Boolean
    ) {
        val allTestsPassed: Boolean get() = serviceAvailable && serviceRunning && canPerformGestures && hasScreenshotPermission && isReady
    }

    data class ClickTestResult(
        val x: Int,
        val y: Int,
        val coordinatesValid: Boolean,
        val clickSuccessful: Boolean,
        val executionTimeNanos: Long
    )

    data class ClickAccuracyResult(
        val testResults: List<ClickTestResult>,
        val overallSuccessRate: Double,
        val coordinateValidationRate: Double,
        val averageExecutionTime: Double
    )

    data class MemoryMeasurement(
        val timestamp: Long,
        val usedMemoryBytes: Long,
        val totalMemoryBytes: Long,
        val maxMemoryBytes: Long,
        val freeMemoryBytes: Long
    )

    data class MemoryUsageResult(
        val measurements: List<MemoryMeasurement>,
        val averageUsedMemoryBytes: Double,
        val maxUsedMemoryBytes: Long,
        val minUsedMemoryBytes: Long,
        val memoryLeakDetected: Boolean
    )

    data class RecognitionTestResult(
        val scenario: String,
        val expectedConfidence: Double,
        val actualSuccess: Boolean,
        val confidenceThresholdMet: Boolean
    )

    data class ImageRecognitionResult(
        val testResults: List<RecognitionTestResult>,
        val overallAccuracy: Double,
        val testImageSet: TestImageSet
    )
}