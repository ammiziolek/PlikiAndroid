package com.pliki.app.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pliki.app.data.*
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.Routes
import com.pliki.app.ui.components.*
import com.pliki.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun StorageScreen(vm: MainViewModel, nav: NavHostController) {
    val context = LocalContext.current
    val idx by vm.indexState.collectAsState()
    val volumes = remember { Volumes.list(context) }
    var volIndex by remember { mutableIntStateOf(0) }
    val volume = volumes.getOrNull(volIndex)
    val stats by produceState<VolumeStats?>(null, volume, idx.ready) {
        value = volume?.let { withContext(Dispatchers.IO) { Volumes.stats(context, it) } }
    }
    data class Analysis(
        val cats: Map<FileCategory, Long>, val large: List<IndexedFile>, val old: List<IndexedFile>,
        val apks: List<IndexedFile>, val folders: List<Pair<String, Long>>,
    )
    val analysis by produceState<Analysis?>(null, idx.files, volume) {
        if (volume == null) return@produceState
        value = withContext(Dispatchers.Default) {
            val root = volume.root.path + "/"
            val files = idx.files.filter { !it.isDir && it.path.startsWith(root) }
            val folderSizes = HashMap<String, Long>()
            files.forEach { f ->
                val parts = f.path.removePrefix(root).split('/')
                if (parts.size >= 2) {
                    val key = parts.take(minOf(2, parts.size - 1)).joinToString("/")
                    folderSizes[key] = (folderSizes[key] ?: 0L) + f.size
                }
            }
            Analysis(
                cats = files.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.size } },
                large = Cleanup.large(files), old = if (volume.primary) Cleanup.oldDownloads(files) else emptyList(),
                apks = Cleanup.apks(files),
                folders = folderSizes.entries.sortedByDescending { it.value }.take(6).map { it.key to it.value },
            )
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item { Text("Miejsce", style = MaterialTheme.typography.headlineLarge) }
        if (volumes.size > 1) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    volumes.forEachIndexed { i, v -> FilterPill(v.label, i == volIndex, { volIndex = i }) }
                }
            }
        }
        item {
            val s = stats
            val a = analysis
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                val order = FileCategory.entries
                val total = (s?.total ?: 1L).toFloat()
                val known = order.filter { it != FileCategory.OTHER }.sumOf { a?.cats?.get(it) ?: 0L }
                val other = ((s?.used ?: 0L) - known).coerceAtLeast(0L)
                val segments = order.map { c -> catChartColor(c) to (if (c == FileCategory.OTHER) other else a?.cats?.get(c) ?: 0L) / total }
                val pct = if (s != null) (s.used * 100 / s.total.coerceAtLeast(1)).toInt() else 0
                Box(
                    Modifier.size(150.dp).semantics { contentDescription = "Zajęte $pct procent" },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = 19.dp.toPx()
                        val inset = stroke / 2
                        val arcSize = Size(size.width - stroke, size.height - stroke)
                        drawArc(PC.Border, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                        if (a != null) {
                            var start = -90f
                            segments.forEach { (color, frac) ->
                                val sweep = frac * 360f
                                if (sweep > 1.2f) drawArc(color, start, sweep - 1.2f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                                start += sweep
                            }
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("$pct%", style = MaterialTheme.typography.headlineMedium)
                        Text(s?.let { "${formatSize(it.used)} / ${formatSize(it.total)}" } ?: "", style = MaterialTheme.typography.labelSmall, color = PC.Muted)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    order.forEach { c ->
                        val v = if (c == FileCategory.OTHER) other else a?.cats?.get(c) ?: 0L
                        if (v > 0 || c == FileCategory.OTHER) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.size(10.dp).clip(CircleShape).background(catChartColor(c)))
                                Text(c.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(formatSize(v), style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
                            }
                        }
                    }
                }
            }
        }
        item {
            val a = analysis
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Do posprzątania", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CleanCard("Duplikaty", "Znajdź identyczne pliki", Icons.Outlined.ContentCopy, catColors(FileCategory.IMAGE), Modifier.weight(1f)) {
                        nav.navigate(Routes.cleanup("duplicates"))
                    }
                    CleanCard("Duże pliki", a?.let { "powyżej 100 MB · ${formatSize(it.large.sumOf { f -> f.size })}" } ?: "…", Icons.Outlined.BarChart, catColors(FileCategory.VIDEO), Modifier.weight(1f)) {
                        nav.navigate(Routes.cleanup("large"))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CleanCard("Stare pobrane", a?.let { "starsze niż 90 dni · ${formatSize(it.old.sumOf { f -> f.size })}" } ?: "…", Icons.Outlined.Download, catColors(FileCategory.DOCUMENT), Modifier.weight(1f)) {
                        nav.navigate(Routes.cleanup("old"))
                    }
                    CleanCard("Pliki APK", a?.let { "${filesCount(it.apks.size)} · ${formatSize(it.apks.sumOf { f -> f.size })}" } ?: "…", Icons.Outlined.Android, catColors(FileCategory.AUDIO), Modifier.weight(1f)) {
                        nav.navigate(Routes.cleanup("apk"))
                    }
                }
            }
        }
        val folders = analysis?.folders.orEmpty()
        if (folders.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Największe foldery", style = MaterialTheme.typography.titleMedium)
                    val max = folders.first().second.toFloat().coerceAtLeast(1f)
                    folders.forEach { (name, sz) ->
                        Surface(onClick = { nav.navigate(Routes.browse(volume!!.root.path + "/" + name)) }, color = androidx.compose.ui.graphics.Color.Transparent) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row {
                                    Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text(formatSize(sz), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                }
                                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(PC.Surface2)) {
                                    Box(Modifier.fillMaxWidth(sz / max).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(PC.Accent))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (!idx.ready) {
            item { Text("Indeksowanie plików… ${idx.scanned}", style = MaterialTheme.typography.bodySmall, color = PC.Muted) }
        }
    }
}

@Composable
private fun CleanCard(title: String, sub: String, icon: ImageVector, colors: CatColors, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = PC.Surface, modifier = modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, colors.bg, colors.fg, size = 40.dp, iconSize = 20.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.labelLarge)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = PC.Muted, minLines = 2)
            }
        }
    }
}
