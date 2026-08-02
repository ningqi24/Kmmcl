# Kmmcl — Kotlin Multiplatform Minecraft Launcher

A modern Minecraft launcher built with Kotlin Multiplatform and Compose Multiplatform.

## Tech Stack

| Layer       | Technology                            |
|-------------|---------------------------------------|
| UI          | Compose Multiplatform + Material 3    |
| DI          | Koin                                  |
| Network     | Ktor Client                           |
| Auth        | Mokt (Microsoft + Offline)            |
| Download    | KDownloadFiles (resumable)            |
| Archive     | kzip                                  |
| Async       | kotlinx.coroutines                    |

## Project Structure

```
Kmmcl/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/
├── composeApp/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── kotlin/com/kmmcl/
│       │   │   ├── MainActivity.kt
│       │   │   ├── KmmclApp.kt
│       │   │   ├── core/
│       │   │   │   ├── auth/AuthService.kt
│       │   │   │   ├── download/DownloadManager.kt
│       │   │   │   ├── game/GameService.kt
│       │   │   │   └── di/KoinModules.kt
│       │   │   ├── data/
│       │   │   │   ├── model/GameVersion.kt
│       │   │   │   └── repository/GameRepository.kt
│       │   │   ├── ui/
│       │   │   │   ├── KmmclApp.kt
│       │   │   │   ├── theme/Theme.kt
│       │   │   │   ├── components/
│       │   │   │   │   ├── GlassComponents.kt
│       │   │   │   │   └── ProgressBar.kt
│       │   │   │   └── screens/
│       │   │   │       ├── game/GameViewModel.kt
│       │   │   │       ├── home/HomeScreen.kt
│       │   │   │       ├── versions/VersionScreen.kt
│       │   │   │       └── settings/SettingsScreen.kt
│       │   │   └── utils/
│       │   │       └── PathUtils.kt
│       │   └── res/
│       │       ├── values/
│       │       ├── drawable/
│       │       ├── mipmap-anydpi-v26/
│       │       └── xml/file_paths.xml
│       ├── commonMain/kotlin/com/kmmcl/utils/PlatformDispatcher.kt
│       └── androidMain/kotlin/com/kmmcl/utils/PlatformDispatcher.kt
```

## Build

1. Open in Android Studio (Ladybug+)
2. Sync Gradle
3. Run `composeApp` on an Android device/emulator (API 26+)

## License

MIT
