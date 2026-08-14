package com.patbaumgartner.spring.migrator.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Plans and applies migrations of deprecated Spring Boot configuration properties.
 * <p>
 * The engine never rewrites a whole file. It parses a document, locates the exact source
 * span of each deprecated key and replaces only those spans, so line terminators,
 * indentation, comments, quoting and the presence or absence of a trailing newline all
 * survive untouched, and the resulting diff shows only the keys that actually changed.
 * <p>
 * A key is rewritten only when doing so is provably safe. Anything else - a replacement
 * whose value type changed, a target that already exists, several keys collapsing onto
 * one replacement, or a YAML key whose replacement belongs under a different parent - is
 * reported for manual action rather than guessed at.
 */
public final class MigrationEngine {

	private static final char BOM = '\uFEFF';

	/**
	 * Analyses the given files without modifying anything.
	 * @param rootDir the project root, used to render relative paths
	 * @param files the configuration files to analyse
	 * @param catalog the deprecations known to the project's classpath
	 * @param springBootVersion the detected Spring Boot version, or {@code null}
	 * @return the plan describing every finding and every pending write
	 */
	public MigrationPlan plan(Path rootDir, List<Path> files, DeprecationCatalog catalog, String springBootVersion) {
		List<MigrationChange> changes = new ArrayList<>();
		Map<Path, String> pending = new LinkedHashMap<>();
		List<String> diagnostics = new ArrayList<>();

		if (catalog.isEmpty()) {
			diagnostics.add("No Spring Boot configuration metadata was found on the project classpath, "
					+ "so no deprecated property can be detected. Check that the project's dependencies resolve.");
		}

		for (Path file : files) {
			planFile(rootDir, file, catalog, changes, pending, diagnostics);
		}
		return new MigrationPlan(changes, pending, diagnostics, files.size(),
				describeMetadata(catalog, springBootVersion));
	}

	/**
	 * Writes every pending change from a plan.
	 * <p>
	 * Each file is written to a sibling temporary file and then moved into place, so an
	 * interrupted run cannot leave a half-written configuration file behind.
	 * @param plan the plan to apply
	 * @throws IOException if a file cannot be written
	 */
	public void apply(MigrationPlan plan) throws IOException {
		for (Map.Entry<Path, String> entry : plan.pendingContent().entrySet()) {
			writeAtomically(entry.getKey(), entry.getValue());
		}
	}

