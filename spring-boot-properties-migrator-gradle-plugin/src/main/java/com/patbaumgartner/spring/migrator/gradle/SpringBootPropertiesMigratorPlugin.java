package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class SpringBootPropertiesMigratorPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		SpringBootPropertiesMigratorExtension extension = project.getExtensions()
			.create("springBootPropertiesMigrator", SpringBootPropertiesMigratorExtension.class);

		project.getTasks().register("springBootPropertiesMigratorAnalyze", AnalyzeTask.class, (task) -> {
			task.setGroup("verification");
			task.setDescription("Dry-run analysis of deprecated Spring Boot properties.");
			task.setExtension(extension);
		});

		project.getTasks().register("springBootPropertiesMigrate", MigrateTask.class, (task) -> {
			task.setGroup("refactoring");
			task.setDescription("Migrates deprecated Spring Boot properties in-place.");
			task.setExtension(extension);
		});
	}

}
