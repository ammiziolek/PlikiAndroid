package com.pliki.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.pliki.app.data.FileCategory
import com.pliki.app.data.TagEntity
import com.pliki.app.data.formatShortDate
import com.pliki.app.data.hasRule
import com.pliki.app.ui.theme.*

/** Okno tworzenia/edycji tagu (zbioru), z opcjonalną regułą automatyczną. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagEditDialog(
    initial: TagEntity?,
    onDismiss: () -> Unit,
    onSave: (TagEntity) -> Unit,
    onDelete: (() -> Unit)? = null,
    defaultColor: Long = TagPalette[0],
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var color by remember { mutableLongStateOf(initial?.color ?: defaultColor) }
    var ruleOn by remember { mutableStateOf(initial?.hasRule() == true) }
    var ruleQuery by remember { mutableStateOf(initial?.ruleQuery ?: "") }
    var ruleCategory by remember { mutableStateOf(initial?.ruleCategory) }
    var ruleFolder by remember { mutableStateOf(initial?.ruleFolder ?: "") }
    var ruleFrom by remember { mutableStateOf(initial?.ruleFrom) }
    var ruleTo by remember { mutableStateOf(initial?.ruleTo) }
    var pickDates by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = PC.Surface,
        title = { Text(if (initial == null) "Nowy zbiór" else "Edytuj zbiór") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nazwa") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Kolor", style = MaterialTheme.typography.labelMedium, color = PC.Muted)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    TagPalette.forEach { c ->
                        Box(
                            Modifier.size(36.dp).clip(CircleShape).background(tagColor(c))
                                .then(if (c == color) Modifier.border(3.dp, PC.Text, CircleShape) else Modifier)
                                .clickable { color = c },
                            contentAlignment = Alignment.Center,
                        ) { if (c == color) Icon(Icons.Rounded.Check, "wybrany", tint = OnTag, modifier = Modifier.size(18.dp)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Bolt, null, tint = PC.AccentText, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text("Reguła automatyczna", style = MaterialTheme.typography.bodyMedium)
                        Text("Pasujące pliki trafią tu same", style = MaterialTheme.typography.bodySmall, color = PC.Muted)
                    }
                    Switch(ruleOn, { ruleOn = it })
                }
                if (ruleOn) {
                    OutlinedTextField(ruleQuery, { ruleQuery = it }, label = { Text("Nazwa pliku zawiera") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Typ pliku", style = MaterialTheme.typography.labelMedium, color = PC.Muted)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill("Dowolny", ruleCategory == null, { ruleCategory = null })
                        FileCategory.entries.filter { it != FileCategory.OTHER }.forEach { c ->
                            FilterPill(c.label, ruleCategory == c.name, { ruleCategory = c.name })
                        }
                    }
                    OutlinedTextField(
                        ruleFolder, { ruleFolder = it },
                        label = { Text("Tylko z folderu (opcjonalnie)") },
                        placeholder = { Text("np. DCIM/Camera") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(onClick = { pickDates = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.DateRange, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (ruleFrom == null && ruleTo == null) "Zakres dat: dowolny"
                            else "Od ${ruleFrom?.let(::formatShortDate) ?: "…"} do ${ruleTo?.let(::formatShortDate) ?: "…"}",
                            color = PC.Text,
                        )
                    }
                    if (ruleFrom != null || ruleTo != null) {
                        TextButton(onClick = { ruleFrom = null; ruleTo = null }) { Text("Wyczyść daty", color = PC.AccentText) }
                    }
                }
                if (onDelete != null) {
                    TextButton(onClick = { confirmDelete = true }) { Text("Usuń zbiór", color = PC.Danger) }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank(),
                onClick = {
                    val base = initial ?: TagEntity(name = "", color = color)
                    onSave(
                        base.copy(
                            name = name.trim(),
                            color = color,
                            ruleQuery = if (ruleOn) ruleQuery.trim().ifEmpty { null } else null,
                            ruleCategory = if (ruleOn) ruleCategory else null,
                            ruleFolder = if (ruleOn) ruleFolder.trim().ifEmpty { null } else null,
                            ruleFrom = if (ruleOn) ruleFrom else null,
                            ruleTo = if (ruleOn) ruleTo else null,
                            ruleMinSize = if (ruleOn) base.ruleMinSize else null,
                        )
                    )
                },
            ) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj", color = PC.Text) } },
    )

    if (pickDates) {
        val st = rememberDateRangePickerState(initialSelectedStartDateMillis = ruleFrom, initialSelectedEndDateMillis = ruleTo)
        DatePickerDialog(
            onDismissRequest = { pickDates = false },
            confirmButton = {
                TextButton(onClick = {
                    ruleFrom = st.selectedStartDateMillis
                    // koniec dnia, żeby objąć pliki z ostatniego dnia zakresu
                    ruleTo = st.selectedEndDateMillis?.plus(24L * 3600 * 1000 - 1)
                    pickDates = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDates = false }) { Text("Anuluj") } },
        ) {
            DateRangePicker(state = st, modifier = Modifier.weight(1f))
        }
    }

    if (confirmDelete && onDelete != null) {
        ConfirmDialog(
            title = "Usunąć zbiór?",
            text = "Zbiór „${initial?.name}” zostanie usunięty. Same pliki zostaną na miejscu.",
            confirm = "Usuń",
            onConfirm = { confirmDelete = false; onDelete() },
            onDismiss = { confirmDelete = false },
        )
    }
}
