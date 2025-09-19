package com.autoclicker.android.model

import java.util.UUID

data class ClickConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val templateImagePath: String = "",
    val clickX: Int = 0,
    val clickY: Int = 0,
    val intervalMs: Long = 1000L,
    val repeatCount: Int = 1, // -1 for infinite mode
    val searchRadius: Int = 30, // Search radius for the target
    val threshold: Double = 0.8 // Match threshold for image recognition
) {
    companion object {
        const val MIN_INTERVAL_MS = 100L
        const val MAX_INTERVAL_MS = 60000L
        const val MIN_THRESHOLD = 0.1
        const val MAX_THRESHOLD = 1.0
        const val INFINITE_REPEAT = -1
    }

    fun isValid(): Boolean {
        return validateId() && 
               validateName() && 
               validateTemplatePath() && 
               validateCoordinates() && 
               validateInterval() && 
               validateRepeatCount() && 
               validateThreshold()
    }

    fun validateId(): Boolean = id.isNotEmpty()

    fun validateName(): Boolean = name.isNotEmpty() && name.length <= 50

    fun validateTemplatePath(): Boolean = templateImagePath.isNotEmpty()

    fun validateCoordinates(): Boolean = clickX >= 0 && clickY >= 0

    fun validateInterval(): Boolean = intervalMs in MIN_INTERVAL_MS..MAX_INTERVAL_MS

    fun validateRepeatCount(): Boolean = repeatCount == INFINITE_REPEAT || repeatCount > 0

    fun validateThreshold(): Boolean = threshold in MIN_THRESHOLD..MAX_THRESHOLD

    fun getValidationErrors(): List<String> {
        val errors = mutableListOf<String>()
        
        if (!validateId()) errors.add("ID cannot be empty")
        if (!validateName()) errors.add("Name must be between 1 and 50 characters")
        if (!validateTemplatePath()) errors.add("Template image path cannot be empty")
        if (!validateCoordinates()) errors.add("Click coordinates must be non-negative")
        if (!validateInterval()) errors.add("Interval must be between ${MIN_INTERVAL_MS}ms and ${MAX_INTERVAL_MS}ms")
        if (!validateRepeatCount()) errors.add("Repeat count must be positive or -1 for infinite")
        if (!validateThreshold()) errors.add("Threshold must be between $MIN_THRESHOLD and $MAX_THRESHOLD")
        
        return errors
    }
}