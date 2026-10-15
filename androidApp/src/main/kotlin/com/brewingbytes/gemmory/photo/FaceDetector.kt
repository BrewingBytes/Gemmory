package com.brewingbytes.gemmory.photo

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import com.brewingbytes.gemmory.converter.FaceCheck
import com.brewingbytes.gemmory.telemetry.NonFatalReporter
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Face check on the cropped photo with ML Kit's bundled, on-device face detection: the photo never leaves the phone
 * (design doc, photo privacy). Any face applies the face rule to the whole photo (R3-4). A failure or a check longer
 * than 1 s counts as no face (design D4); a failure is also reported as a non-fatal.
 */
object FaceDetector {
    private const val TIMEOUT_MS = 1_000L

    private val detector by lazy {
        FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setMinFaceSize(0.1f)
                .build(),
        )
    }

    /**
     * Loads the model and runs one tiny detection while the player crops, so the first real check isn't spent on
     * model loading (seconds on a slow phone) and lands inside its 1 s budget.
     */
    fun warmUp(reporter: NonFatalReporter) {
        try {
            detector.process(InputImage.fromBitmap(createBitmap(64, 64), 0))
                .addOnFailureListener { reporter.report(FaceDetectorException("warm-up", it)) }
        } catch (e: Exception) {
            reporter.report(FaceDetectorException("init", e))
        }
    }

    suspend fun check(photo: Bitmap, reporter: NonFatalReporter): FaceCheck {
        val found = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                try {
                    detector.process(InputImage.fromBitmap(photo, 0))
                        .addOnSuccessListener { faces -> cont.resume(faces.isNotEmpty()) }
                        .addOnFailureListener {
                            reporter.report(FaceDetectorException("check", it))
                            cont.resume(false)
                        }
                        .addOnCanceledListener { cont.resume(false) }
                } catch (e: Exception) {
                    reporter.report(FaceDetectorException("init", e))
                    cont.resume(false)
                }
            }
        }
        return if (found == true) FaceCheck.FACE else FaceCheck.NO_FACE
    }
}

/** ML Kit face detection failed at [stage] (init, warm-up or check). The photo is never part of it. */
class FaceDetectorException(stage: String, cause: Throwable) : Exception("face detection $stage failed", cause)
