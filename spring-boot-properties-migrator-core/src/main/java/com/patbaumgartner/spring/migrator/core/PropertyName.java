package com.patbaumgartner.spring.migrator.core;

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

}
