package dev.pivisolutions.dictus.localcleanup

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.PowerManager
import dev.pivisolutions.dictus.core.text.CleanupSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** One job per private worker process. close() kills only the authenticated worker. */
internal class CleanupWorkerClient(private val context: Context) : CleanupSession {
    private val result = CompletableDeferred<String>()
    private val wakeLock = (context.getSystemService(Context.POWER_SERVICE) as PowerManager)
        .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LocalDictation:cleanup").apply { setReferenceCounted(false) }
    private val closed = AtomicBoolean(false)
    @Volatile private var bound = false
    @Volatile private var remote: Messenger? = null
    @Volatile private var pid = 0
    @Volatile private var input = ""
    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        when (message.what) {
            CleanupWorkerService.HELLO -> {
                if (message.arg2 != Process.myUid() || message.arg1 <= 0 || message.arg1 == Process.myPid()) {
                    result.completeExceptionally(IllegalStateException("Invalid cleanup worker identity"))
                } else {
                    pid = message.arg1
                    if (closed.get()) terminateWorker() else {
                        val request = Message.obtain(null, CleanupWorkerService.RUN)
                        request.data = Bundle().apply { putString("text", input) }
                        request.replyTo = replyMessenger()
                        try { remote?.send(request) } catch (_: Exception) { failed() }
                    }
                }
            }
            CleanupWorkerService.DONE -> result.complete(message.data.getString("text").orEmpty())
            CleanupWorkerService.FAILED -> failed()
        }
        true
    })
    // A method avoids referencing a property from its own initialization lambda.
    private fun replyMessenger() = reply
    private fun failed() { result.completeExceptionally(IllegalStateException("Cleanup worker unavailable")) }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            remote = Messenger(service)
            if (closed.get()) return
            try {
                remote?.send(Message.obtain(null, CleanupWorkerService.HELLO).apply { replyTo = reply })
            } catch (_: Exception) { failed() }
        }
        override fun onServiceDisconnected(name: ComponentName) { failed() }
        override fun onBindingDied(name: ComponentName) { failed() }
        override fun onNullBinding(name: ComponentName) { failed() }
    }

    override suspend fun infer(text: String): String = withContext(Dispatchers.Main.immediate) {
        check(!closed.get())
        input = text
        wakeLock.acquire(50_000)
        bound = context.bindService(Intent(context, CleanupWorkerService::class.java), connection, Context.BIND_AUTO_CREATE)
        check(bound) { "Could not bind cleanup worker" }
        result.await()
    }

    private fun terminateWorker() {
        if (pid > 0 && pid != Process.myPid() && remote?.binder?.isBinderAlive == true) {
            Process.killProcess(pid)
        }
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        try {
            terminateWorker()
        } finally {
            if (bound) {
                try { context.unbindService(connection) } catch (_: IllegalArgumentException) { }
                bound = false
            }
            input = ""
            result.cancel()
            if (wakeLock.isHeld) wakeLock.release()
        }
    }
}
