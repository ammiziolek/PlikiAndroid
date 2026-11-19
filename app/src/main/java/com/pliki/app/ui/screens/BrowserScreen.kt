package com.pliki.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SdStorage
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pliki.app.data.*
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.Routes
import com.pliki.app.ui.components.*
import com.pliki.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class SortBy(val label: String) { NAME("Nazwa"), DATE("Data"), SIZE("Rozmiar"), TYPE("Typ") }

fun sortFiles(list: List<FileUi>, by: SortBy, desc: Boolean): List<FileUi> {
    val cmp: Comparator<FileUi> = when (by) {
        SortBy.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortBy.DATE -> compareBy { it.modified }
        SortBy.SIZE -> compareBy { it.size }
        SortBy.TYPE -> compareBy<FileUi> { it.category.ordinal }.thenBy { it.name.substringAfterLast('.', "").lowercase() }
    }
    val c = if (desc) cmp.reversed() else cmp
    return list.sortedWith(compareByDescending<FileUi> { it.isDir }.then(c))
}

private const val VOLUMES = ""

@Composable
fun BrowserScreen(vm: MainViewModel, nav: NavHostController, initialPath: String?) {
    val context = LocalContext.current
    val volumes = remember { Volumes.list(context) }
    val primaryRoot = remember { Volumes.primaryRoot().path }
    var current by rememberSaveable { mutableStateOf(initialPath ?: primaryRoot) }
    var sortBy by rememberSaveable { mutableStateOf(SortBy.DATE) }
    var desc by rememberSaveable { mutableStateOf(true) }
    var showHidden by rememberSaveable { mutableStateOf(false) }
    var grid by rememberSaveable { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    var newFolder by remember { mutableStateOf(false) }
    val revision by vm.revision.collectAsState()
    val pathTags by vm.pathTags.collectAsState()
    val clip by vm.clip.collectAsState()

    val volume = volumes.firstOrNull { current == it.root.path || current.startsWith(it.root.path + "/") }
    val atVolumeRoot = volume != null && current == volume.root.path

    val items by produceState<List<FileUi>?>(null, current, sortBy, desc, showHidden, revision) {
        value = if (current == VOLUMES) emptyList() else withContext(Dispatchers.IO) {
            val list = File(current).listFiles()?.filter { showHidden || !it.name.startsWith(".") }?.map { it.toUi() } ?: emptyList()
            sortFiles(list, sortBy, desc)
        }
    }

    fun goUp() {
        selected = emptySet()
        current = when {
            current == VOLUMES -> current
            atVolumeRoot -> if (volumes.size > 1) VOLUMES else current
            else -> File(current).parent ?: current
        }
    }

    val canGoUp = current != VOLUMES && (!atVolumeRoot || volumes.size > 1)
    BackHandler(selected.isNotEmpty() || canGoUp) {
        if (selected.isNotEmpty()) selected = emptySet() else goUp()
    }

    val open = rememberOpener(vm) { selected = emptySet(); current = it }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selected.isNotEmpty()) {
                SelectionHeader(selected.size, { selected = emptySet() }, { selected = items.orEmpty().map { it.path }.toSet() })
            } else {
                Column(Modifier.fillMaxWidth().background(PC.Bar).statusBarsPadding().padding(start = 8.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canGoUp) IconButton(onClick = { goUp() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "W górę") }
                        else Spacer(Modifier.width(12.dp))
                        Text(
                            when {
                                current == VOLUMES -> "Pamięć"
                                atVolumeRoot -> volume!!.label
                                else -> File(current).name
                            },
                            style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { nav.navigate(Routes.search()) }) { Icon(Icons.Outlined.Search, "Szukaj") }
                        if (current != VOLUMES) {
                            IconButton(onClick = { newFolder = true }) { Icon(Icons.Outlined.CreateNewFolder, "Nowy folder") }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "Więcej") }
                                DropdownMenu(menu, { menu = false }, containerColor = PC.Surface2) {
                                    DropdownMenuItem(
                                        text = { Text(if (showHidden) "Ukryj ukryte pliki" else "Pokaż ukryte pliki") },
                                        leadingIcon = { Icon(if (showHidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, null) },
                                        onClick = { showHidden = !showHidden; menu = false },
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Zaznacz wszystko") },
                                        leadingIcon = { Icon(Icons.Outlined.SelectAll, null) },
                                        onClick = { selected = items.orEmpty().map { it.path }.toSet(); menu = false },
                                    )
                                }
                            }
                        }
                    }
                    if (current != VOLUMES && volume != null) {
                        Breadcrumbs(volume, current, volumes.size > 1, onVolumes = { current = VOLUMES }) { selected = emptySet(); current = it }
                    }
                }
            }

            if (current == VOLUMES) {
                VolumesList(context, volumes) { current = it.root.path }
            } else {
                val list = items
                if (list != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val dirs = list.count { it.isDir }
                        Text(
                            "${plural(dirs, "folder", "foldery", "folderów")} · ${filesCount(list.size - dirs)}",
                            style = MaterialTheme.typography.bodySmall, color = PC.Muted, modifier = Modifier.weight(1f),
                        )
                        Box {
                            SmallPill(sortBy.label + if (desc) " ↓" else " ↑", Icons.AutoMirrored.Outlined.Sort) { sortMenu = true }
                            DropdownMenu(sortMenu, { sortMenu = false }, containerColor = PC.Surface2) {
                                SortBy.entries.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(s.label, fontWeight = if (s == sortBy) FontWeight.SemiBold else FontWeight.Normal) },
                                        onClick = {
                                            if (s == sortBy) desc = !desc else { sortBy = s; desc = s != SortBy.NAME }
                                            sortMenu = false
                                        },
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        IconButton(
                            onClick = { grid = !grid },
                            modifier = Modifier.size(36.dp),
                            colors = IconButtonDefaults.iconButtonColors(containerColor = PC.Surface),
                        ) { Icon(if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, if (grid) "Widok listy" else "Widok siatki", modifier = Modifier.size(18.dp)) }
                    }
                    FilesView(
                        items = list, grid = grid, selected = selected, pathTags = pathTags,
                        onOpen = open,
                        onToggle = { f -> selected = if (f.path in selected) selected - f.path else selected + f.path },
                        empty = { EmptyState(Icons.Outlined.FolderOpen, "Pusty folder", "Nie ma tu jeszcze żadnych plików.") },
                    )
                } else {
                    Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = PC.Accent) }
                }
            }
        }

        if (selected.isNotEmpty()) {
            SelectionBar(vm, selected, onDone = { selected = emptySet() }, modifier = Modifier.align(Alignment.BottomCenter), zipDir = current)
        } else if (clip != null && current != VOLUMES) {
            PasteBar(clip!!.paths.size, clip!!.cut, onPaste = { vm.paste(current) }, onCancel = { vm.clearClip() }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (newFolder) {
        NameDialog(
            title = "Nowy folder", initial = "", confirm = "Utwórz",
            onConfirm = { vm.mkdir(current, it); newFolder = false },
            onDismiss = { newFolder = false },
        )
    }
}

@Composable
private fun SmallPill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(10.dp), color = PC.Surface, contentColor = Color0) {
        Row(Modifier.height(36.dp).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

private val Color0 = androidx.compose.ui.graphics.Color(0xFFE6E7E9)

@Composable
private fun Breadcrumbs(volume: Volume, current: String, multiVolume: Boolean, onVolumes: () -> Unit, onPick: (String) -> Unit) {
    val rel = current.removePrefix(volume.root.path).trim('/')
    val segments = if (rel.isEmpty()) emptyList() else rel.split('/')
    val scroll = rememberScrollState()
    LaunchedEffect(current) { scroll.animateScrollTo(scroll.maxValue) }
    Row(
        Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp).horizontalScroll(scroll),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Crumb(if (volume.primary) "Pamięć" else volume.label, segments.isEmpty()) { if (segments.isEmpty() && multiVolume) onVolumes() else onPick(volume.root.path) }
        segments.forEachIndexed { i, s ->
            Icon(Icons.Rounded.ChevronRight, null, tint = PC.Muted, modifier = Modifier.size(16.dp))
            val path = volume.root.path + "/" + segments.take(i + 1).joinToString("/")
            Crumb(s, i == segments.lastIndex) { onPick(path) }
        }
    }
}

@Composable
private fun Crumb(text: String, last: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(8.dp), color = if (last) PC.Surface2 else androidx.compose.ui.graphics.Color.Transparent, contentColor = if (last) PC.Text else PC.Muted) {
        Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (last) FontWeight.SemiBold else FontWeight.Normal), modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun VolumesList(context: android.content.Context, volumes: List<Volume>, onPick: (Volume) -> Unit) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        volumes.forEach { v ->
            val stats = remember(v) { runCatching { Volumes.stats(context, v) }.getOrNull() }
            Surface(onClick = { onPick(v) }, shape = RoundedCornerShape(20.dp), color = PC.Surface) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    IconTile(if (v.primary) Icons.Rounded.Smartphone else Icons.Rounded.SdStorage, PC.AccentSoft, PC.AccentText)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(v.label, style = MaterialTheme.typography.bodyLarge)
                        if (stats != null) {
                            LinearProgressIndicator(
                                progress = { stats.used.toFloat() / stats.total.coerceAtLeast(1) },
                                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                                color = PC.Accent, trackColor = PC.Surface3, drawStopIndicator = {},
                            )
                            Text("${formatSize(stats.free)} wolne z ${formatSize(stats.total)}", style = MaterialTheme.typography.bodySmall, color = PC.Muted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PasteBar(count: Int, cut: Boolean, onPaste: () -> Unit, onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(12.dp).shadow(16.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(PC.Surface2).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            (if (cut) "Do przeniesienia: " else "W schowku: ") + filesCount(count),
            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        TextButton(onClick = onCancel) { Text("Anuluj", color = PC.Text) }
        Button(onClick = onPaste, shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Outlined.ContentPaste, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (cut) "Przenieś tutaj" else "Wklej tutaj")
        }
    }
}
