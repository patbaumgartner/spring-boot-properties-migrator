package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.tasks.TaskAction;

public abstract class MigrateTask extends AbstractMigrationTask {

	@TaskAction
	public void migrate() {
		runMigration(true);
	}

}
