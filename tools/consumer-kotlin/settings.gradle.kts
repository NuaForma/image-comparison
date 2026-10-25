pluginManagement {
    repositories { gradlePluginPortal(); mavenCentral() }
}
rootProject.name = "independent-kotlin-consumer"
dependencyResolutionManagement {
    repositories {
        exclusiveContent {
            forRepository {
                maven {
                    name = "verification"
                    url = uri("../../build/verification-repository")
                    if (providers.gradleProperty("pomOnly").orNull == "true") {
                        metadataSources { mavenPom(); ignoreGradleMetadataRedirection() }
                    }
                }
            }
            filter { includeGroup("dev.nuaforma") }
        }
        mavenCentral()
    }
}
