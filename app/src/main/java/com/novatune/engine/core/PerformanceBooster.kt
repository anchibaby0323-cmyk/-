package com.novatune.engine.core

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.hardware.display.DisplayManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Debug
import android.os.Process
import android.view.WindowManager
import com.novatune.engine.RuntimeCaches
import java.io.Closeable
import java.net.DatagramSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PerformanceBooster(context: Context) : Closeable {
    private val appContext = context.applicationContext
    private val activityManager = appContext.getSystemService(ActivityManager::class.java)
    private val connectivityManager = appContext.getSystemService(ConnectivityManager::class.java)
    private val displayManager = appContext.getSystemService(DisplayManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val networkLabel = AtomicReference("Unknown")

    private val _stats = MutableStateFlow(readStats())
    val stats: StateFlow<DeviceStats> = _stats.asStateFlow()

    private val cacheRegistration = RuntimeCaches.register { }
    private var sampler: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = refreshNetwork(network)

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            networkLabel.set(labelFor(caps))
        }

        override fun onLost(network: Network) {
            networkLabel.set("Disconnected")
        }
    }

    init {
        registerNetworkMonitoring()
        sampler = scope.launch {
            while (isActive) {
                _stats.value = readStats()
                delay(1_000)
            }
        }
    }

    suspend fun runManualLocalReclaim(): BoostResult = withContext(Dispatchers.Default) {
        val beforeHeap = usedJavaHeapBytes()
        val beforeAvail = readMemoryInfo().availMem
        val oldPriority = runCatching {
            Process.getThreadPriority(Process.myTid())
        }.getOrDefault(Process.THREAD_PRIORITY_DEFAULT)

        val priorityApplied = runCatching {
            Process.setThreadPriority(Process.THREAD_PRIORITY_FOREGROUND)
            true
        }.getOrDefault(false)

        try {
            RuntimeCaches.trim(android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN)
            System.gc()
            Runtime.getRuntime().runFinalization()
        } finally {
            runCatching { Process.setThreadPriority(oldPriority) }
        }

        val afterHeap = usedJavaHeapBytes()
        val afterAvail = readMemoryInfo().availMem
        _stats.value = readStats()

        BoostResult(
            localHeapFreedBytes = max(0L, beforeHeap - afterHeap),
            availableRamDeltaBytes = afterAvail - beforeAvail,
            workerPriorityApplied = priorityApplied
        )
    }

    fun requestHighestRefreshRate(activity: Activity): Float {
        val display = if (Build.VERSION.SDK_INT >= 30) {
            activity.display
        } else {
            @Suppress("DEPRECATION")
            (activity.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay
        } ?: return 0f

        val current = display.mode
        val best = display.supportedModes
            .filter {
                it.physicalWidth == current.physicalWidth &&
                    it.physicalHeight == current.physicalHeight
            }
            .maxByOrNull { it.refreshRate }
            ?: return display.refreshRate

        val params = activity.window.attributes
        params.preferredDisplayModeId = best.modeId
        activity.window.attributes = params
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        return best.refreshRate
    }

    fun configureLowLatencySocket(socket: Socket): Boolean = runCatching {
        socket.tcpNoDelay = true
        socket.trafficClass = 0x10
        true
    }.getOrDefault(false)

    fun configureLowLatencySocket(socket: DatagramSocket): Boolean = runCatching {
        socket.trafficClass = 0x10
        true
    }.getOrDefault(false)

    private fun readStats(): DeviceStats {
        val memory = readMemoryInfo()
        val javaHeap = usedJavaHeapBytes()
        val nativeHeap = Debug.getNativeHeapAllocatedSize()
        val threadCount = Thread.getAllStackTraces().keys.count { it.isAlive }
        val display = displayManager.getDisplay(android.view.Display.DEFAULT_DISPLAY)

        return DeviceStats(
            availMemBytes = memory.availMem,
            totalMemBytes = memory.totalMem,
            lowMemory = memory.lowMemory,
            lowMemoryThresholdBytes = memory.threshold,
            javaHeapUsedBytes = javaHeap,
            nativeHeapUsedBytes = nativeHeap,
            activeThreadCount = threadCount,
            network = networkLabel.get(),
            displayRefreshRateHz = display?.refreshRate ?: 0f
        )
    }

    private fun readMemoryInfo(): ActivityManager.MemoryInfo =
        ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)

    private fun usedJavaHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun registerNetworkMonitoring() {
        runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            connectivityManager.activeNetwork?.let(::refreshNetwork)
        }
    }

    private fun refreshNetwork(network: Network) {
        connectivityManager.getNetworkCapabilities(network)?.let {
            networkLabel.set(labelFor(it))
        }
    }

    private fun labelFor(caps: NetworkCapabilities): String {
        val transport = when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Mobile"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Other"
        }
        val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        return if (validated) "$transport · validated" else "$transport · unvalidated"
    }

    override fun close() {
        sampler?.cancel()
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        cacheRegistration.close()
        scope.cancel()
    }
}

data class DeviceStats(
    val availMemBytes: Long,
    val totalMemBytes: Long,
    val lowMemory: Boolean,
    val lowMemoryThresholdBytes: Long,
    val javaHeapUsedBytes: Long,
    val nativeHeapUsedBytes: Long,
    val activeThreadCount: Int,
    val network: String,
    val displayRefreshRateHz: Float
) {
    val usedRamFraction: Float
        get() = if (totalMemBytes <= 0L) 0f else
            ((totalMemBytes - availMemBytes).toDouble() / totalMemBytes.toDouble())
                .toFloat()
                .coerceIn(0f, 1f)
}

data class BoostResult(
    val localHeapFreedBytes: Long,
    val availableRamDeltaBytes: Long,
    val workerPriorityApplied: Boolean
)
