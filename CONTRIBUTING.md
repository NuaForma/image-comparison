<!-- Modified by NuaForma: independent fork contribution process. See MODIFICATIONS.md. -->
# Contributing

Use [this fork's issues](https://github.com/NuaForma/image-comparison/issues), searching existing issues first. Include a goal, explicit scope, prerequisites and testable acceptance criteria. Report bugs with the commit/version, JDK, dimensions, relevant options and the smallest synthetic reproducer. Do not upload private images or secrets. Use SECURITY.md for vulnerability reports.

Discuss API or behavior changes before implementation. Maintenance priorities are correct pixel comparison, reliable difference markings, exclusions, performance and memory use. PDF rendering and product integration belong elsewhere. Open upstream PRs are evidence to review, not a backlog to import automatically.

Branch from `develop` as `codex/<fork-issue>-<topic>` and target `develop`. Keep changes focused. Preserve Java 17 APIs/bytecode, the `dev.nuaforma.image.comparison` namespace, original author credit and Apache-2.0. Mark modifications to upstream files and update MODIFICATIONS.md. Review primary release/license/security information before changing dependencies; do not add runtime dependencies without a concrete need.

Run `./mvnw --batch-mode --no-transfer-progress clean verify` on JDK 17 and 25, the artifact/consumer checks from README.md, and relevant synthetic regression tests. Keep all inherited tests enabled; no-test builds must fail. Record real commands/results and untested boundaries. Never regenerate expected image fixtures just to hide a regression; explain the authoring process and review all changed pixels.

Commit explicitly with `git commit -S`, using English Conventional Commits (`build: ...`, `fix: ...`, `docs: ...`, `ci: ...`) and `Refs #N` for a real issue in this fork. Verify signatures locally and require GitHub `verified=true`, `reason=valid` after pushing. Never fall back to unsigned commits or rewrite published history without explicit permission.

Use the PR template. Explain behavior, compatibility, security/license and documentation impact, tests and limits. Wait for current-head Java 17 and 25 CI and review. Do not merge without maintainer authorization. Authorized merges use normal signed merge commits, preserving individual commits. See docs/maintenance.md for release promotions; publication always needs separate authorization.
