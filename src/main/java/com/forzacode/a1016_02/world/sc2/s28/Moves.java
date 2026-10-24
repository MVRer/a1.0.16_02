package com.forzacode.a1016_02.world.sc2.s28;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.world.sc.ScGates;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

import org.jspecify.annotations.Nullable;

/**
 * 28's rules (docs/SELF_CREATED_2.md, 28): one stack from a container in one player's base into an empty slot of a
 * container in another's, through {@code TraceService.moveStack} (ledgered MOVE_STACK, cause {@value #CAUSE}; Ending D and
 * the switch give it back). Two participants A (whose stack goes) and B (who gets it), each with a known base in the
 * overworld ({@code world.Bases}), the bases at least twice the visit radius apart (one shared house is one base); B
 * visited A's base within {@code visitDays}; the move never comes from the base the last one came from (directions take
 * turns); A and B each {@code awayDistance} from their own base, asleep, or in another dimension. The stack: food,
 * building blocks (a full block without a block entity), raw ores, ingots and the like ({@link #movable}), at most
 * {@code maxCount}, common, stackable, never named, enchanted, written, marked (custom data: the story's own items) or
 * holding items; from any container in A's base but the one worth the most ({@link #worth}; a base with one container
 * gives nothing); into a container in B's base with an empty slot. Containers: chests and barrels a player placed
 * ({@code PlayerWatch.placedNear}) within the visit radius of the base, loaded, never a known player's first chest, never
 * in a protected area; a double chest is one container. Both out of everyone's view and nobody within
 * {@code nobodyWithin} of either (an owner asleep in their own base aside). The fake: a stack moved between two of the
 * target's own containers, under the same rules. At most {@code maxPerWorld}, {@code minDaysApart} apart (fakes count);
 * forced skips the visit, the turns and the caps, never the stack, the containers, the distances or the view. Server
 * thread.
 */
public final class Moves {
	public static final String ID = "sc_28";
	public static final String CAUSE = "world:sc28";

	/** Raw ores, ingots, nuggets and the plain minerals a player keeps in stacks. */
	static final Set<Item> MINERALS = Set.of(Items.COAL, Items.CHARCOAL, Items.RAW_IRON, Items.RAW_GOLD, Items.RAW_COPPER, Items.IRON_INGOT,
			Items.GOLD_INGOT, Items.COPPER_INGOT, Items.IRON_NUGGET, Items.GOLD_NUGGET, Items.COPPER_NUGGET, Items.REDSTONE, Items.LAPIS_LAZULI,
			Items.QUARTZ, Items.AMETHYST_SHARD, Items.FLINT, Items.CLAY_BALL);
	/** What makes a container worth more than a pile of stone: these count as much as a tool. */
	static final Set<Item> PRECIOUS = Set.of(Items.DIAMOND, Items.EMERALD, Items.NETHERITE_INGOT, Items.NETHERITE_SCRAP, Items.ANCIENT_DEBRIS,
			Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, Items.NETHERITE_BLOCK, Items.GOLD_BLOCK, Items.IRON_BLOCK);

	/** Food or blocks a player keeps for a reason, never moved: the golden apples, the precious blocks. */
	static final Set<Item> KEPT = Set.of(Items.GOLDEN_APPLE, Items.ENCHANTED_GOLDEN_APPLE, Items.DIAMOND_BLOCK, Items.EMERALD_BLOCK, Items.NETHERITE_BLOCK,
			Items.GOLD_BLOCK, Items.IRON_BLOCK, Items.ANCIENT_DEBRIS, Items.DIAMOND_ORE, Items.DEEPSLATE_DIAMOND_ORE, Items.EMERALD_ORE,
			Items.DEEPSLATE_EMERALD_ORE, Items.BUDDING_AMETHYST, Items.DRAGON_EGG);

	/** One container: a barrel, a single chest, or both halves of a double chest. */
	public record Box(List<BlockPos> halves) {
		public BlockPos first() {
			return halves.getFirst();
		}
	}

