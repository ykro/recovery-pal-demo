package dev.ykro.recoverypal.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import dev.ykro.recoverypal.RecoveryPalApp
import dev.ykro.recoverypal.ui.checkin.CheckinScreen
import dev.ykro.recoverypal.ui.checkin.CheckinViewModel
import dev.ykro.recoverypal.ui.journal.JournalScreen
import dev.ykro.recoverypal.ui.onboarding.OnboardingScreen
import dev.ykro.recoverypal.ui.settings.SettingsScreen
import dev.ykro.recoverypal.ui.settings.SettingsViewModel
import dev.ykro.recoverypal.ui.today.TodayScreen
import dev.ykro.recoverypal.ui.wound.WoundPhotoScreen
import dev.ykro.recoverypal.ui.wound.WoundPhotoViewModel
import java.util.UUID
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable object OnboardingRoute
@Serializable object TodayRoute
@Serializable object CheckinRoute
@Serializable object WoundRoute
@Serializable object JournalRoute
@Serializable object SettingsRoute

@Composable
fun RecoveryPalNavHost(app: RecoveryPalApp, nav: NavHostController, startAtCheckin: Boolean) {
  val patient by app.patients.patient.collectAsStateWithLifecycle(initialValue = null)
  val scope = rememberCoroutineScope()
  NavHost(navController = nav, startDestination = if (startAtCheckin) CheckinRoute else TodayRoute) {
    composable<OnboardingRoute> {
      OnboardingScreen { r ->
        scope.launch {
          app.patients.onboard(r.surgery, r.surgeryDate, r.hour, r.minute, r.careTeam, "recovery-" + UUID.randomUUID().toString().take(8))
          app.reminders.schedule(r.hour, r.minute)
          nav.navigate(TodayRoute) { popUpTo(OnboardingRoute) { inclusive = true } }
        }
      }
    }
    composable<TodayRoute> {
      val p = patient
      if (p == null) {
        OnboardingScreen { r ->
          scope.launch {
            app.patients.onboard(r.surgery, r.surgeryDate, r.hour, r.minute, r.careTeam, "recovery-" + UUID.randomUUID().toString().take(8))
            app.reminders.schedule(r.hour, r.minute)
          }
        }
      } else {
        TodayScreen(
          app = app,
          patient = p,
          onCheckin = { nav.navigate(CheckinRoute) },
          onPhoto = { nav.navigate(WoundRoute) },
          onJournal = { nav.navigate(JournalRoute) },
          onSettings = { nav.navigate(SettingsRoute) },
        )
      }
    }
    composable<CheckinRoute> {
      val vm: CheckinViewModel = viewModel(factory = viewModelFactory { initializer { CheckinViewModel(app.agentRuntime, app.patients) } })
      CheckinScreen(vm, onBack = { if (!nav.popBackStack()) nav.navigate(TodayRoute) }, onTakePhoto = { nav.navigate(WoundRoute) })
    }
    composable<WoundRoute> {
      val vm: WoundPhotoViewModel = viewModel(factory = viewModelFactory { initializer { WoundPhotoViewModel(app, app.agentRuntime, app.database, app.patients) } })
      WoundPhotoScreen(vm, onBack = { nav.popBackStack() }, onOpenSettings = { nav.navigate(SettingsRoute) })
    }
    composable<JournalRoute> { JournalScreen(app, onBack = { nav.popBackStack() }) }
    composable<SettingsRoute> {
      val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app, app.patients, app.database, app.agentRuntime, app.reminders) } })
      SettingsScreen(vm, onBack = { nav.popBackStack() }, onDataDeleted = { nav.navigate(TodayRoute) { popUpTo(0) } })
    }
  }
}
