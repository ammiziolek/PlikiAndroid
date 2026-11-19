package com.pliki.app.data

import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class FileCategory(val label: String) {
    IMAGE("Zdjęcia"),
    VIDEO("Wideo"),
    AUDIO("Audio"),
    DOCUMENT("Dokumenty"),
    APK("APK"),
    ARCHIVE("Archiwa"),
    OTHER("Inne");

    companion object {
        private val map: Map<String, FileCategory> = buildMap {
            listOf("jpg", "jpeg", "png", "gif", "webp", "heic", "heif", "bmp", "dng", "raw", "svg", "avif", "tif", "tiff")
                .forEach { put(it, IMAGE) }
            listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v", "ts", "wmv", "flv")
                .forEach { put(it, VIDEO) }
            listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr", "wma", "mid", "midi", "m4b")
                .forEach { put(it, AUDIO) }
            listOf(
                "pdf", "doc", "docx", "odt", "rtf", "txt", "md", "xls", "xlsx", "ods", "csv", "ppt", "pptx",
                "odp", "epub", "mobi", "json", "xml", "html", "htm", "pages", "numbers", "key", "log", "vcf", "ics"
            ).forEach { put(it, DOCUMENT) }
            listOf("apk", "apks", "xapk", "apkm").forEach { put(it, APK) }
            listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz").forEach { put(it, ARCHIVE) }
        }

        fun of(name: String): FileCategory = map[name.substringAfterLast('.', "").lowercase()] ?: OTHER
    }
}

fun File.category(): FileCategory = FileCategory.of(name)

private val PL = Locale.forLanguageTag("pl-PL")

fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var v = bytes / 1024.0
    var i = 0
    while (v >= 1024 && i < units.lastIndex) {
        v /= 1024; i++
    }
    val s = if (v >= 100 || i == 0) String.format(PL, "%.0f", v) else String.format(PL, "%.1f", v)
    return "$s ${units[i]}"
}

fun formatDate(ms: Long, now: Long = System.currentTimeMillis()): String {
    val diff = now - ms
    val min = 60_000L
    val hour = 60 * min
    if (diff in 0 until min) return "przed chwilą"
    if (diff in min until hour) return "${diff / min} min temu"
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    val n = Calendar.getInstance().apply { timeInMillis = now }
    val sameYear = c.get(Calendar.YEAR) == n.get(Calendar.YEAR)
    val dayDiff = n.get(Calendar.DAY_OF_YEAR) - c.get(Calendar.DAY_OF_YEAR)
    if (sameYear && dayDiff == 0) return "${diff / hour} godz. temu"
    if (sameYear && dayDiff == 1) return "wczoraj"
    return SimpleDateFormat(if (sameYear) "d MMM" else "d MMM yyyy", PL).format(ms)
}

fun formatFullDate(ms: Long): String = SimpleDateFormat("d MMMM yyyy, HH:mm", PL).format(ms)
fun formatShortDate(ms: Long): String = SimpleDateFormat("d MMM yyyy", PL).format(ms)

/** Polska odmiana: 1 plik, 2 pliki, 5 plików. */
fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val w = when {
        n == 1 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "$n $w"
}

fun filesCount(n: Int) = plural(n, "plik", "pliki", "plików")
fun itemsCount(n: Int) = plural(n, "element", "elementy", "elementów")
