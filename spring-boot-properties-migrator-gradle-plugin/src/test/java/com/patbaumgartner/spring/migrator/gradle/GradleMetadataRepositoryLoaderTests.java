package com.patbaumgartner.spring.migrator.gradle;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;

import static org.assertj.core.api.Assertions.assertThat;

class GradleMetadataRepositoryLoaderTests {

	@TempDir
	Path tempDir;

	@Test
	void loadsSpringMetadataFromJar() throws IOException {
		Path jar = createJarWithMetadata("""
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

		Set<java.io.File> jars = new LinkedHashSet<>();
		jars.add(jar.toFile());

		ConfigurationMetadataRepository repository = GradleMetadataRepositoryLoader.loadFromArtifacts(jars);

		assertThat(repository.getAllProperties()).containsKey("legacy.key");
		assertThat(repository.getAllProperties().get("legacy.key").isDeprecated()).isTrue();
	}

	@Test
	void ignoresJarWithoutMetadata() throws IOException {
		Path jar = this.tempDir.resolve("empty.jar");
		try (OutputStream out = Files.newOutputStream(jar); JarOutputStream jout = new JarOutputStream(out)) {
			jout.putNextEntry(new JarEntry("META-INF/"));
			jout.closeEntry();
		}

		ConfigurationMetadataRepository repository = GradleMetadataRepositoryLoader
			.loadFromArtifacts(Set.of(jar.toFile()));

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
