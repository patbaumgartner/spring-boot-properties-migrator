package com.patbaumgartner.spring.migrator.core;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.error.Mark;
import org.yaml.snakeyaml.error.YAMLException;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.NodeTuple;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;

/**
 * Finds property keys in a YAML document by composing it into a node tree and recording
 * the exact source span of every mapping key.
 * <p>
 * The parser is used for analysis only; the document is never re-emitted. Composing
 * rather than pattern matching is what makes block scalars, flow mappings, quoted keys
 * and multi-document files safe: text that merely looks like a key is not a key, and a
 * key that does not look like one is still found.
 * <p>
 * Documents containing anchors or aliases, duplicate keys, or syntax errors are reported
 * as analysis-only, because a single key edit in those files can change more than the one
 * property it appears to change.
 */
final class YamlKeyParser {

	private YamlKeyParser() {
	}

	static DocumentKeys parse(String text) {
		LineIndex index = LineIndex.of(text);
		Collector collector = new Collector(index);
		try {
			for (Node document : new Yaml(new LoaderOptions()).composeAll(new StringReader(text))) {
				collector.walk(document, "");
			}
		}
		catch (YAMLException ex) {
			return DocumentKeys.readOnly(List.of(), "file is not valid YAML (" + rootMessage(ex) + ")");
		}
		return (collector.restriction != null) ? DocumentKeys.readOnly(collector.keys, collector.restriction)
				: DocumentKeys.editable(collector.keys);
	}

	private static String rootMessage(YAMLException ex) {
		String message = ex.getMessage();
		if (message == null || message.isBlank()) {
			return ex.getClass().getSimpleName();
		}
		return message.lines().findFirst().orElse(message).trim();
	}

	private static final class Collector {

		private final LineIndex index;

		private final List<KeyOccurrence> keys = new ArrayList<>();

		private final Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());

		private String restriction;

		private Collector(LineIndex index) {
			this.index = index;
		}

		private void walk(Node node, String path) {
			if (node == null || !this.visited.add(node)) {
				return;
			}
			if (node.getAnchor() != null) {
				restrict("file uses YAML anchors or aliases");
			}
			if (node instanceof MappingNode mapping) {
				walkMapping(mapping, path);
			}
			else if (node instanceof SequenceNode sequence) {
				List<Node> items = sequence.getValue();
				for (int i = 0; i < items.size(); i++) {
					walk(items.get(i), path + "[" + i + "]");
				}
			}
		}

		private void walkMapping(MappingNode mapping, String path) {
			Set<String> seen = new HashSet<>();
			for (NodeTuple tuple : mapping.getValue()) {
				Node keyNode = tuple.getKeyNode();
				if (!(keyNode instanceof ScalarNode scalar)) {
					restrict("file uses non-scalar mapping keys");
					continue;
				}
				String name = scalar.getValue();
				String fullName = path.isEmpty() ? name : path + "." + name;
				if (!seen.add(PropertyName.uniform(fullName))) {
					restrict("file declares the same key more than once");
				}
				this.keys.add(new KeyOccurrence(fullName, path, offsetOf(scalar.getStartMark()),
						offsetOf(scalar.getEndMark()), scalar.getStartMark().getLine() + 1));
				walk(tuple.getValueNode(), fullName);
			}
		}

		private int offsetOf(Mark mark) {
			return this.index.offsetOf(mark.getLine(), mark.getColumn());
		}

		private void restrict(String reason) {
			if (this.restriction == null) {
				this.restriction = reason;
			}
		}

	}

}
