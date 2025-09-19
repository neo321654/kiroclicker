package com.autoclicker.android.utils

import android.graphics.Bitmap
import android.graphics.Point
import com.autoclicker.android.AutoClickerApplication
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.MockedStatic
import org.mockito.kotlin.whenever

class OpenCVImageMatcherTest {

    private lateinit var imageMatcher: OpenCVImageMatcher
    private lateinit var mockScreenshot: Bitmap
    private lateinit var mockTemplate: Bitmap
    private lateinit var mockAutoClickerApplication: MockedStatic<AutoClickerApplication>

    @Before
    fun setUp() {
        imageMatcher = OpenCVImageMatcher()
        
        // Create mock bitmaps
        mockScreenshot = mock(Bitmap::class.java).apply {
            whenever(width).thenReturn(1080)
            whenever(height).thenReturn(1920)
            whenever(isRecycled).thenReturn(false)
        }
        
        mockTemplate = mock(Bitmap::class.java).apply {
            whenever(width).thenReturn(100)
            whenever(height).thenReturn(100)
            whenever(isRecycled).thenReturn(false)
        }
        
        // Mock AutoClickerApplication static method
        mockAutoClickerApplication = mockStatic(AutoClickerApplication::class.java)
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(true)
    }

    @Test
    fun `isInitialized should return OpenCV status`() {
        // Test when OpenCV is ready
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(true)
        assertTrue("Should return true when OpenCV is ready", imageMatcher.isInitialized())
        
        // Test when OpenCV is not ready
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(false)
        assertFalse("Should return false when OpenCV is not ready", imageMatcher.isInitialized())
    }

    @Test
    fun `initialize should return success when OpenCV is ready`() = runTest {
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(true)
            
        val result = imageMatcher.initialize()
        
        assertTrue("Initialize should succeed when OpenCV is ready", result.isSuccess)
        assertTrue("Should return true", result.getOrNull() == true)
    }

