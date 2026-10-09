package com.forzacode.a1016_02.accident.trap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;

import com.forzacode.a1016_02.accident.AccidentConfig;
import com.forzacode.a1016_02.accident.AccidentData;
import com.forzacode.a1016_02.accident.ArmedTrap;
import com.forzacode.a1016_02.accident.Candidate;
import com.forzacode.a1016_02.accident.TraceOp;
import com.forzacode.a1016_02.accident.TrapContext;
import com.forzacode.a1016_02.core.Habit;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * D-034, his part out of the overworld: one block of your own bridge goes missing, out of view, ahead on your route.
 * {@code void_bridge}: over the End's void. {@code lava_bridge}: over a Nether lava lake. You misstepped in the dark.
 * The clue: a perfect 1x1 gap, the blocks either side untouched, nothing broken off. A death counts only after you
 * dropped through that gap.
 */
public final class BridgeGapTrap extends BaseTrap {
	private final Bridges.Below below;
	private final ResourceKey<Level> dimension;

	private BridgeGapTrap(String id, String cause, Bridges.Below below, ResourceKey<Level> dimension) {
		super(id, false, cause, EnumSet.of(Habit.WATCHER));
		this.below = below;
		this.dimension = dimension;
	}

	/** A block of your bridge over the End's void. */
	public static BridgeGapTrap overVoid() {
		return new BridgeGapTrap("void_bridge", "fell", Bridges.Below.VOID, Level.END);
	}

	/** A block of your bridge over a Nether lava lake. */
	public static BridgeGapTrap overLava() {
		return new BridgeGapTrap("lava_bridge", "lava", Bridges.Below.LAVA, Level.NETHER);
	}

	/** Only in its own dimension. */
	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel level, AccidentConfig cfg) {
		return level.dimension().equals(dimension);
	}

	@Override
	public List<Candidate> candidates(TrapContext ctx) {
		ServerLevel level = ctx.level();
		AccidentConfig cfg = ctx.cfg();
		List<Candidate> found = new ArrayList<>();
		if (!level.dimension().equals(dimension)) {
			return found;
		}
		for (BlockPos deck : Bridges.walkedDeck(ctx)) {
			if (Bridges.fromPlayer(ctx, deck) < cfg.bridgeMinFromPlayer || !Bridges.clean(level, deck) || Bridges.spanAxis(level, deck) == null
					|| !Bridges.ahead(ctx, deck, below)) {
				continue;
			}
			int r = cfg.bridgeZoneRadius;
			BlockPos bottom;
			String clue;
			if (below == Bridges.Below.VOID) {
				if (!Bridges.overVoid(level, deck)) {
					continue;
				}
				bottom = new BlockPos(deck.getX() - r, level.getMinY() - 128, deck.getZ() - r);
				clue = "One block of your bridge over the void at " + at(deck) + " is gone: a perfect 1x1 gap, the blocks either side untouched, nothing broken off.";
			} else {
				int lava = Bridges.lavaBelow(level, deck, cfg.lavaBridgeMaxDrop, cfg.lavaLakeMinSources);
				if (lava == Integer.MIN_VALUE) {
					continue;
				}
				bottom = new BlockPos(deck.getX() - r, lava - 4, deck.getZ() - r);
				clue = "One block of your bridge over the lava at " + at(deck) + " is gone: a perfect 1x1 gap, the blocks either side untouched, nothing burned.";
			}
			found.add(Candidate.of(deck, List.of(TraceOp.remove(deck)), bottom, deck.offset(r, 3, r), clue));
		}
		found.sort(Comparator.comparingDouble(c -> Bridges.fromPlayer(ctx, c.pos)));
		return found;
	}

	/** Every tick while set: notes when the subject drops through the gap (from the deck down to three blocks under it). */
	@Override
	public void watch(ServerPlayer subject, ArmedTrap armed, AccidentData data, AccidentConfig cfg, long now) {
		if (subject.getBoundingBox().intersects(gapShaft(armed.pos()))) {
			data.gapPassTick = now;
		}
	}

	/** The cell of the gap and the three under it. */
	public static AABB gapShaft(BlockPos gap) {
		return new AABB(gap.getX(), gap.getY() - 3, gap.getZ(), gap.getX() + 1, gap.getY() + 1, gap.getZ() + 1);
	}

	/** The void or a fall under the End bridge; lava or its fire under the Nether one. */
	@Override
	public boolean matches(DamageSource source, ArmedTrap armed) {
		return below == Bridges.Below.VOID ? source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.is(DamageTypes.FALL)
				: source.is(DamageTypes.LAVA) || source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE);
	}

	/** The void, a fall or the lava, and only after dropping through the gap since it was made. */
	@Override
	public boolean claims(ServerPlayer player, DamageSource source, ArmedTrap armed, AccidentData data, long now) {
		long memory = AccidentConfig.get().bridgeFallMemoryTicks();
		return matches(source, armed) && data.gapPassTick >= armed.setAt() && now - data.gapPassTick <= memory;
	}
}
