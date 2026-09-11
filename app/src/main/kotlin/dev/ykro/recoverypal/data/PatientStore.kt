package dev.ykro.recoverypal.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "patient")

/** What the patient configured in onboarding plus the id of the ADK session for this recovery episode. */
data class Patient(
  val surgery: Surgery,
  val surgeryDate: LocalDate,
  val reminderHour: Int,
  val reminderMinute: Int,
  val careTeamContact: String,
  val sessionId: String,
  val simulatedDay: Int?,
) {
  fun dayToday(today: LocalDate = LocalDate.now()): Int = protocolDay(surgeryDate, today, simulatedDay)
  fun phaseToday(today: LocalDate = LocalDate.now()): Phase = surgery.phaseFor(dayToday(today))
}

class PatientStore(private val context: Context) {
  private val surgery = stringPreferencesKey("surgery")
  private val surgeryDate = longPreferencesKey("surgery_date_epoch_day")
  private val reminderHour = intPreferencesKey("reminder_hour")
  private val reminderMinute = intPreferencesKey("reminder_minute")
  private val contact = stringPreferencesKey("care_team_contact")
  private val sessionId = stringPreferencesKey("episode_session_id")
  private val simulatedDay = intPreferencesKey("simulated_day")

  val patient: Flow<Patient?> = context.dataStore.data.map { p -> p.toPatient() }

  suspend fun current(): Patient? = patient.first()

  private fun Preferences.toPatient(): Patient? {
    val s = Surgery.fromName(this[surgery]) ?: return null
    val d = this[surgeryDate] ?: return null
    val id = this[sessionId] ?: return null
    return Patient(s, LocalDate.ofEpochDay(d), this[reminderHour] ?: 9, this[reminderMinute] ?: 0, this[contact].orEmpty(), id, this[simulatedDay])
  }

  suspend fun onboard(s: Surgery, date: LocalDate, hour: Int, minute: Int, careTeam: String, newSessionId: String) =
    context.dataStore.edit {
      it[surgery] = s.name
      it[surgeryDate] = date.toEpochDay()
      it[reminderHour] = hour
      it[reminderMinute] = minute
      it[contact] = careTeam
      it[sessionId] = newSessionId
      it.remove(simulatedDay)
    }

  suspend fun setSimulatedDay(day: Int?) = context.dataStore.edit { if (day == null) it.remove(simulatedDay) else it[simulatedDay] = day }

  suspend fun clear() = context.dataStore.edit { it.clear() }
}
