import dev.nuaforma.image.comparison.ImageComparison;
import dev.nuaforma.image.comparison.model.ImageComparisonResult;
import dev.nuaforma.image.comparison.model.ImageComparisonState;
import dev.nuaforma.image.comparison.model.Rectangle;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Collections;

/** Independent consumer compiled against Java 17 APIs, with only the packaged library on its classpath. */
public final class Java17Consumer {
    public static void main(String[] args) {
        BufferedImage expected = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        BufferedImage actual = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        require(new ImageComparison(expected, actual).compareImages().getImageComparisonState()
                == ImageComparisonState.MATCH, "identical images");
        actual.setRGB(5, 5, Color.WHITE.getRGB());
        ImageComparisonResult mismatch = new ImageComparison(expected, actual)
                .setPixelToleranceLevel(0).compareImages();
        require(mismatch.getImageComparisonState() == ImageComparisonState.MISMATCH, "different pixel");
        require(mismatch.getResult() != null && !mismatch.getRectangles().isEmpty(), "difference marking");
        Rectangle excluded = new Rectangle(4, 4, 6, 6);
        require(new ImageComparison(expected, actual).setPixelToleranceLevel(0)
                .setExcludedAreas(Collections.singletonList(excluded)).compareImages()
                .getImageComparisonState() == ImageComparisonState.MATCH, "excluded region");
        require(new ImageComparison(expected, new BufferedImage(11, 10, BufferedImage.TYPE_INT_RGB))
                .compareImages().getImageComparisonState() == ImageComparisonState.SIZE_MISMATCH, "size mismatch");
        System.out.println("Independent Java 17 consumer passed on " + System.getProperty("java.version"));
    }

    private static void require(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }
}
