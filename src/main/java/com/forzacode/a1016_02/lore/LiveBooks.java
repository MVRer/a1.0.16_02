package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.Services;

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
 * Fragment books whose text moves on: the second copy of the list (F23) gains the cause of each marked death
 * ("[PLAYER NAME] . lava", the latest cause), and in Ending B the changelog (F10) gains its last line from its
 * data ("* removed [PLAYER NAME]"). Every word is the fragment's own; only the cause is the list's word for the
 * death. A book is brought up to date when it is opened (in a hand or on a lectern), so it never changes on
 * camera; {@link #finishF10} also updates every copy it can reach at once. The list (F06) never changes.
 */
public final class LiveBooks {
	/** Set when F10 gained its last line (Ending B). */
	public static final String F10_FINISHED = "lore:f10_finished";
	private static final String NAME_LINE = Fragment.PLAYER_NAME + " .";

	private LiveBooks() {
	}

	/**
	 * The pages a fragment book shows now, {@code [PLAYER NAME]} replaced: F23 with the cause on the player's line,
	 * F10 with its last line once finished, every other book as written.
	 */
	public static List<String> pages(Fragment fragment, String playerName, Optional<String> cause, boolean f10Finished) {
		List<String> pages = new ArrayList<>(fragment.pages());
		if (fragment.id().equals("F23") && cause.isPresent() && !cause.get().isBlank()) {
			pages.replaceAll(page -> withCause(page, cause.get().strip()));
		}
		if (fragment.id().equals("F10") && f10Finished && fragment.lastLine().isPresent() && !pages.isEmpty()) {
			int last = pages.size() - 1;
			pages.set(last, pages.get(last) + "\n" + fragment.lastLine().get());
		}
		pages.replaceAll(page -> page.replace(Fragment.PLAYER_NAME, playerName));
		return pages;
	}

	/** "[PLAYER NAME] ." becomes "[PLAYER NAME] . cause" on its own line. */
	static String withCause(String page, String cause) {
		String[] lines = page.split("\n", -1);
		for (int n = 0; n < lines.length; n++) {
			if (lines[n].strip().equals(NAME_LINE)) {
				lines[n] = NAME_LINE + " " + cause;
			}
		}
		return String.join("\n", lines);
	}

	/** The cause the list shows for the subject: the latest marked death's word, if any. */
	public static Optional<String> cause(MinecraftServer server) {
		Optional<String> recorded = TellingData.get(server).listCause();
		if (recorded.isPresent()) {
			return recorded;
		}
		List<MarkedDeath> deaths = HerobrineState.get(server).markedDeaths();
		return deaths.isEmpty() ? Optional.empty() : Optional.of(deaths.getLast().cause());
	}

	/** The name the books carry: the subject's, else the reader's. */
	static String name(MinecraftServer server, ServerPlayer reader) {
		return HerobrineState.get(server).subject().map(HerobrineState.Subject::name)
				.orElse(reader == null ? "Steve" : reader.getName().getString());
	}

	/** MARKED_DEATH: the subject's line on F23 takes the cause. */
	static void onMarkedDeath(ServerPlayer player, String cause, TellingData data) {
		MinecraftServer server = player.level().getServer();
		if (HerobrineState.get(server).subject().isPresent() && !Services.watch().isSubject(player)) {
			return;
		}
		data.setListCause(cause);
		A1016_02.LOGGER.info("[a1016] lore: the list now reads \"{} . {}\"", player.getName().getString(), cause);
	}

	/**
	 * Brings a fragment book up to date (F23's cause, F10's last line). True if its pages changed. The content is
	 * left unresolved so vanilla sends the new pages before it opens the book.
	 */
	static boolean refresh(ItemStack stack, String playerName, Optional<String> cause, boolean f10Finished) {
		Optional<String> id = FragmentItems.fragmentId(stack).filter(f -> f.equals("F23") || f.equals("F10"));
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (id.isEmpty() || content == null) {
			return false;
		}
		Optional<Fragment> fragment = FragmentData.get(id.get());
		if (fragment.isEmpty()) {
			return false;
		}
		List<String> now = pages(fragment.get(), playerName, cause, f10Finished);
		List<String> current = content.pages().stream().map(page -> page.raw().getString()).toList();
		if (now.equals(current)) {
			return false;
		}
		List<Filterable<Component>> pages = now.stream().map(page -> Filterable.passThrough((Component) Component.literal(page))).toList();
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(content.title(), content.author(), content.generation(), pages, false));
		return true;
	}

	/** Refreshes a book that is about to be read, with the live cause, name and F10 state. */
	static boolean refreshForReading(ItemStack stack, ServerPlayer reader) {
		MinecraftServer server = reader.level().getServer();
		return refresh(stack, name(server, reader), cause(server), HerobrineState.get(server).hasFlag(F10_FINISHED));
	}

	/**
	 * Ending B: F10 gains "* removed [PLAYER NAME]" wherever it is. From now on every copy shows it when read; the
	 * copies within reach now (players' inventories and ender chests, loaded containers and lecterns near where it
	 * was placed, dropped items and item frames in loaded areas) are updated at once. Returns how many were.
	 */
	public static int finishF10(MinecraftServer server) {
		return finishF10(server, HerobrineState.get(server));
	}

	static int finishF10(MinecraftServer server, HerobrineState state) {
		state.setFlag(F10_FINISHED, true);
		String name = state.subject().map(HerobrineState.Subject::name).orElse("Steve");
		Optional<String> cause = cause(server);
		int updated = 0;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			updated += refreshAll(player.getInventory(), name, cause);
			updated += refreshAll(player.getEnderChestInventory(), name, cause);
		}
		GlobalPos placed = state.fragmentsPlaced().get("F10");
		if (placed != null) {
			ServerLevel level = server.getLevel(placed.dimension());
			if (level != null) {
				updated += refreshBlockEntities(level, placed.pos(), 2, name, cause);
			}
		}
		for (ServerLevel level : server.getAllLevels()) {
			for (ItemEntity item : level.getEntities(EntityTypes.ITEM, e -> FragmentItems.is(e.getItem(), "F10"))) {
				updated += refresh(item.getItem(), name, cause, true) ? 1 : 0;
			}
			// Item frames and glow item frames: the stack is changed in place, silently (no frame sound, nothing seen:
			// a book's pages are not shown on a frame), and saved with the frame.
			for (ItemFrame frame : level.getEntities(EntityTypeTest.forClass(ItemFrame.class), e -> FragmentItems.is(e.getItem(), "F10"))) {
				updated += refresh(frame.getItem(), name, cause, true) ? 1 : 0;
			}
		}
		A1016_02.LOGGER.info("[a1016] lore: F10 gained its last line ({} copies within reach)", updated);
		return updated;
	}

	private static int refreshAll(Container container, String name, Optional<String> cause) {
		int updated = 0;
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			if (refresh(container.getItem(slot), name, cause, true)) {
				updated++;
			}
		}
		if (updated > 0) {
			container.setChanged();
		}
		return updated;
	}

	/** Containers and lecterns in loaded chunks within {@code chunks} chunks of {@code center}. */
	private static int refreshBlockEntities(ServerLevel level, BlockPos center, int chunks, String name, Optional<String> cause) {
		int updated = 0;
		int cx = center.getX() >> 4;
		int cz = center.getZ() >> 4;
		for (int x = cx - chunks; x <= cx + chunks; x++) {
			for (int z = cz - chunks; z <= cz + chunks; z++) {
				if (!ChunkGate.loaded(level, x, z)) {
					continue;
				}
				for (BlockEntity blockEntity : level.getChunk(x, z).getBlockEntities().values()) {
					if (blockEntity instanceof LecternBlockEntity lectern && refresh(lectern.getBook(), name, cause, true)) {
						lectern.setChanged();
						updated++;
					} else if (blockEntity instanceof Container container && !(blockEntity instanceof net.minecraft.world.RandomizableContainer loot
							&& loot.getLootTable() != null)) {
						updated += refreshAll(container, name, cause);
					}
				}
			}
		}
		return updated;
	}
}
