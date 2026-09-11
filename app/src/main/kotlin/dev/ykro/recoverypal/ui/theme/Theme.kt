package dev.ykro.recoverypal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Recovery Pal palette: teal primary, peach accent, mint canvas (matches the generated art).
val Teal = Color(0xFF00897B)
val TealDark = Color(0xFF00695C)
val TealSoft = Color(0xFFCCEBE6)
val Peach = Color(0xFFFFAB91)
val PeachSoft = Color(0xFFFFE0D6)
val Canvas = Color(0xFFE6F4F1)
val Ink = Color(0xFF16302B)
val InkMuted = Color(0xFF4F6B65)
val Danger = Color(0xFFC62828)
val DangerSoft = Color(0xFFFFE0E0)
val Success = Color(0xFF2E7D32)
val SuccessSoft = Color(0xFFDDF2DF)
// Kept for the shared components copied from Bug Reporter.
val AmberSoft = PeachSoft
val IndigoSoft = TealSoft
val Indigo = Teal

private val scheme =
  lightColorScheme(
    primary = Teal,
    onPrimary = Color.White,
    primaryContainer = TealSoft,
    onPrimaryContainer = TealDark,
    secondary = Peach,
    onSecondary = Ink,
    secondaryContainer = PeachSoft,
    onSecondaryContainer = Ink,
    background = Canvas,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = TealSoft,
    onSurfaceVariant = InkMuted,
    outline = Color(0xFFB2D8D2),
    error = Danger,
    errorContainer = DangerSoft,
    onErrorContainer = Danger,
  )

private val shapes = Shapes(small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp), large = RoundedCornerShape(28.dp))

@Composable
fun RecoveryPalTheme(content: @Composable () -> Unit) {
  MaterialTheme(colorScheme = scheme, shapes = shapes, content = content)
}
