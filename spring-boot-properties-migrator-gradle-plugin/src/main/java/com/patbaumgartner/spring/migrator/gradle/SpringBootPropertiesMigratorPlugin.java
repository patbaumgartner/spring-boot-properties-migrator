package com.patbaumgartner.spring.migrator.gradle;

import java.util.ArrayList;
import java.util.List;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.specs.Specs;

/**
 * Registers the analyze and migrate tasks and wires them to the project's configuration.
 */
public class SpringBootPropertiesMigratorPlugin implements Plugin<Project> {

	static final String EXTENSION_NAME = "springBootPropertiesMigrator";

	static final String ANALYZE_TASK = "springBootPropertiesMigratorAnalyze";

	static final String MIGRATE_TASK = "springBootPropertiesMigratorMigrate";

	static final String LEGACY_MIGRATE_TASK = "springBootPropertiesMigrate";

	static final String GROUP = "spring boot properties migrator";

	private static final List<String> CLASSPATH_CONFIGURATIONS = List.of("runtimeClasspath", "compileClasspath");

	@Override
	public void apply(Project project) {
		SpringBootPropertiesMigratorExtension extension = project.getExtensions()
			.create(EXTENSION_NAME, SpringBootPropertiesMigratorExtension.class);

		project.getTasks()
			.register(ANALYZE_TASK, AnalyzeTask.class, (task) -> task
				.setDescription("Reports deprecated Spring Boot properties without changing any file."));
		project.getTasks()
			.register(MIGRATE_TASK, MigrateTask.class,
					(task) -> task.setDescription("Rewrites deprecated Spring Boot properties in place."));

		project.getTasks().withType(AbstractMigrationTask.class).configureEach((task) -> {
			task.setGroup(GROUP);
			task.getProjectDirectory().set(project.getLayout().getProjectDirectory());
			task.getIncludes().convention(project.provider(extension::getIncludes));
			task.getFailOn().convention(project.provider(extension::getFailOn));
			task.getDryRun().convention(project.provider(extension::isDryRun));
			task.getSpringBootVersion().convention(project.provider(extension::getSpringBootVersion));
			task.getReportFile().convention(project.provider(extension::getReportFile));
			// The task reads and rewrites its own sources, so it is never up to date.
			task.getOutputs().upToDateWhen(Specs.satisfyNone());
		});

		project.getPluginManager()
			.withPlugin("java",
					(applied) -> project.getTasks()
						.withType(AbstractMigrationTask.class)
						.configureEach((task) -> task.getClasspath().from(resolvableClasspaths(project))));

		registerLegacyMigrateAlias(project);
	}

	/**
	 * Registers the pre-1.0 task name so existing builds keep working.
	 * @param project the project being configured
	 */
	private void registerLegacyMigrateAlias(Project project) {
		project.getTasks().register(LEGACY_MIGRATE_TASK, (task) -> {
			task.setGroup(GROUP);
			task.setDescription("Deprecated alias for " + MIGRATE_TASK + ".");
			task.dependsOn(MIGRATE_TASK);
		});
	}

	private static List<FileCollection> resolvableClasspaths(Project project) {
		List<FileCollection> classpaths = new ArrayList<>();
		for (String name : CLASSPATH_CONFIGURATIONS) {
			Configuration configuration = project.getConfigurations().findByName(name);
			if (configuration != null && configuration.isCanBeResolved()) {
				classpaths.add(configuration);
			}
		}
		return classpaths;
	}

}
