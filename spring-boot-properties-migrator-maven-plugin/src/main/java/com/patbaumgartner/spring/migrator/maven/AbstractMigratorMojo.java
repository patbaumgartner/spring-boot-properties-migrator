package com.patbaumgartner.spring.migrator.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;


import com.patbaumgartner.spring.migrator.core.DeprecatedProperty;
import com.patbaumgartner.spring.migrator.core.MigrationEngine;
import com.patbaumgartner.spring.migrator.core.MigrationRunResult;
import com.patbaumgartner.spring.migrator.core.PropertyFileScanner;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;

abstract class AbstractMigratorMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	protected MavenProject project;

	@Parameter
	protected List<String> includes;

	@Parameter(defaultValue = "false")
	protected boolean failOnError;

	@Parameter
	protected String reportFile;

	@Parameter
	protected String springBootVersion;

	protected abstract boolean applyChanges();

	protected final void executeInternal() throws MojoExecutionException {
		try {
			Path baseDir = this.project.getBasedir().toPath();
			List<String> effectiveIncludes = this.includes == null || this.includes.isEmpty()
					? PropertyFileScanner.defaultIncludes() : this.includes;

			List<Path> files = PropertyFileScanner.scan(baseDir, effectiveIncludes);
			if (files.isEmpty()) {
				getLog().info("No property files found for configured includes.");
				return;
			}

			Optional<String> detectedVersion = this.springBootVersion == null || this.springBootVersion.isBlank()
					? MavenSpringBootVersionDetector.detect(this.project) : Optional.of(this.springBootVersion);
			detectedVersion.ifPresent(v -> getLog().info("Detected Spring Boot version: " + v));

			LinkedHashSet<org.eclipse.aether.artifact.Artifact> artifacts = new LinkedHashSet<>();
			for (Artifact artifact : this.project.getArtifacts()) {
				DefaultArtifact aether = new DefaultArtifact(artifact.getGroupId(), artifact.getArtifactId(),
						artifact.getClassifier(), artifact.getType(), artifact.getVersion());
				aether = (DefaultArtifact) aether.setFile(artifact.getFile());
				artifacts.add(aether);
			}

			ConfigurationMetadataRepository repository = MavenMetadataRepositoryLoader.loadFromArtifacts(artifacts);
			Map<String, DeprecatedProperty> deprecatedByKey = MigrationEngine
				.toDeprecatedMap(repository.getAllProperties());

			MigrationEngine engine = new MigrationEngine();
			MigrationRunResult result = engine.run(baseDir, files, deprecatedByKey, applyChanges());

			String summary = result.renderSummary(!applyChanges());
			getLog().info(System.lineSeparator() + summary);

			if (this.reportFile != null && !this.reportFile.isBlank()) {
				Path output = baseDir.resolve(this.reportFile).normalize();
				if (output.getParent() != null) {
					Files.createDirectories(output.getParent());
				}
				Files.writeString(output, summary, StandardCharsets.UTF_8);
				getLog().info("Wrote migration report to " + output);
			}

			if (this.failOnError && !result.getUnsupported().isEmpty()) {
				throw new MojoExecutionException(
						"Unsupported deprecated properties found: " + result.getUnsupported().size());
			}
		}
		catch (IOException ex) {
			throw new MojoExecutionException("Failed running Spring Boot properties migration", ex);
		}
	}

}
