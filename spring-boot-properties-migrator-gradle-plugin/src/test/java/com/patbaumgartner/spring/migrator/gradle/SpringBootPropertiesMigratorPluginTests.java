package com.patbaumgartner.spring.migrator.gradle;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootPropertiesMigratorPluginTests {

	@Test
	void registersExtensionAndTasks() {
		Project project = ProjectBuilder.builder().build();
		project.getPlugins().apply("java");
		project.getPlugins().apply(SpringBootPropertiesMigratorPlugin.class);

		SpringBootPropertiesMigratorExtension extension = project.getExtensions()
			.getByType(SpringBootPropertiesMigratorExtension.class);
		Task analyze = project.getTasks().getByName("springBootPropertiesMigratorAnalyze");
		Task migrate = project.getTasks().getByName("springBootPropertiesMigrate");

		assertThat(extension).isNotNull();
		assertThat(analyze).isInstanceOf(AnalyzeTask.class);
		assertThat(migrate).isInstanceOf(MigrateTask.class);
		assertThat(analyze.getGroup()).isEqualTo("verification");
		assertThat(migrate.getGroup()).isEqualTo("refactoring");
	}

}
