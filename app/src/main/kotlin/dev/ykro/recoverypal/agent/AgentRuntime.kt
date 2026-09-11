package dev.ykro.recoverypal.agent

import android.content.Context
import com.google.adk.firebase.models.Firebase
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.apps.App
import com.google.adk.kt.artifacts.ArtifactService
import com.google.adk.kt.artifacts.FileArtifactService
import com.google.adk.kt.artifacts.fromExternalFilesDir
import com.google.adk.kt.events.Event
import com.google.adk.kt.events.ToolConfirmation
import com.google.adk.kt.litertlm.LiteRtLmModel
import com.google.adk.kt.memory.MemoryService
import com.google.adk.kt.memory.appsearch.AppSearchMemoryService
import com.google.adk.kt.models.Model
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.InMemorySessionService
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.sessions.SessionService
import com.google.adk.kt.sessions.room.RoomSessionService
import com.google.adk.kt.summarizer.EventsCompactionConfig
import com.google.adk.kt.summarizer.LlmEventSummarizer
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.types.Blob
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.FunctionCall
import com.google.adk.kt.types.FunctionResponse
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.EngineConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.FirebaseAI
import dev.ykro.recoverypal.data.Patient
import dev.ykro.recoverypal.data.PatientStore
import dev.ykro.recoverypal.data.RecoveryDatabase
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import timber.log.Timber

sealed interface AgentUiEvent {
  data class ToolCall(val id: String, val name: String, val args: Map<String, Any?>, val isSkill: Boolean) : AgentUiEvent
  data class ToolResult(val id: String, val name: String, val isError: Boolean, val summary: String) : AgentUiEvent
  data class PartialText(val text: String) : AgentUiEvent
  data class FinalText(val text: String) : AgentUiEvent
  data class ConfirmationRequested(val confirmationCallId: String, val toolName: String, val args: Map<String, Any?>, val hint: String?) : AgentUiEvent
  data class Error(val message: String) : AgentUiEvent
  data class UserText(val text: String, val isSystem: Boolean) : AgentUiEvent
  data object Done : AgentUiEvent
}

data class SessionStats(val events: Int, val compactions: Int)

/**
 * Two runners, one process. The cloud runner keeps ONE session for the whole recovery episode
 * (Room), compacts it as it grows and indexes check-ins into AppSearch memory. The on-device runner
 * gets a throwaway session per photo and never touches the network.
 */
class AgentRuntime(private val context: Context, private val db: RecoveryDatabase, private val patients: PatientStore) {
  val sessionService: SessionService = RoomSessionService.fromContext(context)
  val artifactService: ArtifactService = FileArtifactService.fromExternalFilesDir(context)
  val memoryService: MemoryService = AppSearchMemoryService.fromContext(context)
  val careTeam = CareTeamClient(db, patients)

  private var recoveryRunner: InMemoryRunner? = null
  private var recoveryRunnerFor: String? = null
  private var woundRunner: InMemoryRunner? = null
  private var liteRtModel: LiteRtLmModel? = null

  fun sessionKey(sessionId: String) = SessionKey(APP_NAME, USER_ID, sessionId)

  private fun firebaseModel(): Model {
    val app = runCatching { FirebaseApp.getInstance() }.getOrElse { error("Firebase is not configured. Add app/google-services.json (see README).") }
    return Firebase.create(CLOUD_MODEL, FirebaseAI.getInstance(app))
  }

  /** Built per patient (the instruction embeds surgery and date); rebuilt after onboarding changes. */
  @Synchronized
  fun recoveryRunner(patient: Patient): InMemoryRunner {
    val key = "${patient.surgery.name}|${patient.surgeryDate}|${patient.sessionId}"
    recoveryRunner?.let { if (recoveryRunnerFor == key) return it }
    val model = firebaseModel()
    val agent = RecoveryAgent.create(context, model, patient, ProtocolTools(patients), JournalTools(db, patients), CareTeamTools(careTeam))
    val app =
      App(
        appName = APP_NAME,
        rootAgent = agent,
        eventsCompactionConfig = EventsCompactionConfig(compactionInterval = COMPACTION_INTERVAL, overlapSize = 1, summarizer = LlmEventSummarizer(model)),
      )
    return InMemoryRunner(app = app, sessionService = sessionService, artifactService = artifactService, memoryService = memoryService)
      .also { recoveryRunner = it; recoveryRunnerFor = key }
  }

