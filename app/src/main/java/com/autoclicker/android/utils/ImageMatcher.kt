package com.autoclicker.android.utils

import android.graphics.Bitmap
import android.graphics.Point
import com.autoclicker.android.model.MatchResult

/**
 * Interface for image matching operations using template matching
 * Provides methods to find and match template images within screenshots
 */
interface ImageMatcher {
    
    /**
     * Find a template image within a screenshot
     * @param screenshot The screenshot bitmap to search in
     * @param template The template bitmap to find
     * @param threshold The confidence threshold for matching (0.0 to 1.0)
     * @return MatchResult containing match information
     * @throws IllegalArgumentException if bitmaps are null or threshold is invalid
     */
    suspend fun findTemplate(
        screenshot: Bitmap, 
        template: Bitmap, 
        threshold: Double = 0.8
    ): Result<MatchResult>
    
    /**
     * Perform template matching and return the best match location
     * @param screenshot The screenshot bitmap to search in
     * @param template The template bitmap to find
     * @param threshold The confidence threshold for matching
     * @return Point of the best match location, null if no match found
     */
    suspend fun matchTemplate(
        screenshot: Bitmap, 
        template: Bitmap, 
        threshold: Double = 0.8
    ): Result<Point?>
    
    /**
     * Find all matches of a template within a screenshot
     * @param screenshot The screenshot bitmap to search in
     * @param template The template bitmap to find
     * @param threshold The confidence threshold for matching
     * @param maxMatches Maximum number of matches to return
     * @return List of MatchResult for all found matches
     */
    suspend fun findAllMatches(
        screenshot: Bitmap,
        template: Bitmap,
        threshold: Double = 0.8,
        maxMatches: Int = 10
    ): Result<List<MatchResult>>
    
    /**
     * Get the confidence score for a template match at a specific location
     * @param screenshot The screenshot bitmap
     * @param template The template bitmap
     * @param location The location to check
     * @return Confidence score between 0.0 and 1.0
     */
    suspend fun getMatchConfidence(
        screenshot: Bitmap,
        template: Bitmap,
        location: Point
    ): Result<Double>
    
    /**
     * Check if OpenCV is properly initialized
     * @return true if OpenCV is ready for use
     */
    fun isInitialized(): Boolean
    
    /**
     * Initialize OpenCV if not already initialized
     * @return true if initialization was successful
     */
    suspend fun initialize(): Result<Boolean>
}