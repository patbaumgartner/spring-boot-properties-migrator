package com.patbaumgartner.spring.migrator.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps between character offsets and line/column positions in a document, without
 * altering the document's line terminators.
 * <p>
 * Physical lines are recorded with the offset of their first character and the offset
 * just past their content, so that {@code \n}, {@code \r\n} and {@code \r} terminators
 * survive untouched: nothing here ever rebuilds text by joining lines.
 */
final class LineIndex {

	private final String text;

	private final List<Integer> lineStarts;

	private final List<Integer> contentEnds;

	private LineIndex(String text, List<Integer> lineStarts, List<Integer> contentEnds) {
		this.text = text;
		this.lineStarts = lineStarts;
		this.contentEnds = contentEnds;
	}

	static LineIndex of(String text) {
		List<Integer> lineStarts = new ArrayList<>();
		List<Integer> contentEnds = new ArrayList<>();
		int position = 0;
		lineStarts.add(0);
		while (position < text.length()) {
			char current = text.charAt(position);
			if (current == '\n') {
				contentEnds.add(position);
				position++;
				lineStarts.add(position);
			}
			else if (current == '\r') {
				contentEnds.add(position);
				position += (position + 1 < text.length() && text.charAt(position + 1) == '\n') ? 2 : 1;
				lineStarts.add(position);
			}
			else {
				position++;
			}
		}
		contentEnds.add(text.length());
		return new LineIndex(text, lineStarts, contentEnds);
	}

	int lineCount() {
		return this.lineStarts.size();
	}

	/**
	 * Returns the content of a line without its terminator.
	 * @param line the zero-based line number
	 * @return the line content
	 */
	String lineContent(int line) {
		return this.text.substring(this.lineStarts.get(line), this.contentEnds.get(line));
	}

	/**
	 * Returns the absolute offset of the first character of a line.
	 * @param line the zero-based line number
	 * @return the offset
	 */
	int lineStart(int line) {
		return this.lineStarts.get(line);
	}

	/**
	 * Converts a zero-based line and column into an absolute character offset.
	 * @param line the zero-based line number
	 * @param column the zero-based column
	 * @return the offset, clamped to the end of the document
	 */
	int offsetOf(int line, int column) {
		if (line >= this.lineStarts.size()) {
			return this.text.length();
		}
		return Math.min(this.lineStarts.get(line) + column, this.text.length());
	}

}
