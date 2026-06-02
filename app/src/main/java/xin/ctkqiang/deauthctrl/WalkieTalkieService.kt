package xin.ctkqiang.deauthctrl

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import xin.ctkqiang.deauthctrl.manager.WalkieTalkieManager

/**
 * 对讲机前台服务
 *
 * Android Foreground Service，在对讲机功能运行时在状态栏显示持久通知。
 * 通知包含两个操作按钮：
 * - "LIVE 10s": 触发/停止免提模式（10 秒自动发送）
 * - "关闭": 停止对讲机服务
 *
 * ## 服务生命周期
 * - 由 WalkieTalkieViewModel.start() 通过 startForegroundService() 启动
 * - 由 WalkieTalkieViewModel.stop() 通过 stopService() 停止
 * - 通知内容通过 ACTION_UPDATE intent 实时更新（说话中/监听中状态切换）
 *
 * ## Intent Actions
 * - ACTION_TOGGLE_LIVE: 切换免提模式（开始/停止说话）
 * - ACTION_STOP: 停止服务并关闭通知
 * - ACTION_UPDATE: 更新通知内容（说话状态文字）
 *
 * ## 前台服务类型
 * Android 14+ (API 34+) 声明 foregroundServiceType="microphone"，
 * 在 AndroidManifest.xml 中注册。
 */
class WalkieTalkieService : Service() {

    companion object {
        const val CHANNEL_ID = "walkie_talkie_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_TOGGLE_LIVE = "xin.ctkqiang.deauthctrl.TOGGLE_LIVE"
        const val ACTION_STOP = "xin.ctkqiang.deauthctrl.STOP_SERVICE"
        const val ACTION_UPDATE = "xin.ctkqiang.deauthctrl.UPDATE_NOTIFICATION"

        /** 全局 WalkieTalkieManager 引用，由 ViewModel 在启动时注入 */
        var instance: WalkieTalkieManager? = null
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    /**
     * 处理 Intent 命令
     *
     * 根据 action 类型执行相应操作，然后调用 startForeground 显示/更新通知。
     * @return START_STICKY — 服务被杀后自动重启
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_LIVE -> {
                instance?.let {
                    if (!it.isTalking) it.startTalk()
                    else it.stopTalk()
                }
            }
            ACTION_STOP -> {
                instance?.stop()
                stopSelf()
            }
            ACTION_UPDATE -> {
                val talking = instance?.isTalking ?: false
                updateNotification(talking)
            }
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        return START_STICKY
    }

    /**
     * 更新通知内容
     *
     * 在对讲机状态变化时（开始/停止说话）由 ViewModel 通过 ACTION_UPDATE intent 触发。
     *
     * @param isTalking 当前是否正在发送音频
     */
    fun updateNotification(isTalking: Boolean) {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(isTalking))
    }

    /**
     * 构建通知
     *
     * 包含两个操作按钮：
     * 1. LIVE 10s / 停止说话（根据 isTalking 状态切换）
     * 2. 关闭（停止服务）
     *
     * @param isTalking 当前说话状态
     */
    private fun buildNotification(isTalking: Boolean = false): Notification {
        val piLive = PendingIntent.getService(this, 0, Intent(this, WalkieTalkieService::class.java).apply { action = ACTION_TOGGLE_LIVE }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val piStop = PendingIntent.getService(this, 1, Intent(this, WalkieTalkieService::class.java).apply { action = ACTION_STOP }, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("WiFi 对讲机")
            .setContentText(if (isTalking) "发送中..." else "正在监听")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_media_play, if (isTalking) "停止说话" else "LIVE 10s", piLive)
            .addAction(android.R.drawable.ic_delete, "关闭", piStop)
            .build()
    }

    /**
     * 创建通知渠道
     *
     * Android 8.0+ (API 26+) 必须创建 NotificationChannel 才能显示通知。
     * 渠道重要性设为 IMPORTANCE_LOW（不发出声音，仅状态栏显示）。
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "WiFi 对讲机", NotificationManager.IMPORTANCE_LOW).apply { description = "对讲机前台服务" }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
