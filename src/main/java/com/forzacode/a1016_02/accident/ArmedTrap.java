package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.CoreCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * The one armed trap, saved with the world.
 *
 * @param type      trap id ({@link TrapKind#id()})
 * @param pos       the trap's spot: the block he takes, or the place a live trap watches
 * @param targets   the blocks he took (preset) or plans to take (live)
 * @param saved     blocks to put back later (the dark corner's torches)
 * @param offPos    the dark corner: where the one torch goes back, a block off
 * @param mob       the moved mob
 * @param phase     watching for its moment, set, or (dark corner) set and put back
 * @param armedAt   play tick it was armed
 * @param setAt     play tick the world changed, or -1 while watching
 * @param until     play tick it ends: the end of the watch, or of the window in which a death counts
 * @param zoneMin   one corner of the place a death counts in
 * @param zoneMax   the other corner
 * @param clue      what a careful player can find afterward
 * @param step      progress of a stepwise trap (torches out so far)
 */
public record ArmedTrap(String type, ResourceKey<Level> dimension, BlockPos pos, List<BlockPos> targets, List<SavedBlock> saved,
		Optional<BlockPos> offPos, Optional<UUID> mob, Phase phase, long armedAt, long setAt, long until, BlockPos zoneMin, BlockPos zoneMax,
		String clue, int step) {
	public enum Phase { WATCHING, SET, RESTORED }

	/** A block and the state to put back. */
	public record SavedBlock(BlockPos pos, BlockState state) {
		public static final Codec<SavedBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
				BlockPos.CODEC.fieldOf("pos").forGetter(SavedBlock::pos),
				BlockState.CODEC.fieldOf("state").forGetter(SavedBlock::state)
		).apply(i, SavedBlock::new));
	}

	public static final Codec<ArmedTrap> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("type").forGetter(ArmedTrap::type),
			Level.RESOURCE_KEY_CODEC.fieldOf("dimension").forGetter(ArmedTrap::dimension),
			BlockPos.CODEC.fieldOf("pos").forGetter(ArmedTrap::pos),
			BlockPos.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(ArmedTrap::targets),
			SavedBlock.CODEC.listOf().optionalFieldOf("saved", List.of()).forGetter(ArmedTrap::saved),
			BlockPos.CODEC.optionalFieldOf("offPos").forGetter(ArmedTrap::offPos),
			UUIDUtil.CODEC.optionalFieldOf("mob").forGetter(ArmedTrap::mob),
			CoreCodecs.enumCodec(Phase.class).fieldOf("phase").forGetter(ArmedTrap::phase),
			Codec.LONG.fieldOf("armedAt").forGetter(ArmedTrap::armedAt),
			Codec.LONG.optionalFieldOf("setAt", -1L).forGetter(ArmedTrap::setAt),
			Codec.LONG.fieldOf("until").forGetter(ArmedTrap::until),
			BlockPos.CODEC.fieldOf("zoneMin").forGetter(ArmedTrap::zoneMin),
			BlockPos.CODEC.fieldOf("zoneMax").forGetter(ArmedTrap::zoneMax),
			Codec.STRING.optionalFieldOf("clue", "").forGetter(ArmedTrap::clue),
			Codec.INT.optionalFieldOf("step", 0).forGetter(ArmedTrap::step)
	).apply(i, ArmedTrap::new));

	public ArmedTrap {
		targets = List.copyOf(targets);
		saved = List.copyOf(saved);
	}

	public boolean isSet() {
		return phase != Phase.WATCHING;
	}

	/** The place a death counts in, grown by {@code slack} blocks. */
	public AABB zone(int slack) {
		return AABB.encapsulatingFullBlocks(zoneMin, zoneMax).inflate(slack);
	}

	/** The world changed now: the death window starts. */
	public ArmedTrap set(long now, long window, List<BlockPos> done) {
		return new ArmedTrap(type, dimension, pos, done, saved, offPos, mob, Phase.SET, armedAt, now, now + window, zoneMin, zoneMax, clue, step);
	}

	public ArmedTrap withPhase(Phase newPhase, long newUntil) {
		return new ArmedTrap(type, dimension, pos, targets, saved, offPos, mob, newPhase, armedAt, setAt, newUntil, zoneMin, zoneMax, clue, step);
	}

	public ArmedTrap withStep(int newStep, List<BlockPos> newTargets) {
		return new ArmedTrap(type, dimension, pos, newTargets, saved, offPos, mob, phase, armedAt, setAt, until, zoneMin, zoneMax, clue, newStep);
	}

	public ArmedTrap withZone(BlockPos min, BlockPos max) {
		return new ArmedTrap(type, dimension, pos, targets, saved, offPos, mob, phase, armedAt, setAt, until, min, max, clue, step);
	}
}
