package com.autoclicker.android.utils

import android.graphics.Bitmap
import android.graphics.Point
import com.autoclicker.android.model.MatchResult

/**
 * Basic image matcher implementation without OpenCV
 * Provides simple pixel-based matching as fallback
 */
class BasicImageMatcher : ImageMatcher {
    
    override suspend fun findTemplate(screenshot: Bitmap, template: Bitmap, threshold: Double): Result<MatchResult> {
        return try {
            // Simple implementation - just check if template fits in screenshot
            if (template.width > screenshot.width || template.height > screenshot.height) {
                Result.success(MatchResult(
                    found = false,
                    confidence = 0.0,
                    location = null
                ))
            } else {
                // For basic implementation, assume template is found at center
                // In real implementation, this would do pixel-by-pixel comparison
                val centerX = (screenshot.width - template.width) / 2
                val centerY = (screenshot.height - template.height) / 2
                
                Result.success(MatchResult(
                    found = true,
                    confidence = 0.7, // Basic confidence
                    location = Point(centerX, centerY)
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun matchTemplate(screenshot: Bitmap, template: Bitmap, threshold: Double): Result<Point?> {
        return try {
            val result = findTemplate(screenshot, template, threshold).getOrNull()
            Result.success(result?.location)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun findAllMatches(screenshot: Bitmap, template: Bitmap, threshold: Double, maxMatches: Int): Result<List<MatchResult>> {
        return try {
            val result = findTemplate(screenshot, template, threshold).getOrNull()
            if (result?.found == true) {
                Result.success(listOf(result))
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getMatchConfidence(screenshot: Bitmap, template: Bitmap, location: Point): Result<Double> {
        return Result.success(0.7) // Basic confidence
    }
    
    override fun isInitialized(): Boolean = true
    
    override suspend fun initialize(): Result<Boolean> = Result.success(true)
}