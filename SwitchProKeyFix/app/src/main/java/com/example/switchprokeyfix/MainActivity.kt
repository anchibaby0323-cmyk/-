package com.example.switchprokeyfix

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.view.InputDevice
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private val requestCodeShizuku = 1001
    private var privilegedBinder: IBinder? = null
    private var pendingAction: Boolean? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, PrivilegedService::class.java.name)
        )
            .tag("switch_pro_keyfix")
            .processNameSuffix("privileged")
            .version(2)
            .debuggable(BuildConfig.DEBUG)
            .daemon(false)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            privilegedBinder = service
            val action = pendingAction
            pendingAction = null
            if (action != null) executeRemote(action) else refreshStatus()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            privilegedBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        val title = TextView(this).apply {
            text = "Switch Pro 按鍵修正"
            textSize = 24f
        }
        status = TextView(this).apply {
            textSize = 16f
            setPadding(0, 24, 0, 24)
        }
        val grant = Button(this).apply {
            text = "授權 Shizuku"
            setOnClickListener { requestShizuku() }
        }
        val apply = Button(this).apply {
            text = "套用 A↔B、X↔Y"
            setOnClickListener { runAction(true) }
        }
        val restore = Button(this).apply {
            text = "恢復原始配置"
            setOnClickListener { runAction(false) }
        }
        val refresh = Button(this).apply {
            text = "重新偵測"
            setOnClickListener { refreshStatus() }
        }

        root.addView(title)
        root.addView(status)
        root.addView(grant)
        root.addView(apply)
        root.addView(restore)
        root.addView(refresh)
        setContentView(ScrollView(this).apply { addView(root) })

        refreshStatus()
    }

    private fun requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                status.text = "Shizuku 尚未啟動。"
                return
            }
            if (Shizuku.isPreV11()) {
                status.text = "Shizuku 版本過舊，請先更新。"
                return
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                status.text = "Shizuku 已授權。"
            } else if (Shizuku.shouldShowRequestPermissionRationale()) {
                status.text = "請在 Shizuku 中允許此 App 權限。"
            } else {
                Shizuku.requestPermission(requestCodeShizuku)
            }
        } catch (t: Throwable) {
            status.text = "Shizuku 不可用：${t.message}"
        }
    }

    private fun refreshStatus() {
        val devices = InputDevice.getDeviceIds()
            .toList()
            .mapNotNull { id -> InputDevice.getDevice(id) }
        val pro = devices.firstOrNull { device ->
            device.vendorId == 0x057e && device.productId == 0x2009
        }

        val shizukuState = try {
            when {
                !Shizuku.pingBinder() -> "未連線"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "已授權"
                else -> "未授權"
            }
        } catch (_: Throwable) {
            "不可用"
        }

        status.text = buildString {
            appendLine("Shizuku：$shizukuState")
            if (pro != null) {
                appendLine("Switch Pro：已偵測")
                appendLine("VID:PID = %04x:%04x".format(pro.vendorId, pro.productId))
                appendLine("Descriptor：${pro.descriptor}")
            } else {
                appendLine("Switch Pro：未偵測")
            }
            appendLine()
            appendLine("套用或恢復後，請關閉並重新連接手把。")
        }
    }

    private fun runAction(apply: Boolean) {
        try {
            if (!Shizuku.pingBinder()) {
                status.text = "Shizuku 尚未啟動。"
                return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                requestShizuku()
                return
            }
        } catch (t: Throwable) {
            status.text = "Shizuku 不可用：${t.message}"
            return
        }

        if (privilegedBinder == null || privilegedBinder?.isBinderAlive != true) {
            pendingAction = apply
            status.text = "正在啟動 Shizuku UserService…"
            try {
                Shizuku.bindUserService(userServiceArgs, userServiceConnection)
            } catch (t: Throwable) {
                pendingAction = null
                status.text = "UserService 啟動失敗：${t.message}"
            }
            return
        }

        executeRemote(apply)
    }

    private fun executeRemote(apply: Boolean) {
        val binder = privilegedBinder ?: run {
            status.text = "UserService 尚未連線。"
            return
        }
        val script = if (apply) buildApplyScript() else buildRestoreScript()

        Thread {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(PrivilegedService.DESCRIPTOR)
                data.writeString(script)
                val ok = binder.transact(PrivilegedService.TRANSACTION_RUN, data, reply, 0)
                if (!ok) throw IllegalStateException("UserService 沒有接受執行請求")
                reply.readException()
                val result = reply.readString().orEmpty()
                runOnUiThread {
                    status.text = "$result\n\n完成後請重新連接 Switch Pro 手把。"
                }
            } catch (t: Throwable) {
                runOnUiThread { status.text = "執行失敗：${t.message}" }
            } finally {
                data.recycle()
                reply.recycle()
            }
        }.start()
    }

    private fun buildApplyScript(): String = """
set -eu
DST=/data/system/devices/keylayout
BASE=""
for p in /odm/usr/keylayout /vendor/usr/keylayout /system/usr/keylayout /product/usr/keylayout; do
  if [ -f "${'$'}p/Vendor_057e_Product_2009.kl" ]; then BASE="${'$'}p/Vendor_057e_Product_2009.kl"; break; fi
done
if [ -z "${'$'}BASE" ]; then echo "找不到 Switch Pro 原始 .kl"; exit 20; fi
mkdir -p "${'$'}DST" 2>/dev/null || true
if [ ! -d "${'$'}DST" ] || [ ! -w "${'$'}DST" ]; then
  echo "Shizuku shell 無法寫入 ${'$'}DST"
  ls -ld "${'$'}DST" 2>&1 || true
  exit 21
fi
VERSION=$(dumpsys input 2>/dev/null | awk '/Vendor: 0x057e/{f=1} f&&/Product: 0x2009/{p=1} p&&/Version:/{print ${'$'}2; exit}' | sed 's/0x//')
if [ -z "${'$'}VERSION" ]; then VERSION=0000; fi
VERSION=$(printf "%04s" "${'$'}VERSION" | tr ' ' '0')
OUT="${'$'}DST/Vendor_057e_Product_2009_Version_${'$'}VERSION.kl"
TMP="${'$'}OUT.tmp"
cp "${'$'}BASE" "${'$'}TMP"
sed -i \
  -e 's/BUTTON_A/__SWITCHPRO_TMP_A__/g' \
  -e 's/BUTTON_B/BUTTON_A/g' \
  -e 's/__SWITCHPRO_TMP_A__/BUTTON_B/g' \
  -e 's/BUTTON_X/__SWITCHPRO_TMP_X__/g' \
  -e 's/BUTTON_Y/BUTTON_X/g' \
  -e 's/__SWITCHPRO_TMP_X__/BUTTON_Y/g' "${'$'}TMP"
mv "${'$'}TMP" "${'$'}OUT"
chmod 0644 "${'$'}OUT"
echo "已建立：${'$'}OUT"
grep -E 'BUTTON_[ABXY]' "${'$'}OUT" || true
""".trimIndent()

    private fun buildRestoreScript(): String = """
set -eu
DST=/data/system/devices/keylayout
if [ ! -d "${'$'}DST" ]; then
  echo "沒有自訂配置需要移除。"
  exit 0
fi
rm -f "${'$'}DST"/Vendor_057e_Product_2009_Version_*.kl
rm -f "${'$'}DST"/Vendor_057e_Product_2009_Version_*.kl.tmp
echo "已移除 Switch Pro 自訂覆蓋配置。"
""".trimIndent()
}

class PrivilegedService : Binder() {
    companion object {
        const val DESCRIPTOR = "com.example.switchprokeyfix.PrivilegedService"
        const val TRANSACTION_RUN = FIRST_CALL_TRANSACTION
        private const val TRANSACTION_DESTROY = 16777115
    }

    override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
        return when (code) {
            TRANSACTION_RUN -> {
                data.enforceInterface(DESCRIPTOR)
                val script = data.readString().orEmpty()
                val result = runScript(script)
                reply?.writeNoException()
                reply?.writeString(result)
                true
            }
            TRANSACTION_DESTROY -> {
                reply?.writeNoException()
                Thread { System.exit(0) }.start()
                true
            }
            else -> super.onTransact(code, data, reply, flags)
        }
    }

    private fun runScript(script: String): String {
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", script)
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()
            buildString {
                appendLine("結果代碼：$code")
                if (output.isNotBlank()) append(output.trimEnd())
            }
        } catch (t: Throwable) {
            "UserService 執行失敗：${t.javaClass.simpleName}: ${t.message}"
        }
    }
}
