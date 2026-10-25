# Kotlin image assertions

The optional `dev.nuaforma:image-comparison-kotlin` artifact is intended for a consumer's test dependencies. Declare **only this artifact**: the Java core, Kotlin standard library and required JetBrains annotations arrive transitively. It works with any JVM test framework that recognizes `AssertionError`; JUnit, Kotest, AssertJ, kotlin-test and kotlin-reflect are not runtime dependencies.

No NuaForma artifact is published yet. The first release is planned as `5.0.0`; development uses `5.0.0-SNAPSHOT`. No package is published by choosing the version. Java 17 is the minimum runtime/API/bytecode target; this version builds with Kotlin 2.4.21 and is qualified with that Kotlin compiler and JDK 17/25.

```kotlin
dependencies {
    testImplementation("dev.nuaforma:image-comparison-kotlin:5.0.0-SNAPSHOT")
}
```

Maven consumers likewise need just this dependency with `<scope>test</scope>`. The independent consumer verifies both Gradle module metadata and Maven-POM-only resolution, without a source-project dependency or a separate core declaration.

## Assertions and diagnostics

```kotlin
import dev.nuaforma.image.comparison.kotlin.assertImagesMatch

val result = assertImagesMatch(expected, actual, "invoice preview") {
    pixelTolerance = 0.1
    allowedDifferentPixelsPercent = 0.0
    threshold = 5
    excludeRectangle(10, 20, 100, 200)
}
```

`expected` and `actual` are `BufferedImage` values. The example rectangle must fit inside the expected image; all coordinates are inclusive. Invalid, reversed, negative or out-of-bounds rectangles throw `IllegalArgumentException`. Tolerance must be finite in `[0,1)`; allowed percentage must be finite in `[0,100]`; threshold must be positive. These checks are deliberately stricter than silently ignored invalid Java setters.

An assertion returns the Java `ImageComparisonResult` for `MATCH`. For `MISMATCH` or `SIZE_MISMATCH`, it throws `ImageComparisonAssertionError`, an `AssertionError` whose `comparisonResult` exposes the original result and difference image. Its locale-independent message includes state, image dimensions, differing-pixel percentage and region count. It does not include pixel data or write files. Save diagnostics only when the caller explicitly chooses to do so:

```kotlin
import dev.nuaforma.image.comparison.kotlin.ImageComparisonAssertionError
import java.io.File

try {
    assertImagesMatch(expected, actual)
} catch (failure: ImageComparisonAssertionError) {
    failure.comparisonResult.writeResultTo(File("build/image-diagnostics/difference.png"))
    throw failure
}
```

For inspection without an assertion, use `compareImages(expected, actual) { ... }` with the same configuration. It returns the engine result for all states. `SIZE_MISMATCH` is never accepted by the assertion, even with an allowed percentage of 100.

## Engine policies and ownership

Each call creates a fresh configuration and Java engine. Options and exclusions do not leak between calls. Input images and the returned Java result remain caller-owned and mutable; the error does not create a frozen snapshot. Rectangle collections are never null: MATCH and SIZE_MISMATCH have empty lists. Default lists are mutable and independent; assigning a non-null list preserves its existing aliasing/mutability, and assigning null resets to a fresh empty list. Owned drawing contexts are disposed after drawing without invalidating the result image. Do not mutate shared images concurrently with comparison.

The adapter follows the [core comparison contract](../docs/comparison-contract.md). Both RGB and alpha are compared, including hidden RGB under alpha zero, with no implicit background compositing. RGB keeps its Euclidean normalization; alpha distance uses its own 0-to-255 scale. Either distance exceeding `pixelTolerance` marks the pixel as different.

`differencePercent` and `allowedDifferentPixelsPercent` use the same non-excluded pixel population. Excluded pixels count in neither numerator nor denominator; all excluded means zero. Accepted matches retain their measured percentage. An allowance of 25 accepts one differing pixel out of four; excluding two unchanged pixels changes that percentage to 50. Unequal dimensions always return `SIZE_MISMATCH` and 100%, irrespective of allowance, without resizing.

The DSL does not expose rectangle fills or drawing filters. Core drawing filters affect only the drawn output, not mismatch classification or returned detected regions. Grouping uses a heap worklist, avoiding shape-dependent stack overflow. Callers must still bound image dimensions and concurrent work; no hostile-image resource guarantee, PDF rendering or file decoding is added. Remaining fill/large-area limitations are tracked separately (#5/#10). Java image-loading helpers reject undecodable input at the read boundary and close owned classpath streams; the DSL still accepts BufferedImages directly and adds no file-loading API.

## Documentation and verification

Public declarations carry KDoc in the sources JAR. A compact HTML API reference ships in the documentation (`javadoc`) JAR without external scripts/fonts. The root build verifies all six binary/source/documentation archives and preserves project attribution.

See the root README and `docs/maintenance.md` for builds and dependency review. No release, Maven namespace claim or external publication is authorized by this implementation.
