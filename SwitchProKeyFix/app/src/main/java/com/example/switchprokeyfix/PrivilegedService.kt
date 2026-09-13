package com.example.switchprokeyfix

class PrivilegedService : IPrivilegedService.Stub() {
    override fun runScript(script: String): String {
        return try {
            val process = ProcessBuilder("/system/bin/sh", "-c", script)
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().use { it.readText() }
            val code = process.waitFor()

            buildString {
                appendLine("UserService：已連線")
                appendLine("shell uid：${android.os.Process.myUid()}")
                appendLine("結果代碼：$code")
                if (output.isNotBlank()) {
                    append(output.trimEnd())
                } else {
                    append("指令沒有輸出內容。")
                }
            }
        } catch (t: Throwable) {
            buildString {
                appendLine("UserService：執行失敗")
                appendLine("例外：${t.javaClass.name}")
                append("訊息：${t.message ?: "(無)"}")
            }
        }
    }
}
