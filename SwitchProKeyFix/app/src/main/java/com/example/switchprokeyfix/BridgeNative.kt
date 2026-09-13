package com.example.switchprokeyfix

object BridgeNative {
    init {
        System.loadLibrary("switchprobridge")
    }

    external fun diagnostics(): String
    external fun startBridge(): String
    external fun stopBridge(): String
}