	private void planFile(Path rootDir, Path file, DeprecationCatalog catalog, List<MigrationChange> changes,
			Map<Path, String> pending, List<String> diagnostics) {
		String relativePath = relativize(rootDir, file);
		String raw;
		try {
			raw = Files.readString(file, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			diagnostics.add("Skipped " + relativePath + ": " + ex.getMessage());
			return;
		}

		boolean hasBom = !raw.isEmpty() && raw.charAt(0) == BOM;
		String text = hasBom ? raw.substring(1) : raw;

		DocumentKeys document = parse(file, text);
		String restriction = document.restriction();
		if (restriction == null) {
			restriction = writeRestrictionFor(file);
		}

		List<Candidate> candidates = collectCandidates(document, catalog);
		if (candidates.isEmpty()) {
			return;
		}

		Set<String> present = new HashSet<>();
		document.keys().forEach((key) -> present.add(PropertyName.uniform(key.name())));
		Map<String, Long> targetCounts = countTargets(candidates);

		List<Edit> edits = new ArrayList<>();
		for (Candidate candidate : candidates) {
			changes.add(decide(candidate, relativePath, catalog, present, targetCounts, restriction, edits));
		}

		if (!edits.isEmpty()) {
			pending.put(file, (hasBom ? String.valueOf(BOM) : "") + applyEdits(text, edits));
		}
	}

	private List<Candidate> collectCandidates(DocumentKeys document, DeprecationCatalog catalog) {
		List<Candidate> candidates = new ArrayList<>();
		for (KeyOccurrence occurrence : document.keys()) {
			catalog.find(occurrence.name())
				.ifPresent((property) -> candidates
					.add(new Candidate(occurrence, property, catalog.resolveReplacement(property))));
		}
		return candidates;
	}

	private Map<String, Long> countTargets(List<Candidate> candidates) {
		Map<String, Long> counts = new HashMap<>();
		for (Candidate candidate : candidates) {
			candidate.replacement().ifPresent((target) -> counts.merge(PropertyName.uniform(target), 1L, Long::sum));
		}
		return counts;
	}

	private MigrationChange decide(Candidate candidate, String relativePath, DeprecationCatalog catalog,
			Set<String> present, Map<String, Long> targetCounts, String restriction, List<Edit> edits) {
		KeyOccurrence occurrence = candidate.occurrence();
		DeprecatedProperty property = candidate.property();
		String reason = property.reason();
		int line = occurrence.line();
		String key = occurrence.name();

		if (!property.hasReplacement()) {
			return MigrationChange.unsupported(relativePath, line, key, reason, property.isRemoved()
					? "Spring Boot no longer reads this property, so it currently has no effect" : null);
		}
		if (candidate.replacement().isEmpty()) {
			return MigrationChange.manual(relativePath, line, key, property.replacement(), reason,
					"the metadata declares a circular replacement chain");
		}

		String target = candidate.replacement().get();
		if (catalog.changesType(key, target)) {
			return MigrationChange.manual(relativePath, line, key, target, reason,
					"the replacement holds a " + simpleTypeName(catalog.typeOf(target)) + " where this property held a "
							+ simpleTypeName(catalog.typeOf(key)) + ", so the current value must be converted by hand");
		}
		if (present.contains(PropertyName.uniform(target))) {
			return MigrationChange.manual(relativePath, line, key, target, reason,
					"the file already defines " + target + "; migrating would create a duplicate key");
		}
		if (targetCounts.getOrDefault(PropertyName.uniform(target), 0L) > 1) {
			return MigrationChange.manual(relativePath, line, key, target, reason,
					"several deprecated keys in this file map to " + target
							+ ", so their values have to be merged by hand");
		}
		if (restriction != null) {
			return MigrationChange.manual(relativePath, line, key, target, reason, restriction);
		}
		if (!PropertyName.isAncestorOf(occurrence.ancestorPath(), target)) {
			return MigrationChange.manual(relativePath, line, key, target, reason,
					"the replacement belongs under '" + parentOf(target) + "' but this key is nested under '"
							+ occurrence.ancestorPath() + "', so it has to be moved by hand");
		}

		edits.add(new Edit(occurrence.start(), occurrence.end(),
				PropertyName.relativize(occurrence.ancestorPath(), target)));
		return MigrationChange.migrated(relativePath, line, key, target, reason);
	}

	private static String applyEdits(String text, List<Edit> edits) {
		List<Edit> ordered = new ArrayList<>(edits);
		ordered.sort(Comparator.comparingInt(Edit::start).reversed());
		StringBuilder result = new StringBuilder(text);
		for (Edit edit : ordered) {
			result.replace(edit.start(), edit.end(), edit.replacement());
		}
		return result.toString();
	}

	private static void writeAtomically(Path file, String content) throws IOException {
		Path directory = (file.getParent() != null) ? file.getParent() : file.toAbsolutePath().getParent();
		Path temporary = Files.createTempFile(directory, file.getFileName().toString(), ".migrator");
		try {
			copyPermissions(file, temporary);
			Files.writeString(temporary, content, StandardCharsets.UTF_8);
			try {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			}
			catch (AtomicMoveNotSupportedException ex) {
				Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally {
			Files.deleteIfExists(temporary);
		}
	}

	private static void copyPermissions(Path source, Path target) throws IOException {
		if (!Files.getFileStore(source).supportsFileAttributeView("posix")) {
			return;
		}
		Files.setPosixFilePermissions(target, Files.getPosixFilePermissions(source));
	}

	private static DocumentKeys parse(Path file, String text) {
		String name = file.getFileName().toString();
		if (name.endsWith(".yml") || name.endsWith(".yaml")) {
			return YamlKeyParser.parse(text);
		}
		return PropertiesKeyParser.parse(text);
	}

	/**
	 * Returns why a file must not be rewritten even though its content parsed cleanly.
	 * @param file the candidate file
	 * @return the restriction, or {@code null} when the file may be rewritten
	 */
	private static String writeRestrictionFor(Path file) {
		if (Files.isSymbolicLink(file)) {
			return "the file is a symbolic link, so rewriting it would change a file outside the project";
		}
		if (!Files.isWritable(file)) {
			return "the file is read-only";
		}
		return null;
	}

	private static String describeMetadata(DeprecationCatalog catalog, String springBootVersion) {
		String description = catalog.deprecationCount() + " deprecated propert"
				+ (catalog.deprecationCount() == 1 ? "y" : "ies");
		return (springBootVersion != null && !springBootVersion.isBlank())
				? description + " (Spring Boot " + springBootVersion + ")" : description;
	}

	private static String simpleTypeName(Optional<String> type) {
		return type.map((name) -> name.substring(Math.max(name.lastIndexOf('.'), name.lastIndexOf('$')) + 1))
			.orElse("value");
	}

	private static String parentOf(String key) {
		int separator = key.lastIndexOf('.');
		return (separator < 0) ? "" : key.substring(0, separator);
	}

	private static String relativize(Path rootDir, Path file) {
		try {
			return rootDir.relativize(file).toString().replace('\\', '/');
		}
		catch (IllegalArgumentException ex) {
			return file.toString().replace('\\', '/');
		}
	}

	private record Candidate(KeyOccurrence occurrence, DeprecatedProperty property, Optional<String> replacement) {
	}

	private record Edit(int start, int end, String replacement) {
	}

}
