package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry.Site;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.WorldProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.JukeboxBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Placement with seeded sites (no world or dig yet) and forced traces (the test level has no players, so the
 * view check passes anyway). Each test builds a site in its own area, records it, and places one fragment.
 */
public class LorePlacementTests extends LoreTextTests {
	static final String NAME = "Tester";

	static Placing.Request request(GameTestHelper helper, String id, BlockPos absOrigin, int min, int max, TestFacts facts) {
		ServerLevel level = helper.getLevel();
		return new Placing.Request(level, fragment(id), absOrigin, min, max, 1, Services.traces().forced(), NAME, facts, level.getRandom(), true,
				new Placing.Loads());
	}

	static Placing.Result place(GameTestHelper helper, String id, BlockPos absOrigin, TestFacts facts) {
		return place(helper, id, absOrigin, 3, facts);
	}

	/** Places with sites searched within {@code max} blocks of {@code absOrigin}: tests are 13 blocks apart, so only this test's. */
	static Placing.Result place(GameTestHelper helper, String id, BlockPos absOrigin, int max, TestFacts facts) {
		Optional<Placing.Result> result = Placers.place(request(helper, id, absOrigin, 0, max, facts));
		if (result.isEmpty()) {
			throw helper.assertionException(Component.literal(id + " was not placed"));
		}
		return result.get();
	}

	static BlockPos abs(GameTestHelper helper, int x, int y, int z) {
		return helper.absolutePos(new BlockPos(x, y, z));
	}

	static Site seed(GameTestHelper helper, SiteType type, BlockPos rel, int size) {
		return Services.sites().record(type, helper.getLevel().dimension(), helper.absolutePos(rel), size);
	}

	static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	static List<String> pages(ItemStack stack) {
		WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
		return content == null ? List.of() : content.pages().stream().map(Filterable::raw).map(Component::getString).toList();
	}

	/** The container at {@code pos} holds the fragment's item; books must have its exact pages. */
	static void assertHolds(GameTestHelper helper, BlockPos pos, String id) {
		BlockEntity blockEntity = helper.getLevel().getBlockEntity(pos);
		if (!(blockEntity instanceof Container container)) {
			throw helper.assertionException(Component.literal(id + ": no container at " + pos.toShortString() + " but " + helper.getLevel().getBlockState(pos)));
		}
		for (int slot = 0; slot < container.getContainerSize(); slot++) {
			ItemStack stack = container.getItem(slot);
			if (FragmentItems.is(stack, id)) {
				Fragment fragment = fragment(id);
				if (fragment.isBook()) {
					helper.assertTrue(pages(stack).equals(fragment.pagesFor(NAME)), id + " pages " + pages(stack));
				}
				return;
			}
		}
		throw helper.assertionException(Component.literal(id + " is not in the container at " + pos.toShortString()));
	}

	static void assertSign(GameTestHelper helper, BlockPos pos, String id) {
		if (!(helper.getLevel().getBlockEntity(pos) instanceof SignBlockEntity sign)) {
			throw helper.assertionException(Component.literal(id + ": no sign at " + pos.toShortString() + " but " + helper.getLevel().getBlockState(pos)));
		}
		List<String> lines = sign.getText(SignTextSlot.FRONT).getMessages(false).stream().map(Component::getString).toList();
		helper.assertTrue(lines.equals(fragment(id).linesFor(NAME)), id + " sign reads " + lines);
		helper.assertTrue(helper.getLevel().getBlockState(pos).canSurvive(helper.getLevel(), pos), id + " sign would fall off");
	}

	static void assertClaimed(GameTestHelper helper, Placing.Result result, String id) {
		helper.assertTrue(result.site.isPresent(), id + " did not use a site");
		int siteId = result.site.get().id();
		helper.assertTrue(Services.sites().all().stream().anyMatch(s -> s.id() == siteId && s.claimedBy().equals(Optional.of(id))),
				id + " did not claim its site");
	}

	// --- engine pass with forced stages ---

