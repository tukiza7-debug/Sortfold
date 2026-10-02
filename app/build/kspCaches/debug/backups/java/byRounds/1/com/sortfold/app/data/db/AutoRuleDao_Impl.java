package com.sortfold.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityDeletionOrUpdateAdapter;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class AutoRuleDao_Impl implements AutoRuleDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<AutoRuleEntity> __insertionAdapterOfAutoRuleEntity;

  private final EntityDeletionOrUpdateAdapter<AutoRuleEntity> __updateAdapterOfAutoRuleEntity;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfSetLastRun;

  public AutoRuleDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfAutoRuleEntity = new EntityInsertionAdapter<AutoRuleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `auto_rules` (`id`,`name`,`treeUri`,`modesCsv`,`dateGranularity`,`duplicatePolicy`,`enabled`,`lastRunAt`) VALUES (nullif(?, 0),?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AutoRuleEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getTreeUri());
        statement.bindString(4, entity.getModesCsv());
        statement.bindString(5, entity.getDateGranularity());
        statement.bindString(6, entity.getDuplicatePolicy());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(7, _tmp);
        if (entity.getLastRunAt() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getLastRunAt());
        }
      }
    };
    this.__updateAdapterOfAutoRuleEntity = new EntityDeletionOrUpdateAdapter<AutoRuleEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `auto_rules` SET `id` = ?,`name` = ?,`treeUri` = ?,`modesCsv` = ?,`dateGranularity` = ?,`duplicatePolicy` = ?,`enabled` = ?,`lastRunAt` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final AutoRuleEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getName());
        statement.bindString(3, entity.getTreeUri());
        statement.bindString(4, entity.getModesCsv());
        statement.bindString(5, entity.getDateGranularity());
        statement.bindString(6, entity.getDuplicatePolicy());
        final int _tmp = entity.getEnabled() ? 1 : 0;
        statement.bindLong(7, _tmp);
        if (entity.getLastRunAt() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getLastRunAt());
        }
        statement.bindLong(9, entity.getId());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM auto_rules WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfSetLastRun = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE auto_rules SET lastRunAt = ? WHERE id = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final AutoRuleEntity rule, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfAutoRuleEntity.insertAndReturnId(rule);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final AutoRuleEntity rule, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfAutoRuleEntity.handle(rule);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object delete(final long id, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDelete.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDelete.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object setLastRun(final long id, final long runAt,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetLastRun.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, runAt);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, id);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetLastRun.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<AutoRuleEntity>> observeAll() {
    final String _sql = "SELECT * FROM auto_rules ORDER BY id DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"auto_rules"}, new Callable<List<AutoRuleEntity>>() {
      @Override
      @NonNull
      public List<AutoRuleEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "treeUri");
          final int _cursorIndexOfModesCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "modesCsv");
          final int _cursorIndexOfDateGranularity = CursorUtil.getColumnIndexOrThrow(_cursor, "dateGranularity");
          final int _cursorIndexOfDuplicatePolicy = CursorUtil.getColumnIndexOrThrow(_cursor, "duplicatePolicy");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfLastRunAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRunAt");
          final List<AutoRuleEntity> _result = new ArrayList<AutoRuleEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AutoRuleEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpTreeUri;
            _tmpTreeUri = _cursor.getString(_cursorIndexOfTreeUri);
            final String _tmpModesCsv;
            _tmpModesCsv = _cursor.getString(_cursorIndexOfModesCsv);
            final String _tmpDateGranularity;
            _tmpDateGranularity = _cursor.getString(_cursorIndexOfDateGranularity);
            final String _tmpDuplicatePolicy;
            _tmpDuplicatePolicy = _cursor.getString(_cursorIndexOfDuplicatePolicy);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final Long _tmpLastRunAt;
            if (_cursor.isNull(_cursorIndexOfLastRunAt)) {
              _tmpLastRunAt = null;
            } else {
              _tmpLastRunAt = _cursor.getLong(_cursorIndexOfLastRunAt);
            }
            _item = new AutoRuleEntity(_tmpId,_tmpName,_tmpTreeUri,_tmpModesCsv,_tmpDateGranularity,_tmpDuplicatePolicy,_tmpEnabled,_tmpLastRunAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object enabledRules(final Continuation<? super List<AutoRuleEntity>> $completion) {
    final String _sql = "SELECT * FROM auto_rules WHERE enabled = 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<AutoRuleEntity>>() {
      @Override
      @NonNull
      public List<AutoRuleEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "treeUri");
          final int _cursorIndexOfModesCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "modesCsv");
          final int _cursorIndexOfDateGranularity = CursorUtil.getColumnIndexOrThrow(_cursor, "dateGranularity");
          final int _cursorIndexOfDuplicatePolicy = CursorUtil.getColumnIndexOrThrow(_cursor, "duplicatePolicy");
          final int _cursorIndexOfEnabled = CursorUtil.getColumnIndexOrThrow(_cursor, "enabled");
          final int _cursorIndexOfLastRunAt = CursorUtil.getColumnIndexOrThrow(_cursor, "lastRunAt");
          final List<AutoRuleEntity> _result = new ArrayList<AutoRuleEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final AutoRuleEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final String _tmpTreeUri;
            _tmpTreeUri = _cursor.getString(_cursorIndexOfTreeUri);
            final String _tmpModesCsv;
            _tmpModesCsv = _cursor.getString(_cursorIndexOfModesCsv);
            final String _tmpDateGranularity;
            _tmpDateGranularity = _cursor.getString(_cursorIndexOfDateGranularity);
            final String _tmpDuplicatePolicy;
            _tmpDuplicatePolicy = _cursor.getString(_cursorIndexOfDuplicatePolicy);
            final boolean _tmpEnabled;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfEnabled);
            _tmpEnabled = _tmp != 0;
            final Long _tmpLastRunAt;
            if (_cursor.isNull(_cursorIndexOfLastRunAt)) {
              _tmpLastRunAt = null;
            } else {
              _tmpLastRunAt = _cursor.getLong(_cursorIndexOfLastRunAt);
            }
            _item = new AutoRuleEntity(_tmpId,_tmpName,_tmpTreeUri,_tmpModesCsv,_tmpDateGranularity,_tmpDuplicatePolicy,_tmpEnabled,_tmpLastRunAt);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
