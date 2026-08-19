package com.patbaumgartner.spring.migrator.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;
import org.springframework.boot.configurationmetadata.Deprecation;

/**
 * The set of deprecated properties known to the configuration metadata on a project's
 * classpath, indexed so that lookups honour Spring Boot's relaxed binding.
 * <p>
 * The catalog also knows the declared type of every metadata property, which lets the
 * engine detect renames whose value would no longer be valid, and it resolves chained
 * deprecations ({@code a -> b -> c}) to their terminal replacement so that migrating
 * twice is a no-op.
 */
public final class DeprecationCatalog {

	private static final int MAX_CHAIN_LENGTH = 16;

	private final Map<String, DeprecatedProperty> deprecations;

	private final Map<String, String> types;

	private DeprecationCatalog(Map<String, DeprecatedProperty> deprecations, Map<String, String> types) {
		this.deprecations = deprecations;
		this.types = types;
	}

	/**
	 * Builds a catalog from raw configuration metadata properties.
	 * @param metadataProperties all properties known to the resolved metadata repository
	 * @return the catalog
	 */
	public static DeprecationCatalog from(Map<String, ConfigurationMetadataProperty> metadataProperties) {
		Map<String, DeprecatedProperty> deprecations = new LinkedHashMap<>();
		Map<String, String> types = new HashMap<>();
		metadataProperties.forEach((name, property) -> {
			types.put(PropertyName.uniform(name), property.getType());
			Deprecation deprecation = property.getDeprecation();
			if (!property.isDeprecated() || deprecation == null) {
				return;
			}
			deprecations.put(PropertyName.uniform(name), new DeprecatedProperty(name, deprecation.getReplacement(),
					deprecation.getShortReason(), deprecation.getLevel()));
		});
		return new DeprecationCatalog(deprecations, types);
	}

	/**
	 * Returns an empty catalog, used when no metadata could be resolved.
	 * @return an empty catalog
	 */
	public static DeprecationCatalog empty() {
		return new DeprecationCatalog(Map.of(), Map.of());
	}

	/**
	 * Looks up a deprecated property, honouring relaxed binding so that
	 * {@code server.maxHttpHeaderSize} matches {@code server.max-http-header-size}.
	 * @param key the property name exactly as written in the configuration file
	 * @return the deprecation, if the property is deprecated
	 */
	public Optional<DeprecatedProperty> find(String key) {
		return Optional.ofNullable(this.deprecations.get(PropertyName.uniform(key)));
	}

	/**
	 * Follows chained deprecations to the property a user should end up with.
	 * <p>
	 * Metadata may declare {@code a -> b} while {@code b} is itself deprecated in favour
	 * of {@code c}. Migrating straight to {@code c} keeps a second migration run a no-op.
	 * @param property the deprecated property to resolve
	 * @return the terminal replacement, or empty when the chain is cyclic
	 */
	public Optional<String> resolveReplacement(DeprecatedProperty property) {
		if (!property.hasReplacement()) {
			return Optional.empty();
		}
		Set<String> seen = new HashSet<>();
		seen.add(PropertyName.uniform(property.key()));
		String current = property.replacement();
		for (int hop = 0; hop < MAX_CHAIN_LENGTH; hop++) {
			if (!seen.add(PropertyName.uniform(current))) {
				return Optional.empty();
			}
			DeprecatedProperty next = this.deprecations.get(PropertyName.uniform(current));
			if (next == null || !next.hasReplacement()) {
				return Optional.of(current);
			}
			current = next.replacement();
		}
		return Optional.empty();
	}

	/**
	 * Returns whether migrating {@code from} to {@code to} is known to change the value
	 * type, which means the existing value is unlikely to remain valid.
	 * <p>
	 * A missing type on either side is not treated as a mismatch: the metadata simply
	 * does not say, and refusing to migrate then would block many safe renames.
	 * @param from the deprecated property name
	 * @param to the replacement property name
	 * @return whether both types are known and differ
	 */
	public boolean changesType(String from, String to) {
		String fromType = this.types.get(PropertyName.uniform(from));
		String toType = this.types.get(PropertyName.uniform(to));
		return fromType != null && toType != null && !fromType.equals(toType);
	}

	/**
	 * Returns the declared type of a property, when the metadata knows it.
	 * @param key the property name
	 * @return the fully qualified type name
	 */
	public Optional<String> typeOf(String key) {
		return Optional.ofNullable(this.types.get(PropertyName.uniform(key)));
	}

	/**
	 * Returns whether the metadata describes the given key, honouring the fact that
	 * aggregate properties are declared at their prefix rather than per entry.
	 * <p>
	 * A {@code Map}-typed property such as {@code logging.level} stands for every key
	 * written below it, and a collection-typed property such as
	 * {@code spring.config.import} stands for every indexed element. Without that rule
	 * every map entry in a project would look unrecognised.
	 * @param key the property name exactly as written in the configuration file
	 * @return whether the key is backed by a metadata property
	 */
	public boolean isKnown(String key) {
		if (this.types.containsKey(PropertyName.uniform(key))) {
			return true;
		}
		for (String ancestor : PropertyName.ancestors(key)) {
			String uniformAncestor = PropertyName.uniform(ancestor);
			if (!this.types.containsKey(uniformAncestor)) {
				continue;
			}
			if (absorbsDescendants(uniformAncestor)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether a metadata property stands for keys written below it.
	 * <p>
	 * Aggregates do so by definition. So does any property whose type the metadata never
	 * decomposes into child properties: the migrator cannot see inside it, and guessing
	 * that a key below it is wrong would be exactly the false positive this check must
	 * avoid.
	 * @param uniformName the uniform name of the metadata property
	 * @return whether descendants of the property count as known
	 */
	private boolean absorbsDescendants(String uniformName) {
		if (AggregateTypes.isAggregate(this.types.get(uniformName))) {
			return true;
		}
		return !hasChildren(uniformName);
	}

	private boolean hasChildren(String uniformName) {
		String prefix = uniformName + ".";
		for (String candidate : this.types.keySet()) {
			if (candidate.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether the metadata describes at least one property in a namespace.
	 * <p>
	 * This is what tells an absent optional module apart from a wrong key. When Actuator
	 * is not on the classpath nothing describes {@code management.*}, and every key a
	 * project writes there would otherwise look unrecognised.
	 * @param namespace the top-level namespace, such as {@code management}
	 * @return whether any known property lives under the namespace
	 */
	public boolean describesNamespace(String namespace) {
		String prefix = PropertyName.uniform(namespace) + ".";
		for (String candidate : this.types.keySet()) {
			if (candidate.startsWith(prefix)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Returns whether the catalog holds no metadata at all, which usually means the
	 * project classpath could not be resolved rather than that the project is clean.
	 * @return whether no metadata was loaded
	 */
	public boolean isEmpty() {
		return this.types.isEmpty();
	}

	/**
	 * Returns how many deprecated properties the catalog knows about.
	 * @return the number of deprecated properties
	 */
	public int deprecationCount() {
		return this.deprecations.size();
	}

}
