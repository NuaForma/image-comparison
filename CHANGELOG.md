# Changelog

## 5.0.0 — release candidate

- Prepare protected manual Maven Central workflows for the two library modules, signed source tags, exact eight-artifact review and separate upload/publish/public verification (#21).
- Include the Kotlin sources JAR in Maven publication metadata as well as local assembly; verify both complete POM/binary/source/documentation sets and standalone Maven/Gradle consumers (#21).
- Support opt-in in-memory artifact signing; ordinary builds remain credential-free. Local signing QA uses disposable identities and offline negative release-gate tests (#21).

- Reject present but undecodable image inputs at the read boundary with ImageComparisonException; preserve ImageNotFoundException for missing input, sanitize control characters in diagnostic locations and retain I/O causes (#11).
- Close owned classpath streams and dispose all library-created drawing contexts on success/failure, including graphics setup and conversion; preserve caller/result images and rendering output (#12).
- **Result contract:** rectangle collections are never null. Every result starts with its own mutable empty list; null setter input resets to a fresh empty list while non-null list aliasing/mutability remains unchanged (#13).

- **Breaking behavior:** report the percentage of non-excluded pixels exceeding tolerance; accepted matches retain their measured percentage. Exclusions affect numerator and denominator. All excluded means zero; unequal dimensions always mean `SIZE_MISMATCH` and 100%, without resizing (#6).
- **Breaking behavior:** compare alpha independently alongside the existing normalized RGB distance at every tolerance; compare straight ARGB including hidden RGB under alpha zero (#7).
- **Breaking behavior:** minimum rectangle size and maximum rectangle count filter drawing only; returned regions, masks, classification and percentage retain detected differences (#8).
- Replace recursive difference-region traversal with a bounded-call-stack heap worklist and add constrained packaged-consumer measurements (#9).
- Kotlin assertion diagnostics now label the metric as differing pixels rather than RGB intensity. Add a percentage-preserving MATCH factory overload while retaining existing factory signatures.

- Add an optional public Kotlin comparison/assertion DSL with validated configuration and inspectable framework-independent failures (#17).
- Migrate to a pinned Gradle Kotlin DSL build with separate Java core/Kotlin adapter artifacts, transitive consumer checks and unchanged core APIs.

- Preserve single-pixel differences at (0,0) when merging difference rectangles (#3).
- Reset per-operation region counters so sequential comparisons and mask creation remain consistent (#4).

- Limit test dependencies to JUnit Jupiter API and engine; remove Mockito, its instrumentation dependencies and unused parameterized-test support.
- Update the test-only transitive JSpecify annotation dependency from 1.0.0 to the reviewed 1.0.1 patch.

- Establish NuaForma Image Comparison as an independently maintained Apache-2.0 fork of upstream v4.4.0.
- Preserve the original comparison logic and method shapes.
- **Breaking API change:** relocate packages to `dev.nuaforma.image.comparison`; callers must update imports and recompile.
- **Breaking runtime change:** require Java 17 APIs/bytecode/runtime; Java 8 is no longer supported.
- Add a pinned Maven wrapper/build, explicit JUnit test engine, Java 17/25 checks and an independent Java 17 consumer check.
- Define contribution, security, attribution and release review practices.

The first NuaForma release candidate is **5.0.0**, selected by the maintainer on 25 October 2026. This source fixes the candidate version for release review (#23). No fork release, tag or Maven publication is created by release preparation.

## Upstream history

See the unchanged [upstream release notes](RELEASE_NOTES.md) and [v4.4.0 release](https://github.com/romankh3/image-comparison/releases/tag/v4.4.0). Upstream version numbers and tags identify upstream artifacts, not NuaForma releases.
