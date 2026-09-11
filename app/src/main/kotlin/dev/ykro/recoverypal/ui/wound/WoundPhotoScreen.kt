package dev.ykro.recoverypal.ui.wound

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import dev.ykro.recoverypal.R
import dev.ykro.recoverypal.agent.AgentRuntime
import dev.ykro.recoverypal.agent.WoundObservation
import dev.ykro.recoverypal.ui.theme.Peach
import dev.ykro.recoverypal.ui.theme.PeachSoft
import dev.ykro.recoverypal.ui.theme.SuccessSoft
import dev.ykro.recoverypal.ui.theme.Teal
import dev.ykro.recoverypal.ui.theme.TealSoft
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WoundPhotoScreen(viewModel: WoundPhotoViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Wound photo", style = MaterialTheme.typography.titleLarge)
            Text("On device · ${AgentRuntime.ON_DEVICE_MODEL_LABEL}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
  ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
      when (state.step) {
        WoundStep.UNAVAILABLE -> Unavailable(onOpenSettings)
        WoundStep.CAMERA -> CameraStep(state.error, onCaptured = viewModel::onCaptured)
        WoundStep.ANALYZING -> Analyzing(state)
        WoundStep.RESULT, WoundStep.SAVED -> ResultStep(state, onSave = viewModel::save, onRetake = viewModel::retake, onDone = onBack)
      }
    }
  }
}

@Composable
private fun PrivacyBanner(offline: Boolean) {
  Row(Modifier.fillMaxWidth().background(TealSoft, RoundedCornerShape(12.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Icon(if (offline) Icons.Outlined.CloudOff else Icons.Outlined.Lock, null, tint = Teal)
    Column {
      Text("Analyzed on your device", style = MaterialTheme.typography.titleSmall)
      Text(if (offline) "No network connection · nothing can leave the phone" else "The photo never leaves the phone", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun CameraStep(error: String?, onCaptured: (Bitmap) -> Unit) {
  val context = LocalContext.current
  var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
  val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
  LaunchedEffect(Unit) { if (!granted) launcher.launch(Manifest.permission.CAMERA) }
  PrivacyBanner(offline = false)
  Image(painterResource(R.drawable.wound_photo_hint), null, Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Fit)
  Text("Frame the wound inside the guide, in good light, about 20 cm away.", style = MaterialTheme.typography.bodyMedium)
  error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
  if (!granted) {
    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Allow camera") }
    return
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
  Box(Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp))) {
    AndroidView(
      factory = { ctx ->
        PreviewView(ctx).also { view ->
          val future = ProcessCameraProvider.getInstance(ctx)
          future.addListener(
            {
              val provider = future.get()
              val preview = Preview.Builder().build().also { it.surfaceProvider = view.surfaceProvider }
              provider.unbindAll()
              runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture) }
                .onFailure { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview, imageCapture) }
            },
            ContextCompat.getMainExecutor(ctx),
          )
        }
      },
      modifier = Modifier.fillMaxSize(),
    )
    Box(Modifier.fillMaxSize().padding(36.dp).border(3.dp, Peach, RoundedCornerShape(16.dp)))
  }
  Button(
    onClick = {
      imageCapture.takePicture(
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageCapturedCallback() {
          override fun onCaptureSuccess(image: ImageProxy) {
            val bitmap = image.toBitmap()
            val rotation = image.imageInfo.rotationDegrees
            image.close()
            val upright = if (rotation != 0) Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, android.graphics.Matrix().apply { postRotate(rotation.toFloat()) }, true) else bitmap
            onCaptured(upright)
          }

          override fun onError(exception: ImageCaptureException) = Unit
        },
      )
    },
    modifier = Modifier.fillMaxWidth().height(56.dp),
  ) {
    Text("Take photo", style = MaterialTheme.typography.titleMedium)
  }
}

@Composable
private fun Analyzing(state: WoundUiState) {
  PrivacyBanner(state.offline)
  state.preview?.let { Image(it, null, Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop) }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    Text("Analyzing on your device · nothing leaves your phone", style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
private fun ResultStep(state: WoundUiState, onSave: () -> Unit, onRetake: () -> Unit, onDone: () -> Unit) {
  val obs = state.observation ?: return
  PrivacyBanner(state.offline)
  state.preview?.let { Image(it, null, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(20.dp)), contentScale = ContentScale.Crop) }
  ObservationCard(obs, state.analysisMs)
  if (state.step == WoundStep.SAVED) {
    Column(Modifier.fillMaxWidth().background(SuccessSoft, RoundedCornerShape(16.dp)).padding(14.dp)) {
      Text("Saved to your journal", style = MaterialTheme.typography.titleSmall)
      Text("Recovery Pal can read this observation as text in your next check-in. Sharing the photo itself always asks you first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
  } else {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      OutlinedButton(onClick = onRetake, modifier = Modifier.weight(1f)) { Text("Retake") }
      Button(onClick = onSave, modifier = Modifier.weight(1f)) { Text("Save to journal") }
    }
  }
}

@Composable
fun ObservationCard(obs: WoundObservation, analysisMs: Long = 0) {
  Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(0.dp)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      Text("Observation", style = MaterialTheme.typography.labelLarge, color = Teal)
      ObsRow("Redness", obs.rednessAroundIncision)
      ObsRow("Discharge", obs.discharge)
      ObsRow("Edges closed", obs.edgesClosed)
      ObsRow("Swelling", obs.swelling)
      ObsRow("Image quality", obs.imageQuality)
      if (obs.freeText.isNotBlank()) Text(obs.freeText, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.background(PeachSoft, RoundedCornerShape(10.dp)).padding(10.dp).fillMaxWidth())
      if (analysisMs > 0) Text("Descriptive categories only, no diagnosis · ${analysisMs / 1000.0} s on device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun ObsRow(label: String, value: String) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
  }
}

@Composable
private fun Unavailable(onOpenSettings: () -> Unit) {
  Column(Modifier.fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text("Photo analysis is not available on this device", style = MaterialTheme.typography.titleMedium)
    Text("The on-device model is not installed. Recovery Pal never sends wound photos to the cloud by itself; you can still share a photo with your care team from a check-in, with your approval.", style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(4.dp))
    Button(onClick = onOpenSettings) { Text("Install the model in Settings") }
  }
}
