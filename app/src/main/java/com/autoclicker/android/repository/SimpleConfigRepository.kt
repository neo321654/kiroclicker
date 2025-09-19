package com.autoclicker.android.repository

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.autoclicker.android.model.ClickConfig
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Simple repository implementation for ConfigViewModel
 * Provides synchronous operations for easier use in ViewModel
 */
class SimpleConfigRepository(private val context: Context) {
    
    companion object {
        private const val TAG = "SimpleConfigRepository"
        private const val PREFS_NAME = "autoclicker_simple_configs"
        private const val CONFIGS_KEY = "saved_configs"
        private const val LAST_USED_KEY = "last_used_config"
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
    
    /**
     * Save configuration with image
     */
    fun saveConfig(config: ClickConfig, image: Bitmap): Boolean {
        return try {
            // Save image first
            val imageFileName = "${config.id}_${System.currentTimeMillis()}"
            val imagePath = saveImageFile(image, imageFileName)
            
            if (imagePath != null) {
                // Update config with image path
                val configWithPath = config.copy(templateImagePath = imagePath)
                
                // Save config to preferences
                val currentConfigs = getAllConfigsInternal().toMutableMap()
                currentConfigs[config.id] = configWithPath.toSerializable()
                
                val configsJson = json.encodeToString(currentConfigs)
                sharedPreferences.edit()
                    .putString(CONFIGS_KEY, configsJson)
                    .apply()
                
                Log.d(TAG, "Successfully saved config: ${config.name}")
                true
            } else {
                Log.e(TAG, "Failed to save image for config: ${config.name}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving config: ${config.name}", e)
            false
        }
    }
    
    /**
     * Load configuration with image by name
     */
    fun loadConfigWithImage(name: String): Pair<ClickConfig, Bitmap>? {
        return try {
            val configs = getAllConfigsInternal()
            val serializableConfig = configs.values.find { it.name == name }
            
            if (serializableConfig != null) {
                val config = serializableConfig.toClickConfig()
                val image = loadImageFile(config.templateImagePath)
                
                if (image != null) {
                    Pair(config, image)
                } else {
                    Log.w(TAG, "Failed to load image for config: $name")
                    null
                }
            } else {
                Log.w(TAG, "Config not found: $name")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading config with image: $name", e)
            null
        }
    }
    
    /**
     * Get all configuration names
     */
    fun getAllConfigNames(): List<String> {
        return try {
            val configs = getAllConfigsInternal()
            configs.values.map { it.name }.sorted()
        } catch (e: Exception) {
            Log.e(TAG, "Error getting config names", e)
            emptyList()
        }
    }
    
    /**
     * Set last used configuration
     */
    fun setLastUsedConfig(config: ClickConfig) {
        try {
            sharedPreferences.edit()
                .putString(LAST_USED_KEY, config.id)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error setting last used config", e)
        }
    }
    
    /**
     * Get last used configuration
     */
    fun getLastUsedConfig(): ClickConfig? {
        return try {
            val lastUsedId = sharedPreferences.getString(LAST_USED_KEY, null)
            if (lastUsedId != null) {
                val configs = getAllConfigsInternal()
                configs[lastUsedId]?.toClickConfig()
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting last used config", e)
            null
        }
    }
    
    /**
     * Delete configuration by name
     */
    fun deleteConfig(name: String): Boolean {
        return try {
            val currentConfigs = getAllConfigsInternal().toMutableMap()
            val configToDelete = currentConfigs.values.find { it.name == name }
            
            if (configToDelete != null) {
                // Delete image file
                deleteImageFile(configToDelete.templateImagePath)
                
                // Remove from configs
                currentConfigs.remove(configToDelete.id)
                
                // Save updated configs
                val configsJson = json.encodeToString(currentConfigs)
                sharedPreferences.edit()
                    .putString(CONFIGS_KEY, configsJson)
                    .apply()
                
                Log.d(TAG, "Successfully deleted config: $name")
                true
            } else {
                Log.w(TAG, "Config not found for deletion: $name")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting config: $name", e)
            false
        }
    }
    
    /**
     * Save image file to internal storage
     */
    private fun saveImageFile(bitmap: Bitmap, filename: String): String? {
        return try {
            val imageFile = File(imagesDir, "$filename.png")
            
            FileOutputStream(imageFile).use { outputStream ->
                val success = bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                if (success) {
                    imageFile.absolutePath
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving image file: $filename", e)
            null
        }
    }
    
    /**
     * Load image file from internal storage
     */
    private fun loadImageFile(imagePath: String): Bitmap? {
        return try {
            if (imagePath.isEmpty()) {
                return null
            }
            
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                BitmapFactory.decodeFile(imagePath)
            } else {
                Log.w(TAG, "Image file does not exist: $imagePath")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading image file: $imagePath", e)
            null
        }
    }
    
    /**
     * Delete image file
     */
    private fun deleteImageFile(imagePath: String): Boolean {
        return try {
            if (imagePath.isEmpty()) {
                return true
            }
            
            val imageFile = File(imagePath)
            if (imageFile.exists()) {
                imageFile.delete()
            } else {
                true // File doesn't exist, consider it deleted
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting image file: $imagePath", e)
            false
        }
    }
    
    /**
     * Get all configs as internal map
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