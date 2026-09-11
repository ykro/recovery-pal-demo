package dev.ykro.recoverypal.ui.checkin

import androidx.lifecycle.ViewModel
import com.google.adk.kt.types.FunctionCall
import androidx.lifecycle.viewModelScope
import dev.ykro.recoverypal.agent.AgentRuntime
import dev.ykro.recoverypal.agent.AgentUiEvent
import dev.ykro.recoverypal.data.Patient
import dev.ykro.recoverypal.data.PatientStore
import dev.ykro.recoverypal.ui.components.ToolChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface TimelineItem {
  data class Tools(val chips: List<ToolChip>) : TimelineItem
  data class Agent(val text: String) : TimelineItem
  data class User(val text: String) : TimelineItem
  data class System(val text: String) : TimelineItem
  data class Failure(val text: String) : TimelineItem
}

data class PendingConfirmation(val callId: String, val toolName: String, val args: Map<String, Any?>, val hint: String?)

data class CheckinUiState(
  val items: List<TimelineItem> = emptyList(),
  val streamingText: String? = null,
  val busy: Boolean = true,
  val confirmation: PendingConfirmation? = null,
  val checkinSaved: Boolean = false,
  val day: Int = 0,
  val phaseTitle: String = "",
  val resumed: Boolean = false,
  val fatal: String? = null,
)

/**
 * The whole recovery episode is ONE ADK session. Opening this screen appends today's check-in to
 * it; the timeline shows only today's turns (older ones are still in Room and in the model's
 * compacted context).
 */
class CheckinViewModel(private val runtime: AgentRuntime, private val patients: PatientStore) : ViewModel() {
  private val _state = MutableStateFlow(CheckinUiState())
  val state: StateFlow<CheckinUiState> = _state
  private lateinit var patient: Patient
  private var eventsBefore = 0

  init {
    viewModelScope.launch { start() }
  }

  private suspend fun start() {
    val p = patients.current()
    if (p == null) {
      _state.update { it.copy(busy = false, fatal = "Finish onboarding first.") }
      return
    }
    patient = p
    _state.update { it.copy(day = p.dayToday(), phaseTitle = p.phaseToday().title) }
    runtime.ensureSession(p.sessionId)
    val events = runtime.sessionEvents(p.sessionId)
    // Show today's turns only: everything after the last kickoff message.
    val lastKickoff = events.indexOfLast { e -> e.author == "user" && e.content?.parts.orEmpty().any { it.text?.startsWith(AgentRuntime.SYSTEM_PREFIX) == true } }
    val todayEvents = if (lastKickoff >= 0) events.drop(lastKickoff) else emptyList()
    val replay = runtime.replay(todayEvents)
    val unfinished = replay.isNotEmpty() && replay.none { it is AgentUiEvent.ToolCall && it.name == "log_checkin" }
    if (unfinished) {
      replay.forEach { apply(it) }
      _state.update { it.copy(busy = false, resumed = true) }
      if (_state.value.confirmation == null && replay.lastOrNull() !is AgentUiEvent.FinalText) {
        send("${AgentRuntime.SYSTEM_PREFIX} The app was reopened. Continue the check-in from where you were.", showAsUser = false)
      }
    } else {
      eventsBefore = events.size
      send(AgentRuntime.checkinKickoff(p.dayToday()), showAsUser = false)
    }
  }

  fun say(text: String) {
    if (text.isBlank() || _state.value.busy) return
    viewModelScope.launch { send(text.trim(), showAsUser = true) }
  }

  fun resolveConfirmation(confirmed: Boolean) {
    val pending = _state.value.confirmation ?: return
    _state.update { it.copy(confirmation = null, busy = true, items = it.items + TimelineItem.System(if (confirmed) "You approved ${pending.toolName}" else "You declined ${pending.toolName}")) }
    viewModelScope.launch {
      if (!confirmed) runtime.careTeam.recordRejected(if (pending.toolName == "share_wound_photo") "PHOTO_SHARE" else "ESCALATION", (pending.args["reason"] ?: pending.args["artifactName"] ?: "").toString())
      runtime.sendConfirmation(patient, pending.callId, confirmed).flowOn(Dispatchers.IO).collect { apply(it) }
    }
  }

  private suspend fun send(text: String, showAsUser: Boolean) {
    _state.update { it.copy(busy = true, items = it.items + (if (showAsUser) TimelineItem.User(text) else TimelineItem.System(describeSystem(text)))) }
    runtime.sendText(patient, text).flowOn(Dispatchers.IO).collect { apply(it) }
  }

  private fun describeSystem(text: String) =
    when {
      text.contains("opened today's check-in") -> "Check-in started · day ${patient.dayToday()}"
      text.contains("reopened") -> "App reopened, resuming"
      else -> text.removePrefix(AgentRuntime.SYSTEM_PREFIX).trim()
    }

  private fun apply(event: AgentUiEvent) {
    when (event) {
      is AgentUiEvent.ToolCall -> {
        addChip(ToolChip(event.id, event.name, event.isSkill))
        if (event.name == "log_checkin") _state.update { it.copy(checkinSaved = true) }
      }
      is AgentUiEvent.ToolResult -> {
        completeChip(event)
        if (event.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) _state.update { it.copy(confirmation = null) }
        if (event.name == "log_checkin" && !event.isError) viewModelScope.launch { runtime.indexSessionInMemory(patient.sessionId) }
      }
      is AgentUiEvent.PartialText -> _state.update { it.copy(streamingText = event.text) }
      is AgentUiEvent.FinalText -> _state.update { it.copy(items = it.items + TimelineItem.Agent(event.text), streamingText = null) }
      is AgentUiEvent.ConfirmationRequested ->
        _state.update { it.copy(confirmation = PendingConfirmation(event.confirmationCallId, event.toolName, event.args, event.hint), busy = false, streamingText = null) }
      is AgentUiEvent.Error -> _state.update { it.copy(items = it.items + TimelineItem.Failure(event.message), streamingText = null) }
      is AgentUiEvent.UserText -> _state.update { it.copy(items = it.items + if (event.isSystem) TimelineItem.System(describeSystem(event.text)) else TimelineItem.User(event.text)) }
      AgentUiEvent.Done -> _state.update { it.copy(busy = false, streamingText = null) }
    }
  }

  private fun addChip(chip: ToolChip) =
    _state.update { s ->
      val last = s.items.lastOrNull()
      s.copy(items = if (last is TimelineItem.Tools) s.items.dropLast(1) + last.copy(chips = last.chips + chip) else s.items + TimelineItem.Tools(listOf(chip)))
    }

  private fun completeChip(result: AgentUiEvent.ToolResult) =
    _state.update { s ->
      s.copy(items = s.items.map { item -> if (item !is TimelineItem.Tools) item else item.copy(chips = item.chips.map { c -> if (c.id == result.id || (c.name == result.name && !c.done)) c.copy(done = true, isError = result.isError, summary = result.summary) else c }) })
    }
}
