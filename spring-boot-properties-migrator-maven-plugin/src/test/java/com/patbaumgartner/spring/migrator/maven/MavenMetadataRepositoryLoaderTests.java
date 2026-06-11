package com.patbaumgartner.spring.migrator.maven;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;


import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;

import static org.assertj.core.api.Assertions.assertThat;

class MavenMetadataRepositoryLoaderTests {

	@TempDir
	Path tempDir;

	@Test
	void loadsSpringConfigurationMetadataFromJar() throws Exception {
		Path metadataJar = createJarWithMetadata("""
				{
				  "properties": [
				    {
				      "name": "legacy.key",
				      "type": "java.lang.String",
				      "deprecated": true,
				      "deprecation": {
				        "replacement": "modern.key",
				        "reason": "Renamed"
				      }
				    }
				  ]
				}
				""");

		Artifact artifact = new DefaultArtifact("org.springframework.boot", "spring-boot-autoconfigure", "jar", "4.1.0")
			.setFile(metadataJar.toFile());

		ConfigurationMetadataRepository repository = MavenMetadataRepositoryLoader.loadFromArtifacts(List.of(artifact));

		assertThat(repository.getAllProperties()).containsKey("legacy.key");
		assertThat(repository.getAllProperties().get("legacy.key").isDeprecated()).isTrue();
		assertThat(repository.getAllProperties().get("legacy.key").getDeprecation().getReplacement())
			.isEqualTo("modern.key");
	}

	@Test
	void ignoresJarsWithoutMetadata() throws Exception {
		Path emptyJar = this.tempDir.resolve("empty.jar");
		try (OutputStream out = Files.newOutputStream(emptyJar); JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/"));
			jar.closeEntry();
		}

		Artifact artifact = new DefaultArtifact("org.example", "empty", "jar", "1.0.0").setFile(emptyJar.toFile());

		ConfigurationMetadataRepository repository = MavenMetadataRepositoryLoader.loadFromArtifacts(List.of(artifact));

		assertThat(repository.getAllProperties()).isEmpty();
	}

	private Path createJarWithMetadata(String metadataJson) throws IOException {
		Path jarPath = this.tempDir.resolve("metadata.jar");
		try (OutputStream out = Files.newOutputStream(jarPath); JarOutputStream jar = new JarOutputStream(out)) {
			jar.putNextEntry(new JarEntry("META-INF/spring-configuration-metadata.json"));
			jar.write(metadataJson.getBytes(StandardCharsets.UTF_8));
			jar.closeEntry();
		}
		return jarPath;
	}

}
