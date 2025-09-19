package com.autoclicker.android.model

import org.junit.Test
import org.junit.Assert.*

class AutoClickStateTest {

    @Test
    fun `idle state should be singleton`() {
        val state1 = AutoClickState.Idle
        val state2 = AutoClickState.Idle

        assertSame("Idle states should be the same instance", state1, state2)
    }

    @Test
    fun `searching state should be singleton`() {
        val state1 = AutoClickState.Searching
        val state2 = AutoClickState.Searching

        assertSame("Searching states should be the same instance", state1, state2)
    }

    @Test
    fun `clicking state should be singleton`() {
        val state1 = AutoClickState.Clicking
        val state2 = AutoClickState.Clicking

        assertSame("Clicking states should be the same instance", state1, state2)
    }

    @Test
    fun `waiting state should be singleton`() {
        val state1 = AutoClickState.Waiting
        val state2 = AutoClickState.Waiting

        assertSame("Waiting states should be the same instance", state1, state2)
    }

    @Test
    fun `error state should contain message`() {
        val errorMessage = "Test error message"
        val errorState = AutoClickState.Error(errorMessage)

        assertEquals("Error state should contain the message", errorMessage, errorState.message)
    }

    @Test
    fun `error states with same message should be equal`() {
        val message = "Same error"
        val error1 = AutoClickState.Error(message)
        val error2 = AutoClickState.Error(message)

        assertEquals("Error states with same message should be equal", error1, error2)
    }

    @Test
    fun `error states with different messages should not be equal`() {
        val error1 = AutoClickState.Error("Error 1")
        val error2 = AutoClickState.Error("Error 2")

        assertNotEquals("Error states with different messages should not be equal", error1, error2)
    }

    @Test
    fun `completed state should contain click count`() {
        val clickCount = 42
        val completedState = AutoClickState.Completed(clickCount)

        assertEquals("Completed state should contain click count", clickCount, completedState.clickCount)
    }

    @Test
    fun `completed states with same count should be equal`() {
        val count = 10
        val completed1 = AutoClickState.Completed(count)
        val completed2 = AutoClickState.Completed(count)

        assertEquals("Completed states with same count should be equal", completed1, completed2)
    }

    @Test
    fun `completed states with different counts should not be equal`() {
        val completed1 = AutoClickState.Completed(5)
        val completed2 = AutoClickState.Completed(10)

        assertNotEquals("Completed states with different counts should not be equal", completed1, completed2)
    }

    @Test
    fun `all states should have different types`() {
        val states = listOf(
            AutoClickState.Idle,
            AutoClickState.Searching,
            AutoClickState.Clicking,
            AutoClickState.Waiting,
            AutoClickState.Error("test"),
            AutoClickState.Completed(1)
        )

        // Check that all states are of different classes
        val stateClasses = states.map { it::class }
        val uniqueClasses = stateClasses.toSet()

        assertEquals("All states should have different types", stateClasses.size, uniqueClasses.size)
    }

    @Test
    fun `when expression should handle all states`() {
        val states = listOf(
            AutoClickState.Idle,
            AutoClickState.Searching,
            AutoClickState.Clicking,
            AutoClickState.Waiting,
            AutoClickState.Error("test"),
            AutoClickState.Completed(1)
        )

        // This test ensures exhaustive when expressions work
        states.forEach { state ->
            val result = when (state) {
                is AutoClickState.Idle -> "idle"
                is AutoClickState.Searching -> "searching"
                is AutoClickState.Clicking -> "clicking"
                is AutoClickState.Waiting -> "waiting"
                is AutoClickState.Error -> "error: ${state.message}"
                is AutoClickState.Completed -> "completed: ${state.clickCount}"
            }

            assertNotNull("When expression should handle all states", result)
        }
    }
}