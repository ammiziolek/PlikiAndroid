package com.pliki.app.data

import android.content.Context
import android.media.MediaScannerConnection
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class FileOps(private val context: Context, db: AppDatabase) {
    private val dao = db.dao()

    /** Zwraca wolną nazwę w folderze: "plik.pdf", "plik (1).pdf", ... */
    fun uniqueTarget(dir: File, name: String): File {
        var target = File(dir, name)
        if (!target.exists()) return target
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (target.exists()) {
            target = File(dir, "$base ($i)$ext")
            i++
        }
        return target
    }

    private fun isInside(child: File, parent: File): Boolean =
        child.canonicalPath == parent.canonicalPath || child.canonicalPath.startsWith(parent.canonicalPath + "/")

    fun scanMedia(paths: Collection<String>) {
        if (paths.isEmpty()) return
        MediaScannerConnection.scanFile(context, paths.toTypedArray(), null, null)
    }

    private fun mediaPaths(f: File): List<String> =
        if (f.isDirectory) f.walkTopDown().filter { it.isFile }.map { it.path }.take(5000).toList() else listOf(f.path)

    suspend fun copy(sources: List<File>, destDir: File): Int {
        var n = 0
        val touched = mutableListOf<String>()
        for (src in sources) {
            if (src.isDirectory && isInside(destDir, src)) continue
            val target = uniqueTarget(destDir, src.name)
            if (src.isDirectory) src.copyRecursively(target) else src.copyTo(target)
            touched += mediaPaths(target)
            n++
        }
        scanMedia(touched)
        return n
    }

    suspend fun move(sources: List<File>, destDir: File): Int {
        var n = 0
        val touched = mutableListOf<String>()
        for (src in sources) {
            if (src.parentFile?.canonicalPath == destDir.canonicalPath) continue
            if (src.isDirectory && isInside(destDir, src)) continue
            val before = mediaPaths(src)
            val target = uniqueTarget(destDir, src.name)
            if (!src.renameTo(target)) {
                if (src.isDirectory) src.copyRecursively(target) else src.copyTo(target)
                src.deleteRecursively()
            }
            dao.movePath(src.path, target.path)
            dao.movePrefix(src.path, target.path)
            touched += before
            touched += mediaPaths(target)
            n++
        }
        scanMedia(touched)
        return n
    }

    suspend fun rename(file: File, newName: String): File? {
        val target = File(file.parentFile, newName)
        if (target.exists()) return null
        val before = mediaPaths(file)
        if (!file.renameTo(target)) return null
        dao.movePath(file.path, target.path)
        dao.movePrefix(file.path, target.path)
        scanMedia(before + mediaPaths(target))
        return target
    }

    fun mkdir(parent: File, name: String): File? {
        val dir = File(parent, name)
        if (dir.exists()) return null
        return if (dir.mkdirs()) dir else null
    }

    private fun trashRootFor(file: File): File {
        val vol = Volumes.volumeOf(context, file)?.root ?: Volumes.primaryRoot()
        val root = File(vol, TRASH_DIR)
        if (!root.exists()) {
            root.mkdirs()
            File(root, ".nomedia").createNewFile()
        }
        return root
    }

    suspend fun moveToTrash(files: List<File>): Int {
        var n = 0
        val touched = mutableListOf<String>()
        for (f in files) {
            if (!f.exists()) continue
            val before = mediaPaths(f)
            val size = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
            val target = File(trashRootFor(f), "${System.currentTimeMillis()}_${f.name}")
            if (!f.renameTo(target)) continue
            dao.insertTrash(TrashEntity(trashPath = target.path, originalPath = f.path, name = f.name, size = size, isDir = target.isDirectory))
            dao.movePath(f.path, target.path)
            dao.movePrefix(f.path, target.path)
            touched += before
            n++
        }
        scanMedia(touched)
        return n
    }

    suspend fun restore(entries: List<TrashEntity>): Int {
        var n = 0
        val touched = mutableListOf<String>()
        for (e in entries) {
            val src = File(e.trashPath)
            if (!src.exists()) {
                dao.deleteTrash(e.id); continue
            }
            val orig = File(e.originalPath)
            orig.parentFile?.mkdirs()
            val target = if (orig.exists()) uniqueTarget(orig.parentFile!!, orig.name) else orig
            if (!src.renameTo(target)) continue
            dao.movePath(src.path, target.path)
            dao.movePrefix(src.path, target.path)
            dao.deleteTrash(e.id)
            touched += mediaPaths(target)
            n++
        }
        scanMedia(touched)
        return n
    }

    suspend fun deleteForever(entries: List<TrashEntity>) {
        for (e in entries) {
            File(e.trashPath).deleteRecursively()
            dao.deletePathTree(e.trashPath)
            dao.deleteTrash(e.id)
        }
    }

    /** Usuwa z kosza elementy starsze niż 30 dni. */
    suspend fun purgeOldTrash() {
        val limit = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
        deleteForever(dao.allTrash().filter { it.deletedAt < limit })
    }

    suspend fun zip(sources: List<File>, destDir: File): File {
        val baseName = if (sources.size == 1) sources[0].nameWithoutExtension.ifEmpty { sources[0].name } else "Archiwum"
        val target = uniqueTarget(destDir, "$baseName.zip")
        ZipOutputStream(FileOutputStream(target).buffered()).use { zos ->
            for (src in sources) {
                val base = src.parentFile ?: continue
                src.walkTopDown().forEach { f ->
                    val rel = f.relativeTo(base).path
                    if (f.isDirectory) {
                        zos.putNextEntry(ZipEntry("$rel/")); zos.closeEntry()
                    } else {
                        zos.putNextEntry(ZipEntry(rel))
                        FileInputStream(f).use { it.copyTo(zos) }
                        zos.closeEntry()
                    }
                }
            }
        }
        scanMedia(listOf(target.path))
        return target
    }

    suspend fun unzip(zip: File, destParent: File): File {
        val dest = uniqueTarget(destParent, zip.nameWithoutExtension)
        dest.mkdirs()
        val destCanon = dest.canonicalPath + "/"
        ZipInputStream(FileInputStream(zip).buffered()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(dest, entry.name)
                // Ochrona przed "zip slip" (wpisy z ../ wychodzące poza folder)
                if (!out.canonicalPath.startsWith(destCanon)) continue
                if (entry.isDirectory) out.mkdirs() else {
                    out.parentFile?.mkdirs()
                    FileOutputStream(out).use { zis.copyTo(it) }
                }
            }
        }
        scanMedia(mediaPaths(dest))
        return dest
    }
}
