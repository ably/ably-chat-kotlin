
rootProject.name = "Ably Chat SDK"
include("chat-android")
include("example")
include("chat-extensions-compose")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
