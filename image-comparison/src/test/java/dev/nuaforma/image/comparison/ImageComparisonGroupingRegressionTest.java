package dev.nuaforma.image.comparison;

import dev.nuaforma.image.comparison.model.Rectangle;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static dev.nuaforma.image.comparison.model.ImageComparisonState.MISMATCH;
import static org.junit.jupiter.api.Assertions.*;

class ImageComparisonGroupingRegressionTest {
    @Test void longHorizontalAndVerticalRegionsDoNotDependOnCallStackDepth() {
        for (int[] dimensions : new int[][]{{65536, 1}, {1, 65536}}) {
            for (int threshold : new int[]{1, 5}) {
                assertDenseRegion(dimensions[0], dimensions[1], threshold);
            }
        }
    }

    @Test void denseBlocksRetainExactBoundsAndMarking() {
        assertDenseRegion(256, 256, 1);
        assertDenseRegion(256, 256, 5);
    }

    @Test void diagonalCheckerboardRemainsAConnectedRegion() {
        var expected = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        var actual = new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            if ((x + y) % 2 == 0) actual.setRGB(x, y, Color.WHITE.getRGB());
        }
        var result = new ImageComparison(expected, actual).setThreshold(1).compareImages();
        assertEquals(MISMATCH, result.getImageComparisonState());
        assertEquals(List.of(new Rectangle(0, 0, 15, 15)), result.getRectangles());
        assertEquals(50f, result.getDifferencePercent());
        for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) {
            boolean border = x == 0 || x == 15 || y == 0 || y == 15;
            assertEquals(border ? Color.RED.getRGB() : actual.getRGB(x, y), result.getResult().getRGB(x, y));
        }
    }

    @Test void horizontalVerticalAndDiagonalGapsRespectThresholdBoundaries() {
        for (int[] end : new int[][]{{4, 1}, {1, 4}, {4, 4}}) {
            var expected = new BufferedImage(6, 6, BufferedImage.TYPE_INT_RGB);
            var actual = new BufferedImage(6, 6, BufferedImage.TYPE_INT_RGB);
            actual.setRGB(1, 1, Color.WHITE.getRGB());
            actual.setRGB(end[0], end[1], Color.WHITE.getRGB());
            var comparison = new ImageComparison(expected, actual).setThreshold(2);
            assertEquals(List.of(new Rectangle(1, 1, 1, 1), new Rectangle(end[0], end[1], end[0], end[1])), comparison.createMask());
            assertEquals(List.of(new Rectangle(1, 1, end[0], end[1])), comparison.setThreshold(3).createMask());
        }
    }

    @Test void upwardDiagonalAndDisconnectedRegionsRetainScanOrder() {
        var expected = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        var actual = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        for (int[] p : new int[][]{{4, 1}, {3, 2}, {2, 3}, {1, 4}, {7, 7}}) actual.setRGB(p[0], p[1], Color.WHITE.getRGB());
        var comparison = new ImageComparison(expected, actual).setThreshold(1);
        assertEquals(List.of(new Rectangle(1, 1, 4, 4), new Rectangle(7, 7, 7, 7)), comparison.createMask());
        assertEquals(comparison.createMask(), comparison.compareImages().getRectangles());
    }

    @Test void oversizedThresholdIsClampedToReachableImageDistances() {
        assertDenseRegion(4, 4, Integer.MAX_VALUE);
    }

    private static void assertDenseRegion(int width, int height, int threshold) {
        var expected = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var actual = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) actual.setRGB(x, y, Color.WHITE.getRGB());
        var result = new ImageComparison(expected, actual).setThreshold(threshold).compareImages();
        assertEquals(MISMATCH, result.getImageComparisonState());
        assertEquals(List.of(new Rectangle(0, 0, width - 1, height - 1)), result.getRectangles());
        assertEquals(100f, result.getDifferencePercent());
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean border = x == 0 || y == 0 || x == width - 1 || y == height - 1;
            assertEquals(border ? Color.RED.getRGB() : Color.WHITE.getRGB(), result.getResult().getRGB(x, y));
            assertEquals(Color.BLACK.getRGB(), expected.getRGB(x, y));
            assertEquals(Color.WHITE.getRGB(), actual.getRGB(x, y));
        }
    }
}
