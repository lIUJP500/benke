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

    @Query("DELETE FROM raw_inputs WHERE record_id = :recordId")
    suspend fun deleteByRecordId(recordId: Long)

}
