# NuaForma modifications

This fork starts at upstream v4.4.0 (`188adc076324ed481300f72af627d13f5bb92ee1`). The original LICENSE and comparison algorithm implementations are unchanged. All production and test Java files have been relocated from `com.github.romankh3.image.comparison` to `dev.nuaforma.image.comparison`, with package/import edits and prominent modification notices.

Changes for the unreleased maintenance baseline (2026-10-25):

- `pom.xml`: independent project metadata, Java 17 API/bytecode compilation, Java 17 test compilation, pinned build/test tooling, deterministic archive timestamps and bundled attribution.
- `README.md`, `CONTRIBUTING.md`, `.github/pull_request_template.md`: independent fork identity, build instructions and contribution/review process.
- `.github/ISSUE_TEMPLATE/*.md`: remove automatic assignment to the upstream author.
- `CODE_OF_CONDUCT.md`: route fork incidents to fork maintainers instead of the upstream author.
- `.gitignore`: ignore local agent settings and generated output.
- `src/test/java/dev/nuaforma/image/comparison/ImageComparisonUnitTest.java` and `ImageComparisonUtilUnitTest.java`: identify modified tests and use Maven output paths. The save-failure test uses a real invalid parent path rather than relying on obsolete mocking behavior.
- Retired the duplicate Gradle build/launcher/wrapper, Travis workflow, old Gradle GitHub workflow and upstream funding configuration. They are recoverable in upstream history; obsolete publishing/service integrations are not used by this fork.
- Added Maven wrapper, CI, consumer/bytecode verification, maintenance/security/license review guides, changelog, agent entry point and this attribution record.

No comparison algorithms or test image fixtures were changed. The Java package relocation changes fully qualified API names and the automatic module name; Java 17 is the new minimum. Both are intentional compatibility breaks. New repository files are NuaForma maintenance additions unless they carry an Apache wrapper header. Future changes must update this record and mark modified upstream files clearly.
