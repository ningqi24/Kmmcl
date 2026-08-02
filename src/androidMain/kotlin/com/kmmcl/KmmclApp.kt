package com.kmmcl

import android.app.Application
import com.kmmcl.core.di.initKoin

class KmmclApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            this@KmmclApp
        }
    }
}
