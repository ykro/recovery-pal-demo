package dev.ykro.recoverypal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ykro.recoverypal.ui.theme.AmberSoft
import dev.ykro.recoverypal.ui.theme.Canvas

/** Human-in-the-loop gate: ADK paused before a care-team tool; nothing leaves the phone until the patient decides. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmationSheet(toolName: String, args: Map<String, Any?>, hint: String?, busy: Boolean, confirmLabel: String, onConfirm: () -> Unit, onCancel: () -> Unit) {
  val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = { if (!busy) onCancel() }, sheetState = state, containerColor = MaterialTheme.colorScheme.surface) {
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(Icons.Outlined.Shield, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.primary)
        Column {
          Text("Send this to your care team?", style = MaterialTheme.typography.titleLarge)
          Text("The agent wants to call `$toolName`", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      hint?.let {
        Spacer(Modifier.height(10.dp))
        Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.background(AmberSoft, RoundedCornerShape(10.dp)).padding(10.dp).fillMaxWidth())
      }
      Spacer(Modifier.height(14.dp))
      (args["reason"] as? String)?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
      (args["urgency"] as? String)?.let { Row(Modifier.padding(top = 6.dp)) { SuggestionChip(onClick = {}, label = { Text("Urgency: $it") }) } }
      Spacer(Modifier.height(10.dp))
      Column(Modifier.fillMaxWidth().heightIn(max = 340.dp).background(Canvas, RoundedCornerShape(14.dp)).padding(12.dp).verticalScroll(rememberScrollState())) {
        MarkdownText((args["summary"] as? String) ?: args.entries.joinToString("\n") { "**${it.key}**: ${it.value}" })
      }
      Spacer(Modifier.height(18.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onCancel, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Don't send") }
        Button(onClick = onConfirm, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (busy) "Working…" else confirmLabel) }
      }
    }
  }
}
