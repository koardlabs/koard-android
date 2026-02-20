package com.payroc.terminal.ui.theme

import androidx.compose.ui.graphics.Color

// Payroc Brand Colors
val PayrocBlue = Color(0xFF0051BB)       // Primary brand blue - buttons, accents
val PayrocNavy = Color(0xFF001D4E)       // Deep navy - payment screen background
val PayrocDarkText = Color(0xFF191C26)   // Primary text (almost black)
val PayrocMediumGray = Color(0xFF636363) // Secondary text
val PayrocLightGray = Color(0xFFE5E5E5)  // Borders, dividers, inactive
val PayrocLightestGray = Color(0xFFF5F5F5) // Subtle backgrounds
val PayrocWhite = Color(0xFFFFFFFF)      // Primary background
val PayrocRed = Color(0xFFD93737)        // Error states, declined

// Status Colors
val StatusGreen = Color(0xFF22A550)      // Captured / Settled
val StatusAmber = Color(0xFFF59E0B)      // Pending / Surcharge pending
val StatusOrange = Color(0xFFF6693E)     // Authorized / Refunded / Reversed
val StatusRed = Color(0xFFD32F2F)        // Declined / Error
val StatusGray = Color(0xFF9E9E9E)       // Unknown / Canceled

// Legacy aliases kept for compatibility during transition
val KoardGreen800 = PayrocBlue
val KoardRed500 = PayrocRed
