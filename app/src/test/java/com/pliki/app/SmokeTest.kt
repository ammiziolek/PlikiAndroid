package com.pliki.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Environment
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Test "dymny": uruchamia całą aplikację na sztucznej pamięci z przykładowymi plikami,
 * przechodzi przez główne ekrany i zapisuje zrzuty do app/screenshots/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class SmokeTest {
    init { org.robolectric.shadows.ShadowLog.stream = System.out }

    @get:Rule
    val rule = createEmptyComposeRule()

    private lateinit var root: File

    private fun jpg(f: File, c1: Int, c2: Int) {
        f.parentFile!!.mkdirs()
        val bmp = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        val p = Paint().apply { shader = LinearGradient(0f, 0f, 240f, 240f, c1, c2, Shader.TileMode.CLAMP) }
        Canvas(bmp).drawRect(0f, 0f, 240f, 240f, p)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    }

    private fun file(rel: String, bytes: Int, seed: Int = rel.hashCode()) {
        val f = File(root, rel)
        f.parentFile!!.mkdirs()
        f.writeBytes(ByteArray(bytes) { ((it * 31 + seed) % 251).toByte() })
    }

    @Before
    fun setUp() {
        com.pliki.app.util.StorageAccess.override = { true }
        root = Environment.getExternalStorageDirectory()
        root.mkdirs()
        jpg(File(root, "DCIM/Camera/IMG_20260812_1843.jpg"), Color.rgb(135, 183, 217), Color.rgb(233, 194, 122))
        jpg(File(root, "DCIM/Camera/IMG_20260814_0912.jpg"), Color.rgb(47, 111, 163), Color.rgb(232, 221, 196))
        jpg(File(root, "DCIM/Camera/IMG_20260815_1720.jpg"), Color.rgb(217, 140, 95), Color.rgb(59, 42, 42))
        jpg(File(root, "Pictures/WhatsApp/IMG-20260815-WA0003.jpg"), Color.rgb(90, 140, 90), Color.rgb(242, 230, 184))
        file("Download/faktura_11_2026.pdf", 214_000)
        file("Download/faktura_10_2026.pdf", 198_000)
        file("Download/bilety_ryanair.pdf", 340_000)
        file("Download/Dokumenty/umowa_najmu_2026.pdf", 2_100_000)
        file("Download/Dokumenty/budzet_remont.xlsx", 86_000)
        file("Documents/Scans/Umowa_deweloperska_skan.pdf", 870_000)
        file("WhatsApp/Documents/aneks_do_umowy.docx", 64_000)
        file("Music/playlista.mp3", 4_200_000)
        // duplikat
        file("Download/IMG_kopia.bin", 500_000, seed = 7)
        file("Pictures/IMG_kopia.bin", 500_000, seed = 7)
        ZipOutputStream(File(root, "Download/PIT_2025.zip").outputStream()).use { z ->
            z.putNextEntry(ZipEntry("PIT-37.pdf")); z.write(ByteArray(1000)); z.closeEntry()
        }
    }

    private fun waitText(text: String, substring: Boolean = false, timeout: Long = 15_000) {
        try {
            rule.waitUntil(timeout) { rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty() }
        } catch (e: Throwable) {
            runCatching { rule.onRoot().captureRoboImage("screenshots/FAIL.png") }
            rule.onRoot(useUnmergedTree = true).printToLog("FAIL")
            throw AssertionError("Nie pojawił się tekst: $text", e)
        }
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        rule.onRoot().captureRoboImage("screenshots/$name.png")
    }

    @Test
    fun mainFlow() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Start: indeks gotowy, ostatnie pliki widoczne
            waitText("Ostatnie")
            shot("01_start")

            // Pobrane -> przeglądanie folderu
            rule.onNodeWithText("Pobrane").performClick()
            waitText("faktura_11_2026.pdf")
            shot("02_folder")

            // Zaznaczanie dwóch faktur
            rule.onNodeWithText("faktura_11_2026.pdf").performTouchInput { longClick() }
            rule.onNodeWithText("faktura_10_2026.pdf").performClick()
            waitText("Zaznaczono 2")
            shot("03_zaznaczanie")

            // Tagowanie: nowy tag "Faktury"
            rule.onNodeWithText("Taguj").performClick()
            waitText("Taguj 2 pliki")
            rule.onNode(hasSetTextAction()).performTextInput("Faktury")
            rule.onNodeWithText("Utwórz „Faktury”").performClick()
            rule.waitUntil(5000) { rule.onAllNodesWithText("Faktury").fetchSemanticsNodes().isNotEmpty() }
            rule.onNode(hasSetTextAction()).performTextClearance()
            shot("04_tagowanie")
            rule.onNodeWithText("Zapisz").performClick()
            waitText("Dodano do: Faktury")

            // Zbiory
            rule.onNodeWithText("Zbiory").performClick()
            waitText("2 pliki", substring = true)
            shot("05_zbiory")
            rule.onNodeWithText("Faktury").performClick()
            waitText("ZBIÓR")
            shot("06_zbior")
            rule.onNodeWithContentDescription("Wstecz").performClick()

            // Wyszukiwanie
            waitText("Zbiory")
            rule.onNodeWithContentDescription("Szukaj").performClick()
            rule.onNode(hasSetTextAction()).performTextInput("umow")
            waitText("Wyniki: 3")
            shot("07_szukaj")
            rule.onNodeWithContentDescription("Wstecz").performClick()

            // Miejsce i duplikaty
            rule.onNodeWithText("Miejsce").performClick()
            waitText("Do posprzątania")
            shot("08_miejsce")
            rule.onNodeWithText("Duplikaty").performClick()
            waitText("Zaznacz kopie automatycznie")
            shot("09_duplikaty")
            rule.onNodeWithText("Zaznacz kopie automatycznie").performClick()
            waitText("Zaznaczono 1")
            rule.onNodeWithText("Do kosza").performClick()
            waitText("Przenieść do kosza?")
            rule.onAllNodesWithText("Do kosza").onLast().performClick()
            waitText("Przeniesiono do kosza", substring = true)
            rule.waitUntil(5000) { rule.onAllNodesWithText("Przeniesiono do kosza", substring = true).fetchSemanticsNodes().isEmpty() }
            val a = File(root, "Download/IMG_kopia.bin").exists()
            val b = File(root, "Pictures/IMG_kopia.bin").exists()
            assertTrue("dokładnie jedna kopia powinna zostać", a xor b)
            assertTrue(File(root, ".PlikiKosz").listFiles()!!.any { it.name.endsWith("IMG_kopia.bin") })
            rule.onNodeWithContentDescription("Wstecz").performClick()

            // Kosz i przywracanie
            rule.onNodeWithText("Start").performClick()
            waitText("Ostatnie")
            rule.onNodeWithText("Kosz").performClick()
            waitText("IMG_kopia.bin")
            shot("10_kosz")
            rule.onNodeWithText("IMG_kopia.bin").performClick()
            rule.onNodeWithText("Przywróć").performClick()
            waitText("Przywrócono 1 plik")
            assertTrue(File(root, "Download/IMG_kopia.bin").exists() && File(root, "Pictures/IMG_kopia.bin").exists())
            assertFalse(File(root, ".PlikiKosz").listFiles()!!.any { it.name.endsWith("IMG_kopia.bin") })
        }
    }
}
