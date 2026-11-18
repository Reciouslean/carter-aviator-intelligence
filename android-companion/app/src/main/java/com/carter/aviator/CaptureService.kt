package com.carter.aviator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

class CaptureService : Service() {
    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val PREFS = "carter_aviator_local"
        const val KEY_RESULTS = "ocr_candidates"
        private const val CHANNEL = "carter_capture"
        private const val NOTIFICATION_ID = 42
        @Volatile var isRunning = false
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var worker: HandlerThread? = null
    private var workerHandler: Handler? = null
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val processing = AtomicBoolean(false)
    private var lastCandidate = ""
    private var lastCandidateAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (data == null || resultCode == 0) { stopSelf(); return START_NOT_STICKY }
        startCapture(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = max(1, metrics.widthPixels)
            val height = max(1, metrics.heightPixels)
            val density = metrics.densityDpi
            worker = HandlerThread("CART3R-OCR").also { it.start() }
            workerHandler = Handler(worker!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            reader!!.setOnImageAvailableListener({ source ->
                val image = source.acquireLatestImage() ?: return@setOnImageAvailableListener
                if (processing.compareAndSet(false, true)) {
                    workerHandler?.post {
                        try { processImage(image) }
                        finally { image.close(); processing.set(false) }
                    } ?: run { image.close(); processing.set(false) }
                } else image.close()
            }, workerHandler)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, workerHandler)
            virtualDisplay = projection?.createVirtualDisplay(
                "CART3R Screen OCR", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader!!.surface, null, workerHandler
            )
            isRunning = true
        } catch (_: Exception) {
            stopSelf()
        }
    }

    private fun processImage(image: Image) {
        val bitmap = imageToBitmap(image) ?: return
        val input = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                val text = result.text
                // Only candidate extraction: OCR can mistake unrelated screen text for a multiplier.
                val regex = Regex("""(?i)(?<![\d.])(\d{1,6}(?:[.,]\d{1,3})?)\s*[x×](?!\w)""")
                val match = regex.findAll(text).lastOrNull()
                if (match != null) {
                    val valueText = match.groupValues[1].replace(',', '.')
                    val value = valueText.toDoubleOrNull()
                    if (value != null && value >= 1.0 && value <= 1000000.0) {
                        val candidate = String.format(Locale.US, "%.2fx", value)
                        val now = System.currentTimeMillis()
                        if (candidate != lastCandidate || now - lastCandidateAt > 12000) {
                            lastCandidate = candidate
                            lastCandidateAt = now
                            saveCandidate(candidate)
                        }
                    }
                }
            }
            .addOnCompleteListener { bitmap.recycle() }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun saveCandidate(candidate: String) {
        val prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val old = prefs.getString(KEY_RESULTS, "") ?: ""
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(java.util.Date())
        val updated = ("$timestamp  $candidate\n" + old).lineSequence().take(100).joinToString("\n")
        prefs.edit().putString(KEY_RESULTS, updated).apply()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "CART3R screen monitoring", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("CART3R screen monitoring active")
            .setContentText("On-device OCR is checking visible screen text. Tap Stop in CART3R to end.")
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        isRunning = false
        virtualDisplay?.release()
        reader?.close()
        projection?.stop()
        worker?.quitSafely()
        try { recognizer.close() } catch (_: Exception) {}
        super.onDestroy()
    }
}
