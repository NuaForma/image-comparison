# Exact library release reviews

No release is approved by this directory or by successful tests. The first planned version is 5.0.0; no 5.0.0 approval record exists yet. Add `<version>.json` only after explicit human review of the exact source and all eight artifacts.

1. Commit the final release version/source/artifact inputs on reviewed main; fix source commit S.
2. Prepare/test S and collect exact POM, binary, sources and documentation SHA-256 values for both modules using the protected pinned-JDK job.
3. With separate tag authorization, create an annotated SSH-signed `v<version>` tag on S, verify it locally and require GitHub verified=true/reason=valid after its explicitly authorized push. Never move/reuse a tag.
4. Add the human review record in a later reviewed administration-only commit on main. Do not change artifact inputs/notices/version in that commit. S must be an ancestor of main, not its HEAD.
5. Upload and publish need their own separate authorization. USER_MANAGED upload is already external distribution.

Schema (illustrative only; placeholders/false approval are deliberately not accepted):

```json
{
  "repository": "NuaForma/image-comparison",
  "version": "5.0.0",
  "sourceCommit": "<exact 40-character tagged-source commit>",
  "signingFingerprint": "74317C6CE9C61B80A2A1C0B40F736E8D598CB08B",
  "approved": false,
  "reviewer": "mrhoeve",
  "reviewedAt": "YYYY-MM-DD",
  "artifactHashes": {
    "dev/nuaforma/image-comparison/5.0.0/image-comparison-5.0.0.pom": "<reviewed SHA256>"
  }
}
```

`artifactHashes` must contain exactly eight full Maven-layout paths: `.pom`, `.jar`, `-sources.jar`, `-javadoc.jar` for image-comparison and image-comparison-kotlin. No executable review scripts, private keys, credentials, artifact binaries or automatic approval generators belong here. See [the release guide](../docs/maven-central-releases.md).
