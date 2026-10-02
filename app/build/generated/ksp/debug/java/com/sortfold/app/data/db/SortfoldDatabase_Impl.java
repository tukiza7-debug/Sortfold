package com.sortfold.app.data.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class SortfoldDatabase_Impl extends SortfoldDatabase {
  private volatile SortJobDao _sortJobDao;

  private volatile MoveLogDao _moveLogDao;

  private volatile ErrorDao _errorDao;

  private volatile AutoRuleDao _autoRuleDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `sort_jobs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `treeUri` TEXT NOT NULL, `destTreeUri` TEXT NOT NULL, `modesCsv` TEXT NOT NULL, `duplicatePolicy` TEXT NOT NULL, `status` TEXT NOT NULL, `totalFiles` INTEGER NOT NULL, `doneFiles` INTEGER NOT NULL, `totalBytes` INTEGER NOT NULL, `doneBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isAuto` INTEGER NOT NULL, `message` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `move_logs` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `jobId` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `sourceDocId` TEXT NOT NULL, `displayName` TEXT NOT NULL, `mime` TEXT, `destFolder` TEXT NOT NULL, `destDocId` TEXT, `destName` TEXT NOT NULL, `sizeBytes` INTEGER NOT NULL, `status` TEXT NOT NULL, `detail` TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `error_reports` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `timestamp` INTEGER NOT NULL, `module` TEXT NOT NULL, `severity` TEXT NOT NULL, `type` TEXT NOT NULL, `message` TEXT NOT NULL, `stackTrace` TEXT, `jobId` INTEGER, `appVersion` TEXT NOT NULL, `androidVersion` TEXT NOT NULL, `deviceModel` TEXT NOT NULL)");
        db.execSQL("CREATE TABLE IF NOT EXISTS `auto_rules` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `treeUri` TEXT NOT NULL, `modesCsv` TEXT NOT NULL, `dateGranularity` TEXT NOT NULL, `duplicatePolicy` TEXT NOT NULL, `enabled` INTEGER NOT NULL, `lastRunAt` INTEGER)");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '7d1e73917be985e1f6f5afb7959c8a87')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `sort_jobs`");
        db.execSQL("DROP TABLE IF EXISTS `move_logs`");
        db.execSQL("DROP TABLE IF EXISTS `error_reports`");
        db.execSQL("DROP TABLE IF EXISTS `auto_rules`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsSortJobs = new HashMap<String, TableInfo.Column>(14);
        _columnsSortJobs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("treeUri", new TableInfo.Column("treeUri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("destTreeUri", new TableInfo.Column("destTreeUri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("modesCsv", new TableInfo.Column("modesCsv", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("duplicatePolicy", new TableInfo.Column("duplicatePolicy", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("totalFiles", new TableInfo.Column("totalFiles", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("doneFiles", new TableInfo.Column("doneFiles", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("totalBytes", new TableInfo.Column("totalBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("doneBytes", new TableInfo.Column("doneBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("updatedAt", new TableInfo.Column("updatedAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("isAuto", new TableInfo.Column("isAuto", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsSortJobs.put("message", new TableInfo.Column("message", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysSortJobs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesSortJobs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoSortJobs = new TableInfo("sort_jobs", _columnsSortJobs, _foreignKeysSortJobs, _indicesSortJobs);
        final TableInfo _existingSortJobs = TableInfo.read(db, "sort_jobs");
        if (!_infoSortJobs.equals(_existingSortJobs)) {
          return new RoomOpenHelper.ValidationResult(false, "sort_jobs(com.sortfold.app.data.db.SortJobEntity).\n"
                  + " Expected:\n" + _infoSortJobs + "\n"
                  + " Found:\n" + _existingSortJobs);
        }
        final HashMap<String, TableInfo.Column> _columnsMoveLogs = new HashMap<String, TableInfo.Column>(12);
        _columnsMoveLogs.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("jobId", new TableInfo.Column("jobId", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("seq", new TableInfo.Column("seq", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("sourceDocId", new TableInfo.Column("sourceDocId", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("displayName", new TableInfo.Column("displayName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("mime", new TableInfo.Column("mime", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("destFolder", new TableInfo.Column("destFolder", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("destDocId", new TableInfo.Column("destDocId", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("destName", new TableInfo.Column("destName", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("sizeBytes", new TableInfo.Column("sizeBytes", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsMoveLogs.put("detail", new TableInfo.Column("detail", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysMoveLogs = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesMoveLogs = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoMoveLogs = new TableInfo("move_logs", _columnsMoveLogs, _foreignKeysMoveLogs, _indicesMoveLogs);
        final TableInfo _existingMoveLogs = TableInfo.read(db, "move_logs");
        if (!_infoMoveLogs.equals(_existingMoveLogs)) {
          return new RoomOpenHelper.ValidationResult(false, "move_logs(com.sortfold.app.data.db.MoveLogEntity).\n"
                  + " Expected:\n" + _infoMoveLogs + "\n"
                  + " Found:\n" + _existingMoveLogs);
        }
        final HashMap<String, TableInfo.Column> _columnsErrorReports = new HashMap<String, TableInfo.Column>(11);
        _columnsErrorReports.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("module", new TableInfo.Column("module", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("severity", new TableInfo.Column("severity", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("message", new TableInfo.Column("message", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("stackTrace", new TableInfo.Column("stackTrace", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("jobId", new TableInfo.Column("jobId", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("appVersion", new TableInfo.Column("appVersion", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("androidVersion", new TableInfo.Column("androidVersion", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsErrorReports.put("deviceModel", new TableInfo.Column("deviceModel", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysErrorReports = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesErrorReports = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoErrorReports = new TableInfo("error_reports", _columnsErrorReports, _foreignKeysErrorReports, _indicesErrorReports);
        final TableInfo _existingErrorReports = TableInfo.read(db, "error_reports");
        if (!_infoErrorReports.equals(_existingErrorReports)) {
          return new RoomOpenHelper.ValidationResult(false, "error_reports(com.sortfold.app.data.db.ErrorEntity).\n"
                  + " Expected:\n" + _infoErrorReports + "\n"
                  + " Found:\n" + _existingErrorReports);
        }
        final HashMap<String, TableInfo.Column> _columnsAutoRules = new HashMap<String, TableInfo.Column>(8);
        _columnsAutoRules.put("id", new TableInfo.Column("id", "INTEGER", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutoRules.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutoRules.put("treeUri", new TableInfo.Column("treeUri", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutoRules.put("modesCsv", new TableInfo.Column("modesCsv", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutoRules.put("dateGranularity", new TableInfo.Column("dateGranularity", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutoRules.put("duplicatePolicy", new TableInfo.Column("duplicatePolicy", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutoRules.put("enabled", new TableInfo.Column("enabled", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAutoRules.put("lastRunAt", new TableInfo.Column("lastRunAt", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAutoRules = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAutoRules = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAutoRules = new TableInfo("auto_rules", _columnsAutoRules, _foreignKeysAutoRules, _indicesAutoRules);
        final TableInfo _existingAutoRules = TableInfo.read(db, "auto_rules");
        if (!_infoAutoRules.equals(_existingAutoRules)) {
          return new RoomOpenHelper.ValidationResult(false, "auto_rules(com.sortfold.app.data.db.AutoRuleEntity).\n"
                  + " Expected:\n" + _infoAutoRules + "\n"
                  + " Found:\n" + _existingAutoRules);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "7d1e73917be985e1f6f5afb7959c8a87", "d41b111319b362319a7d9134183ebeed");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "sort_jobs","move_logs","error_reports","auto_rules");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `sort_jobs`");
      _db.execSQL("DELETE FROM `move_logs`");
      _db.execSQL("DELETE FROM `error_reports`");
      _db.execSQL("DELETE FROM `auto_rules`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(SortJobDao.class, SortJobDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(MoveLogDao.class, MoveLogDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ErrorDao.class, ErrorDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AutoRuleDao.class, AutoRuleDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public SortJobDao sortJobDao() {
    if (_sortJobDao != null) {
      return _sortJobDao;
    } else {
      synchronized(this) {
        if(_sortJobDao == null) {
          _sortJobDao = new SortJobDao_Impl(this);
        }
        return _sortJobDao;
      }
    }
  }

  @Override
  public MoveLogDao moveLogDao() {
    if (_moveLogDao != null) {
      return _moveLogDao;
    } else {
      synchronized(this) {
        if(_moveLogDao == null) {
          _moveLogDao = new MoveLogDao_Impl(this);
        }
        return _moveLogDao;
      }
    }
  }

  @Override
  public ErrorDao errorDao() {
    if (_errorDao != null) {
      return _errorDao;
    } else {
      synchronized(this) {
        if(_errorDao == null) {
          _errorDao = new ErrorDao_Impl(this);
        }
        return _errorDao;
      }
    }
  }

  @Override
  public AutoRuleDao autoRuleDao() {
    if (_autoRuleDao != null) {
      return _autoRuleDao;
    } else {
      synchronized(this) {
        if(_autoRuleDao == null) {
          _autoRuleDao = new AutoRuleDao_Impl(this);
        }
        return _autoRuleDao;
      }
    }
  }
}
