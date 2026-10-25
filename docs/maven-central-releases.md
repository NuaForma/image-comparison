# Maven Central release preparation

The first release candidate is **5.0.0**, explicitly selected by the maintainer and fixed in this source; the future source tag is `v5.0.0`. This setup creates no tag, GitHub release, Central deployment or publication. It follows NuaForma's parameterized release approach for exactly two libraries, without its server/Playground/container gates.

## Coordinates and local publications

- `dev.nuaforma:image-comparison`: Java 17 library, no external runtime dependencies.
- `dev.nuaforma:image-comparison-kotlin`: optional Kotlin DSL; declaring it alone resolves core, pinned Kotlin stdlib and required annotations transitively.

Both publications include POM, binary, sources and documentation JARs: **eight reviewed artifacts**. Every JAR retains LICENSE/NOTICE/MODIFICATIONS. Kotlin sources are explicitly registered with the Maven publication, not merely assembled locally. Checks verify dependency scopes/versions, no test-runtime leaks, Java-17 class files, embedded POM bytes, metadata, attribution and all four checksums. Snapshot preparation resolves timestamped local Maven metadata; immutable bundles reject snapshots.

```sh
./gradlew --no-daemon clean build verifyJavaConsumer verifyGroupingConsumer verifyLibraryPublication
python3 tools/release/consumers.py --repository build/verification-repository --version 5.0.0 --output build/release-consumers
python3 -m unittest discover -s tools/release
python3 -O -m unittest discover -s tools/release
python3 tools/release/local_signing_qa.py --output build/signing-qa-fresh
```

These commands require JDK 17 for release consumers, Python 3, GnuPG and Maven (tested locally with the existing Maven 3.9.6; the pinned Apache dependency plugin 3.11.0 requires Maven 3.6.3 or newer). Normal builds use the existing Gradle wrapper/Kotlin compiler pins and need no signing secrets. `-PsignArtifacts=true` is explicit and fails without both signing values; malformed flags fail. `-PreleaseVersion=0.0.0-local.1` is used only for local synthetic QA, not an approval or version publication.

The synthetic-key test creates a short-lived, passphrase-protected disposable identity in a private temporary GnuPG home, keeps its armored export only in process memory, passes it to Gradle's in-memory signer and removes the synthetic key/agent afterward. No maintainer keyring or organization credential is read. It verifies all eight signatures and rejects altered bytes, missing/invalid signatures, wrong identity, expired/revoked keys, snapshots and bundle overwrite. Its known synthetic passphrase and temporary test keyring are test data, not real release credentials. Build outputs stay ignored; use a fresh output path on reruns. These records explicitly are not release approval.

Maven uses an isolated candidate-specific cache, explicit credential-free settings and a pinned dependency plugin; javac compiles a standalone Java consumer that calls the Kotlin API through one adapter dependency. Gradle independently compiles Kotlin through both module metadata and POM-only resolution. Both graphs must match the exact candidate JARs and the externally reviewed stdlib/annotation hashes. No source-project substitution is allowed. Build-tool transitive dependencies are not claimed to have received a full vulnerability/source audit.

## Repository-specific environment and organization secrets

Create/configure image-comparison's own `maven-central` environment before any real workflow dispatch. Organization secrets do not copy environment protection. Required settings match NuaForma:

- Sole required reviewer: user `mrhoeve`.
- Prevent self-review: disabled while there is one human maintainer.
- Administrator bypass: disabled.
- Deployment branch rules: custom, exactly one rule of type branch named `main`; no tags, wildcards or other branches.

The implementation reads these settings and refuses weaker/missing/unavailable metadata. It does not change existing security settings. Initial inspection on 25 October 2026 found no environments and no repository secrets. Following the maintainer's request for the same NuaForma configuration, the new maven-central environment was created with the exact required reviewer/no-bypass/main-only rules and verified through the API. No existing environment, branch protection or secret was changed. Configuration is available under the repository's Settings / Environments; verify the actual JSON, not just the environment name in YAML. Anonymous GitHub metadata access is used for this public project, as in NuaForma; 403/404/rate limits fail closed. No claim is made that contents:read GITHUB_TOKEN can inspect environment/secret metadata.

