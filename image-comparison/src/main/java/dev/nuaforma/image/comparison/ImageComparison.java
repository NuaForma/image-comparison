// Modified by NuaForma: package relocation, comparison contracts and bounded-stack grouping (#3, #4, #6, #7, #8, #9); see MODIFICATIONS.md.
package dev.nuaforma.image.comparison;

import dev.nuaforma.image.comparison.model.ExcludedAreas;
import dev.nuaforma.image.comparison.model.ImageComparisonResult;
import dev.nuaforma.image.comparison.model.ImageComparisonState;
import dev.nuaforma.image.comparison.model.Rectangle;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;

/**
 * Main class for comparison images.
 */
public class ImageComparison {

    /**
     * The threshold which means the max distance between non-equal pixels.
     * Could be changed according to the size and requirements of the image.
     */
    private int threshold = 5;

    /**
     * Expected image for comparison
     */
    private final BufferedImage expected;

    /**
     * Actual image for comparison
     */
    private final BufferedImage actual;

    /**
     * Width of the line that is drawn the rectangle
     */
    private int rectangleLineWidth = 1;

    /**
     * {@link File} of the result destination.
     */
    private /* @Nullable */ File destination;

    /**
     * The number which marks how many rectangles. Beginning from 2.
     */
    private int counter = 2;

    /**
     * The number of the marking specific rectangle.
     */
    private int regionCount = counter;

    /**
     * Minimum area (width x height) for drawing a difference rectangle only.
     * Does not change match classification, returned regions, masks or difference percentage.
     */
    private Integer minimalRectangleSize = 1;

    /**
     * Maximal count of the {@link Rectangle}s.
     * It means that would get the first x biggest rectangles.
     * Default value is -1, that means that all the rectangles would be drawn.
     */
    private Integer maximalRectangleCount = -1;

    /**
     * Pixel distance tolerance in [0,1), default 0.1. RGB distance uses Euclidean
     * normalization by 255 * sqrt(3); alpha uses absolute distance divided by 255.
     * A pixel differs if either distance exceeds the tolerance. Zero compares exact ARGB.
     * Straight ARGB values from getRGB are compared, including hidden RGB at alpha zero;
     * no implicit background compositing is performed.
     */
    private double pixelToleranceLevel = 0.1;

    /**
     * Constant using for counting the level of the difference.
     */
    private double differenceConstant;

    /**
     * Matrix YxX => int[y][x].
     * E.g.:
     * | X - width ----
     * | .....................................
     * Y . (0, 0)                            .
     * | .                                   .
     * | .                                   .
     * h .                                   .
     * e .                                   .
     * i .                                   .
     * g .                                   .
     * h .                                   .
     * t .                             (X, Y).
     * | .....................................
     */
    private int[][] matrix;

    /**
     * ExcludedAreas contains a List of {@link Rectangle}s to be ignored when comparing images
     */
    private ExcludedAreas excludedAreas = new ExcludedAreas();

    /**
     * Flag which says draw excluded rectangles or not.
     */
    private boolean drawExcludedRectangles = false;

    /**
     * Percentage of non-excluded pixels exceeding the configured tolerance; measured on every call.
     */
    private double differencePercent;

    /**
     * Flag for filling comparison difference rectangles.
     */
    private boolean fillDifferenceRectangles = false;

    /**
     * Sets the opacity percentage of the fill of comparison difference rectangles. 0.0 means completely transparent and 100.0 means completely opaque.
     */
    private double percentOpacityDifferenceRectangles = 20.0;

    /**
     * Flag for filling excluded rectangles.
     */
    private boolean fillExcludedRectangles = false;

    /**
     * Sets the opacity percentage of the fill of excluded rectangles. 0.0 means completely transparent and 100.0 means completely opaque.
     */
    private double percentOpacityExcludedRectangles = 20.0;

    /**
     * The percent of the allowing pixels to be different to stay {@link ImageComparisonState#MATCH} for comparison.
     * E.g. percent of the pixels, which would ignore in comparison.
     */
    private double allowingPercentOfDifferentPixels = 0.0;

