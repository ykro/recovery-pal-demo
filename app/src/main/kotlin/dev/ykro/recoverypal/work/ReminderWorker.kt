package dev.ykro.recoverypal.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.ykro.recoverypal.MainActivity
import dev.ykro.recoverypal.R
import dev.ykro.recoverypal.RecoveryPalApp
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import timber.log.Timber

/** The daily trigger is the OS, not a chat: the worker only posts a notification, it never calls the model. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
  override suspend fun doWork(): Result {
    val app = applicationContext as RecoveryPalApp
    val patient = app.patients.current() ?: return Result.success()
    app.reminders.notifyNow(patient.dayToday())
    return Result.success()
  }
}

class ReminderScheduler(private val context: Context) {
  fun createChannel() {
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL, "Daily check-in", NotificationManager.IMPORTANCE_DEFAULT))
  }

  /** 24 h periodic work, first run delayed to the chosen reminder time. UPDATE keeps the schedule when the time changes. */
  fun schedule(hour: Int, minute: Int) {
    val now = LocalDateTime.now()
    var next = now.toLocalDate().atTime(LocalTime.of(hour, minute))
    if (!next.isAfter(now)) next = next.plusDays(1)
    val delay = Duration.between(now, next)
    val request =
      PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
        .setInitialDelay(delay.toMinutes(), TimeUnit.MINUTES)
        .addTag(WORK)
        .build()
    WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    Timber.i("Daily reminder scheduled at %02d:%02d (first in %d min)", hour, minute, delay.toMinutes())
  }

  fun cancel() = WorkManager.getInstance(context).cancelUniqueWork(WORK)

  fun canNotify(): Boolean =
    Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

  fun notifyNow(day: Int) {
    if (!canNotify()) return
    val intent = Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_CHECKIN, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    val pending = PendingIntent.getActivity(context, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val notification =
      NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_notification)
        .setContentTitle("Day $day check-in")
        .setContentText("Two minutes with Recovery Pal: pain, symptoms and today's exercises.")
        .setContentIntent(pending)
        .setAutoCancel(true)
        .build()
    context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
  }

  private companion object {
    const val CHANNEL = "daily-checkin"
    const val WORK = "daily-checkin"
    const val NOTIFICATION_ID = 42
  }
}
