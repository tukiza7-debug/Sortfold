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
public final class MoveLogDao_Impl implements MoveLogDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<MoveLogEntity> __insertionAdapterOfMoveLogEntity;

  private final EntityDeletionOrUpdateAdapter<MoveLogEntity> __updateAdapterOfMoveLogEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  private final SharedSQLiteStatement __preparedStmtOfDeleteLogsForJobsOlderThan;

  private final SharedSQLiteStatement __preparedStmtOfClearAll;

  public MoveLogDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfMoveLogEntity = new EntityInsertionAdapter<MoveLogEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `move_logs` (`id`,`jobId`,`seq`,`sourceDocId`,`displayName`,`mime`,`destFolder`,`destDocId`,`destName`,`sizeBytes`,`status`,`detail`) VALUES (nullif(?, 0),?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MoveLogEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getJobId());
        statement.bindLong(3, entity.getSeq());
        statement.bindString(4, entity.getSourceDocId());
        statement.bindString(5, entity.getDisplayName());
        if (entity.getMime() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getMime());
        }
        statement.bindString(7, entity.getDestFolder());
        if (entity.getDestDocId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getDestDocId());
        }
        statement.bindString(9, entity.getDestName());
        statement.bindLong(10, entity.getSizeBytes());
        statement.bindString(11, entity.getStatus());
        if (entity.getDetail() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getDetail());
        }
      }
    };
    this.__updateAdapterOfMoveLogEntity = new EntityDeletionOrUpdateAdapter<MoveLogEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "UPDATE OR ABORT `move_logs` SET `id` = ?,`jobId` = ?,`seq` = ?,`sourceDocId` = ?,`displayName` = ?,`mime` = ?,`destFolder` = ?,`destDocId` = ?,`destName` = ?,`sizeBytes` = ?,`status` = ?,`detail` = ? WHERE `id` = ?";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final MoveLogEntity entity) {
        statement.bindLong(1, entity.getId());
        statement.bindLong(2, entity.getJobId());
        statement.bindLong(3, entity.getSeq());
        statement.bindString(4, entity.getSourceDocId());
        statement.bindString(5, entity.getDisplayName());
        if (entity.getMime() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getMime());
        }
        statement.bindString(7, entity.getDestFolder());
        if (entity.getDestDocId() == null) {
          statement.bindNull(8);
        } else {
          statement.bindString(8, entity.getDestDocId());
        }
        statement.bindString(9, entity.getDestName());
        statement.bindLong(10, entity.getSizeBytes());
        statement.bindString(11, entity.getStatus());
        if (entity.getDetail() == null) {
          statement.bindNull(12);
        } else {
          statement.bindString(12, entity.getDetail());
        }
        statement.bindLong(13, entity.getId());
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE move_logs SET status = ?, detail = ?, destDocId = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfDeleteLogsForJobsOlderThan = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM move_logs WHERE jobId IN (SELECT id FROM sort_jobs WHERE createdAt < ?)";
        return _query;
      }
    };
    this.__preparedStmtOfClearAll = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM move_logs";
        return _query;
      }
    };
  }

  @Override
  public Object insertAll(final List<MoveLogEntity> logs,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfMoveLogEntity.insert(logs);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object insert(final MoveLogEntity log, final Continuation<? super Long> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Long>() {
      @Override
      @NonNull
      public Long call() throws Exception {
        __db.beginTransaction();
        try {
          final Long _result = __insertionAdapterOfMoveLogEntity.insertAndReturnId(log);
          __db.setTransactionSuccessful();
          return _result;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object update(final MoveLogEntity log, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __updateAdapterOfMoveLogEntity.handle(log);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final long id, final String status, final String destDocId,
      final String detail, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        if (detail == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, detail);
        }
        _argIndex = 3;
        if (destDocId == null) {
          _stmt.bindNull(_argIndex);
        } else {
          _stmt.bindString(_argIndex, destDocId);
        }
        _argIndex = 4;
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
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteLogsForJobsOlderThan(final long cutoff,
      final Continuation<? super Integer> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Integer>() {
      @Override
      @NonNull
      public Integer call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteLogsForJobsOlderThan.acquire();
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
          __preparedStmtOfDeleteLogsForJobsOlderThan.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearAll(final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearAll.acquire();
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
          __preparedStmtOfClearAll.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object byJob(final long jobId,
      final Continuation<? super List<MoveLogEntity>> $completion) {
    final String _sql = "SELECT * FROM move_logs WHERE jobId = ? ORDER BY seq ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, jobId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MoveLogEntity>>() {
      @Override
      @NonNull
      public List<MoveLogEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfJobId = CursorUtil.getColumnIndexOrThrow(_cursor, "jobId");
          final int _cursorIndexOfSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "seq");
          final int _cursorIndexOfSourceDocId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceDocId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMime = CursorUtil.getColumnIndexOrThrow(_cursor, "mime");
          final int _cursorIndexOfDestFolder = CursorUtil.getColumnIndexOrThrow(_cursor, "destFolder");
          final int _cursorIndexOfDestDocId = CursorUtil.getColumnIndexOrThrow(_cursor, "destDocId");
          final int _cursorIndexOfDestName = CursorUtil.getColumnIndexOrThrow(_cursor, "destName");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfDetail = CursorUtil.getColumnIndexOrThrow(_cursor, "detail");
          final List<MoveLogEntity> _result = new ArrayList<MoveLogEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MoveLogEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpJobId;
            _tmpJobId = _cursor.getLong(_cursorIndexOfJobId);
            final int _tmpSeq;
            _tmpSeq = _cursor.getInt(_cursorIndexOfSeq);
            final String _tmpSourceDocId;
            _tmpSourceDocId = _cursor.getString(_cursorIndexOfSourceDocId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMime;
            if (_cursor.isNull(_cursorIndexOfMime)) {
              _tmpMime = null;
            } else {
              _tmpMime = _cursor.getString(_cursorIndexOfMime);
            }
            final String _tmpDestFolder;
            _tmpDestFolder = _cursor.getString(_cursorIndexOfDestFolder);
            final String _tmpDestDocId;
            if (_cursor.isNull(_cursorIndexOfDestDocId)) {
              _tmpDestDocId = null;
            } else {
              _tmpDestDocId = _cursor.getString(_cursorIndexOfDestDocId);
            }
            final String _tmpDestName;
            _tmpDestName = _cursor.getString(_cursorIndexOfDestName);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpDetail;
            if (_cursor.isNull(_cursorIndexOfDetail)) {
              _tmpDetail = null;
            } else {
              _tmpDetail = _cursor.getString(_cursorIndexOfDetail);
            }
            _item = new MoveLogEntity(_tmpId,_tmpJobId,_tmpSeq,_tmpSourceDocId,_tmpDisplayName,_tmpMime,_tmpDestFolder,_tmpDestDocId,_tmpDestName,_tmpSizeBytes,_tmpStatus,_tmpDetail);
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
  public Object byJobStatusPaged(final long jobId, final String status, final int limit,
      final int offset, final Continuation<? super List<MoveLogEntity>> $completion) {
    final String _sql = "SELECT * FROM move_logs WHERE jobId = ? AND status = ? ORDER BY seq ASC LIMIT ? OFFSET ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 4);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, jobId);
    _argIndex = 2;
    _statement.bindString(_argIndex, status);
    _argIndex = 3;
    _statement.bindLong(_argIndex, limit);
    _argIndex = 4;
    _statement.bindLong(_argIndex, offset);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<MoveLogEntity>>() {
      @Override
      @NonNull
      public List<MoveLogEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfJobId = CursorUtil.getColumnIndexOrThrow(_cursor, "jobId");
          final int _cursorIndexOfSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "seq");
          final int _cursorIndexOfSourceDocId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceDocId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMime = CursorUtil.getColumnIndexOrThrow(_cursor, "mime");
          final int _cursorIndexOfDestFolder = CursorUtil.getColumnIndexOrThrow(_cursor, "destFolder");
          final int _cursorIndexOfDestDocId = CursorUtil.getColumnIndexOrThrow(_cursor, "destDocId");
          final int _cursorIndexOfDestName = CursorUtil.getColumnIndexOrThrow(_cursor, "destName");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfDetail = CursorUtil.getColumnIndexOrThrow(_cursor, "detail");
          final List<MoveLogEntity> _result = new ArrayList<MoveLogEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MoveLogEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpJobId;
            _tmpJobId = _cursor.getLong(_cursorIndexOfJobId);
            final int _tmpSeq;
            _tmpSeq = _cursor.getInt(_cursorIndexOfSeq);
            final String _tmpSourceDocId;
            _tmpSourceDocId = _cursor.getString(_cursorIndexOfSourceDocId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMime;
            if (_cursor.isNull(_cursorIndexOfMime)) {
              _tmpMime = null;
            } else {
              _tmpMime = _cursor.getString(_cursorIndexOfMime);
            }
            final String _tmpDestFolder;
            _tmpDestFolder = _cursor.getString(_cursorIndexOfDestFolder);
            final String _tmpDestDocId;
            if (_cursor.isNull(_cursorIndexOfDestDocId)) {
              _tmpDestDocId = null;
            } else {
              _tmpDestDocId = _cursor.getString(_cursorIndexOfDestDocId);
            }
            final String _tmpDestName;
            _tmpDestName = _cursor.getString(_cursorIndexOfDestName);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpDetail;
            if (_cursor.isNull(_cursorIndexOfDetail)) {
              _tmpDetail = null;
            } else {
              _tmpDetail = _cursor.getString(_cursorIndexOfDetail);
            }
            _item = new MoveLogEntity(_tmpId,_tmpJobId,_tmpSeq,_tmpSourceDocId,_tmpDisplayName,_tmpMime,_tmpDestFolder,_tmpDestDocId,_tmpDestName,_tmpSizeBytes,_tmpStatus,_tmpDetail);
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
  public Flow<List<MoveLogEntity>> observeByJobPaged(final long jobId, final int limit) {
    final String _sql = "SELECT * FROM move_logs WHERE jobId = ? ORDER BY seq ASC LIMIT ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, jobId);
    _argIndex = 2;
    _statement.bindLong(_argIndex, limit);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"move_logs"}, new Callable<List<MoveLogEntity>>() {
      @Override
      @NonNull
      public List<MoveLogEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfJobId = CursorUtil.getColumnIndexOrThrow(_cursor, "jobId");
          final int _cursorIndexOfSeq = CursorUtil.getColumnIndexOrThrow(_cursor, "seq");
          final int _cursorIndexOfSourceDocId = CursorUtil.getColumnIndexOrThrow(_cursor, "sourceDocId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfMime = CursorUtil.getColumnIndexOrThrow(_cursor, "mime");
          final int _cursorIndexOfDestFolder = CursorUtil.getColumnIndexOrThrow(_cursor, "destFolder");
          final int _cursorIndexOfDestDocId = CursorUtil.getColumnIndexOrThrow(_cursor, "destDocId");
          final int _cursorIndexOfDestName = CursorUtil.getColumnIndexOrThrow(_cursor, "destName");
          final int _cursorIndexOfSizeBytes = CursorUtil.getColumnIndexOrThrow(_cursor, "sizeBytes");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfDetail = CursorUtil.getColumnIndexOrThrow(_cursor, "detail");
          final List<MoveLogEntity> _result = new ArrayList<MoveLogEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final MoveLogEntity _item;
            final long _tmpId;
            _tmpId = _cursor.getLong(_cursorIndexOfId);
            final long _tmpJobId;
            _tmpJobId = _cursor.getLong(_cursorIndexOfJobId);
            final int _tmpSeq;
            _tmpSeq = _cursor.getInt(_cursorIndexOfSeq);
            final String _tmpSourceDocId;
            _tmpSourceDocId = _cursor.getString(_cursorIndexOfSourceDocId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpMime;
            if (_cursor.isNull(_cursorIndexOfMime)) {
              _tmpMime = null;
            } else {
              _tmpMime = _cursor.getString(_cursorIndexOfMime);
            }
            final String _tmpDestFolder;
            _tmpDestFolder = _cursor.getString(_cursorIndexOfDestFolder);
            final String _tmpDestDocId;
            if (_cursor.isNull(_cursorIndexOfDestDocId)) {
              _tmpDestDocId = null;
            } else {
              _tmpDestDocId = _cursor.getString(_cursorIndexOfDestDocId);
            }
            final String _tmpDestName;
            _tmpDestName = _cursor.getString(_cursorIndexOfDestName);
            final long _tmpSizeBytes;
            _tmpSizeBytes = _cursor.getLong(_cursorIndexOfSizeBytes);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpDetail;
            if (_cursor.isNull(_cursorIndexOfDetail)) {
              _tmpDetail = null;
            } else {
              _tmpDetail = _cursor.getString(_cursorIndexOfDetail);
            }
            _item = new MoveLogEntity(_tmpId,_tmpJobId,_tmpSeq,_tmpSourceDocId,_tmpDisplayName,_tmpMime,_tmpDestFolder,_tmpDestDocId,_tmpDestName,_tmpSizeBytes,_tmpStatus,_tmpDetail);
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
  public Object countByStatus(final long jobId, final String status,
      final Continuation<? super Integer> $completion) {
    final String _sql = "SELECT COUNT(*) FROM move_logs WHERE jobId = ? AND status = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindLong(_argIndex, jobId);
    _argIndex = 2;
    _statement.bindString(_argIndex, status);
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
