package com.muddakir.quran

import kotlinx.coroutines.CancellationException

/**
 * Like Kotlin's runCatching, but never converts coroutine cancellation into a
 * normal failure. Cancellation must propagate so downloads/searches stop
 * promptly when their screen or scope is destroyed.
 */
suspend inline fun <T> runCatchingCancellable(crossinline block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (t: Throwable) {
        Result.failure(t)
    }
