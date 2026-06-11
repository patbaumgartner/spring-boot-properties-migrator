package com.patbaumgartner.spring.migrator.maven;

import java.util.List;
import java.util.Optional;


import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Model;
import org.apache.maven.model.Parent;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MavenSpringBootVersionDetectorTests {

	@Test
	void detectsVersionFromSpringBootProperty() {
		MavenProject project = new MavenProject();
		project.getProperties().setProperty("spring-boot.version", "4.1.0");

		Optional<String> detected = MavenSpringBootVersionDetector.detect(project);

		assertThat(detected).contains("4.1.0");
	}

	@Test
	void detectsVersionFromSpringBootParent() {
		Model model = new Model();
		Parent parent = new Parent();
		parent.setGroupId("org.springframework.boot");
		parent.setArtifactId("spring-boot-starter-parent");
		parent.setVersion("4.0.2");
		model.setParent(parent);

		MavenProject project = new MavenProject(model);

		Optional<String> detected = MavenSpringBootVersionDetector.detect(project);

		assertThat(detected).contains("4.0.2");
	}

	@Test
	void detectsVersionFromDependencies() {
		MavenProject project = new MavenProject();
		Dependency dependency = new Dependency();
		dependency.setGroupId("org.springframework.boot");
		dependency.setArtifactId("spring-boot-starter-web");
		dependency.setVersion("4.1.1");
		project.setDependencies(List.of(dependency));

		Optional<String> detected = MavenSpringBootVersionDetector.detect(project);

		assertThat(detected).contains("4.1.1");
	}

	@Test
	void detectsVersionFromDependencyManagementWhenDirectDependencyHasNoVersion() {
		Model model = new Model();
		MavenProject project = new MavenProject(model);

		Dependency direct = new Dependency();
		direct.setGroupId("org.springframework.boot");
		direct.setArtifactId("spring-boot-starter");
		project.setDependencies(List.of(direct));

		Dependency managed = new Dependency();
		managed.setGroupId("org.springframework.boot");
		managed.setArtifactId("spring-boot-starter");
		managed.setVersion("4.0.1");

		DependencyManagement dependencyManagement = new DependencyManagement();
		dependencyManagement.setDependencies(List.of(managed));
		model.setDependencyManagement(dependencyManagement);

		Optional<String> detected = MavenSpringBootVersionDetector.detect(project);

		assertThat(detected).contains("4.0.1");
	}

	@Test
	void returnsEmptyWhenNoBootVersionCanBeResolved() {
		MavenProject project = new MavenProject();

		Optional<String> detected = MavenSpringBootVersionDetector.detect(project);

		assertThat(detected).isEmpty();
	}

}
