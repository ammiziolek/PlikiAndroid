package com.pliki.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.pliki.app.data.*
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.theme.PC
import com.pliki.app.util.openWith
import com.pliki.app.util.shareFiles
import java.io.File

data class ExtraAction(val label: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun SelectionHeader(count: Int, onClear: () -> Unit, onSelectAll: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().background(PC.Bar).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Anuluj zaznaczenie") }
        Text("Zaznaczono $count", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (onSelectAll != null) {
            FilledTonalButton(
                onClick = onSelectAll,
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = PC.Surface2, contentColor = PC.Text),
            ) { Text("Zaznacz wszystko", style = MaterialTheme.typography.labelMedium) }
        }
    }
}

@Composable
private fun BarButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier, accent: Boolean = false, danger: Boolean = false) {
    val fg = when {
        accent -> PC.OnAccent
        danger -> PC.Danger
        else -> Color(0xFFE6E7E9)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (accent) PC.Accent else Color.Transparent,
        contentColor = fg,
        modifier = modifier,
    ) {
        Column(Modifier.padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, modifier = Modifier.size(22.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/**
 * Dolny pasek akcji dla zaznaczonych plików wraz ze wszystkimi okienkami
 * (tagowanie, zmiana nazwy, usuwanie, szczegóły).
 */
@Composable
fun SelectionBar(
    vm: MainViewModel,
    selected: Set<String>,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    zipDir: String? = null,
    extra: List<ExtraAction> = emptyList(),
) {
    val context = LocalContext.current
    var showTags by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<String?>(null) }
    var details by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val files = selected.map(::File)

    Row(
        modifier
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .shadow(16.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(PC.Surface2)
            .padding(6.dp),
    ) {
        val m = Modifier.weight(1f)
        BarButton("Kopiuj", Icons.Outlined.ContentCopy, { vm.copyToClip(selected); onDone() }, m)
        BarButton("Przenieś", Icons.AutoMirrored.Outlined.DriveFileMove, { vm.cutToClip(selected); onDone() }, m)
        BarButton("Taguj", Icons.Outlined.Sell, { showTags = true }, m, accent = true)
        BarButton("Udostępnij", Icons.Outlined.Share, { shareFiles(context, files) }, m)
        BarButton("Do kosza", Icons.Outlined.Delete, { confirmDelete = true }, m, danger = true)
        Box(m) {
            BarButton("Więcej", Icons.Outlined.MoreHoriz, { menu = true }, Modifier.fillMaxWidth())
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = PC.Surface2) {
                extra.forEach { a ->
                    DropdownMenuItem(text = { Text(a.label) }, leadingIcon = { Icon(a.icon, null) }, onClick = { menu = false; a.onClick() })
                }
                if (selected.size == 1) {
                    DropdownMenuItem(text = { Text("Zmień nazwę") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { menu = false; renaming = selected.first() })
                    if (files[0].isFile) {
                        DropdownMenuItem(text = { Text("Otwórz za pomocą…") }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.OpenInNew, null) }, onClick = { menu = false; openWith(context, files[0]) })
                    }
                }
                DropdownMenuItem(text = { Text("Spakuj do ZIP") }, leadingIcon = { Icon(Icons.Outlined.FolderZip, null) }, onClick = {
                    menu = false
                    vm.zip(selected, zipDir ?: files[0].parent ?: Volumes.primaryRoot().path)
                    onDone()
                })
                DropdownMenuItem(text = { Text("Szczegóły") }, leadingIcon = { Icon(Icons.Outlined.Info, null) }, onClick = { menu = false; details = selected.first() })
            }
        }
    }

    if (showTags) {
        TagSheet(vm, selected, onDismiss = { showTags = false; onDone() })
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "Przenieść do kosza?",
            text = "${filesCount(selected.size)} trafi do kosza. Możesz je przywrócić przez 30 dni.",
            confirm = "Do kosza",
            onConfirm = { confirmDelete = false; vm.moveToTrash(selected); onDone() },
            onDismiss = { confirmDelete = false },
        )
    }
    renaming?.let { path ->
        NameDialog(
            title = "Zmień nazwę",
            initial = File(path).name,
            selectBaseName = File(path).isFile,
            confirm = "Zmień",
            onConfirm = { vm.rename(path, it); renaming = null; onDone() },
            onDismiss = { renaming = null },
        )
    }
    details?.let { path ->
        DetailsDialog(vm, if (selected.size == 1) listOf(path) else selected.toList(), onDismiss = { details = null })
    }
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PC.Surface,
        title = { Text(title) },
        text = { Text(text, color = PC.Muted2) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = if (danger) PC.Danger else PC.Accent, contentColor = PC.OnAccent),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = PC.Text) } },
    )
}

@Composable
fun NameDialog(
    title: String,
    initial: String,
    confirm: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
    selectBaseName: Boolean = false,
    label: String = "Nazwa",
) {
    val end = if (selectBaseName && initial.lastIndexOf('.') > 0) initial.lastIndexOf('.') else initial.length
    var value by remember { mutableStateOf(TextFieldValue(initial, TextRange(0, end))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PC.Surface,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(value.text) }, enabled = value.text.isNotBlank()) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = PC.Text) } },
    )
}

@Composable
fun DetailsDialog(vm: MainViewModel, paths: List<String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val pathTags by vm.pathTags.collectAsState()
    var info by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    LaunchedEffect(paths) {
        info = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            if (paths.size == 1) {
                val f = File(paths[0])
                val size = if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length()
                buildList {
                    add("Nazwa" to f.name)
                    add("Lokalizacja" to Volumes.displayPath(context, f.parent ?: ""))
                    add("Rozmiar" to formatSize(size))
                    if (f.isDirectory) add("Zawartość" to itemsCount(f.walkTopDown().count() - 1))
                    else add("Typ" to FileCategory.of(f.name).label + " (." + f.extension.lowercase() + ")")
                    add("Zmodyfikowano" to formatFullDate(f.lastModified()))
                }
            } else {
                val size = paths.map(::File).sumOf { f -> if (f.isDirectory) f.walkTopDown().filter { it.isFile }.sumOf { it.length() } else f.length() }
                listOf("Zaznaczono" to itemsCount(paths.size), "Łączny rozmiar" to formatSize(size))
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PC.Surface,
        title = { Text("Szczegóły") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                info.forEach { (k, v) ->
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = PC.Muted)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                val tags = if (paths.size == 1) pathTags[paths[0]].orEmpty() else emptyList()
                if (tags.isNotEmpty()) {
                    Text("Tagi", style = MaterialTheme.typography.labelMedium, color = PC.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { TagPill(it) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Zamknij", color = PC.AccentText) } },
    )
}
