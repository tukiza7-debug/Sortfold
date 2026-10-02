package com.sortfold.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Integer;
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
public final class ErrorDao_Impl implements ErrorDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ErrorEntity> __insertionAdapterOfErrorEntity;

  private final SharedSQLiteStatement __preparedStmtOfDelete;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public ErrorDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfErrorEntity = new EntityInsertionAdapter<ErrorEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `error_reports` (`id`,`timestamp`,`module`,`severity`,`type`,`message`,`stackTrace`,`jobId`,`appVersion`,`androidVersion`,`deviceModel`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ErrorEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getTimestamp());
        statement.bindString(3, entity.getModule());
        statement.bindString(4, entity.getSeverity());
        statement.bindString(5, entity.getType());
        statement.bindString(6, entity.getMessage());
        if (entity.getStackTrace() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getStackTrace());
        }
        if (entity.getJobId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindLong(8, entity.getJobId());
        }
        statement.bindString(9, entity.getAppVersion());
        statement.bindString(10, entity.getAndroidVersion());
        statement.bindString(11, entity.getDeviceModel());
      }
    };
    this.__preparedStmtOfDelete = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM error_reports WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM error_reports WHERE timestamp < ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM error_reports";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final ErrorEntity error, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfErrorEntity.insertAndReturnId(error);
          __db.setTransactionSuccessful();
          return _result;
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
  public Object deleteOlderThan(final long cutoff,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteOlderThan.acquire();
        int _argIndex = 1;
        _stmt.bindLong(_argIndex, cutoff);
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfDeleteOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
        try {
          __db.beginTransaction();
          try {
            final Integer _result = _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return _result;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ErrorEntity>> observeAll() {
    final String _sql = "SELECT * FROM error_reports ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"error_reports"}, new Callable<List<ErrorEntity>>() {
      @Override
      @NonNull
      public List<ErrorEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfModule = CursorUtil.getColumnIndexOrThrow(_cursor, "module");
          final int _cursorIndexOfSeverity = CursorUtil.getColumnIndexOrThrow(_cursor, "severity");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "message");
          final int _cursorIndexOfStackTrace = CursorUtil.getColumnIndexOrThrow(_cursor, "stackTrace");
          final int _cursorIndexOfJobId = CursorUtil.getColumnIndexOrThrow(_cursor, "jobId");
          final int _cursorIndexOfAppVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "appVersion");
          final int _cursorIndexOfAndroidVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "androidVersion");
          final int _cursorIndexOfDeviceModel = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceModel");
          final List<ErrorEntity> _result = new ArrayList<ErrorEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ErrorEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpModule;
            _tmpModule = _cursor.getString(_cursorIndexOfModule);
            final String _tmpSeverity;
            _tmpSeverity = _cursor.getString(_cursorIndexOfSeverity);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpMessage;
            _tmpMessage = _cursor.getString(_cursorIndexOfMessage);
            final String _tmpStackTrace;
            if (_cursor.isNull(_cursorIndexOfStackTrace)) {
              _tmpStackTrace = null;
            } else {
              _tmpStackTrace = _cursor.getString(_cursorIndexOfStackTrace);
            }
            final Long _tmpJobId;
            if (_cursor.isNull(_cursorIndexOfJobId)) {
              _tmpJobId = null;
            } else {
              _tmpJobId = _cursor.getLong(_cursorIndexOfJobId);
            }
            final String _tmpAppVersion;
            _tmpAppVersion = _cursor.getString(_cursorIndexOfAppVersion);
            final String _tmpAndroidVersion;
            _tmpAndroidVersion = _cursor.getString(_cursorIndexOfAndroidVersion);
            final String _tmpDeviceModel;
            _tmpDeviceModel = _cursor.getString(_cursorIndexOfDeviceModel);
            _item = new ErrorEntity(_tmpId,_tmpTimestamp,_tmpModule,_tmpSeverity,_tmpType,_tmpMessage,_tmpStackTrace,_tmpJobId,_tmpAppVersion,_tmpAndroidVersion,_tmpDeviceModel);
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
  public Object byId(final long id, final Continuation<? super ErrorEntity> $completion) {
    final String _sql = "SELECT * FROM error_reports WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ErrorEntity>() {
      @Override
      @Nullable
      public ErrorEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfModule = CursorUtil.getColumnIndexOrThrow(_cursor, "module");
          final int _cursorIndexOfSeverity = CursorUtil.getColumnIndexOrThrow(_cursor, "severity");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "message");
          final int _cursorIndexOfStackTrace = CursorUtil.getColumnIndexOrThrow(_cursor, "stackTrace");
          final int _cursorIndexOfJobId = CursorUtil.getColumnIndexOrThrow(_cursor, "jobId");
          final int _cursorIndexOfAppVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "appVersion");
          final int _cursorIndexOfAndroidVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "androidVersion");
          final int _cursorIndexOfDeviceModel = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceModel");
          final ErrorEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpModule;
            _tmpModule = _cursor.getString(_cursorIndexOfModule);
            final String _tmpSeverity;
            _tmpSeverity = _cursor.getString(_cursorIndexOfSeverity);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpMessage;
            _tmpMessage = _cursor.getString(_cursorIndexOfMessage);
            final String _tmpStackTrace;
            if (_cursor.isNull(_cursorIndexOfStackTrace)) {
              _tmpStackTrace = null;
            } else {
              _tmpStackTrace = _cursor.getString(_cursorIndexOfStackTrace);
            }
            final Long _tmpJobId;
            if (_cursor.isNull(_cursorIndexOfJobId)) {
              _tmpJobId = null;
            } else {
              _tmpJobId = _cursor.getLong(_cursorIndexOfJobId);
            }
            final String _tmpAppVersion;
            _tmpAppVersion = _cursor.getString(_cursorIndexOfAppVersion);
            final String _tmpAndroidVersion;
            _tmpAndroidVersion = _cursor.getString(_cursorIndexOfAndroidVersion);
            final String _tmpDeviceModel;
            _tmpDeviceModel = _cursor.getString(_cursorIndexOfDeviceModel);
            _result = new ErrorEntity(_tmpId,_tmpTimestamp,_tmpModule,_tmpSeverity,_tmpType,_tmpMessage,_tmpStackTrace,_tmpJobId,_tmpAppVersion,_tmpAndroidVersion,_tmpDeviceModel);
          } else {
            _result = null;
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @Override
  public Object since(final long since, final Continuation<? super List<ErrorEntity>> $completion) {
    final String _sql = "SELECT * FROM error_reports WHERE timestamp >= ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, since);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<ErrorEntity>>() {
      @Override
      @NonNull
      public List<ErrorEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfModule = CursorUtil.getColumnIndexOrThrow(_cursor, "module");
          final int _cursorIndexOfSeverity = CursorUtil.getColumnIndexOrThrow(_cursor, "severity");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "message");
          final int _cursorIndexOfStackTrace = CursorUtil.getColumnIndexOrThrow(_cursor, "stackTrace");
          final int _cursorIndexOfJobId = CursorUtil.getColumnIndexOrThrow(_cursor, "jobId");
          final int _cursorIndexOfAppVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "appVersion");
          final int _cursorIndexOfAndroidVersion = CursorUtil.getColumnIndexOrThrow(_cursor, "androidVersion");
          final int _cursorIndexOfDeviceModel = CursorUtil.getColumnIndexOrThrow(_cursor, "deviceModel");
          final List<ErrorEntity> _result = new ArrayList<ErrorEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ErrorEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpModule;
            _tmpModule = _cursor.getString(_cursorIndexOfModule);
            final String _tmpSeverity;
            _tmpSeverity = _cursor.getString(_cursorIndexOfSeverity);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpMessage;
            _tmpMessage = _cursor.getString(_cursorIndexOfMessage);
            final String _tmpStackTrace;
            if (_cursor.isNull(_cursorIndexOfStackTrace)) {
              _tmpStackTrace = null;
            } else {
              _tmpStackTrace = _cursor.getString(_cursorIndexOfStackTrace);
            }
            final Long _tmpJobId;
            if (_cursor.isNull(_cursorIndexOfJobId)) {
              _tmpJobId = null;
            } else {
              _tmpJobId = _cursor.getLong(_cursorIndexOfJobId);
            }
            final String _tmpAppVersion;
            _tmpAppVersion = _cursor.getString(_cursorIndexOfAppVersion);
            final String _tmpAndroidVersion;
            _tmpAndroidVersion = _cursor.getString(_cursorIndexOfAndroidVersion);
            final String _tmpDeviceModel;
            _tmpDeviceModel = _cursor.getString(_cursorIndexOfDeviceModel);
            _item = new ErrorEntity(_tmpId,_tmpTimestamp,_tmpModule,_tmpSeverity,_tmpType,_tmpMessage,_tmpStackTrace,_tmpJobId,_tmpAppVersion,_tmpAndroidVersion,_tmpDeviceModel);
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

  @Override
  public Object count(final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM error_reports";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final Integer _result;
          if (_cursor.moveToFirst()) {
            final int _tmp;
            _tmp = _cursor.getInt(0);
            _result = _tmp;
          } else {
            _result = 0;
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
