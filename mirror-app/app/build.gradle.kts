import java.util.Properties
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val signingProps = Properties().apply {
    val f = file("${System.getProperty("user.home")}/.android/autopenn-signing.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.autopenn.mirror"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.penn.auto_penn"
        minSdk = 29
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.3"
    }

    signingConfigs {
        create("upload") {
            if (signingProps.isNotEmpty()) {
                storeFile = file(signingProps.getProperty("storeFile"))
                storePassword = signingProps.getProperty("storePassword")
                keyAlias = signingProps.getProperty("keyAlias")
                keyPassword = signingProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("upload")
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.core:core-ktx:1.17.0")
    // On-device ADB client (Wireless debugging) so the app can start its own shell helper, no computer needed.
    implementation("com.github.MuntashirAkon:libadb-android:3.1.1")
    implementation("com.github.MuntashirAkon:sun-security-android:1.1")
    implementation("org.conscrypt:conscrypt-android:2.5.3")
}
