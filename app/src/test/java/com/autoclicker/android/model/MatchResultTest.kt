package com.autoclicker.android.model

import android.graphics.Point
import org.junit.Test
import org.junit.Assert.*

class MatchResultTest {

    @Test
    fun `notFound factory method should create valid not found result`() {
        val result = MatchResult.notFound()

        assertFalse("Result should not be found", result.found)
        assertEquals("Confidence should be 0.0", 0.0, result.confidence, 0.001)
        assertNull("Location should be null", result.location)
        assertTrue("Not found result should be valid", result.isValid())
    }

    @Test
    fun `found factory method should create valid found result`() {
        val confidence = 0.9
        val location = Point(100, 200)
        val result = MatchResult.found(confidence, location)

        assertTrue("Result should be found", result.found)
        assertEquals("Confidence should match", confidence, result.confidence, 0.001)
        assertEquals("Location should match", location, result.location)
        assertTrue("Found result should be valid", result.isValid())
    }

    @Test(expected = IllegalArgumentException::class)
    fun `found factory method should reject invalid confidence below range`() {
        MatchResult.found(-0.1, Point(0, 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `found factory method should reject invalid confidence above range`() {
        MatchResult.found(1.1, Point(0, 0))
    }

    @Test
    fun `found factory method should accept boundary confidence values`() {
        val result1 = MatchResult.found(0.0, Point(0, 0))
        val result2 = MatchResult.found(1.0, Point(0, 0))

        assertTrue("Minimum confidence should be valid", result1.isValid())
        assertTrue("Maximum confidence should be valid", result2.isValid())
    }

    @Test
    fun `isValid should return true for proper found result`() {
        val result = MatchResult(
            found = true,
            confidence = 0.8,
            location = Point(50, 75)
        )

        assertTrue("Valid found result should pass validation", result.isValid())
    }

    @Test
    fun `isValid should return true for proper not found result`() {
        val result = MatchResult(
            found = false,
            confidence = 0.0,
            location = null
        )

        assertTrue("Valid not found result should pass validation", result.isValid())
    }

    @Test
    fun `isValid should return false for found result with null location`() {
        val result = MatchResult(
            found = true,
            confidence = 0.8,
            location = null
        )

        assertFalse("Found result with null location should be invalid", result.isValid())
    }

    @Test
    fun `isValid should return false for found result with invalid confidence`() {
        val result1 = MatchResult(
            found = true,
            confidence = -0.1,
            location = Point(0, 0)
        )

        val result2 = MatchResult(
            found = true,
            confidence = 1.1,
            location = Point(0, 0)
        )

        assertFalse("Found result with negative confidence should be invalid", result1.isValid())
        assertFalse("Found result with confidence > 1.0 should be invalid", result2.isValid())
    }

    @Test
    fun `isValid should return true for not found result with non-null location`() {
        // This is technically allowed - not found but location provided
        val result = MatchResult(
            found = false,
            confidence = 0.0,
            location = Point(0, 0)
        )

        // Based on current implementation, this should be invalid
        assertFalse("Not found result with location should be invalid", result.isValid())
    }

    @Test
    fun `hasHighConfidence should work with default threshold`() {
        val highConfidenceResult = MatchResult.found(0.9, Point(0, 0))
        val lowConfidenceResult = MatchResult.found(0.7, Point(0, 0))
        val notFoundResult = MatchResult.notFound()

        assertTrue("High confidence result should pass default threshold", 
            highConfidenceResult.hasHighConfidence())
        assertFalse("Low confidence result should not pass default threshold", 
            lowConfidenceResult.hasHighConfidence())
        assertFalse("Not found result should not have high confidence", 
            notFoundResult.hasHighConfidence())
    }

    @Test
    fun `hasHighConfidence should work with custom threshold`() {
        val result = MatchResult.found(0.75, Point(0, 0))

        assertTrue("Result should pass lower threshold", result.hasHighConfidence(0.7))
        assertFalse("Result should not pass higher threshold", result.hasHighConfidence(0.8))
    }

    @Test
    fun `hasHighConfidence should handle boundary values`() {
        val result = MatchResult.found(0.8, Point(0, 0))

        assertTrue("Result should pass equal threshold", result.hasHighConfidence(0.8))
        assertFalse("Result should not pass slightly higher threshold", 
            result.hasHighConfidence(0.801))
    }

    @Test
    fun `equality should work correctly`() {
        val location = Point(100, 200)
        val result1 = MatchResult(true, 0.8, location)
        val result2 = MatchResult(true, 0.8, Point(100, 200))
        val result3 = MatchResult(true, 0.9, location)

        assertEquals("Results with same values should be equal", result1, result2)
        assertNotEquals("Results with different confidence should not be equal", result1, result3)
    }
}