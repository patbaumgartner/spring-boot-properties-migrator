package com.patbaumgartner.spring.migrator.core;

public record DeprecatedProperty(String key, String replacement, String reason) {

	public boolean hasReplacement() {
		return replacement != null && !replacement.isBlank();
	}
}