	/** One move: {@code slot} of the container at {@code from} into the container at {@code to}, whose bases they are. */
	public record Move(BlockPos from, int slot, BlockPos to, ItemStack stack, UUID fromPlayer, UUID toPlayer, boolean fake) {
	}

	/** What a plan found: a move, or why none ({@code deep}: the pair's own gates held and the containers said no). */
	public record Plan(@Nullable Move move, String reason, boolean deep) {
		Plan(@Nullable Move move, String reason) {
			this(move, reason, move != null);
		}

		static Plan none(String reason) {
			return new Plan(null, reason, false);
		}

		Plan deeper() {
			return new Plan(move, reason, true);
		}

		public boolean found() {
			return move != null;
		}
	}

	/** What a fire did. */
	public record Outcome(com.forzacode.a1016_02.core.FireResult result, @Nullable Move move, String reason) {
	}

	private Moves() {
	}

	// --- the stack (pure) ---

	/**
	 * True for a stack that may go: at most {@code maxCount} items, stackable, common, no name, enchantment, writing,
	 * custom data or contents, and food, a building block or a mineral ({@link #MINERALS}).
	 */
	public static boolean movable(ItemStack stack, int maxCount) {
		if (stack.isEmpty() || stack.getCount() > maxCount || stack.getMaxStackSize() <= 1 || stack.isDamageableItem()) {
			return false;
		}
		if (stack.getRarity() != Rarity.COMMON || KEPT.contains(stack.getItem()) || stack.isEnchanted() || stack.has(DataComponents.STORED_ENCHANTMENTS) || stack.has(DataComponents.CUSTOM_NAME)
				|| stack.has(DataComponents.CUSTOM_DATA) || stack.has(DataComponents.WRITTEN_BOOK_CONTENT) || stack.has(DataComponents.WRITABLE_BOOK_CONTENT)
				|| stack.has(DataComponents.CONTAINER) || stack.has(DataComponents.BUNDLE_CONTENTS) || stack.has(DataComponents.MAP_ID)) {
			return false;
		}
		if (stack.has(DataComponents.FOOD) || MINERALS.contains(stack.getItem())) {
			return true;
		}
		return buildingBlock(stack);
	}

	/** A block item whose block is a full cube without a block entity: stone, planks, logs, dirt, ores, wool, glass. */
	static boolean buildingBlock(ItemStack stack) {
		if (!(stack.getItem() instanceof BlockItem item)) {
			return false;
		}
		Block block = item.getBlock();
		if (block instanceof EntityBlock || block == Blocks.TNT) {
			return false;
		}
		BlockState state = block.defaultBlockState();
		return state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
	}

	/** What one stack is worth to whoever keeps it: gear, enchanted and rare things far more than stone. */
	static long unitWorth(ItemStack stack) {
		if (stack.isEnchanted() || stack.has(DataComponents.STORED_ENCHANTMENTS)) {
			return 256;
		}
		long worth = switch (stack.getRarity()) {
			case UNCOMMON -> 8;
			case RARE -> 32;
			case EPIC -> 128;
			default -> 1;
		};
		if (stack.getMaxStackSize() <= 1 || PRECIOUS.contains(stack.getItem())) {
			worth = Math.max(worth, 64);
		} else if (MINERALS.contains(stack.getItem())) {
			worth = Math.max(worth, 4);
		}
		return worth;
	}

	/** A container's worth: each stack's count times what one is worth ({@link #unitWorth}). */
	public static long worth(List<ItemStack> contents) {
		long sum = 0;
		for (ItemStack stack : contents) {
			if (!stack.isEmpty()) {
				sum += stack.getCount() * unitWorth(stack);
			}
		}
		return sum;
	}

	// --- containers ---

	/** True for a block whose container may give or take: a chest (copper and trapped too) or a barrel. */
	public static boolean kind(BlockState state) {
		return state.getBlock() instanceof ChestBlock || state.is(Blocks.BARREL);
	}

