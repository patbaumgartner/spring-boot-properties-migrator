package com.patbaumgartner.spring.migrator.core;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.configurationmetadata.ConfigurationMetadataProperty;

public final class MigrationEngine {

	private static final Pattern YAML_KEY_PATTERN = Pattern.compile("^(\\s*)([A-Za-z0-9_.-]+)(\\s*:\\s*.*)$");

	public MigrationRunResult run(Path rootDir, List<Path> files, Map<String, DeprecatedProperty> deprecatedByKey,
			boolean applyChanges) throws IOException {
		MigrationRunResult result = new MigrationRunResult();
		for (Path file : files) {
			String name = file.getFileName().toString();
			if (name.endsWith(".properties")) {
				processProperties(rootDir, file, deprecatedByKey, applyChanges, result);
			}
			else if (name.endsWith(".yml") || name.endsWith(".yaml")) {
				processYaml(rootDir, file, deprecatedByKey, applyChanges, result);
			}
		}
		return result;
	}

	private void processProperties(Path rootDir, Path file, Map<String, DeprecatedProperty> deprecatedByKey,
			boolean applyChanges, MigrationRunResult result) throws IOException {
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		List<String> updated = new ArrayList<>(lines.size());
		boolean changed = false;

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			String key = parsePropertiesKey(line);
			if (key == null) {
				updated.add(line);
				continue;
			}

			DeprecatedProperty property = deprecatedByKey.get(key);
			if (property == null) {
				updated.add(line);
				continue;
			}

			String rel = rootDir.relativize(file).toString();
			if (property.hasReplacement()) {
				String replaced = replacePropertiesKey(line, property.replacement());
				updated.add(replaced);
				changed = changed || !Objects.equals(line, replaced);
				result
					.addRenamed(new MigrationChange(rel, i + 1, key, property.replacement(), property.reason(), false));
			}
			else {
				updated.add(line);
				result.addUnsupported(new MigrationChange(rel, i + 1, key, null, property.reason(), true));
			}
		}

		if (applyChanges && changed) {
			Files.write(file, updated, StandardCharsets.UTF_8);
		}
	}

	private void processYaml(Path rootDir, Path file, Map<String, DeprecatedProperty> deprecatedByKey,
			boolean applyChanges, MigrationRunResult result) throws IOException {
		List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
		List<String> updated = new ArrayList<>(lines);
		Deque<YamlKeyDepth> stack = new ArrayDeque<>();
		boolean changed = false;

		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (isCommentOrBlank(line) || isListItem(line)) {
				continue;
			}

			Matcher matcher = YAML_KEY_PATTERN.matcher(line);
			if (!matcher.matches()) {
				continue;
			}

			String indentText = matcher.group(1);
			String key = matcher.group(2);
			String suffix = matcher.group(3);
			int indent = indentText.length();

			while (!stack.isEmpty() && stack.peek().indent >= indent) {
				stack.pop();
			}

			String fullPath = fullPath(stack, key);
			DeprecatedProperty property = deprecatedByKey.get(fullPath);
			String rel = rootDir.relativize(file).toString();
			if (property != null) {
				if (property.hasReplacement()) {
					String replacementKey = chooseYamlReplacementKey(fullPath, property.replacement());
					String newLine = indentText + replacementKey + suffix;
					updated.set(i, newLine);
					changed = changed || !Objects.equals(line, newLine);
					result.addRenamed(new MigrationChange(rel, i + 1, fullPath, property.replacement(),
							property.reason(), false));
					stack.push(new YamlKeyDepth(indent, replacementKey));
					continue;
				}
				result.addUnsupported(new MigrationChange(rel, i + 1, fullPath, null, property.reason(), true));
			}

			stack.push(new YamlKeyDepth(indent, key));
		}

		if (applyChanges && changed) {
			Files.write(file, updated, StandardCharsets.UTF_8);
		}
	}

	private static boolean isCommentOrBlank(String line) {
		String trimmed = line.trim();
		return trimmed.isEmpty() || trimmed.startsWith("#");
	}

	private static boolean isListItem(String line) {
		String trimmed = line.trim();
		return trimmed.startsWith("-") || trimmed.startsWith("?");
	}

	private static String chooseYamlReplacementKey(String originalPath, String replacementPath) {
		String originalParent = parent(originalPath);
		String replacementParent = parent(replacementPath);
		if (Objects.equals(originalParent, replacementParent)) {
			int idx = replacementPath.lastIndexOf('.');
			return idx >= 0 ? replacementPath.substring(idx + 1) : replacementPath;
		}
		return replacementPath;
	}

	private static String parent(String key) {
		int idx = key.lastIndexOf('.');
		return idx < 0 ? "" : key.substring(0, idx);
	}

	private static String fullPath(Deque<YamlKeyDepth> stack, String key) {
		if (stack.isEmpty()) {
			return key;
		}
		List<String> keys = new ArrayList<>();
		for (YamlKeyDepth depth : stack) {
			keys.add(0, depth.key);
		}
		keys.add(key);
		return String.join(".", keys);
	}

	private static String parsePropertiesKey(String line) {
		String trimmed = line.trim();
		if (trimmed.isEmpty() || trimmed.startsWith("#") || trimmed.startsWith("!")) {
			return null;
		}

		int offset = firstNonWhitespace(line);
		if (offset < 0) {
			return null;
		}
		String body = line.substring(offset);

		int end = 0;
		while (end < body.length()) {
			char ch = body.charAt(end);
			if (ch == '=' || ch == ':' || Character.isWhitespace(ch)) {
				break;
			}
			end++;
		}

		if (end == 0) {
			return null;
		}
		return body.substring(0, end);
	}

	private static String replacePropertiesKey(String line, String replacement) {
		int offset = firstNonWhitespace(line);
		if (offset < 0) {
			return line;
		}
		String prefix = line.substring(0, offset);
		String body = line.substring(offset);

		int end = 0;
		while (end < body.length()) {
			char ch = body.charAt(end);
			if (ch == '=' || ch == ':' || Character.isWhitespace(ch)) {
				break;
			}
			end++;
		}
		if (end == 0) {
			return line;
		}

		return prefix + replacement + body.substring(end);
	}

	private static int firstNonWhitespace(String line) {
		for (int i = 0; i < line.length(); i++) {
			if (!Character.isWhitespace(line.charAt(i))) {
				return i;
			}
		}
		return -1;
	}

	public static Map<String, DeprecatedProperty> toDeprecatedMap(
			Map<String, ConfigurationMetadataProperty> metadataProperties) {
		Map<String, DeprecatedProperty> result = new LinkedHashMap<>();
		metadataProperties.forEach((key, value) -> {
			if (!value.isDeprecated() || value.getDeprecation() == null) {
				return;
			}
			String replacement = value.getDeprecation().getReplacement();
			String reason = value.getDeprecation().getShortReason();
			result.put(key, new DeprecatedProperty(key, replacement, reason));
		});
		return result;
	}

	private record YamlKeyDepth(int indent, String key) {
	}

}
