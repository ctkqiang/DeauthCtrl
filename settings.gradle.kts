pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "DeauthCtrl"
include(":app")
// Termux 终端引擎（v0.118.3，Apache 2.0，逐文件原样 vendor）
include(":terminal-emulator")
include(":terminal-view")