    /**
     * Sets rectangle color of image difference. By default, it's red.
     */
    private Color differenceRectangleColor = Color.RED;

    /**
     * Sets rectangle color of excluded part. By default, it's green.
     */
    private Color excludedRectangleColor = Color.GREEN;

    /**
     * Create a new instance of {@link ImageComparison} that can compare the given images.
     *
     * @param expected expected image to be compared
     * @param actual   actual image to be compared
     */
    public ImageComparison(String expected, String actual) {
        this(ImageComparisonUtil.readImageFromResources(expected),
                ImageComparisonUtil.readImageFromResources(actual),
                null);
    }

    /**
     * Create a new instance of {@link ImageComparison} that can compare the given images.
     *
     * @param expected    expected image to be compared
     * @param actual      actual image to be compared
     * @param destination destination to save the result. If null, the result is shown in the UI.
     */
    public ImageComparison(BufferedImage expected, BufferedImage actual, File destination) {
        this.expected = expected;
        this.actual = actual;
        this.destination = destination;
        differenceConstant = calculateDifferenceConstant();
    }

    /**
     * Create a new instance of {@link ImageComparison} that can compare the given images.
     *
     * @param expected expected image to be compared
     * @param actual   actual image to be compared
     */
    public ImageComparison(BufferedImage expected, BufferedImage actual) {
        this(expected, actual, null);
    }

    /**
     * Compares non-excluded pixels and returns their differing-pixel percentage in [0,100].
     * Excluded pixels count in neither numerator nor denominator; all excluded means zero.
     * MATCH uses the inclusive allowed percentage and retains the measured percentage.
     * Unequal dimensions yield SIZE_MISMATCH and 100 percent without resizing, irrespective
     * of exclusions or allowed percentage. Drawing filters affect only the result image.
     * Draw rectangles which cover the regions of the difference pixels.
     *
     * @return the result of the drawing.
     */
    public ImageComparisonResult compareImages() {

        // check that the images have the same size
        if (isImageSizesNotEqual(expected, actual)) {
            return ImageComparisonResult.defaultSizeMisMatchResult(expected, actual, 100f);
        }

        List<Rectangle> rectangles = populateRectangles();

        if (rectangles.isEmpty()) {
            ImageComparisonResult matchResult = ImageComparisonResult.defaultMatchResult(expected, actual, (float) differencePercent);
            if (drawExcludedRectangles) {
                matchResult.setResult(drawRectangles(rectangles));
                saveImageForDestination(matchResult.getResult());
            }
            return matchResult;
        }

        BufferedImage resultImage = drawRectangles(rectangles);
        saveImageForDestination(resultImage);
        return ImageComparisonResult.defaultMisMatchResult(expected, actual, (float) differencePercent)
                .setResult(resultImage)
                .setRectangles(rectangles);
    }

    /**
     * Check images for equals their widths and heights.
     *
     * @param expected {@link BufferedImage} object of the expected image.
     * @param actual   {@link BufferedImage} object of the actual image.
     * @return true if image size are not equal, false otherwise.
     */
    private boolean isImageSizesNotEqual(BufferedImage expected, BufferedImage actual) {
        return expected.getHeight() != actual.getHeight() || expected.getWidth() != actual.getWidth();
    }

    /**
     * Populate binary matrix with "0" and "1". If the pixels are different set it as "1", otherwise "0".
     *
     * @return the count of different pixels
     */
    private long populateTheMatrixOfTheDifferences() {
        long countOfDifferentPixels = 0;
        long comparedPixelCount = 0;
        matrix = new int[expected.getHeight()][expected.getWidth()];
        for (int y = 0; y < expected.getHeight(); y++) {
            for (int x = 0; x < expected.getWidth(); x++) {
                if (!excludedAreas.contains(new Point(x, y))) {
                    comparedPixelCount++;
                    if (isDifferentPixels(expected.getRGB(x, y), actual.getRGB(x, y))) {
                        matrix[y][x] = 1;
                        countOfDifferentPixels++;
                    }
                }
            }
        }
        differencePercent = comparedPixelCount == 0 ? 0.0
                : 100.0 * countOfDifferentPixels / comparedPixelCount;
        return countOfDifferentPixels;
    }

