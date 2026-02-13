import com.vanniktech.maven.publish.MavenPublishBaseExtension

plugins {
    alias(libs.plugins.detekt)
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.kotlin) apply false
    alias(libs.plugins.multiplatform.kotlin) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.build.config) apply false
    alias(libs.plugins.maven.publish) apply false
    alias(libs.plugins.binary.compatibility.validator) apply false
}

dependencies {
    detektPlugins(libs.detekt.formatting)
    detektPlugins(libs.detekt.rules.libraries)
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

detekt {
    description = "Runs detekt for all modules"
    autoCorrect = true
}

// Detekt for SDK modules (strict rules)
tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektSdk") {
    description = "Run detekt on SDK modules"
    config.setFrom(files("${rootProject.rootDir}/detekt.yml"))
    setSource(files(
        "chat/src",
        "chat-extensions-compose/src",
        "test-fixtures/src",
    ))
    include("**/*.kt")
    exclude("**/build/**")
    reports {
        md.required.set(true)
        html.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
    }
}

// Detekt for Compose UI modules (relaxed rules)
tasks.register<io.gitlab.arturbosch.detekt.Detekt>("detektCompose") {
    description = "Run detekt on Compose UI modules"
    config.setFrom(files("${rootProject.rootDir}/detekt-compose.yml"))
    setSource(files(
        "chat-ui-compose/src",
        "example/src",
        "example-ui-kit/src",
    ))
    include("**/*.kt")
    exclude("**/build/**")
    reports {
        md.required.set(true)
        html.required.set(false)
        xml.required.set(false)
        txt.required.set(false)
        sarif.required.set(false)
    }
}

// Configure main detekt task to run both SDK and Compose tasks
tasks.named("detekt") {
    dependsOn("detektSdk", "detektCompose")
}

tasks.register("check") {
    // register check task for the root project so our detekt task will run on `gradlew check`
}

configure(subprojects) {
    pluginManager.withPlugin("com.vanniktech.maven.publish") {
        extensions.configure<MavenPublishBaseExtension> {
            signAllPublications()
        }
    }
}