	/** One engine pass over these fragments at {@code stage}: eligible ones are placed (forced) and recorded in the facts. */
	static List<String> pass(GameTestHelper helper, Stage stage, TestFacts facts, BlockPos origin, String... ids) {
		List<String> placed = new ArrayList<>();
		for (String id : ids) {
			Fragment fragment = fragment(id);
			if (!facts.enabled(id) || facts.placed(id).isPresent() || !FragmentEngine.eligible(fragment, stage, facts)) {
				continue;
			}
			Optional<Placing.Result> result = Placers.place(request(helper, id, origin, 0, 4, facts));
			result.ifPresent(r -> {
				facts.placed.put(id, GlobalPos.of(helper.getLevel().dimension(), r.pos));
				placed.add(id);
			});
		}
		return placed;
	}

	@GameTest
	public void fragmentsSpawnAtTheirStage(GameTestHelper helper) {
		floor(helper);
		seed(helper, SiteType.RUINED_HUT, new BlockPos(2, 1, 2), 1);
		seed(helper, SiteType.TUNNEL_END, new BlockPos(5, 1, 5), 20);
		TestFacts facts = new TestFacts(helper);
		facts.enabled.addAll(List.of("F01", "F06"));
		BlockPos origin = abs(helper, 3, 1, 3);
		helper.assertTrue(pass(helper, Stage.ALONE, facts, origin, "F01", "F06").isEmpty(), "something was placed while alone");
		helper.assertTrue(pass(helper, Stage.TRACES, facts, origin, "F01", "F06").equals(List.of("F01")), "Traces should place F01 only");
		helper.assertTrue(pass(helper, Stage.PROXIMITY, facts, origin, "F01", "F06").equals(List.of("F06")), "Proximity should add F06");
		assertHolds(helper, facts.placed.get("F01").pos(), "F01");
		assertHolds(helper, facts.placed.get("F06").pos(), "F06");
		helper.succeed();
	}

	@GameTest
	public void disabledFragmentsAreNotPlaced(GameTestHelper helper) {
		floor(helper);
		seed(helper, SiteType.RUINED_HUT, new BlockPos(3, 1, 3), 1);
		TestFacts facts = new TestFacts(helper);
		helper.assertTrue(pass(helper, Stage.REMOVAL, facts, abs(helper, 3, 1, 3), "F01").isEmpty(), "a disabled fragment was placed");
		helper.succeed();
	}

	/** With no world site yet, F23 waits; once own builds are allowed it leaves its own panic tower (a recorded site). */
	@GameTest(skyAccess = true)
	public void ownBuildsWaitForWorldSites(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockPos center = abs(helper, 3, 1, 3);
		Placing.Request waiting = new Placing.Request(level, fragment("F23"), center, 0, 2, 4, Services.traces().forced(), NAME,
				new TestFacts(helper), level.getRandom(), false, new Placing.Loads());
		helper.assertTrue(Placers.place(waiting).isEmpty(), "F23 built its own tower before the wait");
		Placing.Request building = new Placing.Request(level, fragment("F23"), center, 0, 2, 4, Services.traces().forced(), NAME,
				new TestFacts(helper), level.getRandom(), true, new Placing.Loads());
		Placing.Result result = Placers.place(building).orElseThrow(() -> helper.assertionException(Component.literal("no own panic tower")));
		assertHolds(helper, result.pos, "F23");
		helper.assertTrue(level.getBlockState(result.pos.below()).is(Blocks.DIRT), "the chest is not on a dirt pillar");
		assertClaimed(helper, result, "F23");
		BlockPos ground = helper.absolutePos(new BlockPos(0, 0, 0));
		for (BlockPos pos : BlockPos.betweenClosed(ground.offset(0, 1, 0), ground.offset(7, 20, 7))) {
			level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
		}
		helper.succeed();
	}

