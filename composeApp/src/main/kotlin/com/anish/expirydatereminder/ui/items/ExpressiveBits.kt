package com.anish.expirydatereminder.ui.items

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anish.expirydatereminder.domain.model.ExpiryStatus

/**
 * Expressive shape vocabulary.
 *
 * Material 3 Expressive ships a catalogue of non-rectangular shapes. Giving each urgency
 * tier its own silhouette means the status is legible before any text is read, and it
 * survives grayscale and color-blindness in a way a color swatch alone does not.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun shapeFor(status: ExpiryStatus): Shape = when (status) {
    // Spiky and unmissable.
    ExpiryStatus.EXPIRED -> MaterialShapes.Sunny.toShape()
    // Softly scalloped, draws the eye without alarming.
    ExpiryStatus.EXPIRING_SOON -> MaterialShapes.Cookie9Sided.toShape()
    // Calm and regular.
    ExpiryStatus.OK -> MaterialShapes.Circle.toShape()
}

/**
 * A shaped urgency marker.
 *
 * Grows slightly for the more urgent tiers, so scanning the list gives an impression of
 * severity from the silhouettes alone.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UrgencyGlyph(status: ExpiryStatus, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val style = urgencyStyle(status)
    val scale by animateFloatAsState(
        targetValue = when (status) {
            ExpiryStatus.EXPIRED -> 1f
            ExpiryStatus.EXPIRING_SOON -> 0.9f
            ExpiryStatus.OK -> 0.72f
        },
        label = "glyphScale",
    )
    Box(
        modifier
            .size(size * scale)
            .clip(shapeFor(status))
            .background(style.accent),
    )
}

/**
 * Photo container using an expressive silhouette rather than a rounded rectangle. Falls
 * back to a tinted shape when the item has no photo.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ShapedPhotoFrame(
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .clip(MaterialShapes.Cookie12Sided.toShape())
            .background(tint),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}
