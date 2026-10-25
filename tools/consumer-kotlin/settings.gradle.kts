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
                    url = uri(providers.gradleProperty("comparisonRepository").orElse("../../build/verification-repository").get())
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