	@GameTest
	public void placeIsIdempotentAndGatedOnTheProfile(GameTestHelper helper) {
		HerobrineState state = new HerobrineState();
		state.reroll(42L, 7L);
		String enabled = state.profile().fragments().iterator().next();
		String disabled = WorldProfile.allFragmentIds().stream().filter(id -> !state.profile().fragments().contains(id)).findFirst().orElseThrow();
		int[] calls = new int[1];
		helper.assertFalse(FragmentServiceImpl.placeOnce(state, disabled, () -> ++calls[0] > 0), "a fragment this world did not roll was placed");
		helper.assertTrue(FragmentServiceImpl.placeOnce(state, enabled, () -> ++calls[0] > 0) && calls[0] == 1, "the first place did not place");
		state.setFragmentPlaced(enabled, GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO));
		helper.assertTrue(FragmentServiceImpl.placeOnce(state, enabled, () -> ++calls[0] > 0) && calls[0] == 1, "a second place placed again");
		helper.succeed();
	}

	/** Far, unloaded chunks are never read: the attempt waits and the chunks are only queued for a ticket. */
	@GameTest
	public void farChunksAreQueuedNotLoaded(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos far = abs(helper, 3, 1, 3).offset(48_000, 0, 48_000);
		helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "the far chunk is already loaded");
		Placing.Request request = new Placing.Request(level, fragment("F05"), far, 0, 0, 3, Services.traces().forced(), NAME,
				new TestFacts(helper), level.getRandom(), true, new Placing.Loads());
		helper.assertTrue(Placers.place(request).isEmpty(), "F05 was placed in an unloaded chunk");
		helper.assertTrue(request.loads().waiting(), "the attempt does not wait for its chunks");
		helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "placement loaded a chunk synchronously");
		helper.succeed();
	}

	/** F19 (not_with F07) leaves the largest pyramid to the seed while F07 is not placed. */
	@GameTest
	public void theLargestPyramidIsKeptForTheSeed(GameTestHelper helper) {
		floor(helper);
		for (BlockPos core : List.of(new BlockPos(2, 1, 2), new BlockPos(5, 1, 5))) {
			for (BlockPos pos : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 1, 1))) {
				if (!pos.equals(core)) {
					helper.setBlock(pos, Blocks.SANDSTONE);
				}
			}
		}
		seed(helper, SiteType.OCEAN_PYRAMID, new BlockPos(2, 1, 2), 9);
		seed(helper, SiteType.OCEAN_PYRAMID, new BlockPos(5, 1, 5), 2);
		TestFacts facts = new TestFacts(helper);
		facts.enabled.addAll(List.of("F07", "F19"));
		facts.base = abs(helper, 2, 1, 2);
		Placing.Result result = place(helper, "F19", abs(helper, 2, 1, 2), 6, facts);
		helper.assertTrue(result.pos.equals(abs(helper, 5, 1, 5)), "F19 took the seed's pyramid: " + result.pos.toShortString());
		assertHolds(helper, result.pos, "F19");
		helper.assertTrue(place(helper, "F07", abs(helper, 2, 1, 2), 6, facts).pos.equals(abs(helper, 2, 1, 2)), "F07 did not get the largest");
		helper.succeed();
	}

	// --- one rule each ---

	@GameTest
	public void ruinedHutGetsTheCow(GameTestHelper helper) {
		floor(helper);
		seed(helper, SiteType.RUINED_HUT, new BlockPos(3, 1, 3), 2);
		Placing.Result result = place(helper, "F01", abs(helper, 3, 1, 3), new TestFacts(helper));
		assertHolds(helper, result.pos, "F01");
		assertClaimed(helper, result, "F01");
		helper.succeed();
	}

	@GameTest
	public void secondPostGoesInTheSameHutChest(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 3, Blocks.CHEST);
		TestFacts facts = new TestFacts(helper);
		facts.placed.put("F01", GlobalPos.of(helper.getLevel().dimension(), abs(helper, 3, 1, 3)));
		Placing.Result result = place(helper, "F02", abs(helper, 3, 1, 3), facts);
		helper.assertTrue(result.pos.equals(abs(helper, 3, 1, 3)), "F02 not in F01's chest");
		assertHolds(helper, result.pos, "F02");
		helper.succeed();
	}

	@GameTest
	public void theListGoesToTheLongestTunnel(GameTestHelper helper) {
		floor(helper);
		seed(helper, SiteType.TUNNEL_END, new BlockPos(1, 1, 1), 8);
		seed(helper, SiteType.TUNNEL_END, new BlockPos(6, 1, 6), 30);
		Placing.Result result = place(helper, "F06", abs(helper, 3, 1, 3), 6, new TestFacts(helper));
		helper.assertTrue(result.pos.closerThan(abs(helper, 6, 1, 6), 2.5), "F06 not at the longest tunnel: " + result.pos.toShortString());
		assertHolds(helper, result.pos, "F06");
		helper.succeed();
	}

	@GameTest
	public void noLongerOnTheStoneFace(GameTestHelper helper) {
		for (int x = 1; x <= 6; x++) {
			for (int y = 0; y <= 3; y++) {
				for (int z = 0; z <= 7; z++) {
					boolean tunnel = (x == 3 || x == 4) && (y == 1 || y == 2) && z <= 5;
					helper.setBlock(x, y, z, tunnel ? Blocks.AIR : Blocks.STONE);
				}
			}
		}
		seed(helper, SiteType.TUNNEL_END, new BlockPos(3, 1, 5), 6);
		Placing.Result result = place(helper, "F08", abs(helper, 3, 1, 5), new TestFacts(helper));
		helper.assertTrue(helper.getLevel().getBlockState(result.pos).getBlock() instanceof WallSignBlock, "F08 is not on a wall");
		assertSign(helper, result.pos, "F08");
		helper.assertTrue(result.readTargets.contains(result.pos), "F08 sign is not a read target");
		helper.succeed();
	}

	@GameTest
	public void theSeedInThePyramidCore(GameTestHelper helper) {
		floor(helper);
		BlockPos core = new BlockPos(3, 1, 3);
		for (BlockPos pos : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 1, 1))) {
			if (!pos.equals(core)) {
				helper.setBlock(pos, Blocks.SANDSTONE);
			}
		}
		seed(helper, SiteType.OCEAN_PYRAMID, core, 2);
		Placing.Result result = place(helper, "F07", abs(helper, 3, 1, 3), new TestFacts(helper));
		helper.assertTrue(result.pos.equals(helper.absolutePos(core)), "F07 not in the core");
		assertSign(helper, result.pos, "F07");
		helper.succeed();
	}

	@GameTest
	public void theCairnTakesTheFirstCraftingTable(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(1, 1, 1, Blocks.CRAFTING_TABLE);
		BlockPos core = new BlockPos(5, 1, 5);
		for (BlockPos pos : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 1, 1))) {
			if (!pos.equals(core)) {
				helper.setBlock(pos, Blocks.SANDSTONE);
			}
		}
		seed(helper, SiteType.OCEAN_PYRAMID, core, 2);
		TestFacts facts = new TestFacts(helper);
		facts.firstBlocks = new HerobrineState.FirstBlocks(null,
				new PlacedBlock(GlobalPos.of(helper.getLevel().dimension(), abs(helper, 1, 1, 1)), Blocks.CRAFTING_TABLE.defaultBlockState()), null);
		Placing.Result result = place(helper, "F13", abs(helper, 5, 1, 5), facts);
		helper.assertBlockPresent(Blocks.CRAFTING_TABLE, core);
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(1, 1, 1));
		TraceLedger.Entry last = TraceLedger.get(helper.getLevel().getServer()).entries().getLast();
		helper.assertTrue(last.kind() == TraceLedger.Kind.MOVE && last.cause().equals("lore:his/F13"), "the move is not his in the ledger");
		helper.assertTrue(result.readTargets.contains(helper.absolutePos(core)), "the cairn is not a read target");
		helper.succeed();
	}

	@GameTest
	public void theChangesWhereTheTableStood(GameTestHelper helper) {
		floor(helper);
		TestFacts facts = new TestFacts(helper);
		facts.firstBlocks = new HerobrineState.FirstBlocks(null,
				new PlacedBlock(GlobalPos.of(helper.getLevel().dimension(), abs(helper, 3, 1, 3)), Blocks.CRAFTING_TABLE.defaultBlockState()), null);
		Placing.Result result = place(helper, "F10", abs(helper, 3, 1, 3), facts);
		helper.assertTrue(result.pos.equals(abs(helper, 3, 1, 3)), "F10 not where the table stood");
		assertHolds(helper, result.pos, "F10");
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void neverHadUnderTheCenterTree(GameTestHelper helper) {
		helper.setBlock(3, 0, 3, Blocks.STONE);
		helper.setBlock(3, 1, 3, Blocks.DIRT);
		for (int y = 2; y <= 5; y++) {
			helper.setBlock(3, y, 3, Blocks.OAK_LOG);
		}
		seed(helper, SiteType.BARE_GROVE, new BlockPos(3, 2, 3), 3);
		Placing.Result result = place(helper, "F09", abs(helper, 3, 2, 3), new TestFacts(helper));
		helper.assertTrue(result.pos.equals(abs(helper, 3, 0, 3)), "F09 not buried under the trunk: " + result.pos.toShortString());
		assertHolds(helper, result.pos, "F09");
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void theListCopyOnThePanicTower(GameTestHelper helper) {
		for (int y = 0; y <= 4; y++) {
			helper.setBlock(3, y, 3, Blocks.DIRT);
		}
		seed(helper, SiteType.PANIC_TOWER, new BlockPos(3, 0, 3), 5);
		Placing.Result result = place(helper, "F23", abs(helper, 3, 0, 3), new TestFacts(helper));
		helper.assertTrue(result.pos.equals(abs(helper, 3, 5, 3)), "F23 not on top: " + result.pos.toShortString());
		assertHolds(helper, result.pos, "F23");
		helper.succeed();
	}

	@GameTest
	public void theBedrockNoteAtTheStairBottom(GameTestHelper helper) {
		floor(helper);
		seed(helper, SiteType.STAIR_BOTTOM, new BlockPos(3, 1, 3), 40);
		Placing.Result result = place(helper, "F25", abs(helper, 3, 1, 3), new TestFacts(helper));
		assertHolds(helper, result.pos, "F25");
		helper.succeed();
	}

	@GameTest
	public void discElevenUnderTheBase(GameTestHelper helper) {
		floor(helper);
		TestFacts facts = new TestFacts(helper);
		facts.base = abs(helper, 3, 1, 3);
		seed(helper, SiteType.UNDER_BASE, new BlockPos(3, 1, 3), 20);
		Placing.Result result = place(helper, "F12", abs(helper, 3, 1, 3), facts);
		assertHolds(helper, result.pos, "F12");
		Container chest = (Container) helper.getLevel().getBlockEntity(result.pos);
		long items = java.util.stream.IntStream.range(0, chest.getContainerSize()).filter(i -> !chest.getItem(i).isEmpty()).count();
		helper.assertTrue(items == 1 && chest.getItem(0).is(Items.MUSIC_DISC_11), "F12: disc 11 alone in the chest");
		helper.succeed();
	}

	/**
	 * D-004: F21 only ever goes beside world's still-burning furnace. Two ordinary emptied houses (one bare, one with
	 * a lit furnace) sit in F21's band; the camp (its build claimed by world, its house beside a lit furnace) is
	 * recorded last. Every attempt in one test, in order, because the camp is looked up at any distance.
	 */
	@GameTest
	public void theDiaryWaitsForStillBurning(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		BlockState lit = Blocks.FURNACE.defaultBlockState().setValue(AbstractFurnaceBlock.LIT, true);
		Site bare = seed(helper, SiteType.EMPTIED_HOUSE, new BlockPos(1, 1, 1), 1);
		helper.setBlock(new BlockPos(1, 1, 6), lit);
		Site litHouse = seed(helper, SiteType.EMPTIED_HOUSE, new BlockPos(1, 1, 5), 1);
		helper.setBlock(new BlockPos(6, 1, 6), lit);
		TestFacts facts = new TestFacts(helper);
		BlockPos near = abs(helper, 3, 1, 3);

		// 2. Without the flag F21 waits: not into an emptied house, and no furnace or house of its own.
		helper.assertTrue(Placers.place(request(helper, "F21", near, 0, 4, facts)).isEmpty(), "F21 was placed before still burning");
		// 3. The flag is set but world has not recorded the camp's house yet: it still waits.
		facts.stillBurning = true;
		helper.assertTrue(Placers.place(request(helper, "F21", near, 0, 4, facts)).isEmpty(), "F21 was placed before the camp's house exists");
		helper.assertTrue(Placers.waitsForStillBurning(true, Services.sites().all(), level.dimension()), "the list would not say F21 waits");
		helper.assertTrue(Placers.waitsForStillBurning(false, Services.sites().all(), level.dimension()), "F21 does not wait without the flag");
		assertNoDiary(helper, bare, litHouse);

		// 1. World built the camp: F21 goes beside its furnace, far outside F21's 150 to 1500 band.
		Site build = seed(helper, SiteType.ABANDONED_BUILD, new BlockPos(7, 1, 7), 2);
		Services.sites().claim(build, Placers.CAMP_CLAIM);
		Site camp = seed(helper, SiteType.EMPTIED_HOUSE, new BlockPos(6, 1, 4), 3);
		helper.assertFalse(Placers.waitsForStillBurning(true, Services.sites().all(), level.dimension()), "the list would still say F21 waits");
		BlockPos far = near.offset(3000, 0, 0);
		Placing.Result result = Placers.place(request(helper, "F21", far, 150, 1500, facts))
				.orElseThrow(() -> helper.assertionException(Component.literal("F21 was not placed in the still-burning camp")));
		helper.assertTrue(result.pos.distManhattan(abs(helper, 6, 1, 6)) == 1, "F21 not beside the camp's furnace: " + result.pos.toShortString());
		assertHolds(helper, result.pos, "F21");
		assertClaimed(helper, result, "F21");
		helper.assertTrue(result.site.get().id() == camp.id(), "F21 took another emptied house: site #" + result.site.get().id());
		assertNoDiary(helper, bare, litHouse, result.pos);
		helper.succeed();
	}

	/** Only the test's two furnaces, no chest but {@code allowed}, and the ordinary emptied houses still free. */
	private static void assertNoDiary(GameTestHelper helper, Site bare, Site litHouse, BlockPos... allowed) {
		int found = 0;
		for (BlockPos pos : BlockPos.betweenClosed(helper.absolutePos(new BlockPos(-1, 0, -1)), helper.absolutePos(new BlockPos(8, 4, 8)))) {
			BlockState state = helper.getLevel().getBlockState(pos);
			if (state.getBlock() instanceof AbstractFurnaceBlock) {
				found++;
			}
			if (state.is(Blocks.CHEST) && !List.of(allowed).contains(pos)) {
				throw helper.assertionException(Component.literal("a chest was left at " + pos.toShortString()));
			}
		}
		helper.assertTrue(found == 2, "lore lit a furnace of its own: " + found + " furnaces");
		for (Site site : List.of(bare, litHouse)) {
			helper.assertTrue(Services.sites().all().stream().anyMatch(s -> s.id() == site.id() && !s.claimed()), "an ordinary emptied house was used");
		}
	}

	@GameTest
	public void theSignByTheCross(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 3, Blocks.COBBLESTONE);
		helper.setBlock(3, 2, 3, Blocks.COBBLESTONE);
		helper.setBlock(3, 3, 3, Blocks.COBBLESTONE);
		helper.setBlock(2, 2, 3, Blocks.COBBLESTONE);
		helper.setBlock(4, 2, 3, Blocks.COBBLESTONE);
		seed(helper, SiteType.CROSS, new BlockPos(3, 1, 3), 1);
		Placing.Result result = place(helper, "F24", abs(helper, 3, 1, 3), new TestFacts(helper));
		helper.assertTrue(result.pos.closerThan(abs(helper, 3, 1, 3), 2.9), "F24 not by the cross");
		assertSign(helper, result.pos, "F24");
		helper.succeed();
	}

	@GameTest
	public void theLastNoteInTheHouseCopy(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(3, 1, 3, Blocks.CHEST);
		seed(helper, SiteType.HOUSE_COPY, new BlockPos(3, 1, 3), 3);
		Placing.Result result = place(helper, "F27", abs(helper, 3, 1, 3), new TestFacts(helper));
		helper.assertTrue(result.pos.equals(abs(helper, 3, 1, 3)), "F27 not in the copy's chest");
		assertHolds(helper, result.pos, "F27");
		helper.succeed();
	}

	@GameTest
	public void thePictureTenBlocksUnderSpawn(GameTestHelper helper) {
		helper.setBlock(4, 1, 4, Blocks.STONE);
		TestFacts facts = new TestFacts(helper);
		facts.spawn = abs(helper, 4, 11, 4);
		Placing.Result result = place(helper, "F14", facts.spawn, facts);
		helper.assertTrue(result.pos.equals(abs(helper, 4, 1, 4)), "F14 not 10 blocks down: " + result.pos.toShortString());
		assertHolds(helper, result.pos, "F14");
		helper.succeed();
	}

	@GameTest(skyAccess = true)
	public void theWhiteEyesJukeboxIsSilent(GameTestHelper helper) {
		floor(helper);
		Placing.Request request = request(helper, "F11", abs(helper, 3, 1, 3), 1, 3, new TestFacts(helper));
		Placing.Result result = Placers.plain(request, Optional.empty()).orElseThrow(() -> helper.assertionException(Component.literal("F11 was not left")));
		if (!(helper.getLevel().getBlockEntity(result.pos) instanceof JukeboxBlockEntity jukebox)) {
			throw helper.assertionException(Component.literal("no jukebox at " + result.pos.toShortString()));
		}
		helper.assertTrue(jukebox.getTheItem().is(Items.MUSIC_DISC_13) && FragmentItems.is(jukebox.getTheItem(), "F11"), "the jukebox holds disc 13");
		helper.assertFalse(jukebox.getSongPlayer().isPlaying(), "the disc plays by itself");
		assertSign(helper, result.readTargets.getFirst(), "F11");
		helper.succeed();
	}

	/**
	 * F30: one sign on the oldest poplar, its twin on bedrock under the seed pyramid. The grove scan needs chunks
	 * around the test, which load by ticket, so the placement is retried each tick until they are there. Then
	 * nothing can break the twins: not destroying, removing, exploding, pistons, nor TraceService.
	 */
	@GameTest(skyAccess = true, maxTicks = 400)
	public void theTwinSignsCannotBeBroken(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		helper.setBlock(2, 0, 2, Blocks.DIRT);
		for (int y = 1; y <= 4; y++) {
			helper.setBlock(2, y, 2, Blocks.POPLAR_LOG);
		}
		TestFacts facts = new TestFacts(helper);
		facts.grove = GlobalPos.of(level.dimension(), abs(helper, 2, 1, 2));
		facts.placed.put("F07", GlobalPos.of(level.dimension(), abs(helper, 5, 1, 5)));
		Placing.Result[] placed = new Placing.Result[1];
		helper.startSequence().thenWaitUntil(() -> {
			if (placed[0] == null) {
				Placing.Request request = request(helper, "F30", abs(helper, 2, 1, 2), 0, 3, facts);
				placed[0] = Placers.place(request).orElseThrow(() -> helper.assertionException(Component.literal(
						request.loads().waiting() ? "F30 waits for its chunks" : "F30 was not placed")));
			}
		}).thenExecute(() -> {
			BlockPos grove = facts.anchors.get("F30/grove").pos();
			BlockPos bedrock = facts.anchors.get("F30/bedrock").pos();
			try {
				helper.assertTrue(level.getBlockState(grove).getBlock() instanceof WallSignBlock, "the grove sign is not on the poplar");
				assertSign(helper, grove, "F30");
				assertSign(helper, bedrock, "F30");
				helper.assertTrue(level.getBlockState(bedrock.below()).is(Blocks.BEDROCK), "the twin is not on bedrock");
				helper.assertTrue(placed[0].readTargets.containsAll(List.of(grove, bedrock)), "both twins are read targets");
				for (BlockPos sign : List.of(grove, bedrock)) {
					helper.assertTrue(UnbreakableSigns.isProtected(level, sign), "a twin is not protected");
					helper.assertTrue(level.getBlockEntity(sign) instanceof SignBlockEntity entity && entity.isWaxed(), "a twin can be edited");
					helper.assertFalse(level.destroyBlock(sign, true), "destroyBlock broke a twin");
					helper.assertFalse(level.removeBlock(sign, false), "removeBlock broke a twin");
					helper.assertFalse(Services.traces().forced().remove(level, sign, "test:twin"), "TraceService removed a twin");
					helper.assertFalse(PistonBaseBlock.isPushable(level.getBlockState(sign), level, sign, Direction.NORTH, true, Direction.NORTH),
							"a piston could move a twin");
				}
				helper.assertFalse(Services.traces().forced().remove(level, grove.relative(
						level.getBlockState(grove).getValue(WallSignBlock.FACING).getOpposite()), "test:twin"), "TraceService took the log under a twin");
				level.explode(null, grove.getX() + 0.5, grove.getY() + 0.5, grove.getZ() + 1.5, 2.0F, Level.ExplosionInteraction.TNT);
				helper.assertTrue(level.getBlockEntity(grove) instanceof SignBlockEntity entity
						&& entity.getText(SignTextSlot.FRONT).getMessages(false).getFirst().getString().equals("i did, but"), "an explosion broke the grove twin");
			} finally {
				UnbreakableSigns.release(level, grove);
				UnbreakableSigns.release(level, bedrock);
				level.setBlock(bedrock, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
			}
		}).thenSucceed();
	}

	/** The F15 room is built at Y 12 to 20, right above this test's area, then F16, F22 and F29 go in it. */
	@GameTest
	public void theSealedTestRoom(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		BlockPos corner = new BlockPos(origin.getX(), 12, origin.getZ());
		List<BlockPos> volume = new ArrayList<>();
		for (BlockPos pos : BlockPos.betweenClosed(corner.offset(-1, -1, -1), corner.offset(7, 9, 7))) {
			volume.add(pos.immutable());
			level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		try {
			TestFacts facts = new TestFacts(helper);
			Optional<Placing.Result> room = Placers.place(request(helper, "F15", corner.offset(3, 0, 3), 0, 0, facts));
			helper.assertTrue(room.isPresent(), "F15 room was not built");
			Builders.Room built = new Builders.Room(room.get().anchors.get("F15/room"));
			helper.assertTrue(built.corner().equals(corner), "room corner " + built.corner().toShortString());
			assertHolds(helper, built.rulesChest(), "F15");
			helper.assertTrue(level.getBlockState(corner.offset(3, 2, 3)).isAir(), "the room is not hollow");
			helper.assertTrue(level.getBlockState(corner.offset(0, 4, 3)).is(Blocks.SMOOTH_STONE) && level.getBlockState(corner.offset(3, 8, 3)).is(Blocks.SMOOTH_STONE)
					&& level.getBlockState(corner.offset(3, 0, 3)).is(Blocks.SMOOTH_STONE), "the shell is not smooth stone");
			helper.assertTrue(level.getBlockState(corner.offset(3, 4, 1)).is(Blocks.SMOOTH_STONE_SLAB), "no loft");
			for (BlockPos pos : BlockPos.betweenClosed(corner.offset(-1, -1, -1), corner.offset(7, 9, 7))) {
				boolean outside = pos.getX() < corner.getX() || pos.getX() > corner.getX() + 6 || pos.getY() < corner.getY()
						|| pos.getY() > corner.getY() + 8 || pos.getZ() < corner.getZ() || pos.getZ() > corner.getZ() + 6;
				if (outside) {
					helper.assertTrue(level.getBlockState(pos).is(Blocks.STONE), "the room has an opening at " + pos.toShortString());
				}
			}

			facts.placed.put("F15", GlobalPos.of(level.dimension(), built.rulesChest()));
			facts.anchors.put("F15/room", GlobalPos.of(level.dimension(), corner));
			assertHolds(helper, place(helper, "F16", corner, facts).pos, "F16");
			assertHolds(helper, place(helper, "F29", corner, facts).pos, "F29");
			Placing.Result below = place(helper, "F22", corner, facts);
			assertSign(helper, below.pos, "F22");
			helper.assertTrue(level.getBlockEntity(below.pos.below()) instanceof ChestBlockEntity chest && chest.isEmpty(), "F22's chest is not empty");
			helper.assertTrue(below.pos.getY() < corner.getY(), "F22 is not below the room");
		} finally {
			for (BlockPos pos : volume) {
				level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
			}
			BlockState air = Blocks.AIR.defaultBlockState();
			level.setBlock(new Builders.Room(corner).belowChest(), air, Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS);
		}
		helper.succeed();
	}
}
