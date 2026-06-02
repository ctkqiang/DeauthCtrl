package xin.ctkqiang.deauthctrl.mirroring.host

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import xin.ctkqiang.deauthctrl.MainActivity

/**
 * 前台服务：保持屏幕镜像在后台持续运行。
 *
 * ## 生命周期（两步启动 — Android 14 兼容）
 * 1. [ACTION_PREPARE] → 进入前台模式 + 启动 TCP 服务器 + 注册 NSD
 * 2. [ACTION_START_CAPTURE] → MediaProjection.getMediaProjection() + 屏幕采集
 * 3. [ACTION_STOP] → 释放所有资源并停止服务
 *
 * ## 为什么分两步？
 * Android 14+ 要求调用 getMediaProjection() 时进程内已有一个
 * 活跃的 mediaProjection 类型前台服务。因此必须在请求权限之前
 * 先启动前台服务。
 */
class MirroringHostService : Service() {

    companion object {
        private const val TAG = "MirroringHost"
        private const val CHANNEL_ID = "screen_mirror_channel"
        private const val NOTIFICATION_ID = 2001

        /** 仅进入前台模式 + 启动流服务器，不开始采集 */
        const val ACTION_PREPARE = "xin.ctkqiang.deauthctrl.action.PREPARE_MIRRORING"
        /** 收到 MediaProjection 授权后开始屏幕采集 */
        const val ACTION_START_CAPTURE = "xin.ctkqiang.deauthctrl.action.START_CAPTURE"
        const val ACTION_STOP = "xin.ctkqiang.deauthctrl.action.STOP_MIRRORING"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private lateinit var captureManager: ScreenCaptureManager
    private lateinit var videoServer: VideoStreamServer
    private lateinit var inputReceiver: InputReceiver
    private var pumpJob: Job? = null
    private var prepared = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        captureManager = ScreenCaptureManager()
        videoServer = VideoStreamServer()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> {
                // 第一步：进入前台，启动流服务器和输入接收器
                enterForeground()
                videoServer.dataChannel = captureManager.encodedDataChannel
                videoServer.start(serviceScope)
                pumpJob = serviceScope.launch { videoServer.pumpLoop() }

                val dm = resources.displayMetrics
                val injector = TouchInputInjector(dm.widthPixels, dm.heightPixels)
                inputReceiver = InputReceiver(injector)
                inputReceiver.start(serviceScope)

                prepared = true
                Log.d(TAG, "Host 已就绪（前台 + TCP 服务器）— 等待采集启动")
            }

            ACTION_START_CAPTURE -> {
                if (!prepared) {
                    // 未 prepare 就先 prepare
                    enterForeground()
                    prepared = true
                }
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                    ?: return START_NOT_STICKY

                // 此时前台服务已在运行 → getMediaProjection 检查通过
                val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = pm.getMediaProjection(resultCode, data)

                val dpi = resources.displayMetrics.densityDpi
                captureManager.start(projection, dpi)
                Log.d(TAG, "屏幕采集已启动")
            }

            ACTION_STOP -> {
                stopAll()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopAll()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun enterForeground() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification(),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun stopAll() {
        prepared = false
        pumpJob?.cancel()
        captureManager.release()
        videoServer.stop()
        if (::inputReceiver.isInitialized) inputReceiver.stop()
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("屏幕镜像运行中")
            .setContentText("正在共享屏幕")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "屏幕镜像", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
