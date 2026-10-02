package com.sortfold.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SortJobEntity::class, MoveLogEntity::class, ErrorEntity::class, AutoRuleEntity::class],
    version = 2,
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
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

        /** v2: error reports carry versionCode and buildId for release triage. */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE error_reports ADD COLUMN versionCode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE error_reports ADD COLUMN buildId TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
