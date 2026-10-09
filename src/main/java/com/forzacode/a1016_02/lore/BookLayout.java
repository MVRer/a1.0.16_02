package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;

/**
 * How fragment text fits on a book page or a sign, measured with the vanilla default font (ASCII advances from
 * {@code font/ascii.png}). Used to warn when a data file's page or sign line would be cut off, and by the tests.
 */
public final class BookLayout {
	/** Book page text area (vanilla {@code BookViewScreen}). */
	public static final int PAGE_WIDTH = 114;
	public static final int PAGE_LINES = 14;
	/** Sign line width (vanilla {@code SignBlockEntity}). */
	public static final int SIGN_WIDTH = 90;
	/** {@code [PLAYER NAME]} is measured as the widest 16-character name. */
	private static final int NAME_WIDTH = 16 * 7;

	private BookLayout() {
	}

	/** Advance in pixels of one ASCII character in the default font (6 for anything unknown). */
	public static int advance(char c) {
		return switch (c) {
			case ' ', '"', '(', ')', '*', 'I', '[', ']', 't', '{', '}' -> 4;
			case '!', '\'', ',', '.', ':', ';', 'i', '|' -> 2;
			case '<', '>', 'f', 'k' -> 5;
			case '`', 'l' -> 3;
			case '@', '~' -> 7;
			default -> 6;
		};
	}

	public static int width(String text) {
		if (text.contains(Fragment.PLAYER_NAME)) {
			return width(text.replace(Fragment.PLAYER_NAME, "")) + NAME_WIDTH;
		}
		int width = 0;
		for (int n = 0; n < text.length(); n++) {
			width += advance(text.charAt(n));
		}
		return width;
	}

	/** Greedy word wrap at {@link #PAGE_WIDTH}, like the client's line splitter. */
	public static List<String> wrap(String line) {
		List<String> lines = new ArrayList<>();
		String current = null;
		for (String word : line.split(" ", -1)) {
			String candidate = current == null ? word : current + " " + word;
			if (current == null || width(candidate) <= PAGE_WIDTH) {
				current = candidate;
			} else {
				lines.add(current);
				current = word;
			}
		}
		lines.add(current == null ? "" : current);
		return lines;
	}

	/** Visual lines a page takes ("\n" starts a new line). */
	public static int pageLines(String page) {
		int lines = 0;
		for (String line : page.split("\n", -1)) {
			lines += wrap(line).size();
		}
		return lines;
	}

	/** Everything in a fragment that would not fit: pages over 14 lines, sign lines over 90 px. */
	public static List<String> problems(Fragment fragment) {
		List<String> problems = new ArrayList<>();
		for (int n = 0; n < fragment.pages().size(); n++) {
			int lines = pageLines(fragment.pages().get(n));
			if (lines > PAGE_LINES) {
				problems.add("page " + (n + 1) + " has " + lines + " lines (max " + PAGE_LINES + ")");
			}
		}
		for (String line : fragment.lines()) {
			if (width(line) > SIGN_WIDTH) {
				problems.add("sign line '" + line + "' is " + width(line) + " px wide (max " + SIGN_WIDTH + ")");
			}
		}
		if (fragment.title().length() > 32) {
			problems.add("title is longer than 32 characters");
		}
		return problems;
	}
}
