package com.patbaumgartner.samples;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the plugin against real Spring Boot 3.5 metadata: renames that are safe are
 * applied, a rename whose value type changes is deliberately left alone, and a key the
 * metadata does not describe at all is reported without being touched.
 */
class GradleSampleMigrationTest {

	@Test
	void appliesSafeRenames() throws IOException {
		String content = migratedFixture();

		assertThat(content).contains("server.max-http-request-header-size=16KB")
			.contains("spring.http.codecs.max-in-memory-size=1MB");
		assertThat(content).doesNotContain("server.max-http-header-size=")
			.doesNotContain("spring.codec.max-in-memory-size=");
	}

	@Test
	void leavesRenamesThatWouldInvalidateTheValue() throws IOException {
		// server.use-forward-headers is a Boolean, server.forward-headers-strategy is an
		// enum. Renaming the key would leave "true", which no longer binds.
		assertThat(migratedFixture()).contains("server.use-forward-headers=true")
			.doesNotContain("server.forward-headers-strategy");
	}

	@Test
	void reportsTheManualChangeWithGuidance() throws IOException {
		Path report = Path.of("build", "reports", "migration-report-test-fixture.txt");
		assertThat(report).exists();

		assertThat(Files.readString(report)).contains("Needs manual action")
			.contains("server.use-forward-headers -> server.forward-headers-strategy")
			.contains("converted by hand");
	}

	@Test
	void reportsKeysTheMetadataDoesNotDescribeWithoutTouchingThem() throws IOException {
		// server.servlet.context-pathh is a typo, so no metadata describes it. The
		// deprecation scan is blind to it; only the unknownKeys check surfaces it.
		Path report = Path.of("build", "reports", "migration-report-test-fixture.txt");
		assertThat(Files.readString(report)).contains("Not found in resolved metadata")
			.contains("server.servlet.context-pathh");

		assertThat(migratedFixture()).contains("server.servlet.context-pathh=/demo");
	}

	@Test
	void preservesCommentsAndFormatting() throws IOException {
		assertThat(migratedFixture()).startsWith("# Deprecated Spring Boot 3.5 properties");
	}

	private static String migratedFixture() throws IOException {
		Path fixture = Path.of("build", "migration-fixture", "application.properties");
		assertThat(fixture).exists();
		return Files.readString(fixture);
	}

}
