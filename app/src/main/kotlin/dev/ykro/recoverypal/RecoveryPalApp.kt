package dev.ykro.recoverypal

import android.app.Application
import dev.ykro.recoverypal.agent.AgentRuntime
import dev.ykro.recoverypal.data.PatientStore
import dev.ykro.recoverypal.data.RecoveryDatabase
import dev.ykro.recoverypal.work.ReminderScheduler
import timber.log.Timber

class RecoveryPalApp : Application() {
  val patients by lazy { PatientStore(this) }
  val database by lazy { RecoveryDatabase.create(this) }
  val agentRuntime by lazy { AgentRuntime(this, database, patients) }
  val reminders by lazy { ReminderScheduler(this) }

  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) Timber.plant(Timber.DebugTree())
    reminders.createChannel()
  }
}
