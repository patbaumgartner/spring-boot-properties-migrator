package com.patbaumgartner.spring.migrator.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes configuration property names so that names written in any of Spring Boot's
 * accepted forms resolve to the same lookup key.
 * <p>
 * Spring Boot's relaxed binding treats the following as the same property:
 * {@code server.max-http-header-size}, {@code server.maxHttpHeaderSize} and
 * {@code server.max_http_header_size}. All three share the uniform form
 * {@code server.maxhttpheadersize}.
 * <p>
 * Only {@code .} separates path elements. Environment-variable notation such as
 * {@code SERVER_MAX_HTTP_HEADER_SIZE} is deliberately <em>not</em> considered equivalent,
 * because underscores are element separators only for the system environment property
 * source, never inside a {@code .properties} or YAML file.
 */
final class PropertyName {

	private PropertyName() {
	}

	/**
	 * Returns the uniform form of the given property name: lower-cased with {@code -} and
	 * {@code _} removed from each element.
	 * @param name the property name as written by the user
	 * @return the uniform form used for equality comparisons
	 */
	static String uniform(String name) {
		StringBuilder uniform = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char candidate = name.charAt(i);
			if (candidate == '-' || candidate == '_') {
				continue;
			}
			uniform.append(Character.toLowerCase(candidate));
		}
		return uniform.toString();
	}

	/**
	 * Returns whether {@code prefix} is an element-wise ancestor path of {@code name}. An
	 * empty prefix is an ancestor of every name.
	 * @param prefix the candidate ancestor path
	 * @param name the full property name
	 * @return whether the name is nested below the prefix
	 */
	static boolean isAncestorOf(String prefix, String name) {
		if (prefix.isEmpty()) {
			return true;
		}
		String uniformPrefix = uniform(prefix);
		String uniformName = uniform(name);
		return uniformName.length() > uniformPrefix.length() && uniformName.startsWith(uniformPrefix)
				&& uniformName.charAt(uniformPrefix.length()) == '.';
	}

	/**
	 * Returns the portion of {@code name} below {@code prefix}, keeping the original
	 * (non-uniform) spelling of the remaining elements.
	 * @param prefix the ancestor path, which must satisfy {@link #isAncestorOf}
	 * @param name the full property name
	 * @return the relative name below the prefix
	 */
	static String relativize(String prefix, String name) {
		if (prefix.isEmpty()) {
			return name;
		}
		int elements = countElements(prefix);
		int offset = 0;
		for (int i = 0; i < elements; i++) {
			offset = name.indexOf('.', offset) + 1;
		}
		return name.substring(offset);
	}

	private static int countElements(String name) {
		int elements = 1;
		for (int i = 0; i < name.length(); i++) {
			if (name.charAt(i) == '.') {
				elements++;
			}
		}
		return elements;
	}

	/**
	 * Splits a property name into its path elements, treating {@code .} as a separator
	 * only outside brackets.
	 * <p>
	 * Bracket awareness matters because a map key may itself contain dots:
	 * {@code logging.level[com.example]} has the elements {@code logging} and
	 * {@code level[com.example]}, not five elements. An index such as {@code [0]} stays
	 * attached to the element it indexes, so {@code clients[0].timeout} yields
	 * {@code clients[0]} and {@code timeout}.
	 * @param name the property name as written by the user
	 * @return the path elements, in order
	 */
	static List<String> elements(String name) {
		List<String> elements = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int depth = 0;
		for (int i = 0; i < name.length(); i++) {
			char candidate = name.charAt(i);
			if (candidate == '[') {
				depth++;
			}
			else if (candidate == ']') {
				depth = Math.max(0, depth - 1);
			}
			if (candidate == '.' && depth == 0) {
				elements.add(current.toString());
				current.setLength(0);
				continue;
			}
			current.append(candidate);
		}
		elements.add(current.toString());
		return elements;
	}

	/**
	 * Returns the strict ancestor paths of a name, longest first.
	 * <p>
	 * An indexed element contributes both its indexed and its un-indexed form, because
	 * metadata declares the aggregate itself without the index:
	 * {@code clients[0].timeout} must be able to find the metadata property
	 * {@code clients}.
	 * @param name the full property name
	 * @return the ancestor paths, from the longest to the shortest
	 */
	static List<String> ancestors(String name) {
		List<String> elements = elements(name);
		List<String> ancestors = new ArrayList<>();
		String withoutBrackets = stripBrackets(name);
		if (!withoutBrackets.equals(name)) {
			ancestors.add(withoutBrackets);
		}
		String withoutIndexes = stripIndexes(name);
		if (!withoutIndexes.equals(name) && !withoutIndexes.equals(withoutBrackets)) {
			ancestors.add(withoutIndexes);
		}
		for (int length = elements.size() - 1; length >= 1; length--) {
			String path = String.join(".", elements.subList(0, length));
			ancestors.add(path);
			String bare = stripBrackets(path);
			if (!bare.equals(path)) {
				ancestors.add(bare);
			}
		}
		return ancestors;
	}

	/**
	 * Removes every bracketed token from a name, whether it indexes a collection or names
	 * a map entry.
	 * <p>
	 * A map entry may be written either as {@code logging.level.com.example} or as
	 * {@code logging.level[com.example]}. Both have to resolve to the metadata property
	 * {@code logging.level}, and only stripping the brackets exposes it.
	 * @param name the property name
	 * @return the name with all bracketed tokens removed
	 */
	static String stripBrackets(String name) {
		if (name.indexOf('[') < 0) {
			return name;
		}
		StringBuilder result = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char candidate = name.charAt(i);
			if (candidate != '[') {
				result.append(candidate);
				continue;
			}
			int close = name.indexOf(']', i);
			if (close < 0) {
				break;
			}
			i = close;
		}
		return result.toString();
	}

	/**
	 * Removes every bracketed index from a name, keeping non-numeric bracketed map keys.
	 * @param name the property name
	 * @return the name without numeric indexes
	 */
	static String stripIndexes(String name) {
		if (name.indexOf('[') < 0) {
			return name;
		}
		StringBuilder result = new StringBuilder(name.length());
		for (int i = 0; i < name.length(); i++) {
			char candidate = name.charAt(i);
			if (candidate != '[') {
				result.append(candidate);
				continue;
			}
			int close = name.indexOf(']', i);
			if (close < 0) {
				result.append(name.substring(i));
				break;
			}
			String inside = name.substring(i + 1, close);
			if (!isNumeric(inside)) {
				result.append(name, i, close + 1);
			}
			i = close;
		}
		return result.toString();
	}

	/**
	 * Returns whether a bracketed token is a collection index rather than a map key.
	 * @param value the text between brackets
	 * @return whether the value is a non-empty run of digits
	 */
	static boolean isNumeric(String value) {
		if (value.isEmpty()) {
			return false;
		}
		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

}
