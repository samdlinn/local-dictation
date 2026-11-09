package dev.pivisolutions.dictus.debug

import org.json.JSONObject
import java.io.File

/** Metrics-only diagnostics. The legacy raw-text file is removed on first access. */
class TranscriptionDebugStore(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lock = Any()

    init {
        val legacy = File(file.parentFile, LEGACY_FILE_NAME)
        if (legacy.exists()) check(legacy.delete()) { "Could not remove legacy diagnostic data" }
    }

    /** Appends one entry, purging the file first if this process has not yet. Call off the main thread. */
    fun append(entry: TranscriptionDebugEntry) = synchronized(lock) {
        purge()
        file.appendText(entry.toJson().toString() + "\n")
    }

    /** Every entry of the last 7 days, oldest first, after dropping the older ones from the file. */
    fun readAll(): List<TranscriptionDebugEntry> = synchronized(lock) { purge() }

    /** « Clear »: empties the log. */
    fun clear() = synchronized(lock) {
        file.delete()
        Unit
    }

    /** Rewrites the file without the entries older than [RETENTION_MS] and returns the rest. */
    private fun purge(): List<TranscriptionDebugEntry> {
        if (!file.exists()) return emptyList()
        val cutoff = now() - RETENTION_MS
        val lines = file.readLines().filter { it.isNotBlank() }
        val kept = lines.mapNotNull { line ->
            val entry = runCatching { JSONObject(line) }.getOrNull()?.let(TranscriptionDebugEntry::fromJson)
            if (entry != null && entry.timestampMs >= cutoff) line to entry else null
        }
        if (kept.size != lines.size) {
            file.writeText(kept.joinToString("") { (line, _) -> line + "\n" })
        }
        return kept.map { (_, entry) -> entry }
    }

    companion object {
        /** iOS `PolishEventStore.retentionPeriod`. */
        const val RETENTION_MS = 7 * 24 * 3_600 * 1_000L

        /** iOS `PolishEventStore.fileName`, in the app's files directory. */
        const val FILE_NAME = "transcription_metrics_v2.jsonl"
        const val LEGACY_FILE_NAME = "polish_events.jsonl"
    }
}
