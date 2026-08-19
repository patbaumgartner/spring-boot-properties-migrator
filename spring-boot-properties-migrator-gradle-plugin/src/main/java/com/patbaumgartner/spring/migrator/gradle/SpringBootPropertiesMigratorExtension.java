package com.patbaumgartner.spring.migrator.gradle;

import java.util.ArrayList;
import java.util.List;

import com.patbaumgartner.spring.migrator.core.PropertyFileScanner;

/**
 * Project-wide configuration for the analyze and migrate tasks.
 */
public class SpringBootPropertiesMigratorExtension {

	private List<String> includes = new ArrayList<>(PropertyFileScanner.defaultIncludes());

	private String failOn = "never";

	private String reportFile;

	private String springBootVersion;

	private boolean dryRun;

	private String unknownKeys = "off";

	private List<String> unknownKeyIncludes = new ArrayList<>();

	private List<String> unknownKeyExcludes = new ArrayList<>();

	/**
	 * Returns the glob patterns, relative to the project directory, selecting the
	 * configuration files to inspect.
	 * @return the include patterns
	 */
	public List<String> getIncludes() {
		return this.includes;
	}

	/**
	 * Sets the glob patterns selecting the configuration files to inspect.
	 * @param includes the include patterns
	 */
	public void setIncludes(List<String> includes) {
		this.includes = includes;
	}

	/**
	 * Returns when the build should fail: {@code never}, {@code manual} when a finding
	 * needs a human, or {@code any} for any deprecated property at all.
	 * @return the failure policy name
	 */
	public String getFailOn() {
		return this.failOn;
	}

	/**
	 * Sets when the build should fail.
	 * @param failOn one of {@code never}, {@code manual} or {@code any}
	 */
	public void setFailOn(String failOn) {
		this.failOn = failOn;
	}

	/**
	 * Returns the optional file, relative to the project directory, to write the report
	 * to.
	 * @return the report path, or {@code null}
	 */
	public String getReportFile() {
		return this.reportFile;
	}

	/**
	 * Sets the file to write the report to.
	 * @param reportFile a path relative to the project directory
	 */
	public void setReportFile(String reportFile) {
		this.reportFile = reportFile;
	}

	/**
	 * Returns the Spring Boot version shown in the report. Metadata is always read from
	 * the resolved project classpath, so this does not change what gets detected.
	 * @return the version override, or {@code null}
	 */
	public String getSpringBootVersion() {
		return this.springBootVersion;
	}

	/**
	 * Sets the Spring Boot version shown in the report.
	 * @param springBootVersion the version to display
	 */
	public void setSpringBootVersion(String springBootVersion) {
		this.springBootVersion = springBootVersion;
	}

	/**
	 * Returns whether the migrate task should report without writing anything.
	 * @return whether to run dry
	 */
	public boolean isDryRun() {
		return this.dryRun;
	}

	/**
	 * Sets whether the migrate task should report without writing anything.
	 * @param dryRun whether to run dry
	 */
	public void setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
	}

	/**
	 * Returns whether to report configuration keys that no metadata on the resolved
	 * classpath describes: {@code off}, {@code report}, or {@code fail}.
	 * <p>
	 * This is advisory. A key can be absent because Spring Boot removed it, or simply
	 * because the code that reads it publishes no metadata, so it never rewrites or
	 * removes anything and never affects {@code failOn}.
	 * @return the unknown key policy name
	 */
	public String getUnknownKeys() {
		return this.unknownKeys;
	}

	/**
	 * Sets whether to report keys the metadata does not describe.
	 * @param unknownKeys one of {@code off}, {@code report} or {@code fail}
	 */
	public void setUnknownKeys(String unknownKeys) {
		this.unknownKeys = unknownKeys;
	}

	/**
	 * Returns the namespaces to inspect for unrecognised keys instead of the Spring Boot
	 * ones the check defaults to.
	 * @return the namespaces to inspect
	 */
	public List<String> getUnknownKeyIncludes() {
		return this.unknownKeyIncludes;
	}

	/**
	 * Sets the namespaces to inspect for unrecognised keys.
	 * @param unknownKeyIncludes the namespaces to inspect
	 */
	public void setUnknownKeyIncludes(List<String> unknownKeyIncludes) {
		this.unknownKeyIncludes = unknownKeyIncludes;
	}

	/**
	 * Returns the exact keys or prefixes never to report as unrecognised.
	 * @return the suppressed keys and prefixes
	 */
	public List<String> getUnknownKeyExcludes() {
		return this.unknownKeyExcludes;
	}

	/**
	 * Sets the exact keys or prefixes never to report as unrecognised.
	 * @param unknownKeyExcludes the suppressed keys and prefixes
	 */
	public void setUnknownKeyExcludes(List<String> unknownKeyExcludes) {
		this.unknownKeyExcludes = unknownKeyExcludes;
	}

}
