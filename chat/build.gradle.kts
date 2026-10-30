import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
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
        // Set language and API version to 2.0 for backward compatibility with Kotlin 2.0 projects
        languageVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
        apiVersion.set(org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_0)
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_1_8)
            freeCompilerArgs.add("-Xjvm-default=all")
        }
    }

    sourceSets {
        commonMain.dependencies {
            /*
             * Apps choose their side by depending on io.ably.pubsub:device or io.ably.pubsub:server, both of which
             * publish the same io.ably.pubsub.* types, so chat compiles against core and works with either.
             */
            compileOnly(libs.ably.pubsub.core)
            implementation(libs.ably.pubsub.adapter)
            implementation(libs.gson)
            implementation(libs.coroutine.core)
        }
        androidMain.dependencies {
            implementation(libs.coroutine.android)
        }
        commonTest.dependencies {
            implementation(libs.ably.pubsub.device)
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
    compileSdk = 34
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
