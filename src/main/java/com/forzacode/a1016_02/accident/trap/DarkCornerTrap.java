package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Dark corner: the torches from one corner of your base, for one night, put back before morning with one torch a
 * block off. A creeper in a fully lit house. The clue: one torch is a block off from where you put it.
 */
public final class DarkCornerTrap extends BaseTrap {
	public DarkCornerTrap() {
		super("dark_corner", false, "dark", EnumSet.of(Habit.VISITOR, Habit.WATCHER));
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		long t = Scan.timeOfDay(level);
		return t >= cfg.nightStart && t < cfg.restoreFrom - 2000;
	}

	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		return byMonster(source);
	}

	@Override
	public long window(AccidentConfig cfg) {
		// The torches stay out until they are put back; tick() starts the grace window then.
		return Long.MAX_VALUE / 4;
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		AccidentConfig cfg = ctx.cfg();
		BlockPos base = base(ctx);
		List<BlockPos> torches = Services.watch().placedNear(level, base, cfg.baseRadius, Scan::torch);
		List<Candidate> found = new ArrayList<>();
		if (torches.size() < cfg.darkCornerMinTorches) {
			return found;
		}
		double cx = 0;
		double cz = 0;
		for (BlockPos torch : torches) {
			cx += torch.getX();
			cz += torch.getZ();
		}
		BlockPos middle = BlockPos.containing(cx / torches.size(), base.getY(), cz / torches.size());
		List<BlockPos> corners = new ArrayList<>(torches);
		corners.sort((a, b) -> Double.compare(Scan.horizontalDistSqr(b, middle), Scan.horizontalDistSqr(a, middle)));
		for (BlockPos corner : corners.subList(0, Math.min(4, corners.size()))) {
			List<BlockPos> group = new ArrayList<>();
			for (BlockPos torch : torches) {
				if (torch.distSqr(corner) <= (double) cfg.darkCornerRadius * cfg.darkCornerRadius) {
					group.add(torch);
				}
			}
			group.sort((a, b) -> Double.compare(a.distSqr(corner), b.distSqr(corner)));
			if (group.size() > cfg.darkCornerMaxTorches) {
				group = new ArrayList<>(group.subList(0, cfg.darkCornerMaxTorches));
			}
			if (torches.size() - group.size() < cfg.darkCornerKeepLit) {
				continue;
			}
			// The torch that goes back a block off comes first in the saved list.
			BlockPos off = null;
			for (int i = 0; i < group.size() && off == null; i++) {
				off = offSpot(level, group.get(i), group);
				if (off != null && i > 0) {
					group.add(0, group.remove(i));
				}
			}
			if (off == null) {
				continue;
			}
			List<ArmedTrap.SavedBlock> saved = new ArrayList<>();
			List<TraceOp> ops = new ArrayList<>();
			for (BlockPos torch : group) {
				saved.add(new ArmedTrap.SavedBlock(torch, level.getBlockState(torch)));
				ops.add(TraceOp.remove(torch));
			}
			int r = cfg.baseRadius;
			found.add(Candidate.of(corner, ops, base.offset(-r, -8, -r), base.offset(r, 16, r),
					"One torch in the corner at " + at(group.get(0)) + " is a block off from where you put it.").withSaved(saved, off));
		}
		return found;
	}

	/** A spot one block from {@code torch} where the same torch would hold, not used by another torch of the group. */
	static @Nullable BlockPos offSpot(ServerLevel level, BlockPos torch, List<BlockPos> group) {
		BlockState state = level.getBlockState(torch);
		List<BlockPos> tries = new ArrayList<>();
		if (state.getBlock() instanceof WallTorchBlock) {
			Direction facing = state.getValue(WallTorchBlock.FACING);
			tries.add(torch.relative(facing.getClockWise()));
			tries.add(torch.relative(facing.getCounterClockWise()));
			tries.add(torch.above());
			tries.add(torch.below());
		} else {
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				tries.add(torch.relative(dir));
			}
		}
		for (BlockPos spot : tries) {
			if (!group.contains(spot) && level.getBlockState(spot).isAir() && state.canSurvive(level, spot)) {
				return spot;
			}
		}
		return null;
	}

	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		if (armed.phase() != ArmedTrap.Phase.SET) {
			return armed;
		}
		long t = Scan.timeOfDay(ctx.level());
		ServerPlayer player = ctx.player();
		boolean morningNear = t >= ctx.cfg().restoreFrom || t < ctx.cfg().nightStart || player != null && player.isSleeping();
		if (!morningNear || !restore(ctx.level(), ctx, armed)) {
			return armed;
		}
		return armed.withPhase(ArmedTrap.Phase.RESTORED, ctx.now() + ctx.cfg().darkCornerGraceTicks());
	}

	/**
	 * Puts the torches back, the first one a block off, all at once and out of view. Spots the player filled in the
	 * meantime are skipped. The exact ones leave the trace ledger (undone); the moved one stays in it.
	 */
	public static boolean restore(ServerLevel level, TrapContext ctx, ArmedTrap armed) {
		List<TraceOp> ops = new ArrayList<>();
		List<BlockPos> exact = new ArrayList<>();
		for (int i = 0; i < armed.saved().size(); i++) {
			ArmedTrap.SavedBlock torch = armed.saved().get(i);
			BlockPos spot = i == 0 && armed.offPos().isPresent() ? armed.offPos().get() : torch.pos();
			if (!level.getBlockState(spot).isAir() || !torch.state().canSurvive(level, spot)) {
				continue;
			}
			ops.add(new TraceOp.Leave(spot, torch.state()));
			if (spot.equals(torch.pos())) {
				exact.add(spot);
			}
		}
		if (ops.isEmpty()) {
			return true;
		}
		if (!TraceOp.apply(level, ctx.view(), "accident:dark_corner/back", ops)) {
			return false;
		}
		TraceLedger ledger = TraceLedger.get(level.getServer());
		for (TraceLedger.Entry entry : List.copyOf(ledger.entries())) {
			if (entry.kind() == TraceLedger.Kind.REMOVE && entry.cause().equals("accident:dark_corner") && entry.pos().dimension().equals(level.dimension())
					&& exact.contains(entry.pos().pos())) {
				ledger.remove(entry);
			}
		}
		return true;
	}
}
