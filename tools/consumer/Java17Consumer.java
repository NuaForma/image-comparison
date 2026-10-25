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
        ImageComparison comparison = new ImageComparison(expected, actual).setPixelToleranceLevel(0);
        ImageComparisonResult mismatch = comparison.compareImages();
        require(mismatch.getImageComparisonState() == ImageComparisonState.MISMATCH, "different pixel");
        require(mismatch.getResult() != null && !mismatch.getRectangles().isEmpty(), "difference marking");
        require(comparison.compareImages().getImageComparisonState() == ImageComparisonState.MISMATCH,
                "repeated comparison");
        require(comparison.createMask().size() == 1, "mask after comparisons");
        require(comparison.compareImages().getImageComparisonState() == ImageComparisonState.MISMATCH,
                "comparison after mask");
        Rectangle excluded = new Rectangle(4, 4, 6, 6);
        require(new ImageComparison(expected, actual).setPixelToleranceLevel(0)
                .setExcludedAreas(Collections.singletonList(excluded)).compareImages()
                .getImageComparisonState() == ImageComparisonState.MATCH, "excluded region");
        require(new ImageComparison(expected, new BufferedImage(11, 10, BufferedImage.TYPE_INT_RGB))
                .compareImages().getImageComparisonState() == ImageComparisonState.SIZE_MISMATCH, "size mismatch");
        BufferedImage originActual = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        originActual.setRGB(0, 0, Color.WHITE.getRGB());
        ImageComparisonResult origin = new ImageComparison(
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB), originActual)
                .setPixelToleranceLevel(0).compareImages();
        require(origin.getImageComparisonState() == ImageComparisonState.MISMATCH, "different origin pixel");
        require(origin.getRectangles().equals(Collections.singletonList(new Rectangle(0, 0, 0, 0))),
                "origin difference marking");
        BufferedImage alphaExpected = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        BufferedImage alphaActual = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        alphaActual.setRGB(1, 1, 0xff000000);
        ImageComparison alphaComparison = new ImageComparison(alphaExpected, alphaActual);
        require(alphaComparison.compareImages().getImageComparisonState() == ImageComparisonState.MISMATCH,
                "alpha participates at default tolerance");
        ImageComparisonResult accepted = alphaComparison.setAllowingPercentOfDifferentPixels(25).compareImages();
        require(accepted.getImageComparisonState() == ImageComparisonState.MATCH
                && accepted.getDifferencePercent() == 25f, "accepted percentage retained");
        ImageComparisonResult hidden = comparison.setMinimalRectangleSize(2).compareImages();
        require(hidden.getImageComparisonState() == ImageComparisonState.MISMATCH
                && hidden.getRectangles().size() == 1
                && hidden.getResult().getRGB(5, 5) == Color.WHITE.getRGB(), "drawing filter preserves detection");
        System.out.println("Independent Java 17 consumer passed on " + System.getProperty("java.version"));
    }

    private static void require(boolean condition, String scenario) {
        if (!condition) {
            throw new AssertionError(scenario);
        }
    }
}
