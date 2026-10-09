package com.forzacode.a1016_02.ending.d;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ProtectedAreas;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.lore.LoreData;
import com.forzacode.a1016_02.lore.UntouchedGrove;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Game tests of Ending D (registered through {@code ending.EndingGameTests}): each step's detection, the team's
 * stair. The dangers, the undo and the sting are in the superclasses.
 */
public class EndingDGameTests extends EndingDDangerTests {
	private static final Set<String> NONE_READ = Set.of();

	private static void write(GameTestHelper helper, BlockPos rel, String... lines) {
		helper.setBlock(rel, Blocks.OAK_SIGN);
		SignBlockEntity sign = helper.getBlockEntity(rel, SignBlockEntity.class);
		List<Component> messages = new java.util.ArrayList<>();
		for (int i = 0; i < 4; i++) {
			messages.add(i < lines.length ? Component.literal(lines[i]) : Component.empty());
		}
		sign.setText(new SignText(messages, messages, DyeColor.BLACK, false), SignTextSlot.FRONT);
	}

	private static void check(GameTestHelper helper, EndingDState data, ServerPlayer player, Set<String> read) {
		Chain.check(helper.getLevel().getServer(), data, new EndingDConfig(), player, read::contains, EndingDSupport.EVERYONE);
	}

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void teamStairWindsDownToTheTwin(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		StairPlan plan = EndingDSupport.buildStair(helper, data);
		helper.assertTrue(plan.complete() && plan.yTop() == helper.absolutePos(new BlockPos(0, 18, 0)).getY(), "the stair's top is not under the ceiling");
		helper.assertTrue(plan.stairs().size() == 14, "expected 14 stairs, got " + plan.stairs().size());
		for (int i = 0; i < plan.stairs().size(); i++) {
			BlockPos stair = plan.stairs().get(i);
			helper.assertTrue(level.getBlockState(stair).getBlock() instanceof StairBlock, "no stair at " + stair.toShortString());
			helper.assertTrue(level.getBlockState(stair.above()).isAir() && level.getBlockState(stair.above(2)).isAir(), "no headroom over " + stair.toShortString());
			if (i > 0) {
				BlockPos prev = plan.stairs().get(i - 1);
				helper.assertTrue(prev.getY() - stair.getY() == 1 && prev.distManhattan(stair) == 2, "the stair is not one block wide and one step down at " + i);
			}
		}
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(EndingDSupport.AX, 10, EndingDSupport.AZ));
		helper.assertBlockPresent(Blocks.OAK_SIGN, new BlockPos(EndingDSupport.AX, EndingDSupport.TWIN_Y, EndingDSupport.AZ));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(EndingDSupport.AX - 3, 2, EndingDSupport.AZ - 3));
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(EndingDSupport.AX + 3, 5, EndingDSupport.AZ + 3));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(EndingDSupport.AX + 3, 6, EndingDSupport.AZ + 3));
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(EndingDSupport.AX, 19, EndingDSupport.AZ));
		helper.assertBlockPresent(Blocks.SAND, new BlockPos(EndingDSupport.AX, 20, EndingDSupport.AZ));
		// What the team built stays for good, and is left by others: lore never counts it as his traces (D-041).
		helper.assertTrue(Stair.CAUSE.startsWith("lore:left/"), "the stair is not ledgered as left by others");
		TraceLedger.get(level.getServer()).entries().stream().filter(e -> e.cause().startsWith(Stair.CAUSE)
				&& plan.chamberBox().inflatedBy(2).isInside(e.pos().pos())).forEach(e -> helper.assertTrue(
						Undo.skipReason(level.getServer(), e).isPresent(), "the undo would take the stair apart"));
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.YARD)
	public void chainTheMapTheGroveAndTakingItBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		LoreData lore = LoreData.get(server);
		Optional<GlobalPos> oldGrove = lore.anchor(LoreData.GROVE);
		Optional<ProtectedAreas.Area> oldArea = Services.protectedAreas().get(UntouchedGrove.AREA_ID);
		HerobrineState state = HerobrineState.get(server);
		GlobalPos oldCairn = state.fragmentsPlaced().get("F13");
		EndingDState data = new EndingDState();
		try {
			lore.setAnchor(LoreData.GROVE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(8, 1, 8))));
			Services.protectedAreas().protect(UntouchedGrove.AREA_ID, level.dimension(),
					BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(13, 11, 13))));
			ServerPlayer player = EndingDSupport.player(helper, new Vec3(8.5, 1, 8.5), 0, 0);

			// 1. The map: read, and in the grove.
			check(helper, data, player, NONE_READ);
			helper.assertTrue(data.step() == Step.MAP, "the map step passed without F28");
			check(helper, data, player, Set.of("F28"));
			helper.assertTrue(data.step() == Step.GROVE, "reaching the grove with F28 read did not count");

			// 2. The grove: F30 read and a poplar log cut there (its drop is marked as grove wood).
			check(helper, data, player, Set.of("F28", "F30"));
			helper.assertTrue(data.step() == Step.GROVE, "the grove passed without cutting a log");
			helper.setBlock(10, 1, 10, Blocks.POPLAR_LOG);
			BlockPos log = helper.absolutePos(new BlockPos(10, 1, 10));
			helper.assertTrue(Grove.cutLog(level, data, log, Blocks.POPLAR_LOG.defaultBlockState()), "a poplar log in the grove did not count");
			helper.assertFalse(Grove.cutLog(level, data, log, Blocks.OAK_LOG.defaultBlockState()), "an oak log counted");
			// Outside the protected area, or carried in and placed: not grove wood.
			helper.setBlock(15, 1, 15, Blocks.POPLAR_LOG);
			helper.assertFalse(Grove.cutLog(level, data, helper.absolutePos(new BlockPos(15, 1, 15)), Blocks.POPLAR_LOG.defaultBlockState()),
					"a log outside the grove's protected area counted");
			helper.setBlock(11, 1, 11, Blocks.POPLAR_LOG);
			BlockPos carriedLog = helper.absolutePos(new BlockPos(11, 1, 11));
			Services.watch().onPlaced(player, level, carriedLog, Blocks.POPLAR_LOG.defaultBlockState());
			helper.assertFalse(Grove.cutLog(level, data, carriedLog, Blocks.POPLAR_LOG.defaultBlockState()), "a log carried into the grove counted");
			helper.assertTrue(data.groveLogs() == 1, "expected one grove log, counted " + data.groveLogs());
			ItemEntity drop = new ItemEntity(level, log.getX() + 0.5, log.getY() + 0.5, log.getZ() + 0.5, new ItemStack(Items.POPLAR_LOG));
			level.addFreshEntity(drop);
			helper.assertTrue(Marks.has(drop.getItem(), Marks.GROVE_WOOD), "the log's drop is not grove wood");
			drop.discard();
			check(helper, data, player, Set.of("F28", "F30"));
			helper.assertTrue(data.step() == Step.TAKE_BACK, "the grove did not pass");

			// 3. Take it back: out of the cairn's center with no offering, and carried away.
			BlockPos cairn = helper.absolutePos(new BlockPos(3, 1, 3));
			state.setFragmentPlaced("F13", GlobalPos.of(level.dimension(), cairn));
			helper.setBlock(3, 1, 3, Blocks.CRAFTING_TABLE);
			Cairn.setOffering(true);
			helper.assertTrue(Cairn.take(level, data, cairn, Blocks.CRAFTING_TABLE.defaultBlockState()), "the cairn's center was not seen");
			helper.assertFalse(data.has(EndingDState.FIRST_TAKEN), "it counted with an offering left");
			Cairn.setOffering(false);
			helper.assertTrue(Cairn.take(level, data, cairn, Blocks.CRAFTING_TABLE.defaultBlockState()), "the cairn's center was not seen");
			helper.assertTrue(data.has(EndingDState.FIRST_TAKEN), "taking it back cleanly did not count");
			ItemStack first = new ItemStack(Items.CRAFTING_TABLE);
			Marks.set(first, Marks.FIRST_BLOCK);
			player.getInventory().add(first);
			check(helper, data, player, Set.of("F28", "F30"));
			helper.assertTrue(data.step() == Step.TAKE_BACK, "it counted at the cairn, not carried away");
			ServerPlayer away = EndingDSupport.player(helper, new Vec3(8.5, 1, 60.5), 0, 0);
			ItemStack carried = new ItemStack(Items.CRAFTING_TABLE);
			Marks.set(carried, Marks.FIRST_BLOCK);
			away.getInventory().add(carried);
			check(helper, data, away, Set.of("F28", "F30"));
			helper.assertTrue(data.step() == Step.UNDER_SEED, "carrying it away did not count");
		} finally {
			oldGrove.ifPresentOrElse(g -> lore.setAnchor(LoreData.GROVE, g), () -> lore.removeAnchor(LoreData.GROVE));
			oldArea.ifPresentOrElse(a -> Services.protectedAreas().protect(a.id(), a.dimension(), a.box()),
					() -> Services.protectedAreas().unprotect(UntouchedGrove.AREA_ID));
			state.setFragmentPlaced("F13", oldCairn);
			Cairn.clear();
			Grove.clear();
		}
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void chainDownTheStairTorchesSentenceAndCross(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		StairPlan plan = EndingDSupport.buildStair(helper, data);
		try {
			// 4. Under the seed: reaching the chamber.
			data.setStep(Step.UNDER_SEED);
			ServerPlayer up = EndingDSupport.player(helper, new Vec3(EndingDSupport.AX + 0.5, 12, EndingDSupport.AZ - 0.5), 0, 0);
			check(helper, data, up, NONE_READ);
			helper.assertTrue(data.step() == Step.UNDER_SEED, "the stair counted before the chamber");
			ServerPlayer down = EndingDSupport.player(helper, new Vec3(5.5, 2, 5.5), 0, 0);
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.step() == Step.TORCHES, "reaching the chamber did not count");

			// 5. Exactly six torches: five or seven do nothing.
			int[][] spots = {{5, 5}, {5, 11}, {11, 5}, {11, 11}, {5, 8}, {11, 8}, {8, 11}};
			for (int i = 0; i < 5; i++) {
				helper.setBlock(spots[i][0], 2, spots[i][1], Blocks.TORCH);
			}
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.step() == Step.TORCHES, "five torches counted");
			helper.setBlock(spots[5][0], 2, spots[5][1], Blocks.TORCH);
			helper.setBlock(spots[6][0], 2, spots[6][1], Blocks.TORCH);
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.step() == Step.TORCHES, "seven torches counted");
			helper.setBlock(spots[6][0], 2, spots[6][1], Blocks.AIR);
			helper.assertTrue(Chamber.torches(level, plan) == 6, "expected six torches, counted " + Chamber.torches(level, plan));
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.step() == Step.SENTENCE, "six torches did not count");

			// 6. The sentence under the twin, in any case and spacing; far from the twin it does not count.
			helper.assertTrue(Chamber.sentence().isPresent(), "F08's lines are not loaded");
			write(helper, new BlockPos(11, 2, 6), "he is no", "longer", "with us");
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.step() == Step.SENTENCE, "the sentence counted far from the twin");
			write(helper, new BlockPos(EndingDSupport.AX + 2, 2, EndingDSupport.AZ - 2), "sorry");
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.has(EndingDState.NAMED), "\"sorry\" in the chamber did not count as naming him");
			write(helper, new BlockPos(EndingDSupport.AX - 2, 2, EndingDSupport.AZ + 1), "He  is NO", "LONGER with", "us.");
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.step() == Step.CROSS, "the finished sentence did not count");

			// 7. His cross: the first block on bedrock, a post with arms, topped with grove planks.
			GlobalPos foot = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(6, 2, 6)));
			GlobalPos top = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(6, 4, 6)));
			helper.setBlock(6, 2, 6, Blocks.CRAFTING_TABLE);
			helper.setBlock(6, 3, 6, Blocks.STONE);
			helper.setBlock(5, 3, 6, Blocks.STONE);
			helper.setBlock(7, 3, 6, Blocks.STONE);
			helper.setBlock(6, 4, 6, Blocks.POPLAR_PLANKS);
			data.track(foot, Marks.FIRST_BLOCK);
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.step() == Step.CROSS, "a cross topped with planks from anywhere counted");
			data.track(top, Marks.GROVE_WOOD);
			helper.assertTrue(Chamber.cross(level, plan, data).isPresent(), "his cross is not seen");
			check(helper, data, down, NONE_READ);
			helper.assertTrue(data.step() == Step.LAST_MINUTE, "the last plank did not start the last minute");
			helper.assertTrue(LastMinute.running(), "the last minute is not running");
		} finally {
			LastMinute.reset();
			Chain.clear();
		}
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.YARD, maxTicks = 40)
	public void marksFollowGroveWoodIntoPlanksAndPlacedBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		// Crafting: planks from a grove log keep the mark; from any other log they do not.
		net.minecraft.world.inventory.TransientCraftingContainer grid = new net.minecraft.world.inventory.TransientCraftingContainer(
				new net.minecraft.world.inventory.AbstractContainerMenu(null, 0) {
					@Override
					public ItemStack quickMoveStack(net.minecraft.world.entity.player.Player player, int slot) {
						return ItemStack.EMPTY;
					}

					@Override
					public boolean stillValid(net.minecraft.world.entity.player.Player player) {
						return true;
					}
				}, 2, 2);
		ItemStack log = new ItemStack(Items.POPLAR_LOG);
		grid.setItem(0, log);
		helper.assertFalse(Marks.has(Marks.onCraft(grid, new ItemStack(Items.POPLAR_PLANKS, 4)), Marks.GROVE_WOOD), "planks from any log got the mark");
		Marks.set(log, Marks.GROVE_WOOD);
		ItemStack planks = Marks.onCraft(grid, new ItemStack(Items.POPLAR_PLANKS, 4));
		helper.assertTrue(Marks.has(planks, Marks.GROVE_WOOD) && planks.getCount() == 4, "planks from a grove log lost the mark");

		// A marked block that is broken marks its drop again (and stops being tracked).
		EndingDState data = EndingDState.get(server);
		BlockPos placed = helper.absolutePos(new BlockPos(4, 1, 4));
		GlobalPos at = GlobalPos.of(level.dimension(), placed);
		helper.setBlock(4, 1, 4, Blocks.POPLAR_PLANKS);
		data.track(at, Marks.GROVE_WOOD);
		Marks.onBreak(level, EndingDSupport.player(helper, new Vec3(4.5, 2, 6.5), 0, 0), placed, Blocks.POPLAR_PLANKS.defaultBlockState());
		helper.assertTrue(data.trackedAt(at).isEmpty(), "a broken block is still tracked");
		ItemEntity drop = new ItemEntity(level, placed.getX() + 0.5, placed.getY() + 0.3, placed.getZ() + 0.5, new ItemStack(Items.POPLAR_PLANKS));
		level.addFreshEntity(drop);
		helper.assertTrue(Marks.has(drop.getItem(), Marks.GROVE_WOOD), "the drop of a tracked block lost the mark");
		drop.discard();
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.YARD, maxTicks = 40)
	public void lastMinutePreviewPlaysWithoutCompletingTheEnding(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		HerobrineState state = HerobrineState.get(server);
		EndingDState data = new EndingDState();
		EndingDConfig cfg = new EndingDConfig();
		ServerPlayer player = EndingDSupport.player(helper, new Vec3(8.5, 1, 8.5), 0, 0);
		HerobrineState.Effects effects = state.effects();
		// A stair block he took: a real last minute would give it back; the preview must not.
		helper.setBlock(3, 1, 3, Blocks.OAK_STAIRS);
		BlockPos stair = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.assertTrue(Services.traces().forced().remove(level, stair, Stair.LOSS_CAUSE), "setup remove failed");
		TraceLedger.Entry taken = TraceLedger.get(server).entries().stream()
				.filter(e -> e.cause().equals(Stair.LOSS_CAUSE) && e.pos().pos().equals(stair)).findFirst().orElseThrow();
		try {
			LastMinute.start(server, data, true, player.blockPosition());
			long now = server.getTickCount();
			boolean flag = false;
			for (int i = 0; i < 40000 && LastMinute.running(); i++) {
				LastMinute.tick(server, data, cfg, player, now + i);
				flag |= state.hasFlag(LastMinute.FLAG);
				helper.assertTrue(LastMinute.figure() == null, "a preview spawned him");
			}
			helper.assertFalse(LastMinute.running(), "the preview did not finish: " + LastMinute.phase());
			helper.assertFalse(flag || state.hasFlag(LastMinute.FLAG), "a preview set ending:last_minute");
			helper.assertTrue(TraceLedger.get(server).entries().contains(taken) && level.getBlockState(stair).isAir(), "a preview gave a stair block back");
			helper.assertFalse(state.hasFlag(EndingDInit.COMPLETE_FLAG) || state.hasFlag(EndingDInit.SILENCE_FOREVER_FLAG), "a preview completed the ending");
			helper.assertTrue(data.step() == Step.MAP, "a preview moved the chain");
		} finally {
			LastMinute.reset();
			Services.traces().forced().restoreBlock(level, taken, stair);
			com.forzacode.a1016_02.core.ClientEffects.setDuskFog(server, effects.duskFogLevel());
			com.forzacode.a1016_02.core.ClientEffects.setMusicOff(server, effects.musicOff());
		}
		helper.succeed();
	}

	/** ending:last_minute is set only while a real give-back runs, and reset (the debug step) always clears it. */
	@GameTest
	public void theLastMinuteFlagNeverOutlivesTheGiving(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		EndingDState data = new EndingDState();
		boolean had = state.hasFlag(LastMinute.FLAG);
		try {
			LastMinute.start(server, data, false, helper.absolutePos(BlockPos.ZERO));
			helper.assertFalse(state.hasFlag(LastMinute.FLAG), "set before the footsteps");
			LastMinute.enter(server, data, LastMinute.Phase.FOOTSTEPS);
			helper.assertTrue(state.hasFlag(LastMinute.FLAG), "not set for the footsteps");
			LastMinute.enter(server, data, LastMinute.Phase.TORCH);
			helper.assertFalse(state.hasFlag(LastMinute.FLAG), "still set after the footsteps");
			LastMinute.enter(server, data, LastMinute.Phase.LEAVES);
			helper.assertTrue(state.hasFlag(LastMinute.FLAG), "not set for the leaf wave");
			LastMinute.reset();
			helper.assertFalse(state.hasFlag(LastMinute.FLAG) || LastMinute.running(), "reset left ending:last_minute set");
			LastMinute.start(server, data, true, helper.absolutePos(BlockPos.ZERO));
			LastMinute.enter(server, data, LastMinute.Phase.FOOTSTEPS);
			helper.assertFalse(state.hasFlag(LastMinute.FLAG), "a preview set the flag");
		} finally {
			LastMinute.reset();
			state.setFlag(LastMinute.FLAG, had);
		}
		helper.succeed();
	}

	@GameTest
	public void endingDIsWired(GameTestHelper helper) {
		helper.assertTrue(Services.traces() != null, "no trace service");
		helper.assertTrue(EndingDConfig.get().torchesRequired == 6, "six torches, one per dead list member");
		helper.assertTrue(Step.byNumber(7).orElseThrow() == Step.CROSS && Step.CROSS.next() == Step.LAST_MINUTE, "the steps are out of order");
		helper.succeed();
	}
}
