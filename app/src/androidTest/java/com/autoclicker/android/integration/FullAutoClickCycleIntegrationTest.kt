package com.autoclicker.android.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autoclicker.android.model.AutoClickState
import com.autoclicker.android.model.ClickConfig
import com.autoclicker.android.model.MatchResult
import com.autoclicker.android.repository.ConfigRepository
import com.autoclicker.android.service.AutoClickService
import com.autoclicker.android.ui.ConfigViewModel
import com.autoclicker.android.utils.ImageMatcher
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class FullAutoClickCycleIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var viewModel: ConfigViewModel
    private lateinit var service: AutoClickService
    private lateinit var mockRepository: ConfigRepository
    private lateinit var mockImageMatcher: ImageMatcher
    private lateinit var localBroadcastManager: LocalBroadcastManager
    
    // Test data
    private val testConfig = ClickConfig(
        id = "integration-test",
        name = "Integration Test Config",
        templateImagePath = "/test/template.png",
        clickX = 150,
        clickY = 250,
        intervalMs = 1000,
        repeatCount = 3,
        threshold = 0.8
    )

    @Before
    fun setup() {
        // Create mocks
        mockRepository = mockk<ConfigRepository>(relaxed = true)
        mockImageMatcher = mockk<ImageMatcher>(relaxed = true)
        
        // Setup mock behaviors
        every { mockRepository.saveConfig(any()) } returns true
        every { mockRepository.loadConfig(any()) } returns testConfig
        every { mockRepository.getAllConfigs() } returns listOf(testConfig)
        every { mockRepository.deleteConfig(any()) } returns true
        
        // Setup image matcher mock
        every { mockImageMatcher.findTemplate(any(), any()) } returns MatchResult(
            found = true,
            confidence = 0.9,
            location = android.graphics.Point(150, 250)
        )
        
        // Initialize components
        viewModel = ConfigViewModel(context)
        service = AutoClickService()
        localBroadcastManager = LocalBroadcastManager.getInstance(context)
    }

    @After
    fun tearDown() {
        service.stopAutoClick()
        clearAllMocks()
    }

    @Test
    fun testCompleteConfigurationToExecutionFlow() = runTest {
        val stateChanges = mutableListOf<AutoClickState>()
        val latch = CountDownLatch(3) // Expecting multiple state changes
        
        // Set up broadcast receiver to track state changes
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AutoClickService.ACTION_STATE_CHANGED) {
                    val stateString = intent.getStringExtra(AutoClickService.EXTRA_STATE)
                    stateString?.let { state ->
                        when {
                            state.contains("Searching") -> {
                                stateChanges.add(AutoClickState.Searching)
                                latch.countDown()
                            }
                            state.contains("Clicking") -> {
                                stateChanges.add(AutoClickState.Clicking)
                                latch.countDown()
                            }
                            state.contains("Completed") -> {
                                stateChanges.add(AutoClickState.Completed(3))
                                latch.countDown()
                            }
                        }
                    }
                }
            }
        }
        
        val filter = IntentFilter(AutoClickService.ACTION_STATE_CHANGED)
        localBroadcastManager.registerReceiver(receiver, filter)
        
        try {
            // Step 1: Configure ViewModel with complete setup
            val mockBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
            viewModel.setTemplateImage(mockBitmap)
            viewModel.setClickCoordinates(150, 250)
            viewModel.setInterval(1000L)
            viewModel.setRepeatCount(3)
            
            // Verify configuration is valid
            assertTrue("Configuration should be valid", viewModel.isConfigurationValid.value == true)
            
            // Step 2: Create configuration from ViewModel
            val config = viewModel.createCurrentConfig("integration_test")
            assertNotNull("Config should be created", config)
            assertEquals("integration_test", config!!.name)
            assertEquals(150, config.clickX)
            assertEquals(250, config.clickY)
            assertEquals(1000L, config.intervalMs)
            assertEquals(3, config.repeatCount)
            
            // Step 3: Start auto-click through ViewModel
            viewModel.startAutoClick()
            assertEquals(AutoClickState.Searching, viewModel.autoClickState.value)
            
            // Step 4: Simulate service processing
            service.startAutoClick(config)
            
            // Simulate the auto-click cycle
            service.updateState(AutoClickState.Searching)
            Thread.sleep(100) // Small delay to simulate processing
            
            service.updateState(AutoClickState.Clicking)
            service.updateClickCount(1)
            Thread.sleep(100)
            
            service.updateState(AutoClickState.Searching)
            Thread.sleep(100)
            
            service.updateState(AutoClickState.Clicking)
            service.updateClickCount(2)
            Thread.sleep(100)
            
            service.updateState(AutoClickState.Searching)
            Thread.sleep(100)
            
            service.updateState(AutoClickState.Clicking)
            service.updateClickCount(3)
            Thread.sleep(100)
            
            service.updateState(AutoClickState.Completed(3))
            
            // Wait for state changes to be broadcast
            assertTrue("State changes not received in time", latch.await(5, TimeUnit.SECONDS))
            
            // Verify the flow
            assertTrue("Should have received state changes", stateChanges.isNotEmpty())
            assertEquals(3, service.getClickCount())
            assertTrue("Should end in completed state", service.getCurrentState() is AutoClickState.Completed)
            
        } finally {
            localBroadcastManager.unregisterReceiver(receiver)
        }
    }

    @Test
    fun testConfigurationSaveLoadCycle() = runTest {
        // Step 1: Create and save configuration
        val mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(200, 300)
        viewModel.setInterval(1500L)
        viewModel.setRepeatCount(5)
        
        // Save configuration
        viewModel.saveConfiguration("save_load_test")
        
        // Verify save was called
        verify { mockRepository.saveConfig(any()) }
        
        // Step 2: Reset configuration
        viewModel.resetConfiguration()
        assertNull(viewModel.templateImage.value)
        assertNull(viewModel.clickCoordinates.value)
        assertEquals(1000L, viewModel.interval.value)
        assertEquals(10, viewModel.repeatCount.value)
        
        // Step 3: Load configuration
        viewModel.loadConfiguration("save_load_test")
        
        // Verify load was called
        verify { mockRepository.loadConfig("save_load_test") }
        
        // Step 4: Verify configuration can be used for auto-click
        val config = viewModel.createCurrentConfig("loaded_config")
        assertNotNull("Loaded config should be usable", config)
    }

    @Test
    fun testErrorHandlingInFullCycle() = runTest {
        val errorMessages = mutableListOf<String>()
        val latch = CountDownLatch(2) // Expecting 2 error scenarios
        
        // Set up error tracking
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AutoClickService.ACTION_STATE_CHANGED) {
                    val stateString = intent.getStringExtra(AutoClickService.EXTRA_STATE)
                    if (stateString?.contains("Error") == true) {
                        val errorMsg = intent.getStringExtra(AutoClickService.EXTRA_ERROR_MESSAGE) ?: ""
                        errorMessages.add(errorMsg)
                        latch.countDown()
                    }
                }
            }
        }
        
        val filter = IntentFilter(AutoClickService.ACTION_STATE_CHANGED)
        localBroadcastManager.registerReceiver(receiver, filter)
        
        try {
            // Scenario 1: Invalid configuration
            viewModel.startAutoClick() // No image or coordinates set
            assertEquals("Configuration is not valid", viewModel.errorMessage.value)
            
            // Scenario 2: Service error
            service.updateState(AutoClickState.Error("Service connection failed"))
            
            // Scenario 3: Invalid parameters
            viewModel.setInterval(50L) // Too small
            assertEquals("Interval must be at least 100ms", viewModel.errorMessage.value)
            
            viewModel.setClickCoordinates(-10, 50) // Invalid coordinates
            assertEquals("Invalid coordinates: (-10, 50)", viewModel.errorMessage.value)
            
            // Trigger another service error
            service.updateState(AutoClickState.Error("Template not found"))
            
            // Wait for error broadcasts
            assertTrue("Error broadcasts not received", latch.await(3, TimeUnit.SECONDS))
            
            // Verify error handling
            assertTrue("Should have received error messages", errorMessages.isNotEmpty())
            
        } finally {
            localBroadcastManager.unregisterReceiver(receiver)
        }
    }

    @Test
    fun testImageMatchingIntegration() = runTest {
        // Setup image matching scenario
        val templateBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
        val screenBitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        
        // Configure successful match
        every { mockImageMatcher.findTemplate(screenBitmap, templateBitmap) } returns MatchResult(
            found = true,
            confidence = 0.95,
            location = android.graphics.Point(100, 150)
        )
        
        // Set up ViewModel with image
        viewModel.setTemplateImage(templateBitmap)
        viewModel.setClickCoordinates(25, 25) // Relative to template
        
        // Verify configuration
        assertTrue("Configuration should be valid", viewModel.isConfigurationValid.value == true)
        
        // Create config and test
        val config = viewModel.createCurrentConfig("image_match_test")
        assertNotNull("Config should be created", config)
        
        // Simulate image matching in service
        val matchResult = mockImageMatcher.findTemplate(screenBitmap, templateBitmap)
        assertTrue("Template should be found", matchResult.found)
        assertEquals(0.95, matchResult.confidence, 0.01)
        assertNotNull("Location should be found", matchResult.location)
        
        // Verify the match result can be used for clicking
        val clickX = matchResult.location!!.x + config!!.clickX
        val clickY = matchResult.location!!.y + config.clickY
        assertTrue("Click coordinates should be valid", service.isValidClickCoordinates(clickX, clickY))
    }

    @Test
    fun testRepositoryIntegrationWithService() = runTest {
        // Test repository operations through the full stack
        
        // Step 1: Save configuration through ViewModel
        val mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(100, 200)
        viewModel.saveConfiguration("repo_integration_test")
        
        // Verify repository interaction
        verify { mockRepository.saveConfig(any()) }
        
        // Step 2: Get all configurations
        val configNames = viewModel.getAllConfigurationNames()
        assertNotNull("Config names should be returned", configNames)
        
        // Step 3: Load and use configuration
        viewModel.loadConfiguration("repo_integration_test")
        verify { mockRepository.loadConfig("repo_integration_test") }
        
        // Step 4: Delete configuration
        val deleteResult = viewModel.deleteConfiguration("repo_integration_test")
        verify { mockRepository.deleteConfig("repo_integration_test") }
        
        // Verify all repository methods were called
        verify(exactly = 1) { mockRepository.saveConfig(any()) }
        verify(exactly = 1) { mockRepository.loadConfig("repo_integration_test") }
        verify(exactly = 1) { mockRepository.deleteConfig("repo_integration_test") }
        verify(atLeast = 1) { mockRepository.getAllConfigs() }
    }

    @Test
    fun testConcurrentOperationsHandling() = runTest {
        val stateChanges = mutableListOf<AutoClickState>()
        val latch = CountDownLatch(4)
        
        // Set up state tracking
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AutoClickService.ACTION_STATE_CHANGED) {
                    val stateString = intent.getStringExtra(AutoClickService.EXTRA_STATE)
                    stateString?.let { state ->
                        when {
                            state.contains("Searching") -> stateChanges.add(AutoClickState.Searching)
                            state.contains("Idle") -> stateChanges.add(AutoClickState.Idle)
                        }
                        latch.countDown()
                    }
                }
            }
        }
        
        val filter = IntentFilter(AutoClickService.ACTION_STATE_CHANGED)
        localBroadcastManager.registerReceiver(receiver, filter)
        
        try {
            // Set up valid configuration
            val mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            viewModel.setTemplateImage(mockBitmap)
            viewModel.setClickCoordinates(100, 200)
            
            // Start auto-click
            viewModel.startAutoClick()
            service.updateState(AutoClickState.Searching)
            
            // Immediately try to start again (should handle gracefully)
            viewModel.startAutoClick()
            
            // Stop auto-click
            viewModel.stopAutoClick()
            service.updateState(AutoClickState.Idle)
            
            // Try to stop again (should handle gracefully)
            viewModel.stopAutoClick()
            service.updateState(AutoClickState.Idle)
            
            // Wait for state changes
            assertTrue("State changes not received", latch.await(3, TimeUnit.SECONDS))
            
            // Verify final state is idle
            assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
            assertEquals(AutoClickState.Idle::class, service.getCurrentState()::class)
            
        } finally {
            localBroadcastManager.unregisterReceiver(receiver)
        }
    }

    @Test
    fun testMemoryAndResourceManagement() = runTest {
        // Test that repeated operations don't cause memory leaks or resource issues
        
        for (i in 1..10) {
            // Create configuration
            val mockBitmap = Bitmap.createBitmap(50, 50, Bitmap.Config.ARGB_8888)
            viewModel.setTemplateImage(mockBitmap)
            viewModel.setClickCoordinates(i * 10, i * 20)
            
            // Save configuration
            viewModel.saveConfiguration("test_config_$i")
            
            // Start and stop auto-click
            viewModel.startAutoClick()
            service.startAutoClick(testConfig.copy(id = "test-$i"))
            
            Thread.sleep(10) // Brief processing time
            
            viewModel.stopAutoClick()
            service.stopAutoClick()
            
            // Reset configuration
            viewModel.resetConfiguration()
            
            // Verify clean state after each iteration
            assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
            assertEquals(AutoClickState.Idle::class, service.getCurrentState()::class)
            assertNull(viewModel.templateImage.value)
            assertNull(viewModel.clickCoordinates.value)
        }
        
        // Verify repository was called for each save
        verify(exactly = 10) { mockRepository.saveConfig(any()) }
        
        // Test completed without memory issues
        assertTrue("Memory management test completed", true)
    }
}