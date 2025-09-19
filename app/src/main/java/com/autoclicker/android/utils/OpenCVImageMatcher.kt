package com.autoclicker.android.utils

import android.graphics.Bitmap
import android.graphics.Point
import android.util.Log
import com.autoclicker.android.AutoClickerApplication
import com.autoclicker.android.model.MatchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.*
import org.opencv.imgproc.Imgproc

/**
 * OpenCV implementation of ImageMatcher interface
 * Provides template matching functionality using OpenCV library
 */
class OpenCVImageMatcher : ImageMatcher {
    
    companion object {
        private const val TAG = "OpenCVImageMatcher"
    }
    
    override fun isInitialized(): Boolean {
        return try {
            // Try to access OpenCV to check if it's initialized
            Class.forName("org.opencv.core.Core")
            true
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun initialize(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (isInitialized()) {
                Result.success(true)
            } else {
                Log.w(TAG, "OpenCV not initialized yet")
                Result.failure(IllegalStateException("OpenCV not initialized"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking OpenCV initialization", e)
            Result.failure(e)
        }
    }
    
    override suspend fun findTemplate(
        screenshot: Bitmap,
        template: Bitmap,
        threshold: Double
    ): Result<MatchResult> = withContext(Dispatchers.Default) {
        try {
            validateInputs(screenshot, template, threshold)
            
            if (!isInitialized()) {
                return@withContext Result.failure(IllegalStateException("OpenCV not initialized"))
            }
            
            val screenshotMat = Mat()
            val templateMat = Mat()
            
            try {
                // Convert bitmaps to OpenCV Mat objects
                Utils.bitmapToMat(screenshot, screenshotMat)
                Utils.bitmapToMat(template, templateMat)
                
                // Convert to grayscale for better matching performance
                val screenshotGray = Mat()
                val templateGray = Mat()
                Imgproc.cvtColor(screenshotMat, screenshotGray, Imgproc.COLOR_RGB2GRAY)
                Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_RGB2GRAY)
                
                // Perform template matching
                val result = Mat()
                Imgproc.matchTemplate(screenshotGray, templateGray, result, Imgproc.TM_CCOEFF_NORMED)
                
                // Find the best match
                val minMaxLocResult = Core.minMaxLoc(result)
                val confidence = minMaxLocResult.maxVal
                val matchLocation = minMaxLocResult.maxLoc
                
                val matchResult = if (confidence >= threshold) {
                    MatchResult(
                        found = true,
                        confidence = confidence,
                        location = Point(matchLocation.x.toInt(), matchLocation.y.toInt())
                    )
                } else {
                    MatchResult(
                        found = false,
                        confidence = confidence,
                        location = null
                    )
                }
                
                Log.d(TAG, "Template matching completed. Confidence: $confidence, Threshold: $threshold")
                Result.success(matchResult)
                
            } finally {
                // Clean up Mat objects to prevent memory leaks
                screenshotMat.release()
                templateMat.release()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in findTemplate", e)
            Result.failure(e)
        }
    }
    
    override suspend fun matchTemplate(
        screenshot: Bitmap,
        template: Bitmap,
        threshold: Double
    ): Result<Point?> = withContext(Dispatchers.Default) {
        try {
            val matchResult = findTemplate(screenshot, template, threshold)
            matchResult.fold(
                onSuccess = { result ->
                    Result.success(result.location)
                },
                onFailure = { error ->
                    Result.failure(error)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in matchTemplate", e)
            Result.failure(e)
        }
    }
    
    override suspend fun findAllMatches(
        screenshot: Bitmap,
        template: Bitmap,
        threshold: Double,
        maxMatches: Int
    ): Result<List<MatchResult>> = withContext(Dispatchers.Default) {
        try {
            validateInputs(screenshot, template, threshold)
            
            if (!isInitialized()) {
                return@withContext Result.failure(IllegalStateException("OpenCV not initialized"))
            }
            
            val screenshotMat = Mat()
            val templateMat = Mat()
            
            try {
                // Convert bitmaps to OpenCV Mat objects
                Utils.bitmapToMat(screenshot, screenshotMat)
                Utils.bitmapToMat(template, templateMat)
                
                // Convert to grayscale
                val screenshotGray = Mat()
                val templateGray = Mat()
                Imgproc.cvtColor(screenshotMat, screenshotGray, Imgproc.COLOR_RGB2GRAY)
                Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_RGB2GRAY)
                
                // Perform template matching
                val result = Mat()
                Imgproc.matchTemplate(screenshotGray, templateGray, result, Imgproc.TM_CCOEFF_NORMED)
                
                val matches = mutableListOf<MatchResult>()
                val templateSize = templateMat.size()
                
                // Find all matches above threshold
                for (i in 0 until result.rows()) {
                    for (j in 0 until result.cols()) {
                        val confidence = result.get(i, j)[0]
                        if (confidence >= threshold && matches.size < maxMatches) {
                            matches.add(
                                MatchResult(
                                    found = true,
                                    confidence = confidence,
                                    location = Point(j, i)
                                )
                            )
                        }
                    }
                }
                
                // Sort by confidence (highest first)
                matches.sortByDescending { it.confidence }
                
                Log.d(TAG, "Found ${matches.size} matches above threshold $threshold")
                Result.success(matches.take(maxMatches))
                
            } finally {
                screenshotMat.release()
                templateMat.release()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in findAllMatches", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getMatchConfidence(
        screenshot: Bitmap,
        template: Bitmap,
        location: Point
    ): Result<Double> = withContext(Dispatchers.Default) {
        try {
            validateInputs(screenshot, template, 0.0)
            
            if (!isInitialized()) {
                return@withContext Result.failure(IllegalStateException("OpenCV not initialized"))
            }
            
            val screenshotMat = Mat()
            val templateMat = Mat()
            
            try {
                Utils.bitmapToMat(screenshot, screenshotMat)
                Utils.bitmapToMat(template, templateMat)
                
                val screenshotGray = Mat()
                val templateGray = Mat()
                Imgproc.cvtColor(screenshotMat, screenshotGray, Imgproc.COLOR_RGB2GRAY)
                Imgproc.cvtColor(templateMat, templateGray, Imgproc.COLOR_RGB2GRAY)
                
                // Extract region of interest from screenshot at the specified location
                val templateSize = templateMat.size()
                val roi = Rect(
                    location.x,
                    location.y,
                    templateSize.width.toInt(),
                    templateSize.height.toInt()
                )
                
                // Check if ROI is within screenshot bounds
                if (roi.x + roi.width > screenshotGray.cols() || 
                    roi.y + roi.height > screenshotGray.rows() ||
                    roi.x < 0 || roi.y < 0) {
                    return@withContext Result.failure(
                        IllegalArgumentException("Location is outside screenshot bounds")
                    )
                }
                
                val roiMat = Mat(screenshotGray, roi)
                
                // Calculate normalized cross-correlation
                val result = Mat()
                Imgproc.matchTemplate(roiMat, templateGray, result, Imgproc.TM_CCOEFF_NORMED)
                
                val confidence = result.get(0, 0)[0]
                
                Log.d(TAG, "Confidence at location ($location): $confidence")
                Result.success(confidence)
                
            } finally {
                screenshotMat.release()
                templateMat.release()
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in getMatchConfidence", e)
            Result.failure(e)
        }
    }
    
    private fun validateInputs(screenshot: Bitmap, template: Bitmap, threshold: Double) {
        require(!screenshot.isRecycled) { "Screenshot bitmap is recycled" }
        require(!template.isRecycled) { "Template bitmap is recycled" }
        require(threshold in 0.0..1.0) { "Threshold must be between 0.0 and 1.0" }
        require(template.width <= screenshot.width && template.height <= screenshot.height) {
            "Template size (${template.width}x${template.height}) must not exceed screenshot size (${screenshot.width}x${screenshot.height})"
        }
    }
}