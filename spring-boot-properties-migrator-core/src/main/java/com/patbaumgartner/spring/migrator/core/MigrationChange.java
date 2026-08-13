package com.patbaumgartner.spring.migrator.core;

/**
 * One deprecated property found in one configuration file.
 *
 * @param file the file path relative to the project root
 * @param line the one-based line the key appears on
 * @param key the deprecated key exactly as the user wrote it
 * @param replacement the property to migrate to, or {@code null} when there is none
 * @param outcome what the migrator decided to do
 * @param reason the deprecation reason recorded in the metadata, or {@code null}
 * @param advice why manual action is required, or {@code null} when none is
 */
public record MigrationChange(String file, int line, String key, String replacement, Outcome outcome, String reason,
		String advice) {

	static MigrationChange migrated(String file, int line, String key, String replacement, String reason) {
		return new MigrationChange(file, line, key, replacement, Outcome.MIGRATED, reason, null);
	}

	static MigrationChange manual(String file, int line, String key, String replacement, String reason, String advice) {
		return new MigrationChange(file, line, key, replacement, Outcome.MANUAL, reason, advice);
	}

	static MigrationChange unsupported(String file, int line, String key, String reason) {
		return new MigrationChange(file, line, key, null, Outcome.UNSUPPORTED, reason, null);
	}

}
