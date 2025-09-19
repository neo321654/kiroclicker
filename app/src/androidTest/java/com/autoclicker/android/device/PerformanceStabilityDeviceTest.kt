package com.autoclicker.android.device

import android.graphics.Bitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.autoclicker.android.model.AutoClickState
import com.autoclicker.android.model.ClickConfig
import com.autoclicker.android.service.AutoClickService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Device tests for performance optimization and stability.
 * These tests verify that the application performs well under various conditions
 * and maintains stability during extended use.
 */
@ExperimentalCoroutinesApi
@RunWith(AndroidJUnit4::class)
class PerformanceStabilityDeviceTest {

    private lateinit var testImages: DeviceTestUtils.TestImageSet

    @Before
    fun setup() {
        testImages = DeviceTestUtils.createTestImages()
        println("=== Performance and Stability Device Test Setup ===")
    }

    @Test
    fun testClickAccuracyPerformance() = runTest {
        println("=== Click Accuracy Performance Test ===")
        
        val result = DeviceTestUtils.testClickAccuracy()
        
        println("Click Accuracy Results:")
        println("  Overall Success Rate: ${result.overallSuccessRate * 100}%")
        println("  Coordinate Validation Rate: ${result.coordinateValidationRate * 100}%")
        println("  Average Execution Time: ${result.averageExecutionTime / 1_000_000.0} ms")
        
        result.testResults.forEach { testResult ->
            println("  Position (${testResult.x}, ${testResult.y}):")
            println("    Valid: ${testResult.coordinatesValid}")
            println("    Success: ${testResult.clickSuccessful}")
            println("    Time: ${testResult.executionTimeNanos / 1_000_000.0} ms")
        }
        
        // Performance assertions
        assertTrue("Coordinate validation rate should be 100%", result.coordinateValidationRate == 1.0)
        assertTrue("Average execution time should be under 100ms", 
            result.averageExecutionTime < 100_000_000) // 100ms in nanoseconds
        
        // If accessibility service is enabled, success rate should be high
        if (DeviceTestUtils.isAccessibilityServiceEnabled()) {
            assertTrue("Success rate should be at least 80% when service is enabled", 
                result.overallSuccessRate >= 0.8)
        }
    }

    @Test
    fun testMemoryUsageStability() = runTest {
        println("=== Memory Usage Stability Test ===")
        
        // Monitor memory usage for 30 seconds during various operations
        val memoryResult = DeviceTestUtils.monitorMemoryUsage(30000)
        
        // Perform various operations during monitoring
        Thread {
            val service = AutoClickService.getInstance()
            repeat(100) { iteration ->
                // Simulate auto-click operations
                service?.updateClickCount(iteration)
                service?.updateState(AutoClickState.Searching)
                Thread.sleep(100)
                service?.updateState(AutoClickState.Clicking)
                Thread.sleep(100)
                service?.updateState(AutoClickState.Waiting)
                Thread.sleep(100)
            }
        }.start()
        
        Thread.sleep(30000) // Wait for monitoring to complete
        
        println("Memory Usage Results:")
        println("  Average Used: ${memoryResult.averageUsedMemoryBytes / 1024 / 1024} MB")
        println("  Max Used: ${memoryResult.maxUsedMemoryBytes / 1024 / 1024} MB")
        println("  Min Used: ${memoryResult.minUsedMemoryBytes / 1024 / 1024} MB")
        println("  Memory Leak Detected: ${memoryResult.memoryLeakDetected}")
        println("  Total Measurements: ${memoryResult.measurements.size}")
        
        // Stability assertions
        assertFalse("No memory leak should be detected", memoryResult.memoryLeakDetected)
        assertTrue("Memory usage should be reasonable", 
            memoryResult.maxUsedMemoryBytes < 200 * 1024 * 1024) // Less than 200MB
        assertTrue("Memory should not grow excessively", 
            memoryResult.maxUsedMemoryBytes < memoryResult.averageUsedMemoryBytes * 2)
    }

