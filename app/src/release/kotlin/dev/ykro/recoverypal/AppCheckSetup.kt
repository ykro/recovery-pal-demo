package dev.ykro.recoverypal

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/** Release builds attest with Play Integrity; the signing certificate's SHA-256 must be registered in Firebase. */
object AppCheckSetup {
  fun install(context: Context) {
    if (FirebaseApp.getApps(context).isEmpty()) return
    FirebaseAppCheck.getInstance()
      .installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
  }
}
