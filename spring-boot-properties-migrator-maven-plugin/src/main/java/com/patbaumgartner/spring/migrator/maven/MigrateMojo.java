package com.patbaumgartner.spring.migrator.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Rewrites deprecated Spring Boot configuration properties in place.
 */
@Mojo(name = "migrate", requiresDependencyResolution = ResolutionScope.TEST, threadSafe = true)
public class MigrateMojo extends AbstractMigratorMojo {

	/**
	 * Creates the goal. Maven instantiates it reflectively.
	 */
	public MigrateMojo() {
	}

	/**
	 * Reports what would change without writing anything, making this goal behave like
	 * {@code analyze}.
	 */
	@Parameter(property = PREFIX + "dryRun", defaultValue = "false")
	private boolean dryRun;

	@Override
	protected boolean applyChanges() {
		return !this.dryRun;
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		executeInternal();
	}

}
