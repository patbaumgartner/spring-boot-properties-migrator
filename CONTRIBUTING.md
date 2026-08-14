# Contributing

Thanks for your interest in contributing.

## Development Setup

Prerequisites:

- Java 17
- Maven 3.9+
- Gradle 8.0+ (the repository ships wrappers)

## Versioning

The project uses a single version source for both Maven and Gradle:

- `.mvn/maven.config` with `-Drevision=...`
- `spring-boot-properties-migrator-gradle-plugin/gradle.properties` for Gradle plugin dependency/plugin versions
- `samples/spring-boot-3.5-gradle-sample/gradle.properties` for sample Gradle dependency versions

When bumping versions, update only `revision` in `.mvn/maven.config`.
Maven modules and the Gradle plugin read that same value automatically.
The released version comes from the git tag, including for JReleaser.
For Gradle dependency/plugin version bumps, update the relevant `gradle.properties` file(s).

Build everything:

```bash
# Maven modules, plus the Maven sample regression test
./mvnw -B -ntp install -pl spring-boot-properties-migrator-maven-plugin -am
./samples/spring-boot-3.5-maven-sample/mvnw -B -ntp \
  -f samples/spring-boot-3.5-maven-sample/pom.xml test

# Gradle plugin (unit + TestKit functional tests), plus the Gradle sample
./spring-boot-properties-migrator-gradle-plugin/gradlew \
  -p spring-boot-properties-migrator-gradle-plugin build
./samples/spring-boot-3.5-gradle-sample/gradlew \
  -p samples/spring-boot-3.5-gradle-sample test
```

## Code Style

The project uses [spring-javaformat](https://github.com/spring-io/spring-javaformat), and
the build **verifies** rather than applies it. Fix violations with:

```bash
./mvnw io.spring.javaformat:spring-javaformat-maven-plugin:apply
./spring-boot-properties-migrator-gradle-plugin/gradlew \
  -p spring-boot-properties-migrator-gradle-plugin format
```

## Architecture

`spring-boot-properties-migrator-core` holds everything that decides *what* to change:

| Type | Responsibility |
|---|---|
| `PropertyFileScanner` | Finds candidate configuration files |
| `PropertiesKeyParser` / `YamlKeyParser` | Locate each key and its exact source span |
| `DeprecationCatalog` | Relaxed-binding lookup, replacement chains, value types |
| `MigrationEngine` | Decides per key, then plans or applies span edits |
| `FailurePolicy` | Turns a plan into a build outcome |

The Maven and Gradle modules are thin adapters: they resolve the classpath, call
`plan()`, render the report, and call `apply()`. Behaviour changes belong in core, where
they are tested once and shared by both plugins.

The Gradle plugin compiles the core sources directly (see `sourceSets` in its
`build.gradle`) so the published plugin is self-contained and can never drift from the
core version it was built against.

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
