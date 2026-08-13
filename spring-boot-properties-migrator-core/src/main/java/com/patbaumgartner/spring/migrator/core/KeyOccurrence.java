package com.patbaumgartner.spring.migrator.core;

/**
 * A property key as it physically appears in a configuration file.
 *
 * @param name the full property name, including any ancestor path
 * @param ancestorPath the structural path the key is nested under, which is empty for
 * {@code .properties} files and for flat top-level YAML keys
 * @param start the index of the first character of the editable key token
 * @param end the index after the last character of the editable key token
 * @param line the one-based line number the key appears on
 */
record KeyOccurrence(String name, String ancestorPath, int start, int end, int line) {
}
