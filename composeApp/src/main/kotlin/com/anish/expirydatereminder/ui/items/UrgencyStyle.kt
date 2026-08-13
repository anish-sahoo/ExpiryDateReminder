package com.anish.expirydatereminder.ui.items

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.domain.model.ExpiryStatus
import com.anish.expirydatereminder.ui.theme.urgencyPalette

/** Color, icon and label for a status, resolved once so rows and headers stay consistent. */
data class UrgencyStyle(
    val container: Color,
    val onContainer: Color,
    val accent: Color,
    val icon: ImageVector,
    val label: String,
)

@Composable
fun urgencyStyle(status: ExpiryStatus): UrgencyStyle {
    val palette = urgencyPalette()
    return when (status) {
        ExpiryStatus.EXPIRED -> UrgencyStyle(
            container = palette.expiredContainer,
            onContainer = palette.expiredContent,
            accent = palette.expiredAccent,
            icon = Icons.Filled.ErrorOutline,
            label = stringResource(R.string.badge_expired),
        )

        ExpiryStatus.EXPIRING_SOON -> UrgencyStyle(
            container = palette.soonContainer,
            onContainer = palette.soonContent,
            accent = palette.soonAccent,
            icon = Icons.Filled.Schedule,
            label = stringResource(R.string.badge_expiring_soon),
        )

        ExpiryStatus.OK -> UrgencyStyle(
            container = palette.okContainer,
            onContainer = palette.okContent,
            accent = palette.okAccent,
            icon = Icons.Filled.CheckCircle,
            label = stringResource(R.string.badge_ok),
        )
    }
}