    @Test
    fun testServiceStabilityUnderLoad() = runTest {
        println("=== Service Stability Under Load Test ===")
        
        val service = AutoClickService.getInstance()
        if (service == null) {
            println("WARNING: Service not available - skipping stability test")
            return@runTest
        }
        
        val iterations = 1000
        val errors = mutableListOf<String>()
        val executionTimes = mutableListOf<Long>()
        
        println("Performing $iterations operations...")
        
        repeat(iterations) { i ->
            try {
                val startTime = System.nanoTime()
                
                // Perform various service operations
                service.isValidClickCoordinates(i % 1000, (i * 2) % 1000)
                service.updateClickCount(i)
                service.updateState(when (i % 4) {
                    0 -> AutoClickState.Idle
                    1 -> AutoClickState.Searching
                    2 -> AutoClickState.Clicking
                    3 -> AutoClickState.Waiting
                    else -> AutoClickState.Idle
                })
                
                val endTime = System.nanoTime()
                executionTimes.add(endTime - startTime)
                
                if ((i + 1) % 100 == 0) {
                    println("  Completed ${i + 1}/$iterations operations")
                }
                
            } catch (e: Exception) {
                errors.add("Iteration $i: ${e.message}")
            }
        }
        
        val averageTime = executionTimes.average() / 1_000_000.0 // Convert to ms
        val maxTime = (executionTimes.maxOrNull() ?: 0L) / 1_000_000.0
        val errorRate = errors.size.toDouble() / iterations
        
        println("Stability Test Results:")
        println("  Total Operations: $iterations")
        println("  Errors: ${errors.size}")
        println("  Error Rate: ${errorRate * 100}%")
        println("  Average Execution Time: $averageTime ms")
        println("  Max Execution Time: $maxTime ms")
        
        // Stability assertions
        assertTrue("Error rate should be under 1%", errorRate < 0.01)
        assertTrue("Average execution time should be reasonable", averageTime < 10.0)
        assertTrue("Max execution time should not exceed 100ms", maxTime < 100.0)
        
        if (errors.isNotEmpty()) {
            println("Errors encountered:")
            errors.take(5).forEach { println("  $it") }
        }
    }

    @Test
    fun testConcurrentOperationsStability() = runTest {
        println("=== Concurrent Operations Stability Test ===")
        
        val service = AutoClickService.getInstance()
        if (service == null) {
            println("WARNING: Service not available - skipping concurrent test")
            return@runTest
        }
        
        val threadCount = 5
        val operationsPerThread = 200
        val latch = CountDownLatch(threadCount)
        val errors = mutableListOf<String>()
        val results = mutableListOf<Boolean>()
        
        // Start multiple threads performing concurrent operations
        repeat(threadCount) { threadId ->
            Thread {
                try {
                    repeat(operationsPerThread) { opId ->
                        // Perform concurrent service operations
                        val x = (threadId * 100 + opId) % 1000
                        val y = (threadId * 150 + opId * 2) % 1000
                        
                        val isValid = service.isValidClickCoordinates(x, y)
                        results.add(isValid)
                        
                        service.updateClickCount(threadId * operationsPerThread + opId)
                        
                        // Small delay to simulate real usage
                        Thread.sleep(1)
                    }
                } catch (e: Exception) {
                    synchronized(errors) {
                        errors.add("Thread $threadId: ${e.message}")
                    }
                } finally {
                    latch.countDown()
                }
            }.start()
        }
        
        // Wait for all threads to complete
        val completed = latch.await(30, TimeUnit.SECONDS)
        
        println("Concurrent Operations Results:")
        println("  Threads: $threadCount")
        println("  Operations per thread: $operationsPerThread")
        println("  Total operations: ${threadCount * operationsPerThread}")
        println("  Completed in time: $completed")
        println("  Errors: ${errors.size}")
        println("  Successful results: ${results.count { it }}")
        
        // Concurrent stability assertions
        assertTrue("All threads should complete in time", completed)
        assertTrue("Error count should be minimal", errors.size < threadCount * operationsPerThread * 0.01)
        assertTrue("Most coordinate validations should succeed", 
            results.count { it }.toDouble() / results.size > 0.9)
        
        if (errors.isNotEmpty()) {
            println("Concurrent errors:")
            errors.take(3).forEach { println("  $it") }
        }
    }

