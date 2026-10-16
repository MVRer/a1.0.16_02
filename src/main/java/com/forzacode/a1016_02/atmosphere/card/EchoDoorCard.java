package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.atmosphere.mixin.FenceGateBlockAccessor;
import com.forzacode.a1016_02.atmosphere.mixin.TrapDoorBlockAccessor;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.PlayerAction;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.WoodType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Your door, again (D-076; MINOR, Traces, SOUND): a door, trapdoor or fence gate the player used by hand 5 to 60 minutes
 * of their play ago ({@code echoDoorMin/MaxAgeMinutes}), now 6 to 16 blocks from them ({@code echoDoorMin/MaxDistance})
 * and out of everyone's view, plays its own open and close sounds (a door's or trapdoor's {@code BlockSetType}, a gate's
 * wood type, as vanilla plays them) with the gap the player left between opening and closing it, or a plausible one
 * ({@code echoDoorGapMin/MaxTicks}) if they left none, louder than vanilla's door so the whole band is heard
 * ({@code echoDoorVolume}). The block never changes. Anyone in range hears it (D-060). Never within 30 s of the
 * player's combat. No fake.
 *
 * <p>The sounds end on the state the door is in, so whoever looks finds what they heard: a closed door opens then
 * closes (the usual case), one standing open closes then opens again.
 */
public final class EchoDoorCard extends EchoCard {
	public static final String ID = "echo_door";

	public EchoDoorCard() {
		super(ID, Tier.MINOR, Stage.TRACES, Set.of(), Set.of(CardTag.SOUND), false);
	}

	/** A door's own open and close sounds. */
	record DoorSounds(SoundEvent open, SoundEvent close) {
	}

	/** A door the player used that may echo now: where, its state now, and the gap they left (or -1). */
	record Candidate(BlockPos pos, BlockState state, int gap) {
		/** The door's whole body for the view rule: both halves of a door. */
		AABB box() {
			return state.getBlock() instanceof DoorBlock ? new AABB(pos).expandTowards(0.0, 1.0, 0.0) : new AABB(pos);
		}
	}

	@Override
	public boolean contextFits(ServerPlayer player, ServerLevel world) {
		return fits(player, !doors(player, world, false).isEmpty());
	}

	@Override
	public FireResult fire(FireContext ctx) {
		ServerPlayer player = ctx.player();
		ServerLevel level = ctx.level();
		List<Candidate> doors = doors(player, level, ctx.forced());
		if (doors.isEmpty()) {
			return FireResult.SKIPPED;
		}
		for (Candidate door : doors) {
			if (outOfView.test(level, door.box())) {
				episodes.accept(new Replay(watch.get(), player.getUUID(), level, Vec3.atCenterOf(door.pos()), beats(door, ctx.random(), cfg()), sounds));
				return FireResult.FIRED;
			}
		}
		return FireResult.NO_SPOT;
	}

