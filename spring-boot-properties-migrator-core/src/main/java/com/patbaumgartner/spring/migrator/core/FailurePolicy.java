package com.patbaumgartner.spring.migrator.core;

import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * When a migration run should fail the build.
 * <p>
 * Replaces a plain {@code failOnError} flag, which only ever considered properties that
 * had no replacement and so could not express the case teams actually want in CI: fail
 * while any deprecated property remains.
 */
public enum FailurePolicy {

	/**
	 * Report findings but always succeed.
	 */
	NEVER,

	/**
	 * Fail when a finding needs a human, either because migrating it is not provably safe
	 * or because the property has no replacement at all.
	 */
	MANUAL,

	/**
	 * Fail when any deprecated property is present, including ones that were or could be
	 * migrated automatically.
	 */
	ANY;

	/**
	 * Parses a policy name, ignoring case and surrounding whitespace.
	 * @param value the configured value, which may be {@code null} or blank
	 * @return the parsed policy, defaulting to {@link #NEVER}
	 * @throws IllegalArgumentException if the value names no policy
	 */
	public static FailurePolicy parse(String value) {
		if (value == null || value.isBlank()) {
			return NEVER;
		}
		String normalized = value.strip().toUpperCase(Locale.ROOT);
		for (FailurePolicy policy : values()) {
			if (policy.name().equals(normalized)) {
				return policy;
			}
		}
		throw new IllegalArgumentException("Unknown failOn value '" + value + "'. Valid values are " + names() + ".");
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
	 * Returns whether the plan violates this policy.
	 * @param plan the analysed plan
	 * @return whether the build should fail
	 */
	public boolean isViolatedBy(MigrationPlan plan) {
		return switch (this) {
			case NEVER -> false;
			case MANUAL -> !plan.changes(Outcome.MANUAL).isEmpty() || !plan.changes(Outcome.UNSUPPORTED).isEmpty();
			case ANY -> plan.hasFindings();
		};
	}

	/**
	 * Describes why the policy was violated.
	 * @param plan the analysed plan
	 * @return a message for the build failure
	 */
	public String describeViolation(MigrationPlan plan) {
		int manual = plan.changes(Outcome.MANUAL).size();
		int unsupported = plan.changes(Outcome.UNSUPPORTED).size();
		return switch (this) {
			case NEVER -> "";
			case MANUAL ->
				manual + " deprecated propert" + ((manual == 1) ? "y needs" : "ies need") + " manual action and "
						+ unsupported + " ha" + ((unsupported == 1) ? "s" : "ve") + " no replacement";
			case ANY -> plan.deprecations().size() + " deprecated propert"
					+ ((plan.deprecations().size() == 1) ? "y" : "ies") + " found";
		};
	}

}
