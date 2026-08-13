package com.patbaumgartner.spring.migrator.maven;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.DefaultArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MavenMojoExecutionTests {

	@TempDir
	Path tempDir;

	@Test
	void analyzeWritesReportWithoutChangingFile() throws Exception {
		Path properties = writeProperties("legacy.key=value\n");
		AnalyzeMojo mojo = analyzeMojo(metadataJar("legacy.key", "modern.key", "Renamed"));
		mojo.reportFile = "target/migration-report.txt";

		mojo.execute();

		assertThat(Files.readString(this.tempDir.resolve("target/migration-report.txt"), StandardCharsets.UTF_8))
			.contains("legacy.key -> modern.key")
			.contains("Renamed");
		assertThat(Files.readString(properties, StandardCharsets.UTF_8)).isEqualTo("legacy.key=value\n");
	}

	@Test
	void migrateRewritesTheFile() throws Exception {
		Path properties = writeProperties("legacy.key=value\n");
		MigrateMojo mojo = migrateMojo(metadataJar("legacy.key", "modern.key", "Renamed"));

		mojo.execute();

		assertThat(Files.readString(properties, StandardCharsets.UTF_8)).isEqualTo("modern.key=value\n");
	}

	@Test
	void migrateHonoursDryRun() throws Exception {
		Path properties = writeProperties("legacy.key=value\n");
		MigrateMojo mojo = migrateMojo(metadataJar("legacy.key", "modern.key", "Renamed"));
		setDryRun(mojo, true);

		mojo.execute();

		assertThat(Files.readString(properties, StandardCharsets.UTF_8)).isEqualTo("legacy.key=value\n");
	}

	@Test
	void failOnAnyFailsEvenWhenEverythingCanBeMigrated() throws Exception {
		writeProperties("legacy.key=value\n");
		AnalyzeMojo mojo = analyzeMojo(metadataJar("legacy.key", "modern.key", "Renamed"));
		mojo.failOn = "any";

		assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
			.hasMessageContaining("1 deprecated property found");
	}

	@Test
	void failOnManualIgnoresKeysThatCanBeMigrated() throws Exception {
		writeProperties("legacy.key=value\n");
		AnalyzeMojo mojo = analyzeMojo(metadataJar("legacy.key", "modern.key", "Renamed"));
		mojo.failOn = "manual";

		mojo.execute();
	}

	@Test
	void failOnManualFailsForPropertiesWithoutReplacement() throws Exception {
		writeProperties("legacy.gone=value\n");
		AnalyzeMojo mojo = analyzeMojo(metadataJar("legacy.gone", null, "No longer supported"));
		mojo.failOn = "manual";

		assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class)
			.hasMessageContaining("no replacement");
	}

	@Test
	void migrateDoesNotWriteWhenThePolicyIsViolated() throws Exception {
		Path properties = writeProperties("legacy.key=value\n");
		MigrateMojo mojo = migrateMojo(metadataJar("legacy.key", "modern.key", "Renamed"));
		mojo.failOn = "any";

		assertThatThrownBy(mojo::execute).isInstanceOf(MojoFailureException.class);
		assertThat(Files.readString(properties, StandardCharsets.UTF_8)).isEqualTo("legacy.key=value\n");
	}

	@Test
	void rejectsAnUnknownFailOnValue() throws Exception {
		writeProperties("legacy.key=value\n");
		AnalyzeMojo mojo = analyzeMojo(metadataJar("legacy.key", "modern.key", "Renamed"));
		mojo.failOn = "sometimes";

		assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class)
			.hasMessageContaining("Unknown failOn value 'sometimes'");
	}

	@Test
	void skipLeavesEverythingAlone() throws Exception {
		Path properties = writeProperties("legacy.key=value\n");
		MigrateMojo mojo = migrateMojo(metadataJar("legacy.key", "modern.key", "Renamed"));
		mojo.skip = true;
		mojo.failOn = "any";

		mojo.execute();

		assertThat(Files.readString(properties, StandardCharsets.UTF_8)).isEqualTo("legacy.key=value\n");
	}

	private AnalyzeMojo analyzeMojo(Path metadataJar) {
		AnalyzeMojo mojo = new AnalyzeMojo();
		configure(mojo, metadataJar);
		return mojo;
	}

	private MigrateMojo migrateMojo(Path metadataJar) {
		MigrateMojo mojo = new MigrateMojo();
		configure(mojo, metadataJar);
		return mojo;
	}

	private void configure(AbstractMigratorMojo mojo, Path metadataJar) {
		MavenProject project = new MavenProject();
		project.setFile(this.tempDir.resolve("pom.xml").toFile());
		project.setArtifacts(Set.of(artifact(metadataJar)));
		mojo.project = project;
		mojo.includes = List.of("src/main/resources/application.properties");
		mojo.failOn = "never";
	}

	private static void setDryRun(MigrateMojo mojo, boolean dryRun) throws Exception {
		java.lang.reflect.Field field = MigrateMojo.class.getDeclaredField("dryRun");
		field.setAccessible(true);
		field.setBoolean(mojo, dryRun);
	}

	private static Artifact artifact(Path jarFile) {
		Artifact artifact = new DefaultArtifact("org.springframework.boot", "spring-boot-autoconfigure", "4.1.0",
				"compile", "jar", null, new DefaultArtifactHandler("jar"));
		artifact.setFile(jarFile.toFile());
		return artifact;
	}

	private Path writeProperties(String content) throws IOException {
		Path file = this.tempDir.resolve("src/main/resources/application.properties");
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	private Path metadataJar(String key, String replacement, String reason) throws IOException {
		String deprecation = (replacement == null) ? "\"deprecation\": { \"reason\": \"" + reason + "\" }"
				: "\"deprecation\": { \"replacement\": \"" + replacement + "\", \"reason\": \"" + reason + "\" }";
		String metadataJson = """
				{
				  "properties": [
				    {
				      "name": "%s",
				      "type": "java.lang.String",
				      "deprecated": true,
				      %s
				    }
				  ]
				}
				""".formatted(key, deprecation);

		Path jarPath = this.tempDir.resolve("metadata-%s.jar".formatted(key.replace('.', '-')));
		try (OutputStream out = Files.newOutputStream(jarPath); JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/spring-configuration-metadata.json"));
			jar.write(metadataJson.getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		}
		return jarPath;
	}

}
