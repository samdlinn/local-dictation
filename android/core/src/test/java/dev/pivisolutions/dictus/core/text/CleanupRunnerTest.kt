package dev.pivisolutions.dictus.core.text

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.advanceTimeBy
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CleanupRunnerTest {
    private class Session(val action: suspend () -> String) : CleanupSession {
        var closed = false
        override suspend fun infer(text: String) = action()
        override fun close() { closed = true }
    }

    @Test fun `successful cleanup disposes worker`() = runTest {
        val session = Session { "Send the report tomorrow. [[END]]" }
        assertEquals("Send the report tomorrow.", CleanupRunner.run("um send the report tomorrow", session))
        assertTrue(session.closed)
    }

    @Test fun `unresponsive worker hits deadline and is disposed`() = runTest {
        // Models a worker that never replies; no native operation runs in the caller.
        val session = Session { awaitCancellation() }
        val result = async { CleanupRunner.run("send the report", session, 1000) }
        runCurrent()
        advanceTimeBy(1000)
        runCurrent()
        assertEquals("send the report", result.await())
        assertTrue(session.closed)
    }

    @Test fun `worker failure preserves original and disposes resources`() = runTest {
        val session = Session { throw IllegalStateException("worker failed") }
        assertEquals("do not send 15 dollars", CleanupRunner.run("do not send 15 dollars", session))
        assertTrue(session.closed)
    }

    @Test fun `caller cancellation is propagated and disposes worker`() = runTest {
        val entered = CompletableDeferred<Unit>()
        val session = Session { entered.complete(Unit); awaitCancellation() }
        val result = async { CleanupRunner.run("send the report", session) }
        entered.await()
        result.cancel()
        result.join()
        assertTrue(result.isCancelled)
        assertTrue(session.closed)
    }

    @Test fun `rejected output preserves the complete original`() = runTest {
        val session = Session { "Send 50 dollars. [[END]]" }
        assertEquals("do not send 15 dollars", CleanupRunner.run("do not send 15 dollars", session))
        assertTrue(session.closed)
    }
}
