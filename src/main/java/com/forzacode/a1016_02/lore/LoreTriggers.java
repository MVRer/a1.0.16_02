package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.core.Attention;
import com.forzacode.a1016_02.core.AttentionTrigger;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The attention triggers lore owns now (D-017): {@code CARRYING_LIST} while any participant carries the list (F06),
 * {@code RULES_BOOK_NEAR_BASE} while the rules book (F15) is stored near any participant's base. Attention is one
 * value for the world (D-058), so each fires once per occasion, never once per witness: once per interval while
 * the list is carried, however many players carry a copy; once per interval while the book is stored near a home,
 * however many players live there. Both are rate-limited by {@link LoreConfig}.
 */
final class LoreTriggers {
	private long nextListCheck;
	private long lastListTrigger = Long.MIN_VALUE;
	private long nextRulesCheck;
	private long lastRulesTrigger = Long.MIN_VALUE;

	void reset() {
		nextListCheck = 0;
		lastListTrigger = Long.MIN_VALUE;
		nextRulesCheck = 0;
		lastRulesTrigger = Long.MIN_VALUE;
	}

	void tick(MinecraftServer server) {
		long now = server.getTickCount();
		if (now < nextListCheck && now < nextRulesCheck) {
			return;
		}
		PlayerWatch watch = Services.watch();
		LoreConfig config = LoreConfig.get();
		if (now >= nextListCheck) {
			nextListCheck = now + ModConfig.realTicks(config.carryingListCheckSeconds);
			if (elapsed(now, lastListTrigger, ModConfig.realTicks(config.carryingListIntervalMinutes * 60))
					&& anyCarries(watch.participants(server), "F06")) {
				lastListTrigger = now;
				Attention.trigger(server, AttentionTrigger.CARRYING_LIST);
			}
		}
		if (now >= nextRulesCheck) {
			nextRulesCheck = now + ModConfig.realTicks(config.rulesBookCheckSeconds);
			if (elapsed(now, lastRulesTrigger, ModConfig.realTicks(config.rulesBookIntervalMinutes * 60))
					&& storedNearAny(server, bases(watch, watch.participants(server)), config.rulesBookBaseRadius, "F15")) {
				lastRulesTrigger = now;
				Attention.trigger(server, AttentionTrigger.RULES_BOOK_NEAR_BASE);
			}
		}
	}

	private static boolean elapsed(long now, long last, long interval) {
		return last == Long.MIN_VALUE || now - last >= interval;
	}

	/** True if any of these players has the fragment's item anywhere in their inventory. */
	static boolean anyCarries(List<ServerPlayer> players, String id) {
		return players.stream().anyMatch(player -> carries(player, id));
	}

	/** True if the player has the fragment's item anywhere in their inventory. */
	static boolean carries(ServerPlayer player, String id) {
		Inventory inventory = player.getInventory();
		for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
			if (FragmentItems.is(inventory.getItem(slot), id)) {
				return true;
			}
		}
		return false;
	}

	/** These players' bases, each once (friends who share a home share one), in their own dimensions. */
	static List<GlobalPos> bases(PlayerWatch watch, List<ServerPlayer> players) {
		List<GlobalPos> bases = new ArrayList<>();
		for (ServerPlayer player : players) {
			watch.base(player).filter(base -> !bases.contains(base)).ifPresent(bases::add);
		}
		return bases;
	}

	/** True if the fragment's item is stored near any of these bases (the first one found is enough: one occasion). */
	static boolean storedNearAny(MinecraftServer server, List<GlobalPos> bases, int radius, String id) {
		for (GlobalPos base : bases) {
			ServerLevel level = server.getLevel(base.dimension());
			if (level != null && storedNear(level, base.pos(), radius, id)) {
				return true;
			}
		}
		return false;
	}

	/** True if the fragment's item is in a container (or on a lectern) within {@code radius} blocks (cube) of {@code center}. */
	static boolean storedNear(ServerLevel level, BlockPos center, int radius, String id) {
		int minX = (center.getX() - radius) >> 4;
		int maxX = (center.getX() + radius) >> 4;
		int minZ = (center.getZ() - radius) >> 4;
		int maxZ = (center.getZ() + radius) >> 4;
		for (int cx = minX; cx <= maxX; cx++) {
			for (int cz = minZ; cz <= maxZ; cz++) {
				if (!level.hasChunk(cx, cz)) {
					continue;
				}
				LevelChunk chunk = level.getChunk(cx, cz);
				for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
					BlockPos pos = blockEntity.getBlockPos();
					if (Math.abs(pos.getX() - center.getX()) > radius || Math.abs(pos.getY() - center.getY()) > radius
							|| Math.abs(pos.getZ() - center.getZ()) > radius) {
						continue;
					}
					if (blockEntity instanceof LecternBlockEntity lectern && FragmentItems.is(lectern.getBook(), id)) {
						return true;
					}
					// An unopened loot chest has no book yet; reading its slots would roll its loot.
					if (blockEntity instanceof Container container
							&& !(blockEntity instanceof RandomizableContainer loot && loot.getLootTable() != null)) {
						for (int slot = 0; slot < container.getContainerSize(); slot++) {
							if (FragmentItems.is(container.getItem(slot), id)) {
								return true;
							}
						}
					}
				}
			}
		}
		return false;
	}
}
