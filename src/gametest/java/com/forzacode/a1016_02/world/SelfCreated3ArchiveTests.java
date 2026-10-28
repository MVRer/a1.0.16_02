package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.world.sc2.kit.Heights;
import com.forzacode.a1016_02.world.sc2.kit.NoiseVolume;
import com.forzacode.a1016_02.world.sc2.kit.Overlay;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Kind;
import com.forzacode.a1016_02.world.sc2.kit.Sc2Structure;
import com.forzacode.a1016_02.world.sc2.kit.Shape;
import com.forzacode.a1016_02.world.sc2.kit.Snapshot;
import com.forzacode.a1016_02.world.sc2.s17.ArchiveRulesTestKit;
import com.forzacode.a1016_02.world.sc2.s17.ArchiveV3TestKit;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * R3-1's game tests (docs/SELF_CREATED_2_R3.md, "The archive v3: the stacks", R3-1): the bodies that need the archive's
 * package-private parts are in {@code world.sc2.s17.ArchiveV3TestKit}.
 */
public class SelfCreated3ArchiveTests {
	/**
	 * Mariano's report on 1.5.0 ("the library looks like a building coming out of the ground"): over the real noise of four
	 * worlds (three default ones, an amplified one: valleys, cliffs, rivers, crevasses), every archive start the kit makes
	 * round SC2-H's centres (4 a world, {@code -Da1016_02.sc3bury=<n>} for more) and the three SC2-H's survey once flagged,
	 * filled chunk by chunk as worldgen would: no built block of it (cobblestone, shelves, planks, logs) has a face on air or
	 * water the plan did not open (the sky, a cave, a crevasse, a river or a cliff's air; its openings are its insides, its
	 * ways' mouths, the well's crack, the pool), and SC2-H's audit is clean (no fluid in it, no leak, no cave into it, no
	 * part in mid air). The same starts as version 2 are the control (the scan must see). Logs each chunk's fill time.
	 */
	@GameTest(environment = "a1016_02:world/sc2_heights", maxTicks = 36000)
	public void noWallOfItInSight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		int perWorld = Integer.getInteger("a1016_02.sc3bury", 4);
		List<Sc2RealNoise.World> worlds = List.of(Sc2RealNoise.normal(level, SelfCreated2HeightsTests.REAL_SEED), Sc2RealNoise.normal(level, 1L),
				Sc2RealNoise.normal(level, 20261024L),
				Sc2RealNoise.of(level, "amplified", Sc2RealNoise.PRESETS.get("amplified"), SelfCreated2HeightsTests.REAL_SEED, -64, 384));
		// The starts SC2-H's survey flagged before the apron was only filled: {world, chunk x, chunk z}.
		long[][] flagged = {{1L, 214, 146}, {1L, -203, 131}, {20261024L, 392, 36}};
		Sc2Kind kind = Sc2Kind.get("s17");
		List<String> wrong = new ArrayList<>();
		List<Double> chunkMillis = new ArrayList<>();
		List<String> slow = new ArrayList<>();
		List<Double> oldMillis = new ArrayList<>();
		List<String> oldFaults = new ArrayList<>();
		int starts = 0;
		int control = 0;
		try {
			for (Sc2RealNoise.World world : worlds) {
				Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
				Holder<Structure> structure = Sc2RealNoise.structures(level).get("s17");
				StructurePlacement placement = Sc2RealNoise.placement(level, structure);
				int lowest = Heights.lowest(world.height(), world.generator());
				List<ChunkPos> chunks = new ArrayList<>();
				for (long[] f : flagged) {
					if (f[0] == world.seed() && world.name().startsWith("normal")) {
						chunks.add(new ChunkPos((int) f[1], (int) f[2]));
					}
				}
				for (int[] c : SelfCreated2HeightsTests.CENTRES) {
					chunks.addAll(Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, 40));
				}
				int found = 0;
				for (ChunkPos chunk : chunks) {
					Sc2Structure.Sited sited = SelfCreated2HeightsTests.sited(world, structure, kind, chunk, snapshot);
					if (!sited.made()) {
						continue;
					}
					Shape shape = kind.shape(kind.resolve(sited.start()), world.terrain());
					if (!ArchiveV3TestKit.isArchive(shape) || ArchiveV3TestKit.version(shape) < 3) {
						wrong.add("a start of version " + ArchiveV3TestKit.version(shape));
						continue;
					}
					BoundingBox box = shape.box();
					int minY = Math.max(lowest, box.minY() - 8);
					int baseTop = Math.min(world.maxY(), box.maxY() + 8);
					Overlay.Base base = NoiseVolume.base(world.generator(), world.random(), minY, baseTop);
					Overlay overlay = new Overlay(base, true);
					overlay.fill(shape, world.terrain(), level.registryAccess(), lowest, world.maxY(), Optional.empty());
					// Timed: a second fill, warm (the noise read and the code run once), chunk by chunk as worldgen does.
					Overlay timed = new Overlay(base, true);
					for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
						for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
							BoundingBox column = new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15, world.maxY(), (cz << 4) + 15);
							Optional<BoundingBox> clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(column, box);
							if (clip.isEmpty()) {
								continue;
							}
							long t0 = System.nanoTime();
							shape.fill(timed.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
							double ms = (System.nanoTime() - t0) / 1e6;
							chunkMillis.add(ms);
							if (ms > 25) {
								slow.add(String.format(java.util.Locale.ROOT, "%.1f ms chunk %d %d of %s %s", ms, cx, cz, world.name(), ArchiveV3TestKit.part(shape, cx, cz)));
							}
						}
					}
					List<String> exposed = ArchiveV3TestKit.exposed(shape, overlay, world.terrain(), minY, baseTop);
					Sc2Audit.Report audit = Sc2Audit.of(shape, overlay, baseTop, List.of());
					starts++;
					// The control: the same start as version 2 (no jacket): the scan must be able to see what it looks for.
					Shape old = kind.shape(sited.start().withVersion(2), world.terrain());
					BoundingBox oldBox = old.box();
					Overlay.Base oldBase = NoiseVolume.base(world.generator(), world.random(), Math.max(lowest, oldBox.minY() - 8),
							Math.min(world.maxY(), oldBox.maxY() + 8));
					Overlay oldOverlay = new Overlay(oldBase, true);
					oldOverlay.fill(old, world.terrain(), level.registryAccess(), lowest, world.maxY(), Optional.empty());
					Overlay oldTimed = new Overlay(oldBase, true);
					for (int cx = oldBox.minX() >> 4; cx <= oldBox.maxX() >> 4; cx++) {
						for (int cz = oldBox.minZ() >> 4; cz <= oldBox.maxZ() >> 4; cz++) {
							BoundingBox column = new BoundingBox(cx << 4, lowest + 1, cz << 4, (cx << 4) + 15, world.maxY(), (cz << 4) + 15);
							Optional<BoundingBox> clip = com.forzacode.a1016_02.world.sc2.kit.Draw.intersection(column, oldBox);
							if (clip.isEmpty()) {
								continue;
							}
							long t0 = System.nanoTime();
							old.fill(oldTimed.writer(clip.get(), world.terrain(), level.registryAccess()), clip.get());
							oldMillis.add((System.nanoTime() - t0) / 1e6);
						}
					}

