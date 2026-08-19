# Spring Boot Properties Migrator

[![Build](https://github.com/patbaumgartner/spring-boot-properties-migrator/actions/workflows/build.yml/badge.svg)](https://github.com/patbaumgartner/spring-boot-properties-migrator/actions/workflows/build.yml)
[![Maven Central](https://img.shields.io/maven-central/v/com.patbaumgartner/spring-boot-properties-migrator-maven-plugin.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/com.patbaumgartner/spring-boot-properties-migrator-maven-plugin)
[![Gradle Plugin Portal](https://img.shields.io/gradle-plugin-portal/v/com.patbaumgartner.spring-boot-properties-migrator)](https://plugins.gradle.org/plugin/com.patbaumgartner.spring-boot-properties-migrator)
[![License](https://img.shields.io/github/license/patbaumgartner/spring-boot-properties-migrator)](LICENSE)
[![Java](https://img.shields.io/badge/Java-17%2B-007396)](#prerequisites)

Maven and Gradle plugins that find deprecated Spring Boot configuration properties in your
`application.properties` and `application.yml` files, and rename the ones that can be
renamed safely.

Spring Boot ships a machine-readable description of every configuration property it knows,
including which ones are deprecated and what replaced them. These plugins read that
metadata straight from the jars your project already resolves, so the answers always match
the Spring Boot version you are actually on.

## What Makes This Safe

Renaming a property key is not always a safe edit, and a migration tool that pretends
otherwise will quietly break your application. Measured against Spring Boot 3.5's own
metadata, of the 188 deprecated properties that name a replacement:

- **73 %** move the key to a different parent, so a nested YAML key cannot simply be
  renamed in place.
- **29 %** change the value type. `server.use-forward-headers` is a boolean, but
  `server.forward-headers-strategy` is an enum, so carrying `true` across produces
  configuration that no longer binds.
- **7 %** collapse several old keys onto one replacement, so a blind rename creates
  duplicate keys and silently drops values.

This tool therefore **only rewrites a key when it can prove the edit is safe**, and reports
everything else with the exact reason and target key. It never guesses.

The same restraint applies in the other direction. Opt in with `unknownKeys` and it will
also point out keys that no metadata describes at all — the ones a deprecation scan is
structurally blind to, because a property Spring Boot deleted outright leaves nothing
behind to match against. Those are only ever reported, never removed: absence from the
metadata does not prove that nothing reads the key.

It also never reformats your files. Documents are parsed for structure and only the
character span of a key is replaced, so comments, indentation, quoting style, CRLF line
endings, a byte-order mark and a missing trailing newline all survive untouched. A
migration shows up in `git diff` as the keys that changed, and nothing else.

## Prerequisites

- Java 17 or later
- Maven 3.9+ or Gradle 8.0+

## Maven Plugin

```xml
<plugin>
  <groupId>com.patbaumgartner</groupId>
  <artifactId>spring-boot-properties-migrator-maven-plugin</artifactId>
  <version>0.3.0</version>
</plugin>
```

Report what would change:

```bash
./mvnw spring-boot-properties-migrator:analyze
```

Apply the safe renames:

```bash
./mvnw spring-boot-properties-migrator:migrate
```

Every parameter can also be set from the command line:

```bash
./mvnw spring-boot-properties-migrator:migrate -Dspring-boot-properties-migrator.dryRun=true
./mvnw spring-boot-properties-migrator:analyze -Dspring-boot-properties-migrator.failOn=any
./mvnw verify -Dspring-boot-properties-migrator.skip=true
```

To fail a build while any deprecated property remains, bind `analyze` to a phase:

```xml
<plugin>
  <groupId>com.patbaumgartner</groupId>
  <artifactId>spring-boot-properties-migrator-maven-plugin</artifactId>
  <version>0.3.0</version>
  <configuration>
    <failOn>any</failOn>
    <reportFile>target/reports/spring-boot-migration-report.txt</reportFile>
  </configuration>
  <executions>
    <execution>
      <goals>
        <goal>analyze</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

## Gradle Plugin

```kotlin
plugins {
    id("com.patbaumgartner.spring-boot-properties-migrator") version "0.3.0"
}

springBootPropertiesMigrator {
    failOn = "any"
    reportFile = "build/reports/spring-boot-migration-report.txt"
}
```

```bash
./gradlew springBootPropertiesMigratorAnalyze
./gradlew springBootPropertiesMigratorMigrate
```

Both tasks work with the Gradle configuration cache. Settings can also be overridden per
task:

```kotlin
tasks.named<MigrateTask>("springBootPropertiesMigratorMigrate") {
    includes.set(listOf("src/main/resources/application.yml"))
}
```

## Example Report

```text
Spring Boot Properties Migration (applied)
Scanned 1 file against 304 deprecated properties (Spring Boot 3.5.16)

Migrated (2)
  src/main/resources/application.properties:2  server.max-http-header-size -> server.max-http-request-header-size
  src/main/resources/application.properties:8  spring.codec.max-in-memory-size -> spring.http.codecs.max-in-memory-size

Needs manual action (1)
  src/main/resources/application.properties:5  server.use-forward-headers -> server.forward-headers-strategy
      why: the replacement holds a ForwardHeadersStrategy where this property held a Boolean, so the current value must be converted by hand
      reason: Replaced to support additional strategies.

Summary: 2 migrated, 1 manual, 0 without replacement
```

With `unknownKeys` switched on, an extra advisory section is appended:

```text
Not found in resolved metadata (1)
  src/main/resources/application.properties:11  server.servlet.context-pathh
      why: no property or aggregate ancestor of this name exists in the configuration metadata on the resolved project classpath, so it may have been removed or misspelled, or it may be read by code that publishes no metadata; nothing was changed

Summary: 2 migrated, 1 manual, 0 without replacement, 1 not found in metadata
```

## Configuration Reference

| Name | Type | Default | Description |
|---|---|---|---|
| `includes` | list | see below | Glob patterns, relative to the project directory |
| `failOn` | string | `never` | `never`, `manual` or `any` |
| `reportFile` | string | none | Optional file to write the report to |
| `springBootVersion` | string | detected | Version shown in the report |
| `dryRun` | boolean | `false` | Migrate task/goal only: report without writing |
| `unknownKeys` | string | `off` | `off`, `report` or `fail`: flag keys no metadata describes |
| `unknownKeyIncludes` | list | Spring Boot's own | Namespaces to inspect for unrecognised keys |
| `unknownKeyExcludes` | list | none | Exact keys or prefixes never to report |
| `skip` | boolean | `false` | Maven only: skip the goal entirely |

Default `includes` cover `application.properties`, `application.yml`, `application.yaml`
and their `application-*` profile variants under both `src/main/resources` and
`src/test/resources`.

`failOn` decides when the build fails:

- `never` — report only.
- `manual` — fail when a finding needs a human, either because migrating it is not
  provably safe or because the property has no replacement.
- `any` — fail while any deprecated property is present. Useful as a CI gate.

The policy is evaluated **before** anything is written, so a failing build never leaves
some files migrated and others not.

`springBootVersion` only changes the version shown in the report. Metadata is always read
from the resolved project classpath.

`unknownKeys` is evaluated separately from `failOn`, and never influences it. An
unrecognised key is an *absence of evidence*, not a deprecation the metadata proved, so it
must not break a build configured to fail only on real deprecations.

## When a Key Is Migrated Automatically

A key is rewritten only when all of the following hold:

- The metadata names a replacement, and following any chain of replacements terminates.
- The value type is not known to change.
- The replacement key does not already exist in the same file.
- No other key in the file maps to the same replacement.
- For a nested YAML key, the replacement still belongs under the same parent.

Everything else is reported under **Needs manual action** with the target key and the
reason, so you can make the change deliberately.

Chained deprecations are followed to their final replacement, so running a migration twice
changes nothing the second time.

## YAML Support

YAML is parsed rather than pattern matched, which means:

- Block scalars are left alone. Text inside `|` or `>` that happens to look like a key is
  not a key and is never rewritten.
- Flow mappings (`server: {max-http-header-size: 16KB}`) and quoted keys are found.
- Multi-document files separated by `---` are handled document by document.
- Both nested (`server:` / `  max-http-header-size:`) and flat dotted
  (`server.max-http-header-size:`) styles are supported.

Files that use anchors or aliases, declare the same key twice, or fail to parse are
analysed but never modified, because a single key edit in those files can change more than
the key it appears to change.

## Relaxed Binding

Spring Boot accepts several spellings of the same property, and so does this tool.
`server.max-http-header-size`, `server.maxHttpHeaderSize` and
`server.max_http_header_size` are all recognised as the same property, and the replacement
is written in canonical kebab-case.

Environment-variable style (`SERVER_MAX_HTTP_HEADER_SIZE`) is deliberately *not* treated as
equivalent, because underscores separate path elements only for the system environment
property source, never inside a configuration file.

## Which Deprecations Are Detected

Metadata is read from the jars your project resolves, so the plugins know exactly the
deprecations your current Spring Boot version still describes. That makes them useful for
cleaning up before an upgrade, and for catching properties a dependency has deprecated.

A property that a newer Spring Boot removed *and* dropped from its metadata is not a
deprecation any more — nothing on the classpath describes it, so there is no rename to
apply. Run the migration **before** bumping Spring Boot, or once per intermediate version,
so each version's deprecations are drained while its metadata still documents them.

To catch what is left behind after an upgrade, switch on the unrecognised-key check:

```xml
<unknownKeys>report</unknownKeys>
```

```groovy
springBootPropertiesMigrator { unknownKeys = 'report' }
```

It reports keys that no metadata on the resolved classpath describes, and it **never
rewrites or removes them**. Absence from the metadata does not prove the key is dead: it
may be read by your own code, by a library that publishes no metadata, or by a module
whose annotation processor did not run. Deleting it automatically could silently change
behaviour, so the check is advisory by design and off by default.

To keep it quiet enough to trust, it deliberately under-reports:

- only namespaces Spring Boot owns (`spring`, `server`, `management`, `logging`), and only
  when the resolved metadata proves it knows that namespace — an absent Actuator means
  `management.*` is skipped rather than condemned;
- map entries and indexed list elements are resolved against the aggregate property that
  declares them, so `logging.level.com.example` and `clients[0].timeout` stay quiet;
- keys nested below a type the metadata never decomposes are left alone;
- YAML structural parents are ignored — only keys that carry a value are judged;
- keys built from placeholders such as `${...}` are skipped;
- bootstrap keys Spring Boot reads outside property binding (`spring.config.*`,
  `spring.profiles.active`, `spring.application.name`, `spring.autoconfigure.exclude`, …)
  are allowlisted.

Use `unknownKeyExcludes` for anything left over, and `unknownKeyIncludes` to point the
check at a namespace of your own.

If no configuration metadata is found at all, the report says so rather than claiming the
project is clean.

## Samples

- `samples/spring-boot-3.5-maven-sample`
- `samples/spring-boot-3.5-gradle-sample`

Both use real Spring Boot 3.5 properties and assert all three behaviours: the safe renames
are applied, the enum-typed `server.forward-headers-strategy` rename is reported instead of
breaking the value, and — with `unknownKeys = report` — a key no metadata describes
(`server.servlet.context-pathh`, a typo) is reported without being rewritten or removed.

## Upgrading from 0.1.x

- `failOnError` (boolean) is replaced by `failOn` (`never`, `manual`, `any`).
  `failOnError=true` corresponds to `failOn=manual`.
- The Gradle task `springBootPropertiesMigrate` is now
  `springBootPropertiesMigratorMigrate`. The old name still works as an alias.
- Maven parameters are bound under the `spring-boot-properties-migrator.` prefix, so
  `-DdryRun=true` becomes `-Dspring-boot-properties-migrator.dryRun=true`. It previously
  had no effect at all.
- Renames whose value type changes are now reported instead of applied. If you relied on
  the old behaviour, it was producing configuration that could not bind.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Security reports: [SECURITY.md](SECURITY.md).

## License

Apache License 2.0. See [LICENSE](LICENSE).
