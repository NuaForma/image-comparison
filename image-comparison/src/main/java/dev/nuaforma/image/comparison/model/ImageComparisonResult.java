// Modified by NuaForma: package relocation and measured percentages and non-null result collections (#6, #13); see MODIFICATIONS.md.
package dev.nuaforma.image.comparison.model;

import dev.nuaforma.image.comparison.ImageComparisonUtil;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Data transfer objects which contains all the needed data for result of the comparison.
 */
public class ImageComparisonResult {

    /**
     * {@link BufferedImage} object of the expected.
     */
    private BufferedImage expected;

    /**
     * {@link BufferedImage} object of the actual.
     */
    private BufferedImage actual;

    /**
     * {@link BufferedImage} object of the comparison result.
     */
    private BufferedImage result;

    /**
     * State of the comparison.
     */
    private ImageComparisonState imageComparisonState;

    /**
     * Percentage of non-excluded pixels exceeding tolerance. Accepted matches may be nonzero;
     * library-produced SIZE_MISMATCH results use 100 without resizing.
     */
    private float differencePercent;

    /**
     * Detected difference rectangles; never null. Every result starts with its own mutable empty list.
     */
    private List<Rectangle> rectangles = new ArrayList<>();

    /**
     * Create default instance of the {@link ImageComparisonResult} with {@link ImageComparisonState#SIZE_MISMATCH}.
     *
     * @param expected          expected {@link BufferedImage} object.
     * @param actual            actual {@link BufferedImage} object.
     * @param differencePercent the percent of the differences between images.
     * @return instance of the {@link ImageComparisonResult} object.
     */
    public static ImageComparisonResult defaultSizeMisMatchResult(BufferedImage expected, BufferedImage actual,
                                                                  float differencePercent) {
        return new ImageComparisonResult()
                .setImageComparisonState(ImageComparisonState.SIZE_MISMATCH)
                .setDifferencePercent(differencePercent)
                .setExpected(expected)
                .setActual(actual)
                .setResult(actual);
    }

    /**
     * Create default instance of the {@link ImageComparisonResult} with {@link ImageComparisonState#MISMATCH}.
     *
     * @param expected expected {@link BufferedImage} object.
     * @param actual   actual {@link BufferedImage} object.
     * @param differencePercent the persent of the differences between images.
     * @return instance of the {@link ImageComparisonResult} object.
     */
    public static ImageComparisonResult defaultMisMatchResult(BufferedImage expected, BufferedImage actual, float differencePercent) {
        return new ImageComparisonResult()
                .setImageComparisonState(ImageComparisonState.MISMATCH)
                .setDifferencePercent(differencePercent)
                .setExpected(expected)
                .setActual(actual)
                .setResult(actual);
    }

    /**
     * Create default instance of the {@link ImageComparisonResult} with {@link ImageComparisonState#MATCH}.
     *
     * @param expected expected {@link BufferedImage} object.
     * @param actual   actual {@link BufferedImage} object.
     * @return instance of the {@link ImageComparisonResult} object.
     */
    public static ImageComparisonResult defaultMatchResult(BufferedImage expected, BufferedImage actual) {
        return defaultMatchResult(expected, actual, 0f);
    }

    /**
     * Creates an accepted result retaining the measured differing-pixel percentage.
     *
     * @param expected expected image
     * @param actual actual image
     * @param differencePercent measured percentage, which may be nonzero for an accepted match
     * @return accepted comparison result
     */
    public static ImageComparisonResult defaultMatchResult(BufferedImage expected, BufferedImage actual,
                                                          float differencePercent) {
        return new ImageComparisonResult()
                .setImageComparisonState(ImageComparisonState.MATCH)
                .setDifferencePercent(differencePercent)
                .setExpected(expected)
                .setActual(actual)
                .setResult(actual);
    }

    /**
     * Save the image to the provided {@link File} object.
     *
     * @param file the provided {@link File} object.
     * @return this {@link ImageComparisonResult} object.
     */
    public ImageComparisonResult writeResultTo(File file) {
        ImageComparisonUtil.saveImage(file, result);
        return this;
    }

    public BufferedImage getExpected() {
        return expected;
    }

    public ImageComparisonResult setExpected(BufferedImage expected) {
        this.expected = expected;
        return this;
    }

    public BufferedImage getActual() {
        return actual;
    }

    public ImageComparisonResult setActual(BufferedImage actual) {
        this.actual = actual;
        return this;
    }

    public BufferedImage getResult() {
        return result;
    }

    public ImageComparisonResult setResult(BufferedImage result) {
        this.result = result;
        return this;
    }

    public ImageComparisonState getImageComparisonState() {
        return imageComparisonState;
    }

    public ImageComparisonResult setImageComparisonState(ImageComparisonState imageComparisonState) {
        this.imageComparisonState = imageComparisonState;
        return this;
    }

    public float getDifferencePercent() {
        return differencePercent;
    }

    ImageComparisonResult setDifferencePercent(float differencePercent) {
        this.differencePercent = differencePercent;
        return this;
    }

    /**
     * Returns the current non-null collection. Library-produced MATCH and SIZE_MISMATCH results are empty.
     * Defaults are mutable and independent; an explicitly supplied list retains its own mutability/aliasing.
     *
     * @return current rectangle collection, never null
     */
    public List<Rectangle> getRectangles() {
        return rectangles;
    }

    /**
     * Keeps a supplied non-null list by reference, preserving existing ownership and mutability.
     * Null is normalized to a new mutable empty list, without clearing any previously supplied list.
     *
     * @param rectangles current rectangles, or null to reset to an empty collection
     * @return this result
     */
    public ImageComparisonResult setRectangles(List<Rectangle> rectangles) {
        this.rectangles = rectangles == null ? new ArrayList<>() : rectangles;
        return this;
    }
}