					control += ArchiveV3TestKit.exposed(old, oldOverlay, world.terrain(), Math.max(lowest, oldBox.minY() - 8),
							Math.min(world.maxY(), oldBox.maxY() + 8)).size();
					String at = world.name() + " " + world.seed() + " " + chunk + " " + sited.start().origin().toShortString();
					if (!exposed.isEmpty()) {
						wrong.add(at + ": " + exposed.size() + " exposed, first " + exposed.subList(0, Math.min(4, exposed.size())));
					}
					Sc2Audit.Report oldAudit = Sc2Audit.of(old, oldOverlay, Math.min(world.maxY(), oldBox.maxY() + 8), List.of());
					boolean newFinding = audit.fluid() > oldAudit.fluid() || audit.leak() > oldAudit.leak() || audit.cave() > oldAudit.cave()
							|| audit.sky() > 0 || audit.floating() > oldAudit.floating() || !(audit.entrance().equals("ok") || audit.entrance().equals("closed"));
					if (!audit.clean(false) && !newFinding) {
						oldFaults.add("v3 as v2 at " + world.name() + " " + chunk + ": " + audit);
					}
					if (newFinding) {
						String first = audit.where().isEmpty() ? "" : audit.where().getFirst();
						String[] w = first.split(" ");
						String local = w.length == 4 ? ArchiveV3TestKit.where(shape, Integer.parseInt(w[1]), Integer.parseInt(w[2]), Integer.parseInt(w[3])) : "";
						wrong.add(at + ": audit " + audit + " first at " + local);
					}
					if (++found >= perWorld + (int) java.util.Arrays.stream(flagged).filter(f -> f[0] == world.seed()).count()) {
						break;
					}
				}
			}
		} finally {
			Snapshot.install(null);
			Snapshot.refresh(level.getServer());
		}
		chunkMillis.sort(null);
		Sc2RealNoise.log("archive burial: " + starts + " starts scanned, " + wrong.size() + " with a fault; as version 2, " + control
				+ " blocks in sight; a chunk's fill " + (chunkMillis.isEmpty() ? "-" : String.format(java.util.Locale.ROOT, "median %.2f ms, 90%% %.2f, worst %.2f (%d chunks)",
						chunkMillis.get(chunkMillis.size() / 2), chunkMillis.get(chunkMillis.size() * 9 / 10), chunkMillis.getLast(), chunkMillis.size())));
		for (String w : wrong) {
			Sc2RealNoise.log("archive burial: " + w);
		}
		oldMillis.sort(null);
		if (!oldMillis.isEmpty()) {
			Sc2RealNoise.log(String.format(java.util.Locale.ROOT, "archive burial: as version 2 (the same starts, the same machine) a chunk's fill median %.2f ms, 90%% %.2f, worst %.2f (%d chunks); its audit faults: %s",
					oldMillis.get(oldMillis.size() / 2), oldMillis.get(oldMillis.size() * 9 / 10), oldMillis.getLast(), oldMillis.size(), oldFaults));
		}
		for (String w : slow.subList(0, Math.min(20, slow.size()))) {
			Sc2RealNoise.log("archive burial: slow " + w);
		}
		helper.assertTrue(control > 0, "the scan sees nothing even in version 2 (no jacket): it cannot be trusted");
		helper.assertTrue(starts >= 8, "too few starts scanned: " + starts);
		helper.assertTrue(wrong.isEmpty(), "faults: " + wrong);
		helper.succeed();
	}

	/** Dev, when asked ({@code -Da1016_02.sc3why=true}): why the archive's sitings refuse, over three default worlds. */
	@GameTest(environment = "a1016_02:world/sc2_heights", maxTicks = 36000)
	public void whySitingsRefuseWhenAsked(GameTestHelper helper) {
		if (!Boolean.getBoolean("a1016_02.sc3why")) {
			helper.succeed();
			return;
		}
		ServerLevel level = helper.getLevel();
		java.util.Map<String, Integer> why = new java.util.TreeMap<>();
		ArchiveV3TestKit.why(reason -> why.merge(reason, 1, Integer::sum));
		Sc2Kind kind = Sc2Kind.get("s17");
		int made = 0;
		int asked = 0;
		try {
			for (long seed : new long[] {SelfCreated2HeightsTests.REAL_SEED, 1L, 20261024L}) {
				Sc2RealNoise.World world = Sc2RealNoise.normal(level, seed);
				Snapshot snapshot = Sc2RealNoise.snapshot(world.seed());
				Holder<Structure> structure = Sc2RealNoise.structures(level).get("s17");
				StructurePlacement placement = Sc2RealNoise.placement(level, structure);
				for (int[] c : SelfCreated2HeightsTests.CENTRES) {
					for (ChunkPos chunk : Sc2RealNoise.candidates(world, placement, c[0], c[1], 2400, 30)) {
						asked++;
						Sc2Structure.Sited sited = SelfCreated2HeightsTests.sited(world, structure, kind, chunk, snapshot);
						made += sited.made() ? 1 : 0;
						if (!sited.made()) {
							why.merge("kit: " + (sited.why() == null ? "?" : sited.why().split(":")[0]), 1, Integer::sum);
						}
					}
				}
			}
		} finally {
			ArchiveV3TestKit.why(null);
			Snapshot.install(null);
			Snapshot.refresh(level.getServer());
		}
		Sc2RealNoise.log("archive sitings: " + asked + " asked, " + made + " made; refused " + why);
		helper.succeed();
	}

	/** Version 3's shape: the hall, the decks and rails, the floating grid, the well, its pool, lights, crack, the walk. */
	@GameTest(environment = "a1016_02:world/s17", maxTicks = 2400)
	public void shapeOfTheStacksAndTheWell(GameTestHelper helper) {
		ArchiveV3TestKit.shape3(helper);
	}

	/** R12-2: version 2 still builds version 2; version 3's kept numbers give back its well; the small annex. */
	@GameTest(environment = "a1016_02:world/s17")
	public void versionsStayApart(GameTestHelper helper) {
		ArchiveV3TestKit.versions(helper);
	}

	/** Version 3's siting: its depth, a valley over the hall, the deep dark at the well, a world from 0 to 255. */
	@GameTest(environment = "a1016_02:world/s17")
	public void sitingOfTheStacks(GameTestHelper helper) {
		ArchiveV3TestKit.siting3(helper);
	}

	/** Version 3: worldgen builds exactly what the gallery builds, the six's written records included. */
	@GameTest(environment = "a1016_02:world/s17", maxTicks = 2400)
	public void worldgenEqualsLiveForTheStacks(GameTestHelper helper) {
		ArchiveV3TestKit.worldgenEqualsLive3(helper);
	}

	/**
	 * FIX-R3-17 (R3A-1): version 4's well, its top a piece of land lifted 16 to 24 over its own (the land's relief, grass over
	 * dirt to the faces, the faces the world's section with a cave cut in, the lighting fault on all four), and R3A-11's
	 * hall (no full shelf a hand could reach); version 3's checks of the hall, the well, its crack and the walk too.
	 */
	@GameTest(environment = "a1016_02:world/s17", maxTicks = 2400)
	public void shapeOfTheLiftedWell(GameTestHelper helper) {
		com.forzacode.a1016_02.world.sc2.s17.ArchiveV3TestKit.shape(helper, 4);
	}

	/** FIX-R3-17: version 4's worldgen equals the gallery's, block for block and block entity for block entity. */
	@GameTest(environment = "a1016_02:world/s17", maxTicks = 2400)
	public void worldgenEqualsLiveForTheLiftedWell(GameTestHelper helper) {
		com.forzacode.a1016_02.world.sc2.s17.ArchiveV3TestKit.worldgenEqualsLive(helper, 4);
	}

	/**
	 * FIX-R3-17 (R3-1's open item): version 3's apron over a front that falls away ahead and beside: its kerbs keep anyone
	 * walking out of the crack from a drop of more than 3 (D-097.7, read with {@link Edges}).
	 */
	@GameTest(environment = "a1016_02:world/s17", maxTicks = 2400)
	public void theApronsKerbsGuardAFallingFront(GameTestHelper helper) {
		frontIsFair(helper, 3);
	}

	/**
	 * FIX-R3-17 (R3A-1): version 4's talus over the same front: scree sloping one a block from the crack's sill down to the
	 * land, a kerb where the slope meets land more than 3 lower; no drop of more than 3 from anywhere a walk from the crack
	 * reaches.
	 */
	@GameTest(environment = "a1016_02:world/s17", maxTicks = 2400)
	public void theTalusGuardsAFallingFront(GameTestHelper helper) {
		frontIsFair(helper, 4);
	}

	private static void frontIsFair(GameTestHelper helper, int version) {
		com.forzacode.a1016_02.world.sc2.s17.ArchiveV3TestKit.Front front = com.forzacode.a1016_02.world.sc2.s17.ArchiveV3TestKit.front(helper, version);
		helper.assertTrue(front.version() == version, "built version " + front.version());
		List<String> drops = Edges.unguardedDrops(front.level(), front.area(), front.seeds(), front.own());
		helper.assertTrue(drops.isEmpty(), "unguarded drops before the crack (version " + version + "): " + drops);
		helper.assertTrue(front.kerbs() > 0, "no kerb over a front falling " + com.forzacode.a1016_02.world.sc2.s17.ArchiveV3TestKit.CLIFF);
		helper.assertTrue(version < 4 || front.scree() > 20, "no scree before the crack: " + front.scree());
		helper.succeed();
	}

	// --- the shape (R3-1's shape work adds its tests above this line) ---

	// --- the rules: 17a with the books, 17b on the grid, 17f, 17g, the day books, the debug (added below this line) ---

	@GameTest(maxTicks = 400)
	public void rulesRecordsAreTheBooksForEveryKnownPlayerNeverTwice(GameTestHelper helper) {
		ArchiveRulesTestKit.recordsAreTheBooks(helper);
	}

	@GameTest(maxTicks = 400)
	public void rulesRecordRewrittenAfterTheWallOnceAVisitTheOldOneAnEdition(GameTestHelper helper) {
		ArchiveRulesTestKit.recordRewritten(helper);
	}

	@GameTest(maxTicks = 400)
	public void rulesTwoPlayersTwoRecordsTheirOwnEditionsAFriendNamed(GameTestHelper helper) {
		ArchiveRulesTestKit.twoPlayersRecords(helper);
	}

	@GameTest(maxTicks = 400)
	public void rulesDayBooksNearTheirGalleryPastWrittenTodayUnfinishedFutureBlank(GameTestHelper helper) {
		ArchiveRulesTestKit.dayBooksInTheWell(helper);
	}

	@GameTest(maxTicks = 400)
	public void rulesWellGoesDarkFromTheTopDownNeverNearThePlayerGlimpseAfterThree(GameTestHelper helper) {
		ArchiveRulesTestKit.wellGoesDarkFromAbove(helper);
	}

	@GameTest(maxTicks = 400)
	public void rulesWellDarkStopsTwoOverTheHigherOfTwoPlayers(GameTestHelper helper) {
		ArchiveRulesTestKit.wellDarkBetweenFriends(helper);
	}

	@GameTest(maxTicks = 400)
	public void rulesLightsOutTakesTheFloatingGridRowByRow(GameTestHelper helper) {
		ArchiveRulesTestKit.lightsOutOnTheFloatingGrid(helper);
	}

	@GameTest(maxTicks = 400)
	public void rulesAsleepOrCreativeNothingOfTheArchiveComes(GameTestHelper helper) {
		ArchiveRulesTestKit.asleepAndCreativeRefuseAll(helper);
	}
}
