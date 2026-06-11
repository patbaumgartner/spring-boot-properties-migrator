package com.patbaumgartner.spring.migrator.gradle;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpringBootPropertiesMigratorExtensionTests {

	@Test
	void defaultsAreSensible() {
		SpringBootPropertiesMigratorExtension extension = new SpringBootPropertiesMigratorExtension();

		assertThat(extension.getIncludes()).isNotEmpty();
		assertThat(extension.isFailOnError()).isFalse();
		assertThat(extension.isDryRun()).isFalse();
		assertThat(extension.getReportFile()).isNull();
		assertThat(extension.getSpringBootVersion()).isNull();
	}

	@Test
	void settersUpdateConfiguration() {
		SpringBootPropertiesMigratorExtension extension = new SpringBootPropertiesMigratorExtension();

		extension.setIncludes(List.of("src/main/resources/application.properties"));
		extension.setFailOnError(true);
		extension.setDryRun(true);
		extension.setReportFile("build/report.txt");
		extension.setSpringBootVersion("4.1.0");

		assertThat(extension.getIncludes()).containsExactly("src/main/resources/application.properties");
		assertThat(extension.isFailOnError()).isTrue();
		assertThat(extension.isDryRun()).isTrue();
		assertThat(extension.getReportFile()).isEqualTo("build/report.txt");
		assertThat(extension.getSpringBootVersion()).isEqualTo("4.1.0");
	}

}
