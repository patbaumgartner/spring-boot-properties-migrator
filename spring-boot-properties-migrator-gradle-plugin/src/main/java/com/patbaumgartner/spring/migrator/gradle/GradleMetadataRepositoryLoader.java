package com.patbaumgartner.spring.migrator.gradle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;

final class GradleMetadataRepositoryLoader {

	private GradleMetadataRepositoryLoader() {
	}

	static ConfigurationMetadataRepository loadFromArtifacts(Set<File> jars) throws IOException {
		ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder.create();
		for (File jar : jars) {
			addMetadataIfPresent(jar, builder);
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
