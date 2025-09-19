package com.autoclicker.android.ui

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.autoclicker.android.model.ClickConfig
import com.autoclicker.android.model.AutoClickState
import com.autoclicker.android.repository.SimpleConfigRepository
import com.autoclicker.android.service.AutoClickService
import kotlinx.coroutines.launch
import java.util.UUID
import java.io.File
import java.io.FileOutputStream

class ConfigViewModel(application: Application) : AndroidViewModel(application) {
    
    private val configRepository = SimpleConfigRepository(application)
    
    // Template image
    private val _templateImage = MutableLiveData<Bitmap?>()
    val templateImage: LiveData<Bitmap?> = _templateImage
    
    // Click coordinates (x, y)
    private val _clickCoordinates = MutableLiveData<Pair<Int, Int>?>()
    val clickCoordinates: LiveData<Pair<Int, Int>?> = _clickCoordinates
    
    // Configuration settings
    private val _interval = MutableLiveData<Long>()
    val interval: LiveData<Long> = _interval
    
    private val _repeatCount = MutableLiveData<Int>()
    val repeatCount: LiveData<Int> = _repeatCount
    
    // Current configuration
    private val _currentConfig = MutableLiveData<ClickConfig?>()
    val currentConfig: LiveData<ClickConfig?> = _currentConfig
    
    // AutoClick state
    private val _autoClickState = MutableLiveData<AutoClickState>()
    val autoClickState: LiveData<AutoClickState> = _autoClickState
    
    // Error messages
    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> = _errorMessage
    
    // Validation state
    private val _isConfigurationValid = MutableLiveData<Boolean>()
    val isConfigurationValid: LiveData<Boolean> = _isConfigurationValid
    
    // Click count
    private val _clickCount = MutableLiveData<Int>()
    val clickCount: LiveData<Int> = _clickCount
    
    // Service communication
    private lateinit var localBroadcastManager: LocalBroadcastManager
    private var serviceReceiver: BroadcastReceiver? = null
    
    init {
        // Initialize with default values
        _autoClickState.value = AutoClickState.Idle
        _interval.value = 1000L
        _repeatCount.value = 10
        _isConfigurationValid.value = false
        _errorMessage.value = ""
        _clickCount.value = 0
        
        // Initialize service communication
        initializeServiceCommunication()
        
        // Load last saved configuration if available
        loadLastConfiguration()
    }
    
    /**
     * Set the template image for matching
     */
    fun setTemplateImage(bitmap: Bitmap) {
        _templateImage.value = bitmap
        validateConfiguration()
        clearError()
        autoSaveCurrentConfiguration()
    }
    
    /**
     * Set click coordinates relative to the template image
     */
    fun setClickCoordinates(x: Int, y: Int) {
        if (x >= 0 && y >= 0) {
            _clickCoordinates.value = Pair(x, y)
            validateConfiguration()
            clearError()
            autoSaveCurrentConfiguration()
        } else {
            setError("Invalid coordinates: ($x, $y)")
        }
    }
    
    /**
     * Set the interval between clicks in milliseconds
     */
    fun setInterval(intervalMs: Long) {
        if (intervalMs >= 100) {
            _interval.value = intervalMs
            validateConfiguration()
            clearError()
            autoSaveCurrentConfiguration()
        } else {
            setError("Interval must be at least 100ms")
        }
    }
    
    /**
     * Set the number of times to repeat the click (-1 for infinite)
     */
    fun setRepeatCount(count: Int) {
        if (count == -1 || count > 0) {
            _repeatCount.value = count
            validateConfiguration()
            clearError()
            autoSaveCurrentConfiguration()
        } else {
            setError("Repeat count must be positive or -1 for infinite")
        }
    }
    
    /**
     * Create a ClickConfig from current settings
     */
    fun createCurrentConfig(name: String = "temp_config"): ClickConfig? {
        val image = _templateImage.value
        val coordinates = _clickCoordinates.value
        val intervalValue = _interval.value ?: 1000L
        val repeatValue = _repeatCount.value ?: 10
        
        return if (image != null && coordinates != null) {
            ClickConfig(
                id = UUID.randomUUID().toString(),
                name = name,
                templateImagePath = "", // Will be set when saving
                clickX = coordinates.first,
                clickY = coordinates.second,
                intervalMs = intervalValue,
                repeatCount = repeatValue,
                threshold = 0.8
            )
        } else {
            null
        }
    }
    
