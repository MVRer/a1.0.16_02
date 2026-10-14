package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MarkedDeath;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;


/**
 * Fragment books whose text names someone, brought up to date when they are opened (in a hand or on a lectern), so
 * they never change on camera (D-047). The list (F06) and its second copy (F23) are one shared list (D-063): their
 * {@code [PLAYER NAME] .} line becomes one line for every known player, in first-join order, each with that
 * player's own latest marked-death cause ("Alex . lava"); nobody who never took part is on it (D-061). In Ending B
 * the changelog (F10) gains its last line from its data, "* removed [PLAYER NAME]", once for every player whose line
 * the ending finished, in the order they were finished; never a living friend. Any other {@code [PLAYER NAME]} names
 * the reader. Every word is the fragment's own; only the cause is
 * the list's word for the death. {@link #finishF10} also updates every copy it can reach at once.
 */
public final class LiveBooks {
	/** Set when F10 gained its last line (Ending B). */
	public static final String F10_FINISHED = "lore:f10_finished";
	private static final String NAME_LINE = Fragment.PLAYER_NAME + " .";

	/** One player's line on the list: their name and their latest marked death's cause, if any. */
	public record Line(String name, Optional<String> cause) {
		/** "Alex ." or "Alex . lava". */
		public String text() {
			return cause.map(String::strip).filter(c -> !c.isEmpty()).map(c -> name + " . " + c).orElse(name + " .");
		}
	}

	private LiveBooks() {
	}

	// --- the list's lines ---

	/** The list's player lines now: one for every known player, in first-join order, each with their own latest cause. */
	static List<Line> lines(HerobrineState state, TellingData data) {
		List<Line> lines = new ArrayList<>();
		for (HerobrineState.Subject player : state.players()) {
			lines.add(new Line(player.name(), cause(state, data, player.uuid())));
		}
		return lines;
	}

	/**
	 * That player's latest cause: the one MARKED_DEATH gave lore for them, else their own latest recorded marked
	 * death's. Empty while he has not killed them.
	 */
	static Optional<String> cause(HerobrineState state, TellingData data, UUID player) {
		Optional<String> recorded = data.listCause(player);
		if (recorded.isPresent()) {
			return recorded;
		}
		List<MarkedDeath> deaths = state.markedDeaths(player);
		return deaths.isEmpty() ? Optional.empty() : Optional.of(deaths.getLast().cause());
	}

	/** {@link #cause(HerobrineState, TellingData, UUID)} in the live world. */
	public static Optional<String> cause(MinecraftServer server, UUID player) {
		return cause(HerobrineState.get(server), TellingData.get(server), player);
	}

	/**
	 * MARKED_DEATH: that player's own line takes the cause, whoever they are (D-063). Only known players are shown,
	 * so a death of someone who never took part is kept but named nowhere.
	 */
	static void onMarkedDeath(ServerPlayer player, String cause, TellingData data) {
		data.setListCause(player.getUUID(), cause);
		A1016_02.LOGGER.info("[a1016] lore: the list now reads \"{} . {}\"", player.getName().getString(), cause);
	}

	// --- pages ---

	/** True for a book whose text names someone ({@code [PLAYER NAME]} in a page or its last line): F06, F23, F10. */
	static boolean isLive(Fragment fragment) {
		return fragment.isBook() && (fragment.pages().stream().anyMatch(page -> page.contains(Fragment.PLAYER_NAME))
				|| fragment.lastLine().map(line -> line.contains(Fragment.PLAYER_NAME)).orElse(false));
	}

	/** The pages for one player who is everyone: the list with their line only, F10 finished naming them (previews, tests). */
	public static List<String> pages(Fragment fragment, String playerName, Optional<String> cause, boolean f10Finished) {
		return pages(fragment, playerName, List.of(new Line(playerName, cause)), f10Finished ? List.of(playerName) : List.of());
	}