    /**
     * Say if the two pixels equal or not. The rule is the difference between two pixels
     * need to be more than {@link #pixelToleranceLevel}.
     *
     * @param expectedRgb the RGB value of the Pixel of the Expected image.
     * @param actualRgb   the RGB value of the Pixel of the Actual image.
     * @return {@code true} if they' are difference, {@code false} otherwise.
     */
    private boolean isDifferentPixels(int expectedRgb, int actualRgb) {
        if (expectedRgb == actualRgb) {
            return false;
        } else if (pixelToleranceLevel == 0.0) {
            return true;
        }

        int alpha1 = (expectedRgb >>> 24) & 0xff;
        int alpha2 = (actualRgb >>> 24) & 0xff;
        if (Math.abs(alpha2 - alpha1) > pixelToleranceLevel * 255) {
            return true;
        }

        int red1 = (expectedRgb >> 16) & 0xff;
        int green1 = (expectedRgb >> 8) & 0xff;
        int blue1 = (expectedRgb) & 0xff;
        int red2 = (actualRgb >> 16) & 0xff;
        int green2 = (actualRgb >> 8) & 0xff;
        int blue2 = (actualRgb) & 0xff;

        return (Math.pow(red2 - red1, 2) + Math.pow(green2 - green1, 2) + Math.pow(blue2 - blue1, 2))
                > differenceConstant;
    }

    /**
     * Populate rectangles of the differences
     *
     * @return the collection of the populated {@link Rectangle} objects.
     */
    private List<Rectangle> populateRectangles() {
        counter = 2;
        regionCount = counter;
        long countOfDifferentPixels = populateTheMatrixOfTheDifferences();

        if (countOfDifferentPixels == 0) {
            return emptyList();
        }

        if (isAllowedPercentOfDifferentPixels()) {
            return emptyList();
        }
        groupRegions();
        List<Rectangle> rectangles = new ArrayList<>();
        while (counter <= regionCount) {
            Rectangle rectangle = createRectangle();
            if (!rectangle.equals(Rectangle.createDefault())) {
                rectangles.add(rectangle);
            }
            counter++;
        }

        return mergeRectangles(mergeRectangles(rectangles));
    }

    /** Whether the measured percentage is within the inclusive acceptance limit. */
    private boolean isAllowedPercentOfDifferentPixels() {
        return differencePercent <= allowingPercentOfDifferentPixels;
    }

    /**
     * Create a {@link Rectangle} object.
     *
     * @return the {@link Rectangle} object.
     */
    private Rectangle createRectangle() {
        Rectangle rectangle = Rectangle.createDefault();
        for (int y = 0; y < matrix.length; y++) {
            for (int x = 0; x < matrix[0].length; x++) {
                if (matrix[y][x] == counter) {
                    updateRectangleCreation(rectangle, x, y);
                }
            }
        }
        return rectangle;
    }

    /**
     * Update {@link Point} of the rectangle based on x and y coordinates.
     */
    private void updateRectangleCreation(Rectangle rectangle, int x, int y) {
        if (x < rectangle.getMinPoint().getX()) {
            rectangle.getMinPoint().x = x;
        }
        if (x > rectangle.getMaxPoint().getX()) {
            rectangle.getMaxPoint().x = x;
        }

        if (y < rectangle.getMinPoint().getY()) {
            rectangle.getMinPoint().y = y;
        }
        if (y > rectangle.getMaxPoint().getY()) {
            rectangle.getMaxPoint().y = y;
        }
    }

