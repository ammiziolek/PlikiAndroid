package com.pliki.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.pliki.app.data.*
import com.pliki.app.ui.theme.*
import java.io.File

data class FileUi(
    val path: String,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val category: FileCategory,
    val childCount: Int? = null,
)

fun IndexedFile.toUi() = FileUi(path, name, isDir, size, modified, category)

fun File.toUi(withChildCount: Boolean = true): FileUi {
    val dir = isDirectory
    return FileUi(
        path = path,
        name = name,
        isDir = dir,
        size = if (dir) 0 else length(),
        modified = lastModified(),
        category = if (dir) FileCategory.OTHER else FileCategory.of(name),
        childCount = if (dir && withChildCount) (list()?.size ?: 0) else null,
    )
}

fun FileUi.defaultSubtitle(): String =
    if (isDir) (childCount?.let { itemsCount(it) } ?: "Folder") + " · " + formatDate(modified)
    else formatSize(size) + " · " + formatDate(modified)

fun categoryIcon(c: FileCategory): ImageVector = when (c) {
    FileCategory.IMAGE -> Icons.Outlined.Image
    FileCategory.VIDEO -> Icons.Outlined.Movie
    FileCategory.AUDIO -> Icons.Outlined.MusicNote
    FileCategory.DOCUMENT -> Icons.Outlined.Description
    FileCategory.APK -> Icons.Outlined.Android
    FileCategory.ARCHIVE -> Icons.Outlined.Inventory2
    FileCategory.OTHER -> Icons.AutoMirrored.Outlined.InsertDriveFile
}

@Composable
fun IconTile(icon: ImageVector, bg: Color, fg: Color, size: Dp = 44.dp, corner: Dp = 12.dp, iconSize: Dp = 22.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(corner)).background(bg),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = fg, modifier = Modifier.size(iconSize)) }
}

@Composable
fun FileThumb(item: FileUi, size: Dp = 44.dp, corner: Dp = 12.dp) {
    if (item.isDir) {
        IconTile(Icons.Rounded.Folder, PC.FolderBg, PC.FolderFg, size, corner)
        return
    }
    val cc = catColors(item.category)
    if (item.category == FileCategory.IMAGE || item.category == FileCategory.VIDEO) {
        Box(Modifier.size(size).clip(RoundedCornerShape(corner)).background(cc.bg), contentAlignment = Alignment.Center) {
            Icon(categoryIcon(item.category), null, tint = cc.fg.copy(alpha = 0.5f), modifier = Modifier.size(size / 2))
            AsyncImage(
                model = File(item.path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    } else {
        IconTile(categoryIcon(item.category), cc.bg, cc.fg, size, corner)
    }
}

@Composable
fun TagDots(tags: List<TagEntity>) {
    if (tags.isEmpty()) return
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        tags.take(5).forEach {
            Box(Modifier.size(8.dp).clip(CircleShape).background(tagColor(it.color)))
        }
    }
}

@Composable
fun TagPill(tag: TagEntity) {
    Text(
        tag.name,
        style = MaterialTheme.typography.labelSmall,
        color = OnTag,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(tagColor(tag.color)).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(
    item: FileUi,
    tags: List<TagEntity>,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    subtitle: String = item.defaultSubtitle(),
    trailing: @Composable (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .then(if (selected) Modifier.background(PC.SelectedRow).border(1.5.dp, PC.Accent, shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 8.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (selected) IconTile(Icons.Rounded.Check, PC.Accent, PC.OnAccent)
        else if (selectionMode) Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).border(1.5.dp, PC.Surface3, RoundedCornerShape(12.dp)))
        else FileThumb(item)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(item.name, style = MaterialTheme.typography.bodyLarge.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize * 0.94f), maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = if (selected) PC.Muted2 else PC.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                TagDots(tags)
            }
        }
        trailing?.invoke()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileGridCell(
    item: FileUi,
    tags: List<TagEntity>,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    showName: Boolean = true,
) {
    Column(
        Modifier.clip(RoundedCornerShape(14.dp)).combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(2.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(1f)) {
            FileThumb(item, size = maxWidth, corner = 12.dp)
            if (item.category == FileCategory.VIDEO && !item.isDir) {
                Icon(
                    Icons.Rounded.PlayArrow, null, tint = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).size(22.dp).clip(CircleShape).background(Color(0x99000000)).padding(2.dp),
                )
            }
            if (tags.isNotEmpty()) {
                Box(Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(4.dp)) { TagDots(tags) }
            }
            if (selected) {
                Box(Modifier.matchParentSize().clip(RoundedCornerShape(12.dp)).background(Color(0x66FF7A3D)).border(2.dp, PC.Accent, RoundedCornerShape(12.dp)))
                Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).clip(CircleShape).background(PC.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Check, null, tint = PC.OnAccent, modifier = Modifier.size(16.dp))
                }
            }
        }
        if (showName) {
            Text(item.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, color = PC.Muted2)
        }
    }
}

@Composable
fun CircleIconButton(icon: ImageVector, label: String, onClick: () -> Unit, bg: Color = PC.Surface, tint: Color = PC.Text) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(44.dp),
        colors = IconButtonDefaults.iconButtonColors(containerColor = bg, contentColor = tint),
    ) { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            TextButton(onClick = onAction) { Text(action, color = PC.AccentText) }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconTile(icon, PC.Surface, PC.Muted, size = 64.dp, corner = 20.dp, iconSize = 30.dp)
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = PC.Muted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit, leading: (@Composable () -> Unit)? = null, trailingIcon: ImageVector? = null) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) PC.Text else PC.Surface,
        contentColor = if (selected) PC.Bg else PC.Text,
        modifier = Modifier.height(36.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            leading?.invoke()
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.SemiBold else androidx.compose.ui.text.font.FontWeight.Normal))
            trailingIcon?.let { Icon(it, null, modifier = Modifier.size(16.dp)) }
        }
    }
}
