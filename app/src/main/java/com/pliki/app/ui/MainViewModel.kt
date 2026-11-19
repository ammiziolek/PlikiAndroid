package com.pliki.app.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pliki.app.PlikiApp
import com.pliki.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class TagInfo(val tag: TagEntity, val paths: List<String>, val size: Long) {
    val count get() = paths.size
}

data class Clip(val paths: List<String>, val cut: Boolean)

/** Normalizacja do porównań: małe litery, bez polskich znaków. */
fun normalize(s: String): String = s.lowercase()
    .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e').replace('ł', 'l').replace('ń', 'n')
    .replace('ó', 'o').replace('ś', 's').replace('ź', 'z').replace('ż', 'z')

fun TagEntity.ruleMatches(f: IndexedFile, primaryRoot: String): Boolean {
    if (f.isDir || !hasRule()) return false
    ruleQuery?.takeIf { it.isNotBlank() }?.let { if (!normalize(f.name).contains(normalize(it.trim()))) return false }
    ruleCategory?.let { if (f.category.name != it) return false }
    ruleFolder?.takeIf { it.isNotBlank() }?.let { folder ->
        val abs = (if (folder.startsWith("/")) folder else "$primaryRoot/${folder.trim('/')}").trimEnd('/')
        if (!f.path.startsWith("$abs/")) return false
    }
    ruleFrom?.let { if (f.modified < it) return false }
    ruleTo?.let { if (f.modified > it) return false }
    ruleMinSize?.let { if (f.size < it) return false }
    return true
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val plikiApp = app as PlikiApp
    private val dao = plikiApp.db.dao()
    private val ops = plikiApp.ops
    val index = plikiApp.index

    val indexState: StateFlow<IndexState> = index.state
    val tags: StateFlow<List<TagEntity>> = dao.tags().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val fileTags = dao.fileTags().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val trash: StateFlow<List<TrashEntity>> = dao.trash().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _clip = MutableStateFlow<Clip?>(null)
    val clip: StateFlow<Clip?> = _clip.asStateFlow()

    private val _busy = MutableStateFlow<String?>(null)
    val busy: StateFlow<String?> = _busy.asStateFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    /** Zwiększany po każdej operacji, żeby otwarte foldery się odświeżyły. */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    private val primaryRoot = Volumes.primaryRoot().path

    val tagInfos: StateFlow<List<TagInfo>> = combine(tags, fileTags, indexState) { tags, fts, idx ->
        val byPath = HashMap<String, IndexedFile>(idx.files.size * 2)
        idx.files.forEach { byPath[it.path] = it }
        val manual = fts.groupBy { it.tagId }
        tags.map { tag ->
            val paths = LinkedHashSet<String>()
            manual[tag.id]?.forEach { if (!it.path.contains("/$TRASH_DIR/")) paths += it.path }
            if (tag.hasRule()) idx.files.forEach { if (tag.ruleMatches(it, primaryRoot)) paths += it.path }
            val size = paths.sumOf { p -> byPath[p]?.size ?: 0L }
            TagInfo(tag, paths.toList(), size)
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Ścieżka -> tagi pliku (ręczne i z reguł). */
    val pathTags: StateFlow<Map<String, List<TagEntity>>> = tagInfos.map { infos ->
        val m = HashMap<String, MutableList<TagEntity>>()
        infos.forEach { info -> info.paths.forEach { m.getOrPut(it) { mutableListOf() } += info.tag } }
        m as Map<String, List<TagEntity>>
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Ręcznie przypisane tagi (do zaznaczania w panelu tagowania). */
    fun manualTagIds(path: String): Set<Long> = fileTags.value.filter { it.path == path }.map { it.tagId }.toSet()

    init {
        viewModelScope.launch(Dispatchers.IO) { runCatching { ops.purgeOldTrash() } }
        // Po każdym zakończonym skanie: odnajdź otagowane pliki przeniesione inną aplikacją.
        viewModelScope.launch {
            indexState.filter { it.ready && !it.scanning }.collect { recoverMovedTags(it.files) }
        }
    }

    fun startIndex() {
        if (!indexState.value.ready && !indexState.value.scanning) index.rescan()
    }

    fun refreshIndex() = index.rescan()

    private suspend fun recoverMovedTags(files: List<IndexedFile>) = withContext(Dispatchers.IO) {
        val missing = dao.allFileTags().filter { !it.path.contains("/$TRASH_DIR/") && !File(it.path).exists() }
        if (missing.isEmpty()) return@withContext
        val byKey = files.filter { !it.isDir }.groupBy { it.name to it.size }
        for (ft in missing) {
            val candidates = byKey[ft.name to ft.size] ?: continue
            if (candidates.size == 1) dao.movePath(ft.path, candidates[0].path)
        }
    }

    fun message(text: String) {
        _messages.trySend(text)
    }

    private fun run(label: String, block: suspend () -> String?) {
        viewModelScope.launch {
            _busy.value = label
            val msg = try {
                withContext(Dispatchers.IO) { block() }
            } catch (e: Exception) {
                "Błąd: ${e.message ?: e.javaClass.simpleName}"
            }
            _busy.value = null
            _revision.value++
            index.rescanSoon()
            msg?.let { _messages.send(it) }
        }
    }

    // ---------- Schowek ----------
    fun copyToClip(paths: Collection<String>) {
        _clip.value = Clip(paths.toList(), cut = false)
        message("Skopiowano ${filesCount(paths.size)} — przejdź do folderu i wybierz „Wklej tutaj”")
    }

    fun cutToClip(paths: Collection<String>) {
        _clip.value = Clip(paths.toList(), cut = true)
        message("Do przeniesienia: ${filesCount(paths.size)} — przejdź do folderu i wybierz „Przenieś tutaj”")
    }

    fun clearClip() {
        _clip.value = null
    }

    fun paste(destDir: String) {
        val c = _clip.value ?: return
        _clip.value = null
        val files = c.paths.map(::File).filter { it.exists() }
        run(if (c.cut) "Przenoszenie…" else "Kopiowanie…") {
            val n = if (c.cut) ops.move(files, File(destDir)) else ops.copy(files, File(destDir))
            if (c.cut) "Przeniesiono ${filesCount(n)}" else "Skopiowano ${filesCount(n)}"
        }
    }

    // ---------- Operacje na plikach ----------
    fun moveToTrash(paths: Collection<String>) = run("Przenoszenie do kosza…") {
        val n = ops.moveToTrash(paths.map(::File))
        "Przeniesiono do kosza: ${filesCount(n)}"
    }

    fun rename(path: String, newName: String) = run("Zmiana nazwy…") {
        if (newName.isBlank() || newName.contains('/')) return@run "Nieprawidłowa nazwa"
        if (ops.rename(File(path), newName.trim()) == null) "Nie udało się zmienić nazwy (taka nazwa już istnieje?)" else null
    }

    fun mkdir(parent: String, name: String) = run("Tworzenie folderu…") {
        if (name.isBlank() || name.contains('/')) return@run "Nieprawidłowa nazwa"
        if (ops.mkdir(File(parent), name.trim()) == null) "Nie udało się utworzyć folderu" else null
    }

    fun zip(paths: Collection<String>, destDir: String) = run("Pakowanie do ZIP…") {
        val f = ops.zip(paths.map(::File), File(destDir))
        "Utworzono ${f.name}"
    }

    fun unzip(path: String) = run("Rozpakowywanie…") {
        val f = File(path)
        val dest = ops.unzip(f, f.parentFile!!)
        "Rozpakowano do folderu ${dest.name}"
    }

    fun restore(entries: List<TrashEntity>) = run("Przywracanie…") {
        "Przywrócono ${filesCount(ops.restore(entries))}"
    }

    fun deleteForever(entries: List<TrashEntity>) = run("Usuwanie…") {
        ops.deleteForever(entries)
        "Usunięto na zawsze: ${filesCount(entries.size)}"
    }

    // ---------- Tagi ----------
    fun createTag(tag: TagEntity, thenAssign: Collection<String> = emptyList()) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = dao.insert(tag)
            if (thenAssign.isNotEmpty()) assign(thenAssign, id)
        }
    }

    suspend fun createTagNow(tag: TagEntity): Long = withContext(Dispatchers.IO) { dao.insert(tag) }

    fun updateTag(tag: TagEntity) {
        viewModelScope.launch(Dispatchers.IO) { dao.update(tag) }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch(Dispatchers.IO) { dao.delete(id) }
    }

    private suspend fun assign(paths: Collection<String>, tagId: Long) {
        dao.addFileTags(paths.map {
            val f = File(it)
            FileTagEntity(it, tagId, f.name, if (f.isFile) f.length() else 0)
        })
    }

    /** Ustawia tagi dla plików: dodaje [add], usuwa [remove]. Opcjonalnie przenosi do folderu "Zbiory/<tag>". */
    fun applyTags(paths: Collection<String>, add: Set<Long>, remove: Set<Long>, moveToTagFolder: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            add.forEach { assign(paths, it) }
            remove.forEach { id -> paths.forEach { dao.removeFileTag(it, id) } }
            if (moveToTagFolder && add.isNotEmpty()) {
                val tag = dao.tag(add.first()) ?: return@launch
                val dir = File(Volumes.primaryRoot(), "Zbiory/${tag.name.replace('/', '-')}")
                dir.mkdirs()
                ops.move(paths.map(::File), dir)
                _revision.value++
                index.rescanSoon()
            }
            val names = tags.value.filter { it.id in add }.joinToString { it.name }
            if (add.isNotEmpty()) _messages.send("Dodano do: $names")
            else if (remove.isNotEmpty()) _messages.send("Zaktualizowano tagi")
        }
    }

    fun removeFromTag(paths: Collection<String>, tagId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            paths.forEach { dao.removeFileTag(it, tagId) }
            val tag = dao.tag(tagId)
            if (tag != null && tag.hasRule()) _messages.send("Pliki pasujące do reguły automatycznej nadal będą w zbiorze")
        }
    }

    /** Podpowiedzi tagów na podstawie nazw plików (np. "faktura_11.pdf" -> "Faktury"). */
    fun suggestTags(paths: Collection<String>): List<TagEntity> {
        val names = paths.map { normalize(File(it).name) }
        return tags.value.filter { tag ->
            val n = normalize(tag.name).filter { it.isLetterOrDigit() }
            if (n.length < 3) return@filter false
            val stem = n.take(maxOf(3, minOf(n.length, n.length - 2)))
            names.any { it.contains(stem) }
        }
    }
}
