
rootProject.name = "Ably Chat SDK"
include("chat")
include("example")
include("example-ui-kit")
include("chat-extensions-compose")
include("chat-ui-compose")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
