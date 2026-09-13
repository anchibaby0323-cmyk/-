package com.example.switchprokeyfix

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private var service: IPrivilegedService? = null
    private var pendingCommand = 0
    private val permissionRequestCode = 1001

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            refreshLocalState()
            if (hasShizukuPermission() && pendingCommand != 0) {
                bindIfNeeded(pendingCommand)
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        runOnUiThread {
            status.text = "Shizuku Binder 已斷線。請重新啟動 Shizuku 後再試。"
        }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != permissionRequestCode) return@OnRequestPermissionResultListener
        runOnUiThread {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                status.text = "Shizuku：已授權 ✅\n正在建立特權服務連線…"
                val command = if (pendingCommand != 0) pendingCommand else 3
                bindIfNeeded(command)
            } else {
                pendingCommand = 0
                status.text = "Shizuku：授權被拒絕。\n請再按一次『授權 Shizuku』，並在彈出的視窗選擇允許。"
            }
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, PrivilegedService::class.java.name)
        )
            .tag("switch_pro_keyfix_bridge")
            .processNameSuffix("bridge")
            .version(21)
            .debuggable(true)
            .daemon(true)
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IPrivilegedService.Stub.asInterface(binder)
            val cmd = pendingCommand
            pendingCommand = 0
            if (cmd != 0) execute(cmd) else execute(3)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            runOnUiThread { status.text = "特權服務已斷線。請重新連線 Shizuku。" }
        }

        override fun onBindingDied(name: ComponentName) {
            service = null
            runOnUiThread { status.text = "Shizuku UserService 啟動後中止。請重啟 Shizuku 再試。" }
        }

        override fun onNullBinding(name: ComponentName) {
            service = null
            runOnUiThread { status.text = "Shizuku UserService 回傳空 Binder。" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 官方 Shizuku 流程需要監聽 Binder 與授權結果；上一版漏掉這一段。
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Switch Pro Key Fix v0.2.1"
            textSize = 24f
        }
        val description = TextView(this).apply {
            text = "HAC-013 · 057e:2009\n無 Root · 以 Shizuku + evdev/uinput 建立虛擬 Xbox 360 手把\n最終效果：手把 A/B/X/Y 會依鍵帽字母輸出。"
            textSize = 15f
            setPadding(0, 16, 0, 16)
        }
        status = TextView(this).apply {
            textSize = 15f
            setPadding(0, 16, 0, 24)
            text = "正在偵測 Shizuku…"
        }

        val grant = Button(this).apply {
            text = "授權 Shizuku"
            setOnClickListener { requestShizukuPermission() }
        }
        val diagnose = Button(this).apply {
            text = "診斷手把 / uinput"
            setOnClickListener { runCommand(3) }
        }
        val start = Button(this).apply {
            text = "開始全域修正"
            setOnClickListener { runCommand(1) }
        }
        val stop = Button(this).apply {
            text = "停止修正"
            setOnClickListener { runCommand(2) }
        }

        root.addView(title)
        root.addView(description)
        root.addView(status)
        root.addView(grant)
        root.addView(diagnose)
        root.addView(start)
        root.addView(stop)

        setContentView(ScrollView(this).apply { addView(root) })

        if (shizukuReady()) {
            bindIfNeeded(3)
        } else {
            refreshLocalState()
        }
    }

    override fun onDestroy() {
        try { Shizuku.removeBinderReceivedListener(binderReceivedListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderDeadListener(binderDeadListener) } catch (_: Throwable) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResultListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun hasShizukuPermission(): Boolean {
        return try {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun refreshLocalState() {
        val running = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        val granted = hasShizukuPermission()
        status.text = buildString {
            appendLine("Shizuku：${if (running) "已啟動 ✅" else "未啟動 ❌"}")
            appendLine("權限：${if (granted) "已授權 ✅" else "未授權"}")
            if (!running) {
                append("請先打開 Shizuku 並啟動服務。")
            } else if (!granted) {
                append("請按『授權 Shizuku』，App 會直接呼叫 Shizuku 的授權視窗。")
            } else {
                append("Shizuku 已就緒，可以開始診斷。")
            }
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                status.text = "Shizuku 尚未啟動。請先進入 Shizuku App 啟動服務。"
                return
            }
            if (Shizuku.isPreV11()) {
                status.text = "Shizuku 版本過舊，請更新至新版後再試。"
                return
            }
            if (hasShizukuPermission()) {
                status.text = "Shizuku 已授權 ✅\n正在連接特權服務…"
                bindIfNeeded(if (pendingCommand != 0) pendingCommand else 3)
                return
            }

            // 不依賴 Activity 的 onRequestPermissionsResult；Shizuku 有自己的 callback。
            status.text = "正在要求 Shizuku 授權…"
            Shizuku.requestPermission(permissionRequestCode)
        } catch (t: Throwable) {
            status.text = "Shizuku 授權呼叫失敗：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}"
        }
    }

    private fun shizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && hasShizukuPermission()
        } catch (_: Throwable) {
            false
        }
    }

    private fun runCommand(command: Int) {
        if (!shizukuReady()) {
            pendingCommand = command
            status.text = "Shizuku 尚未授權，正在請求權限…"
            requestShizukuPermission()
            return
        }
        bindIfNeeded(command)
    }

    private fun bindIfNeeded(command: Int) {
        if (service != null) {
            execute(command)
            return
        }
        pendingCommand = command
        status.text = "正在啟動 Shizuku 特權服務…"
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (t: Throwable) {
            pendingCommand = 0
            status.text = "特權服務啟動失敗：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}"
        }
    }

    private fun execute(command: Int) {
        val remote = service ?: return
        status.text = when (command) {
            1 -> "正在抓取 Switch Pro 並建立虛擬手把…"
            2 -> "正在停止修正…"
            else -> "正在診斷…"
        }

        Thread {
            val result = try {
                when (command) {
                    1 -> remote.startBridge()
                    2 -> remote.stopBridge()
                    else -> remote.getDiagnostics()
                }
            } catch (t: Throwable) {
                service = null
                "Binder 呼叫失敗：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}"
            }
            runOnUiThread { status.text = result }
        }.start()
    }
}
