package xin.ctkqiang.deauthctrl.manager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import xin.ctkqiang.deauthctrl.MainActivity

/**
 * 局域网会议的前台服务 —— 只服务于屏幕共享源。
 *
 * Android 14+ 要求调用 getMediaProjection() 时进程内已有活跃的
 * mediaProjection 类型前台服务，因此采用与镜像模块相同的两步启动：
 * 1. [ACTION_PREPARE] → 进入前台模式（UI 随后发起 MediaProjection 授权）
 * 2. [ACTION_START_CAPTURE] → getMediaProjection → 显式交接给
 *    [ConferenceManager.handoffScreenProjection]（由会议管理器注册
 *    MediaProjection.Callback 并创建 VirtualDisplay）
 * 3. [ACTION_STOP] → 停止投影并退出前台
 *
 * 注意：一个 MediaProjection 授权会话只能 createVirtualDisplay 一次；
 * 停止后必须重新走系统授权，本服务不会后台静默重启采集。
 *
 * 摄像头源的会议不需要本服务。
 */
class ConferenceHostService : Service() {

    companion object {
        private const val TAG = "ConfHost"
        private const val CHANNEL_ID = "conference_channel"
        private const val NOTIFICATION_ID = 2002

        const val ACTION_PREPARE = "xin.ctkqiang.deauthctrl.action.PREPARE_CONFERENCE"
        const val ACTION_START_CAPTURE = "xin.ctkqiang.deauthctrl.action.START_CONF_CAPTURE"
        const val ACTION_STOP = "xin.ctkqiang.deauthctrl.action.STOP_CONFERENCE"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }

    private var prepared = false
    private var projection: MediaProjection? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> {
                enterForeground()
                prepared = true
                Log.d(TAG, "会议前台服务就绪 —— 等待屏幕授权")
            }

            ACTION_START_CAPTURE -> {
                if (!prepared) enterForeground()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, -1)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                    ?: return START_NOT_STICKY

                // 前台服务已在运行 → Android 14+ 检查通过
                val pm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val granted = runCatching { pm.getMediaProjection(resultCode, data) }.getOrElse { t ->
                    Log.e(TAG, "获取屏幕授权失败", t)
                    null
                }
                if (granted == null) return START_NOT_STICKY

                runCatching { projection?.stop() }
                projection = granted
                // 显式交接：MediaProjection 所有权交给会议管理器，
                // 由它负责 registerCallback（Android 14+ 必须）与 createVirtualDisplay
                ConferenceManager.handoffScreenProjection(granted)
                Log.d(TAG, "屏幕授权已交接给会议管理器")
            }

            ACTION_STOP -> {
                stopCapture()
                prepared = false
                // stopForeground(int) 是 API 24+ 方法，minSdk 23 必须回退到旧重载
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopCapture()
        super.onDestroy()
    }

    private fun stopCapture() {
        val current = projection
        projection = null
        // 触发会议管理器的 MediaProjection.Callback.onStop()，由管理器清理编码器与 VirtualDisplay
        runCatching { current?.stop() }
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

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("局域网会议运行中")
            .setContentText("屏幕共享需用户授权，停止后需重新授权")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        // NotificationChannel 是 API 26+；API 23-25 直接用通知构造器的 channelId 即可
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(CHANNEL_ID, "局域网会议", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
