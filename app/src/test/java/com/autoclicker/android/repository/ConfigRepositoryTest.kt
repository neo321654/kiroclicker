package com.autoclicker.android.repository

import com.autoclicker.android.model.ClickConfig
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mockito.*

class ConfigRepositoryTest {

    // Mock implementation for testing interface contract
    private class MockConfigRepository : ConfigRepository {
        private val configs = mutableMapOf<String, ClickConfig>()
        
        override suspend fun saveConfig(config: ClickConfig): Result<Boolean> {
            return if (config.isValid()) {
                configs[config.id] = config
                Result.success(true)
            } else {
                Result.failure(IllegalArgumentException("Invalid config"))
            }
        }
        
        override suspend fun loadConfig(id: String): Result<ClickConfig?> {
            return Result.success(configs[id])
        }
        
        override suspend fun loadConfigByName(name: String): Result<ClickConfig?> {
            val config = configs.values.find { it.name == name }
            return Result.success(config)
        }
        
        override suspend fun getAllConfigs(): Result<List<ClickConfig>> {
            return Result.success(configs.values.toList())
        }
        
        override suspend fun deleteConfig(id: String): Result<Boolean> {
            val removed = configs.remove(id) != null
            return Result.success(removed)
        }
        
        override suspend fun deleteConfigByName(name: String): Result<Boolean> {
            val config = configs.values.find { it.name == name }
            config?.let { configs.remove(it.id) }
            return Result.success(config != null)
        }
        
        override suspend fun updateConfig(config: ClickConfig): Result<Boolean> {
            return if (config.isValid() && configs.containsKey(config.id)) {
                configs[config.id] = config
                Result.success(true)
            } else {
                Result.success(false)
            }
        }
        
        override suspend fun configExists(name: String): Result<Boolean> {
            val exists = configs.values.any { it.name == name }
            return Result.success(exists)
        }
        
        override suspend fun getConfigCount(): Result<Int> {
            return Result.success(configs.size)
        }
        
        override suspend fun clearAllConfigs(): Result<Boolean> {
            configs.clear()
            return Result.success(true)
        }
    }

    private val repository = MockConfigRepository()

    @Test
    fun `saveConfig should save valid configuration`() = runTest {
        val config = ClickConfig(
            id = "test-id",
            name = "Test Config",
            templateImagePath = "/path/to/image.png",
            clickX = 100,
            clickY = 200
        )

        val result = repository.saveConfig(config)
        
        assertTrue("Save should succeed for valid config", result.isSuccess)
        assertTrue("Save result should be true", result.getOrNull() == true)
    }

