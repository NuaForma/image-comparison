# Kotlin image assertions

The optional `dev.nuaforma:image-comparison-kotlin` artifact is intended for a consumer's test dependencies. Declare **only this artifact**: the Java core, Kotlin standard library and required JetBrains annotations arrive transitively. It works with any JVM test framework that recognizes `AssertionError`; JUnit, Kotest, AssertJ, kotlin-test and kotlin-reflect are not runtime dependencies.

No NuaForma artifact is published yet. `4.4.1-SNAPSHOT` is a development placeholder, not a release promise. Java 17 is the minimum runtime/API/bytecode target; this version builds with Kotlin 2.4.21 and is qualified with that Kotlin compiler and JDK 17/25.

```kotlin
dependencies {
    testImplementation("dev.nuaforma:image-comparison-kotlin:4.4.1-SNAPSHOT")
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

An assertion returns the Java `ImageComparisonResult` for `MATCH`. For `MISMATCH` or `SIZE_MISMATCH`, it throws `ImageComparisonAssertionError`, an `AssertionError` whose `comparisonResult` exposes the original result and difference image. Its locale-independent message includes state, image dimensions, reported RGB difference and region count. It does not include pixel data or write files. Save diagnostics only when the caller explicitly chooses to do so:

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

Each call creates a fresh configuration and Java engine. Options and exclusions do not leak between calls. Input images and the returned Java result remain caller-owned and mutable under the core API's existing semantics; the error does not create a frozen snapshot. Do not mutate shared images concurrently with comparison.

This adapter delegates comparison rather than redefining it. The core's `allowedDifferentPixelsPercent` counts pixels, while `differencePercent` reports RGB channel distance and may include excluded pixels; they are not interchangeable measures. Alpha/tolerance behavior is inherited as well. Issues #6 and #7 track these policy decisions. The first DSL does not expose rectangle fill or minimum-size filters (issues #5/#8). It does not add resource limits for hostile images (#9/#10), decode input files, render PDFs or integrate with NuaForma.

## Documentation and verification

Public declarations carry KDoc in the sources JAR. A compact HTML API reference ships in the documentation (`javadoc`) JAR without external scripts/fonts. The root build verifies all six binary/source/documentation archives and preserves project attribution.

See the root README and `docs/maintenance.md` for builds and dependency review. No release, Maven namespace claim or external publication is authorized by this implementation.
