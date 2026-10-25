# Build and dependency review

Reviewed 2026-10-25 against primary project release pages and Maven Central POMs/metadata, for the maintenance baseline and the issue #14 POM cleanup. Versions are exact pins, not dynamic selectors. The dependency/POM cleanup leaves production comparison logic unchanged and introduces no external runtime dependency.

| Component | Selected version | License / rationale |
| --- | --- | --- |
| [Apache Maven](https://maven.apache.org/docs/3.10.0/release-notes.html) | 3.10.0 | Apache-2.0; current stable 3.x release, not Maven 4 prerelease. Runs the single supported build. |
| [Maven Wrapper](https://maven.apache.org/tools/wrapper/) | 3.3.4 | Apache-2.0; official only-script distribution, no wrapper JAR to vendor. Original script license headers retained. |
| Maven Clean / Resources | 3.5.0 / 3.5.0 | Apache-2.0; explicit lifecycle pins avoid Maven default drift. |
| Maven Enforcer / Compiler | 3.6.3 / 3.16.0 | Apache-2.0; enforce build prerequisites and Java 17 `--release` API/bytecode boundary independently of test compilation. |
| [Maven Surefire](https://github.com/apache/maven-surefire/releases/tag/surefire-3.6.0) | 3.6.0 | Apache-2.0; explicit JUnit Platform discovery; fail when no tests are found. |
| Maven JAR / Source / Javadoc | 3.5.1 / 3.4.0 / 3.12.0 | Apache-2.0; deterministic binary/source/doc packaging. |
| [JUnit Jupiter API / engine](https://docs.junit.org/6.1.3/running-tests/build-support.html) | 6.1.3 / 6.1.3 | EPL-2.0, test-only; the API compiles tests and the explicit engine executes them. No parameterized tests currently require the Jupiter aggregate or params module. |
| [JSpecify](https://github.com/jspecify/jspecify/releases/tag/v1.0.1) | 1.0.1 | Apache-2.0, test-only through JUnit. A narrow dependency-management override updates JUnit's 1.0.0 pin to the current compatible patch; upstream reports documentation/build/module metadata changes without semantic changes. No dependencies of its own. |
| [actions/checkout](https://github.com/actions/checkout/releases/tag/v7.0.1) / [actions/setup-java](https://github.com/actions/setup-java/releases/tag/v6.1.0) | 7.0.1 / 6.1.0 | MIT; exact commit SHA pins, read-only CI permissions. JDK vendor updates intentionally follow the selected Java major; CI images/JDK patch versions are not a hermetic build environment. |

Plugin release/version metadata comes from the [official Maven plugin catalog](https://maven.apache.org/plugins/) and exact POMs under `https://repo.maven.apache.org/maven2/org/apache/maven/plugins/`. JUnit and JSpecify POMs were inspected for license and transitive dependency scopes. Mockito and its three transitive instrumentation/instantiation dependencies were removed: the sole remaining mock test is more directly exercised with real temporary files. JUnit's API Guardian, OpenTest4J and JSpecify dependencies are Apache-2.0, test-only. Test/build components are not shaded into the binary, sources or Javadoc JARs, so their test-framework licenses do not replace this library's Apache-2.0 license. Preserve their licenses/notices if separately redistributing a build/test distribution.

Generated Javadoc carries standard OpenJDK doclet support assets and their `legal/` notices, including GPLv2 with the Classpath Exception. These generated documentation assets are distinct from the Apache-2.0 library code; retain the exception, notices and applicable source-availability obligations when distributing them. The binary and source library archives do not embed the JDK. No Javadoc distribution is authorized here; review the actual release JDK's generated assets again before publication.

The Maven 3.10.0 binary ZIP was fetched from Maven Central and its SHA-512 matched Central's published checksum; the matching SHA-256 is pinned in `.mvn/wrapper/maven-wrapper.properties`. This verifies repository-byte consistency, not an independently authenticated release signature. Wrapper scripts were generated from the official versioned distribution, retaining Apache headers. Dependency downloads use Maven Central over HTTPS. Transitive build dependencies are not fully vendored or cryptographically locked by this baseline; pinning versions and repeat-build tests do not constitute a hermetic supply-chain guarantee.

Security review considers maintained upstream releases, primary release/advisory information and the resolved test dependency graph. No runtime libraries, publication secrets or paid integrations are introduced. This is not a complete vulnerability audit of every build transitive; review advisories and requalify on each dependency/toolchain change. Keep automated update proposals subject to the same source, license, compatibility and security review instead of auto-merging them.

Exact-version [OSV API](https://google.github.io/osv.dev/api/) queries on 2026-10-25 returned no listed advisories for the seven retained test components: JUnit Jupiter API/engine 6.1.3, JUnit Platform engine/commons 6.1.3, API Guardian 1.1.2, OpenTest4J 1.3.0 and JSpecify 1.0.1. Maven Central stable metadata confirms these selected test versions are current. The initial baseline also queried the now-removed Mockito stack. Database results are time-bound and do not prove absence of vulnerabilities or cover the complete build-plugin dependency graph.

## Original distribution

The upstream v4.4.0 repository has an Apache-2.0 LICENSE and no NOTICE. Image fixtures and original author history are preserved; source changes and later behavior fixes are identified in MODIFICATIONS.md. NOTICE credits Roman Beskrovnyi, upstream contributors and the original logo attribution to @dee-y. No new image/font fixtures are introduced; generated doclet assets are covered above. Binary, sources and Javadoc JARs embed LICENSE, NOTICE and MODIFICATIONS.md; the POM identifies both original author and fork maintainers. Namespace registration, artifact signing/publication and any new redistribution still need separate approval.


## POM structure and necessity (issue #14)

The project remains a single JAR module. Production has no external compile/runtime dependencies. Two explicit test dependencies are sufficient: Jupiter API and its engine, sharing one version property. The previously selected Jupiter aggregate also included the unused params module; remove it until tests actually require it. Replacing the one remaining Mockito test removes Mockito, Byte Buddy, Byte Buddy Agent and Objenesis without reducing test coverage.

Retain the eight pinned build plugins: Clean/Resources/Compiler/Surefire/JAR implement the normal lifecycle; Enforcer checks the supported build JDK/Maven range; Source/Javadoc produce the separately verified source/documentation archives. The Javadoc resource execution carries the required project attribution into that archive. These are build-time components, not consumer runtime dependencies.

Plugin versions remain next to their single declarations. A shared JUnit version aligns the two test artifacts; a BOM, parent POM, additional profiles or a multi-module build offers no concrete benefit at this size. The single dependency-management entry is intentional: it constrains JUnit's required JSpecify annotation dependency to the reviewed 1.0.1 patch without adding an unnecessary direct dependency. Reassess/remove that override when JUnit selects 1.0.1 or later itself. Do not independently replace build-plugin transitive versions just to maximize their version numbers; review them with the owning plugin and qualify any security-driven override.

Kotlin DSL support is a separate design decision. This cleanup introduces no Kotlin compiler/runtime, assertion framework or optional module in anticipation of that decision.
