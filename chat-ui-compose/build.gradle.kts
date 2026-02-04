import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.android.kotlin)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.ably.chat.ui"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    @Suppress("UnstableApiUsage")
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }
}

kotlin {
    explicitApi()

    jvmToolchain(17)

    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    implementation(project(":chat"))
    implementation(project(":chat-extensions-compose"))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.foundation)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.material3)
    implementation(libs.coil.compose)

    // Unit test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutine.test)
    testImplementation(libs.turbine)
    testImplementation(libs.molecule)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.ui.test.junit4)

    // E2E test dependencies (shared sandbox utilities)
    testImplementation(project(":test-fixtures"))

    debugImplementation(libs.androidx.ui.test.manifest)
}

// Register e2eTest task that only runs E2E tests
tasks.register("e2eTest") {
    description = "Runs end-to-end tests against Ably sandbox."
    group = "verification"

    dependsOn("testDebugUnitTest")

    doFirst {
        // Configure the test task to only run e2e tests
        tasks.named("testDebugUnitTest", Test::class) {
            filter {
                includeTestsMatching("com.ably.chat.ui.e2e.*")
            }
        }
    }
}

// Exclude e2e tests from regular unit test runs
tasks.withType<Test>().configureEach {
    if (name == "testDebugUnitTest" || name == "testReleaseUnitTest") {
        // Only exclude if not running e2eTest task
        if (!gradle.startParameter.taskNames.any { it.contains("e2eTest") }) {
            filter {
                excludeTestsMatching("com.ably.chat.ui.e2e.*")
            }
        }
    }
}
