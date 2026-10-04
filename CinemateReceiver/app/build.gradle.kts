import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val keystoreProps = Properties()
val keystorePropsFile = rootProject.file("local.properties")
if (keystorePropsFile.exists()) {
    keystorePropsFile.inputStream().use { keystoreProps.load(it) }
}

android {
    namespace = "com.cinemate.receiver"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cinemate.receiver"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../CinemateReceiver-release.jks")
            storePassword = keystoreProps.getProperty("KEYSTORE_PASSWORD", "")
            keyAlias = "cinemate"
            keyPassword = keystoreProps.getProperty("KEY_PASSWORD", "")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    lint {
        // Release-сборки без lint-vital: падает на внутреннем баге lint+JDK,
        // на работу APK не влияет.
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")

    // Крошечный HTTP-сервер
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}