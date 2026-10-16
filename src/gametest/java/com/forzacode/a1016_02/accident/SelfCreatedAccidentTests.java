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
 * with drops of 2, 3 and 4 from the feet, lava, water, magma and solid rock under its cells, a stand from 5 s ago on
 * one of the player's routes goes (ledgered, the day and count saved) once they are 8 blocks past and facing away;
 * refused for a block a player placed, in view, beside a friend, with nothing, a fluid, a floor that hurts or a fall
 * of more than 3 under it (review R9: never lethal on its own), on an armed trap's ground, too soon or too late, off
 * their routes (a forced arm drops the passes), on a bridge of theirs, while they are hurt, once today, after the
 * story; the standing ring and the card's moment; storage and the section. Each test has its own group, routes, ring,
 * traps, memory and clocks; only the trace service is the live one.
 */
public class SelfCreatedAccidentTests {
	// Cells along x on the slab (ground at y 9, feet at y 10, z 6) and what lies under each.
	/** Air at y 8 onto stone: a fall of 2 from the feet. */
	private static final int PIT = 10;
	/** Air at y 7 and 8 onto stone: a fall of 3. */
	private static final int PIT3 = 14;
	/** Air at y 8 over lava. */
	private static final int LAVA = 18;
	/** Air from y 6 to 8 onto stone: a fall of 4. */
	private static final int DEEP = 22;
	private static final int SOLID = 26;
	/** Water right under the ground. */
	private static final int WATER = 30;
	/** Air at y 8 onto magma. */
	private static final int MAGMA = 34;

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
			for (int x = 0; x < 44; x++) {
				for (int z = 0; z < 12; z++) {
					for (int y = 0; y < 16; y++) {
						level.setBlock(at(x, y, z), y < 10 ? stone : Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
					}
				}
			}
			air(PIT, 8, 8);
			air(PIT3, 7, 8);
			air(LAVA, 8, 8);
			level.setBlock(at(LAVA, 7, 6), Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
			air(DEEP, 6, 8);
			level.setBlock(at(WATER, 8, 6), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
			air(MAGMA, 8, 8);
			level.setBlock(at(MAGMA, 7, 6), Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
			ServerPlayer mock = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
			this.player = mock;
			group = new Group(helper, mock);
			standAt(PIT + 8, -90);
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
	 * The block they stood on 5 s ago over a short drop, on a route walked 3 times, goes once they are 8 past it facing
	 * away: one ledgered removal, the day and the world's count saved, and then nothing more that day. The undo puts it
	 * back.
	 */
	@GameTest
	public void theBlockTheyStoodOnGoesBehindThemOverAShortDrop(GameTestHelper helper) {
		Field field = new Field(helper, 61);
		BlockPos ground = field.feet(PIT).below();
		BlockState before = field.level.getBlockState(ground);
		try {
			field.walked(PIT, 3, 0);
			field.stood(PIT, 5);
			helper.assertTrue(field.why(PIT, false) == null, "refused: " + field.why(PIT, false));
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

	/**
	 * Only a short drop onto plain ground may open under it (review R9): a fall of 2 or 3 from the feet; never lava,
	 * water, magma, a fall of 4 (whatever {@code airDropMax} says: never past vanilla's safe fall) or solid rock.
	 */
	@GameTest
	public void onlyAShortDropOntoPlainGroundMayOpenUnderIt(GameTestHelper helper) {
		Field field = new Field(helper, 62);
		Sc15Config config = field.config;
		StoodOn.Under two = StoodOn.under(field.level, field.group.watch, field.feet(PIT).below(), config);
		helper.assertTrue(two.cavity() == StoodOn.Cavity.AIR && two.depth() == 2, "a fall of 2: " + two);
		StoodOn.Under three = StoodOn.under(field.level, field.group.watch, field.feet(PIT3).below(), config);
		helper.assertTrue(three.cavity() == StoodOn.Cavity.AIR && three.depth() == 3, "a fall of 3: " + three);
		StoodOn.Under lava = StoodOn.under(field.level, field.group.watch, field.feet(LAVA).below(), config);
		helper.assertTrue(lava.cavity() == null && "lava or water under it".equals(lava.why()), "lava: " + lava);
		StoodOn.Under water = StoodOn.under(field.level, field.group.watch, field.feet(WATER).below(), config);
		helper.assertTrue(water.cavity() == null && "lava or water under it".equals(water.why()), "water: " + water);
		StoodOn.Under magma = StoodOn.under(field.level, field.group.watch, field.feet(MAGMA).below(), config);
		helper.assertTrue(magma.cavity() == null && String.valueOf(magma.why()).startsWith("a block that hurts under it"), "magma: " + magma);
		StoodOn.Under deep = StoodOn.under(field.level, field.group.watch, field.feet(DEEP).below(), config);
		helper.assertTrue(deep.cavity() == null && "a fall of 4 from their feet (more than 3)".equals(deep.why()), "a fall of 4: " + deep);
		Sc15Config wide = new Sc15Config();
		wide.airDropMax = 6;
		StoodOn.Under capped = StoodOn.under(field.level, field.group.watch, field.feet(DEEP).below(), wide);
		helper.assertTrue(capped.cavity() == null, "a fall of 4 with airDropMax 6: " + capped);
		StoodOn.Under solid = StoodOn.under(field.level, field.group.watch, field.feet(SOLID).below(), config);
		helper.assertTrue(solid.cavity() == null && "no cavity under it".equals(solid.why()), "solid rock: " + solid);
		field.standAt(SOLID + 8, -90);
		field.walked(SOLID, 3, 0);
		field.stood(SOLID, 5);
		helper.assertTrue("no cavity under it".equals(field.why(SOLID, false)), "solid: " + field.why(SOLID, false));
		field.ring.clear();
		field.standAt(PIT3 + 8, -90);
		field.walked(PIT3, 3, 0);
		field.stood(PIT3, 5);
		helper.assertTrue(field.why(PIT3, false) == null, "a fall of 3 refused: " + field.why(PIT3, false));
		BlockPos ground = field.feet(PIT3).below();
		String area = "test:sc15/" + field.origin.toShortString();
		Services.protectedAreas().protect(area, Level.OVERWORLD, net.minecraft.world.level.levelgen.structure.BoundingBox.fromCorners(ground, ground));
		try {
			helper.assertTrue("a protected area".equals(field.why(PIT3, false)), "taken in a protected area: " + field.why(PIT3, false));
		} finally {
			Services.protectedAreas().unprotect(area);
		}
		helper.succeed();
	}

	/**
	 * Refused for a block a player placed, in view, beside a friend, on an armed trap's ground, too soon or too late, off
	 * their routes (a forced arm drops the passes), on a bridge of theirs, after the story, with the layer off, while they
	 * are hurt.
	 */
	@GameTest
	public void theBlockIsRefusedWhereItWouldNotBeDeniable(GameTestHelper helper) {
		Field field = new Field(helper, 63);
		BlockPos ground = field.feet(PIT).below();
		field.walked(PIT, 3, 0);
		field.stood(PIT, 5);
		helper.assertTrue(field.why(PIT, false) == null, "the setup is refused: " + field.why(PIT, false));
		field.standAt(PIT + 8, 90);
		helper.assertTrue("in view".equals(field.why(PIT, false)), "taken in view: " + field.why(PIT, false));
		field.standAt(PIT + 3, -90);
		helper.assertTrue(String.valueOf(field.why(PIT, false)).startsWith("they are not 5 past it"), "taken 3 blocks from them");
		field.standAt(PIT + 8, -90);
		field.traps.add(new ArmedTrap("test_trap", Level.OVERWORLD, ground, List.of(), List.of(), Optional.empty(), List.of(), ArmedTrap.Phase.WATCHING, 0, -1,
				Long.MAX_VALUE, ArmedTrap.NO_CLOCK, ground.offset(-1, -1, -1), ground.offset(1, 1, 1), "", 0));
		helper.assertTrue("an armed trap's ground".equals(field.why(PIT, false)), "taken on a trap's ground");
		field.traps.clear();
		field.ring.clear();
		field.stood(PIT, 1);
		helper.assertTrue(String.valueOf(field.why(PIT, false)).startsWith("not 3 to 8 s ago"), "taken 1 s after");
		field.ring.clear();
		field.stood(PIT, 9);
		helper.assertTrue(String.valueOf(field.why(PIT, false)).startsWith("not 3 to 8 s ago"), "taken 9 s after");
		field.ring.clear();
		field.stood(PIT3, 5);
		field.standAt(PIT3 + 8, -90);
		field.walked(PIT3, 1, 0);
		helper.assertTrue(String.valueOf(field.why(PIT3, false)).startsWith("not one of their routes (1 passes)"), "taken off their routes");
		helper.assertTrue(field.why(PIT3, true) == null, "a forced arm kept the passes: " + field.why(PIT3, true));
		field.walked(PIT3, 2, RouteBook.ON_PLACED);
		helper.assertTrue("a bridge, a ladder or water of theirs".equals(field.why(PIT3, false)), "taken on a bridge of theirs");
		field.ring.clear();
		field.stood(PIT, 5);
		field.standAt(PIT + 8, -90);
		field.group.watch.onPlaced(field.player, field.level, ground, field.level.getBlockState(ground));
		helper.assertTrue("not a natural full block".equals(field.why(PIT, false)), "took a placed block");

		ServerPlayer friend = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		friend.snapTo(field.feet(PIT + 30).getX() + 0.5, field.feet(PIT).getY(), field.feet(PIT).getZ() + 0.5, 0, 0);
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
		field.player.setHealth(6.0F);
		helper.assertTrue("they are hurt (health 6, under 10)".equals(StoodOn.gate(field.player, field.env())), "while hurt: "
				+ StoodOn.gate(field.player, field.env()));
		field.player.setHealth(field.player.getMaxHealth());
		helper.assertTrue(StoodOn.gate(field.player, field.env()) == null, "healed: " + StoodOn.gate(field.player, field.env()));
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

	/**
	 * The saved days and count round-trip, a world without the file has none; {@code sections.sc15} is version 2, and a
	 * version 1 file's drop of 6 (counted from under the block) becomes 3 from the feet while one of its own is kept.
	 */
	@GameTest
	public void theMemoryRoundTripsAndTheSectionIsVersionTwo(GameTestHelper helper) {
		StoodOnData data = new StoodOnData();
		data.record(new UUID(1, 2), 7);
		CompoundTag tag = (CompoundTag) StoodOnData.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		StoodOnData back = StoodOnData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(back.count() == 1 && back.lastDay(new UUID(1, 2)) == 7, "not round-tripped");
		helper.assertTrue(StoodOnData.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow().count() == 0, "an empty file loaded a count");
		Sc15Config cfg = ModConfig.readSection(new JsonObject(), Sc15Config.SECTION, Sc15Config.class, Sc15Config::new);
		helper.assertTrue(cfg.version == 3 && cfg.armMinutes == 20 && cfg.minPasses == 3 && cfg.delaySecondsMin == 3 && cfg.delaySecondsMax == 8
				&& cfg.behindMin == 5 && cfg.airDropMax == 3 && cfg.minHealth == 10 && cfg.nobodyWithin == 16 && cfg.maxPerWorld == 6, "the numbers");
		helper.assertTrue(versionOne(6).airDropMax == 3 && versionOne(6).version == 3, "version 1's default drop was kept: " + versionOne(6).airDropMax);
		helper.assertTrue(versionOne(2).airDropMax == 2, "a drop of their own was not kept: " + versionOne(2).airDropMax);
		helper.succeed();
	}

	private static Sc15Config versionOne(int airDropMax) {
		JsonObject section = new JsonObject();
		section.addProperty("version", 1);
		section.addProperty("cavityDepthMax", 6);
		section.addProperty("airDropMax", airDropMax);
		JsonObject sections = new JsonObject();
		sections.add(Sc15Config.SECTION, section);
		return ModConfig.readSection(sections, Sc15Config.SECTION, Sc15Config.class, Sc15Config::new);
	}
}
