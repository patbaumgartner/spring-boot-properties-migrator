package com.patbaumgartner.spring.migrator.gradle;

import java.io.File;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GradleSpringBootVersionDetector {

	private static final Pattern BOOT_VERSION_PATTERN = Pattern
		.compile("spring-boot-[a-z0-9-]*?([0-9]+\\.[0-9]+\\.[0-9]+(?:[-A-Za-z0-9.]*)?)\\.jar");

	private GradleSpringBootVersionDetector() {
	}

	static Optional<String> detect(Set<File> jars, String override) {
		if (override != null && !override.isBlank()) {
			return Optional.of(override);
		}

		return jars.stream()
			.map(File::getName)
			.map(BOOT_VERSION_PATTERN::matcher)
			.filter(Matcher::matches)
			.map((matcher) -> matcher.group(1))
			.max(Comparator.naturalOrder());
	}

}
