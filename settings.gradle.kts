
rootProject.name = "Ably Chat SDK"
include("chat")
include("example")
include("chat-extensions-compose")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