	/**
	 * The doors this card can echo, the one used last first: each door the player used by hand in their level now (its
	 * newest use) {@code echoDoorMinAgeMinutes} to {@code echoDoorMaxAgeMinutes} of play ago (any age for a forced fire),
	 * loaded, the same block now, one a hand opens ({@link #sounds(BlockState)}), {@code echoDoorMinDistance} to
	 * {@code echoDoorMaxDistance} from the player. The view rule is the fire's.
	 */
	List<Candidate> doors(ServerPlayer player, ServerLevel level, boolean forced) {
		AtmosphereConfig cfg = cfg();
		Map<BlockPos, List<PlayerAction.DoorUse>> uses = new LinkedHashMap<>();
		for (PlayerAction action : watch.get().recentActions(player)) {
			if (action instanceof PlayerAction.DoorUse use && use.dimension().equals(level.dimension())) {
				uses.computeIfAbsent(use.pos(), k -> new ArrayList<>()).add(use);
			}
		}
		long now = now(level);
		long min = minutes(cfg.echoDoorMinAgeMinutes);
		long max = minutes(cfg.echoDoorMaxAgeMinutes);
		List<Candidate> doors = new ArrayList<>();
		for (Map.Entry<BlockPos, List<PlayerAction.DoorUse>> entry : uses.entrySet()) {
			BlockPos pos = entry.getKey();
			PlayerAction.DoorUse newest = entry.getValue().getFirst();
			if (!forced && !Gates.within(now - newest.tick(), min, max) || !level.isLoaded(pos)) {
				continue;
			}
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() != newest.state().getBlock() || sounds(state) == null) {
				continue;
			}
			double distance = player.position().distanceTo(Vec3.atCenterOf(pos));
			if (Gates.within(distance, cfg.echoDoorMinDistance, cfg.echoDoorMaxDistance)) {
				doors.add(new Candidate(pos, state, gap(entry.getValue(), cfg.echoDoorMaxGapTicks)));
			}
		}
		return doors;
	}

	/**
	 * The gap the player left at this door: of its uses (newest first), the newest open followed by a close at most
	 * {@code maxGap} ticks later, else the newest pair of opposite uses that close together; -1 if none.
	 */
	static int gap(List<PlayerAction.DoorUse> newestFirst, int maxGap) {
		int any = -1;
		for (int i = 0; i + 1 < newestFirst.size(); i++) {
			PlayerAction.DoorUse later = newestFirst.get(i);
			PlayerAction.DoorUse earlier = newestFirst.get(i + 1);
			long gap = later.tick() - earlier.tick();
			if (gap <= 0 || gap > maxGap || later.opened() == earlier.opened()) {
				continue;
			}
			if (earlier.opened()) {
				return (int) gap;
			}
			if (any < 0) {
				any = (int) gap;
			}
		}
		return any;
	}

	/**
	 * The echo: the door's open and close sounds, the second the player's gap (or a plausible one) after the first, in
	 * the order that leaves the door as it stands (closed: open then close; open: close then open). At
	 * {@code echoDoorVolume} (1.6: heard to 25.6 blocks, so the band's far end at 16 is not silent as at vanilla's 1)
	 * and vanilla's pitch (0.9 to 1).
	 */
	static List<Beat> beats(Candidate door, RandomSource random, AtmosphereConfig cfg) {
		DoorSounds sounds = sounds(door.state());
		if (sounds == null) {
			return List.of();
		}
		int lo = Math.max(1, cfg.echoDoorGapMinTicks);
		int hi = Math.max(lo, cfg.echoDoorGapMaxTicks);
		int gap = door.gap() > 0 ? door.gap() : lo + random.nextInt(hi - lo + 1);
		boolean openNow = door.state().getValueOrElse(BlockStateProperties.OPEN, false);
		float volume = (float) cfg.echoDoorVolume;
		SoundEvent first = openNow ? sounds.close() : sounds.open();
		SoundEvent second = openNow ? sounds.open() : sounds.close();
		return List.of(new Beat(0, first, volume, random.nextFloat() * 0.1F + 0.9F), new Beat(gap, second, volume, random.nextFloat() * 0.1F + 0.9F));
	}

	/**
	 * The block's own open and close sounds, as vanilla plays them when a hand uses it: a door (its lower half) or
	 * trapdoor by its {@code BlockSetType}, a fence gate by its wood type. Null for anything else, and for a door or
	 * trapdoor a hand cannot open (iron).
	 */
	static @Nullable DoorSounds sounds(BlockState state) {
		if (state.getBlock() instanceof DoorBlock door) {
			BlockSetType type = door.type();
			return type.canOpenByHand() && state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? new DoorSounds(type.doorOpen(), type.doorClose()) : null;
		}
		if (state.getBlock() instanceof TrapDoorBlock trapdoor) {
			BlockSetType type = ((TrapDoorBlockAccessor) trapdoor).a1016_02$type();
			return type.canOpenByHand() ? new DoorSounds(type.trapdoorOpen(), type.trapdoorClose()) : null;
		}
		if (state.getBlock() instanceof FenceGateBlock gate) {
			WoodType wood = ((FenceGateBlockAccessor) gate).a1016_02$type();
			return new DoorSounds(wood.fenceGateOpen(), wood.fenceGateClose());
		}
		return null;
	}
}
