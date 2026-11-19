package com.pliki.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pliki.app.data.*
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.Routes
import com.pliki.app.ui.components.*
import com.pliki.app.ui.goTab
import com.pliki.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun TagsScreen(vm: MainViewModel, nav: NavHostController) {
    val infos by vm.tagInfos.collectAsState()
    var newTag by remember { mutableStateOf(false) }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Zbiory", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                    CircleIconButton(Icons.Outlined.Search, "Szukaj", { nav.navigate(Routes.search()) })
                }
                Text(
                    "Wirtualne foldery: jeden plik może być w wielu zbiorach, a pliki zostają tam, gdzie są.",
                    style = MaterialTheme.typography.bodyMedium, color = PC.Muted,
                )
            }
        }
        items(infos, key = { it.tag.id }) { info ->
            TagCard(info, height = 130.dp) { nav.navigate(Routes.tag(info.tag.id)) }
        }
        item { NewTagCard(height = 130.dp) { newTag = true } }
        if (infos.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(shape = RoundedCornerShape(20.dp), color = PC.Surface) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Jak to działa?", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "1. Utwórz zbiór, np. „Faktury” albo „Wakacje 2026”.\n" +
                                "2. W dowolnym folderze przytrzymaj plik, zaznacz kilka i wybierz „Taguj”.\n" +
                                "3. Możesz też dodać regułę automatyczną — wtedy pasujące pliki trafią do zbioru same.",
                            style = MaterialTheme.typography.bodyMedium, color = PC.Muted2,
                        )
                    }
                }
            }
        }
    }
    if (newTag) {
        TagEditDialog(
            initial = null,
            defaultColor = TagPalette[infos.size % TagPalette.size],
            onDismiss = { newTag = false },
            onSave = { vm.createTag(it); newTag = false },
        )
    }
}

