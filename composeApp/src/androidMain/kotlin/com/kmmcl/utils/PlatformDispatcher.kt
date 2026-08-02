package com.kmmcl.utils

actual object PlatformDispatcher {
    actual val io = kotlinx.coroutines.Dispatchers.IO
    actual val main = kotlinx.coroutines.Dispatchers.Main
    actual val default = kotlinx.coroutines.Dispatchers.Default
}