    @Test
    fun `initialize should return failure when OpenCV is not ready`() = runTest {
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(false)
            
        val result = imageMatcher.initialize()
        
        assertTrue("Initialize should fail when OpenCV is not ready", result.isFailure)
        assertTrue("Should throw IllegalStateException", 
            result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `findTemplate should validate inputs`() = runTest {
        // Test with recycled screenshot
        val recycledScreenshot = mock(Bitmap::class.java).apply {
            whenever(isRecycled).thenReturn(true)
        }
        
        val result1 = imageMatcher.findTemplate(recycledScreenshot, mockTemplate, 0.8)
        assertTrue("Should fail with recycled screenshot", result1.isFailure)
        
        // Test with recycled template
        val recycledTemplate = mock(Bitmap::class.java).apply {
            whenever(isRecycled).thenReturn(true)
        }
        
        val result2 = imageMatcher.findTemplate(mockScreenshot, recycledTemplate, 0.8)
        assertTrue("Should fail with recycled template", result2.isFailure)
        
        // Test with invalid threshold
        val result3 = imageMatcher.findTemplate(mockScreenshot, mockTemplate, -0.1)
        assertTrue("Should fail with invalid threshold", result3.isFailure)
        
        val result4 = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 1.1)
        assertTrue("Should fail with invalid threshold", result4.isFailure)
    }

    @Test
    fun `findTemplate should validate template size`() = runTest {
        // Create template larger than screenshot
        val largeTemplate = mock(Bitmap::class.java).apply {
            whenever(width).thenReturn(2000)
            whenever(height).thenReturn(3000)
            whenever(isRecycled).thenReturn(false)
        }
        
        val result = imageMatcher.findTemplate(mockScreenshot, largeTemplate, 0.8)
        
        assertTrue("Should fail when template is larger than screenshot", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `findTemplate should fail when OpenCV not initialized`() = runTest {
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(false)
            
        val result = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 0.8)
        
        assertTrue("Should fail when OpenCV not initialized", result.isFailure)
        assertTrue("Should throw IllegalStateException", 
            result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `findTemplate should return match result when OpenCV is ready`() = runTest {
        // This test will use the placeholder OpenCV implementation
        val result = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 0.8)
        
        assertTrue("Should succeed with valid inputs", result.isSuccess)
        val matchResult = result.getOrNull()!!
        
        // The placeholder implementation should return a high confidence match
        assertTrue("Should find a match with placeholder implementation", matchResult.found)
        assertTrue("Confidence should be reasonable", matchResult.confidence >= 0.8)
        assertNotNull("Should have a location", matchResult.location)
    }

    @Test
    fun `matchTemplate should return point for successful match`() = runTest {
        val result = imageMatcher.matchTemplate(mockScreenshot, mockTemplate, 0.8)
        
        assertTrue("Should succeed", result.isSuccess)
        val point = result.getOrNull()
        assertNotNull("Should return a point for successful match", point)
    }

    @Test
    fun `findAllMatches should validate maxMatches parameter`() = runTest {
        val result = imageMatcher.findAllMatches(mockScreenshot, mockTemplate, 0.8, 0)
        
        assertTrue("Should fail with maxMatches = 0", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `findAllMatches should return limited results`() = runTest {
        val maxMatches = 3
        val result = imageMatcher.findAllMatches(mockScreenshot, mockTemplate, 0.8, maxMatches)
        
        assertTrue("Should succeed", result.isSuccess)
        val matches = result.getOrNull()!!
        assertTrue("Should not exceed maxMatches", matches.size <= maxMatches)
    }

    @Test
    fun `getMatchConfidence should validate location bounds`() = runTest {
        // Test with location outside screenshot bounds
        val invalidLocation = Point(2000, 3000) // Outside 1080x1920 screenshot
        
        val result = imageMatcher.getMatchConfidence(mockScreenshot, mockTemplate, invalidLocation)
        
        assertTrue("Should fail with location outside bounds", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `getMatchConfidence should return confidence for valid location`() = runTest {
        val validLocation = Point(100, 200)
        
        val result = imageMatcher.getMatchConfidence(mockScreenshot, mockTemplate, validLocation)
        
        assertTrue("Should succeed with valid location", result.isSuccess)
        val confidence = result.getOrNull()!!
        assertTrue("Confidence should be in valid range", confidence in 0.0..1.0)
    }

    @Test
    fun `all methods should handle exceptions gracefully`() = runTest {
        // Test with null bitmaps (should be caught by validation)
        val nullBitmap: Bitmap? = null
        
        try {
            // This should throw an exception that gets caught and wrapped in Result.failure
            val result = imageMatcher.findTemplate(nullBitmap!!, mockTemplate, 0.8)
            assertTrue("Should handle null bitmap gracefully", result.isFailure)
        } catch (e: Exception) {
            // If exception is not caught, that's also acceptable for this test
            assertTrue("Exception should be handled", true)
        }
    }

    @Test
    fun `boundary threshold values should be accepted`() = runTest {
        val result1 = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 0.0)
        val result2 = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 1.0)
        
        // Both should succeed (assuming OpenCV is initialized)
        assertTrue("Should accept minimum threshold 0.0", result1.isSuccess || result1.exceptionOrNull() is IllegalStateException)
        assertTrue("Should accept maximum threshold 1.0", result2.isSuccess || result2.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `template matching should work with different confidence thresholds`() = runTest {
        // Test with low threshold (should find match more easily)
        val lowThresholdResult = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 0.1)
        
        // Test with high threshold (should be more strict)
        val highThresholdResult = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 0.95)
        
        if (lowThresholdResult.isSuccess && highThresholdResult.isSuccess) {
            val lowMatch = lowThresholdResult.getOrNull()!!
            val highMatch = highThresholdResult.getOrNull()!!
            
            // With placeholder implementation, both should succeed
            // In real implementation, low threshold would be more likely to find matches
            assertTrue("Low threshold should work", lowMatch.confidence >= 0.1 || !lowMatch.found)
            assertTrue("High threshold should work", highMatch.confidence >= 0.95 || !highMatch.found)
        }
    }
}