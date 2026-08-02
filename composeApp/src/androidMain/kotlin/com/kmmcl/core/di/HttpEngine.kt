
package com.kmmcl.core.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android

internal fun httpClientEngine(): HttpClientEngine = Android.create()
