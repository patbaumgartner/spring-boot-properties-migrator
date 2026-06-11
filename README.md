# Spring Boot Properties Migrator Plugins

[![Build](https://github.com/patbaumgartner/spring-boot-properties-migrator/actions/workflows/build.yml/badge.svg)](https://github.com/patbaumgartner/spring-boot-properties-migrator/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.patbaumgartner/spring-boot-properties-migrator-maven-plugin.svg?label=Maven%20Central)](https://search.maven.org/search?q=g:com.patbaumgartner%20AND%20a:spring-boot-properties-migrator-maven-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.patbaumgartner.spring-boot-properties-migrator)](https://plugins.gradle.org/plugin/com.patbaumgartner.spring-boot-properties-migrator)
[![License](https://img.shields.io/github/license/patbaumgartner/spring-boot-properties-migrator)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17-007396)](#prerequisites)

Automated migration support for deprecated Spring Boot configuration properties.

This repository provides:

- Shared core module: migration engine and report model used by both plugins
- Maven plugin: analyze + migrate goals
- Gradle plugin: analyze + migrate tasks
- Spring Boot version detection from project dependencies
- Dry-run report mode and in-place migration mode
- Comment-preserving line-based rewrites for `.properties`, `.yml`, `.yaml`

## Why This Exists

During Spring Boot upgrades (including 4.0 and 4.1), property keys can be renamed or removed.
These plugins detect those deprecated keys based on Spring Boot metadata and help you migrate safely.

## Prerequisites

- Java 17+
- Maven 3.9+ (or use the included Maven Wrapper)
- Gradle 9.5.1+ (or use the included Gradle Wrapper in `spring-boot-properties-migrator-gradle-plugin`)

## Maven Plugin

Coordinates:

- Group: `com.patbaumgartner`
- Artifact: `spring-boot-properties-migrator-maven-plugin`

### Configure

```xml
<build>
  <plugins>
    <plugin>
      <groupId>com.patbaumgartner</groupId>
      <artifactId>spring-boot-properties-migrator-maven-plugin</artifactId>
      <version>0.0.1-SNAPSHOT</version>
      <configuration>
        <failOnError>true</failOnError>
        <reportFile>target/reports/spring-boot-migration-report.txt</reportFile>
      </configuration>
    </plugin>
  </plugins>
</build>
```

### Run Dry-Run Analysis

```bash
./mvnw spring-boot-properties-migrator:analyze
```

### Run In-Place Migration

```bash
./mvnw spring-boot-properties-migrator:migrate
```

Optional dry-run through migrate goal:

```bash
./mvnw spring-boot-properties-migrator:migrate -DdryRun=true
```

## Gradle Plugin

Plugin ID:

- `com.patbaumgartner.spring-boot-properties-migrator`

### Configure (Kotlin DSL)

```kotlin
plugins {
    id("com.patbaumgartner.spring-boot-properties-migrator") version "0.0.1-SNAPSHOT"
}

springBootPropertiesMigrator {
    failOnError = true
    reportFile = "build/reports/spring-boot-migration-report.txt"
}
```

### Configure (Groovy DSL)

```groovy
plugins {
    id 'com.patbaumgartner.spring-boot-properties-migrator' version '0.0.1-SNAPSHOT'
}

springBootPropertiesMigrator {
    failOnError = true
    reportFile = 'build/reports/spring-boot-migration-report.txt'
}
```

### Run Dry-Run Analysis

```bash
./spring-boot-properties-migrator-gradle-plugin/gradlew springBootPropertiesMigratorAnalyze
```

### Run In-Place Migration

```bash
./spring-boot-properties-migrator-gradle-plugin/gradlew springBootPropertiesMigrate
```

## Samples (Spring Boot 3.5)

- Maven sample: `samples/spring-boot-3.5-maven-sample`
- Gradle sample: `samples/spring-boot-3.5-gradle-sample`

Both samples intentionally use the deprecated Spring Boot 3.5 property
`server.max-http-header-size` so you can verify migration to
`server.max-http-request-header-size`.

Run Maven sample analyze:

```bash
./mvnw -f samples/spring-boot-3.5-maven-sample/pom.xml \
  com.patbaumgartner:spring-boot-properties-migrator-maven-plugin:0.0.1-SNAPSHOT:analyze
```

Run Maven sample migrate:

```bash
./mvnw -f samples/spring-boot-3.5-maven-sample/pom.xml \
  com.patbaumgartner:spring-boot-properties-migrator-maven-plugin:0.0.1-SNAPSHOT:migrate
```

Run Gradle sample analyze:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
./spring-boot-properties-migrator-gradle-plugin/gradlew \
  -p samples/spring-boot-3.5-gradle-sample springBootPropertiesMigratorAnalyze
```

Run Gradle sample migrate:

```bash
JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
./spring-boot-properties-migrator-gradle-plugin/gradlew \
  -p samples/spring-boot-3.5-gradle-sample springBootPropertiesMigrate
```

## Default File Scan Patterns

- `src/main/resources/application.properties`
- `src/main/resources/application-*.properties`
- `src/main/resources/application.yml`
- `src/main/resources/application.yaml`
- `src/main/resources/application-*.yml`
- `src/main/resources/application-*.yaml`
- Same patterns under `src/test/resources`

## Configuration Reference

| Name | Type | Default | Description |
|---|---|---|---|
| `includes` | list | default scan patterns | File glob patterns relative to project root |
| `failOnError` | boolean | `false` | Fail build if unsupported deprecated keys are found |
| `reportFile` | string | none | Optional output file for report summary |
| `springBootVersion` | string | auto-detected | Explicit version override |
| `dryRun` | boolean | `false` | Apply only to migrate mode/task |

## Spring Boot Version Detection

The plugins detect Spring Boot version from:

- `spring-boot.version` property
- `spring-boot-starter-parent` version
- resolved Spring Boot artifacts on classpath

You can always override detection with `springBootVersion`.

## Example Dry-Run Output

```text
Spring Boot Properties Migration (dry-run)
Renamed keys: 2
Unsupported keys: 1

Renamed
- src/main/resources/application.properties:5  server.max.http.header.size -> server.max-http-request-header-size
- src/main/resources/application.yml:12  management.metrics.export.prometheus.enabled -> management.prometheus.metrics.export.enabled

Unsupported
- src/main/resources/application.properties:20  legacy.custom.setting (reason: This is no longer supported.)
```

## YAML and Comments

YAML rewrites are performed line-by-line to preserve comments and formatting where possible.

## Compatibility

- Java 17 baseline
- Spring Boot 4.0 and 4.1 targeted

## Release

Releases are automated with:

- GitHub Actions
- JReleaser
- Sonatype (Maven Central)
- Gradle Plugin Portal

## Roadmap

- Better support for complex YAML list structures
- Optional JSON report output
- Optional pull-request style patch output

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## Security

See [SECURITY.md](SECURITY.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
