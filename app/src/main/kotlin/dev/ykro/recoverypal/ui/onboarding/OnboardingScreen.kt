package dev.ykro.recoverypal.ui.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.ykro.recoverypal.R
import dev.ykro.recoverypal.data.Surgery
import dev.ykro.recoverypal.ui.theme.Canvas
import dev.ykro.recoverypal.ui.theme.Peach
import dev.ykro.recoverypal.ui.theme.PeachSoft
import dev.ykro.recoverypal.ui.theme.Teal
import java.time.LocalDate

data class OnboardingResult(val surgery: Surgery, val surgeryDate: LocalDate, val hour: Int, val minute: Int, val careTeam: String)

@Composable
fun OnboardingScreen(onDone: (OnboardingResult) -> Unit) {
  var surgery by remember { mutableStateOf<Surgery?>(null) }
  var daysAgo by remember { mutableStateOf("12") }
  var time by remember { mutableStateOf("09:00") }
  var careTeam by remember { mutableStateOf("nurse@clinic.example") }
  val days = daysAgo.toIntOrNull()
  val parsedTime = time.split(":").takeIf { it.size == 2 }?.let { (h, m) -> h.toIntOrNull()?.let { hh -> m.toIntOrNull()?.let { mm -> hh to mm } } }
  val valid = surgery != null && days != null && days in 0..120 && parsedTime != null

  Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
    Image(painterResource(R.drawable.onboarding_hero), null, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)), contentScale = ContentScale.Crop)
    Text("Recovery Pal", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
    Text("A daily companion for the weeks after surgery. It follows your protocol, keeps a journal and only contacts your care team when you say so.", style = MaterialTheme.typography.bodyLarge)
    Column(Modifier.fillMaxWidth().background(PeachSoft, RoundedCornerShape(14.dp)).padding(12.dp)) {
      Text("Educational demo, not a medical device", style = MaterialTheme.typography.titleSmall)
      Text("It does not diagnose. Follow your surgeon's instructions and call your care team when in doubt.", style = MaterialTheme.typography.bodySmall)
    }
    Text("Which surgery?", style = MaterialTheme.typography.titleMedium)
    Surgery.entries.forEach { s ->
      val selected = s == surgery
      Row(
        Modifier.fillMaxWidth()
          .clip(RoundedCornerShape(16.dp))
          .background(Color.White)
          .border(2.dp, if (selected) Teal else Color.Transparent, RoundedCornerShape(16.dp))
          .clickable { surgery = s }
          .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        Image(painterResource(s.imageRes), null, Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)))
        Column {
          Text(s.displayName, style = MaterialTheme.typography.titleMedium)
          Text(s.phases.joinToString(" · ") { "${it.title} (${it.label})" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }
    OutlinedTextField(value = daysAgo, onValueChange = { daysAgo = it.filter(Char::isDigit).take(3) }, label = { Text("Days since surgery (0 = today)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = time, onValueChange = { time = it.take(5) }, label = { Text("Daily reminder time (HH:MM)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = careTeam, onValueChange = { careTeam = it }, label = { Text("Care team contact (email or phone)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(4.dp))
    Button(
      onClick = { onDone(OnboardingResult(surgery!!, LocalDate.now().minusDays(days!!.toLong()), parsedTime!!.first, parsedTime.second, careTeam.trim())) },
      enabled = valid,
      modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
      Text("Start my recovery", style = MaterialTheme.typography.titleMedium)
    }
  }
}