    /**
     * Save current configuration with a name
     */
    fun saveConfiguration(name: String) {
        viewModelScope.launch {
            try {
                val config = createCurrentConfig(name)
                val image = _templateImage.value
                
                if (config != null && image != null) {
                    val success = configRepository.saveConfig(config, image)
                    if (success) {
                        _currentConfig.value = config
                        clearError()
                    } else {
                        setError("Failed to save configuration")
                    }
                } else {
                    setError("Invalid configuration - cannot save")
                }
            } catch (e: Exception) {
                setError("Error saving configuration: ${e.message}")
            }
        }
    }
    
    /**
     * Load a configuration by name
     */
    fun loadConfiguration(name: String) {
        viewModelScope.launch {
            try {
                val configWithImage = configRepository.loadConfigWithImage(name)
                if (configWithImage != null) {
                    val (config, image) = configWithImage
                    
                    // Update all fields
                    _templateImage.value = image
                    _clickCoordinates.value = Pair(config.clickX, config.clickY)
                    _interval.value = config.intervalMs
                    _repeatCount.value = config.repeatCount
                    _currentConfig.value = config
                    
                    validateConfiguration()
                    clearError()
                } else {
                    setError("Configuration '$name' not found")
                }
            } catch (e: Exception) {
                setError("Error loading configuration: ${e.message}")
            }
        }
    }
    
