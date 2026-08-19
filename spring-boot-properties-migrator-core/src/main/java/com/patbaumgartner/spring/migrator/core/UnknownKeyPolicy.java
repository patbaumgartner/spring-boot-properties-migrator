package com.patbaumgartner.spring.migrator.core;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * What to do about configuration keys that no metadata on the project classpath
 * describes.
 * <p>
 * This is deliberately separate from {@link FailurePolicy}. A deprecated property is a
 * fact stated by the metadata, whereas an unrecognised key is only an absence of
 * evidence: the key may have been removed from Spring Boot, or it may be read by
 * application code the migrator cannot see. Folding the two together would let an
 * advisory finding break builds that were configured to fail only on real deprecations.
 */
public enum UnknownKeyPolicy {

	/**
	 * Do not look for unrecognised keys at all.
	 */
	OFF,

	/**
	 * Report unrecognised keys without ever failing the build.
	 */
	REPORT,

	/**
	 * Report unrecognised keys and fail the build when any remains.
	 */
	FAIL;

	/**
	 * Parses a policy name, ignoring case and surrounding whitespace.
	 * @param value the configured value, which may be {@code null} or blank
	 * @return the parsed policy, defaulting to {@link #OFF}
	 * @throws IllegalArgumentException if the value names no policy
	 */
	public static UnknownKeyPolicy parse(String value) {
		if (value == null || value.isBlank()) {
			return OFF;
		}
		String normalized = value.strip().toUpperCase(Locale.ROOT);
		for (UnknownKeyPolicy policy : values()) {
			if (policy.name().equals(normalized)) {
				return policy;
			}
		}
		throw new IllegalArgumentException(
				"Unknown unknownKeys value '" + value + "'. Valid values are " + names() + ".");
	}

	/**
	 * Returns the accepted values, for use in error messages.
	 * @return a human-readable list of policy names
	 */
	public static String names() {
		return Stream.of(values())
			.map((policy) -> policy.name().toLowerCase(Locale.ROOT))
			.collect(Collectors.joining(", "));
	}

	/**
	 * Returns whether the detector should run.
	 * @return whether unrecognised keys are looked for
	 */
	public boolean isEnabled() {
		return this != OFF;
	}

	/**
	 * Returns whether the plan violates this policy.
	 * @param plan the analysed plan
	 * @return whether the build should fail
	 */
	public boolean isViolatedBy(MigrationPlan plan) {
		return this == FAIL && !plan.changes(Outcome.UNKNOWN).isEmpty();
	}

	/**
	 * Describes why the policy was violated.
	 * @param plan the analysed plan
	 * @return a message for the build failure
	 */
	public String describeViolation(MigrationPlan plan) {
		int unknown = plan.changes(Outcome.UNKNOWN).size();
		return unknown + " configuration key" + ((unknown == 1) ? " is" : "s are")
				+ " not described by the configuration metadata on the resolved project classpath";
	}

}
