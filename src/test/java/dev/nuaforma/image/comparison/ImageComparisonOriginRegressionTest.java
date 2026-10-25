package dev.nuaforma.image.comparison;

import dev.nuaforma.image.comparison.model.ImageComparisonResult;
import dev.nuaforma.image.comparison.model.Rectangle;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static dev.nuaforma.image.comparison.model.ImageComparisonState.MATCH;
import static dev.nuaforma.image.comparison.model.ImageComparisonState.MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageComparisonOriginRegressionTest {
    @Test
    void detectsDifferentSinglePixelImage() {
        assertSinglePixelDifference(1, 1, 0, 0);
    }

    @Test
    void detectsEachCornerEdgeAndInteriorPixel() {
        int[][] points = {{0, 0}, {11, 0}, {0, 11}, {11, 11},
                {0, 6}, {6, 0}, {11, 6}, {6, 11}, {6, 6}};
        for (int[] point : points) {
            assertSinglePixelDifference(12, 12, point[0], point[1]);
        }
    }

    @Test
    void retainsOriginAndSeparateInteriorRegion() {
        BufferedImage expected = image(12, 12);
        BufferedImage actual = image(12, 12);
        actual.setRGB(0, 0, Color.WHITE.getRGB());
        actual.setRGB(10, 10, Color.WHITE.getRGB());

        ImageComparisonResult result = comparison(expected, actual).compareImages();

        assertEquals(MISMATCH, result.getImageComparisonState());
        assertEquals(List.of(new Rectangle(0, 0, 0, 0), new Rectangle(10, 10, 10, 10)),
                result.getRectangles());
    }

    @Test
    void mergesOverlappingBoundsWithoutDroppingTheOrigin() {
        BufferedImage expected = image(12, 12);
        BufferedImage actual = image(12, 12);
        int[][] pixels = {{0, 0}, {2, 2}, {3, 2}, {4, 2}, {4, 3}, {4, 4}, {2, 4}};
        for (int[] pixel : pixels) {
            actual.setRGB(pixel[0], pixel[1], Color.WHITE.getRGB());
        }

        ImageComparisonResult result = comparison(expected, actual).compareImages();

        assertEquals(MISMATCH, result.getImageComparisonState());
        assertEquals(List.of(new Rectangle(0, 0, 0, 0), new Rectangle(2, 2, 4, 4)),
                result.getRectangles());
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                boolean origin = x == 0 && y == 0;
                boolean border = (x >= 2 && x <= 4 && (y == 2 || y == 4))
                        || (y >= 2 && y <= 4 && (x == 2 || x == 4));
                assertEquals(origin || border ? Color.RED.getRGB() : actual.getRGB(x, y),
                        result.getResult().getRGB(x, y), "output pixel " + x + "," + y);
            }
        }
    }

    @Test
    void identicalSinglePixelAndLargerImagesStillMatch() {
        for (int size : new int[]{1, 12}) {
            ImageComparison comparison = comparison(image(size, size), image(size, size));
            assertEquals(MATCH, comparison.compareImages().getImageComparisonState());
            assertTrue(comparison.createMask().isEmpty());
        }
    }

    private void assertSinglePixelDifference(int width, int height, int px, int py) {
        BufferedImage expected = image(width, height);
        BufferedImage actual = image(width, height);
        actual.setRGB(px, py, Color.WHITE.getRGB());

        ImageComparisonResult result = comparison(expected, actual).compareImages();

        assertEquals(MISMATCH, result.getImageComparisonState(), "difference at " + px + "," + py);
        assertEquals(List.of(new Rectangle(px, py, px, py)), result.getRectangles());
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                boolean changed = x == px && y == py;
                assertEquals(changed ? Color.RED.getRGB() : 0, result.getResult().getRGB(x, y));
                assertEquals(0, expected.getRGB(x, y), "expected input remains unchanged");
                assertEquals(changed ? Color.WHITE.getRGB() : 0, actual.getRGB(x, y),
                        "actual input remains unchanged");
            }
        }
    }

    private BufferedImage image(int width, int height) {
        return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    private ImageComparison comparison(BufferedImage expected, BufferedImage actual) {
        return new ImageComparison(expected, actual).setPixelToleranceLevel(0).setThreshold(1);
    }
}
