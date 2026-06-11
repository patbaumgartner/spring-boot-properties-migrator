package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.tasks.TaskAction;

public abstract class AnalyzeTask extends AbstractMigrationTask {

	@TaskAction
	public void analyze() {
		runMigration(false);
	}

}
