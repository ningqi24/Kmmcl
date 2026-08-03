package com.kmmcl.core.di

import com.kmmcl.core.auth.AuthService
import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.download.DownloadProvider
import com.kmmcl.core.game.GameService
import com.kmmcl.core.game.ManifestResolver
import com.kmmcl.core.game.VersionService
import com.kmmcl.core.jre.JreManager
import com.kmmcl.core.launch.GameLauncher
import com.kmmcl.data.repository.GameRepository
import com.kmmcl.ui.screens.game.GameViewModel
import com.kmmcl.ui.screens.setup.SetupViewModel
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidApplication
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

val appModule: Module = module {
    single<HttpClient> {
        HttpClient(httpClientEngine()) {
            install(ContentNegotiation) {
                json(Json { ignoreUnknownKeys = true })
            }
            install(HttpTimeout) {
                requestTimeoutMillis = 600_000
                connectTimeoutMillis = 60_000
                socketTimeoutMillis = 60_000
            }
        }
    }

    single { AuthService(androidContext()) }
    single { DownloadProvider.BMCLAPI as DownloadProvider }
    single { VersionService(get(), get()) }
    single { ManifestResolver(get()) }
    single { DownloadManager(get()) }
    single { GameRepository(get()) }

    single<File> {
        val ctx = androidContext()
        File(ctx.getExternalFilesDir(null), "game")
    }

    single { JreManager(httpClient = get()) }
    single { GameService(manifestResolver = get(), downloadManager = get(), gameDir = get(), provider = get()) }
    single { GameLauncher(jreManager = get(), manifestResolver = get(), gameDir = get()) }

    single { GameViewModel(androidApplication(), get(), get()) }
    single { SetupViewModel(jreManager = get(), versionService = get(), gameService = get()) }
}
