package com.patbaumgartner.spring.migrator.core;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds the configuration files a migration should look at.
 * <p>
 * Rather than walking the whole project and filtering afterwards, the scanner derives the
 * directories it needs to visit from the literal prefix of each include pattern. A
 * default run therefore touches only {@code src/main/resources} and
 * {@code src/test/resources} instead of descending into {@code .git}, {@code target} or
 * {@code node_modules}.
 */
public final class PropertyFileScanner {

	private static final Set<String> GENERATED_DIRECTORIES = Set.of(".git", ".gradle", ".idea", ".mvn", ".svn", "bin",
			"build", "node_modules", "out", "target");

	private static final String WILDCARDS = "*?[{";

	private PropertyFileScanner() {
	}

	/**
	 * Scans a project for files matching the given glob patterns.
	 * @param rootDir the project root that patterns are relative to
	 * @param includes glob patterns relative to the project root
	 * @return the matching files and any directories that could not be read
	 */
	public static ScanResult scan(Path rootDir, List<String> includes) {
		List<PathMatcher> matchers = includes.stream()
			.map((pattern) -> rootDir.getFileSystem().getPathMatcher("glob:" + pattern))
			.toList();

		Set<Path> files = new LinkedHashSet<>();
		List<String> warnings = new ArrayList<>();
		for (Path searchRoot : searchRoots(rootDir, includes)) {
			walk(rootDir, searchRoot, matchers, files, warnings);
		}

		List<Path> sorted = new ArrayList<>(files);
		sorted.sort(Comparator.comparing(Path::toString));
		return new ScanResult(List.copyOf(sorted), List.copyOf(warnings));
	}

	private static void walk(Path rootDir, Path searchRoot, List<PathMatcher> matchers, Set<Path> files,
			List<String> warnings) {
		if (!Files.isDirectory(searchRoot)) {
			return;
		}
		try {
			Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {

				@Override
				public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
					boolean generated = !directory.equals(searchRoot)
							&& GENERATED_DIRECTORIES.contains(directory.getFileName().toString());
					return generated ? FileVisitResult.SKIP_SUBTREE : FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
					Path relative = rootDir.relativize(file);
					if (matchers.stream().anyMatch((matcher) -> matcher.matches(relative))) {
						files.add(file);
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFileFailed(Path file, IOException failure) {
					warnings.add("Could not read " + file + ": " + failure.getMessage());
					return FileVisitResult.CONTINUE;
				}

			});
		}
		catch (IOException ex) {
			warnings.add("Could not scan " + searchRoot + ": " + ex.getMessage());
		}
	}

	/**
	 * Returns the directories that must be walked to satisfy the given patterns, dropping
	 * any root already covered by a shallower one.
	 * @param rootDir the project root
	 * @param includes the glob patterns
	 * @return the directories to walk
	 */
	private static List<Path> searchRoots(Path rootDir, List<String> includes) {
		Set<String> prefixes = new LinkedHashSet<>();
		for (String include : includes) {
			prefixes.add(literalPrefix(include));
		}
		if (prefixes.contains("")) {
			return List.of(rootDir);
		}
		List<String> ordered = new ArrayList<>(prefixes);
		ordered.sort(Comparator.comparingInt(String::length));

		List<String> roots = new ArrayList<>();
		for (String candidate : ordered) {
			boolean covered = roots.stream()
				.anyMatch((root) -> candidate.equals(root) || candidate.startsWith(root + "/"));
			if (!covered) {
				roots.add(candidate);
			}
		}
		return roots.stream().map(rootDir::resolve).toList();
	}

	/**
	 * Returns the leading directory path of a glob pattern that contains no wildcard.
	 * @param pattern the glob pattern
	 * @return the literal directory prefix, which is empty when the pattern starts with a
	 * wildcard
	 */
	private static String literalPrefix(String pattern) {
		String[] segments = pattern.split("/");
		StringBuilder prefix = new StringBuilder();
		for (int i = 0; i < segments.length - 1; i++) {
			if (containsWildcard(segments[i])) {
				break;
			}
			if (!prefix.isEmpty()) {
				prefix.append('/');
			}
			prefix.append(segments[i]);
		}
		return prefix.toString();
	}

	private static boolean containsWildcard(String segment) {
		for (int i = 0; i < segment.length(); i++) {
			if (WILDCARDS.indexOf(segment.charAt(i)) >= 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns the default patterns, covering Spring Boot's conventional configuration
	 * file locations for main and test resources.
	 * @return the default include patterns
	 */
	public static List<String> defaultIncludes() {
		return List.of("src/main/resources/application.properties", "src/main/resources/application-*.properties",
				"src/main/resources/application.yml", "src/main/resources/application.yaml",
				"src/main/resources/application-*.yml", "src/main/resources/application-*.yaml",
				"src/test/resources/application.properties", "src/test/resources/application-*.properties",
				"src/test/resources/application.yml", "src/test/resources/application.yaml",
				"src/test/resources/application-*.yml", "src/test/resources/application-*.yaml");
	}

	/**
	 * The files a scan found, together with anything that stopped it from looking.
	 *
	 * @param files the matching files, in a stable order
	 * @param warnings paths that could not be read
	 */
	public record ScanResult(List<Path> files, List<String> warnings) {
	}

}
