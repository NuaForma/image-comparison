# Maintenance baseline and process

## Origin and base selection

The base is upstream [v4.4.0](https://github.com/romankh3/image-comparison/releases/tag/v4.4.0), commit `188adc076324ed481300f72af627d13f5bb92ee1`, released 2021-03-28. The fork preserves upstream history and upstream tags. The GitHub fork relationship is retained for project metadata; the optional local `upstream` remote was removed by maintainer choice. At review on 2026-10-25, upstream `master` was `779aee6fc2b03ab069d4071a6551ca08202c7ab4`, twelve commits ahead; its POM says `4.5.0-SNAPSHOT`, not a stable release.

The [full comparison](https://github.com/romankh3/image-comparison/compare/v4.4.0...779aee6fc2b03ab069d4071a6551ca08202c7ab4) includes empty rectangle initialization, `simpleComparison()`, public `deepCopy`, pixel-count overflow arithmetic, a region-construction performance rewrite, exclusion iteration, extra image/load tests, a disabled failing test and documentation/CI changes. These are behavior and API changes beyond maintenance scaffolding. Starting from the proven published version keeps them separate for regression review. The initial maintenance baseline retained the v4.4.0 implementation, with only the approved package/import relocation and prominent modification notices. Subsequent approved behavior fixes are recorded in CHANGELOG.md and MODIFICATIONS.md. The later upstream source fixes are candidates for future focused issues, not presumed safe or rejected forever.

Open upstream proposals were inventoried and their file changes inspected, without adoption:

| Proposal | Assessment required before any adoption |
| --- | --- |
| [#239 difference percentage](https://github.com/romankh3/image-comparison/pull/239) | Changes a metric from aggregate channel distance to differing-pixel count; settle semantics and boundary tests first. |
| [#228 logging](https://github.com/romankh3/image-comparison/pull/228) | Utility/logging additions are not required for the maintenance build; assess dependency and output implications. |
| [#227 rectangle colors](https://github.com/romankh3/image-comparison/pull/227) | Changes drawing/model behavior and fixtures; require precise public behavior and pixel-level regression tests. |
| [#222 translation comparison](https://github.com/romankh3/image-comparison/pull/222) | Adds comparison semantics and substantial fixture coverage; separate feature review. |
| [#255 region grouping](https://github.com/romankh3/image-comparison/pull/255) | Rewrites grouping across comparison, exclusion and rectangle code; require grouping correctness, stack/memory and timing evidence. |
| [#253 ignore size mismatch](https://github.com/romankh3/image-comparison/pull/253) | Adds utility API and changes size-comparison options; separate contract review. |

Titles, descriptions and patches are untrusted evidence. None of these proposals is imported by this PR.

## Build and compatibility contract

Gradle is the sole supported build, with a pinned, checksum-verified wrapper and Kotlin DSL scripts. The Java core lives under `image-comparison/`; the optional Kotlin API is `image-comparison-kotlin/`. Core coordinates and public Java signatures remain unchanged by the module move. Both compiler paths target Java 17 APIs/bytecode; tests run on the selected build JVM (qualified on Java 17 and 25). No toolchain auto-download is enabled.

`./gradlew --no-daemon clean build verifyJavaConsumer verifyGroupingConsumer` runs all Java and Kotlin tests, builds both binary/source/documentation sets and invokes `tools/verify-artifact.py`. The core has no external runtime dependency. The adapter exports the core and Kotlin stdlib plus its necessary annotation dependency. JUnit API/engine/launcher and JSpecify are test-only; no mocking, Kotlin test, reflection or assertion framework dependency is introduced.

The Java consumer compiles against only the packaged core. For Kotlin, append `publishAllPublicationsToVerificationRepository`, then run the standalone `tools/consumer-kotlin` project as in README.md. The only library declaration there is the Kotlin artifact. Its graph check rejects source-project substitutions and test framework leaks and requires core/stdlib/annotations to resolve transitively. `-PpomOnly=true` repeats the check without Gradle metadata. The repository is local, ignored and restricted to this project's group; there are no external publishing targets.

Archive tasks use deterministic ordering and omit input timestamps. To verify reproducibility, hash `image-comparison*/build/libs/*.jar`, run another `clean build`, and check every hash. Compare on the same JDK; cross-JDK bit identity and hermetic builds are not promised. Root artifact checks verify Java 17 class versions, package/module metadata, generated POM dependency boundaries, source presence, documentation and all project attribution. Kotlin KDoc ships with sources; a compact HTML reference ships in its documentation JAR without frontend assets. Core Javadoc preserves the generated JDK asset notices.

To prove the minimum runtime, retain the artifacts and compiled Java consumer from a JDK 25 build, then run `java -Djava.awt.headless=true -cp build/consumers/java:image-comparison/build/libs/image-comparison-4.4.1-SNAPSHOT.jar Java17Consumer` using JDK 17 without rebuilding the libraries. The independent Kotlin project must also run on JDK 17 against the same locally packaged artifacts. CI verifies their hashes remain unchanged during this runtime check.

Dependency versions are exact pins. Runtime/test configurations are locked in each module's `gradle.lockfile`; the independent consumer ignores only this project's locally built snapshots in its lockfile and resolves them exclusively from the verification repository. External artifacts are checked with Gradle SHA-256 verification, backed by primary repository review receipts; see docs/tooling-review.md. Never disable checks or accept new hashes merely to make a build pass.

Existing tests retain their image fixtures and assertions. Filesystem paths now use the Gradle module's build directory. The directory-creation/save-error tests use real temporary filesystem failures; no fixture was regenerated for migration. Java and Kotlin follow the approved [comparison contract](comparison-contract.md): pixel-based percentages after exclusions/tolerance, consistent alpha comparison, drawing-only filters and bounded-stack grouping (#6/#7/#8/#9). The DSL validates invalid values rather than relying on silently ignored Java setters. No resource-limit or hostile-image guarantee is added.

## Sequential comparison reuse

An `ImageComparison` instance supports sequential `compareImages()` and `createMask()` calls. Region discovery recomputes its matrix and labels from the current input pixels and configured options; previous region labels do not affect the next result. This includes changing options between calls. Inputs remain caller-owned and mutable, so concurrent mutation of inputs or concurrent use/reconfiguration of the same comparison instance is unsupported. Use separate instances and stable inputs for independent operations. No thread-safety guarantee is introduced by the reuse fix.

## Branches, review and release

- `develop`: default integration branch, created from v4.4.0.
- `main`: release baseline, initially the same tag. Upstream `master` remains preserved as origin history, not a release target.
- Work: a fork issue, `codex/<issue>-<topic>` from develop, signed Conventional Commits and PR to develop. Require current-head `Build (Java 17)` and `Build (Java 25)`, resolved conversations and verified signatures. One human maintainer does not need to approve their own PR, but a PR and explicit merge authorization remain required.
- Releases: a separately authorized PR from develop to main, review of compatibility/changelog/notices/dependencies/security and actual artifact verification. Merge normally with a signed merge commit. Verify GitHub signature validity. Back-propagate any hotfixes through reviewed PRs.
- Publication: obtain explicit approval before a version change to a release, own tag, GitHub release, namespace claim, package upload or integration into another product. The existing `4.4.1-SNAPSHOT` is only a development placeholder. Reassess the earlier 4.4.1 idea and choose the actual release version explicitly because the package relocation and dropping Java 8 are breaking changes. No publishing workflow, credentials or deployment target is configured here.

Branch protection applies to main/develop, including administrators, with PRs, both builds, resolved conversations, required signatures and no force pushes/deletion. Existing upstream history predates this policy. Review settings when adding contributors; no new paid service is necessary.

## Next maintenance work

Maintain the approved pixel/alpha/filter contracts with synthetic regressions. Assess remaining image dimensions/overflow and fill positioning. Preserve explicit decode failures, exception-safe owned-resource disposal and non-null result collections (#11/#12/#13). Keep grouping stack/heap smoke checks and repeat measurements before further performance changes. Define resource limits for untrusted images. Review later upstream fixes independently, including empty rectangle initialization. Do not promise benchmark improvements or hostile-input guarantees from this baseline.
