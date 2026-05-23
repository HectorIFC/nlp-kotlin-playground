## Description

<!-- What does this PR do? Why is it needed? -->

## Type of change

- [ ] `feat` — new feature
- [ ] `fix` — bug fix
- [ ] `refactor` — refactoring with no behavior change
- [ ] `test` — adding or fixing tests
- [ ] `docs` — documentation
- [ ] `build` — build / CI / Docker changes
- [ ] `style` — UI / CSS / formatting changes with no logic impact
- [ ] `chore` — maintenance

## User-visible changes

- [ ] No
- [ ] Yes — describe the change to behavior, UI, or HTTP API:

> Breaking changes to the HTTP API or Docker image contract require a major version bump.

## Checklist

- [ ] `./gradlew test` passes locally
- [ ] `./gradlew ktlintCheck` passes (no formatting violations)
- [ ] `./gradlew detekt` passes (no static analysis violations)
- [ ] `docker build .` succeeds and the resulting container responds `200` on `/health`
- [ ] `CHANGELOG.md` updated under `[Unreleased]`
- [ ] New public Kotlin symbols have KDoc; non-public symbols are `internal`

## Tests added / modified

<!-- List new or changed tests. -->

## Notes for the reviewer

<!-- Context, design decisions, known pitfalls. -->
