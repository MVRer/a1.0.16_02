package com.forzacode.a1016_02.world.sc2.s17;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.debug.gallery.GalleryRun;
import com.forzacode.a1016_02.world.sc2.s17.books.Books;
import com.forzacode.a1016_02.world.sc2.s17.books.DayLog;
import com.forzacode.a1016_02.world.sc2.s17.books.Pages;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.fabricmc.fabric.api.client.gametest.v1.screenshot.TestScreenshotOptions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * R3A-8 (FIX-R3-17): the archive's books as a player reads them, {@code 17r} in the gallery ({@code ./gradlew gallery
 * -Pgallery=17r}, or {@code all}; never part of {@code build}). A world on day 6 with five days kept in the day log, then
 * vanilla's own book screen opened on five books: the player's record (the dais wall's), an edition (tattered, from the
 * stacks), a past day's book, today's (it breaks off) and a day to come (blank). Each page is split with the client's
 * own font at the screen's width (114) and checked: no page longer than the screen shows (14 lines), no word wider than
 * a line (vanilla would cut it), the page count within 1 to 16, today's last line unfinished; the tooltips' author and
 * generation lines are written beside the screens ({@code 17r/books.txt}). Fails the run on any of it.
 */
public class ArchiveBooksClientTest implements FabricClientGameTest {
	/** The screen's text width and the lines it shows ({@code BookViewScreen}: 114, 128 / 9). */
	static final int WIDTH = 114;
	static final int SHOWN = 128 / 9;
	static final int TODAY = 6;

	/** One book to read: its file name, the stack, and the pages to shoot (0-based; -1 the last). */
	record Book(String name, ItemStack stack, int[] shots) {
	}

	/** True when the gallery's list asks for the books' screens. */
	static boolean asked() {
		String list = GalleryRun.list();
		for (String id : list.split(",")) {
			if (id.trim().equals("17r") || id.trim().equals("all")) {
				return true;
			}
		}
		return false;
	}

