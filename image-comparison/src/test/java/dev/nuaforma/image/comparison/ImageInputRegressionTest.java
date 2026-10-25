package dev.nuaforma.image.comparison;

import dev.nuaforma.image.comparison.exception.ImageComparisonException;
import dev.nuaforma.image.comparison.exception.ImageNotFoundException;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImageInputRegressionTest {
    @TempDir Path temporary;

    @Test void rejectsPresentNonImagesAtTheReadBoundary() throws IOException {
        Path path = temporary.resolve("not-an-image.png");
        Files.writeString(path, "synthetic invalid image data");
        var error = assertThrows(ImageComparisonException.class,
                () -> ImageComparisonUtil.readImageFromResources(path.toString()));
        assertTrue(error.getMessage().contains(path.toString()));
        assertFalse(error.getMessage().contains("synthetic invalid image data"));
    }

    @Test void rejectsEmptyAndTruncatedFiles() throws IOException {
        Path empty = temporary.resolve("empty.png");
        Files.write(empty, new byte[0]);
        assertThrows(ImageComparisonException.class, () -> ImageComparisonUtil.readImageFromResources(empty.toString()));
        Path truncated = temporary.resolve("truncated.png");
        Files.write(truncated, new byte[]{(byte)137, 80, 78, 71, 13, 10, 26, 10, 0, 0, 0});
        assertThrows(ImageComparisonException.class, () -> ImageComparisonUtil.readImageFromResources(truncated.toString()));
    }

    @Test void distinguishesDirectoriesAndMissingPaths() {
        assertThrows(ImageComparisonException.class, () -> ImageComparisonUtil.readImageFromResources(temporary.toString()));
        assertThrows(ImageNotFoundException.class,
                () -> ImageComparisonUtil.readImageFromResources(temporary.resolve("missing.png").toString()));
    }

    @Test void constructorDoesNotDeferAnInvalidDecodeToComparison() throws IOException {
        Path path = temporary.resolve("invalid.png");
        Files.writeString(path, "invalid");
        assertThrows(ImageComparisonException.class, () -> new ImageComparison(path.toString(), path.toString()));
    }

    @Test void readsValidPngAndJpegFilesWithoutChangingTheImage() throws IOException {
        for (String format : new String[]{"png", "jpg"}) {
            var original = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) original.setRGB(x, y, Color.RED.getRGB());
            Path path = temporary.resolve("valid." + format);
            assertTrue(ImageIO.write(original, format, path.toFile()));
            var decoded = ImageComparisonUtil.readImageFromResources(path.toString());
            var direct = ImageIO.read(path.toFile());
            assertEquals(4, decoded.getWidth());
            assertEquals(4, decoded.getHeight());
            for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) {
                assertEquals(direct.getRGB(x, y), decoded.getRGB(x, y));
                assertEquals(Color.RED.getRGB(), original.getRGB(x, y));
            }
        }
    }

    @Test void keepsExistingClasspathResourceLookup() {
        assertNotNull(ImageComparisonUtil.readImageFromResources("actual.png"));
        assertThrows(ImageNotFoundException.class, () -> ImageComparisonUtil.readImageFromResources("missing-synthetic-resource.png"));
    }
}