@Composable
fun TagDetailScreen(vm: MainViewModel, nav: NavHostController, id: Long) {
    val context = LocalContext.current
    val infos by vm.tagInfos.collectAsState()
    val idx by vm.indexState.collectAsState()
    val pathTags by vm.pathTags.collectAsState()
    val revision by vm.revision.collectAsState()
    val info = infos.firstOrNull { it.tag.id == id }
    var filter by remember { mutableStateOf<FileCategory?>(null) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var editing by remember { mutableStateOf(false) }

    if (info == null) {
        LaunchedEffect(infos) { if (infos.isNotEmpty() || idx.ready) nav.popBackStack() }
        return
    }
    val tag = info.tag

    val all by produceState(emptyList<FileUi>(), info.paths, idx.files, revision) {
        value = withContext(Dispatchers.IO) {
            val byPath = idx.files.associateBy { it.path }
            info.paths.mapNotNull { p -> byPath[p]?.toUi() ?: File(p).takeIf { it.exists() }?.toUi() }
                .sortedByDescending { it.modified }
        }
    }
    val counts = all.groupingBy { if (it.isDir) FileCategory.OTHER else it.category }.eachCount()
    val shown = if (filter == null) all else all.filter { !it.isDir && it.category == filter }
    val media = shown.filter { !it.isDir && (it.category == FileCategory.IMAGE || it.category == FileCategory.VIDEO) }
    val others = shown - media.toSet()
    val sources = remember(all) { all.mapNotNull { File(it.path).parent }.distinct().size }

    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    val open = rememberOpener(vm) { nav.navigate(Routes.browse(it)) }
    val toggle: (FileUi) -> Unit = { f -> selected = if (f.path in selected) selected - f.path else selected + f.path }
    val click: (FileUi) -> Unit = { if (selected.isNotEmpty()) toggle(it) else open(it) }
    val fg = OnTag

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(110.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)).background(tagColor(tag.color))
                        .statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircleIconButton(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz", { nav.popBackStack() }, bg = Color(0x1F08142A), tint = fg)
                        Spacer(Modifier.weight(1f))
                        CircleIconButton(Icons.Outlined.Search, "Szukaj w zbiorze", { nav.navigate(Routes.search(tag.id)) }, bg = Color(0x1F08142A), tint = fg)
                        Spacer(Modifier.width(8.dp))
                        CircleIconButton(Icons.Outlined.Edit, "Edytuj zbiór", { editing = true }, bg = Color(0x1F08142A), tint = fg)
                    }
                    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("ZBIÓR", style = MaterialTheme.typography.labelMedium, color = fg)
                        Text(tag.name, style = MaterialTheme.typography.headlineLarge, color = fg)
                        Text(
                            filesCount(info.count) + " · " + formatSize(info.size) + if (sources > 1) " · z ${plural(sources, "folderu", "folderów", "folderów")}" else "",
                            style = MaterialTheme.typography.bodyMedium, color = fg,
                        )
                    }
                    if (tag.hasRule()) {
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color(0x1F08142A)).padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Outlined.Bolt, null, tint = fg, modifier = Modifier.size(18.dp))
                            Text("Auto: " + ruleDescription(tag), style = MaterialTheme.typography.bodySmall, color = fg)
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("Wszystko", filter == null, { filter = null })
                    FileCategory.entries.forEach { c ->
                        val n = counts[c] ?: 0
                        if (n > 0) FilterPill("${c.label} $n", filter == c, { filter = c })
                    }
                }
            }
            if (all.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(Icons.Outlined.Sell, "Zbiór jest pusty", "W dowolnym folderze przytrzymaj plik i wybierz „Taguj”, żeby go tu dodać.")
                }
            }
            items(media, key = { "m" + it.path }) { f ->
                Box(Modifier.padding(horizontal = 2.dp)) {
                    FileGridCell(f, emptyList(), f.path in selected, { click(f) }, { toggle(f) }, showName = false)
                }
            }
            if (others.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(if (media.isEmpty()) "Pliki" else "Dokumenty i inne", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp))
                }
            }
            items(others, key = { "o" + it.path }, span = { GridItemSpan(maxLineSpan) }) { f ->
                Box(Modifier.padding(horizontal = 12.dp)) {
                    FileRow(
                        f, pathTags[f.path].orEmpty().filter { it.id != tag.id }, f.path in selected, selected.isNotEmpty(),
                        onClick = { click(f) }, onLongClick = { toggle(f) },
                        subtitle = Volumes.displayPath(context, File(f.path).parent ?: "") + " · " + (if (f.isDir) "folder" else formatSize(f.size)),
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            Box(Modifier.align(Alignment.TopCenter)) {
                SelectionHeader(selected.size, { selected = emptySet() }, { selected = shown.map { it.path }.toSet() })
            }
            SelectionBar(
                vm, selected, onDone = { selected = emptySet() }, modifier = Modifier.align(Alignment.BottomCenter),
                extra = listOf(ExtraAction("Usuń ze zbioru", Icons.Outlined.RemoveCircleOutline) {
                    vm.removeFromTag(selected, tag.id); selected = emptySet()
                }),
            )
        } else {
            ExtendedFloatingActionButton(
                onClick = {
                    vm.message("Przytrzymaj plik, zaznacz kolejne i wybierz „Taguj” → ${tag.name}")
                    nav.goTab("browse")
                },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("Dodaj pliki") },
                containerColor = PC.Accent, contentColor = PC.OnAccent,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            )
        }
    }

    if (editing) {
        TagEditDialog(
            initial = tag,
            onDismiss = { editing = false },
            onSave = { vm.updateTag(it); editing = false },
            onDelete = { editing = false; vm.deleteTag(tag.id); nav.popBackStack() },
        )
    }
}

fun ruleDescription(t: TagEntity): String = buildList {
    t.ruleCategory?.let { c -> add(runCatching { FileCategory.valueOf(c).label.lowercase() }.getOrDefault("pliki")) }
    if (t.ruleCategory == null) add("pliki")
    t.ruleQuery?.let { add("z „$it” w nazwie") }
    t.ruleFolder?.let { add("z folderu $it") }
    if (t.ruleFrom != null && t.ruleTo != null) add("z ${formatShortDate(t.ruleFrom)} – ${formatShortDate(t.ruleTo)}")
    else t.ruleFrom?.let { add("od ${formatShortDate(it)}") }
    t.ruleMinSize?.let { add("większe niż ${formatSize(it)}") }
}.joinToString(" ") + " trafiają tu same"
