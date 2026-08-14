package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.tasks.TaskAction;
import org.gradle.work.DisableCachingByDefault;

/**
 * Reports deprecated Spring Boot configuration properties without changing any file.
 */
@DisableCachingByDefault(because = "The task reports on source files rather than producing a cacheable output")
public abstract class AnalyzeTask extends AbstractMigrationTask {

	/**
	 * Reports deprecated properties found in the project.
	 */
	@TaskAction
	public void analyze() {
		runMigration(false);
	}

}
