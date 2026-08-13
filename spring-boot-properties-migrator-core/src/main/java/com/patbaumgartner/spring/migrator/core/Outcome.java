package com.patbaumgartner.spring.migrator.core;

/**
 * What the migrator decided to do about a deprecated property it found.
 */
public enum Outcome {

	/**
	 * The key was rewritten to its replacement, or would be when not running dry.
	 */
	MIGRATED,

	/**
	 * A replacement exists but applying it automatically is not provably safe, so the key
	 * was left untouched and reported instead.
	 */
	MANUAL,

	/**
	 * The property is deprecated and the metadata names no replacement.
	 */
	UNSUPPORTED

}
