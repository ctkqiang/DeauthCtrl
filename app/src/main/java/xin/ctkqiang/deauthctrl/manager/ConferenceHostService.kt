package xin.ctkqiang.deauthctrl.manager

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
import xin.ctkqiang.deauthctrl.MainActivity

/**
 * 局域网会议的前台服务 —— 只服务于屏幕共享源。
 *
 * Android 14+ 要求调用 getMediaProjection() 时进程内已有活跃的
 * mediaProjection 类型前台服务，因此采用与镜像模块相同的两步启动：
 * 1. [ACTION_PREPARE] → 进入前台模式（UI 随后发起 MediaProjection 授权）
 * 2. [ACTION_START_CAPTURE] → getMediaProjection → 写入
 *    [ConferenceManager.screenProjectionHolder]，会议管理器消费后开流
 * 3. [ACTION_STOP] → 释放投影并退出前台
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
                val projection = pm.getMediaProjection(resultCode, data)
                ConferenceManager.screenProjectionHolder = projection
                Log.d(TAG, "屏幕授权已就绪，交由会议管理器开流")
            }

            ACTION_STOP -> {
                runCatching { ConferenceManager.screenProjectionHolder?.stop() }
                ConferenceManager.screenProjectionHolder = null
                prepared = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        runCatching { ConferenceManager.screenProjectionHolder?.stop() }
        ConferenceManager.screenProjectionHolder = null
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

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("局域网会议运行中")
            .setContentText("正在共享屏幕画面")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "局域网会议", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }
}
