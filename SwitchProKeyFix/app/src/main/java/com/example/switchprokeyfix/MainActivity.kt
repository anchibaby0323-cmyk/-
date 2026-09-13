package com.example.switchprokeyfix

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
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
    private var privilegedService: IPrivilegedService? = null
    private var pendingAction: Boolean? = null

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(packageName, PrivilegedService::class.java.name)
        )
            .tag("switch_pro_keyfix")
            .processNameSuffix("privileged")
            .version(5)
            .debuggable(true)
            .daemon(false)
    }

    private val userServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            privilegedService = IPrivilegedService.Stub.asInterface(service)
            val action = pendingAction
            pendingAction = null
            if (action != null) {
                status.text = "UserService 已連線，正在執行…"
                executeRemote(action)
            } else {
                refreshStatus()
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            privilegedService = null
            runOnUiThread { status.text = "UserService 已斷線。請重新按一次操作按鈕。" }
        }

        override fun onBindingDied(name: ComponentName) {
            privilegedService = null
            runOnUiThread { status.text = "UserService 啟動後死亡。請重啟 Shizuku 後再試。" }
        }

        override fun onNullBinding(name: ComponentName) {
            privilegedService = null
            runOnUiThread { status.text = "UserService 回傳空 Binder，無法執行。" }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        val title = TextView(this).apply {
            text = "Switch Pro 按鍵修正 v0.1.4"
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
        val devices = InputDevice.getDeviceIds().toList().mapNotNull { id -> InputDevice.getDevice(id) }
        val gamepads = devices.filter { device ->
            val sources = device.sources
            (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
        val exact = devices.firstOrNull { it.vendorId == 0x057e && it.productId == 0x2009 }
        val named = gamepads.firstOrNull { device ->
            val n = device.name.lowercase()
            n.contains("pro controller") || n.contains("nintendo") || n.contains("switch")
        }
        val pro = exact ?: named
        val detectionMethod = when {
            exact != null -> "VID/PID"
            named != null -> "裝置名稱 / GAMEPAD"
            else -> null
        }
        val shizukuState = try {
            when {
                !Shizuku.pingBinder() -> "未連線"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "已授權"
                else -> "未授權"
            }
        } catch (_: Throwable) { "不可用" }

        status.text = buildString {
            appendLine("Shizuku：$shizukuState")
            appendLine("UserService：${if (privilegedService != null) "已連線" else "尚未連線"}")
            if (pro != null) {
                appendLine("Switch Pro：已偵測（$detectionMethod）")
                appendLine("名稱：${pro.name}")
                appendLine("VID:PID = %04x:%04x".format(pro.vendorId, pro.productId))
            } else {
                appendLine("Switch Pro：Android API 尚未辨識")
                appendLine("仍可按『套用』，Shizuku 會直接從 dumpsys input 尋找。")
            }
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

        if (privilegedService == null) {
            pendingAction = apply
            status.text = "正在啟動 Shizuku UserService…"
            try {
                Shizuku.bindUserService(userServiceArgs, userServiceConnection)
            } catch (t: Throwable) {
                pendingAction = null
                status.text = "UserService 啟動失敗：${t.javaClass.simpleName}: ${t.message}"
            }
            return
        }
        executeRemote(apply)
    }

    private fun executeRemote(apply: Boolean) {
        val remote = privilegedService ?: run {
            status.text = "UserService 尚未連線。"
            return
        }
        val script = if (apply) buildApplyScript() else buildRestoreScript()
        status.text = "UserService 已連線，正在執行指令…"
        Thread {
            try {
                val result = remote.runScript(script)
                runOnUiThread { status.text = result.ifBlank { "UserService 已完成，但沒有回傳文字。" } }
            } catch (t: Throwable) {
                privilegedService = null
                runOnUiThread {
                    status.text = "Binder 呼叫失敗：${t.javaClass.name}\n${t.message ?: "(無訊息)"}"
                }
            }
        }.start()
    }

    private fun buildApplyScript(): String = """
set -u
DST=/data/system/devices/keylayout
BASE=""
for p in /product/usr/keylayout /system_ext/usr/keylayout /odm/usr/keylayout /vendor/usr/keylayout /apex/com.android.input.config/etc/usr/keylayout /system/usr/keylayout; do
  if [ -f "${'$'}p/Vendor_057e_Product_2009.kl" ]; then BASE="${'$'}p/Vendor_057e_Product_2009.kl"; break; fi
done
if [ -z "${'$'}BASE" ]; then
  echo "ERROR 20：找不到原始 Switch Pro .kl"
  exit 20
fi

IDENTIFIER=$(dumpsys input 2>/dev/null | grep -i -m1 'vendor=0x057e, product=0x2009, version=0x' || true)
VERSION=$(printf '%s\n' "${'$'}IDENTIFIER" | sed -n 's/.*version=0x\([0-9A-Fa-f][0-9A-Fa-f]*\).*/\1/p' | head -n 1)
if [ -z "${'$'}VERSION" ]; then
  echo "ERROR 22：已找到 Shizuku，但無法解析 Switch Pro version。"
  echo "VID/PID：057e:2009"
  exit 22
fi
VERSION=$(printf '%s' "${'$'}VERSION" | tr '[:upper:]' '[:lower:]')
VERSION=$(printf "%04s" "${'$'}VERSION" | tr ' ' '0')
OUT="${'$'}DST/Vendor_057e_Product_2009_Version_${'$'}VERSION.kl"
TMP="${'$'}OUT.tmp"

echo "Shizuku：已連線"
echo "shell uid=$(id -u 2>/dev/null || echo '?')"
echo "手把：Nintendo Switch Pro Controller"
echo "VID:PID：057e:2009"
echo "Version：${'$'}VERSION"
echo "來源 KL：${'$'}BASE"
echo "目標 KL：${'$'}OUT"

mkdir -p "${'$'}DST" 2>/dev/null || true
if [ ! -d "${'$'}DST" ] || [ ! -w "${'$'}DST" ]; then
  echo "寫入狀態：失敗"
  echo "ERROR 21：Shizuku shell 無法寫入 ${'$'}DST"
  ls -ld "${'$'}DST" 2>&1 || true
  exit 21
fi

for p in /product/usr/keylayout /system_ext/usr/keylayout /odm/usr/keylayout /vendor/usr/keylayout /apex/com.android.input.config/etc/usr/keylayout /system/usr/keylayout; do
  if [ -f "${'$'}p/Vendor_057e_Product_2009_Version_${'$'}VERSION.kl" ]; then
    echo "寫入狀態：停止"
    echo "ERROR 26：系統已存在更高優先級的 version-specific KL：${'$'}p/Vendor_057e_Product_2009_Version_${'$'}VERSION.kl"
    exit 26
  fi
done

cp "${'$'}BASE" "${'$'}TMP" || { echo "寫入狀態：複製失敗"; exit 23; }
sed -i \
  -e 's/BUTTON_A/__SWITCHPRO_TMP_A__/g' \
  -e 's/BUTTON_B/BUTTON_A/g' \
  -e 's/__SWITCHPRO_TMP_A__/BUTTON_B/g' \
  -e 's/BUTTON_X/__SWITCHPRO_TMP_X__/g' \
  -e 's/BUTTON_Y/BUTTON_X/g' \
  -e 's/__SWITCHPRO_TMP_X__/BUTTON_Y/g' "${'$'}TMP" || { echo "寫入狀態：修改失敗"; exit 24; }
mv "${'$'}TMP" "${'$'}OUT" || { echo "寫入狀態：移動失敗"; exit 25; }
chmod 0644 "${'$'}OUT" || true

echo "寫入狀態：成功"
echo "請中斷並重新連接 Switch Pro 手把後測試。"
echo "按鍵配置："
grep -E 'BUTTON_[ABXY]' "${'$'}OUT" || true
""".trimIndent()

    private fun buildRestoreScript(): String = """
set -u
DST=/data/system/devices/keylayout
IDENTIFIER=$(dumpsys input 2>/dev/null | grep -i -m1 'vendor=0x057e, product=0x2009, version=0x' || true)
VERSION=$(printf '%s\n' "${'$'}IDENTIFIER" | sed -n 's/.*version=0x\([0-9A-Fa-f][0-9A-Fa-f]*\).*/\1/p' | head -n 1)
if [ -z "${'$'}VERSION" ]; then
  echo "ERROR 22：無法解析目前 Switch Pro version，因此不會刪除任何檔案。"
  exit 22
fi
VERSION=$(printf '%s' "${'$'}VERSION" | tr '[:upper:]' '[:lower:]')
VERSION=$(printf "%04s" "${'$'}VERSION" | tr ' ' '0')
OUT="${'$'}DST/Vendor_057e_Product_2009_Version_${'$'}VERSION.kl"
TMP="${'$'}OUT.tmp"

echo "手把：Nintendo Switch Pro Controller"
echo "Version：${'$'}VERSION"
echo "目標 KL：${'$'}OUT"
rm -f "${'$'}TMP"
if [ -f "${'$'}OUT" ]; then
  rm -f "${'$'}OUT" || { echo "恢復狀態：刪除失敗"; exit 27; }
  echo "恢復狀態：成功"
  echo "請中斷並重新連接手把。"
else
  echo "恢復狀態：沒有找到此版本的自訂配置。"
fi
""".trimIndent()
}
