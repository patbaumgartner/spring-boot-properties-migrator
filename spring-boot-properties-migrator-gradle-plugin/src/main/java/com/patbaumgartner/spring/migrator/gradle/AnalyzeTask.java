package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Task analyzes project properties and writes logs")
public abstract class AnalyzeTask extends AbstractMigrationTask {

	@TaskAction
	public void analyze() {
		runMigration(false);
	}

}
