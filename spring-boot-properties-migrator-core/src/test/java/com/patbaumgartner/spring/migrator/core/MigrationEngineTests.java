package com.patbaumgartner.spring.migrator.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.Deprecation;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the engine only ever rewrites the key it reports, and leaves everything else
 * in the file byte-for-byte alone.
 */
class MigrationEngineTests {

	@TempDir
	Path tempDir;

	private final MigrationEngine engine = new MigrationEngine();

	@Test
	void renamesPropertiesKeyAndKeepsCommentsAndSpacing() throws Exception {
		Path file = write("application.properties", """
				# keep me
				old.key = value
				other.key=untouched
				""");

		MigrationPlan plan = migrate(file, catalog("old.key", "new.key"));

		assertThat(read(file)).isEqualTo("""
				# keep me
				new.key = value
				other.key=untouched
				""");
		assertThat(plan.changes(Outcome.MIGRATED)).singleElement()
			.satisfies((change) -> assertThat(change.line()).isEqualTo(2));
	}

	@Test
	void preservesCarriageReturnLineEndings() throws Exception {
		Path file = write("application.properties", "# c\r\nold.key=value\r\n");

		migrate(file, catalog("old.key", "new.key"));

		assertThat(read(file)).isEqualTo("# c\r\nnew.key=value\r\n");
	}

	@Test
	void preservesMissingTrailingNewline() throws Exception {
		Path file = write("application.properties", "old.key=value");

		migrate(file, catalog("old.key", "new.key"));

		assertThat(read(file)).isEqualTo("new.key=value");
	}

	@Test
	void preservesByteOrderMark() throws Exception {
		Path file = write("application.properties", "\uFEFFold.key=value\n");

		migrate(file, catalog("old.key", "new.key"));

		assertThat(read(file)).isEqualTo("\uFEFFnew.key=value\n");
	}

	@Test
	void doesNotTreatContinuationLineContentAsKey() throws Exception {
		Path file = write("application.properties", """
				some.list=a,\\
				  old.key=this is still a value
				""");

		MigrationPlan plan = migrate(file, catalog("old.key", "new.key"));

		assertThat(read(file)).contains("old.key=this is still a value");
		assertThat(plan.changes()).isEmpty();
	}

	@Test
	void renamesNestedYamlKeyInPlaceWhenParentIsUnchanged() throws Exception {
		Path file = write("application.yml", """
				server:
				  # keep me
				  max-http-header-size: 16KB
				""");

		migrate(file, catalog("server.max-http-header-size", "server.max-http-request-header-size"));

		assertThat(read(file)).isEqualTo("""
				server:
				  # keep me
				  max-http-request-header-size: 16KB
				""");
	}