The maintainer has configured these organization secrets for NuaForma and image-comparison:

| Organization secret | Needed step |
| --- | --- |
| MAVEN_SIGNING_KEY | Local signing only; armored passphrase-protected OpenPGP private key |
| MAVEN_SIGNING_PASSWORD | Local signing only; corresponding passphrase |
| MAVEN_CENTRAL_USERNAME | Separately authorized Portal operation; token username, not account login |
| MAVEN_CENTRAL_PASSWORD | Separately authorized Portal operation; token password |

1Password remains the source of real values. Do not retrieve them to write configuration, create duplicate secrets, alter NuaForma's repository secrets, print them, write private-key exports into temporary files or include them in logs/caches/artifacts. The real signer consumes the two signing variables only in its step and Gradle memory; secret-bearing Gradle output is captured without being printed or retained. No signing material is passed in command arguments. Public-only signature verification runs in a separate temporary GnuPG home.

Before every future real credential test, use an existing authorized maintainer account to run:

```sh
python3 tools/release/check_secret_sources.py --output build/release-review/secret-source-check.json
```

It reads only metadata/names: verify all four organization names are available to this public repository, and no same-named repository or maven-central environment secrets override them. It creates no token/secret/settings. If that account cannot inspect metadata, perform the same names/visibility review in GitHub's authorized UI; do not assume a runtime token has these permissions or request new secrets. Workflow dispatch requires explicit confirmation of this just-completed preflight; it is a human assertion, not an automatic proof of metadata rights. The current authorized CLI account could inspect repository/environment secret names, but could not inspect organization secret metadata; organization visibility therefore remains an explicit maintainer preflight before any real dispatch. No actual value correctness or credential availability has been tested by local QA.

The approved **public primary** Maven signing fingerprint is `74317C6CE9C61B80A2A1C0B40F736E8D598CB08B`. Protected jobs fetch its public certificate from keyserver.ubuntu.com and verify identity/expiry/revocation before private-key use. Every detached signature must validate against that primary identity, including subkey linkage, modern SHA-2 digest and expiry/revocation status. Missing/altered/malformed signatures and a different primary key fail. Git SSH commit/tag signing remains separate.

## Manual preparation, no external distribution

`Prepare Maven Central release (no upload)` starts only by workflow_dispatch on reviewed `main` in this repository, behind the protected environment. First verify actual policy and the names-only preflight; then build/test unsigned libraries and run independent consumers. Fetch/check the expected public certificate, map only the two signing secrets to the local signing step, then independently verify all eight signatures. No Central token is available to preparation.

Only non-executable JSON source/hash/consumer records are retained as Actions artifacts. No JAR, ZIP bundle, private key, public-key file, cache, raw signing output or review script is uploaded as an Actions artifact. Source commit and source-tree cleanliness are recorded; every generated record has releaseApproval=false. Snapshot preparation is allowed as a configuration test and cannot become an immutable release bundle.

The release toolchain is pinned to Temurin 17.0.20+1, observed in the successful project CI, with the checked Gradle wrapper. Preparation and later rebuild use that same pin. Other Java/vendor/patch combinations can change documentation bytes; exact review hashes fail closed on any difference and must never be updated merely to make a publish pass. Normal CI still checks Java 17 and 25 and unchanged Java-25-built artifacts on Java 17.

The workflows must first reach main through the normal reviewed promotion process. Their existence on develop does not authorize or trigger execution on main. No push, merge or tag automatically starts a release job.

## Source, signed tag and human review order

