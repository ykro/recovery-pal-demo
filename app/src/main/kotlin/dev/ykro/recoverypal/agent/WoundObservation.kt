package dev.ykro.recoverypal.agent

import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Purely descriptive categories. No "infected" or "severity": interpretation stays with the care team. */
@Serializable
data class WoundObservation(
  val rednessAroundIncision: String = "UNSURE", // NONE | MILD | MODERATE | MARKED | UNSURE
  val discharge: String = "UNSURE", // NONE | CLEAR | CLOUDY | BLOODY | UNSURE
  val edgesClosed: String = "UNSURE", // YES | PARTIALLY | NO | UNSURE
  val swelling: String = "UNSURE", // NONE | MILD | MODERATE | MARKED | UNSURE
  val imageQuality: String = "GOOD", // GOOD | BLURRY | DARK | INCISION_NOT_VISIBLE
  val freeText: String = "",
)

object WoundObservationSchema {
  private fun cat(desc: String, values: List<String>) = Schema(type = Type.STRING, description = desc, enum = values)

  val schema: Schema =
    Schema(
      type = Type.OBJECT,
      description = "Descriptive observation of a surgical wound photo. Categories only, no diagnosis.",
      properties =
        mapOf(
          "rednessAroundIncision" to cat("Redness of the skin around the incision", listOf("NONE", "MILD", "MODERATE", "MARKED", "UNSURE")),
          "discharge" to cat("Visible discharge on or around the incision", listOf("NONE", "CLEAR", "CLOUDY", "BLOODY", "UNSURE")),
          "edgesClosed" to cat("Whether the wound edges look closed", listOf("YES", "PARTIALLY", "NO", "UNSURE")),
          "swelling" to cat("Swelling around the area", listOf("NONE", "MILD", "MODERATE", "MARKED", "UNSURE")),
          "imageQuality" to cat("Quality of the photo", listOf("GOOD", "BLURRY", "DARK", "INCISION_NOT_VISIBLE")),
          "freeText" to Schema(type = Type.STRING, description = "What you see, at most 40 words, descriptive only"),
        ),
      required = listOf("rednessAroundIncision", "discharge", "edgesClosed", "swelling", "imageQuality", "freeText"),
    )

  private val json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

  fun parse(text: String): WoundObservation? {
    val trimmed = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val start = trimmed.indexOf('{')
    val end = trimmed.lastIndexOf('}')
    if (start < 0 || end <= start) return null
    return runCatching { json.decodeFromString<WoundObservation>(trimmed.substring(start, end + 1)) }.getOrNull()
  }
}
