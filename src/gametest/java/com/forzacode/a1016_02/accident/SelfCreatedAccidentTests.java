package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.accident.sc.Sc15Config;
import com.forzacode.a1016_02.accident.sc.StoodOn;
import com.forzacode.a1016_02.accident.sc.StoodOnData;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Pacing;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.core.TraceService;
import com.google.gson.JsonObject;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The self-created layer's accident part (docs/SELF_CREATED.md, 15): on a slab of natural stone far from everything,
 * with lava, water, a short drop, a long drop and solid rock under its cells, a stand from 5 s ago on one of the
 * player's routes goes (ledgered, the day and count saved) once they are 8 blocks past and facing away; refused for a
 * block a player placed, in view, beside a friend, with nothing or a long drop under it, on an armed trap's ground,
 * too soon or too late, off their routes (a forced arm drops the passes), on a bridge of theirs, once today, after the
 * story; the standing ring and the card's moment; storage and the section. Each test has its own group, routes, ring,
 * traps, memory and clocks; only the trace service is the live one.
 */
public class SelfCreatedAccidentTests {
	/** Cells along x on the slab (ground at y 9, feet at y 10, z 6) and what lies under each. */
	private static final int LAVA = 10;
	private static final int WATER = 14;
	private static final int SHORT_DROP = 18;
	private static final int LONG_DROP = 22;
	private static final int SOLID = 26;

	private static final class Field {
		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		final ServerPlayer player;
		final Group group;
		final RouteBook routes = new RouteBook();
		final List<RouteSampler.Stand> ring = new ArrayList<>();
		final List<ArmedTrap> traps = new ArrayList<>();
		final StoodOnData memory = new StoodOnData();
		final Sc15Config config = new Sc15Config();
		final long[] now = {1_000_000};
		final long[] day = {5};
		boolean enabled = true;
		long walkedAt;
		long sinceCombat = Long.MAX_VALUE;

