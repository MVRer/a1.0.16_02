package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.CoreGaps;
import com.forzacode.a1016_02.accident.Scan;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.accident.ViewGate;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.jspecify.annotations.Nullable;

/**
 * Dark corner: the torches from one corner of your base, taken at sunset for one night and put back before morning
 * with one torch a block off. A creeper in a fully lit house. The clue: one torch is a block off from where you put
 * it. Only a monster that spawned in the darkened cells (dark now, lit by the taken torches before) during that
 * night counts; any other mob death in the base does not.
 */
public final class DarkCornerTrap extends BaseTrap {
	/** A torch lights cells up to this Manhattan distance (light 14, falling by one per block). */
	static final int TORCH_REACH = 13;
	static final String CAUSE = "accident:dark_corner";

	public DarkCornerTrap() {
		super("dark_corner", false, "dark", EnumSet.of(Habit.VISITOR, Habit.WATCHER));
	}

	/** Armed around sunset, before the dark that matters. */
	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		long t = Scan.timeOfDay(level);
		return t >= cfg.darkCornerArmFrom && t < cfg.nightStart;
	}

	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		Entity attacker = source.getEntity();
		return attacker != null && armed.blames(attacker.getUUID());
	}

	@Override
	public long window(AccidentConfig cfg) {
		// Bounded by the game clock instead (clockUntil and expired()).
		return Long.MAX_VALUE / 4;
	}

	/** The coming morning: the next time the overworld clock reaches {@code restoreFrom}. */
	@Override
	public long clockUntil(ServerLevel level, AccidentConfig cfg) {
		long clock = level.getServer().overworld().getOverworldClockTime();
		long target = clock - Math.floorMod(clock, 24000L) + cfg.restoreFrom;
		return target <= clock ? target + 24000L : target;
	}

	/** Over once the morning plus the grace has passed on the game clock, whether or not the base is loaded. */
	@Override
	public boolean expired(ArmedTrap armed, long now, long clock, AccidentConfig cfg) {
		if (armed.phase() == ArmedTrap.Phase.RESTORED && now > armed.until()) {
			return true;
		}
		return armed.hasClock() && clock > armed.clockUntil() + cfg.darkCornerGraceTicks();
	}

	/** A monster that spawned in the darkened cells while the torches were out: its damage counts. */
	@Override
	public ArmedTrap onSpawned(TrapContext ctx, Entity entity, ArmedTrap armed) {
		if (armed.phase() != ArmedTrap.Phase.SET || !(entity instanceof Enemy) || entity.level() != ctx.level()
				|| !armed.zone(0).contains(entity.position())) {
			return armed;
		}
		return inDarkenedCells(ctx.level(), entity.blockPosition(), armed) ? armed.blame(entity.getUUID()) : armed;
	}

	/** Dark now, and within reach of a taken torch (so it was lit before he took them). */
	public static boolean inDarkenedCells(ServerLevel level, BlockPos pos, ArmedTrap armed) {
		if (level.getBrightness(LightLayer.BLOCK, pos) > 0) {
			return false;
		}
		for (ArmedTrap.SavedBlock torch : armed.saved()) {
			if (torch.pos().distManhattan(pos) <= TORCH_REACH) {
				return true;
			}
		}
		return false;
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

	/** Puts the torches back when the morning comes on the game clock (or the player sleeps through the night). */
	@Override
	public @Nullable ArmedTrap tick(TrapContext ctx, ArmedTrap armed) {
		if (armed.phase() != ArmedTrap.Phase.SET) {
			return armed;
		}
		long clock = ctx.level().getServer().overworld().getOverworldClockTime();
		ServerPlayer player = ctx.player();
		boolean morning = armed.hasClock() && clock >= armed.clockUntil() || player != null && player.isSleeping();
		if (!morning || !restore(ctx.level(), ctx.view(), armed)) {
			return armed;
		}
		return armed.withPhase(ArmedTrap.Phase.RESTORED, ctx.now() + ctx.cfg().darkCornerGraceTicks());
	}

	/**
	 * Puts the torches back, the first one a block off, all out of view. Spots the player filled in the meantime are
	 * skipped. With core's {@code restoreBlock} each torch comes back from its own ledger entry; until then they are put
	 * back with {@code leave} and their removals leave the ledger, so Ending D never makes a second torch.
	 */
	public static boolean restore(ServerLevel level, ViewGate view, ArmedTrap armed) {
		List<ArmedTrap.SavedBlock> torches = new ArrayList<>();
		List<BlockPos> spots = new ArrayList<>();
		for (int i = 0; i < armed.saved().size(); i++) {
			ArmedTrap.SavedBlock torch = armed.saved().get(i);
			BlockPos spot = i == 0 && armed.offPos().isPresent() ? armed.offPos().get() : torch.pos();
			if (level.getBlockState(spot).isAir() && torch.state().canSurvive(level, spot)) {
				torches.add(torch);
				spots.add(spot);
			}
		}
		if (torches.isEmpty()) {
			return true;
		}
		if (!view.outOfView(level, spots)) {
			return false;
		}
		TraceLedger ledger = TraceLedger.get(level.getServer());
		if (CoreGaps.RESTORE_BLOCK) {
			boolean all = true;
			for (int i = 0; i < torches.size(); i++) {
				TraceLedger.Entry entry = removal(ledger, level, torches.get(i).pos());
				all &= entry != null && CoreGaps.restoreBlock(level, entry, spots.get(i));
			}
			return all;
		}
		List<TraceOp> ops = new ArrayList<>();
		for (int i = 0; i < torches.size(); i++) {
			ops.add(new TraceOp.Leave(spots.get(i), torches.get(i).state()));
		}
		if (!TraceOp.apply(level, view, CAUSE + "/back", ops)) {
			return false;
		}
		for (ArmedTrap.SavedBlock torch : torches) {
			TraceLedger.Entry entry = removal(ledger, level, torch.pos());
			if (entry != null) {
				ledger.remove(entry);
			}
		}
		return true;
	}

	/** The newest ledgered removal of a dark corner torch at {@code pos}. */
	static TraceLedger.@Nullable Entry removal(TraceLedger ledger, ServerLevel level, BlockPos pos) {
		List<TraceLedger.Entry> entries = ledger.entries();
		for (int i = entries.size() - 1; i >= 0; i--) {
			TraceLedger.Entry e = entries.get(i);
			if (e.kind() == TraceLedger.Kind.REMOVE && e.cause().equals(CAUSE) && e.pos().dimension().equals(level.dimension()) && e.pos().pos().equals(pos)) {
				return e;
			}
		}
		return null;
	}
}
