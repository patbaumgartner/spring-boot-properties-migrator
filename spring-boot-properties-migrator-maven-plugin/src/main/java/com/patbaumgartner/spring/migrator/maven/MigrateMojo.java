package com.patbaumgartner.spring.migrator.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "migrate", requiresDependencyResolution = ResolutionScope.TEST)
public class MigrateMojo extends AbstractMigratorMojo {

	@Parameter(defaultValue = "false")
	private boolean dryRun;

	@Override
	protected boolean applyChanges() {
		return !this.dryRun;
	}

	@Override
	public void execute() throws MojoExecutionException {
		executeInternal();
	}

}