  @Synchronized
  fun woundRunner(): InMemoryRunner {
    woundRunner?.let { return it }
    val file = ModelStore.find(context) ?: error("The on-device model is not installed. Download it in Settings.")
    val model =
      // visionBackend is what lets the engine accept image parts; without it LiteRT-LM rejects inlineData.
      LiteRtLmModel.create(
          EngineConfig(modelPath = file.absolutePath, backend = Backend.CPU(), visionBackend = Backend.CPU(), maxNumImages = 1, cacheDir = context.cacheDir.absolutePath),
          name = file.name,
        )
        .also { it.engine.initialize(); liteRtModel = it }
    return InMemoryRunner(agent = WoundPhotoAgent.create(model), appName = WOUND_APP_NAME, sessionService = InMemorySessionService()).also { woundRunner = it }
  }

  fun isWoundModelInstalled(): Boolean = ModelStore.find(context) != null

  suspend fun ensureSession(sessionId: String) {
    if (sessionService.getSession(sessionKey(sessionId)) == null) sessionService.createSession(sessionKey(sessionId))
  }

  suspend fun sessionEvents(sessionId: String): List<Event> = sessionService.getSession(sessionKey(sessionId))?.events.orEmpty()

  suspend fun sessionStats(sessionId: String): SessionStats {
    val events = sessionEvents(sessionId)
    return SessionStats(events.size, events.count { it.actions.compaction != null })
  }

  /** Indexes the episode so `load_memory` can recall week-1 remarks in week 5. */
  suspend fun indexSessionInMemory(sessionId: String) {
    val session = sessionService.getSession(sessionKey(sessionId)) ?: return
    runCatching { memoryService.addSessionToMemory(session) }.onFailure { Timber.w(it, "Memory indexing failed") }
  }

  suspend fun savePhoto(sessionId: String, jpeg: ByteArray): String {
    val name = "wound-${System.currentTimeMillis()}.jpg"
    artifactService.saveArtifact(sessionKey(sessionId), name, Part(inlineData = Blob(mimeType = "image/jpeg", data = jpeg)))
    return name
  }

  suspend fun loadPhoto(sessionId: String, name: String): ByteArray? = artifactService.loadArtifact(sessionKey(sessionId), name)?.inlineData?.data

  fun sendText(patient: Patient, text: String): Flow<AgentUiEvent> =
    run(patient, Content(role = Role.USER, parts = listOf(Part(text = text))))

  fun sendConfirmation(patient: Patient, confirmationCallId: String, confirmed: Boolean): Flow<AgentUiEvent> =
    run(
      patient,
      Content(
        role = Role.USER,
        parts =
          listOf(
            Part(
              functionResponse =
                FunctionResponse(
                  name = FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME,
                  id = confirmationCallId,
                  response = mapOf(ToolConfirmation.CONFIRMED_KEY to confirmed),
                )
            )
          ),
      ),
    )

  private fun run(patient: Patient, message: Content): Flow<AgentUiEvent> = flow {
    val partial = StringBuilder()
    var finalText: String? = null
    try {
      recoveryRunner(patient)
        .runAsync(userId = USER_ID, sessionId = patient.sessionId, newMessage = message, runConfig = RunConfig(streamingMode = StreamingMode.SSE, maxLlmCalls = 20))
        .collect { event ->
          event.errorMessage?.let { emit(AgentUiEvent.Error(it)) }
          if (!event.partial) mapFunctionParts(event).forEach { emit(it) }
          if (event.author == RecoveryAgent.NAME) {
            val text = modelText(event)
            if (event.partial) {
              if (text.isNotEmpty()) { partial.append(text); emit(AgentUiEvent.PartialText(partial.toString())) }
            } else if (text.isNotBlank()) {
              finalText = text
              partial.setLength(0)
            }
          }
        }
      finalText?.let { emit(AgentUiEvent.FinalText(it.trim())) }
    } catch (e: Exception) {
      Timber.e(e, "Check-in turn failed")
      emit(AgentUiEvent.Error(e.message ?: e::class.simpleName.orEmpty()))
    }
    emit(AgentUiEvent.Done)
  }

  /** One ephemeral session per photo; the image goes to the model as inline bytes and is never stored in a session. */
  suspend fun analyzeWound(jpeg: ByteArray): Pair<WoundObservation, Long> {
    val runner = woundRunner()
    val sessionId = "photo-" + UUID.randomUUID().toString().take(8)
    runner.sessionService.createSession(SessionKey(WOUND_APP_NAME, USER_ID, sessionId))
    val message =
      Content(
        role = Role.USER,
        parts = listOf(Part(inlineData = Blob(mimeType = "image/jpeg", data = jpeg)), Part(text = "Describe this surgical wound photo using only the schema.")),
      )
    val started = System.currentTimeMillis()
    var text = ""
    runner.runAsync(userId = USER_ID, sessionId = sessionId, newMessage = message, runConfig = RunConfig(streamingMode = StreamingMode.NONE, maxLlmCalls = 3)).collect { event ->
      event.errorMessage?.let { error(it) }
      if (event.author == WoundPhotoAgent.NAME && !event.partial) modelText(event).takeIf { it.isNotBlank() }?.let { text = it }
    }
    val elapsed = System.currentTimeMillis() - started
    Timber.i("Wound observation in %d ms: %s", elapsed, text.take(200))
    val observation = WoundObservationSchema.parse(text) ?: WoundObservation(imageQuality = "UNSURE", freeText = text.take(160).ifBlank { "The model returned no observation." })
    return observation to elapsed
  }

