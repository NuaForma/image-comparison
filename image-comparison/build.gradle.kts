plugins { `java-library` }

java { withJavadocJar() }
tasks.named<Jar>("jar") {
    manifest { attributes("Automatic-Module-Name" to "dev.nuaforma.image.comparison") }
}
tasks.withType<Javadoc>().configureEach {
    (options as StandardJavadocDocletOptions).apply {
        addBooleanOption("notimestamp", true)
        addStringOption("-release", "17")
        encoding = "UTF-8"
    }
}
