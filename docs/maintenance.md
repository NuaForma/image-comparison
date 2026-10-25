# Maintenance baseline and process

## Origin and base selection

The base is upstream [v4.4.0](https://github.com/romankh3/image-comparison/releases/tag/v4.4.0), commit `188adc076324ed481300f72af627d13f5bb92ee1`, released 2021-03-28. The fork preserves upstream history, upstream tags and a local `upstream` remote. At review on 2026-10-25, upstream `master` was `779aee6fc2b03ab069d4071a6551ca08202c7ab4`, twelve commits ahead; its POM says `4.5.0-SNAPSHOT`, not a stable release.

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

Maven is the only supported build. Use its checked-in wrapper. Production compilation uses `--release 17`, which constrains both standard APIs and classfile version; tests compile for Java 17 and run with an explicit Jupiter engine. Java 17 is also the minimum supported consumer runtime; Java 8 is deliberately unsupported. The approved package relocation to `dev.nuaforma.image.comparison` changes fully qualified class names and breaks source/binary compatibility. Method shapes remain unchanged; callers must update imports, reflection/configuration names and recompile. Later comparison fixes are identified in the changelog. The automatic module name moves to the same NuaForma namespace. The Java runtime baseline also changes incompatibly. Filesystem tests use real temporary files; no mocking framework or instrumentation agent is needed.

The build creates binary, sources and Javadoc JARs, with fixed archive timestamps and pinned plugins/dependencies. Repeat `clean verify` with the same JDK and compare SHA-256 hashes of all JARs to verify reproducibility. Different JDK compilers/Javadoc implementations can emit different archives; cross-JDK bit identity is not promised. Central access and downloaded build dependencies are needed; offline/hermetic builds are not claimed.

`tools/verify-artifact.py` checks every production class is Java 17 bytecode, uses the NuaForma package prefix, contains attribution and declares no non-test dependencies. `javac --release 17` compiles `tools/consumer/Java17Consumer.java` against only the built JAR; it exercises match, mismatch, difference marking, exclusions and size mismatch. CI runs this consumer on JDK 17/25 and explicitly runs the JDK 25-built artifact on Java 17. Local verification uses the available JDK 17 and 25 installations only; it installs no extra JDK. The consumer check is representative, not proof for every VM/vendor or arbitrary resource usage.

Tests retain upstream image fixtures and assertions; filesystem paths use Maven output locations. The obsolete mock-based save-error test now writes to a real directory to exercise the same IOException contract. Keep failing tests visible. Do not disable a test to prepare a release.

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

Assess pixel-difference semantics, alpha handling, image dimensions/overflow, marking/grouping correctness and exclusions with minimal synthetic cases. Measure region grouping and allocation/stack behavior before adopting performance changes. Define resource limits for untrusted images. Review later upstream fixes independently, including empty rectangle initialization. Do not promise benchmark improvements or hostile-input guarantees from this baseline.
