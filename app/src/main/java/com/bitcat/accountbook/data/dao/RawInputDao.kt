package com.bitcat.accountbook.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.bitcat.accountbook.data.entity.RawInputEntity
import kotlinx.coroutines.flow.Flow
@Dao
interface RawInputDao {
    @Query("""
    SELECT * FROM raw_inputs
    WHERE record_id = :recordId
    ORDER BY created_at DESC
""")
    fun observeByRecordId(recordId: Long): Flow<List<RawInputEntity>>

    // 删除某个时间点之前的所有原始输入，返回删除行数
    @Query("DELETE FROM raw_inputs WHERE created_at < :thresholdMillis")
    suspend fun deleteBefore(thresholdMillis: Long): Int

    // 仅用于展示：当前 raw_inputs 数量（设置页显示用）
    @Query("SELECT COUNT(*) FROM raw_inputs")
    suspend fun countAll(): Int

    @Insert
    suspend fun insert(entity: RawInputEntity): Long

    @Query("SELECT * FROM raw_inputs WHERE record_id = :recordId ORDER BY created_at DESC LIMIT 1")
    suspend fun getLatestByRecordId(recordId: Long): RawInputEntity?

    @Query(
        """
        SELECT ri.*
        FROM raw_inputs ri
        INNER JOIN (
            SELECT record_id, MAX(created_at) AS max_created_at
            FROM raw_inputs
            WHERE record_id IN (:recordIds)
            GROUP BY record_id
        ) latest
        ON ri.record_id = latest.record_id
        AND ri.created_at = latest.max_created_at
        """
    )
    suspend fun getLatestByRecordIds(recordIds: List<Long>): List<RawInputEntity>

    @Query("DELETE FROM raw_inputs WHERE record_id = :recordId")
    suspend fun deleteByRecordId(recordId: Long)

}
