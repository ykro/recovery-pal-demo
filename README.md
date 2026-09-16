# Recovery Pal — ADK for Kotlin demo

A post-operative companion that follows the patient for weeks: one daily check-in with a cloud
agent that reads the surgery's protocol **skill** phase by phase, a wound-photo observer that runs
**entirely on the device**, and a care-team escalation that never happens without the patient's
explicit approval. Built with [ADK for Kotlin](https://github.com/google/adk-kotlin) 1.0.1.

> Educational demo, not a medical device. It does not diagnose. Protocol text is illustrative.

One of three ADK for Kotlin demos, each a standalone repo. The other two: [Cart Shop](https://github.com/ykro/cart-shop-demo) · [Trail Aid](https://github.com/ykro/trail-aid-demo).

## What you'll learn

| ADK feature | Where |
|---|---|
| `SkillToolset` + `AssetSkillSource`: one skill per surgery, one asset per phase, loaded only when the day matches | `assets/skills/*`, `agent/Agents.kt` |
| Session resumability with `RoomSessionService`: **one session for the whole episode**, appended to every day | `agent/AgentRuntime.kt`, `ui/checkin/` |
| Context compaction (`App` + `EventsCompactionConfig` + `LlmEventSummarizer`) so a 6-week session stays bounded | `AgentRuntime.recoveryRunner`, Settings → Session |
| On-device memory with `AppSearchMemoryService` + `LoadMemoryTool` ("it hurts more in the mornings" recalled weeks later) | `AgentRuntime.indexSessionInMemory`, the `load_memory` chip |
| Hybrid: cloud `LlmAgent` (Firebase AI Logic, `gemini-3.8-flash`) and on-device `LlmAgent` (`LiteRtLmModel`, Gemma 4 E2B) sharing one app | `agent/Agents.kt` |
| Image input on device: the JPEG goes to the model as `Part(inlineData = Blob("image/jpeg"))` and never to the cloud | `AgentRuntime.analyzeWound` |
| Structured output (`outputSchema`) for the descriptive `WoundObservation` | `agent/WoundObservation.kt` |
| Human-in-the-loop (`requireConfirmation = true`) for `escalate_to_care_team` and `share_wound_photo` | `agent/Tools.kt`, `ui/components/ConfirmationSheet.kt` |
| `FileArtifactService` for private photo storage | `AgentRuntime.savePhoto` |
| WorkManager as the trigger: the OS posts "Day N check-in", the worker never calls the model | `work/ReminderWorker.kt` |
| Tool errors via `FunctionTool.ERROR_KEY` | `agent/Tools.kt` |

## Architecture

```mermaid
%%{init: {'theme':'base','themeVariables': {'lineColor':'#546E7A','textColor':'#212121','edgeLabelBackground':'#FFFFFF','fontSize':'14px'},'flowchart': {'wrappingWidth': 260, 'nodeSpacing': 28, 'rankSpacing': 48}}}%%
flowchart LR
  N["Day N notification<br/>WorkManager"] --> Chk["Check-in chat"]
  Chk --> RT["AgentRuntime<br/>compaction · memory"]
  RT --> AG["LlmAgent<br/>recovery_pal"]
  AG --> T["Tools<br/>protocol day · journal"]
  AG --> SK["Skills<br/>3 surgeries × phases"]
  AG --> ESC["escalate_to_care_team<br/>⚠︎ needs approval"]
  AG --> G["gemini-3.8-flash<br/>Firebase AI Logic"]

  classDef ui fill:#E0F2F1,stroke:#00897B,stroke-width:1.5px,color:#212121
  classDef agent fill:#FFFFFF,stroke:#00897B,stroke-width:2px,color:#212121
  classDef tool fill:#F5F5F5,stroke:#26A69A,stroke-width:1.5px,color:#212121
  classDef model fill:#B2DFDB,stroke:#00695C,stroke-width:2px,color:#212121
  classDef accent fill:#FFF3E0,stroke:#EF6C00,stroke-width:2px,color:#212121
  class N,Chk ui
  class RT,AG agent
  class T,SK tool
  class G model
  class ESC accent
```

The wound photo never touches the cloud: a second, tool-less agent runs on device.

```mermaid
%%{init: {'theme':'base','themeVariables': {'lineColor':'#546E7A','textColor':'#212121','edgeLabelBackground':'#FFFFFF','fontSize':'14px'},'flowchart': {'wrappingWidth': 260, 'nodeSpacing': 28, 'rankSpacing': 48}}}%%
flowchart LR
  P["Wound photo<br/>CameraX"] --> WA["WoundPhotoAgent<br/>no tools · outputSchema"]
  WA --> L["Gemma 4 E2B<br/>on device, never cloud"]
  WA --> J["WoundObservation<br/>saved to journal"]

  classDef ui fill:#E0F2F1,stroke:#00897B,stroke-width:1.5px,color:#212121
  classDef agent fill:#FFFFFF,stroke:#00897B,stroke-width:2px,color:#212121
  classDef tool fill:#F5F5F5,stroke:#26A69A,stroke-width:1.5px,color:#212121
  classDef accent fill:#FFF3E0,stroke:#EF6C00,stroke-width:2px,color:#212121
  class P ui
  class WA agent
  class J tool
  class L accent
```

### A check-in that escalates

```mermaid
sequenceDiagram
  autonumber
  participant P as Patient
  participant App as Recovery Pal
  participant A as RecoveryAgent
  participant M as AppSearch memory
  App->>A: check-in opened (day 12)
  Note over A: get_protocol_day → phase 1<br/>load_skill(ankle-fracture-orif)<br/>load_skill_resource(phase-1-days-0-14.md)
  A-->>P: "Day 12. Pain 0–10?"
  P->>App: "6, with fever and discharge"
  Note over A: load_skill_resource(warning-signs.md)
  A->>App: escalate_to_care_team → approval sheet
  alt approve
    P->>App: Send
    App->>A: confirmed = true → care team notified
  else cancel
    P->>App: Cancel
    App->>A: confirmed = false
  end
  Note over A: log_checkin(pain = 6, symptoms = …)
  App->>M: addSessionToMemory(session)
  A-->>P: closing line + phase reminder
```

## Screens

<table>
  <tr>
    <td align="center"><img src="docs/screenshots/01-onboarding.png" width="230" alt="Onboarding with the three surgeries and the disclaimer"><br><sub>Onboarding</sub></td>
    <td align="center"><img src="docs/screenshots/02-today.png" width="230" alt="Today screen with day, phase, last check-in and the phase text read from the skill asset"><br><sub>Today · phase text read from the skill</sub></td>
    <td align="center"><img src="docs/screenshots/11-notification.png" width="230" alt="Day 16 check-in notification posted by WorkManager"><br><sub>WorkManager reminder</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/03-checkin-chips.png" width="230" alt="Check-in with get_protocol_day, load_skill and one load_skill_resource chip"><br><sub>Check-in: protocol day → skill → one phase asset</sub></td>
    <td align="center"><img src="docs/screenshots/05-escalation-sheet.png" width="230" alt="Approval sheet for escalate_to_care_team with urgency and summary"><br><sub>Warning sign → approval sheet</sub></td>
    <td align="center"><img src="docs/screenshots/05b-escalation-sent.png" width="230" alt="Escalation sent after approval, check-in logged"><br><sub>Sent only after approval</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/04-wound-photo.png" width="230" alt="Wound photo camera with framing guide and on-device banner"><br><sub>Wound photo (CameraX)</sub></td>
    <td align="center"><img src="docs/screenshots/04c-wound-result.png" width="230" alt="Observation card with categorical results computed on device in 8.96 seconds"><br><sub>Observation, on device, no network</sub></td>
    <td align="center"><img src="docs/screenshots/10-memory-recall.png" width="230" alt="load_memory chip recalling a preference from an earlier check-in"><br><sub>load_memory recalls week-old preferences</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/screenshots/06-journal.png" width="230" alt="Journal with pain chart and check-in entries"><br><sub>Journal</sub></td>
    <td align="center"><img src="docs/screenshots/09-settings-session.png" width="230" alt="Settings showing one session with 50 events and 2 compaction summaries"><br><sub>One session, compaction summaries</sub></td>
    <td align="center"><img src="docs/screenshots/07-data-left-device.png" width="230" alt="Data that left the device: escalations only, never the photo"><br><sub>Data that left the device</sub></td>
  </tr>
</table>

More: [analyzing](docs/screenshots/04b-analyzing.png) · [saved to journal](docs/screenshots/04d-saved.png) · [settings](docs/screenshots/08-settings.png). All captured on the `Pixel_9_API_36` emulator.

## Setup

1. **Firebase**: register `dev.ykro.recoverypal` in your Firebase project, enable Firebase AI Logic
   and drop `google-services.json` into `app/`. The app builds without it; the check-in then reports
   "Firebase is not configured".
   **App Check**: Firebase AI Logic rejects unattested requests once enforcement is on. Register the
   signing certificate's SHA-256 (`./gradlew signingReport`) for Play Integrity; debug builds install
   the *debug provider* instead (`src/debug/.../AppCheckSetup.kt`), so on first launch copy the token
   logcat prints (`Firebase App Check debug token: …`) into App Check → *Manage debug tokens*.
2. **On-device model** (wound photos): Settings → *Download over Wi-Fi*, or
   `adb push gemma-4-E2B-it.litertlm /sdcard/Android/data/dev.ykro.recoverypal/files/`.
   Without it the photo screen says analysis is unavailable and never falls back to the cloud.
3. `./gradlew :app:installDebug` (JDK 17+; Gradle 9.7.1 / AGP 9.4.0 pinned by the wrapper).

## Demo script (~75 s)

1. Onboarding: pick *Ankle fracture (ORIF surgery)*, 12 days ago, reminder time, care-team contact.
2. Settings → *Notify now* → the "Day 12 check-in" notification opens the check-in.
3. Chips: `get_protocol_day` → `load_skill(ankle-fracture-orif)` → `load_skill_resource(assets/phase-1-days-0-14.md)`. Only phase 1.
4. Wound photo (emulator camera works): "Analyzing on your device · nothing leaves your phone",
   then the observation card. Turn on airplane mode first to show the "no network" badge.
5. Back in the check-in, mention fever and discharge: `load_skill_resource(assets/warning-signs.md)`
   → `escalate_to_care_team` → approval sheet → *Send*. Settings → *Data that left the device* lists
   the escalation; the photo is not there.
6. Settings → simulated *Day 30* → new check-in loads `phase-2-days-15-42.md`.
7. `adb shell am force-stop dev.ykro.recoverypal`, reopen mid check-in: the conversation resumes from Room.
8. After several check-ins, Settings → Session shows the event count and compaction summaries.

## Verified on the emulator (Pixel_9_API_36)

Onboarding, Today (phase asset read straight from the skill), on-device wound photo with the
emulated camera (schema-valid `WoundObservation` in ~3 min on the emulator CPU), journal, WorkManager
notification and its deep link into the check-in. The cloud check-in (skills, compaction, memory,
escalation) needs your `google-services.json`.

## Layout

```
app/src/main/kotlin/dev/ykro/recoverypal/
  agent/   RecoveryAgent (cloud) + WoundPhotoAgent (on-device), tools, schema, runtime, model store
  data/    Surgery/phase model, Room (check-ins, observations, outbound log), DataStore patient config
  work/    WorkManager reminder + notification
  ui/      onboarding, today, checkin, wound (CameraX), journal, settings
app/src/main/assets/skills/<surgery>/SKILL.md + assets/phase-*.md, warning-signs.md, ...
app/src/test/   phase boundaries, WoundObservation schema/parse
```
