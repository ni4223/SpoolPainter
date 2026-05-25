package com.spoolpainter.app.ui.common

sealed interface UiEffect {
    data class ShowSnackbar(val message: String) : UiEffect
    data class Navigate(val destination: String) : UiEffect
}
