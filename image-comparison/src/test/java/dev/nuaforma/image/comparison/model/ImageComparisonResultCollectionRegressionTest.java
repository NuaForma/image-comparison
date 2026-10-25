package dev.nuaforma.image.comparison.model;

import dev.nuaforma.image.comparison.ImageComparison;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ImageComparisonResultCollectionRegressionTest {
    private BufferedImage image() { return new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB); }

    @Test void newDtosAndEveryPublicFactoryHaveAnEmptyCollection() {
        var image = image();
        for (var result : List.of(new ImageComparisonResult(),
                ImageComparisonResult.defaultMatchResult(image, image),
                ImageComparisonResult.defaultMatchResult(image, image, 25f),
                ImageComparisonResult.defaultMisMatchResult(image, image, 25f),
                ImageComparisonResult.defaultSizeMisMatchResult(image, image, 100f))) {
            assertNotNull(result.getRectangles());
            assertTrue(result.getRectangles().isEmpty());
        }
    }

    @Test void comparisonsExposeCollectionsForEveryState() {
        var expected = image();
        var actual = image();
        var identical = new ImageComparison(expected, actual).compareImages();
        assertEquals(ImageComparisonState.MATCH, identical.getImageComparisonState());
        assertTrue(identical.getRectangles().isEmpty());
        var sizeMismatch = new ImageComparison(expected, new BufferedImage(5, 4, BufferedImage.TYPE_INT_RGB)).compareImages();
        assertEquals(ImageComparisonState.SIZE_MISMATCH, sizeMismatch.getImageComparisonState());
        assertTrue(sizeMismatch.getRectangles().isEmpty());
        actual.setRGB(1, 1, Color.WHITE.getRGB());
        var mismatch = new ImageComparison(expected, actual).compareImages();
        assertEquals(ImageComparisonState.MISMATCH, mismatch.getImageComparisonState());
        assertEquals(List.of(new Rectangle(1, 1, 1, 1)), mismatch.getRectangles());
        var excluded = new ImageComparison(expected, actual).setExcludedAreas(List.of(new Rectangle(1, 1, 1, 1))).compareImages();
        assertEquals(ImageComparisonState.MATCH, excluded.getImageComparisonState());
        assertTrue(excluded.getRectangles().isEmpty());
        var accepted = new ImageComparison(expected, actual).setAllowingPercentOfDifferentPixels(100).compareImages();
        assertEquals(ImageComparisonState.MATCH, accepted.getImageComparisonState());
        assertTrue(accepted.getRectangles().isEmpty());
        assertEquals(6.25f, accepted.getDifferencePercent());
    }

    @Test void defaultCollectionsAreMutableAndIndependent() {
        var first = ImageComparisonResult.defaultMatchResult(image(), image());
        var second = ImageComparisonResult.defaultMatchResult(image(), image());
        first.getRectangles().add(new Rectangle(1, 1, 1, 1));
        assertEquals(1, first.getRectangles().size());
        assertTrue(second.getRectangles().isEmpty());
        assertNotSame(first.getRectangles(), second.getRectangles());
    }

    @Test void nullSetterNormalizesWithoutClearingThePreviousCallerList() {
        var callerList = new ArrayList<>(List.of(new Rectangle(1, 1, 1, 1)));
        var result = new ImageComparisonResult().setRectangles(callerList);
        assertSame(result, result.setRectangles(null));
        assertNotNull(result.getRectangles());
        assertTrue(result.getRectangles().isEmpty());
        assertEquals(1, callerList.size());
        var reset = result.getRectangles();
        result.setRectangles(null);
        assertNotSame(reset, result.getRectangles());
        result.getRectangles().add(new Rectangle(2, 2, 2, 2));
        assertTrue(reset.isEmpty());
    }

    @Test void nonNullSetterPreservesExistingAliasingAndMutability() {
        var callerList = new ArrayList<Rectangle>();
        var result = new ImageComparisonResult().setRectangles(callerList);
        assertSame(callerList, result.getRectangles());
        callerList.add(new Rectangle(1, 1, 1, 1));
        assertEquals(1, result.getRectangles().size());
        result.getRectangles().clear();
        assertTrue(callerList.isEmpty());
        var immutable = List.of(new Rectangle(2, 2, 2, 2));
        result.setRectangles(immutable);
        assertSame(immutable, result.getRectangles());
        assertThrows(UnsupportedOperationException.class, () -> result.getRectangles().clear());
    }
}
