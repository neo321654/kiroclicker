package com.autoclicker.android.model

import org.junit.Test
import org.junit.Assert.*

class ClickConfigTest {

    @Test
    fun `valid config should pass validation`() {
        val config = ClickConfig(
            id = "test-id",
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            clickX = 100,
            clickY = 200,
            intervalMs = 1000L,
            repeatCount = 5,
            threshold = 0.8
        )

        assertTrue("Valid config should pass validation", config.isValid())
        assertTrue("All validation errors should be empty", config.getValidationErrors().isEmpty())
    }

    @Test
    fun `empty id should fail validation`() {
        val config = ClickConfig(
            id = "",
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )

        assertFalse("Empty ID should fail validation", config.validateId())
        assertFalse("Config with empty ID should be invalid", config.isValid())
        assertTrue("Should contain ID error", config.getValidationErrors().any { it.contains("ID") })
    }

    @Test
    fun `empty name should fail validation`() {
        val config = ClickConfig(
            name = "",
            templateImagePath = "/path/to/image.png"
        )

        assertFalse("Empty name should fail validation", config.validateName())
        assertFalse("Config with empty name should be invalid", config.isValid())
    }

    @Test
    fun `long name should fail validation`() {
        val longName = "a".repeat(51)
        val config = ClickConfig(
            name = longName,
            templateImagePath = "/path/to/image.png"
        )

        assertFalse("Long name should fail validation", config.validateName())
        assertTrue("Should contain name length error", 
            config.getValidationErrors().any { it.contains("50 characters") })
    }

    @Test
    fun `empty template path should fail validation`() {
        val config = ClickConfig(
            name = "Test Config",
            templateImagePath = ""
        )

        assertFalse("Empty template path should fail validation", config.validateTemplatePath())
        assertFalse("Config with empty template path should be invalid", config.isValid())
    }

    @Test
    fun `negative coordinates should fail validation`() {
        val config = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            clickX = -1,
            clickY = -1
        )

        assertFalse("Negative coordinates should fail validation", config.validateCoordinates())
        assertFalse("Config with negative coordinates should be invalid", config.isValid())
    }

    @Test
    fun `invalid interval should fail validation`() {
        val configTooShort = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            intervalMs = 50L
        )

        val configTooLong = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            intervalMs = 70000L
        )

        assertFalse("Too short interval should fail validation", configTooShort.validateInterval())
        assertFalse("Too long interval should fail validation", configTooLong.validateInterval())
    }

    @Test
    fun `valid repeat count should pass validation`() {
        val infiniteConfig = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            repeatCount = ClickConfig.INFINITE_REPEAT
        )

        val finiteConfig = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            repeatCount = 10
        )

        assertTrue("Infinite repeat count should be valid", infiniteConfig.validateRepeatCount())
        assertTrue("Positive repeat count should be valid", finiteConfig.validateRepeatCount())
    }

    @Test
    fun `zero repeat count should fail validation`() {
        val config = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            repeatCount = 0
        )

        assertFalse("Zero repeat count should fail validation", config.validateRepeatCount())
    }

    @Test
    fun `invalid threshold should fail validation`() {
        val configTooLow = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            threshold = 0.05
        )

        val configTooHigh = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            threshold = 1.5
        )

        assertFalse("Too low threshold should fail validation", configTooLow.validateThreshold())
        assertFalse("Too high threshold should fail validation", configTooHigh.validateThreshold())
    }

    @Test
    fun `boundary values should be valid`() {
        val config = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            intervalMs = ClickConfig.MIN_INTERVAL_MS,
            threshold = ClickConfig.MIN_THRESHOLD
        )

        assertTrue("Minimum boundary values should be valid", config.isValid())

        val configMax = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            intervalMs = ClickConfig.MAX_INTERVAL_MS,
            threshold = ClickConfig.MAX_THRESHOLD
        )

        assertTrue("Maximum boundary values should be valid", configMax.isValid())
    }

    @Test
    fun `default constructor should generate unique id`() {
        val config1 = ClickConfig()
        val config2 = ClickConfig()

        assertNotEquals("Default constructor should generate unique IDs", config1.id, config2.id)
        assertTrue("Generated ID should not be empty", config1.id.isNotEmpty())
    }
}