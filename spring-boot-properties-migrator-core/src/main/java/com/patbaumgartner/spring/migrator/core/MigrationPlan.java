package com.patbaumgartner.spring.migrator.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The outcome of analysing a project: every deprecated property that was found, why the
 * migrator classified it the way it did, and the exact file content that would be written
 * if the plan is applied.
 * <p>
 * Analysis and mutation are separate on purpose. Callers can inspect a plan, decide to
 * fail the build, and never touch a single file, which avoids the trap of rewriting some
 * files and then aborting because of a finding elsewhere.
 */
public final class MigrationPlan {

	private final List<MigrationChange> changes;

	private final Map<Path, String> pendingContent;

	private final List<String> diagnostics;

	private final int scannedFiles;

	private final String metadataDescription;

	MigrationPlan(List<MigrationChange> changes, Map<Path, String> pendingContent, List<String> diagnostics,
			int scannedFiles, String metadataDescription) {
		this.changes = List.copyOf(changes);
		this.pendingContent = Map.copyOf(pendingContent);
		this.diagnostics = List.copyOf(diagnostics);
		this.scannedFiles = scannedFiles;
		this.metadataDescription = metadataDescription;
	}

	/**
	 * Returns every finding, in file and line order.
	 * @return all changes
	 */
	public List<MigrationChange> changes() {
		return this.changes;
	}

	/**
	 * Returns the findings with a given outcome.
	 * @param outcome the outcome to filter by
	 * @return the matching changes
	 */
	public List<MigrationChange> changes(Outcome outcome) {
		return this.changes.stream().filter((change) -> change.outcome() == outcome).toList();
	}

	/**
	 * Returns messages about conditions that make the result less trustworthy, such as
	 * unreadable files or an empty metadata repository.
	 * @return the diagnostics
	 */
	public List<String> diagnostics() {
		return this.diagnostics;
	}

	/**
	 * Returns whether any deprecated property was found.
	 * @return whether the plan has findings
	 */
	public boolean hasFindings() {
		return !this.changes.isEmpty();
	}

	/**
	 * Returns whether applying the plan would modify any file.
	 * @return whether there is anything to write
	 */
	public boolean hasPendingWrites() {
		return !this.pendingContent.isEmpty();
	}

	Map<Path, String> pendingContent() {
		return this.pendingContent;
	}

	/**
	 * Renders a human-readable report.
	 * @param applied whether the plan has been written to disk
	 * @return the report text
	 */
	public String render(boolean applied) {
		String newline = System.lineSeparator();
		StringBuilder report = new StringBuilder(512);
		report.append("Spring Boot Properties Migration ").append(applied ? "(applied)" : "(analysis)").append(newline);
		report.append("Scanned ")
			.append(this.scannedFiles)
			.append(this.scannedFiles == 1 ? " file" : " files")
			.append(" against ")
			.append(this.metadataDescription)
			.append(newline);
		report.append(newline);

		appendSection(report, newline, applied ? "Migrated" : "Ready to migrate", Outcome.MIGRATED);
		appendSection(report, newline, "Needs manual action", Outcome.MANUAL);
		appendSection(report, newline, "Deprecated with no replacement", Outcome.UNSUPPORTED);

		if (!this.changes.isEmpty()) {
			report.append("Summary: ")
				.append(changes(Outcome.MIGRATED).size())
				.append(applied ? " migrated, " : " ready, ")
				.append(changes(Outcome.MANUAL).size())
				.append(" manual, ")
				.append(changes(Outcome.UNSUPPORTED).size())
				.append(" without replacement")
				.append(newline);
		}
		else {
			report.append("No deprecated properties found.").append(newline);
		}

		if (!this.diagnostics.isEmpty()) {
			report.append(newline).append("Warnings").append(newline);
			for (String diagnostic : this.diagnostics) {
				report.append("  ! ").append(diagnostic).append(newline);
			}
		}
		return report.toString();
	}

	private void appendSection(StringBuilder report, String newline, String title, Outcome outcome) {
		List<MigrationChange> section = changes(outcome);
		if (section.isEmpty()) {
			return;
		}
		report.append(title).append(" (").append(section.size()).append(")").append(newline);
		for (MigrationChange change : section) {
			report.append("  ")
				.append(change.file())
				.append(":")
				.append(change.line())
				.append("  ")
				.append(change.key());
			if (change.replacement() != null) {
				report.append(" -> ").append(change.replacement());
			}
			report.append(newline);
			appendDetail(report, newline, "why", change.advice());
			appendDetail(report, newline, "reason", change.reason());
		}
		report.append(newline);
	}

	private void appendDetail(StringBuilder report, String newline, String label, String value) {
		if (value != null && !value.isBlank()) {
			report.append("      ").append(label).append(": ").append(value.strip()).append(newline);
		}
	}

}
