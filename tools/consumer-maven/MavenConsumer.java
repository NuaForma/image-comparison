import dev.nuaforma.image.comparison.kotlin.ImageAssertionsKt;
import dev.nuaforma.image.comparison.kotlin.ImageComparisonAssertionError;
import dev.nuaforma.image.comparison.model.ImageComparisonState;
import java.awt.image.BufferedImage;
import kotlin.Unit;

/** Maven declares only the Kotlin adapter; Java core and Kotlin runtime must resolve transitively. */
public final class MavenConsumer {
    public static void main(String[] args) {
        var expected = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        var actual = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        var match = ImageAssertionsKt.assertImagesMatch(expected, actual, "Maven consumer", options -> Unit.INSTANCE);
        if (match.getImageComparisonState() != ImageComparisonState.MATCH || !match.getRectangles().isEmpty()) throw new AssertionError("Invalid match");
        actual.setRGB(1, 1, 0xff000000);
        try {
            ImageAssertionsKt.assertImagesMatch(expected, actual, "Maven consumer", options -> Unit.INSTANCE);
            throw new AssertionError("Alpha difference must fail");
        } catch (ImageComparisonAssertionError failure) {
            if (failure.getComparisonResult().getDifferencePercent() != 25f) throw new AssertionError("Wrong percentage");
        }
        System.out.println("Independent Maven consumer passed");
    }
}
