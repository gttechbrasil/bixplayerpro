package pro.bixplayer.player.ui.theme

import androidx.compose.ui.graphics.Color

// Brand (Bix Player Pro): warm near-black surfaces with an orange accent. Contrast checked
// against WCAG AA: accent on background 7.6:1, onSurfaceVariant on surfaceVariant 6.9:1,
// dark text on the accent 7.0:1 (white text on the accent would fail, hence onPrimary is dark).
val BixAccent = Color(0xFFFF8A00)
val BixAccentLight = Color(0xFFFFB35C)
val BixAccentDark = Color(0xFFC96B00)

// Surfaces. The app is dark-only: on a TV a bright UI is fatiguing and washes out video.
val BixBackground = Color(0xFF1F1F13)
val BixSurface = Color(0xFF2B2B1E)
val BixSurfaceVariant = Color(0xFF3A3A2C)
val BixOnSurface = Color(0xFFF5F3EA)
val BixOnSurfaceVariant = Color(0xFFC9C6B5)

// Status
val BixSuccess = Color(0xFF34D399)
val BixWarning = Color(0xFFFBBF24)
val BixError = Color(0xFFF87171)

// Scrim used behind text drawn over artwork or video
val BixScrim = Color(0xCC000000)
