package com.autoclicker.android.device

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoclicker.android.utils.ImageMatcher
import com.autoclicker.android.utils.ImageMatcherFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Device tests for image recognition accuracy and performance.
 * These tests verify that image matching works correctly on real devices
 * with various image conditions and performance requirements.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ImageRecognitionDeviceTest {

    private lateinit var imageMatcher: ImageMatcher
    private lateinit var testImages: DeviceTestUtils.TestImageSet

    @Before
    fun setup() {
        imageMatcher = ImageMatcherFactory.createImageMatcher()
        testImages = DeviceTestUtils.createTestImages()
        
        println("=== Image Recognition Device Test Setup ===")
        println("Test images created in: ${testImages.testDirectory.absolutePath}")
    }

    @After
    fun tearDown() {
        // Clean up test images
        testImages.testDirectory.deleteRecursively()
    }

    @Test
    fun testImageMatchingAccuracy() = runTest {
        println("=== Image Matching Accuracy Test ===")
        
        val result = DeviceTestUtils.testImageRecognitionAccuracy()
        
        println("Overall Accuracy: ${result.overallAccuracy * 100}%")
        
        result.testResults.forEach { testResult ->
            println("Scenario: ${testResult.scenario}")
            println("  Expected Confidence: ${testResult.expectedConfidence}")
            println("  Actual Success: ${testResult.actualSuccess}")
            println("  Threshold Met: ${testResult.confidenceThresholdMet}")
        }
        
        // Verify minimum accuracy requirements
        assertTrue("Overall accuracy should be at least 60%", result.overallAccuracy >= 0.6)
        
        // Exact match should always work
        val exactMatchResult = result.testResults.find { it.scenario == "exact_match" }
        assertNotNull("Exact match test should be present", exactMatchResult)
        assertTrue("Exact match should succeed", exactMatchResult!!.actualSuccess)
    }

    @Test
    fun testImageMatchingPerformance() = runTest {
        println("=== Image Matching Performance Test ===")
        
        // Load test bitmaps
        val templateBitmap = loadBitmapFromFile(testImages.simplePattern)
        val targetBitmap = createLargerBitmapWithTemplate(templateBitmap)
        
        // Measure performance
        val performanceResult = DeviceTestUtils.measureImageMatchingPerformance(
            templateBitmap, targetBitmap, iterations = 20
        )
        
        println("Performance Results:")
        println("  Average Time: ${performanceResult.averageTimeMs} ms")
        println("  Min Time: ${performanceResult.minTimeMs} ms")
        println("  Max Time: ${performanceResult.maxTimeMs} ms")
        println("  Success Rate: ${performanceResult.successRate * 100}%")
        println("  Total Iterations: ${performanceResult.totalIterations}")
        
        // Performance assertions
        assertTrue("Success rate should be at least 80%", performanceResult.successRate >= 0.8)
        assertTrue("Average time should be under 500ms", performanceResult.averageTimeMs < 500.0)
        assertTrue("Max time should be under 1000ms", performanceResult.maxTimeMs < 1000.0)
    }

    @Test
    fun testDifferentImageSizes() = runTest {
        println("=== Different Image Sizes Test ===")
        
        val imageSizes = listOf(
            Pair(50, 50),     // Small
            Pair(100, 100),   // Medium
            Pair(200, 200),   // Large
            Pair(300, 150),   // Wide
            Pair(150, 300)    // Tall
        )
        
        imageSizes.forEach { (width, height) ->
            println("Testing image size: ${width}x${height}")
            
            val templateBitmap = createTestBitmap(width, height)
            val targetBitmap = createLargerBitmapWithTemplate(templateBitmap)
            
            val startTime = System.nanoTime()
            val matchResult = imageMatcher.findTemplate(targetBitmap, templateBitmap)
            val endTime = System.nanoTime()
            
            val executionTime = (endTime - startTime) / 1_000_000.0
            
            println("  Result: Found=${matchResult.found}, Confidence=${matchResult.confidence}")
            println("  Execution Time: ${executionTime} ms")
            
            assertNotNull("Match result should not be null", matchResult)
            assertTrue("Execution time should be reasonable", executionTime < 1000.0)
            
            if (matchResult.found) {
                assertNotNull("Location should be provided when found", matchResult.location)
                assertTrue("Confidence should be reasonable", matchResult.confidence > 0.0)
            }
        }
    }

    @Test
    fun testImageMatchingWithNoise() = runTest {
        println("=== Image Matching with Noise Test ===")
        
        val originalBitmap = loadBitmapFromFile(testImages.simplePattern)
        
        // Test with different noise levels
        val noiseLevels = listOf(0.1f, 0.2f, 0.3f, 0.4f, 0.5f)
        
        noiseLevels.forEach { noiseLevel ->
            println("Testing with noise level: $noiseLevel")
            
            val noisyBitmap = addNoiseToImage(originalBitmap, noiseLevel)
            val targetBitmap = createLargerBitmapWithTemplate(noisyBitmap)
            
            val matchResult = imageMatcher.findTemplate(targetBitmap, originalBitmap)
            
            println("  Found: ${matchResult.found}")
            println("  Confidence: ${matchResult.confidence}")
            
            // Lower noise levels should still allow matching
            if (noiseLevel <= 0.3f) {
                assertTrue("Should find match with low noise ($noiseLevel)", 
                    matchResult.confidence > 0.3)
            }
        }
    }

    @Test
    fun testImageMatchingWithRotation() = runTest {
        println("=== Image Matching with Rotation Test ===")
        
        val originalBitmap = loadBitmapFromFile(testImages.complexPattern)
        
        // Test with different rotation angles
        val rotationAngles = listOf(0f, 5f, 10f, 15f, 30f, 45f, 90f)
        
        rotationAngles.forEach { angle ->
            println("Testing with rotation angle: ${angle}°")
            
            val rotatedBitmap = rotateImage(originalBitmap, angle)
            val targetBitmap = createLargerBitmapWithTemplate(rotatedBitmap)
            
            val matchResult = imageMatcher.findTemplate(targetBitmap, originalBitmap)
            
            println("  Found: ${matchResult.found}")
            println("  Confidence: ${matchResult.confidence}")
            
            // Small rotations should still be detectable
            if (angle <= 10f) {
                assertTrue("Should handle small rotations ($angle°)", 
                    matchResult.confidence > 0.2)
            }
        }
    }

    @Test
    fun testImageMatchingWithScaling() = runTest {
        println("=== Image Matching with Scaling Test ===")
        
        val originalBitmap = loadBitmapFromFile(testImages.textPattern)
        
        // Test with different scale factors
        val scaleFactors = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        
        scaleFactors.forEach { scale ->
            println("Testing with scale factor: $scale")
            
            val scaledBitmap = scaleImage(originalBitmap, scale)
            val targetBitmap = createLargerBitmapWithTemplate(scaledBitmap)
            
            val matchResult = imageMatcher.findTemplate(targetBitmap, originalBitmap)
            
            println("  Found: ${matchResult.found}")
            println("  Confidence: ${matchResult.confidence}")
            
            // Exact scale should work perfectly
            if (scale == 1.0f) {
                assertTrue("Exact scale should work", matchResult.found)
                assertTrue("Exact scale should have high confidence", 
                    matchResult.confidence > 0.8)
            }
        }
    }

    @Test
    fun testImageMatchingWithLightingChanges() = runTest {
        println("=== Image Matching with Lighting Changes Test ===")
        
        val originalBitmap = loadBitmapFromFile(testImages.colorGradient)
        
        // Test with different brightness levels
        val brightnessFactors = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
        
        brightnessFactors.forEach { brightness ->
            println("Testing with brightness factor: $brightness")
            
            val adjustedBitmap = adjustBrightness(originalBitmap, brightness)
            val targetBitmap = createLargerBitmapWithTemplate(adjustedBitmap)
            
            val matchResult = imageMatcher.findTemplate(targetBitmap, originalBitmap)
            
            println("  Found: ${matchResult.found}")
            println("  Confidence: ${matchResult.confidence}")
            
            // Normal brightness should work well
            if (brightness == 1.0f) {
                assertTrue("Normal brightness should work", matchResult.found)
            }
            
            // Moderate changes should still be detectable
            if (brightness in 0.75f..1.25f) {
                assertTrue("Moderate brightness changes should be handled", 
                    matchResult.confidence > 0.3)
            }
        }
    }

    @Test
    fun testImageMatchingStressTest() = runTest {
        println("=== Image Matching Stress Test ===")
        
        val templateBitmap = loadBitmapFromFile(testImages.simplePattern)
        val targetBitmap = createLargerBitmapWithTemplate(templateBitmap)
        
        val iterations = 100
        val results = mutableListOf<Boolean>()
        val times = mutableListOf<Long>()
        
        repeat(iterations) { i ->
            val startTime = System.nanoTime()
            val matchResult = imageMatcher.findTemplate(targetBitmap, templateBitmap)
            val endTime = System.nanoTime()
            
            results.add(matchResult.found)
            times.add(endTime - startTime)
            
            if ((i + 1) % 20 == 0) {
                println("Completed ${i + 1}/$iterations iterations")
            }
        }
        
        val successRate = results.count { it }.toDouble() / iterations
        val averageTime = times.average() / 1_000_000.0 // Convert to ms
        val maxTime = (times.maxOrNull() ?: 0L) / 1_000_000.0
        val minTime = (times.minOrNull() ?: 0L) / 1_000_000.0
        
        println("Stress Test Results:")
        println("  Success Rate: ${successRate * 100}%")
        println("  Average Time: $averageTime ms")
        println("  Min Time: $minTime ms")
        println("  Max Time: $maxTime ms")
        
        // Stress test assertions
        assertTrue("Success rate should be at least 95%", successRate >= 0.95)
        assertTrue("Average time should be consistent", averageTime < 200.0)
        assertTrue("Max time should not exceed 1 second", maxTime < 1000.0)
    }

    @Test
    fun testMemoryUsageDuringImageMatching() = runTest {
        println("=== Memory Usage During Image Matching Test ===")
        
        val templateBitmap = loadBitmapFromFile(testImages.complexPattern)
        val targetBitmap = createLargerBitmapWithTemplate(templateBitmap)
        
        // Monitor memory usage during intensive image matching
        val memoryResult = DeviceTestUtils.monitorMemoryUsage(10000) // 10 seconds
        
        // Perform image matching operations during monitoring
        Thread {
            repeat(50) {
                imageMatcher.findTemplate(targetBitmap, templateBitmap)
                Thread.sleep(100)
            }
        }.start()
        
        Thread.sleep(10000) // Wait for monitoring to complete
        
        println("Memory Usage Results:")
        println("  Average Used Memory: ${memoryResult.averageUsedMemoryBytes / 1024 / 1024} MB")
        println("  Max Used Memory: ${memoryResult.maxUsedMemoryBytes / 1024 / 1024} MB")
        println("  Min Used Memory: ${memoryResult.minUsedMemoryBytes / 1024 / 1024} MB")
        println("  Memory Leak Detected: ${memoryResult.memoryLeakDetected}")
        
        // Memory assertions
        assertFalse("No memory leak should be detected", memoryResult.memoryLeakDetected)
        assertTrue("Memory usage should be reasonable", 
            memoryResult.maxUsedMemoryBytes < 100 * 1024 * 1024) // Less than 100MB
    }

    // Helper methods
    private fun loadBitmapFromFile(file: java.io.File): Bitmap {
        return android.graphics.BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint().apply {
            color = android.graphics.Color.BLUE
            style = android.graphics.Paint.Style.FILL
        }
        
        // Draw a simple pattern
        canvas.drawRect(width * 0.2f, height * 0.2f, width * 0.8f, height * 0.8f, paint)
        paint.color = android.graphics.Color.WHITE
        canvas.drawCircle(width * 0.5f, height * 0.5f, width * 0.2f, paint)
        
        return bitmap
    }

    private fun createLargerBitmapWithTemplate(template: Bitmap): Bitmap {
        val largeBitmap = Bitmap.createBitmap(
            template.width * 3, 
            template.height * 3, 
            Bitmap.Config.ARGB_8888
        )
        largeBitmap.eraseColor(android.graphics.Color.GRAY)
        
        val canvas = android.graphics.Canvas(largeBitmap)
        canvas.drawBitmap(template, template.width.toFloat(), template.height.toFloat(), null)
        
        return largeBitmap
    }

    private fun addNoiseToImage(bitmap: Bitmap, noiseLevel: Float): Bitmap {
        val noisyBitmap = bitmap.copy(bitmap.config, true)
        val pixels = IntArray(bitmap.width * bitmap.height)
        noisyBitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        
        val random = java.util.Random()
        
        for (i in pixels.indices) {
            if (random.nextFloat() < noiseLevel) {
                // Add random noise to this pixel
                val originalColor = pixels[i]
                val r = ((originalColor shr 16) and 0xFF)
                val g = ((originalColor shr 8) and 0xFF)
                val b = (originalColor and 0xFF)
                val a = ((originalColor shr 24) and 0xFF)
                
                val noiseR = (r + random.nextInt(51) - 25).coerceIn(0, 255)
                val noiseG = (g + random.nextInt(51) - 25).coerceIn(0, 255)
                val noiseB = (b + random.nextInt(51) - 25).coerceIn(0, 255)
                
                pixels[i] = (a shl 24) or (noiseR shl 16) or (noiseG shl 8) or noiseB
            }
        }
        
        noisyBitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return noisyBitmap
    }

    private fun rotateImage(bitmap: Bitmap, degrees: Float): Bitmap {
        val matrix = android.graphics.Matrix()
        matrix.postRotate(degrees)
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun scaleImage(bitmap: Bitmap, scale: Float): Bitmap {
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    private fun adjustBrightness(bitmap: Bitmap, factor: Float): Bitmap {
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setScale(factor, factor, factor, 1f)
        
        val paint = android.graphics.Paint()
        paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        
        val result = Bitmap.createBitmap(bitmap.width, bitmap.height, bitmap.config)
        val canvas = android.graphics.Canvas(result)
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        
        return result
    }
}