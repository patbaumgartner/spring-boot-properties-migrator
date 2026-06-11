# Contributing

Thanks for your interest in contributing.

## Development Setup

Prerequisites:

- Java 17
- Maven 3.9+
- Gradle 9.5.1+

## Versioning

The project uses a single version source for both Maven and Gradle:

- `.mvn/maven.config` with `-Drevision=...`
- `spring-boot-properties-migrator-gradle-plugin/gradle.properties` for Gradle plugin dependency/plugin versions
- `samples/spring-boot-3.5-gradle-sample/gradle.properties` for sample Gradle dependency versions

When bumping versions, update only `revision` in `.mvn/maven.config`.
Maven modules and the Gradle plugin read that same value automatically.
For Gradle dependency/plugin version bumps, update the relevant `gradle.properties` file(s).

Build everything:

```bash
./mvnw -B -ntp verify -pl spring-boot-properties-migrator-maven-plugin -am
cd spring-boot-properties-migrator-gradle-plugin
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 ./gradlew build functionalTest
```

## Pull Requests

- Keep PRs focused and small
- Add or update tests for behavior changes
- Update docs for user-visible changes
- Ensure CI is green

## Commit Style

Conventional commits are preferred:

- feat: add support for ...
- fix: handle ...
- docs: update ...

## Local Release Dry-Run

```bash
jreleaser full-release --dry-run
```

## Release Automation Script

This repository includes `scripts/release.sh` to automate dependency updates,
release preparation, tagging, and next-iteration setup.

Common commands:

```bash
# Run Maven + Gradle update/cleanup pipeline and regression tests
./scripts/release.sh update-deps

# Same as above, then create a dependency-update commit automatically
./scripts/release.sh update-deps --commit

# Prepare release commit (sets versions, runs regressions, commits)
./scripts/release.sh prepare-release 0.1.0

# Prepare release commit + create tag (add --push to push commit/tag)
./scripts/release.sh release 0.1.0

# Prepare next development iteration
./scripts/release.sh prepare-next 0.2.0-SNAPSHOT
```

Commit behavior:

- `update-deps` commits only with `--commit`.
- `prepare-release`, `release`, and `prepare-next` auto-commit all resulting changes.

Optional environment variables:

- `JAVA_HOME`: used for Gradle commands.
- `GRADLE_VERSION`: if set, updates both Gradle wrappers during `update-deps`.