    /**
     * Find overlapping rectangles and merge them.
     */
    private List<Rectangle> mergeRectangles(List<Rectangle> rectangles) {
        // Invalid default bounds cannot collide with a real single-pixel region at (0,0).
        int position = 0;
        while (position < rectangles.size()) {
            if (rectangles.get(position).equals(Rectangle.createDefault())) {
                position++;
                continue;
            }
            for (int i = 1 + position; i < rectangles.size(); i++) {
                Rectangle r1 = rectangles.get(position);
                Rectangle r2 = rectangles.get(i);
                if (r2.equals(Rectangle.createDefault())) {
                    continue;
                }
                if (r1.isOverlapping(r2)) {
                    rectangles.set(position, r1.merge(r2));
                    r2.setDefaultValues();
                    if (position != 0) {
                        position--;
                    }
                }
            }
            position++;
        }

        return rectangles.stream().filter(it -> !it.equals(Rectangle.createDefault())).collect(Collectors.toList());
    }

    /**
     * Draw the rectangles based on collection of the rectangles and result image.
     *
     * @param rectangles the collection of the {@link Rectangle} objects.
     * @return result {@link BufferedImage} with drawn rectangles.
     */
    private BufferedImage drawRectangles(List<Rectangle> rectangles) {
        BufferedImage resultImage = ImageComparisonUtil.deepCopy(actual);
        Graphics2D graphics = preparedGraphics2D(resultImage);

        drawExcludedRectangles(graphics);
        drawRectanglesOfDifferences(rectangles, graphics);

        return resultImage;
    }

    /**
     * Draw excluded rectangles.
     *
     * @param graphics prepared {@link Graphics2D}object.
     */
    private void drawExcludedRectangles(Graphics2D graphics) {
        if (drawExcludedRectangles) {
            graphics.setColor(this.excludedRectangleColor);
            draw(graphics, excludedAreas.getExcluded());

            if (fillExcludedRectangles) {
                fillRectangles(graphics, excludedAreas.getExcluded(), percentOpacityExcludedRectangles);
            }
        }
    }

    /**
     * Draw rectangles with the differences.
     *
     * @param rectangles the collection of the {@link Rectangle} of differences.
     * @param graphics   prepared {@link Graphics2D}object.
     */
    private void drawRectanglesOfDifferences(List<Rectangle> rectangles, Graphics2D graphics) {
        List<Rectangle> visibleRectangles = rectangles.stream()
                .filter(rectangle -> rectangle.size() >= minimalRectangleSize)
                .collect(Collectors.toList());
        List<Rectangle> rectanglesForDraw;
        graphics.setColor(this.differenceRectangleColor);

        if (maximalRectangleCount > 0 && maximalRectangleCount < visibleRectangles.size()) {
            rectanglesForDraw = visibleRectangles.stream()
                    .sorted(Comparator.comparing(Rectangle::size))
                    .skip(visibleRectangles.size() - maximalRectangleCount)
                    .collect(Collectors.toList());
        } else {
            rectanglesForDraw = visibleRectangles;
        }

        draw(graphics, rectanglesForDraw);

        if (fillDifferenceRectangles) {
            fillRectangles(graphics, rectanglesForDraw, percentOpacityDifferenceRectangles);
        }
    }

    /**
     * Prepare {@link Graphics2D} based on resultImage and rectangleLineWidth
     *
     * @param image image based on created {@link Graphics2D}.
     * @return prepared {@link Graphics2D} object.
     */
    private Graphics2D preparedGraphics2D(BufferedImage image) {
        Graphics2D graphics = image.createGraphics();
        graphics.setStroke(new BasicStroke(rectangleLineWidth));
        return graphics;
    }

    /**
     * Save image to destination object if exists.
     *
     * @param image {@link BufferedImage} to be saved.
     */
    private void saveImageForDestination(BufferedImage image) {
        if (Objects.nonNull(destination)) {
            ImageComparisonUtil.saveImage(destination, image);
        }
    }

