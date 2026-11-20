plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.phoneclouddrive.admin"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.phoneclouddrive.admin"
        // Android 10+: the bundled server binaries need exec from nativeLibraryDir (W^X rules)
        // and All-files access for the drive folder.
        minSdk = 29
        targetSdk = 35
        versionCode = 7
        versionName = "2.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            // Personal sideloaded builds: sign with the debug key unless you add your own config.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // libsftpgo.so / libcloudflared.so are executables (see native/build.sh). They must be
            // extracted to nativeLibraryDir, the only place an app may run programs from.
            useLegacyPackaging = true
            keepDebugSymbols += "**/*.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

/** Fails early with a clear message instead of producing an APK without the server. */
val checkNativeServer by tasks.registering {
    val jni = file("src/main/jniLibs")
    val ui = file("src/main/assets/sftpgo-ui.zip")
    doLast {
        val binaries = jni.walkTopDown().filter { it.name == "libsftpgo.so" || it.name == "libcloudflared.so" }.toList()
        if (!ui.exists() || binaries.size < 2) {
            throw GradleException("Built-in server files are missing. Run 'bash android/native/build.sh' first (see docs/android-app.md).")
        }
    }
}
tasks.named("preBuild") { dependsOn(checkNativeServer) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-service:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // QR codes for the drive's addresses (encoder only, pure Java).
    implementation("com.google.zxing:core:3.5.3")

    testImplementation("junit:junit:4.13.2")
    // Android's org.json is a stub in local unit tests; use the real implementation there.
    testImplementation("org.json:json:20240303")
}
