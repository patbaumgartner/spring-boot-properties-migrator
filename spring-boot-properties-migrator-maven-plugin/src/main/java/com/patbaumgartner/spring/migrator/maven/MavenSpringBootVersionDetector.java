package com.patbaumgartner.spring.migrator.maven;

import java.util.List;
import java.util.Optional;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.MavenProject;

final class MavenSpringBootVersionDetector {

	private MavenSpringBootVersionDetector() {
	}

	static Optional<String> detect(MavenProject project) {
		String byProperty = project.getProperties().getProperty("spring-boot.version");
		if (byProperty != null && !byProperty.isBlank()) {
			return Optional.of(byProperty);
		}

		String byParentVersion = project.getModel().getParent() != null
				&& "org.springframework.boot".equals(project.getModel().getParent().getGroupId())
				&& "spring-boot-starter-parent".equals(project.getModel().getParent().getArtifactId())
						? project.getModel().getParent().getVersion() : null;
		if (byParentVersion != null && !byParentVersion.isBlank()) {
			return Optional.of(byParentVersion);
		}

		String byDependency = detectFromDependencies(project.getDependencies());
		if (byDependency != null) {
			return Optional.of(byDependency);
		}

		DependencyManagement dm = project.getDependencyManagement();
		if (dm != null) {
			byDependency = detectFromDependencies(dm.getDependencies());
			if (byDependency != null) {
				return Optional.of(byDependency);
			}
		}

		return Optional.empty();
	}

	private static String detectFromDependencies(List<Dependency> dependencies) {
		for (Dependency dependency : dependencies) {
			if (!"org.springframework.boot".equals(dependency.getGroupId())) {
				continue;
			}
			if (!dependency.getArtifactId().startsWith("spring-boot")) {
				continue;
			}
			if (dependency.getVersion() != null && !dependency.getVersion().isBlank()) {
				return dependency.getVersion();
			}
		}
		return null;
	}

}
