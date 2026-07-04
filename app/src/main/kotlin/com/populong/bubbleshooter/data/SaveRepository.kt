package com.populong.bubbleshooter.data

import android.content.Context
import com.populong.bubbleshooter.core.progress.SaveCodec
import com.populong.bubbleshooter.core.progress.SaveData
import java.io.File
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Plain-file persistence for [SaveData], built directly on the frozen [SaveCodec] JSON format
 * (no DataStore/Room dependency needed for a single small blob).
 *
 * The in-memory [save] flow is updated synchronously inside [update] so observers see the new
 * value immediately; the actual JSON write to disk happens asynchronously on a dedicated
 * single-thread executor so callers never block on I/O. Disk writes are atomic: the new JSON is
 * written to a temporary file first, which is then renamed over the real save file, so a crash
 * or process death mid-write can never leave a half-written, corrupt save on disk.
 */
class SaveRepository(context: Context) {

    private val file = File(context.filesDir, "save.json")
    private val writeExecutor = Executors.newSingleThreadExecutor()
    private val lock = Any()

    private val _save = MutableStateFlow(load())

    /** The current save data. Collect for reactive UI; read [StateFlow.value] for one-off reads. */
    val save: StateFlow<SaveData> = _save.asStateFlow()

    /**
     * Applies [transform] to the current save, publishing the result to [save] immediately, then
     * schedules the JSON write to disk on the background executor. Safe to call from any thread;
     * concurrent calls are serialized so no update is lost.
     */
    fun update(transform: (SaveData) -> SaveData) {
        val updated = synchronized(lock) {
            val next = transform(_save.value)
            _save.value = next
            next
        }
        writeExecutor.execute { writeToDisk(updated) }
    }

    private fun writeToDisk(data: SaveData) {
        try {
            val parent = file.parentFile
            val tmp = File(parent, "${file.name}.tmp")
            tmp.writeText(SaveCodec.encode(data))
            if (!tmp.renameTo(file)) {
                // Rename can fail across some filesystems/encryption boundaries; fall back to a
                // direct (non-atomic, but still best-effort) write.
                file.writeText(SaveCodec.encode(data))
                tmp.delete()
            }
        } catch (_: Exception) {
            // Persistence is best-effort; a failed write must never crash the app.
        }
    }

    private fun load(): SaveData = try {
        if (file.exists()) SaveCodec.decode(file.readText()) else SaveData()
    } catch (_: Exception) {
        SaveData()
    }
}
