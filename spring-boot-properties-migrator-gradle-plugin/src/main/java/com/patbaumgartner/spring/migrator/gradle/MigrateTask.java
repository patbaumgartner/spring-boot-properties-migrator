package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Rewrites deprecated Spring Boot configuration properties in place.
 */
@DisableCachingByDefault(because = "The task rewrites source files in place")
public abstract class MigrateTask extends AbstractMigrationTask {

	/**
	 * Rewrites the deprecated properties that can be migrated safely.
	 */
	@TaskAction
	public void migrate() {
		runMigration(true);
	}

}
