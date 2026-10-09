package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TrapType;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.level.block.Blocks;

/**
 * Game tests of the accident workstream (the trap tests live in {@link TrapGameTests}): the planner's one-at-a-time
 * and once-per-session rules, which deaths it claims, the death marker and its cross, the cards, and saved data.
 */
public class AccidentGameTests extends TrapGameTests {
	private static final AtomicInteger MARKED_EVENTS = new AtomicInteger();

	static {
		HerobrineEvents.MARKED_DEATH.register((player, cause, pos) -> MARKED_EVENTS.incrementAndGet());
	}

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
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.NOBODY);
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
		y.data.setArmed(set.withPhase(ArmedTrap.Phase.SET, set.setAt() - 1));
		helper.assertFalse(planner.causedBy(player, damage.lava()), "a death after the window was claimed");
		y.data.setArmed(set);

		planner.disarm(level.getServer(), "test");
		helper.assertTrue(y.data.armed().isEmpty(), "disarm left the trap armed");
		helper.assertFalse(planner.causedBy(player, damage.lava()), "a death with nothing armed was claimed");
		helper.assertTrue(planner.arm(player, Traps.LAVA_WALL, false).status() == AccidentPlannerImpl.Status.SESSION,
				"a second trap was armed in the same session");
		helper.assertFalse(planner.arm(player, new TrapType("no_such_trap")), "an unknown trap was armed");
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void plannerNeverArmsInView(GameTestHelper helper) {
		Yard y = lavaTunnel(helper);
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.EVERYONE);
		AccidentPlannerImpl.ArmResult result = planner.arm(y.player, Traps.LAVA_FLOOR, true);
		helper.assertTrue(result.status() == AccidentPlannerImpl.Status.IN_VIEW, "expected IN_VIEW, got " + result.status());
		helper.assertTrue(y.data.armed().isEmpty() && planner.sessionArms() == 0, "a refused arm counted");
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
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.NOBODY);
		y.player.snapTo(y.absVec(2.5, 3, 8.5), 90, 0);
		AccidentPlannerImpl.ArmResult result = planner.arm(y.player, Traps.FLOODED_TUNNEL, false);
		helper.assertTrue(result.armed() && !result.trap().isSet(), "flooded tunnel should arm as a watch");
		helper.assertTrue(result.trap().until() > y.now(), "no watch window");
		for (int x = 6; x <= 10; x++) {
			helper.assertBlockPresent(Blocks.STONE, new BlockPos(x, 5, 8));
		}
		helper.assertFalse(planner.causedBy(y.player, y.level.damageSources().drown()), "a watch that never sprang claimed a death");
		// The watch runs out: the planner drops it.
		y.data.setArmed(result.trap().withPhase(ArmedTrap.Phase.WATCHING, y.now() - 1));
		planner.step(y.level.getServer(), y.data, null, y.cfg, y.now());
		helper.assertTrue(y.data.armed().isEmpty(), "an expired watch stayed armed");
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void markedDeathLeavesACrossBuiltFromTheGround(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		y.fill(0, 1, 0, 23, 1, 23, Blocks.DIRT);
		BlockPos death = y.abs(12, 2, 12);
		DeathMarkerImpl watched = new DeathMarkerImpl(server -> y.data, Yard.EVERYONE);
		DeathMarkerImpl unseen = new DeathMarkerImpl(server -> y.data, Yard.NOBODY);
		int deaths = HerobrineState.get(level.getServer()).markedDeaths().size();
		int events = MARKED_EVENTS.get();

		watched.mark(y.player, "fell", death);
		List<MarkedDeath> marked = HerobrineState.get(level.getServer()).markedDeaths();
		helper.assertTrue(marked.size() == deaths + 1, "the death was not recorded");
		MarkedDeath last = marked.get(marked.size() - 1);
		helper.assertTrue(last.cause().equals("fell") && last.pos().pos().equals(death) && last.pos().dimension().equals(level.dimension()), "wrong record " + last);
		helper.assertTrue(MARKED_EVENTS.get() > events, "MARKED_DEATH did not fire");
		helper.assertTrue(y.data.crosses().size() == 1 && dirtAbove(y) == 0, "the cross went up in view");

		unseen.tick(level.getServer());
		helper.assertTrue(y.data.crosses().isEmpty(), "the cross was not built out of view");
		int raised = dirtAbove(y);
		int height = raised - 2;
		helper.assertTrue(height >= y.cfg.crossMinHeight && height <= y.cfg.crossMaxHeight, "cross of " + raised + " blocks; " + y.data.history()
				+ " column " + List.of(level.getBlockState(death), level.getBlockState(death.above()), level.getBlockState(death.below())));
		helper.assertTrue(holes(y) == raised, "the cross is not made of blocks taken from the ground: " + holes(y) + " holes, " + raised + " blocks");
		helper.assertTrue(isCross(y, height), "the blocks do not stand as a cross");
		y.succeedWithoutDrops();
	}

	/** Dirt standing above the ground (y >= 2). */
	private static int dirtAbove(Yard y) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 2, 0), y.abs(23, 8, 23))) {
			n += y.level.getBlockState(pos).is(Blocks.DIRT) ? 1 : 0;
		}
		return n;
	}

	/** Missing dirt in the ground layer. */
	private static int holes(Yard y) {
		int n = 0;
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 1, 0), y.abs(23, 1, 23))) {
			n += y.level.getBlockState(pos).isAir() ? 1 : 0;
		}
		return n;
	}

	/** One column of {@code height} with one arm each side just under its top, and nothing else. */
	private static boolean isCross(Yard y, int height) {
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 2, 0), y.abs(23, 2, 23))) {
			if (!y.level.getBlockState(pos).is(Blocks.DIRT)) {
				continue;
			}
			BlockPos base = pos.immutable();
			for (net.minecraft.core.Direction.Axis axis : new net.minecraft.core.Direction.Axis[] {net.minecraft.core.Direction.Axis.X, net.minecraft.core.Direction.Axis.Z}) {
				if (CrossBuilder.cells(base, height, axis).stream().allMatch(c -> y.level.getBlockState(c).is(Blocks.DIRT))) {
					return true;
				}
			}
		}
		return false;
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
		BlockPos pos = new BlockPos(10, 64, -20);
		data.routes().record(level.dimension(), pos, 3, RouteBook.UNDER | RouteBook.LADDER, 0, 100, 10);
		data.routes().record(level.dimension(), pos, 4, RouteBook.WATER, 500, 100, 10);
		data.routes().record(level.dimension(), pos.east(), 4, 0, 500, 100, 10);
		data.setArmed(new ArmedTrap("dark_corner", level.dimension(), pos, List.of(pos.north()),
				List.of(new ArmedTrap.SavedBlock(pos.north(), Blocks.TORCH.defaultBlockState())), Optional.of(pos.east()),
				List.of(java.util.UUID.fromString("00000000-0000-0000-0000-00000000a016")), ArmedTrap.Phase.SET, 5, 6, 700, 22500, pos.offset(-3, -3, -3),
				pos.offset(3, 3, 3), "one torch a block off", 2).withLit(java.util.Set.of(pos, pos.above(), pos.west(3))));
		data.addCross(new AccidentData.PendingCross(net.minecraft.core.GlobalPos.of(level.dimension(), pos), "fell", 4, 1));
		data.setCairn(net.minecraft.core.GlobalPos.of(level.dimension(), pos.south(40)));
		data.addCairnVisit();
		data.log("a line");
		Tag saved = AccidentData.CODEC.encodeStart(ops, data).getOrThrow();
		AccidentData loaded = AccidentData.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(loaded.armed().equals(data.armed()), "armed trap did not round-trip");
		helper.assertTrue(loaded.crosses().equals(data.crosses()) && loaded.cairnVisits() == 1 && loaded.cairn().equals(data.cairn()), "crosses or cairn");
		RouteBook.Point point = loaded.routes().get(level.dimension(), pos);
		helper.assertTrue(point != null && point.passes == 2 && point.firstDay == 3 && point.lastDay == 4 && point.has(RouteBook.WATER) && point.has(RouteBook.LADDER),
				"route point did not round-trip");
		helper.assertTrue(loaded.routes().near(level.dimension(), pos, 2, 10).size() == 2, "route index not rebuilt");
		helper.assertTrue(loaded.history().equals(List.of("a line")), "history");
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
