package dev.nuaforma.image.comparison.kotlin

import dev.nuaforma.image.comparison.ImageComparison
import dev.nuaforma.image.comparison.model.ImageComparisonState
import dev.nuaforma.image.comparison.model.Rectangle
import java.awt.Color
import java.awt.image.BufferedImage
import java.util.Locale
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ImageAssertionsTest {
    private fun image(width: Int = 10, height: Int = 10): BufferedImage =
        BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)

    private fun changed(x: Int = 5, y: Int = 5): BufferedImage = image().also {
        it.setRGB(x, y, Color.WHITE.rgb)
    }

    @Test
    fun matchingAssertionReturnsTheEngineResult() {
        val expected = image()
        val actual = image()
        val result = assertImagesMatch(expected, actual)
        assertEquals(ImageComparisonState.MATCH, result.imageComparisonState)
        assertSame(expected, result.expected)
        assertSame(actual, result.actual)
    }

    @Test
    fun mismatchThrowsAnInspectableFrameworkIndependentAssertion() {
        val error = assertThrows(ImageComparisonAssertionError::class.java) {
            assertImagesMatch(image(), changed(), "rendered page") { pixelTolerance = 0.0 }
        }
        assertInstanceOf(AssertionError::class.java, error)
        assertEquals(ImageComparisonState.MISMATCH, error.comparisonResult.imageComparisonState)
        assertEquals(listOf(Rectangle(5, 5, 5, 5)), error.comparisonResult.rectangles)
        assertEquals(Color.RED.rgb, error.comparisonResult.result.getRGB(5, 5))
        assertTrue(error.message!!.startsWith("rendered page: MISMATCH: expected 10x10, actual 10x10"))
    }

    @Test
    fun originAndRepeatedCallsRemainMismatches() {
        repeat(3) {
            val result = compareImages(image(), changed(0, 0)) { pixelTolerance = 0.0 }
            assertEquals(ImageComparisonState.MISMATCH, result.imageComparisonState)
            assertEquals(listOf(Rectangle(0, 0, 0, 0)), result.rectangles)
            assertThrows(AssertionError::class.java) { assertImagesMatch(image(), changed(0, 0)) }
        }
    }

    @Test
    fun excludedRectangleCanProduceAMatchWithoutLeakingToTheNextCall() {
        val actual = changed()
        assertEquals(ImageComparisonState.MATCH, assertImagesMatch(image(), actual) {
            pixelTolerance = 0.0
            excludeRectangle(5, 5, 5, 5)
        }.imageComparisonState)
        assertThrows(AssertionError::class.java) { assertImagesMatch(image(), actual) }
    }

    @Test
    fun allowedPixelPercentageRetainsExactBoundarySemantics() {
        assertEquals(ImageComparisonState.MATCH, assertImagesMatch(image(), changed()) {
            pixelTolerance = 0.0
            allowedDifferentPixelsPercent = 1.0
        }.imageComparisonState)
        assertThrows(AssertionError::class.java) {
            assertImagesMatch(image(), changed()) { allowedDifferentPixelsPercent = 0.99 }
        }
    }

    @Test
    fun pixelToleranceIsAppliedByTheJavaEngine() {
        val actual = image().also { it.setRGB(5, 5, 0xff202020.toInt()) }
        assertThrows(AssertionError::class.java) { assertImagesMatch(image(), actual) { pixelTolerance = 0.0 } }
        assertEquals(ImageComparisonState.MATCH, assertImagesMatch(image(), actual) {
            pixelTolerance = 0.5
        }.imageComparisonState)
    }

    @Test
    fun sizeMismatchAlwaysFailsAnAssertionAndPreservesDiagnostics() {
        val error = assertThrows(ImageComparisonAssertionError::class.java) {
            assertImagesMatch(image(), image(11, 10)) { allowedDifferentPixelsPercent = 100.0 }
        }
        assertEquals(ImageComparisonState.SIZE_MISMATCH, error.comparisonResult.imageComparisonState)
        assertEquals(100.0f, error.comparisonResult.differencePercent)
        assertTrue(error.message!!.contains("expected 10x10, actual 11x10"))
        assertTrue(error.message!!.endsWith("regions 0"))
        assertEquals(ImageComparisonState.SIZE_MISMATCH, compareImages(image(), image(11, 10)).imageComparisonState)
    }

    @Test
    fun diagnosticsAreIndependentOfTheDefaultLocale() {
        val original = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("nl-NL"))
            val error = assertThrows(ImageComparisonAssertionError::class.java) {
                assertImagesMatch(image(), changed())
            }
            assertTrue(error.message!!.contains("different pixels 1.0000%"))
        } finally {
            Locale.setDefault(original)
        }
    }

    @Test
    fun alphaOnlyDifferencesFailWithAnInspectablePixelPercentage() {
        val expected = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
        val actual = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(1, 1, 0xff000000.toInt()) }
        val error = assertThrows(ImageComparisonAssertionError::class.java) { assertImagesMatch(expected, actual) }
        assertEquals(25.0f, error.comparisonResult.differencePercent)
        assertTrue(error.message!!.contains("different pixels 25.0000%"))
        val accepted = assertImagesMatch(expected, actual) { allowedDifferentPixelsPercent = 25.0 }
        assertEquals(25.0f, accepted.differencePercent)
    }

    @Test
    fun exclusionDenominatorAndMeasuredAcceptedPercentageMatchTheCore() {
        val expected = image(4, 1)
        val actual = image(4, 1).also { it.setRGB(3, 0, Color.WHITE.rgb) }
        assertThrows(ImageComparisonAssertionError::class.java) {
            assertImagesMatch(expected, actual) {
                excludeRectangle(0, 0, 1, 0)
                allowedDifferentPixelsPercent = 25.0
            }
        }
        val accepted = assertImagesMatch(expected, actual) {
            excludeRectangle(0, 0, 1, 0)
            allowedDifferentPixelsPercent = 50.0
        }
        assertEquals(50.0f, accepted.differencePercent)
    }

    @Test
    fun longRegionsReturnAssertionFailuresRatherThanStackOverflow() {
        val expected = image(65536, 1)
        val actual = image(65536, 1).also { image ->
            for (x in 0 until image.width) image.setRGB(x, 0, Color.WHITE.rgb)
        }
        val error = assertThrows(ImageComparisonAssertionError::class.java) { assertImagesMatch(expected, actual) }
        assertEquals(100.0f, error.comparisonResult.differencePercent)
        assertEquals(listOf(Rectangle(0, 0, 65535, 0)), error.comparisonResult.rectangles)
    }

    @Test
    fun invalidToleranceIsRejectedInsteadOfSilentlyIgnored() {
        for (value in listOf(-0.1, 1.0, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                compareImages(image(), image()) { pixelTolerance = value }
            }
        }
    }

    @Test
    fun invalidAllowedPercentageIsRejected() {
        for (value in listOf(-0.1, 100.1, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertThrows(IllegalArgumentException::class.java) {
                compareImages(image(), image()) { allowedDifferentPixelsPercent = value }
            }
        }
    }

    @Test
    fun groupingThresholdMustBePositive() {
        for (value in listOf(0, -1)) {
            assertThrows(IllegalArgumentException::class.java) {
                compareImages(image(), image()) { threshold = value }
            }
        }
    }

    @Test
    fun excludedCoordinatesMustBeOrderedNonnegativeAndInsideExpectedImage() {
        for (coordinates in listOf(intArrayOf(-1, 0, 1, 1), intArrayOf(0, -1, 1, 1),
            intArrayOf(2, 0, 1, 1), intArrayOf(0, 2, 1, 1), intArrayOf(0, 0, 10, 9), intArrayOf(0, 0, 9, 10))) {
            assertThrows(IllegalArgumentException::class.java) {
                compareImages(image(), image()) {
                    excludeRectangle(coordinates[0], coordinates[1], coordinates[2], coordinates[3])
                }
            }
        }
        assertEquals(ImageComparisonState.MATCH, assertImagesMatch(image(), changed(0, 0)) {
            excludeRectangle(0, 0, 9, 9)
        }.imageComparisonState)
    }

    @Test
    fun configurationExceptionsPropagateWithoutBeingConvertedToAssertions() {
        val exception = IllegalStateException("configuration failure")
        assertSame(exception, assertThrows(IllegalStateException::class.java) {
            assertImagesMatch(image(), image()) { throw exception }
        })
    }

    @Test
    fun resultAndExactOutputMatchDirectJavaConfiguration() {
        val expected = image()
        val actual = changed().also { it.setRGB(8, 8, Color.WHITE.rgb) }
        val direct = ImageComparison(expected, actual).setPixelToleranceLevel(0.0)
            .setThreshold(1).setAllowingPercentOfDifferentPixels(0.0)
            .setExcludedAreas(listOf(Rectangle(8, 8, 8, 8))).compareImages()
        val dsl = compareImages(expected, actual) {
            pixelTolerance = 0.0
            threshold = 1
            allowedDifferentPixelsPercent = 0.0
            excludeRectangle(8, 8, 8, 8)
        }
        assertEquals(direct.imageComparisonState, dsl.imageComparisonState)
        assertEquals(direct.rectangles, dsl.rectangles)
        assertEquals(direct.differencePercent, dsl.differencePercent)
        for (y in 0 until 10) for (x in 0 until 10) {
            assertEquals(direct.result.getRGB(x, y), dsl.result.getRGB(x, y))
            assertEquals(Color.BLACK.rgb, expected.getRGB(x, y))
        }
        assertEquals(Color.WHITE.rgb, actual.getRGB(5, 5))
    }
}
