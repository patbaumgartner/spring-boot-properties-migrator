package com.patbaumgartner.spring.migrator.core;

import org.springframework.boot.configurationmetadata.Deprecation;

/**
 * A deprecated configuration property as described by Spring Boot configuration metadata.
 *
 * @param key the canonical name of the deprecated property
 * @param replacement the canonical name of the property that replaces it, or {@code null}
 * when there is none
 * @param reason the short reason recorded in the metadata, or {@code null}
 * @param level the deprecation level, or {@code null} when the metadata omits it
 */
public record DeprecatedProperty(String key, String replacement, String reason, Deprecation.Level level) {

	public DeprecatedProperty(String key, String replacement, String reason) {
		this(key, replacement, reason, null);
	}

	/**
	 * Returns whether the metadata names a property to migrate to.
	 * @return whether a replacement is available
	 */
	public boolean hasReplacement() {
		return this.replacement != null && !this.replacement.isBlank();
	}

	/**
	 * Returns whether the property is already removed rather than merely discouraged.
	 * @return whether the deprecation level is {@code error}
	 */
	public boolean isRemoved() {
		return this.level == Deprecation.Level.ERROR;
	}

}
