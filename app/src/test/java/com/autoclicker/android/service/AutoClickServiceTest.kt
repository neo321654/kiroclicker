package com.autoclicker.android.service

import android.accessibilityservice.AccessibilityServiceInfo
import org.junit.Test
import org.junit.Assert.*
import org.junit.Before
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class AutoClickServiceTest {
    
    @Mock
    private lateinit var mockServiceInfo: AccessibilityServiceInfo
    
    private lateinit var service: AutoClickService
    
    @Before
    fun setUp() {
        MockitoAnnotations.openMocks(this)
        service = AutoClickService()
    }
    
    @Test
    fun `test service instance management`() {
        // Initially no instance should be available
        assertNull(AutoClickService.getInstance())
        assertFalse(AutoClickService.isServiceRunning())
    }
    
    @Test
    fun `test service ready state without connection`() {
        // Service should not be ready without proper connection
        assertFalse(service.isReady())
    }
    
    @Test
    fun `test auto-click state management`() {
        // Initially should be idle
        assertEquals(com.autoclicker.android.model.AutoClickState.Idle::class, service.getCurrentState()::class)
        
        // Click count should be 0
        assertEquals(0, service.getClickCount())
        
        // Should not be running
        assertFalse(service.isAutoClickRunning())
    }
    
    @Test
    fun `test start auto-click with invalid service state`() {
        val config = createMockClickConfig()
        
        // Start auto-click when service is not ready
        service.startAutoClick(config)
        
        // Should result in error state
        assertTrue(service.getCurrentState() is com.autoclicker.android.model.AutoClickState.Error)
    }
    
    @Test
    fun `test stop auto-click`() {
        // Stop auto-click (should not crash even if not running)
        service.stopAutoClick()
        
        // Should be in idle state
        assertEquals(com.autoclicker.android.model.AutoClickState.Idle::class, service.getCurrentState()::class)
        
        // Should not be running
        assertFalse(service.isAutoClickRunning())
    }
    
    @Test
    fun `test broadcast action constants`() {
        // Test that broadcast action constants are properly defined
        assertEquals("com.autoclicker.android.STATE_CHANGED", AutoClickService.ACTION_STATE_CHANGED)
        assertEquals("com.autoclicker.android.CLICK_COUNT_UPDATED", AutoClickService.ACTION_CLICK_COUNT_UPDATED)
        assertEquals("com.autoclicker.android.START_AUTO_CLICK", AutoClickService.ACTION_START_AUTO_CLICK)
        assertEquals("com.autoclicker.android.STOP_AUTO_CLICK", AutoClickService.ACTION_STOP_AUTO_CLICK)
    }
    
    @Test
    fun `test broadcast extra constants`() {
        // Test that broadcast extra constants are properly defined
        assertEquals("state", AutoClickService.EXTRA_STATE)
        assertEquals("click_count", AutoClickService.EXTRA_CLICK_COUNT)
        assertEquals("error_message", AutoClickService.EXTRA_ERROR_MESSAGE)
    }
    
    @Test
    fun `test screenshot permission check`() {
        // Test that screenshot permission check doesn't crash
        val hasPermission = service.hasScreenshotPermission()
        
        // Should return false when service is not properly initialized
        assertFalse(hasPermission)
    }
    
    @Test
    fun `test click coordinate validation`() {
        // Test coordinate validation with various inputs
        assertTrue(service.isValidClickCoordinates(100, 200))
        assertTrue(service.isValidClickCoordinates(0, 0))
        assertFalse(service.isValidClickCoordinates(-1, 100))
        assertFalse(service.isValidClickCoordinates(100, -1))
    }
    
    @Test
    fun `test gesture capability check`() {
        // Test that gesture capability check doesn't crash
        val canPerform = service.canPerformGestures()
        
        // Should return false when service is not properly initialized
        assertFalse(canPerform)
    }
    
    @Test
    fun `test perform click when service not ready`() {
        var callbackResult: Boolean? = null
        
        // Perform click when service is not ready
        service.performClick(100, 200) { result ->
            callbackResult = result
        }
        
        // Should fail when service is not ready
        assertEquals(false, callbackResult)
    }
    
    @Test
    fun `test perform long click when service not ready`() {
        var callbackResult: Boolean? = null
        
        // Perform long click when service is not ready
        service.performLongClick(100, 200, 1000) { result ->
            callbackResult = result
        }
        
        // Should fail when service is not ready
        assertEquals(false, callbackResult)
    }
    
    private fun createMockClickConfig(): com.autoclicker.android.model.ClickConfig {
        return com.autoclicker.android.model.ClickConfig(
            id = "test-id",
            name = "Test Config",
            templateImagePath = "/test/path",
            clickX = 100,
            clickY = 200,
            intervalMs = 1000,
            repeatCount = 5,
            threshold = 0.8
        )
    }
}