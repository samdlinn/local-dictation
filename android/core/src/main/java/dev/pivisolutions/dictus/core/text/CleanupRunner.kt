package dev.pivisolutions.dictus.core.text

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** The native job lives outside this coroutine/process and must be disposable. */
interface CleanupSession : AutoCloseable {
    suspend fun infer(text: String): String
}

object CleanupRunner {
    suspend fun run(text: String, session: CleanupSession, timeoutMs: Long = 45_000): String = try {
        withTimeout(timeoutMs) { LocalCleanupPolicy.accept(text, session.infer(text)) ?: text }
    } catch (_: TimeoutCancellationException) {
        text
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        text
    } finally {
        try { session.close() } catch (_: Exception) { /* Preserve the result if disposal reports an OS failure. */ }
    }
}