		Field(GameTestHelper helper, int slot) {
			this.helper = helper;
			this.level = helper.getLevel();
			this.origin = helper.absolutePos(BlockPos.ZERO).offset(9000 + slot * 512, 40, 5000);
			BlockState stone = Blocks.STONE.defaultBlockState();
			for (int x = 0; x < 36; x++) {
				for (int z = 0; z < 12; z++) {
					for (int y = 0; y < 16; y++) {
						level.setBlock(at(x, y, z), y < 10 ? stone : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			air(LAVA, 8, 8);
			level.setBlock(at(LAVA, 7, 6), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
			level.setBlock(at(WATER, 8, 6), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
			air(SHORT_DROP, 5, 8);
			air(LONG_DROP, 1, 8);
			ServerPlayer mock = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			this.player = mock;
			group = new Group(helper, mock);
			standAt(LAVA + 8, -90);
		}

		BlockPos at(int x, int y, int z) {
			return origin.offset(x, y, z);
		}

		private void air(int x, int y0, int y1) {
			for (int y = y0; y <= y1; y++) {
				level.setBlock(at(x, y, 6), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
		}

		/** The player's feet on the slab at this x, facing this yaw (-90: east, further along; 90: back west). */
		void standAt(int x, float yaw) {
			BlockPos feet = at(x, 10, 6);
			player.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, yaw, 0);
			player.setYHeadRot(yaw);
		}

		/** The feet cell over the ground block at this x. */
		BlockPos feet(int x) {
			return at(x, 10, 6);
		}

		/** That cell walked {@code passes} more separate times (each a pass apart), with these flags. */
		void walked(int x, int passes, int flags) {
			for (int i = 0; i < passes; i++) {
				routes.record(Level.OVERWORLD, feet(x), 5, flags, walkedAt += 10_000L, 1200, 10_000);
			}
		}

		/** A stand on that cell {@code secondsAgo} ago. */
		void stood(int x, double secondsAgo) {
			ring.add(new RouteSampler.Stand(Level.OVERWORLD, feet(x), now[0] - ModConfig.realTicks(secondsAgo)));
		}

		StoodOn.Env env() {
			Pacing pacing = ModConfig.pacing();
			int chunks = level.getServer().getPlayerList().getViewDistance();
			return new StoodOn.Env(group.watch, uuid -> List.copyOf(ring), uuid -> routes, () -> List.copyOf(traps),
					l -> group.online.stream().filter(p -> p.level() == l).toList(),
					(l, pos) -> TraceService.positionsOutOfView(l, List.of(pos), group.online.stream().filter(p -> p.level() == l)
							.map(p -> TraceService.Viewer.of(p, chunks)).toList(), pacing.viewNearBlocks, pacing.viewConeDegrees),
					Services.traces(), memory, config, AccidentConfig.get().zoneSlack, () -> now[0], () -> day[0], uuid -> 0L, p -> sinceCombat,
					() -> enabled, () -> group.state);
		}

		String why(int x, boolean forced) {
			return StoodOn.check(player, ring.getLast(), forced, env());
		}
	}

	private static List<TraceLedger.Entry> removals(ServerLevel level, BlockPos ground) {
		return TraceLedger.get(level.getServer()).entries().stream().filter(e -> StoodOn.CAUSE.equals(e.cause()) && e.pos().pos().equals(ground)).toList();
	}

	/**
	 * The block they stood on 5 s ago over lava, on a route walked 3 times, goes once they are 8 past it facing away:
	 * one ledgered removal, the day and the world's count saved, and then nothing more that day. The undo puts it back.
	 */
	@GameTest
	public void theBlockTheyStoodOnGoesBehindThemOverLava(GameTestHelper helper) {
		Field field = new Field(helper, 61);
		BlockPos ground = field.feet(LAVA).below();
		BlockState before = field.level.getBlockState(ground);
		try {
			field.walked(LAVA, 3, 0);
			field.stood(LAVA, 5);
			helper.assertTrue(field.why(LAVA, false) == null, "refused: " + field.why(LAVA, false));
			helper.assertTrue(StoodOn.tryNow(field.player, false, field.env()), "nothing went");
			helper.assertTrue(field.level.getBlockState(ground).isAir(), "the block is still there");
			List<TraceLedger.Entry> removed = removals(field.level, ground);
			helper.assertTrue(removed.size() == 1 && removed.getFirst().kind() == TraceLedger.Kind.REMOVE, "not ledgered: " + removed);
			helper.assertTrue(field.memory.count() == 1 && field.memory.lastDay(field.player.getUUID()) == 5, "not saved");
			helper.assertTrue("already today (day 5)".equals(StoodOn.gate(field.player, field.env())), "a second one the same day");
			field.day[0] = 6;
			helper.assertTrue(StoodOn.gate(field.player, field.env()) == null, "not again the next day");
			helper.assertTrue(Services.traces().forced().undo(field.level, removed.getFirst()) == TraceService.UndoResult.DONE
					&& field.level.getBlockState(ground).equals(before), "the undo did not put it back");
		} finally {
			removals(field.level, ground).forEach(TraceLedger.get(field.level.getServer())::remove);
		}
		helper.succeed();
	}

	/** Water or a short drop under it may open; solid rock, a drop of 8 or a cavity a player dug may not. */
	@GameTest
	public void onlyLavaWaterOrAShortDropMayOpenUnderIt(GameTestHelper helper) {
		Field field = new Field(helper, 62);
		Sc15Config config = field.config;
		helper.assertTrue(StoodOn.under(field.level, field.group.watch, field.feet(LAVA).below(), config).cavity() == StoodOn.Cavity.LAVA, "lava");
		helper.assertTrue(StoodOn.under(field.level, field.group.watch, field.feet(WATER).below(), config).cavity() == StoodOn.Cavity.WATER, "water");
		StoodOn.Under drop = StoodOn.under(field.level, field.group.watch, field.feet(SHORT_DROP).below(), config);
		helper.assertTrue(drop.cavity() == StoodOn.Cavity.AIR && drop.depth() == 4, "a drop of 4: " + drop);
		StoodOn.Under deep = StoodOn.under(field.level, field.group.watch, field.feet(LONG_DROP).below(), config);
		helper.assertTrue(deep.cavity() == null && String.valueOf(deep.why()).startsWith("a drop deeper than 6"), "a drop of 8: " + deep);
		StoodOn.Under solid = StoodOn.under(field.level, field.group.watch, field.feet(SOLID).below(), config);
		helper.assertTrue(solid.cavity() == null && "no cavity under it".equals(solid.why()), "solid rock: " + solid);
		field.standAt(SOLID + 8, -90);
		field.walked(SOLID, 3, 0);
		field.stood(SOLID, 5);
		helper.assertTrue("no cavity under it".equals(field.why(SOLID, false)), "solid: " + field.why(SOLID, false));
		field.ring.clear();
		field.standAt(WATER + 8, -90);
		field.walked(WATER, 3, 0);
		field.stood(WATER, 5);
		helper.assertTrue(field.why(WATER, false) == null, "water refused: " + field.why(WATER, false));
		BlockPos ground = field.feet(WATER).below();
		String area = "test:sc15/" + field.origin.toShortString();
		Services.protectedAreas().protect(area, Level.OVERWORLD, net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(ground, ground));
		try {
			helper.assertTrue("a protected area".equals(field.why(WATER, false)), "taken in a protected area: " + field.why(WATER, false));
		} finally {
			Services.protectedAreas().unprotect(area);
		}
		helper.succeed();
	}

	/**
	 * Refused for a block a player placed, in view, beside a friend, on an armed trap's ground, too soon or too late, off
	 * their routes (a forced arm drops the passes), on a bridge of theirs, after the story, with the layer off.
	 */
	@GameTest
	public void theBlockIsRefusedWhereItWouldNotBeDeniable(GameTestHelper helper) {
		Field field = new Field(helper, 63);
		BlockPos ground = field.feet(LAVA).below();
		field.walked(LAVA, 3, 0);
		field.stood(LAVA, 5);
		helper.assertTrue(field.why(LAVA, false) == null, "the setup is refused: " + field.why(LAVA, false));
		field.standAt(LAVA + 8, 90);
		helper.assertTrue("in view".equals(field.why(LAVA, false)), "taken in view: " + field.why(LAVA, false));
		field.standAt(LAVA + 3, -90);
		helper.assertTrue(String.valueOf(field.why(LAVA, false)).startsWith("they are not 5 past it"), "taken 3 blocks from them");
		field.standAt(LAVA + 8, -90);
		field.traps.add(new ArmedTrap("test_trap", Level.OVERWORLD, ground, List.of(), List.of(), Optional.empty(), List.of(), ArmedTrap.Phase.WATCHING, 0, -1,
				Long.MAX_VALUE, ArmedTrap.NO_CLOCK, ground.offset(-1, -1, -1), ground.offset(1, 1, 1), "", 0));
		helper.assertTrue("an armed trap's ground".equals(field.why(LAVA, false)), "taken on a trap's ground");
		field.traps.clear();
		field.ring.clear();
		field.stood(LAVA, 1);
		helper.assertTrue(String.valueOf(field.why(LAVA, false)).startsWith("not 3 to 8 s ago"), "taken 1 s after");
		field.ring.clear();
		field.stood(LAVA, 9);
		helper.assertTrue(String.valueOf(field.why(LAVA, false)).startsWith("not 3 to 8 s ago"), "taken 9 s after");
		field.ring.clear();
		field.stood(WATER, 5);
		field.standAt(WATER + 8, -90);
		field.walked(WATER, 1, 0);
		helper.assertTrue(String.valueOf(field.why(WATER, false)).startsWith("not one of their routes (1 passes)"), "taken off their routes");
		helper.assertTrue(field.why(WATER, true) == null, "a forced arm kept the passes: " + field.why(WATER, true));
		field.walked(WATER, 2, RouteBook.ON_PLACED);
		helper.assertTrue("a bridge, a ladder or water of theirs".equals(field.why(WATER, false)), "taken on a bridge of theirs");
		field.ring.clear();
		field.stood(LAVA, 5);
		field.standAt(LAVA + 8, -90);
		field.group.watch.onPlaced(field.player, field.level, ground, field.level.getBlockState(ground));
		helper.assertTrue("not a natural full block".equals(field.why(LAVA, false)), "took a placed block");

		ServerPlayer friend = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		friend.snapTo(field.feet(LAVA + 30).getX() + 0.5, field.feet(LAVA).getY(), field.feet(LAVA).getZ() + 0.5, 0, 0);
		field.group.online.add(friend);
		helper.assertTrue("not alone".equals(StoodOn.gate(field.player, field.env())), "beside a friend: " + StoodOn.gate(field.player, field.env()));
		field.group.online.remove(friend);
		field.group.state.setFlag("ending:path=C", true);
		helper.assertTrue("the story is quiet for good".equals(StoodOn.gate(field.player, field.env())), "after the story");
		field.group.state.setFlag("ending:path=C", false);
		field.enabled = false;
		helper.assertTrue("the layer is switched off".equals(StoodOn.gate(field.player, field.env())), "with the layer off");
		field.enabled = true;
		field.sinceCombat = ModConfig.realTicks(12);
		helper.assertTrue("combat in the last 30 s".equals(StoodOn.gate(field.player, field.env())), "after combat");
		field.sinceCombat = Long.MAX_VALUE;
		for (int i = 0; i < field.config.maxPerWorld; i++) {
			field.memory.record(new UUID(9, i), 1);
		}
		helper.assertTrue(String.valueOf(StoodOn.gate(field.player, field.env())).startsWith("the world had its 6"), "a seventh in the world");
		helper.succeed();
	}

	/**
	 * The route sampler keeps a standing ring only for the players it is told to, on the ground, the last 10 s; the card's
	 * moment is standing on one of their routes, not armed yet; arms end when asked.
	 */
	@GameTest
	public void theStandingRingAndTheCardsMoment(GameTestHelper helper) {
		Field field = new Field(helper, 64);
		RouteSampler sampler = new RouteSampler();
		UUID uuid = field.player.getUUID();
		AccidentData data = new AccidentData();
		field.player.setOnGround(true);
		sampler.sample(field.player, data, AccidentConfig.get(), 0, 0);
		helper.assertTrue(sampler.standing(uuid).isEmpty(), "a ring for someone not asked for");
		sampler.ringFor(uuid::equals);
		sampler.sample(field.player, data, AccidentConfig.get(), 20, 0);
		helper.assertTrue(sampler.standing(uuid).size() == 1 && sampler.standing(uuid).getFirst().feet().equals(field.player.blockPosition()),
				"no stand: " + sampler.standing(uuid));
		sampler.stand(uuid, new RouteSampler.Stand(Level.OVERWORLD, field.feet(1), 10_000));
		sampler.stand(uuid, new RouteSampler.Stand(Level.OVERWORLD, field.feet(2), 10_000 + RouteSampler.STANDING_TICKS + 1));
		helper.assertTrue(sampler.standing(uuid).size() == 1, "the ring kept more than 10 s: " + sampler.standing(uuid));
		sampler.reset(uuid);
		helper.assertTrue(sampler.standing(uuid).isEmpty(), "the ring outlived a reset");

		field.standAt(SOLID, -90);
		field.player.setOnGround(true);
		field.walked(SOLID, 2, 0);
		helper.assertFalse(StoodOn.fits(field.player, field.env()), "fit on a cell walked twice");
		field.walked(SOLID, 1, 0);
		helper.assertTrue(StoodOn.fits(field.player, field.env()), "did not fit on one of their routes");
		StoodOn.arm(uuid, 1000, false);
		try {
			helper.assertFalse(StoodOn.fits(field.player, field.env()), "fit while armed");
			helper.assertTrue(StoodOn.ticksLeft(uuid, 400) == 600, "ticks left " + StoodOn.ticksLeft(uuid, 400));
		} finally {
			StoodOn.disarm(uuid);
		}
		helper.assertTrue(StoodOn.ticksLeft(uuid, 400) == -1, "the arm outlived its disarm");
		helper.succeed();
	}

	/** The saved days and count round-trip, a world without the file has none; {@code sections.sc15} is version 1. */
	@GameTest
	public void theMemoryRoundTripsAndTheSectionIsVersionOne(GameTestHelper helper) {
		StoodOnData data = new StoodOnData();
		data.record(new UUID(1, 2), 7);
		CompoundTag tag = (CompoundTag) StoodOnData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		StoodOnData back = StoodOnData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.count() == 1 && back.lastDay(new UUID(1, 2)) == 7, "not round-tripped");
		helper.assertTrue(StoodOnData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow().count() == 0, "an empty file loaded a count");
		Sc15Config cfg = ModConfig.readSection(new JsonObject(), Sc15Config.SECTION, Sc15Config.class, Sc15Config::new);
		helper.assertTrue(cfg.version == 1 && cfg.armMinutes == 20 && cfg.minPasses == 3 && cfg.delaySecondsMin == 3 && cfg.delaySecondsMax == 8
				&& cfg.behindMin == 5 && cfg.cavityDepthMax == 6 && cfg.airDropMax == 6 && cfg.nobodyWithin == 16 && cfg.maxPerWorld == 6, "the numbers");
		helper.succeed();
	}
}
