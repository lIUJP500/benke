package com.bitcat.accountbook.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.bitcat.accountbook.data.entity.RecordEntity
import com.bitcat.accountbook.data.entity.RecordTagCrossRef
import com.bitcat.accountbook.data.model.RecordWithTags
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {

    @Insert
    suspend fun insert(record: RecordEntity): Long
    @Insert
    suspend fun insertCrossRef(crossRef: RecordTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRecordTags(items: List<RecordTagCrossRef>)

    @Transaction
    suspend fun replaceTagsForRecord(recordId: Long, tagIds: List<Long>) {
        deleteTagsByRecordId(recordId)
        if (tagIds.isNotEmpty()) {
            insertRecordTags(tagIds.map { tid -> RecordTagCrossRef(recordId, tid) })
        }
    }

    @Transaction
    suspend fun insertRecordWithTags(
        record: RecordEntity,
        tagIds: List<Long>
    ) :Long{
        val recordId = insert(record)

        tagIds.forEach { tagId ->
            insertCrossRef(
                RecordTagCrossRef(
                    recordId = recordId,
                    tagId = tagId
                )
            )
        }
        return recordId
    }


    // 范围内总额（BudgetPill）
    @Query(
        "SELECT COALESCE(SUM(amount), 0) FROM records " +
                "WHERE occurred_at BETWEEN :startMillis AND :endMillis"
    )
    fun observeSumInRange(startMillis: Long, endMillis: Long): Flow<Double>

    // 记录列表：按条件过滤（时间+金额）
    @Query(
        """
        SELECT * FROM records
        WHERE occurred_at BETWEEN :startMillis AND :endMillis
          AND (:minAmount IS NULL OR amount >= :minAmount)
          AND (:maxAmount IS NULL OR amount <= :maxAmount)
        ORDER BY occurred_at DESC
        """
    )
    fun observeRecordsFiltered(
        startMillis: Long,
        endMillis: Long,
        minAmount: Double?,
        maxAmount: Double?
    ): Flow<List<RecordEntity>>

    //  同条件总额（列表页底部显示）
    @Query(
        """
        SELECT COALESCE(SUM(amount), 0) FROM records
        WHERE occurred_at BETWEEN :startMillis AND :endMillis
          AND (:minAmount IS NULL OR amount >= :minAmount)
          AND (:maxAmount IS NULL OR amount <= :maxAmount)
        """
    )
    fun observeTotalFiltered(
        startMillis: Long,
        endMillis: Long,
        minAmount: Double?,
        maxAmount: Double?
    ): Flow<Double>

    @Transaction
    @Query("""
        SELECT * FROM records
        WHERE occurred_at BETWEEN :startMillis AND :endMillis
        AND (:minAmount IS NULL OR amount >= :minAmount)
        AND (:maxAmount IS NULL OR amount <= :maxAmount)
        AND (
            :tagId IS NULL OR id IN (
                SELECT record_id FROM record_tag WHERE tag_id = :tagId
            )
        )
        ORDER BY occurred_at DESC
    """)
    fun observeRecordsWithTagsFiltered(
        startMillis: Long,
        endMillis: Long,
        minAmount: Double?,
        maxAmount: Double?,
        tagId: Long?
    ): kotlinx.coroutines.flow.Flow<List<com.bitcat.accountbook.data.model.RecordWithTags>>

    @Query("""
        SELECT COALESCE(SUM(amount), 0) FROM records
        WHERE occurred_at BETWEEN :startMillis AND :endMillis
        AND (:minAmount IS NULL OR amount >= :minAmount)
        AND (:maxAmount IS NULL OR amount <= :maxAmount)
        AND (
            :tagId IS NULL OR id IN (
                SELECT record_id FROM record_tag WHERE tag_id = :tagId
            )
        )
    """)
    fun observeTotalWithTagFiltered(
        startMillis: Long,
        endMillis: Long,
        minAmount: Double?,
        maxAmount: Double?,
        tagId: Long?
    ): kotlinx.coroutines.flow.Flow<Double>

    // 1) 按天汇总（用于 WEEK / MONTH 折线）
    @androidx.room.Query("""
    SELECT 
      strftime('%Y-%m-%d', datetime(occurred_at/1000, 'unixepoch', 'localtime')) AS day,
      COALESCE(SUM(amount), 0) AS total
    FROM records
    WHERE occurred_at BETWEEN :startMillis AND :endMillis
    GROUP BY day
    ORDER BY day ASC
""")
    fun observeDailyTotals(
        startMillis: Long,
        endMillis: Long
    ): kotlinx.coroutines.flow.Flow<List<com.bitcat.accountbook.data.model.DayTotal>>

    // 2) 按周汇总（用于 TAG 模式的“近12周趋势”）
    @androidx.room.Query("""
    SELECT 
      strftime('%Y-%W', datetime(occurred_at/1000, 'unixepoch', 'localtime')) AS yw,
      COALESCE(SUM(amount), 0) AS total
    FROM records
    WHERE occurred_at BETWEEN :startMillis AND :endMillis
    GROUP BY yw
    ORDER BY yw ASC
""")
    fun observeWeeklyTotals(
        startMillis: Long,
        endMillis: Long
    ): kotlinx.coroutines.flow.Flow<List<com.bitcat.accountbook.data.model.WeekTotal>>

    // 3) 某个标签：按周汇总（TAG 模式折线）
    @androidx.room.Query("""
    SELECT 
      strftime('%Y-%W', datetime(r.occurred_at/1000, 'unixepoch', 'localtime')) AS yw,
      COALESCE(SUM(r.amount), 0) AS total
    FROM records r
    JOIN record_tag rt ON rt.record_id = r.id
    WHERE r.occurred_at BETWEEN :startMillis AND :endMillis
      AND rt.tag_id = :tagId
    GROUP BY yw
    ORDER BY yw ASC
""")
    fun observeWeeklyTotalsByTag(
        startMillis: Long,
        endMillis: Long,
        tagId: Long
    ): kotlinx.coroutines.flow.Flow<List<com.bitcat.accountbook.data.model.WeekTotal>>

    // 4) 标签饼图：按标签汇总（某个时间范围）
    @androidx.room.Query("""
    SELECT 
      t.id AS tagId,
      t.name AS tagName,
      COALESCE(SUM(r.amount), 0) AS total
    FROM tags t
    JOIN record_tag rt ON rt.tag_id = t.id
    JOIN records r ON r.id = rt.record_id
    WHERE r.occurred_at BETWEEN :startMillis AND :endMillis
    GROUP BY t.id, t.name
    HAVING total > 0
    ORDER BY total DESC
""")
    fun observeTagTotalsInRange(
        startMillis: Long,
        endMillis: Long
    ): kotlinx.coroutines.flow.Flow<List<com.bitcat.accountbook.data.model.TagTotal>>



    @Query("DELETE FROM records WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM records WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RecordEntity?

    @Update
    suspend fun updateRecord(record: RecordEntity)
    @Query("DELETE FROM record_tag WHERE record_id = :recordId")
    suspend fun deleteTagsByRecordId(recordId: Long)

    @Transaction
    @Query("SELECT * FROM records WHERE id = :recordId LIMIT 1")
    fun observeRecordWithTags(recordId: Long): Flow<RecordWithTags?>

}
