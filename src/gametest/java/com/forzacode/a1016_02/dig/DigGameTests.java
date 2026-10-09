package com.forzacode.a1016_02.dig;

import java.util.List;
import java.util.Map;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceLedger;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of the dig workstream, already registered in the gametest fabric.mod.json. The network tests are in
 * {@link NetworkGameTests} and the tunnel tests in {@link TunnelGameTests}.
 */
public class DigGameTests extends TunnelGameTests {
	private static final Map<String, Tier> CARDS = Map.of(
			TorchCards.TorchesGone.ID, Tier.MINOR,
			TorchCards.TorchesBehindYou.ID, Tier.MAJOR,
			SoundCards.MiningThatMoves.ID, Tier.MAJOR,
			ScarCards.TunnelThatGrows.ID, Tier.MAJOR,
			ScarCards.TunnelIntoMine.ID, Tier.MAJOR,
			ScarCards.TreesStripped.ID, Tier.MAJOR,
			SoundCards.UnderYouSound.ID, Tier.MINOR,
			SoundCards.UnderYouFootstep.ID, Tier.MINOR,
			UnderYouStackCard.ID, Tier.MINOR);

	@GameTest
	public void everyCardIsRegisteredAndFires(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 7, 8, 4, 8, Blocks.STONE);
		g.stand(g.at(4, 4, 4));
		for (Map.Entry<String, Tier> entry : CARDS.entrySet()) {
			EventCard card = CardRegistry.get(entry.getKey()).orElse(null);
			helper.assertTrue(card != null, "card not registered: " + entry.getKey());
			helper.assertTrue(card.tier() == entry.getValue(), entry.getKey() + " tier " + card.tier());
			helper.assertTrue(card.earliestStage().atLeast(Stage.TRACES), entry.getKey() + " too early");
			for (boolean fake : card.hasFake() ? List.of(false, true) : List.of(false)) {
				// No base, no network, no torches here: every card either fires or says there is no spot, without throwing.
				FireResult result = card.fire(new FireContext(g.mock, g.level, fake, true, RandomSource.create(1L)));
				helper.assertTrue(result != null, entry.getKey() + " returned null");
			}
		}
		helper.succeed();
	}

	@GameTest
	public void miningThatMovesComesThroughTheStoneAndStopsUnderThePlayer(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 8, 40, 16, 40, Blocks.STONE);
		BlockPos feet = g.at(20, 16, 20);
		g.stand(feet);
		DigConfig config = DigConfig.get();
		EventCard card = CardRegistry.get(SoundCards.MiningThatMoves.ID).orElseThrow();
		int before = DigTicker.INSTANCE.pendingCues();
		helper.assertTrue(card.fire(new FireContext(g.mock, g.level, false, true, RandomSource.create(2L))) == FireResult.FIRED, "did not fire");
		List<Vec3> cues = DigTicker.INSTANCE.pendingCuePositions(g.mock.getUUID());
		helper.assertTrue(DigTicker.INSTANCE.pendingCues() - before == config.miningSteps * 3, "expected " + config.miningSteps * 3 + " cues");
		Vec3 first = cues.getFirst();
		Vec3 last = cues.getLast();
		helper.assertTrue(first.distanceTo(Vec3.atCenterOf(feet)) > config.miningStartDistance - 1, "it does not start far away");
		helper.assertTrue(last.distanceTo(Vec3.atCenterOf(feet.below(3))) < 1.0E-6, "it does not stop right under the player: " + last);
		helper.assertTrue(DigData.get(g.level.getServer()).hasOnce(SoundCards.MiningThatMoves.onceKey(g.level)), "not marked for this stage");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void treesStrippedTakesTheLeavesOfAPlantedTree(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 9, 15, 1, 15, Blocks.DIRT);
		BlockPos root = g.at(7, 1, 7);
		BlockState sapling = Blocks.OAK_SAPLING.defaultBlockState();
		g.place(root, sapling);
		// Leaves the player placed next to it stay.
		BlockPos placedLeaves = g.at(2, 1, 2);
		g.place(placedLeaves, Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true));
		RandomSource random = RandomSource.create(4L);
		for (int i = 0; i < 20 && !g.level.getBlockState(root).is(Blocks.OAK_LOG); i++) {
			((SaplingBlock) Blocks.OAK_SAPLING).advanceTree(g.level, root, g.level.getBlockState(root).setValue(SaplingBlock.STAGE, 1), random);
		}
		helper.assertTrue(g.level.getBlockState(root).is(Blocks.OAK_LOG), "the sapling did not grow");
		helper.assertTrue(Services.watch().wasPlacedByPlayer(g.level, root), "the trunk no longer counts as planted");

		TreeStripper.Tree tree = TreeStripper.find(g.level, root);
		helper.assertTrue(tree != null && tree.logs().size() >= 4 && tree.leaves().size() >= 20, "tree not found: " + tree);
		helper.assertTrue(TreeStripper.strip(g.level, tree, Services.traces()), "strip refused");
		for (BlockPos log : tree.logs()) {
			helper.assertTrue(g.level.getBlockState(log).is(Blocks.OAK_LOG), "a log went at " + log);
		}
		BlockPos.betweenClosed(root.offset(-7, 0, -7), root.offset(7, 12, 7)).forEach(pos -> {
			BlockState state = g.level.getBlockState(pos);
			helper.assertFalse(state.is(Blocks.OAK_LEAVES) && !state.getValue(LeavesBlock.PERSISTENT), "natural leaves left at " + pos);
		});
		helper.assertTrue(g.level.getBlockState(placedLeaves).is(Blocks.OAK_LEAVES), "the player's own leaves went");
		helper.assertTrue(TraceLedger.get(g.level.getServer()).entries().stream().anyMatch(e -> e.cause().equals(TreeStripper.CAUSE)), "not ledgered");
		helper.succeed();
	}

	@GameTest(maxTicks = 100)
	public void torchesGoneFromACaveThePlayerLeft(GameTestHelper helper) {
		DigGround g = DigGround.of(helper, 10, 24, 8, 96, Blocks.STONE);
		// A natural cave the player explored and lit at the south end; the player is now at the north end.
		g.hollow(8, 2, 70, 14, 4, 90);
		DigData.get(g.level.getServer()).explored(g.level.dimension()).add(g.at(11, 2, 80), Integer.MAX_VALUE);
		BlockPos wallTorch = g.at(8, 3, 75);
		g.place(wallTorch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.EAST));
		BlockPos floorTorch = g.at(11, 2, 82);
		g.place(floorTorch, Blocks.TORCH.defaultBlockState());
		g.stand(g.at(11, 8, 4));

		int removed = TorchCards.removeFromLeftCave(g.level, g.mock, RandomSource.create(6L));
		helper.assertTrue(removed == 2, "removed " + removed + " torches");
		helper.assertTrue(g.level.getBlockState(wallTorch).isAir() && g.level.getBlockState(floorTorch).isAir(), "torches still there");
		helper.assertTrue(g.level.getBlockState(wallTorch.west()).is(Blocks.STONE) && g.level.getBlockState(floorTorch.below()).is(Blocks.STONE),
				"a support block went");
		helper.succeed();
	}

	@GameTest
	public void digDataRoundTrips(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		DigData data = new DigData();
		Network net = new Network(helper.getLevel().dimension(), new BlockPos(10, 70, -4));
		net.depth = 58;
		net.addAnchor(new BlockPos(12, 58, -2), false);
		net.addAnchor(new BlockPos(13, 58, -2), false);
		net.addAnchor(new BlockPos(13, 59, -2), true);
		net.heads.add(new Network.Head(new BlockPos(13, 58, -2), Direction.EAST, 5));
		net.bedHead = new BlockPos(10, 70, -4);
		net.alcove = new BlockPos(1, 2, 3);
		net.lastNight = 7;
		net.nights = 4;
		net.budget = 3;
		data.networks.add(net);
		GrowingTunnel tunnel = new GrowingTunnel(helper.getLevel().dimension(), new BlockPos(0, 64, 0), new BlockPos(40, 64, 0), Direction.WEST);
		tunnel.anchors.add(new BlockPos(40, 64, 0));
		tunnel.visited = true;
		data.growing = tunnel;
		data.tunnels.add(new DigData.CardTunnel(helper.getLevel().dimension(), List.of(new BlockPos(5, 5, 5)), "plain", 3));
		data.explored(helper.getLevel().dimension()).add(new BlockPos(1, 1, 1), 10);
		data.once.add("mining_that_moves@PROXIMITY");

		Tag saved = DigData.CODEC.encodeStart(ops, data).getOrThrow();
		DigData loaded = DigData.CODEC.parse(ops, saved).getOrThrow();
		Network back = loaded.network().orElseThrow();
		helper.assertTrue(back.anchors.equals(net.anchors) && back.cells.equals(net.cells) && back.shaftAnchors.equals(net.shaftAnchors), "anchors");
		helper.assertTrue(back.depth == 58 && back.lastNight == 7 && back.nights == 4 && back.budget == 3 && net.alcove.equals(back.alcove)
				&& net.bedHead.equals(back.bedHead) && back.heads.size() == 1 && back.heads.getFirst().dir == Direction.EAST, "network fields");
		GrowingTunnel t = loaded.growing().orElseThrow();
		helper.assertTrue(t.anchors.equals(tunnel.anchors) && t.visited && t.dir == Direction.WEST && t.first.equals(tunnel.first), "growing tunnel");
		helper.assertTrue(loaded.tunnels.size() == 1 && loaded.hasOnce("mining_that_moves@PROXIMITY")
				&& loaded.explored(helper.getLevel().dimension()).contains(new BlockPos(1, 1, 1)), "tunnels, once, explored");
		helper.assertTrue(loaded.tunnelCells(helper.getLevel().dimension()).contains(new BlockPos(13, 60, -1).asLong()), "tunnel index");
		helper.succeed();
	}
}
