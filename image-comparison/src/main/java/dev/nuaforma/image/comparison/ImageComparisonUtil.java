// Modified by NuaForma: package relocation, pixel metric and owned image resources (#6, #7, #11, #12); see MODIFICATIONS.md.
package dev.nuaforma.image.comparison;

import dev.nuaforma.image.comparison.exception.ImageComparisonException;
import dev.nuaforma.image.comparison.exception.ImageNotFoundException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;

/**
 * Tools for the {@link ImageComparison} object.
 */
public final class ImageComparisonUtil {

    /**
     * Make a copy of the {@link BufferedImage} object.
     *
     * @param image the provided image.
     * @return copy of the provided image.
     */
    static BufferedImage deepCopy(BufferedImage image) {
        ColorModel cm = image.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = image.copyData(image.getRaster().createCompatibleWritableRaster());
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }

    /**
     * Reads a filesystem image first, falling back to a classpath resource when the path does not exist.
     * Library-owned input streams are closed on success and failure. Never returns null.
     *
     * @param path filesystem or classpath location
     * @return decoded image, owned by the caller
     * @throws ImageNotFoundException if neither a file nor a classpath resource exists
     * @throws ImageComparisonException if input is present but unsupported, invalid or unreadable,
     *         or if closing its owned stream fails; I/O causes are retained
     */
    public static BufferedImage readImageFromResources(String path) throws ImageComparisonException {
        return readImageFromResources(path, ImageComparisonUtil.class.getClassLoader());
    }

    // Internal lookup seam for observing resource ownership without changing the public API or global loaders.
    static BufferedImage readImageFromResources(String path, ClassLoader resources) {
        File imageFile = new File(path);
        if (imageFile.exists()) {
            String context = "Cannot read image from the file, path=" + safeLocation(path);
            try {
                return requireDecodedImage(ImageIO.read(imageFile), context);
            } catch (IOException error) {
                throw new ImageComparisonException(context, error);
            }
        }
        InputStream input = resources.getResourceAsStream(path);
        if (input == null) {
            throw new ImageNotFoundException("Image with path = " + safeLocation(path) + " not found");
        }
        return readOwnedImage(input, "Cannot read image from the resource, path=" + safeLocation(path));
    }

    private static BufferedImage readOwnedImage(InputStream input, String context) {
        try (input) {
            return requireDecodedImage(ImageIO.read(input), context);
        } catch (IOException error) {
            throw new ImageComparisonException(context, error);
        }
    }

    private static BufferedImage requireDecodedImage(BufferedImage decoded, String context) {
        if (decoded == null) {
            throw new ImageComparisonException(context + "; unsupported or invalid image data");
        }
        return decoded;
    }

    private static String safeLocation(String path) {
        StringBuilder safe = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char value = path.charAt(i);
            safe.append(Character.isISOControl(value) ? '?' : value);
        }
        return safe.toString();
    }

    // All internally created drawing contexts pass through this exception-safe ownership boundary.
    static void drawWithGraphics(BufferedImage image, Consumer<Graphics2D> draw) {
        Graphics2D graphics = image.createGraphics();
        try {
            draw.accept(graphics);
        } finally {
            graphics.dispose();
        }
    }

    /**
     * Save image to the provided path.
     *
     * @param pathFile the path to the saving image.
     * @param image the {@link BufferedImage} object of this specific image.
     * @throws ImageComparisonException due to save image.
     */
    public static void saveImage(File pathFile, BufferedImage image) throws ImageComparisonException {
        File dir = pathFile.getParentFile();
        // make dir if it's not using from Gradle.
        boolean dirExists = dir == null || dir.isDirectory() || dir.mkdirs();
        if (!dirExists) {
            throw new ImageComparisonException("Unable to create directory " + dir);
        }
        try {
            ImageIO.write(image, "png", pathFile);
        } catch (IOException e) {
            throw new ImageComparisonException(
                    String.format("Cannot save image to path=%s", pathFile.getAbsolutePath()), e);
        }
    }

    /**
     * Resize image to new dimensions and return new image.
     *
     * @param img the object of the image to be resized.
     * @param newW the new width.
     * @param newH the new height.
     * @return resized {@link BufferedImage} object.
     */
    public static BufferedImage resize(BufferedImage img, int newW, int newH) {
        return toBufferedImage(img.getScaledInstance(newW, newH, Image.SCALE_SMOOTH));
    }

    /**
     * Convert image to buffered image.
     *
     * @param img the object of the image to be converted to buffered image.
     * @return the converted buffered image.
     */
    public static BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        float softenFactor = 0.05f;
        final Image temp = new ImageIcon(img).getImage();
        final BufferedImage bufferedImage = new BufferedImage(
                temp.getWidth(null),
                temp.getHeight(null),
                BufferedImage.TYPE_INT_RGB);
        drawWithGraphics(bufferedImage, graphics -> {
            graphics.setColor(Color.white);
            graphics.fillRect(0, 0, temp.getWidth(null), temp.getHeight(null));
            graphics.drawImage(temp, 0, 0, null);
        });

        final float[] softenArray = {0, softenFactor, 0, softenFactor, 1 - (softenFactor * 4), softenFactor, 0,
                softenFactor, 0};
        final Kernel kernel = new Kernel(3, 3, softenArray);
        final ConvolveOp cOp = new ConvolveOp(kernel, ConvolveOp.EDGE_NO_OP, null);

        return cOp.filter(bufferedImage, null);
    }

    /**
     * Returns the percentage of pixels whose exact ARGB values differ. This convenience
     * method uses zero tolerance and no exclusions, unlike a configured ImageComparison.
     * Unequal dimensions return 100 without resizing. Hidden RGB at alpha zero is compared.
     *
     * @param img1 the first image
     * @param img2 the second image
     * @return differing-pixel percentage in [0,100]
     */
    public static float getDifferencePercent(BufferedImage img1, BufferedImage img2) {
        int width = img1.getWidth();
        int height = img1.getHeight();
        if (width != img2.getWidth() || height != img2.getHeight()) {
            return 100f;
        }
        long different = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (img1.getRGB(x, y) != img2.getRGB(x, y)) different++;
            }
        }
        return (float) (100.0 * different / ((long) width * height));
    }

    /**
     * Raw Manhattan RGB channel distance, ignoring alpha. This legacy intensity helper
     * is not used by comparison classification or the differing-pixel percentage.
     *
     * @param rgb1 the first rgb
     * @param rgb2 the second rgn
     * @return the difference.
     */
    public static int pixelDiff(int rgb1, int rgb2) {
        int r1 = (rgb1 >> 16) & 0xff;
        int g1 = (rgb1 >> 8) & 0xff;
        int b1 = rgb1 & 0xff;
        int r2 = (rgb2 >> 16) & 0xff;
        int g2 = (rgb2 >> 8) & 0xff;
        int b2 = rgb2 & 0xff;
        return Math.abs(r1 - r2) + Math.abs(g1 - g2) + Math.abs(b1 - b2);
    }
}
