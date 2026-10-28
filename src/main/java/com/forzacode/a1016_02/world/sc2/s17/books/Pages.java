package com.forzacode.a1016_02.world.sc2.s17.books;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * The page rules of the archive's books (docs/SELF_CREATED_2_R3.md, "How a player's record is made"): lines of at most
 * {@value #WIDTH} characters wrapped at spaces, at most {@value #LINES} lines a page, at most {@value #PAGES} pages, a
 * title of at most {@value #TITLE} characters, and vanilla's {@link WrittenBookContent} around them, so a book is never
 * longer than vanilla allows (its page length, title length and generation). Pure: any thread.
 */
public final class Pages {
	/** Characters a line (the book screen's width in the default font, 114 pixels at 6 a character). */
	public static final int WIDTH = 19;
	/** Lines a page (the book screen shows 14; one kept free). */
	public static final int LINES = 13;
	/** Pages a book. */
	public static final int PAGES = 16;
	/** Characters a title (vanilla's {@link WrittenBookContent#TITLE_MAX_LENGTH}). */
	public static final int TITLE = WrittenBookContent.TITLE_MAX_LENGTH;
	/** Vanilla's generations: an original, a copy, a copy of a copy, tattered. */
	public static final int ORIGINAL = 0;
	public static final int TATTERED = WrittenBookContent.MAX_GENERATION;

	private Pages() {
	}

	/** {@code text} wrapped at spaces into lines of at most {@link #WIDTH}; a word longer than a line is cut. */
	public static List<String> wrap(String text) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.trim().split(" +")) {
			if (word.isEmpty()) {
				continue;
			}
			while (word.length() > WIDTH) {
				if (!line.isEmpty()) {
					out.add(line.toString());
					line.setLength(0);
				}
				out.add(word.substring(0, WIDTH));
				word = word.substring(WIDTH);
			}
			if (line.isEmpty()) {
				line.append(word);
			} else if (line.length() + 1 + word.length() <= WIDTH) {
				line.append(' ').append(word);
			} else {
				out.add(line.toString());
				line.setLength(0);
				line.append(word);
			}
		}
		if (!line.isEmpty()) {
			out.add(line.toString());
		}
		return out;
	}

	/** The lines {@code text} takes: each of its own lines ({@code \n}) wrapped; an empty line stays one. */
	public static List<String> lines(String text) {
		List<String> out = new ArrayList<>();
		for (String own : text.split("\n", -1)) {
			List<String> wrapped = wrap(own);
			if (wrapped.isEmpty()) {
				out.add("");
			} else {
				out.addAll(wrapped);
			}
		}
		return out;
	}

	/**
	 * The pages for these entries, in order: each entry's wrapped lines kept on one page where it fits one, an empty line
	 * between two entries on a page, a new page when the next would not fit. Unbounded: {@link #fits} says whether the
	 * result is within {@link #PAGES}.
	 */
	public static List<String> paginate(List<String> entries) {
		List<String> pages = new ArrayList<>();
		List<String> page = new ArrayList<>();
		for (String entry : entries) {
			List<String> lines = lines(entry);
			int need = lines.size() + (page.isEmpty() ? 0 : 1);
			if (!page.isEmpty() && page.size() + need > LINES) {
				pages.add(String.join("\n", page));
				page.clear();
			}
			if (!page.isEmpty()) {
				page.add("");
			}
			for (String line : lines) {
				if (page.size() == LINES) {
					pages.add(String.join("\n", page));
					page.clear();
				}
				page.add(line);
			}
		}
		if (!page.isEmpty()) {
			pages.add(String.join("\n", page));
		}
		return pages;
	}

	/** True if these entries make at most {@link #PAGES} pages. */
	public static boolean fits(List<String> entries) {
		return paginate(entries).size() <= PAGES;
	}

	/**
	 * Pages written as pages (the six's, each a page with {@code \n} between its lines): each wrapped to the width, a page
	 * longer than {@link #LINES} going on to the next; at most {@link #PAGES}.
	 */
	public static List<String> raw(List<String> written) {
		List<String> pages = new ArrayList<>();
		for (String page : written) {
			List<String> lines = lines(page);
			for (int from = 0; from < lines.size(); from += LINES) {
				pages.add(String.join("\n", lines.subList(from, Math.min(lines.size(), from + LINES))));
			}
		}
		return pages.size() > PAGES ? List.copyOf(pages.subList(0, PAGES)) : pages;
	}

	/** A title as the books write it: lowercase, at most {@link #TITLE} characters. */
	public static String title(String name) {
		String lower = name.toLowerCase(Locale.ROOT).trim();
		return lower.length() > TITLE ? lower.substring(0, TITLE) : lower;
	}

	/**
	 * A written book: {@code title} (cut to {@link #TITLE}), {@code author}, {@code generation} (0 to vanilla's max), these
	 * pages (at most {@link #PAGES}; none makes one empty page), resolved (plain text).
	 */
	public static ItemStack book(String title, String author, int generation, List<String> pages) {
		List<Filterable<Component>> content = new ArrayList<>();
		for (String page : pages.size() > PAGES ? pages.subList(0, PAGES) : pages) {
			content.add(Filterable.passThrough(Component.literal(page)));
		}
		if (content.isEmpty()) {
			content.add(Filterable.passThrough(Component.empty()));
		}
		String cut = title.length() > TITLE ? title.substring(0, TITLE) : title;
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(cut), author,
				Math.clamp(generation, ORIGINAL, TATTERED), List.copyOf(content), true));
		return stack;
	}

	/** A written book's pages as plain text (empty for anything else). */
	public static List<String> text(ItemStack stack) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (content == null) {
			return List.of();
		}
		List<String> out = new ArrayList<>();
		content.pages().forEach(page -> out.add(page.raw().getString()));
		return out;
	}
}
