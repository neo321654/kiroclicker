package com.autoclicker.android.repository

import com.autoclicker.android.model.ClickConfig

/**
 * Repository interface for managing ClickConfig persistence
 * Provides CRUD operations for configuration management
 */
interface ConfigRepository {
    
    /**
     * Save a configuration to persistent storage
     * @param config The configuration to save
     * @return true if save was successful, false otherwise
     * @throws IllegalArgumentException if config is invalid
     */
    suspend fun saveConfig(config: ClickConfig): Result<Boolean>
    
    /**
     * Load a configuration by its ID
     * @param id The unique identifier of the configuration
     * @return The configuration if found, null otherwise
     */
    suspend fun loadConfig(id: String): Result<ClickConfig?>
    
    /**
     * Load a configuration by its name
     * @param name The name of the configuration
     * @return The configuration if found, null otherwise
     */
    suspend fun loadConfigByName(name: String): Result<ClickConfig?>
    
    /**
     * Get all saved configurations
     * @return List of all configurations, empty list if none exist
     */
    suspend fun getAllConfigs(): Result<List<ClickConfig>>
    
    /**
     * Delete a configuration by its ID
     * @param id The unique identifier of the configuration to delete
     * @return true if deletion was successful, false if config not found
     */
    suspend fun deleteConfig(id: String): Result<Boolean>
    
    /**
     * Delete a configuration by its name
     * @param name The name of the configuration to delete
     * @return true if deletion was successful, false if config not found
     */
    suspend fun deleteConfigByName(name: String): Result<Boolean>
    
    /**
     * Update an existing configuration
     * @param config The updated configuration
     * @return true if update was successful, false if config not found
     * @throws IllegalArgumentException if config is invalid
     */
    suspend fun updateConfig(config: ClickConfig): Result<Boolean>
    
    /**
     * Check if a configuration with the given name exists
     * @param name The name to check
     * @return true if a configuration with this name exists
     */
    suspend fun configExists(name: String): Result<Boolean>
    
    /**
     * Get the count of saved configurations
     * @return The number of saved configurations
     */
    suspend fun getConfigCount(): Result<Int>
    
    /**
     * Clear all saved configurations
     * @return true if all configurations were cleared successfully
     */
    suspend fun clearAllConfigs(): Result<Boolean>
}