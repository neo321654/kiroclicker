package com.autoclicker.android.device

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.autoclicker.android.service.AutoClickService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

/**
 * Device tests for Accessibility Service functionality.
 * These tests verify that the AutoClickService works correctly on real devices.
 * 
 * Prerequisites:
 * - Device must have the AutoClicker accessibility service enabled
 * - Device must grant necessary permissions
 * - Tests should be run on a physical device or emulator with API 28+
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class AccessibilityServiceDeviceTest {

    @Before
    fun setup() {
        // Ensure we're running on a device with proper API level
        assertTrue("Tests require API 28+", android.os.Build.VERSION.SDK_INT >= 28)
    }

    @Test
    fun testAccessibilityServiceAvailability() = runTest {
        val result = DeviceTestUtils.testAccessibilityServiceFunctionality()
        
        // Log results for manual verification
        println("=== Accessibility Service Test Results ===")
        println("Service Available: ${result.serviceAvailable}")
        println("Service Running: ${result.serviceRunning}")
        println("Can Perform Gestures: ${result.canPerformGestures}")
        println("Has Screenshot Permission: ${result.hasScreenshotPermission}")
        println("Service Ready: ${result.isReady}")
        println("All Tests Passed: ${result.allTestsPassed}")
        
        // Basic assertions - some may fail if service is not enabled
        if (DeviceTestUtils.isAccessibilityServiceEnabled()) {
            assertTrue("Service should be available when enabled", result.serviceAvailable)
            assertTrue("Service should be running when enabled", result.serviceRunning)
        } else {
            println("WARNING: Accessibility service is not enabled. Enable it manually for full testing.")
        }
        
        // These tests verify the service structure is correct
        assertNotNull("Service info should be available", DeviceTestUtils.getAccessibilityServiceInfo())
    }

    @Test
    fun testAccessibilityServicePermissions() = runTest {
        val serviceInfo = DeviceTestUtils.getAccessibilityServiceInfo()
        
        if (serviceInfo != null) {
            println("=== Accessibility Service Configuration ===")
            println("Service ID: ${serviceInfo.id}")
            println("Capabilities: ${serviceInfo.capabilities}")
            println("Event Types: ${serviceInfo.eventTypes}")
            println("Feedback Type: ${serviceInfo.feedbackType}")
            println("Flags: ${serviceInfo.flags}")
            
            // Verify service has necessary capabilities
            val hasGestureCapability = (serviceInfo.capabilities and 
                android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_PERFORM_GESTURES) != 0
            
            val hasTakeScreenshotCapability = (serviceInfo.capabilities and 
                android.accessibilityservice.AccessibilityServiceInfo.CAPABILITY_CAN_TAKE_SCREENSHOT) != 0
            
            println("Has Gesture Capability: $hasGestureCapability")
            println("Has Screenshot Capability: $hasTakeScreenshotCapability")
            
            // These capabilities are required for auto-clicking
            assertTrue("Service should have gesture capability", hasGestureCapability)
            assertTrue("Service should have screenshot capability", hasTakeScreenshotCapability)
        } else {
            println("WARNING: Service info not available. Service may not be installed correctly.")
        }
    }

    @Test
    fun testServiceInstanceManagement() = runTest {
        // Test service singleton behavior
        val instance1 = AutoClickService.getInstance()
        val instance2 = AutoClickService.getInstance()
        
        if (instance1 != null && instance2 != null) {
            assertSame("Service should follow singleton pattern", instance1, instance2)
            assertTrue("Service should report as running", AutoClickService.isServiceRunning())
        } else {
            println("WARNING: Service instance not available. Service may not be connected.")
            assertFalse("Service should report as not running", AutoClickService.isServiceRunning())
        }
    }

    @Test
    fun testClickCoordinateValidation() = runTest {
        val service = AutoClickService.getInstance()
        
        if (service != null) {
            println("=== Click Coordinate Validation Test ===")
            
            // Test various coordinate combinations
            val testCases = listOf(
                Pair(0, 0) to true,           // Origin
                Pair(100, 100) to true,       // Normal coordinates
                Pair(1000, 1000) to true,     // Large coordinates
                Pair(-1, 100) to false,       // Negative X
                Pair(100, -1) to false,       // Negative Y
                Pair(-10, -20) to false,      // Both negative
                Pair(Int.MAX_VALUE, 100) to true,  // Edge case
                Pair(100, Int.MAX_VALUE) to true   // Edge case
            )
            
            testCases.forEach { (coordinates, expected) ->
                val (x, y) = coordinates
                val result = service.isValidClickCoordinates(x, y)
                println("Coordinates ($x, $y): Expected $expected, Got $result")
                assertEquals("Coordinate validation for ($x, $y)", expected, result)
            }
        } else {
            println("WARNING: Cannot test coordinate validation - service not available")
        }
    }

    @Test
    fun testServiceCapabilities() = runTest {
        val service = AutoClickService.getInstance()
        
        if (service != null) {
            println("=== Service Capabilities Test ===")
            
            val canPerformGestures = service.canPerformGestures()
            val hasScreenshotPermission = service.hasScreenshotPermission()
            val isReady = service.isReady()
            
            println("Can Perform Gestures: $canPerformGestures")
            println("Has Screenshot Permission: $hasScreenshotPermission")
            println("Service Ready: $isReady")
            
            if (DeviceTestUtils.isAccessibilityServiceEnabled()) {
                assertTrue("Service should be able to perform gestures when enabled", canPerformGestures)
                assertTrue("Service should have screenshot permission when enabled", hasScreenshotPermission)
                assertTrue("Service should be ready when properly enabled", isReady)
            } else {
                println("WARNING: Service not enabled - capabilities may be limited")
            }
        } else {
            println("WARNING: Service not available for capability testing")
        }
    }

    @Test
    fun testServiceStateManagement() = runTest {
        val service = AutoClickService.getInstance()
        
        if (service != null) {
            println("=== Service State Management Test ===")
            
            // Test initial state
            val initialState = service.getCurrentState()
            val initialClickCount = service.getClickCount()
            val initiallyRunning = service.isAutoClickRunning()
            
            println("Initial State: ${initialState::class.simpleName}")
            println("Initial Click Count: $initialClickCount")
            println("Initially Running: $initiallyRunning")
            
            // Verify initial conditions
            assertEquals("Initial click count should be 0", 0, initialClickCount)
            assertFalse("Service should not be running initially", initiallyRunning)
            
            // Test state updates
            service.updateClickCount(5)
            assertEquals("Click count should be updated", 5, service.getClickCount())
            
            // Reset state
            service.stopAutoClick()
            assertEquals("Click count should reset after stop", 0, service.getClickCount())
            assertFalse("Service should not be running after stop", service.isAutoClickRunning())
        } else {
            println("WARNING: Service not available for state management testing")
        }
    }

    @Test
    fun testScreenshotCapability() = runTest {
        // Test screenshot functionality using UiAutomator
        val screenshotFile = DeviceTestUtils.takeScreenshot("accessibility_test_screenshot.png")
        
        if (screenshotFile != null) {
            println("=== Screenshot Test Results ===")
            println("Screenshot saved to: ${screenshotFile.absolutePath}")
            println("File size: ${screenshotFile.length()} bytes")
            
            assertTrue("Screenshot file should exist", screenshotFile.exists())
            assertTrue("Screenshot file should not be empty", screenshotFile.length() > 0)
            
            // Clean up
            screenshotFile.delete()
        } else {
            println("WARNING: Screenshot capability test failed - may require additional permissions")
        }
    }

    @Test
    fun testServiceErrorHandling() = runTest {
        val service = AutoClickService.getInstance()
        
        if (service != null) {
            println("=== Service Error Handling Test ===")
            
            // Test invalid click attempts
            var callbackExecuted = false
            var callbackResult: Boolean? = null
            
            service.performClick(-10, -20) { result ->
                callbackExecuted = true
                callbackResult = result
            }
            
            // Wait for callback
            Thread.sleep(1000)
            
            println("Callback Executed: $callbackExecuted")
            println("Callback Result: $callbackResult")
            
            assertTrue("Callback should be executed for invalid coordinates", callbackExecuted)
            assertEquals("Invalid coordinates should result in false", false, callbackResult)
        } else {
            println("WARNING: Service not available for error handling testing")
        }
    }

    @Test
    fun testServicePerformanceBaseline() = runTest {
        val service = AutoClickService.getInstance()
        
        if (service != null) {
            println("=== Service Performance Baseline Test ===")
            
            val iterations = 10
            val times = mutableListOf<Long>()
            
            repeat(iterations) {
                val startTime = System.nanoTime()
                
                // Test coordinate validation performance
                service.isValidClickCoordinates(100, 200)
                
                val endTime = System.nanoTime()
                times.add(endTime - startTime)
            }
            
            val averageTime = times.average()
            val minTime = times.minOrNull() ?: 0L
            val maxTime = times.maxOrNull() ?: 0L
            
            println("Coordinate Validation Performance:")
            println("Average: ${averageTime / 1_000_000.0} ms")
            println("Min: ${minTime / 1_000_000.0} ms")
            println("Max: ${maxTime / 1_000_000.0} ms")
            
            // Performance assertions (reasonable thresholds)
            assertTrue("Average validation time should be under 1ms", averageTime < 1_000_000)
            assertTrue("Max validation time should be under 5ms", maxTime < 5_000_000)
        } else {
            println("WARNING: Service not available for performance testing")
        }
    }

    @Test
    fun testAccessibilitySettingsIntent() = runTest {
        println("=== Accessibility Settings Intent Test ===")
        
        val intent = DeviceTestUtils.openAccessibilitySettings()
        
        assertNotNull("Accessibility settings intent should be created", intent)
        assertEquals("Intent should target accessibility settings", 
            android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS, intent.action)
        assertTrue("Intent should have NEW_TASK flag", 
            (intent.flags and android.content.Intent.FLAG_ACTIVITY_NEW_TASK) != 0)
        
        println("Accessibility settings intent created successfully")
        println("Action: ${intent.action}")
        println("Flags: ${intent.flags}")
    }

    @Test
    fun testDeviceCompatibility() = runTest {
        println("=== Device Compatibility Test ===")
        
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        
        // Check Android version
        val apiLevel = android.os.Build.VERSION.SDK_INT
        println("Android API Level: $apiLevel")
        assertTrue("API level should be 28 or higher", apiLevel >= 28)
        
        // Check accessibility manager availability
        val accessibilityManager = context.getSystemService(android.content.Context.ACCESSIBILITY_SERVICE)
        assertNotNull("Accessibility manager should be available", accessibilityManager)
        
        // Check if device supports required features
        val packageManager = context.packageManager
        val hasTouchscreen = packageManager.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN)
        
        println("Has Touchscreen: $hasTouchscreen")
        assertTrue("Device should have touchscreen for auto-clicking", hasTouchscreen)
        
        println("Device compatibility check passed")
    }
}