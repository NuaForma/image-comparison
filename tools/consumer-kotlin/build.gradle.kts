import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.4.21"
    application
}

dependencyLocking {
    lockAllConfigurations()
    ignoredDependencies.add("dev.nuaforma:*")
}
configurations.configureEach { resolutionStrategy.cacheChangingModulesFor(0, "seconds") }
dependencies {
    // Deliberately the only declared library dependency: core and runtime must arrive transitively.
    implementation("dev.nuaforma:image-comparison-kotlin:4.4.1-SNAPSHOT")
}
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
application { mainClass.set("KotlinConsumerKt") }

val verifyDependencyGraph = tasks.register("verifyDependencyGraph") {
    doLast {
        val configuration = configurations.runtimeClasspath.get()
        val rootId = configuration.incoming.resolutionResult.rootComponent.get().id
        check(configuration.incoming.resolutionResult.allComponents.none {
            it.id is ProjectComponentIdentifier && it.id != rootId
        }) { "Consumer must resolve published artifacts, not source project dependencies" }
        val artifacts = configuration.incoming.artifacts.artifacts
        val identities = artifacts.map { it.id.componentIdentifier as ModuleComponentIdentifier }
        val names = identities.map { "${it.group}:${it.module}" }.toSet()
        check(names == setOf("dev.nuaforma:image-comparison-kotlin", "dev.nuaforma:image-comparison",
            "org.jetbrains.kotlin:kotlin-stdlib", "org.jetbrains:annotations")) { names }
        check(identities.first { it.module == "annotations" }.version == "26.1.0")
        println("Single dependency resolved transitively: " + identities.sortedBy { it.toString() })
    }
}
tasks.named("run") { dependsOn(verifyDependencyGraph) }
tasks.withType<JavaExec>().configureEach { systemProperty("java.awt.headless", "true") }