	/**
	 * The pages a fragment book shows now. The list's {@code [PLAYER NAME] .} line becomes {@code list}, one line
	 * each (none while nobody is known); F10 gains its last line once for each name in {@code removed}, in order
	 * (Ending B's finished players); any other {@code [PLAYER NAME]} is the reader's name. A list too long for its
	 * page goes on to the next, and so do F10's lines.
	 */
	public static List<String> pages(Fragment fragment, String reader, List<Line> list, List<String> removed) {
		List<String> pages = new ArrayList<>();
		for (String page : fragment.pages()) {
			String withList = withList(page, list);
			// Only a page the list grew may go on to the next; every other page stays exactly as the data has it.
			pages.addAll(withList.equals(page) ? List.of(page) : fit(withList));
		}
		if (!removed.isEmpty() && fragment.lastLine().isPresent() && !pages.isEmpty()) {
			int last = pages.size() - 1;
			StringBuilder page = new StringBuilder(pages.get(last));
			for (String name : removed) {
				page.append('\n').append(fragment.lastLine().get().replace(Fragment.PLAYER_NAME, name));
			}
			pages.remove(last);
			pages.addAll(fit(page.toString()));
		}
		pages.replaceAll(page -> page.replace(Fragment.PLAYER_NAME, reader));
		return pages;
	}

	/** "[PLAYER NAME] ." becomes one line per entry of {@code list} (or goes, if it is empty). */
	static String withList(String page, List<Line> list) {
		String[] lines = page.split("\n", -1);
		List<String> out = new ArrayList<>(lines.length + list.size());
		for (String line : lines) {
			if (line.strip().equals(NAME_LINE)) {
				list.forEach(entry -> out.add(entry.text()));
			} else {
				out.add(line);
			}
		}
		return String.join("\n", out);
	}

	/** A page that fits (at most {@link BookLayout#PAGE_LINES} lines), else split into pages that do, line by line. */
	static List<String> fit(String page) {
		if (BookLayout.pageLines(page) <= BookLayout.PAGE_LINES) {
			return List.of(page);
		}
		List<String> pages = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		int used = 0;
		for (String line : page.split("\n", -1)) {
			int lines = BookLayout.wrap(line).size();
			if (used > 0 && used + lines > BookLayout.PAGE_LINES) {
				pages.add(current.toString());
				current.setLength(0);
				used = 0;
			}
			if (used > 0) {
				current.append('\n');
			}
			current.append(line);
			used += lines;
		}
		pages.add(current.toString());
		return pages;
	}

	/** The names F10's Ending B lines finish: each finished player the ending named, in order, while they are known. */
	static List<String> removedNames(HerobrineState state, TellingData data) {
		return data.f10Removed().stream().flatMap(uuid -> state.player(uuid).stream()).map(HerobrineState.Subject::name).toList();
	}

