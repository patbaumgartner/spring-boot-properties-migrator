package com.patbaumgartner.spring.migrator.gradle;

import java.io.File;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GradleSpringBootVersionDetectorTests {

	@Test
	void usesOverrideWhenProvided() {
		Optional<String> version = GradleSpringBootVersionDetector.detect(Set.of(), "4.1.0");

		assertThat(version).contains("4.1.0");
	}

	@Test
	void detectsVersionFromJarNames() {
		Set<File> jars = new LinkedHashSet<>();
		jars.add(new File("spring-boot-autoconfigure-4.0.2.jar"));
		jars.add(new File("spring-boot-4.1.0.jar"));

		Optional<String> version = GradleSpringBootVersionDetector.detect(jars, null);

		assertThat(version).contains("4.1.0");
	}

	@Test
	void returnsEmptyWhenNoBootJarNameMatches() {
		Optional<String> version = GradleSpringBootVersionDetector.detect(Set.of(new File("other-lib-1.0.jar")), null);

		assertThat(version).isEmpty();
	}

}
