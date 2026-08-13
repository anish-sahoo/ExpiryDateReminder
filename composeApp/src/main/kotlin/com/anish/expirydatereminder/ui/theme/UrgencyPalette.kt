package com.anish.expirydatereminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Urgency colors, deliberately fixed rather than derived from the wallpaper.
 *
 * Everything else in the app is Material You dynamic color, but "expiring soon" has to
 * mean the same thing on every device. Mapping it to `tertiaryContainer` made it come out
 * pink on some wallpapers and teal on others, which is not what a warning should do.
 * Material keeps its own error color fixed for the same reason.
 *
 * The tones are muted on purpose. Saturated red and amber fills across a whole list are
 * tiring to read, so the containers are soft washes and the saturation is reserved for
 * the small badge and dot.
 */
data class UrgencyPalette(
    val expiredContainer: Color,
    val expiredContent: Color,
    val expiredAccent: Color,
    val soonContainer: Color,
    val soonContent: Color,
    val soonAccent: Color,
    val okContainer: Color,
    val okContent: Color,
    val okAccent: Color,
)

private val LightUrgency = UrgencyPalette(
    // Soft clay rather than alarm red.
    expiredContainer = Color(0xFFFBEDEA),
    expiredContent = Color(0xFF6B3A32),
    expiredAccent = Color(0xFFB3503C),
    // Warm sand, reads as "attention" without shouting.
    soonContainer = Color(0xFFFDF3E3),
    soonContent = Color(0xFF6A5330),
    soonAccent = Color(0xFFB0842F),
    // Barely tinted; "fine" should recede.
    okContainer = Color(0xFFF2F4F1),
    okContent = Color(0xFF4A5250),
    okAccent = Color(0xFF7C8B84),
)

private val DarkUrgency = UrgencyPalette(
    expiredContainer = Color(0xFF3A2B28),
    expiredContent = Color(0xFFF0C9C0),
    expiredAccent = Color(0xFFD98E79),
    soonContainer = Color(0xFF383224),
    soonContent = Color(0xFFEDD8AE),
    soonAccent = Color(0xFFD3B072),
    okContainer = Color(0xFF262A29),
    okContent = Color(0xFFBCC6C2),
    okAccent = Color(0xFF8A9A93),
)

@Composable
fun urgencyPalette(): UrgencyPalette = if (isSystemInDarkTheme()) DarkUrgency else LightUrgency
