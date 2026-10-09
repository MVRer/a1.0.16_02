package com.forzacode.a1016_02.lore;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * F30's twin signs: placed, never removable, not by the player and not by him. A protected sign cannot be mined,
 * exploded, pushed, replaced or destroyed (lore's mixins on {@code Level}, explosions and pistons), cannot be
 * edited (it is waxed), and {@code TraceService} refuses any edit that would touch it. Positions come from
 * {@link LoreData} anchors {@code F30/grove} and {@code F30/bedrock}.
 */
public final class UnbreakableSigns {
	static final String[] ANCHORS = {"F30/grove", "F30/bedrock"};

	/** Copy-on-write, so the hot {@code setBlock} check is one volatile read and, almost always, an empty map. */
	private static volatile Map<ResourceKey<Level>, LongSet> protectedPositions = Map.of();

	private UnbreakableSigns() {
	}

	/** True if the block at {@code pos} is a protected sign. Cheap; any thread. */
	public static boolean isProtected(Level level, BlockPos pos) {
		Map<ResourceKey<Level>, LongSet> current = protectedPositions;
		if (current.isEmpty()) {
			return false;
		}
		LongSet positions = current.get(level.dimension());
		return positions != null && positions.contains(pos.asLong());
	}

	static synchronized void protect(ServerLevel level, BlockPos pos) {
		Map<ResourceKey<Level>, LongSet> copy = copy();
		copy.computeIfAbsent(level.dimension(), k -> new LongOpenHashSet()).add(pos.asLong());
		protectedPositions = Map.copyOf(copy);
	}

	/** Tests only. */
	static synchronized void release(ServerLevel level, BlockPos pos) {
		Map<ResourceKey<Level>, LongSet> copy = copy();
		LongSet positions = copy.get(level.dimension());
		if (positions != null) {
			positions.remove(pos.asLong());
			if (positions.isEmpty()) {
				copy.remove(level.dimension());
			}
		}
		protectedPositions = Map.copyOf(copy);
	}

	/** Protects the placed twins of this world (server start). */
	static synchronized void load(MinecraftServer server) {
		protectedPositions = Map.of();
		LoreData data = LoreData.get(server);
		for (String key : ANCHORS) {
			Optional<GlobalPos> pos = data.anchor(key);
			pos.ifPresent(p -> {
				ServerLevel level = server.getLevel(p.dimension());
				if (level != null) {
					protect(level, p.pos());
				}
			});
		}
	}

	static synchronized void clear() {
		protectedPositions = Map.of();
	}

	private static Map<ResourceKey<Level>, LongSet> copy() {
		Map<ResourceKey<Level>, LongSet> copy = new HashMap<>();
		protectedPositions.forEach((dimension, positions) -> copy.put(dimension, new LongOpenHashSet(positions)));
		return copy;
	}
}
