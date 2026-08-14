package com.patbaumgartner.spring.migrator.gradle;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GradleSpringBootVersionDetectorTests {

	@Test
	void usesOverrideWhenProvided() {
		assertThat(GradleSpringBootVersionDetector.detect(List.of(), "4.1.0")).contains("4.1.0");
	}

	@Test
	void detectsVersionFromJarNames() {
		Optional<String> version = GradleSpringBootVersionDetector
			.detect(List.of(Path.of("spring-boot-autoconfigure-4.0.2.jar"), Path.of("spring-boot-4.1.0.jar")), null);

		assertThat(version).contains("4.1.0");
	}

	@Test
	void comparesVersionsNumericallyNotAlphabetically() {
		Optional<String> version = GradleSpringBootVersionDetector
			.detect(List.of(Path.of("spring-boot-3.9.0.jar"), Path.of("spring-boot-3.10.0.jar")), null);

		assertThat(version).contains("3.10.0");
	}

	@Test
	void prefersAReleaseOverAPreReleaseOfTheSameVersion() {
		Optional<String> version = GradleSpringBootVersionDetector
			.detect(List.of(Path.of("spring-boot-4.1.0-RC1.jar"), Path.of("spring-boot-4.1.0.jar")), null);

		assertThat(version).contains("4.1.0");
	}

	@Test
	void returnsEmptyWhenNoBootJarMatches() {
		assertThat(GradleSpringBootVersionDetector.detect(List.of(Path.of("other-lib-1.0.jar")), null)).isEmpty();
	}

}