    /**
     * Draw rectangles based on collection of the {@link Rectangle} and {@link Graphics2D}.
     * getWidth/getHeight return real width/height,
     * so need to draw rectangle on one px smaller because minpoint + width/height is point on excluded pixel.
     *
     * @param graphics   the {@link Graphics2D} object for drawing.
     * @param rectangles the collection of the {@link Rectangle}.
     */
    private void draw(Graphics2D graphics, List<Rectangle> rectangles) {
        rectangles.forEach(rectangle -> graphics.drawRect(
                rectangle.getMinPoint().x,
                rectangle.getMinPoint().y,
                rectangle.getWidth() - 1,
                rectangle.getHeight() - 1)
        );
    }

    /**
     * Fill rectangles based on collection of the {@link Rectangle} and {@link Graphics2D}.
     * getWidth/getHeight return real width/height,
     * so need to draw rectangle fill two px smaller to fit inside rectangle borders.
     *
     * @param graphics       the {@link Graphics2D} object for drawing.
     * @param rectangles     rectangles the collection of the {@link Rectangle}.
     * @param percentOpacity the opacity of the fill.
     */
    private void fillRectangles(Graphics2D graphics, List<Rectangle> rectangles, double percentOpacity) {

        graphics.setColor(new Color(graphics.getColor().getRed(),
                graphics.getColor().getGreen(),
                graphics.getColor().getBlue(),
                (int) (percentOpacity / 100 * 255)
        ));
        rectangles.forEach(rectangle -> graphics.fillRect(
                rectangle.getMinPoint().x - 1,
                rectangle.getMinPoint().y - 1,
                rectangle.getWidth() - 2,
                rectangle.getHeight() - 2)
        );
    }


    /**
     * Group rectangle regions in matrix.
     */
    private void groupRegions() {
        for (int y = 0; y < matrix.length; y++) {
            for (int x = 0; x < matrix[y].length; x++) {
                if (matrix[y][x] == 1) {
                    joinToRegion(x, y);
                    regionCount++;
                }
            }
        }
    }

    /**
     * Visits the same five directional rays as the original grouping algorithm using a heap worklist.
     * A pixel is labelled when queued, so each different pixel enters the worklist at most once.
     * Call-stack use is constant; auxiliary heap use is O(number of differing pixels).
     */
    private void joinToRegion(int x, int y) {
        Deque<Point> pending = new ArrayDeque<>();
        queueRegionPixel(x, y, pending);
        int maxJump = Math.min(threshold, Math.max(matrix.length - 1, matrix[0].length - 1));
        while (!pending.isEmpty()) {
            Point point = pending.removeLast();
            for (int i = 0; i < maxJump; i++) {
                long distance = (long) i + 1;
                queueRegionPixel(point.x + distance, point.y, pending);
                queueRegionPixel(point.x, point.y + distance, pending);
                queueRegionPixel(point.x + distance, point.y - distance, pending);
                queueRegionPixel(point.x - distance, point.y + distance, pending);
                queueRegionPixel(point.x + distance, point.y + distance, pending);
            }
        }
    }

    private void queueRegionPixel(long x, long y, Deque<Point> pending) {
        if (y < 0 || y >= matrix.length || x < 0 || x >= matrix[(int) y].length
                || matrix[(int) y][(int) x] != 1) {
            return;
        }
        matrix[(int) y][(int) x] = regionCount;
        pending.addLast(new Point((int) x, (int) y));
    }

    /**
     * Returns all detected regions when the percentage exceeds the acceptance limit.
     * Drawing filters do not suppress mask regions. An accepted comparison returns an empty mask.
     * If you submit two images that are the same barring the parts you want to excludedAreas you get a list of
     * rectangles that can be used as said excludedAreas
     *
     * @return List of {@link Rectangle}
     * @throws IllegalArgumentException if image dimensions differ; no shared mask grid exists
     */
    public List<Rectangle> createMask() {
        if (isImageSizesNotEqual(expected, actual)) {
            throw new IllegalArgumentException("Mask creation requires equal image dimensions");
        }
        return populateRectangles();
    }

    public double getPixelToleranceLevel() {
        return pixelToleranceLevel;
    }

