package dev.ykro.recoverypal.agent

import android.content.Context
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.models.Model
import com.google.adk.kt.skills.AssetSkillSource
import com.google.adk.kt.tools.LoadMemoryTool
import com.google.adk.kt.tools.SkillToolset
import com.google.adk.kt.types.GenerateContentConfig
import com.google.adk.kt.types.ThinkingConfig
import com.google.adk.kt.types.ThinkingLevel
import dev.ykro.recoverypal.data.Patient

/** Cloud agent: runs the daily check-in against the surgery's protocol skill. */
object RecoveryAgent {
  const val NAME = "recovery_pal"
  const val OUTPUT_KEY = "last_reply"

  fun create(context: Context, model: Model, patient: Patient, protocol: ProtocolTools, journal: JournalTools, careTeam: CareTeamTools): LlmAgent =
    LlmAgent(
      name = NAME,
      model = model,
      description = "Post-operative recovery companion following a phase-based protocol skill.",
      instruction = Instruction(instruction(patient)),
      tools = protocol.generatedTools() + journal.generatedTools() + careTeam.generatedTools() + LoadMemoryTool(),
      toolsets = listOf(SkillToolset(AssetSkillSource.fromContext(context, skillsBaseDir = "skills"))),
      outputKey = OUTPUT_KEY,
      generateContentConfig = GenerateContentConfig(temperature = 0.3f, thinkingConfig = ThinkingConfig(thinkingLevel = ThinkingLevel.LOW)),
    )

  private fun instruction(p: Patient) =
    """
    You are Recovery Pal, a warm and concise companion for a patient recovering from
    ${p.surgery.displayName}, operated on ${p.surgeryDate}. The protocol skill is `${p.surgery.skillName}`.

    Every check-in, in this order:
    1. Call `get_protocol_day` to learn today's day number, phase and the phase asset file.
    2. Call `load_skill` with `${p.surgery.skillName}`, then `load_skill_resource` with ONLY the asset for
       the current phase (the `phaseAsset` value). Never load another phase's asset.
    3. Greet the patient with the day number and phase, then ask about pain (0–10) and how they feel.
       Ask one thing at a time. Keep every message under 80 words.
    4. If the patient mentions a symptom that could be a complication (fever, spreading redness,
       discharge, calf pain, chest pain, shortness of breath, numb or blue toes, vomiting, pain that
       gets worse instead of better), load `assets/warning-signs.md` from the skill, compare, and if it
       matches propose `escalate_to_care_team` with a clear summary. The patient approves it in the app.
       If they reject, acknowledge and continue; never call it again in the same check-in.
    5. If the patient asks about the wound or a photo, call `get_latest_wound_observation` and use its
       text. Never ask to see the photo; it stays on the device. Offer `share_wound_photo` only if
       the observation or the symptoms justify it.
    6. When the patient refers to earlier days, habits or preferences, call `load_memory` to recall
       past check-ins before answering.
    7. When you have pain, symptoms and exercises done (or the patient says there is nothing more),
       call `log_checkin` exactly once, then close with one encouraging line and, if relevant, one
       reminder from the phase asset.

    Rules:
    - Never diagnose, never name medication doses, never change the surgeon's instructions. When in
      doubt, say "check with your care team".
    - Use the phase asset's exercise ids when logging exercises.
    - Plain text only, no Markdown headings, no bullet lists longer than three items.
    """
      .trimIndent()
}

/** On-device agent: describes a wound photo with categorical fields. No tools, no network. */
object WoundPhotoAgent {
  const val NAME = "wound_observer"

  fun create(model: Model): LlmAgent =
    LlmAgent(
      name = NAME,
      model = model,
      description = "Describes a surgical wound photo using fixed categories.",
      instruction =
        Instruction(
          """
          You look at one photo of a surgical wound or cast and describe it using only the response
          schema. Be literal: report what is visible, pick UNSURE when you cannot tell, and set
          imageQuality to INCISION_NOT_VISIBLE if there is no wound in frame. Never diagnose, never
          mention infection or treatment. freeText is at most 40 words. Reply with the JSON object only.
          """
            .trimIndent()
        ),
      outputSchema = WoundObservationSchema.schema,
      outputKey = "wound_observation",
    )
}
