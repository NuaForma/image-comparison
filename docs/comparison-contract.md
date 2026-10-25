# Comparison contract

This is the approved pre-release behavior for issues #6, #7 and #8. It intentionally changes upstream v4.4.0 semantics; consumers must review thresholds and percentage expectations. No NuaForma release has been published yet.

## Pixels, tolerance and transparency

The engine compares straight ARGB values returned by `BufferedImage.getRGB`, including RGB values stored under alpha zero. It neither composites onto an implicit background nor treats hidden RGB as automatically equal. Premultiplied images are read through `getRGB` as well; conversion/quantization can affect their returned values.

For tolerance `t` in `[0,1)`, a pixel differs if either of these distances exceeds `t`:

- RGB: `sqrt(deltaR^2 + deltaG^2 + deltaB^2) / (255 * sqrt(3))`.
- Alpha: `abs(deltaA) / 255`.

Equality at the boundary is accepted. At zero tolerance, packed ARGB values are compared exactly. This retains the existing RGB normalization for opaque images, while applying alpha tolerance independently. Tolerance is a per-pixel distance, not a percentage of pixels that may differ. The default remains `0.1`. The DSL validates finite values; the Java setter retains its existing invalid-input behavior.

For example, alpha 0 versus 255 differs even at tolerance 0.99; alpha 0 versus 51 is accepted at tolerance 0.2. RGB `(0,0,0)` versus `(51,51,51)` is likewise accepted at tolerance 0.2. Transparent red and transparent black can differ because hidden RGB is compared.

## Percentage, exclusions and acceptance

For equal dimensions, count `N` non-excluded pixels and `D` of those pixels that exceed tolerance. `differencePercent = 100 * D / N`. Excluded pixels count in neither numerator nor denominator. Overlapping inclusive exclusion rectangles count their union once; all excluded means `0%` and `MATCH`.

The result is `MATCH` if `differencePercent <= allowingPercentOfDifferentPixels`, otherwise `MISMATCH`. The default allowance is zero. An accepted result retains its measured percentage, which can be nonzero. Acceptance uses the double-precision calculation before the public float result is rounded. Copying a rounded float percentage into an acceptance limit can therefore move a fractional boundary slightly; use the intended limit directly.

| Example | Measured percentage |
| --- | --- |
| One changed pixel out of four, even a single-channel change at zero tolerance | 25% |
| Same image, but two unchanged pixels excluded | 50% |
| Two changed pixels in a 12x12 image, with one changed pixel excluded | `100 / 143`, approximately 0.6993% |
| All differing pixels within tolerance | 0% |
| All pixels excluded | 0% |

An allowance of 25 accepts exactly one differing pixel out of four and preserves the reported 25. An allowance just below 25 rejects it. Pixels excluded from comparison never inflate the reported metric.

Unequal dimensions always produce `SIZE_MISMATCH` and `100%`, irrespective of tolerance, exclusions or an allowance of 100. The engine does not resize/interpolate input images to manufacture a percentage. The result image remains the original actual image, as before.

`ImageComparisonUtil.getDifferencePercent` is the convenience form for zero tolerance and no exclusions: count exact ARGB differences, or return 100 for unequal dimensions. It does not inherit options from an engine instance. The legacy `pixelDiff` helper still returns raw Manhattan RGB channel distance and ignores alpha; it is not used for classification or percentage calculation.

## Detection, returned regions and drawing

Classification and percentage are computed from pixels before drawing filters. When the allowance is exceeded, the result contains all detected/merged region rectangles. `minimalRectangleSize` only suppresses drawing of rectangles whose final bounding-box area is below the limit. `maximalRectangleCount` then restricts drawing to the largest remaining rectangles (nonpositive values mean no maximum). Neither option removes detected regions from the result or changes classification/percentage.

`createMask` requires equal image dimensions and rejects unequal dimensions with `IllegalArgumentException`. It returns the same unfiltered detected regions when the allowance is exceeded; it returns an empty mask for an accepted comparison. All library-produced results and public result factories expose a non-null rectangle collection. MATCH and SIZE_MISMATCH have empty collections; the unpopulated MISMATCH factory is also empty until regions are assigned. Every new result starts with its own mutable empty list. `setRectangles(null)` replaces it with a fresh mutable empty list without clearing a previously supplied list. A non-null list is still stored by reference: its mutability, element identity and caller aliasing are preserved. Results remain mutable DTOs, not snapshots.

Thus a single differing pixel with minimum area 2 is still `MISMATCH`, with its 1x1 region and measured percentage available, even though the result image has no drawn difference outline. Exclusion drawing, colors, line widths, fills and input-image ownership retain their separate behavior; the fill-position defect remains tracked in #5.

## Image input and owned resources

