package ai.revrag.monitoring.kotlin

import android.content.Context
import android.graphics.Bitmap
import android.view.View
import androidx.core.view.drawToBitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class ScreenSnapshotPaths(
    val jsonPath: String,
    val screenshotPath: String?,
)

data class CaptureStatus(
    val captureCount: Int = 0,
    val lastScreen: String? = null,
    val lastJsonPath: String? = null,
    val lastScreenshotPath: String? = null,
    val lastCapturedAt: Long? = null,
    val lastBytesUploadedSkipped: Long = 0,
)

object ScreenCaptureStore {
    /** Mirrors Flutter's `widgetTreeSnapshotDebounceDuration` (500ms) + screenshot debounce (350ms). */
    private const val SCREENSHOT_DEBOUNCE_MS = 350L
    private const val MAX_PNG_BYTES = 1_048_576L

    private val captureCount = AtomicInteger(0)
    @Volatile private var latestSnapshotPaths: ScreenSnapshotPaths? = null
    private val lastScreenshotAt = mutableMapOf<String, Long>()

    private val _status = MutableStateFlow(CaptureStatus())
    val status: StateFlow<CaptureStatus> = _status

    fun latestPaths(): ScreenSnapshotPaths? = latestSnapshotPaths

    suspend fun saveSnapshot(
        context: Context,
        rootView: View,
        route: String,
        payload: Map<String, Any?>,
        capturePng: Boolean,
    ): ScreenSnapshotPaths = withContext(Dispatchers.IO) {
        val routeKey = sanitizeRoute(route)
        val structureDir = File(context.filesDir, "screenstructure").apply { mkdirs() }
        val screenshotDir = File(context.filesDir, "ss").apply { mkdirs() }

        val jsonFile = File(structureDir, "$routeKey.json")
        jsonFile.writeText(JSONObject(payload).toString())

        val screenshotPath = if (capturePng && shouldCaptureScreenshot(routeKey)) {
            captureScreenshotSafe(rootView, screenshotDir, routeKey)
        } else null

        val paths = ScreenSnapshotPaths(jsonPath = jsonFile.absolutePath, screenshotPath = screenshotPath)
        latestSnapshotPaths = paths
        val count = captureCount.incrementAndGet()
        _status.value = CaptureStatus(
            captureCount = count,
            lastScreen = route,
            lastJsonPath = paths.jsonPath,
            lastScreenshotPath = paths.screenshotPath,
            lastCapturedAt = System.currentTimeMillis(),
            lastBytesUploadedSkipped = 0,
        )
        paths
    }

    private suspend fun captureScreenshotSafe(rootView: View, screenshotDir: File, routeKey: String): String? {
        val bitmap = withContext(Dispatchers.Main) {
            runCatching { rootView.drawToBitmap(Bitmap.Config.ARGB_8888) }.getOrNull()
        } ?: return null
        return withContext(Dispatchers.IO) {
            val buffer = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, buffer)
            if (buffer.size().toLong() > MAX_PNG_BYTES) return@withContext null
            val shotFile = File(screenshotDir, "$routeKey.png")
            FileOutputStream(shotFile).use { out -> buffer.writeTo(out) }
            shotFile.absolutePath
        }
    }

    private fun shouldCaptureScreenshot(routeKey: String): Boolean {
        val now = System.currentTimeMillis()
        val last = lastScreenshotAt[routeKey] ?: 0L
        return if (now - last >= SCREENSHOT_DEBOUNCE_MS) {
            lastScreenshotAt[routeKey] = now
            true
        } else {
            false
        }
    }

    private fun sanitizeRoute(route: String): String =
        route.replace("/", "_").ifBlank { "root" }
}
