package com.patbaumgartner.spring.migrator.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationEngineTests {

	@TempDir
	Path tempDir;

	@Test
	void migratesPropertiesAndKeepsComments() throws Exception {
		Path file = this.tempDir.resolve("src/main/resources/application.properties");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "# keep me\nold.key=value\n", StandardCharsets.UTF_8);

		MigrationEngine engine = new MigrationEngine();
		MigrationRunResult result = engine.run(this.tempDir, List.of(file),
				Map.of("old.key", new DeprecatedProperty("old.key", "new.key", "renamed")), true);

		String content = Files.readString(file, StandardCharsets.UTF_8);
		assertThat(content).contains("# keep me").contains("new.key=value");
		assertThat(result.getRenamed()).hasSize(1);
	}

	@Test
	void migratesYamlAndKeepsComments() throws Exception {
		Path file = this.tempDir.resolve("src/main/resources/application.yml");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "server:\n  # keep me\n  old: 8080\n", StandardCharsets.UTF_8);

		MigrationEngine engine = new MigrationEngine();
		MigrationRunResult result = engine.run(this.tempDir, List.of(file),
				Map.of("server.old", new DeprecatedProperty("server.old", "server.port", "renamed")), true);

		String content = Files.readString(file, StandardCharsets.UTF_8);
		assertThat(content).contains("# keep me").contains("port: 8080");
		assertThat(result.getRenamed()).hasSize(1);
	}

	@Test
	void dryRunDoesNotModifyPropertiesFile() throws Exception {
		Path file = this.tempDir.resolve("src/main/resources/application.properties");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "old.key=value\n", StandardCharsets.UTF_8);

		MigrationEngine engine = new MigrationEngine();
		MigrationRunResult result = engine.run(this.tempDir, List.of(file),
				Map.of("old.key", new DeprecatedProperty("old.key", "new.key", "renamed")), false);

		String content = Files.readString(file, StandardCharsets.UTF_8);
		assertThat(content).contains("old.key=value");
		assertThat(result.getRenamed()).hasSize(1);
	}

	@Test
	void reportsUnsupportedDeprecatedPropertyWithoutReplacement() throws Exception {
		Path file = this.tempDir.resolve("src/main/resources/application.properties");
		Files.createDirectories(file.getParent());
		Files.writeString(file, "legacy.unsupported=value\n", StandardCharsets.UTF_8);

		MigrationEngine engine = new MigrationEngine();
		MigrationRunResult result = engine.run(this.tempDir, List.of(file),
				Map.of("legacy.unsupported", new DeprecatedProperty("legacy.unsupported", null, "No longer supported")),
				true);

		String content = Files.readString(file, StandardCharsets.UTF_8);
		assertThat(content).contains("legacy.unsupported=value");
		assertThat(result.getUnsupported()).hasSize(1);
		assertThat(result.getUnsupported().get(0).reason()).isEqualTo("No longer supported");
	}

}
