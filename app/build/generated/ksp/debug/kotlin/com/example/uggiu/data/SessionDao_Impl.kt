package com.example.uggiu.`data`

import androidx.room.EntityDeleteOrUpdateAdapter
import androidx.room.EntityInsertAdapter
import androidx.room.RoomDatabase
import androidx.room.coroutines.createFlow
import androidx.room.util.getColumnIndexOrThrow
import androidx.room.util.performSuspending
import androidx.sqlite.SQLiteStatement
import kotlin.Int
import kotlin.Long
import kotlin.String
import kotlin.Suppress
import kotlin.Unit
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.mutableListOf
import kotlin.reflect.KClass
import kotlinx.coroutines.flow.Flow

@Suppress(names = ["UNCHECKED_CAST", "DEPRECATION", "REDUNDANT_PROJECTION", "REMOVAL"])
public class SessionDao_Impl(
  __db: RoomDatabase,
) : SessionDao {
  private val __db: RoomDatabase

  private val __insertAdapterOfWorkoutSession: EntityInsertAdapter<WorkoutSession>

  private val __deleteAdapterOfWorkoutSession: EntityDeleteOrUpdateAdapter<WorkoutSession>

  private val __updateAdapterOfWorkoutSession: EntityDeleteOrUpdateAdapter<WorkoutSession>
  init {
    this.__db = __db
    this.__insertAdapterOfWorkoutSession = object : EntityInsertAdapter<WorkoutSession>() {
      protected override fun createQuery(): String =
          "INSERT OR REPLACE INTO `workout_sessions` (`id`,`startTime`,`endTime`,`averageHeartRate`,`maxHeartRate`,`heartRateHistoryString`) VALUES (nullif(?, 0),?,?,?,?,?)"

      protected override fun bind(statement: SQLiteStatement, entity: WorkoutSession) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.startTime)
        statement.bindLong(3, entity.endTime)
        statement.bindLong(4, entity.averageHeartRate.toLong())
        statement.bindLong(5, entity.maxHeartRate.toLong())
        statement.bindText(6, entity.heartRateHistoryString)
      }
    }
    this.__deleteAdapterOfWorkoutSession = object : EntityDeleteOrUpdateAdapter<WorkoutSession>() {
      protected override fun createQuery(): String = "DELETE FROM `workout_sessions` WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: WorkoutSession) {
        statement.bindLong(1, entity.id.toLong())
      }
    }
    this.__updateAdapterOfWorkoutSession = object : EntityDeleteOrUpdateAdapter<WorkoutSession>() {
      protected override fun createQuery(): String =
          "UPDATE OR ABORT `workout_sessions` SET `id` = ?,`startTime` = ?,`endTime` = ?,`averageHeartRate` = ?,`maxHeartRate` = ?,`heartRateHistoryString` = ? WHERE `id` = ?"

      protected override fun bind(statement: SQLiteStatement, entity: WorkoutSession) {
        statement.bindLong(1, entity.id.toLong())
        statement.bindLong(2, entity.startTime)
        statement.bindLong(3, entity.endTime)
        statement.bindLong(4, entity.averageHeartRate.toLong())
        statement.bindLong(5, entity.maxHeartRate.toLong())
        statement.bindText(6, entity.heartRateHistoryString)
        statement.bindLong(7, entity.id.toLong())
      }
    }
  }

  public override suspend fun insertSession(session: WorkoutSession): Long = performSuspending(__db,
      false, true) { _connection ->
    val _result: Long = __insertAdapterOfWorkoutSession.insertAndReturnId(_connection, session)
    _result
  }

  public override suspend fun deleteSession(session: WorkoutSession): Unit = performSuspending(__db,
      false, true) { _connection ->
    __deleteAdapterOfWorkoutSession.handle(_connection, session)
  }

  public override suspend fun updateSession(session: WorkoutSession): Unit = performSuspending(__db,
      false, true) { _connection ->
    __updateAdapterOfWorkoutSession.handle(_connection, session)
  }

  public override fun getAllSessions(): Flow<List<WorkoutSession>> {
    val _sql: String = "SELECT * FROM workout_sessions ORDER BY startTime DESC"
    return createFlow(__db, false, arrayOf("workout_sessions")) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTime: Int = getColumnIndexOrThrow(_stmt, "startTime")
        val _columnIndexOfEndTime: Int = getColumnIndexOrThrow(_stmt, "endTime")
        val _columnIndexOfAverageHeartRate: Int = getColumnIndexOrThrow(_stmt, "averageHeartRate")
        val _columnIndexOfMaxHeartRate: Int = getColumnIndexOrThrow(_stmt, "maxHeartRate")
        val _columnIndexOfHeartRateHistoryString: Int = getColumnIndexOrThrow(_stmt,
            "heartRateHistoryString")
        val _result: MutableList<WorkoutSession> = mutableListOf()
        while (_stmt.step()) {
          val _item: WorkoutSession
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpStartTime: Long
          _tmpStartTime = _stmt.getLong(_columnIndexOfStartTime)
          val _tmpEndTime: Long
          _tmpEndTime = _stmt.getLong(_columnIndexOfEndTime)
          val _tmpAverageHeartRate: Int
          _tmpAverageHeartRate = _stmt.getLong(_columnIndexOfAverageHeartRate).toInt()
          val _tmpMaxHeartRate: Int
          _tmpMaxHeartRate = _stmt.getLong(_columnIndexOfMaxHeartRate).toInt()
          val _tmpHeartRateHistoryString: String
          _tmpHeartRateHistoryString = _stmt.getText(_columnIndexOfHeartRateHistoryString)
          _item =
              WorkoutSession(_tmpId,_tmpStartTime,_tmpEndTime,_tmpAverageHeartRate,_tmpMaxHeartRate,_tmpHeartRateHistoryString)
          _result.add(_item)
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun getSessionById(id: Int): WorkoutSession? {
    val _sql: String = "SELECT * FROM workout_sessions WHERE id = ? LIMIT 1"
    return performSuspending(__db, true, false) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        var _argIndex: Int = 1
        _stmt.bindLong(_argIndex, id.toLong())
        val _columnIndexOfId: Int = getColumnIndexOrThrow(_stmt, "id")
        val _columnIndexOfStartTime: Int = getColumnIndexOrThrow(_stmt, "startTime")
        val _columnIndexOfEndTime: Int = getColumnIndexOrThrow(_stmt, "endTime")
        val _columnIndexOfAverageHeartRate: Int = getColumnIndexOrThrow(_stmt, "averageHeartRate")
        val _columnIndexOfMaxHeartRate: Int = getColumnIndexOrThrow(_stmt, "maxHeartRate")
        val _columnIndexOfHeartRateHistoryString: Int = getColumnIndexOrThrow(_stmt,
            "heartRateHistoryString")
        val _result: WorkoutSession?
        if (_stmt.step()) {
          val _tmpId: Int
          _tmpId = _stmt.getLong(_columnIndexOfId).toInt()
          val _tmpStartTime: Long
          _tmpStartTime = _stmt.getLong(_columnIndexOfStartTime)
          val _tmpEndTime: Long
          _tmpEndTime = _stmt.getLong(_columnIndexOfEndTime)
          val _tmpAverageHeartRate: Int
          _tmpAverageHeartRate = _stmt.getLong(_columnIndexOfAverageHeartRate).toInt()
          val _tmpMaxHeartRate: Int
          _tmpMaxHeartRate = _stmt.getLong(_columnIndexOfMaxHeartRate).toInt()
          val _tmpHeartRateHistoryString: String
          _tmpHeartRateHistoryString = _stmt.getText(_columnIndexOfHeartRateHistoryString)
          _result =
              WorkoutSession(_tmpId,_tmpStartTime,_tmpEndTime,_tmpAverageHeartRate,_tmpMaxHeartRate,_tmpHeartRateHistoryString)
        } else {
          _result = null
        }
        _result
      } finally {
        _stmt.close()
      }
    }
  }

  public override suspend fun deleteAllSessions() {
    val _sql: String = "DELETE FROM workout_sessions"
    return performSuspending(__db, false, true) { _connection ->
      val _stmt: SQLiteStatement = _connection.prepare(_sql)
      try {
        _stmt.step()
      } finally {
        _stmt.close()
      }
    }
  }

  public companion object {
    public fun getRequiredConverters(): List<KClass<*>> = emptyList()
  }
}
