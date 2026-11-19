package com.pliki.app.data

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest

object Cleanup {
    const val LARGE = 100L * 1024 * 1024
    const val OLD_DAYS = 90

    fun large(files: List<IndexedFile>) = files.filter { !it.isDir && it.size >= LARGE }.sortedByDescending { it.size }

    fun oldDownloads(files: List<IndexedFile>): List<IndexedFile> {
        val dl = File(Volumes.primaryRoot(), "Download").path + "/"
        val limit = System.currentTimeMillis() - OLD_DAYS * 24L * 3600 * 1000
        return files.filter { !it.isDir && it.path.startsWith(dl) && it.modified < limit }.sortedByDescending { it.size }
    }

    fun apks(files: List<IndexedFile>) = files.filter { !it.isDir && it.category == FileCategory.APK }.sortedByDescending { it.size }

    private fun partialHash(path: String, size: Long): String {
        val md = MessageDigest.getInstance("MD5")
        val buf = ByteArray(16 * 1024)
        RandomAccessFile(path, "r").use { raf ->
            val n1 = raf.read(buf); if (n1 > 0) md.update(buf, 0, n1)
            if (size > buf.size * 2) {
                raf.seek(size - buf.size)
                val n2 = raf.read(buf); if (n2 > 0) md.update(buf, 0, n2)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun fullHash(path: String): String {
        val md = MessageDigest.getInstance("SHA-1")
        File(path).inputStream().buffered().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf); if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** Grupy identycznych plików (ten sam rozmiar i ta sama zawartość), od największej straty miejsca. */
    suspend fun duplicates(files: List<IndexedFile>, onProgress: (Float) -> Unit = {}): List<List<IndexedFile>> {
        val bySize = files.filter { !it.isDir && it.size >= 1024 }.groupBy { it.size }.values.filter { it.size > 1 }
        val result = mutableListOf<List<IndexedFile>>()
        bySize.forEachIndexed { i, group ->
            currentCoroutineContext().ensureActive()
            onProgress(i.toFloat() / bySize.size.coerceAtLeast(1))
            val byPartial = group.groupBy { runCatching { partialHash(it.path, it.size) }.getOrNull() }
                .filterKeys { it != null }.values.filter { it.size > 1 }
            for (pg in byPartial) {
                val byFull = pg.groupBy { runCatching { fullHash(it.path) }.getOrNull() }
                    .filterKeys { it != null }.values.filter { it.size > 1 }
                result += byFull
            }
        }
        onProgress(1f)
        return result.sortedByDescending { it[0].size * (it.size - 1) }
    }
}
