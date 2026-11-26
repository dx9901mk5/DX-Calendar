import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

// 自動載入專案資料夾內的私有簽章設定 signing.properties（透過雲端硬碟跨電腦同步，已列入 .gitignore）
val signingProps = Properties()
val signingPropsFile = rootProject.file("signing.properties")
if (signingPropsFile.exists()) {
    signingPropsFile.inputStream().use { signingProps.load(it) }
}
val releaseKeystoreFile = signingProps.getProperty("STORE_FILE")?.let { rootProject.file(it) }
val hasReleaseKeystore = releaseKeystoreFile != null && releaseKeystoreFile.exists()

android {
    namespace = "com.dx.calendar"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dx.calendar"
        minSdk = 26
        targetSdk = 36
        versionCode = 29
        versionName = "2.0.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = signingProps.getProperty("STORE_PASSWORD")
                keyAlias = signingProps.getProperty("KEY_ALIAS")
                keyPassword = signingProps.getProperty("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    // 雙發行通道：github (含 GitHub APK 自動下載更新器) 與 play (移除 REQUEST_INSTALL_PACKAGES，專供 Google Play AAB 發行)
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_PLAY_BUILD", "false")
        }
        create("play") {
            dimension = "distribution"
            buildConfigField("boolean", "IS_PLAY_BUILD", "true")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")

    // Compose UI & Material 3 + Adaptive Window Size
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class")
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room Database
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Preferences DataStore
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Biometric (Dual-Fingerprint Lock for Privacy Calendar)
    implementation("androidx.biometric:biometric:1.1.0")

    // Google Auth & HTTP for Google Tasks Sync + ICS Subscription
    implementation("com.google.android.gms:play-services-auth:21.2.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Image loading for custom calendar wallpaper (Jorte/vivo style background)
    implementation("io.coil-kt:coil-compose:2.7.0")

    testImplementation("junit:junit:4.13.2")
}
