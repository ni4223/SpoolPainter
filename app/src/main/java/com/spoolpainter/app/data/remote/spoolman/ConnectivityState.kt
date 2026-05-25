package com.spoolpainter.app.data.remote.spoolman

sealed interface ConnectivityState {
    data object Unknown : ConnectivityState
    data object Reachable : ConnectivityState
    data class Unreachable(val reason: String) : ConnectivityState
}
