package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceService;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A test patch in another dimension (the test server's flat End or Nether), far from everything and unique to the
 * test (its own slot, never shared with another test running at the same time): chunks held loaded, blocks in
 * coordinates relative to {@link #origin}, a private {@link AccidentData}, and a stand-in player who lives in that
 * level (not in the player list, so never the subject).
 */
final class Away {
	/** Half the width of the patch held loaded, in blocks. */
	private static final int REACH = 96;
	/** Each patch gets its own slot, this far apart along x, so tests running together never share a block or a chunk. */
	private static final int SLOT_SPACING = 512;
	private static final AtomicInteger SLOTS = new AtomicInteger();
	/**
	 * Where this run's slots start: far out and random, like the framework's own test grid, because the test world is
	 * kept between runs and a patch must never meet what an earlier run left there.
	 */
	private static final BlockPos BASE = new BlockPos(16 * ThreadLocalRandom.current().nextInt(-500_000, 500_000), 0,
			16 * ThreadLocalRandom.current().nextInt(-500_000, 500_000));

	final GameTestHelper helper;
	final ServerLevel level;
	final BlockPos origin;
	final AccidentData data = new AccidentData();
	final AccidentConfig cfg = AccidentConfig.get();
	final ServerPlayer player;

	Away(GameTestHelper helper, ResourceKey<Level> dimension, int y) {
		this.helper = helper;
		this.level = helper.getLevel().getServer().getLevel(dimension);
		this.origin = new BlockPos(BASE.getX() + SLOTS.getAndIncrement() * SLOT_SPACING, y, BASE.getZ());
		forEachChunk(true);
		this.player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player"), ClientInformation.createDefault()) {
			@Override
			public GameType gameMode() {
				return GameType.SURVIVAL;
			}

			@Override
			public boolean isClientAuthoritative() {
				return false;
			}
		};
		GameType.SURVIVAL.updatePlayerAbilities(player.getAbilities());
	}

	private void forEachChunk(boolean load) {
		for (int cx = (origin.getX() - REACH) >> 4; cx <= (origin.getX() + REACH) >> 4; cx++) {
			for (int cz = (origin.getZ() - 32) >> 4; cz <= (origin.getZ() + 32) >> 4; cz++) {
				level.setChunkForced(cx, cz, load);
				if (load) {
					level.getChunk(cx, cz);
				}
			}
		}
	}

	/** True once every chunk of the patch has its entities loaded and ticking (forced chunks take a few ticks). */
	boolean ready() {
		for (int cx = (origin.getX() - REACH) >> 4; cx <= (origin.getX() + REACH) >> 4; cx++) {
			for (int cz = (origin.getZ() - 32) >> 4; cz <= (origin.getZ() + 32) >> 4; cz++) {
				if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
					return false;
				}
			}
		}
		return true;
	}

	/** Runs {@code then} on the first tick the patch is {@link #ready()}; {@code then} ends the test. */
	void whenReady(Runnable then) {
		helper.startSequence().thenWaitUntil(() -> helper.assertTrue(ready(), "the patch in " + level.dimension().identifier() + " is not loaded yet: " + why()))
				.thenExecute(then);
	}

	private String why() {
		int ticking = 0;
		int loaded = 0;
		int entityTicking = 0;
		int all = 0;
		for (int cx = (origin.getX() - REACH) >> 4; cx <= (origin.getX() + REACH) >> 4; cx++) {
			for (int cz = (origin.getZ() - 32) >> 4; cz <= (origin.getZ() + 32) >> 4; cz++) {
				ChunkPos pos = new ChunkPos(cx, cz);
				all++;
				loaded += level.areEntitiesLoaded(pos.pack()) ? 1 : 0;
				ticking += level.areEntitiesActuallyLoadedAndTicking(pos) ? 1 : 0;
				entityTicking += level.isPositionEntityTicking(pos.getMiddleBlockPosition(64)) ? 1 : 0;
			}
		}
		return all + " chunks, " + loaded + " with entities loaded, " + ticking + " ticking, " + entityTicking + " entity ticking";
	}

	/** Lets the chunks go and removes the test's mobs from the patch. */
	void done(Entity... mobs) {
		for (Entity mob : mobs) {
			mob.discard();
		}
		forEachChunk(false);
	}

	BlockPos at(int x, int y, int z) {
		return origin.offset(x, y, z);
	}

	void set(BlockPos pos, BlockState state) {
		level.setBlock(pos, state, Block.UPDATE_CLIENTS);
	}

	/** A block "the player" placed (footprint, in this dimension). */
	void placed(BlockPos pos, Block block) {
		set(pos, block.defaultBlockState());
		Services.watch().onPlaced(player, level, pos, block.defaultBlockState());
	}

	/** Air in every cell from the bottom of the world up to just under the origin, over this patch: a gap to the void. */
	void openToTheBottom(int x1, int z1, int x2, int z2) {
		for (int x = x1; x <= x2; x++) {
			for (int z = z1; z <= z2; z++) {
				for (int y = level.getMinY(); y < origin.getY(); y++) {
					BlockPos pos = new BlockPos(origin.getX() + x, y, origin.getZ() + z);
					if (!level.getBlockState(pos).isAir()) {
						set(pos, Blocks.AIR.defaultBlockState());
					}
				}
			}
		}
	}

	/** A 1-wide bridge the player placed and walked, along x from {@code x1} to {@code x2} at the origin's height. */
	void bridge(int x1, int x2) {
		for (int x = x1; x <= x2; x++) {
			placed(at(x, 0, 0), Blocks.COBBLESTONE);
			data.routes().record(level.dimension(), at(x, 1, 0), (int) today(), RouteBook.ON_PLACED, 0, cfg.routePassGapTicks(), cfg.routeMaxPointsPerDimension);
		}
	}

	/** Puts the stand-in's feet at the relative spot, body and head turned to {@code yaw} (0 looks +z, -90 +x, 90 -x). */
	void stand(double x, double y, double z, float yaw, float pitch) {
		player.snapTo(Vec3.atLowerCornerOf(origin).add(x, y, z), yaw, pitch);
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
	}

	/** Which way the stand-in was walking, as the route sampler would have noted. */
	void walking(Vec3 heading) {
		data.heading = heading.normalize();
		data.headingDimension = level.dimension();
	}

	/** What the stand-in sees, as a gate. */
	ViewGate eyes() {
		return ViewGate.of(List.of(TraceService.Viewer.of(player, 8)));
	}

	long today() {
		return GameClock.day(level.getServer());
	}

	long now() {
		return GameClock.playTicks(level.getServer());
	}

	TrapContext ctx(ViewGate view) {
		return new TrapContext(level, player, player.blockPosition(), data, view, cfg, now(), today());
	}

	/** Mobs of this kind in the patch. */
	<T extends Entity> int count(Class<T> type) {
		return level.getEntitiesOfClass(type, new AABB(origin).inflate(REACH, 128, 32)).size();
	}
}