    @Test
    fun testLongRunningOperationStability() = runTest {
        println("=== Long Running Operation Stability Test ===")
        
        val service = AutoClickService.getInstance()
        if (service == null) {
            println("WARNING: Service not available - skipping long running test")
            return@runTest
        }
        
        val testDurationMs = 60000L // 1 minute
        val startTime = System.currentTimeMillis()
        var operationCount = 0
        val errors = mutableListOf<String>()
        val memorySnapshots = mutableListOf<Long>()
        
        println("Running operations for ${testDurationMs / 1000} seconds...")
        
        while (System.currentTimeMillis() - startTime < testDurationMs) {
            try {
                // Simulate continuous auto-click operations
                val x = (operationCount * 17) % 1000
                val y = (operationCount * 23) % 1000
                
                service.isValidClickCoordinates(x, y)
                service.updateClickCount(operationCount)
                
                // Cycle through states
                val state = when (operationCount % 5) {
                    0 -> AutoClickState.Idle
                    1 -> AutoClickState.Searching
                    2 -> AutoClickState.Clicking
                    3 -> AutoClickState.Waiting
                    4 -> AutoClickState.Completed(operationCount / 5)
                    else -> AutoClickState.Idle
                }
                service.updateState(state)
                
                operationCount++
                
                // Take memory snapshot every 1000 operations
                if (operationCount % 1000 == 0) {
                    val runtime = Runtime.getRuntime()
                    val usedMemory = runtime.totalMemory() - runtime.freeMemory()
                    memorySnapshots.add(usedMemory)
                    
                    println("  Operations: $operationCount, Memory: ${usedMemory / 1024 / 1024} MB")
                }
                
                Thread.sleep(10) // Small delay to simulate real timing
                
            } catch (e: Exception) {
                errors.add("Operation $operationCount: ${e.message}")
            }
        }
        
        val actualDuration = System.currentTimeMillis() - startTime
        val operationsPerSecond = operationCount.toDouble() / (actualDuration / 1000.0)
        
        println("Long Running Test Results:")
        println("  Duration: ${actualDuration / 1000.0} seconds")
        println("  Total Operations: $operationCount")
        println("  Operations per Second: $operationsPerSecond")
        println("  Errors: ${errors.size}")
        println("  Error Rate: ${errors.size.toDouble() / operationCount * 100}%")
        
        if (memorySnapshots.isNotEmpty()) {
            val initialMemory = memorySnapshots.first()
            val finalMemory = memorySnapshots.last()
            val memoryGrowth = finalMemory - initialMemory
            
            println("  Initial Memory: ${initialMemory / 1024 / 1024} MB")
            println("  Final Memory: ${finalMemory / 1024 / 1024} MB")
            println("  Memory Growth: ${memoryGrowth / 1024 / 1024} MB")
        }
        
        // Long running stability assertions
        assertTrue("Should complete significant number of operations", operationCount > 1000)
        assertTrue("Error rate should be under 0.1%", errors.size.toDouble() / operationCount < 0.001)
        assertTrue("Operations per second should be reasonable", operationsPerSecond > 10.0)
        
        if (memorySnapshots.size >= 2) {
            val memoryGrowth = memorySnapshots.last() - memorySnapshots.first()
            assertTrue("Memory growth should be limited", memoryGrowth < 50 * 1024 * 1024) // Less than 50MB growth
        }
    }

