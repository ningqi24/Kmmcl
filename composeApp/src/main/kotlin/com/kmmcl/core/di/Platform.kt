package com.kmmcl.core.di

import io.ktor.client.engine.HttpClientEngine

expect fun httpClientEngine(): HttpClientEngine
