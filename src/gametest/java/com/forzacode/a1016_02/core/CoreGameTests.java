package com.forzacode.a1016_02.core;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Core game tests (plus {@link TraceGameTests}). Same package as core so they can reach package-private setters. */
public class CoreGameTests extends TraceGameTests {
	@GameTest
	public void profileRollIsDeterministic(GameTestHelper helper) {
		Set<WorldProfile> distinct = new HashSet<>();
		for (long seed = -200; seed < 300; seed++) {
			long salt = seed * 0x5DEECE66DL + 11;
			WorldProfile a = WorldProfile.roll(seed, salt);
			WorldProfile b = WorldProfile.roll(seed, salt);
			helper.assertTrue(a.equals(b), "roll is not deterministic for seed " + seed);
			helper.assertTrue(a.fragments().containsAll(WorldProfile.FIXED_FRAGMENTS), "missing fixed fragments: " + a.fragments());
			int rolled = a.fragments().size() - WorldProfile.FIXED_FRAGMENTS.size();
			helper.assertTrue(rolled >= WorldProfile.ROLLED_MIN && rolled <= WorldProfile.ROLLED_MAX, "rolled " + rolled + " fragments");
			WorldProfile.DEPENDENCIES.forEach((id, deps) -> {
				if (a.fragments().contains(id)) {
					helper.assertTrue(a.fragments().containsAll(deps), id + " without " + deps);
				}
			});
			helper.assertTrue(a.habits().size() == 2, "habits " + a.habits());
			if (seed == 42) {
				for (long other = 0; other < 20; other++) {
					distinct.add(WorldProfile.roll(seed, other));
				}
			}
		}
		helper.assertTrue(distinct.size() > 1, "different salts gave the same profile (D-008)");
		helper.succeed();
	}

	@GameTest
	public void stateRoundTripsThroughCodec(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);

		HerobrineState state = new HerobrineState();
		state.reroll(1234L, 5678L);
		state.addAttention(42.5);
		state.addTension(140.0); // clamps to 100
		state.setSubject(UUID.fromString("00000000-0000-0000-0000-00000000a016"), "subject");
		state.addPlayTicks(123_456L);
		state.addWarpDays(3);
		state.setStopFired(true);
		state.setTellingStarted(true);
		state.markFragmentRead("F03");
		state.setFragmentPlaced("F01", GlobalPos.of(Level.OVERWORLD, new BlockPos(300, 64, -20)));
		state.addMarkedDeath(new MarkedDeath("fell", GlobalPos.of(Level.NETHER, new BlockPos(1, 2, 3)), 9));
		state.recordFirst(HerobrineState.FirstKind.BLOCK, new PlacedBlock(GlobalPos.of(Level.OVERWORLD, BlockPos.ZERO), Blocks.OAK_PLANKS.defaultBlockState()));
		state.recordFirst(HerobrineState.FirstKind.CHEST, new PlacedBlock(GlobalPos.of(Level.OVERWORLD, new BlockPos(5, 5, 5)), Blocks.CHEST.defaultBlockState()));
		state.setEffects(new HerobrineState.Effects(true, 0.4F));
		state.setFlag("lore:f04_done", true);

		CompoundTag encoded = (CompoundTag) HerobrineState.CODEC.encodeStart(ops, state).getOrThrow();
		encoded.putString("stage", Stage.PROXIMITY.name());
		HerobrineState decoded = HerobrineState.CODEC.parse(ops, encoded).getOrThrow();
		Tag reencoded = HerobrineState.CODEC.encodeStart(ops, decoded).getOrThrow();

