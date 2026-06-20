package io.shubham0204.startwithsmollm.ui.theme

import androidx.compose.ui.graphics.Color

// ===== Brand palette derived from the app icon gradient =====
// Icon gradient: Indigo #6366F1 → Violet #8B5CF6 → Purple #A855F7
// Warm-neutral surfaces preserved for readability; brand accents drive primary/secondary/tertiary.

// --- Light scheme ---
val LightPrimary = Color(0xFF6366F1)          // indigo-500 (icon start)
val LightOnPrimary = Color(0xFFFFFFFF)
val LightPrimaryContainer = Color(0xFFE0E1FD) // soft indigo tint
val LightOnPrimaryContainer = Color(0xFF1E1B5C)

val LightSecondary = Color(0xFF8B5CF6)        // violet-500 (icon mid)
val LightOnSecondary = Color(0xFFFFFFFF)
val LightSecondaryContainer = Color(0xFFEDE4FE)
val LightOnSecondaryContainer = Color(0xFF2E1065)

val LightTertiary = Color(0xFFA855F7)         // purple-500 (icon end)
val LightOnTertiary = Color(0xFFFFFFFF)
val LightTertiaryContainer = Color(0xFFF3E4FE)
val LightOnTertiaryContainer = Color(0xFF3B0764)

val LightError = Color(0xFFB42318)
val LightOnError = Color(0xFFFFFFFF)
val LightErrorContainer = Color(0xFFFEE4E2)
val LightOnErrorContainer = Color(0xFF7A1C13)

// Warm neutral surfaces (stone palette) — fluid, premium feel
val LightBackground = Color(0xFFF8F7F4)       // warm off-white (paper)
val LightOnBackground = Color(0xFF1C1B1A)
val LightSurface = Color(0xFFFFFFFF)
val LightOnSurface = Color(0xFF1C1B1A)
val LightSurfaceVariant = Color(0xFFEFEEEA)
val LightOnSurfaceVariant = Color(0xFF5C5B57)
val LightOutline = Color(0xFFD7D5CF)
val LightOutlineVariant = Color(0xFFE7E5DF)
val LightSurfaceContainer = Color(0xFFF1EFEB) // bubble background
val LightSurfaceContainerHigh = Color(0xFFE9E7E2) // input pill

// --- Dark scheme ---
val DarkPrimary = Color(0xFFA5A6F6)            // lifted indigo for dark surfaces
val DarkOnPrimary = Color(0xFF1E1B5C)
val DarkPrimaryContainer = Color(0xFF3730A3)
val DarkOnPrimaryContainer = Color(0xFFE0E1FD)

val DarkSecondary = Color(0xFFC4B5FD)          // lifted violet
val DarkOnSecondary = Color(0xFF2E1065)
val DarkSecondaryContainer = Color(0xFF5B21B6)
val DarkOnSecondaryContainer = Color(0xFFEDE4FE)

val DarkTertiary = Color(0xFFD8B4FE)           // lifted purple
val DarkOnTertiary = Color(0xFF3B0764)
val DarkTertiaryContainer = Color(0xFF7E22CE)
val DarkOnTertiaryContainer = Color(0xFFF3E4FE)

val DarkError = Color(0xFFFCA5A5)
val DarkOnError = Color(0xFF5A0F0A)
val DarkErrorContainer = Color(0xFF7A1C13)
val DarkOnErrorContainer = Color(0xFFFEE4E2)

// Warm-tinted true-dark surfaces (not pure black; feels softer)
val DarkBackground = Color(0xFF111111)
val DarkOnBackground = Color(0xFFECEAE5)
val DarkSurface = Color(0xFF161615)
val DarkOnSurface = Color(0xFFECEAE5)
val DarkSurfaceVariant = Color(0xFF222220)
val DarkOnSurfaceVariant = Color(0xFFB3B0A9)
val DarkOutline = Color(0xFF3D3C39)
val DarkOutlineVariant = Color(0xFF2A2927)
val DarkSurfaceContainer = Color(0xFF1C1C1A)
val DarkSurfaceContainerHigh = Color(0xFF252523)