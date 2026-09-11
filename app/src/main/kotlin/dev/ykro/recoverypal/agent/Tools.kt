package dev.ykro.recoverypal.agent

import com.google.adk.kt.annotations.Param
import com.google.adk.kt.annotations.Tool
import com.google.adk.kt.tools.FunctionTool
import dev.ykro.recoverypal.data.CheckinEntity
import dev.ykro.recoverypal.data.OutboundEntity
import dev.ykro.recoverypal.data.PatientStore
import dev.ykro.recoverypal.data.RecoveryDatabase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import timber.log.Timber

private val dateTime: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())

/** Where the patient is in the protocol. The agent calls this first on every check-in. */
class ProtocolTools(private val patients: PatientStore) {
  @Tool(name = "get_protocol_day", description = "The configured surgery, the surgery date, today's post-operative day number and the expected phase with the asset file to load.")
  suspend fun getProtocolDay(): Map<String, Any?> {
    val p = patients.current() ?: return mapOf(FunctionTool.ERROR_KEY to "No patient configured yet.")
    val day = p.dayToday()
    val phase = p.surgery.phaseFor(day)
    return mapOf(
      "surgery" to p.surgery.displayName,
      "skillName" to p.surgery.skillName,
      "surgeryDate" to p.surgeryDate.toString(),
      "dayNumber" to day,
      "phaseNumber" to phase.number,
      "phaseTitle" to phase.title,
      "phaseDays" to phase.label,
      "phaseAsset" to phase.assetFile,
      "simulatedDay" to (p.simulatedDay != null),
    )
  }
}

class JournalTools(private val db: RecoveryDatabase, private val patients: PatientStore) {
  @Tool(name = "log_checkin", description = "Saves today's check-in to the patient's journal. Call it once per check-in, after the patient answered.")
  suspend fun logCheckin(
    @Param("Pain from 0 (none) to 10 (worst)") pain: Int,
    @Param("Body temperature in Celsius if measured, otherwise null") temperatureC: Double? = null,
    @Param("Ids of the exercises completed today, from the phase asset") exercisesDone: List<String>? = null,
    @Param("Symptoms reported, free text, or null") symptoms: String? = null,
    @Param("Any other notes") notes: String? = null,
  ): Map<String, Any?> {
    val p = patients.current() ?: return mapOf(FunctionTool.ERROR_KEY to "No patient configured yet.")
    val day = p.dayToday()
    val id =
      db.checkins()
        .insert(
          CheckinEntity(
            atEpochMs = System.currentTimeMillis(),
            dayNumber = day,
            pain = pain.coerceIn(0, 10),
            temperatureC = temperatureC,
            exercisesDone = exercisesDone.orEmpty().joinToString(","),
            symptoms = symptoms,
            notes = notes,
          )
        )
    Timber.i("Check-in saved (day %d, pain %d)", day, pain)
    return mapOf("saved" to true, "checkinId" to id, "dayNumber" to day, "totalCheckins" to db.checkins().count())
  }

  @Tool(name = "get_recent_checkins", description = "The patient's check-ins from the last N days (max 14), newest first.")
  suspend fun getRecentCheckins(@Param("Days back, max 14") days: Int? = 7): List<Map<String, Any?>> {
    val p = patients.current() ?: return emptyList()
    val fromDay = p.dayToday() - (days ?: 7).coerceIn(1, 14)
    return db.checkins().since(fromDay).map {
      mapOf(
        "day" to it.dayNumber,
        "at" to dateTime.format(Instant.ofEpochMilli(it.atEpochMs)),
        "pain" to it.pain,
        "temperatureC" to it.temperatureC,
        "exercisesDone" to it.exercisesDone.split(',').filter(String::isNotBlank),
        "symptoms" to it.symptoms,
        "notes" to it.notes,
      )
    }
  }

  @Tool(name = "get_latest_wound_observation", description = "The most recent wound-photo observation produced on the device, as structured text. Never returns the image.")
  suspend fun getLatestWoundObservation(): Map<String, Any?> {
    val o = db.wounds().latest() ?: return mapOf("observation" to null, "note" to "No wound photo has been analyzed yet.")
    return mapOf(
      "capturedAt" to dateTime.format(Instant.ofEpochMilli(o.capturedAtEpochMs)),
      "dayNumber" to o.dayNumber,
      "rednessAroundIncision" to o.rednessAroundIncision,
      "discharge" to o.discharge,
      "edgesClosed" to o.edgesClosed,
      "swelling" to o.swelling,
      "imageQuality" to o.imageQuality,
      "freeText" to o.freeText,
      "photoArtifact" to o.artifactName,
      "analyzedOnDevice" to true,
    )
  }
}

/**
 * Stub care-team channel: it writes to a local inbox (the Data screen). In a real app this would be
 * the clinic's API; the point of the demo is that both tools need the patient's approval first.
 */
class CareTeamClient(private val db: RecoveryDatabase, private val patients: PatientStore) {
  suspend fun send(kind: String, summary: String): Long {
    val contact = patients.current()?.careTeamContact.orEmpty().ifBlank { "care team" }
    return db.outbound().insert(OutboundEntity(atEpochMs = System.currentTimeMillis(), kind = kind, summary = "To $contact: $summary"))
  }

  suspend fun recordRejected(kind: String, summary: String) {
    db.outbound().insert(OutboundEntity(atEpochMs = System.currentTimeMillis(), kind = "${kind}_REJECTED", summary = summary))
  }
}

class CareTeamTools(private val careTeam: CareTeamClient) {
  @Tool(
    name = "escalate_to_care_team",
    description = "Sends a short summary of the patient's situation to their care team. The patient must approve it first.",
    requireConfirmation = true,
  )
  suspend fun escalate(
    @Param("One-line reason for escalating") reason: String,
    @Param("Summary of symptoms and the latest check-ins, 2 to 4 sentences") summary: String,
    @Param("ROUTINE, SOON or URGENT") urgency: String,
  ): Map<String, Any?> {
    val id = careTeam.send("ESCALATION", "[$urgency] $reason — $summary")
    Timber.i("Escalation sent (%s): %s", urgency, reason)
    return mapOf("sent" to true, "messageId" to id, "urgency" to urgency)
  }

  @Tool(name = "share_wound_photo", description = "Shares the latest wound photo with the care team. The patient must approve it first.", requireConfirmation = true)
  suspend fun sharePhoto(@Param("Artifact name of the photo, from get_latest_wound_observation") artifactName: String): Map<String, Any?> {
    val id = careTeam.send("PHOTO_SHARE", "Wound photo $artifactName")
    return mapOf("shared" to true, "messageId" to id, "artifactName" to artifactName)
  }
}
