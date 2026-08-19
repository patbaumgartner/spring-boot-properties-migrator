package com.patbaumgartner.spring.migrator.core;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that keys absent from the metadata are reported only when the migrator can say so
 * without guessing, because a false accusation costs more than a missed key.
 */
class UnknownKeyDetectionTests {

	@TempDir
	Path tempDir;

	private final MigrationEngine engine = new MigrationEngine();

	@Test
	void reportsAKeyThatNoMetadataDescribes() throws Exception {
		Path file = write("application.properties", "spring.codec.max-in-memory-size=1MB\n");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(plan.changes(Outcome.UNKNOWN)).singleElement()
			.satisfies((change) -> assertThat(change.key()).isEqualTo("spring.codec.max-in-memory-size"));
	}

	@Test
	void neverRewritesOrRemovesAnUnknownKey() throws Exception {
		String original = "spring.codec.max-in-memory-size=1MB\n";
		Path file = write("application.properties", original);

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));
		this.engine.apply(plan);

		assertThat(read(file)).isEqualTo(original);
		assertThat(plan.hasPendingWrites()).isFalse();
	}

	@Test
	void staysSilentWhenTheCheckIsOff() throws Exception {
		Path file = write("application.properties", "spring.codec.max-in-memory-size=1MB\n");

		MigrationPlan plan = this.engine.plan(this.tempDir, List.of(file),
				catalog("spring.application.name", "java.lang.String"), "4.1.0", UnknownKeyOptions.disabled());

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void treatsMapEntriesWrittenBelowAMapPropertyAsKnown() throws Exception {
		Path file = write("application.properties", "logging.level.com.example.MyClass=DEBUG\n");

		MigrationPlan plan = analyze(file,
				catalog("logging.level", "java.util.Map<java.lang.String,java.lang.String>"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void treatsBracketedMapKeysContainingDotsAsKnown() throws Exception {
		Path file = write("application.properties", "logging.level[com.example.MyClass]=DEBUG\n");

		MigrationPlan plan = analyze(file,
				catalog("logging.level", "java.util.Map<java.lang.String,java.lang.String>"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void treatsIndexedCollectionElementsAsKnown() throws Exception {
		Path file = write("application.properties", "spring.sample.clients[0]=first\n");

		MigrationPlan plan = analyze(file, catalog("spring.sample.clients", "java.util.List<java.lang.String>"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void reportsAKeyBelowAStructuredPropertyThatMetadataDecomposes() throws Exception {
		Path file = write("application.properties", "server.tomcat.accesslog.enabeld=true\n");

		Map<String, ConfigurationMetadataProperty> metadata = new LinkedHashMap<>();
		metadata.put("server.tomcat.accesslog", property("server.tomcat.accesslog", "com.example.Accesslog"));
		metadata.put("server.tomcat.accesslog.enabled",
				property("server.tomcat.accesslog.enabled", "java.lang.Boolean"));

		MigrationPlan plan = analyze(file, DeprecationCatalog.from(metadata));

		assertThat(plan.changes(Outcome.UNKNOWN)).singleElement()
			.satisfies((change) -> assertThat(change.key()).isEqualTo("server.tomcat.accesslog.enabeld"));
	}

	@Test
	void treatsKeysBelowAnOpaquePropertyAsKnown() throws Exception {
		Path file = write("application.properties", "spring.opaque.anything=value\n");

		MigrationPlan plan = analyze(file, catalog("spring.opaque", "com.example.SomethingCustom"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void ignoresYamlStructuralParentKeys() throws Exception {
		Path file = write("application.yml", """
				spring:
				  application:
				    name: demo
				""");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void reportsTheEffectiveYamlLeafRatherThanItsParents() throws Exception {
		Path file = write("application.yml", """
				spring:
				  codec:
				    max-in-memory-size: 1MB
				""");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(plan.changes(Outcome.UNKNOWN)).singleElement()
			.satisfies((change) -> assertThat(change.key()).isEqualTo("spring.codec.max-in-memory-size"));
	}

	@Test
	void ignoresKeysBuiltFromPlaceholders() throws Exception {
		Path file = write("application.properties", "spring.datasource.${env}.url=jdbc:h2:mem:test\n");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void ignoresInfrastructureKeysThatMetadataNeverDescribes() throws Exception {
		Path file = write("application.properties", """
				spring.profiles.active=dev
				spring.config.import=optional:file:./local.properties
				spring.application.name=demo
				spring.autoconfigure.exclude[0]=com.example.Auto
				spring.profiles.group.local=dev,debug
				spring.config.activate.on-profile=dev
				""");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void ignoresNamespacesOutsideSpringBootsOwn() throws Exception {
		Path file = write("application.properties", "acme.billing.endpoint=https://example.test\n");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void ignoresANamespaceTheResolvedMetadataKnowsNothingAbout() throws Exception {
		Path file = write("application.properties", "management.endpoints.web.exposure.include=health\n");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void honoursConfiguredExclusions() throws Exception {
		Path file = write("application.properties", "spring.codec.max-in-memory-size=1MB\n");

		MigrationPlan plan = this.engine.plan(this.tempDir, List.of(file),
				catalog("spring.application.name", "java.lang.String"), "4.1.0",
				new UnknownKeyOptions(UnknownKeyPolicy.REPORT, List.of(), List.of("spring.codec")));

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void honoursConfiguredIncludes() throws Exception {
		Path file = write("application.properties", "acme.billing.endpoint=https://example.test\n");

		MigrationPlan plan = this.engine.plan(this.tempDir, List.of(file),
				catalog("spring.application.name", "java.lang.String"), "4.1.0",
				new UnknownKeyOptions(UnknownKeyPolicy.REPORT, List.of("acme"), List.of()));

		assertThat(plan.changes(Outcome.UNKNOWN)).singleElement()
			.satisfies((change) -> assertThat(change.key()).isEqualTo("acme.billing.endpoint"));
	}

	@Test
	void staysSilentWhenNoMetadataResolvedAtAll() throws Exception {
		Path file = write("application.properties", "spring.codec.max-in-memory-size=1MB\n");

		MigrationPlan plan = analyze(file, DeprecationCatalog.empty());

		assertThat(plan.changes(Outcome.UNKNOWN)).isEmpty();
	}

	@Test
	void keepsUnknownKeysOutOfTheDeprecationFailurePolicies() throws Exception {
		Path file = write("application.properties", "spring.codec.max-in-memory-size=1MB\n");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(plan.hasFindings()).isFalse();
		assertThat(FailurePolicy.ANY.isViolatedBy(plan)).isFalse();
		assertThat(FailurePolicy.MANUAL.isViolatedBy(plan)).isFalse();
	}

	@Test
	void failsOnlyUnderItsOwnPolicy() throws Exception {
		Path file = write("application.properties", "spring.codec.max-in-memory-size=1MB\n");

		MigrationPlan plan = analyze(file, catalog("spring.application.name", "java.lang.String"));

		assertThat(UnknownKeyPolicy.FAIL.isViolatedBy(plan)).isTrue();
		assertThat(UnknownKeyPolicy.REPORT.isViolatedBy(plan)).isFalse();
	}

	@Test
	void describesTheFindingAsAnAbsenceRatherThanARemoval() throws Exception {
		Path file = write("application.properties", "spring.codec.max-in-memory-size=1MB\n");

		String report = analyze(file, catalog("spring.application.name", "java.lang.String")).render(false);

		assertThat(report).contains("Not found in resolved metadata (1)")
			.contains("1 not found in metadata")
			.doesNotContain("Spring Boot no longer reads this property");
	}

	private MigrationPlan analyze(Path file, DeprecationCatalog catalog) {
		return this.engine.plan(this.tempDir, List.of(file), catalog, "4.1.0",
				new UnknownKeyOptions(UnknownKeyPolicy.REPORT, List.of(), List.of()));
	}

	private DeprecationCatalog catalog(String name, String type) {
		return DeprecationCatalog.from(Map.of(name, property(name, type)));
	}

	private static ConfigurationMetadataProperty property(String name, String type) {
		ConfigurationMetadataProperty property = new ConfigurationMetadataProperty();
		property.setId(name);
		property.setName(name);
		property.setType(type);
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
