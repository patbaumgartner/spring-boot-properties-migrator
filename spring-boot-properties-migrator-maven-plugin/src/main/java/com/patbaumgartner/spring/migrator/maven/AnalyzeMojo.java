package com.patbaumgartner.spring.migrator.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;

@Mojo(name = "analyze", defaultPhase = LifecyclePhase.VALIDATE, requiresDependencyResolution = ResolutionScope.TEST)
public class AnalyzeMojo extends AbstractMigratorMojo {

	@Override
	protected boolean applyChanges() {
		return false;
	}

	@Override
	public void execute() throws MojoExecutionException {
		executeInternal();
	}

}
