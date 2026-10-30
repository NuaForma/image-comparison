# Maven development repositories

Stable versions (`X.Y.Z`) publish to **Maven Central**. Immutable development versions (`X.Y.Z-alpha.N`, beta, RC and other SemVer prereleases) publish to **Nexus prereleases**. Canonical `X.Y.Z-SNAPSHOT` versions publish to **Nexus snapshots**. Existing artifacts already on Central remain available there; they are not migrated or overwritten.

| Version | Destination | Publication behavior |
| --- | --- | --- |
| `1.2.3` | `https://repo.maven.apache.org/maven2/` | Existing Central validation and publication gates |
| `1.2.3-alpha.1`, `1.2.3-beta.1`, `1.2.3-rc.1` | `https://repo.nuaforma.dev/repository/nuaforma-prereleases/` | Immutable, immediately public after upload |
| `1.2.3-SNAPSHOT` | `https://repo.nuaforma.dev/repository/nuaforma-snapshots/` | Native Maven timestamp/build-number generations |

There is no fallback to Central when Nexus fails. Invalid versions, noncanonical snapshot spellings, build metadata and path fragments fail closed. Central upload/publish helpers reject every non-stable version, including when invoked directly or through an older workflow. Read-only verification of previously published Central alphas remains possible.

## Nexus operator setup

Create two **maven2 (hosted)** repositories. Keep **Online** enabled, **Layout policy: Strict**, and use the intended blob store:

- `nuaforma-prereleases`: **Version policy: Release**, **Deployment policy: Disable redeploy**.
- `nuaforma-snapshots`: **Version policy: Snapshot**, **Deployment policy: Allow redeploy** (Maven metadata changes for new timestamped generations).

For each repository, grant the dedicated publisher only the corresponding `nx-repository-view-maven2-<repository>-browse`, `-read`, `-add`, and `-edit` privileges. `edit` supports Maven PUT deployment and metadata updates; Nexus's deployment policy still refuses replacing immutable release artifacts. Repository administration and delete privileges are unnecessary. Enable anonymous access with only the matching **browse/read** privileges; give anonymous users no upload rights. Verify these policies in the Nexus UI before the first real publication. Anonymous HTTP checks cannot prove authenticated deployment policy.

Use organization secrets **`NEXUS_USERNAME`** and **`NEXUS_PASSWORD`**, scoped to `NuaForma/NuaForma` and `NuaForma/image-comparison`. Continue using **`MAVEN_SIGNING_KEY`** and **`MAVEN_SIGNING_PASSWORD`** for both destinations. Stable Central operations keep **`MAVEN_CENTRAL_USERNAME`** and **`MAVEN_CENTRAL_PASSWORD`**. Resolve unintended repository/environment overrides by name. Secret values stay in 1Password and process/step environments; do not put credentials in repository URLs, Gradle properties, workflow inputs, files, command arguments or logs. Signing/publication commands disable the configuration cache.

## Automatic develop snapshots

Every merge into protected `develop` runs the complete Java 17/25 and release-preparation checks, then publishes a signed snapshot of **both** `image-comparison` and `image-comparison-kotlin`. PRs, forks, codex branches, main pushes and manual CI runs do not automatically publish. NuaForma's separate product snapshot workflow remains manual.

The shared publisher derives the development coordinate from the checked-out source: stable `5.0.0` produces `5.0.1-SNAPSHOT`, an immutable `5.1.0-alpha.1` produces `5.1.0-SNAPSHOT`, and an explicit `5.1.0-SNAPSHOT` source version is retained. Publication overrides the Maven version without changing the source file or published stable installation examples.

CI and publication use GitHub's native `queue: max` concurrency queues, with cancellation disabled. Newer merges do not replace pending runs. This also serializes metadata updates; GitHub queues up to 100 pending runs per group ([concurrency documentation](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)).

Before loading publishing secrets, the reusable publisher verifies the exact signed checkout, the calling Build run's repository/branch/event/revision, and successful Java 17, Java 25 and disposable-key release-preparation jobs. It checks the current run rather than an unrelated check with the same name. No publication credential is provided to ordinary test jobs. Verification receipts record the source commit and workflow run alongside anonymous artifact/signature hashes.

Gradle's native Maven publisher produces timestamp/build-number metadata. Remote tasks still require explicit opt-in, canonical snapshot versions and in-memory signing. Anonymous verification compares POM/main/source/documentation JARs, Gradle module metadata when present, and all signatures. No release tag, GitHub release, container or site deployment is created by a snapshot publication.

For deliberate manual recovery, select **Publish signed snapshot to Nexus**, choose **develop**, supply the explicit snapshot version and type **PUBLISH_SNAPSHOT**. This uses the same serialized publisher and exact-source checks. It does not weaken immutable prerelease or Central approval gates.

## Immutable prerelease integrity and recovery

The prerelease route reuses the reviewed Maven-layout bundle and signature validation; it does not rebuild or re-sign after approval. The uploader checks the reviewed bundle hash, every artifact hash, required detached signatures and checksums; rejects unexpected/duplicate entries; checks every target path and existing module index before writing; then uploads the same bytes. It maintains module `maven-metadata.xml` and its checksums, preserves earlier versions and uses available metadata ETags as conflict guards. Existing index entries also refuse a repeated version.

Only trusted fixed HTTPS Nexus URLs receive authentication. Redirects are rejected, no write is automatically retried by the prerelease uploader, and anonymous readback verifies every artifact, signature, checksum and module index. A JSON journal records successful paths and the pending path **before** each write, with no credentials. A success response alone is not proof of public byte identity.

Nexus PUT publication is **not an atomic multi-artifact transaction**. If it fails partway through, keep the receipt and inspect the pending path and repository state; do not rerun the whole workflow or delete/overwrite a release version to hide a partial upload. Resume only a separately reviewed missing operation using the original approved bytes, or select a new prerelease version and obtain its normal approval. Snapshot generations are deliberately mutable development coordinates; their workflow also performs no whole-job automatic retry.

## Anonymous consumers

Stable users continue to use Maven Central. Add the following repository when opting into a **new Nexus prerelease**; keep dependency versions explicit. No consumer credentials are needed.

```xml
<repositories>
  <repository>
    <id>nuaforma-prereleases</id>
    <url>https://repo.nuaforma.dev/repository/nuaforma-prereleases/</url>
    <releases><enabled>true</enabled></releases>
    <snapshots><enabled>false</enabled></snapshots>
  </repository>
</repositories>
```

```kotlin
repositories {
    mavenCentral()
    maven {
        name = "nuaformaPrereleases"
        url = uri("https://repo.nuaforma.dev/repository/nuaforma-prereleases/")
        content { includeGroup("dev.nuaforma") }
        mavenContent { releasesOnly() }
    }
}
```

For snapshots add the separate `nuaforma-snapshots` URL, enable snapshots and disable releases in Maven, or use `mavenContent { snapshotsOnly() }` in Gradle. Avoid dynamic versions in reproducible builds: snapshots may resolve to a newer generation later. Publishing these workflow changes does not publish any artifact; a first authorized Nexus run is still needed to verify the real credentials and server write policies.

The Maven client maintains repository metadata ([Sonatype documentation](https://help.sonatype.com/en/tasks.html)); Gradle supports Nexus and native snapshot publishing through its existing [Maven Publish plugin](https://docs.gradle.org/current/userguide/publishing_maven.html). No runtime dependency is added by this routing.
