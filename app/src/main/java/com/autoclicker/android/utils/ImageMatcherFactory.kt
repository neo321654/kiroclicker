package com.autoclicker.android.utils

/**
 * Factory for creating ImageMatcher instances
 */
object ImageMatcherFactory {
    
    /**
     * Creates an ImageMatcher instance
     * Returns OpenCVImageMatcher if OpenCV is available, otherwise BasicImageMatcher
     */
    fun createImageMatcher(): ImageMatcher {
        return try {
            // Try to create OpenCV matcher first
            OpenCVImageMatcher()
        } catch (e: Exception) {
            // Fallback to basic matcher if OpenCV is not available
            BasicImageMatcher()
        }
    }
}