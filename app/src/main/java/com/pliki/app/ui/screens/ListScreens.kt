package com.pliki.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pliki.app.data.*
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.Routes
import com.pliki.app.ui.components.*
import com.pliki.app.ui.theme.PC
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
private fun TitleBar(title: String, subtitle: String?, nav: NavHostController, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz") }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = PC.Muted) }
        }
        actions()
    }
}

/** Wszystkie pliki danej kategorii (Zdjęcia, Wideo, Dokumenty...) z całej pamięci. */
@Composable
fun CategoryScreen(vm: MainViewModel, nav: NavHostController, catName: String) {
    val context = LocalContext.current
    val cat = runCatching { FileCategory.valueOf(catName) }.getOrDefault(FileCategory.OTHER)
    val idx by vm.indexState.collectAsState()
    val pathTags by vm.pathTags.collectAsState()
    var sortBy by rememberSaveable { mutableStateOf(SortBy.DATE) }
    var grid by rememberSaveable { mutableStateOf(cat == FileCategory.IMAGE || cat == FileCategory.VIDEO) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var sortMenu by remember { mutableStateOf(false) }

    val items by produceState(emptyList<FileUi>(), idx.files, sortBy) {
        value = withContext(Dispatchers.Default) {
            sortFiles(idx.files.filter { !it.isDir && it.category == cat && !it.path.contains("/$TRASH_DIR/") }.map { it.toUi() }, sortBy, sortBy != SortBy.NAME)
        }
    }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    val open = rememberOpener(vm) { nav.navigate(Routes.browse(it)) }

    Box(Modifier.fillMaxSize()) {
        Column {
            if (selected.isNotEmpty()) SelectionHeader(selected.size, { selected = emptySet() }, { selected = items.map { it.path }.toSet() })
            else TitleBar(cat.label, "${filesCount(items.size)} · ${formatSize(items.sumOf { it.size })}", nav) {
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.AutoMirrored.Outlined.Sort, "Sortuj") }
                    DropdownMenu(sortMenu, { sortMenu = false }, containerColor = PC.Surface2) {
                        SortBy.entries.filter { it != SortBy.TYPE }.forEach { s -> DropdownMenuItem(text = { Text(s.label) }, onClick = { sortBy = s; sortMenu = false }) }
                    }
                }
                IconButton(onClick = { grid = !grid }) { Icon(if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, "Zmień widok") }
            }
            FilesView(
                items, grid, selected, pathTags, open,
                onToggle = { f -> selected = if (f.path in selected) selected - f.path else selected + f.path },
                subtitle = { f -> Volumes.displayPath(context, File(f.path).parent ?: "") + " · " + formatSize(f.size) + " · " + formatDate(f.modified) },
                empty = { EmptyState(categoryIcon(cat), if (idx.ready) "Brak plików" else "Indeksowanie…", if (idx.ready) "Nie znaleziono plików tego typu." else "Trwa skanowanie pamięci.") },
            )
        }
        if (selected.isNotEmpty()) SelectionBar(vm, selected, { selected = emptySet() }, Modifier.align(Alignment.BottomCenter))
    }
}