`ImageComparisonUtil.readImageFromResources` reads an existing filesystem path first, using the existing ImageIO file path; otherwise it tries the library class loader. Missing files/resources raise `ImageNotFoundException`. Present but unsupported, invalid or unreadable input raises `ImageComparisonException` at the read boundary, including when the decoder returns null. I/O causes are preserved. A stream-close failure after decoding is reported as a read failure; if decoding already failed, a close failure remains suppressed on that primary failure.

Library-generated exception messages include the input location with ISO control characters replaced by `?`, rather than input contents. Lookup still uses the original path. Decoder causes are retained for diagnosis. No format/decoder extension, global ImageIO cache/registry change or automatic input normalization is introduced.

The library closes classpath streams it obtains, on success and every failure path. This is necessary because [ImageIO.read(InputStream)](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/javax/imageio/ImageIO.html#read(java.io.InputStream)) does not close the supplied stream. File-stream management remains with the existing ImageIO file reader.

All graphics contexts created by the library share an internal try/finally ownership boundary. It disposes contexts after normal drawing, stroke/setup failures and drawing exceptions, including image conversion. Caller-owned and returned BufferedImages are never flushed or disposed and remain usable; only the graphics contexts created for the operation are released. The JDK documents [Graphics.dispose](https://docs.oracle.com/en/java/javase/17/docs/api/java.desktop/java/awt/Graphics.html#dispose()) as releasing the context's system resources. Rendering geometry, colors, opacity and exact fixture pixels are unchanged by resource cleanup.

## Grouping and resource use

Issue #9 replaces recursive traversal with a heap worklist. The existing five directional rays and threshold distances are preserved; a pixel is labelled when enqueued and enters the worklist at most once. Call-stack use is constant, rather than proportional to region shape. Worklist heap use is O(D), in addition to the O(P) matrix for P image pixels and result images. Region extraction/rectangle merging retain their existing algorithms and can still cost substantial CPU for many disconnected regions.

The packaged `GroupingConsumer` exercises horizontal/vertical strips, dense blocks, checkerboards and disconnected pixels under `-Xss1m -Xmx128m`. It reports median time over five measured operations after two warmups and current-thread allocation bytes where the JDK supports them. This is a repeatable smoke measurement, not a general throughput, retained-memory or hostile-input guarantee. Run `./gradlew --no-daemon build verifyJavaConsumer verifyGroupingConsumer` or, after building, run the consumer directly for process RSS measurements:

```sh
/usr/bin/time -v java -Xss1m -Xmx128m -Djava.awt.headless=true \
  -cp build/consumers/java:image-comparison/build/libs/image-comparison-5.0.0.jar GroupingConsumer
```

Callers must still bound decoded image dimensions and concurrent comparisons. Dimension/rectangle overflow and resource limits remain tracked in #10; this work does not claim arbitrary image sizes are supported. Owned-stream/graphics cleanup, explicit decode failures and non-null result collections are covered by #11/#12/#13. Existing binary image fixtures are unchanged.

## Recorded smoke measurements

Recorded on 25 October 2026 at commit `ca289815178dee02154698bd884fa4f01d7dec1c` (PR #19), WSL Ubuntu on this development workstation, using the Java-17-targeted packaged core and the consumer above. JDKs: Ubuntu OpenJDK 17.0.20.1 and the installed OpenJDK 25.0.4.1. Heap 128 MiB, stack 1 MiB; two warmups and five measurements per case. Times are medians in milliseconds; allocations are current-thread cumulative allocated bytes per operation in MiB, not retained heap. JIT/escape analysis, GC, scheduling and hardware affect these results; there is no cross-JDK speed claim or performance pass threshold.

| Case | Shape | Threshold | Regions | Java 17 ms / allocated MiB | Java 25 ms / allocated MiB |
| --- | --- | --- | --- | --- | --- |
| Horizontal | 65536x1 | 1 | 1 | 12.914 / 14.753 | 9.879 / 12.211 |
| Horizontal default | 65536x1 | 5 | 1 | 8.560 / 15.582 | 6.972 / 12.582 |
| Vertical | 1x65536 | 1 | 1 | 9.323 / 16.003 | 8.698 / 13.003 |
| Dense | 256x256 | 5 | 1 | 9.332 / 14.531 | 7.105 / 11.531 |
| Checkerboard | 128x128 | 1 | 1 | 2.580 / 3.448 | 2.560 / 3.448 |
| Disconnected | 64x64 | 1 | 484 | 7.028 / 23.463 | 4.592 / 7.354 |

`/usr/bin/time -v` observed whole-process maximum RSS of 137264 KiB (Java 17) and 139292 KiB (Java 25), including JVM/native memory outside the 128 MiB heap, across all six cases in one process. These are observations, not library memory bounds. Before the fix, the 65536x1 strip threw `StackOverflowError` at thresholds 1 and 5 under the same stack/heap limits on both JDKs. No meaningful before/after speed comparison is claimed for that failing case.