	/**
	 * Brings a live fragment book up to date. True if its pages changed. The content is left unresolved so vanilla
	 * sends the new pages before it opens the book.
	 */
	static boolean refresh(ItemStack stack, String reader, List<Line> list, List<String> removed) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		Optional<Fragment> fragment = FragmentItems.fragmentId(stack).flatMap(FragmentData::get).filter(LiveBooks::isLive);
		if (fragment.isEmpty() || content == null) {
			return false;
		}
		List<String> now = pages(fragment.get(), reader, list, removed);
		List<String> current = content.pages().stream().map(page -> page.raw().getString()).toList();
		if (now.equals(current)) {
			return false;
		}
		List<Filterable<Component>> pages = now.stream().map(page -> Filterable.passThrough((Component) Component.literal(page))).toList();
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(content.title(), content.author(), content.generation(), pages, false));
		return true;
	}

	/** {@link #refresh} for one name that is everyone (tests, previews). */
	static boolean refresh(ItemStack stack, String playerName, Optional<String> cause, boolean f10Finished) {
		return refresh(stack, playerName, List.of(new Line(playerName, cause)), f10Finished ? List.of(playerName) : List.of());
	}

	/** Refreshes a book that {@code reader} is about to read: the shared list now, F10 as the story stands, their own name elsewhere. */
	static boolean refreshForReading(ItemStack stack, ServerPlayer reader) {
		MinecraftServer server = reader.level().getServer();
		return refreshForReading(stack, reader.getName().getString(), HerobrineState.get(server), TellingData.get(server));
	}

	static boolean refreshForReading(ItemStack stack, String reader, HerobrineState state, TellingData data) {
		List<String> removed = state.hasFlag(F10_FINISHED) ? removedNames(state, data) : List.of();
		return refresh(stack, reader, lines(state, data), removed);
	}

	// --- Ending B ---

	/**
	 * Ending B: F10 gains one more "* removed [PLAYER NAME]" line, naming {@code player}: a player whose line the
	 * ending finished (D-063; the ending calls this once for each, in the order they were finished). A player already
	 * named keeps their place; someone who never took part is never named (D-061). From then on every copy shows the
	 * lines when read; the copies within reach now (players' inventories and ender chests, loaded containers and
	 * lecterns near where it was placed, dropped items and item frames in loaded areas) are updated at once. Returns
	 * how many were.
	 */
	public static int finishF10(MinecraftServer server, UUID player) {
		return finishF10(server, HerobrineState.get(server), TellingData.get(server), player);
	}

	static int finishF10(MinecraftServer server, HerobrineState state, TellingData data, UUID player) {
		if (state.player(player).isEmpty()) {
			return 0;
		}
		data.addF10Removed(player);
		state.setFlag(F10_FINISHED, true);
		List<Line> list = lines(state, data);
		List<String> removed = removedNames(state, data);
		int updated = 0;
		for (ServerPlayer online : server.getPlayerList().getPlayers()) {
			String name = online.getName().getString();
			updated += refreshAll(online.getInventory(), name, list, removed);
			updated += refreshAll(online.getEnderChestInventory(), name, list, removed);
		}
		// Copies nobody holds name nobody: the reader's name is written in when one of them opens it.
		String nobody = removed.isEmpty() ? "" : removed.getFirst();
		GlobalPos placed = state.fragmentsPlaced().get("F10");
		if (placed != null) {
			ServerLevel level = server.getLevel(placed.dimension());
			if (level != null) {
				updated += refreshBlockEntities(level, placed.pos(), 2, nobody, list, removed);
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (ItemEntity item : level.getEntities(EntityTypes.ITEM, e -> FragmentItems.is(e.getItem(), "F10"))) {
				updated += refresh(item.getItem(), nobody, list, removed) ? 1 : 0;
			}
			// Item frames and glow item frames: the stack is changed in place, silently (no frame sound, nothing seen:
			// a book's pages are not shown on a frame), and saved with the frame.
			for (ItemFrame frame : level.getEntities(EntityTypeTest.forClass(ItemFrame.class), e -> FragmentItems.is(e.getItem(), "F10"))) {
				updated += refresh(frame.getItem(), nobody, list, removed) ? 1 : 0;
			}
		}
		A1016_02.LOGGER.info("[a1016] lore: F10 now ends with a line for {} ({} copies within reach)", String.join(", ", removed), updated);
		return updated;
	}

	private static int refreshAll(Container container, String reader, List<Line> list, List<String> removed) {
		int updated = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (FragmentItems.is(container.getItem(slot), "F10") && refresh(container.getItem(slot), reader, list, removed)) {
				updated++;
			}
		}
		if (updated > 0) {
			container.setChanged();
		}
		return updated;
	}

	/** Containers and lecterns in loaded chunks within {@code chunks} chunks of {@code center}. */
	private static int refreshBlockEntities(ServerLevel level, BlockPos center, int chunks, String reader, List<Line> list, List<String> removed) {
		int updated = 0;
		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		for (int x = cx - chunks; x <= cx + chunks; x++) {
			for (int z = cz - chunks; z <= cz + chunks; z++) {
				if (!ChunkGate.loaded(level, x, z)) {
					continue;
				}
				for (BlockEntity blockEntity : level.getChunk(x, z).getBlockEntities().values()) {
					if (blockEntity instanceof LecternBlockEntity lectern && FragmentItems.is(lectern.getBook(), "F10")
							&& refresh(lectern.getBook(), reader, list, removed)) {
						lectern.setChanged();
						updated++;
					} else if (blockEntity instanceof Container container && !(blockEntity instanceof net.minecraft.world.RandomizableContainer loot
							&& loot.getLootTable() != null)) {
						updated += refreshAll(container, reader, list, removed);
					}
				}
			}
		}
		return updated;
	}
}
