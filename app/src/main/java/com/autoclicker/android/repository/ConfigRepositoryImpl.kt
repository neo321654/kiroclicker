package com.autoclicker.android.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.util.Log
import com.autoclicker.android.model.ClickConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Concrete implementation of ConfigRepository using SharedPreferences for metadata
 * and internal storage for image files
 */
class ConfigRepositoryImpl(private val context: Context) : ConfigRepository {
    
    companion object {
        private const val TAG = "ConfigRepositoryImpl"
        private const val PREFS_NAME = "autoclicker_configs"
        private const val CONFIGS_KEY = "saved_configs"
        private const val IMAGES_DIR = "template_images"
    }
    
    private val sharedPreferences: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    private val json = Json { 
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    
    private val imagesDir: File by lazy {
        File(context.filesDir, IMAGES_DIR).apply {
            if (!exists()) {
                mkdirs()
            }
        }
    }
    
    @Serializable
    private data class SerializableConfig(
        val id: String,
        val name: String,
        val templateImagePath: String,
        val clickX: Int,
        val clickY: Int,
        val intervalMs: Long,
        val repeatCount: Int,
        val threshold: Double
    )
    
    private fun ClickConfig.toSerializable() = SerializableConfig(
        id = id,
        name = name,
        templateImagePath = templateImagePath,
        clickX = clickX,
        clickY = clickY,
        intervalMs = intervalMs,
        repeatCount = repeatCount,
        threshold = threshold
    )
    
    private fun SerializableConfig.toClickConfig() = ClickConfig(
        id = id,
        name = name,
        templateImagePath = templateImagePath,
        clickX = clickX,
        clickY = clickY,
        intervalMs = intervalMs,
        repeatCount = repeatCount,
        threshold = threshold
    )
    
    override suspend fun saveConfig(config: ClickConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Validate config before saving
            if (!config.isValid()) {
                val errors = config.getValidationErrors()
                Log.w(TAG, "Invalid config: ${errors.joinToString(", ")}")
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid config: ${errors.joinToString(", ")}")
                )
            }
            
            // Get current configs
            val currentConfigs = getAllConfigsInternal().toMutableMap()
            
            // Check if name already exists (but allow updating same ID)
            val existingWithSameName = currentConfigs.values.find { 
                it.name == config.name && it.id != config.id 
            }
            if (existingWithSameName != null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Configuration with name '${config.name}' already exists")
                )
            }
            
            // Add/update config
            currentConfigs[config.id] = config.toSerializable()
            
            // Save to SharedPreferences
            val configsJson = json.encodeToString(currentConfigs)
            val success = sharedPreferences.edit()
                .putString(CONFIGS_KEY, configsJson)
                .commit()
            
