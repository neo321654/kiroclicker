package com.autoclicker.android.utils

import com.autoclicker.android.AutoClickerApplication
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.MockedStatic
import org.mockito.Mockito.*

class ImageMatcherFactoryTest {

    private lateinit var mockAutoClickerApplication: MockedStatic<AutoClickerApplication>

    @Before
    fun setUp() {
        mockAutoClickerApplication = mockStatic(AutoClickerApplication::class.java)
    }

    @Test
    fun `create should return OpenCVImageMatcher instance`() {
        val imageMatcher = ImageMatcherFactory.create()
        
        assertNotNull("Should create ImageMatcher instance", imageMatcher)
        assertTrue("Should return OpenCVImageMatcher", imageMatcher is OpenCVImageMatcher)
    }

    @Test
    fun `createAndInitialize should return success when OpenCV is ready`() = runTest {
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(true)
        
        val result = ImageMatcherFactory.createAndInitialize()
        
        assertTrue("Should succeed when OpenCV is ready", result.isSuccess)
        val imageMatcher = result.getOrNull()!!
        assertTrue("Should return OpenCVImageMatcher", imageMatcher is OpenCVImageMatcher)
        assertTrue("ImageMatcher should be initialized", imageMatcher.isInitialized())
    }

    @Test
    fun `createAndInitialize should return failure when OpenCV is not ready`() = runTest {
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(false)
        
        val result = ImageMatcherFactory.createAndInitialize()
        
        assertTrue("Should fail when OpenCV is not ready", result.isFailure)
        assertTrue("Should return appropriate exception", 
            result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `isAvailable should return true when OpenCV is ready`() {
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(true)
        
        val available = ImageMatcherFactory.isAvailable()
        
        assertTrue("Should be available when OpenCV is ready", available)
    }

    @Test
    fun `isAvailable should return false when OpenCV is not ready`() {
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(false)
        
        val available = ImageMatcherFactory.isAvailable()
        
        assertFalse("Should not be available when OpenCV is not ready", available)
    }

    @Test
    fun `isAvailable should handle exceptions gracefully`() {
        // Mock an exception during creation
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenThrow(RuntimeException("Test exception"))
        
        val available = ImageMatcherFactory.isAvailable()
        
        assertFalse("Should return false when exception occurs", available)
    }

    @Test
    fun `multiple create calls should return different instances`() {
        val matcher1 = ImageMatcherFactory.create()
        val matcher2 = ImageMatcherFactory.create()
        
        assertNotNull("First instance should not be null", matcher1)
        assertNotNull("Second instance should not be null", matcher2)
        assertNotSame("Should return different instances", matcher1, matcher2)
    }

    @Test
    fun `createAndInitialize should handle initialization errors`() = runTest {
        // This test verifies that the factory properly handles initialization failures
        mockAutoClickerApplication.`when`<Boolean> { AutoClickerApplication.isOpenCVReady() }
            .thenReturn(false) // This will cause initialization to fail
        
        val result = ImageMatcherFactory.createAndInitialize()
        
        assertTrue("Should fail gracefully", result.isFailure)
        assertNotNull("Should have an exception", result.exceptionOrNull())
    }
}