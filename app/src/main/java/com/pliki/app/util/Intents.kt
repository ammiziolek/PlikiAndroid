package com.pliki.app.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import java.io.File

fun mimeOf(file: File): String {
    val ext = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: when (ext) {
        "apk" -> "application/vnd.android.package-archive"
        else -> "*/*"
    }
}

fun uriFor(context: Context, file: File): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.files", file)

fun openFile(context: Context, file: File) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, file), mimeOf(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Brak aplikacji, która otworzy ten plik", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Nie można otworzyć: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openWith(context: Context, file: File) {
    try {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(context, file), mimeOf(file))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(Intent.createChooser(intent, "Otwórz za pomocą").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Nie można otworzyć", Toast.LENGTH_SHORT).show()
    }
}

fun shareFiles(context: Context, files: List<File>) {
    val list = files.filter { it.isFile }
    if (list.isEmpty()) {
        Toast.makeText(context, "Foldery można udostępnić po spakowaniu do ZIP", Toast.LENGTH_SHORT).show()
        return
    }
    val uris = ArrayList(list.map { uriFor(context, it) })
    val mime = list.map(::mimeOf).distinct().singleOrNull() ?: "*/*"
    val intent = if (uris.size == 1) {
        Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    } else {
        Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    }
    intent.type = mime
    intent.clipData = ClipData.newRawUri("", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(intent, "Udostępnij").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Sprawdzenie dostępu do wszystkich plików (w testach można je podmienić). */
object StorageAccess {
    @Volatile
    var override: (() -> Boolean)? = null

    fun granted(): Boolean = override?.invoke() ?: android.os.Environment.isExternalStorageManager()
}
