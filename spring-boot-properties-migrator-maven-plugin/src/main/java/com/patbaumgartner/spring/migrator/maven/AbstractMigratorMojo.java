package com.patbaumgartner.spring.migrator.maven;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.patbaumgartner.spring.migrator.core.DeprecationCatalog;
import com.patbaumgartner.spring.migrator.core.FailurePolicy;
import com.patbaumgartner.spring.migrator.core.MetadataRepositoryLoader;
import com.patbaumgartner.spring.migrator.core.MigrationEngine;
import com.patbaumgartner.spring.migrator.core.MigrationPlan;
import com.patbaumgartner.spring.migrator.core.PropertyFileScanner;
import com.patbaumgartner.spring.migrator.core.UnknownKeyOptions;
import com.patbaumgartner.spring.migrator.core.UnknownKeyPolicy;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.springframework.boot.configurationmetadata.ConfigurationMetadataRepository;

/**
 * Shared behaviour of the analyze and migrate goals.
 */
abstract class AbstractMigratorMojo extends AbstractMojo {

	static final String PREFIX = "spring-boot-properties-migrator.";

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	protected MavenProject project;

	/**
	 * Glob patterns, relative to the project directory, selecting the configuration files
	 * to inspect. Defaults to Spring Boot's conventional locations under
	 * {@code src/main/resources} and {@code src/test/resources}.
	 */
	@Parameter
	protected List<String> includes;

	/**
	 * When the build should fail: {@code never}, {@code manual} when a finding needs a
	 * human, or {@code any} for any deprecated property at all.
	 */
	@Parameter(property = PREFIX + "failOn", defaultValue = "never")
	protected String failOn;

	/**
	 * Optional file, relative to the project directory, to write the report to.
	 */
	@Parameter(property = PREFIX + "reportFile")
	protected String reportFile;

	/**
	 * Overrides the Spring Boot version shown in the report. Metadata is always read from
	 * the resolved project classpath, so this does not change what gets detected.
	 */
	@Parameter(property = PREFIX + "springBootVersion")
	protected String springBootVersion;

	/**
	 * Whether to report configuration keys that no metadata on the resolved classpath
	 * describes: {@code off}, {@code report}, or {@code fail} to also break the build.
	 * <p>
	 * This is advisory. A key can be absent because Spring Boot removed it, or simply
	 * because the code that reads it publishes no metadata, so it never rewrites or
	 * removes anything and never affects {@code failOn}.
	 */
	@Parameter(property = PREFIX + "unknownKeys", defaultValue = "off")
	protected String unknownKeys;

	/**
	 * Namespaces to inspect for unrecognised keys instead of the Spring Boot ones the
	 * check defaults to.
	 */
	@Parameter
	protected List<String> unknownKeyIncludes;

	/**
	 * Exact keys or prefixes never to report as unrecognised.
	 */
	@Parameter
	protected List<String> unknownKeyExcludes;

	/**
	 * Skips the goal entirely.
	 */
	@Parameter(property = PREFIX + "skip", defaultValue = "false")
	protected boolean skip;

	/**
	 * Returns whether this goal writes the migrated files.
	 * @return whether to apply the plan
	 */
	protected abstract boolean applyChanges();

