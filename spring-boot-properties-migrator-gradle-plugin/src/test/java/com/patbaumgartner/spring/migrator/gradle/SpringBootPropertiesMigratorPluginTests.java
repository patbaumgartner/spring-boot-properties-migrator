package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootPropertiesMigratorPluginTests {

	@Test
	void registersExtensionAndTasks() {
		Project project = projectWithPlugin();

		Task analyze = project.getTasks().getByName(SpringBootPropertiesMigratorPlugin.ANALYZE_TASK);
		Task migrate = project.getTasks().getByName(SpringBootPropertiesMigratorPlugin.MIGRATE_TASK);

		assertThat(project.getExtensions().getByType(SpringBootPropertiesMigratorExtension.class)).isNotNull();
		assertThat(analyze).isInstanceOf(AnalyzeTask.class);
		assertThat(migrate).isInstanceOf(MigrateTask.class);
		assertThat(analyze.getGroup()).isEqualTo(SpringBootPropertiesMigratorPlugin.GROUP);
		assertThat(migrate.getGroup()).isEqualTo(SpringBootPropertiesMigratorPlugin.GROUP);
	}

	@Test
	void keepsThePreviousMigrateTaskNameWorking() {
		Project project = projectWithPlugin();

		Task legacy = project.getTasks().getByName(SpringBootPropertiesMigratorPlugin.LEGACY_MIGRATE_TASK);

		assertThat(legacy.getDependsOn()).contains(SpringBootPropertiesMigratorPlugin.MIGRATE_TASK);
		assertThat(legacy.getDescription()).contains("Deprecated alias");
	}

	@Test
	void tasksTakeTheirDefaultsFromTheExtension() {
		Project project = projectWithPlugin();
		SpringBootPropertiesMigratorExtension extension = project.getExtensions()
			.getByType(SpringBootPropertiesMigratorExtension.class);
		extension.setFailOn("manual");
		extension.setReportFile("build/report.txt");

		AnalyzeTask analyze = (AnalyzeTask) project.getTasks()
			.getByName(SpringBootPropertiesMigratorPlugin.ANALYZE_TASK);

		assertThat(analyze.getFailOn().get()).isEqualTo("manual");
		assertThat(analyze.getReportFile().get()).isEqualTo("build/report.txt");
		assertThat(analyze.getIncludes().get()).isNotEmpty();
	}

	@Test
	void wiresTheProjectClasspathWhenTheJavaPluginIsApplied() {
		Project project = projectWithPlugin();

		AnalyzeTask analyze = (AnalyzeTask) project.getTasks()
			.getByName(SpringBootPropertiesMigratorPlugin.ANALYZE_TASK);

		assertThat(analyze.getClasspath()).isNotNull();
		assertThat(analyze.getProjectDirectory().get().getAsFile()).isEqualTo(project.getProjectDir());
	}

	private static Project projectWithPlugin() {
		Project project = ProjectBuilder.builder().build();
		project.getPlugins().apply("java");
		project.getPlugins().apply(SpringBootPropertiesMigratorPlugin.class);
		return project;
	}

}
