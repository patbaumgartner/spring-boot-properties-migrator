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
		Path propertiesFile = this.tempDir.resolve("src/main/resources/application.properties");
		Files.createDirectories(propertiesFile.getParent());
		Files.writeString(propertiesFile, "legacy.key=value\n", StandardCharsets.UTF_8);

		Path metadataJar = createMetadataJar("legacy.key", "modern.key", "Renamed");

		AnalyzeMojo mojo = new AnalyzeMojo();
		mojo.project = createProject(this.tempDir, metadataJar);
		mojo.includes = List.of("src/main/resources/application.properties");
		mojo.reportFile = "target/migration-report.txt";

		mojo.execute();

		String report = Files.readString(this.tempDir.resolve("target/migration-report.txt"), StandardCharsets.UTF_8);
		String fileContent = Files.readString(propertiesFile, StandardCharsets.UTF_8);

		assertThat(report).contains("Renamed keys: 1").contains("legacy.key -> modern.key");
		assertThat(fileContent).contains("legacy.key=value");
	}

	@Test
	void analyzeFailsWhenUnsupportedPropertiesFoundAndFailOnErrorEnabled() throws Exception {
		Path propertiesFile = this.tempDir.resolve("src/main/resources/application.properties");
		Files.createDirectories(propertiesFile.getParent());
		Files.writeString(propertiesFile, "legacy.unsupported=value\n", StandardCharsets.UTF_8);

		Path metadataJar = createMetadataJar("legacy.unsupported", null, "No longer supported");

		AnalyzeMojo mojo = new AnalyzeMojo();
		mojo.project = createProject(this.tempDir, metadataJar);
		mojo.includes = List.of("src/main/resources/application.properties");
		mojo.failOnError = true;

		assertThatThrownBy(mojo::execute).isInstanceOf(MojoExecutionException.class)
			.hasMessageContaining("Unsupported deprecated properties found: 1");
	}

	private static MavenProject createProject(Path basedir, Path metadataJar) {
		MavenProject project = new MavenProject();
		project.setFile(basedir.resolve("pom.xml").toFile());
		project.setArtifacts(Set.of(createArtifact(metadataJar)));
		return project;
	}

	private static Artifact createArtifact(Path jarFile) {
		Artifact artifact = new DefaultArtifact("org.springframework.boot", "spring-boot-autoconfigure", "4.1.0",
				"compile", "jar", null, new DefaultArtifactHandler("jar"));
		artifact.setFile(jarFile.toFile());
		return artifact;
	}

	private Path createMetadataJar(String key, String replacement, String reason) throws IOException {
		String deprecation = replacement == null ? "\"deprecation\": { \"reason\": \"" + reason + "\" }"
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
