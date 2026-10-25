package dev.nuaforma.image.comparison;

import dev.nuaforma.image.comparison.model.ImageComparisonResult;
import dev.nuaforma.image.comparison.model.Rectangle;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static dev.nuaforma.image.comparison.model.ImageComparisonState.*;
import static org.junit.jupiter.api.Assertions.*;

class ImageComparisonContractRegressionTest {
    private BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private ImageComparison exact(BufferedImage expected, BufferedImage actual) {
        return new ImageComparison(expected, actual).setPixelToleranceLevel(0).setThreshold(1);
    }

    @Test void countsChangedPixelsRatherThanChannelIntensity() {
        var expected = image(2, 2);
        var actual = image(2, 2);
        actual.setRGB(0, 0, 0x00010000);
        assertEquals(25f, exact(expected, actual).compareImages().getDifferencePercent());
        assertEquals(25f, ImageComparisonUtil.getDifferencePercent(expected, actual));
    }

    @Test void appliesToleranceBeforeCountingPixels() {
        var expected = image(2, 2);
        var actual = image(2, 2);
        actual.setRGB(0, 0, 0x00010101);
        actual.setRGB(1, 1, 0x00ffffff);
        var result = new ImageComparison(expected, actual).compareImages();
        assertEquals(MISMATCH, result.getImageComparisonState());
        assertEquals(25f, result.getDifferencePercent());
        assertEquals(50f, ImageComparisonUtil.getDifferencePercent(expected, actual));
    }

    @Test void removesExcludedPixelsFromNumeratorAndDenominator() {
        var expected = image(12, 12);
        var actual = image(12, 12);
        actual.setRGB(2, 2, Color.WHITE.getRGB());
        actual.setRGB(9, 9, Color.WHITE.getRGB());
        var result = exact(expected, actual)
                .setExcludedAreas(List.of(new Rectangle(2, 2, 2, 2))).compareImages();
        assertEquals(MISMATCH, result.getImageComparisonState());
        assertEquals((float)(100.0 / 143), result.getDifferencePercent());
        assertEquals(List.of(new Rectangle(9, 9, 9, 9)), result.getRectangles());
    }

    @Test void overlappingExclusionsCountTheirUnionOnlyOnce() {
        var expected = image(4, 1);
        var actual = image(4, 1);
        for (int x = 0; x < 4; x++) actual.setRGB(x, 0, Color.WHITE.getRGB());
        var comparison = exact(expected, actual).setExcludedAreas(List.of(
                new Rectangle(0, 0, 1, 0), new Rectangle(1, 0, 2, 0)));
        assertEquals(100f, comparison.compareImages().getDifferencePercent());
        assertEquals(MISMATCH, comparison.setAllowingPercentOfDifferentPixels(99).compareImages().getImageComparisonState());
    }

    @Test void allExcludedPixelsGiveZeroPercentAndMatch() {
        var expected = image(2, 2);
        var actual = image(2, 2);
        actual.setRGB(0, 0, Color.WHITE.getRGB());
        var result = exact(expected, actual).setExcludedAreas(List.of(new Rectangle(0, 0, 1, 1))).compareImages();
        assertEquals(MATCH, result.getImageComparisonState());
        assertEquals(0f, result.getDifferencePercent());
    }

    @Test void allowedPercentageIsInclusiveAndRetainsMeasuredPercentage() {
        var expected = image(4, 1);
        var actual = image(4, 1);
        actual.setRGB(3, 0, Color.WHITE.getRGB());
        var comparison = exact(expected, actual).setAllowingPercentOfDifferentPixels(25);
        var accepted = comparison.compareImages();
        assertEquals(MATCH, accepted.getImageComparisonState());
        assertEquals(25f, accepted.getDifferencePercent());
        assertTrue(comparison.createMask().isEmpty());
        assertEquals(MISMATCH, comparison.setAllowingPercentOfDifferentPixels(Math.nextDown(25.0)).compareImages().getImageComparisonState());
    }

