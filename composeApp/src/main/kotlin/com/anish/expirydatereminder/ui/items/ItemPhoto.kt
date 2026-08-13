package com.anish.expirydatereminder.ui.items

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.anish.expirydatereminder.images.ImageStore
import org.koin.compose.koinInject

/**
 * Renders an item photo from its stored relative path.
 *
 * Paths are resolved through [ImageStore] rather than embedded in the row, so moving the
 * storage location later touches one file. A missing file falls back to the placeholder
 * instead of showing a broken image: photos written by the pre-2.0 app lived in `cacheDir`
 * and may legitimately have been evicted before migration ever ran.
 */
@Composable
fun ItemPhoto(
    imagePath: String?,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = DEFAULT_PHOTO_RADIUS,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val imageStore: ImageStore = koinInject()
    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        val file = imagePath?.let { imageStore.fileFor(it) }?.takeIf { it.isFile }
        if (file == null) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        } else {
            AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * The stored photo's width/height ratio, or null while unknown.
 *
 * Decoded with `inJustDecodeBounds`, which reads the header only and never allocates the
 * pixels, so this is cheap enough to call during composition. Used to size the detail
 * frame to the photo instead of cropping it into a fixed 4:3 box.
 */

/** Thumbnail rounding. Smaller than any card radius, because it nests inside one. */
private val DEFAULT_PHOTO_RADIUS = 12.dp

@Composable
fun rememberImageAspectRatio(imagePath: String?): Float? {
    val imageStore: ImageStore = koinInject()
    return remember(imagePath) {
        val file = imagePath?.let { imageStore.fileFor(it) }?.takeIf { it.isFile } ?: return@remember null
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) {
            null
        } else {
            options.outWidth.toFloat() / options.outHeight.toFloat()
        }
    }
}
