package com.pliki.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Sell
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pliki.app.data.*
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.Routes
import com.pliki.app.ui.TagInfo
import com.pliki.app.ui.components.*
import com.pliki.app.ui.goTab
import com.pliki.app.ui.theme.*
import com.pliki.app.util.openFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun HomeScreen(vm: MainViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val idx by vm.indexState.collectAsState()
    val tagInfos by vm.tagInfos.collectAsState()
    val pathTags by vm.pathTags.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var newTag by remember { mutableStateOf(false) }

    val primary = remember { Volumes.list(context).firstOrNull { it.primary } }
    val stats by produceState<VolumeStats?>(null, idx.ready) {
        value = primary?.let { withContext(Dispatchers.IO) { Volumes.stats(context, it) } }
    }
    val catSizes by produceState(emptyMap<FileCategory, Long>(), idx.files) {
        value = withContext(Dispatchers.Default) {
            val root = Volumes.primaryRoot().path + "/"
            idx.files.filter { !it.isDir && it.path.startsWith(root) }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.size } }
        }
    }
    val recent by produceState(emptyList<IndexedFile>(), idx.files) {
        value = withContext(Dispatchers.Default) {
            idx.files.asSequence().filter { !it.isDir && !it.path.contains("/$TRASH_DIR/") }
                .sortedByDescending { it.modified }.take(12).toList()
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Pliki", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                CircleIconButton(Icons.Outlined.Search, "Szukaj", { nav.navigate(Routes.search()) })
                Spacer(Modifier.width(8.dp))
                Box {
                    CircleIconButton(Icons.Outlined.MoreVert, "Więcej", { menu = true })
                    DropdownMenu(menu, { menu = false }, containerColor = PC.Surface2) {
                        DropdownMenuItem(text = { Text("Kosz") }, leadingIcon = { Icon(Icons.Outlined.Delete, null) }, onClick = { menu = false; nav.navigate(Routes.TRASH) })
                        DropdownMenuItem(text = { Text("Odśwież indeks plików") }, leadingIcon = { Icon(Icons.Outlined.Refresh, null) }, onClick = { menu = false; vm.refreshIndex() })
                    }
                }
            }
        }

        item { StorageCard(stats, catSizes, idx) { nav.goTab(Routes.STORAGE) } }

        item {
            val cats = listOf(
                QuickCat("Zdjęcia", Icons.Outlined.Image, catColors(FileCategory.IMAGE)) { nav.navigate(Routes.category(FileCategory.IMAGE.name)) },
                QuickCat("Wideo", Icons.Outlined.Movie, catColors(FileCategory.VIDEO)) { nav.navigate(Routes.category(FileCategory.VIDEO.name)) },
                QuickCat("Audio", Icons.Outlined.MusicNote, catColors(FileCategory.AUDIO)) { nav.navigate(Routes.category(FileCategory.AUDIO.name)) },
                QuickCat("Dokumenty", Icons.Outlined.Description, catColors(FileCategory.DOCUMENT)) { nav.navigate(Routes.category(FileCategory.DOCUMENT.name)) },
                QuickCat("Pobrane", Icons.Outlined.Download, catColors(FileCategory.OTHER)) {
                    nav.navigate(Routes.browse(File(Volumes.primaryRoot(), "Download").path))
                },
                QuickCat("APK", Icons.Outlined.Android, catColors(FileCategory.OTHER)) { nav.navigate(Routes.category(FileCategory.APK.name)) },
                QuickCat("Archiwa", Icons.Outlined.Inventory2, catColors(FileCategory.OTHER)) { nav.navigate(Routes.category(FileCategory.ARCHIVE.name)) },
                QuickCat("Kosz", Icons.Outlined.Delete, catColors(FileCategory.OTHER)) { nav.navigate(Routes.TRASH) },
            )
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                cats.chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { c ->
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).clickable(onClick = c.onClick).padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                IconTile(c.icon, c.colors.bg, c.colors.fg, size = 56.dp, corner = 18.dp, iconSize = 24.dp)
                                Text(c.label, style = MaterialTheme.typography.labelMedium, color = PC.Muted2)
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionTitle("Twoje zbiory", action = "Wszystkie", onAction = { nav.goTab(Routes.TAGS) })
                LazyRow(
                    Modifier.fillMaxWidth().padding(end = 0.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tagInfos, key = { it.tag.id }) { info -> TagCard(info, Modifier.width(140.dp)) { nav.navigate(Routes.tag(info.tag.id)) } }
                    item { NewTagCard(Modifier.width(140.dp)) { newTag = true } }
                }
            }
        }

        item {
            Text("Ostatnie", style = MaterialTheme.typography.titleMedium)
        }
        if (!idx.ready) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PC.Accent)
                    Text("Indeksowanie plików… ${idx.scanned}", style = MaterialTheme.typography.bodySmall, color = PC.Muted)
                }
            }
        }
        items(recent, key = { it.path }) { f ->
            val ui = f.toUi()
            FileRow(
                ui, pathTags[f.path].orEmpty(), selected = false, selectionMode = false,
                onClick = { openFile(context, File(f.path)) },
                onLongClick = { nav.navigate(Routes.browse(File(f.path).parent)) },
                subtitle = "${Volumes.displayPath(context, File(f.path).parent ?: "")} · ${formatSize(f.size)} · ${formatDate(f.modified)}",
                trailing = pathTags[f.path]?.firstOrNull()?.let { t -> { TagPill(t) } },
            )
        }
    }

    if (newTag) {
        TagEditDialog(
            initial = null,
            defaultColor = TagPalette[tagInfos.size % TagPalette.size],
            onDismiss = { newTag = false },
            onSave = { vm.createTag(it); newTag = false },
        )
    }
}

