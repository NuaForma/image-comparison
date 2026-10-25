# Build and dependency review

Reviewed on 2026-10-25 using primary release documentation, official repository POMs/bytes and exact-version advisory queries. This describes the current Gradle/Kotlin build introduced in issue #17. Earlier Maven configuration and its reviews remain in Git history.

## Components and compatibility

| Component | Pin | Purpose and license |
| --- | --- | --- |
| [Gradle](https://docs.gradle.org/9.7.1/release-notes.html) | 9.7.1 | Apache-2.0 build/runtime and official wrapper; Java 17/25 supported. |
| [Kotlin JVM plugin/compiler/stdlib](https://kotlinlang.org/docs/gradle-configure-project.html) | 2.4.21 | Apache-2.0; JVM compilation and optional adapter runtime. The Java core has no Kotlin dependency. |
| [JetBrains annotations](https://github.com/JetBrains/java-annotations) | 26.1.0 | Apache-2.0; required stdlib annotations. Explicit adapter API dependency upgrades stdlib's legacy 13.0 pin consistently for Gradle and POM-only consumers. It adds no new kind of runtime component. |
| [JUnit Jupiter API/engine and Platform launcher](https://docs.junit.org/6.1.3/running-tests/build-support.html) | 6.1.3 | EPL-2.0, test-only. Gradle requires an explicit matching launcher. No unused params aggregate or mocking framework is retained. |
| JSpecify / API Guardian / OpenTest4J | 1.0.1 / 1.1.2 / 1.3.0 | Apache-2.0, test-only transitive dependencies. JSpecify is constrained to its reviewed compatible patch. |
| actions/checkout / actions/setup-java | 7.0.1 / 6.1.0 | MIT CI actions, pinned to exact commits; repository permissions remain read-only. |

Gradle 9.8.2 is the newest stable release at this review. Kotlin 2.4.21 documents compatibility through Gradle 9.7.0. We select the latest 9.7 patch (9.7.1) and explicitly qualify it on Java 17/25 rather than advancing to the newer Gradle minor before Kotlin documents support. Patch qualification is test evidence, not a broader vendor compatibility guarantee. Reassess this exception on the next Kotlin update and advance to current stable Gradle when the supported range permits it. No extra local JDK is installed or auto-downloaded.

The Gradle 9.7.1 distribution is pinned to official SHA-256 `acd53f1edaf02f1a8ff99879f8a34b302661a057d9b063ae9e35b552f804d20a`. Its generated wrapper JAR was independently compared with the official published wrapper SHA-256 `7a9ce74cff467ca1bf60a4fcd9f05185acceda4d0f382434d393e17864262c5d`. Primary version/checksum metadata: https://services.gradle.org/versions/all . This confirms official repository bytes; no independent release-signature authentication is claimed.

## Runtime and consumer boundaries

The core publication has no external compile/runtime dependencies. The adapter's public API exposes core result types and Kotlin function types, so the core and stdlib are API dependencies. The necessary annotations are explicitly versioned as well to keep Maven-POM-only resolution current. A user declares only `dev.nuaforma:image-comparison-kotlin`; the independent consumer rejects source project substitution and verifies the exact four runtime artifacts (adapter, core, stdlib, annotations) for both Gradle metadata and POM-only resolution. No JUnit, kotlin-test, Kotest, AssertJ, reflection or coroutine dependency is exported.

Both libraries emit Java 17 bytecode. Java compilation uses `--release 17`; Kotlin uses JVM target 17 and `-Xjdk-release=17` to constrain JDK APIs even when building on Java 25. The consumer compiler used for qualification is Kotlin 2.4.21; older Kotlin compiler compatibility is not promised. Input/result ownership and unresolved core semantics are documented in the Kotlin module README.

## Checksums, locks and update procedure

Module and independent consumer runtime/test configurations are locked. Plugins have exact version pins; their downloaded artifacts are also covered by strict SHA-256 verification. The standalone consumer excludes only the locally built `dev.nuaforma` snapshots from locking/checksum verification. That group resolves exclusively from the local verification repository, and the project artifact verifier checks the resulting archives and dependency metadata. External groups have no broad trust exception.

For a deliberate dependency change, run the affected verification commands with `--write-locks --write-verification-metadata sha256`, inspect versions/licenses/security and review every new hash against fresh official bytes with:

```sh
python3 tools/review-dependency-checksums.py --refresh --review-date YYYY-MM-DD
python3 tools/review-dependency-checksums.py
```

The refresh command checks existing generated hashes against Maven Central or the official Gradle Plugin Portal; it does not accept mismatching bytes or silently add alternate hashes. Commit matching `docs/dependency-verification-receipts.json` after reviewing the change. CI checks receipt consistency without refetching all artifacts. Hash receipts establish repository-byte consistency, not a complete source-code or vulnerability audit. Do not disable verification, remove lock enforcement or add broad trust entries to obtain a passing build.

Exact-version OSV queries on the review date returned no listed advisories for Kotlin stdlib/plugin/compiler 2.4.21, JetBrains annotations 26.1.0, JUnit Jupiter API/engine and Platform commons/engine/launcher 6.1.3, JSpecify 1.0.1, API Guardian 1.1.2 and OpenTest4J 1.3.0. Database results are time-bound and do not cover every build transitive.

## Attribution and documentation

The upstream LICENSE, author history and fixtures are preserved. Each binary/source/documentation archive includes LICENSE, NOTICE and MODIFICATIONS.md. The wrapper retains its original Apache headers. Kotlin stdlib/annotations and compiler/build tools are separate dependencies, not shaded into the libraries. Preserve their own notices if separately redistributing a tool/runtime distribution.

Java Javadoc retains its generated OpenJDK support-asset notices, including GPLv2 with the Classpath Exception and applicable bundled asset notices; recheck the actual release JDK's output before distribution. Kotlin API documentation uses source KDoc and a small manually maintained HTML reference without third-party JavaScript/fonts or a documentation plugin. No documentation site, release, package or namespace claim is published by this change.
