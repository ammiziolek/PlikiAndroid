package com.pliki.app.data

import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

data class IndexedFile(
    val path: String,
    val name: String,
    val size: Long,
    val modified: Long,
    val isDir: Boolean,
    val category: FileCategory,
)

data class IndexState(
    val files: List<IndexedFile> = emptyList(),
    val scanning: Boolean = false,
    val ready: Boolean = false,
    val scanned: Int = 0,
)

data class Volume(val root: File, val label: String, val primary: Boolean)

data class VolumeStats(val total: Long, val free: Long) {
    val used get() = total - free
}

object Volumes {
    fun list(context: Context): List<Volume> {
        val sm = context.getSystemService(StorageManager::class.java)
        val list = runCatching {
            sm.storageVolumes.mapNotNull { v ->
                val dir = v.directory ?: return@mapNotNull null
                if (v.state != Environment.MEDIA_MOUNTED) return@mapNotNull null
                Volume(dir, if (v.isPrimary) "Pamięć wewnętrzna" else (v.getDescription(context) ?: "Karta SD"), v.isPrimary)
            }.sortedByDescending { it.primary }
        }.getOrDefault(emptyList())
        // Awaryjnie: zawsze przynajmniej pamięć wewnętrzna.
        return list.ifEmpty { listOf(Volume(primaryRoot(), "Pamięć wewnętrzna", true)) }
    }

    fun primaryRoot(): File = Environment.getExternalStorageDirectory()

    fun stats(context: Context, volume: Volume): VolumeStats {
        if (volume.primary) {
            runCatching {
                val ssm = context.getSystemService(StorageStatsManager::class.java)
                val total = ssm.getTotalBytes(StorageManager.UUID_DEFAULT)
                val free = ssm.getFreeBytes(StorageManager.UUID_DEFAULT)
                return VolumeStats(total, free)
            }
        }
        val fs = StatFs(volume.root.path)
        return VolumeStats(fs.totalBytes, fs.availableBytes)
    }

    fun volumeOf(context: Context, file: File): Volume? =
        list(context).firstOrNull { file.path == it.root.path || file.path.startsWith(it.root.path + "/") }

    /** Ścieżka do wyświetlenia, np. "DCIM/Camera" zamiast "/storage/emulated/0/DCIM/Camera". */
    fun displayPath(context: Context, path: String): String {
        val v = list(context).firstOrNull { path == it.root.path || path.startsWith(it.root.path + "/") }
            ?: return path
        val rel = path.removePrefix(v.root.path).trimStart('/')
        return when {
            rel.isEmpty() -> v.label
            v.primary -> rel
            else -> "${v.label}/$rel"
        }
    }
}

const val TRASH_DIR = ".PlikiKosz"

/**
 * Indeks wszystkich plików w pamięci. Skanuje drzewo katalogów w tle;
 * pomija ukryte foldery oraz Android/data i Android/obb (niedostępne od Androida 11).
 */
class StorageIndex(private val context: Context) {
    private val _state = MutableStateFlow(IndexState())
    val state: StateFlow<IndexState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private var pending: Job? = null

    fun rescan() {
        job?.cancel()
        job = scope.launch { scan() }
    }

    /** Ponowny skan z krótkim opóźnieniem (łączy kilka operacji w jeden skan). */
    fun rescanSoon() {
        pending?.cancel()
        pending = scope.launch {
            delay(800)
            rescan()
        }
    }

    private suspend fun scan() {
        _state.update { it.copy(scanning = true, scanned = 0) }
        val out = ArrayList<IndexedFile>(50_000)
        for (v in Volumes.list(context)) {
            val skip = setOf(
                File(v.root, "Android/data").path,
                File(v.root, "Android/obb").path,
            )
            val stack = ArrayDeque<File>()
            stack.add(v.root)
            while (stack.isNotEmpty()) {
                currentCoroutineContext().ensureActive()
                val dir = stack.removeLast()
                val children = dir.listFiles() ?: continue
                for (f in children) {
                    val name = f.name
                    if (name.startsWith(".")) continue
                    if (f.isDirectory) {
                        if (f.path in skip) continue
                        out.add(IndexedFile(f.path, name, 0, f.lastModified(), true, FileCategory.OTHER))
                        stack.add(f)
                    } else {
                        out.add(IndexedFile(f.path, name, f.length(), f.lastModified(), false, FileCategory.of(name)))
                    }
                }
                if (out.size % 3000 < children.size) _state.update { it.copy(scanned = out.size) }
            }
        }
        _state.value = IndexState(files = out, scanning = false, ready = true, scanned = out.size)
    }
}
