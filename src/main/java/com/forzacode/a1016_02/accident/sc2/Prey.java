package com.forzacode.a1016_02.accident.sc2;

import com.forzacode.a1016_02.core.TraceService;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * Who a scene of the second wave's traps is for (docs/SELF_CREATED_2.md, 25 and 26), looked up again at every check:
 * the trap's player as they are this tick, or, for the gallery's staging only, a fixed stand-in (no player: nothing is
 * ever set on it, the instruments only move around it).
 *
 * @param feet   where their feet are
 * @param eye    where they look from
 * @param look   the unit vector they look along
 * @param player the player, or null for the gallery's stand-in
 */
public record Prey(ServerLevel level, Vec3 feet, Vec3 eye, Vec3 look, @Nullable ServerPlayer player) {
	/** The player as they are now. */
	public static Prey of(ServerPlayer player) {
		return new Prey(player.level(), player.position(), player.getEyePosition(), player.getViewVector(1.0F), player);
	}

	/** The gallery's stand-in: feet at {@code feet}, a player's eye height, looking along {@code yaw} and {@code pitch}. */
	public static Prey standIn(ServerLevel level, Vec3 feet, float yaw, float pitch) {
		return new Prey(level, feet, feet.add(0.0, 1.62, 0.0), Vec3.directionFromRotation(pitch, yaw), null);
	}

	public BlockPos block() {
		return BlockPos.containing(feet);
	}

	/** Their body: the player's box, or a player-sized one at the stand-in's feet. */
	public AABB body() {
		return player != null ? player.getBoundingBox() : new AABB(feet.x - 0.3, feet.y, feet.z - 0.3, feet.x + 0.3, feet.y + 1.8, feet.z + 0.3);
	}

	/** Their eyes as a viewer that sees as far as {@code distance}. */
	public TraceService.Viewer viewer(double distance) {
		return new TraceService.Viewer(eye, look, body(), distance);
	}

	/** Horizontal distance from their feet. */
	public double across(Vec3 at) {
		double dx = at.x - feet.x;
		double dz = at.z - feet.z;
		return Math.sqrt(dx * dx + dz * dz);
	}
}
