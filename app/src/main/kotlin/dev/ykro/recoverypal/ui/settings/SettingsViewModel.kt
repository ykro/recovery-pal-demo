package dev.ykro.recoverypal.ui.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ykro.recoverypal.agent.AgentRuntime
import dev.ykro.recoverypal.agent.ModelStore
import dev.ykro.recoverypal.agent.SessionStats
import dev.ykro.recoverypal.data.OutboundEntity
import dev.ykro.recoverypal.data.PatientStore
import dev.ykro.recoverypal.data.RecoveryDatabase
import dev.ykro.recoverypal.work.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

data class SettingsUiState(
  val modelPresent: Boolean = false,
  val modelFileName: String? = null,
  val downloading: Boolean = false,
  val progress: Float = 0f,
  val error: String? = null,
  val pushCommand: String = "",
  val simulatedDay: Int? = null,
  val calendarDay: Int = 0,
  val sessionStats: SessionStats = SessionStats(0, 0),
  val sessionId: String = "",
  val outbound: List<OutboundEntity> = emptyList(),
  val reminderLabel: String = "",
)

class SettingsViewModel(
  private val context: Context,
  private val patients: PatientStore,
  private val db: RecoveryDatabase,
  private val runtime: AgentRuntime,
  private val reminders: ReminderScheduler,
) : ViewModel() {
  private val _state = MutableStateFlow(SettingsUiState(pushCommand = ModelStore.pushCommand(context)))
  val state: StateFlow<SettingsUiState> = _state
  private var downloadJob: Job? = null

  init {
    refreshModel()
    viewModelScope.launch {
      patients.patient.collect { p ->
        if (p == null) return@collect
        val stats = runCatching { runtime.sessionStats(p.sessionId) }.getOrDefault(SessionStats(0, 0))
        _state.update {
          it.copy(
            simulatedDay = p.simulatedDay,
            calendarDay = p.copy(simulatedDay = null).dayToday(),
            sessionStats = stats,
            sessionId = p.sessionId,
            reminderLabel = "%02d:%02d".format(p.reminderHour, p.reminderMinute),
          )
        }
      }
    }
    viewModelScope.launch { db.outbound().observeAll().collect { list -> _state.update { it.copy(outbound = list) } } }
  }

  fun refreshModel() {
    val file = ModelStore.find(context)
    _state.update { it.copy(modelPresent = file != null, modelFileName = file?.name) }
  }

  fun refreshStats() = viewModelScope.launch {
    val p = patients.current() ?: return@launch
    _state.update { it.copy(sessionStats = runtime.sessionStats(p.sessionId)) }
  }

  fun setSimulatedDay(day: Int?) = viewModelScope.launch { patients.setSimulatedDay(day) }

  fun notifyNow() = viewModelScope.launch { patients.current()?.let { reminders.notifyNow(it.dayToday()) } }

  fun download() {
    if (downloadJob?.isActive == true) return
    downloadJob =
      viewModelScope.launch {
        _state.update { it.copy(downloading = true, progress = 0f, error = null) }
        try {
          ModelStore.download(context).collect { p -> _state.update { it.copy(progress = p) } }
        } catch (e: Exception) {
          Timber.e(e, "Model download failed")
          _state.update { it.copy(error = e.message ?: "Download failed") }
        }
        _state.update { it.copy(downloading = false) }
        refreshModel()
      }
  }

  fun cancelDownload() {
    downloadJob?.cancel()
    _state.update { it.copy(downloading = false) }
  }

  fun deleteModel() {
    ModelStore.delete(context)
    refreshModel()
  }

  /** Wipes the episode: patient config, journal, outbound log and the ADK session. */
  fun deleteAllData(onDone: () -> Unit) = viewModelScope.launch {
    patients.current()?.let { runCatching { runtime.sessionService.deleteSession(runtime.sessionKey(it.sessionId)) } }
    db.checkins().clear(); db.wounds().clear(); db.outbound().clear()
    reminders.cancel()
    patients.clear()
    onDone()
  }
}
