package com.pliki.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.pliki.app.data.TagEntity
import com.pliki.app.data.filesCount
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.normalize
import com.pliki.app.ui.theme.*
import kotlinx.coroutines.launch
import java.io.File

private enum class Check { ON, OFF, PARTIAL }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagSheet(vm: MainViewModel, paths: Set<String>, onDismiss: () -> Unit) {
    val tags by vm.tags.collectAsState()
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Stan początkowy: ile z zaznaczonych plików ma dany tag (ręcznie).
    val initial = remember(paths) {
        val per = paths.map { vm.manualTagIds(it) }
        tags.associate { t ->
            val n = per.count { t.id in it }
            t.id to when (n) {
                0 -> Check.OFF
                paths.size -> Check.ON
                else -> Check.PARTIAL
            }
        }
    }
    val state = remember { mutableStateMapOf<Long, Check>().apply { putAll(initial) } }
    var query by remember { mutableStateOf("") }
    var moveToFolder by remember { mutableStateOf(false) }
    var creating by remember { mutableStateOf(false) }
    val suggestions = remember(paths, tags) { vm.suggestTags(paths).filter { (state[it.id] ?: Check.OFF) != Check.ON } }

    fun createQuick(name: String) {
        scope.launch {
            val color = TagPalette[tags.size % TagPalette.size]
            val id = vm.createTagNow(TagEntity(name = name.trim(), color = color))
            state[id] = Check.ON
            query = ""
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color1C,
        dragHandle = { Box(Modifier.padding(top = 12.dp).size(40.dp, 4.dp).clip(CircleShape).background(PC.Surface3)) },
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Taguj ${filesCount(paths.size)}", style = MaterialTheme.typography.headlineSmall)
                Text(
                    paths.joinToString { File(it).name },
                    style = MaterialTheme.typography.bodySmall, color = PC.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Znajdź lub utwórz tag…") },
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = PC.Surface2, unfocusedContainerColor = PC.Surface2,
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            suggestions.firstOrNull()?.let { s ->
                Surface(onClick = { state[s.id] = Check.ON }, shape = RoundedCornerShape(16.dp), color = PC.SelectedRow, contentColor = androidx.compose.ui.graphics.Color(0xFFFFC2A3)) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Outlined.Bolt, null, modifier = Modifier.size(18.dp))
                        Text(
                            buildAnnotatedString {
                                append("Podpowiedź: pasuje do nazwy — dodać do ")
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(s.name) }
                                append("?")
                            },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("TWOJE TAGI", style = MaterialTheme.typography.labelMedium, color = PC.Muted)
                val q = normalize(query.trim())
                val visible = tags.filter { q.isEmpty() || normalize(it.name).contains(q) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    visible.forEach { tag ->
                        val st = state[tag.id] ?: Check.OFF
                        TagToggleChip(tag, st) {
                            state[tag.id] = if (st == Check.ON) Check.OFF else Check.ON
                        }
                    }
                    val exact = tags.any { normalize(it.name) == q }
                    Surface(
                        onClick = { if (query.isNotBlank() && !exact) createQuick(query) else creating = true },
                        shape = RoundedCornerShape(20.dp),
                        color = androidx.compose.ui.graphics.Color.Transparent,
                        contentColor = PC.Muted2,
                        modifier = Modifier.height(40.dp).border(1.5.dp, PC.Surface3, RoundedCornerShape(20.dp)),
                    ) {
                        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Rounded.Add, null, modifier = Modifier.size(16.dp))
                            Text(if (query.isNotBlank() && !exact) "Utwórz „${query.trim()}”" else "Nowy tag", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
                if (tags.isEmpty()) {
                    Text("Nie masz jeszcze tagów. Wpisz nazwę powyżej, np. „Faktury”, i utwórz pierwszy.", style = MaterialTheme.typography.bodySmall, color = PC.Muted)
                }
            }

            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Przenieś też do folderu tagu", style = MaterialTheme.typography.bodyMedium)
                    Text("Zbiory/<nazwa tagu> w pamięci wewnętrznej", style = MaterialTheme.typography.bodySmall, color = PC.Muted)
                }
                Switch(checked = moveToFolder, onCheckedChange = { moveToFolder = it })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = PC.Surface2, contentColor = PC.Text),
                ) { Text("Anuluj") }
                Button(
                    onClick = {
                        val add = state.filter { (id, st) -> st == Check.ON && initial[id] != Check.ON }.keys
                        val remove = state.filter { (id, st) -> st == Check.OFF && (initial[id] ?: Check.OFF) != Check.OFF }.keys
                        vm.applyTags(paths, add, remove, moveToFolder)
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(18.dp),
                ) { Text("Zapisz") }
            }
        }
    }

    if (creating) {
        TagEditDialog(
            initial = null,
            defaultColor = TagPalette[tags.size % TagPalette.size],
            onDismiss = { creating = false },
            onSave = { tag ->
                creating = false
                scope.launch {
                    val id = vm.createTagNow(tag)
                    state[id] = Check.ON
                }
            },
        )
    }
}

private val Color1C = androidx.compose.ui.graphics.Color(0xFF1A1C20)

@Composable
private fun TagToggleChip(tag: TagEntity, st: Check, onClick: () -> Unit) {
    val on = st == Check.ON
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (on) tagColor(tag.color) else PC.Surface2,
        contentColor = if (on) OnTag else PC.Text,
        modifier = Modifier.height(40.dp),
    ) {
        Row(
            Modifier.padding(start = if (st == Check.OFF) 14.dp else 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (st) {
                Check.ON -> Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                Check.PARTIAL -> Icon(Icons.Rounded.Remove, "częściowo", tint = tagColor(tag.color), modifier = Modifier.size(18.dp))
                Check.OFF -> Box(Modifier.size(10.dp).clip(CircleShape).background(tagColor(tag.color)))
            }
            Text(tag.name, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal))
        }
    }
}
