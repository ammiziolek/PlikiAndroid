package com.pliki.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pliki.app.data.*
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.Routes
import com.pliki.app.ui.components.*
import com.pliki.app.ui.normalize
import com.pliki.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

enum class DateFilter(val label: String, val days: Int?) { ANY("Dowolna data", null), WEEK("Ostatnie 7 dni", 7), MONTH("Ostatnie 30 dni", 30), YEAR("Ostatni rok", 365) }
enum class SizeFilter(val label: String, val min: Long?) {
    ANY("Dowolny rozmiar", null), MB10("Powyżej 10 MB", 10L shl 20), MB100("Powyżej 100 MB", 100L shl 20), GB1("Powyżej 1 GB", 1L shl 30)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: MainViewModel, nav: NavHostController, tagId: Long?) {
    val context = LocalContext.current
    val idx by vm.indexState.collectAsState()
    val tagInfos by vm.tagInfos.collectAsState()
    val pathTags by vm.pathTags.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var cat by rememberSaveable { mutableStateOf<FileCategory?>(null) }
    var tag by rememberSaveable { mutableStateOf(tagId) }
    var date by rememberSaveable { mutableStateOf(DateFilter.ANY) }
    var size by rememberSaveable { mutableStateOf(SizeFilter.ANY) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var saveDialog by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (tagId == null) focus.requestFocus() }

    val tagInfo = tagInfos.firstOrNull { it.tag.id == tag }
    val hasFilter = query.isNotBlank() || cat != null || tag != null || date != DateFilter.ANY || size != SizeFilter.ANY

    val results by produceState<List<FileUi>?>(null, query, cat, tag, date, size, idx.files, tagInfo) {
        if (!hasFilter) { value = null; return@produceState }
        delay(150)
        value = withContext(Dispatchers.Default) {
            val q = normalize(query.trim())
            val since = date.days?.let { System.currentTimeMillis() - it * 24L * 3600 * 1000 }
            val tagPaths = tagInfo?.paths?.toHashSet()
            idx.files.asSequence()
                .filter { !it.path.contains("/$TRASH_DIR/") }
                .filter { q.isEmpty() || normalize(it.name).contains(q) }
                .filter { cat == null || (!it.isDir && it.category == cat) }
                .filter { since == null || it.modified >= since }
                .filter { size.min == null || (!it.isDir && it.size >= size.min!!) }
                .filter { tagPaths == null || it.path in tagPaths }
                .sortedWith(compareByDescending<IndexedFile> { q.isNotEmpty() && normalize(it.name).startsWith(q) }.thenByDescending { it.modified })
                .take(500)
                .map { it.toUi() }
                .toList()
        }
    }

    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    val open = rememberOpener(vm) { nav.navigate(Routes.browse(it)) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            if (selected.isNotEmpty()) {
                SelectionHeader(selected.size, { selected = emptySet() }, { selected = results.orEmpty().map { it.path }.toSet() })
            } else {
                Column(Modifier.statusBarsPadding().padding(start = 8.dp, end = 16.dp, top = 12.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Wstecz") }
                        TextField(
                            value = query, onValueChange = { query = it },
                            placeholder = { Text("Szukaj plików i folderów") },
                            leadingIcon = { Icon(Icons.Outlined.Search, null) },
                            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Wyczyść") } },
                            singleLine = true,
                            shape = RoundedCornerShape(26.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = PC.Surface, unfocusedContainerColor = PC.Surface,
                                focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                        )
                    }
                    FlowRow(Modifier.padding(start = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DropPill(cat?.label ?: "Typ", cat != null, FileCategory.entries.map { it.label }, onPick = { cat = FileCategory.entries[it] }, onClear = { cat = null })
                        DropPill(
                            tagInfo?.tag?.name ?: "Tag", tag != null, tagInfos.map { it.tag.name },
                            onPick = { tag = tagInfos[it].tag.id }, onClear = { tag = null },
                            dot = tagInfo?.tag?.color,
                        )
                        DropPill(if (date == DateFilter.ANY) "Data" else date.label, date != DateFilter.ANY, DateFilter.entries.map { it.label }, onPick = { date = DateFilter.entries[it] }, onClear = { date = DateFilter.ANY })
                        DropPill(if (size == SizeFilter.ANY) "Rozmiar" else size.label, size != SizeFilter.ANY, SizeFilter.entries.map { it.label }, onPick = { size = SizeFilter.entries[it] }, onClear = { size = SizeFilter.ANY })
                    }
                }
            }

            val list = results
            when {
                !idx.ready -> Row(Modifier.padding(24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PC.Accent)
                    Text("Indeksowanie plików… ${idx.scanned}", color = PC.Muted, style = MaterialTheme.typography.bodySmall)
                }
                list == null -> EmptyState(Icons.Outlined.Search, "Czego szukasz?", "Wpisz fragment nazwy albo wybierz filtry: typ, tag, datę lub rozmiar.")
                else -> FilesView(
                    items = list, grid = false, selected = selected, pathTags = pathTags,
                    onOpen = open,
                    onToggle = { f -> selected = if (f.path in selected) selected - f.path else selected + f.path },
                    subtitle = { f -> Volumes.displayPath(context, File(f.path).parent ?: "") + " · " + (if (f.isDir) "folder" else formatSize(f.size)) },
                    top = {
                        Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                if (list.size >= 500) "Ponad 500 wyników — doprecyzuj wyszukiwanie" else "Wyniki: ${list.size}",
                                style = MaterialTheme.typography.bodySmall, color = PC.Muted,
                            )
                            if (tag == null && (query.isNotBlank() || cat != null || size != SizeFilter.ANY)) {
                                Surface(onClick = { saveDialog = true }, shape = RoundedCornerShape(18.dp), color = PC.Surface) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Column(Modifier.weight(1f)) {
                                            Text("Zapisz jako inteligentny zbiór", style = MaterialTheme.typography.labelLarge)
                                            Text("Nowe pliki pasujące do tych filtrów dołączą same", style = MaterialTheme.typography.bodySmall, color = PC.Muted)
                                        }
                                        IconTile(Icons.Rounded.Add, PC.Accent, PC.OnAccent, size = 40.dp)
                                    }
                                }
                            }
                        }
                    },
                    empty = { EmptyState(Icons.Outlined.Search, "Brak wyników", "Spróbuj innej nazwy albo zmień filtry.") },
                )
            }
        }
        if (selected.isNotEmpty()) {
            SelectionBar(vm, selected, onDone = { selected = emptySet() }, modifier = Modifier.align(Alignment.BottomCenter))
        }
    }

    if (saveDialog) {
        val since = date.days?.let { System.currentTimeMillis() - it * 24L * 3600 * 1000 }
        TagEditDialog(
            initial = TagEntity(
                name = query.trim().replaceFirstChar { it.uppercase() },
                color = TagPalette[tagInfos.size % TagPalette.size],
                ruleQuery = query.trim().ifEmpty { null },
                ruleCategory = cat?.name,
                ruleFrom = since,
                ruleMinSize = size.min,
            ),
            onDismiss = { saveDialog = false },
            onSave = { t ->
                saveDialog = false
                vm.createTag(t.copy(id = 0))
                vm.message("Utworzono zbiór „${t.name}”")
            },
        )
    }
}

@Composable
private fun DropPill(label: String, active: Boolean, options: List<String>, onPick: (Int) -> Unit, onClear: () -> Unit, dot: Long? = null) {
    var open by remember { mutableStateOf(false) }
    Box {
        Surface(
            onClick = { open = true },
            shape = RoundedCornerShape(12.dp),
            color = if (active) PC.AccentSoft else PC.Surface,
            contentColor = if (active) Color(0xFFFFB08A) else PC.Text,
        ) {
            Row(Modifier.height(36.dp).padding(start = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                dot?.let { Box(Modifier.size(8.dp).clip(CircleShape).background(tagColor(it))) }
                Text(label, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(open, { open = false }, containerColor = PC.Surface2) {
            if (active) DropdownMenuItem(text = { Text("Wyczyść filtr", color = PC.AccentText) }, onClick = { onClear(); open = false })
            if (options.isEmpty()) DropdownMenuItem(text = { Text("Brak opcji", color = PC.Muted) }, onClick = { open = false })
            options.forEachIndexed { i, o -> DropdownMenuItem(text = { Text(o) }, onClick = { onPick(i); open = false }) }
        }
    }
}
