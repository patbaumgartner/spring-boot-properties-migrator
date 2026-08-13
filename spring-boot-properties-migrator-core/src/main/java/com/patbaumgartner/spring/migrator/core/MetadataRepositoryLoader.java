package com.patbaumgartner.spring.migrator.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepositoryJsonBuilder;

/**
 * Collects Spring Boot configuration metadata from a resolved classpath.
 * <p>
 * Both jars and exploded class directories are supported, so metadata produced by the
 * project itself or by a sibling module that has not been packaged yet is not lost.
 * Entries are read in a stable order so that the same classpath always yields the same
 * result, whichever build tool assembled it.
 */
public final class MetadataRepositoryLoader {

	private static final String METADATA_ENTRY = "META-INF/spring-configuration-metadata.json";

	private MetadataRepositoryLoader() {
	}

	/**
	 * Reads the configuration metadata contained in the given classpath entries.
	 * @param classpathEntries jars and directories making up the project classpath
	 * @return the merged metadata repository
	 * @throws IOException if a readable entry contains malformed metadata
	 */
	public static ConfigurationMetadataRepository load(Collection<Path> classpathEntries) throws IOException {
		ConfigurationMetadataRepositoryJsonBuilder builder = ConfigurationMetadataRepositoryJsonBuilder
			.create(StandardCharsets.UTF_8);
		List<Path> ordered = new ArrayList<>(classpathEntries);
		ordered.sort(Comparator.comparing(Path::toString));
		for (Path entry : ordered) {
			if (Files.isDirectory(entry)) {
				addFromDirectory(entry, builder);
			}
			else if (Files.isRegularFile(entry)) {
				addFromArchive(entry, builder);
			}
		}
		return builder.build();
	}

	private static void addFromDirectory(Path directory, ConfigurationMetadataRepositoryJsonBuilder builder)
			throws IOException {
		Path metadata = directory.resolve(METADATA_ENTRY);
		if (!Files.isRegularFile(metadata)) {
			return;
		}
		try (InputStream in = Files.newInputStream(metadata)) {
			builder.withJsonResource(in);
		}
	}

	private static void addFromArchive(Path archive, ConfigurationMetadataRepositoryJsonBuilder builder)
			throws IOException {
		try (ZipFile zip = new ZipFile(archive.toFile())) {
			ZipEntry entry = zip.getEntry(METADATA_ENTRY);
			if (entry == null) {
				return;
			}
			try (InputStream in = zip.getInputStream(entry)) {
				builder.withJsonResource(in);
			}
		}
		catch (ZipException ex) {
			// A classpath entry that is not a readable archive simply carries no
			// metadata; that must not abort the whole run.
		}
	}

}
