package dev.nuaforma.image.comparison;

import dev.nuaforma.image.comparison.exception.ImageComparisonException;
import dev.nuaforma.image.comparison.exception.ImageNotFoundException;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImageResourceLifecycleRegressionTest {
    @TempDir Path temporary;

    @Test void closesOwnedClasspathStreamsAfterPngAndJpegDecode() throws IOException {
        for (String format : new String[]{"png", "jpg"}) {
            var input = new TrackedInput(encoded(format));
            var image = read(input, "synthetic-valid-image." + format);
            assertEquals(1, input.closed);
            assertEquals(4, image.getWidth());
            assertEquals(4, image.getHeight());
            assertDoesNotThrow(() -> image.setRGB(0, 0, Color.BLUE.getRGB()));
        }
    }

    @Test void closesOwnedStreamsAfterUnsupportedEmptyAndTruncatedInput() throws IOException {
        byte[] png = encoded("png");
        for (byte[] data : new byte[][]{new byte[0], "synthetic invalid data".getBytes(StandardCharsets.UTF_8), Arrays.copyOf(png, 40)}) {
            var input = new TrackedInput(data);
            assertThrows(ImageComparisonException.class, () -> read(input, "synthetic-invalid-image.png"));
            assertEquals(1, input.closed);
        }
    }

    @Test void closesOwnedStreamOnReadFailureAndPreservesIoCause() throws IOException {
        var input = new TrackedInput(encoded("png"));
        input.failAfter = 40;
        var error = assertThrows(ImageComparisonException.class, () -> read(input, "synthetic-io-failure.png"));
        assertEquals(1, input.closed);
        assertTrue(hasCause(error, input.readFailure));
        assertTrue(error.getMessage().contains("synthetic-io-failure.png"));
    }

    @Test void reportsCloseFailureAfterSuccessfulDecode() throws IOException {
        var input = new TrackedInput(encoded("png"));
        input.closeFailure = new IOException("synthetic close failure");
        var error = assertThrows(ImageComparisonException.class, () -> read(input, "synthetic-close-failure.png"));
        assertEquals(1, input.closed);
        assertSame(input.closeFailure, error.getCause());
    }

    @Test void suppressesCloseFailureWithoutReplacingDecodeFailure() {
        var input = new TrackedInput(new byte[0]);
        input.closeFailure = new IOException("synthetic close failure");
        var error = assertThrows(ImageComparisonException.class, () -> read(input, "synthetic-invalid-image.png"));
        assertEquals(1, input.closed);
        assertTrue(error.getMessage().contains("unsupported or invalid image data"));
        assertArrayEquals(new Throwable[]{input.closeFailure}, error.getSuppressed());
    }

    @Test void absentClasspathResourceIsNotFoundRatherThanAnInvalidDecode() {
        var resources = new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) { return null; }
        };
        assertThrows(ImageNotFoundException.class,
                () -> ImageComparisonUtil.readImageFromResources("synthetic-absent-image.png", resources));
    }

    @Test void keepsFilesystemPrecedenceOverClasspath() throws IOException {
        Path path = temporary.resolve("valid.png");
        Files.write(path, encoded("png"));
        var resources = new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String name) { throw new AssertionError("Filesystem must win"); }
        };
        assertEquals(4, ImageComparisonUtil.readImageFromResources(path.toString(), resources).getWidth());
    }

    @Test void sanitizesLocationControlCharactersWithoutIncludingInputData() {
        var input = new TrackedInput("private synthetic image content".getBytes(StandardCharsets.UTF_8));
        var invalid = assertThrows(ImageComparisonException.class, () -> read(input, "synthetic-invalid\nimage.png"));
        assertTrue(invalid.getMessage().contains("synthetic-invalid?image.png"));
        assertFalse(invalid.getMessage().contains("\n"));
        assertFalse(invalid.getMessage().contains("private synthetic image content"));
        var missing = assertThrows(ImageNotFoundException.class,
                () -> ImageComparisonUtil.readImageFromResources("synthetic-missing\nimage.png", new ClassLoader(null) {}));
        assertFalse(missing.getMessage().contains("\n"));
    }

    @Test void disposesOwnedGraphicsAndKeepsImagesUsable() {
        var image = new TrackedGraphicsImage();
        ImageComparisonUtil.drawWithGraphics(image, graphics -> {
            graphics.setColor(Color.RED);
            graphics.fillRect(1, 1, 1, 1);
        });
        assertEquals(1, image.created);
        assertEquals(1, image.disposed);
        assertEquals(Color.RED.getRGB(), image.getRGB(1, 1));
        image.setRGB(2, 2, Color.BLUE.getRGB());
        assertEquals(Color.BLUE.getRGB(), image.getRGB(2, 2));
    }

    @Test void disposesGraphicsWhenDrawingOrStrokeConfigurationThrows() {
        var image = new TrackedGraphicsImage();
        var failure = new IllegalStateException("synthetic drawing failure");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> ImageComparisonUtil.drawWithGraphics(image, graphics -> { throw failure; })));
        assertEquals(1, image.disposed);
        assertThrows(IllegalArgumentException.class,
                () -> ImageComparisonUtil.drawWithGraphics(image, graphics -> graphics.setStroke(new BasicStroke(-1))));
        assertEquals(2, image.disposed);
        var error = new AssertionError("synthetic rendering error");
        assertSame(error, assertThrows(AssertionError.class,
                () -> ImageComparisonUtil.drawWithGraphics(image, graphics -> { throw error; })));
        assertEquals(3, image.disposed);
        assertEquals(3, image.created);
    }

    @Test void repeatedReadAndDrawOperationsCloseOnlyTheirOwnedResources() throws IOException {
        byte[] png = encoded("png");
        for (int i = 0; i < 25; i++) {
            var input = new TrackedInput(png);
            BufferedImage decoded = read(input, "synthetic-repeat-image.png");
            assertEquals(1, input.closed);
            var target = new TrackedGraphicsImage();
            ImageComparisonUtil.drawWithGraphics(target, graphics -> graphics.drawImage(decoded, 0, 0, null));
            assertEquals(1, target.disposed);
            assertEquals(decoded.getRGB(1, 1), target.getRGB(1, 1));
            var actual = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            var result = new ImageComparison(decoded, actual).compareImages();
            assertEquals(Color.RED.getRGB(), decoded.getRGB(1, 1));
            assertEquals(Color.BLACK.getRGB(), actual.getRGB(1, 1));
            assertDoesNotThrow(() -> result.getResult().setRGB(0, 0, Color.BLUE.getRGB()));
        }
    }

    private static BufferedImage read(TrackedInput input, String name) {
        var resources = new ClassLoader(null) {
            @Override public InputStream getResourceAsStream(String resource) { return input; }
        };
        return ImageComparisonUtil.readImageFromResources(name, resources);
    }

    private static byte[] encoded(String format) throws IOException {
        var image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) image.setRGB(x, y, Color.RED.getRGB());
        var bytes = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, bytes));
        return bytes.toByteArray();
    }

    private static boolean hasCause(Throwable error, Throwable target) {
        while (error != null) {
            if (error == target) return true;
            error = error.getCause();
        }
        return false;
    }

    private static final class TrackedInput extends FilterInputStream {
        int closed;
        int position;
        int failAfter = Integer.MAX_VALUE;
        final IOException readFailure = new IOException("synthetic read failure");
        IOException closeFailure;

        TrackedInput(byte[] bytes) { super(new ByteArrayInputStream(bytes)); }
        @Override public int read() throws IOException {
            if (position >= failAfter) throw readFailure;
            int value = in.read();
            if (value >= 0) position++;
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (position >= failAfter) throw readFailure;
            int count = in.read(bytes, offset, Math.min(length, failAfter - position));
            if (count > 0) position += count;
            return count;
        }
        @Override public void close() throws IOException {
            closed++;
            super.close();
            if (closeFailure != null) throw closeFailure;
        }
    }
}
