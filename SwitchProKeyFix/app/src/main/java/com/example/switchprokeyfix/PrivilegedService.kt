package com.example.switchprokeyfix

import android.content.Context

class PrivilegedService : IPrivilegedService.Stub {

    @Suppress("unused")
    constructor() : super()

    @Suppress("unused")
    constructor(context: Context?) : super()

    override fun getDiagnostics(): String {
        return try {
            buildString {
                appendLine("UserService：已連線")
                appendLine("shell uid：${android.os.Process.myUid()}")
                append(BridgeNative.diagnostics())
            }
        } catch (t: Throwable) {
            "診斷失敗：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}"
        }
    }

    override fun startBridge(): String {
        return try {
            BridgeNative.startBridge()
        } catch (t: Throwable) {
            "啟動失敗：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}"
        }
    }

    override fun stopBridge(): String {
        return try {
            BridgeNative.stopBridge()
        } catch (t: Throwable) {
            "停止失敗：${t.javaClass.simpleName}: ${t.message ?: "(無訊息)"}"
        }
    }

    @Suppress("unused")
    fun destroy() {
        try {
            BridgeNative.stopBridge()
        } catch (_: Throwable) {
        }
        System.exit(0)
    }
}
