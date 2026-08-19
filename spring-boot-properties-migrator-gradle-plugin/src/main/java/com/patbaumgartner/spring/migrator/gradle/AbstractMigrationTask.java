package com.patbaumgartner.spring.migrator.gradle;

import java.io.File;
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
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

/**
 * Shared behaviour of the analyze and migrate tasks.
 * <p>
 * Every input is a lazy Gradle property populated at configuration time. Nothing reaches
 * for {@code Task.project} while executing, which is what makes the tasks usable with the
 * configuration cache.
 */
@DisableCachingByDefault(because = "The task reads and rewrites source files rather than producing a cacheable output")
public abstract class AbstractMigrationTask extends DefaultTask {

	/**
	 * Returns the glob patterns selecting the configuration files to inspect.
	 * @return the include patterns
	 */
	@Input
	public abstract ListProperty<String> getIncludes();

	/**
	 * Returns when the build should fail: {@code never}, {@code manual} or {@code any}.
	 * @return the failure policy name
	 */
	@Input
	public abstract Property<String> getFailOn();

	/**
	 * Returns whether the migrate task should report without writing anything.
	 * @return whether to run dry
	 */
	@Input
	public abstract Property<Boolean> getDryRun();

	/**
	 * Returns the Spring Boot version shown in the report.
	 * @return the version override
	 */
	@Input
	@org.gradle.api.tasks.Optional
	public abstract Property<String> getSpringBootVersion();

	/**
	 * Returns the optional report path, relative to the project directory.
	 * @return the report path
	 */
	@Input
	@org.gradle.api.tasks.Optional
	public abstract Property<String> getReportFile();

	/**
	 * Returns whether to report configuration keys that no metadata describes:
	 * {@code off}, {@code report} or {@code fail}.
	 * @return the unknown key policy name
	 */
	@Input
	public abstract Property<String> getUnknownKeys();

	/**
	 * Returns the namespaces to inspect for unrecognised keys instead of the defaults.
	 * @return the namespaces to inspect
	 */
	@Input
	public abstract ListProperty<String> getUnknownKeyIncludes();

	/**
	 * Returns the exact keys or prefixes never to report as unrecognised.
	 * @return the suppressed keys and prefixes
	 */
	@Input
	public abstract ListProperty<String> getUnknownKeyExcludes();

	/**
	 * Returns the classpath whose entries are searched for configuration metadata.
	 * @return the resolved project classpath
	 */
	@Classpath
	public abstract ConfigurableFileCollection getClasspath();

	/**
	 * Returns the project directory that include patterns are relative to.
	 * @return the project directory
	 */
	@Internal
	public abstract DirectoryProperty getProjectDirectory();

	/**
	 * Analyses the project and, when asked to and allowed to, writes the result.
	 * @param apply whether this task is permitted to rewrite files
	 */
	protected void runMigration(boolean apply) {
		FailurePolicy policy = parsePolicy();
		UnknownKeyPolicy unknownKeyPolicy = parseUnknownKeyPolicy();
		Path projectPath = getProjectDirectory().get().getAsFile().toPath();
		List<String> includes = getIncludes().get().isEmpty() ? PropertyFileScanner.defaultIncludes()
				: getIncludes().get();

		PropertyFileScanner.ScanResult scan = PropertyFileScanner.scan(projectPath, includes);
		scan.warnings().forEach((warning) -> getLogger().warn(warning));
		if (scan.files().isEmpty()) {
			getLogger().lifecycle("No configuration files matched {}.", includes);
			return;
		}

		boolean writing = apply && !getDryRun().get();
		MigrationPlan plan = analyze(projectPath, scan.files(), unknownKeyPolicy);
		String report = plan.render(writing && plan.hasPendingWrites());
		getLogger().lifecycle(System.lineSeparator() + report);
		plan.diagnostics().forEach((diagnostic) -> getLogger().warn(diagnostic));
		writeReport(projectPath, report);

		// Decide before mutating, so a failing policy never leaves files half migrated.
		if (policy.isViolatedBy(plan)) {
			throw new GradleException(
					"Spring Boot properties migration failed: " + policy.describeViolation(plan) + ".");
		}
		if (unknownKeyPolicy.isViolatedBy(plan)) {
			throw new GradleException(
					"Spring Boot properties migration failed: " + unknownKeyPolicy.describeViolation(plan) + ".");
		}
		if (writing) {
			applyPlan(plan);
		}
	}

	private MigrationPlan analyze(Path projectPath, List<Path> files, UnknownKeyPolicy unknownKeyPolicy) {
		List<Path> classpath = new ArrayList<>();
		for (File entry : getClasspath().getFiles()) {
			classpath.add(entry.toPath());
		}

		Optional<String> version = GradleSpringBootVersionDetector.detect(classpath,
				getSpringBootVersion().getOrNull());
		version.ifPresent((detected) -> getLogger().lifecycle("Detected Spring Boot version: {}", detected));

		try {
			DeprecationCatalog catalog = DeprecationCatalog
				.from(MetadataRepositoryLoader.load(classpath).getAllProperties());
			UnknownKeyOptions unknownKeys = new UnknownKeyOptions(unknownKeyPolicy, getUnknownKeyIncludes().get(),
					getUnknownKeyExcludes().get());
			return new MigrationEngine().plan(projectPath, files, catalog, version.orElse(null), unknownKeys);
		}
		catch (IOException ex) {
			throw new GradleException("Failed reading Spring Boot configuration metadata", ex);
		}
	}

	private void applyPlan(MigrationPlan plan) {
		try {
			new MigrationEngine().apply(plan);
		}
		catch (IOException ex) {
			throw new GradleException("Failed writing migrated configuration files", ex);
		}
	}

	private void writeReport(Path projectPath, String report) {
		String configured = getReportFile().getOrNull();
		if (configured == null || configured.isBlank()) {
			return;
		}
		try {
			Path output = projectPath.resolve(configured).normalize();
			if (output.getParent() != null) {
				Files.createDirectories(output.getParent());
			}
			Files.writeString(output, report, StandardCharsets.UTF_8);
			getLogger().lifecycle("Wrote migration report to {}", output);
		}
		catch (IOException ex) {
			throw new GradleException("Failed writing migration report to " + configured, ex);
		}
	}

	private FailurePolicy parsePolicy() {
		try {
			return FailurePolicy.parse(getFailOn().getOrNull());
		}
		catch (IllegalArgumentException ex) {
			throw new GradleException(ex.getMessage(), ex);
		}
	}

	private UnknownKeyPolicy parseUnknownKeyPolicy() {
		try {
			return UnknownKeyPolicy.parse(getUnknownKeys().getOrNull());
		}
		catch (IllegalArgumentException ex) {
			throw new GradleException(ex.getMessage(), ex);
		}
	}

}
