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
	void analyzeTaskCreatesReportAndDoesNotMutateProperties() throws Exception {
		Path projectDir = createSampleProject("server.max-http-header-size=16KB\n", false);

		BuildResult result = GradleRunner.create()
			.withProjectDir(projectDir.toFile())
			.withArguments("springBootPropertiesMigratorAnalyze", "--stacktrace")
			.withPluginClasspath()
			.build();

		String report = Files.readString(projectDir.resolve("build/reports/migration.txt"), StandardCharsets.UTF_8);
		String appProperties = Files.readString(projectDir.resolve("src/main/resources/application.properties"),
				StandardCharsets.UTF_8);

		assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
		assertThat(report).contains("Renamed keys:").contains("server.max-http-header-size");
		assertThat(appProperties).contains("server.max-http-header-size=16KB");
	}

	@Test
	void migrateTaskRewritesDeprecatedProperty() throws Exception {
		Path projectDir = createSampleProject("server.max-http-header-size=16KB\n", false);

		BuildResult result = GradleRunner.create()
			.withProjectDir(projectDir.toFile())
			.withArguments("springBootPropertiesMigrate", "--stacktrace")
			.withPluginClasspath()
			.build();

		String appProperties = Files.readString(projectDir.resolve("src/main/resources/application.properties"),
				StandardCharsets.UTF_8);

		assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
		assertThat(appProperties).contains("server.max-http-request-header-size=16KB");
	}

	@Test
	void migrateTaskRespectsDryRunFlag() throws Exception {
		Path projectDir = createSampleProject("server.max-http-header-size=16KB\n", true);

		BuildResult result = GradleRunner.create()
			.withProjectDir(projectDir.toFile())
			.withArguments("springBootPropertiesMigrate", "--stacktrace")
			.withPluginClasspath()
			.build();

		String appProperties = Files.readString(projectDir.resolve("src/main/resources/application.properties"),
				StandardCharsets.UTF_8);

		assertThat(result.getOutput()).contains("BUILD SUCCESSFUL");
		assertThat(appProperties).contains("server.max-http-header-size=16KB");
	}

	private Path createSampleProject(String propertiesContent, boolean dryRun) throws Exception {
		Path projectDir = this.tempDir.resolve("project-" + System.nanoTime());
		Files.createDirectories(projectDir);
		Files.writeString(projectDir.resolve("settings.gradle"), "rootProject.name='sample-gradle-app'\n",
				StandardCharsets.UTF_8);

		String buildFile = """
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
				    dryRun = %s
				}
				""".formatted(dryRun);
		Files.writeString(projectDir.resolve("build.gradle"), buildFile, StandardCharsets.UTF_8);

		Path propertiesFile = projectDir.resolve("src/main/resources/application.properties");
		Files.createDirectories(propertiesFile.getParent());
		Files.writeString(propertiesFile, propertiesContent, StandardCharsets.UTF_8);
		return projectDir;
	}

}