1. Review final source/artifact inputs on main, set `version=5.0.0` only for the explicitly approved candidate and fix exact source commit S. Run protected preparation on S and review all eight POM/binary/source/doc hashes, compatibility, attribution, dependency findings and standalone consumers. Disposable signing does not prove the real key/password pair or Portal-token validity.
2. With separate tag authorization, create an annotated SSH-signed tag on S using `git tag -s v5.0.0 <S> -m '<English release description>'`. Verify locally with trusted allowed-signers and `git verify-tag`; after its explicitly authorized push require GitHub verified=true and reason=valid. Never move/reuse a version tag. Maven signatures use OpenPGP, not this SSH identity.
3. Add `release-approvals/5.0.0.json` in a later reviewed administration-only commit/PR on main. It must bind repository, explicit version, S, approved human reviewer/date, Maven fingerprint and exactly eight SHA256 paths. Do not change version, source, notices or any artifact input in that administration commit. S is an ancestor of main, not necessarily current HEAD. This avoids a self-referential commit hash. See [the record schema](../release-approvals/README.md).
4. Before private secret use, the upload job validates current main policy, the record and the immutable annotated tag/live reference/signature/main ancestry/source version. It copies the reviewed record and current policy helpers to RUNNER_TEMP, then detaches to S and builds/signs exactly S. Validators remain from the current main policy; artifact attribution is checked against tagged source.

## Separately authorized external upload

`Upload reviewed release to Central (USER_MANAGED)` requires an explicit immutable version, fresh secret-source preflight, the exact typed UPLOAD_ONCE confirmation and environment approval. It rechecks that all eight public coordinates are unused, verifies the exact review bytes/signatures/checksums and prepares a canonical 48-entry Maven-layout ZIP locally. It refuses extra/duplicate/path-escaping entries, snapshots, unsafe files, noncanonical/hidden data, unreviewed bytes or dirty source. Only then is the Portal token mapped into the upload step.

One POST uploads in USER_MANAGED mode for validation; this is already external distribution, even though publication does not happen automatically. The record keeps deployment UUID/version/source/bundle hash with published=false. No automatic retry occurs. A timeout, malformed response, cancellation or record-write failure can follow a successful external action: inspect the Portal and recover its identity rather than blindly rerunning. Credentials use Base64(tokenUsername:tokenPassword) as Bearer; redirects with credentials are refused.

## Separately authorized publication and public verification

`Publish validated release on Central` requires the explicit version, exact existing deployment UUID, PUBLISH_ONCE confirmation, main policy/tag/review checks, names-only preflight and environment approval. It requires VALIDATED and exact module/version purls. It independently downloads all 48 staged files: hashes every reviewed base artifact, verifies all four checksums and all eight OpenPGP signatures against the expected current public certificate, then sends one publish request. It rejects expired/revoked/wrong keys, missing/invalid signatures and changed artifacts/checksums. A 204 only records publishRequested=true and published=false. Already PUBLISHING/PUBLISHED states must not cause another publish request.

`Verify published release (read only)` is a separate manual job: require PUBLISHED for that exact identity/version, anonymously retrieve all artifacts/signatures/checksums from normal Maven Central URLs, verify exact reviewed hashes/public identity and then run independent Maven and Gradle consumers against the public coordinates. Portal credentials are scoped only to the Portal-status/download check; consumer processes run in a separate step without them. Public success and actual consumer usability must be observed before announcing availability or separately creating a GitHub release for the existing tag.

## Current verification limits and remaining manual actions

Local positive/negative tests use disposable identities and an offline Portal transport, not real credentials or staging. The first actual protected preparation is a separately authorized key/password-sharing test after environment setup and main promotion. It will not validate the Portal token; a later explicitly authorized Portal operation is separate. Names-only metadata cannot prove secret values are correct. No real tag/release, Portal upload, USER_MANAGED staging, publish request or NuaForma change has been performed by this implementation.

Primary references: [Central requirements](https://central.sonatype.org/publish/requirements/), [Portal API and staged downloads](https://central.sonatype.org/publish/publish-portal-api/), [token authentication](https://central.sonatype.org/publish/generate-portal-token/), [GitHub environments](https://docs.github.com/en/actions/deployment/targeting-different-environments/managing-environments-for-deployment), [Apache dependency plugin](https://maven.apache.org/plugins/maven-dependency-plugin/plugin-info.html). The NuaForma generic workflow/helper design was reviewed and its chat explicitly consulted; historical central-alpha-* workflows were not copied.
