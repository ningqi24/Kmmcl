package com.kmmcl.core.di

import android.content.Context
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin

fun initKoin(androidContext: () -> Context) {
    stopKoin()
    startKoin {
        androidContext(androidContext())
        modules(appModule)
    }
}