/** Listy do sprzątania: duplikaty, duże pliki, stare pobrane, APK. */
@Composable
fun CleanupScreen(vm: MainViewModel, nav: NavHostController, kind: String) {
    val context = LocalContext.current
    val idx by vm.indexState.collectAsState()
    val pathTags by vm.pathTags.collectAsState()
    var selected by remember { mutableStateOf(setOf<String>()) }
    var progress by remember { mutableFloatStateOf(0f) }
    val title = when (kind) {
        "duplicates" -> "Duplikaty"
        "large" -> "Duże pliki"
        "old" -> "Stare pobrane"
        else -> "Pliki APK"
    }

    val groups by produceState<List<List<IndexedFile>>?>(null, idx.files, idx.ready) {
        if (!idx.ready) return@produceState
        value = withContext(Dispatchers.IO) {
            val files = idx.files.filter { !it.path.contains("/$TRASH_DIR/") && File(it.path).exists() }
            when (kind) {
                "duplicates" -> Cleanup.duplicates(files) { progress = it }
                "large" -> listOf(Cleanup.large(files))
                "old" -> listOf(Cleanup.oldDownloads(files))
                else -> listOf(Cleanup.apks(files))
            }
        }
    }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    val open = rememberOpener(vm) { nav.navigate(Routes.browse(it)) }
    val all = groups.orEmpty().flatten()
    val wasted = if (kind == "duplicates") groups.orEmpty().sumOf { it[0].size * (it.size - 1) } else all.sumOf { it.size }

    Box(Modifier.fillMaxSize()) {
        Column {
            if (selected.isNotEmpty()) SelectionHeader(selected.size, { selected = emptySet() }, { selected = all.map { it.path }.toSet() })
            else TitleBar(title, groups?.let { if (kind == "duplicates") "Do odzyskania: ${formatSize(wasted)}" else "${filesCount(all.size)} · ${formatSize(wasted)}" }, nav)

            val g = groups
            if (g == null) {
                Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (kind == "duplicates" && progress > 0f) LinearProgressIndicator(progress = { progress }, color = PC.Accent, trackColor = PC.Surface, modifier = Modifier.fillMaxWidth())
                    else CircularProgressIndicator(color = PC.Accent)
                    Text(if (kind == "duplicates") "Porównuję zawartość plików…" else "Analizuję…", color = PC.Muted)
                }
            } else if (all.isEmpty()) {
                EmptyState(Icons.Outlined.CleaningServices, "Czysto!", "Nie ma tu nic do posprzątania.")
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (kind == "duplicates") {
                        item {
                            Surface(
                                onClick = {
                                    // Zostaw najstarszy plik w każdej grupie (zwykle oryginał), zaznacz pozostałe.
                                    selected = g.flatMap { grp -> grp.sortedBy { it.modified }.drop(1) }.map { it.path }.toSet()
                                },
                                shape = RoundedCornerShape(18.dp), color = PC.Surface, modifier = Modifier.padding(vertical = 8.dp),
                            ) {
                                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Outlined.DoneAll, null, tint = PC.AccentText)
                                    Column(Modifier.weight(1f)) {
                                        Text("Zaznacz kopie automatycznie", style = MaterialTheme.typography.labelLarge)
                                        Text("Zostawia najstarszy plik z każdej grupy", style = MaterialTheme.typography.bodySmall, color = PC.Muted)
                                    }
                                }
                            }
                        }
                    }
                    g.forEachIndexed { gi, grp ->
                        if (kind == "duplicates") {
                            item(key = "h$gi") {
                                Text(
                                    "${grp.size} × ${formatSize(grp[0].size)}",
                                    style = MaterialTheme.typography.labelMedium, color = PC.Muted,
                                    modifier = Modifier.padding(start = 8.dp, top = 14.dp, bottom = 4.dp),
                                )
                            }
                        }
                        items(grp, key = { it.path }) { f ->
                            val ui = f.toUi()
                            FileRow(
                                ui, pathTags[f.path].orEmpty(), f.path in selected, selected.isNotEmpty(),
                                onClick = { if (selected.isNotEmpty()) selected = if (f.path in selected) selected - f.path else selected + f.path else open(ui) },
                                onLongClick = { selected = if (f.path in selected) selected - f.path else selected + f.path },
                                subtitle = Volumes.displayPath(context, File(f.path).parent ?: "") + " · " + formatSize(f.size) + " · " + formatDate(f.modified),
                            )
                        }
                    }
                }
            }
        }
        if (selected.isNotEmpty()) SelectionBar(vm, selected, { selected = emptySet() }, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
fun TrashScreen(vm: MainViewModel, nav: NavHostController) {
    val trash by vm.trash.collectAsState()
    var selected by remember { mutableStateOf(setOf<Long>()) }
    var confirmEmpty by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    val context = LocalContext.current

    Box(Modifier.fillMaxSize()) {
        Column {
            if (selected.isNotEmpty()) SelectionHeader(selected.size, { selected = emptySet() }, { selected = trash.map { it.id }.toSet() })
            else TitleBar("Kosz", "${filesCount(trash.size)} · ${formatSize(trash.sumOf { it.size })}", nav) {
                if (trash.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text("Opróżnij", color = PC.Danger) }
            }
            if (trash.isEmpty()) {
                EmptyState(Icons.Outlined.Delete, "Kosz jest pusty", "Usunięte pliki trafiają tu na 30 dni. Możesz je w tym czasie przywrócić.")
            } else {
                LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    item {
                        Text(
                            "Pliki w koszu są usuwane na zawsze po 30 dniach.",
                            style = MaterialTheme.typography.bodySmall, color = PC.Muted, modifier = Modifier.padding(8.dp),
                        )
                    }
                    items(trash, key = { it.id }) { e ->
                        val left = 30 - ((System.currentTimeMillis() - e.deletedAt) / (24L * 3600 * 1000)).toInt()
                        val ui = FileUi(e.trashPath, e.name, e.isDir, e.size, e.deletedAt, if (e.isDir) FileCategory.OTHER else FileCategory.of(e.name))
                        val toggle = { selected = if (e.id in selected) selected - e.id else selected + e.id }
                        FileRow(
                            ui, emptyList(), e.id in selected, selected.isNotEmpty(),
                            onClick = toggle, onLongClick = toggle,
                            subtitle = "z: ${Volumes.displayPath(context, File(e.originalPath).parent ?: "")} · ${formatSize(e.size)} · zostało ${plural(left.coerceAtLeast(0), "dzień", "dni", "dni")}",
                        )
                    }
                }
            }
        }
        if (selected.isNotEmpty()) {
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilledTonalButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PC.Surface2, contentColor = PC.Danger),
                ) { Text("Usuń na zawsze") }
                Button(
                    onClick = { vm.restore(trash.filter { it.id in selected }); selected = emptySet() },
                    modifier = Modifier.weight(1f).height(52.dp), shape = RoundedCornerShape(18.dp),
                ) { Text("Przywróć") }
            }
        }
    }
    if (confirmEmpty) {
        ConfirmDialog("Opróżnić kosz?", "${filesCount(trash.size)} zostanie usuniętych na zawsze. Tego nie da się cofnąć.", "Opróżnij",
            onConfirm = { vm.deleteForever(trash); confirmEmpty = false }, onDismiss = { confirmEmpty = false })
    }
    if (confirmDelete) {
        ConfirmDialog("Usunąć na zawsze?", "${filesCount(selected.size)} zostanie usuniętych bez możliwości przywrócenia.", "Usuń",
            onConfirm = { vm.deleteForever(trash.filter { it.id in selected }); selected = emptySet(); confirmDelete = false }, onDismiss = { confirmDelete = false })
    }
}
