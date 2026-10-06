package com.sortfold.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [SortJobEntity::class, MoveLogEntity::class, ErrorEntity::class, AutoRuleEntity::class],
    version = 3,
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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigrationOnDowngrade()
                .build()

        /** Fresh in-memory database for tests. */
        fun buildInMemory(context: Context): SortfoldDatabase =
            Room.inMemoryDatabaseBuilder(context, SortfoldDatabase::class.java).build()

        /** v2: error reports carry versionCode and buildId for release triage. */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE error_reports ADD COLUMN versionCode INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE error_reports ADD COLUMN buildId TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * v3 (1.2.0): capacity split support and the move_logs (jobId, status)
         * index the worker's paging relies on. Real migration — never
         * destructive for upgrades.
         */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sort_jobs ADD COLUMN capacityBytes INTEGER")
                db.execSQL("ALTER TABLE auto_rules ADD COLUMN capacityBytes INTEGER")
                db.execSQL("ALTER TABLE auto_rules ADD COLUMN capacityOrder TEXT NOT NULL DEFAULT 'SEQUENTIAL'")
                db.execSQL("ALTER TABLE auto_rules ADD COLUMN capacityPrefix TEXT NOT NULL DEFAULT 'Part'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_move_logs_jobId_status ON move_logs(jobId, status)")
            }
        }

        /** Test seam: DbMigrationTest opens a v2-shaped DB and runs this migration. */
        fun MIGRATION_2_3_FOR_TESTS(): androidx.room.migration.Migration = MIGRATION_2_3
    }
}