		helper.assertTrue(encoded.equals(reencoded), "re-encoded state differs:\n" + encoded + "\n" + reencoded);
		helper.assertTrue(decoded.stage() == Stage.PROXIMITY, "stage");
		helper.assertTrue(decoded.attention() == 42.5 && decoded.tension() == 100.0, "attention/tension");
		helper.assertTrue(decoded.profile().equals(WorldProfile.roll(1234L, 5678L)) && decoded.salt() == 5678L, "profile/salt");
		helper.assertTrue(decoded.subject().map(HerobrineState.Subject::name).orElse("").equals("subject"), "subject");
		helper.assertTrue(decoded.playTicks() == 123_456L && decoded.warpDays() == 3, "clock");
		helper.assertTrue(decoded.stopFired() && !decoded.listRead() && decoded.tellingStarted(), "story flags");
		helper.assertTrue(decoded.fragmentsRead().equals(Set.of("F03")) && decoded.fragmentsPlaced().containsKey("F01"), "fragments");
		helper.assertTrue(decoded.markedDeaths().equals(state.markedDeaths()), "marked deaths");
		helper.assertTrue(decoded.firstBlocks().equals(state.firstBlocks()) && decoded.firstBlocks().craftingTable() == null, "first blocks");
		helper.assertTrue(decoded.effects().equals(state.effects()) && decoded.hasFlag("lore:f04_done"), "effects/flags");
		helper.succeed();
	}

	@GameTest
	public void outOfViewBehindWallNotInFront(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// A stone wall at z=3 covering x 0..2: the left target hides behind it, the right one is in the open.
		for (int x = 0; x <= 2; x++) {
			for (int y = 0; y <= 7; y++) {
				helper.setBlock(x, y, 3, Blocks.STONE);
			}
		}
		BlockPos hidden = helper.absolutePos(new BlockPos(1, 2, 6));
		BlockPos open = helper.absolutePos(new BlockPos(6, 2, 6));
		BlockPos behind = helper.absolutePos(new BlockPos(1, 2, 0)).north(6);
		helper.setBlock(1, 2, 6, Blocks.DIRT);
		helper.setBlock(6, 2, 6, Blocks.DIRT);

		// A player-shaped viewpoint at (1.5, 1, 0.5), looking south (+z), toward the wall.
		Player player = helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(1.5, 1.0, 0.5)), 0.0F, 0.0F);
		List<TraceService.Viewer> viewers = List.of(TraceService.Viewer.of(player, 8));

		helper.assertTrue(TraceService.isOutOfView(level, new AABB(hidden), viewers, 3, 160), "a block behind the wall counts as in view");
		helper.assertFalse(TraceService.isOutOfView(level, new AABB(open), viewers, 3, 160), "a block in the open in front counts as out of view");
		helper.assertTrue(TraceService.isOutOfView(level, new AABB(behind), viewers, 3, 160), "a block behind the viewer counts as in view");
		BlockPos near = helper.absolutePos(new BlockPos(1, 1, 0)).north(2);
		helper.assertFalse(TraceService.isOutOfView(level, new AABB(near), viewers, 3, 160), "a block within 3 blocks counts as out of view");
		helper.succeed();
	}

	@GameTest
	public void footprintTracksPlacedAndDug(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		PlayerWatch watch = Services.watch();
		BlockPos placed = helper.absolutePos(new BlockPos(5, 1, 5));
		BlockPos dug = helper.absolutePos(new BlockPos(6, 1, 1));
		helper.setBlock(5, 1, 5, Blocks.OAK_PLANKS);

		watch.onPlaced((ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL), level, placed, Blocks.OAK_PLANKS.defaultBlockState());
		watch.onBroken(level, dug);

		helper.assertTrue(watch.wasPlacedByPlayer(level, placed) && !watch.wasDugByPlayer(level, placed), "placed block not recorded");
		helper.assertTrue(watch.wasDugByPlayer(level, dug) && !watch.wasPlacedByPlayer(level, dug), "dug block not recorded");
		helper.assertTrue(watch.placedNear(level, placed.offset(3, 0, -3), 4, Blocks.OAK_PLANKS).contains(placed), "placedNear missed it");
		helper.assertTrue(watch.placedNear(level, placed, 4, Blocks.STONE).isEmpty(), "placedNear ignored the block filter");
		helper.assertTrue(watch.dugNear(level, dug.offset(-2, 1, 2), 2).contains(dug) && watch.dugNear(level, dug.offset(10, 0, 0), 4).isEmpty(), "dugNear");

		watch.onBroken(level, placed); // breaking a player-placed block is not digging
		helper.assertTrue(!watch.wasPlacedByPlayer(level, placed) && !watch.wasDugByPlayer(level, placed), "broken placed block");
		helper.succeed();
	}

	@GameTest
	public void removeWritesLedgerAndBatchCommits(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
		helper.setBlock(4, 1, 4, Blocks.COBBLESTONE);
		int before = TraceLedger.get(level.getServer()).entries().size();

		// No real players are in the test level, so everything is out of view.
		helper.assertTrue(Services.traces().isOutOfView(level, pos), "no players, yet in view");
		helper.assertTrue(Services.traces().remove(level, pos, "test:remove"), "remove failed");
		helper.assertTrue(level.getBlockState(pos).isAir(), "block still there");
		List<TraceLedger.Entry> entries = TraceLedger.get(level.getServer()).entries();
		helper.assertTrue(entries.size() == before + 1 && entries.getLast().cause().equals("test:remove")
				&& entries.getLast().state().orElseThrow().is(Blocks.COBBLESTONE), "ledger entry missing");

		BlockPos a = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos b = helper.absolutePos(new BlockPos(3, 1, 2));
		helper.setBlock(2, 1, 2, Blocks.GRASS_BLOCK);
		boolean committed = Services.traces().batch(level, "test:batch").convert(a, Blocks.DIRT.defaultBlockState())
				.leave(b, Blocks.GLOWSTONE.defaultBlockState()).commit();
		helper.assertTrue(committed && level.getBlockState(a).is(Blocks.DIRT) && level.getBlockState(b).is(Blocks.GLOWSTONE), "batch did not apply");
		helper.succeed();
	}
}
