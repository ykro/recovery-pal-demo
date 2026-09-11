package dev.ykro.recoverypal.data

import androidx.annotation.DrawableRes
import dev.ykro.recoverypal.R
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A recovery phase as written in the skill: the agent must load only the asset for the current one. */
data class Phase(val number: Int, val title: String, val fromDay: Int, val toDay: Int?, val assetFile: String) {
  val label: String
    get() = if (toDay == null) "days $fromDay+" else "days $fromDay–$toDay"
}

enum class Surgery(val skillName: String, val displayName: String, val shortName: String, @DrawableRes val imageRes: Int, val phases: List<Phase>) {
  APPENDECTOMY(
    "laparoscopic-appendectomy",
    "Laparoscopic appendectomy",
    "appendectomy",
    R.drawable.surgery_appendectomy,
    listOf(
      Phase(1, "Rest and first steps", 0, 3, "assets/phase-1-days-0-3.md"),
      Phase(2, "Back on your feet", 4, 14, "assets/phase-2-days-4-14.md"),
      Phase(3, "Return to normal", 15, null, "assets/phase-3-days-15-42.md"),
    ),
  ),
  ANKLE_CONSERVATIVE(
    "ankle-fracture-conservative",
    "Ankle fracture (cast or boot)",
    "ankle fracture, cast",
    R.drawable.surgery_ankle_cast,
    listOf(
      Phase(1, "Immobilization", 0, 21, "assets/phase-1-days-0-21.md"),
      Phase(2, "Partial weight bearing", 22, 42, "assets/phase-2-days-22-42.md"),
      Phase(3, "Rehabilitation", 43, null, "assets/phase-3-days-43-plus.md"),
    ),
  ),
  ANKLE_ORIF(
    "ankle-fracture-orif",
    "Ankle fracture (ORIF surgery)",
    "ankle ORIF",
    R.drawable.surgery_ankle_orif,
    listOf(
      Phase(1, "After surgery", 0, 14, "assets/phase-1-days-0-14.md"),
      Phase(2, "Stitches out, first weight", 15, 42, "assets/phase-2-days-15-42.md"),
      Phase(3, "Physiotherapy", 43, null, "assets/phase-3-days-43-plus.md"),
    ),
  );

  fun phaseFor(day: Int): Phase = phases.lastOrNull { day >= it.fromDay } ?: phases.first()

  companion object {
    fun fromName(name: String?): Surgery? = entries.firstOrNull { it.name == name }
  }
}

/** Day 0 is the day of surgery. A simulated day (debug setting) overrides the calendar. */
fun protocolDay(surgeryDate: LocalDate, today: LocalDate, simulatedDay: Int?): Int =
  simulatedDay ?: ChronoUnit.DAYS.between(surgeryDate, today).toInt().coerceAtLeast(0)