    @Test void exclusionsAffectAllowedPercentageToo() {
        var expected = image(4, 1);
        var actual = image(4, 1);
        actual.setRGB(3, 0, Color.WHITE.getRGB());
        var comparison = exact(expected, actual).setExcludedAreas(List.of(new Rectangle(0, 0, 1, 0)))
                .setAllowingPercentOfDifferentPixels(25);
        assertEquals(MISMATCH, comparison.compareImages().getImageComparisonState());
        var accepted = comparison.setAllowingPercentOfDifferentPixels(50).compareImages();
        assertEquals(MATCH, accepted.getImageComparisonState());
        assertEquals(50f, accepted.getDifferencePercent());
    }

    @Test void sizeMismatchIsAlwaysOneHundredPercentWithoutResizing() {
        var expected = image(2, 2);
        var actual = image(3, 2);
        var result = exact(expected, actual).setAllowingPercentOfDifferentPixels(100)
                .setExcludedAreas(List.of(new Rectangle(0, 0, 1, 1))).compareImages();
        assertEquals(SIZE_MISMATCH, result.getImageComparisonState());
        assertEquals(100f, result.getDifferencePercent());
        assertSame(actual, result.getResult());
        assertEquals(100f, ImageComparisonUtil.getDifferencePercent(expected, actual));
        assertEquals(100f, ImageComparisonUtil.getDifferencePercent(actual, expected));
    }

    @Test void transparentAndOpaqueBlackDifferAtDefaultAndZeroTolerance() {
        var expected = image(2, 2);
        var actual = image(2, 2);
        actual.setRGB(1, 1, 0xff000000);
        for (double tolerance : new double[]{0, 0.1, 0.99}) {
            var result = new ImageComparison(expected, actual).setPixelToleranceLevel(tolerance).compareImages();
            assertEquals(MISMATCH, result.getImageComparisonState());
            assertEquals(25f, result.getDifferencePercent());
        }
    }

    @Test void alphaToleranceBoundaryIsInclusiveAndIndependentOfRgbNormalization() {
        var expected = image(1, 1);
        var actual = image(1, 1);
        actual.setRGB(0, 0, 0x33000000); // 51/255 = 0.2
        assertEquals(MATCH, new ImageComparison(expected, actual).setPixelToleranceLevel(0.2)
                .compareImages().getImageComparisonState());
        actual.setRGB(0, 0, 0x34000000);
        assertEquals(MISMATCH, new ImageComparison(expected, actual).setPixelToleranceLevel(0.2)
                .compareImages().getImageComparisonState());
    }

    @Test void rgbToleranceKeepsItsExistingBoundary() {
        var expected = image(1, 1);
        var actual = image(1, 1);
        expected.setRGB(0, 0, 0xff000000);
        actual.setRGB(0, 0, 0xff333333);
        assertEquals(MATCH, new ImageComparison(expected, actual).setPixelToleranceLevel(0.2).compareImages().getImageComparisonState());
        actual.setRGB(0, 0, 0xff343434);
        assertEquals(MISMATCH, new ImageComparison(expected, actual).setPixelToleranceLevel(0.2).compareImages().getImageComparisonState());
    }

    @Test void hiddenRgbIsComparedWithoutImplicitBackgroundCompositing() {
        var expected = image(1, 1);
        var actual = image(1, 1);
        actual.setRGB(0, 0, 0x00ff0000);
        assertEquals(MISMATCH, new ImageComparison(expected, actual).compareImages().getImageComparisonState());
        assertEquals(100f, ImageComparisonUtil.getDifferencePercent(expected, actual));
    }

