package com.patbaumgartner.spring.migrator.core;

public record MigrationChange(String file, int line, String key, String replacement, String reason,
		boolean unsupported) {
}
