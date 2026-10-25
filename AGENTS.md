# Agent instructions

Maintain a generic image comparison library. Read README.md, CONTRIBUTING.md, docs/maintenance.md and applicable local instructions before editing. Preserve existing work.

- Keep production Java 17 APIs/bytecode and `dev.nuaforma.image.comparison` packages. Build/test JDK requirements are separate.
- Use the checked-in Gradle wrapper; run the full build, archive and independent consumer checks for build/source changes. Never hide failing or undiscovered tests.
- Preserve Apache-2.0, original authorship, NOTICE and MODIFICATIONS.md; mark modified upstream files.
- Avoid runtime dependencies without a concrete reviewed need. Review versions, primary sources, licenses and security before tooling/dependency changes.
- Branch from develop using `codex/<fork-issue>-<topic>` and target develop; main is the release baseline. Use English Conventional Commits with a real fork issue reference, explicit `git commit -S`, local verification and GitHub verified/valid verification after push.
- Do not merge, publish artifacts/tags/releases or contact other people without explicit user authorization. Do not rewrite published history without authorization.
- Never store secrets in repository files or logs. Keep workstation-specific instructions in ignored AGENTS.local.md.
