package com.patbaumgartner.spring.migrator.gradle;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.gradle.testkit.runner.BuildResult;
import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class GradlePluginFunctionalTests {

	@TempDir
	Path tempDir;

	@Test
	void analyzeReportsWithoutMutatingProperties() throws Exception {
		Path projectDir = sampleProject("");

		BuildResult result = run(projectDir, "springBootPropertiesMigratorAnalyze");

		assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
		assertThat(report(projectDir)).contains("server.max-http-header-size -> server.max-http-request-header-size");
		assertThat(properties(projectDir)).contains("server.max-http-header-size=16KB");
	}

	@Test
	void migrateRewritesTheDeprecatedProperty() throws Exception {
		Path projectDir = sampleProject("");

		run(projectDir, "springBootPropertiesMigratorMigrate");

		assertThat(properties(projectDir)).contains("server.max-http-request-header-size=16KB");
	}

	@Test
	void migrateHonoursDryRun() throws Exception {
		Path projectDir = sampleProject("dryRun = true");

		run(projectDir, "springBootPropertiesMigratorMigrate");

		assertThat(properties(projectDir)).contains("server.max-http-header-size=16KB");
	}

	@Test
	void tasksAreCompatibleWithTheConfigurationCache() throws Exception {
		Path projectDir = sampleProject("");

		BuildResult first = run(projectDir, "springBootPropertiesMigratorAnalyze", "--configuration-cache");
		assertThat(first.getOutput()).contains("BUILD SUCCESSFUL");

		BuildResult second = run(projectDir, "springBootPropertiesMigratorAnalyze", "--configuration-cache");
		assertThat(second.getOutput()).contains("BUILD SUCCESSFUL").contains("Configuration cache entry reused");
	}

	@Test
	void migrateIsIdempotent() throws Exception {
		Path projectDir = sampleProject("");

		run(projectDir, "springBootPropertiesMigratorMigrate");
		String afterFirst = properties(projectDir);
		run(projectDir, "springBootPropertiesMigratorMigrate");

		assertThat(properties(projectDir)).isEqualTo(afterFirst);
	}

	@Test
	void failOnAnyFailsTheBuildAndLeavesFilesAlone() throws Exception {
		Path projectDir = sampleProject("failOn = 'any'");

		BuildResult result = GradleRunner.create()
			.withProjectDir(projectDir.toFile())
			.withArguments("springBootPropertiesMigratorMigrate", "--stacktrace")
			.withPluginClasspath()
			.buildAndFail();

		assertThat(result.getOutput()).contains("deprecated propert");
		assertThat(properties(projectDir)).contains("server.max-http-header-size=16KB");
	}

	@Test
	void rejectsAnUnknownFailOnValue() throws Exception {
		Path projectDir = sampleProject("failOn = 'sometimes'");

		BuildResult result = GradleRunner.create()
			.withProjectDir(projectDir.toFile())
			.withArguments("springBootPropertiesMigratorAnalyze")
			.withPluginClasspath()
			.buildAndFail();

		assertThat(result.getOutput()).contains("Unknown failOn value 'sometimes'");
	}

	@Test
	void keepsThePreviousMigrateTaskNameWorking() throws Exception {
		Path projectDir = sampleProject("");

		BuildResult result = run(projectDir, "springBootPropertiesMigrate");

		assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
		assertThat(properties(projectDir)).contains("server.max-http-request-header-size=16KB");
	}

	private BuildResult run(Path projectDir, String... arguments) {
		String[] withStacktrace = new String[arguments.length + 1];
		System.arraycopy(arguments, 0, withStacktrace, 0, arguments.length);
		withStacktrace[arguments.length] = "--stacktrace";
		return GradleRunner.create()
			.withProjectDir(projectDir.toFile())
			.withArguments(withStacktrace)
			.withPluginClasspath()
			.build();
	}

	private String properties(Path projectDir) throws Exception {
		return Files.readString(projectDir.resolve("src/main/resources/application.properties"),
				StandardCharsets.UTF_8);
	}

	private String report(Path projectDir) throws Exception {
		return Files.readString(projectDir.resolve("build/reports/migration.txt"), StandardCharsets.UTF_8);
	}

	private Path sampleProject(String extraConfiguration) throws Exception {
		Path projectDir = this.tempDir.resolve("project-" + System.nanoTime());
		Files.createDirectories(projectDir);
		Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name='sample-gradle-app'\n",
				StandardCharsets.UTF_8);

		Files.writeString(projectDir.resolve("build.gradle"), """
				plugins {
				    id 'java'
				    id 'com.patbaumgartner.spring-boot-properties-migrator'
				}

				repositories {
				    mavenCentral()
				}

				dependencies {
				    implementation 'org.springframework.boot:spring-boot-autoconfigure:3.5.0'
				}

				springBootPropertiesMigrator {
				    reportFile = 'build/reports/migration.txt'
				    %s
				}
				""".formatted(extraConfiguration), StandardCharsets.UTF_8);

		Path propertiesFile = projectDir.resolve("src/main/resources/application.properties");
		Files.createDirectories(propertiesFile.getParent());
		Files.writeString(propertiesFile, "server.max-http-header-size=16KB\n", StandardCharsets.UTF_8);
		return projectDir;
	}

}
