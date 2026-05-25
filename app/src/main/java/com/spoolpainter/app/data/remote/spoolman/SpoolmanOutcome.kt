package com.spoolpainter.app.data.remote.spoolman

sealed interface SpoolmanOutcome<out T> {
    data class Success<out T>(val data: T) : SpoolmanOutcome<T>
    data class HttpError(val code: Int, val message: String) : SpoolmanOutcome<Nothing>
    data class NetworkError(val cause: Throwable) : SpoolmanOutcome<Nothing>
    data class ParseError(val cause: Throwable) : SpoolmanOutcome<Nothing>
}

inline fun <T, R> SpoolmanOutcome<T>.flatMap(
    block: (T) -> SpoolmanOutcome<R>,
): SpoolmanOutcome<R> = when (this) {
    is SpoolmanOutcome.Success -> block(data)
    is SpoolmanOutcome.HttpError,
    is SpoolmanOutcome.NetworkError,
    is SpoolmanOutcome.ParseError -> @Suppress("UNCHECKED_CAST") (this as SpoolmanOutcome<R>)
}

inline fun <T, R> SpoolmanOutcome<T>.map(
    block: (T) -> R,
): SpoolmanOutcome<R> = flatMap { SpoolmanOutcome.Success(block(it)) }
