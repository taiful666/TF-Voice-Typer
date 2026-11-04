package com.taiful.tfvoicetyper.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Record::class], version = 1, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun records(): RecordDao

    companion object {
        @Volatile private var inst: AppDatabase? = null
        fun get(context: Context): AppDatabase = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "tf_voice_typer.db")
                .build().also { inst = it }
        }
    }
}