private data class QuickCat(val label: String, val icon: ImageVector, val colors: CatColors, val onClick: () -> Unit)

@Composable
private fun StorageCard(stats: VolumeStats?, catSizes: Map<FileCategory, Long>, idx: IndexState, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(22.dp), color = PC.Surface) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row {
                Text("Pamięć wewnętrzna", style = MaterialTheme.typography.bodyMedium, color = PC.Muted, modifier = Modifier.weight(1f))
                stats?.let { Text("${formatSize(it.free)} wolne", style = MaterialTheme.typography.bodySmall, color = PC.Muted) }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(stats?.let { formatSize(it.used) } ?: "…", style = MaterialTheme.typography.headlineMedium)
                Text(stats?.let { " / ${formatSize(it.total)}" } ?: "", style = MaterialTheme.typography.bodyLarge, color = PC.Muted, modifier = Modifier.padding(bottom = 4.dp))
            }
            val total = stats?.total?.toFloat() ?: 1f
            val order = listOf(FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO, FileCategory.DOCUMENT, FileCategory.APK, FileCategory.ARCHIVE)
            val known = order.sumOf { catSizes[it] ?: 0L }
            val other = ((stats?.used ?: 0L) - known).coerceAtLeast(0L)
            Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(PC.Border), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (idx.ready && stats != null) {
                    order.forEach { c ->
                        val w = (catSizes[c] ?: 0L) / total
                        if (w > 0.003f) Box(Modifier.weight(w).fillMaxHeight().background(catChartColor(c)))
                    }
                    if (other / total > 0.003f) Box(Modifier.weight(other / total).fillMaxHeight().background(catChartColor(FileCategory.OTHER)))
                    val free = stats.free / total
                    if (free > 0.003f) Spacer(Modifier.weight(free))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(FileCategory.IMAGE, FileCategory.VIDEO, FileCategory.AUDIO, FileCategory.DOCUMENT, FileCategory.OTHER).forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(catChartColor(c)))
                        Text(c.label, style = MaterialTheme.typography.labelSmall, color = PC.Muted2)
                    }
                }
            }
        }
    }
}

@Composable
fun TagCard(info: TagInfo, modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 112.dp, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = tagColor(info.tag.color), contentColor = OnTag, modifier = modifier.height(height)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Sell, null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.weight(1f))
                if (info.tag.hasRule()) Icon(Icons.Outlined.Bolt, "reguła automatyczna", modifier = Modifier.size(18.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info.tag.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    filesCount(info.count) + if (info.size > 0) " · ${formatSize(info.size)}" else "",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
fun NewTagCard(modifier: Modifier = Modifier, height: androidx.compose.ui.unit.Dp = 112.dp, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = PC.Surface, contentColor = PC.Muted2, modifier = modifier.height(height)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(PC.Surface2), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp))
            }
            Text("Nowy zbiór", style = MaterialTheme.typography.titleSmall)
        }
    }
}
