package com.forzacode.a1016_02.ending.d;

import java.util.Optional;

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
 * Step 3, take it back: at the F13 cairn (the ocean pyramid nearest the base, whose center holds the player's first
 * block) the player leaves no offering, breaks the first block out of the center and carries it away. Its drop is
 * marked, and followed wherever it is placed ({@link Marks}). The danger is accident's cairn lure: the way they always
 * walk to the cairn goes hollow over a drop or lava ({@code AccidentPlanner.arm}).
 */
public final class Cairn {
	/** Accident's cairn lure. */
	public static final TrapType LURE = new TrapType("lure_cairn");

	/** The current visit, in memory: at the cairn, and whether an offering was left on it. */
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

	/** Visits and offerings, each check. */
	static void watch(ServerPlayer player, EndingDConfig cfg) {
		Optional<GlobalPos> cairn = center(player.level().getServer());
		if (cairn.isEmpty() || !cairn.get().dimension().equals(player.level().dimension())) {
			return;
		}
		double d2 = cairn.get().pos().distToCenterSqr(player.position());
		if (d2 <= (double) cfg.cairnRadius * cfg.cairnRadius) {
			atCairn = true;
		} else if (d2 > 4.0 * cfg.cairnRadius * cfg.cairnRadius && atCairn) {
			atCairn = false;
			offering = false;
		}
	}

	/** An item entity was added: one the subject threw down at the cairn is an offering. */
	static void onItemAdded(ServerLevel level, ItemEntity item, EndingDConfig cfg) {
		Entity owner = item.getOwner();
		if (!(owner instanceof ServerPlayer player) || !Services.watch().isSubject(player)) {
			return;
		}
		Optional<GlobalPos> cairn = center(level.getServer());
		if (cairn.isPresent() && cairn.get().dimension().equals(level.dimension())
				&& cairn.get().pos().distToCenterSqr(item.position()) <= (double) cfg.cairnRadius * cfg.cairnRadius) {
			offering = true;
		}
	}

	/**
	 * A player is breaking a block: the subject breaking the cairn's center takes the first block back. Its drop is
	 * marked; it counts for step 3 only with no offering left on this visit.
	 */
	static void onBreak(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
		if (Services.watch().isSubject(player)) {
			take(level, EndingDState.get(level.getServer()), pos, state);
		}
	}

	/**
	 * The subject broke this block: if it is the cairn's center, the first block is taken back (its drop is marked) and
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

	/** True once the first block came out cleanly and is carried at least {@code carryAwayBlocks} from the cairn. */
	static boolean carriedAway(ServerPlayer player, EndingDState data, EndingDConfig cfg) {
		if (!data.has(EndingDState.FIRST_TAKEN) || !Marks.carries(player, Marks.FIRST_BLOCK)) {
			return false;
		}
		Optional<GlobalPos> cairn = center(player.level().getServer());
		return cairn.isEmpty() || !cairn.get().dimension().equals(player.level().dimension())
				|| cairn.get().pos().distToCenterSqr(player.position()) >= (double) cfg.carryAwayBlocks * cfg.carryAwayBlocks;
	}

	/** Step 3's danger: while the player is away from the cairn, arm accident's cairn lure (once). */
	static void danger(ServerPlayer player, EndingDState data, EndingDConfig cfg) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		if (data.has(EndingDState.CAIRN_ARMED) || now < nextArm || atCairn || Services.accidents().armed().isPresent()) {
			return;
		}
		nextArm = now + EndingDConfig.ticks(cfg.cairnArmRetrySeconds);
		if (Services.accidents().arm(player, LURE)) {
			data.set(EndingDState.CAIRN_ARMED, true);
			A1016_02.LOGGER.info("[a1016] ending d: the way to the cairn is hollow now");
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
