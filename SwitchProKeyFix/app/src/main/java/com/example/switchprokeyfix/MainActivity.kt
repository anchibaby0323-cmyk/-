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
            .version(4)
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
            runOnUiThread {
                status.text = "UserService 已斷線。請重新按一次操作按鈕。"
            }
        }

        override fun onBindingDied(name: ComponentName) {
            privilegedService = null
            runOnUiThread {
                status.text = "UserService 啟動後死亡。請重啟 Shizuku 後再試。"
            }
        }

        override fun onNullBinding(name: ComponentName) {
            privilegedService = null
            runOnUiThread {
                status.text = "UserService 回傳空 Binder，無法執行。"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 40, 40, 40)
        }
        val title = TextView(this).apply {
            text = "Switch Pro 按鍵修正 v0.1.3"
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

        val gamepads = devices.filter { device ->
            val sources = device.sources
            (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }

        val exact = devices.firstOrNull { device ->
            device.vendorId == 0x057e && device.productId == 0x2009
        }

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
        } catch (_: Throwable) {
            "不可用"
        }

        status.text = buildString {
            appendLine("Shizuku：$shizukuState")
            appendLine("UserService：${if (privilegedService != null) "已連線" else "尚未連線"}")
            if (pro != null) {
                appendLine("Switch Pro：已偵測（$detectionMethod）")
                appendLine("名稱：${pro.name}")
                appendLine("VID:PID = %04x:%04x".format(pro.vendorId, pro.productId))
                appendLine("Descriptor：${pro.descriptor}")
            } else {
                appendLine("Switch Pro：一般 Android API 尚未辨識")
                if (gamepads.isNotEmpty()) {
                    appendLine("GAMEPAD / JOYSTICK 候選：")
                    gamepads.forEach { d ->
                        appendLine("• ${d.name}  %04x:%04x".format(d.vendorId, d.productId))
                    }
                } else {
                    appendLine("目前沒有 GAMEPAD / JOYSTICK 類型的 InputDevice。")
                }
                appendLine("仍可按『套用』，Shizuku 會從 Linux 輸入資訊再次尋找。")
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
                runOnUiThread {
                    status.text = result.ifBlank { "UserService 已完成，但沒有回傳文字。" }
                }
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
printf '診斷開始\n'
printf 'uid='; id -u 2>&1 || true
printf 'whoami='; id 2>&1 || true

DST=/data/system/devices/keylayout
BASE=""
for p in /product/usr/keylayout /system_ext/usr/keylayout /odm/usr/keylayout /vendor/usr/keylayout /apex/com.android.input.config/etc/usr/keylayout /system/usr/keylayout; do
  if [ -f "${'$'}p/Vendor_057e_Product_2009.kl" ]; then BASE="${'$'}p/Vendor_057e_Product_2009.kl"; break; fi
done
if [ -z "${'$'}BASE" ]; then
  echo "ERROR 20：找不到 Switch Pro 原始 .kl"
  exit 20
fi
echo "base=${'$'}BASE"

VERSION=""
if [ -r /proc/bus/input/devices ]; then
  VERSION=$(awk 'BEGIN{IGNORECASE=1} /Vendor=057e/ && /Product=2009/ {for(i=1;i<=NF;i++){if(${'$'}i ~ /^Version=/){sub(/^Version=/,"",${'$'}i); print ${'$'}i; exit}}}' /proc/bus/input/devices 2>/dev/null || true)
fi
if [ -z "${'$'}VERSION" ]; then
  VERSION=$(dumpsys input 2>/dev/null | awk 'BEGIN{IGNORECASE=1} /Vendor: 0x057e/{v=1} v&&/Product: 0x2009/{p=1} p&&/Version:/{gsub("0x","",${'$'}2); print ${'$'}2; exit}' || true)
fi
if [ -z "${'$'}VERSION" ]; then
  echo "ERROR 22：Shizuku 已執行，但找不到 057e:2009 的控制器版本。"
  echo "--- input candidates ---"
  grep -i -B1 -A5 -E '057e|2009|pro controller|nintendo|switch' /proc/bus/input/devices 2>/dev/null || true
  echo "--- dumpsys candidates ---"
  dumpsys input 2>/dev/null | grep -i -B2 -A6 -E '057e|2009|pro controller|nintendo|switch' | head -n 120 || true
  exit 22
fi
VERSION=$(echo "${'$'}VERSION" | tr '[:upper:]' '[:lower:]' | sed 's/^0x//')
VERSION=$(printf "%04s" "${'$'}VERSION" | tr ' ' '0')
echo "controller=057e:2009 version=${'$'}VERSION"

mkdir -p "${'$'}DST" 2>/dev/null || true
if [ ! -d "${'$'}DST" ] || [ ! -w "${'$'}DST" ]; then
  echo "ERROR 21：Shizuku shell 無法寫入 ${'$'}DST"
  ls -ld "${'$'}DST" 2>&1 || true
  exit 21
fi

OUT="${'$'}DST/Vendor_057e_Product_2009_Version_${'$'}VERSION.kl"
TMP="${'$'}OUT.tmp"
cp "${'$'}BASE" "${'$'}TMP" || exit 23
sed -i \
  -e 's/BUTTON_A/__SWITCHPRO_TMP_A__/g' \
  -e 's/BUTTON_B/BUTTON_A/g' \
  -e 's/__SWITCHPRO_TMP_A__/BUTTON_B/g' \
  -e 's/BUTTON_X/__SWITCHPRO_TMP_X__/g' \
  -e 's/BUTTON_Y/BUTTON_X/g' \
  -e 's/__SWITCHPRO_TMP_X__/BUTTON_Y/g' "${'$'}TMP" || exit 24
mv "${'$'}TMP" "${'$'}OUT" || exit 25
chmod 0644 "${'$'}OUT" || true
echo "SUCCESS：已建立 ${'$'}OUT"
grep -E 'BUTTON_[ABXY]' "${'$'}OUT" || true
""".trimIndent()

    private fun buildRestoreScript(): String = """
set -u
DST=/data/system/devices/keylayout
echo "restore uid=$(id -u 2>/dev/null || echo '?')"
if [ ! -d "${'$'}DST" ]; then
  echo "沒有自訂配置需要移除。"
  exit 0
fi
rm -f "${'$'}DST"/Vendor_057e_Product_2009_Version_*.kl
rm -f "${'$'}DST"/Vendor_057e_Product_2009_Version_*.kl.tmp
echo "SUCCESS：已移除 Switch Pro 自訂覆蓋配置。"
""".trimIndent()
}
