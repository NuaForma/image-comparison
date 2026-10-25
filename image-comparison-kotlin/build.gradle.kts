import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `java-library`
    kotlin("jvm")
}

kotlin {
    explicitApi()
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        freeCompilerArgs.add("-Xjdk-release=17")
    }
}

dependencies {
    api(project(":image-comparison"))
    api("org.jetbrains.kotlin:kotlin-stdlib:2.4.21")
    // A direct API pin also preserves this required stdlib transitive version for POM-only consumers.
    api("org.jetbrains:annotations:26.1.0")
}

tasks.named<Jar>("jar") {
    manifest { attributes("Automatic-Module-Name" to "dev.nuaforma.image.comparison.kotlin") }
}

val documentationJar = tasks.register<Jar>("documentationJar") {
    archiveClassifier.set("javadoc")
    from("docs")
    from("README.md")
}
tasks.named("assemble") { dependsOn(documentationJar) }
tasks.named("assemble") { dependsOn("kotlinSourcesJar") }
tasks.named<AbstractArchiveTask>("kotlinSourcesJar") {
    from(rootProject.projectDir) {
        include("LICENSE", "NOTICE", "MODIFICATIONS.md")
        into("META-INF")
    }
}
publishing.publications.named<MavenPublication>("mavenJava") {
    artifact(documentationJar)
    artifact(tasks.named("kotlinSourcesJar"))
}
