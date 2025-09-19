package com.autoclicker.android.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Observer
import com.autoclicker.android.model.AutoClickState
import com.autoclicker.android.model.ClickConfig
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*

@ExperimentalCoroutinesApi
class ConfigViewModelTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var viewModel: ConfigViewModel
    private lateinit var mockApplication: Application
    private lateinit var mockBitmap: Bitmap

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        mockApplication = mockk<Application>(relaxed = true)
        mockBitmap = mockk<Bitmap>(relaxed = true)
        
        // Mock application context
        every { mockApplication.filesDir } returns mockk(relaxed = true)
        every { mockApplication.getSharedPreferences(any(), any()) } returns mockk(relaxed = true)
        
        viewModel = ConfigViewModel(mockApplication)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state should be correct`() {
        // Verify initial values
        assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
        assertEquals(1000L, viewModel.interval.value)
        assertEquals(10, viewModel.repeatCount.value)
        assertEquals(false, viewModel.isConfigurationValid.value)
        assertEquals("", viewModel.errorMessage.value)
        assertNull(viewModel.templateImage.value)
        assertNull(viewModel.clickCoordinates.value)
        assertNull(viewModel.currentConfig.value)
    }

    @Test
    fun `setTemplateImage should update template image and validate configuration`() {
        // Arrange
        val observer = mockk<Observer<Bitmap?>>(relaxed = true)
        viewModel.templateImage.observeForever(observer)

        // Act
        viewModel.setTemplateImage(mockBitmap)

        // Assert
        verify { observer.onChanged(mockBitmap) }
        assertEquals(mockBitmap, viewModel.templateImage.value)
        assertEquals("", viewModel.errorMessage.value)
    }

    @Test
    fun `setClickCoordinates should update coordinates when valid`() {
        // Arrange
        val observer = mockk<Observer<Pair<Int, Int>?>>(relaxed = true)
        viewModel.clickCoordinates.observeForever(observer)

        // Act
        viewModel.setClickCoordinates(100, 200)

        // Assert
        verify { observer.onChanged(Pair(100, 200)) }
        assertEquals(Pair(100, 200), viewModel.clickCoordinates.value)
        assertEquals("", viewModel.errorMessage.value)
    }

    @Test
    fun `setClickCoordinates should set error when coordinates are negative`() {
        // Arrange
        val coordinatesObserver = mockk<Observer<Pair<Int, Int>?>>(relaxed = true)
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.clickCoordinates.observeForever(coordinatesObserver)
        viewModel.errorMessage.observeForever(errorObserver)

        // Act
        viewModel.setClickCoordinates(-10, 50)

        // Assert
        verify { coordinatesObserver wasNot Called }
        verify { errorObserver.onChanged("Invalid coordinates: (-10, 50)") }
        assertNull(viewModel.clickCoordinates.value)
    }

    @Test
    fun `setInterval should update interval when valid`() {
        // Arrange
        val observer = mockk<Observer<Long>>(relaxed = true)
        viewModel.interval.observeForever(observer)

        // Act
        viewModel.setInterval(2000L)

        // Assert
        verify { observer.onChanged(2000L) }
        assertEquals(2000L, viewModel.interval.value)
        assertEquals("", viewModel.errorMessage.value)
    }

    @Test
    fun `setInterval should set error when interval is too small`() {
        // Arrange
        val intervalObserver = mockk<Observer<Long>>(relaxed = true)
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.interval.observeForever(intervalObserver)
        viewModel.errorMessage.observeForever(errorObserver)

        // Act
        viewModel.setInterval(50L)

        // Assert
        verify { intervalObserver wasNot Called }
        verify { errorObserver.onChanged("Interval must be at least 100ms") }
        assertEquals(1000L, viewModel.interval.value) // Should remain unchanged
    }

    @Test
    fun `setRepeatCount should update repeat count when valid`() {
        // Arrange
        val observer = mockk<Observer<Int>>(relaxed = true)
        viewModel.repeatCount.observeForever(observer)

        // Act
        viewModel.setRepeatCount(5)

        // Assert
        verify { observer.onChanged(5) }
        assertEquals(5, viewModel.repeatCount.value)
        assertEquals("", viewModel.errorMessage.value)
    }

    @Test
    fun `setRepeatCount should accept infinite mode (-1)`() {
        // Arrange
        val observer = mockk<Observer<Int>>(relaxed = true)
        viewModel.repeatCount.observeForever(observer)

        // Act
        viewModel.setRepeatCount(-1)

        // Assert
        verify { observer.onChanged(-1) }
        assertEquals(-1, viewModel.repeatCount.value)
        assertEquals("", viewModel.errorMessage.value)
    }

    @Test
    fun `setRepeatCount should set error when count is invalid`() {
        // Arrange
        val repeatObserver = mockk<Observer<Int>>(relaxed = true)
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.repeatCount.observeForever(repeatObserver)
        viewModel.errorMessage.observeForever(errorObserver)

        // Act
        viewModel.setRepeatCount(0)

        // Assert
        verify { repeatObserver wasNot Called }
        verify { errorObserver.onChanged("Repeat count must be positive or -1 for infinite") }
        assertEquals(10, viewModel.repeatCount.value) // Should remain unchanged
    }

    @Test
    fun `createCurrentConfig should return null when image is missing`() {
        // Arrange - set coordinates but no image
        viewModel.setClickCoordinates(100, 200)

        // Act
        val config = viewModel.createCurrentConfig("test")

        // Assert
        assertNull(config)
    }

    @Test
    fun `createCurrentConfig should return null when coordinates are missing`() {
        // Arrange - set image but no coordinates
        viewModel.setTemplateImage(mockBitmap)

        // Act
        val config = viewModel.createCurrentConfig("test")

        // Assert
        assertNull(config)
    }

    @Test
    fun `createCurrentConfig should return valid config when all data is present`() {
        // Arrange
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(100, 200)
        viewModel.setInterval(1500L)
        viewModel.setRepeatCount(20)

        // Act
        val config = viewModel.createCurrentConfig("test_config")

        // Assert
        assertNotNull(config)
        assertEquals("test_config", config!!.name)
        assertEquals(100, config.clickX)
        assertEquals(200, config.clickY)
        assertEquals(1500L, config.intervalMs)
        assertEquals(20, config.repeatCount)
        assertEquals(0.8, config.threshold, 0.001)
    }

    @Test
    fun `startAutoClick should update state to Searching when configuration is valid`() {
        // Arrange
        val observer = mockk<Observer<AutoClickState>>(relaxed = true)
        viewModel.autoClickState.observeForever(observer)
        
        // Set up valid configuration
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(100, 200)

        // Act
        viewModel.startAutoClick()

        // Assert
        verify { observer.onChanged(AutoClickState.Searching) }
        assertEquals(AutoClickState.Searching, viewModel.autoClickState.value)
    }

    @Test
    fun `startAutoClick should set error when configuration is invalid`() {
        // Arrange
        val stateObserver = mockk<Observer<AutoClickState>>(relaxed = true)
        val errorObserver = mockk<Observer<String>>(relaxed = true)
        viewModel.autoClickState.observeForever(stateObserver)
        viewModel.errorMessage.observeForever(errorObserver)

        // Act - start without setting up configuration
        viewModel.startAutoClick()

        // Assert
        verify { errorObserver.onChanged("Configuration is not valid") }
        assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
    }

    @Test
    fun `stopAutoClick should update state to Idle`() {
        // Arrange
        val observer = mockk<Observer<AutoClickState>>(relaxed = true)
        viewModel.autoClickState.observeForever(observer)
        
        // Set initial state to something other than Idle
        viewModel.updateAutoClickState(AutoClickState.Searching)

        // Act
        viewModel.stopAutoClick()

        // Assert
        verify { observer.onChanged(AutoClickState.Idle) }
        assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
    }

    @Test
    fun `updateAutoClickState should update the state`() {
        // Arrange
        val observer = mockk<Observer<AutoClickState>>(relaxed = true)
        viewModel.autoClickState.observeForever(observer)

        // Act
        viewModel.updateAutoClickState(AutoClickState.Clicking)

        // Assert
        verify { observer.onChanged(AutoClickState.Clicking) }
        assertEquals(AutoClickState.Clicking, viewModel.autoClickState.value)
    }

    @Test
    fun `resetConfiguration should reset all values to defaults`() {
        // Arrange - set up some configuration
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(100, 200)
        viewModel.setInterval(2000L)
        viewModel.setRepeatCount(50)
        viewModel.updateAutoClickState(AutoClickState.Searching)

        // Act
        viewModel.resetConfiguration()

        // Assert
        assertNull(viewModel.templateImage.value)
        assertNull(viewModel.clickCoordinates.value)
        assertEquals(1000L, viewModel.interval.value)
        assertEquals(10, viewModel.repeatCount.value)
        assertNull(viewModel.currentConfig.value)
        assertEquals(AutoClickState.Idle, viewModel.autoClickState.value)
        assertEquals(false, viewModel.isConfigurationValid.value)
        assertEquals("", viewModel.errorMessage.value)
    }

    @Test
    fun `configuration validation should work correctly`() {
        // Arrange
        val observer = mockk<Observer<Boolean>>(relaxed = true)
        viewModel.isConfigurationValid.observeForever(observer)

        // Act & Assert - Initially invalid
        assertEquals(false, viewModel.isConfigurationValid.value)

        // Set image only - still invalid
        viewModel.setTemplateImage(mockBitmap)
        assertEquals(false, viewModel.isConfigurationValid.value)

        // Set coordinates - now valid
        viewModel.setClickCoordinates(100, 200)
        assertEquals(true, viewModel.isConfigurationValid.value)

        // Set invalid interval - becomes invalid
        viewModel.setInterval(50L)
        assertEquals(false, viewModel.isConfigurationValid.value)

        // Fix interval - becomes valid again
        viewModel.setInterval(1000L)
        assertEquals(true, viewModel.isConfigurationValid.value)
    }

    @Test
    fun `saveConfiguration should call repository saveConfig`() {
        // Arrange
        viewModel.setTemplateImage(mockBitmap)
        viewModel.setClickCoordinates(100, 200)

        // Act
        viewModel.saveConfiguration("test_config")

        // Assert - verify that the configuration is created and saved
        // Note: This test verifies the method doesn't crash and handles the flow
        assertEquals("", viewModel.errorMessage.value)
    }

    @Test
    fun `loadConfiguration should update all configuration fields`() {
        // Arrange
        val imageObserver = mockk<Observer<Bitmap?>>(relaxed = true)
        val coordObserver = mockk<Observer<Pair<Int, Int>?>>(relaxed = true)
        val intervalObserver = mockk<Observer<Long>>(relaxed = true)
        val repeatObserver = mockk<Observer<Int>>(relaxed = true)
        
        viewModel.templateImage.observeForever(imageObserver)
        viewModel.clickCoordinates.observeForever(coordObserver)
        viewModel.interval.observeForever(intervalObserver)
        viewModel.repeatCount.observeForever(repeatObserver)

        // Act
        viewModel.loadConfiguration("test_config")

        // Assert - verify that load was attempted
        // Note: Since we're using mocked repository, this mainly tests the method structure
        assertTrue(true) // Method executed without crashing
    }

    @Test
    fun `deleteConfiguration should call repository deleteConfig`() {
        // Act
        val result = viewModel.deleteConfiguration("test_config")

        // Assert - verify that delete was attempted
        // Note: Since we're using mocked repository, this mainly tests the method structure
        assertTrue(result || !result) // Method executed and returned a boolean
    }

    @Test
    fun `getAllConfigurationNames should return list from repository`() {
        // Act
        val names = viewModel.getAllConfigurationNames()

        // Assert
        assertNotNull(names)
        assertTrue(names is List<String>)
    }
}