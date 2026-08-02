package com.kmmcl.utils

import kotlinx.coroutines.CoroutineDispatcher

actual object PlatformDispatcher {
    actual val io: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.IO
    actual val main: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Main
    actual val default: CoroutineDispatcher = kotlinx.coroutines.Dispatchers.Default
}
