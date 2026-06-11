package com.patbaumgartner.spring.migrator.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class PropertyFileScanner {

	private PropertyFileScanner() {
	}

	public static List<Path> scan(Path rootDir, List<String> includes) throws IOException {
		List<PathMatcher> matchers = includes.stream()
			.map(pattern -> rootDir.getFileSystem().getPathMatcher("glob:" + pattern))
			.toList();

		List<Path> files = new ArrayList<>();
		FileVisitor<Path> visitor = new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
				Path relative = rootDir.relativize(file);
				for (PathMatcher matcher : matchers) {
					if (matcher.matches(relative)) {
						files.add(file);
						break;
					}
				}
				return FileVisitResult.CONTINUE;
			}
		};

		Files.walkFileTree(rootDir, visitor);
		files.sort(Comparator.comparing(Path::toString));
		return files;
	}

	public static List<String> defaultIncludes() {
		return List.of("src/main/resources/application.properties", "src/main/resources/application-*.properties",
				"src/main/resources/application.yml", "src/main/resources/application.yaml",
				"src/main/resources/application-*.yml", "src/main/resources/application-*.yaml",
				"src/test/resources/application.properties", "src/test/resources/application-*.properties",
				"src/test/resources/application.yml", "src/test/resources/application.yaml",
				"src/test/resources/application-*.yml", "src/test/resources/application-*.yaml");
	}

}
