package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.world.sig.HouseCopier;
import com.forzacode.a1016_02.world.sig.HouseCopyState;
import com.forzacode.a1016_02.world.sig.HouseShell;
import com.forzacode.a1016_02.world.sig.LoneTorch;
import com.forzacode.a1016_02.world.sig.Outcome;
import com.forzacode.a1016_02.world.sig.StillBurning;
import com.forzacode.a1016_02.world.live.NewScarPlacer;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Two-player game tests of the world workstream (D-058 to D-062): whose house the copy copies, once-per-world in one
 * tick, a friend's base, the world-wide new-scar rate, and saves from before multiplayer. Every test has its own
 * players, state and {@link WorldData} ({@link Friends}); none of their players joins the server.
 */
public class WorldMultiplayerTests {
	private static final String BIG = "a1016_02:world/signature";

	/** A closed 3x4 house the {@code builder} placed: cobblestone floor (y 2), plank walls (y 3-4), plank roof (y 5). */
	private static List<BlockPos> house(GameTestHelper helper, ServerPlayer builder) {
		List<BlockPos> placed = new ArrayList<>();
		for (BlockPos rel : BlockPos.betweenClosed(0, 0, 0, 7, 1, 8)) {
			helper.setBlock(rel, Blocks.STONE);
		}
		for (int x = 2; x <= 4; x++) {
			for (int z = 2; z <= 5; z++) {
				placed.add(put(helper, builder, new BlockPos(x, 2, z), Blocks.COBBLESTONE.defaultBlockState()));
				placed.add(put(helper, builder, new BlockPos(x, 5, z), Blocks.SPRUCE_PLANKS.defaultBlockState()));
				for (int y = 3; y <= 4 && (x == 2 || x == 4 || z == 2 || z == 5); y++) {
					placed.add(put(helper, builder, new BlockPos(x, y, z), Blocks.OAK_PLANKS.defaultBlockState()));
				}
			}
		}
		return placed;
	}

	private static BlockPos put(GameTestHelper helper, ServerPlayer builder, BlockPos rel, BlockState state) {
		helper.setBlock(rel, state);
		BlockPos abs = helper.absolutePos(rel);
		Services.watch().onPlaced(builder, helper.getLevel(), abs, state);
		return abs;
	}

