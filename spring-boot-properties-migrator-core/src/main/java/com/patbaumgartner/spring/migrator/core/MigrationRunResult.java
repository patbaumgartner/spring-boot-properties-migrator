package com.patbaumgartner.spring.migrator.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MigrationRunResult {

	private final List<MigrationChange> renamed = new ArrayList<>();

	private final List<MigrationChange> unsupported = new ArrayList<>();

	public void addRenamed(MigrationChange change) {
		this.renamed.add(change);
	}

	public void addUnsupported(MigrationChange change) {
		this.unsupported.add(change);
	}

	public List<MigrationChange> getRenamed() {
		return Collections.unmodifiableList(this.renamed);
	}

	public List<MigrationChange> getUnsupported() {
		return Collections.unmodifiableList(this.unsupported);
	}

	public int totalFindings() {
		return this.renamed.size() + this.unsupported.size();
	}

	public String renderSummary(boolean dryRun) {
		StringBuilder sb = new StringBuilder(512);
		sb.append("Spring Boot Properties Migration ")
			.append(dryRun ? "(dry-run)" : "(apply)")
			.append(System.lineSeparator());
		sb.append("Renamed keys: ").append(this.renamed.size()).append(System.lineSeparator());
		sb.append("Unsupported keys: ")
			.append(this.unsupported.size())
			.append(System.lineSeparator())
			.append(System.lineSeparator());

		if (!this.renamed.isEmpty()) {
			sb.append("Renamed").append(System.lineSeparator());
			for (MigrationChange change : this.renamed) {
				sb.append("- ")
					.append(change.file())
					.append(":")
					.append(change.line())
					.append("  ")
					.append(change.key())
					.append(" -> ")
					.append(change.replacement())
					.append(System.lineSeparator());
			}
			sb.append(System.lineSeparator());
		}

		if (!this.unsupported.isEmpty()) {
			sb.append("Unsupported").append(System.lineSeparator());
			for (MigrationChange change : this.unsupported) {
				sb.append("- ")
					.append(change.file())
					.append(":")
					.append(change.line())
					.append("  ")
					.append(change.key());
				if (change.reason() != null && !change.reason().isBlank()) {
					sb.append(" (reason: ").append(change.reason()).append(")");
				}
				sb.append(System.lineSeparator());
			}
			sb.append(System.lineSeparator());
		}

		return sb.toString();
	}

}
