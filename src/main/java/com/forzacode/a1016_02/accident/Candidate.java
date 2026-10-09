package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import org.jspecify.annotations.Nullable;

/**
 * A spot where a trap could be set right now: what he would take or move, where a death would count, and the
 * one clue it leaves.
 */
public final class Candidate {
	public final BlockPos pos;
	public final List<TraceOp> ops;
	public final BlockPos zoneMin;
	public final BlockPos zoneMax;
	public final String clue;
	public final List<ArmedTrap.SavedBlock> saved;
	public final @Nullable BlockPos offPos;
	public final @Nullable Mob mob;
	public final @Nullable BlockPos mobTo;
	/** The level the edits happen in, when it is not the one searched (the bed while you are in the Nether). */
	public final @Nullable ResourceKey<Level> dimension;

	private Candidate(BlockPos pos, List<TraceOp> ops, BlockPos zoneMin, BlockPos zoneMax, String clue, List<ArmedTrap.SavedBlock> saved,
			@Nullable BlockPos offPos, @Nullable Mob mob, @Nullable BlockPos mobTo, @Nullable ResourceKey<Level> dimension) {
		this.pos = pos.immutable();
		this.ops = List.copyOf(ops);
		this.zoneMin = zoneMin.immutable();
		this.zoneMax = zoneMax.immutable();
		this.clue = clue;
		this.saved = List.copyOf(saved);
		this.offPos = offPos;
		this.mob = mob;
		this.mobTo = mobTo;
		this.dimension = dimension;
	}

	/** A spot with its edits; a death counts inside {@code zoneMin..zoneMax}. */
	public static Candidate of(BlockPos pos, List<TraceOp> ops, BlockPos zoneMin, BlockPos zoneMax, String clue) {
		return new Candidate(pos, ops, zoneMin, zoneMax, clue, List.of(), null, null, null, null);
	}

	/** A death counts within {@code radius} blocks of {@code pos} (and {@code below} blocks under it). */
	public static Candidate around(BlockPos pos, List<TraceOp> ops, int radius, int below, String clue) {
		return of(pos, ops, pos.offset(-radius, -radius - below, -radius), pos.offset(radius, radius, radius), clue);
	}

	public Candidate withSaved(List<ArmedTrap.SavedBlock> blocks, @Nullable BlockPos off) {
		return new Candidate(pos, ops, zoneMin, zoneMax, clue, blocks, off, mob, mobTo, dimension);
	}

	public Candidate withMob(Mob moved, BlockPos to) {
		return new Candidate(pos, ops, zoneMin, zoneMax, clue, saved, offPos, moved, to, dimension);
	}

	public Candidate inDimension(ResourceKey<Level> key) {
		return new Candidate(pos, ops, zoneMin, zoneMax, clue, saved, offPos, mob, mobTo, key);
	}

	public Optional<BlockPos> off() {
		return Optional.ofNullable(offPos);
	}

	/** The blocks this spot takes away. */
	public List<BlockPos> taken() {
		return TraceOp.taken(ops);
	}

	public static String at(BlockPos pos) {
		return pos.getX() + " " + pos.getY() + " " + pos.getZ();
	}
}
