package dev.nuaforma.image.comparison.kotlin

import dev.nuaforma.image.comparison.ImageComparison
import dev.nuaforma.image.comparison.model.ImageComparisonResult
import dev.nuaforma.image.comparison.model.ImageComparisonState
import dev.nuaforma.image.comparison.model.Rectangle
import java.awt.image.BufferedImage
import java.util.Locale

/** Marks the receiver scope of image comparison configuration. */
@DslMarker
@Target(AnnotationTarget.CLASS, AnnotationTarget.TYPE)
public annotation class ImageComparisonDsl

/**
 * Validated configuration for a single comparison. Each DSL call creates a new configuration and engine.
 *
 * Rectangle coordinates are inclusive and relative to the expected image. Percentage counts pixels
 * exceeding RGB or alpha tolerance among non-excluded pixels. This builder does not expose drawing
 * filters or rectangle fills. Straight ARGB values, including hidden RGB at alpha zero, are compared.
 */
@ImageComparisonDsl
public class ImageComparisonOptions internal constructor() {
    /** RGB and alpha tolerance in [0,1); zero requests exact ARGB comparison, including hidden RGB. */
    public var pixelTolerance: Double = 0.1
        set(value) {
            require(value.isFinite() && value >= 0 && value < 1) { "pixelTolerance must be finite and in [0, 1)" }
            field = value
        }

    /** Inclusive percentage of non-excluded differing pixels the engine may accept, in [0,100]. */
    public var allowedDifferentPixelsPercent: Double = 0.0
        set(value) {
            require(value.isFinite() && value >= 0 && value <= 100) {
                "allowedDifferentPixelsPercent must be finite and in [0, 100]"
            }
            field = value
        }

    /** Positive pixel distance used by the engine to group differing regions; defaults to 5. */
    public var threshold: Int = 5
        set(value) {
            require(value > 0) { "threshold must be positive" }
            field = value
        }

    private val exclusions: MutableList<Rectangle> = mutableListOf()

    /**
     * Excludes an inclusive rectangle. All coordinates must be nonnegative and maxima must not precede minima.
     * Its maximum coordinates must also lie inside the expected image; that check occurs before comparison.
     *
     * @throws IllegalArgumentException if coordinates are invalid.
     */
    public fun excludeRectangle(minX: Int, minY: Int, maxX: Int, maxY: Int) {
        require(minX >= 0 && minY >= 0 && maxX >= minX && maxY >= minY) {
            "excludeRectangle requires nonnegative, ordered inclusive coordinates"
        }
        exclusions.add(Rectangle(minX, minY, maxX, maxY))
    }

    internal fun engine(expected: BufferedImage, actual: BufferedImage): ImageComparison {
        for (rectangle in exclusions) {
            require(rectangle.maxPoint.x < expected.width && rectangle.maxPoint.y < expected.height) {
                "excluded rectangles must lie inside the expected image (${expected.width}x${expected.height})"
            }
        }
        return ImageComparison(expected, actual)
            .setPixelToleranceLevel(pixelTolerance)
            .setAllowingPercentOfDifferentPixels(allowedDifferentPixelsPercent)
            .setThreshold(threshold)
            .setExcludedAreas(exclusions.map { Rectangle(it) })
    }
}

/**
 * Runs a fresh Java comparison engine and returns its unmodified result. No files are written.
 * Input images and result objects retain the Java engine's ownership and mutability semantics.
 *
 * @throws IllegalArgumentException if configuration is invalid, including out-of-bounds exclusions.
 */
public fun compareImages(
    expected: BufferedImage,
    actual: BufferedImage,
    configure: ImageComparisonOptions.() -> Unit = {},
): ImageComparisonResult = ImageComparisonOptions().apply(configure).engine(expected, actual).compareImages()

/**
 * Returns the engine result for MATCH, or throws [ImageComparisonAssertionError] for MISMATCH/SIZE_MISMATCH.
 * The error exposes the result image for explicit caller-controlled saving; assertions never write files.
 *
 * @param message optional context prepended to deterministic, locale-independent comparison diagnostics.
 * @throws IllegalArgumentException if configuration is invalid.
 * @throws ImageComparisonAssertionError if the images do not match under the configured engine policy.
 */
public fun assertImagesMatch(
    expected: BufferedImage,
    actual: BufferedImage,
    message: String? = null,
    configure: ImageComparisonOptions.() -> Unit = {},
): ImageComparisonResult {
    val result = compareImages(expected, actual, configure)
    if (result.imageComparisonState != ImageComparisonState.MATCH) {
        val diagnostic = "${result.imageComparisonState}: expected ${expected.width}x${expected.height}, " +
            "actual ${actual.width}x${actual.height}; different pixels " +
            String.format(Locale.ROOT, "%.4f%%", result.differencePercent) +
            "; regions ${result.rectangles.size}"
        val context = message?.takeIf { it.isNotEmpty() }?.let { "$it: " } ?: ""
        throw ImageComparisonAssertionError(result, context + diagnostic)
    }
    return result
}

/**
 * Framework-independent assertion failure, catchable as [AssertionError].
 *
 * [comparisonResult] is the actual, mutable Java result, not a serialized snapshot. Its result image
 * may be saved explicitly by the caller; the failure message itself contains no image contents.
 */
public class ImageComparisonAssertionError internal constructor(
    public val comparisonResult: ImageComparisonResult,
    message: String,
) : AssertionError(message)
