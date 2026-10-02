package com.sortfold.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SortJobEntity::class, MoveLogEntity::class, ErrorEntity::class, AutoRuleEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class SortfoldDatabase : RoomDatabase() {
    abstract fun sortJobDao(): SortJobDao
    abstract fun moveLogDao(): MoveLogDao
    abstract fun errorDao(): ErrorDao
    abstract fun autoRuleDao(): AutoRuleDao

    companion object {
        fun build(context: Context): SortfoldDatabase =
            Room.databaseBuilder(context, SortfoldDatabase::class.java, "sortfold.db")
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()
    }
}
