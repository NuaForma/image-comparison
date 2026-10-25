package dev.nuaforma.image.comparison;

import dev.nuaforma.image.comparison.model.ImageComparisonResult;
import dev.nuaforma.image.comparison.model.Rectangle;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

import static dev.nuaforma.image.comparison.model.ImageComparisonState.MATCH;
import static dev.nuaforma.image.comparison.model.ImageComparisonState.MISMATCH;
import static dev.nuaforma.image.comparison.model.ImageComparisonState.SIZE_MISMATCH;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImageComparisonReuseRegressionTest {
    @Test
    void repeatedComparisonsRetainStateRegionsPercentageAndPixels() {
        BufferedImage expected = image();
        BufferedImage actual = image();
        actual.setRGB(5, 5, Color.WHITE.getRGB());
        ImageComparison comparison = comparison(expected, actual);
        ImageComparisonResult first = comparison.compareImages();

        for (int run = 0; run < 3; run++) {
            ImageComparisonResult next = comparison.compareImages();
            assertEquals(MISMATCH, next.getImageComparisonState());
            assertEquals(List.of(new Rectangle(5, 5, 5, 5)), next.getRectangles());
            assertEquals(first.getDifferencePercent(), next.getDifferencePercent());
            for (int y = 0; y < 12; y++) {
                for (int x = 0; x < 12; x++) {
                    assertEquals(first.getResult().getRGB(x, y), next.getResult().getRGB(x, y));
                }
            }
        }
        assertEquals(Color.WHITE.getRGB(), actual.getRGB(5, 5));
        assertEquals(0, expected.getRGB(5, 5));
    }

    @Test
    void masksAndComparisonsCanBeInterleaved() {
        BufferedImage actual = image();
        actual.setRGB(5, 5, Color.WHITE.getRGB());
        ImageComparison comparison = comparison(image(), actual);
        List<Rectangle> mask = List.of(new Rectangle(5, 5, 5, 5));

        assertEquals(mask, comparison.createMask());
        assertEquals(mask, comparison.createMask());
        assertEquals(MISMATCH, comparison.compareImages().getImageComparisonState());
        assertEquals(mask, comparison.createMask());
        assertEquals(mask, comparison.compareImages().getRectangles());
    }

    @Test
    void sequentialConfigurationChangesRemainEffective() {
        BufferedImage actual = image();
        actual.setRGB(5, 5, 0x00202020); // Keep alpha equal to isolate RGB tolerance changes.
        ImageComparison comparison = comparison(image(), actual);

        assertEquals(MISMATCH, comparison.compareImages().getImageComparisonState());
        comparison.setExcludedAreas(List.of(new Rectangle(5, 5, 5, 5)));
        assertEquals(MATCH, comparison.compareImages().getImageComparisonState());
        comparison.setExcludedAreas(List.of());
        assertEquals(MISMATCH, comparison.compareImages().getImageComparisonState());
        comparison.setAllowingPercentOfDifferentPixels(100);
        assertEquals(MATCH, comparison.compareImages().getImageComparisonState());
        comparison.setAllowingPercentOfDifferentPixels(0).setPixelToleranceLevel(0.5);
        assertEquals(MATCH, comparison.compareImages().getImageComparisonState());
        comparison.setPixelToleranceLevel(0);
        assertEquals(MISMATCH, comparison.compareImages().getImageComparisonState());
    }

    @Test
    void thresholdChangesRecomputeRegions() {
        BufferedImage actual = image();
        actual.setRGB(3, 3, Color.WHITE.getRGB());
        actual.setRGB(6, 3, Color.WHITE.getRGB());
        ImageComparison comparison = comparison(image(), actual);

        assertEquals(2, comparison.createMask().size());
        comparison.setThreshold(5);
        assertEquals(List.of(new Rectangle(3, 3, 6, 3)), comparison.createMask());
        comparison.setThreshold(1);
        assertEquals(2, comparison.compareImages().getRectangles().size());
    }

    @Test
    void changesToInputPixelsAreObservedAfterMatchesAndMismatches() {
        BufferedImage actual = image();
        ImageComparison comparison = comparison(image(), actual);

        assertEquals(MATCH, comparison.compareImages().getImageComparisonState());
        actual.setRGB(5, 5, Color.WHITE.getRGB());
        assertEquals(MISMATCH, comparison.compareImages().getImageComparisonState());
        actual.setRGB(5, 5, 0);
        assertEquals(MATCH, comparison.compareImages().getImageComparisonState());
        assertTrue(comparison.createMask().isEmpty());
        actual.setRGB(7, 7, Color.WHITE.getRGB());
        assertEquals(List.of(new Rectangle(7, 7, 7, 7)), comparison.createMask());
        assertEquals(MISMATCH, comparison.compareImages().getImageComparisonState());
    }

    @Test
    void repeatedSizeMismatchResultsRemainStable() {
        ImageComparison comparison = comparison(image(), new BufferedImage(13, 12,
                BufferedImage.TYPE_INT_ARGB));
        for (int run = 0; run < 3; run++) {
            assertEquals(SIZE_MISMATCH, comparison.compareImages().getImageComparisonState());
        }
    }

    private BufferedImage image() {
        return new BufferedImage(12, 12, BufferedImage.TYPE_INT_ARGB);
    }

    private ImageComparison comparison(BufferedImage expected, BufferedImage actual) {
        return new ImageComparison(expected, actual).setPixelToleranceLevel(0).setThreshold(1);
    }
}
