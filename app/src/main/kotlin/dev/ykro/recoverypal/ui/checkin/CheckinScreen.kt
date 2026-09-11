package dev.ykro.recoverypal.ui.checkin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ykro.recoverypal.R
import dev.ykro.recoverypal.agent.AgentRuntime
import dev.ykro.recoverypal.ui.components.ConfirmationSheet
import dev.ykro.recoverypal.ui.components.ToolCallChip
import dev.ykro.recoverypal.ui.theme.Danger
import dev.ykro.recoverypal.ui.theme.DangerSoft
import dev.ykro.recoverypal.ui.theme.SuccessSoft
import dev.ykro.recoverypal.ui.theme.Teal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckinScreen(viewModel: CheckinViewModel, onBack: () -> Unit, onTakePhoto: () -> Unit) {
  val state by viewModel.state.collectAsStateWithLifecycle()
  val listState = rememberLazyListState()
  var input by remember { mutableStateOf("") }

  LaunchedEffect(state.items.size, state.streamingText, state.checkinSaved) {
    val count = listState.layoutInfo.totalItemsCount
    if (count > 0) listState.animateScrollToItem(count - 1)
  }

  Scaffold(
    topBar = {
      TopAppBar(
        title = {
          Column {
            Text("Day ${state.day} check-in", style = MaterialTheme.typography.titleLarge)
            Text("${state.phaseTitle} · cloud · ${AgentRuntime.CLOUD_MODEL}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        actions = { IconButton(onClick = onTakePhoto) { Icon(Icons.Outlined.PhotoCamera, "Wound photo") } },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
      )
    },
    containerColor = MaterialTheme.colorScheme.background,
    bottomBar = {
      if (state.fatal == null) {
        Row(Modifier.background(MaterialTheme.colorScheme.background).padding(12.dp).imePadding(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = input,
            onValueChange = { input = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(if (state.busy) "Recovery Pal is thinking…" else "Type your answer") },
            enabled = !state.busy,
            maxLines = 3,
          )
          FilledIconButton(onClick = { viewModel.say(input); input = "" }, enabled = !state.busy && input.isNotBlank()) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
        }
      }
    },
  ) { padding ->
    if (state.fatal != null) {
      Column(Modifier.fillMaxSize().padding(padding).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(state.fatal!!, color = Danger)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onBack) { Text("Back") }
      }
      return@Scaffold
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (state.resumed) item { Text("Resumed from the saved session", style = MaterialTheme.typography.labelMedium, color = Teal) }
      items(state.items) { TimelineRow(it) }
      state.streamingText?.let { item { AgentBubble(it, streaming = true) } }
      if (state.busy && state.streamingText == null) item { Thinking() }
      if (state.checkinSaved && !state.busy) {
        item {
          Column(Modifier.fillMaxWidth().background(SuccessSoft, RoundedCornerShape(16.dp)).padding(14.dp)) {
            Text("Check-in saved to your journal", style = MaterialTheme.typography.titleSmall)
            Text("Indexed in on-device memory so Recovery Pal remembers it next week.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
        }
      }
      item { Spacer(Modifier.height(8.dp)) }
    }
  }

  state.confirmation?.let { pending ->
    ConfirmationSheet(
      toolName = pending.toolName,
      args = pending.args,
      hint = pending.hint,
      busy = state.busy,
      confirmLabel = if (pending.toolName == "share_wound_photo") "Share photo" else "Send",
      onConfirm = { viewModel.resolveConfirmation(true) },
      onCancel = { viewModel.resolveConfirmation(false) },
    )
  }
}

@Composable
private fun TimelineRow(item: TimelineItem) {
  when (item) {
    is TimelineItem.Tools -> FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { item.chips.forEach { ToolCallChip(it) } }
    is TimelineItem.Agent -> AgentBubble(item.text, streaming = false)
    is TimelineItem.User ->
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Text(item.text, Modifier.background(Teal, RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)).padding(horizontal = 14.dp, vertical = 10.dp), color = Color.White, style = MaterialTheme.typography.bodyLarge)
      }
    is TimelineItem.System -> Text(item.text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    is TimelineItem.Failure -> Text(item.text, Modifier.fillMaxWidth().background(DangerSoft, RoundedCornerShape(12.dp)).padding(12.dp), color = Danger, style = MaterialTheme.typography.bodyMedium)
  }
}

@Composable
fun AgentBubble(text: String, streaming: Boolean) {
  Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Image(painterResource(R.drawable.agent_avatar), null, Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)))
    Text(
      text,
      Modifier.weight(1f, fill = false).background(MaterialTheme.colorScheme.surface, RoundedCornerShape(4.dp, 18.dp, 18.dp, 18.dp)).padding(horizontal = 14.dp, vertical = 10.dp),
      style = MaterialTheme.typography.bodyLarge,
      color = if (streaming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun Thinking() {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
    Text("Thinking…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
