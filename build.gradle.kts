import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.plugins.signing.SigningExtension

plugins {
    base
    kotlin("jvm") version "2.4.21" apply false
}

val publicationVersion = providers.gradleProperty("releaseVersion")
    .orElse(providers.gradleProperty("version")).get()
require(publicationVersion.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z.-]+)?"))) {
    "Publication version must be a safe explicit version"
}
allprojects {
    group = "dev.nuaforma"
    version = publicationVersion
}

val publishSnapshots = providers.gradleProperty("publishSnapshots").map { it.toBooleanStrict() }.orElse(false).get()
if (publishSnapshots) {
    require(Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)-SNAPSHOT").matches(publicationVersion)) {
        "Nexus snapshots require canonical X.Y.Z-SNAPSHOT"
    }
    tasks.register("publishSnapshots") {
        group = "publishing"
        description = "Publish signed development snapshots using Maven timestamp metadata (explicit opt-in)."
        dependsOn(subprojects.map { "${it.path}:publishMavenJavaPublicationToNexusSnapshotsRepository" })
    }
}

subprojects {
    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    dependencyLocking { lockAllConfigurations() }

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        if (project.name == "image-comparison") withSourcesJar()
    }
    tasks.withType<JavaCompile>().configureEach { options.release.set(17) }
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        systemProperty("java.awt.headless", "true")
        failOnNoDiscoveredTests.set(true)
    }
    tasks.withType<AbstractArchiveTask>().configureEach {
        isPreserveFileTimestamps = false
        isReproducibleFileOrder = true
    }
    tasks.withType<Jar>().configureEach {
        from(rootProject.projectDir) {
            include("LICENSE", "NOTICE", "MODIFICATIONS.md")
            into("META-INF")
        }
    }
    dependencies {
        add("testImplementation", "org.junit.jupiter:junit-jupiter-api:6.1.3")
        add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:6.1.3")
        add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:6.1.3")
        constraints { add("testImplementation", "org.jspecify:jspecify:1.0.1") }
    }
    extensions.configure<PublishingExtension> {
        publications.create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name.set(if (project.name == "image-comparison") "NuaForma Image Comparison" else "NuaForma Image Comparison Kotlin DSL")
                description.set(if (project.name == "image-comparison") "Generic Java image comparison without external runtime dependencies" else "Optional framework-independent Kotlin image assertions")
                url.set("https://github.com/NuaForma/image-comparison")
                licenses { license { name.set("Apache License, Version 2.0"); url.set("https://www.apache.org/licenses/LICENSE-2.0") } }
                developers {
                    developer { id.set("romankh3"); name.set("Roman Beskrovnyi"); roles.set(listOf("Original author")); url.set("https://github.com/romankh3") }
                    developer { id.set("NuaForma"); name.set("NuaForma maintainers"); roles.set(listOf("Fork maintainer")); url.set("https://github.com/NuaForma"); organization.set("NuaForma"); organizationUrl.set("https://nuaforma.dev") }
                }
                scm { url.set("https://github.com/NuaForma/image-comparison"); connection.set("scm:git:https://github.com/NuaForma/image-comparison.git"); developerConnection.set("scm:git:ssh://git@github.com/NuaForma/image-comparison.git") }
            }
        }
        repositories {
            maven {
                name = "verification"
                url = rootProject.layout.buildDirectory.dir("verification-repository").get().asFile.toURI()
            }
            if (publishSnapshots) {
                maven {
                    name = "NexusSnapshots"
                    url = uri("https://repo.nuaforma.dev/repository/nuaforma-snapshots/")
                    credentials {
                        username = providers.environmentVariable("NEXUS_USERNAME").orNull
                        password = providers.environmentVariable("NEXUS_PASSWORD").orNull
                    }
                }
            }
        }
    }
    val signArtifacts = providers.gradleProperty("signArtifacts").orNull
    tasks.withType<org.gradle.api.publish.maven.tasks.PublishToMavenRepository>().configureEach {
        doFirst {
            require(repository.name != "NexusSnapshots" || signArtifacts == "true") {
                "Remote snapshot publication requires in-memory artifact signing"
            }
        }
    }
    require(signArtifacts == null || signArtifacts in setOf("true", "false")) { "signArtifacts must be true or false" }
    if (signArtifacts == "true") {
        apply(plugin = "signing")
        val key = providers.environmentVariable("MAVEN_SIGNING_KEY").orNull
        val password = providers.environmentVariable("MAVEN_SIGNING_PASSWORD").orNull
        require(!key.isNullOrBlank() && !password.isNullOrBlank()) { "Artifact signing credentials are required" }
        extensions.configure<SigningExtension> {
            useInMemoryPgpKeys(key, password)
            sign(extensions.getByType<PublishingExtension>().publications["mavenJava"])
        }
    }
    tasks.named<Jar>("jar") {
        dependsOn("generatePomFileForMavenJavaPublication")
        from(layout.buildDirectory.file("publications/mavenJava/pom-default.xml")) {
            into("META-INF/maven/dev.nuaforma/${project.name}")
            rename { "pom.xml" }
        }
    }
    tasks.named("check") { dependsOn("generatePomFileForMavenJavaPublication") }
}

