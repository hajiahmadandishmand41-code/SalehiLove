package com.salehilove.feature.personal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Decoded photo state; image files are decoded off the main thread. */
data class PrivatePhotoState(
    val bitmap: Bitmap? = null,
    val isLoaded: Boolean = false,
)

/**
 * Loads an app-private photo without blocking Compose's UI thread.
 * Images are down-sampled to limit memory use for large camera photos.
 */
@Composable
fun rememberPrivatePhotoState(
    path: String?,
    maxDimension: Int = 720,
): PrivatePhotoState {
    var state by remember(path, maxDimension) {
        mutableStateOf(PrivatePhotoState(isLoaded = path.isNullOrBlank()))
    }

    LaunchedEffect(path, maxDimension) {
        if (path.isNullOrBlank()) {
            state = PrivatePhotoState(isLoaded = true)
        } else {
            state = PrivatePhotoState(isLoaded = false)
            state = PrivatePhotoState(
                bitmap = decodePrivatePhoto(path, maxDimension.coerceAtLeast(128)),
                isLoaded = true,
            )
        }
    }

    return state
}

private suspend fun decodePrivatePhoto(path: String, maxDimension: Int): Bitmap? =
    withContext(Dispatchers.IO) {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                null
            } else {
                var sampleSize = 1
                while (
                    bounds.outWidth / sampleSize > maxDimension ||
                    bounds.outHeight / sampleSize > maxDimension
                ) {
                    sampleSize *= 2
                }
                BitmapFactory.decodeFile(
                    path,
                    BitmapFactory.Options().apply {
                        inSampleSize = sampleSize
                        inPreferredConfig = Bitmap.Config.ARGB_8888
                    },
                )
            }
        } catch (_: Exception) {
            null
        }
    }
