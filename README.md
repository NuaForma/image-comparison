# NuaForma Image Comparison

An independently maintained Apache-2.0 fork of [romankh3/image-comparison](https://github.com/romankh3/image-comparison), created from [v4.4.0](https://github.com/romankh3/image-comparison/tree/v4.4.0). Original author: Roman Beskrovnyi and upstream contributors. The original logo remains credited to [@dee-y](https://github.com/dee-y).

Compare two `BufferedImage` values, classify matches and size mismatches, mark differing regions and exclude configured rectangles. The Java core uses standard Java/AWT only, with **no external runtime dependencies**. This is a generic image comparison library; PDF rendering and product integration are outside its scope.

## Why this fork exists

We needed a dependable image-comparison library for our testing workflows and wanted to build on the useful work already available in this project. For our use, we also needed predictable maintenance, verified fixes and a release process we could manage ourselves.

When this fork was created on 25 October 2026, the latest published upstream release was [v4.4.0, dated 28 March 2021](https://github.com/romankh3/image-comparison/releases/tag/v4.4.0), and several [long-standing pull requests](https://github.com/romankh3/image-comparison/pulls) remained open. Given that release gap and the contributions still awaiting integration, we felt an independently maintained fork was the most practical way to meet our requirements and take responsibility for future fixes and releases.

We appreciate the original author's and contributors' work. Their authorship, Git history and Apache-2.0 licensing are retained, and our modifications are identified separately. NuaForma's maintenance and release decisions are independent of upstream.

## Status and compatibility

No NuaForma release has been published. This source prepares the first release candidate **5.0.0**, selected by the maintainer. Coordinates are `dev.nuaforma:image-comparison` and the optional `dev.nuaforma:image-comparison-kotlin`. Java baseline, namespace and approved behavior changes intentionally break compatibility with upstream v4.4.0. Choosing the version does not create a release or tag. Upstream tags identify upstream releases only.

Java 17 is the minimum supported runtime and API/bytecode target. Java 8 is unsupported. Production classes now use `dev.nuaforma.image.comparison` instead of `com.github.romankh3.image.comparison`. Method shapes are preserved; approved behavior corrections are recorded in CHANGELOG.md. The package relocation breaks source/binary compatibility and requires callers to update imports and recompile. The runtime baseline is also intentionally breaking. Build and tests require JDK 17 or later; CI checks JDK 17 and 25 and runs a standalone packaged consumer on both, including the Java 17 minimum for artifacts built on Java 25. Test libraries do not become runtime dependencies. Kotlin is optional and is not required by Java-only consumers. See [maintenance and compatibility](docs/maintenance.md) for exact boundaries and verification.

## Modules and Kotlin tests

- `image-comparison`: the existing Java library; no external runtime dependencies.
- `image-comparison-kotlin`: the optional [Kotlin assertion DSL](image-comparison-kotlin/README.md). A consumer declares **only this artifact**, and the core, Kotlin standard library and required annotations arrive transitively. It is test-framework independent.

```kotlin
import dev.nuaforma.image.comparison.kotlin.assertImagesMatch

assertImagesMatch(expected, actual, "preview") {
    pixelTolerance = 0.1
    excludeRectangle(10, 20, 100, 200)
}
```

For Kotlin users, the single dependency is:

```kotlin
dependencies {
    testImplementation("dev.nuaforma:image-comparison-kotlin:5.0.0")
}
```

This brings in `image-comparison`, Kotlin stdlib and required annotations transitively; no separate core declaration is needed. These coordinates are not published yet.

The rectangle is inclusive and must fit inside `expected`. Mismatches throw an inspectable `AssertionError`; the API never writes diagnostics automatically. No Kotlin artifact is published yet. See the module guide for allowed percentages, validation, result ownership and inherited core-policy limitations.

## Comparison behavior

The [comparison contract](docs/comparison-contract.md) defines tolerance, transparency, exclusions, percentages and drawing filters. RGB and alpha both participate in pixel comparison. Reported difference is the percentage of non-excluded pixels exceeding tolerance; accepted matches can retain a nonzero percentage. Unequal dimensions always mean `SIZE_MISMATCH` and 100%, without resizing. Minimum rectangle size and maximum rectangle count affect drawing only. Grouping uses a heap worklist rather than recursive calls. Result rectangle collections are always non-null. Image loading rejects invalid input immediately and releases its owned classpath streams; internal drawing contexts are disposed even when drawing fails.

## Build

Use the checked-in Gradle wrapper and select an installed JDK 17 or 25 with `JAVA_HOME`. Production Java and Kotlin target Java 17 APIs/bytecode independently of the JDK that runs the build/tests. No JDK is downloaded automatically. The first run requires access to Gradle distribution repositories, the official Gradle Plugin Portal and Maven Central.

```sh
./gradlew --no-daemon clean build verifyJavaConsumer verifyGroupingConsumer publishAllPublicationsToVerificationRepository
./gradlew --no-daemon -p tools/consumer-kotlin run
./gradlew --no-daemon -p tools/consumer-kotlin run -PpomOnly=true
```

The first command builds/tests both modules, checks artifact boundaries and runs Java consumers against only the packaged core, including grouping smoke checks with a 1 MiB stack and 128 MiB heap. It writes artifacts to the ignored local `build/verification-repository` solely for verification; no external repository or credentials are configured. The independent Kotlin project declares only the Kotlin artifact and checks its transitives twice: Gradle metadata and POM-only resolution. No source-project substitution is allowed.

Use `gradlew.bat` and the platform classpath separator where appropriate. Run archive reproducibility/Java-17 runtime checks as described in [maintenance](docs/maintenance.md). The approved Gradle migration replaces the Maven build; historical build configurations remain in Git history. No release, namespace claim or upload is performed by these commands.

## Example

```java
import dev.nuaforma.image.comparison.ImageComparison;
import dev.nuaforma.image.comparison.model.ImageComparisonResult;
import java.awt.image.BufferedImage;

BufferedImage expected = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
BufferedImage actual = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
ImageComparisonResult result = new ImageComparison(expected, actual)
        .setPixelToleranceLevel(0.1)
        .compareImages();
```

Configuration options, image examples and the original usage guide are available in the [upstream v4.4.0 README](https://github.com/romankh3/image-comparison/blob/v4.4.0/README.md). Its installation coordinates refer to upstream artifacts; replace its `com.github.romankh3.image.comparison` imports with `dev.nuaforma.image.comparison` when adapting examples. This fork does not include later upstream APIs such as `simpleComparison()`.

## Release preparation

The [Maven Central release guide](docs/maven-central-releases.md) describes the shared NuaForma approach: manual preparation on protected `main`, an immutable signed `v<version>` source tag and exact artifact review, then separately authorized upload and publication. Ordinary builds require no signing credentials. Disposable-key QA and independent Maven/Gradle consumers run locally without external distribution. No push, merge or tag automatically releases packages.

## Maintenance and contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), [maintenance and release process](docs/maintenance.md), [tooling and license review](docs/tooling-review.md), [SECURITY.md](SECURITY.md) and [CHANGELOG.md](CHANGELOG.md). Fork issues and PRs belong in [NuaForma/image-comparison](https://github.com/NuaForma/image-comparison). `develop` is the integration branch; `main` is the release baseline.

## License and attribution

[Apache License 2.0](LICENSE), preserved from upstream. See [NOTICE](NOTICE) and [MODIFICATIONS.md](MODIFICATIONS.md) for origin, authorship and the fork's modifications. Original source and fixture attribution are retained. The inherited [release notes](RELEASE_NOTES.md) describe upstream history. Community participation follows the [Code of Conduct](CODE_OF_CONDUCT.md).
