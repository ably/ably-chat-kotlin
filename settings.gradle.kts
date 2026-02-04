
rootProject.name = "Ably Chat SDK"
include("chat")
include("example")
include("example-ui-kit")
include("chat-extensions-compose")
include("chat-ui-compose")
include("test-fixtures")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
