package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.forzacode.a1016_02.accident.RouteBook;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TrapContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * The player's own bridges out of the overworld (D-034): blocks they placed and walked on, over the End's void or a
 * Nether lava lake. Built on the footprint ({@code PlayerWatch}) and the learned routes, both kept per dimension.
 */
public final class Bridges {
	/** What is under the bridge. */
	public enum Below { VOID, LAVA }

	private Bridges() {
	}

	/**
	 * Deck blocks the player walked on near the center: placed by a player, a full block, open for two cells above.
	 * Nearest to the center first.
	 */
	public static List<BlockPos> walkedDeck(TrapContext ctx) {
		ServerLevel level = ctx.level();
		Set<BlockPos> deck = new LinkedHashSet<>();
		for (RouteBook.Spot spot : ctx.routeSpots()) {
			BlockPos feet = spot.pos();
			BlockPos block = feet.below();
			if (ctx.placedByPlayer(block) && Scan.fullSolid(level, block) && Scan.open(level, feet) && Scan.open(level, feet.above())) {
				deck.add(block.immutable());
			}
		}
		return new ArrayList<>(deck);
	}

	/** Nothing at all under the block down to the bottom of the world: a step off here falls out of it. */
	public static boolean overVoid(Level level, BlockPos deck) {
		BlockPos.MutableBlockPos cursor = deck.mutable().move(Direction.DOWN);
		while (cursor.getY() >= level.getMinY()) {
			if (!level.isLoaded(cursor) || !Scan.open(level, cursor)) {
				return false;
			}
			cursor.move(Direction.DOWN);
		}
		return true;
	}

	/**
	 * The height of the lava lake under the block (open air down to it, within {@code maxDrop}, with at least
	 * {@code minSources} lava sources in the 5x5 there), or {@link Integer#MIN_VALUE}.
	 */
	public static int lavaBelow(Level level, BlockPos deck, int maxDrop, int minSources) {
		BlockPos.MutableBlockPos cursor = deck.mutable().move(Direction.DOWN);
		int drop = 0;
		while (drop < maxDrop && level.isLoaded(cursor) && Scan.open(level, cursor)) {
			cursor.move(Direction.DOWN);
			drop++;
		}
		if (!level.isLoaded(cursor) || !Scan.lava(level, cursor)) {
			return Integer.MIN_VALUE;
		}
		int sources = 0;
		for (BlockPos pos : BlockPos.betweenClosed(cursor.offset(-2, 0, -2), cursor.offset(2, 0, 2))) {
			if (level.isLoaded(pos) && Scan.lava(level, pos) && level.getFluidState(pos).isSource()) {
				sources++;
			}
		}
		return sources >= minSources ? cursor.getY() : Integer.MIN_VALUE;
	}

	/** True if this kind of drop is under the block. */
	public static boolean over(Level level, BlockPos deck, Below below, int maxDrop, int minSources) {
		return below == Below.VOID ? overVoid(level, deck) : lavaBelow(level, deck, maxDrop, minSources) != Integer.MIN_VALUE;
	}

	/**
	 * The axis along which the bridge goes on past this block on both sides (taking it leaves a 1x1 gap with bridge on
	 * either side), or null.
	 */
	public static Direction.@Nullable Axis spanAxis(Level level, BlockPos deck) {
		for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z}) {
			Direction dir = Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
			if (Scan.fullSolid(level, deck.relative(dir)) && Scan.fullSolid(level, deck.relative(dir.getOpposite()))) {
				return axis;
			}
		}
		return null;
	}

	/** One block wide: across the span, the cells beside the deck (and above them) have nothing to stand on or in. */
	public static boolean oneWide(Level level, BlockPos deck, Direction.Axis along) {
		Direction side = Direction.fromAxisAndDirection(along == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X, Direction.AxisDirection.POSITIVE);
		for (Direction dir : new Direction[] {side, side.getOpposite()}) {
			BlockPos beside = deck.relative(dir);
			if (!Scan.passable(level, beside) || !Scan.passable(level, beside.above())) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Nothing hangs on the block that would go with it: every side is open air or another full block, and above and
	 * below are air (no torch, sign, rail or vine on it), so taking it leaves exactly one empty cell.
	 */
	public static boolean clean(Level level, BlockPos deck) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos beside = deck.relative(dir);
			if (!level.getBlockState(beside).isAir() && !Scan.fullSolid(level, beside)) {
				return false;
			}
		}
		return level.getBlockState(deck.above()).isAir() && level.getBlockState(deck.below()).isAir();
	}

	/** The player in the searched level, or null. */
	static @Nullable ServerPlayer here(TrapContext ctx) {
		ServerPlayer player = ctx.player();
		return player != null && player.level() == ctx.level() ? player : null;
	}

	/** The way the player is going in this level: the way they last walked, else where they look. Unit length, or null. */
	public static @Nullable Vec3 heading(TrapContext ctx) {
		ServerPlayer player = here(ctx);
		if (player == null) {
			return null;
		}
		Vec3 walked = ctx.data().heading;
		if (walked != null && ctx.level().dimension().equals(ctx.data().headingDimension)) {
			return walked;
		}
		Vec3 look = player.getViewVector(1.0F);
		Vec3 flat = new Vec3(look.x, 0, look.z);
		return flat.lengthSqr() < 1.0E-6 ? null : flat.normalize();
	}

	/**
	 * On the player's route ahead. Off the bridge (on an island, in a fortress) every walked bridge block is: they
	 * cross it on the way back. On a bridge, only what lies the way they are going.
	 */
	public static boolean ahead(TrapContext ctx, BlockPos deck, Below below) {
		ServerPlayer player = here(ctx);
		if (player == null) {
			return true;
		}
		BlockPos under = player.blockPosition().below();
		boolean onBridge = ctx.placedByPlayer(under) && Scan.fullSolid(ctx.level(), under)
				&& over(ctx.level(), under, below, ctx.cfg().lavaBridgeMaxDrop, ctx.cfg().lavaLakeMinSources);
		if (!onBridge) {
			return true;
		}
		Vec3 heading = heading(ctx);
		if (heading == null) {
			return true;
		}
		Vec3 to = Vec3.atCenterOf(deck).subtract(player.position());
		return to.x * heading.x + to.z * heading.z > 0;
	}

	/** Distance from the player (or the search center without one). */
	public static double fromPlayer(TrapContext ctx, BlockPos pos) {
		ServerPlayer player = here(ctx);
		Vec3 from = player != null ? player.position() : Vec3.atCenterOf(ctx.center());
		return Math.sqrt(Vec3.atCenterOf(pos).distanceToSqr(from));
	}
}
