package dev.pivisolutions.dictus.localcleanup

import android.content.Context
import android.app.ActivityManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

object LocalCleanup {
    private const val MODEL = "Qwen3-1.7B-Q8_0.gguf"
    private const val SIZE = 1834426016L
    private const val SHA = "061b54daade076b5d3362dac252678d17da8c68f07560be70818cace6590cb1a"
    private const val URL_MODEL = "https://huggingface.co/Qwen/Qwen3-1.7B-GGUF/resolve/90862c4b9d2787eaed51d12237eafdfe7c5f6077/Qwen3-1.7B-Q8_0.gguf"
    private val mutex = Mutex()
    val downloading = AtomicBoolean(false)

    internal fun modelPath(context: Context) = File(context.filesDir, "cleanup/$MODEL")
    private fun prefs(context: Context) = context.getSharedPreferences("local_cleanup", Context.MODE_PRIVATE)
    fun ready(context: Context) = modelPath(context).let { it.isFile && it.length() == SIZE }
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", false)
    fun enable(context: Context, value: Boolean) { prefs(context).edit().putBoolean("enabled", value).apply() }

    suspend fun download(context: Context, progress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) {
        check(downloading.compareAndSet(false, true)) { "A download is already running" }
        val dest = modelPath(context)
        dest.parentFile?.mkdirs()
        val temporary = File(dest.parentFile, "$MODEL.part")
        val connection = URL(URL_MODEL).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = 20000
            connection.readTimeout = 30000
            check(connection.responseCode in 200..299) { "Model download failed: ${connection.responseCode}" }
            val digest = MessageDigest.getInstance("SHA-256")
            var received = 0L
            var lastPercent = -1
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        received += count
                        check(received <= SIZE) { "Unexpected model size" }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        val percent = (received * 100 / SIZE).toInt()
                        if (percent != lastPercent) {
                            withContext(Dispatchers.Main) { progress(percent) }
                            lastPercent = percent
                        }
                    }
                }
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
            check(received == SIZE && hash == SHA) { "Model verification failed. Please download again." }
            if (dest.exists()) check(dest.delete())
            check(temporary.renameTo(dest)) { "Could not save the downloaded model" }
        } finally {
            connection.disconnect()
            temporary.delete()
            downloading.set(false)
        }
    }

    suspend fun cleanOrOriginal(context: Context, text: String): String {
        if (!enabled(context) || !ready(context) || text.isBlank() || text.length > 6000) return text
        val memory = ActivityManager.MemoryInfo()
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(memory)
        if (memory.lowMemory || memory.availMem < 3_000_000_000L || !mutex.tryLock()) return text
        return try {
            dev.pivisolutions.dictus.core.text.CleanupRunner.run(text, CleanupWorkerClient(context.applicationContext))
        } finally {
            mutex.unlock()
        }
    }
}
