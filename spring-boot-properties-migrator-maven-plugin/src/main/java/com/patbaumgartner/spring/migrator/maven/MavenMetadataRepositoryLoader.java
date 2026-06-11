package com.patbaumgartner.spring.migrator.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


import org.eclipse.aether.artifact.Artifact;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;

final class MavenMetadataRepositoryLoader {

	private MavenMetadataRepositoryLoader() {
	}

	static ConfigurationMetadataRepository loadFromArtifacts(Collection<Artifact> artifacts) throws IOException {
		ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();
		for (Artifact artifact : artifacts) {
			File file = artifact.getFile();
			if (file == null || !file.getName().endsWith(".jar")) {
				continue;
			}
			addMetadataIfPresent(file, builder);
		}
		return builder.build();
	}

	private static void addMetadataIfPresent(File file, ConfigurationMetadataRepositoryJsonBuilder builder)
			throws IOException {
		try (ZipFile zipFile = new ZipFile(file)) {
			ZipEntry entry = zipFile.getEntry("META-INF/spring-configuration-metadata.json");
			if (entry == null) {
				return;
			}
			try (InputStream in = zipFile.getInputStream(entry)) {
				builder.withJsonResource(in);
			}
		}
	}

}
