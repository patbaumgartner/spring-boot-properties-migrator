package com.patbaumgartner.spring.migrator.core;

import java.util.List;

/**
 * The keys discovered in a configuration file, plus an optional reason why the file must
 * not be rewritten.
 *
 * @param keys every property key found, in source order
 * @param restriction why the file is analysis-only, or {@code null} when it may be
 * rewritten
 */
record DocumentKeys(List<KeyOccurrence> keys, String restriction) {

	static DocumentKeys editable(List<KeyOccurrence> keys) {
		return new DocumentKeys(keys, null);
	}

	static DocumentKeys readOnly(List<KeyOccurrence> keys, String restriction) {
		return new DocumentKeys(keys, restriction);
	}

	boolean isEditable() {
		return this.restriction == null;
	}

}
