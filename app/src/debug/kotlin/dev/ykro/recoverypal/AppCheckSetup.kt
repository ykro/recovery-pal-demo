package dev.ykro.recoverypal

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import timber.log.Timber

/**
 * Debug builds attest with the App Check *debug provider*: Play Integrity cannot vouch for a debug
 * APK on an emulator. On first launch logcat prints
 * `Firebase App Check debug token: …`; register that token under
 * App Check → Recovery Pal → Manage debug tokens. The release source set installs Play Integrity instead.
 */
object AppCheckSetup {
  fun install(context: Context) {
    if (FirebaseApp.getApps(context).isEmpty()) {
      Timber.w("App Check skipped: Firebase is not configured (google-services.json missing)")
      return
    }
    FirebaseAppCheck.getInstance()
      .installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
    Timber.i("App Check: debug provider installed")
  }
}
