package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TrapType;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.level.block.Blocks;

/**
 * Game tests of the accident workstream (the trap tests live in {@link TrapGameTests}): the planner's one-per-player
 * and once-per-session rules, which deaths it claims, the cards, and saved data. The death marker and its cross are in
 * {@link CrossGameTests}, the rules with friends in {@link GroupGameTests}.
 */
public class AccidentGameTests extends CrossGameTests {
	@GameTest
	public void realServicesAndCardsAreInstalled(GameTestHelper helper) {
		helper.assertTrue(Services.accidents() instanceof AccidentPlannerImpl, "the real planner is not installed");
		helper.assertTrue(Services.deaths() instanceof DeathMarkerImpl, "the real death marker is not installed");
		helper.assertTrue(Traps.ALL.size() == 21, "13 accidents, 4 lures, the zombie's sword and 3 bridges, got " + Traps.ALL.size());
		for (TrapKind trap : Traps.ALL) {
			helper.assertTrue(trap.blocked() == null || trap.blocked().contains("MobTamper"), trap.id() + " is still blocked: " + trap.blocked());
		}
		for (TrapKind trap : Traps.ALL) {
			EventCard card = CardRegistry.get(Traps.cardId(trap)).orElse(null);
			helper.assertTrue(card != null, "no card for " + trap.id());
			helper.assertTrue(card.tier() == Tier.MAJOR && card.earliestStage() == Stage.PROXIMITY && card.tags().contains(CardTag.ACCIDENT) && !card.hasFake(),
					"card " + card.id() + " is not a MAJOR ACCIDENT from Proximity");
		}
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void plannerArmsOneAtATimeAndOncePerSession(GameTestHelper helper) {
		Yard y = lavaTunnel(helper);
		ServerLevel level = y.level;
		AccidentPlannerImpl planner = y.planner(Yard.NOBODY);
		ServerPlayer player = y.player;

		AccidentPlannerImpl.ArmResult first = planner.arm(player, Traps.LAVA_FLOOR, false);
		helper.assertTrue(first.armed() && first.trap() != null, "lava floor not armed: " + first.message());
		helper.assertTrue(y.data.armed().map(ArmedTrap::type).orElse("").equals("lava_floor"), "the armed trap was not saved");
		helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(10, 4, 8));
		helper.assertTrue(planner.arm(player, Traps.LAVA_WALL, true).status() == AccidentPlannerImpl.Status.BUSY, "two traps armed at once");

		// Which deaths it claims: the right damage, in the place, in the window.
		DamageSources damage = level.damageSources();
		player.snapTo(y.absVec(10.5, 4, 8.5), 0, 0);
		helper.assertTrue(planner.causedBy(player, damage.lava()), "a lava death in the pocket was not claimed");
		helper.assertTrue(planner.causedBy(player, damage.onFire()), "burning to death after the lava was not claimed");
		helper.assertFalse(planner.causedBy(player, damage.fall()), "a fall was claimed by the lava floor");
		helper.assertFalse(planner.causedBy(player, damage.drown()), "drowning was claimed by the lava floor");
		player.snapTo(y.absVec(10.5, 4, 8.5).add(60, 0, 0), 0, 0);
		helper.assertFalse(planner.causedBy(player, damage.lava()), "a lava death far away was claimed");
		player.snapTo(y.absVec(10.5, 4, 8.5), 0, 0);
		ArmedTrap set = y.data.armed().orElseThrow();
		y.data.setArmed(y.owner(), set.withPhase(ArmedTrap.Phase.SET, set.setAt() - 1));
		helper.assertFalse(planner.causedBy(player, damage.lava()), "a death after the window was claimed");
		y.data.setArmed(y.owner(), set);

		planner.disarm(level.getServer(), "test");
		helper.assertTrue(y.data.armed().isEmpty(), "disarm left the trap armed");
		helper.assertFalse(planner.causedBy(player, damage.lava()), "a death with nothing armed was claimed");
		helper.assertTrue(planner.arm(player, Traps.LAVA_WALL, false).status() == AccidentPlannerImpl.Status.SESSION,
				"a second trap was armed in the same session");
		helper.assertFalse(planner.arm(player, new TrapType("no_such_trap")), "an unknown trap was armed");
		y.succeedWithoutDrops();
	}

