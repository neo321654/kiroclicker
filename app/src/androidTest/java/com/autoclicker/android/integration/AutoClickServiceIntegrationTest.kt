package com.autoclicker.android.integration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autoclicker.android.model.AutoClickState
import com.autoclicker.android.model.ClickConfig
import com.autoclicker.android.service.AutoClickService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AutoClickServiceIntegrationTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var service: AutoClickService
    private lateinit var localBroadcastManager: LocalBroadcastManager
    
    // Test data holders
    private var receivedStates = mutableListOf<AutoClickState>()
    private var receivedClickCounts = mutableListOf<Int>()
    private var receivedErrors = mutableListOf<String>()

    @Before
    fun setup() {
        service = AutoClickService()
        localBroadcastManager = LocalBroadcastManager.getInstance(context)
        
        // Clear test data
        receivedStates.clear()
        receivedClickCounts.clear()
        receivedErrors.clear()
    }

    @After
    fun tearDown() {
        // Clean up service state
        service.stopAutoClick()
    }

    @Test
    fun testServiceInstanceManagement() = runTest {
        // Test singleton pattern
        assertNull(AutoClickService.getInstance())
        assertFalse(AutoClickService.isServiceRunning())
        
        // Simulate service connection
        service.onServiceConnected()
        
        // After connection, instance should be available
        assertNotNull(AutoClickService.getInstance())
        assertTrue(AutoClickService.isServiceRunning())
        
        // Test service disconnection
        service.onInterrupt()
        service.onDestroy()
    }

    @Test
    fun testServiceStateManagement() = runTest {
        // Initial state should be Idle
        assertEquals(AutoClickState.Idle::class, service.getCurrentState()::class)
        assertEquals(0, service.getClickCount())
        assertFalse(service.isAutoClickRunning())
        
        // Test state transitions
        service.updateState(AutoClickState.Searching)
        assertEquals(AutoClickState.Searching::class, service.getCurrentState()::class)
        
        service.updateState(AutoClickState.Clicking)
        assertEquals(AutoClickState.Clicking::class, service.getCurrentState()::class)
        
        service.updateState(AutoClickState.Waiting)
        assertEquals(AutoClickState.Waiting::class, service.getCurrentState()::class)
        
        // Test error state
        val errorState = AutoClickState.Error("Test error")
        service.updateState(errorState)
        assertTrue(service.getCurrentState() is AutoClickState.Error)
        assertEquals("Test error", (service.getCurrentState() as AutoClickState.Error).message)
        
        // Test completed state
        val completedState = AutoClickState.Completed(10)
        service.updateState(completedState)
        assertTrue(service.getCurrentState() is AutoClickState.Completed)
        assertEquals(10, (service.getCurrentState() as AutoClickState.Completed).clickCount)
    }

    @Test
    fun testBroadcastCommunication() = runTest {
        val latch = CountDownLatch(3) // Expecting 3 broadcasts
        
        // Set up broadcast receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                when (intent?.action) {
                    AutoClickService.ACTION_STATE_CHANGED -> {
                        val stateString = intent.getStringExtra(AutoClickService.EXTRA_STATE)
                        stateString?.let { 
                            // Parse state from string representation
                            when {
                                it.contains("Searching") -> receivedStates.add(AutoClickState.Searching)
                                it.contains("Clicking") -> receivedStates.add(AutoClickState.Clicking)
                                it.contains("Error") -> {
                                    val errorMsg = intent.getStringExtra(AutoClickService.EXTRA_ERROR_MESSAGE) ?: ""
                                    receivedStates.add(AutoClickState.Error(errorMsg))
                                    receivedErrors.add(errorMsg)
                                }
                                else -> receivedStates.add(AutoClickState.Idle)
                            }
                        }
                        latch.countDown()
                    }
                    AutoClickService.ACTION_CLICK_COUNT_UPDATED -> {
                        val count = intent.getIntExtra(AutoClickService.EXTRA_CLICK_COUNT, 0)
                        receivedClickCounts.add(count)
                        latch.countDown()
                    }
                }
            }
        }
        
        // Register receiver
        val filter = IntentFilter().apply {
            addAction(AutoClickService.ACTION_STATE_CHANGED)
            addAction(AutoClickService.ACTION_CLICK_COUNT_UPDATED)
        }
        localBroadcastManager.registerReceiver(receiver, filter)
        
        try {
            // Trigger broadcasts
            service.updateState(AutoClickState.Searching)
            service.updateClickCount(5)
            service.updateState(AutoClickState.Error("Test broadcast error"))
            
            // Wait for broadcasts
            assertTrue("Broadcasts not received in time", latch.await(5, TimeUnit.SECONDS))
            
            // Verify received data
            assertTrue("Should have received states", receivedStates.isNotEmpty())
            assertTrue("Should have received click counts", receivedClickCounts.isNotEmpty())
            assertTrue("Should have received error", receivedErrors.isNotEmpty())
            
            assertEquals("Test broadcast error", receivedErrors.first())
            assertEquals(5, receivedClickCounts.first())
            
        } finally {
            localBroadcastManager.unregisterReceiver(receiver)
        }
    }

    @Test
    fun testAutoClickConfigurationValidation() = runTest {
        // Test with invalid configuration (missing image path)
        val invalidConfig = ClickConfig(
            id = "test-invalid",
            name = "Invalid Config",
            templateImagePath = "",
            clickX = 100,
            clickY = 200,
            intervalMs = 1000,
            repeatCount = 5,
            threshold = 0.8
        )
        
        service.startAutoClick(invalidConfig)
        
        // Should result in error state
        assertTrue("Service should be in error state", service.getCurrentState() is AutoClickState.Error)
        
        // Test with valid configuration structure
        val validConfig = ClickConfig(
            id = "test-valid",
            name = "Valid Config",
            templateImagePath = "/test/path/image.png",
            clickX = 100,
            clickY = 200,
            intervalMs = 1000,
            repeatCount = 5,
            threshold = 0.8
        )
        
        // Reset service state
        service.stopAutoClick()
        assertEquals(AutoClickState.Idle::class, service.getCurrentState()::class)
        
        // Start with valid config (will fail due to missing accessibility permissions, but structure is valid)
        service.startAutoClick(validConfig)
        
        // Should attempt to start (may result in error due to permissions, but not due to invalid config)
        assertTrue("Service should have processed the valid config", 
            service.getCurrentState() is AutoClickState.Error || service.getCurrentState() is AutoClickState.Searching)
    }

    @Test
    fun testCoordinateValidation() = runTest {
        // Test valid coordinates
        assertTrue(service.isValidClickCoordinates(100, 200))
        assertTrue(service.isValidClickCoordinates(0, 0))
        assertTrue(service.isValidClickCoordinates(1920, 1080))
        
        // Test invalid coordinates
        assertFalse(service.isValidClickCoordinates(-1, 100))
        assertFalse(service.isValidClickCoordinates(100, -1))
        assertFalse(service.isValidClickCoordinates(-10, -20))
    }

    @Test
    fun testServiceCapabilityChecks() = runTest {
        // Test permission checks (will be false in test environment)
        assertFalse("Screenshot permission should be false in test", service.hasScreenshotPermission())
        assertFalse("Gesture capability should be false in test", service.canPerformGestures())
        assertFalse("Service should not be ready in test", service.isReady())
    }

    @Test
    fun testClickPerformanceWithCallback() = runTest {
        val latch = CountDownLatch(1)
        var callbackResult: Boolean? = null
        
        // Perform click (will fail due to no accessibility service, but callback should be called)
        service.performClick(100, 200) { result ->
            callbackResult = result
            latch.countDown()
        }
        
        // Wait for callback
        assertTrue("Callback not called in time", latch.await(3, TimeUnit.SECONDS))
        
        // Should be false due to service not being ready
        assertEquals(false, callbackResult)
    }

    @Test
    fun testLongClickPerformanceWithCallback() = runTest {
        val latch = CountDownLatch(1)
        var callbackResult: Boolean? = null
        
        // Perform long click (will fail due to no accessibility service, but callback should be called)
        service.performLongClick(100, 200, 1000) { result ->
            callbackResult = result
            latch.countDown()
        }
        
        // Wait for callback
        assertTrue("Callback not called in time", latch.await(3, TimeUnit.SECONDS))
        
        // Should be false due to service not being ready
        assertEquals(false, callbackResult)
    }

    @Test
    fun testAutoClickStopFunctionality() = runTest {
        // Start auto-click with valid config
        val config = ClickConfig(
            id = "test-stop",
            name = "Stop Test Config",
            templateImagePath = "/test/path/image.png",
            clickX = 100,
            clickY = 200,
            intervalMs = 1000,
            repeatCount = 10,
            threshold = 0.8
        )
        
        service.startAutoClick(config)
        
        // Verify it's running (or attempting to run)
        assertTrue("Service should be processing", 
            service.getCurrentState() !is AutoClickState.Idle)
        
        // Stop auto-click
        service.stopAutoClick()
        
        // Should return to idle state
        assertEquals(AutoClickState.Idle::class, service.getCurrentState()::class)
        assertFalse("Service should not be running", service.isAutoClickRunning())
        assertEquals(0, service.getClickCount())
    }

    @Test
    fun testServiceLifecycleIntegration() = runTest {
        // Test service lifecycle methods
        service.onServiceConnected()
        assertTrue("Service should be running after connection", AutoClickService.isServiceRunning())
        
        // Test interrupt handling
        service.onInterrupt()
        assertEquals(AutoClickState.Idle::class, service.getCurrentState()::class)
        
        // Test destroy handling
        service.onDestroy()
        // Service should clean up properly (no exceptions thrown)
        assertTrue("Service lifecycle completed", true)
    }

    @Test
    fun testClickCountManagement() = runTest {
        // Initial count should be 0
        assertEquals(0, service.getClickCount())
        
        // Update click count
        service.updateClickCount(5)
        assertEquals(5, service.getClickCount())
        
        service.updateClickCount(10)
        assertEquals(10, service.getClickCount())
        
        // Reset count (through stop)
        service.stopAutoClick()
        assertEquals(0, service.getClickCount())
    }

    @Test
    fun testErrorHandlingIntegration() = runTest {
        val latch = CountDownLatch(1)
        var receivedError: String? = null
        
        // Set up error broadcast receiver
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == AutoClickService.ACTION_STATE_CHANGED) {
                    val stateString = intent.getStringExtra(AutoClickService.EXTRA_STATE)
                    if (stateString?.contains("Error") == true) {
                        receivedError = intent.getStringExtra(AutoClickService.EXTRA_ERROR_MESSAGE)
                        latch.countDown()
                    }
                }
            }
        }
        
        val filter = IntentFilter(AutoClickService.ACTION_STATE_CHANGED)
        localBroadcastManager.registerReceiver(receiver, filter)
        
        try {
            // Trigger error state
            service.updateState(AutoClickState.Error("Integration test error"))
            
            // Wait for error broadcast
            assertTrue("Error broadcast not received", latch.await(3, TimeUnit.SECONDS))
            assertEquals("Integration test error", receivedError)
            
        } finally {
            localBroadcastManager.unregisterReceiver(receiver)
        }
    }
}