    @Test void premultipliedAndStraightImagesUseGetRgbValues() {
        var straight = image(2, 2);
        var premultiplied = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB_PRE);
        straight.setRGB(1, 1, 0x80ff0000);
        premultiplied.setRGB(1, 1, 0x80ff0000);
        assertEquals(MATCH, exact(straight, premultiplied).compareImages().getImageComparisonState());
        premultiplied.setRGB(1, 1, 0x40ff0000);
        assertEquals(MISMATCH, new ImageComparison(straight, premultiplied).compareImages().getImageComparisonState());
    }

    @Test void alphaDifferencesRespectExclusionsAndAllowedPercentage() {
        var expected = image(4, 1);
        var actual = image(4, 1);
        actual.setRGB(0, 0, 0xff000000);
        actual.setRGB(3, 0, 0xff000000);
        var comparison = new ImageComparison(expected, actual).setExcludedAreas(List.of(new Rectangle(0, 0, 1, 0)));
        assertEquals(50f, comparison.compareImages().getDifferencePercent());
        assertEquals(MATCH, comparison.setAllowingPercentOfDifferentPixels(50).compareImages().getImageComparisonState());
    }

    @Test void minimumSizeFiltersOnlyDrawingAndKeepsDetectedRegions() {
        var expected = image(3, 3);
        var actual = image(3, 3);
        actual.setRGB(1, 1, Color.WHITE.getRGB());
        var comparison = exact(expected, actual).setMinimalRectangleSize(2);
        var result = comparison.compareImages();
        assertEquals(MISMATCH, result.getImageComparisonState());
        assertEquals(List.of(new Rectangle(1, 1, 1, 1)), result.getRectangles());
        assertEquals(result.getRectangles(), comparison.createMask());
        assertEquals((float)(100.0/9), result.getDifferencePercent());
        assertImagesEqual(actual, result.getResult());
        assertEquals(0, expected.getRGB(1, 1));
    }

    @Test void drawingFiltersRespectAreaBoundaryAndMaximumCountWithoutChangingMetrics() {
        var expected = image(12, 3);
        var actual = image(12, 3);
        actual.setRGB(1, 1, Color.WHITE.getRGB());
        actual.setRGB(8, 1, Color.WHITE.getRGB());
        actual.setRGB(9, 1, Color.WHITE.getRGB());
        var comparison = exact(expected, actual);
        var base = comparison.compareImages();
        for (int min : new int[]{1, 2, 3}) {
            var result = comparison.setMinimalRectangleSize(min).setMaximalRectangleCount(1).compareImages();
            assertEquals(MISMATCH, result.getImageComparisonState());
            assertEquals(base.getRectangles(), result.getRectangles());
            assertEquals(base.getDifferencePercent(), result.getDifferencePercent());
            assertEquals(Color.WHITE.getRGB(), result.getResult().getRGB(1, 1));
            assertEquals(min <= 2 ? Color.RED.getRGB() : Color.WHITE.getRGB(), result.getResult().getRGB(8, 1));
        }
        assertEquals(MATCH, comparison.setAllowingPercentOfDifferentPixels(100).compareImages().getImageComparisonState());
    }

    @Test void maskCreationRejectsUnequalDimensionsExplicitly() {
        assertThrows(IllegalArgumentException.class, () -> exact(image(2, 2), image(3, 2)).createMask());
        assertThrows(IllegalArgumentException.class, () -> exact(image(3, 2), image(2, 2)).createMask());
    }

    @Test void identicalImagesRetainZeroPercentUnderDrawingOptions() {
        var result = exact(image(2, 2), image(2, 2)).setMinimalRectangleSize(100)
                .setMaximalRectangleCount(1).compareImages();
        assertEquals(MATCH, result.getImageComparisonState());
        assertEquals(0f, result.getDifferencePercent());
    }

    private static void assertImagesEqual(BufferedImage expected, BufferedImage actual) {
        for (int y = 0; y < expected.getHeight(); y++) for (int x = 0; x < expected.getWidth(); x++) {
            assertEquals(expected.getRGB(x, y), actual.getRGB(x, y), "pixel " + x + "," + y);
        }
    }
}
