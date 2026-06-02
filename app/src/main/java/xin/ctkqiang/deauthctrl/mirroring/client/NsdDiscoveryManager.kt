package xin.ctkqiang.deauthctrl.mirroring.client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import xin.ctkqiang.deauthctrl.mirroring.model.MirroringDevice

/**
 * 通过 mDNS / NSD (Network Service Discovery) 扫描局域网中的屏幕镜像主机。
 *
 * ## 服务类型
 * `_screenmirror._tcp` — 主机通过 [NsdManager.registerService] 注册此类型，
 * 客户端通过 [NsdManager.discoverServices] 发现。
 *
 * ## 解析
 * NSD TXT 记录中包含 `video_port` 和 `input_port` 两个端口号，
 * 客户端据此建立 TCP 连接。
 */
class NsdDiscoveryManager(private val context: Context) {

    companion object {
        private const val TAG = "NsdDiscovery"
        const val SERVICE_TYPE = "_screenmirror._tcp."
        private const val KEY_VIDEO_PORT = "video_port"
        private const val KEY_INPUT_PORT = "input_port"
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    private val _devices = MutableStateFlow<List<MirroringDevice>>(emptyList())
    val devices: StateFlow<List<MirroringDevice>> = _devices

    private val deviceMap = mutableMapOf<String, MirroringDevice>()

    @Volatile var isDiscovering = false
        private set

    /** 开始扫描局域网 */
    fun startDiscovery() {
        if (isDiscovering) return
        isDiscovering = true
        deviceMap.clear()
        _devices.value = emptyList()
        nsdManager.discoverServices(
            SERVICE_TYPE,
            NsdManager.PROTOCOL_DNS_SD,
            discoveryListener,
        )
    }

    /** 注册为主机，供其他设备发现 */
    fun registerHost(videoPort: Int, inputPort: Int): Channel<Unit> {
        val done = Channel<Unit>(1)
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = "DeauthCtrl-Mirror"
            serviceType = SERVICE_TYPE
            port = videoPort // NSD 主端口填视频端口
            setAttribute(KEY_VIDEO_PORT, videoPort.toString())
            setAttribute(KEY_INPUT_PORT, inputPort.toString())
        }
        nsdManager.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(service: NsdServiceInfo?) {
                Log.d(TAG, "NSD 注册成功: ${service?.serviceName}")
                done.trySend(Unit)
            }
            override fun onRegistrationFailed(service: NsdServiceInfo?, error: Int) {
                Log.e(TAG, "NSD 注册失败: $error")
                done.trySend(Unit)
            }
            override fun onServiceUnregistered(service: NsdServiceInfo?) {
                Log.d(TAG, "NSD 注销: ${service?.serviceName}")
            }
            override fun onUnregistrationFailed(service: NsdServiceInfo?, error: Int) {}
        })
        return done
    }

    /** 注销 NSD 服务 */
    fun unregisterHost() {
        nsdManager.unregisterService(object : NsdManager.RegistrationListener {
            override fun onServiceUnregistered(service: NsdServiceInfo?) {}
            override fun onUnregistrationFailed(service: NsdServiceInfo?, error: Int) {}
            override fun onServiceRegistered(service: NsdServiceInfo?) {}
            override fun onRegistrationFailed(service: NsdServiceInfo?, error: Int) {}
        })
    }

    fun stopDiscovery() {
        isDiscovering = false
        nsdManager.stopServiceDiscovery(discoveryListener)
    }

    private val discoveryListener = object : NsdManager.DiscoveryListener {
        override fun onDiscoveryStarted(regType: String?) {
            Log.d(TAG, "NSD 扫描已启动")
        }

        override fun onServiceFound(service: NsdServiceInfo?) {
            service ?: return
            // 找到服务后解析其详细信息（IP + 端口 + TXT 记录）
            nsdManager.resolveService(service, resolveListener)
        }

        override fun onServiceLost(service: NsdServiceInfo?) {
            service ?: return
            deviceMap.remove(service.serviceName)
            _devices.value = deviceMap.values.toList()
        }

        override fun onDiscoveryStopped(regType: String?) {
            Log.d(TAG, "NSD 扫描已停止")
        }

        override fun onStartDiscoveryFailed(regType: String?, error: Int) {
            Log.e(TAG, "NSD 启动扫描失败: $error")
            isDiscovering = false
        }

        override fun onStopDiscoveryFailed(regType: String?, error: Int) {}
    }

    private val resolveListener = object : NsdManager.ResolveListener {
        override fun onResolveFailed(service: NsdServiceInfo?, error: Int) {
            Log.w(TAG, "NSD 解析失败: ${service?.serviceName} error=$error")
        }

        override fun onServiceResolved(resolved: NsdServiceInfo?) {
            resolved ?: return
            val host = resolved.host?.hostAddress ?: return
            val videoPort = resolved.attributes[KEY_VIDEO_PORT]?.let { String(it).toIntOrNull() }
                ?: resolved.port
            val inputPort = resolved.attributes[KEY_INPUT_PORT]?.let { String(it).toIntOrNull() }
                ?: (resolved.port + 1)

            val device = MirroringDevice(
                name = resolved.serviceName,
                hostAddress = host,
                videoPort = videoPort,
                inputPort = inputPort,
            )
            deviceMap[resolved.serviceName] = device
            _devices.value = deviceMap.values.toList()
            Log.d(TAG, "发现设备: $device")
        }
    }
}
