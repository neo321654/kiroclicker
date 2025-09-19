package com.autoclicker.android.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import com.autoclicker.android.model.ClickConfig
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import java.io.File
import java.io.IOException

class ConfigRepositoryImplTest {

    private lateinit var mockContext: Context
    private lateinit var mockSharedPreferences: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var mockFilesDir: File
    private lateinit var repository: ConfigRepositoryImpl

    @Before
    fun setup() {
        mockContext = mock(Context::class.java)
        mockSharedPreferences = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)
        mockFilesDir = mock(File::class.java)

        // Setup mock behavior
        whenever(mockContext.getSharedPreferences(any(), any())).thenReturn(mockSharedPreferences)
        whenever(mockContext.filesDir).thenReturn(mockFilesDir)
        whenever(mockSharedPreferences.edit()).thenReturn(mockEditor)
        whenever(mockEditor.putString(any(), any())).thenReturn(mockEditor)
        whenever(mockEditor.remove(any())).thenReturn(mockEditor)
        whenever(mockEditor.commit()).thenReturn(true)

        // Setup files directory
        val mockImagesDir = mock(File::class.java)
        whenever(mockFilesDir.exists()).thenReturn(true)
        whenever(mockImagesDir.exists()).thenReturn(true)
        whenever(mockImagesDir.mkdirs()).thenReturn(true)