	@Override
	public void runTest(ClientGameTestContext context) {
		if (!asked()) {
			return;
		}
		try (TestSingleplayerContext world = context.worldBuilder().adjustSettings(com.forzacode.a1016_02.debug.client.gallery.GalleryWorlds::adjust).create()) {
			context.waitTicks(40);
			world.getServer().runCommand("time set " + ((TODAY - 1) * 24000L + 6000L));
			context.waitTicks(5);
			List<Book> books = world.getServer().computeOnServer(ArchiveBooksClientTest::books);
			Path dir = GalleryRun.freshDir("17r");
			List<String> notes = new ArrayList<>();
			List<String> wrong = new ArrayList<>();
			for (Book book : books) {
				ItemStack stack = book.stack();
				List<String> tooltip = context.computeOnClient(client -> tooltip(client, stack));
				List<Component> pages = stack.getOrDefault(DataComponents.WRITTEN_BOOK_CONTENT, WrittenBookContent.EMPTY).getPages(false);
				notes.add(book.name() + ": " + pages.size() + " pages; tooltip " + tooltip);
				wrong.addAll(context.computeOnClient(client -> check(client.font, book.name(), pages)));
				if (pages.isEmpty() || pages.size() > Pages.PAGES) {
					wrong.add(book.name() + ": " + pages.size() + " pages");
				}
				context.setScreen(() -> new BookViewScreen(new BookViewScreen.BookAccess(pages)));
				context.waitTicks(3);
				for (int shot : book.shots()) {
					int page = shot < 0 ? Math.max(0, pages.size() + shot) : Math.min(shot, Math.max(0, pages.size() - 1));
					context.runOnClient(client -> {
						if (client.gui.screen() instanceof BookViewScreen screen) {
							screen.setPage(page);
						}
					});
					context.waitTicks(2);
					// The window's own size: the book screen is laid out for it (a resized frame shows it in a corner).
					context.takeScreenshot(TestScreenshotOptions.of(String.format(Locale.ROOT, "%s_p%02d", book.name(), page + 1)).withDestinationDir(dir));
				}
				context.setScreen(() -> null);
				context.waitTicks(2);
			}
			// Today's book breaks off mid-line; an edition is tattered; a record is by its player, a day book by nobody.
			Book today = books.get(3);
			List<String> text = Pages.text(today.stack());
			String last = text.isEmpty() ? "" : text.getLast().strip();
			if (last.isEmpty() || last.endsWith(".")) {
				wrong.add("today's book ends whole: \"" + last + "\"");
			}
			if (generation(books.get(1).stack()) != Pages.TATTERED || generation(books.get(0).stack()) != Pages.ORIGINAL) {
				wrong.add("generations: record " + generation(books.get(0).stack()) + ", edition " + generation(books.get(1).stack()));
			}
			notes.add("today's last line: \"" + last + "\"");
			try {
				Files.write(dir.resolve("books.txt"), notes);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			notes.forEach(n -> A1016_02.LOGGER.info("[a1016] gallery: 17r {}", n));
			if (!wrong.isEmpty()) {
				throw new AssertionError("the books' screens: " + wrong);
			}
		}
	}

	/** The five books, made on the server: the log filled for days 1 to 5 and today opened, the player known. */
	static List<Book> books(MinecraftServer server) {
		ServerLevel level = server.overworld();
		Books.init();
		UUID player = server.getPlayerList().getPlayers().getFirst().getUUID();
		DayLog log = DayLog.get(server);
		for (int day = 1; day <= TODAY; day++) {
			log.open(day, 2);
			log.online(0);
			log.moon(day % 8);
			if (day % 2 == 0) {
				log.weather(DayLog.RAIN);
			}
			if (day == 2) {
				log.nightOut(0);
			}
			if (day == 3) {
				log.death(0, "fall", false, true, day * 24000L);
			}
			if (day == 4) {
				log.took(0, "oak door", false);
			}
		}
		A1016_02.LOGGER.info("[a1016] gallery: 17r today {}, known {}", Books.today(server), HerobrineState.get(server).players().size());
		List<Book> out = new ArrayList<>();
		out.add(new Book("record", Books.record(level, player, 0), new int[] {0, 1, -1}));
		out.add(new Book("edition", Books.edition(level, Books.record(level, player, 1)), new int[] {0, -1}));
		out.add(new Book("day_past", Books.day(level, 3), new int[] {0, -1}));
		out.add(new Book("day_today", Books.day(level, Books.today(server)), new int[] {0, -1}));
		out.add(new Book("day_future", Books.day(level, Books.today(server) + 3), new int[] {0}));
		return out;
	}

	/** The tooltip's lines as the client shows them (the title, then "by ..." and the generation). */
	static List<String> tooltip(Minecraft client, ItemStack stack) {
		List<String> out = new ArrayList<>();
		for (Component line : stack.getTooltipLines(Item.TooltipContext.of(client.level), client.player, TooltipFlag.NORMAL)) {
			out.add(line.getString());
		}
		return out;
	}

	static int generation(ItemStack stack) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content == null ? -1 : content.generation();
	}

	/** Each page as the screen splits it: no more lines than it shows, no word wider than a line. */
	static List<String> check(Font font, String name, List<Component> pages) {
		List<String> wrong = new ArrayList<>();
		for (int p = 0; p < pages.size(); p++) {
			Component page = pages.get(p);
			int lines = font.split(page, WIDTH).size();
			if (lines > SHOWN) {
				wrong.add(name + " page " + (p + 1) + ": " + lines + " lines");
			}
			for (String word : page.getString().split("[ \n]+")) {
				if (font.width(word) > WIDTH) {
					wrong.add(name + " page " + (p + 1) + ": \"" + word + "\" wider than a line");
				}
			}
		}
		return wrong;
	}
}
