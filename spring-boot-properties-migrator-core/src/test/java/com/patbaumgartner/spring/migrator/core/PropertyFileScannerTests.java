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
		Path included = touch("src/main/resources/application.properties");
		touch("src/main/resources/other.properties");

		PropertyFileScanner.ScanResult result = PropertyFileScanner.scan(this.tempDir,
				List.of("src/main/resources/application.properties"));

		assertThat(result.files()).containsExactly(included);
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	void matchesWildcardPatterns() throws Exception {
		Path dev = touch("src/main/resources/application-dev.yml");
		touch("src/main/resources/application-dev.txt");

		PropertyFileScanner.ScanResult result = PropertyFileScanner.scan(this.tempDir,
				List.of("src/main/resources/application-*.yml"));

		assertThat(result.files()).containsExactly(dev);
	}

	@Test
	void skipsGeneratedDirectoriesWhenPatternsAreRooted() throws Exception {
		Path source = touch("src/main/resources/application.properties");
		touch("target/classes/application.properties");
		touch("build/classes/application.properties");
		touch("node_modules/pkg/application.properties");

		PropertyFileScanner.ScanResult result = PropertyFileScanner.scan(this.tempDir,
				List.of("**/application.properties"));

		assertThat(result.files()).containsExactly(source);
	}

	@Test
	void stillScansAGeneratedDirectoryWhenItIsNamedExplicitly() throws Exception {
		Path fixture = touch("build/migration-fixture/application.properties");

		PropertyFileScanner.ScanResult result = PropertyFileScanner.scan(this.tempDir,
				List.of("build/migration-fixture/application.properties"));

		assertThat(result.files()).containsExactly(fixture);
	}

	@Test
	void returnsNoFilesWhenTheProjectHasNoResources() {
		PropertyFileScanner.ScanResult result = PropertyFileScanner.scan(this.tempDir,
				PropertyFileScanner.defaultIncludes());

		assertThat(result.files()).isEmpty();
		assertThat(result.warnings()).isEmpty();
	}

	@Test
	void reportsEachMatchingFileOnlyOnce() throws Exception {
		Path file = touch("src/main/resources/application.properties");

		PropertyFileScanner.ScanResult result = PropertyFileScanner.scan(this.tempDir,
				List.of("src/main/resources/application.properties", "**/application.properties"));

		assertThat(result.files()).containsExactly(file);
	}

	@Test
	void defaultIncludesCoverMainAndTestResources() {
		List<String> includes = PropertyFileScanner.defaultIncludes();

		assertThat(includes).contains("src/main/resources/application.properties")
			.contains("src/test/resources/application.properties")
			.contains("src/main/resources/application-*.yml");
	}

	private Path touch(String relativePath) throws Exception {
		Path file = this.tempDir.resolve(relativePath);
		Files.createDirectories(file.getParent());
		Files.writeString(file, "a=b\n", StandardCharsets.UTF_8);
		return file;
	}

}
