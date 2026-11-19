package com.pliki.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pliki.app.ui.components.IconTile
import com.pliki.app.ui.theme.PC

@Composable
fun PermissionScreen() {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().systemBarsPadding().padding(28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp, Alignment.CenterVertically),
    ) {
        IconTile(Icons.Rounded.Folder, PC.AccentSoft, PC.AccentText, size = 72.dp, corner = 24.dp, iconSize = 36.dp)
        Text("Witaj w Plikach", style = MaterialTheme.typography.headlineLarge)
        Text(
            "Żeby przeglądać, porządkować i tagować pliki, aplikacja potrzebuje dostępu do wszystkich plików na telefonie.\n\n" +
                "Pliki nie są nigdzie wysyłane — wszystko dzieje się na urządzeniu.",
            style = MaterialTheme.typography.bodyLarge, color = PC.Muted2,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                runCatching { context.startActivity(intent) }
                    .onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(18.dp),
        ) { Text("Przyznaj dostęp") }
        Text(
            "W ustawieniach włącz „Zezwalaj na dostęp do zarządzania wszystkimi plikami”, a potem wróć do aplikacji.",
            style = MaterialTheme.typography.bodySmall, color = PC.Muted,
        )
    }
}
