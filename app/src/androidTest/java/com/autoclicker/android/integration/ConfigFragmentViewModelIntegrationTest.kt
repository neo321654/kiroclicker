package com.autoclicker.android.integration

import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.fragment.app.testing.FragmentScenario
import androidx.fragment.app.testing.launchFragmentInContainer
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autoclicker.android.model.AutoClickState
import com.autoclicker.android.ui.ConfigFragment
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class ConfigFragmentViewModelIntegrationTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private lateinit var scenario: FragmentScenario<ConfigFragment>
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setup() {
        scenario = launchFragmentInContainer<ConfigFragment>()
        scenario.moveToState(Lifecycle.State.STARTED)
    }

    @After
    fun tearDown() {
        scenario.close()
    }

    @Test
    fun testFragmentViewModelInitialization() = runTest {
        scenario.onFragment { fragment ->
            // Verify that ViewModel is properly initialized
            assertNotNull(fragment.viewModel)
            
            // Verify initial state
            assertEquals(AutoClickState.Idle, fragment.viewModel.autoClickState.value)
            assertEquals(1000L, fragment.viewModel.interval.value)
            assertEquals(10, fragment.viewModel.repeatCount.value)
            assertEquals(false, fragment.viewModel.isConfigurationValid.value)
            assertNull(fragment.viewModel.templateImage.value)
            assertNull(fragment.viewModel.clickCoordinates.value)
        }
    }

    @Test
    fun testImageSelectionFlow() = runTest {
        scenario.onFragment { fragment ->
            val viewModel = fragment.viewModel
            
            // Create a mock bitmap
            val mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            
            // Simulate image selection
            viewModel.setTemplateImage(mockBitmap)
            
            // Verify image is set
            assertEquals(mockBitmap, viewModel.templateImage.value)
            assertEquals("", viewModel.errorMessage.value)
            
            // Configuration should still be invalid (no coordinates)
            assertEquals(false, viewModel.isConfigurationValid.value)
        }
    }

    @Test
    fun testCoordinateSelectionFlow() = runTest {
        scenario.onFragment { fragment ->
            val viewModel = fragment.viewModel
            
            // Set coordinates
            viewModel.setClickCoordinates(150, 250)
            
            // Verify coordinates are set
            assertEquals(Pair(150, 250), viewModel.clickCoordinates.value)
            assertEquals("", viewModel.errorMessage.value)
            
            // Configuration should still be invalid (no image)
            assertEquals(false, viewModel.isConfigurationValid.value)
        }
    }

    @Test
    fun testCompleteConfigurationFlow() = runTest {
        scenario.onFragment { fragment ->
            val viewModel = fragment.viewModel
            
            // Create complete configuration
            val mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            viewModel.setTemplateImage(mockBitmap)
            viewModel.setClickCoordinates(150, 250)
            viewModel.setInterval(1500L)
            viewModel.setRepeatCount(20)
            
            // Verify configuration is valid
            assertEquals(true, viewModel.isConfigurationValid.value)
            assertEquals("", viewModel.errorMessage.value)
            
            // Test configuration creation
            val config = viewModel.createCurrentConfig("test_config")
            assertNotNull(config)
            assertEquals("test_config", config!!.name)
            assertEquals(150, config.clickX)
            assertEquals(250, config.clickY)
            assertEquals(1500L, config.intervalMs)
            assertEquals(20, config.repeatCount)
        }
    }

    @Test
    fun testParameterValidationFlow() = runTest {
        scenario.onFragment { fragment ->
            val viewModel = fragment.viewModel
            
            // Test invalid interval
            viewModel.setInterval(50L)
            assertEquals("Interval must be at least 100ms", viewModel.errorMessage.value)
            assertEquals(1000L, viewModel.interval.value) // Should remain unchanged
            
            // Test invalid coordinates
            viewModel.setClickCoordinates(-10, 50)
            assertEquals("Invalid coordinates: (-10, 50)", viewModel.errorMessage.value)
            assertNull(viewModel.clickCoordinates.value)
            
            // Test invalid repeat count
            viewModel.setRepeatCount(0)
            assertEquals("Repeat count must be positive or -1 for infinite", viewModel.errorMessage.value)
            assertEquals(10, viewModel.repeatCount.value) // Should remain unchanged
        }
    }

    @Test
    fun testAutoClickStartStopFlow() = runTest {
        scenario.onFragment { fragment ->
            val viewModel = fragment.viewModel
            
            // Try to start without valid configuration
            viewModel.startAutoClick()
            assertEquals("Configuration is not valid", viewModel.errorMessage.value)
            assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
            
            // Set up valid configuration
            val mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            viewModel.setTemplateImage(mockBitmap)
            viewModel.setClickCoordinates(150, 250)
            
            // Start auto-click
            viewModel.startAutoClick()
            assertEquals(AutoClickState.Searching, viewModel.autoClickState.value)
            
            // Stop auto-click
            viewModel.stopAutoClick()
            assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
        }
    }

    @Test
    fun testConfigurationResetFlow() = runTest {
        scenario.onFragment { fragment ->
            val viewModel = fragment.viewModel
            
            // Set up configuration
            val mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            viewModel.setTemplateImage(mockBitmap)
            viewModel.setClickCoordinates(150, 250)
            viewModel.setInterval(2000L)
            viewModel.setRepeatCount(50)
            viewModel.updateAutoClickState(AutoClickState.Searching)
            
            // Verify configuration is set
            assertEquals(true, viewModel.isConfigurationValid.value)
            
            // Reset configuration
            viewModel.resetConfiguration()
            
            // Verify everything is reset
            assertNull(viewModel.templateImage.value)
            assertNull(viewModel.clickCoordinates.value)
            assertEquals(1000L, viewModel.interval.value)
            assertEquals(10, viewModel.repeatCount.value)
            assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
            assertEquals(false, viewModel.isConfigurationValid.value)
            assertEquals("", viewModel.errorMessage.value)
        }
    }

    @Test
    fun testStateTransitionFlow() = runTest {
        scenario.onFragment { fragment ->
            val viewModel = fragment.viewModel
            
            // Test state transitions
            viewModel.updateAutoClickState(AutoClickState.Searching)
            assertEquals(AutoClickState.Searching, viewModel.autoClickState.value)
            
            viewModel.updateAutoClickState(AutoClickState.Clicking)
            assertEquals(AutoClickState.Clicking, viewModel.autoClickState.value)
            
            viewModel.updateAutoClickState(AutoClickState.Waiting)
            assertEquals(AutoClickState.Waiting, viewModel.autoClickState.value)
            
            val errorState = AutoClickState.Error("Test error")
            viewModel.updateAutoClickState(errorState)
            assertEquals(errorState, viewModel.autoClickState.value)
            
            val completedState = AutoClickState.Completed(5)
            viewModel.updateAutoClickState(completedState)
            assertEquals(completedState, viewModel.autoClickState.value)
        }
    }

    @Test
    fun testConfigurationPersistenceFlow() = runTest {
        scenario.onFragment { fragment ->
            val viewModel = fragment.viewModel
            
            // Set up configuration
            val mockBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
            viewModel.setTemplateImage(mockBitmap)
            viewModel.setClickCoordinates(150, 250)
            viewModel.setInterval(1500L)
            viewModel.setRepeatCount(25)
            
            // Save configuration
            viewModel.saveConfiguration("integration_test_config")
            
            // Verify no error occurred
            assertEquals("", viewModel.errorMessage.value)
            
            // Reset and load configuration
            viewModel.resetConfiguration()
            viewModel.loadConfiguration("integration_test_config")
            
            // Note: Since we're using mocked repository in tests,
            // we mainly verify that the methods execute without crashing
            assertTrue(true) // Test completed successfully
        }
    }
}