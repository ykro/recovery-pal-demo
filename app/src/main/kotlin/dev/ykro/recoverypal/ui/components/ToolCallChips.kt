package dev.ykro.recoverypal.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ykro.recoverypal.ui.theme.AmberSoft
import dev.ykro.recoverypal.ui.theme.DangerSoft
import dev.ykro.recoverypal.ui.theme.IndigoSoft
import dev.ykro.recoverypal.ui.theme.SuccessSoft

/** One tool invocation as the UI sees it: pending until its response arrives. */
data class ToolChip(val id: String, val name: String, val isSkill: Boolean, val done: Boolean = false, val isError: Boolean = false, val summary: String = "")

/** The "agent is working" trace: skills load in amber, tools in indigo, done in green, errors in red. */
@Composable
fun ToolCallChip(chip: ToolChip, modifier: Modifier = Modifier) {
  val bg =
    when {
      chip.isError -> DangerSoft
      chip.done -> SuccessSoft
      chip.isSkill -> AmberSoft
      else -> IndigoSoft
    }
  Row(
    modifier.background(bg, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    when {
      chip.isError -> Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.error)
      chip.done -> Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
      else -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
    }
    Icon(if (chip.isSkill) Icons.Outlined.MenuBook else Icons.Outlined.Build, null, Modifier.size(16.dp))
    Text(chip.name, style = MaterialTheme.typography.labelLarge)
    if (chip.summary.isNotBlank()) Text("· ${chip.summary}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}
