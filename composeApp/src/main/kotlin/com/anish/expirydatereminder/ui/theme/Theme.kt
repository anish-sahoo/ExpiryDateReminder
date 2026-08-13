package com.anish.expirydatereminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

/**
 * Material 3 Expressive theming.
 *
 * [MaterialExpressiveTheme] swaps in the expressive motion scheme, whose springs are
 * bouncier and more physical than the standard set, and unlocks the expressive component
 * family (shape morphing, button groups, FAB menus).
 *
 * Color is dynamic and comes from the user's wallpaper. The static schemes below are a
 * runtime fallback only; the pre-2.0 brand purple is deliberately not preserved.
 */
private val FallbackLight = lightColorScheme()
private val FallbackDark = darkColorScheme()

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EdrTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val scheme: ColorScheme = runCatching {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }.getOrElse { if (darkTheme) FallbackDark else FallbackLight }

    MaterialExpressiveTheme(
        colorScheme = scheme,
        motionScheme = MotionScheme.expressive(),
        shapes = EdrShapes,
        typography = EdrTypography,
        content = content,
    )
}