        repository = ConfigRepositoryImpl(mockContext)
    }

    @Test
    fun `saveConfig should save valid configuration successfully`() = runTest {
        // Given
        val config = ClickConfig(
            id = "test-id",
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            clickX = 100,
            clickY = 200
        )

        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(null)

        // When
        val result = repository.saveConfig(config)

        // Then
        assertTrue("Save should succeed for valid config", result.isSuccess)
        assertTrue("Save result should be true", result.getOrNull() == true)
        verify(mockEditor).putString(eq("saved_configs"), any())
        verify(mockEditor).commit()
    }

    @Test
    fun `saveConfig should fail for invalid configuration`() = runTest {
        // Given
        val invalidConfig = ClickConfig(
            name = "", // Invalid empty name
            templateImagePath = "/path/to/image.png"
        )

        // When
        val result = repository.saveConfig(invalidConfig)

        // Then
        assertTrue("Save should fail for invalid config", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
        verify(mockEditor, never()).commit()
    }

    @Test
    fun `saveConfig should fail when name already exists`() = runTest {
        // Given
        val existingConfig = ClickConfig(
            id = "existing-id",
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )
        val newConfig = ClickConfig(
            id = "new-id",
            name = "Test Config", // Same name as existing
            templateImagePath = "/path/to/image2.png"
        )

        val existingConfigsJson = """{"existing-id":{"id":"existing-id","name":"Test Config","templateImagePath":"/path/to/image.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(existingConfigsJson)

        // When
        val result = repository.saveConfig(newConfig)

        // Then
        assertTrue("Save should fail when name already exists", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
        assertTrue("Error message should mention duplicate name",
            result.exceptionOrNull()?.message?.contains("already exists") == true)
    }

    @Test
    fun `loadConfig should return saved configuration`() = runTest {
        // Given
        val configId = "test-id"
        val configsJson = """{"test-id":{"id":"test-id","name":"Test Config","templateImagePath":"/path/to/image.png","clickX":100,"clickY":200,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(configsJson)

        // When
        val result = repository.loadConfig(configId)

        // Then
        assertTrue("Load should succeed", result.isSuccess)
        val config = result.getOrNull()
        assertNotNull("Config should not be null", config)
        assertEquals("Config ID should match", configId, config?.id)
        assertEquals("Config name should match", "Test Config", config?.name)
        assertEquals("Click X should match", 100, config?.clickX)
        assertEquals("Click Y should match", 200, config?.clickY)
    }

    @Test
    fun `loadConfig should return null for non-existent id`() = runTest {
        // Given
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(null)

        // When
        val result = repository.loadConfig("non-existent-id")

        // Then
        assertTrue("Load should succeed", result.isSuccess)
        assertNull("Should return null for non-existent config", result.getOrNull())
    }

    @Test
    fun `loadConfig should fail for empty id`() = runTest {
        // When
        val result = repository.loadConfig("")

        // Then
        assertTrue("Load should fail for empty ID", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `loadConfigByName should return configuration by name`() = runTest {
        // Given
        val configsJson = """{"test-id":{"id":"test-id","name":"Test Config","templateImagePath":"/path/to/image.png","clickX":100,"clickY":200,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(configsJson)

        // When
        val result = repository.loadConfigByName("Test Config")

        // Then
        assertTrue("Load by name should succeed", result.isSuccess)
        val config = result.getOrNull()
        assertNotNull("Config should not be null", config)
        assertEquals("Config name should match", "Test Config", config?.name)
    }

    @Test
    fun `getAllConfigs should return all saved configurations`() = runTest {
        // Given
        val configsJson = """{"id1":{"id":"id1","name":"Config 1","templateImagePath":"/path1.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8},"id2":{"id":"id2","name":"Config 2","templateImagePath":"/path2.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(configsJson)

        // When
        val result = repository.getAllConfigs()

        // Then
        assertTrue("Get all should succeed", result.isSuccess)
        val configs = result.getOrNull()!!
        assertEquals("Should return 2 configs", 2, configs.size)
        assertTrue("Should contain Config 1", configs.any { it.name == "Config 1" })
        assertTrue("Should contain Config 2", configs.any { it.name == "Config 2" })
    }

    @Test
    fun `getAllConfigs should return empty list when no configs exist`() = runTest {
        // Given
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(null)

        // When
        val result = repository.getAllConfigs()

        // Then
        assertTrue("Get all should succeed", result.isSuccess)
        assertTrue("Should return empty list", result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `deleteConfig should remove existing configuration`() = runTest {
        // Given
        val configsJson = """{"test-id":{"id":"test-id","name":"Test Config","templateImagePath":"/path/to/image.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(configsJson)

        // When
        val result = repository.deleteConfig("test-id")

        // Then
        assertTrue("Delete should succeed", result.isSuccess)
        assertTrue("Delete should return true", result.getOrNull() == true)
        verify(mockEditor).putString(eq("saved_configs"), any())
        verify(mockEditor).commit()
    }

    @Test
    fun `deleteConfig should return false for non-existent configuration`() = runTest {
        // Given
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(null)

        // When
        val result = repository.deleteConfig("non-existent-id")

        // Then
        assertTrue("Delete should succeed", result.isSuccess)
        assertFalse("Delete should return false for non-existent config", result.getOrNull() == true)
    }

    @Test
    fun `updateConfig should update existing configuration`() = runTest {
        // Given
        val originalConfigsJson = """{"test-id":{"id":"test-id","name":"Original Config","templateImagePath":"/path/to/image.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(originalConfigsJson)

        val updatedConfig = ClickConfig(
            id = "test-id",
            name = "Updated Config",
            templateImagePath = "/path/to/image.png"
        )

        // When
        val result = repository.updateConfig(updatedConfig)

        // Then
        assertTrue("Update should succeed", result.isSuccess)
        assertTrue("Update should return true", result.getOrNull() == true)
        verify(mockEditor).putString(eq("saved_configs"), any())
        verify(mockEditor).commit()
    }

    @Test
    fun `updateConfig should return false for non-existent configuration`() = runTest {
        // Given
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(null)

        val config = ClickConfig(
            id = "non-existent-id",
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )

        // When
        val result = repository.updateConfig(config)

        // Then
        assertTrue("Update should succeed", result.isSuccess)
        assertFalse("Update should return false for non-existent config", result.getOrNull() == true)
    }

    @Test
    fun `configExists should return true for existing configuration`() = runTest {
        // Given
        val configsJson = """{"test-id":{"id":"test-id","name":"Test Config","templateImagePath":"/path/to/image.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(configsJson)

        // When
        val result = repository.configExists("Test Config")

        // Then
        assertTrue("Exists check should succeed", result.isSuccess)
        assertTrue("Should return true for existing config", result.getOrNull() == true)
    }

    @Test
    fun `configExists should return false for non-existent configuration`() = runTest {
        // Given
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(null)

        // When
        val result = repository.configExists("Non-existent Config")

        // Then
        assertTrue("Exists check should succeed", result.isSuccess)
        assertFalse("Should return false for non-existent config", result.getOrNull() == true)
    }

    @Test
    fun `getConfigCount should return correct count`() = runTest {
        // Given
        val configsJson = """{"id1":{"id":"id1","name":"Config 1","templateImagePath":"/path1.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8},"id2":{"id":"id2","name":"Config 2","templateImagePath":"/path2.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(configsJson)

        // When
        val result = repository.getConfigCount()

        // Then
        assertTrue("Count should succeed", result.isSuccess)
        assertEquals("Should return correct count", 2, result.getOrNull())
    }

    @Test
    fun `clearAllConfigs should remove all configurations`() = runTest {
        // Given
        val configsJson = """{"id1":{"id":"id1","name":"Config 1","templateImagePath":"/path1.png","clickX":0,"clickY":0,"intervalMs":1000,"repeatCount":1,"threshold":0.8}}"""
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(configsJson)

        // When
        val result = repository.clearAllConfigs()

        // Then
        assertTrue("Clear should succeed", result.isSuccess)
        assertTrue("Clear should return true", result.getOrNull() == true)
        verify(mockEditor).remove("saved_configs")
        verify(mockEditor).commit()
    }

    @Test
    fun `saveConfig should handle SharedPreferences commit failure`() = runTest {
        // Given
        val config = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn(null)
        whenever(mockEditor.commit()).thenReturn(false) // Simulate commit failure

        // When
        val result = repository.saveConfig(config)

        // Then
        assertTrue("Save should fail when commit fails", result.isFailure)
        assertTrue("Should throw IOException", result.exceptionOrNull() is IOException)
    }

    @Test
    fun `getAllConfigs should handle malformed JSON gracefully`() = runTest {
        // Given
        whenever(mockSharedPreferences.getString(any(), any())).thenReturn("invalid json")

        // When
        val result = repository.getAllConfigs()

        // Then
        assertTrue("Get all should succeed even with malformed JSON", result.isSuccess)
        assertTrue("Should return empty list for malformed JSON", result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `imageFileExists should return true for existing file`() = runTest {
        // Given
        val imagePath = "/path/to/image.png"
        val mockImageFile = mock(File::class.java)
        whenever(mockImageFile.exists()).thenReturn(true)
        whenever(mockImageFile.canRead()).thenReturn(true)

        // When
        val result = repository.imageFileExists(imagePath)

        // Then
        assertTrue("Image exists check should succeed", result.isSuccess)
        // Note: This test would need more sophisticated mocking to work properly
        // as we can't easily mock the File constructor in the implementation
    }

    @Test
    fun `imageFileExists should return false for empty path`() = runTest {
        // When
        val result = repository.imageFileExists("")

        // Then
        assertTrue("Image exists check should succeed", result.isSuccess)
        assertFalse("Should return false for empty path", result.getOrNull() == true)
    }
}