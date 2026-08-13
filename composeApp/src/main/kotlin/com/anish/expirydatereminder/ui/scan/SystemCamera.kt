package com.anish.expirydatereminder.ui.scan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.anish.expirydatereminder.logging.Log
import java.io.File

/**
 * Photo capture through the device's own camera app.
 *
 * Deliberately not a custom CameraX screen. The system camera gives people the shutter,
 * flash, zoom and HDR they already know, needs no CAMERA runtime permission when launched
 * by intent, and is a fraction of the code. A bespoke preview would only be worth building
 * for live continuous OCR, which this app does not do.
 */
class CameraCapture internal constructor(private val context: Context, private val launch: (Uri) -> Unit) {
    fun capture() {
        val file = File(context.cacheDir, CAPTURE_DIR).apply { mkdirs() }
            .let { File(it, "capture_${System.currentTimeMillis()}.jpg") }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        pendingFile = file
        launch(uri)
    }

    internal companion object {
        const val CAPTURE_DIR = "captures"

        /** Set immediately before launching and read back when the result arrives. */
        var pendingFile: File? = null
    }
}

/**
 * @param onCaptured receives an upright bitmap, or is not called if the user canceled.
 */
@Composable
fun rememberCameraCapture(onCaptured: (Bitmap) -> Unit): CameraCapture {
    val context = LocalContext.current

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val file = CameraCapture.pendingFile
        CameraCapture.pendingFile = null
        if (!success || file == null || !file.isFile) {
            Log.d(TAG, "Capture canceled or produced no file")
            return@rememberLauncherForActivityResult
        }
        val bitmap = decodeUpright(file)
        // The scratch file has served its purpose; the caller re-encodes what it keeps.
        file.delete()
        if (bitmap == null) Log.w(TAG, "Could not decode captured photo") else onCaptured(bitmap)
    }

    return remember(context) { CameraCapture(context) { uri -> launcher.launch(uri) } }
}

/**
 * Decodes and applies the EXIF orientation the camera recorded.
 *
 * Skipping this is why photos come out sideways: most cameras write the sensor buffer
 * as-is and describe the rotation in EXIF rather than rotating the pixels. It also matters
 * for OCR, which does badly on rotated text.
 */
private fun decodeUpright(file: File): Bitmap? = runCatching {
    val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
    val orientation = ExifInterface(file).getAttributeInt(
        ExifInterface.TAG_ORIENTATION,
        ExifInterface.ORIENTATION_NORMAL,
    )
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
        else -> return bitmap
    }
    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        .also { if (it !== bitmap) bitmap.recycle() }
}.getOrElse {
    Log.w(TAG, "Failed to decode capture", it)
    null
}

/**
 * Whether a camera exists at all. There is no runtime permission to request: launching the
 * system camera by intent does not need CAMERA, which is one more reason to prefer it.
 */
fun hasCamera(context: Context): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

@Suppress("unused")
private fun cameraPermissionGranted(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

private const val TAG = "CameraCapture"
