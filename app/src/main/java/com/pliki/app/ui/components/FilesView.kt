package com.pliki.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pliki.app.data.FileCategory
import com.pliki.app.data.TagEntity
import com.pliki.app.ui.MainViewModel
import com.pliki.app.ui.theme.PC
import com.pliki.app.util.openFile
import java.io.File

/** Lista albo siatka plików z obsługą zaznaczania (długie przytrzymanie). */
@Composable
fun FilesView(
    items: List<FileUi>,
    grid: Boolean,
    selected: Set<String>,
    pathTags: Map<String, List<TagEntity>>,
    onOpen: (FileUi) -> Unit,
    onToggle: (FileUi) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: (FileUi) -> String = { it.defaultSubtitle() },
    bottomPadding: Int = 120,
    top: (@Composable () -> Unit)? = null,
    empty: (@Composable () -> Unit)? = null,
) {
    val selectionMode = selected.isNotEmpty()
    val click: (FileUi) -> Unit = { if (selectionMode) onToggle(it) else onOpen(it) }
    if (grid) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (top != null) item(span = { GridItemSpan(maxLineSpan) }) { top() }
            if (items.isEmpty() && empty != null) item(span = { GridItemSpan(maxLineSpan) }) { empty() }
            items(items, key = { it.path }) { f ->
                FileGridCell(f, pathTags[f.path].orEmpty(), f.path in selected, { click(f) }, { onToggle(f) })
            }
        }
    } else {
        LazyColumn(
            modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = bottomPadding.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            if (top != null) item { top() }
            if (items.isEmpty() && empty != null) item { empty() }
            items(items, key = { it.path }) { f ->
                FileRow(
                    f, pathTags[f.path].orEmpty(), f.path in selected, selectionMode,
                    onClick = { click(f) }, onLongClick = { onToggle(f) }, subtitle = subtitle(f),
                )
            }
        }
    }
}

/** Otwieranie plików: foldery przez [onDir], ZIP z pytaniem o rozpakowanie, reszta w zewnętrznej aplikacji. */
@Composable
fun rememberOpener(vm: MainViewModel, onDir: (String) -> Unit): (FileUi) -> Unit {
    val context = LocalContext.current
    var zipAsk by remember { mutableStateOf<FileUi?>(null) }
    zipAsk?.let { z ->
        AlertDialog(
            onDismissRequest = { zipAsk = null },
            containerColor = PC.Surface,
            title = { Text(z.name) },
            text = { Text("Archiwum ZIP. Rozpakować je do folderu obok?", color = PC.Muted2) },
            confirmButton = { Button(onClick = { vm.unzip(z.path); zipAsk = null }) { Text("Rozpakuj tutaj") } },
            dismissButton = {
                TextButton(onClick = { openFile(context, File(z.path)); zipAsk = null }) { Text("Otwórz w innej aplikacji", color = PC.Text) }
            },
        )
    }
    return { f ->
        when {
            f.isDir -> onDir(f.path)
            f.category == FileCategory.ARCHIVE && f.name.endsWith(".zip", ignoreCase = true) -> zipAsk = f
            else -> openFile(context, File(f.path))
        }
    }
}
