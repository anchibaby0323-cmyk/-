package com.novatune.engine

import android.app.Application
import java.util.concurrent.CopyOnWriteArrayList

class NovaTuneApplication : Application() {
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        RuntimeCaches.trim(level)
    }
}

object RuntimeCaches {
    private val trimmers = CopyOnWriteArrayList<(Int) -> Unit>()

    fun register(trimmer: (Int) -> Unit): AutoCloseable {
        trimmers += trimmer
        return AutoCloseable { trimmers -= trimmer }
    }

    fun trim(level: Int) {
        trimmers.forEach { runCatching { it(level) } }
    }
}
