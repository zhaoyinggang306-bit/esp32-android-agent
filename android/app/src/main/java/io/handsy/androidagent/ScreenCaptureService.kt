package io.handsy.androidagent

import android.app.*
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.IBinder
import android.util.DisplayMetrics
import java.io.ByteArrayOutputStream

/** Latest JPEG is deliberately retained on-device for the future visual-agent loop. */
object ScreenFrames {
    @Volatile var latestJpeg: ByteArray? = null
        private set
    internal fun update(bytes: ByteArray) { latestJpeg = bytes }
}

class ScreenCaptureService : Service() {
    private var reader: ImageReader? = null
    private var display: android.hardware.display.VirtualDisplay? = null
    private var projection: MediaProjection? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val channel = NotificationChannel("capture", "Screen capture", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification = Notification.Builder(this, "capture")
            .setSmallIcon(android.R.drawable.ic_menu_view).setContentTitle("ESP32 Android Agent")
            .setContentText("正在读取屏幕以执行你的任务").build()
        startForeground(7, notification)
        val manager = getSystemService(MediaProjectionManager::class.java)
        @Suppress("DEPRECATION") val approval = intent?.getParcelableExtra("data") as? Intent ?: return START_NOT_STICKY
        projection = manager.getMediaProjection(intent.getIntExtra("resultCode", 0), approval).also { session ->
            // Required on current Android releases; also releases the foreground capture cleanly.
            session.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, null)
        }
        val metrics = DisplayMetrics(); @Suppress("DEPRECATION") getSystemService(android.view.WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2).also { output ->
            output.setOnImageAvailableListener({ source ->
                source.acquireLatestImage()?.use { image ->
                    // Copy only when somebody asks for a frame; keeping this bounded avoids image backlog.
                    val plane = image.planes[0]
                    val paddedWidth = plane.rowStride / plane.pixelStride
                    val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                    padded.copyPixelsFromBuffer(plane.buffer)
                    val bitmap = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
                    padded.recycle()
                    val bytes = ByteArrayOutputStream(); bitmap.compress(Bitmap.CompressFormat.JPEG, 75, bytes); bitmap.recycle()
                    ScreenFrames.update(bytes.toByteArray())
                }
            }, null)
            display = projection!!.createVirtualDisplay("ESP32 Agent capture", metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, output.surface, null, null)
        }
        return START_NOT_STICKY
    }
    override fun onDestroy() { display?.release(); display = null; projection?.stop(); projection = null; reader?.close(); reader = null; super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}
