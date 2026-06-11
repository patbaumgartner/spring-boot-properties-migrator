package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

@DisableCachingByDefault(because = "Task mutates project property files by applying migration results")
public abstract class MigrateTask extends AbstractMigrationTask {

	@TaskAction
	public void migrate() {
		runMigration(true);
	}

}
