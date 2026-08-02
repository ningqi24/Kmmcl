package com.kmmcl.core.di

import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.android.Android

fun httpClientEngine(): HttpClientEngine = Android.create()
