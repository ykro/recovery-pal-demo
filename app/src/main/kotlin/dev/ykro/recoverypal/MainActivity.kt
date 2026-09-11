package dev.ykro.recoverypal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import dev.ykro.recoverypal.ui.CheckinRoute
import dev.ykro.recoverypal.ui.RecoveryPalNavHost
import dev.ykro.recoverypal.ui.theme.RecoveryPalTheme

class MainActivity : ComponentActivity() {
  private var nav: NavHostController? = null

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    val openCheckin = intent?.getBooleanExtra(EXTRA_OPEN_CHECKIN, false) == true
    setContent {
      RecoveryPalTheme {
        val controller = rememberNavController().also { nav = it }
        RecoveryPalNavHost(application as RecoveryPalApp, controller, startAtCheckin = openCheckin)
      }
    }
  }

  /** The daily notification reopens the running activity straight into the check-in. */
  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    if (intent.getBooleanExtra(EXTRA_OPEN_CHECKIN, false)) nav?.navigate(CheckinRoute)
  }

  companion object {
    const val EXTRA_OPEN_CHECKIN = "open_checkin"
  }
}
