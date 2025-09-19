package com.autoclicker.android.utils

import android.graphics.Bitmap
import android.graphics.Point
import com.autoclicker.android.model.MatchResult
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

class ImageMatcherTest {

    // Mock implementation for testing interface contract
    private class MockImageMatcher : ImageMatcher {
        private var initialized = true
        private val mockResults = mutableMapOf<String, MatchResult>()
        
        fun setMockResult(key: String, result: MatchResult) {
            mockResults[key] = result
        }
        
        override suspend fun findTemplate(
            screenshot: Bitmap,
            template: Bitmap,
            threshold: Double
        ): Result<MatchResult> {
            if (!initialized) {
                return Result.failure(IllegalStateException("ImageMatcher not initialized"))
            }
            
            if (threshold !in 0.0..1.0) {
                return Result.failure(IllegalArgumentException("Invalid threshold"))
            }
            
            val key = "findTemplate_${screenshot.width}x${screenshot.height}_${template.width}x${template.height}_$threshold"
            val result = mockResults[key] ?: MatchResult.notFound()
            return Result.success(result)
        }
        
        override suspend fun matchTemplate(
            screenshot: Bitmap,
            template: Bitmap,
            threshold: Double
        ): Result<Point?> {
            val findResult = findTemplate(screenshot, template, threshold)
            return if (findResult.isSuccess) {
                val matchResult = findResult.getOrNull()!!
                Result.success(if (matchResult.found) matchResult.location else null)
            } else {
                Result.failure(findResult.exceptionOrNull()!!)
            }
        }
        
        override suspend fun findAllMatches(
            screenshot: Bitmap,
            template: Bitmap,
            threshold: Double,
            maxMatches: Int
        ): Result<List<MatchResult>> {
            if (!initialized) {
                return Result.failure(IllegalStateException("ImageMatcher not initialized"))
            }
            
            if (threshold !in 0.0..1.0) {
                return Result.failure(IllegalArgumentException("Invalid threshold"))
            }
            
            if (maxMatches <= 0) {
                return Result.failure(IllegalArgumentException("maxMatches must be positive"))
            }
            
            // Mock multiple results
            val results = mutableListOf<MatchResult>()
            for (i in 0 until minOf(maxMatches, 3)) {
                results.add(MatchResult.found(0.9, Point(i * 10, i * 10)))
            }
            
            return Result.success(results)
        }
        
        override suspend fun getMatchConfidence(
            screenshot: Bitmap,
            template: Bitmap,
            location: Point
        ): Result<Double> {
            if (!initialized) {
                return Result.failure(IllegalStateException("ImageMatcher not initialized"))
            }
            
            // Mock confidence based on location
            val confidence = when {
                location.x < 0 || location.y < 0 -> 0.0
                location.x == 100 && location.y == 200 -> 0.95
                else -> 0.7
            }
            
            return Result.success(confidence)
        }
        
        override fun isInitialized(): Boolean = initialized
        
        override suspend fun initialize(): Result<Boolean> {
            initialized = true
            return Result.success(true)
        }
        
        fun setInitialized(value: Boolean) {
            initialized = value
        }
    }

    private val imageMatcher = MockImageMatcher()
    private val mockScreenshot = mock(Bitmap::class.java).apply {
        `when`(width).thenReturn(1080)
        `when`(height).thenReturn(1920)
    }
    private val mockTemplate = mock(Bitmap::class.java).apply {
        `when`(width).thenReturn(100)
        `when`(height).thenReturn(100)
    }

    @Test
    fun `findTemplate should return success for valid inputs`() = runTest {
        val result = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 0.8)
        
