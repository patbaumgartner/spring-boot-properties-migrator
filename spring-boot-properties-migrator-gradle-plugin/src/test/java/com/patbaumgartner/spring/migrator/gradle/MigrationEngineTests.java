package com.patbaumgartner.spring.migrator.gradle;

import com.patbaumgartner.spring.migrator.core.DeprecatedProperty;
import com.patbaumgartner.spring.migrator.core.MigrationEngine;
import com.patbaumgartner.spring.migrator.core.MigrationRunResult;

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

}
