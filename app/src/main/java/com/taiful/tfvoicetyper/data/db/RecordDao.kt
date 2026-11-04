package com.taiful.tfvoicetyper.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE status != 0 ORDER BY createdAt DESC")
    fun all(): Flow<List<Record>>

    @Query(
        "SELECT * FROM records WHERE status != 0 AND (bangla LIKE '%' || :q || '%' OR english LIKE '%' || :q || '%') " +
            "ORDER BY createdAt DESC"
    )
    fun search(q: String): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE status = 2 ORDER BY createdAt DESC LIMIT 1")
    fun latestDone(): Flow<Record?>

    @Query("SELECT * FROM records WHERE id = :id")
    fun observe(id: Long): Flow<Record?>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun get(id: Long): Record?

    @Query("SELECT * FROM records WHERE status IN (:statuses)")
    suspend fun byStatus(statuses: List<Int>): List<Record>

    @Query("SELECT * FROM records WHERE audioDir != ''")
    suspend fun withAudio(): List<Record>

    @Query("SELECT * FROM records WHERE id IN (:ids)")
    suspend fun getMany(ids: List<Long>): List<Record>

    @Query("SELECT * FROM records")
    suspend fun everything(): List<Record>

    @Query("SELECT COUNT(*) FROM records WHERE status != 0")
    fun count(): Flow<Int>

    @Insert
    suspend fun insert(r: Record): Long

    @Update
    suspend fun update(r: Record)

    @Query("DELETE FROM records WHERE id IN (:ids)")
    suspend fun delete(ids: List<Long>)

    @Query("DELETE FROM records")
    suspend fun deleteAll()
}
