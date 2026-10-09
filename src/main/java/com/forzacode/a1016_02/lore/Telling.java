package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.lore.TellingData.WrittenBook;
import com.forzacode.a1016_02.lore.TellingData.WrittenSign;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.Filterable;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WritableBookContent;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.SignBlockEntity;

/**
 * The telling watcher. Naming him puts him back: a sign or a book that names him (see {@link NameMatcher}), or
 * one written within {@code pacing.tellingRadius} of his traces ({@link TraceIndex}, D-041), and chat that names
 * him, are "telling". Each telling counts, raises attention ({@code NAMED_HIM}, {@code WROTE_NEAR_TRACES}) and
 * fires {@link HerobrineEvents#TELLING} with {@code namesHim}; only naming him sets {@code tellingStarted} and
 * starts Stage 3. Signs and books about him are remembered in {@link TellingData}; so is every sign written after
 * the first time he was named, for blank sign (both lists capped, oldest dropped). Destroying them lowers attention ({@code DESTROYED_OWN_WRITING}); the list (F06) burnt
 * in lava or fire lowers it sharply ({@code LIST_IN_LAVA}) and he raises a new pyramid ({@link ListPyramid}).
 */
public final class Telling {
	/** Custom data key stamped into a book written about him, holding its id in {@link TellingData}. */
	public static final String BOOK_MARKER = "a1016_02:telling";
	/** The telling count's mirror flag in {@code HerobrineState}, {@code lore:telling_count=<n>} (kept for compatibility). */
	public static final String COUNT_FLAG = HerobrineState.TELLING_COUNT_FLAG;
	/** Set once a copy of the list (F06) burnt in lava or fire. */
	public static final String LIST_BURNED_FLAG = "lore:list_burned";

	/** Where his traces are, for "written near his traces". */
	@FunctionalInterface
	interface Traces {
		boolean near(GlobalPos pos, int radius);
	}

	/** What a writing turned out to be. */
	record Told(boolean told, boolean namesHim, boolean nearTraces, boolean recorded) {
		static final Told NOTHING = new Told(false, false, false, false);
	}

	private static long nextVisitCheck;
	private static long nextBlankRetry;

	private Telling() {
	}

	static void reset() {
		nextVisitCheck = 0;
		nextBlankRetry = 0;
		LIVE.clear();
		ListPyramid.reset();
	}

