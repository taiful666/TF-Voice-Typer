package com.taiful.tfvoicetyper.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "records", indices = [Index("createdAt")])
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val durationMs: Long = 0,
    val bangla: String = "",
    val english: String = "",
    val status: Int = STATUS_RECORDING,
    val error: String? = null,
    /** Folder holding this recording's audio parts ("" once deleted). */
    val audioDir: String = "",
    val model: String = "",
    val updatedAt: Long = createdAt,
) {
    val isDone get() = status == STATUS_DONE
    val isFailed get() = status == STATUS_FAILED

    companion object {
        const val STATUS_RECORDING = 0
        const val STATUS_PROCESSING = 1
        const val STATUS_DONE = 2
        const val STATUS_FAILED = 3
    }
}
