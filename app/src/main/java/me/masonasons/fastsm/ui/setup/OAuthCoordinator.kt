package me.masonasons.fastsm.ui.setup

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridges the OAuth redirect captured by MainActivity's deep-link intent
 * filter and the add-account view model waiting for the code. Lives in the
 * AppContainer so the activity and the composable can both reach it without
 * plumbing a callback through navigation.
 */
class OAuthCoordinator {
    private val _callbacks = MutableSharedFlow<OAuthCallback>(replay = 0, extraBufferCapacity = 1)
    val callbacks: SharedFlow<OAuthCallback> = _callbacks.asSharedFlow()

    suspend fun emit(callback: OAuthCallback) = _callbacks.emit(callback)
}

sealed interface OAuthCallback {
    data class Code(val code: String) : OAuthCallback
    data class Error(val error: String) : OAuthCallback
}
