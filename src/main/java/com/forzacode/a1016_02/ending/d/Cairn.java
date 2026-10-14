package com.forzacode.a1016_02.ending.d;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.accident.AccidentInit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TrapType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Step 3, take it back: at the F13 cairn (the ocean pyramid nearest the base, whose center holds the first block of
 * the {@code story:first_blocks} owner, lore's choice, D-061) someone breaks the first block out of the center and it
 * goes away: any of them (D-064), whoever's block it was. Its drop is marked, and followed wherever it is placed
 * ({@link Marks}). D-066 M4: it counts once it is out of the cairn cleanly and {@code carryAwayBlocks} or more from it,
 * carried by anyone in it or placed anywhere, as soon as the chain reaches step 3. "Cleanly" belongs to the visit it
 * left the cairn on (the group's visit): an item anyone threw down at the cairn on that visit spoils it, and the step
 * waits until someone brings it back within the cairn's radius, leaves nothing, and takes it out again. The danger is
 * accident's cairn lure: the way they always walk to the cairn goes hollow over a drop or lava.
 */
public final class Cairn {
	/** Accident's cairn lure. */
	public static final TrapType LURE = new TrapType("lure_cairn");

	/**
	 * The group's current visit, in memory: someone is at the cairn, and whether anyone left an offering on it (an item
	 * thrown by anyone spoils it). The visit ends once nobody is near.
	 */
	private static boolean atCairn;
	private static boolean offering;
	private static long nextArm;

	private Cairn() {
	}

	/** The cairn's center (where F13 put the first block). */
	public static Optional<GlobalPos> center(MinecraftServer server) {
		return Optional.ofNullable(HerobrineState.get(server).fragmentsPlaced().get("F13"));
	}

	/** True if this is the cairn's center. */
	public static boolean isCenter(MinecraftServer server, ServerLevel level, BlockPos pos) {
		return center(server).filter(c -> c.dimension().equals(level.dimension()) && c.pos().equals(pos)).isPresent();
	}

	/**
	 * The group's visit, each check: it starts when anyone comes within the radius, and ends once all of them are far.
	 * Then where the first block is ({@link #follow}).
	 */
	static void watch(List<ServerPlayer> players, EndingDState data, EndingDConfig cfg) {
		if (players.isEmpty()) {
			return;
		}
		Optional<GlobalPos> cairn = center(players.getFirst().level().getServer());
		if (cairn.isEmpty()) {
			return;
		}
		double r2 = (double) cfg.cairnRadius * cfg.cairnRadius;
		boolean near = false;
		boolean allFar = true;
		for (ServerPlayer player : players) {
			if (!cairn.get().dimension().equals(player.level().dimension())) {
				continue;
			}
			double d2 = cairn.get().pos().distToCenterSqr(player.position());
			near |= d2 <= r2;
			allFar &= d2 > 4.0 * r2;
		}
		// The block's way out is read before the visit can end: it always leaves the radius before everyone is far.
		follow(players, data, cfg, cairn.get());
		if (near) {
			atCairn = true;
		} else if (allFar && atCairn) {
			atCairn = false;
			offering = false;
		}
	}

	/**
	 * Where the first block is (carried by anyone in it, or placed as a tracked block), and whether it just left the
	 * cairn: once every copy known is beyond the cairn's radius after one was within it, it left on this visit, cleanly
	 * if nobody left an offering on it ({@link EndingDState#FIRST_TAKEN}). In a chest or on the ground it is not
	 * followed: nothing changes until someone carries or places it again.
	 */
	static void follow(List<ServerPlayer> players, EndingDState data, EndingDConfig cfg, GlobalPos cairn) {
		double r2 = (double) cfg.cairnRadius * cfg.cairnRadius;
		boolean seen = false;
		boolean near = false;
		for (ServerPlayer player : players) {
			if (Marks.carries(player, Marks.FIRST_BLOCK)) {
				seen = true;
				near |= within(cairn, player.level().dimension(), player.position(), r2);
			}
		}
		for (EndingDState.Tracked tracked : data.tracked()) {
			if (tracked.mark().equals(Marks.FIRST_BLOCK)) {
				seen = true;
				near |= within(cairn, tracked.pos().dimension(), Vec3.atCenterOf(tracked.pos().pos()), r2);
			}
		}
		if (!seen) {
			return;
		}
		if (near) {
			data.set(EndingDState.FIRST_NEAR, true);
		} else if (data.has(EndingDState.FIRST_NEAR)) {
			data.set(EndingDState.FIRST_NEAR, false);
			data.set(EndingDState.FIRST_TAKEN, !offering);
			A1016_02.LOGGER.info("[a1016] ending d: the first block left the cairn{}", offering ? " on a visit with an offering (it does not count)" : " cleanly");
		}
	}

	private static boolean within(GlobalPos cairn, ResourceKey<Level> dimension, Vec3 at, double r2) {
		return cairn.dimension().equals(dimension) && cairn.pos().distToCenterSqr(at) <= r2;
	}

	/** An item entity was added: one that anyone who takes part threw down at the cairn is an offering. */
	static void onItemAdded(ServerLevel level, ItemEntity item, EndingDConfig cfg, Predicate<ServerPlayer> takesPart) {
		Entity owner = item.getOwner();
		if (!(owner instanceof ServerPlayer player) || !takesPart.test(player)) {
			return;
		}
		Optional<GlobalPos> cairn = center(level.getServer());
		if (cairn.isPresent() && cairn.get().dimension().equals(level.dimension())
				&& cairn.get().pos().distToCenterSqr(item.position()) <= (double) cfg.cairnRadius * cfg.cairnRadius) {
			offering = true;
		}
	}

	/**
	 * A player is breaking a block: anyone who takes part breaking the cairn's center takes the first block back. Its
	 * drop is marked. True if it was the center.
	 */
	static boolean onBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, EndingDState data, Predicate<ServerPlayer> takesPart) {
		return takesPart.test(player) && take(level, data, pos, state);
	}

	/**
	 * Someone broke this block: if it is the cairn's center, the first block is taken back (its drop is marked). It is at
	 * the cairn now: whether it got out cleanly is decided when it leaves ({@link #follow}). True if it was the center.
	 */
	public static boolean take(ServerLevel level, EndingDState data, BlockPos pos, BlockState state) {
		if (state.isAir() || !isCenter(level.getServer(), level, pos)) {
			return false;
		}
		Marks.expectDrop(level, pos, state.getBlock().asItem(), Marks.FIRST_BLOCK);
		data.set(EndingDState.FIRST_NEAR, true);
		data.set(EndingDState.FIRST_TAKEN, false);
		A1016_02.LOGGER.info("[a1016] ending d: the first block came out of the cairn{}", offering ? " (an offering was left on this visit)" : "");
		return true;
	}

	/** Tests: an offering was (or was not) left on this visit. */
	static void setOffering(boolean value) {
		offering = value;
	}

	/**
	 * Step 3 (M4): the first block got out of the cairn cleanly and some copy of it is now at least
	 * {@code carryAwayBlocks} from the cairn (or in another dimension): carried by anyone in it, or placed anywhere.
	 */
	static boolean away(MinecraftServer server, List<ServerPlayer> players, EndingDState data, EndingDConfig cfg) {
		if (!data.has(EndingDState.FIRST_TAKEN)) {
			return false;
		}
		Optional<GlobalPos> cairn = center(server);
		double far2 = (double) cfg.carryAwayBlocks * cfg.carryAwayBlocks;
		for (ServerPlayer player : players) {
			if (Marks.carries(player, Marks.FIRST_BLOCK) && far(cairn, player.level().dimension(), player.position(), far2)) {
				return true;
			}
		}
		for (EndingDState.Tracked tracked : data.tracked()) {
			if (tracked.mark().equals(Marks.FIRST_BLOCK) && far(cairn, tracked.pos().dimension(), Vec3.atCenterOf(tracked.pos().pos()), far2)) {
				return true;
			}
		}
		return false;
	}

	private static boolean far(Optional<GlobalPos> cairn, ResourceKey<Level> dimension, Vec3 at, double far2) {
		return cairn.isEmpty() || !cairn.get().dimension().equals(dimension) || cairn.get().pos().distToCenterSqr(at) >= far2;
	}

	/**
	 * Step 3's danger: while nobody is at the cairn, arm accident's cairn lure (once for the world), for the first one
	 * in it the planner can set it for (the route they used before goes hollow; it stays deniable for whoever walks it).
	 * Accident keeps one trap per player: a friend's armed trap elsewhere never holds the lure up for the group; only
	 * whoever it is set for must have room for one of their own.
	 */
	static void danger(List<ServerPlayer> players, EndingDState data, EndingDConfig cfg) {
		if (players.isEmpty()) {
			return;
		}
		MinecraftServer server = players.getFirst().level().getServer();
		long now = server.getTickCount();
		if (data.has(EndingDState.CAIRN_ARMED) || now < nextArm || atCairn) {
			return;
		}
		nextArm = now + EndingDConfig.ticks(cfg.cairnArmRetrySeconds);
		for (ServerPlayer player : players) {
			if (!AccidentInit.planner().canArm(server, player.getUUID())) {
				continue;
			}
			if (Services.accidents().arm(player, LURE)) {
				data.set(EndingDState.CAIRN_ARMED, true);
				A1016_02.LOGGER.info("[a1016] ending d: the way to the cairn is hollow now ({})", player.getName().getString());
				return;
			}
		}
	}

	static boolean offering() {
		return offering;
	}

	static boolean atCairn() {
		return atCairn;
	}

	static void clear() {
		atCairn = false;
		offering = false;
		nextArm = 0;
	}
}
