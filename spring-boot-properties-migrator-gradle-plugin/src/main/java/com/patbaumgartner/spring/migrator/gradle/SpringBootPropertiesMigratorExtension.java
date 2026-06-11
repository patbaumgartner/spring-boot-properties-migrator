package com.patbaumgartner.spring.migrator.gradle;

import java.util.ArrayList;
import java.util.List;

import com.patbaumgartner.spring.migrator.core.PropertyFileScanner;

public class SpringBootPropertiesMigratorExtension {

	private List<String> includes = new ArrayList<>(PropertyFileScanner.defaultIncludes());

	private boolean failOnError;

	private String reportFile;

	private String springBootVersion;

	private boolean dryRun;

	public List<String> getIncludes() {
		return includes;
	}

	public void setIncludes(List<String> includes) {
		this.includes = includes;
	}

	public boolean isFailOnError() {
		return failOnError;
	}

	public void setFailOnError(boolean failOnError) {
		this.failOnError = failOnError;
	}

	public String getReportFile() {
		return reportFile;
	}

	public void setReportFile(String reportFile) {
		this.reportFile = reportFile;
	}

	public String getSpringBootVersion() {
		return springBootVersion;
	}

	public void setSpringBootVersion(String springBootVersion) {
		this.springBootVersion = springBootVersion;
	}

	public boolean isDryRun() {
		return dryRun;
	}

	public void setDryRun(boolean dryRun) {
		this.dryRun = dryRun;
	}

}
