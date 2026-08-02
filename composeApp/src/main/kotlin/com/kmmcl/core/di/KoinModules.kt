package com.kmmcl.core.di

import com.kmmcl.core.auth.AuthService
import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.game.GameService
import com.kmmcl.ui.screens.game.GameViewModel
import org.koin.core.context.startKoin
import org.koin.dsl.module

val coreModule = module {
    single { AuthService() }
    single { DownloadManager() }
    single { GameService() }
}

val viewModelModule = module {
    single { GameViewModel(get(), get(), get()) }
}

fun initKoin(platformModules: org.koin.core.module.Module.() -> Unit = {}) {
    startKoin {
        modules(platformModules)
        modules(coreModule)
        modules(viewModelModule)
    }
}
