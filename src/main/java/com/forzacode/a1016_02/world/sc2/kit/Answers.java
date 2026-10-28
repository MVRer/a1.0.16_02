package com.forzacode.a1016_02.world.sc2.kit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc.ScGates;
import com.forzacode.a1016_02.world.sc.WorldSc;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * How his places answer a visit (docs/SELF_CREATED_2.md, "Remains: common rules", D-106): an answer's edit is planned
 * and tried every {@value #TRY_EVERY_TICKS} ticks until it can be done out of everyone's view (its cells first through
 * the view rule, then {@link TraceService}, which checks again and ledgers it), and dropped after its time limit. Before
 * each try the fair-danger checks (D-097) hold for everyone inside: the switch and the story, never in their director
 * quiet, never in their first hour of play, at least the answer's health, carrying its tool; and its caps (per player per
 * in-game day, per place per day, per place ever). A try that fails a check waits for the next. Once done: the counts,
 * {@code REMAINS_ANSWERED} for everyone inside, and {@value #TENSION} tension each. One pending answer of an id per place.
 * Ending D gives every answer back through the ledger; {@link #giveBackWhenOff} gives back an answer's edits when the
 * layer is switched off and retries while it stays off. Server thread.
 *
 * <pre>{@code
 * Answers.Rules rules = Answers.Rules.of("16b", "world:s16/close").perPlaceEver(3).health(10).tool(Answers::pickaxe).limitSeconds(120);
 * Answers.LIVE.submit(level, place, rules, (lvl, p, inside) -> {
 *     List<BlockPos> cells = ...; // what moves where
 *     return Answers.Edit.of(cells, traces -> traces.batch(lvl, rules.cause()).move(a, b).move(c, d).commit());
 * });
 * }</pre>
 */
public final class Answers {
	/** How often a pending answer is tried (ticks: 5 s). */
	public static final int TRY_EVERY_TICKS = 100;
	/** A player's own first hour of play (ticks): nothing answers them in it. */
	public static final long FIRST_HOUR_TICKS = 3600L * 20L;
	/** Tension each player inside gets from an answer. */
	public static final double TENSION = 4.0;
	/** While the layer is off, answers not given back yet are tried again this often (ticks: 30 s). */
	public static final int GIVE_BACK_RETRY_TICKS = 600;

	/**
	 * An answer's rules.
	 *
	 * @param answer       its id ({@code "16b"}): the caps and the event
	 * @param cause        its ledger cause ({@code "world:s16/close"})
	 * @param perPlayerDay at most this many a player per in-game day (the common rule: 1)
	 * @param perPlaceDay  at most this many a place per in-game day
	 * @param perPlaceEver at most this many a place ever
	 * @param minHealth    everyone inside has at least this much health
	 * @param tool         everyone inside carries a stack this accepts (null: no tool rule)
	 * @param limitTicks   dropped this long after it was asked for if it could not be done
	 * @param counts       false for a later edit of an answer already counted (16c's next torch): no caps, no counts, no
	 *                     event, no tension; the fair-danger checks still hold
	 */
	public record Rules(String answer, String cause, int perPlayerDay, int perPlaceDay, int perPlaceEver, float minHealth,
			@Nullable Predicate<ItemStack> tool, long limitTicks, boolean counts) {
		/** One a player a day, one a place a day, no cap ever, no health or tool rule, 120 s, counted. */
		public static Rules of(String answer, String cause) {
			return new Rules(answer, cause, 1, 1, Integer.MAX_VALUE, 0, null, 120 * 20, true);
		}

		public Rules perPlayerDay(int n) {
			return new Rules(answer, cause, n, perPlaceDay, perPlaceEver, minHealth, tool, limitTicks, counts);
		}

		public Rules perPlaceDay(int n) {
			return new Rules(answer, cause, perPlayerDay, n, perPlaceEver, minHealth, tool, limitTicks, counts);
		}

		public Rules perPlaceEver(int n) {
			return new Rules(answer, cause, perPlayerDay, perPlaceDay, n, minHealth, tool, limitTicks, counts);
		}

		public Rules health(float min) {
			return new Rules(answer, cause, perPlayerDay, perPlaceDay, perPlaceEver, min, tool, limitTicks, counts);
		}

		public Rules tool(@Nullable Predicate<ItemStack> accepts) {
			return new Rules(answer, cause, perPlayerDay, perPlaceDay, perPlaceEver, minHealth, accepts, limitTicks, counts);
		}

		public Rules limitSeconds(double seconds) {
			return new Rules(answer, cause, perPlayerDay, perPlaceDay, perPlaceEver, minHealth, tool, Math.round(seconds * 20), counts);
		}

		/** A later part of an answer already counted. */
		public Rules part() {
			return new Rules(answer, cause, perPlayerDay, perPlaceDay, perPlaceEver, minHealth, tool, limitTicks, false);
		}
	}

	/** One try's edit: the cells it touches (for the view rule) and what commits it (a {@code TraceService} batch). */
	public interface Edit {
		Collection<BlockPos> cells();

		/** Applies it through {@code traces}; false if it could not (in view, vetoed, not replaceable): tried again. */
		boolean commit(TraceService traces);

		static Edit of(Collection<BlockPos> cells, Function<TraceService, Boolean> commit) {
			List<BlockPos> copy = List.copyOf(cells);
			return new Edit() {
				@Override
				public Collection<BlockPos> cells() {
					return copy;
				}

				@Override
				public boolean commit(TraceService traces) {
					return Boolean.TRUE.equals(commit.apply(traces));
				}
			};
		}
	}

	/** Plans one try now, or null if there is nothing to do yet (it waits). */
	@FunctionalInterface
	public interface Planner {
		@Nullable Edit plan(ServerLevel level, Remains.Place place, List<ServerPlayer> inside);
	}

	/** What happens once an answer is done (the live one: the event and the tension). */
	@FunctionalInterface
	public interface Sink {
		void answered(ServerLevel level, Remains.Place place, String answer, ServerPlayer player);
	}

	/** Who is inside a place now. */
	@FunctionalInterface
	public interface Inside {
		List<ServerPlayer> of(MinecraftServer server, Remains.Place place);
	}

	/** Out of everyone's view (the live one: {@link TraceService#isOutOfView}). */
	@FunctionalInterface
	public interface View {
		boolean outOfView(ServerLevel level, Collection<BlockPos> cells);
	}

	/**
	 * What answers read and write: the live world's ({@link #LIVE}) or a test's.
	 *
	 * @param data      the counts
	 * @param traces    every edit
	 * @param view      the view rule asked before a commit
	 * @param inside    who is inside
	 * @param quiet     in their director quiet
	 * @param playTicks their own play time
	 * @param story     why the story holds the layer back now, or null ({@link ScGates#worldRefusal} live)
	 * @param day       today's in-game day
	 * @param now       the server's tick count
	 * @param sink      what a done answer does
	 */
	public record Env(Function<MinecraftServer, Sc2Data> data, TraceService traces, View view, Inside inside, Predicate<UUID> quiet,
			ToLongFunction<UUID> playTicks, Function<MinecraftServer, @Nullable String> story, LongSupplier day, LongSupplier now, Sink sink) {
	}

	/** What a submit came to. */
	public enum Status {
		/** Done at once. */
		DONE,
		/** Waiting for a try that can be done (out of view, the checks met). */
		WAITING,
		/** Refused now; nothing waits. */
		REFUSED
	}

	public record Result(Status status, String reason) {
	}

	private record Pending(ServerLevel level, Remains.Place place, Rules rules, Planner planner, long deadline) {
	}

	/** The live answers. */
	public static final Answers LIVE = new Answers(null);

	private final @Nullable Env fixed;
	private final Map<String, Pending> pending = new LinkedHashMap<>();
	private final Map<String, String> waitingWhy = new LinkedHashMap<>();
	private static final Map<String, String> GIVE_BACKS = new LinkedHashMap<>();
	private static final Set<String> GIVE_BACK_PENDING = new java.util.LinkedHashSet<>();

	private Answers(@Nullable Env fixed) {
		this.fixed = fixed;
	}

	/** A test's own answers over this env. */
	public static Answers over(Env env) {
		return new Answers(env);
	}

	/** The live env of this server. */
	public static Env live(MinecraftServer server) {
		TraceService traces = Services.traces();
		return new Env(s -> RemainsImpl.LIVE.data(), traces, traces::isOutOfView, Visits.LIVE::inside, uuid -> Services.director().inQuiet(server, uuid),
				uuid -> GameClock.playTicks(server, uuid), Answers::story, () -> GameClock.day(server),
				server::getTickCount, (level, place, answer, player) -> {
					HerobrineEvents.REMAINS_ANSWERED.invoker().onAnswered(level, place, answer, player.getUUID());
					Attention.raiseTension(server, player.getUUID(), TENSION, "s2 " + answer);
				});
	}

	private Env env(MinecraftServer server) {
		return fixed != null ? fixed : live(server);
	}

	/** The reason {@link #held} and {@link #refusal} give while the world is under Traces (D-108: his places sleep). */
	public static final String ASLEEP = "asleep";

	/**
	 * The live story's reason to hold the places back now, or null: the story's own gates ({@code ScGates.worldRefusal}:
	 * quiet for good, path C, the endings), then {@link #ASLEEP} while the world's stage is under Traces (D-108: the
	 * places are there from the world's creation and do nothing until then). A test's env passes its own.
	 */
	public static @Nullable String story(MinecraftServer server) {
		HerobrineState state = HerobrineState.get(server);
		String world = ScGates.worldRefusal(state);
		if (world != null) {
			return world;
		}
		return state.stage().level() < Stage.TRACES.level() ? ASLEEP : null;
	}

	private static String key(Remains.Place place, String answer) {
		return place.dimension().identifier() + "/" + place.anchor().asLong() + "/" + place.kind() + "/" + answer;
	}

	/** True if a stack anywhere in this player's inventory is accepted. */
	public static boolean carries(ServerPlayer player, Predicate<ItemStack> accepts) {
		Inventory inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (!stack.isEmpty() && accepts.test(stack)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * True if a living thing (a pet, a mob, a player; never a spectator) stands in any of these cells: a block moved in
	 * there would shut it in the wall (R12-11). Loaded cells only are asked; one not loaded counts as taken.
	 */
	public static boolean occupied(ServerLevel level, Collection<BlockPos> cells) {
		for (BlockPos cell : cells) {
			if (!level.isLoaded(cell)) {
				return true;
			}
			if (!level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(cell), e -> !e.isSpectator()).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** True if every one of these cells is loaded (a planner never loads a chunk: R12-20). */
	public static boolean loaded(ServerLevel level, Collection<BlockPos> cells) {
		for (BlockPos cell : cells) {
			if (!level.isLoaded(cell)) {
				return false;
			}
		}
		return true;
	}

	/** A pickaxe (any). */
	public static boolean pickaxe(ItemStack stack) {
		return stack.is(ItemTags.PICKAXES);
	}

	/** A tool that digs this block and gets it back. */
	public static Predicate<ItemStack> digs(BlockState state) {
		return stack -> stack.isCorrectToolForDrops(state);
	}

	/**
	 * Why nothing of the layer's places may happen now, whatever and wherever: the switch off, or the story's own gates
	 * ({@code ScGates.worldRefusal}: quiet for good, path C, the endings), or the places asleep under Traces
	 * ({@link #ASLEEP}, D-108; the live env's {@link #story}), or null. For rules that are not answers (17a, 17c: R12-10).
	 * {@code core.Remains.awake} is this being null.
	 */
	public @Nullable String held(MinecraftServer server) {
		if (!SelfCreated.enabled()) {
			return "switched off";
		}
		return env(server).story().apply(server);
	}

	/**
	 * Why this answer may not happen at this place now, or null: the switch and the story (asleep under Traces included,
	 * D-108), nobody inside (a creative or spectator player is never inside: D-116), the place not known, then for everyone
	 * inside their quiet, their first hour, their health, their combat within 30 s and their tool, then (if it counts) the
	 * caps.
	 */
	public @Nullable String refusal(MinecraftServer server, Remains.Place place, Rules rules) {
		return refusal(env(server), server, place, rules, null);
	}

	private @Nullable String refusal(Env env, MinecraftServer server, Remains.Place place, Rules rules, @Nullable List<ServerPlayer> insideOut) {
		if (!SelfCreated.enabled()) {
			return "switched off";
		}
		String story = env.story().apply(server);
		if (story != null) {
			return story;
		}
		Sc2Data data = env.data().apply(server);
		if (data.known(place).isEmpty()) {
			return "not a known place";
		}
		List<ServerPlayer> inside = env.inside().of(server, place);
		if (inside.isEmpty()) {
			return "nobody inside";
		}
		long today = env.day().getAsLong();
		for (ServerPlayer player : inside) {
			String name = player.getName().getString();
			// R10-7: a player's first time of each answer (and its later parts that day) comes even in their quiet: they
			// chose to go in. Later ones keep D-106.
			Sc2Data.Spent mine = data.spent(player.getUUID(), rules.answer());
			boolean first = rules.counts() ? mine.ever() == 0 : mine.ever() == 0 || mine.ever() == 1 && mine.day() == today;
			if (env.quiet().test(player.getUUID()) && !first) {
				return name + " is in their quiet";
			}
			// A creative player is inside only where a kind counts them (Visits.CREATIVE_INSIDE, R3-2: tried in a creative
			// world); the first hour protects a player's first play, not a test.
			if (!player.isCreative() && env.playTicks().applyAsLong(player.getUUID()) < FIRST_HOUR_TICKS) {
				return name + " is in their first hour";
			}
			if (player.getHealth() < rules.minHealth()) {
				return name + " has under " + rules.minHealth() + " health";
			}
			// R3-V: never within 30 s of the combat of anyone inside (the layer's common gate, as 33's answers hold it):
			// a way shut, a light taken or a glimpse never comes while someone fights.
			String fight = ScGates.combatRefusal(Services.watch().ticksSinceCombat(player));
			if (fight != null) {
				return name + " " + fight;
			}
			if (rules.tool() != null && !carries(player, rules.tool())) {
				return name + " carries no tool for it";
			}
			if (rules.tool() != null && !player.getAbilities().mayBuild) {
				// R12-25: a tool is no way out for one who may not break blocks (adventure mode).
				return name + " may not dig";
			}
			if (rules.counts() && data.spent(player.getUUID(), rules.answer()).on(today) >= rules.perPlayerDay()) {
				return name + " had it today";
			}
		}
		if (rules.counts()) {
			Sc2Data.Spent here = data.spent(place, rules.answer());
			if (here.on(today) >= rules.perPlaceDay()) {
				return "the place had it today";
			}
			if (here.ever() >= rules.perPlaceEver()) {
				return "the place had it " + here.ever() + " times";
			}
		}
		if (insideOut != null) {
			insideOut.addAll(inside);
		}
		return null;
	}

	/**
	 * Asks for an answer: refused if its checks fail now (nothing waits), else tried at once and, if that cannot be done
	 * yet, again every {@value #TRY_EVERY_TICKS} ticks until it is or its limit passes. Refused too while the same answer
	 * waits at this place.
	 */
	public Result submit(ServerLevel level, Remains.Place place, Rules rules, Planner planner) {
		MinecraftServer server = level.getServer();
		Env env = env(server);
		String key = key(place, rules.answer());
		if (pending.containsKey(key)) {
			return new Result(Status.REFUSED, "already waiting");
		}
		String refused = refusal(env, server, place, rules, null);
		if (refused != null) {
			return new Result(Status.REFUSED, refused);
		}
		Pending ask = new Pending(level, place, rules, planner, env.now().getAsLong() + rules.limitTicks());
		String why = attempt(env, server, ask);
		if (why == null) {
			return new Result(Status.DONE, "done");
		}
		pending.put(key, ask);
		waitingWhy.put(key, why);
		return new Result(Status.WAITING, why);
	}

	/** The live tick: every {@value #TRY_EVERY_TICKS} ticks, and the give-backs' retry while the layer is off. */
	public void tick(MinecraftServer server) {
		if (server.getTickCount() % TRY_EVERY_TICKS == 9 && !pending.isEmpty()) {
			tryAll(server);
		}
		if (server.getTickCount() % GIVE_BACK_RETRY_TICKS == 29 && !GIVE_BACK_PENDING.isEmpty()) {
			retryGiveBacks(server);
		}
	}

	/** Tries every pending answer once now (dropping the expired). */
	public void tryAll(MinecraftServer server) {
		Env env = env(server);
		long now = env.now().getAsLong();
		Iterator<Map.Entry<String, Pending>> it = pending.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<String, Pending> entry = it.next();
			Pending ask = entry.getValue();
			if (now > ask.deadline()) {
				it.remove();
				A1016_02.LOGGER.debug("[a1016] s2: {} dropped at its limit ({})", ask.rules().answer(), waitingWhy.remove(entry.getKey()));
				continue;
			}
			String why = attempt(env, server, ask);
			if (why == null) {
				it.remove();
				waitingWhy.remove(entry.getKey());
			} else {
				waitingWhy.put(entry.getKey(), why);
			}
		}
	}

	/** One try: null if done, else why it waits. */
	private @Nullable String attempt(Env env, MinecraftServer server, Pending ask) {
		List<ServerPlayer> inside = new ArrayList<>();
		String refused = refusal(env, server, ask.place(), ask.rules(), inside);
		if (refused != null) {
			return refused;
		}
		Edit edit = ask.planner().plan(ask.level(), ask.place(), List.copyOf(inside));
		if (edit == null) {
			return "nothing to do yet";
		}
		if (!env.view().outOfView(ask.level(), edit.cells())) {
			return "in view";
		}
		if (!edit.commit(env.traces())) {
			return "could not be done";
		}
		if (ask.rules().counts()) {
			env.data().apply(server).spend(ask.place(), ask.rules().answer(), inside.stream().map(ServerPlayer::getUUID).toList(), env.day().getAsLong());
			for (ServerPlayer player : inside) {
				env.sink().answered(ask.level(), ask.place(), ask.rules().answer(), player);
			}
		}
		return null;
	}

	/** True if this answer waits at this place. */
	public boolean waiting(Remains.Place place, String answer) {
		return pending.containsKey(key(place, answer));
	}

	/** Why this answer waits at this place (its last try), or null if it does not. */
	public @Nullable String whyWaiting(Remains.Place place, String answer) {
		return waitingWhy.get(key(place, answer));
	}

	/** Forgets every pending answer (switch off, server stop). */
	public void clear() {
		pending.clear();
		waitingWhy.clear();
	}

	// --- the switch ---

	/**
	 * Registers the switch's give-back for an answer's edits (in init): when the layer is switched off (and at a start
	 * while it is off) every ledger entry with {@code cause} (or under it) that is loaded and out of view is given back
	 * ({@link WorldSc#giveBackAll}); what waits is tried again every {@value #GIVE_BACK_RETRY_TICKS} ticks while the layer
	 * stays off. {@code id} names the revert ({@code "sc16"}).
	 */
	public static synchronized void giveBackWhenOff(String id, String cause) {
		GIVE_BACKS.put(id, cause);
		SelfCreated.onSwitchedOff(id, server -> giveBack(server, id));
	}

	private static void giveBack(MinecraftServer server, String id) {
		String cause = GIVE_BACKS.get(id);
		if (cause == null) {
			return;
		}
		WorldSc.Back back = WorldSc.giveBackAll(server, cause);
		if (back.waiting() > 0) {
			GIVE_BACK_PENDING.add(id);
		} else {
			GIVE_BACK_PENDING.remove(id);
		}
	}

	private static void retryGiveBacks(MinecraftServer server) {
		if (SelfCreated.enabled()) {
			GIVE_BACK_PENDING.clear();
			return;
		}
		for (String id : List.copyOf(GIVE_BACK_PENDING)) {
			giveBack(server, id);
		}
	}

	/** Server stop: nothing waits. */
	public static void stopped() {
		LIVE.clear();
		GIVE_BACK_PENDING.clear();
	}
}
