import com.sun.management.ThreadMXBean;
import dev.nuaforma.image.comparison.ImageComparison;
import dev.nuaforma.image.comparison.model.ImageComparisonResult;
import dev.nuaforma.image.comparison.model.ImageComparisonState;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.management.ManagementFactory;
import java.util.Arrays;

/** Repeatable packaged-library smoke measurements, not a general performance benchmark. */
public final class GroupingConsumer {
    public static void main(String[] args) {
        System.out.println("Grouping consumer Java " + System.getProperty("java.version")
                + "; max heap MiB=" + Runtime.getRuntime().maxMemory() / 1048576);
        measure("horizontal", 65536, 1, 1, 0);
        measure("horizontal-default", 65536, 1, 5, 0);
        measure("vertical", 1, 65536, 1, 0);
        measure("dense", 256, 256, 5, 0);
        measure("checkerboard", 128, 128, 1, 1);
        measure("disconnected", 64, 64, 1, 2);
    }

    private static void measure(String name, int width, int height, int threshold, int pattern) {
        BufferedImage expected = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        BufferedImage actual = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        int changed = 0;
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) {
            boolean different = pattern == 0 || (pattern == 1 ? (x + y) % 2 == 0 : x % 3 == 0 && y % 3 == 0);
            if (different) {
                actual.setRGB(x, y, Color.WHITE.getRGB());
                changed++;
            }
        }
        ImageComparison comparison = new ImageComparison(expected, actual).setThreshold(threshold);
        int regions = pattern == 2 ? changed : 1;
        float percentage = (float) (100.0 * changed / ((long) width * height));
        for (int i = 0; i < 2; i++) validate(comparison.compareImages(), regions, percentage);
        var bean = ManagementFactory.getThreadMXBean();
        ThreadMXBean allocation = bean instanceof ThreadMXBean candidate && candidate.isThreadAllocatedMemorySupported()
                ? candidate : null;
        if (allocation != null && !allocation.isThreadAllocatedMemoryEnabled()) allocation.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        long bytes = 0;
        long[] elapsed = new long[5];
        for (int i = 0; i < elapsed.length; i++) {
            long before = allocation == null ? 0 : allocation.getThreadAllocatedBytes(thread);
            long start = System.nanoTime();
            ImageComparisonResult result = comparison.compareImages();
            elapsed[i] = System.nanoTime() - start;
            if (allocation != null) bytes += allocation.getThreadAllocatedBytes(thread) - before;
            validate(result, regions, percentage);
        }
        Arrays.sort(elapsed);
        System.out.printf(java.util.Locale.ROOT, "%s %dx%d threshold=%d regions=%d median-ms=%.3f allocated-MiB/op=%.3f%n",
                name, width, height, threshold, regions, elapsed[2] / 1e6,
                allocation == null ? -1.0 : bytes / 5.0 / 1048576);
    }

    private static void validate(ImageComparisonResult result, int regions, float percentage) {
        if (result.getImageComparisonState() != ImageComparisonState.MISMATCH
                || result.getRectangles().size() != regions || result.getDifferencePercent() != percentage) {
            throw new AssertionError("Wrong region classification/count/percentage");
        }
    }
}
