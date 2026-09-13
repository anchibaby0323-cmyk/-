package com.example.switchprokeyfix

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Bundle
import android.view.InputDevice
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private val requestCodeShizuku = 1001

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
        if (Shizuku.isPreV11()) {
            status.text = "Shizuku 版本過舊，請先更新。"
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            refreshStatus()
        } else if (Shizuku.shouldShowRequestPermissionRationale()) {
            status.text = "請在 Shizuku 中允許此 App 權限。"
        } else {
            Shizuku.requestPermission(requestCodeShizuku)
        }
    }

    private fun refreshStatus() {
        val devices = InputDevice.getDeviceIds().mapNotNull { InputDevice.getDevice(it) }
        val pro = devices.firstOrNull { it.vendorId == 0x057e && it.productId == 0x2009 }
        val shizukuState = try {
            when {
                !Shizuku.pingBinder() -> "未連線"
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> "已授權"
                else -> "未授權"
            }
        } catch (_: Throwable) { "不可用" }

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
            appendLine("套用後請關閉並重新連接手把。")
        }
    }

    private fun runAction(apply: Boolean) {
        if (!Shizuku.pingBinder()) {
            status.text = "Shizuku 尚未啟動。"
            return
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            requestShizuku()
            return
        }

        val cmd = if (apply) {
            arrayOf("sh", "-c", buildApplyScript())
        } else {
            arrayOf("sh", "-c", buildRestoreScript())
        }

        Thread {
            try {
                val process = Shizuku.newProcess(cmd, null, null)
                val out = process.inputStream.bufferedReader().readText()
                val err = process.errorStream.bufferedReader().readText()
                val code = process.waitFor()
                runOnUiThread {
                    status.text = "結果代碼：$code\n$out${if (err.isNotBlank()) "\n錯誤：$err" else ""}\n\n完成後請重新連接 Switch Pro 手把。"
                }
            } catch (t: Throwable) {
                runOnUiThread { status.text = "執行失敗：${t.message}" }
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
mkdir -p "${'$'}DST"
if [ ! -w "${'$'}DST" ]; then echo "Shizuku shell 無法寫入 ${'$'}DST"; ls -ld "${'$'}DST" || true; exit 21; fi
VERSION=$(dumpsys input | awk '/Vendor: 0x057e/{f=1} f&&/Product: 0x2009/{p=1} p&&/Version:/{print ${'$'}2; exit}' | sed 's/0x//')
if [ -z "${'$'}VERSION" ]; then VERSION=0000; fi
VERSION=$(printf "%04s" "${'$'}VERSION" | tr ' ' '0')
OUT="${'$'}DST/Vendor_057e_Product_2009_Version_${'$'}VERSION.kl"
cp "${'$'}BASE" "${'$'}OUT.tmp"
sed -i -e 's/BUTTON_A/__TMP_A__/g' -e 's/BUTTON_B/BUTTON_A/g' -e 's/__TMP_A__/BUTTON_B/g' "${'$'}OUT.tmp"
sed -i -e 's/BUTTON_X/__TMP_X__/g' -e 's/BUTTON_Y/BUTTON_X/g' -e 's/__TMP_X__/BUTTON_Y/g' "${'$'}OUT.tmp"
mv "${'$'}OUT.tmp" "${'$'}OUT"
chmod 0644 "${'$'}OUT"
echo "已建立：${'$'}OUT"
grep -E 'BUTTON_[ABXY]' "${'$'}OUT" || true
""".trimIndent()

    private fun buildRestoreScript(): String = """
set -eu
DST=/data/system/devices/keylayout
rm -f "${'$'}DST"/Vendor_057e_Product_2009_Version_*.kl
echo "已移除 Switch Pro 自訂覆蓋配置。"
""".trimIndent()
}
