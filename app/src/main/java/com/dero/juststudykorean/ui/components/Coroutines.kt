package com.dero.juststudykorean.ui.components

import kotlinx.coroutines.CancellationException

/** runCatching for suspend calls: a cancelled coroutine stays cancelled instead of becoming a failure. */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
