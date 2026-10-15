package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.AccidentPlanner;
import com.forzacode.a1016_02.core.HerobrineEvents;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.TrapType;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.monster.Phantom;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.storage.LevelData;

/**
 * Accidents with friends (D-058 to D-063): one trap per player on their own routes and level, never two on the same
 * ground, a trap killing whoever sets it off with the death theirs (their cross, their cause), the trap staying armed
 * when its player leaves, two deaths in one tick each with its own cross, the bed being the target's own, sessions per
 * player, and a v1.0 single-trap save. Every test uses its own {@link Group}: mock players are never on the test
 * server's player list, so nobody else's tests see them.
 */
public class GroupGameTests extends GroupTrapGameTests {
	/** Whose marked deaths fired {@code MARKED_DEATH}, in order. */
	private static final List<UUID> MARKED = Collections.synchronizedList(new ArrayList<>());

	static {
		HerobrineEvents.MARKED_DEATH.register((player, cause, pos) -> MARKED.add(player.getUUID()));
	}

	private static ServerPlayer mock(GameTestHelper helper) {
		return (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
	}

	/**
	 * Stone with two 2-high tunnels along x at y 5..6: z 4 is Alex's (walked by {@code first}), z 16 is Sam's (walked
	 * by {@code second}), each over a lava pocket at x 10.
	 */
	private static void twoMines(Yard y, ServerPlayer first, ServerPlayer second) {
		y.fill(0, 0, 0, 20, 9, 20, Blocks.STONE);
		y.fill(1, 5, 4, 18, 6, 4, Blocks.AIR);
		y.fill(1, 5, 16, 18, 6, 16, Blocks.AIR);
		y.set(10, 3, 4, Blocks.LAVA);
		y.set(10, 3, 16, Blocks.LAVA);
		for (int x = 1; x <= 18; x++) {
			y.walk(first, x, 5, 4, RouteBook.UNDER, 1, y.today());
			y.walk(second, x, 5, 16, RouteBook.UNDER, 1, y.today());
		}
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void trapsGoOnTheTargetsOwnRoutesAndNeverShareGround(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerPlayer alex = y.player;
		ServerPlayer sam = mock(helper);
		twoMines(y, alex, sam);
		alex.snapTo(y.absVec(3.5, 5, 4.5), 90, 0);
		sam.snapTo(y.absVec(3.5, 5, 4.5), 90, 0);
		AccidentPlannerImpl planner = new Group(helper, alex, sam).planner(y.data, Yard.NOBODY);
		MinecraftServer server = y.level.getServer();

		// Sam stands in Alex's mine: his trap still goes on his own route (his mine), not on Alex's.
		AccidentPlannerImpl.ArmResult his = planner.arm(sam, Traps.LAVA_FLOOR, false);
		helper.assertTrue(his.armed() && his.trap().pos().equals(y.abs(10, 4, 16)), "Sam's trap is not in Sam's mine: " + his.message());
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(10, 4, 4));
		// Alex's only spot is 12 blocks from Sam's trap: two setups on the same ground, refused.
		AccidentPlannerImpl.ArmResult crowded = planner.arm(alex, Traps.LAVA_FLOOR, false);
		helper.assertTrue(crowded.status() == AccidentPlannerImpl.Status.NO_SPOT && crowded.message().contains("another player's trap"),
				"a second trap went on the same ground: " + crowded.status() + " " + crowded.message());
		helper.assertTrue(planner.candidates(alex, Traps.LAVA_FLOOR).isEmpty(), "the card gate still offers the crowded spot");
		helper.assertBlockPresent(Blocks.STONE, new BlockPos(10, 4, 4));
		// Once Sam's is over, Alex gets his own in his own mine.
		planner.disarm(server, sam.getUUID(), "test");
		AccidentPlannerImpl.ArmResult own = planner.arm(alex, Traps.LAVA_FLOOR, false);
		helper.assertTrue(own.armed() && own.trap().pos().equals(y.abs(10, 4, 4)), "Alex's trap is not in Alex's mine: " + own.message());
		helper.assertTrue(planner.armedTraps(server).keySet().equals(Set.of(alex.getUUID())), "traps " + planner.armedTraps(server));
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 100)
	public void twoPlayersInTwoLevelsEachHaveATrapArmedAtOnce(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerPlayer alex = y.player;
		y.fill(0, 0, 4, 20, 9, 12, Blocks.STONE);
		y.fill(1, 5, 8, 18, 6, 8, Blocks.AIR);
		y.set(10, 3, 8, Blocks.LAVA);
		for (int x = 1; x <= 18; x++) {
			y.walk(alex, x, 5, 8, RouteBook.UNDER, 1, y.today());
		}
		alex.snapTo(y.absVec(3.5, 5, 8.5), 90, 0);
		// Sam is in the Nether, in his own mine there (walked by him alone).
		Away nether = new Away(helper, Level.NETHER, 64);
		ServerPlayer sam = nether.player;
		for (BlockPos pos : BlockPos.betweenClosed(nether.at(-1, -3, -3), nether.at(21, 4, 3))) {
			nether.set(pos.immutable(), Blocks.STONE.defaultBlockState());
		}
		for (int x = 1; x <= 18; x++) {
			nether.set(nether.at(x, 1, 0), Blocks.AIR.defaultBlockState());
			nether.set(nether.at(x, 2, 0), Blocks.AIR.defaultBlockState());
			y.data.routes(sam.getUUID()).record(Level.NETHER, nether.at(x, 1, 0), (int) y.today(), RouteBook.UNDER, 0, y.cfg.routePassGapTicks(),
					y.cfg.routeMaxPointsPerDimension);
		}
		nether.set(nether.at(10, -1, 0), Blocks.LAVA.defaultBlockState());
		nether.stand(3.5, 1, 0.5, 90, 0);
		Group group = new Group(helper, alex, sam);
		AccidentPlannerImpl planner = group.planner(y.data, Yard.NOBODY);
		MinecraftServer server = y.level.getServer();
		try {
			AccidentPlannerImpl.ArmResult a = planner.arm(alex, Traps.LAVA_FLOOR, false);
			AccidentPlannerImpl.ArmResult s = planner.arm(sam, Traps.LAVA_FLOOR, false);
			helper.assertTrue(a.armed() && s.armed(), "not both armed: " + a.message() + " / " + s.message());
			helper.assertTrue(a.trap().dimension().equals(Level.OVERWORLD) && a.trap().pos().equals(y.abs(10, 4, 8)), "Alex's trap " + a.trap());
			helper.assertTrue(s.trap().dimension().equals(Level.NETHER) && s.trap().pos().equals(nether.at(10, 0, 0)), "Sam's trap is not in his level: " + s.trap());
			helper.assertTrue(nether.level.getBlockState(nether.at(10, 0, 0)).isAir() && y.state(10, 4, 8).isAir(), "a floor block is still there");
			helper.assertTrue(planner.armedTraps(server).size() == 2 && planner.armedTrap(server, sam.getUUID()).isPresent(), "one trap per player");
			// Each ticks in its own level, and each death counts only in its own place and level.
			planner.step(server, y.data, y.cfg, y.now());
			helper.assertTrue(planner.armedTraps(server).size() == 2, "a step dropped a trap: " + y.data.history());
			DamageSources damage = y.level.damageSources();
			nether.stand(10.5, 0, 0.5, 90, 0);
			helper.assertTrue(planner.claim(sam, damage.lava()).map(c -> c.owner().equals(sam.getUUID())).orElse(false), "Sam's lava death in his mine");
			alex.snapTo(nether.player.position(), 0, 0);
			helper.assertTrue(planner.claim(alex, damage.lava()).isEmpty(), "an overworld player at the Nether trap's coordinates was claimed by it");
		} finally {
			nether.done();
		}
		y.succeedWithoutDrops();
	}

	/** A lava floor trap of {@code owner}'s, armed out of nothing in the dirt yard: a death within 5 blocks of the middle counts. */
	private static ArmedTrap pocket(Yard y, ServerPlayer owner) {
		BlockPos middle = y.abs(12, 2, 12);
		Candidate spot = Candidate.around(middle, List.of(), 5, 3, "test pocket");
		ArmedTrap trap = AccidentPlannerImpl.build(Traps.LAVA_FLOOR, y.ctx(Yard.NOBODY, middle, owner), spot, y.cfg);
		y.data.setArmed(owner.getUUID(), trap);
		return trap;
	}

	private static Yard dirtYard(GameTestHelper helper) {
		Yard y = new Yard(helper);
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		y.fill(0, 1, 0, 23, 1, 23, Blocks.DIRT);
		return y;
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void aFriendWhoDiesInYourTrapGetsTheMarkedDeathAndTheCross(GameTestHelper helper) {
		Yard y = dirtYard(helper);
		ServerPlayer alex = y.player;
		ServerPlayer sam = mock(helper);
		Group group = new Group(helper, alex, sam);
		AccidentPlannerImpl planner = group.planner(y.data, Yard.NOBODY);
		DeathMarkerImpl marker = group.marker(y.data, Yard.NOBODY);
		MinecraftServer server = y.level.getServer();
		pocket(y, alex);
		alex.snapTo(y.absVec(12.5, 2, 30.5), 0, 0);
		sam.snapTo(y.absVec(12.5, 2, 12.5), 0, 0);
		DamageSources damage = y.level.damageSources();

		// causedBy works for a friend: Sam is in Alex's trap.
		helper.assertTrue(planner.causedBy(sam, damage.lava()), "a friend's death in the trap was not claimed");
		helper.assertTrue(planner.claim(sam, damage.lava()).map(c -> c.owner().equals(alex.getUUID()) && !c.spent()).orElse(false), "not Alex's trap");
		helper.assertFalse(planner.causedBy(sam, damage.fall()), "a fall was claimed by the lava trap");
		int fired = MARKED.size();
		planner.onDeath(sam, damage.lava());
		planner.onDeath(sam, damage.lava());

		List<MarkedDeath> his = group.state.markedDeaths(sam.getUUID());
		helper.assertTrue(his.size() == 1 && his.getFirst().cause().equals("lava") && his.getFirst().pos().pos().equals(sam.blockPosition()),
				"the death is not Sam's own, once: " + group.state.markedDeaths());
		helper.assertTrue(group.state.markedDeaths(alex.getUUID()).isEmpty(), "the trap's owner got the death");
		helper.assertTrue(marker.count(server, sam.getUUID()) == 1 && marker.count(server, alex.getUUID()) == 0 && marker.count(server) == 1, "counts");
		helper.assertTrue(MARKED.subList(fired, MARKED.size()).equals(List.of(sam.getUUID())), "MARKED_DEATH did not fire once with Sam");
		helper.assertTrue(y.data.armed(alex.getUUID()).isEmpty() && y.data.traps().isEmpty(), "Alex's trap is not used up");
		GlobalPos death = GlobalPos.of(y.level.dimension(), sam.blockPosition());
		List<AccidentData.BuiltCross> crosses = y.data.built();
		helper.assertTrue(crosses.size() == 1 && crosses.getFirst().player().equals(Optional.of(sam.getUUID())) && crosses.getFirst().marked(),
				"Sam's cross: " + crosses + "; " + y.data.history());
		helper.assertTrue(marker.crossFor(server, death).equals(Optional.of(crosses.getFirst().base())) && marker.lastCrossPos(server).isPresent(), "crossFor");
		helper.assertTrue(y.data.history().stream().anyMatch(line -> line.contains("killed")), "no history line: " + y.data.history());
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 100)
	public void yourTrapStaysArmedWhenYouLeaveAndSpringsForAFriend(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerPlayer alex = y.player;
		ServerPlayer sam = mock(helper);
		// Alex's shaft, with gravel above its cap.
		y.fill(3, 0, 3, 7, 12, 7, Blocks.STONE);
		y.fill(5, 1, 5, 5, 7, 5, Blocks.AIR);
		y.fill(5, 9, 5, 5, 11, 5, Blocks.GRAVEL);
		for (int h = 1; h <= 7; h++) {
			y.walk(alex, 5, h, 5, RouteBook.UNDER, 1, y.today());
		}
		y.face(alex, 5.5, 3, 5.5, 0, 0);
		sam.snapTo(y.absVec(15.5, 1, 15.5), 0, 0);
		Group group = new Group(helper, alex, sam);
		AccidentPlannerImpl planner = group.planner(y.data, Yard.NOBODY);
		MinecraftServer server = y.level.getServer();
		AccidentPlannerImpl.ArmResult watching = planner.arm(alex, Traps.GRAVEL_CEILING, false);
		helper.assertTrue(watching.armed() && !watching.trap().isSet(), "the gravel ceiling is not watching: " + watching.message());

		// Alex leaves (not on the player list: a disconnect, not a spectator). The trap stays, out there in the world.
		group.online.remove(alex);
		planner.onParticipation(server, alex.getUUID(), false);
		helper.assertTrue(y.data.armed(alex.getUUID()).isPresent(), "the trap went with its player");
		helper.assertTrue(y.data.history().getLast().contains("stays armed"), "history " + y.data.history());
		// Sam goes down Alex's shaft: it springs for him (dangers hit whoever acts).
		y.face(sam, 5.5, 1, 5.5, 0, 0);
		planner.step(server, y.data, y.cfg, y.now());
		ArmedTrap sprung = y.data.armed(alex.getUUID()).orElse(null);
		helper.assertTrue(sprung != null && sprung.isSet(), "did not spring for the friend in the shaft: " + y.data.history());
		helper.assertFalse(y.state(5, 8, 5).is(Blocks.STONE), "the cap is still there");
		helper.runAfterDelay(30, () -> {
			planner.onDeath(sam, y.level.damageSources().inWall());
			helper.assertTrue(group.state.markedDeaths(sam.getUUID()).size() == 1 && group.state.markedDeaths(sam.getUUID()).getFirst().cause().equals("suffocated"),
					"Sam's death under Alex's gravel is not his: " + group.state.markedDeaths());
			helper.assertTrue(y.data.traps().isEmpty(), "the trap is not used up");
			helper.succeed();
		});
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void twoMarkedDeathsInOneTickGetTwoCrosses(GameTestHelper helper) {
		Yard y = dirtYard(helper);
		ServerPlayer alex = y.player;
		ServerPlayer sam = mock(helper);
		Group group = new Group(helper, alex, sam);
		AccidentPlannerImpl planner = group.planner(y.data, Yard.NOBODY);
		DeathMarkerImpl marker = group.marker(y.data, Yard.NOBODY);
		MinecraftServer server = y.level.getServer();
		pocket(y, alex);
		alex.snapTo(y.absVec(11.5, 2, 12.5), 0, 0);
		sam.snapTo(y.absVec(12.5, 2, 12.5), 0, 0);
		DamageSources damage = y.level.damageSources();

		// One accident, two deaths in the same tick: the trap is spent by the first and still claims the second.
		planner.onDeath(alex, damage.lava());
		planner.onDeath(sam, damage.lava());
		helper.assertTrue(group.state.markedDeaths(alex.getUUID()).size() == 1 && group.state.markedDeaths(sam.getUUID()).size() == 1,
				"not one marked death each: " + group.state.markedDeaths());
		helper.assertTrue(y.data.traps().isEmpty(), "the trap was not spent");
		List<AccidentData.BuiltCross> crosses = y.data.built();
		helper.assertTrue(crosses.size() == 2, "not two crosses: " + crosses + "; " + y.data.history());
		AccidentData.BuiltCross first = crosses.get(0);
		AccidentData.BuiltCross second = crosses.get(1);
		helper.assertTrue(first.player().equals(Optional.of(alex.getUUID())) && second.player().equals(Optional.of(sam.getUUID())), "whose crosses");
		helper.assertFalse(first.base().equals(second.base()), "two crosses on one spot");
		for (BlockPos a : first.cells()) {
			for (BlockPos b : second.cells()) {
				helper.assertTrue(a.distManhattan(b) > 0 && Math.max(Math.abs(a.getX() - b.getX()), Math.max(Math.abs(a.getY() - b.getY()), Math.abs(a.getZ() - b.getZ()))) > 1,
						"the crosses touch at " + a + " and " + b);
			}
		}
		Set<BlockPos> cells = new HashSet<>(first.cells());
		cells.addAll(second.cells());
		for (BlockPos cell : cells) {
			helper.assertTrue(y.level.getBlockState(cell).is(Blocks.DIRT), "a cross cell is not dirt (one cross robbed the other) at " + cell);
		}
		int raised = 0;
		int holes = 0;
		for (BlockPos pos : BlockPos.betweenClosed(y.abs(0, 1, 0), y.abs(23, 10, 23))) {
			raised += pos.getY() > y.abs(0, 1, 0).getY() && y.level.getBlockState(pos).is(Blocks.DIRT) ? 1 : 0;
			holes += pos.getY() == y.abs(0, 1, 0).getY() && y.level.getBlockState(pos).isAir() ? 1 : 0;
		}
		helper.assertTrue(raised == cells.size() && holes == cells.size(), raised + " raised, " + holes + " holes for " + cells.size() + " cells");
		GlobalPos alexDied = GlobalPos.of(y.level.dimension(), alex.blockPosition());
		GlobalPos samDied = GlobalPos.of(y.level.dimension(), sam.blockPosition());
		helper.assertTrue(marker.crossFor(server, alexDied).equals(Optional.of(first.base())) && marker.crossFor(server, samDied).equals(Optional.of(second.base())),
				"crossFor is not per death");
		helper.assertTrue(marker.lastCrossPos(server).equals(Optional.of(second.base())), "lastCrossPos is not the newest");
		y.succeedWithoutDrops();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void noBedTakesTheTargetsOwnBedAndCountsOnlyForThem(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 14, 0, 14, Blocks.STONE);
		BlockState foot = Blocks.BED.white().defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH).setValue(BedBlock.PART, BedPart.FOOT);
		y.set(3, 1, 3, foot);
		y.set(3, 1, 4, foot.setValue(BedBlock.PART, BedPart.HEAD));
		y.set(9, 1, 3, foot);
		y.set(9, 1, 4, foot.setValue(BedBlock.PART, BedPart.HEAD));
		ServerPlayer alex = y.player;
		ServerPlayer sam = mock(helper);
		alex.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), y.abs(3, 1, 3), 0, 0), false), false);
		sam.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), y.abs(9, 1, 3), 0, 0), false), false);
		for (ServerPlayer who : List.of(alex, sam)) {
			who.getStats().setValue(who, Stats.CUSTOM.get(Stats.TIME_SINCE_REST), 1000);
			who.snapTo(y.absVec(200.5, 1, 6.5), 0, 0);
		}
		Group group = new Group(helper, alex, sam);
		AccidentPlannerImpl planner = group.planner(y.data, Yard.NOBODY);
		MinecraftServer server = level.getServer();

		AccidentPlannerImpl.ArmResult armed = planner.arm(alex, Traps.NO_BED, true);
		helper.assertTrue(armed.armed() && armed.trap().pos().equals(y.abs(3, 1, 3)), "Alex's own bed was not the one taken: " + armed.message());
		helper.assertBlockNotPresent(Blocks.BED.white(), new BlockPos(3, 1, 4));
		helper.assertBlockPresent(Blocks.BED.white(), new BlockPos(9, 1, 3));
		helper.assertBlockPresent(Blocks.BED.white(), new BlockPos(9, 1, 4));
		// Sam's bed is 6 blocks from Alex's taken one: no second trap on that ground.
		helper.assertTrue(planner.arm(sam, Traps.NO_BED, true).status() == AccidentPlannerImpl.Status.NO_SPOT, "a second bed went from the same room");

		// A phantom of Alex's sleepless nights: it counts for Alex only (the trap is personal), not for Sam beside him.
		alex.snapTo(y.absVec(8.5, 1, 8.5), 0, 0);
		sam.snapTo(y.absVec(9.5, 1, 8.5), 0, 0);
		Phantom phantom = helper.spawn(EntityTypes.PHANTOM, new BlockPos(8, 6, 8));
		planner.onSpawned(level, phantom);
		DamageSources damage = level.damageSources();
		helper.assertTrue(planner.causedBy(alex, damage.mobAttack(phantom)), "the phantom of Alex's sleepless nights did not count for Alex");
		helper.assertFalse(planner.causedBy(sam, damage.mobAttack(phantom)), "Alex's bed trap claimed Sam's death");
		// Alex leaves: it stays (his bed is still gone). Alex is out (a spectator): it can never count again, so it goes.
		planner.onStopped(server, alex.getUUID(), false);
		helper.assertTrue(y.data.armed(alex.getUUID()).isPresent(), "the bed trap went when Alex left");
		planner.onStopped(server, alex.getUUID(), true);
		helper.assertTrue(y.data.armed(alex.getUUID()).isEmpty(), "the bed trap stayed for a spectator");
		helper.assertBlockNotPresent(Blocks.BED.white(), new BlockPos(3, 1, 3));
		phantom.discard();
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void sessionsArePerPlayer(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerPlayer alex = y.player;
		ServerPlayer sam = mock(helper);
		twoMines(y, alex, sam);
		alex.snapTo(y.absVec(3.5, 5, 4.5), 90, 0);
		sam.snapTo(y.absVec(3.5, 5, 16.5), 90, 0);
		AccidentPlannerImpl planner = new Group(helper, alex, sam).planner(y.data, Yard.NOBODY);
		MinecraftServer server = y.level.getServer();
		helper.assertTrue(planner.arm(alex, Traps.LAVA_FLOOR, false).armed(), "Alex's first trap");
		planner.disarm(server, alex.getUUID(), "test");
		helper.assertTrue(planner.arm(alex, Traps.LAVA_WALL, false).status() == AccidentPlannerImpl.Status.SESSION, "two of Alex's traps in one session of his");
		helper.assertFalse(planner.canArm(server, alex.getUUID()), "the card gate still has room for Alex");
		// Alex's session is his alone: Sam still gets his own.
		helper.assertTrue(planner.canArm(server, sam.getUUID()) && planner.arm(sam, Traps.LAVA_FLOOR, false).armed(), "Sam's session was used up by Alex's trap");
		helper.assertTrue(planner.sessionArms(alex.getUUID()) == 1 && planner.sessionArms(sam.getUUID()) == 1, "session counts");
		planner.onParticipation(server, alex.getUUID(), true);
		helper.assertTrue(planner.sessionArms(alex.getUUID()) == 0 && planner.sessionArms(sam.getUUID()) == 1, "a new session of Alex's touched Sam's");
		helper.assertTrue(planner.canArm(server, alex.getUUID()) && !planner.canArm(server, sam.getUUID()), "the gate after Alex's new session");
		y.succeedWithoutDrops();
	}

	/** Core's {@code AccidentPlanner} answers per player: {@code armed(uuid)} and {@code disarm(uuid)} touch only that player's trap. */
	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void theCorePlannerIsOneTrapPerPlayer(GameTestHelper helper) {
		Yard y = dirtYard(helper);
		ServerPlayer alex = y.player;
		ServerPlayer sam = mock(helper);
		AccidentPlannerImpl impl = new Group(helper, alex, sam).planner(y.data, Yard.NOBODY);
		impl.attach(y.level.getServer());
		AccidentPlanner planner = impl;
		pocket(y, alex);
		pocket(y, sam);
		helper.assertTrue(planner.armed(alex.getUUID()).map(TrapType::id).orElse("").equals(Traps.LAVA_FLOOR.id())
				&& planner.armed(sam.getUUID()).isPresent(), "each player's trap: " + planner.armed(alex.getUUID()) + ", " + planner.armed(sam.getUUID()));
		helper.assertTrue(planner.armed(UUID.randomUUID()).isEmpty(), "someone who never had a trap has one");
		planner.disarm(alex.getUUID());
		helper.assertTrue(planner.armed(alex.getUUID()).isEmpty() && planner.armed(sam.getUUID()).isPresent() && planner.armed().isPresent(),
				"disarming Alex's trap touched Sam's: " + y.data.traps().keySet());
		planner.disarm(alex.getUUID());
		helper.assertTrue(planner.armed(sam.getUUID()).isPresent(), "disarming nothing of Alex's dropped Sam's");
		planner.disarm();
		helper.assertTrue(planner.armed().isEmpty() && planner.armed(sam.getUUID()).isEmpty(), "disarm() left a trap armed");
		AccidentPlanner stub = new AccidentPlanner.Stub();
		stub.disarm(alex.getUUID());
		helper.assertTrue(stub.armed(alex.getUUID()).isEmpty() && stub.armed().isEmpty(), "the stub has a trap");
		y.succeedWithoutDrops();
	}

	@GameTest
	public void anOldSingleTrapSaveBecomesTheFirstKnownPlayers(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ServerPlayer alex = mock(helper);
		ServerPlayer sam = mock(helper);
		BlockPos pos = new BlockPos(40, 64, -20);
		ArmedTrap trap = new ArmedTrap("lava_floor", level.dimension(), pos, List.of(pos), List.of(), Optional.empty(), List.of(), ArmedTrap.Phase.SET, 5, 5, 900,
				ArmedTrap.NO_CLOCK, pos.offset(-5, -3, -5), pos.offset(5, 5, 5), "a pocket", 0);
		ArmedTrap corner = new ArmedTrap("dark_corner", level.dimension(), pos.east(30), List.of(), List.of(), Optional.empty(), List.of(),
				ArmedTrap.Phase.SET, 5, 5, 900, 22500, pos.east(26), pos.east(34), "one torch a block off", 0);
		RouteBook routes = new RouteBook();
		routes.record(level.dimension(), pos.above(), 3, RouteBook.UNDER, 0, 100, 10);
		GlobalPos cairn = GlobalPos.of(level.dimension(), pos.south(50));
		// The file as v1.0 wrote it: one subject's trap, routes and cairn, no players.
		CompoundTag old = new CompoundTag();
		old.put("armed", ArmedTrap.CODEC.encodeStart(ops, trap).getOrThrow());
		old.put("restoring", ArmedTrap.CODEC.encodeStart(ops, corner).getOrThrow());
		old.put("routes", RouteBook.CODEC.encodeStart(ops, routes).getOrThrow());
		old.put("cairn", GlobalPos.CODEC.encodeStart(ops, cairn).getOrThrow());
		old.put("cairnVisits", IntTag.valueOf(2));
		ListTag crosses = new ListTag();
		crosses.add(AccidentData.PendingCross.CODEC.encodeStart(ops, new AccidentData.PendingCross(GlobalPos.of(level.dimension(), pos), "fell", 5, 0)).getOrThrow());
		old.put("crosses", crosses);

		AccidentData loaded = AccidentData.CODEC.parse(ops, old).getOrThrow();
		helper.assertTrue(loaded.hasLegacy() && loaded.traps().isEmpty() && loaded.restoring().equals(List.of(corner)) && loaded.crosses().size() == 1,
				"the old file did not load as pending: " + loaded.traps() + " " + loaded.restoring());
		// Nobody known yet: it keeps waiting, and saving keeps it.
		helper.assertFalse(loaded.adoptLegacy(List.of()), "taken by nobody");
		CompoundTag waiting = (CompoundTag) AccidentData.CODEC.encodeStart(ops, loaded).getOrThrow();
		helper.assertTrue(waiting.contains("armed") && waiting.contains("routes"), "a save before anyone is known lost the old trap");

		// The subject is the first known player: Alex, then Sam.
		helper.assertTrue(loaded.adoptLegacy(Group.known(alex, sam).players()), "not adopted");
		helper.assertTrue(loaded.traps().equals(java.util.Map.of(alex.getUUID(), trap)), "the old trap is not the first known player's: " + loaded.traps());
		helper.assertTrue(loaded.routes(alex.getUUID()).contains(level.dimension(), pos.above()) && loaded.personal(alex.getUUID()).cairn().equals(Optional.of(cairn))
				&& loaded.personal(alex.getUUID()).cairnVisits() == 2, "the old routes and cairn are not Alex's");
		helper.assertTrue(loaded.routes(sam.getUUID()).isEmpty() && loaded.personal(sam.getUUID()).cairn().isEmpty(), "Sam got the old records");
		helper.assertFalse(loaded.hasLegacy() || loaded.adoptLegacy(Group.known(sam).players()), "adopted twice");

		CompoundTag saved = (CompoundTag) AccidentData.CODEC.encodeStart(ops, loaded).getOrThrow();
		for (String legacy : List.of("armed", "restoring", "routes", "cairn", "cairnVisits")) {
			helper.assertFalse(saved.contains(legacy), "the new format still writes " + legacy);
		}
		AccidentData again = AccidentData.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(again.traps().equals(loaded.traps()) && again.restoring().equals(loaded.restoring()) && !again.hasLegacy()
				&& again.routes(alex.getUUID()).contains(level.dimension(), pos.above()), "the new format did not round-trip");
		helper.succeed();
	}
}
