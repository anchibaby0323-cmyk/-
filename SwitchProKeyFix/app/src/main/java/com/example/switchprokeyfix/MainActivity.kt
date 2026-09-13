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

    private fun setStatus(text: String) {
        if (::status.isInitialized) status.text = text
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            if (!::status.isInitialized) return@runOnUiThread
            refreshLocalState()
            if (hasShizukuPermission() && pendingCommand != 0) {
                bindIfNeeded(pendingCommand)
            }
        }
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        service = null
        runOnUiThread { setStatus("Shizuku Binder 已斷線。請重新啟動 Shizuku 後再試。") }
    }

    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != permissionRequestCode) return@OnRequestPermissionResultListener
        runOnUiThread {
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                setStatus("Shizuku：已授權 ✅\n正在建立特權服務連線…")
                val command = if (pendingCommand != 0) pendingCommand else 3
                bindIfNeeded(command)
            } else {
                pendingCommand = 0
                setStatus("Shizuku：授權被拒絕。\n請再按一次『授權 Shizuku』，並在彈出的視窗選擇允許。")
            }
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(ComponentName(packageName, PrivilegedService::class.java.name))
            .tag("switch_pro_keyfix_bridge")
            .processNameSuffix("bridge")
            .version(23)
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
            runOnUiThread { setStatus("特權服務已斷線。請重新連線 Shizuku。") }
        }

        override fun onBindingDied(name: ComponentName) {
            service = null
            runOnUiThread { setStatus("Shizuku UserService 啟動後中止。請重啟 Shizuku 再試。") }
        }

        override fun onNullBinding(name: ComponentName) {
            service = null
            runOnUiThread { setStatus("Shizuku UserService 回傳空 Binder。") }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Switch Pro Key Fix v0.4.0"
            textSize = 24f
        }
        val description = TextView(this).apply {
            text = "HAC-013 · 057e:2009\n無 Root · Shizuku + evdev/uinput\n完整模擬 Xbox Wireless Controller (045e:0b12)\nA/B/X/Y、十字鍵、雙搖桿、L/R、ZL/ZR、+/−、Home、截圖/Share 全部轉成 Xbox 參數。"
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
            text = "開始完整 Xbox 修正"
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

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)

        if (shizukuReady()) bindIfNeeded(3) else refreshLocalState()
    }

    override fun onDestroy() {
        try { Shizuku.removeBinderReceivedListener(binderReceivedListener) } catch (_: Throwable) {}
        try { Shizuku.removeBinderDeadListener(binderDeadListener) } catch (_: Throwable) {}
        try { Shizuku.removeRequestPermissionResultListener(permissionResultListener) } catch (_: Throwable) {}
        super.onDestroy()
    }

    private fun hasShizukuPermission(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) { false }

    private fun refreshLocalState() {
        if (!::status.isInitialized) return
        val running = try { Shizuku.pingBinder() } catch (_: Throwable) { false }
        val granted = hasShizukuPermission()
        setStatus(buildString {
            appendLine("Shizuku：${if (running) "已啟動 ✅" else "未啟動 ❌"}")
            appendLine("權限：${if (granted) "已授權 ✅" else "未授權"}")
            if (!running) append("請先打開 Shizuku 並啟動服務。")
            else if (!granted) append("請按『授權 Shizuku』。")
            else append("Shizuku 已就緒，可以開始診斷。")
        })
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                setStatus("Shizuku 尚未啟動。請先進入 Shizuku App 啟動服務。")
                return
            }
            if (Shizuku.isPreV11()) {
                setStatus("Shizuku 版本過舊，請更新至新版後再試。")
                return
            }
            if (hasShizukuPermission()) {
                setStatus("Shizuku 已授權 ✅\n正在連接特權服務…")
                bindIfNeeded(if (pendingCommand != 0) pendingCommand else 3)
                return
            }
            setStatus("正在要求 Shizuku 授權…")
            Shizuku.requestPermission(permissionRequestCode)
        } catch (t: Throwable) {
            setStatus("Shizuku 授權呼叫失敗：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}")
        }
    }

    private fun shizukuReady(): Boolean = try {
        Shizuku.pingBinder() && hasShizukuPermission()
    } catch (_: Throwable) { false }

    private fun runCommand(command: Int) {
        if (!shizukuReady()) {
            pendingCommand = command
            setStatus("Shizuku 尚未授權，正在請求權限…")
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
        setStatus("正在啟動 Shizuku 特權服務…")
        try {
            Shizuku.bindUserService(userServiceArgs, connection)
        } catch (t: Throwable) {
            pendingCommand = 0
            setStatus("特權服務啟動失敗：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}")
        }
    }

    private fun execute(command: Int) {
        val remote = service ?: return
        setStatus(when (command) {
            1 -> "正在抓取 Switch Pro 並建立完整 Xbox Wireless Controller…"
            2 -> "正在停止修正…"
            else -> "正在診斷…"
        })
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
            runOnUiThread { setStatus(result) }
        }.start()
    }
}
