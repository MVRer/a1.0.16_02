package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.ending.EndingApi;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.lore.LoreApi;
import com.forzacode.a1016_02.lore.LoreData;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.phys.Vec3;

/**
 * The story clues' rule fixes in Ending D (D-066, docs/STORY_CLUES.md): M1 (the sentence's four forms, never his name
 * or "sorry"), M4 (step 3 once the first block is out cleanly and away, carried or placed; an offering spoils only
 * its own visit), M5 (grove wood in any shape on top, the first block anywhere in a post on bedrock), the late clues'
 * shared flags (M7), the chamber reported to lore once the stair is finished (F35, F36), and the figure walking away
 * when the last minute starts.
 */
public class EndingDClueTests extends EndingDGroupTests {
	private static final Set<String> READ = Set.of("F28", "F30");

	private static void sign(GameTestHelper helper, BlockPos rel, String... lines) {
		helper.setBlock(rel, Blocks.OAK_SIGN);
		SignBlockEntity sign = helper.getBlockEntity(rel, SignBlockEntity.class);
		List<Component> messages = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			messages.add(i < lines.length ? Component.literal(lines[i]) : Component.empty());
		}
		sign.setText(new SignText(messages, messages, DyeColor.BLACK, false), SignTextSlot.FRONT);
	}

	private static GlobalPos at(GameTestHelper helper, int x, int y, int z) {
		return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(x, y, z)));
	}

	// --- M1 ---

	@GameTest
	public void theSentenceTakesTheWholeLineAndHesNeverHisNameOrSorry(GameTestHelper helper) {
		List<String> sentences = Chamber.sentences();
		helper.assertTrue(sentences.equals(List.of("heisnolongerwithus", "hesnolongerwithus", "ididbutheisnolongerwithus", "ididbuthesnolongerwithus")),
				"the sentences are " + sentences);
		BlockPos pos = BlockPos.ZERO;
		for (String said : List.of("he is no longer with us", "He's no longer with us.", "I did, but he is no longer with us", "i did but hes no longer with us",
				"HE IS NO LONGER WITH US")) {
			helper.assertTrue(new Chamber.Written(pos, said, "").says(sentences), "\"" + said + "\" did not finish the sentence");
			helper.assertTrue(new Chamber.Written(pos, "", said).says(sentences), "\"" + said + "\" on the back did not finish the sentence");
		}
		for (String wrong : List.of("i did, but", "he is no longer", "he is no longer with us sorry", "sorry he is no longer with us",
				"herobrine is no longer with us", "i did, but he is gone")) {
			helper.assertFalse(new Chamber.Written(pos, wrong, "").says(sentences), "\"" + wrong + "\" finished the sentence");
		}
		helper.assertFalse(new Chamber.Written(pos, "he is no longer with us", "sorry").says(sentences), "a sign that says sorry counted");
		helper.assertFalse(new Chamber.Written(pos, "he is no longer with us", "herobrine").says(sentences), "a sign that names him counted");
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void theCanonLineUnderTheTwinFinishesStepSix(GameTestHelper helper) {
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		EndingDConfig cfg = new EndingDConfig();
		EndingDSupport.buildStair(helper, data);
		try {
			data.setStep(Step.SENTENCE);
			ServerPlayer down = EndingDSupport.player(helper, new Vec3(5.5, 2, 5.5), 0, 0);
			sign(helper, new BlockPos(EndingDSupport.AX - 2, 2, EndingDSupport.AZ + 1), "I did, but he", "is no longer", "with us.");
			Chain.check(helper.getLevel().getServer(), data, cfg, down, Set.<String>of()::contains, EndingDSupport.EVERYONE);
			helper.assertTrue(data.step() == Step.CROSS && !data.has(EndingDState.NAMED), "the canon line did not finish the sentence: " + data.step());
		} finally {
			Chain.clear();
		}
		helper.succeed();
	}

	// --- M4 ---

	@GameTest(structure = EndingDSupport.YARD)
	public void takeItBackCountsOnceItIsOutCleanlyCarriedOrPlaced(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		HerobrineState real = HerobrineState.get(server);
		GlobalPos oldCairn = real.fragmentsPlaced().get("F13");
		EndingDConfig cfg = new EndingDConfig();
		BlockPos cairn = helper.absolutePos(new BlockPos(3, 1, 3));
		try {
			real.setFragmentPlaced("F13", GlobalPos.of(level.dimension(), cairn));
			Cairn.clear();
			ServerPlayer a = EndingDSupport.player(helper, new Vec3(4.5, 1, 4.5), 0, 0);
			ServerPlayer b = EndingDSupport.player(helper, new Vec3(8.5, 1, 300.5), 0, 0);
			List<ServerPlayer> both = List.of(a, b);

			// Taken while the chain is still on step 2, on a visit where A threw bread down: A walks out with it.
			EndingDState data = new EndingDState();
			data.setStep(Step.GROVE);
			Cairn.watch(both, data, cfg);
			ItemEntity bread = new ItemEntity(level, cairn.getX() + 0.5, cairn.getY() + 1, cairn.getZ() + 0.5, new ItemStack(Items.BREAD));
			bread.setThrower(a);
			Cairn.onItemAdded(level, bread, cfg, p -> true);
			helper.assertTrue(Cairn.take(level, data, cairn, Blocks.CRAFTING_TABLE.defaultBlockState()), "the cairn's center was not seen");
			ItemStack first = new ItemStack(Items.CRAFTING_TABLE);
			Marks.set(first, Marks.FIRST_BLOCK);
			a.getInventory().add(first);
			a.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 40.5)), 0, 0);
			Cairn.watch(both, data, cfg);
			helper.assertFalse(data.has(EndingDState.FIRST_TAKEN) || data.has(EndingDState.FIRST_NEAR), "it got out cleanly on a spoiled visit");
			data.setStep(Step.TAKE_BACK);
			Chain.check(server, data, cfg, both, READ::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.TAKE_BACK, "it counted after leaving on a visit with an offering");

			// The offering spoiled only that visit: everyone far, the visit ends; A brings it back, leaves nothing, takes it out.
			a.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 90.5)), 0, 0);
			Cairn.watch(both, data, cfg);
			helper.assertFalse(Cairn.offering() || Cairn.atCairn(), "the spoiled visit did not end");
			a.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 5.5)), 0, 0);
			Cairn.watch(both, data, cfg);
			helper.assertTrue(data.has(EndingDState.FIRST_NEAR), "brought back, it is not at the cairn");
			a.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 40.5)), 0, 0);
			Chain.check(server, data, cfg, both, READ::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.UNDER_SEED && data.has(EndingDState.FIRST_TAKEN), "carried away cleanly the second time, it did not count");

			// Placed: out cleanly, set down 40 blocks away and left there; nobody carries it. It counts once step 3 comes.
			Cairn.clear();
			a.getInventory().clearContent();
			a.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)), 0, 0);
			EndingDState placed = new EndingDState();
			placed.setStep(Step.GROVE);
			Cairn.watch(both, placed, cfg);
			helper.assertTrue(Cairn.take(level, placed, cairn, Blocks.CRAFTING_TABLE.defaultBlockState()), "the cairn's center was not seen");
			ItemStack again = new ItemStack(Items.CRAFTING_TABLE);
			Marks.set(again, Marks.FIRST_BLOCK);
			a.getInventory().add(again);
			a.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 25.5)), 0, 0);
			Cairn.watch(both, placed, cfg);
			helper.assertTrue(placed.has(EndingDState.FIRST_TAKEN), "out of the cairn's radius with nothing left, it did not get out cleanly");
			a.getInventory().clearContent();
			placed.track(at(helper, 3, 1, 45), Marks.FIRST_BLOCK);
			a.snapTo(helper.absoluteVec(new Vec3(4.5, 1, 4.5)), 0, 0);
			Chain.check(server, placed, cfg, both, Set.of("F28")::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(placed.step() == Step.GROVE, "step 3 counted before the chain got there");
			placed.setStep(Step.TAKE_BACK);
			Chain.check(server, placed, cfg, both, READ::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(placed.step() == Step.UNDER_SEED, "the first block placed far away did not count once step 3 came");
		} finally {
			real.setFragmentPlaced("F13", oldCairn);
			Cairn.clear();
			Marks.clear();
		}
		helper.succeed();
	}

	// --- M5 ---

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void hisCrossTakesGroveWoodInAnyShapeAndTheFirstBlockMidPost(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		StairPlan plan = EndingDSupport.buildStair(helper, data);
		// A stone foot on bedrock, the first block on it with arms either side, grove wood on top: a log.
		helper.setBlock(6, 2, 6, Blocks.STONE);
		helper.setBlock(6, 3, 6, Blocks.CRAFTING_TABLE);
		helper.setBlock(5, 3, 6, Blocks.STONE);
		helper.setBlock(7, 3, 6, Blocks.STONE);
		helper.setBlock(6, 4, 6, Blocks.POPLAR_LOG);
		GlobalPos firstAt = at(helper, 6, 3, 6);
		GlobalPos top = at(helper, 6, 4, 6);
		data.track(firstAt, Marks.FIRST_BLOCK);
		helper.assertTrue(Chamber.cross(level, plan, data).isEmpty(), "a top that is not grove wood counted");
		data.track(top, Marks.GROVE_WOOD);
		helper.assertTrue(Chamber.cross(level, plan, data).equals(Optional.of(helper.absolutePos(new BlockPos(6, 2, 6)))),
				"a grove log on top, the first block mid-post, did not count");
		for (var shape : List.of(Blocks.STRIPPED_POPLAR_LOG, Blocks.POPLAR_WOOD, Blocks.STRIPPED_POPLAR_WOOD, Blocks.POPLAR_PLANKS)) {
			helper.setBlock(6, 4, 6, shape);
			helper.assertTrue(Chamber.cross(level, plan, data).isPresent(), shape + " on top did not count");
		}
		helper.setBlock(6, 4, 6, Blocks.OAK_PLANKS);
		helper.assertTrue(Chamber.cross(level, plan, data).isEmpty(), "oak on top counted");
		helper.setBlock(6, 4, 6, Blocks.POPLAR_LOG);
		// The arms rule stays: no arms, no cross.
		helper.setBlock(7, 3, 6, Blocks.AIR);
		helper.assertTrue(Chamber.cross(level, plan, data).isEmpty(), "a post with one arm counted");
		helper.setBlock(7, 3, 6, Blocks.STONE);
		// The post must stand on bedrock.
		helper.setBlock(6, 2, 6, Blocks.AIR);
		helper.assertTrue(Chamber.cross(level, plan, data).isEmpty(), "a post off the bedrock counted");
		helper.setBlock(6, 2, 6, Blocks.STONE);
		helper.assertTrue(Chamber.cross(level, plan, data).isPresent(), "the cross did not count again");

		// Grove wood crafted into wood or stripped wood keeps its mark.
		SimpleContainer grid = new SimpleContainer(9);
		ItemStack log = new ItemStack(Items.POPLAR_LOG, 4);
		Marks.set(log, Marks.GROVE_WOOD);
		grid.setItem(0, log);
		for (var item : List.of(Items.POPLAR_WOOD, Items.STRIPPED_POPLAR_WOOD, Items.POPLAR_PLANKS)) {
			helper.assertTrue(Marks.has(Marks.onCraft(grid, new ItemStack(item)), Marks.GROVE_WOOD), item + " made from grove wood is not grove wood");
		}
		helper.assertFalse(Marks.has(Marks.onCraft(grid, new ItemStack(Items.STICK)), Marks.GROVE_WOOD), "sticks became grove wood");
		helper.succeed();
	}

	// --- M7: the late clues' flags ---

	@GameTest
	public void theChainPublishesItsStepAndDayForTheLateClues(GameTestHelper helper) {
		HerobrineState state = new HerobrineState();
		EndingDState data = new EndingDState();
		helper.assertFalse(Chain.publish(state, data, 4), "step 1's clock started before F23 was read");
		state.markFragmentRead("F23");
		helper.assertTrue(Chain.publish(state, data, 4) && state.hasFlag(EndingApi.D_STEP_FLAG + 1) && state.hasFlag(EndingApi.D_STEP_DAY_FLAG + 4),
				"step 1's clock did not start on the day F23 was read: " + state.flags());
		helper.assertFalse(Chain.publish(state, data, 9) || state.hasFlag(EndingApi.D_STEP_DAY_FLAG + 9), "the clock restarted on the same step");
		data.setStep(Step.TORCHES);
		helper.assertTrue(Chain.publish(state, data, 9), "the advance was not published");
		long steps = state.flags().stream().filter(f -> f.startsWith(EndingApi.D_STEP_FLAG)).count();
		long days = state.flags().stream().filter(f -> f.startsWith(EndingApi.D_STEP_DAY_FLAG)).count();
		helper.assertTrue(state.hasFlag(EndingApi.D_STEP_FLAG + 5) && state.hasFlag(EndingApi.D_STEP_DAY_FLAG + 9) && steps == 1 && days == 1,
				"the flags after the advance: " + state.flags());
		helper.succeed();
	}

	// --- F35, F36: the finished stair's chamber goes to lore ---

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void theFinishedStairsChamberIsReportedToLoreOnce(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		StairPlan plan = EndingDSupport.buildStair(helper, data);
		LoreData lore = LoreData.get(server);
		// Lore's own anchor names for the chamber (package-private there); restored after the test.
		List<String> keys = List.of("D/chamber_min", "D/chamber_max");
		List<Optional<GlobalPos>> before = keys.stream().map(lore::anchor).toList();
		try {
			helper.assertTrue(plan.complete(), "the stair is not finished");
			helper.assertTrue(LoreApi.chamberBuilt(server, plan.dimension(), plan.chamberBox()), "lore did not learn the finished chamber");
			helper.assertFalse(LoreApi.chamberBuilt(server, plan.dimension(), plan.chamberBox()), "the chamber was reported as new twice");
			helper.assertTrue(lore.anchor(keys.get(0)).map(GlobalPos::pos).equals(Optional.of(new BlockPos(plan.chamberBox().minX(), plan.chamberBox().minY(),
					plan.chamberBox().minZ()))), "lore keeps another chamber");
		} finally {
			for (int n = 0; n < keys.size(); n++) {
				String key = keys.get(n);
				before.get(n).ifPresentOrElse(pos -> lore.setAnchor(key, pos), () -> lore.removeAnchor(key));
			}
		}
		helper.succeed();
	}

	// --- the last minute: the figure that was out walks away ---

	/** The last minute's effects, recorded; counts the walk-aways. */
	private static final class Walks implements LastMinute.Ports {
		int walkAways;

		@Override
		public void complete(MinecraftServer server, EndingDConfig cfg) {
		}

		@Override
		public void silence(ServerPlayer player, int ticks, int fadeTicks) {
		}

		@Override
		public void calmMusic(ServerPlayer player) {
		}

		@Override
		public void fogGone(MinecraftServer server) {
		}

		@Override
		public void musicOn(MinecraftServer server) {
		}

		@Override
		public void previewEffects(ServerPlayer player, boolean fogGone, boolean musicOn) {
		}

		@Override
		public Optional<HimEntity> spawn(ServerLevel level, Vec3 feet, float yaw) {
			return Optional.empty();
		}

		@Override
		public int walkAway(MinecraftServer server) {
			walkAways++;
			return 1;
		}

		@Override
		public List<SiteRegistry.Site> groves(ServerLevel level, BlockPos near) {
			return List.of();
		}
	}

	@GameTest
	public void theFigureWalksAwayWhenTheLastMinuteStarts(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		EndingDState data = new EndingDState();
		EndingDConfig cfg = new EndingDConfig();
		Walks ports = new Walks();
		ServerPlayer player = EndingDSupport.player(helper, new Vec3(2.5, 1, 2.5), 0, 0);
		LastMinute.Present present = LastMinute.Present.of(List.of(player));
		try {
			LastMinute.use(ports);
			// A preview never sends him off: it has no figure.
			LastMinute.preview(server, data, player);
			LastMinute.tick(server, data, cfg, present, server.getTickCount() + 1);
			helper.assertTrue(ports.walkAways == 0, "a preview sent the figure away");
			LastMinute.reset();
			LastMinute.start(server, data, false, player.blockPosition());
			LastMinute.tick(server, data, cfg, present, server.getTickCount() + 1);
			helper.assertTrue(ports.walkAways == 1, "the figure that was out was not sent away as the last minute started: " + ports.walkAways);
			LastMinute.tick(server, data, cfg, present, server.getTickCount() + 2);
			helper.assertTrue(ports.walkAways == 1, "he was sent away again");
		} finally {
			LastMinute.use(LastMinute.Ports.LIVE);
			LastMinute.reset();
		}
		helper.succeed();
	}
}
