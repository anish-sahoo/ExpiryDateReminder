package com.anish.expirydatereminder.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Platform seam. iOS would supply a Vision-framework implementation of this interface. */
interface OcrEngine {
    suspend fun recognize(bitmap: Bitmap): String
}

/**
 * ML Kit Text Recognition v2, Play-services delivered rather than bundled: ~260 KB of APK
 * instead of ~4 MB per script. The model downloads on first use, which
 * [ScanAvailability.modelReady] accounts for.
 */
class MlKitOcrEngine : OcrEngine {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(bitmap: Bitmap): String = suspendCoroutine { continuation ->
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { continuation.resume(it.text) }
            .addOnFailureListener { continuation.resumeWithException(it) }
    }
}

/**
 * Decides whether to offer scanning at all, and separately whether the on-device LLM can
 * refine an ambiguous result.
 *
 * Kept as one named type on purpose. Restricting the whole feature to high-end devices
 * later should be a change here and nowhere else.
 *
 * The two tiers have very different reach: ML Kit OCR runs on effectively every device,
 * while Gemini Nano is flagship-only. Gating the scan button on Nano would withhold a
 * working feature from the large majority of users, so it is gated on the camera alone.
 */
class ScanAvailability(private val context: Context) {

    fun hasCamera(): Boolean = context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)

    fun playServicesAvailable(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Whether the scan affordance should be shown. True on essentially all devices. */
    fun scanSupported(): Boolean = hasCamera() && playServicesAvailable()
}
