package com.autoclicker.android.device

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Device Test Suite for Android AutoClicker
 * 
 * This suite runs comprehensive device tests that verify the application works correctly
 * on real devices with actual hardware and system services.
 * 
 * Test Categories:
 * 1. AccessibilityServiceDeviceTest - Tests accessibility service functionality
 * 2. ImageRecognitionDeviceTest - Tests image matching accuracy and performance
 * 3. PerformanceStabilityDeviceTest - Tests performance optimization and stability
 * 
 * Prerequisites for running device tests:
 * - Physical device or emulator with API 28+
 * - AutoClicker accessibility service enabled (for full functionality tests)
 * - Sufficient device storage for test images
 * - Device should not be under heavy load during testing
 * 
 * To run this suite:
 * ./gradlew connectedAndroidTest --tests "com.autoclicker.android.device.DeviceTestSuite"
 * 
 * To run individual test classes:
 * ./gradlew connectedAndroidTest --tests "com.autoclicker.android.device.AccessibilityServiceDeviceTest"
 * ./gradlew connectedAndroidTest --tests "com.autoclicker.android.device.ImageRecognitionDeviceTest"
 * ./gradlew connectedAndroidTest --tests "com.autoclicker.android.device.PerformanceStabilityDeviceTest"
 * 
 * Expected Results:
 * - All tests should pass when accessibility service is properly enabled
 * - Some tests may show warnings if accessibility service is not enabled
 * - Performance tests should demonstrate acceptable response times and memory usage
 * - Image recognition tests should show good accuracy under various conditions
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    AccessibilityServiceDeviceTest::class,
    ImageRecognitionDeviceTest::class,
    PerformanceStabilityDeviceTest::class
)
class DeviceTestSuite