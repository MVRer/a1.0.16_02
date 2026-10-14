package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.TraceVeto;
import com.forzacode.a1016_02.core.WorldProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The story clues (D-066, docs/STORY_CLUES.md): M0 (clue ids always on, old saves too), M2 (F08 fixed), M3 (the cairn
 * once the map is read), M9 (the chamber's signs are not near his traces), M11 (the cairn's fallback block), F35's
 * plates and F36's note in the chamber, and the late clues F38 to F44 (their days, their step, never near a base,
 * once). Placement uses forced traces (the test level has no players); a veto stands in for "in view".
 */
public class LoreClueTests extends LoreMultiplayerTests {
	/** 16 x 12 x 16 of air: room for a camp's edge, a grove, a shore. */
	static final String YARD = "a1016_02:ending/d/yard";
	/** 16 x 30 x 16 of air: the bedrock chamber in miniature. */
	static final String SHAFT = "a1016_02:ending/d/shaft";

	static void fill(GameTestHelper helper, int x0, int y0, int z0, int x1, int y1, int z1, Block block) {
		for (BlockPos pos : BlockPos.betweenClosed(x0, y0, z0, x1, y1, z1)) {
			helper.setBlock(pos, block);
		}
	}

	static GlobalPos global(GameTestHelper helper, int x, int y, int z) {
		return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(x, y, z)));
	}

	static List<String> front(GameTestHelper helper, BlockPos abs) {
		if (!(helper.getLevel().getBlockEntity(abs) instanceof SignBlockEntity sign)) {
			throw helper.assertionException(Component.literal("no sign at " + abs.toShortString()));
		}
		return sign.getText(SignTextSlot.FRONT).getMessages(false).stream().map(Component::getString).toList();
	}

	/** Every fragment of this world placed and read except these. */
	static TestFacts facts(GameTestHelper helper, Set<String> read, String... placed) {
		TestFacts facts = new TestFacts(helper);
		facts.read.addAll(read);
		for (String id : placed) {
			facts.placed.put(id, global(helper, 0, 1, 0));
		}
		facts.enabled.addAll(WorldProfile.allFragmentIds());
		return facts;
	}

	// --- M0, M2: the ids past F30, F08, old saves ---

	@GameTest
	public void oldSavesGainF08AndTheClueFragments(GameTestHelper helper) {
		// A v1.0 save: its profile lists F08 only if it rolled it. This one did not.
		RegistryOps<Tag> ops = LoreRoster.ops(helper);
		WorldProfile rolled = WorldProfile.roll(3L, 5L);
		CompoundTag profile = (CompoundTag) WorldProfile.CODEC.encodeStart(ops, rolled).getOrThrow();
		ListTag old = new ListTag();
		rolled.fragments().stream().filter(id -> !id.equals("F08")).forEach(id -> old.add(StringTag.valueOf(id)));
		profile.put("fragments", old);
		CompoundTag tag = new CompoundTag();
		tag.putLong("salt", 5L);
		tag.put("profile", profile);
		HerobrineState state = HerobrineState.CODEC.parse(ops, tag).getOrThrow();
		helper.assertTrue(state.profile().isEnabled("F08"), "the old save did not gain F08");
		List<String> placedNow = new ArrayList<>();
		for (String id : List.of("F08", "F35", "F36", "F38", "F39", "F40", "F41", "F42", "F43", "F44")) {
			helper.assertTrue(state.profile().isEnabled(id), id + " is not enabled in the old save");
			helper.assertTrue(FragmentServiceImpl.placeOnce(state, id, () -> placedNow.add(id)), id + " may not be placed in the old save");
			helper.assertFalse(PlaceNotFound.mayTake(id), "place not found may take " + id + "'s place");
		}
		helper.assertTrue(placedNow.size() == 10, "placed " + placedNow);
		// Each one once: once recorded, the placer is never asked again.
		state.setFragmentPlaced("F42", global(helper, 1, 1, 1));
		helper.assertTrue(FragmentServiceImpl.placeOnce(state, "F42", () -> {
			throw helper.assertionException(Component.literal("F42 was placed twice"));
		}), "a placed clue reads as not placed");
		helper.succeed();
	}

	// --- M3: the cairn once the map is read ---

	@GameTest
	public void theCairnAppearsOnceTheMapIsReadEvenInProximity(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(1, 1, 1, Blocks.CRAFTING_TABLE);
		BlockPos core = new BlockPos(5, 1, 5);
		for (BlockPos pos : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 1, 1))) {
			if (!pos.equals(core)) {
				helper.setBlock(pos, Blocks.SANDSTONE);
			}
		}
		seed(helper, SiteType.OCEAN_PYRAMID, core, 2);
		// Nobody named him: the story is in Proximity. Without the map read, no cairn.
		TestFacts facts = new TestFacts(helper);
		facts.enabled.add("F13");
		facts.firstBlocks = new HerobrineState.FirstBlocks(null, new PlacedBlock(global(helper, 1, 1, 1), Blocks.CRAFTING_TABLE.defaultBlockState()), null);
		helper.assertTrue(pass(helper, Stage.PROXIMITY, facts, abs(helper, 5, 1, 5), "F13").isEmpty(), "the cairn came in Proximity without the map");
		helper.assertTrue(FragmentEngine.eligible(fragment("F13"), Stage.TELLING, facts), "the cairn no longer comes in Stage 3");
		facts.read.add("F28");
		helper.assertTrue(pass(helper, Stage.PROXIMITY, facts, abs(helper, 5, 1, 5), "F13").equals(List.of("F13")), "the cairn did not come once the map was read");
		helper.assertBlockPresent(Blocks.CRAFTING_TABLE, core);
		helper.succeed();
	}

	// --- M11: the cairn's fallback block ---

	@GameTest
	public void theCairnTakesTheOldestBlockStillStandingWhenTheFirstOnesAreGone(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer builder = mock(helper);
		BlockPos core = new BlockPos(5, 1, 5);
		for (BlockPos pos : BlockPos.betweenClosed(core.offset(-1, 0, -1), core.offset(1, 1, 1))) {
			if (!pos.equals(core)) {
				helper.setBlock(pos, Blocks.SANDSTONE);
			}
		}
		seed(helper, SiteType.OCEAN_PYRAMID, core, 2);
		// The first table, chest and block were all picked up again: nothing stands where they were.
		TestFacts facts = new TestFacts(helper);
		facts.base = abs(helper, 2, 1, 2);
		facts.firstBlocks = new HerobrineState.FirstBlocks(new PlacedBlock(global(helper, 1, 1, 3), Blocks.DIRT.defaultBlockState()),
				new PlacedBlock(global(helper, 2, 1, 3), Blocks.CRAFTING_TABLE.defaultBlockState()),
				new PlacedBlock(global(helper, 3, 1, 3), Blocks.CHEST.defaultBlockState()));
		// The owner's log, oldest first: the oldest is gone too, the second and third still stand.
		PlacedBlock gone = new PlacedBlock(global(helper, 1, 1, 1), Blocks.OAK_PLANKS.defaultBlockState());
		PlacedBlock oldest = new PlacedBlock(global(helper, 2, 1, 1), Blocks.OAK_PLANKS.defaultBlockState());
		PlacedBlock newer = new PlacedBlock(global(helper, 1, 1, 2), Blocks.COBBLESTONE.defaultBlockState());
		helper.setBlock(2, 1, 1, oldest.state());
		helper.setBlock(1, 1, 2, newer.state());
		for (PlacedBlock block : List.of(oldest, newer)) {
			Services.watch().onPlaced(builder, level, block.pos().pos(), block.state());
		}
		facts.ownerPlacements.addAll(List.of(gone, oldest, newer));
		LoreConfig config = LoreConfig.get();
		int radius = config.cairnFallbackRadius;
		// "At their base": within a few blocks of the owner's base here, so only this test's own area is read.
		config.cairnFallbackRadius = 2;
		try {
			Placing.Request request = request(helper, "F13", abs(helper, 5, 1, 5), 0, 3, facts);
			// The choice is the oldest still standing, and it is stored.
			helper.assertTrue(Placers.firstBlock(request).equals(Optional.of(oldest)) && oldest.equals(facts.cairnFallback), "the fallback is "
					+ facts.cairnFallback);
			// It never flips: with the log read in another order, the stored one still stands and is kept.
			facts.ownerPlacements.clear();
			facts.ownerPlacements.addAll(List.of(newer, oldest));
			helper.assertTrue(Placers.firstBlock(request).equals(Optional.of(oldest)), "the stored fallback flipped");
			Placing.Result result = place(helper, "F13", abs(helper, 5, 1, 5), facts);
			helper.assertBlockPresent(Blocks.OAK_PLANKS, core);
			helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, 1, 1));
			helper.assertTrue(result.anchors.get("F13/from").equals(oldest.pos().pos()), "the cairn does not remember where its block came from");
			// A save from before the log: the player-placed block nearest the base.
			TestFacts before = new TestFacts(helper);
			before.base = facts.base;
			before.firstBlocks = facts.firstBlocks;
			helper.assertTrue(Placers.firstBlock(request(helper, "F13", abs(helper, 5, 1, 5), 0, 3, before)).map(PlacedBlock::pos)
					.equals(Optional.of(newer.pos())), "an old save found no fallback");
		} finally {
			config.cairnFallbackRadius = radius;
		}
		helper.succeed();
	}

	/** The story's first blocks belong to someone who placed a block (core's ownerFor with a predicate). */
	@GameTest
	public void theStorysOwnerHasPlacedABlock(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		LoreRoster.Known ann = new LoreRoster.Known("Ann", 9_000);
		LoreRoster.Known ben = new LoreRoster.Known("Ben", 10).withFirsts(new HerobrineState.FirstBlocks(
				new PlacedBlock(global(helper, 1, 1, 1), Blocks.DIRT.defaultBlockState()), null, null));
		LoreRoster roster = new LoreRoster(LoreRoster.state(helper, ann, ben));
		roster.join(roster.player(helper, ann, new Vec3(1.5, 1, 1.5)));
		roster.join(roster.player(helper, ben, new Vec3(2.5, 1, 1.5)));
		// Ann has played far longer, but never placed a block: Ben owns the story's first blocks, and keeps them.
		helper.assertTrue(roster.homes(server).owner().equals(Optional.of(ben.uuid())), "the owner has no first block");
		helper.assertTrue(roster.state.owner(Homes.FIRST_BLOCKS).equals(Optional.of(ben.uuid())), "the choice was not stored");
		helper.succeed();
	}

	// --- M9: the chamber's signs ---

	@GameTest
	public void signsInTheChamberAreNotNearHisTraces(GameTestHelper helper) {
		ServerPlayer player = mock(helper);
		HerobrineState state = new HerobrineState();
		TellingData data = new TellingData();
		LoreData lore = new LoreData();
		TeamChamber.record(lore, helper.getLevel().dimension(), BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)),
				helper.absolutePos(new BlockPos(4, 4, 4))));
		// His traces are everywhere here (the stair above lost blocks to him).
		Telling.Traces traces = Telling.outsideChamber((pos, radius) -> true, pos -> TeamChamber.contains(lore, pos));
		Telling.Told sentence = sign(helper, new BlockPos(2, 1, 2), player, "he is no\nlonger\nwith us", state, data, traces);
		helper.assertFalse(sentence.told() || sentence.nearTraces(), "the true sentence in the chamber was telling");
		helper.assertTrue(state.tellingCount() == 0, "the sentence counted as telling");
		Telling.Told named = sign(helper, new BlockPos(3, 1, 2), player, "herobrine", state, data, traces);
		helper.assertTrue(named.told() && named.namesHim() && !named.nearTraces(), "naming him in the chamber did not count as naming");
		Telling.Told outside = sign(helper, new BlockPos(6, 1, 6), player, "hello", state, data, traces);
		helper.assertTrue(outside.told() && outside.nearTraces(), "a sign outside the chamber, near his traces, did not tell");
		helper.succeed();
	}

	// --- F35 the plates, F36 the note ---

	/** The chamber in miniature: bedrock, stone walls, a 7x7 room 4 high, F30's twin in the middle on bedrock. */
	static BoundingBox chamber(GameTestHelper helper, TestFacts facts) {
		fill(helper, 3, 0, 3, 13, 1, 13, Blocks.BEDROCK);
		fill(helper, 3, 2, 3, 13, 6, 13, Blocks.STONE);
		fill(helper, 5, 2, 5, 11, 5, 11, Blocks.AIR);
		helper.setBlock(8, 2, 8, Blocks.OAK_SIGN.defaultBlockState().setValue(StandingSignBlock.ROTATION, 0));
		BoundingBox box = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(5, 0, 5)), helper.absolutePos(new BlockPos(11, 5, 11)));
		LoreData scratch = new LoreData();
		TeamChamber.record(scratch, helper.getLevel().dimension(), box);
		facts.anchors.put(TeamChamber.MIN, scratch.anchor(TeamChamber.MIN).orElseThrow());
		facts.anchors.put(TeamChamber.MAX, scratch.anchor(TeamChamber.MAX).orElseThrow());
		facts.anchors.put("F30/bedrock", global(helper, 8, 2, 8));
		return box;
	}

	@GameTest(structure = SHAFT)
	public void thePlatesAndTheNoteGoInTheChamberOnceItIsBuilt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestFacts facts = facts(helper, Set.of(), "F30");
		// Before the stair is finished, lore knows no chamber: nothing is placed.
		helper.assertTrue(Placers.place(request(helper, "F35", abs(helper, 8, 2, 8), 0, 4, facts)).isEmpty(), "plates without a chamber");
		BoundingBox box = chamber(helper, facts);

		// In view (a veto stands in for it): nothing at all changes, and it can be tried again.
		TraceVeto inView = (vetoLevel, pos) -> box.isInside(pos);
		Services.traces().addVeto(inView);
		try {
			helper.assertTrue(Placers.place(request(helper, "F35", abs(helper, 8, 2, 8), 0, 4, facts)).isEmpty(), "the plates went up in view");
		} finally {
			Services.traces().removeVeto(inView);
		}
		helper.assertBlockPresent(Blocks.AIR, new BlockPos(5, 2, 7));

		Placing.Result plates = place(helper, "F35", abs(helper, 8, 2, 8), 4, facts);
		List<String> names = fragment("F35").plates();
		helper.assertTrue(plates.readTargets.size() == names.size(), "plates to read: " + plates.readTargets.size());
		List<String> seen = new ArrayList<>();
		for (BlockPos sign : plates.readTargets) {
			helper.assertTrue(level.getBlockState(sign).getBlock() instanceof WallSignBlock && box.isInside(sign), "a plate is not on a chamber wall");
			SignBlockEntity entity = (SignBlockEntity) level.getBlockEntity(sign);
			helper.assertTrue(entity.isWaxed(), "a plate is not waxed");
			List<String> lines = front(helper, sign);
			helper.assertTrue(lines.get(0).isEmpty() && lines.get(2).isEmpty() && lines.get(3).isEmpty(), "a plate has more than its name: " + lines);
			seen.add(lines.get(1));
			// Over a single cobblestone ledge on the floor, with nothing on it.
			helper.assertTrue(level.getBlockState(sign.below(2)).is(Blocks.COBBLESTONE) && level.getBlockState(sign.below()).isAir()
					&& level.getBlockState(sign.below(3)).is(Blocks.BEDROCK), "a plate is not over an empty ledge on the floor");
		}
		helper.assertTrue(seen.equals(names) || new java.util.HashSet<>(seen).equals(new java.util.HashSet<>(names)), "the plates read " + seen);
		// The seventh: blank, not waxed, standing on bedrock two blocks from the twin.
		BlockPos blank = plates.pos;
		BlockPos twin = abs(helper, 8, 2, 8);
		helper.assertTrue(level.getBlockState(blank).getBlock() instanceof StandingSignBlock && level.getBlockState(blank.below()).is(Blocks.BEDROCK)
				&& Math.max(Math.abs(blank.getX() - twin.getX()), Math.abs(blank.getZ() - twin.getZ())) == 2, "the blank plate is not beside the twin");
		helper.assertTrue(!((SignBlockEntity) level.getBlockEntity(blank)).isWaxed() && front(helper, blank).stream().allMatch(String::isEmpty),
				"the blank plate is not blank and open");

		// F36 with it: the team's note in a chest in a corner.
		facts.placed.put("F35", GlobalPos.of(level.dimension(), plates.pos));
		helper.assertTrue(FragmentEngine.eligible(fragment("F36"), Stage.PROXIMITY, facts), "the note does not follow the plates");
		Placing.Result note = place(helper, "F36", abs(helper, 8, 2, 8), 4, facts);
		assertHolds(helper, note.pos, "F36");
		helper.assertTrue(note.anchors.get(TeamChamber.CHEST).equals(note.pos) && box.isInside(note.pos), "the note's chest is not the chamber's");
		helper.assertTrue((note.pos.getX() == box.minX() || note.pos.getX() == box.maxX()) && (note.pos.getZ() == box.minZ() || note.pos.getZ() == box.maxZ()),
				"the note is not in a corner");
		// Never twice: once recorded, the engine passes them by.
		facts.placed.put("F36", GlobalPos.of(level.dimension(), note.pos));
		helper.assertTrue(pass(helper, Stage.PROXIMITY, facts, abs(helper, 8, 2, 8), "F35", "F36").isEmpty(), "the plates or the note were placed twice");
		helper.succeed();
	}

	// --- the late clues ---

	@GameTest
	public void lateCluesWaitTheirDaysOnTheirStepOnly(GameTestHelper helper) {
		record Clue(String id, int step, int days) {
		}
		List<Clue> clues = List.of(new Clue("F38", 1, 5), new Clue("F39", 2, 3), new Clue("F40", 3, 5), new Clue("F41", 4, 5), new Clue("F42", 5, 2),
				new Clue("F43", 6, 2), new Clue("F44", 7, 2));
		for (Clue clue : clues) {
			Fragment fragment = fragment(clue.id());
			helper.assertTrue(fragment.requires().stuck().equals(Optional.of(new Fragment.Stuck(clue.step(), clue.days()))), clue.id() + " "
					+ fragment.requires().stuck());
			TestFacts facts = facts(helper, Set.of("F23"), fragment.requires().placed().toArray(String[]::new));
			facts.stuck(clue.step(), clue.days() - 1);
			helper.assertFalse(FragmentEngine.eligible(fragment, Stage.PROXIMITY, facts), clue.id() + " came a day early");
			facts.stuck(clue.step(), clue.days());
			helper.assertTrue(FragmentEngine.eligible(fragment, Stage.PROXIMITY, facts), clue.id() + " did not come after its days");
			facts.stuck(clue.step() % 7 + 1, 99);
			helper.assertFalse(FragmentEngine.eligible(fragment, Stage.PROXIMITY, facts), clue.id() + " came on another step");
		}
		// F38 is for a map nobody has read.
		TestFacts read = facts(helper, Set.of("F23", "F28"), "F28").stuck(1, 30);
		helper.assertFalse(FragmentEngine.eligible(fragment("F38"), Stage.PROXIMITY, read), "the camp's light came after the map was read");

		// The clock: the shared flags Ending D publishes, in in-game days on the world clock.
		HerobrineState state = new HerobrineState();
		helper.assertTrue(LateClues.daysStuck(state, 3, 20) == -1, "stuck with no flags");
		state.setFlag(LateClues.STEP_FLAG + 3, true);
		state.setFlag(LateClues.STEP_DAY_FLAG + 12, true);
		helper.assertTrue(LateClues.daysStuck(state, 3, 17) == 5 && LateClues.daysStuck(state, 2, 17) == -1, "the days stuck from the flags");
		// The days can be set in the config.
		LoreConfig config = new LoreConfig();
		config.lateClueDays.put("F40", 9);
		helper.assertTrue(LateClues.days(fragment("F40"), fragment("F40").requires().stuck().orElseThrow(), config) == 9
				&& LateClues.days(fragment("F39"), fragment("F39").requires().stuck().orElseThrow(), config) == 3, "the config's days");
		helper.succeed();
	}

	@GameTest(structure = YARD, skyAccess = true)
	public void theLightAtTheCampStandsAtItsEdgeNeverNearABase(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		fill(helper, 0, 0, 0, 15, 0, 15, Blocks.GRASS_BLOCK);
		helper.setBlock(8, 1, 8, Blocks.CHEST);
		LoreConfig config = LoreConfig.get();
		int min = config.campLightMinDistance;
		int max = config.campLightMaxDistance;
		config.campLightMinDistance = 3;
		config.campLightMaxDistance = 5;
		try {
			TestFacts facts = facts(helper, Set.of("F23"), "F23");
			facts.placed.put("F28", global(helper, 8, 1, 8));
			facts.stuck(1, 5);
			// A friend's base right there: never near a base.
			facts.bases.add(global(helper, 1, 1, 1));
			helper.assertTrue(Placers.place(request(helper, "F38", abs(helper, 8, 1, 8), 0, 4, facts)).isEmpty(), "the light stood near a base");
			facts.bases.clear();
			facts.bases.add(GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(8, 1, 300))));
			Placing.Result light = place(helper, "F38", abs(helper, 8, 1, 8), 4, facts);
			helper.assertTrue(level.getBlockState(light.pos).is(Blocks.GLOWSTONE) && level.getBlockState(light.pos.below()).is(Blocks.OAK_FENCE)
					&& level.getBlockState(light.pos.below(2)).is(Blocks.GRASS_BLOCK), "the lone light is not glowstone on a fence post on the ground");
			double d = Math.sqrt(Builders.horizontalDistSqr(light.pos, abs(helper, 8, 1, 8)));
			helper.assertTrue(d >= 2.25 && d <= 5.75, "the light is not at the camp's edge: " + d);
		} finally {
			config.campLightMinDistance = min;
			config.campLightMaxDistance = max;
		}
		helper.succeed();
	}

	@GameTest(structure = YARD, skyAccess = true)
	public void cutOneStandsBesideTheOldestPoplar(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		fill(helper, 0, 0, 0, 15, 0, 15, Blocks.GRASS_BLOCK);
		fill(helper, 8, 1, 8, 8, 5, 8, Blocks.POPLAR_LOG);
		// F30's sign hangs on the trunk, facing away from it (whichever way the test is turned).
		BlockPos trunk = abs(helper, 8, 2, 8);
		BlockPos on = abs(helper, 8, 2, 9);
		Direction away = Direction.getApproximateNearest(on.getX() - trunk.getX(), 0, on.getZ() - trunk.getZ());
		level.setBlock(on, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, away), 3);
		TestFacts facts = facts(helper, Set.of(), "F30");
		facts.anchors.put("F30/grove", global(helper, 8, 2, 9));
		facts.stuck(2, 3);
		Placing.Result sign = place(helper, "F39", abs(helper, 8, 1, 8), 4, facts);
		assertSign(helper, sign.pos, "F39");
		double d = Math.sqrt(Builders.horizontalDistSqr(sign.pos, abs(helper, 8, 1, 8)));
		helper.assertTrue(d >= 2.0 && d <= 4.0 && sign.pos.getY() == abs(helper, 0, 1, 0).getY(), "F39 is not 2 to 4 blocks from the poplar: " + d);
		helper.assertTrue(level.getBlockState(sign.pos).getBlock() instanceof StandingSignBlock && sign.readTargets.contains(sign.pos), "F39's sign");
		helper.succeed();
	}

	@GameTest(structure = YARD, skyAccess = true)
	public void theNearestOneStandsOnTheShoreNearestTheCairn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// Dry stone at y 1 either side of a sea channel (x 4 to 12), its ends closed so the water stays.
		fill(helper, 0, 0, 0, 15, 1, 15, Blocks.STONE);
		fill(helper, 4, 1, 1, 12, 1, 14, Blocks.WATER);
		LoreConfig config = LoreConfig.get();
		int radius = config.shoreSearchRadius;
		int min = config.shoreMinDistance;
		config.shoreSearchRadius = 6;
		config.shoreMinDistance = 2;
		try {
			TestFacts facts = facts(helper, Set.of(), "F13");
			facts.placed.put("F13", global(helper, 8, 1, 8));
			facts.stuck(3, 5);
			Placing.Result sign = place(helper, "F40", abs(helper, 8, 1, 8), 4, facts);
			assertSign(helper, sign.pos, "F40");
			helper.assertTrue(sign.pos.equals(abs(helper, 3, 2, 8)) || sign.pos.equals(abs(helper, 13, 2, 8)), "F40 is not on the nearest shore: "
					+ sign.pos.toShortString() + " (cairn at " + abs(helper, 8, 1, 8).toShortString() + ")");
			helper.assertTrue(level.getFluidState(sign.pos).isEmpty(), "F40 stands in the water");
		} finally {
			config.shoreSearchRadius = radius;
			config.shoreMinDistance = min;
		}
		helper.succeed();
	}

	@GameTest(structure = YARD, skyAccess = true)
	public void theLightOnTheSeedRisesJustAboveTheWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		// The seed pyramid's core, its sand over it, then water up to y 5 in a stone well; open air above.
		fill(helper, 7, 0, 7, 9, 5, 9, Blocks.STONE);
		helper.setBlock(8, 1, 8, Blocks.OAK_SIGN);
		helper.setBlock(8, 2, 8, Blocks.SAND);
		helper.setBlock(8, 3, 8, Blocks.SAND);
		helper.setBlock(8, 4, 8, Blocks.WATER);
		helper.setBlock(8, 5, 8, Blocks.WATER);
		TestFacts facts = facts(helper, Set.of(), "F07");
		facts.placed.put("F07", global(helper, 8, 1, 8));
		facts.stuck(4, 5);
		Placing.Result light = place(helper, "F41", abs(helper, 8, 1, 8), 4, facts);
		helper.assertTrue(light.pos.equals(abs(helper, 8, 6, 8)), "the lone block's top is not just above the water: " + helper.relativePos(light.pos));
		for (int y = 4; y <= 6; y++) {
			helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(8, y, 8));
		}
		helper.assertBlockPresent(Blocks.TORCH, new BlockPos(8, 7, 8));
		BlockPos sign = light.readTargets.getFirst();
		helper.assertTrue(level.getBlockState(sign).getBlock() instanceof WallSignBlock && sign.getY() == light.pos.getY(), "the sign is not on the block");
		helper.assertTrue(front(helper, sign).equals(fragment("F41").linesFor(NAME)), "F41 reads " + front(helper, sign));
		helper.succeed();
	}

	@GameTest(structure = SHAFT)
	public void theLateChamberBooksGoInTheChambersChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TestFacts facts = facts(helper, Set.of(), "F30");
		BoundingBox box = chamber(helper, facts);
		Placing.Result plates = place(helper, "F35", abs(helper, 8, 2, 8), 4, facts);
		facts.placed.put("F35", GlobalPos.of(level.dimension(), plates.pos));
		Placing.Result note = place(helper, "F36", abs(helper, 8, 2, 8), 4, facts);
		facts.anchors.put(TeamChamber.CHEST, GlobalPos.of(level.dimension(), note.pos));
		facts.placed.put("F36", GlobalPos.of(level.dimension(), note.pos));
		// A base down there: the late books keep away.
		facts.stuck(5, 2);
		facts.bases.add(GlobalPos.of(level.dimension(), abs(helper, 8, 2, 9)));
		helper.assertTrue(Placers.place(request(helper, "F42", abs(helper, 8, 2, 8), 0, 4, facts)).isEmpty(), "a late book came near a base");
		facts.bases.clear();
		for (String id : List.of("F42", "F43", "F44")) {
			Placing.Result book = place(helper, id, abs(helper, 8, 2, 8), 4, facts);
			helper.assertTrue(book.pos.equals(note.pos), id + " is not in the chamber's chest");
			assertHolds(helper, note.pos, id);
		}
		// The chest broken: the next one in a free corner.
		((Container) level.getBlockEntity(note.pos)).clearContent();
		level.removeBlock(note.pos, false);
		facts.anchors.put(TeamChamber.CHEST, GlobalPos.of(level.dimension(), note.pos));
		Placing.Result again = place(helper, "F44", abs(helper, 8, 2, 8), 4, facts);
		helper.assertTrue(box.isInside(again.pos) && level.getBlockEntity(again.pos) instanceof Container, "no new chest in the chamber");
		helper.succeed();
	}
}
