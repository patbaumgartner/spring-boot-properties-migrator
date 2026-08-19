package com.patbaumgartner.spring.migrator.core;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Decides which configuration keys are worth reporting as unrecognised.
 * <p>
 * Absence from the metadata is weak evidence. Spring Boot's metadata describes the
 * properties Boot and its starters own; it is not a registry of everything an application
 * may read. A key can be missing because it was removed from Spring Boot, because it is
 * misspelled, or because it is perfectly valid and consumed by application code, a
 * third-party library, or a module whose annotation processor never ran.
 * <p>
 * The detector therefore under-reports on purpose. It looks only inside namespaces Spring
 * Boot owns, only where the resolved metadata proves it knows that namespace, and never
 * at keys whose meaning it cannot pin down.
 */
final class UnknownKeyDetector {

	private static final List<String> DEFAULT_NAMESPACES = List.of("spring", "server", "management", "logging");

	private static final Set<String> DEFAULT_EXACT_KEYS = Set.of("debug", "trace");

	/**
	 * Keys Spring Boot acts on before, or outside, configuration property binding, so
	 * they are never described by metadata.
	 * <p>
	 * The entries are exact names rather than the enclosing prefixes, because suppressing
	 * all of {@code spring.config}, {@code spring.profiles} or {@code spring.application}
	 * would hide genuinely obsolete keys underneath them.
	 */
	private static final Set<String> INFRASTRUCTURE_KEYS = Set.of("spring.config.name", "spring.config.location",
			"spring.config.additional-location", "spring.config.import", "spring.config.on-not-found",
			"spring.config.activate.on-profile", "spring.config.activate.on-cloud-platform", "spring.profiles.active",
			"spring.profiles.include", "spring.profiles.default", "spring.application.name", "spring.application.json",
			"spring.autoconfigure.exclude");

	private static final String INFRASTRUCTURE_PREFIX = "spring.profiles.group";

	private final DeprecationCatalog catalog;

	private final Set<String> namespaces;

	private final List<String> excludes;

	UnknownKeyDetector(DeprecationCatalog catalog, List<String> includes, List<String> excludes) {
		this.catalog = catalog;
		this.excludes = List.copyOf(excludes);
		this.namespaces = resolveNamespaces(catalog, includes);
	}

	/**
	 * Returns the namespaces to inspect.
	 * <p>
	 * A default namespace is only inspected when the metadata actually describes
	 * properties in it. Without that guard a project without Actuator on the classpath
	 * would see every one of its {@code management.*} keys reported.
	 * @param catalog the resolved metadata
	 * @param includes namespaces configured by the user, which are never filtered out
	 * @return the namespaces that will be inspected
	 */
	private static Set<String> resolveNamespaces(DeprecationCatalog catalog, List<String> includes) {
		if (!includes.isEmpty()) {
			return new LinkedHashSet<>(includes);
		}
		Set<String> resolved = new LinkedHashSet<>();
		for (String namespace : DEFAULT_NAMESPACES) {
			if (catalog.describesNamespace(namespace)) {
				resolved.add(namespace);
			}
		}
		return resolved;
	}

	/**
	 * Returns whether a key should be reported as absent from the metadata.
	 * @param occurrence the key as it appears in the file
	 * @return whether the key is in scope, unsuppressed and unrecognised
	 */
	boolean isUnknown(KeyOccurrence occurrence) {
		String key = occurrence.name();
		return occurrence.effective() && !isTemplated(key) && isInScope(key) && !isInfrastructure(key)
				&& !isExcluded(key) && !this.catalog.isKnown(key);
	}

	/**
	 * Returns whether a key is produced at build or run time rather than written
	 * literally, in which case its real name is unknown to the migrator.
	 * @param key the property name
	 * @return whether the key contains a placeholder or build-filter token
	 */
	private boolean isTemplated(String key) {
		return key.contains("${") || key.contains("#{") || key.indexOf('@') >= 0;
	}

	private boolean isInScope(String key) {
		if (DEFAULT_EXACT_KEYS.contains(PropertyName.uniform(key))) {
			return true;
		}
		for (String namespace : this.namespaces) {
			if (matches(namespace, key)) {
				return true;
			}
		}
		return false;
	}

	private boolean isInfrastructure(String key) {
		String withoutIndexes = PropertyName.stripIndexes(key);
		String uniform = PropertyName.uniform(withoutIndexes);
		for (String infrastructure : INFRASTRUCTURE_KEYS) {
			if (PropertyName.uniform(infrastructure).equals(uniform)) {
				return true;
			}
		}
		return matches(INFRASTRUCTURE_PREFIX, withoutIndexes);
	}

	private boolean isExcluded(String key) {
		for (String exclude : this.excludes) {
			if (matches(exclude, key)) {
				return true;
			}
		}
		return false;
	}

	private static boolean matches(String prefix, String key) {
		return PropertyName.uniform(prefix).equals(PropertyName.uniform(key)) || PropertyName.isAncestorOf(prefix, key);
	}

}