    @Test
    fun `saveConfig should fail for invalid configuration`() = runTest {
        val invalidConfig = ClickConfig(
            name = "", // Invalid empty name
            templateImagePath = "/path/to/image.png"
        )

        val result = repository.saveConfig(invalidConfig)
        
        assertTrue("Save should fail for invalid config", result.isFailure)
        assertTrue("Should throw IllegalArgumentException", 
            result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun `loadConfig should return saved configuration`() = runTest {
        val config = ClickConfig(
            id = "test-id",
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )

        repository.saveConfig(config)
        val result = repository.loadConfig("test-id")
        
        assertTrue("Load should succeed", result.isSuccess)
        assertEquals("Loaded config should match saved config", config, result.getOrNull())
    }

    @Test
    fun `loadConfig should return null for non-existent id`() = runTest {
        val result = repository.loadConfig("non-existent-id")
        
        assertTrue("Load should succeed", result.isSuccess)
        assertNull("Should return null for non-existent config", result.getOrNull())
    }

    @Test
    fun `loadConfigByName should return configuration by name`() = runTest {
        val config = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )

        repository.saveConfig(config)
        val result = repository.loadConfigByName("Test Config")
        
        assertTrue("Load by name should succeed", result.isSuccess)
        assertEquals("Loaded config should match saved config", config, result.getOrNull())
    }

    @Test
    fun `getAllConfigs should return all saved configurations`() = runTest {
        val config1 = ClickConfig(name = "Config 1", templateImagePath = "/path1.png")
        val config2 = ClickConfig(name = "Config 2", templateImagePath = "/path2.png")

        repository.saveConfig(config1)
        repository.saveConfig(config2)
        
        val result = repository.getAllConfigs()
        
        assertTrue("Get all should succeed", result.isSuccess)
        val configs = result.getOrNull()!!
        assertEquals("Should return 2 configs", 2, configs.size)
        assertTrue("Should contain config1", configs.contains(config1))
        assertTrue("Should contain config2", configs.contains(config2))
    }

    @Test
    fun `getAllConfigs should return empty list when no configs exist`() = runTest {
        val result = repository.getAllConfigs()
        
        assertTrue("Get all should succeed", result.isSuccess)
        assertTrue("Should return empty list", result.getOrNull()!!.isEmpty())
    }

    @Test
    fun `deleteConfig should remove existing configuration`() = runTest {
        val config = ClickConfig(
            id = "test-id",
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )

        repository.saveConfig(config)
        val deleteResult = repository.deleteConfig("test-id")
        
        assertTrue("Delete should succeed", deleteResult.isSuccess)
        assertTrue("Delete should return true", deleteResult.getOrNull() == true)
        
        val loadResult = repository.loadConfig("test-id")
        assertNull("Config should no longer exist", loadResult.getOrNull())
    }

    @Test
    fun `deleteConfig should return false for non-existent configuration`() = runTest {
        val result = repository.deleteConfig("non-existent-id")
        
        assertTrue("Delete should succeed", result.isSuccess)
        assertFalse("Delete should return false for non-existent config", result.getOrNull() == true)
    }

    @Test
    fun `updateConfig should update existing configuration`() = runTest {
        val originalConfig = ClickConfig(
            id = "test-id",
            name = "Original Config",
            templateImagePath = "/path/to/image.png"
        )

        val updatedConfig = originalConfig.copy(name = "Updated Config")

        repository.saveConfig(originalConfig)
        val updateResult = repository.updateConfig(updatedConfig)
        
        assertTrue("Update should succeed", updateResult.isSuccess)
        assertTrue("Update should return true", updateResult.getOrNull() == true)
        
        val loadResult = repository.loadConfig("test-id")
        assertEquals("Config should be updated", updatedConfig, loadResult.getOrNull())
    }

    @Test
    fun `updateConfig should return false for non-existent configuration`() = runTest {
        val config = ClickConfig(
            id = "non-existent-id",
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )

        val result = repository.updateConfig(config)
        
        assertTrue("Update should succeed", result.isSuccess)
        assertFalse("Update should return false for non-existent config", result.getOrNull() == true)
    }

    @Test
    fun `configExists should return true for existing configuration`() = runTest {
        val config = ClickConfig(
            name = "Test Config",
            templateImagePath = "/path/to/image.png"
        )

        repository.saveConfig(config)
        val result = repository.configExists("Test Config")
        
        assertTrue("Exists check should succeed", result.isSuccess)
        assertTrue("Should return true for existing config", result.getOrNull() == true)
    }

    @Test
    fun `configExists should return false for non-existent configuration`() = runTest {
        val result = repository.configExists("Non-existent Config")
        
        assertTrue("Exists check should succeed", result.isSuccess)
        assertFalse("Should return false for non-existent config", result.getOrNull() == true)
    }

    @Test
    fun `getConfigCount should return correct count`() = runTest {
        val config1 = ClickConfig(name = "Config 1", templateImagePath = "/path1.png")
        val config2 = ClickConfig(name = "Config 2", templateImagePath = "/path2.png")

        repository.saveConfig(config1)
        repository.saveConfig(config2)
        
        val result = repository.getConfigCount()
        
        assertTrue("Count should succeed", result.isSuccess)
        assertEquals("Should return correct count", 2, result.getOrNull())
    }

    @Test
    fun `clearAllConfigs should remove all configurations`() = runTest {
        val config1 = ClickConfig(name = "Config 1", templateImagePath = "/path1.png")
        val config2 = ClickConfig(name = "Config 2", templateImagePath = "/path2.png")

        repository.saveConfig(config1)
        repository.saveConfig(config2)
        
        val clearResult = repository.clearAllConfigs()
        assertTrue("Clear should succeed", clearResult.isSuccess)
        assertTrue("Clear should return true", clearResult.getOrNull() == true)
        
        val countResult = repository.getConfigCount()
        assertEquals("Count should be 0 after clear", 0, countResult.getOrNull())
    }
}