	@Test
	void refusesToRewriteNestedYamlKeyWhoseReplacementMovesToAnotherParent() throws Exception {
		String original = """
				management:
				  metrics:
				    export:
				      prometheus:
				        enabled: true
				""";
		Path file = write("application.yml", original);

		MigrationPlan plan = migrate(file, catalog("management.metrics.export.prometheus.enabled",
				"management.prometheus.metrics.export.enabled"));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes(Outcome.MANUAL)).singleElement()
			.satisfies((change) -> assertThat(change.advice()).contains("moved by hand"));
	}

	@Test
	void rewritesFlatDottedYamlKeyEvenWhenTheParentChanges() throws Exception {
		Path file = write("application.yml", "spring.codec.max-in-memory-size: 1MB\n");

		migrate(file, catalog("spring.codec.max-in-memory-size", "spring.http.codec.max-in-memory-size"));

		assertThat(read(file)).isEqualTo("spring.http.codec.max-in-memory-size: 1MB\n");
	}

	@Test
	void ignoresKeyLookalikesInsideBlockScalars() throws Exception {
		String original = """
				banner:
				  text: |
				    old.key: this is literal text
				server:
				  port: 8080
				""";
		Path file = write("application.yml", original);

		MigrationPlan plan = migrate(file, catalog("banner.text.old.key", "new.key"));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes()).isEmpty();
	}

	@Test
	void findsKeysInsideFlowMappings() throws Exception {
		Path file = write("application.yml", "server: {max-http-header-size: 16KB}\n");

		migrate(file, catalog("server.max-http-header-size", "server.max-http-request-header-size"));

		assertThat(read(file)).isEqualTo("server: {max-http-request-header-size: 16KB}\n");
	}

	@Test
	void migratesEachDocumentOfAMultiDocumentFile() throws Exception {
		Path file = write("application.yml", """
				spring:
				  application:
				    name: demo
				---
				server:
				  max-http-header-size: 16KB
				""");

		migrate(file, catalog("server.max-http-header-size", "server.max-http-request-header-size"));

		assertThat(read(file)).contains("  max-http-request-header-size: 16KB").contains("name: demo");
	}

	@Test
	void refusesToRewriteYamlUsingAnchors() throws Exception {
		String original = """
				defaults: &defaults
				  timeout: 5s
				server:
				  max-http-header-size: 8KB
				""";
		Path file = write("application.yml", original);

		MigrationPlan plan = migrate(file,
				catalog("server.max-http-header-size", "server.max-http-request-header-size"));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes(Outcome.MANUAL)).isNotEmpty()
			.allSatisfy((change) -> assertThat(change.advice()).contains("anchors"));
	}

	@Test
	void refusesToRewriteInvalidYaml() throws Exception {
		String original = "server: [unclosed\n";
		Path file = write("application.yml", original);

		MigrationPlan plan = migrate(file, catalog("server.old", "server.new"));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes()).isEmpty();
	}

	@Test
	void matchesRelaxedFormsOfTheSameProperty() throws Exception {
		Path file = write("application.properties", "server.maxHttpHeaderSize=1KB\n");

		MigrationPlan plan = migrate(file,
				catalog("server.max-http-header-size", "server.max-http-request-header-size"));

		assertThat(read(file)).isEqualTo("server.max-http-request-header-size=1KB\n");
		assertThat(plan.changes(Outcome.MIGRATED)).singleElement()
			.satisfies((change) -> assertThat(change.key()).isEqualTo("server.maxHttpHeaderSize"));
	}

	@Test
	void treatsUnderscoreFormAsTheSameProperty() throws Exception {
		Path file = write("application.properties", "server.max_http_header_size=1KB\n");

		migrate(file, catalog("server.max-http-header-size", "server.max-http-request-header-size"));

		assertThat(read(file)).isEqualTo("server.max-http-request-header-size=1KB\n");
	}

	@Test
	void doesNotMigrateWhenTheReplacementValueTypeChanges() throws Exception {
		String original = "server.use-forward-headers=true\n";
		Path file = write("application.properties", original);

		Map<String, ConfigurationMetadataProperty> metadata = Map.of("server.use-forward-headers",
				property("server.use-forward-headers", "java.lang.Boolean", "server.forward-headers-strategy", null),
				"server.forward-headers-strategy", property("server.forward-headers-strategy",
						"org.springframework.boot.web.ForwardHeadersStrategy", null, null));

		MigrationPlan plan = migrate(file, DeprecationCatalog.from(metadata));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes(Outcome.MANUAL)).singleElement()
			.satisfies((change) -> assertThat(change.advice()).contains("ForwardHeadersStrategy")
				.contains("converted by hand"));
	}

	@Test
	void doesNotMigrateWhenTheReplacementAlreadyExists() throws Exception {
		String original = """
				old.key=1
				new.key=2
				""";
		Path file = write("application.properties", original);

		MigrationPlan plan = migrate(file, catalog("old.key", "new.key"));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes(Outcome.MANUAL)).singleElement()
			.satisfies((change) -> assertThat(change.advice()).contains("duplicate key"));
	}

	@Test
	void doesNotMigrateWhenSeveralKeysCollapseOntoOneReplacement() throws Exception {
		String original = """
				server.jetty.accesslog.locale=de
				server.jetty.accesslog.log-cookies=true
				""";
		Path file = write("application.properties", original);

		Map<String, ConfigurationMetadataProperty> metadata = Map.of("server.jetty.accesslog.locale",
				property("server.jetty.accesslog.locale", null, "server.jetty.accesslog.custom-format", null),
				"server.jetty.accesslog.log-cookies",
				property("server.jetty.accesslog.log-cookies", null, "server.jetty.accesslog.custom-format", null));

		MigrationPlan plan = migrate(file, DeprecationCatalog.from(metadata));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes(Outcome.MANUAL)).hasSize(2)
			.allSatisfy((change) -> assertThat(change.advice()).contains("merged by hand"));
	}

	@Test
	void followsChainedDeprecationsSoASecondRunChangesNothing() throws Exception {
		Path file = write("application.properties", "a.key=1\n");

		Map<String, ConfigurationMetadataProperty> metadata = Map.of("a.key",
				property("a.key", "java.lang.String", "b.key", null), "b.key",
				property("b.key", "java.lang.String", "c.key", null), "c.key",
				property("c.key", "java.lang.String", null, null));

		migrate(file, DeprecationCatalog.from(metadata));
		assertThat(read(file)).isEqualTo("c.key=1\n");

		MigrationPlan second = migrate(file, DeprecationCatalog.from(metadata));
		assertThat(read(file)).isEqualTo("c.key=1\n");
		assertThat(second.changes()).isEmpty();
	}

	@Test
	void reportsCircularReplacementChainsInsteadOfLooping() throws Exception {
		String original = "a.key=1\n";
		Path file = write("application.properties", original);

		Map<String, ConfigurationMetadataProperty> metadata = Map.of("a.key",
				property("a.key", "java.lang.String", "b.key", null), "b.key",
				property("b.key", "java.lang.String", "a.key", null));

		MigrationPlan plan = migrate(file, DeprecationCatalog.from(metadata));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes(Outcome.MANUAL))
			.allSatisfy((change) -> assertThat(change.advice()).contains("circular"));
	}

	@Test
	void reportsDeprecationsWithoutReplacementAndLeavesThemAlone() throws Exception {
		String original = "legacy.setting=value\n";
		Path file = write("application.properties", original);

		Map<String, ConfigurationMetadataProperty> metadata = Map.of("legacy.setting",
				property("legacy.setting", "java.lang.String", null, "No longer supported"));

		MigrationPlan plan = migrate(file, DeprecationCatalog.from(metadata));

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes(Outcome.UNSUPPORTED)).singleElement().satisfies((change) -> {
			assertThat(change.reason()).isEqualTo("No longer supported");
			assertThat(change.advice()).contains("no longer reads this property");
		});
	}

	@Test
	void planningNeverTouchesFiles() throws Exception {
		String original = "old.key=value\n";
		Path file = write("application.properties", original);

		MigrationPlan plan = this.engine.plan(this.tempDir, List.of(file), catalog("old.key", "new.key"), "4.1.0");

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.changes(Outcome.MIGRATED)).hasSize(1);
		assertThat(plan.hasPendingWrites()).isTrue();
	}

	@Test
	void warnsWhenNoMetadataIsAvailableAtAll() throws Exception {
		Path file = write("application.properties", "old.key=value\n");

		MigrationPlan plan = this.engine.plan(this.tempDir, List.of(file), DeprecationCatalog.empty(), null);

		assertThat(plan.diagnostics()).anySatisfy((warning) -> assertThat(warning).contains("No Spring Boot"));
	}

	@Test
	void surfacesTheDeprecationReasonForMigratedKeys() throws Exception {
		Path file = write("application.properties", "old.key=value\n");

		Map<String, ConfigurationMetadataProperty> metadata = Map.of("old.key",
				property("old.key", "java.lang.String", "new.key", "Renamed for clarity"));

		MigrationPlan plan = migrate(file, DeprecationCatalog.from(metadata));

		assertThat(plan.render(true)).contains("Renamed for clarity");
	}

	private MigrationPlan migrate(Path file, DeprecationCatalog catalog) throws Exception {
		MigrationPlan plan = this.engine.plan(this.tempDir, List.of(file), catalog, "4.1.0");
		this.engine.apply(plan);
		return plan;
	}

	private DeprecationCatalog catalog(String key, String replacement) {
		return DeprecationCatalog.from(Map.of(key, property(key, null, replacement, null)));
	}

	private static ConfigurationMetadataProperty property(String name, String type, String replacement, String reason) {
		ConfigurationMetadataProperty property = new ConfigurationMetadataProperty();
		property.setId(name);
		property.setName(name);
		property.setType(type);
		if (replacement != null || reason != null) {
			Deprecation deprecation = new Deprecation();
			deprecation.setReplacement(replacement);
			deprecation.setShortReason(reason);
			deprecation.setLevel(Deprecation.Level.ERROR);
			property.setDeprecation(deprecation);
		}
		return property;
	}

	private Path write(String name, String content) throws Exception {
		Path file = this.tempDir.resolve("src/main/resources").resolve(name);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return file;
	}

	private String read(Path file) throws Exception {
		return Files.readString(file, StandardCharsets.UTF_8);
	}

}
