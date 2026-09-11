package dev.ykro.recoverypal.ui.wound

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.ykro.recoverypal.agent.AgentRuntime
import dev.ykro.recoverypal.agent.WoundObservation
import dev.ykro.recoverypal.data.PatientStore
import dev.ykro.recoverypal.data.RecoveryDatabase
import dev.ykro.recoverypal.data.WoundObservationEntity
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

enum class WoundStep { CAMERA, ANALYZING, RESULT, SAVED, UNAVAILABLE }

data class WoundUiState(
  val step: WoundStep = WoundStep.CAMERA,
  val preview: ImageBitmap? = null,
  val observation: WoundObservation? = null,
  val analysisMs: Long = 0,
  val offline: Boolean = false,
  val error: String? = null,
  val modelInstalled: Boolean = true,
)

/** Camera → on-device model → observation card. The JPEG becomes a private artifact; it never becomes prompt history. */
class WoundPhotoViewModel(private val context: Context, private val runtime: AgentRuntime, private val db: RecoveryDatabase, private val patients: PatientStore) : ViewModel() {
  private val _state = MutableStateFlow(WoundUiState(modelInstalled = runtime.isWoundModelInstalled()))
  val state: StateFlow<WoundUiState> = _state
  private var jpeg: ByteArray? = null
  private var artifactName: String? = null

  init {
    if (!runtime.isWoundModelInstalled()) _state.update { it.copy(step = WoundStep.UNAVAILABLE) }
  }

  fun onCaptured(bitmap: Bitmap) {
    viewModelScope.launch {
      val scaled = withContext(Dispatchers.Default) { downscale(bitmap, 768) }
      val bytes = ByteArrayOutputStream().use { out -> scaled.compress(Bitmap.CompressFormat.JPEG, 85, out); out.toByteArray() }
      jpeg = bytes
      _state.update { it.copy(step = WoundStep.ANALYZING, preview = scaled.asImageBitmap(), offline = !isOnline(), error = null) }
      try {
        val (observation, ms) = withContext(Dispatchers.IO) { runtime.analyzeWound(bytes) }
        _state.update { it.copy(step = WoundStep.RESULT, observation = observation, analysisMs = ms, offline = !isOnline()) }
      } catch (e: Exception) {
        Timber.e(e, "Wound analysis failed")
        _state.update { it.copy(step = WoundStep.CAMERA, error = e.message ?: "Analysis failed") }
      }
    }
  }

  fun retake() = _state.update { it.copy(step = WoundStep.CAMERA, preview = null, observation = null, error = null) }

  /** Persists the observation (Room) and the photo (private artifact of the episode session). */
  fun save() {
    val obs = _state.value.observation ?: return
    val bytes = jpeg ?: return
    viewModelScope.launch {
      val patient = patients.current() ?: return@launch
      val name = artifactName ?: runtime.savePhoto(patient.sessionId, bytes).also { artifactName = it }
      db.wounds()
        .insert(
          WoundObservationEntity(
            capturedAtEpochMs = System.currentTimeMillis(),
            dayNumber = patient.dayToday(),
            rednessAroundIncision = obs.rednessAroundIncision,
            discharge = obs.discharge,
            edgesClosed = obs.edgesClosed,
            swelling = obs.swelling,
            imageQuality = obs.imageQuality,
            freeText = obs.freeText,
            artifactName = name,
            analyzedOffline = _state.value.offline,
            analysisMs = _state.value.analysisMs,
          )
        )
      _state.update { it.copy(step = WoundStep.SAVED) }
    }
  }

  private fun isOnline(): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java)
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
  }

  private fun downscale(src: Bitmap, maxSide: Int): Bitmap {
    val longest = maxOf(src.width, src.height)
    if (longest <= maxSide) return src
    val scale = maxSide.toFloat() / longest
    return Bitmap.createScaledBitmap(src, (src.width * scale).toInt(), (src.height * scale).toInt(), true)
  }

  companion object {
    fun decode(bytes: ByteArray): Bitmap? = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
  }
}
