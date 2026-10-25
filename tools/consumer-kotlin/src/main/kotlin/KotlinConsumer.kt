import dev.nuaforma.image.comparison.kotlin.ImageComparisonAssertionError
import dev.nuaforma.image.comparison.kotlin.assertImagesMatch
import dev.nuaforma.image.comparison.kotlin.compareImages
import dev.nuaforma.image.comparison.model.ImageComparisonState
import java.awt.Color
import java.awt.image.BufferedImage

fun main() {
    val expected = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
    val actual = BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)
    check(assertImagesMatch(expected, actual).imageComparisonState == ImageComparisonState.MATCH)
    actual.setRGB(0, 0, Color.WHITE.rgb)
    repeat(3) {
        check(compareImages(expected, actual) { pixelTolerance = 0.0 }.imageComparisonState == ImageComparisonState.MISMATCH)
    }
    val failure = try {
        assertImagesMatch(expected, actual, "independent consumer") { pixelTolerance = 0.0 }
        error("Different origin pixel must fail")
    } catch (failure: ImageComparisonAssertionError) {
        failure
    }
    check(failure.comparisonResult.result.getRGB(0, 0) == Color.RED.rgb)
    check(assertImagesMatch(expected, actual) {
        pixelTolerance = 0.0
        excludeRectangle(0, 0, 0, 0)
    }.imageComparisonState == ImageComparisonState.MATCH)
    check(compareImages(expected, BufferedImage(11, 10, BufferedImage.TYPE_INT_RGB))
        .imageComparisonState == ImageComparisonState.SIZE_MISMATCH)
    val alphaExpected = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB)
    val alphaActual = BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB).also { it.setRGB(1, 1, 0xff000000.toInt()) }
    val alphaFailure = try {
        assertImagesMatch(alphaExpected, alphaActual)
        error("Alpha difference must fail")
    } catch (failure: ImageComparisonAssertionError) {
        failure
    }
    check(alphaFailure.comparisonResult.differencePercent == 25.0f)
    check(alphaFailure.message!!.contains("different pixels 25.0000%"))
    check(assertImagesMatch(alphaExpected, alphaActual) {
        allowedDifferentPixelsPercent = 25.0
    }.differencePercent == 25.0f)
    val masked = assertImagesMatch(expected, actual) {
        excludeRectangle(1, 0, 9, 9)
        allowedDifferentPixelsPercent = 10.0
    }
    check(masked.differencePercent == 10.0f)
    check(assertImagesMatch(expected, expected).rectangles.isEmpty())
    val sizeResult = compareImages(expected, BufferedImage(11, 10, BufferedImage.TYPE_INT_RGB))
    check(sizeResult.rectangles.isEmpty())
    sizeResult.setRectangles(null)
    check(sizeResult.rectangles.isEmpty())
    failure.comparisonResult.result.setRGB(1, 1, Color.BLUE.rgb)
    check(actual.getRGB(1, 1) == Color.BLACK.rgb)
    println("Independent Kotlin consumer passed on " + System.getProperty("java.version"))
}
