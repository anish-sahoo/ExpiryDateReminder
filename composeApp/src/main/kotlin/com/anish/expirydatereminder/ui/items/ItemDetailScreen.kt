package com.anish.expirydatereminder.ui.items

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anish.expirydatereminder.R
import com.anish.expirydatereminder.ui.common.categoryDisplayName
import com.anish.expirydatereminder.ui.common.format
import com.anish.expirydatereminder.ui.common.relativeExpiry
import com.anish.expirydatereminder.ui.common.rememberFeedback
import com.anish.expirydatereminder.ui.scan.rememberCameraCapture
import com.anish.expirydatereminder.ui.theme.Space
import org.koin.androidx.compose.koinViewModel

/**
 * Item detail.
 *
 * Exists mainly so a photo has somewhere to live at full size. The pre-2.0 app captured
 * only a camera thumbnail, which was too small to be a useful reminder; this shows the
 * full-resolution capture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    itemId: Long,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: ItemDetailViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var viewingPhoto by remember { mutableStateOf(false) }

    val feedback = rememberFeedback()
    val camera = rememberCameraCapture { bitmap -> viewModel.attachPhoto(bitmap) }
    val deletedMessage = stringResource(R.string.toast_item_deleted)

    LaunchedEffect(itemId) { viewModel.load(itemId) }
    LaunchedEffect(state.deleted) {
        if (state.deleted) {
            // The screen is going away, so a Toast is the only thing that will survive it.
            feedback.toast(deletedMessage)
            onBack()
        }
    }

    val item = state.item

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_done),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(itemId) }) {
                        Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.edit_title_edit))
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(
                            Icons.Outlined.Delete,
                            contentDescription = stringResource(R.string.action_delete),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    }
                },
            )
        },
    ) { padding ->
        if (item == null) return@Scaffold

        val style = urgencyStyle(state.status)

        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.md),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // The frame takes the photo's own aspect ratio, so nothing is cropped away.
            // Clamped at both ends: an extreme panorama or a very tall shot would
            // otherwise either vanish to a sliver or push everything else off screen.
            val photoRatio = rememberImageAspectRatio(item.imagePath)
            val frameRatio = photoRatio?.coerceIn(MIN_FRAME_RATIO, MAX_FRAME_RATIO) ?: DEFAULT_FRAME_RATIO

            // The empty frame *is* the add button. A separate button beside an empty
            // placeholder was two controls for one job.
            Surface(
                color = style.container,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(frameRatio)
                    .clickable {
                        if (item.imagePath != null) viewingPhoto = true else camera.capture()
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (item.imagePath != null) {
                        ItemPhoto(
                            item.imagePath,
                            Modifier.fillMaxSize(),
                            cornerRadius = 0.dp,
                            contentScale = ContentScale.Fit,
                        )
                        // Actions float over the photo so they never push it around.
                        Row(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(Space.sm),
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                        ) {
                            PhotoAction(Icons.Outlined.CameraAlt, R.string.detail_replace_photo) {
                                camera.capture()
                            }
                            PhotoAction(Icons.Outlined.Delete, R.string.detail_delete_photo) {
                                viewModel.removePhoto()
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Surface(
                                color = style.accent.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(50),
                            ) {
                                Row(
                                    Modifier.padding(horizontal = Space.md, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Outlined.CameraAlt,
                                        contentDescription = null,
                                        tint = style.accent,
                                        modifier = Modifier.size(20.dp),
                                    )
                                    Spacer(Modifier.size(Space.sm))
                                    Text(
                                        stringResource(R.string.detail_add_photo),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = style.accent,
                                    )
                                }
                            }
                            Spacer(Modifier.height(Space.sm))
                            Text(
                                stringResource(R.string.detail_tap_to_add_photo),
                                style = MaterialTheme.typography.bodySmall,
                                color = style.onContainer.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }

            Text(item.name, style = MaterialTheme.typography.displaySmall)

            Surface(
                color = style.container,
                contentColor = style.onContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(Space.md), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        UrgencyGlyph(state.status, size = 16.dp)
                        Spacer(Modifier.size(Space.sm))
                        Text(
                            relativeExpiry(item.daysUntilExpiry(state.today)),
                            style = MaterialTheme.typography.titleLarge,
                            color = style.accent,
                        )
                    }
                    DetailRow(
                        stringResource(R.string.detail_expires),
                        item.expiry.format(state.settings.dateFormat),
                    )
                    DetailRow(
                        stringResource(R.string.detail_category),
                        categoryDisplayName(item.categoryBuiltinKey, item.categoryName),
                    )
                }
            }

            val notes = item.notes
            if (!notes.isNullOrBlank()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(Space.md)) {
                        Text(
                            stringResource(R.string.detail_notes).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(Space.xs))
                        Text(notes, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            Button(
                onClick = { onEdit(itemId) },
                modifier = Modifier.fillMaxWidth().padding(bottom = Space.xl),
            ) {
                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(Space.sm))
                Text(stringResource(R.string.edit_title_edit))
            }
        }
    }

    val photoPath = item?.imagePath
    if (viewingPhoto && photoPath != null) {
        PhotoViewerDialog(imagePath = photoPath, onDismiss = { viewingPhoto = false })
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(item?.name.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) {
                    Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

/** Small translucent action chip that sits over a photo. */
@Composable
private fun PhotoAction(icon: androidx.compose.ui.graphics.vector.ImageVector, labelRes: Int, onClick: () -> Unit) {
    // Fixed black-on-white rather than theme colors: these sit over arbitrary photo
    // content, where a theme-derived tint can land invisible against the image.
    Surface(
        color = Color.Black.copy(alpha = 0.55f),
        shape = RoundedCornerShape(50),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Icon(
            icon,
            contentDescription = stringResource(labelRes),
            tint = Color.White,
            modifier = Modifier.padding(10.dp).size(20.dp),
        )
    }
}

/** Portrait 2:3 through landscape 16:9; beyond that the frame stops following the photo. */
private const val MIN_FRAME_RATIO = 0.66f
private const val MAX_FRAME_RATIO = 1.78f
private const val DEFAULT_FRAME_RATIO = 4f / 3f
