import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.multiplatform.kotlin)
    alias(libs.plugins.android.library)
    alias(libs.plugins.build.config)
    alias(libs.plugins.kover)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.dokka)
    alias(libs.plugins.binary.compatibility.validator)
}

val version = property("VERSION_NAME")

kotlin {
    explicitApi()

    jvmToolchain(17)

    compilerOptions {
        optIn.add("com.ably.chat.annotations.ExperimentalChatApi")
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            jvmDefault.set(JvmDefaultMode.NO_COMPATIBILITY)
        }
    }

    sourceSets {
        commonMain.dependencies {
            compileOnly(libs.ably.java)
            implementation(libs.ably.pubsub.adapter)
            implementation(libs.gson)
            implementation(libs.coroutine.core)
        }
        androidMain.dependencies {
            api(libs.ably.android)
            implementation(libs.coroutine.android)
        }
        jvmMain.dependencies {
            api(libs.ably.java)
        }
        commonTest.dependencies {
            implementation(libs.junit)
            implementation(libs.mockk)
            implementation(libs.coroutine.test)
            implementation(libs.bundles.ktor.client)
            implementation(libs.nanohttpd)
            implementation(libs.turbine)
        }
        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }
    }
}

android {
    namespace = "com.ably.chat"
    compileSdk = 36
    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
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

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    afterEvaluate {
        tasks.withType<Test>().configureEach {
            testLogging {
                exceptionFormat = TestExceptionFormat.FULL
            }
        }
    }
}

buildConfig {
    packageName("com.ably.chat")
    useKotlinOutput { internalVisibility = true }
    buildConfigField("APP_VERSION", provider { version.toString() })
}

mavenPublishing {
    configure(KotlinMultiplatform(javadocJar = JavadocJar.Empty(), androidVariantsToPublish = listOf("release")))
}
