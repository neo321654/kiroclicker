package com.autoclicker.android.utils

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import org.mockito.Mockito.*
import org.mockito.kotlin.whenever

/**
 * Utility class for creating test images and bitmaps for unit testing
 */
object TestImageUtils {
    
    /**
     * Create a mock bitmap with specified dimensions
     */
    fun createMockBitmap(width: Int, height: Int, isRecycled: Boolean = false): Bitmap {
        return mock(Bitmap::class.java).apply {
            whenever(getWidth()).thenReturn(width)
            whenever(getHeight()).thenReturn(height)
            whenever(this.width).thenReturn(width)
            whenever(this.height).thenReturn(height)
            whenever(this.isRecycled).thenReturn(isRecycled)
            whenever(config).thenReturn(Bitmap.Config.ARGB_8888)
        }
    }
    
    /**
     * Create a simple test bitmap with a colored rectangle
     * This would be used in integration tests with real Android framework
     */
    fun createTestBitmapWithRectangle(
        width: Int, 
        height: Int, 
        rectX: Int, 
        rectY: Int, 
        rectWidth: Int, 
        rectHeight: Int,
        backgroundColor: Int = Color.WHITE,
        rectangleColor: Int = Color.RED
    ): Bitmap {
        // In unit tests, we can't create real bitmaps, so return a mock
        // In instrumented tests, this would create an actual bitmap
        return createMockBitmap(width, height).apply {
            // Add metadata about the rectangle for test verification
            whenever(getPixel(rectX + rectWidth/2, rectY + rectHeight/2)).thenReturn(rectangleColor)
            whenever(getPixel(0, 0)).thenReturn(backgroundColor)
        }
    }
    
    /**
     * Create a template bitmap that should match a specific area
     */
    fun createMatchingTemplate(
        templateWidth: Int,
        templateHeight: Int,
        color: Int = Color.RED
    ): Bitmap {
        return createMockBitmap(templateWidth, templateHeight).apply {
            whenever(getPixel(templateWidth/2, templateHeight/2)).thenReturn(color)
        }
    }
    
    /**
     * Create a template bitmap that should NOT match
     */
    fun createNonMatchingTemplate(
        templateWidth: Int,
        templateHeight: Int,
        color: Int = Color.BLUE
    ): Bitmap {
        return createMockBitmap(templateWidth, templateHeight).apply {
            whenever(getPixel(templateWidth/2, templateHeight/2)).thenReturn(color)
        }
    }
    
    /**
     * Validate bitmap dimensions for testing
     */
    fun validateBitmapDimensions(bitmap: Bitmap, expectedWidth: Int, expectedHeight: Int): Boolean {
        return bitmap.width == expectedWidth && bitmap.height == expectedHeight
    }
    
    /**
     * Create a screenshot bitmap with multiple potential match areas
     */
    fun createScreenshotWithMultipleTargets(
        width: Int = 1080,
        height: Int = 1920,
        targetColor: Int = Color.RED,
        backgroundColor: Int = Color.WHITE
    ): Bitmap {
        return createMockBitmap(width, height).apply {
            // Mock multiple red rectangles at different locations
            whenever(getPixel(100, 200)).thenReturn(targetColor) // First target
            whenever(getPixel(300, 400)).thenReturn(targetColor) // Second target
            whenever(getPixel(500, 600)).thenReturn(targetColor) // Third target
            
            // Mock background pixels
            whenever(getPixel(0, 0)).thenReturn(backgroundColor)
            whenever(getPixel(width-1, height-1)).thenReturn(backgroundColor)
        }
    }
    
    /**
     * Create test data for confidence testing
     */
    data class TestImagePair(
        val screenshot: Bitmap,
        val template: Bitmap,
        val expectedMatch: Boolean,
        val expectedConfidence: Double,
        val description: String
    )
    
    /**
     * Generate test image pairs for various scenarios
     */
    fun generateTestImagePairs(): List<TestImagePair> {
        return listOf(
            TestImagePair(
                screenshot = createScreenshotWithMultipleTargets(),
                template = createMatchingTemplate(50, 50, Color.RED),
                expectedMatch = true,
                expectedConfidence = 0.85,
                description = "Matching red template in screenshot with red targets"
            ),
            TestImagePair(
                screenshot = createScreenshotWithMultipleTargets(),
                template = createNonMatchingTemplate(50, 50, Color.BLUE),
                expectedMatch = false,
                expectedConfidence = 0.3,
                description = "Non-matching blue template in screenshot with red targets"
            ),
            TestImagePair(
                screenshot = createMockBitmap(800, 600),
                template = createMockBitmap(100, 100),
                expectedMatch = false,
                expectedConfidence = 0.5,
                description = "Generic template on generic screenshot"
            )
        )
    }
}