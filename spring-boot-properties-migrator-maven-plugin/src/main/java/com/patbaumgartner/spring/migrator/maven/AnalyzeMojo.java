package com.patbaumgartner.spring.migrator.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Reports deprecated Spring Boot configuration properties without changing any file.
 */
@Mojo(name = "analyze", defaultPhase = LifecyclePhase.VALIDATE, requiresDependencyResolution = ResolutionScope.TEST,
		threadSafe = true)
public class AnalyzeMojo extends AbstractMigratorMojo {

	/**
	 * Creates the goal. Maven instantiates it reflectively.
	 */
	public AnalyzeMojo() {
	}

	@Override
	protected boolean applyChanges() {
		return false;
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		executeInternal();
	}

}
