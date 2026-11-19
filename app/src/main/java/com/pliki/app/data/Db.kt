package com.pliki.app.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "tags")
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Long,
    val createdAt: Long = System.currentTimeMillis(),
    /** Reguła automatyczna: nazwa pliku zawiera ten tekst. */
    val ruleQuery: String? = null,
    /** Reguła automatyczna: kategoria (nazwa enuma FileCategory). */
    val ruleCategory: String? = null,
    /** Reguła automatyczna: ścieżka folderu względem pamięci, np. "DCIM/Camera". */
    val ruleFolder: String? = null,
    /** Reguła automatyczna: data modyfikacji od/do (ms). */
    val ruleFrom: Long? = null,
    val ruleTo: Long? = null,
    /** Reguła automatyczna: minimalny rozmiar w bajtach. */
    val ruleMinSize: Long? = null,
)

fun TagEntity.hasRule(): Boolean =
    !ruleQuery.isNullOrBlank() || ruleCategory != null || !ruleFolder.isNullOrBlank() ||
        ruleFrom != null || ruleTo != null || ruleMinSize != null

@Entity(
    tableName = "file_tags",
    primaryKeys = ["path", "tagId"],
    foreignKeys = [ForeignKey(
        entity = TagEntity::class,
        parentColumns = ["id"],
        childColumns = ["tagId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("tagId")],
)
data class FileTagEntity(
    val path: String,
    val tagId: Long,
    /** Nazwa i rozmiar zapamiętane po to, by odnaleźć plik przeniesiony inną aplikacją. */
    val name: String,
    val size: Long,
)

@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trashPath: String,
    val originalPath: String,
    val name: String,
    val size: Long,
    val isDir: Boolean,
    val deletedAt: Long = System.currentTimeMillis(),
)

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE")
    fun tags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun tag(id: Long): TagEntity?

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM file_tags")
    fun fileTags(): Flow<List<FileTagEntity>>

    @Query("SELECT * FROM file_tags")
    suspend fun allFileTags(): List<FileTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFileTags(list: List<FileTagEntity>)

    @Query("DELETE FROM file_tags WHERE path = :path AND tagId = :tagId")
    suspend fun removeFileTag(path: String, tagId: Long)

    @Query("UPDATE OR REPLACE file_tags SET path = :newPath WHERE path = :oldPath")
    suspend fun movePath(oldPath: String, newPath: String)

    @Query(
        "UPDATE OR REPLACE file_tags SET path = :newPrefix || substr(path, length(:oldPrefix) + 1) " +
            "WHERE substr(path, 1, length(:oldPrefix) + 1) = :oldPrefix || '/'"
    )
    suspend fun movePrefix(oldPrefix: String, newPrefix: String)

    @Query("DELETE FROM file_tags WHERE path = :path OR substr(path, 1, length(:path) + 1) = :path || '/'")
    suspend fun deletePathTree(path: String)

    @Query("SELECT * FROM trash ORDER BY deletedAt DESC")
    fun trash(): Flow<List<TrashEntity>>

    @Query("SELECT * FROM trash")
    suspend fun allTrash(): List<TrashEntity>

    @Insert
    suspend fun insertTrash(entry: TrashEntity): Long

    @Query("DELETE FROM trash WHERE id = :id")
    suspend fun deleteTrash(id: Long)
}

@Database(entities = [TagEntity::class, FileTagEntity::class, TrashEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): TagDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "pliki.db").build()
    }
}
