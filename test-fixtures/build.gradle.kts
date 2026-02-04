plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.kotlin)
}

android {
    namespace = "com.ably.chat.test.fixtures"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":chat"))
    implementation(libs.bundles.ktor.client)
    implementation(libs.gson)
    implementation(libs.coroutine.core)
}
