package com.autoclicker.android.model

import android.graphics.Point

data class MatchResult(
    val found: Boolean,
    val confidence: Double,
    val location: Point?
) {
    companion object {
        fun notFound(): MatchResult = MatchResult(false, 0.0, null)
        
        fun found(confidence: Double, location: Point): MatchResult {
            require(confidence in 0.0..1.0) { "Confidence must be between 0.0 and 1.0" }
            return MatchResult(true, confidence, location)
        }
    }

    fun isValid(): Boolean {
        return if (found) {
            confidence in 0.0..1.0 && location != null
        } else {
            location == null
        }
    }

    fun hasHighConfidence(threshold: Double = 0.8): Boolean {
        return found && confidence >= threshold
    }
}