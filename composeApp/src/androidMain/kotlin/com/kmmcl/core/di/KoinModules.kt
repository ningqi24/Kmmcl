
package com.kmmcl.core.di

import com.kmmcl.core.auth.AuthService
import com.kmmcl.core.download.DownloadManager
import com.kmmcl.core.game.GameService
import com.kmmcl.core.game.VersionService
import com.kmmcl.data.repository.GameRepository
import com.kmmcl.ui.screens.game.GameViewModel
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
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 15_000
            }
        }
    }

    single { AuthService(androidContext()) }
    single { VersionService(get()) }
    single { DownloadManager(get()) }
    single { GameRepository(get()) }
    single {
        val ctx = androidContext()
        GameService(
            versionService = get(),
            downloadManager = get(),
            gameDir = File(ctx.getExternalFilesDir(null), "game")
        )
    }

    single { GameViewModel(androidApplication(), get(), get()) }
}