	protected final void executeInternal() throws MojoExecutionException, MojoFailureException {
		if (this.skip) {
			getLog().info("Skipping Spring Boot properties migration.");
			return;
		}

		FailurePolicy policy = parsePolicy();
		UnknownKeyPolicy unknownKeyPolicy = parseUnknownKeyPolicy();
		Path baseDir = this.project.getBasedir().toPath();
		List<String> effectiveIncludes = (this.includes == null || this.includes.isEmpty())
				? PropertyFileScanner.defaultIncludes() : this.includes;

		PropertyFileScanner.ScanResult scan = PropertyFileScanner.scan(baseDir, effectiveIncludes);
		scan.warnings().forEach((warning) -> getLog().warn(warning));
		if (scan.files().isEmpty()) {
			getLog().info("No configuration files matched " + effectiveIncludes + ".");
			return;
		}

		MigrationPlan plan = analyze(baseDir, scan.files(), unknownKeyPolicy);
		String report = plan.render(applyChanges() && plan.hasPendingWrites());
		getLog().info(System.lineSeparator() + report);
		plan.diagnostics().forEach((diagnostic) -> getLog().warn(diagnostic));
		writeReport(baseDir, report);

		// Decide before mutating, so a failing policy never leaves files half migrated.
		if (policy.isViolatedBy(plan)) {
			throw new MojoFailureException(
					"Spring Boot properties migration failed: " + policy.describeViolation(plan) + ".");
		}
		if (unknownKeyPolicy.isViolatedBy(plan)) {
			throw new MojoFailureException(
					"Spring Boot properties migration failed: " + unknownKeyPolicy.describeViolation(plan) + ".");
		}
		if (applyChanges()) {
			applyPlan(plan);
		}
	}

	private MigrationPlan analyze(Path baseDir, List<Path> files, UnknownKeyPolicy unknownKeyPolicy)
			throws MojoExecutionException {
		Optional<String> detectedVersion = (this.springBootVersion == null || this.springBootVersion.isBlank())
				? MavenSpringBootVersionDetector.detect(this.project) : Optional.of(this.springBootVersion);
		detectedVersion.ifPresent((version) -> getLog().info("Detected Spring Boot version: " + version));

		try {
			ConfigurationMetadataRepository repository = MetadataRepositoryLoader.load(classpathEntries());
			DeprecationCatalog catalog = DeprecationCatalog.from(repository.getAllProperties());
			UnknownKeyOptions unknownKeyOptions = new UnknownKeyOptions(unknownKeyPolicy, this.unknownKeyIncludes,
					this.unknownKeyExcludes);
			return new MigrationEngine().plan(baseDir, files, catalog, detectedVersion.orElse(null), unknownKeyOptions);
		}
		catch (IOException ex) {
			throw new MojoExecutionException("Failed reading Spring Boot configuration metadata", ex);
		}
	}

	private void applyPlan(MigrationPlan plan) throws MojoExecutionException {
		try {
			new MigrationEngine().apply(plan);
		}
		catch (IOException ex) {
			throw new MojoExecutionException("Failed writing migrated configuration files", ex);
		}
	}

	private List<Path> classpathEntries() {
		List<Path> entries = new ArrayList<>();
		for (Artifact artifact : this.project.getArtifacts()) {
			if (artifact.getFile() != null) {
				entries.add(artifact.getFile().toPath());
			}
		}
		return entries;
	}

	private void writeReport(Path baseDir, String report) throws MojoExecutionException {
		if (this.reportFile == null || this.reportFile.isBlank()) {
			return;
		}
		try {
			Path output = baseDir.resolve(this.reportFile).normalize();
			if (output.getParent() != null) {
				Files.createDirectories(output.getParent());
			}
			Files.writeString(output, report, StandardCharsets.UTF_8);
			getLog().info("Wrote migration report to " + output);
		}
		catch (IOException ex) {
			throw new MojoExecutionException("Failed writing migration report to " + this.reportFile, ex);
		}
	}

	private FailurePolicy parsePolicy() throws MojoExecutionException {
		try {
			return FailurePolicy.parse(this.failOn);
		}
		catch (IllegalArgumentException ex) {
			throw new MojoExecutionException(ex.getMessage(), ex);
		}
	}

	private UnknownKeyPolicy parseUnknownKeyPolicy() throws MojoExecutionException {
		try {
			return UnknownKeyPolicy.parse(this.unknownKeys);
		}
		catch (IllegalArgumentException ex) {
			throw new MojoExecutionException(ex.getMessage(), ex);
		}
	}

}