  fun replay(events: List<Event>): List<AgentUiEvent> {
    val out = mutableListOf<AgentUiEvent>()
    for (event in events) {
      if (event.partial) continue
      if (event.author == "user") {
        val text = event.content?.parts.orEmpty().mapNotNull { it.text }.joinToString("").trim()
        if (text.isNotEmpty()) out += AgentUiEvent.UserText(text.removePrefix(SYSTEM_PREFIX).trim(), isSystem = text.startsWith(SYSTEM_PREFIX))
      }
      out += mapFunctionParts(event)
      if (event.author == RecoveryAgent.NAME) modelText(event).takeIf { it.isNotBlank() }?.let { out += AgentUiEvent.FinalText(it.trim()) }
    }
    return out
  }

  private fun modelText(event: Event): String =
    event.content?.parts.orEmpty().filter { it.text != null && it.thought != true }.joinToString("") { it.text.orEmpty() }

  private fun mapFunctionParts(event: Event): List<AgentUiEvent> {
    val out = mutableListOf<AgentUiEvent>()
    for (part in event.content?.parts.orEmpty()) {
      part.functionCall?.let { call ->
        if (call.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
          val original = call.args["originalFunctionCall"] as? Map<*, *>
          val confirmation = call.args["toolConfirmation"] as? Map<*, *>
          out +=
            AgentUiEvent.ConfirmationRequested(
              confirmationCallId = call.id.orEmpty(),
              toolName = original?.get("name") as? String ?: "tool",
              args = (original?.get("args") as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value }.orEmpty(),
              hint = confirmation?.get("hint") as? String,
            )
        } else {
          out += AgentUiEvent.ToolCall(call.id ?: call.name, call.name, call.args, call.name in SKILL_TOOLS)
        }
      }
      part.functionResponse?.let { resp ->
        if (resp.name == FunctionCall.REQUEST_CONFIRMATION_FUNCTION_CALL_NAME) {
          out += AgentUiEvent.ToolResult(resp.id ?: resp.name, resp.name, isError = false, summary = "answered")
        } else {
          out += AgentUiEvent.ToolResult(resp.id ?: resp.name, resp.name, resp.response.containsKey("error"), summarize(resp.name, unwrap(resp.response)))
        }
      }
    }
    return out
  }

  /** KSP-generated tools return `{"result": ...}`; skill tools and errors return their map directly. */
  private fun unwrap(response: Map<String, Any?>): Map<String, Any?> =
    (response["result"] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value } ?: response

  private fun summarize(name: String, response: Map<String, Any?>): String =
    when (name) {
      "get_protocol_day" -> "day ${response["dayNumber"]} · phase ${response["phaseNumber"]}"
      SkillToolset.TOOL_NAME_LOAD_SKILL -> "protocol loaded"
      SkillToolset.TOOL_NAME_LOAD_SKILL_RESOURCE -> "asset loaded"
      SkillToolset.TOOL_NAME_LIST_SKILLS -> "catalog"
      "log_checkin" -> "saved"
      "get_recent_checkins" -> "${(response["result"] as? List<*>)?.size ?: 0} entries"
      "load_memory" -> "recalled"
      "escalate_to_care_team" -> if (response["sent"] == true) "sent" else (response["error"] ?: "").toString()
      "share_wound_photo" -> if (response["shared"] == true) "shared" else (response["error"] ?: "").toString()
      else -> "ok"
    }

  companion object {
    const val APP_NAME = "RecoveryPal"
    const val WOUND_APP_NAME = "RecoveryPalWound"
    const val USER_ID = "patient"
    const val CLOUD_MODEL = "gemini-3.8-flash"
    const val ON_DEVICE_MODEL_LABEL = "Gemma 4 E2B (on device)"
    const val COMPACTION_INTERVAL = 6
    const val SYSTEM_PREFIX = "[app]"
    val SKILL_TOOLS = setOf(SkillToolset.TOOL_NAME_LIST_SKILLS, SkillToolset.TOOL_NAME_LOAD_SKILL, SkillToolset.TOOL_NAME_LOAD_SKILL_RESOURCE)

    fun checkinKickoff(day: Int) = "$SYSTEM_PREFIX The patient opened today's check-in (post-operative day $day). Start the check-in."
  }
}