    @Test
    fun testResourceCleanupStability() = runTest {
        println("=== Resource Cleanup Stability Test ===")
        
        val service = AutoClickService.getInstance()
        if (service == null) {
            println("WARNING: Service not available - skipping cleanup test")
            return@runTest
        }
        
        val cycles = 50
        val operationsPerCycle = 100
        val memoryMeasurements = mutableListOf<Long>()
        
        repeat(cycles) { cycle ->
            // Perform operations
            repeat(operationsPerCycle) { op ->
                service.updateClickCount(op)
                service.updateState(AutoClickState.Searching)
                service.updateState(AutoClickState.Clicking)
                service.updateState(AutoClickState.Waiting)
            }
            
            // Reset/cleanup
            service.stopAutoClick()
            
            // Force garbage collection
            System.gc()
            Thread.sleep(100)
            
            // Measure memory
            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            memoryMeasurements.add(usedMemory)
            
            if ((cycle + 1) % 10 == 0) {
                println("  Completed cycle ${cycle + 1}/$cycles, Memory: ${usedMemory / 1024 / 1024} MB")
            }
        }
        
        // Analyze memory trend
        val firstHalf = memoryMeasurements.take(cycles / 2)
        val secondHalf = memoryMeasurements.drop(cycles / 2)
        
        val firstHalfAvg = firstHalf.average()
        val secondHalfAvg = secondHalf.average()
        val memoryIncrease = secondHalfAvg - firstHalfAvg
        
        println("Resource Cleanup Results:")
        println("  Cycles: $cycles")
        println("  Operations per cycle: $operationsPerCycle")
        println("  First half average memory: ${firstHalfAvg / 1024 / 1024} MB")
        println("  Second half average memory: ${secondHalfAvg / 1024 / 1024} MB")
        println("  Memory increase: ${memoryIncrease / 1024 / 1024} MB")
        
        // Resource cleanup assertions
        assertTrue("Memory increase should be minimal", memoryIncrease < 20 * 1024 * 1024) // Less than 20MB
        assertTrue("Memory should not grow excessively", secondHalfAvg < firstHalfAvg * 1.5)
    }

    @Test
    fun testErrorRecoveryStability() = runTest {
        println("=== Error Recovery Stability Test ===")
        
        val service = AutoClickService.getInstance()
        if (service == null) {
            println("WARNING: Service not available - skipping error recovery test")
            return@runTest
        }
        
        val errorScenarios = listOf(
            "invalid_coordinates" to { service.isValidClickCoordinates(-1, -1) },
            "extreme_coordinates" to { service.isValidClickCoordinates(Int.MAX_VALUE, Int.MAX_VALUE) },
            "rapid_state_changes" to {
                service.updateState(AutoClickState.Searching)
                service.updateState(AutoClickState.Idle)
                service.updateState(AutoClickState.Error("Test error"))
                service.updateState(AutoClickState.Idle)
            },
            "large_click_count" to { service.updateClickCount(Int.MAX_VALUE) },
            "negative_click_count" to { service.updateClickCount(-1) }
        )
        
        val recoveryResults = mutableMapOf<String, Boolean>()
        
        errorScenarios.forEach { (scenarioName, errorOperation) ->
            println("Testing error scenario: $scenarioName")
            
            try {
                // Perform error-inducing operation
                errorOperation()
                
                // Verify service can still perform normal operations
                val normalResult1 = service.isValidClickCoordinates(100, 200)
                service.updateClickCount(42)
                service.updateState(AutoClickState.Idle)
                val normalResult2 = service.getCurrentState()
                
                val recovered = normalResult1 && normalResult2 != null
                recoveryResults[scenarioName] = recovered
                
                println("  Recovery successful: $recovered")
                
            } catch (e: Exception) {
                println("  Exception during $scenarioName: ${e.message}")
                recoveryResults[scenarioName] = false
            }
        }
        
        println("Error Recovery Results:")
        recoveryResults.forEach { (scenario, recovered) ->
            println("  $scenario: ${if (recovered) "RECOVERED" else "FAILED"}")
        }
        
        val recoveryRate = recoveryResults.values.count { it }.toDouble() / recoveryResults.size
        println("  Overall Recovery Rate: ${recoveryRate * 100}%")
        
        // Error recovery assertions
        assertTrue("Recovery rate should be at least 80%", recoveryRate >= 0.8)
        assertTrue("Service should recover from invalid coordinates", 
            recoveryResults["invalid_coordinates"] == true)
        assertTrue("Service should recover from rapid state changes", 
            recoveryResults["rapid_state_changes"] == true)
    }
}