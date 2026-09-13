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

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, PrivilegedService::class.java.name)
        )
            .tag("switch_pro_keyfix_bridge")
            .processNameSuffix("bridge")
            .version(20)
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
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }

        val title = TextView(this).apply {
            text = "Switch Pro Key Fix v0.2.0"
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
            text = "尚未診斷。"
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

    private fun refreshLocalState() {
        status.text = buildString {
            appendLine("Shizuku：${if (try { Shizuku.pingBinder() } catch (_: Throwable) { false }) "已啟動" else "未啟動"}")
            appendLine("權限：${if (try { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED } catch (_: Throwable) { false }) "已授權" else "未授權"}")
            append("請先啟動並授權 Shizuku，再按『診斷手把 / uinput』。")
        }
    }

    private fun requestShizukuPermission() {
        try {
            if (!Shizuku.pingBinder()) {
                status.text = "Shizuku 尚未啟動。"
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                status.text = "Shizuku 已授權。可以直接按診斷或開始修正。"
                bindIfNeeded(3)
                return
            }
            Shizuku.requestPermission(permissionRequestCode)
            status.text = "已送出 Shizuku 授權請求。授權後按『診斷手把 / uinput』。"
        } catch (t: Throwable) {
            status.text = "Shizuku 錯誤：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}"
        }
    }

    private fun shizukuReady(): Boolean {
        return try {
            Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (_: Throwable) {
            false
        }
    }

    private fun runCommand(command: Int) {
        if (!shizukuReady()) {
            status.text = "Shizuku 尚未啟動或未授權。"
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
