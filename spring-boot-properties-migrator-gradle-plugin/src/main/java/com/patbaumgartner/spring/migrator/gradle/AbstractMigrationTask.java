package com.patbaumgartner.spring.migrator.gradle;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.patbaumgartner.spring.migrator.core.DeprecatedProperty;
import com.patbaumgartner.spring.migrator.core.MigrationEngine;
import com.patbaumgartner.spring.migrator.core.MigrationRunResult;
import com.patbaumgartner.spring.migrator.core.PropertyFileScanner;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ResolvedArtifact;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Task reads project configuration and external metadata at execution time")
public abstract class AbstractMigrationTask extends DefaultTask {

	private SpringBootPropertiesMigratorExtension extension;

	@Internal
	public SpringBootPropertiesMigratorExtension getExtension() {
		return this.extension;
	}

	public void setExtension(SpringBootPropertiesMigratorExtension extension) {
		this.extension = extension;
	}

	protected void runMigration(boolean apply) {
		if (this.extension == null) {
			throw new GradleException("SpringBootPropertiesMigratorExtension has not been set");
		}

		try {
			Path projectPath = getProject().getProjectDir().toPath();
			List<String> includes = this.extension.getIncludes();
			if (includes == null || includes.isEmpty()) {
				includes = PropertyFileScanner.defaultIncludes();
			}

			List<Path> files = PropertyFileScanner.scan(projectPath, includes);
			if (files.isEmpty()) {
				getLogger().lifecycle("No property files found for configured includes.");
				return;
			}

			Set<File> jars = resolveClasspathArtifacts();
			Optional<String> detectedVersion = GradleSpringBootVersionDetector.detect(jars,
					this.extension.getSpringBootVersion());
			detectedVersion.ifPresent((version) -> getLogger().lifecycle("Detected Spring Boot version: {}", version));

			var repository = GradleMetadataRepositoryLoader.loadFromArtifacts(jars);
			Map<String, DeprecatedProperty> deprecatedByKey = MigrationEngine
				.toDeprecatedMap(repository.getAllProperties());

			MigrationEngine engine = new MigrationEngine();
			MigrationRunResult result = engine.run(projectPath, files, deprecatedByKey,
					apply && !this.extension.isDryRun());
			String summary = result.renderSummary(!apply || this.extension.isDryRun());
			getLogger().lifecycle(System.lineSeparator() + summary);

			if (this.extension.getReportFile() != null && !this.extension.getReportFile().isBlank()) {
				Path output = projectPath.resolve(this.extension.getReportFile()).normalize();
				if (output.getParent() != null) {
					Files.createDirectories(output.getParent());
				}
				Files.writeString(output, summary, StandardCharsets.UTF_8);
				getLogger().lifecycle("Wrote migration report to {}", output);
			}

			if (this.extension.isFailOnError() && !result.getUnsupported().isEmpty()) {
				throw new GradleException("Unsupported deprecated properties found: " + result.getUnsupported().size());
			}
		}
		catch (IOException ex) {
			throw new GradleException("Failed running Spring Boot properties migration", ex);
		}
	}

	private Set<File> resolveClasspathArtifacts() {
		List<Configuration> candidates = new ArrayList<>();
		Configuration runtimeClasspath = getProject().getConfigurations().findByName("runtimeClasspath");
		if (runtimeClasspath != null && runtimeClasspath.isCanBeResolved()) {
			candidates.add(runtimeClasspath);
		}
		Configuration compileClasspath = getProject().getConfigurations().findByName("compileClasspath");
		if (compileClasspath != null && compileClasspath.isCanBeResolved()) {
			candidates.add(compileClasspath);
		}

		return candidates.stream()
			.flatMap((config) -> config.getResolvedConfiguration().getResolvedArtifacts().stream())
			.map(ResolvedArtifact::getFile)
			.filter((file) -> file.getName().endsWith(".jar"))
			.sorted(Comparator.comparing(File::getName))
			.collect(Collectors.toCollection(java.util.LinkedHashSet::new));
	}

}
