package com.novastats.app.util

/**
 * A runCatching variant for inline call sites that may invoke suspending functions.
 * Cancellation is control flow and must always reach the coroutine/WorkManager caller.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
