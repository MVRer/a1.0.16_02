package com.forzacode.a1016_02.world.sc2.s17;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.TraceBatch;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.world.sc2.kit.Draw;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * 17c, the other door (docs/SELF_CREATED_2.md, feature 17): the three ways in share one plug of 12 blocks. The open way's
 * first three cells inside its mouth and a closed way's plug are the same 12 places in each ({@link ArchivePlan#plug}), so
 * moving one closed way's plug into the open way's cells shuts the mouth that was there (rock) and opens another, every
 * block conserved (one batch of 12 moves, ledgered {@value #CAUSE}, given back by Ending D and the switch).
 */
final class OtherDoor {
	static final String ANSWER = "17c";
	static final String CAUSE = S17.CAUSE + "/door";
	/** Nobody within this of either mouth. */
	static final double MOUTH_CLEAR = 16.0;

	private OtherDoor() {
	}

	/** The way that is open now: the one whose 12 cells inside the mouth are all open (empty if none or two). */
	static int openWay(ServerLevel level, ArchivePlan plan) {
		int open = -1;
		for (int t = 0; t < plan.tunnels.length; t++) {
			boolean all = true;
			for (BlockPos pos : plan.plug(plan.tunnels[t])) {
				if (!level.isLoaded(pos)) {
					return -1;
				}
				BlockState state = level.getBlockState(pos);
				if (!state.isAir() && state.getCollisionShape(level, pos).isEmpty() == false) {
					all = false;
					break;
				}
			}
			if (all) {
				if (open >= 0) {
					return -1;
				}
				open = t;
			}
		}
		return open;
	}

	/** A way's mouth: where a player stands outside it. */
	static Vec3 mouth(ArchivePlan plan, int way) {
		ArchivePlan.Tunnel t = plan.tunnels[way];
		return Vec3.atBottomCenterOf(plan.at(t.left(), ArchivePlan.foot(t.exit()), plan.tunnelF0 + t.exit()));
	}

	/** The closed way to open next: one whose plug is whole, picked by the place's seed and how many moves were made. */
	static int closedToOpen(ServerLevel level, ArchivePlan plan, int open, int moves) {
		List<Integer> closed = new ArrayList<>();
		for (int t = 0; t < plan.tunnels.length; t++) {
			if (t != open && whole(level, plan.plug(plan.tunnels[t])) && dryOut(level, plan, plan.tunnels[t])) {
				closed.add(t);
			}
		}
		if (closed.isEmpty()) {
			return -1;
		}
		return closed.get(Draw.between(plan.seed, 700 + moves, 0, closed.size() - 1));
	}

	/**
	 * True if opening this way lets nothing in and drops nobody (SC2-H): no water or lava in or round its cells from its
	 * mouth to its exit (a mouth under a lake would flood the ways and the rooms), and land within {@value
	 * ArchivePlan#EXIT_DROP} under its exit cell. Loaded cells only (a cell not loaded refuses).
	 */
	static boolean dryOut(ServerLevel level, ArchivePlan plan, ArchivePlan.Tunnel t) {
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int i = t.mouth(); i <= t.exit() + 1; i++) {
			for (int r = t.left() - 1; r <= t.left() + 2; r++) {
				for (int u = ArchivePlan.foot(i) - 1; u <= ArchivePlan.top(i) + 1; u++) {
					at.set(plan.at(r, u, plan.tunnelF0 + i));
					if (!level.isLoaded(at) || !level.getFluidState(at).isEmpty()) {
						return false;
					}
				}
			}
		}
		BlockPos exit = plan.at(t.left(), ArchivePlan.foot(t.exit()), plan.tunnelF0 + t.exit());
		for (int d = 1; d <= ArchivePlan.EXIT_DROP + 1; d++) {
			if (!level.getBlockState(exit.below(d)).getCollisionShape(level, exit.below(d)).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Every one of these cells solid (a plug). */
	static boolean whole(ServerLevel level, List<BlockPos> cells) {
		for (BlockPos pos : cells) {
			if (!level.isLoaded(pos) || level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Why the door may not move now for a newcomer, or null: a newcomer (never inside) within {@code near} of the open
	 * mouth, everyone who has been inside {@code away} or more from it, nobody within {@value #MOUTH_CLEAR} of either
	 * mouth, nobody inside or in a way in.
	 */
	static @Nullable String refusal(ArchivePlan plan, int open, int closed, List<ServerPlayer> people, java.util.function.Predicate<ServerPlayer> been,
			double near, double away, boolean alone) {
		Vec3 a = mouth(plan, open);
		Vec3 b = mouth(plan, closed);
		boolean newcomer = alone;
		for (ServerPlayer p : people) {
			Vec3 at = p.position();
			if (at.distanceTo(a) < MOUTH_CLEAR || at.distanceTo(b) < MOUTH_CLEAR) {
				return p.getName().getString() + " is at a mouth";
			}
			if (inside(plan, p.blockPosition())) {
				return p.getName().getString() + " is inside";
			}
			if (been.test(p)) {
				if (!alone && at.distanceTo(a) < away) {
					return p.getName().getString() + " has been inside and is near";
				}
			} else if (at.distanceTo(a) <= near) {
				newcomer = true;
			}
		}
		return newcomer ? null : "no newcomer near the mouth";
	}

	/** True if a position is in the hall, the cells or a way in (where a closing would shut someone in). */
	static boolean inside(ArchivePlan plan, BlockPos pos) {
		if (plan.interior().contains(pos)) {
			return true;
		}
		int r = plan.localR(pos.getX(), pos.getZ());
		int f = plan.localF(pos.getX(), pos.getZ());
		int t = plan.tunnelAt(r, f);
		if (t < 0) {
			return false;
		}
		int i = f - plan.tunnelF0;
		int u = pos.getY() - plan.floorY();
		return u >= ArchivePlan.foot(i) - 1 && u <= ArchivePlan.top(i) + 1;
	}

	/**
	 * Moves the closed way's plug into the open way's cells, in one batch; false if refused (in view, vetoed, or a living
	 * thing stands in a cell it would fill: R12-11).
	 */
	static boolean move(TraceService traces, ServerLevel level, ArchivePlan plan, int closed, int open) {
		List<BlockPos> from = plan.plug(plan.tunnels[closed]);
		List<BlockPos> to = plan.plug(plan.tunnels[open]);
		if (com.forzacode.a1016_02.world.sc2.kit.Answers.occupied(level, to)) {
			return false;
		}
		TraceBatch batch = traces.batch(level, CAUSE);
		for (int i = 0; i < from.size(); i++) {
			batch.move(from.get(i), to.get(i));
		}
		return batch.commit();
	}

	/** Every cell a move touches (for the view). */
	static List<BlockPos> cells(ArchivePlan plan, int closed, int open) {
		List<BlockPos> out = new ArrayList<>(plan.plug(plan.tunnels[closed]));
		out.addAll(plan.plug(plan.tunnels[open]));
		return out;
	}
}
