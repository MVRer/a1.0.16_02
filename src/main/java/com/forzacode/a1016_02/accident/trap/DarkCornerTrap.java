package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
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

	/** Dark now, and one of the cells the taken torches lit (flood-filled when the trap was armed). */
	public static boolean inDarkenedCells(ServerLevel level, BlockPos pos, ArmedTrap armed) {
		return armed.lit().contains(pos) && level.getBrightness(LightLayer.BLOCK, pos) == 0;
	}

	/** Remembers the cells the taken torches lit, so only spawns there count. */
	@Override
	public ArmedTrap onArmed(TrapContext ctx, Candidate candidate, ArmedTrap armed) {
		return armed.withLit(litCells(ctx.level(), armed.saved()));
	}

	/**
	 * The cells these torches light, the way block light spreads: from each torch's own level, losing at least one per
	 * step and more through blocks that dampen light, never through opaque blocks, so never through a wall. Cells
	 * that end at level 1 or more are lit.
	 */
	public static Set<BlockPos> litCells(ServerLevel level, List<ArmedTrap.SavedBlock> torches) {
		Map<BlockPos, Integer> light = new HashMap<>();
		List<ArrayDeque<BlockPos>> buckets = new ArrayList<>();
		for (int i = 0; i <= 15; i++) {
			buckets.add(new ArrayDeque<>());
		}
		for (ArmedTrap.SavedBlock torch : torches) {
			int emission = Math.min(15, torch.state().getLightEmission());
			if (emission > light.getOrDefault(torch.pos(), 0)) {
				light.put(torch.pos(), emission);
				buckets.get(emission).add(torch.pos());
			}
		}
		for (int lv = 15; lv >= 2; lv--) {
			ArrayDeque<BlockPos> bucket = buckets.get(lv);
			while (!bucket.isEmpty()) {
				BlockPos pos = bucket.poll();
				if (light.getOrDefault(pos, 0) != lv) {
					continue;
				}
				for (Direction dir : Direction.values()) {
					BlockPos next = pos.relative(dir);
					if (!level.isLoaded(next)) {
						continue;
					}
					int dampening = Math.max(1, level.getBlockState(next).getLightDampening());
					int reached = lv - dampening;
					if (dampening < 15 && reached > light.getOrDefault(next, 0)) {
						light.put(next, reached);
						buckets.get(reached).add(next);
					}
				}
			}
		}
		return new HashSet<>(light.keySet());
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
		return offSpot(level, torch, level.getBlockState(torch), group);
	}

	/** The same for a torch that is not there now (taken): where {@code state} would hold one block from {@code torch}. */
	static @Nullable BlockPos offSpot(ServerLevel level, BlockPos torch, BlockState state, List<BlockPos> group) {
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
		ArmedTrap back = morning ? restore(ctx.level(), ctx.view(), armed) : null;
		if (back == null) {
			return armed;
		}
		return back.withPhase(ArmedTrap.Phase.RESTORED, ctx.now() + ctx.cfg().darkCornerGraceTicks());
	}

	/**
	 * Puts the torches back, all out of view, each from its own ledger entry through {@code TraceService.restoreBlock},
	 * with exactly one of them a block off: the ones back in place close their entries, the one a block off becomes a
	 * MOVE, so Ending D never makes a second torch. The one a block off is the planned torch at its planned spot; if the
	 * player blocked that spot in the night, the same torch at another spot a block off; else another taken torch a
	 * block off (one torch a block off anywhere in the corner is enough). If the player blocked every such spot, the
	 * planned torch stays missing (its removal stays open, so Ending D still puts it back) and the clue becomes "one
	 * torch is missing". Spots the player filled are skipped, and so are torches whose removal is no longer in the
	 * ledger (put back already, or by Ending D).
	 *
	 * @return the trap with its clue as it now stands once everything that can come back is back, or null to try again
	 *         later (in view, or an edit was refused; a partial restore is finished on a later call)
	 */
	public static @Nullable ArmedTrap restore(ServerLevel level, ViewGate view, ArmedTrap armed) {
		TraceLedger ledger = TraceLedger.get(level.getServer());
		List<ArmedTrap.SavedBlock> open = new ArrayList<>();
		List<TraceLedger.Entry> openEntries = new ArrayList<>();
		boolean offDone = false;
		for (ArmedTrap.SavedBlock torch : armed.saved()) {
			TraceLedger.Entry entry = removal(ledger, level, torch.pos());
			if (entry != null) {
				open.add(torch);
				openEntries.add(entry);
			} else if (level.getBlockState(torch.pos()) != torch.state()) {
				// Put back already, but not in its own spot: an earlier call put this one a block off.
				offDone = true;
			}
		}
		if (open.isEmpty()) {
			return armed;
		}
		List<BlockPos> taken = new ArrayList<>();
		for (ArmedTrap.SavedBlock torch : armed.saved()) {
			taken.add(torch.pos());
		}
		armed.offPos().ifPresent(taken::add);
		// Which torch goes back a block off, and where (none if one already did).
		int off = -1;
		BlockPos offSpot = null;
		ArmedTrap.SavedBlock missing = null;
		if (!offDone) {
			ArmedTrap.SavedBlock planned = armed.saved().getFirst();
			int first = open.indexOf(planned);
			if (first >= 0) {
				BlockPos at = armed.offPos().filter(spot -> free(level, spot, planned.state())).orElse(null);
				offSpot = at != null ? at : offSpot(level, planned.pos(), planned.state(), taken);
				off = offSpot != null ? first : -1;
			}
			for (int i = 0; off < 0 && i < open.size(); i++) {
				if (i != first) {
					offSpot = offSpot(level, open.get(i).pos(), open.get(i).state(), taken);
					off = offSpot != null ? i : -1;
				}
			}
			if (off < 0) {
				missing = first >= 0 ? planned : open.getFirst();
			}
		}
		List<TraceLedger.Entry> entries = new ArrayList<>();
		List<BlockPos> spots = new ArrayList<>();
		for (int i = 0; i < open.size(); i++) {
			ArmedTrap.SavedBlock torch = open.get(i);
			BlockPos spot = i == off ? offSpot : torch.pos();
			if (torch != missing && free(level, spot, torch.state())) {
				entries.add(openEntries.get(i));
				spots.add(spot);
			}
		}
		if (!spots.isEmpty() && !view.outOfView(level, spots)) {
			return null;
		}
		boolean all = true;
		for (int i = 0; i < spots.size(); i++) {
			all &= Services.traces().restoreBlock(level, entries.get(i), spots.get(i));
		}
		if (!all) {
			return null;
		}
		return missing == null ? armed : armed.withClue("One torch is missing from the corner at " + at(missing.pos()) + ", and no torch dropped anywhere.");
	}

	/** Air, and the torch would hold there. */
	private static boolean free(ServerLevel level, BlockPos spot, BlockState state) {
		return level.getBlockState(spot).isAir() && state.canSurvive(level, spot);
	}

	/** The newest ledgered removal of a dark corner torch at {@code pos} (still open: the torch is not back). */
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