	/** AccidentPlanner.arm(player, type, center): the spots are looked for around the center, not the player. */
	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void plannerArmsAroundACenter(GameTestHelper helper) {
		Yard y = lavaTunnel(helper);
		AccidentPlannerImpl planner = y.planner(Yard.NOBODY);
		BlockPos tunnel = y.abs(3, 5, 8);
		y.player.snapTo(y.absVec(3.5, 5, 8.5).add(200, 0, 0), 90, 0);
		helper.assertTrue(planner.candidates(y.player, Traps.LAVA_FLOOR).isEmpty(), "a spot was found around a player far from the tunnel");
		helper.assertFalse(planner.candidates(y.player, Traps.LAVA_FLOOR, tunnel).isEmpty(), "no spot around the center");
		helper.assertTrue(planner.arm(y.player, new TrapType("lava_floor"), tunnel), "the trap was not armed around the center");
		helper.assertTrue(y.data.armed().map(t -> t.pos().closerThan(tunnel, 16)).orElse(false), "the trap is not near the center");
		helper.assertBlockNotPresent(Blocks.STONE, new BlockPos(10, 4, 8));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void plannerNeverArmsInView(GameTestHelper helper) {
		Yard y = lavaTunnel(helper);
		AccidentPlannerImpl planner = y.planner(Yard.EVERYONE);
		AccidentPlannerImpl.ArmResult result = planner.arm(y.player, Traps.LAVA_FLOOR, true);
		helper.assertTrue(result.status() == AccidentPlannerImpl.Status.IN_VIEW, "expected IN_VIEW, got " + result.status());
		helper.assertTrue(y.data.armed().isEmpty() && planner.sessionArms(y.owner()) == 0, "a refused arm counted");
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(10, 4, 8));
		AccidentPlannerImpl.ArmResult none = planner.arm(y.player, Traps.BARE_WOOL, true);
		helper.assertTrue(none.status() == AccidentPlannerImpl.Status.NO_SPOT, "bare wool found a spot in a stone tunnel");
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void liveTrapsArmAsAWatchAndExpire(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 4, 20, 9, 12, Blocks.STONE);
		y.fill(1, 3, 8, 18, 4, 8, Blocks.AIR);
		y.fill(6, 6, 6, 10, 7, 10, Blocks.WATER);
		for (int x = 1; x <= 18; x++) {
			y.walk(x, 3, 8, RouteBook.UNDER);
		}
		AccidentPlannerImpl planner = y.planner(Yard.NOBODY);
		y.player.snapTo(y.absVec(2.5, 3, 8.5), 90, 0);
		AccidentPlannerImpl.ArmResult result = planner.arm(y.player, Traps.FLOODED_TUNNEL, false);
		helper.assertTrue(result.armed() && !result.trap().isSet(), "flooded tunnel should arm as a watch");
		helper.assertTrue(result.trap().until() > y.now(), "no watch window");
		for (int x = 6; x <= 10; x++) {
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 5, 8));
		}
		helper.assertFalse(planner.causedBy(y.player, y.level.damageSources().drown()), "a watch that never sprang claimed a death");
		// The watch runs out: the planner drops it.
		y.data.setArmed(y.owner(), result.trap().withPhase(ArmedTrap.Phase.WATCHING, y.now() - 1));
		planner.step(y.level.getServer(), y.data, y.cfg, y.now());
		helper.assertTrue(y.data.armed().isEmpty(), "an expired watch stayed armed");
		helper.succeed();
	}

	@GameTest
	public void causeWords(GameTestHelper helper) {
		DamageSources damage = helper.getLevel().damageSources();
		helper.assertTrue(DeathCauses.word(damage.fall(), null).equals("fell"), "fall");
		helper.assertTrue(DeathCauses.word(damage.lava(), null).equals("lava"), "lava");
		helper.assertTrue(DeathCauses.word(damage.drown(), null).equals("drowned"), "drown");
		helper.assertTrue(DeathCauses.word(damage.inWall(), null).equals("suffocated"), "in wall");
		helper.assertTrue(DeathCauses.word(damage.freeze(), null).equals("froze"), "freeze");
		helper.assertTrue(DeathCauses.word(damage.onFire(), null).equals("burned"), "fire");
		helper.assertTrue(DeathCauses.word(damage.generic(), Traps.DARK_CORNER).equals("dark"), "trap fallback");
		helper.succeed();
	}

	@GameTest
	public void savedDataRoundTrips(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		AccidentData data = new AccidentData();
		UUID alex = UUID.fromString("00000000-0000-0000-0000-0000000a1e01");
		UUID sam = UUID.fromString("00000000-0000-0000-0000-00000005a301");
		BlockPos pos = new BlockPos(10, 64, -20);
		data.routes(alex).record(level.dimension(), pos, 3, RouteBook.UNDER | RouteBook.LADDER, 0, 100, 10);
		data.routes(alex).record(level.dimension(), pos, 4, RouteBook.WATER, 500, 100, 10);
		data.routes(alex).record(level.dimension(), pos.east(), 4, 0, 500, 100, 10);
		data.routes(sam).record(level.dimension(), pos.west(9), 4, 0, 500, 100, 10);
		ArmedTrap corner = new ArmedTrap("dark_corner", level.dimension(), pos, List.of(pos.north()),
				List.of(new ArmedTrap.SavedBlock(pos.north(), Blocks.TORCH.defaultBlockState())), Optional.of(pos.east()),
				List.of(UUID.fromString("00000000-0000-0000-0000-00000000a016")), ArmedTrap.Phase.SET, 5, 6, 700, 22500, pos.offset(-3, -3, -3),
				pos.offset(3, 3, 3), "one torch a block off", 2).withLit(java.util.Set.of(pos, pos.above(), pos.west(3)));
		ArmedTrap floor = new ArmedTrap("lava_floor", level.dimension(), pos.west(60), List.of(pos.west(60)), List.of(), Optional.empty(), List.of(),
				ArmedTrap.Phase.SET, 7, 7, 900, ArmedTrap.NO_CLOCK, pos.west(65), pos.west(55), "a pocket", 0);
		data.setArmed(alex, corner);
		data.setArmed(sam, floor);
		data.addRestoring(corner.withClue("waiting"));
		GlobalPos death = GlobalPos.of(level.dimension(), pos);
		data.addCross(new AccidentData.PendingCross(death, "fell", 4, 1, Optional.of(sam)));
		data.addBuilt(new AccidentData.BuiltCross(death, GlobalPos.of(level.dimension(), pos.east(2)), java.util.Set.of(pos.east(2), pos.east(2).above()),
				Optional.of(alex), true));
		data.setCairn(alex, GlobalPos.of(level.dimension(), pos.south(40)));
		data.addCairnVisit(alex);
		data.log("a line");
		Tag saved = AccidentData.CODEC.encodeStart(ops, data).getOrThrow();
		AccidentData loaded = AccidentData.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(loaded.traps().equals(data.traps()) && List.copyOf(loaded.traps().keySet()).equals(List.of(alex, sam)),
				"armed traps did not round-trip with their players and order: " + loaded.traps());
		helper.assertTrue(loaded.armed().equals(Optional.of(floor)), "the newest trap is not the last armed");
		helper.assertTrue(loaded.restoring().equals(data.restoring()), "the corner waiting for its torches");
		helper.assertTrue(loaded.crosses().equals(data.crosses()) && loaded.built().equals(data.built()), "crosses");
		helper.assertTrue(loaded.crossFor(death).equals(Optional.of(GlobalPos.of(level.dimension(), pos.east(2)))), "a built cross's death");
		helper.assertTrue(loaded.personal(alex).cairnVisits() == 1 && loaded.personal(alex).cairn().equals(data.personal(alex).cairn())
				&& loaded.personal(sam).cairn().isEmpty(), "cairn");
		RouteBook.Point point = loaded.routes(alex).get(level.dimension(), pos);
		helper.assertTrue(point != null && point.passes == 2 && point.firstDay == 3 && point.lastDay == 4 && point.has(RouteBook.WATER) && point.has(RouteBook.LADDER),
				"route point did not round-trip");
		helper.assertTrue(loaded.routes(alex).near(level.dimension(), pos, 2, 10).size() == 2, "route index not rebuilt");
		helper.assertTrue(loaded.routes(sam).size(level.dimension()) == 1 && !loaded.routes(sam).contains(level.dimension(), pos), "routes are not kept apart");
		helper.assertTrue(loaded.history().equals(List.of("a line")) && !loaded.hasLegacy(), "history");
		helper.succeed();
	}

	@GameTest
	public void routeBookKeepsTheMostRecentlyWalked(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RouteBook book = new RouteBook();
		for (int i = 0; i < 12; i++) {
			book.record(level.dimension(), new BlockPos(i, 70, 0), 0, 0, i, 100, 10);
		}
		helper.assertTrue(book.size(level.dimension()) == 10, "cap not kept");
		helper.assertFalse(book.contains(level.dimension(), new BlockPos(0, 70, 0)), "the oldest spot was kept");
		helper.assertTrue(book.contains(level.dimension(), new BlockPos(11, 70, 0)), "the newest spot was dropped");
		book.record(level.dimension(), new BlockPos(5, 70, 0), 1, 0, 50, 100, 10);
		helper.assertTrue(book.get(level.dimension(), new BlockPos(5, 70, 0)).passes == 1, "a quick return counted as a new pass");
		book.record(level.dimension(), new BlockPos(5, 70, 0), 1, 0, 400, 100, 10);
		helper.assertTrue(book.get(level.dimension(), new BlockPos(5, 70, 0)).passes == 2, "a later return was not a new pass");
		List<RouteBook.Spot> near = book.near(level.dimension(), new BlockPos(6, 70, 0), 3, 100);
		helper.assertTrue(near.size() == 7 && near.get(0).pos().equals(new BlockPos(6, 70, 0)), "near() is not nearest first");
		helper.succeed();
	}
}
