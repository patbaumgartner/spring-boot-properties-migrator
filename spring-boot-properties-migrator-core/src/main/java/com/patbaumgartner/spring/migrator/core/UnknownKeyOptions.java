package com.patbaumgartner.spring.migrator.core;

import java.util.List;

/**
 * How a run should treat configuration keys that no metadata describes.
 *
 * @param policy whether to look for unrecognised keys, and whether they fail the build
 * @param includes namespaces to inspect instead of the built-in defaults, which is how a
 * project opts a namespace of its own into the check
 * @param excludes exact keys or prefixes to never report, which is the escape hatch for
 * keys read directly by application code or by a library that publishes no metadata
 */
public record UnknownKeyOptions(UnknownKeyPolicy policy, List<String> includes, List<String> excludes) {

	/**
	 * Defensively copies the lists and accepts {@code null} for either, so that a build
	 * tool that leaves an unset list parameter alone behaves like one that sets it empty.
	 * @param policy whether to look for unrecognised keys
	 * @param includes namespaces to inspect instead of the built-in defaults
	 * @param excludes exact keys or prefixes to never report
	 */
	public UnknownKeyOptions {
		includes = (includes == null) ? List.of() : List.copyOf(includes);
		excludes = (excludes == null) ? List.of() : List.copyOf(excludes);
	}

	/**
	 * Returns options that switch the check off, which is the default.
	 * @return disabled options
	 */
	public static UnknownKeyOptions disabled() {
		return new UnknownKeyOptions(UnknownKeyPolicy.OFF, List.of(), List.of());
	}

}
