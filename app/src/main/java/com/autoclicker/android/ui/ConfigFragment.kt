package com.autoclicker.android.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.autoclicker.android.utils.AccessibilityUtils
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.autoclicker.android.R
import com.autoclicker.android.databinding.FragmentConfigBinding
import com.autoclicker.android.model.AutoClickState
import java.io.InputStream

class ConfigFragment : Fragment() {
    
    private var _binding: FragmentConfigBinding? = null
    private val binding get() = _binding!!
    
    private lateinit var viewModel: ConfigViewModel
    
    // Activity result launchers
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleImageSelected(uri)
            }
        }
    }
    
    private val screenshotLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val bitmap = result.data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                viewModel.setTemplateImage(it)
            }
        }
    }
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (!allGranted) {
            Toast.makeText(requireContext(), "Permissions required for image selection", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConfigBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        viewModel = ViewModelProvider(this)[ConfigViewModel::class.java]
        
        setupUI()
        observeViewModel()
        
        // Initialize monitoring
        startMonitoring()
    }
    
    private fun setupUI() {
        setupImageSelection()
        setupCoordinateSelection()
        setupSettingsInputs()
        setupControlButtons()
        setupConfigManagement()
    }
    
    private fun setupImageSelection() {
        binding.btnSelectImage.setOnClickListener {
            checkPermissionsAndSelectImage()
        }
        
        binding.btnTakeScreenshot.setOnClickListener {
            takeScreenshot()
        }
    }
    
    private fun setupCoordinateSelection() {
        binding.ivTemplatePreview.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                val x = event.x.toInt()
                val y = event.y.toInt()
                viewModel.setClickCoordinates(x, y)
                true
            } else {
                false
            }
        }
    }
    
    private fun setupSettingsInputs() {
        // Interval input
        binding.etInterval.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val interval = s?.toString()?.toLongOrNull() ?: 1000L
                viewModel.setInterval(interval)
            }
        })
        
        // Repeat count input
        binding.etRepeatCount.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (!binding.cbInfiniteMode.isChecked) {
                    val count = s?.toString()?.toIntOrNull() ?: 10
                    viewModel.setRepeatCount(count)
                }
            }
        })
        
        // Infinite mode checkbox
        binding.cbInfiniteMode.setOnCheckedChangeListener { _, isChecked ->
            binding.etRepeatCount.isEnabled = !isChecked
            if (isChecked) {
                viewModel.setRepeatCount(-1) // -1 indicates infinite mode
            } else {
                val count = binding.etRepeatCount.text.toString().toIntOrNull() ?: 10
                viewModel.setRepeatCount(count)
            }
        }

        // Search radius input
        binding.etSearchRadius.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val radius = s?.toString()?.toIntOrNull() ?: 30
                viewModel.setSearchRadius(radius)
            }
        })
    }
    
    private fun setupControlButtons() {
        binding.btnStartAutoclick.setOnClickListener {
            if (validateConfiguration()) {
                // Check accessibility service
                val mainActivity = activity as? MainActivity
                if (AccessibilityUtils.isAccessibilityServiceEnabled(requireContext())) {
                    // Check if service is ready
                    if (viewModel.isServiceReady()) {
                        viewModel.startAutoClick()
                        Toast.makeText(requireContext(), "Starting auto-click...", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "AutoClick service not ready. Please try again.", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    Toast.makeText(requireContext(), "Please enable accessibility service first", Toast.LENGTH_LONG).show()
                    AccessibilityUtils.requestAccessibilityPermission(requireContext())
                }
            }
        }
        
        binding.btnStopAutoclick.setOnClickListener {
            viewModel.stopAutoClick()
            Toast.makeText(requireContext(), "Stopping auto-click...", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupConfigManagement() {
        binding.btnSaveConfig.setOnClickListener {
            showSaveConfigDialog()
        }
        
        binding.btnLoadConfig.setOnClickListener {
            showLoadConfigDialog()
        }
    }
    
    private fun observeViewModel() {
        // Template image
        viewModel.templateImage.observe(viewLifecycleOwner) { bitmap ->
            if (bitmap != null) {
                binding.ivTemplatePreview.setImageBitmap(bitmap)
                binding.ivTemplatePreview.visibility = View.VISIBLE
            } else {
                binding.ivTemplatePreview.visibility = View.GONE
            }
        }
        
        // Click coordinates
        viewModel.clickCoordinates.observe(viewLifecycleOwner) { coordinates ->
            if (coordinates != null) {
                binding.tvCoordinatesDisplay.text = "Coordinates: (${coordinates.first}, ${coordinates.second})"
            } else {
                binding.tvCoordinatesDisplay.text = "Coordinates: Not set"
            }
        }
        
        // AutoClick state
        viewModel.autoClickState.observe(viewLifecycleOwner) { state ->
            updateUIForState(state)
            handleStateChange(state)
        }
        
        // Error messages
        viewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                handleError(error)
            }
        }
        
        // Click count
        viewModel.clickCount.observe(viewLifecycleOwner) { count ->
            binding.tvClickCounter.text = count.toString()
        }
        
        // Configuration validation
        viewModel.isConfigurationValid.observe(viewLifecycleOwner) { isValid ->
            binding.btnStartAutoclick.isEnabled = isValid && 
                (viewModel.autoClickState.value is AutoClickState.Idle || 
                 viewModel.autoClickState.value is AutoClickState.Completed ||
                 viewModel.autoClickState.value is AutoClickState.Error)
        }
    }
    
    private fun updateUIForState(state: AutoClickState) {
        when (state) {
            is AutoClickState.Idle -> {
                binding.tvStatus.text = getString(R.string.status_idle)
                binding.tvStatus.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), android.R.color.darker_gray)
                )
                binding.btnStartAutoclick.isEnabled = true
                binding.btnStopAutoclick.isEnabled = false
                
                // Enable configuration controls when idle
                enableConfigurationControls(true)
            }
            is AutoClickState.Searching -> {
                binding.tvStatus.text = getString(R.string.status_searching)
                binding.tvStatus.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_blue_light)
                )
                binding.btnStartAutoclick.isEnabled = false
                binding.btnStopAutoclick.isEnabled = true
                
                // Disable configuration controls during operation
                enableConfigurationControls(false)
            }
            is AutoClickState.Clicking -> {
                val currentCount = viewModel.clickCount.value ?: 0
                binding.tvStatus.text = "${getString(R.string.status_clicking)} ($currentCount)"
                binding.tvStatus.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light)
                )
                binding.btnStartAutoclick.isEnabled = false
                binding.btnStopAutoclick.isEnabled = true
                
                enableConfigurationControls(false)
            }
            is AutoClickState.Waiting -> {
                val currentCount = viewModel.clickCount.value ?: 0
                val totalCount = viewModel.repeatCount.value ?: -1
                val statusText = if (totalCount > 0) {
                    "${getString(R.string.status_waiting)} ($currentCount/$totalCount)"
                } else {
                    "${getString(R.string.status_waiting)} ($currentCount)"
                }
                binding.tvStatus.text = statusText
                binding.tvStatus.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_purple)
                )
                binding.btnStartAutoclick.isEnabled = false
                binding.btnStopAutoclick.isEnabled = true
                
                enableConfigurationControls(false)
            }
            is AutoClickState.Completed -> {
                binding.tvStatus.text = getString(R.string.status_completed, state.clickCount)
                binding.tvStatus.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_green_light)
                )
                binding.btnStartAutoclick.isEnabled = true
                binding.btnStopAutoclick.isEnabled = false
                
                enableConfigurationControls(true)
            }
            is AutoClickState.Error -> {
                binding.tvStatus.text = getString(R.string.status_error, state.message)
                binding.tvStatus.setBackgroundColor(
                    ContextCompat.getColor(requireContext(), android.R.color.holo_red_light)
                )
                binding.btnStartAutoclick.isEnabled = true
                binding.btnStopAutoclick.isEnabled = false
                
                enableConfigurationControls(true)
            }
        }
    }
    
    /**
     * Enable or disable configuration controls during auto-click operation
     */
    private fun enableConfigurationControls(enabled: Boolean) {
        binding.btnSelectImage.isEnabled = enabled
        binding.btnTakeScreenshot.isEnabled = enabled
        binding.etInterval.isEnabled = enabled
        binding.etRepeatCount.isEnabled = enabled && !binding.cbInfiniteMode.isChecked
        binding.cbInfiniteMode.isEnabled = enabled
        binding.btnSaveConfig.isEnabled = enabled
        binding.btnLoadConfig.isEnabled = enabled
        
        // Keep image preview touchable for coordinate selection only when idle
        binding.ivTemplatePreview.isClickable = enabled
    }
    
    /**
     * Handle state changes with appropriate user feedback
     */
    private fun handleStateChange(state: AutoClickState) {
        when (state) {
            is AutoClickState.Searching -> {
                // Show subtle feedback that search is active
                // Could add a progress indicator here if needed
            }
            is AutoClickState.Clicking -> {
                // Provide haptic feedback for click
                try {
                    @Suppress("DEPRECATION")
                    binding.root.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                } catch (e: Exception) {
                    // Ignore haptic feedback errors
                }
            }
            is AutoClickState.Completed -> {
                Toast.makeText(
                    requireContext(), 
                    getString(R.string.autoclick_completed), 
                    Toast.LENGTH_SHORT
                ).show()
            }
            is AutoClickState.Error -> {
                // Error handling is done in handleError method
            }
            else -> {
                // No special handling needed for other states
            }
        }
    }
    
    /**
     * Handle errors with appropriate user feedback and suggestions
     */
    private fun handleError(error: String) {
        when {
            error.contains("service not ready", ignoreCase = true) -> {
                showErrorDialog(
                    "Service Not Ready",
                    "The AutoClick service is not ready. Please ensure accessibility service is enabled and try again.",
                    "Open Settings"
                ) {
                    AccessibilityUtils.requestAccessibilityPermission(requireContext())
                }
            }
            error.contains("accessibility", ignoreCase = true) -> {
                showErrorDialog(
                    "Accessibility Required",
                    "Accessibility service is required for auto-clicking. Please enable it in system settings.",
                    "Open Settings"
                ) {
                    AccessibilityUtils.requestAccessibilityPermission(requireContext())
                }
            }
            error.contains("template", ignoreCase = true) -> {
                showErrorDialog(
                    "Template Error",
                    "There was an issue with the template image. Please select a new image and try again.",
                    "OK"
                ) {
                    // Focus on image selection
                    binding.btnSelectImage.requestFocus()
                }
            }
            error.contains("coordinates", ignoreCase = true) -> {
                showErrorDialog(
                    "Coordinates Error",
                    "Invalid click coordinates. Please tap on the template image to set new coordinates.",
                    "OK"
                ) {
                    // Focus on image preview for coordinate selection
                    if (binding.ivTemplatePreview.visibility == View.VISIBLE) {
                        binding.ivTemplatePreview.requestFocus()
                    }
                }
            }
            else -> {
                // Generic error handling
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show()
            }
        }
    }
    
    /**
     * Show error dialog with action button
     */
    private fun showErrorDialog(
        title: String,
        message: String,
        actionText: String,
        action: (() -> Unit)? = null
    ) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(actionText) { _, _ ->
                action?.invoke()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    private fun checkPermissionsAndSelectImage() {
        val permissions = mutableListOf<String>()
        
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_EXTERNAL_STORAGE) 
            != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        
        // For Android 13+
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) 
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            selectImageFromGallery()
        }
    }
    
    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        imagePickerLauncher.launch(intent)
    }
    
    private fun takeScreenshot() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
        } else {
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            screenshotLauncher.launch(intent)
        }
    }
    
    private fun handleImageSelected(uri: Uri) {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            
            if (bitmap != null) {
                viewModel.setTemplateImage(bitmap)
            } else {
                Toast.makeText(requireContext(), "Failed to load image", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Error loading image: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun validateConfiguration(): Boolean {
        val isValid = viewModel.isConfigurationValid.value == true
        
        if (!isValid) {
            showValidationStatus()
            return false
        }
        
        // Additional runtime validations
        val interval = binding.etInterval.text.toString().toLongOrNull()
        if (interval == null || interval < 100) {
            Toast.makeText(requireContext(), getString(R.string.error_invalid_interval), Toast.LENGTH_SHORT).show()
            return false
        }
        
        if (!binding.cbInfiniteMode.isChecked) {
            val repeatCount = binding.etRepeatCount.text.toString().toIntOrNull()
            if (repeatCount == null || repeatCount <= 0) {
                Toast.makeText(requireContext(), getString(R.string.error_invalid_repeat_count), Toast.LENGTH_SHORT).show()
                return false
            }
        }
        
        return true
    }
    
    /**
     * Start monitoring service status and updates
     */
    private fun startMonitoring() {
        // Sync with service state on startup
        val service = com.autoclicker.android.service.AutoClickService.getInstance()
        if (service != null) {
            val currentState = service.getCurrentState()
            val currentCount = service.getClickCount()
            
            viewModel.updateAutoClickState(currentState)
            // Update click count if different from ViewModel
            if (currentCount != (viewModel.clickCount.value ?: 0)) {
                // The service broadcast will update this automatically
            }
        }
    }
    
    /**
     * Provide detailed validation feedback to user
     */
    private fun showValidationStatus() {
        val hasImage = viewModel.templateImage.value != null
        val hasCoordinates = viewModel.clickCoordinates.value != null
        val hasValidInterval = (viewModel.interval.value ?: 0) >= 100
        val hasValidRepeatCount = (viewModel.repeatCount.value ?: 0) > 0 || 
                                 (viewModel.repeatCount.value ?: 0) == -1
        
        val issues = mutableListOf<String>()
        
        if (!hasImage) issues.add("• Select a template image")
        if (!hasCoordinates) issues.add("• Set click coordinates by tapping the image")
        if (!hasValidInterval) issues.add("• Set interval to at least 100ms")
        if (!hasValidRepeatCount) issues.add("• Set a valid repeat count")
        
        if (issues.isNotEmpty()) {
            val message = "Configuration issues:\n${issues.joinToString("\n")}"
            Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
        }
    }
    
    /**
     * Show dialog to save current configuration
     */
    private fun showSaveConfigDialog() {
        if (viewModel.isConfigurationValid.value != true) {
            Toast.makeText(requireContext(), "Please complete the configuration first", Toast.LENGTH_SHORT).show()
            return
        }
        
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_save_config, null)
        val editText = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.et_config_name)
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.save_config_dialog_title))
            .setView(dialogView)
            .setPositiveButton(getString(R.string.save)) { _, _ ->
                val configName = editText.text.toString().trim()
                if (configName.isEmpty()) {
                    Toast.makeText(requireContext(), getString(R.string.error_empty_config_name), Toast.LENGTH_SHORT).show()
                } else {
                    saveConfiguration(configName)
                }
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
        
        // Focus on the edit text and show keyboard
        editText.requestFocus()
    }
    
    /**
     * Show dialog to load saved configurations
     */
    private fun showLoadConfigDialog() {
        val configNames = viewModel.getAllConfigurationNames()
        
        if (configNames.isEmpty()) {
            Toast.makeText(requireContext(), getString(R.string.no_saved_configs), Toast.LENGTH_SHORT).show()
            return
        }
        
        // Create custom dialog with list and delete buttons
        val dialogView = LayoutInflater.from(requireContext()).inflate(android.R.layout.simple_list_item_1, null)
        
        // Create a custom adapter for the list
        val adapter = ConfigListAdapter(configNames) { configName, action ->
            when (action) {
                ConfigListAdapter.Action.LOAD -> {
                    loadConfiguration(configName)
                }
                ConfigListAdapter.Action.DELETE -> {
                    showDeleteConfigDialog(configName)
                }
            }
        }
        
        val listView = android.widget.ListView(requireContext())
        listView.adapter = adapter
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.load_config_dialog_title))
            .setView(listView)
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Show confirmation dialog for deleting a configuration
     */
    private fun showDeleteConfigDialog(configName: String) {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(getString(R.string.delete_config_confirm_title))
            .setMessage(getString(R.string.delete_config_confirm_message, configName))
            .setPositiveButton(getString(R.string.delete)) { _, _ ->
                deleteConfiguration(configName)
            }
            .setNegativeButton(getString(R.string.cancel)) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }
    
    /**
     * Save current configuration with given name
     */
    private fun saveConfiguration(configName: String) {
        try {
            viewModel.saveConfiguration(configName)
            Toast.makeText(requireContext(), getString(R.string.config_saved_successfully), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.error_saving_config), Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Load configuration with given name
     */
    private fun loadConfiguration(configName: String) {
        try {
            viewModel.loadConfiguration(configName)
            Toast.makeText(requireContext(), getString(R.string.config_loaded_successfully), Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.error_loading_config), Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Delete configuration with given name
     */
    private fun deleteConfiguration(configName: String) {
        try {
            val success = viewModel.deleteConfiguration(configName)
            if (success) {
                Toast.makeText(requireContext(), getString(R.string.config_deleted_successfully), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), getString(R.string.error_deleting_config), Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), getString(R.string.error_deleting_config), Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Refresh monitoring when fragment resumes
        startMonitoring()
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}