	/** True if every chunk within {@code radius} of {@code center} is loaded (nothing here ever loads one). */
	static boolean areaLoaded(Level level, BlockPos center, int radius) {
		for (int x = center.getX() - radius; x <= center.getX() + radius + 15; x += 16) {
			for (int z = center.getZ() - radius; z <= center.getZ() + radius + 15; z += 16) {
				if (!level.isLoaded(new BlockPos(Math.min(x, center.getX() + radius), center.getY(), Math.min(z, center.getZ() + radius)))) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * The containers a player placed within the visit radius of {@code base}, each double chest once, never a known
	 * player's first chest nor one in a protected area. Empty if the base's area is not loaded.
	 */
	public static List<Box> boxes(S28Env env, ServerLevel level, BlockPos base) {
		int radius = Math.max(1, env.config().visitRadius);
		if (!areaLoaded(level, base, radius)) {
			return List.of();
		}
		List<BlockPos> firsts = firstChests(env.state(), level);
		List<Box> boxes = new ArrayList<>();
		List<BlockPos> seen = new ArrayList<>();
		for (BlockPos pos : env.bases().watch().placedNear(level, base, radius, Moves::kind)) {
			if (seen.contains(pos) || !(level.getBlockEntity(pos) instanceof Container) || !storage(level.getBlockEntity(pos))) {
				continue;
			}
			List<BlockPos> halves = new ArrayList<>();
			halves.add(pos.immutable());
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
				BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
				if (level.isLoaded(other) && level.getBlockState(other).getBlock() instanceof ChestBlock && level.getBlockEntity(other) instanceof Container) {
					halves.add(other.immutable());
				}
			}
			seen.addAll(halves);
			if (halves.stream().anyMatch(firsts::contains) || halves.stream().anyMatch(h -> env.protect().isProtected(level.dimension(), h))) {
				continue;
			}
			boxes.add(new Box(List.copyOf(halves)));
		}
		return boxes;
	}

	private static boolean storage(@Nullable BlockEntity entity) {
		return entity instanceof ChestBlockEntity || entity instanceof BarrelBlockEntity;
	}

	/** Every known player's first chest in this level (the story's first blocks are never touched). */
	static List<BlockPos> firstChests(HerobrineState state, ServerLevel level) {
		List<BlockPos> chests = new ArrayList<>();
		for (HerobrineState.Subject subject : state.players()) {
			PlacedBlock chest = state.firstBlocks(subject.uuid()).chest();
			if (chest != null && chest.pos().dimension().equals(level.dimension())) {
				chests.add(chest.pos().pos());
			}
		}
		return chests;
	}

	/** Everything in a container, both halves. */
	public static List<ItemStack> contents(ServerLevel level, Box box) {
		List<ItemStack> all = new ArrayList<>();
		for (BlockPos half : box.halves()) {
			if (level.getBlockEntity(half) instanceof Container container) {
				for (int i = 0; i < container.getContainerSize(); i++) {
					all.add(container.getItem(i));
				}
			}
		}
		return all;
	}

	/** The container worth the most ({@link #worth}), or null for none. */
	static @Nullable Box richest(ServerLevel level, List<Box> boxes) {
		return boxes.stream().max(Comparator.comparingLong(b -> worth(contents(level, b)))).orElse(null);
	}

	/** A half of the container with an empty slot, or null. */
	static @Nullable BlockPos roomIn(ServerLevel level, Box box) {
		for (BlockPos half : box.halves()) {
			if (level.getBlockEntity(half) instanceof Container container) {
				for (int i = 0; i < container.getContainerSize(); i++) {
					if (container.getItem(i).isEmpty()) {
						return half;
					}
				}
			}
		}
		return null;
	}

	/** Every movable stack of the container, as (half, slot). */
	static List<Taken> takeable(ServerLevel level, Box box, int maxCount) {
		List<Taken> found = new ArrayList<>();
		for (BlockPos half : box.halves()) {
			if (level.getBlockEntity(half) instanceof Container container) {
				for (int i = 0; i < container.getContainerSize(); i++) {
					ItemStack stack = container.getItem(i);
					if (movable(stack, maxCount)) {
						found.add(new Taken(half, i, stack.copy()));
					}
				}
			}
		}
		return found;
	}

	record Taken(BlockPos half, int slot, ItemStack stack) {
	}

	/**
	 * Why this container may not be touched now, or null: someone within {@code nobodyWithin} of it (anyone in the level,
	 * spectators included, but {@code sleeper} while asleep: its owner in their own base), or in view.
	 */
	static @Nullable String boxRefusal(S28Env env, ServerLevel level, Box box, @Nullable ServerPlayer sleeper) {
		List<ServerPlayer> people = new ArrayList<>(env.people().in(level));
		if (sleeper != null && env.bases().watch().isSleeping(sleeper)) {
			people.removeIf(p -> p.getUUID().equals(sleeper.getUUID()));
		}
		ServerPlayer near = ScGates.within(people, box.halves(), env.config().nobodyWithin);
		if (near != null) {
			return ScGates.name(near) + " is within " + (int) env.config().nobodyWithin + " of the container at " + box.first().toShortString();
		}
		if (!env.view().outOfView(level, box.halves())) {
			return "the container at " + box.first().toShortString() + " is in view";
		}
		return null;
	}

	// --- the players ---

	/** The player's base in the overworld, if world knows one. */
	static Optional<BlockPos> base(S28Env env, UUID player, ServerLevel overworld) {
		return env.bases().base(player).filter(b -> b.dimension().equals(overworld.dimension())).map(GlobalPos::pos);
	}

	/** True if the player is away from their own base: another dimension, {@code awayDistance} off horizontally, or asleep. */
	static boolean away(S28Env env, ServerPlayer player, BlockPos base, ServerLevel overworld) {
		if (player.level() != overworld || env.bases().watch().isSleeping(player)) {
			return true;
		}
		return ScGates.horizontalSqr(player, base) >= env.config().awayDistance * env.config().awayDistance;
	}

	static double flat(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/**
	 * Why a stack may not go from {@code a}'s base to {@code b}'s now, or null: both bases known in the overworld and far
	 * enough apart, b's visit to a's base within {@code visitDays} and the turns (unless forced), both away.
	 */
	static @Nullable String pairRefusal(S28Env env, ServerPlayer a, ServerPlayer b, ServerLevel overworld, boolean forced) {
		Sc28Config config = env.config();
		Optional<BlockPos> baseA = base(env, a.getUUID(), overworld);
		Optional<BlockPos> baseB = base(env, b.getUUID(), overworld);
		if (baseA.isEmpty() || baseB.isEmpty()) {
			return (baseA.isEmpty() ? ScGates.name(a) : ScGates.name(b)) + " has no base in the overworld";
		}
		if (flat(baseA.get(), baseB.get()) < 2.0 * config.visitRadius) {
			return "the two bases are one (within " + 2 * config.visitRadius + ")";
		}
		if (!forced) {
			long visit = env.data().lastVisit(b.getUUID(), a.getUUID());
			long now = GameClock.dayTicks(env.server());
			if (visit < 0 || now - visit > (long) (config.visitDays * 24000.0)) {
				return ScGates.name(b) + " did not come by " + ScGates.name(a) + "'s base in the last " + config.visitDays + " days";
			}
			if (a.getUUID().equals(env.data().lastFrom())) {
				return "the last one came from " + ScGates.name(a) + "'s base (they take turns)";
			}
		}
		if (!away(env, a, baseA.get(), overworld)) {
			return ScGates.name(a) + " is within " + (int) config.awayDistance + " of their base and awake";
		}
		if (!away(env, b, baseB.get(), overworld)) {
			return ScGates.name(b) + " is within " + (int) config.awayDistance + " of their base and awake";
		}
		return null;
	}

	// --- the plan ---

	/** Why nothing may happen for this target now (the common gates, then the caps unless forced), or null. */
	static @Nullable String gate(S28Env env, ServerPlayer target, boolean forced) {
		String refusal = ScGates.targetRefusal(env.state(), env.bases().watch(), target, target.level(), forced);
		if (refusal != null) {
			return refusal;
		}
		if (!forced) {
			Sc28Config config = env.config();
			if (env.data().count() >= config.maxPerWorld) {
				return env.data().count() + " already (at most " + config.maxPerWorld + ")";
			}
			long today = GameClock.day(env.server());
			if (env.data().lastDay() >= 0 && today - env.data().lastDay() < config.minDaysApart) {
				return "the last was on day " + env.data().lastDay() + " (" + config.minDaysApart + " days apart)";
			}
		}
		return null;
	}

	/**
	 * The move for {@code target} now, or why none: the gates, then each pair with a friend, the target's own base giving
	 * first when the last move did not come from it (the turns), the friends in random order; or the fake.
	 */
	public static Plan plan(S28Env env, ServerPlayer target, boolean forced, boolean fake, RandomSource random) {
		String gated = gate(env, target, forced);
		if (gated != null) {
			return Plan.none(gated);
		}
		ServerLevel overworld = env.server().overworld();
		if (fake) {
			return fake(env, target, overworld, random);
		}
		List<ServerPlayer> friends = new ArrayList<>(env.bases().participants());
		friends.removeIf(p -> p.getUUID().equals(target.getUUID()));
		if (friends.isEmpty()) {
			return Plan.none("nobody else takes part");
		}
		net.minecraft.util.Util.shuffle(friends, random);
		List<ServerPlayer[]> pairs = new ArrayList<>();
		for (ServerPlayer friend : friends) {
			pairs.add(new ServerPlayer[] {target, friend});
			pairs.add(new ServerPlayer[] {friend, target});
		}
		// The turns first: a pair whose giver was not the last one's giver.
		UUID last = env.data().lastFrom();
		pairs.sort(Comparator.comparingInt(p -> p[0].getUUID().equals(last) ? 1 : 0));
		// Why none: the first pair's reason, unless a later pair got past its own gates (then the containers' reason).
		Plan why = null;
		for (ServerPlayer[] pair : pairs) {
			Plan plan = between(env, pair[0], pair[1], overworld, forced, random);
			if (plan.found()) {
				return plan;
			}
			if (why == null || plan.deep() && !why.deep()) {
				why = plan;
			}
		}
		return why == null ? Plan.none("no pair fits") : why;
	}

	/** A move from {@code a}'s base to {@code b}'s, or why none. */
	static Plan between(S28Env env, ServerPlayer a, ServerPlayer b, ServerLevel overworld, boolean forced, RandomSource random) {
		String refusal = pairRefusal(env, a, b, overworld, forced);
		if (refusal != null) {
			return Plan.none(refusal);
		}
		BlockPos baseA = base(env, a.getUUID(), overworld).orElseThrow();
		BlockPos baseB = base(env, b.getUUID(), overworld).orElseThrow();
		Plan plan = containers(env, a, b, baseA, baseB, overworld, random);
		return plan.found() ? plan : plan.deeper();
	}

	/** Past the pair's gates: the containers of both bases. */
	private static Plan containers(S28Env env, ServerPlayer a, ServerPlayer b, BlockPos baseA, BlockPos baseB, ServerLevel overworld, RandomSource random) {
		List<Box> givers = boxes(env, overworld, baseA);
		if (givers.size() < 2) {
			return Plan.none(ScGates.name(a) + "'s base has " + givers.size() + " container(s) loaded (two or more, never the one worth the most)");
		}
		List<Box> takers = boxes(env, overworld, baseB);
		List<BlockPos> rooms = new ArrayList<>();
		String why = takers.isEmpty() ? ScGates.name(b) + "'s base has no container loaded" : ScGates.name(b) + "'s containers are full";
		for (Box taker : takers) {
			BlockPos room = roomIn(overworld, taker);
			if (room == null) {
				continue;
			}
			String no = boxRefusal(env, overworld, taker, b);
			if (no != null) {
				why = no;
				continue;
			}
			rooms.add(room);
		}
		if (rooms.isEmpty()) {
			return Plan.none(why);
		}
		Plan from = take(env, overworld, givers, a, random);
		if (!from.found()) {
			return from;
		}
		Move m = from.move();
		BlockPos to = rooms.get(random.nextInt(rooms.size()));
		return new Plan(new Move(m.from(), m.slot(), to, m.stack(), a.getUUID(), b.getUUID(), false), "from " + ScGates.name(a) + " to " + ScGates.name(b));
	}

	/** A stack to take from {@code givers} (never the richest), the owner's, or why none; {@code to} is left at {@code from}. */
	static Plan take(S28Env env, ServerLevel level, List<Box> givers, ServerPlayer owner, RandomSource random) {
		Box richest = richest(level, givers);
		List<Taken> stacks = new ArrayList<>();
		String why = ScGates.name(owner) + "'s containers hold nothing that may go";
		for (Box giver : givers) {
			if (giver == richest) {
				continue;
			}
			List<Taken> here = takeable(level, giver, env.config().maxCount);
			if (here.isEmpty()) {
				continue;
			}
			String no = boxRefusal(env, level, giver, owner);
			if (no != null) {
				why = no;
				continue;
			}
			stacks.addAll(here);
		}
		if (stacks.isEmpty()) {
			return Plan.none(why);
		}
		Taken taken = stacks.get(random.nextInt(stacks.size()));
		return new Plan(new Move(taken.half(), taken.slot(), taken.half(), taken.stack(), owner.getUUID(), owner.getUUID(), false), "a stack");
	}

	/** The fake: a stack between two of the target's own containers (any but the richest gives; another takes). */
	static Plan fake(S28Env env, ServerPlayer target, ServerLevel overworld, RandomSource random) {
		Optional<BlockPos> base = base(env, target.getUUID(), overworld);
		if (base.isEmpty()) {
			return Plan.none(ScGates.name(target) + " has no base in the overworld");
		}
		if (!away(env, target, base.get(), overworld)) {
			return Plan.none(ScGates.name(target) + " is within " + (int) env.config().awayDistance + " of their base and awake");
		}
		List<Box> boxes = boxes(env, overworld, base.get());
		if (boxes.size() < 2) {
			return Plan.none("their base has " + boxes.size() + " container(s) loaded");
		}
		Plan from = take(env, overworld, boxes, target, random);
		if (!from.found()) {
			return from;
		}
		Move m = from.move();
		List<BlockPos> rooms = new ArrayList<>();
		String why = "no other container there has room";
		for (Box box : boxes) {
			if (box.halves().contains(m.from())) {
				continue;
			}
			BlockPos room = roomIn(overworld, box);
			if (room == null) {
				continue;
			}
			String no = boxRefusal(env, overworld, box, target);
			if (no != null) {
				why = no;
				continue;
			}
			rooms.add(room);
		}
		if (rooms.isEmpty()) {
			return Plan.none(why);
		}
		BlockPos to = rooms.get(random.nextInt(rooms.size()));
		return new Plan(new Move(m.from(), m.slot(), to, m.stack(), target.getUUID(), target.getUUID(), true), "within " + ScGates.name(target) + "'s base");
	}

	/** The card's work: the plan, then the move through the trace service (all or nothing), then the count and the way. */
	public static Outcome fire(S28Env env, ServerPlayer target, boolean forced, boolean fake, RandomSource random) {
		Plan plan = plan(env, target, forced, fake, random);
		if (!plan.found()) {
			String gated = gate(env, target, forced);
			return new Outcome(gated != null ? com.forzacode.a1016_02.core.FireResult.SKIPPED : com.forzacode.a1016_02.core.FireResult.NO_SPOT, null,
					plan.reason());
		}
		Move move = plan.move();
		ServerLevel overworld = env.server().overworld();
		if (!env.traces().moveStack(overworld, move.from(), move.slot(), move.to(), CAUSE)) {
			return new Outcome(com.forzacode.a1016_02.core.FireResult.NO_SPOT, null, "the move was refused (in view)");
		}
		env.data().moved(move.fromPlayer(), move.toPlayer(), GameClock.day(env.server()), move.fake());
		// R13-6: the id and the form only; where (two players' containers) at DEBUG.
		A1016_02.LOGGER.info("[a1016] world: {}{}", ID, move.fake() ? " (fake)" : "");
		A1016_02.LOGGER.debug("[a1016] world: {} from {} to {}", ID, move.from().toShortString(), move.to().toShortString());
		return new Outcome(com.forzacode.a1016_02.core.FireResult.FIRED, move, plan.reason());
	}
}
