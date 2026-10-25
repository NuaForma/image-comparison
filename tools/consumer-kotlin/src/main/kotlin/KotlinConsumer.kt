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
    println("Independent Kotlin consumer passed on " + System.getProperty("java.version"))
}
