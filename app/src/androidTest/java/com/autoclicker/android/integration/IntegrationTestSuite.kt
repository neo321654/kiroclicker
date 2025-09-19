package com.autoclicker.android.integration

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Integration Test Suite for Android AutoClicker
 * 
 * This suite runs all integration tests that verify the interaction between
 * different components of the application:
 * 
 * 1. ConfigFragmentViewModelIntegrationTest - Tests UI-ViewModel interactions
 * 2. AutoClickServiceIntegrationTest - Tests service functionality and communication
 * 3. FullAutoClickCycleIntegrationTest - Tests complete auto-click workflows with mocks
 * 4. RepositoryImageMatcherIntegrationTest - Tests data persistence and image matching
 * 
 * To run this suite:
 * ./gradlew connectedAndroidTest --tests "com.autoclicker.android.integration.IntegrationTestSuite"
 * 
 * Or run individual test classes:
 * ./gradlew connectedAndroidTest --tests "com.autoclicker.android.integration.ConfigFragmentViewModelIntegrationTest"
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ConfigFragmentViewModelIntegrationTest::class,
    AutoClickServiceIntegrationTest::class,
    FullAutoClickCycleIntegrationTest::class,
    RepositoryImageMatcherIntegrationTest::class
)
class IntegrationTestSuite