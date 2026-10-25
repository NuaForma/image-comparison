# Build and dependency review

Reviewed 2026-10-25 against primary project release pages and Maven Central POMs/metadata, before adopting the maintenance build. Versions are exact pins, not dynamic selectors. Production comparison logic remains unchanged and has no external runtime dependency.

| Component | Selected version | License / rationale |
| --- | --- | --- |
| [Apache Maven](https://maven.apache.org/docs/3.10.0/release-notes.html) | 3.10.0 | Apache-2.0; current stable 3.x release, not Maven 4 prerelease. Runs the single supported build. |
| [Maven Wrapper](https://maven.apache.org/tools/wrapper/) | 3.3.4 | Apache-2.0; official only-script distribution, no wrapper JAR to vendor. Original script license headers retained. |
| Maven Clean / Resources | 3.5.0 / 3.5.0 | Apache-2.0; explicit lifecycle pins avoid Maven default drift. |
| Maven Enforcer / Compiler | 3.6.3 / 3.16.0 | Apache-2.0; enforce build prerequisites and Java 17 `--release` API/bytecode boundary independently of test compilation. |
| [Maven Surefire](https://github.com/apache/maven-surefire/releases/tag/surefire-3.6.0) | 3.6.0 | Apache-2.0; explicit JUnit Platform discovery; fail when no tests are found. |
| Maven JAR / Source / Javadoc | 3.5.1 / 3.4.0 / 3.12.0 | Apache-2.0; deterministic binary/source/doc packaging. |
| [JUnit Jupiter](https://github.com/junit-team/junit-framework/releases) | 6.1.3 | EPL-2.0, test-only; continues the existing JUnit ecosystem, with modern JDK support and an actual test engine. Build, tests and consumers now share a Java 17 minimum. |
| [Mockito](https://github.com/mockito/mockito/releases) | 5.24.0 | MIT, test-only; retains the existing mocking stack. Subclass mock maker avoids dynamic instrumentation/agent attachment. |
| Byte Buddy / agent, Objenesis | 1.17.7 / 1.17.7, 3.3 | Apache-2.0 (Byte Buddy also retains bundled ASM notices); Mockito's declared transitive versions. Test-only; no runtime dependency is exported by the library. Reassess with Mockito updates or any new JDK. |
| [actions/checkout](https://github.com/actions/checkout/releases/tag/v7.0.1) / [actions/setup-java](https://github.com/actions/setup-java/releases/tag/v6.1.0) | 7.0.1 / 6.1.0 | MIT; exact commit SHA pins, read-only CI permissions. JDK vendor updates intentionally follow the selected Java major; CI images/JDK patch versions are not a hermetic build environment. |

Plugin release/version metadata comes from the [official Maven plugin catalog](https://maven.apache.org/plugins/) and exact POMs under `https://repo.maven.apache.org/maven2/org/apache/maven/plugins/`. JUnit/Mockito POMs were inspected for license and transitive dependency scopes. JUnit's API Guardian, OpenTest4J and JSpecify dependencies are Apache-2.0, test-only. Test/build components are not shaded into the binary, sources or Javadoc JARs, so their test-framework licenses do not replace this library's Apache-2.0 license. Preserve their licenses/notices if separately redistributing a build/test distribution.

Generated Javadoc carries standard OpenJDK doclet support assets and their `legal/` notices, including GPLv2 with the Classpath Exception. These generated documentation assets are distinct from the Apache-2.0 library code; retain the exception, notices and applicable source-availability obligations when distributing them. The binary and source library archives do not embed the JDK. No Javadoc distribution is authorized here; review the actual release JDK's generated assets again before publication.

The Maven 3.10.0 binary ZIP was fetched from Maven Central and its SHA-512 matched Central's published checksum; the matching SHA-256 is pinned in `.mvn/wrapper/maven-wrapper.properties`. This verifies repository-byte consistency, not an independently authenticated release signature. Wrapper scripts were generated from the official versioned distribution, retaining Apache headers. Dependency downloads use Maven Central over HTTPS. Transitive build dependencies are not fully vendored or cryptographically locked by this baseline; pinning versions and repeat-build tests do not constitute a hermetic supply-chain guarantee.

Security review considers maintained upstream releases, primary release/advisory information and the resolved test dependency graph. No runtime libraries, publication secrets or paid integrations are introduced. This is not a complete vulnerability audit of every build transitive; review advisories and requalify on each dependency/toolchain change. Keep automated update proposals subject to the same source, license, compatibility and security review instead of auto-merging them.

Exact-version [OSV API](https://google.github.io/osv.dev/api/) queries on 2026-10-25 returned no listed advisories for all 13 resolved test components: JUnit Jupiter/API/params/engine 6.1.3, JUnit Platform engine/commons 6.1.3, API Guardian 1.1.2, OpenTest4J 1.3.0, JSpecify 1.0.0, Mockito 5.24.0, Byte Buddy/agent 1.17.7 and Objenesis 3.3. This database result is time-bound and does not prove absence of vulnerabilities or cover the complete build-plugin graph.

## Original distribution

The upstream v4.4.0 repository has an Apache-2.0 LICENSE and no NOTICE. Production comparison logic, image fixtures and original author history are preserved; source changes are the documented package relocation and modification notices. NOTICE credits Roman Beskrovnyi, upstream contributors and the original logo attribution to @dee-y. No new image/font fixtures are introduced; generated doclet assets are covered above. Binary, sources and Javadoc JARs embed LICENSE, NOTICE and MODIFICATIONS.md; the POM identifies both original author and fork maintainers. Namespace registration, artifact signing/publication and any new redistribution still need separate approval.
