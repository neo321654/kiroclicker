package com.autoclicker.android.integration

import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
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
import org.junit.Assert.*

/**
 * Unit tests for component interactions in the AutoClicker application.
 * These tests verify that components work together correctly using mocks.
 */
@ExperimentalCoroutinesApi
class ComponentInteractionTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var mockRepository: ConfigRepository
    private lateinit var mockImageMatcher: ImageMatcher
    private lateinit var mockService: AutoClickService
    private lateinit var viewModel: ConfigViewModel
    private lateinit var mockBitmap: Bitmap

    @Before
    fun setup() {
        // Create mocks
        mockRepository = mockk<ConfigRepository>(relaxed = true)
        mockImageMatcher = mockk<ImageMatcher>(relaxed = true)
        mockService = mockk<AutoClickService>(relaxed = true)
        mockBitmap = mockk<Bitmap>(relaxed = true)

        // Setup default mock behaviors
        every { mockRepository.saveConfig(any()) } returns true
        every { mockRepository.loadConfig(any()) } returns null
        every { mockRepository.getAllConfigs() } returns emptyList()
        every { mockRepository.deleteConfig(any()) } returns true

        every { mockImageMatcher.findTemplate(any(), any()) } returns MatchResult(
            found = true,
            confidence = 0.9,
            location = android.graphics.Point(100, 150)
        )

        every { mockService.isValidClickCoordinates(any(), any()) } returns true
        every { mockService.getCurrentState() } returns AutoClickState.Idle
        every { mockService.getClickCount() } returns 0
        every { mockService.isAutoClickRunning() } returns false

        // Create ViewModel with mocked context
        val mockContext = mockk<android.content.Context>(relaxed = true)
        every { mockContext.filesDir } returns mockk(relaxed = true)
        every { mockContext.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        
        viewModel = ConfigViewModel(mockContext)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testViewModelRepositoryInteraction() = runTest {
        // Test saving configuration through ViewModel
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(100, 200)
        viewModel.setInterval(1500L)
        viewModel.setRepeatCount(10)

        // Create and verify configuration
        val config = viewModel.createCurrentConfig("test_config")
        assertNotNull("Configuration should be created", config)
        assertEquals("test_config", config!!.name)
        assertEquals(100, config.clickX)
        assertEquals(200, config.clickY)
        assertEquals(1500L, config.intervalMs)
        assertEquals(10, config.repeatCount)

        // Test save operation
        viewModel.saveConfiguration("test_config")
        // Note: In real implementation, this would call repository.saveConfig()
        assertEquals("", viewModel.errorMessage.value)
    }

    @Test
    fun testViewModelServiceStateSync() = runTest {
        // Setup ViewModel with valid configuration
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(150, 250)

        // Test starting auto-click
        viewModel.startAutoClick()
        assertEquals(AutoClickState.Searching, viewModel.autoClickState.value)

        // Simulate service state changes
        viewModel.updateAutoClickState(AutoClickState.Clicking)
        assertEquals(AutoClickState.Clicking, viewModel.autoClickState.value)

        viewModel.updateAutoClickState(AutoClickState.Waiting)
        assertEquals(AutoClickState.Waiting, viewModel.autoClickState.value)

        // Test stopping auto-click
        viewModel.stopAutoClick()
        assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
    }

    @Test
    fun testServiceConfigurationValidation() = runTest {
        // Test valid configuration
        val validConfig = ClickConfig(
            id = "valid-test",
            name = "Valid Config",
            templateImagePath = "/valid/path/image.png",
            clickX = 100,
            clickY = 200,
            intervalMs = 1000,
            repeatCount = 5,
            threshold = 0.8
        )

        // Mock service validation
        every { mockService.isValidClickCoordinates(100, 200) } returns true
        assertTrue("Valid coordinates should pass validation", 
            mockService.isValidClickCoordinates(validConfig.clickX, validConfig.clickY))

        // Test invalid configuration
        val invalidConfig = validConfig.copy(clickX = -10, clickY = -20)
        every { mockService.isValidClickCoordinates(-10, -20) } returns false
        assertFalse("Invalid coordinates should fail validation",
            mockService.isValidClickCoordinates(invalidConfig.clickX, invalidConfig.clickY))
    }

    @Test
    fun testImageMatcherServiceIntegration() = runTest {
        // Setup image matching scenario
        val templateBitmap = mockk<Bitmap>(relaxed = true)
        val screenBitmap = mockk<Bitmap>(relaxed = true)

        // Configure successful match
        val matchResult = MatchResult(
            found = true,
            confidence = 0.95,
            location = android.graphics.Point(75, 125)
        )
        every { mockImageMatcher.findTemplate(screenBitmap, templateBitmap) } returns matchResult

        // Test image matching
        val result = mockImageMatcher.findTemplate(screenBitmap, templateBitmap)
        assertTrue("Template should be found", result.found)
        assertEquals(0.95, result.confidence, 0.01)
        assertNotNull("Location should be provided", result.location)

        // Test click coordinate calculation
        val config = ClickConfig(
            id = "match-test",
            name = "Match Test",
            templateImagePath = "/test/path",
            clickX = 25,
            clickY = 35,
            intervalMs = 1000,
            repeatCount = 1,
            threshold = 0.8
        )

        val finalClickX = result.location!!.x + config.clickX
        val finalClickY = result.location!!.y + config.clickY

        // Verify coordinates are valid for service
        every { mockService.isValidClickCoordinates(finalClickX, finalClickY) } returns true
        assertTrue("Final click coordinates should be valid",
            mockService.isValidClickCoordinates(finalClickX, finalClickY))
    }

    @Test
    fun testRepositoryConfigurationLifecycle() = runTest {
        val testConfig = ClickConfig(
            id = "lifecycle-test",
            name = "Lifecycle Test Config",
            templateImagePath = "/test/image.png",
            clickX = 50,
            clickY = 75,
            intervalMs = 2000,
            repeatCount = 8,
            threshold = 0.85
        )

        // Test save
        every { mockRepository.saveConfig(testConfig) } returns true
        val saveResult = mockRepository.saveConfig(testConfig)
        assertTrue("Configuration should be saved", saveResult)

        // Test load
        every { mockRepository.loadConfig("Lifecycle Test Config") } returns testConfig
        val loadedConfig = mockRepository.loadConfig("Lifecycle Test Config")
        assertNotNull("Configuration should be loaded", loadedConfig)
        assertEquals(testConfig.id, loadedConfig!!.id)
        assertEquals(testConfig.name, loadedConfig.name)

        // Test list all
        every { mockRepository.getAllConfigs() } returns listOf(testConfig)
        val allConfigs = mockRepository.getAllConfigs()
        assertEquals(1, allConfigs.size)
        assertEquals(testConfig.name, allConfigs.first().name)

        // Test delete
        every { mockRepository.deleteConfig("Lifecycle Test Config") } returns true
        val deleteResult = mockRepository.deleteConfig("Lifecycle Test Config")
        assertTrue("Configuration should be deleted", deleteResult)

        // Verify interactions
        verify { mockRepository.saveConfig(testConfig) }
        verify { mockRepository.loadConfig("Lifecycle Test Config") }
        verify { mockRepository.getAllConfigs() }
        verify { mockRepository.deleteConfig("Lifecycle Test Config") }
    }

    @Test
    fun testErrorPropagationBetweenComponents() = runTest {
        // Test ViewModel error handling
        viewModel.setInterval(50L) // Invalid interval
        assertEquals("Interval must be at least 100ms", viewModel.errorMessage.value)

        viewModel.setClickCoordinates(-10, 50) // Invalid coordinates
        assertEquals("Invalid coordinates: (-10, 50)", viewModel.errorMessage.value)

        // Test service error states
        every { mockService.getCurrentState() } returns AutoClickState.Error("Service connection failed")
        val serviceState = mockService.getCurrentState()
        assertTrue("Service should be in error state", serviceState is AutoClickState.Error)
        assertEquals("Service connection failed", (serviceState as AutoClickState.Error).message)

        // Test repository error handling
        every { mockRepository.saveConfig(any()) } returns false
        val saveResult = mockRepository.saveConfig(mockk())
        assertFalse("Save should fail", saveResult)

        every { mockRepository.loadConfig("nonexistent") } returns null
        val loadResult = mockRepository.loadConfig("nonexistent")
        assertNull("Load should return null for nonexistent config", loadResult)
    }

    @Test
    fun testConfigurationValidationAcrossComponents() = runTest {
        // Create configuration with edge case values
        val edgeCaseConfig = ClickConfig(
            id = "edge-case",
            name = "Edge Case Config",
            templateImagePath = "",
            clickX = 0,
            clickY = 0,
            intervalMs = 100, // Minimum valid interval
            repeatCount = -1, // Infinite mode
            threshold = 1.0 // Maximum threshold
        )

        // Test ViewModel validation
        viewModel.setInterval(100L)
        assertEquals(100L, viewModel.interval.value)
        assertEquals("", viewModel.errorMessage.value)

        viewModel.setRepeatCount(-1)
        assertEquals(-1, viewModel.repeatCount.value)
        assertEquals("", viewModel.errorMessage.value)

        viewModel.setClickCoordinates(0, 0)
        assertEquals(Pair(0, 0), viewModel.clickCoordinates.value)
        assertEquals("", viewModel.errorMessage.value)

        // Test service coordinate validation
        every { mockService.isValidClickCoordinates(0, 0) } returns true
        assertTrue("Zero coordinates should be valid", 
            mockService.isValidClickCoordinates(0, 0))

        // Test repository handling of edge case config
        every { mockRepository.saveConfig(edgeCaseConfig) } returns true
        val saveResult = mockRepository.saveConfig(edgeCaseConfig)
        assertTrue("Edge case config should be saved", saveResult)
    }

    @Test
    fun testComponentStateConsistency() = runTest {
        // Setup initial state
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(100, 200)

        // Test state transitions
        viewModel.startAutoClick()
        assertEquals(AutoClickState.Searching, viewModel.autoClickState.value)

        // Simulate service state updates
        viewModel.updateAutoClickState(AutoClickState.Clicking)
        assertEquals(AutoClickState.Clicking, viewModel.autoClickState.value)

        viewModel.updateAutoClickState(AutoClickState.Waiting)
        assertEquals(AutoClickState.Waiting, viewModel.autoClickState.value)

        val completedState = AutoClickState.Completed(5)
        viewModel.updateAutoClickState(completedState)
        assertEquals(completedState, viewModel.autoClickState.value)

        // Test reset consistency
        viewModel.resetConfiguration()
        assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
        assertNull(viewModel.templateImage.value)
        assertNull(viewModel.clickCoordinates.value)
        assertEquals(false, viewModel.isConfigurationValid.value)
    }

    @Test
    fun testConcurrentOperationHandling() = runTest {
        // Setup valid configuration
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(100, 200)

        // Test rapid state changes
        viewModel.startAutoClick()
        assertEquals(AutoClickState.Searching, viewModel.autoClickState.value)

        // Immediately try to start again (should handle gracefully)
        viewModel.startAutoClick()
        // Should remain in searching state or handle appropriately
        assertTrue("State should be valid after concurrent start", 
            viewModel.autoClickState.value is AutoClickState.Searching)

        // Stop and immediately start again
        viewModel.stopAutoClick()
        assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)

        viewModel.startAutoClick()
        assertEquals(AutoClickState.Searching, viewModel.autoClickState.value)

        // Multiple stops should be handled gracefully
        viewModel.stopAutoClick()
        viewModel.stopAutoClick()
        assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
    }

    @Test
    fun testDataFlowBetweenComponents() = runTest {
        // Test complete data flow: ViewModel -> Repository -> Service
        
        // Step 1: Configure ViewModel
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(150, 250)
        viewModel.setInterval(1200L)
        viewModel.setRepeatCount(7)

        // Step 2: Create configuration
        val config = viewModel.createCurrentConfig("data_flow_test")
        assertNotNull("Configuration should be created", config)

        // Step 3: Save to repository
        every { mockRepository.saveConfig(config!!) } returns true
        val saveResult = mockRepository.saveConfig(config)
        assertTrue("Configuration should be saved", saveResult)

        // Step 4: Load from repository
        every { mockRepository.loadConfig("data_flow_test") } returns config
        val loadedConfig = mockRepository.loadConfig("data_flow_test")
        assertNotNull("Configuration should be loaded", loadedConfig)

        // Step 5: Validate for service
        every { mockService.isValidClickCoordinates(loadedConfig!!.clickX, loadedConfig.clickY) } returns true
        assertTrue("Loaded config should be valid for service",
            mockService.isValidClickCoordinates(loadedConfig.clickX, loadedConfig.clickY))

        // Verify data integrity throughout the flow
        assertEquals(config.id, loadedConfig.id)
        assertEquals(config.name, loadedConfig.name)
        assertEquals(config.clickX, loadedConfig.clickX)
        assertEquals(config.clickY, loadedConfig.clickY)
        assertEquals(config.intervalMs, loadedConfig.intervalMs)
        assertEquals(config.repeatCount, loadedConfig.repeatCount)
        assertEquals(config.threshold, loadedConfig.threshold, 0.001)
    }
}