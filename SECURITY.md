# Security policy

This fork is not yet released. Security fixes will initially target develop and must retain the Java 17 consumer boundary. No supported fork release range or response-time guarantee exists yet.

Report vulnerabilities through [GitHub private vulnerability reporting](https://github.com/NuaForma/image-comparison/security/advisories/new). If that option is unavailable, open a minimal issue asking for a private reporting channel without including vulnerability details or sensitive images. Do not send fork reports to upstream maintainers automatically.

Include the commit/version, JDK, operating system, minimal synthetic reproducer, impact and resource usage. Remove private images, credentials and personal data. Image decoding and region grouping can consume substantial memory/CPU; callers should bound decoded image dimensions and concurrent work. This maintenance baseline does not establish limits for hostile inputs or claim a security audit.

Maintain dependencies and build tools through reviewed updates. CI has read-only repository permissions and no publishing credentials. Publication and namespace registration require separate authorization.

The optional Kotlin test DSL adds no decoder, renderer or automatic file writing. It follows the documented core comparison contract. Grouping uses constant call-stack space and a heap worklist; image dimensions/concurrency still require caller limits. External Gradle dependencies use reviewed SHA-256 verification; this is repository-byte verification, not a complete code or vulnerability audit. Kotlin assertion diagnostics include only caller context, dimensions, state and aggregate metrics. Keep private image contents out of manually saved/shared diagnostics.
