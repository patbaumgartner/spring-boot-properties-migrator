package com.patbaumgartner.spring.migrator.core;

import java.util.Set;

/**
 * Recognises the declared metadata types that stand for arbitrary keys written below
 * them.
 * <p>
 * Spring Boot declares a map- or collection-valued property once, at its prefix, and
 * binds every entry a user writes underneath it. {@code logging.level} is declared as a
 * {@code Map<String, String>} and absorbs {@code logging.level.com.example}; a
 * {@code List} absorbs {@code [0]}, {@code [1]} and so on.
 * <p>
 * Classification is by name rather than by loading the class, because the migrator
 * inspects a project's classpath without becoming able to run its code. Names are matched
 * exactly: a type is never treated as an aggregate merely because it ends in {@code Map}
 * or {@code List}.
 */
final class AggregateTypes {

	private static final Set<String> AGGREGATES = Set.of("java.util.Map", "java.util.SortedMap",
			"java.util.NavigableMap", "java.util.concurrent.ConcurrentMap",
			"java.util.concurrent.ConcurrentNavigableMap", "java.util.Properties", "java.util.Collection",
			"java.util.List", "java.util.Set", "java.util.SortedSet", "java.util.NavigableSet", "java.util.Queue",
			"java.util.Deque");

	private AggregateTypes() {
	}

	/**
	 * Returns whether a declared metadata type absorbs the keys written below it.
	 * @param type the fully qualified type, possibly generic or an array, or {@code null}
	 * @return whether the type is a map, a collection or an array
	 */
	static boolean isAggregate(String type) {
		if (type == null || type.isBlank()) {
			return false;
		}
		String erased = erase(type);
		return erased.endsWith("[]") || AGGREGATES.contains(erased);
	}

	/**
	 * Strips generic arguments from a declared type, leaving the raw type.
	 * @param type the declared type
	 * @return the type without its generic arguments
	 */
	private static String erase(String type) {
		String trimmed = type.strip();
		int generic = trimmed.indexOf('<');
		return (generic < 0) ? trimmed : trimmed.substring(0, generic).strip();
	}

}
