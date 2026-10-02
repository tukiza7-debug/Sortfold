package com.sortfold.app.data.db;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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
public final class SortJobDao_Impl implements SortJobDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<SortJobEntity> __insertionAdapterOfSortJobEntity;

  private final EntityDeletionOrUpdateAdapter<SortJobEntity> __updateAdapterOfSortJobEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateProgress;

  private final SharedSQLiteStatement __preparedStmtOfDeleteOlderThan;

  public SortJobDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfSortJobEntity = new EntityInsertionAdapter<SortJobEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `sort_jobs` (`id`,`treeUri`,`destTreeUri`,`modesCsv`,`duplicatePolicy`,`status`,`totalFiles`,`doneFiles`,`totalBytes`,`doneBytes`,`createdAt`,`updatedAt`,`isAuto`,`message`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SortJobEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTreeUri());
        statement.bindString(3, entity.getDestTreeUri());
        statement.bindString(4, entity.getModesCsv());
        statement.bindString(5, entity.getDuplicatePolicy());
        statement.bindString(6, entity.getStatus());
        statement.bindLong(7, entity.getTotalFiles());
        statement.bindLong(8, entity.getDoneFiles());
        statement.bindLong(9, entity.getTotalBytes());
        statement.bindLong(10, entity.getDoneBytes());
        statement.bindLong(11, entity.getCreatedAt());
        statement.bindLong(12, entity.getUpdatedAt());
        final int _tmp = entity.isAuto() ? 1 : 0;
        statement.bindLong(13, _tmp);
        if (entity.getMessage() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getMessage());
        }
      }
    };
    this.__updateAdapterOfSortJobEntity = new EntityDeletionOrUpdateAdapter<SortJobEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `sort_jobs` SET `id` = ?,`treeUri` = ?,`destTreeUri` = ?,`modesCsv` = ?,`duplicatePolicy` = ?,`status` = ?,`totalFiles` = ?,`doneFiles` = ?,`totalBytes` = ?,`doneBytes` = ?,`createdAt` = ?,`updatedAt` = ?,`isAuto` = ?,`message` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final SortJobEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindString(2, entity.getTreeUri());
        statement.bindString(3, entity.getDestTreeUri());
        statement.bindString(4, entity.getModesCsv());
        statement.bindString(5, entity.getDuplicatePolicy());
        statement.bindString(6, entity.getStatus());
        statement.bindLong(7, entity.getTotalFiles());
        statement.bindLong(8, entity.getDoneFiles());
        statement.bindLong(9, entity.getTotalBytes());
        statement.bindLong(10, entity.getDoneBytes());
        statement.bindLong(11, entity.getCreatedAt());
        statement.bindLong(12, entity.getUpdatedAt());
        final int _tmp = entity.isAuto() ? 1 : 0;
        statement.bindLong(13, _tmp);
        if (entity.getMessage() == null) {
          statement.bindNull(14);
        } else {
          statement.bindString(14, entity.getMessage());
        }
        statement.bindLong(15, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateProgress = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE sort_jobs SET status = ?, doneFiles = ?, doneBytes = ?, updatedAt = ?, message = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM sort_jobs WHERE createdAt < ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final SortJobEntity job, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfSortJobEntity.insertAndReturnId(job);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final SortJobEntity job, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfSortJobEntity.handle(job);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateProgress(final long id, final String status, final int doneFiles,
      final long doneBytes, final long updatedAt, final String message,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateProgress.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, doneFiles);
        _argIndex = 3;
        _stmt.bindLong(_argIndex, doneBytes);
        _argIndex = 4;
        _stmt.bindLong(_argIndex, updatedAt);
        _argIndex = 5;
        if (message == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, message);
        }
        _argIndex = 6;
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
          __preparedStmtOfUpdateProgress.release(_stmt);
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
  public Object byId(final long id, final Continuation<? super SortJobEntity> $completion) {
    final String _sql = "SELECT * FROM sort_jobs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<SortJobEntity>() {
      @Override
      @Nullable
      public SortJobEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "treeUri");
          final int _cursorIndexOfDestTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "destTreeUri");
          final int _cursorIndexOfModesCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "modesCsv");
          final int _cursorIndexOfDuplicatePolicy = CursorUtil.getColumnIndexOrThrow(_cursor, "duplicatePolicy");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTotalFiles = CursorUtil.getColumnIndexOrThrow(_cursor, "totalFiles");
          final int _cursorIndexOfDoneFiles = CursorUtil.getColumnIndexOrThrow(_cursor, "doneFiles");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDoneBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "doneBytes");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfIsAuto = CursorUtil.getColumnIndexOrThrow(_cursor, "isAuto");
          final int _cursorIndexOfMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "message");
          final SortJobEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTreeUri;
            _tmpTreeUri = _cursor.getString(_cursorIndexOfTreeUri);
            final String _tmpDestTreeUri;
            _tmpDestTreeUri = _cursor.getString(_cursorIndexOfDestTreeUri);
            final String _tmpModesCsv;
            _tmpModesCsv = _cursor.getString(_cursorIndexOfModesCsv);
            final String _tmpDuplicatePolicy;
            _tmpDuplicatePolicy = _cursor.getString(_cursorIndexOfDuplicatePolicy);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final int _tmpTotalFiles;
            _tmpTotalFiles = _cursor.getInt(_cursorIndexOfTotalFiles);
            final int _tmpDoneFiles;
            _tmpDoneFiles = _cursor.getInt(_cursorIndexOfDoneFiles);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDoneBytes;
            _tmpDoneBytes = _cursor.getLong(_cursorIndexOfDoneBytes);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final boolean _tmpIsAuto;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsAuto);
            _tmpIsAuto = _tmp != 0;
            final String _tmpMessage;
            if (_cursor.isNull(_cursorIndexOfMessage)) {
              _tmpMessage = null;
            } else {
              _tmpMessage = _cursor.getString(_cursorIndexOfMessage);
            }
            _result = new SortJobEntity(_tmpId,_tmpTreeUri,_tmpDestTreeUri,_tmpModesCsv,_tmpDuplicatePolicy,_tmpStatus,_tmpTotalFiles,_tmpDoneFiles,_tmpTotalBytes,_tmpDoneBytes,_tmpCreatedAt,_tmpUpdatedAt,_tmpIsAuto,_tmpMessage);
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
  public Flow<SortJobEntity> observeById(final long id) {
    final String _sql = "SELECT * FROM sort_jobs WHERE id = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, id);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sort_jobs"}, new Callable<SortJobEntity>() {
      @Override
      @Nullable
      public SortJobEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "treeUri");
          final int _cursorIndexOfDestTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "destTreeUri");
          final int _cursorIndexOfModesCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "modesCsv");
          final int _cursorIndexOfDuplicatePolicy = CursorUtil.getColumnIndexOrThrow(_cursor, "duplicatePolicy");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTotalFiles = CursorUtil.getColumnIndexOrThrow(_cursor, "totalFiles");
          final int _cursorIndexOfDoneFiles = CursorUtil.getColumnIndexOrThrow(_cursor, "doneFiles");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDoneBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "doneBytes");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfIsAuto = CursorUtil.getColumnIndexOrThrow(_cursor, "isAuto");
          final int _cursorIndexOfMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "message");
          final SortJobEntity _result;
          if (_cursor.moveToFirst()) {
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTreeUri;
            _tmpTreeUri = _cursor.getString(_cursorIndexOfTreeUri);
            final String _tmpDestTreeUri;
            _tmpDestTreeUri = _cursor.getString(_cursorIndexOfDestTreeUri);
            final String _tmpModesCsv;
            _tmpModesCsv = _cursor.getString(_cursorIndexOfModesCsv);
            final String _tmpDuplicatePolicy;
            _tmpDuplicatePolicy = _cursor.getString(_cursorIndexOfDuplicatePolicy);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final int _tmpTotalFiles;
            _tmpTotalFiles = _cursor.getInt(_cursorIndexOfTotalFiles);
            final int _tmpDoneFiles;
            _tmpDoneFiles = _cursor.getInt(_cursorIndexOfDoneFiles);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDoneBytes;
            _tmpDoneBytes = _cursor.getLong(_cursorIndexOfDoneBytes);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final boolean _tmpIsAuto;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsAuto);
            _tmpIsAuto = _tmp != 0;
            final String _tmpMessage;
            if (_cursor.isNull(_cursorIndexOfMessage)) {
              _tmpMessage = null;
            } else {
              _tmpMessage = _cursor.getString(_cursorIndexOfMessage);
            }
            _result = new SortJobEntity(_tmpId,_tmpTreeUri,_tmpDestTreeUri,_tmpModesCsv,_tmpDuplicatePolicy,_tmpStatus,_tmpTotalFiles,_tmpDoneFiles,_tmpTotalBytes,_tmpDoneBytes,_tmpCreatedAt,_tmpUpdatedAt,_tmpIsAuto,_tmpMessage);
          } else {
            _result = null;
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
  public Flow<List<SortJobEntity>> observeRecent(final int limit) {
    final String _sql = "SELECT * FROM sort_jobs ORDER BY createdAt DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"sort_jobs"}, new Callable<List<SortJobEntity>>() {
      @Override
      @NonNull
      public List<SortJobEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "treeUri");
          final int _cursorIndexOfDestTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "destTreeUri");
          final int _cursorIndexOfModesCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "modesCsv");
          final int _cursorIndexOfDuplicatePolicy = CursorUtil.getColumnIndexOrThrow(_cursor, "duplicatePolicy");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTotalFiles = CursorUtil.getColumnIndexOrThrow(_cursor, "totalFiles");
          final int _cursorIndexOfDoneFiles = CursorUtil.getColumnIndexOrThrow(_cursor, "doneFiles");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDoneBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "doneBytes");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfIsAuto = CursorUtil.getColumnIndexOrThrow(_cursor, "isAuto");
          final int _cursorIndexOfMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "message");
          final List<SortJobEntity> _result = new ArrayList<SortJobEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SortJobEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTreeUri;
            _tmpTreeUri = _cursor.getString(_cursorIndexOfTreeUri);
            final String _tmpDestTreeUri;
            _tmpDestTreeUri = _cursor.getString(_cursorIndexOfDestTreeUri);
            final String _tmpModesCsv;
            _tmpModesCsv = _cursor.getString(_cursorIndexOfModesCsv);
            final String _tmpDuplicatePolicy;
            _tmpDuplicatePolicy = _cursor.getString(_cursorIndexOfDuplicatePolicy);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final int _tmpTotalFiles;
            _tmpTotalFiles = _cursor.getInt(_cursorIndexOfTotalFiles);
            final int _tmpDoneFiles;
            _tmpDoneFiles = _cursor.getInt(_cursorIndexOfDoneFiles);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDoneBytes;
            _tmpDoneBytes = _cursor.getLong(_cursorIndexOfDoneBytes);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final boolean _tmpIsAuto;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsAuto);
            _tmpIsAuto = _tmp != 0;
            final String _tmpMessage;
            if (_cursor.isNull(_cursorIndexOfMessage)) {
              _tmpMessage = null;
            } else {
              _tmpMessage = _cursor.getString(_cursorIndexOfMessage);
            }
            _item = new SortJobEntity(_tmpId,_tmpTreeUri,_tmpDestTreeUri,_tmpModesCsv,_tmpDuplicatePolicy,_tmpStatus,_tmpTotalFiles,_tmpDoneFiles,_tmpTotalBytes,_tmpDoneBytes,_tmpCreatedAt,_tmpUpdatedAt,_tmpIsAuto,_tmpMessage);
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
  public Object recent(final int limit,
      final Continuation<? super List<SortJobEntity>> $completion) {
    final String _sql = "SELECT * FROM sort_jobs ORDER BY createdAt DESC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, limit);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<SortJobEntity>>() {
      @Override
      @NonNull
      public List<SortJobEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "treeUri");
          final int _cursorIndexOfDestTreeUri = CursorUtil.getColumnIndexOrThrow(_cursor, "destTreeUri");
          final int _cursorIndexOfModesCsv = CursorUtil.getColumnIndexOrThrow(_cursor, "modesCsv");
          final int _cursorIndexOfDuplicatePolicy = CursorUtil.getColumnIndexOrThrow(_cursor, "duplicatePolicy");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTotalFiles = CursorUtil.getColumnIndexOrThrow(_cursor, "totalFiles");
          final int _cursorIndexOfDoneFiles = CursorUtil.getColumnIndexOrThrow(_cursor, "doneFiles");
          final int _cursorIndexOfTotalBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "totalBytes");
          final int _cursorIndexOfDoneBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "doneBytes");
          final int _cursorIndexOfCreatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "createdAt");
          final int _cursorIndexOfUpdatedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "updatedAt");
          final int _cursorIndexOfIsAuto = CursorUtil.getColumnIndexOrThrow(_cursor, "isAuto");
          final int _cursorIndexOfMessage = CursorUtil.getColumnIndexOrThrow(_cursor, "message");
          final List<SortJobEntity> _result = new ArrayList<SortJobEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final SortJobEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final String _tmpTreeUri;
            _tmpTreeUri = _cursor.getString(_cursorIndexOfTreeUri);
            final String _tmpDestTreeUri;
            _tmpDestTreeUri = _cursor.getString(_cursorIndexOfDestTreeUri);
            final String _tmpModesCsv;
            _tmpModesCsv = _cursor.getString(_cursorIndexOfModesCsv);
            final String _tmpDuplicatePolicy;
            _tmpDuplicatePolicy = _cursor.getString(_cursorIndexOfDuplicatePolicy);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final int _tmpTotalFiles;
            _tmpTotalFiles = _cursor.getInt(_cursorIndexOfTotalFiles);
            final int _tmpDoneFiles;
            _tmpDoneFiles = _cursor.getInt(_cursorIndexOfDoneFiles);
            final long _tmpTotalBytes;
            _tmpTotalBytes = _cursor.getLong(_cursorIndexOfTotalBytes);
            final long _tmpDoneBytes;
            _tmpDoneBytes = _cursor.getLong(_cursorIndexOfDoneBytes);
            final long _tmpCreatedAt;
            _tmpCreatedAt = _cursor.getLong(_cursorIndexOfCreatedAt);
            final long _tmpUpdatedAt;
            _tmpUpdatedAt = _cursor.getLong(_cursorIndexOfUpdatedAt);
            final boolean _tmpIsAuto;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsAuto);
            _tmpIsAuto = _tmp != 0;
            final String _tmpMessage;
            if (_cursor.isNull(_cursorIndexOfMessage)) {
              _tmpMessage = null;
            } else {
              _tmpMessage = _cursor.getString(_cursorIndexOfMessage);
            }
            _item = new SortJobEntity(_tmpId,_tmpTreeUri,_tmpDestTreeUri,_tmpModesCsv,_tmpDuplicatePolicy,_tmpStatus,_tmpTotalFiles,_tmpDoneFiles,_tmpTotalBytes,_tmpDoneBytes,_tmpCreatedAt,_tmpUpdatedAt,_tmpIsAuto,_tmpMessage);
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