    /**
     * Get all saved configuration names
     */
    fun getAllConfigurationNames(): List<String> {
        return try {
            configRepository.getAllConfigNames()
        } catch (e: Exception) {
            setError("Error loading configurations: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Delete a configuration by name
     */
    fun deleteConfiguration(name: String): Boolean {
        return try {
            val success = configRepository.deleteConfig(name)
            if (success) {
                clearError()
            } else {
                setError("Configuration '$name' not found")
            }
            success
        } catch (e: Exception) {
            setError("Error deleting configuration: ${e.message}")
            false
        }
    }
    
    /**
     * Auto-save current configuration as temporary config
     */
    private fun autoSaveCurrentConfiguration() {
        if (_isConfigurationValid.value == true) {
            val config = createCurrentConfig("_auto_save_temp")
            val image = _templateImage.value
            if (config != null && image != null) {
                viewModelScope.launch {
                    try {
                        configRepository.saveConfig(config, image)
                        configRepository.setLastUsedConfig(config)
                    } catch (e: Exception) {
                        // Ignore auto-save errors to not interrupt user experience
                        Log.w("ConfigViewModel", "Auto-save failed: ${e.message}")
                    }
                }
            }
        }
    }
    
    /**
     * Start the auto-click process
     */
    fun startAutoClick() {
        if (_isConfigurationValid.value == true) {
            val image = _templateImage.value
            val coordinates = _clickCoordinates.value
            
            if (image != null && coordinates != null) {
                // Get the service instance
                val service = AutoClickService.getInstance()
                if (service != null && service.isReady()) {
                    viewModelScope.launch {
                        try {
                            // Save image to temporary file
                            val tempImageFile = File(getApplication<Application>().cacheDir, "temp_template_${System.currentTimeMillis()}.png")
                            val outputStream = FileOutputStream(tempImageFile)
                            image.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                            outputStream.close()
                            
                            // Create config with image path
                            val config = ClickConfig(
                                id = UUID.randomUUID().toString(),
                                name = "temp_config",
                                templateImagePath = tempImageFile.absolutePath,
                                clickX = coordinates.first,
                                clickY = coordinates.second,
                                intervalMs = _interval.value ?: 1000L,
                                repeatCount = _repeatCount.value ?: 10,
                                threshold = 0.8
                            )
                            
                            // Start auto-click in service
                            service.startAutoClick(config)
                            clearError()
                        } catch (e: Exception) {
                            setError("Error starting auto-click: ${e.message}")
                        }
                    }
                } else {
                    setError("AutoClick service not available")
                }
            } else {
                setError("Template image or coordinates not available")
            }
        } else {
            setError("Configuration is not valid")
        }
    }
    
    /**
     * Stop the auto-click process
     */
    fun stopAutoClick() {
        val service = AutoClickService.getInstance()
        if (service != null) {
            service.stopAutoClick()
        } else {
            // Update state locally if service is not available
            _autoClickState.value = AutoClickState.Idle
        }
        clearError()
    }
    
    /**
     * Update the auto-click state (called by service or other components)
     */
    fun updateAutoClickState(state: AutoClickState) {
        _autoClickState.value = state
    }
    
    /**
     * Validate the current configuration
     */
    private fun validateConfiguration() {
        val hasImage = _templateImage.value != null
        val hasCoordinates = _clickCoordinates.value != null
        val hasValidInterval = (_interval.value ?: 0) >= 100
        val hasValidRepeatCount = (_repeatCount.value ?: 0) > 0 || (_repeatCount.value ?: 0) == -1
        
        _isConfigurationValid.value = hasImage && hasCoordinates && hasValidInterval && hasValidRepeatCount
    }
    
    /**
     * Set an error message
     */
    private fun setError(message: String) {
        _errorMessage.value = message
    }
    
    /**
     * Clear the current error message
     */
    private fun clearError() {
        _errorMessage.value = ""
    }
    
    /**
     * Load the last saved configuration on startup
     */
    private fun loadLastConfiguration() {
        viewModelScope.launch {
            try {
                val lastConfig = configRepository.getLastUsedConfig()
                if (lastConfig != null) {
                    // Load the last used configuration
                    val configWithImage = configRepository.loadConfigWithImage(lastConfig.name)
                    if (configWithImage != null) {
                        val (config, image) = configWithImage
                        
                        // Update all fields without triggering auto-save
                        _templateImage.value = image
                        _clickCoordinates.value = Pair(config.clickX, config.clickY)
                        _interval.value = config.intervalMs
                        _repeatCount.value = config.repeatCount
                        _currentConfig.value = config
                        
                        validateConfiguration()
                        clearError()
                        
                        Log.d("ConfigViewModel", "Restored last configuration: ${config.name}")
                    }
                }
            } catch (e: Exception) {
                // Ignore errors when loading last configuration
                // App should still work without it
                Log.w("ConfigViewModel", "Failed to load last configuration: ${e.message}")
            }
        }
    }
    
    /**
     * Reset all configuration to default values
     */
    fun resetConfiguration() {
        _templateImage.value = null
        _clickCoordinates.value = null
        _interval.value = 1000L
        _repeatCount.value = 10
        _currentConfig.value = null
        _autoClickState.value = AutoClickState.Idle
        validateConfiguration()
        clearError()
    }
    
    /**
     * Initialize service communication
     */
    private fun initializeServiceCommunication() {
        localBroadcastManager = LocalBroadcastManager.getInstance(getApplication())
        
        serviceReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AutoClickService.ACTION_STATE_CHANGED -> {
                        val stateName = intent.getStringExtra(AutoClickService.EXTRA_STATE)
                        val clickCount = intent.getIntExtra(AutoClickService.EXTRA_CLICK_COUNT, 0)
                        val errorMessage = intent.getStringExtra(AutoClickService.EXTRA_ERROR_MESSAGE)
                        
                        // Update state based on broadcast
                        val newState = when (stateName) {
                            "Idle" -> AutoClickState.Idle
                            "Searching" -> AutoClickState.Searching
                            "Clicking" -> AutoClickState.Clicking
                            "Waiting" -> AutoClickState.Waiting
                            "Completed" -> AutoClickState.Completed(clickCount)
                            "Error" -> AutoClickState.Error(errorMessage ?: "Unknown error")
                            else -> AutoClickState.Idle
                        }
                        
                        _autoClickState.value = newState
                        _clickCount.value = clickCount
                    }
                    AutoClickService.ACTION_CLICK_COUNT_UPDATED -> {
                        val clickCount = intent.getIntExtra(AutoClickService.EXTRA_CLICK_COUNT, 0)
                        _clickCount.value = clickCount
                    }
                }
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(AutoClickService.ACTION_STATE_CHANGED)
            addAction(AutoClickService.ACTION_CLICK_COUNT_UPDATED)
        }
        
        localBroadcastManager.registerReceiver(serviceReceiver!!, filter)
    }
    
    /**
     * Cleanup service communication
     */
    private fun cleanupServiceCommunication() {
        serviceReceiver?.let { receiver ->
            try {
                localBroadcastManager.unregisterReceiver(receiver)
                serviceReceiver = null
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
        }
    }
    
    /**
     * Check if the AutoClick service is available and ready
     */
    fun isServiceReady(): Boolean {
        val service = AutoClickService.getInstance()
        return service?.isReady() == true
    }
    
    /**
     * Get current service state
     */
    fun getServiceState(): AutoClickState {
        val service = AutoClickService.getInstance()
        return service?.getCurrentState() ?: AutoClickState.Idle
    }
    
    override fun onCleared() {
        super.onCleared()
        
        // Cleanup service communication
        cleanupServiceCommunication()
        
        // Save current configuration as last used if valid
        if (_isConfigurationValid.value == true) {
            val config = createCurrentConfig("_auto_save_final")
            val image = _templateImage.value
            if (config != null && image != null) {
                // Save asynchronously without waiting
                viewModelScope.launch {
                    try {
                        configRepository.saveConfig(config, image)
                        configRepository.setLastUsedConfig(config)
                        Log.d("ConfigViewModel", "Final auto-save completed")
                    } catch (e: Exception) {
                        // Ignore errors during cleanup
                        Log.w("ConfigViewModel", "Final auto-save failed: ${e.message}")
                    }
                }
            }
        }
    }
}