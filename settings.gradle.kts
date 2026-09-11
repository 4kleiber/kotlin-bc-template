pluginManagement {
    repositories {
        maven { url = uri("https://repo.maven.apache.org/maven2") }
        maven { url = uri("https://repo.spring.io/milestone") }
        maven { url = uri("https://plugins.gradle.org/m2") }
    }
}

rootProject.name = "kotlin-bc-template"
include("domain")
include("application")
include("storage")
