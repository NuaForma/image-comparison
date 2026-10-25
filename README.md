# NuaForma Image Comparison

An independently maintained Apache-2.0 fork of [romankh3/image-comparison](https://github.com/romankh3/image-comparison), created from [v4.4.0](https://github.com/romankh3/image-comparison/tree/v4.4.0). Original author: Roman Beskrovnyi and upstream contributors. The original logo remains credited to [@dee-y](https://github.com/dee-y).

Compare two `BufferedImage` values, classify matches and size mismatches, mark differing regions and exclude configured rectangles. Production code uses standard Java/AWT only, with **no external runtime dependencies**. This is a generic image comparison library; PDF rendering and product integration are outside its scope.

## Status and compatibility

No NuaForma release has been published. The intended Maven coordinates are `dev.nuaforma:image-comparison`; the development POM uses `4.4.1-SNAPSHOT`. The earlier 4.4.1 release idea must be reassessed before publication because the Java baseline and package changes break compatibility with upstream. The snapshot number is a development placeholder, not a release promise. No namespace claim or package publication is implied. Upstream tags identify upstream releases only.

Java 17 is the minimum supported runtime and API/bytecode target. Java 8 is unsupported. Production classes now use `dev.nuaforma.image.comparison` instead of `com.github.romankh3.image.comparison`. Comparison logic and method shapes are preserved, but the package relocation breaks source/binary compatibility and requires callers to update imports and recompile. The runtime baseline is also intentionally breaking. Build and tests require JDK 17 or later; CI checks JDK 17 and 25 and runs a standalone packaged consumer on both, including the Java 17 minimum for artifacts built on Java 25. Test libraries do not become runtime dependencies. See [maintenance and compatibility](docs/maintenance.md) for exact boundaries and verification.

## Build

Use the checked-in Maven wrapper (Maven 3.10.0; download integrity pinned). The first run needs access to Maven Central. Select the build JDK with `JAVA_HOME`.

```sh
./mvnw --batch-mode --no-transfer-progress clean verify
python3 tools/verify-artifact.py target/image-comparison-4.4.1-SNAPSHOT.jar
mkdir -p target/consumer
javac --release 17 -cp target/image-comparison-4.4.1-SNAPSHOT.jar -d target/consumer tools/consumer/Java17Consumer.java
java -Djava.awt.headless=true -cp target/consumer:target/image-comparison-4.4.1-SNAPSHOT.jar Java17Consumer
```

Use `mvnw.cmd` and the platform classpath separator when appropriate. The Maven build replaces the old parallel Gradle/Travis/Bintray/Coveralls configuration, retained in Git history. `verify` builds the binary, sources and Javadoc archives without uploading anything.

## Example

```java
import dev.nuaforma.image.comparison.ImageComparison;
import dev.nuaforma.image.comparison.model.ImageComparisonResult;
import java.awt.image.BufferedImage;

BufferedImage expected = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
BufferedImage actual = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
ImageComparisonResult result = new ImageComparison(expected, actual)
        .setPixelToleranceLevel(0.1)
        .compareImages();
```

Configuration options, image examples and the original usage guide are available in the [upstream v4.4.0 README](https://github.com/romankh3/image-comparison/blob/v4.4.0/README.md). Its installation coordinates refer to upstream artifacts; replace its `com.github.romankh3.image.comparison` imports with `dev.nuaforma.image.comparison` when adapting examples. This fork does not include later upstream APIs such as `simpleComparison()`.

## Maintenance and contributing

Read [CONTRIBUTING.md](CONTRIBUTING.md), [maintenance and release process](docs/maintenance.md), [tooling and license review](docs/tooling-review.md), [SECURITY.md](SECURITY.md) and [CHANGELOG.md](CHANGELOG.md). Fork issues and PRs belong in [NuaForma/image-comparison](https://github.com/NuaForma/image-comparison). `develop` is the integration branch; `main` is the release baseline.

## License and attribution

[Apache License 2.0](LICENSE), preserved from upstream. See [NOTICE](NOTICE) and [MODIFICATIONS.md](MODIFICATIONS.md) for origin, authorship and the fork's modifications. Original source and fixture attribution are retained. The inherited [release notes](RELEASE_NOTES.md) describe upstream history. Community participation follows the [Code of Conduct](CODE_OF_CONDUCT.md).
