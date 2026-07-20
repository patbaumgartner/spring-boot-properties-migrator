package com.patbaumgartner.spring.migrator.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class PropertyFileScannerTests {

	@TempDir
	Path tempDir;

	@Test
	void scansOnlyConfiguredPatterns() throws Exception {
		Path included = this.tempDir.resolve("src/main/resources/application.properties");
		Path excluded = this.tempDir.resolve("src/main/resources/other.properties");

		Files.createDirectories(included.getParent());
		Files.writeString(included, "a=b\n", StandardCharsets.UTF_8);
		Files.writeString(excluded, "x=y\n", StandardCharsets.UTF_8);

		List<Path> found = PropertyFileScanner.scan(this.tempDir, List.of("src/main/resources/application.properties"));

		assertThat(found).containsExactly(included);
	}

	@Test
	void defaultIncludesContainMainAndTestPatterns() {
		List<String> includes = PropertyFileScanner.defaultIncludes();

		assertThat(includes).contains("src/main/resources/application.properties");
		assertThat(includes).contains("src/test/resources/application.properties");
		assertThat(includes).contains("src/main/resources/application-*.yml");
	}

}
