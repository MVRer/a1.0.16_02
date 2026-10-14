package com.forzacode.a1016_02.ending.d;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TrapType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Step 3, take it back: at the F13 cairn (the ocean pyramid nearest the base, whose center holds the first block of
 * the {@code story:first_blocks} owner, lore's choice, D-061) the group leaves no offering, someone breaks the first
 * block out of the center and someone carries it away: any of them (D-064), whoever's block it was. Its drop is
 * marked, and followed wherever it is placed ({@link Marks}). The danger is accident's cairn lure: the way they always
 * walk to the cairn goes hollow over a drop or lava ({@code AccidentPlanner.arm}).
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

	/** The group's visit, each check: it starts when anyone comes within the radius, and ends once all of them are far. */
	static void watch(List<ServerPlayer> players, EndingDConfig cfg) {
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
		if (near) {
			atCairn = true;
		} else if (allFar && atCairn) {
			atCairn = false;
			offering = false;
		}
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
	 * drop is marked; it counts for step 3 only with no offering left on this visit. True if it was the center.
	 */
	static boolean onBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, EndingDState data, Predicate<ServerPlayer> takesPart) {
		return takesPart.test(player) && take(level, data, pos, state);
	}

	/**
	 * Someone broke this block: if it is the cairn's center, the first block is taken back (its drop is marked) and
	 * counts for step 3 only if no offering was left on this visit. True if it was the center.
	 */
	public static boolean take(ServerLevel level, EndingDState data, BlockPos pos, BlockState state) {
		if (state.isAir() || !isCenter(level.getServer(), level, pos)) {
			return false;
		}
		Marks.expectDrop(level, pos, state.getBlock().asItem(), Marks.FIRST_BLOCK);
		data.set(EndingDState.FIRST_TAKEN, !offering);
		A1016_02.LOGGER.info("[a1016] ending d: the first block came out of the cairn{}", offering ? " (an offering was left: it does not count)" : "");
		return true;
	}

	/** Tests: an offering was (or was not) left on this visit. */
	static void setOffering(boolean value) {
		offering = value;
	}

	/**
	 * True once the first block came out cleanly and this player carries it at least {@code carryAwayBlocks} from the
	 * cairn (whoever took it out, whoever's it was).
	 */
	static boolean carriedAway(ServerPlayer player, EndingDState data, EndingDConfig cfg) {
		if (!data.has(EndingDState.FIRST_TAKEN) || !Marks.carries(player, Marks.FIRST_BLOCK)) {
			return false;
		}
		Optional<GlobalPos> cairn = center(player.level().getServer());
		return cairn.isEmpty() || !cairn.get().dimension().equals(player.level().dimension())
				|| cairn.get().pos().distToCenterSqr(player.position()) >= (double) cfg.carryAwayBlocks * cfg.carryAwayBlocks;
	}

	/**
	 * Step 3's danger: while nobody is at the cairn, arm accident's cairn lure (once for the world), for the first one
	 * in it the planner can set it for (the route they used before goes hollow; it stays deniable for whoever walks it).
	 */
	static void danger(List<ServerPlayer> players, EndingDState data, EndingDConfig cfg) {
		if (players.isEmpty()) {
			return;
		}
		MinecraftServer server = players.getFirst().level().getServer();
		long now = server.getTickCount();
		if (data.has(EndingDState.CAIRN_ARMED) || now < nextArm || atCairn || Services.accidents().armed().isPresent()) {
			return;
		}
		nextArm = now + EndingDConfig.ticks(cfg.cairnArmRetrySeconds);
		for (ServerPlayer player : players) {
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
