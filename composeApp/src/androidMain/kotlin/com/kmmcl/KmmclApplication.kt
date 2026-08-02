
package com.kmmcl

import android.app.Application
import com.kmmcl.core.di.appModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext.startKoin

class KmmclApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@KmmclApplication)
            modules(appModule)
        }
    }
}
