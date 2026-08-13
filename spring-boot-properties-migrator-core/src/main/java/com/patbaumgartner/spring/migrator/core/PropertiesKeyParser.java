package com.patbaumgartner.spring.migrator.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds property keys in a {@code .properties} document, following the grammar the JDK's
 * {@link java.util.Properties} loader uses.
 * <p>
 * Parsing logical rather than physical lines matters: a value continued with a trailing
 * backslash may contain text that looks exactly like {@code some.key=value}, and treating
 * that continuation as a key would silently rewrite a value.
 */
final class PropertiesKeyParser {

	private PropertiesKeyParser() {
	}

	static DocumentKeys parse(String text) {
		LineIndex index = LineIndex.of(text);
		List<KeyOccurrence> keys = new ArrayList<>();
		int line = 0;
		while (line < index.lineCount()) {
			String content = index.lineContent(line);
			String stripped = content.stripLeading();
			if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("!")) {
				line++;
				continue;
			}
			int lastLine = line;
			while (continuesOntoNextLine(index.lineContent(lastLine)) && lastLine + 1 < index.lineCount()) {
				lastLine++;
			}
			readKey(content, index.lineStart(line), line + 1).ifPresent(keys::add);
			line = lastLine + 1;
		}
		return DocumentKeys.editable(keys);
	}

	/**
	 * Returns whether a physical line ends with an odd number of backslashes, which is
	 * how {@code .properties} marks a continuation.
	 * @param content the physical line without its terminator
	 * @return whether the next physical line belongs to the same logical line
	 */
	private static boolean continuesOntoNextLine(String content) {
		int backslashes = 0;
		for (int i = content.length() - 1; i >= 0 && content.charAt(i) == '\\'; i--) {
			backslashes++;
		}
		return backslashes % 2 == 1;
	}

	private static Optional<KeyOccurrence> readKey(String content, int lineOffset, int lineNumber) {
		int start = 0;
		while (start < content.length() && isPropertiesWhitespace(content.charAt(start))) {
			start++;
		}

		StringBuilder key = new StringBuilder();
		int position = start;
		while (position < content.length()) {
			char current = content.charAt(position);
			if (current == '\\') {
				if (position + 1 >= content.length()) {
					// The key itself is continued onto the next physical line. Rewriting
					// that safely would mean reflowing the continuation, so leave it be.
					return Optional.empty();
				}
				position += appendEscaped(content, position, key);
				continue;
			}
			if (current == '=' || current == ':' || isPropertiesWhitespace(current)) {
				break;
			}
			key.append(current);
			position++;
		}

		if (key.isEmpty()) {
			return Optional.empty();
		}
		return Optional
			.of(new KeyOccurrence(key.toString(), "", lineOffset + start, lineOffset + position, lineNumber));
	}

	/**
	 * Appends the decoded form of the escape sequence at {@code position}.
	 * @param content the physical line
	 * @param position the index of the backslash
	 * @param key the buffer collecting the decoded key
	 * @return how many characters of input the escape consumed
	 */
	private static int appendEscaped(String content, int position, StringBuilder key) {
		char escaped = content.charAt(position + 1);
		if (escaped == 'u' && position + 5 < content.length()) {
			try {
				key.append((char) Integer.parseInt(content.substring(position + 2, position + 6), 16));
				return 6;
			}
			catch (NumberFormatException ex) {
				key.append('u');
				return 2;
			}
		}
		key.append(switch (escaped) {
			case 't' -> '\t';
			case 'n' -> '\n';
			case 'r' -> '\r';
			case 'f' -> '\f';
			default -> escaped;
		});
		return 2;
	}

	private static boolean isPropertiesWhitespace(char candidate) {
		return candidate == ' ' || candidate == '\t' || candidate == '\f';
	}

}
