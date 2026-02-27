package com.bitcat.accountbook.data.dao

import androidx.room.*
import com.bitcat.accountbook.data.entity.RecordTagCrossRef
import com.bitcat.accountbook.data.entity.TagEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY name COLLATE NOCASE ASC")
    fun observeAllTags(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE name = :name LIMIT 1")
    suspend fun findByName(name: String): TagEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(tag: TagEntity): Long



    @Transaction
    suspend fun upsertByName(name: String): TagEntity {
        val trimmed = name.trim()
        val existed = findByName(trimmed)
        if (existed != null) return existed

        val now = System.currentTimeMillis()
        val id = insert(TagEntity(name = trimmed, createdAt = now))
        // 如果 IGNORE 导致 id=-1，说明同时被插入了，再查一次
        return if (id > 0) TagEntity(id = id, name = trimmed, createdAt = now)
        else findByName(trimmed)!!
    }
    @Transaction
    suspend fun replaceTagsForRecord(recordId: Long, tagIds: List<Long>) {
        clearTagsForRecord(recordId)

        if (tagIds.isNotEmpty()) {
            insertRefs(
                tagIds.map { tid ->
                    RecordTagCrossRef(recordId, tid)
                }
            )
        }
    }


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRefs(refs: List<RecordTagCrossRef>)

    @Query("DELETE FROM record_tag WHERE record_id = :recordId")
    suspend fun clearTagsForRecord(recordId: Long)
}
