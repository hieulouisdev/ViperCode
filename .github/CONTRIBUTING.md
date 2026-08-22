# Build etiquette (v0.0.9)

Please follow these conventions so the CI pipeline stays green and
the codebase stays shippable.

## Branches
- `main` is the release branch; it should always build a working APK.
- Feature branches: `feat/<short-name>` or `fix/<short-name>`.
- Long-lived branches should be rebased on `main` weekly.

## Commit messages
Use [Conventional Commits](https://www.conventionalcommits.org/):
```
feat(editor): add bracket pair colorizer
fix(ci): correct cache key prefix on cold start
chore(deps): bump compose-bom to 2024.12.01
docs(changelog): add v0.0.9 entry
```

## Pull requests
- One logical change per PR.
- Keep PRs under ~1000 lines where possible.
- Update `CHANGELOG.md` under `## [Unreleased]`.
- Don't bump `versionCode` / `versionName` in a PR — that's done by
  the maintainer as part of the release flow.

## Code style
- Kotlin via the official Android Studio defaults.
- 4-space indent, no tabs (the `gradlew` build will fail otherwise).
- Public API has KDoc (`/** ... */`).
- No `println` / `Log.d` left in committed code — use the
  `Timber`-equivalent stub (if added) or remove.

## Tests
- Unit tests live in `app/src/test/kotlin/...` mirroring the main source.
- Instrumented tests live in `app/src/androidTest/kotlin/...`.
- New utility functions should have at least one happy-path test.

## Releases
- A release is published by tagging `vX.Y.Z` and creating a GitHub Release.
- The `Build Release APK` workflow builds, signs and uploads the APK.
- `CHANGELOG.md` MUST be updated before tagging.
