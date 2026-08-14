package com.patbaumgartner.spring.migrator.gradle;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out which Spring Boot version a project resolves, by reading the version out of
 * the Spring Boot jars on its classpath.
 */
final class GradleSpringBootVersionDetector {

	private static final Pattern BOOT_JAR = Pattern
		.compile("spring-boot-[a-z0-9-]*?([0-9]+\\.[0-9]+\\.[0-9]+(?:[-A-Za-z0-9.]*)?)\\.jar");

	private static final Pattern NUMERIC = Pattern.compile("[0-9]+");

	private GradleSpringBootVersionDetector() {
	}

	static Optional<String> detect(Collection<Path> classpath, String override) {
		if (override != null && !override.isBlank()) {
			return Optional.of(override);
		}
		return classpath.stream()
			.map((entry) -> entry.getFileName().toString())
			.map(BOOT_JAR::matcher)
			.filter(Matcher::matches)
			.map((matcher) -> matcher.group(1))
			.max(GradleSpringBootVersionDetector::compareVersions);
	}

	/**
	 * Compares two version strings numerically, so that 3.10.0 ranks above 3.9.0 rather
	 * than below it as string ordering would have it.
	 * @param left the first version
	 * @param right the second version
	 * @return the comparison result
	 */
	private static int compareVersions(String left, String right) {
		String[] leftParts = left.split("[._-]");
		String[] rightParts = right.split("[._-]");
		for (int i = 0; i < Math.max(leftParts.length, rightParts.length); i++) {
			String leftPart = (i < leftParts.length) ? leftParts[i] : "";
			String rightPart = (i < rightParts.length) ? rightParts[i] : "";
			int comparison = comparePart(leftPart, rightPart);
			if (comparison != 0) {
				return comparison;
			}
		}
		return 0;
	}

	private static int comparePart(String left, String right) {
		if (NUMERIC.matcher(left).matches() && NUMERIC.matcher(right).matches()) {
			return Long.compare(Long.parseLong(left), Long.parseLong(right));
		}
		// A release (no qualifier) outranks any pre-release qualifier of the same
		// version.
		if (left.isEmpty() != right.isEmpty()) {
			return left.isEmpty() ? 1 : -1;
		}
		return Comparator.<String>naturalOrder().compare(left, right);
	}

}
