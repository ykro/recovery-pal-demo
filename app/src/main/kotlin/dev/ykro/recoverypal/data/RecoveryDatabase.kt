package dev.ykro.recoverypal.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "checkins")
data class CheckinEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val atEpochMs: Long,
  val dayNumber: Int,
  val pain: Int,
  val temperatureC: Double?,
  val exercisesDone: String, // comma separated ids
  val symptoms: String?,
  val notes: String?,
)

@Entity(tableName = "wound_observations")
data class WoundObservationEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val capturedAtEpochMs: Long,
  val dayNumber: Int,
  val rednessAroundIncision: String,
  val discharge: String,
  val edgesClosed: String,
  val swelling: String,
  val imageQuality: String,
  val freeText: String,
  val artifactName: String,
  val analyzedOffline: Boolean,
  val analysisMs: Long,
)

/** Everything that left (or was allowed to leave) the device. Shown on the Data screen. */
@Entity(tableName = "outbound_log")
data class OutboundEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val atEpochMs: Long,
  val kind: String, // ESCALATION | PHOTO_SHARE | ESCALATION_REJECTED | PHOTO_SHARE_REJECTED
  val summary: String,
)

@Dao
interface CheckinDao {
  @Insert suspend fun insert(item: CheckinEntity): Long
  @Query("SELECT * FROM checkins ORDER BY atEpochMs DESC") fun observeAll(): Flow<List<CheckinEntity>>
  @Query("SELECT * FROM checkins ORDER BY atEpochMs DESC LIMIT :limit") suspend fun latest(limit: Int): List<CheckinEntity>
  @Query("SELECT * FROM checkins WHERE dayNumber >= :fromDay ORDER BY atEpochMs DESC") suspend fun since(fromDay: Int): List<CheckinEntity>
  @Query("SELECT COUNT(*) FROM checkins") suspend fun count(): Int
  @Query("DELETE FROM checkins") suspend fun clear()
}

@Dao
interface WoundDao {
  @Insert suspend fun insert(item: WoundObservationEntity): Long
  @Query("SELECT * FROM wound_observations ORDER BY capturedAtEpochMs DESC") fun observeAll(): Flow<List<WoundObservationEntity>>
  @Query("SELECT * FROM wound_observations ORDER BY capturedAtEpochMs DESC LIMIT 1") suspend fun latest(): WoundObservationEntity?
  @Query("DELETE FROM wound_observations") suspend fun clear()
}

@Dao
interface OutboundDao {
  @Insert suspend fun insert(item: OutboundEntity): Long
  @Query("SELECT * FROM outbound_log ORDER BY atEpochMs DESC") fun observeAll(): Flow<List<OutboundEntity>>
  @Query("DELETE FROM outbound_log") suspend fun clear()
}

@Database(entities = [CheckinEntity::class, WoundObservationEntity::class, OutboundEntity::class], version = 1, exportSchema = true)
abstract class RecoveryDatabase : RoomDatabase() {
  abstract fun checkins(): CheckinDao
  abstract fun wounds(): WoundDao
  abstract fun outbound(): OutboundDao

  companion object {
    fun create(context: Context): RecoveryDatabase = Room.databaseBuilder(context, RecoveryDatabase::class.java, "recovery_pal.db").build()
  }
}