	/** Every server tick: first visits to fragment sites, signs still to be blanked, pyramids still owed. */
	static void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now < nextVisitCheck && now < nextBlankRetry && TellingData.get(server).burned().pending() <= 0) {
			return;
		}
		TellingData data = TellingData.get(server);
		LoreConfig config = LoreConfig.get();
		if (now >= nextVisitCheck) {
			nextVisitCheck = now + Math.max(20, ModConfig.realTicks(config.visitCheckSeconds));
			recordVisits(server, HerobrineState.get(server), data);
		}
		if (now >= nextBlankRetry) {
			nextBlankRetry = now + Math.max(20, ModConfig.realTicks(config.pendingBlankRetrySeconds));
			if (!data.pendingBlanks().isEmpty()) {
				retryBlanks(server, data, SignEdits.editor(Services.traces()));
			}
		}
		ListPyramid.tick(server, data);
	}

	/** Placed fragments the player has now been near (PlayerWatch's visit sampling), in the order first noticed. */
	static void recordVisits(MinecraftServer server, HerobrineState state, TellingData data) {
		List<String> ids = new ArrayList<>(state.fragmentsPlaced().keySet());
		ids.sort(null);
		for (String id : ids) {
			GlobalPos pos = state.fragmentsPlaced().get(id);
			ServerLevel level = server.getLevel(pos.dimension());
			if (!data.visited().contains(id) && level != null
					&& Services.watch().lastVisitDay(level, net.minecraft.world.level.ChunkPos.containing(pos.pos())) >= 0) {
				data.addVisited(id);
			}
		}
	}

	/** Signs that should be blank (place not found's moved sign) and were in view then. */
	static void retryBlanks(MinecraftServer server, TellingData data, SignEdits.Editor editor) {
		for (GlobalPos at : List.copyOf(data.pendingBlanks())) {
			ServerLevel level = server.getLevel(at.dimension());
			if (level == null || !ChunkGate.loaded(level, at.pos())) {
				continue;
			}
			if (!(level.getBlockEntity(at.pos()) instanceof SignBlockEntity sign) || SignEdits.isBlank(sign)
					|| editor.edit(level, at.pos(), List.of(), List.of(), "lore:his/" + PlaceNotFound.CAUSE)) {
				data.removePendingBlank(at);
			}
		}
	}

	// --- live entry points (mixins and events) ---

	/** A player finished editing a sign (lore's sign mixin, server side). */
	public static void onSignWritten(ServerPlayer player, SignBlockEntity sign) {
		if (!(sign.getLevel() instanceof ServerLevel level)) {
			return;
		}
		MinecraftServer server = level.getServer();
		writeSign(player, level, sign.getBlockPos(), SignEdits.text(sign), HerobrineState.get(server), TellingData.get(server), live(server));
	}

	/** A player saved or signed a book in this inventory slot (lore's book mixin, after vanilla changed it). */
	public static void onBookWritten(ServerPlayer player, int slot, boolean signed) {
		MinecraftServer server = player.level().getServer();
		ItemStack stack = player.getInventory().getItem(slot);
		writeBook(player, stack, signed, HerobrineState.get(server), TellingData.get(server), live(server));
	}

	/** A chat line (or /me, /say) from a player. Only naming him counts. */
	static void onChat(ServerPlayer player, String text) {
		MinecraftServer server = player.level().getServer();
		chat(player, text, HerobrineState.get(server), TellingData.get(server));
	}

	/** A player broke a block (after it broke). */
	static void onBlockBroken(ServerPlayer player, ServerLevel level, BlockPos pos) {
		MinecraftServer server = level.getServer();
		signBroken(player, GlobalPos.of(level.dimension(), pos.immutable()), TellingData.get(server));
	}

	/** An item entity was destroyed by damage (lore's item mixin): a burnt list, or a book someone wrote about him. */
	public static void onItemDestroyed(ItemEntity entity, ServerLevel level, DamageSource source) {
		Entity owner = entity.getOwner();
		if (!(owner instanceof ServerPlayer thrower) || thrower.isDeadOrDying()) {
			return;
		}
		ItemStack stack = entity.getItem();
		TellingData data = TellingData.get(level.getServer());
		if (FragmentItems.is(stack, "F06") && source.is(DamageTypeTags.IS_FIRE)) {
			listBurned(thrower, GlobalPos.of(level.dimension(), entity.blockPosition()), data);
		}
		bookId(stack).ifPresent(id -> bookDestroyed(thrower, id, data));
	}

	// --- the rules (tests call these with their own state and data) ---

	/**
	 * A sign was written with {@code text} (both sides, see {@link SignEdits#text}). Blank or unchanged text tells
	 * nothing. A sign about him (naming him, or near his traces) is told and remembered; before the first time he
	 * was named other signs are not remembered, after it every sign is (blank sign's pool). The first sign naming him
	 * written in Stage 3 becomes the "Stop." candidate.
	 */
	static Told writeSign(ServerPlayer player, ServerLevel level, BlockPos pos, String text, HerobrineState state, TellingData data, Traces traces) {
		GlobalPos at = GlobalPos.of(level.dimension(), pos.immutable());
		Optional<WrittenSign> old = data.sign(at);
		if (text.isBlank()) {
			old.ifPresent(o -> forgetSign(at, data));
			return Told.NOTHING;
		}
		if (old.isPresent() && old.get().text().equals(text) && !old.get().blanked()) {
			return Told.NOTHING;
		}
		boolean names = NameMatcher.namesHim(text);
		boolean near = traces.near(at, ModConfig.pacing().tellingRadius);
		boolean about = names || near;
		if (!about && !data.told()) {
			return Told.NOTHING;
		}
		long seq = data.nextSeq();
		boolean after = data.told();
		long day = GameClock.day(level.getServer());
		if (about) {
			tell(player, text, pos, names, near, seq, day, state, data);
		}
		data.putSign(new WrittenSign(at, player.getUUID(), text, about, names, seq, day, after, false));
		if (names && data.stopCandidate().isEmpty() && !state.stopFired() && state.stage().atLeast(Stage.TELLING)) {
			data.setStopCandidate(at);
			A1016_02.LOGGER.info("[a1016] lore: the sign at {} is the one that will say \"Stop.\"", pos.toShortString());
		}
		return new Told(about, names, near, true);
	}

	/**
	 * A book in a player's hand was saved ({@code signed} false) or signed. A book about him is told (when its text
	 * or signing changed) and stamped with an id, so destroying it later is known.
	 */
	static Told writeBook(ServerPlayer player, ItemStack stack, boolean signed, HerobrineState state, TellingData data, Traces traces) {
		String text = bookText(stack);
		if (text.isBlank()) {
			return Told.NOTHING;
		}
		boolean names = NameMatcher.namesHim(text);
		GlobalPos at = GlobalPos.of(player.level().dimension(), player.blockPosition());
		boolean near = traces.near(at, ModConfig.pacing().tellingRadius);
		Optional<String> id = bookId(stack);
		if (!names && !near) {
			return Told.NOTHING;
		}
		int hash = text.hashCode();
		Optional<WrittenBook> old = id.flatMap(data::book);
		if (old.isPresent() && old.get().textHash() == hash && old.get().signed() == signed) {
			return Told.NOTHING;
		}
		String bookId = id.orElseGet(() -> UUID.randomUUID().toString());
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(BOOK_MARKER, bookId));
		long seq = data.nextSeq();
		long day = GameClock.day(player.level().getServer());
		tell(player, text, player.blockPosition(), names, near, seq, day, state, data);
		data.putBook(new WrittenBook(bookId, player.getUUID(), names || old.map(WrittenBook::namesHim).orElse(false), signed, seq, day, hash));
		return new Told(true, names, near, true);
	}

	/** A chat line that names him is telling (it is not "written", so nothing is remembered but the count). */
	static Told chat(ServerPlayer player, String text, HerobrineState state, TellingData data) {
		if (!NameMatcher.namesHim(text)) {
			return Told.NOTHING;
		}
		tell(player, text, player.blockPosition(), true, false, data.nextSeq(), GameClock.day(player.level().getServer()), state, data);
		return new Told(true, true, false, false);
	}

	/**
	 * Counts a telling, raises attention and fires {@code TELLING} with {@code namesHim}. Naming him also sets
	 * {@code tellingStarted} (the director starts Stage 3 on it, D-041); writing near his traces does not.
	 */
	static void tell(ServerPlayer player, String text, BlockPos pos, boolean names, boolean near, long seq, long day, HerobrineState state,
			TellingData data) {
		MinecraftServer server = player.level().getServer();
		// D-041: both count as telling; only naming him starts it (Stage 3, tellingStarted, the first telling).
		int count = state.incrementTellingCount();
		data.noteTelling(seq, day, names);
		if (names) {
			state.setTellingStarted(true);
		}
		if (names) {
			Attention.trigger(server, AttentionTrigger.NAMED_HIM);
		}
		if (near) {
			Attention.trigger(server, AttentionTrigger.WROTE_NEAR_TRACES);
		}
		A1016_02.LOGGER.info("[a1016] lore: {} told ({}{}), count {}", player.getName().getString(), names ? "named him" : "",
				near ? (names ? ", near his traces" : "near his traces") : "", count);
		HerobrineEvents.TELLING.invoker().onTelling(player, text, pos.immutable(), names);
	}

	/** The count read back from the shared state's flag (0 if none). */
	public static int countFromFlags(HerobrineState state) {
		for (String flag : state.flags()) {
			if (flag.startsWith(COUNT_FLAG + "=")) {
				try {
					return Integer.parseInt(flag.substring(COUNT_FLAG.length() + 1));
				} catch (NumberFormatException e) {
					return 0;
				}
			}
		}
		return 0;
	}

	/**
	 * A player broke a sign. It is forgotten; if it was theirs and about him, they destroyed their own writing.
	 * True if that lowered attention.
	 */
	static boolean signBroken(ServerPlayer player, GlobalPos at, TellingData data) {
		Optional<WrittenSign> sign = forgetSign(at, data);
		if (sign.isEmpty() || !sign.get().aboutHim() || !sign.get().writer().equals(player.getUUID())) {
			return false;
		}
		Attention.trigger(player.level().getServer(), AttentionTrigger.DESTROYED_OWN_WRITING);
		A1016_02.LOGGER.info("[a1016] lore: {} broke their own sign about him at {}", player.getName().getString(), at.pos().toShortString());
		return true;
	}

	private static Optional<WrittenSign> forgetSign(GlobalPos at, TellingData data) {
		Optional<WrittenSign> sign = data.removeSign(at);
		if (data.stopCandidate().filter(at::equals).isPresent()) {
			data.setStopCandidate(null);
		}
		if (data.stopSign().filter(at::equals).isPresent()) {
			data.setStopSign(null);
		}
		return sign;
	}

	/** A book about him was destroyed; if its writer threw it, they destroyed their own writing. */
	static boolean bookDestroyed(ServerPlayer thrower, String id, TellingData data) {
		Optional<WrittenBook> book = data.removeBook(id);
		if (book.isEmpty() || !book.get().writer().equals(thrower.getUUID())) {
			return false;
		}
		Attention.trigger(thrower.level().getServer(), AttentionTrigger.DESTROYED_OWN_WRITING);
		A1016_02.LOGGER.info("[a1016] lore: {} destroyed their own book about him", thrower.getName().getString());
		return true;
	}

	/** The list burnt: attention drops sharply, and he owes the world a pyramid (at most {@link LoreConfig#listPyramidMax}). */
	static void listBurned(ServerPlayer thrower, GlobalPos where, TellingData data) {
		Attention.trigger(thrower.level().getServer(), AttentionTrigger.LIST_IN_LAVA);
		HerobrineState.get(thrower.level().getServer()).setFlag(LIST_BURNED_FLAG, true);
		TellingData.Burned burned = data.burned();
		boolean owed = burned.owed() < LoreConfig.get().listPyramidMax;
		// A new burn far from the last one looks for its own ocean.
		Optional<GlobalPos> ocean = burned.near().filter(last -> last.dimension().equals(where.dimension())
				&& last.pos().distSqr(where.pos()) < 256.0 * 256.0).flatMap(last -> burned.ocean());
		data.setBurned(new TellingData.Burned(burned.lists() + 1, burned.owed() + (owed ? 1 : 0), burned.raised(), Optional.of(where), ocean));
		A1016_02.LOGGER.info("[a1016] lore: {} burnt the list at {}{}", thrower.getName().getString(), where.pos().toShortString(),
				owed ? "; a new pyramid will rise" : "");
	}

	// --- helpers ---

	/** The id stamped into a book written about him. */
	static Optional<String> bookId(ItemStack stack) {
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return Optional.empty();
		}
		return data.copyTag().getString(BOOK_MARKER);
	}

	/** Title and pages of a book and quill or a written book, as plain text. */
	static String bookText(ItemStack stack) {
		List<String> parts = new ArrayList<>();
		WritableBookContent writable = stack.get(DataComponents.WRITABLE_BOOK_CONTENT);
		if (writable != null) {
			writable.pages().stream().map(Filterable::raw).forEach(parts::add);
		}
		WrittenBookContent written = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		if (written != null) {
			parts.add(written.title().raw());
			written.pages().stream().map(page -> page.raw().getString()).forEach(parts::add);
		}
		return String.join("\n", parts).strip();
	}

	/** The live ledger's index, kept up to date incrementally (reset with the server). */
	private static final TraceIndex LIVE = new TraceIndex();

	/** His traces as the live world knows them (D-041): his sites, not lore's own builds, and his ledgered edits. */
	static Traces live(MinecraftServer server) {
		LoreData lore = LoreData.get(server);
		return (pos, radius) -> nearSite(pos, radius, Services.sites().all(), lore::isOwnSite) || LIVE.sync(TraceLedger.get(server)).near(pos, radius);
	}

	/** True if one of his sites (its rough extent counted) is within {@code radius} blocks. */
	static boolean nearSite(GlobalPos pos, int radius, Collection<SiteRegistry.Site> sites, java.util.function.IntPredicate loreBuilt) {
		BlockPos p = pos.pos();
		for (SiteRegistry.Site site : sites) {
			double reach = radius + Math.max(0, site.size());
			if (TraceIndex.isHis(site, loreBuilt) && site.dimension().equals(pos.dimension()) && site.pos().distSqr(p) <= reach * reach) {
				return true;
			}
		}
		return false;
	}

	/** {@link #live} over given sites (none built by lore) and ledger entries (tests). */
	static boolean near(GlobalPos pos, int radius, Collection<SiteRegistry.Site> sites, Collection<TraceLedger.Entry> entries) {
		return nearSite(pos, radius, sites, id -> false) || TraceIndex.of(entries).near(pos, radius);
	}
}
