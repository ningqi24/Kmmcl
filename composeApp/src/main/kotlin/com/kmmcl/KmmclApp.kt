package com.kmmcl

import android.app.Application
import com.kmmcl.core.di.initKoin
import org.koin.android.ext.koin.androidContext

class KmmclApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@KmmclApp)
        }
    }
}