            if (success) {
                Log.d(TAG, "Successfully saved config: ${config.name}")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to save config to SharedPreferences")
                Result.failure(IOException("Failed to save config to SharedPreferences"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving config: ${config.name}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun loadConfig(id: String): Result<ClickConfig?> = withContext(Dispatchers.IO) {
        try {
            validateConfigId(id).getOrElse { return@withContext Result.failure(it) }
            
            val configs = getAllConfigsInternal()
            val config = configs[id]?.toClickConfig()
            
            Log.d(TAG, "Loaded config by ID: $id, found: ${config != null}")
            Result.success(config)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config by ID: $id", e)
            Result.failure(e)
        }
    }
    
    override suspend fun loadConfigByName(name: String): Result<ClickConfig?> = withContext(Dispatchers.IO) {
        try {
            validateConfigName(name).getOrElse { return@withContext Result.failure(it) }
            
            val configs = getAllConfigsInternal()
            val config = configs.values.find { it.name == name }?.toClickConfig()
            
            Log.d(TAG, "Loaded config by name: $name, found: ${config != null}")
            Result.success(config)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config by name: $name", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getAllConfigs(): Result<List<ClickConfig>> = withContext(Dispatchers.IO) {
        try {
            val configs = getAllConfigsInternal()
            val configList = configs.values.map { it.toClickConfig() }
            
            Log.d(TAG, "Loaded ${configList.size} configs")
            Result.success(configList)
        } catch (e: Exception) {
            Log.e(TAG, "Error loading all configs", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteConfig(id: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            validateConfigId(id).getOrElse { return@withContext Result.failure(it) }
            
            val currentConfigs = getAllConfigsInternal().toMutableMap()
            val removedConfig = currentConfigs.remove(id)
            
            if (removedConfig == null) {
                Log.w(TAG, "Config with ID $id not found for deletion")
                return@withContext Result.success(false)
            }
            
            // Delete associated image file if it exists
            deleteImageFile(removedConfig.templateImagePath)
            
            // Save updated configs
            val configsJson = json.encodeToString(currentConfigs)
            val success = sharedPreferences.edit()
                .putString(CONFIGS_KEY, configsJson)
                .commit()
            
            if (success) {
                Log.d(TAG, "Successfully deleted config: ${removedConfig.name}")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to delete config from SharedPreferences")
                Result.failure(IOException("Failed to delete config from SharedPreferences"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting config by ID: $id", e)
            Result.failure(e)
        }
    }
    
    override suspend fun deleteConfigByName(name: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            validateConfigName(name).getOrElse { return@withContext Result.failure(it) }
            
            val currentConfigs = getAllConfigsInternal().toMutableMap()
            val configToDelete = currentConfigs.values.find { it.name == name }
            
            if (configToDelete == null) {
                Log.w(TAG, "Config with name '$name' not found for deletion")
                return@withContext Result.success(false)
            }
            
            // Remove from map
            currentConfigs.remove(configToDelete.id)
            
            // Delete associated image file if it exists
            deleteImageFile(configToDelete.templateImagePath)
            
            // Save updated configs
            val configsJson = json.encodeToString(currentConfigs)
            val success = sharedPreferences.edit()
                .putString(CONFIGS_KEY, configsJson)
                .commit()
            
            if (success) {
                Log.d(TAG, "Successfully deleted config: $name")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to delete config from SharedPreferences")
                Result.failure(IOException("Failed to delete config from SharedPreferences"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting config by name: $name", e)
            Result.failure(e)
        }
    }    

    override suspend fun updateConfig(config: ClickConfig): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Validate config before updating
            if (!config.isValid()) {
                val errors = config.getValidationErrors()
                Log.w(TAG, "Invalid config for update: ${errors.joinToString(", ")}")
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid config: ${errors.joinToString(", ")}")
                )
            }
            
            val currentConfigs = getAllConfigsInternal().toMutableMap()
            
            // Check if config exists
            if (!currentConfigs.containsKey(config.id)) {
                Log.w(TAG, "Config with ID ${config.id} not found for update")
                return@withContext Result.success(false)
            }
            
            // Check if name conflicts with another config
            val existingWithSameName = currentConfigs.values.find { 
                it.name == config.name && it.id != config.id 
            }
            if (existingWithSameName != null) {
                return@withContext Result.failure(
                    IllegalArgumentException("Configuration with name '${config.name}' already exists")
                )
            }
            
            // Update config
            currentConfigs[config.id] = config.toSerializable()
            
            // Save to SharedPreferences
            val configsJson = json.encodeToString(currentConfigs)
            val success = sharedPreferences.edit()
                .putString(CONFIGS_KEY, configsJson)
                .commit()
            
            if (success) {
                Log.d(TAG, "Successfully updated config: ${config.name}")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to update config in SharedPreferences")
                Result.failure(IOException("Failed to update config in SharedPreferences"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating config: ${config.name}", e)
            Result.failure(e)
        }
    }
    
    override suspend fun configExists(name: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            validateConfigName(name).getOrElse { return@withContext Result.failure(it) }
            
            val configs = getAllConfigsInternal()
            val exists = configs.values.any { it.name == name }
            
            Log.d(TAG, "Config exists check for '$name': $exists")
            Result.success(exists)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if config exists: $name", e)
            Result.failure(e)
        }
    }
    
    override suspend fun getConfigCount(): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val configs = getAllConfigsInternal()
            val count = configs.size
            
            Log.d(TAG, "Config count: $count")
            Result.success(count)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting config count", e)
            Result.failure(e)
        }
    }
    
    override suspend fun clearAllConfigs(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            // Get current configs to delete associated image files
            val currentConfigs = getAllConfigsInternal()
            
            // Delete all associated image files
            currentConfigs.values.forEach { config ->
                deleteImageFile(config.templateImagePath)
            }
            
            // Clear SharedPreferences
            val success = sharedPreferences.edit()
                .remove(CONFIGS_KEY)
                .commit()
            
            if (success) {
                Log.d(TAG, "Successfully cleared all configs")
                Result.success(true)
            } else {
                Log.e(TAG, "Failed to clear configs from SharedPreferences")
                Result.failure(IOException("Failed to clear configs from SharedPreferences"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing all configs", e)
            Result.failure(e)
        }
    }
    
    /**
     * Save a bitmap image to internal storage
     * @param bitmap The bitmap to save
     * @param filename The filename to use (without extension)
     * @return The full path to the saved image file
     */
    suspend fun saveImageFile(bitmap: Bitmap, filename: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (filename.isEmpty()) {
                return@withContext Result.failure(
                    IllegalArgumentException("Filename cannot be empty")
                )
            }
            
            if (bitmap.isRecycled) {
                return@withContext Result.failure(
                    IllegalArgumentException("Bitmap is recycled and cannot be saved")
                )
            }
            
            val imageFile = File(imagesDir, "$filename.png")
            
            FileOutputStream(imageFile).use { outputStream ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                if (!success) {
                    return@withContext Result.failure(
                        IOException("Failed to compress bitmap to file")
                    )
                }
            }
            
            val absolutePath = imageFile.absolutePath
            Log.d(TAG, "Successfully saved image: $absolutePath")
            Result.success(absolutePath)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image file: $filename", e)
            Result.failure(e)
        }
    }
    
    /**
     * Delete an image file from internal storage
     * @param imagePath The full path to the image file
     * @return true if deletion was successful or file didn't exist
     */
    private fun deleteImageFile(imagePath: String): Boolean {
        return try {
            if (imagePath.isEmpty()) {
                return true // Nothing to delete
            }
            
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                val deleted = imageFile.delete()
                if (deleted) {
                    Log.d(TAG, "Successfully deleted image file: $imagePath")
                } else {
                    Log.w(TAG, "Failed to delete image file: $imagePath")
                }
                deleted
            } else {
                Log.d(TAG, "Image file does not exist: $imagePath")
                true // File doesn't exist, consider it "deleted"
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting image file: $imagePath", e)
            false
        }
    }
    
    /**
     * Check if an image file exists
     * @param imagePath The full path to the image file
     * @return true if the file exists and is readable
     */
    suspend fun imageFileExists(imagePath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (imagePath.isEmpty()) {
                return@withContext Result.success(false)
            }
            
            val imageFile = File(imagePath)
            val exists = imageFile.exists() && imageFile.canRead()
            
            Log.d(TAG, "Image file exists check for '$imagePath': $exists")
            Result.success(exists)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if image file exists: $imagePath", e)
            Result.failure(e)
        }
    }
    
    /**
     * Get the images directory for external access
     * @return The File object representing the images directory
     */
    fun getImagesDirectory(): File = imagesDir
    
    /**
     * Validate input parameters for repository operations
     */
    private fun validateConfigId(id: String): Result<Unit> {
        return if (id.isEmpty()) {
            Result.failure(IllegalArgumentException("Config ID cannot be empty"))
        } else {
            Result.success(Unit)
        }
    }
    
    private fun validateConfigName(name: String): Result<Unit> {
        return if (name.isEmpty()) {
            Result.failure(IllegalArgumentException("Config name cannot be empty"))
        } else {
            Result.success(Unit)
        }
    }
    
    /**
     * Internal method to get all configs as a map
     * @return Map of config ID to SerializableConfig
     */
    private fun getAllConfigsInternal(): Map<String, SerializableConfig> {
        return try {
            val configsJson = sharedPreferences.getString(CONFIGS_KEY, null)
            if (configsJson.isNullOrEmpty()) {
                emptyMap()
            } else {
                json.decodeFromString<Map<String, SerializableConfig>>(configsJson)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing configs from SharedPreferences", e)
            emptyMap()
        }
    }
}