tasks.named("build") { dependsOn(subprojects.map { "${it.path}:build" }) }
tasks.named("clean") { dependsOn(subprojects.map { "${it.path}:clean" }) }

val verifyArtifacts = tasks.register<Exec>("verifyArtifacts") {
    dependsOn(":image-comparison:assemble", ":image-comparison-kotlin:assemble")
    commandLine("python3", rootProject.file("tools/verify-artifact.py"), "--version", publicationVersion)
}
tasks.named("check") { dependsOn(verifyArtifacts, subprojects.map { "${it.path}:check" }) }

val coreJar = project(":image-comparison").tasks.named<Jar>("jar").flatMap { it.archiveFile }
val javaConsumerDirectory = layout.buildDirectory.dir("consumers/java")
val compileJavaConsumer = tasks.register<Exec>("compileJavaConsumer") {
    dependsOn(":image-comparison:jar")
    inputs.files("tools/consumer/Java17Consumer.java", "tools/consumer/GroupingConsumer.java")
    inputs.file(coreJar)
    outputs.dir(javaConsumerDirectory)
    doFirst {
        javaConsumerDirectory.get().asFile.mkdirs()
        commandLine("${System.getProperty("java.home")}/bin/javac", "--release", "17", "-cp",
            coreJar.get().asFile.absolutePath, "-d", javaConsumerDirectory.get().asFile.absolutePath,
            rootProject.file("tools/consumer/Java17Consumer.java").absolutePath,
            rootProject.file("tools/consumer/GroupingConsumer.java").absolutePath)
    }
}
val verifyJavaConsumer = tasks.register<JavaExec>("verifyJavaConsumer") {
    dependsOn(compileJavaConsumer)
    classpath = files(javaConsumerDirectory, coreJar)
    mainClass.set("Java17Consumer")
    systemProperty("java.awt.headless", "true")
}

// Exercise thin/dense/disconnected regions with explicit stack/heap limits against the core JAR.
tasks.register<JavaExec>("verifyGroupingConsumer") {
    dependsOn(compileJavaConsumer)
    classpath = files(javaConsumerDirectory, coreJar)
    mainClass.set("GroupingConsumer")
    systemProperty("java.awt.headless", "true")
    maxHeapSize = "128m"
    jvmArgs("-Xss1m")
}

// Ordinary builds publish only to the ignored local verification repository; remote snapshots require explicit opt-in.
tasks.register("stageLibraries") {
    dependsOn(subprojects.map { "${it.path}:publishAllPublicationsToVerificationRepository" })
}
tasks.register<Exec>("verifyLibraryPublication") {
    dependsOn("stageLibraries")
    commandLine("python3", rootProject.file("tools/release/publications.py"),
        "--repository", layout.buildDirectory.dir("verification-repository").get().asFile.absolutePath,
        "--version", publicationVersion, "--output", layout.buildDirectory.file("release-review/publications.json").get().asFile.absolutePath)
}
