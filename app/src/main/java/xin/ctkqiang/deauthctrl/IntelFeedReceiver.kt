package xin.ctkqiang.deauthctrl

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import xin.ctkqiang.deauthctrl.manager.IntelFeedManager

/**
 * 情报中心订阅推送 —— 无服务器、无 API Key 的本地推送
 *
 * AlarmManager 周期唤醒(默认 15 分钟)拉取已订阅源的 RSS/Atom,
 * 与已推送链接集合去重后发本地通知。新条目用 ACTION_VIEW 直达原文。
 * 拉取逻辑与情报页共用 [IntelFeedManager],去重集合也共用 ——
 * 应用内看过的链接不会重复推送。
 */
class IntelFeedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val manager = IntelFeedManager(context)
        // 开机广播:闹钟在重启后被系统清空 —— 这里重新挂号
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            if (manager.isNotifyEnabled()) schedule(context)
            return
        }
        if (!manager.isNotifyEnabled()) return
        // Android 13+ 通知权限未授予时 notify() 静默无效 —— 不做事也比假动作干净
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val items = manager.refresh()
                val fresh = items.filter { manager.markNotified(it.link) }.take(5)
                if (fresh.isNotEmpty()) post(context, manager, fresh)
            } catch (_: Exception) {
            } finally {
                pending.finish()
            }
        }
    }

    private fun post(context: Context, manager: IntelFeedManager, items: List<IntelFeedManager.FeedItem>) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notif_intel_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = context.getString(R.string.notif_intel_channel_desc) }
            nm.createNotificationChannel(channel)
        }
        items.forEachIndexed { i, item ->
            val srcName = IntelFeedManager.SOURCES.firstOrNull { it.id == item.sourceId }?.name ?: item.sourceId
            val open = PendingIntent.getActivity(
                context,
                item.id.hashCode() and 0xFFFF,
                Intent(Intent.ACTION_VIEW, Uri.parse(item.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notif = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(srcName)
                .setContentText(item.title)
                .setStyle(NotificationCompat.BigTextStyle().bigText(item.title))
                .setAutoCancel(true)
                .setContentIntent(open)
                .setWhen(item.timeMs)
                .build()
            runCatching { nm.notify(1000 + i, notif) }
        }
    }

    companion object {
        private const val CHANNEL_ID = "intel_feed"
        private const val REQ = 0x1FEE

        /** 启动周期轮询(15 分钟,系统按电量自动合并) */
        fun schedule(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                REQ,
                Intent(context, IntelFeedReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching {
                am.setInexactRepeating(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + 60_000L,
                    15 * 60_000L,
                    pi,
                )
            }
        }

        fun cancel(context: Context) {
            val am = context.getSystemService(AlarmManager::class.java)
            val pi = PendingIntent.getBroadcast(
                context,
                REQ,
                Intent(context, IntelFeedReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching { am.cancel(pi) }
        }
    }
}
