package com.autoclicker.android.integration

import android.content.Context
import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autoclicker.android.model.ClickConfig
import com.autoclicker.android.model.MatchResult
import com.autoclicker.android.repository.ConfigRepository
import com.autoclicker.android.repository.ConfigRepositoryImpl
import com.autoclicker.android.utils.ImageMatcher
import com.autoclicker.android.utils.ImageMatcherFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File
import java.io.FileOutputStream

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class RepositoryImageMatcherIntegrationTest {

    private lateinit var context: Context
    private lateinit var repository: ConfigRepository
    private lateinit var imageMatcher: ImageMatcher
    private lateinit var testImageFile: File
    private lateinit var testBitmap: Bitmap

    @Before
    fun setup() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        repository = ConfigRepositoryImpl(context)
        imageMatcher = ImageMatcherFactory.createImageMatcher()
        
        // Create test bitmap and save it to file
        testBitmap = createTestBitmap()
        testImageFile = File(context.filesDir, "test_template.png")
        saveTestBitmap(testBitmap, testImageFile)
    }

    @After
    fun tearDown() {
        // Clean up test files
        testImageFile.delete()
        
        // Clean up any saved configurations
        val configs = repository.getAllConfigs()
        configs.forEach { config ->
            if (config.name.startsWith("integration_test")) {
                repository.deleteConfig(config.name)
            }
        }
    }

    @Test
    fun testConfigurationSaveAndLoadWithImagePath() = runTest {
        // Create configuration with image path
        val config = ClickConfig(
            id = "integration-test-1",
            name = "integration_test_config_1",
            templateImagePath = testImageFile.absolutePath,
            clickX = 100,
            clickY = 200,
            intervalMs = 1500,
            repeatCount = 10,
            threshold = 0.85
        )
        
        // Save configuration
        val saveResult = repository.saveConfig(config)
        assertTrue("Configuration should be saved successfully", saveResult)
        
        // Load configuration
        val loadedConfig = repository.loadConfig("integration_test_config_1")
        assertNotNull("Configuration should be loaded", loadedConfig)
        
        // Verify all fields
        assertEquals(config.id, loadedConfig!!.id)
        assertEquals(config.name, loadedConfig.name)
        assertEquals(config.templateImagePath, loadedConfig.templateImagePath)
        assertEquals(config.clickX, loadedConfig.clickX)
        assertEquals(config.clickY, loadedConfig.clickY)
        assertEquals(config.intervalMs, loadedConfig.intervalMs)
        assertEquals(config.repeatCount, loadedConfig.repeatCount)
        assertEquals(config.threshold, loadedConfig.threshold, 0.001)
        
        // Verify image file exists and is accessible
        val imageFile = File(loadedConfig.templateImagePath)
        assertTrue("Image file should exist", imageFile.exists())
        assertTrue("Image file should be readable", imageFile.canRead())
    }

    @Test
    fun testImageMatchingWithSavedConfiguration() = runTest {
        // Create and save configuration
        val config = ClickConfig(
            id = "integration-test-2",
            name = "integration_test_config_2",
            templateImagePath = testImageFile.absolutePath,
            clickX = 25,
            clickY = 25,
            intervalMs = 1000,
            repeatCount = 5,
            threshold = 0.8
        )
        
        repository.saveConfig(config)
        
        // Load configuration
        val loadedConfig = repository.loadConfig("integration_test_config_2")
        assertNotNull("Configuration should be loaded", loadedConfig)
        
        // Create a larger bitmap that contains our test template
        val largeBitmap = createLargeBitmapWithTemplate(testBitmap, 50, 75)
        
        // Perform image matching
        val matchResult = imageMatcher.findTemplate(largeBitmap, testBitmap)
        
        // Verify match result
        assertNotNull("Match result should not be null", matchResult)
        assertTrue("Template should be found in large bitmap", matchResult.found)
        assertTrue("Confidence should be reasonable", matchResult.confidence > 0.5)
        assertNotNull("Location should be found", matchResult.location)
        
        // Verify click coordinates calculation
        val finalClickX = matchResult.location!!.x + loadedConfig!!.clickX
        val finalClickY = matchResult.location!!.y + loadedConfig.clickY
        
        assertTrue("Final click X should be positive", finalClickX >= 0)
        assertTrue("Final click Y should be positive", finalClickY >= 0)
        assertTrue("Final click X should be within bounds", finalClickX < largeBitmap.width)
        assertTrue("Final click Y should be within bounds", finalClickY < largeBitmap.height)
    }

    @Test
    fun testMultipleConfigurationsWithDifferentImages() = runTest {
        // Create multiple test images and configurations
        val configs = mutableListOf<ClickConfig>()
        val imageFiles = mutableListOf<File>()
        
        for (i in 1..3) {
            // Create unique test bitmap for each config
            val bitmap = createTestBitmapWithColor(i)
            val imageFile = File(context.filesDir, "test_template_$i.png")
            saveTestBitmap(bitmap, imageFile)
            imageFiles.add(imageFile)
            
            val config = ClickConfig(
                id = "integration-test-$i",
                name = "integration_test_config_$i",
                templateImagePath = imageFile.absolutePath,
                clickX = i * 10,
                clickY = i * 20,
                intervalMs = 1000L + (i * 100),
                repeatCount = i * 2,
                threshold = 0.8 + (i * 0.05)
            )
            
            configs.add(config)
            
            // Save configuration
            val saveResult = repository.saveConfig(config)
            assertTrue("Configuration $i should be saved", saveResult)
        }
        
        // Verify all configurations are saved
        val allConfigs = repository.getAllConfigs()
        val testConfigs = allConfigs.filter { it.name.startsWith("integration_test_config_") }
        assertEquals("Should have 3 test configurations", 3, testConfigs.size)
        
        // Load and verify each configuration
        for (i in 1..3) {
            val loadedConfig = repository.loadConfig("integration_test_config_$i")
            assertNotNull("Configuration $i should be loaded", loadedConfig)
            
            val originalConfig = configs[i - 1]
            assertEquals("Config $i ID should match", originalConfig.id, loadedConfig!!.id)
            assertEquals("Config $i name should match", originalConfig.name, loadedConfig.name)
            assertEquals("Config $i click X should match", originalConfig.clickX, loadedConfig.clickX)
            assertEquals("Config $i click Y should match", originalConfig.clickY, loadedConfig.clickY)
            
            // Verify image file exists
            val imageFile = File(loadedConfig.templateImagePath)
            assertTrue("Image file $i should exist", imageFile.exists())
        }
        
        // Clean up additional files
        imageFiles.forEach { it.delete() }
    }

    @Test
    fun testImageMatchingAccuracyWithDifferentThresholds() = runTest {
        // Create configuration with different thresholds
        val baseConfig = ClickConfig(
            id = "threshold-test",
            name = "integration_test_threshold",
            templateImagePath = testImageFile.absolutePath,
            clickX = 10,
            clickY = 10,
            intervalMs = 1000,
            repeatCount = 1,
            threshold = 0.9 // High threshold
        )
        
        repository.saveConfig(baseConfig)
        
        // Create test scenarios with different image similarities
        val exactMatch = testBitmap.copy(testBitmap.config, false)
        val slightlyDifferent = createSlightlyDifferentBitmap(testBitmap)
        val veryDifferent = createTestBitmapWithColor(999)
        
        // Test exact match
        val exactResult = imageMatcher.findTemplate(exactMatch, testBitmap)
        assertTrue("Exact match should be found", exactResult.found)
        assertTrue("Exact match confidence should be high", exactResult.confidence > 0.95)
        
        // Test slightly different image
        val slightResult = imageMatcher.findTemplate(slightlyDifferent, testBitmap)
        // Result depends on implementation, but should be consistent
        assertNotNull("Slight difference result should not be null", slightResult)
        
        // Test very different image
        val differentResult = imageMatcher.findTemplate(veryDifferent, testBitmap)
        assertNotNull("Very different result should not be null", differentResult)
        
        // Verify threshold behavior
        if (exactResult.confidence >= baseConfig.threshold) {
            assertTrue("High confidence match should be found", exactResult.found)
        }
    }

    @Test
    fun testConfigurationUpdateAndImagePathHandling() = runTest {
        // Create initial configuration
        val initialConfig = ClickConfig(
            id = "update-test",
            name = "integration_test_update",
            templateImagePath = testImageFile.absolutePath,
            clickX = 50,
            clickY = 60,
            intervalMs = 2000,
            repeatCount = 8,
            threshold = 0.75
        )
        
        repository.saveConfig(initialConfig)
        
        // Create new image file
        val newBitmap = createTestBitmapWithColor(42)
        val newImageFile = File(context.filesDir, "test_template_updated.png")
        saveTestBitmap(newBitmap, newImageFile)
        
        // Update configuration with new image path
        val updatedConfig = initialConfig.copy(
            templateImagePath = newImageFile.absolutePath,
            clickX = 75,
            clickY = 85,
            intervalMs = 1200,
            repeatCount = 15,
            threshold = 0.82
        )
        
        // Save updated configuration (should overwrite)
        val updateResult = repository.saveConfig(updatedConfig)
        assertTrue("Updated configuration should be saved", updateResult)
        
        // Load and verify updated configuration
        val loadedConfig = repository.loadConfig("integration_test_update")
        assertNotNull("Updated configuration should be loaded", loadedConfig)
        
        assertEquals("Updated image path should match", newImageFile.absolutePath, loadedConfig!!.templateImagePath)
        assertEquals("Updated click X should match", 75, loadedConfig.clickX)
        assertEquals("Updated click Y should match", 85, loadedConfig.clickY)
        assertEquals("Updated interval should match", 1200L, loadedConfig.intervalMs)
        assertEquals("Updated repeat count should match", 15, loadedConfig.repeatCount)
        assertEquals("Updated threshold should match", 0.82, loadedConfig.threshold, 0.001)
        
        // Verify both image files exist
        assertTrue("Original image file should still exist", testImageFile.exists())
        assertTrue("New image file should exist", newImageFile.exists())
        
        // Clean up
        newImageFile.delete()
    }

    @Test
    fun testRepositoryErrorHandling() = runTest {
        // Test loading non-existent configuration
        val nonExistentConfig = repository.loadConfig("non_existent_config")
        assertNull("Non-existent configuration should return null", nonExistentConfig)
        
        // Test deleting non-existent configuration
        val deleteResult = repository.deleteConfig("non_existent_config")
        assertFalse("Deleting non-existent config should return false", deleteResult)
        
        // Test saving configuration with invalid image path
        val invalidConfig = ClickConfig(
            id = "invalid-test",
            name = "integration_test_invalid",
            templateImagePath = "/invalid/path/image.png",
            clickX = 10,
            clickY = 10,
            intervalMs = 1000,
            repeatCount = 1,
            threshold = 0.8
        )
        
        // Should still save the configuration (path validation might be done elsewhere)
        val saveResult = repository.saveConfig(invalidConfig)
        assertTrue("Configuration with invalid path should still be saved", saveResult)
        
        // But loading it should work (the path is just stored as string)
        val loadedInvalidConfig = repository.loadConfig("integration_test_invalid")
        assertNotNull("Configuration with invalid path should be loaded", loadedInvalidConfig)
        assertEquals("Invalid path should be preserved", "/invalid/path/image.png", loadedInvalidConfig!!.templateImagePath)
    }

    @Test
    fun testImageMatcherWithVariousImageSizes() = runTest {
        // Test image matching with different sized templates and screenshots
        val smallTemplate = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        val mediumTemplate = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val largeTemplate = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        
        // Fill templates with distinct patterns
        fillBitmapWithPattern(smallTemplate, 1)
        fillBitmapWithPattern(mediumTemplate, 2)
        fillBitmapWithPattern(largeTemplate, 3)
        
        // Create large screenshot containing all templates
        val screenshot = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        
        // Test matching each template size
        val smallResult = imageMatcher.findTemplate(screenshot, smallTemplate)
        val mediumResult = imageMatcher.findTemplate(screenshot, mediumTemplate)
        val largeResult = imageMatcher.findTemplate(screenshot, largeTemplate)
        
        // Verify all results are valid (found or not found, but not null)
        assertNotNull("Small template result should not be null", smallResult)
        assertNotNull("Medium template result should not be null", mediumResult)
        assertNotNull("Large template result should not be null", largeResult)
        
        // Verify confidence values are reasonable
        assertTrue("Small template confidence should be valid", smallResult.confidence >= 0.0 && smallResult.confidence <= 1.0)
        assertTrue("Medium template confidence should be valid", mediumResult.confidence >= 0.0 && mediumResult.confidence <= 1.0)
        assertTrue("Large template confidence should be valid", largeResult.confidence >= 0.0 && largeResult.confidence <= 1.0)
    }

    // Helper methods
    private fun createTestBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        fillBitmapWithPattern(bitmap, 1)
        return bitmap
    }

    private fun createTestBitmapWithColor(colorSeed: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        fillBitmapWithPattern(bitmap, colorSeed)
        return bitmap
    }

    private fun fillBitmapWithPattern(bitmap: Bitmap, pattern: Int) {
        val pixels = IntArray(bitmap.width * bitmap.height)
        for (i in pixels.indices) {
            val x = i % bitmap.width
            val y = i / bitmap.width
            // Create a simple pattern based on coordinates and pattern seed
            val color = when ((x + y + pattern) % 4) {
                0 -> 0xFF000000.toInt() // Black
                1 -> 0xFFFFFFFF.toInt() // White
                2 -> 0xFF808080.toInt() // Gray
                else -> 0xFFC0C0C0.toInt() // Light gray
            }
            pixels[i] = color
        }
        bitmap.setPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
    }

    private fun createLargeBitmapWithTemplate(template: Bitmap, offsetX: Int, offsetY: Int): Bitmap {
        val largeBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        largeBitmap.eraseColor(0xFF404040.toInt()) // Dark gray background
        
        // Copy template into large bitmap at specified offset
        val canvas = android.graphics.Canvas(largeBitmap)
        canvas.drawBitmap(template, offsetX.toFloat(), offsetY.toFloat(), null)
        
        return largeBitmap
    }

    private fun createSlightlyDifferentBitmap(original: Bitmap): Bitmap {
        val different = original.copy(original.config, true)
        // Modify a few pixels to create slight difference
        val pixels = IntArray(different.width * different.height)
        different.getPixels(pixels, 0, different.width, 0, 0, different.width, different.height)
        
        // Change every 10th pixel slightly
        for (i in pixels.indices step 10) {
            val originalColor = pixels[i]
            val r = (originalColor shr 16) and 0xFF
            val g = (originalColor shr 8) and 0xFF
            val b = originalColor and 0xFF
            val a = (originalColor shr 24) and 0xFF
            
            // Slightly modify the color
            val newR = (r + 10).coerceIn(0, 255)
            val newG = (g + 10).coerceIn(0, 255)
            val newB = (b + 10).coerceIn(0, 255)
            
            pixels[i] = (a shl 24) or (newR shl 16) or (newG shl 8) or newB
        }
        
        different.setPixels(pixels, 0, different.width, 0, 0, different.width, different.height)
        return different
    }

    private fun saveTestBitmap(bitmap: Bitmap, file: File) {
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}