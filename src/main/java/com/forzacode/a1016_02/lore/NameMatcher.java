package com.forzacode.a1016_02.lore;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Does a text name him? Case-insensitive, ignoring spaces, punctuation and accents, with the simple digit swaps
 * players use to get around a filter (3 for e, 1 for i, 0 for o): "Herobrine", "hero brine", "H.E.R.O.B.R.I.N.E",
 * "her0br1ne" and "h3robrine" all name him. Lines of a sign or pages of a book are joined before matching, so a
 * name split across two lines still counts.
 */
public final class NameMatcher {
	/** His name, as the matcher compares it. */
	static final String NAME = "herobrine";

	private NameMatcher() {
	}

	/** True if the text names him. */
	public static boolean namesHim(String text) {
		return text != null && normalize(text).contains(NAME);
	}

	/** Lower case letters only, accents dropped, 3/1/0 read as e/i/o; everything else removed. */
	static String normalize(String text) {
		String decomposed = Normalizer.normalize(text, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT);
		StringBuilder out = new StringBuilder(decomposed.length());
		for (int n = 0; n < decomposed.length(); n++) {
			char c = decomposed.charAt(n);
			switch (c) {
				case '3' -> out.append('e');
				case '1' -> out.append('i');
				case '0' -> out.append('o');
				default -> {
					if (c >= 'a' && c <= 'z') {
						out.append(c);
					}
				}
			}
		}
		return out.toString();
	}
}
