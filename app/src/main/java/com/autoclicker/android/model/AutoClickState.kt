package com.autoclicker.android.model

sealed class AutoClickState {
    object Idle : AutoClickState()
    object Searching : AutoClickState()
    object Clicking : AutoClickState()
    object Waiting : AutoClickState()
    data class Error(val message: String) : AutoClickState()
    data class Completed(val clickCount: Int) : AutoClickState()
}