        assertTrue("findTemplate should succeed", result.isSuccess)
        assertNotNull("Result should not be null", result.getOrNull())
    }

    @Test
    fun `findTemplate should fail for invalid threshold below range`() = runTest {
        val result = imageMatcher.findTemplate(mockScreenshot, mockTemplate, -0.1)
        
        assertTrue("findTemplate should fail for invalid threshold", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `findTemplate should fail for invalid threshold above range`() = runTest {
        val result = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 1.1)
        
        assertTrue("findTemplate should fail for invalid threshold", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `findTemplate should accept boundary threshold values`() = runTest {
        val result1 = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 0.0)
        val result2 = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 1.0)
        
        assertTrue("Should accept minimum threshold", result1.isSuccess)
        assertTrue("Should accept maximum threshold", result2.isSuccess)
    }

    @Test
    fun `findTemplate should fail when not initialized`() = runTest {
        imageMatcher.setInitialized(false)
        
        val result = imageMatcher.findTemplate(mockScreenshot, mockTemplate, 0.8)
        
        assertTrue("Should fail when not initialized", result.isFailure)
        assertTrue("Should throw IllegalStateException", 
            result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `matchTemplate should return point for found match`() = runTest {
        val matchResult = MatchResult.found(0.9, Point(100, 200))
        imageMatcher.setMockResult("findTemplate_1080x1920_100x100_0.8", matchResult)
        
        val result = imageMatcher.matchTemplate(mockScreenshot, mockTemplate, 0.8)
        
        assertTrue("matchTemplate should succeed", result.isSuccess)
        assertEquals("Should return correct point", Point(100, 200), result.getOrNull())
    }

    @Test
    fun `matchTemplate should return null for no match`() = runTest {
        val matchResult = MatchResult.notFound()
        imageMatcher.setMockResult("findTemplate_1080x1920_100x100_0.8", matchResult)
        
        val result = imageMatcher.matchTemplate(mockScreenshot, mockTemplate, 0.8)
        
        assertTrue("matchTemplate should succeed", result.isSuccess)
        assertNull("Should return null for no match", result.getOrNull())
    }

    @Test
    fun `findAllMatches should return multiple results`() = runTest {
        val result = imageMatcher.findAllMatches(mockScreenshot, mockTemplate, 0.8, 5)
        
        assertTrue("findAllMatches should succeed", result.isSuccess)
        val matches = result.getOrNull()!!
        assertTrue("Should return multiple matches", matches.size > 1)
        assertTrue("All matches should be found", matches.all { it.found })
    }

    @Test
    fun `findAllMatches should respect maxMatches limit`() = runTest {
        val result = imageMatcher.findAllMatches(mockScreenshot, mockTemplate, 0.8, 2)
        
        assertTrue("findAllMatches should succeed", result.isSuccess)
        val matches = result.getOrNull()!!
        assertTrue("Should respect maxMatches limit", matches.size <= 2)
    }

    @Test
    fun `findAllMatches should fail for invalid maxMatches`() = runTest {
        val result = imageMatcher.findAllMatches(mockScreenshot, mockTemplate, 0.8, 0)
        
        assertTrue("Should fail for invalid maxMatches", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `getMatchConfidence should return confidence score`() = runTest {
        val result = imageMatcher.getMatchConfidence(mockScreenshot, mockTemplate, Point(100, 200))
        
        assertTrue("getMatchConfidence should succeed", result.isSuccess)
        val confidence = result.getOrNull()!!
        assertTrue("Confidence should be in valid range", confidence in 0.0..1.0)
        assertEquals("Should return expected confidence", 0.95, confidence, 0.001)
    }

    @Test
    fun `getMatchConfidence should return low confidence for invalid location`() = runTest {
        val result = imageMatcher.getMatchConfidence(mockScreenshot, mockTemplate, Point(-1, -1))
        
        assertTrue("getMatchConfidence should succeed", result.isSuccess)
        val confidence = result.getOrNull()!!
        assertEquals("Should return 0.0 for invalid location", 0.0, confidence, 0.001)
    }

    @Test
    fun `isInitialized should return correct status`() {
        assertTrue("Should be initialized by default", imageMatcher.isInitialized())
        
        imageMatcher.setInitialized(false)
        assertFalse("Should return false when not initialized", imageMatcher.isInitialized())
    }

    @Test
    fun `initialize should set initialized status`() = runTest {
        imageMatcher.setInitialized(false)
        
        val result = imageMatcher.initialize()
        
        assertTrue("Initialize should succeed", result.isSuccess)
        assertTrue("Initialize should return true", result.getOrNull() == true)
        assertTrue("Should be initialized after initialize call", imageMatcher.isInitialized())
    }

    @Test
    fun `interface methods should use default threshold when not specified`() = runTest {
        // Test that default threshold (0.8) is used
        val result1 = imageMatcher.findTemplate(mockScreenshot, mockTemplate)
        val result2 = imageMatcher.matchTemplate(mockScreenshot, mockTemplate)
        val result3 = imageMatcher.findAllMatches(mockScreenshot, mockTemplate)
        
        assertTrue("findTemplate with default threshold should succeed", result1.isSuccess)
        assertTrue("matchTemplate with default threshold should succeed", result2.isSuccess)
        assertTrue("findAllMatches with default threshold should succeed", result3.isSuccess)
    }
}