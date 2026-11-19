package com.pliki.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.pliki.app.R
import com.pliki.app.data.FileCategory

object PC {
    val Bg = Color(0xFF0F1012)
    val Bar = Color(0xFF15171A)
    val Surface = Color(0xFF1C1E22)
    val Surface2 = Color(0xFF24272C)
    val Surface3 = Color(0xFF2E3238)
    val Border = Color(0xFF2A2D33)
    val Text = Color(0xFFF2F2F0)
    val Muted = Color(0xFFA0A4AB)
    val Muted2 = Color(0xFFC4C7CC)
    val Accent = Color(0xFFFF7A3D)
    val AccentText = Color(0xFFFF8A50)
    val AccentSoft = Color(0xFF3A2215)
    val SelectedRow = Color(0xFF2A1C14)
    val OnAccent = Color(0xFF1A0F08)
    val Danger = Color(0xFFFF8F8F)
    val Good = Color(0xFF7FE0B0)
    val FolderBg = Color(0xFF2B2416)
    val FolderFg = Color(0xFFF2C94C)
}

/** Kolory tagów do wyboru. Wszystkie jasne, więc tekst na nich jest ciemny. */
val TagPalette: List<Long> = listOf(
    0xFFFF7A3D, 0xFF5B9DFF, 0xFF4CC38A, 0xFFB48CFF,
    0xFFFF6FA8, 0xFFF2C94C, 0xFF4FD1D9, 0xFF8A8F98,
)

fun tagColor(c: Long) = Color(c)
val OnTag = Color(0xFF111214)

data class CatColors(val bg: Color, val fg: Color)

fun catColors(c: FileCategory): CatColors = when (c) {
    FileCategory.IMAGE -> CatColors(Color(0xFF2B1E17), Color(0xFFFF8A50))
    FileCategory.VIDEO -> CatColors(Color(0xFF17223A), Color(0xFF7DB2FF))
    FileCategory.AUDIO -> CatColors(Color(0xFF2A1C33), Color(0xFFC4A3FF))
    FileCategory.DOCUMENT -> CatColors(Color(0xFF16291F), Color(0xFF5DD39B))
    FileCategory.APK -> CatColors(Color(0xFF15282A), Color(0xFF6FDCE3))
    FileCategory.ARCHIVE -> CatColors(Color(0xFF2B2416), Color(0xFFF2C94C))
    FileCategory.OTHER -> CatColors(PC.Surface, Color(0xFFE6E7E9))
}

/** Kolor kategorii na wykresach. */
fun catChartColor(c: FileCategory): Color = when (c) {
    FileCategory.IMAGE -> Color(0xFFFF7A3D)
    FileCategory.VIDEO -> Color(0xFF5B9DFF)
    FileCategory.AUDIO -> Color(0xFFB48CFF)
    FileCategory.DOCUMENT -> Color(0xFF4CC38A)
    FileCategory.APK -> Color(0xFF4FD1D9)
    FileCategory.ARCHIVE -> Color(0xFFF2C94C)
    FileCategory.OTHER -> Color(0xFF8A8F98)
}

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun grotesk(w: Int) = Font(
    R.font.space_grotesk, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
private fun inter(w: Int) = Font(
    R.font.inter, FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val Grotesk = FontFamily(grotesk(500), grotesk(600), grotesk(700))
val Inter = FontFamily(inter(400), inter(500), inter(600))

private val typography = Typography(
    headlineLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.Bold, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleMedium = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleSmall = TextStyle(fontFamily = Grotesk, fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

private val colors = darkColorScheme(
    primary = PC.Accent,
    onPrimary = PC.OnAccent,
    primaryContainer = PC.AccentSoft,
    onPrimaryContainer = PC.AccentText,
    secondary = PC.Muted2,
    background = PC.Bg,
    onBackground = PC.Text,
    surface = PC.Bg,
    onSurface = PC.Text,
    surfaceVariant = PC.Surface,
    onSurfaceVariant = PC.Muted,
    surfaceContainerLowest = PC.Bg,
    surfaceContainerLow = PC.Bar,
    surfaceContainer = PC.Surface,
    surfaceContainerHigh = Color(0xFF1A1C20),
    surfaceContainerHighest = PC.Surface2,
    outline = Color(0xFF4A4E56),
    outlineVariant = PC.Border,
    error = PC.Danger,
)

@Composable
fun PlikiTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colors, typography = typography, content = content)
}