    public ImageComparison setPixelToleranceLevel(double pixelToleranceLevel) {
        if (0.0 <= pixelToleranceLevel && pixelToleranceLevel < 1) {
            this.pixelToleranceLevel = pixelToleranceLevel;
            differenceConstant = calculateDifferenceConstant();
        }
        return this;
    }

    private double calculateDifferenceConstant() {
        return 3 * Math.pow(pixelToleranceLevel * 255, 2);
    }

    public boolean isDrawExcludedRectangles() {
        return drawExcludedRectangles;
    }

    public ImageComparison setDrawExcludedRectangles(boolean drawExcludedRectangles) {
        this.drawExcludedRectangles = drawExcludedRectangles;
        return this;
    }

    public int getThreshold() {
        return threshold;
    }

    public ImageComparison setThreshold(int threshold) {
        this.threshold = threshold;
        return this;
    }

    public Optional<File> getDestination() {
        return Optional.ofNullable(destination);
    }

    public ImageComparison setDestination(File destination) {
        this.destination = destination;
        return this;
    }

    public BufferedImage getExpected() {
        return expected;
    }

    public BufferedImage getActual() {
        return actual;
    }

    public int getRectangleLineWidth() {
        return rectangleLineWidth;
    }

    public ImageComparison setRectangleLineWidth(int rectangleLineWidth) {
        this.rectangleLineWidth = rectangleLineWidth;
        return this;
    }

    public Integer getMinimalRectangleSize() {
        return minimalRectangleSize;
    }

    public ImageComparison setMinimalRectangleSize(Integer minimalRectangleSize) {
        this.minimalRectangleSize = minimalRectangleSize;
        return this;
    }

    public Integer getMaximalRectangleCount() {
        return maximalRectangleCount;
    }

    public ImageComparison setMaximalRectangleCount(Integer maximalRectangleCount) {
        this.maximalRectangleCount = maximalRectangleCount;
        return this;
    }

    public ImageComparison setExcludedAreas(List<Rectangle> excludedAreas) {
        this.excludedAreas = new ExcludedAreas(excludedAreas);
        return this;
    }

    public boolean isFillDifferenceRectangles() {
        return this.fillDifferenceRectangles;
    }

    public double getPercentOpacityDifferenceRectangles() {
        return this.percentOpacityDifferenceRectangles;
    }

    public ImageComparison setDifferenceRectangleFilling(boolean fillRectangles, double percentOpacity) {
        this.fillDifferenceRectangles = fillRectangles;
        this.percentOpacityDifferenceRectangles = percentOpacity;
        return this;
    }

    public boolean isFillExcludedRectangles() {
        return this.fillExcludedRectangles;
    }

    public double getPercentOpacityExcludedRectangles() {
        return this.percentOpacityExcludedRectangles;
    }

    public ImageComparison setExcludedRectangleFilling(boolean fillRectangles, double percentOpacity) {
        this.fillExcludedRectangles = fillRectangles;
        this.percentOpacityExcludedRectangles = percentOpacity;
        return this;
    }

    public double getAllowingPercentOfDifferentPixels() {
        return allowingPercentOfDifferentPixels;
    }

    public ImageComparison setAllowingPercentOfDifferentPixels(double allowingPercentOfDifferentPixels) {
        if (0.0 <= allowingPercentOfDifferentPixels && allowingPercentOfDifferentPixels <= 100) {
            this.allowingPercentOfDifferentPixels = allowingPercentOfDifferentPixels;
        } else {
            //todo add warning here
        }

        return this;
    }

    public Color getDifferenceRectangleColor() {
        return this.differenceRectangleColor;
    }

    public ImageComparison setDifferenceRectangleColor(Color differenceRectangleColor) {
        this.differenceRectangleColor = differenceRectangleColor;
        return this;
    }

    public Color getExcludedRectangleColor() {
        return this.excludedRectangleColor;
    }

    public ImageComparison setExcludedRectangleColor(Color excludedRectangleColor) {
        this.excludedRectangleColor = excludedRectangleColor;
        return this;
    }
}