	/**
	 * D-061: whose house is chosen once (the participant with the most play ticks at that moment) and never flips: not
	 * when its owner leaves, not when a friend who played far longer is the one online. The friend's card captures the
	 * owner's house (from the owner's own first block, while they are away), never the friend's. D-062.3: a second
	 * start in the same tick (another player's card) is refused, so there is one copy.
	 */
	@GameTest(structure = BIG, maxTicks = 400)
	public void houseCopyOwnerIsChosenOnceAndKeptAfterTheyLeave(GameTestHelper helper) {
		house(helper, (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL));
		BoundingBox walls = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(2, 2, 2)), helper.absolutePos(new BlockPos(4, 5, 5)));
		Friends.Known alex = new Friends.Known("Alex", 100, Friends.at(helper, new BlockPos(3, 2, 3)));
		Friends.Known sam = new Friends.Known("Sam", 50_000, Friends.at(helper, new BlockPos(300, 2, 300)));
		Friends f = new Friends(helper, Friends.state(helper, alex, sam));
		Bases bases = f.bases();

		// Only Alex takes part when the copy first becomes due: Alex is chosen, and stored.
		ServerPlayer a = f.join(alex, new Vec3(20, 2, 20));
		helper.assertTrue(HouseCopier.chooseOwner(bases).equals(Optional.of(alex.uuid())), "Alex was not chosen");
		helper.assertTrue(f.state.owner(HouseCopier.OWNER_KEY).equals(Optional.of(alex.uuid())), "the choice was not stored");
		// Alex leaves, and Sam, who played far longer, is the one online: the choice stays.
		f.leave(a);
		f.join(sam, new Vec3(20, 2, 20));
		helper.assertTrue(HouseCopier.chooseOwner(bases).equals(Optional.of(alex.uuid())), "the owner flipped to Sam");

		// Sam's card starts the copy with Alex away (the house's chunks may need a tick or two to be there).
		Outcome[] second = {null};
		helper.succeedWhen(() -> {
			if (second[0] == null) {
				Outcome first = HouseCopier.start(bases, true);
				helper.assertTrue(first.ok() || first.later(), "the copy was refused: " + first.message());
				helper.assertTrue(first.ok(), "the copy waits: " + first.message());
				// Another player's card in the same tick: refused, still the one copy.
				second[0] = HouseCopier.start(bases, true);
			}
			HouseCopyState copy = f.data.signatures().houseCopy().orElseThrow();
			helper.assertTrue(!second[0].ok(), "a second copy started in the same tick: " + second[0].message());
			helper.assertTrue(copy.owner().equals(Optional.of(alex.uuid())) && HouseCopier.owner(f.state, copy).equals(Optional.of(alex.uuid())),
					"the copy is not Alex's: " + copy.owner());
			helper.assertTrue(copy.realBox().intersects(walls) && walls.isInside(copy.realInside()), "it captured " + copy.realBox() + ", not Alex's house");
			helper.assertTrue(f.state.owner(HouseCopier.OWNER_KEY).equals(Optional.of(alex.uuid())), "the stored owner changed");
		});
	}

	/** {@link #house} captured as Alex's copy, still looking for its site. */
	private static HouseCopyState capturedHouse(GameTestHelper helper, ServerPlayer builder, WorldConfig config, UUID owner) {
		house(helper, builder);
		HouseShell.Capture capture = HouseShell.capture(helper.getLevel(), List.of(helper.absolutePos(new BlockPos(3, 3, 3))), config);
		helper.assertTrue(capture != null, "the house was not captured");
		return HouseCopyState.captured(helper.getLevel().dimension(), capture, 21, 0, owner);
	}

	/**
	 * MP-7: the house copy never grows in an offline friend's yard. Ben's base is a flat yard 300 blocks from Alex's
	 * house, right where the search would put the copy first, and he has been away for three days (his chunks were last
	 * visited then). The search keeps every known base clear: no candidate and no site within
	 * {@code houseCopyBaseClearance} of Ben's base.
	 */
	@GameTest(structure = BIG, maxTicks = 600)
	public void houseCopySiteKeepsAwayFromAnOfflineFriendsBase(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		WorldConfig config = new WorldConfig();
		config.houseCopyMinDistance = 300;
		config.houseCopyMaxDistance = 300;
		Friends.Known alex = new Friends.Known("Alex", 500, Friends.at(helper, new BlockPos(3, 2, 3)));
		Friends.Known ben = new Friends.Known("Ben", 300, null);
		Friends f = new Friends(helper, Friends.state(helper, alex, ben));
		f.join(alex, new Vec3(3, 3, 3));
		HouseCopyState copy = capturedHouse(helper, (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL), config, alex.uuid());
		long today = 50;

		// Where the search would put the copy first with nobody else around: Ben's yard is there.
		HouseCopier.SiteSearch alone = HouseCopier.siteSearch(f.bases(), level, copy, config, RandomSource.create(7), today, (l, chunk) -> -1, 0);
		helper.assertFalse(alone.centers().isEmpty(), "no copy site candidates at all");
		BlockPos first = alone.centers().getFirst();
		BlockPos benBase = new BlockPos(first.getX() + 6, helper.absolutePos(BlockPos.ZERO).getY(), first.getZ() + 4);
		f.data.setBase(ben.uuid(), GlobalPos.of(level.dimension(), benBase));
		ChunkPos benChunk = ChunkPos.containing(benBase);
		NewScarPlacer.VisitLookup visits = (l, chunk) -> Math.abs(chunk.x() - benChunk.x()) <= 1 && Math.abs(chunk.z() - benChunk.z()) <= 1 ? today - 3 : -1;

		HouseCopier.SiteSearch search = HouseCopier.siteSearch(f.bases(), level, copy, config, RandomSource.create(7), today, visits, 0);
		for (BlockPos center : search.centers()) {
			helper.assertTrue(horizontal(center, benBase) >= config.houseCopyBaseClearance, "a copy site candidate at " + center.toShortString() + ", "
					+ Math.round(horizontal(center, benBase)) + " blocks from Ben's base");
		}
		helper.succeedWhen(() -> {
			helper.assertFalse(search.exhausted(), "no copy site among " + search.centers().size() + " candidates");
			BlockPos origin = search.step(level, copy, f.bases().all(level.dimension()), config, level.getServer().getTickCount());
			helper.assertTrue(origin != null, "no copy site picked yet");
			helper.assertTrue(horizontal(origin, benBase) >= config.houseCopyBaseClearance, "the copy goes at " + origin.toShortString() + ", "
					+ Math.round(horizontal(origin, benBase)) + " blocks from Ben's base");
		});
	}

	/**
	 * MP-7: the house copy never stands on what players built, whoever built it and wherever their base is: not on
	 * Ben's flat roof (the roof would be its ground), not on a floor he laid. Natural flat ground next to them is a site.
	 */
	@GameTest(structure = BIG)
	public void houseCopyNeverStandsOnPlayersBlocks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer builder = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		HouseCopyState copy = capturedHouse(helper, builder, new WorldConfig(), UUID.randomUUID());
		for (BlockPos rel : BlockPos.betweenClosed(12, 0, 12, 21, 0, 21)) {
			helper.setBlock(rel, Blocks.STONE);
		}
		for (BlockPos rel : BlockPos.betweenClosed(12, 6, 0, 21, 6, 9)) {
			put(helper, builder, rel.immutable(), Blocks.OAK_PLANKS.defaultBlockState());
		}
		// The test area's barrier ceiling would be the ground there: open it over both spots.
		for (BlockPos rel : BlockPos.betweenClosed(12, 16, 0, 21, 16, 21)) {
			if (helper.getBlockState(rel).is(Blocks.BARRIER)) {
				helper.setBlock(rel, Blocks.AIR);
			}
		}
		BlockPos roof = helper.absolutePos(new BlockPos(16, 6, 4));
		BlockPos ground = helper.absolutePos(new BlockPos(16, 0, 16));
		helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, roof.getX(), roof.getZ()) == roof.getY() + 1
				&& level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.getX(), ground.getZ()) == ground.getY() + 1, "test setup: something above");
		helper.assertTrue(HouseCopier.copyOriginAt(level, copy, ground.getX(), ground.getZ()) != null, "natural flat ground is not a copy site");
		helper.assertTrue(HouseCopier.copyOriginAt(level, copy, roof.getX(), roof.getZ()) == null, "the copy would stand on Ben's flat roof");
		// A cobblestone floor Ben laid on that natural ground is his too.
		for (BlockPos rel : BlockPos.betweenClosed(14, 0, 14, 18, 0, 18)) {
			put(helper, builder, rel.immutable(), Blocks.COBBLESTONE.defaultBlockState());
		}
		helper.assertTrue(HouseCopier.copyOriginAt(level, copy, ground.getX(), ground.getZ()) == null, "the copy would stand on Ben's floor");
		helper.succeed();
	}

	/**
	 * MP-7: the still-burning camp never sits next to an offline friend's base. Ben's base is two chunks from where
	 * the camp's first candidate stands, so his visited chunks (3x3 around him) never touch the camp's own: the
	 * candidates still keep {@code stillBurningBaseClearance} from it.
	 */
	@GameTest
	public void stillBurningCampKeepsAwayFromAnOfflineFriendsBase(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos from = helper.absolutePos(BlockPos.ZERO);
		WorldConfig config = new WorldConfig();
		config.stillBurningMinBlocks = 2000;
		config.stillBurningMaxBlocks = 2000;
		long today = 50;
		Friends.Known alex = new Friends.Known("Alex", 500, GlobalPos.of(level.dimension(), from));
		Friends.Known ben = new Friends.Known("Ben", 300, null);
		Friends f = new Friends(helper, Friends.state(helper, alex, ben));
		f.join(alex, Vec3.ZERO);

		List<StillBurning.Candidate> alone = StillBurning.candidates(level, from, from, f.bases().positionsIn(level), f.bases().all(level.dimension()), false,
				config, RandomSource.create(11), (l, chunk) -> -1);
		helper.assertFalse(alone.isEmpty(), "no camp candidates at all");
		StillBurning.Candidate first = alone.getFirst();
		int east = first.chunks().stream().mapToInt(ChunkPos::x).max().orElseThrow();
		BlockPos benBase = new BlockPos((east + 2) * 16 + 1, from.getY(), first.z());
		helper.assertTrue(horizontal(benBase, new BlockPos(first.x(), 0, first.z())) < config.stillBurningBaseClearance, "test setup: Ben's base is not near");
		f.data.setBase(ben.uuid(), GlobalPos.of(level.dimension(), benBase));
		ChunkPos benChunk = ChunkPos.containing(benBase);
		NewScarPlacer.VisitLookup visits = (l, chunk) -> Math.abs(chunk.x() - benChunk.x()) <= 1 && Math.abs(chunk.z() - benChunk.z()) <= 1 ? today - 3 : -1;

		List<StillBurning.Candidate> found = StillBurning.candidates(level, from, from, f.bases().positionsIn(level), f.bases().all(level.dimension()), false,
				config, RandomSource.create(11), visits);
		helper.assertFalse(found.isEmpty(), "no camp candidates once Ben is known");
		for (StillBurning.Candidate c : found) {
			BlockPos center = new BlockPos(c.x(), 0, c.z());
			helper.assertTrue(horizontal(center, benBase) >= config.stillBurningBaseClearance, "a camp candidate at " + center.toShortString() + ", "
					+ Math.round(horizontal(center, benBase)) + " blocks from Ben's base");
		}
		helper.succeed();
	}

	private static double horizontal(BlockPos a, BlockPos b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return Math.sqrt(dx * dx + dz * dz);
	}

	/** D-033: never near the base, any known player's: an offline friend's first block, or their last base world saw. */
	@GameTest
	public void noLoneTorchNearAFriendsBase(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos home = helper.absolutePos(BlockPos.ZERO);
		Friends.Known alex = new Friends.Known("Alex", 500, GlobalPos.of(level.dimension(), home));
		Friends.Known sam = new Friends.Known("Sam", 300, GlobalPos.of(level.dimension(), home.offset(600, 0, 0)));
		Friends.Known kim = new Friends.Known("Kim", 200, GlobalPos.of(level.dimension(), home.offset(-900, 0, 0)));
		Friends f = new Friends(helper, Friends.state(helper, alex, sam, kim));
		f.join(alex, new Vec3(0, 2, 0));
		// Kim's respawn point, as world saw it while she played, is not her first block.
		BlockPos kimBase = home.offset(0, 0, 700);
		f.data.setBase(kim.uuid(), GlobalPos.of(level.dimension(), kimBase));
		List<BlockPos> bases = f.bases().all(level.dimension());
		helper.assertTrue(bases.size() == 3 && bases.contains(home.offset(600, 0, 0)) && bases.contains(kimBase), "known bases " + bases);

		WorldConfig config = new WorldConfig();
		long today = 50;
		BlockPos nearSam = home.offset(620, -30, 0);
		BlockPos nearKim = home.offset(10, -30, 690);
		BlockPos alone = home.offset(300, -30, 300);
		List<com.forzacode.a1016_02.world.SignatureData.Spot> caves = List.of(spot(level, nearSam, 40), spot(level, nearKim, 40), spot(level, alone, 40));
		// The card fires for Alex, far from both friends' bases: their caves still never get the torch.
		List<LoneTorch.Candidate> found = LoneTorch.candidates(level, caves, List.of(), bases, today, config, (l, chunk) -> -1, RandomSource.create(2));
		helper.assertTrue(found.size() == 1 && found.getFirst().pos().equals(alone), "candidates " + found);
		helper.assertTrue(f.bases().nearAny(level.dimension(), nearSam, config.loneTorchBaseRadius), "a friend's base does not count");
		helper.succeed();
	}

	private static com.forzacode.a1016_02.world.SignatureData.Spot spot(ServerLevel level, BlockPos pos, long day) {
		return new com.forzacode.a1016_02.world.SignatureData.Spot(GlobalPos.of(level.dimension(), pos), day);
	}

	/** World-wide new-scar rate: one real new scar per {@code newScarWorldGapDays} in the whole world, whoever's pacing asks. */
	@GameTest
	public void newScarsKeepAWorldWideRate(GameTestHelper helper) {
		WorldConfig config = new WorldConfig();
		WorldData data = new WorldData();
		helper.assertTrue(NewScarPlacer.rateRefusal(data, 9, config) == null, "a world with no new scar refuses one");
		data.setLastNewScarDay(9);
		helper.assertTrue(NewScarPlacer.rateRefusal(data, 9, config) != null, "a second new scar on the same day (another player's card)");
		helper.assertTrue(NewScarPlacer.rateRefusal(data, 9 + config.newScarWorldGapDays, config) == null, "no new scar after the gap");
		helper.succeed();
	}

	/**
	 * D-062.8: a {@code world.dat} from before multiplayer (no bases, no new-scar day, a house copy that recorded
	 * nobody) loads; the copy is the first known player's, and core's stored choice is set for them only once they
	 * are the one participant online (then core's rule cannot pick anyone else).
	 */
	@GameTest
	public void oldWorldSaveLoadsAsTheFirstPlayers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryOps<Tag> ops = Friends.ops(helper);
		Friends.Known subject = new Friends.Known("Subject", 9000, Friends.at(helper, new BlockPos(1, 2, 1)));
		HerobrineState state = Friends.v1State(helper, subject);
		helper.assertTrue(state.players().size() == 1 && state.players().getFirst().uuid().equals(subject.uuid()), "the v1.0 subject is not known");

		WorldData old = new WorldData();
		old.signatures().setHouseCopy(new HouseCopyState(level.dimension(), helper.absolutePos(new BlockPos(1, 2, 1)), List.of(), List.of(new BlockPos(1, 1, 1)),
				Optional.empty(), -1, List.of(), 0, HouseCopyState.Phase.SITE, 21, Optional.empty()));
		CompoundTag tag = (CompoundTag) WorldData.CODEC.encodeStart(ops, old).getOrThrow();
		tag.remove("bases");
		tag.remove("lastNewScarDay");
		WorldData loaded = WorldData.CODEC.parse(ops, tag).getOrThrow();
		HouseCopyState copy = loaded.signatures().houseCopy().orElseThrow();
		helper.assertTrue(loaded.lastNewScarDay() == Long.MIN_VALUE && loaded.base(subject.uuid()).isEmpty() && copy.owner().isEmpty(), "not an old save");
		helper.assertTrue(HouseCopier.owner(state, copy).equals(Optional.of(subject.uuid())), "the old copy is not the first known player's");

		Friends f = new Friends(helper, state, loaded);
		Bases bases = f.bases();
		helper.assertTrue(bases.all(level.dimension()).equals(List.of(helper.absolutePos(new BlockPos(1, 2, 1)))), "the subject's base is not known");
		helper.assertTrue(HouseCopier.chooseOwner(bases).equals(Optional.of(subject.uuid())) && state.owner(HouseCopier.OWNER_KEY).isEmpty(),
				"choosing changed or stored an owner for an old copy");
		// With a friend online too, core's rule could pick the friend: nothing is stored.
		ServerPlayer friend = f.join(new Friends.Known("Friend", 0, null), new Vec3(4, 2, 4));
		ServerPlayer him = f.join(subject, new Vec3(2, 2, 2));
		helper.assertFalse(HouseCopier.adoptOwner(bases) || state.owner(HouseCopier.OWNER_KEY).isPresent(), "stored with a friend online");
		// The subject alone: stored, exactly them; and never again.
		f.leave(friend);
		helper.assertTrue(HouseCopier.adoptOwner(bases) && state.owner(HouseCopier.OWNER_KEY).equals(Optional.of(subject.uuid())), "not stored");
		helper.assertFalse(HouseCopier.adoptOwner(bases), "stored twice");
		helper.assertTrue(him.getUUID().equals(subject.uuid()), "test setup");
		UUID stored = state.owner(HouseCopier.OWNER_KEY).orElseThrow();
		helper.assertTrue(stored.equals(subject.uuid()), "stored " + stored);
		helper.succeed();
	}
}
