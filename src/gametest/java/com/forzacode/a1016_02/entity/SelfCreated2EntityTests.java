package com.forzacode.a1016_02.entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SoundCues;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.core.TraceService;
import com.forzacode.a1016_02.entity.sc2.s27.CopyRules;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Config;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Data;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27EndPayload;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Payload;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Rules;
import com.forzacode.a1016_02.entity.sc2.s27.Sc27Spot;
import com.forzacode.a1016_02.entity.sc2.s27.Sight;
import com.forzacode.a1016_02.entity.sc2.s27.Walk;
import com.forzacode.a1016_02.entity.sc2.s31.Apex;
import com.forzacode.a1016_02.entity.sc2.s31.InsideTrunks;
import com.forzacode.a1016_02.entity.sc2.s31.Sc31Config;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * SC2-6's game tests (docs/SELF_CREATED_2.md, 27, 31 and 31c), server side; the copy on the viewer's client has its pure
 * rules checked here ({@link CopyRules}) and is seen in the gallery and the LAN checklist. The tests that need land build it
 * at their own spots, X 262 000 plus 640 a slot, Z -262 000, Y 120 (far from every other test class), loaded by a ticket; the
 * figure's own spawns stand in the yard. Every arm of 27 is made on the test's own {@link Sc27} and hooks, every record on a
 * fresh {@link EntityData} or {@link Sc27Data}, so nothing here reads or changes the shared test server's story.
 */
public class SelfCreated2EntityTests {
	private static final String YARD = "a1016_02:accident/yard";
	private static final TicketType TICKET = new TicketType(1200L, TicketType.FLAG_LOADING);
	private static final int BASE_X = 262_000;
	private static final int BASE_Z = -262_000;
	private static final int BASE_Y = 120;

	// --- helpers ---

	/** A player that is in no level's list and never a participant, at a level of the test's choice. */
	static final class TestPlayer extends ServerPlayer {
		final GameType mode;

		TestPlayer(ServerLevel level, String name, GameType mode) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
			this.mode = mode;
			mode.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode == null ? GameType.SURVIVAL : mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	private static ServerPlayer player(ServerLevel level, String name, GameType mode, Vec3 feet, float yaw) {
		ServerPlayer player = new TestPlayer(level, name, mode);
		player.snapTo(feet.x, feet.y, feet.z, yaw, 0.0F);
		player.setYHeadRot(yaw);
		return player;
	}

	/** This slot's spot: its chunks loaded (radius {@code reach}), a stone floor 2 thick with its top at Y 119, air over it. */
	private static BlockPos ground(GameTestHelper helper, int slot, int half) {
		ServerLevel level = helper.getLevel();
		BlockPos o = new BlockPos(BASE_X + slot * 640, BASE_Y, BASE_Z);
		load(level, o, half + 32);
		BlockState stone = Blocks.STONE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();
		BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				level.setBlock(m.set(o.getX() + dx, o.getY() - 2, o.getZ() + dz), stone, Block.UPDATE_CLIENTS);
				level.setBlock(m.set(o.getX() + dx, o.getY() - 1, o.getZ() + dz), stone, Block.UPDATE_CLIENTS);
				for (int y = 0; y < 8; y++) {
					level.setBlock(m.set(o.getX() + dx, o.getY() + y, o.getZ() + dz), air, Block.UPDATE_CLIENTS);
				}
			}
		}
		return o;
	}

	private static void load(ServerLevel level, BlockPos o, int reach) {
		for (int cx = (o.getX() - reach) >> 4; cx <= (o.getX() + reach) >> 4; cx++) {
			for (int cz = (o.getZ() - reach) >> 4; cz <= (o.getZ() + reach) >> 4; cz++) {
				level.getChunkSource().addTicketWithRadius(TICKET, new ChunkPos(cx, cz), 0);
				level.getChunk(cx, cz);
			}
		}
	}

	private static void fill(ServerLevel level, BlockPos from, BlockPos to, BlockState state) {
		for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
			level.setBlock(pos, state, Block.UPDATE_CLIENTS);
		}
	}

	private static Vec3 feet(BlockPos o, double dx, double dz) {
		return new Vec3(o.getX() + dx + 0.5, o.getY(), o.getZ() + dz + 0.5);
	}

	/** Nobody watching (the spawn rule passes everywhere but the ticking). */
	private static Watchers nobody() {
		return new Watchers(List.of());
	}

	/** These players as watchers who see 512 blocks (whatever the test server's own view distance). */
	private static Watchers far(ServerPlayer... players) {
		List<Watchers.Watcher> all = new ArrayList<>();
		for (ServerPlayer p : players) {
			all.add(new Watchers.Watcher(new TraceService.Viewer(p.getEyePosition(), p.getViewVector(1.0F), p.getBoundingBox(), 512.0), 512.0, true,
					p.getName().getString()));
		}
		return new Watchers(all);
	}

	private static void floor(GameTestHelper helper, int size) {
		for (int x = 0; x < size; x++) {
			for (int z = 0; z < size; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
				for (int y = 1; y < 8; y++) {
					helper.setBlock(x, y, z, Blocks.AIR);
				}
			}
		}
	}

	/** 27's world as a test sees it: its own players, what it sent to whom, its own caps and story. */
	private static final class Hooks27 implements Sc27.Hooks {
		final List<ServerPlayer> players = new ArrayList<>();
		final List<ServerPlayer> bystanders = new ArrayList<>();
		final Map<UUID, List<CustomPacketPayload>> sent = new HashMap<>();
		final Sc27Data data = new Sc27Data();
		Optional<String> story = Optional.empty();
		long time = 18000L;
		boolean fog;
		double fogEnd = Double.NaN;
		long day = 10L;
		@Nullable Boolean sees;

		@Override
		public List<ServerPlayer> participants(MinecraftServer server) {
			return List.copyOf(players);
		}

		@Override
		public Optional<ServerPlayer> online(MinecraftServer server, UUID player) {
			return players.stream().filter(p -> p.getUUID().equals(player)).findFirst();
		}

		@Override
		public boolean send(ServerPlayer player, CustomPacketPayload payload) {
			sent.computeIfAbsent(player.getUUID(), u -> new ArrayList<>()).add(payload);
			return true;
		}

		@Override
		public Optional<String> commonRefusal(ServerPlayer viewer) {
			return Optional.empty();
		}

		@Override
		public Optional<String> storyRefusal(MinecraftServer server) {
			return story;
		}

		@Override
		public boolean alone(ServerPlayer viewer) {
			List<ServerPlayer> listeners = new ArrayList<>(players);
			listeners.addAll(bystanders);
			return SoundCues.alone(viewer, Sc27Rules.ALONE_RADIUS, Services.watch(), listeners);
		}

		@Override
		public long timeOfDay(MinecraftServer server) {
			return time;
		}

		@Override
		public boolean fogCounts(MinecraftServer server) {
			return fog;
		}

		@Override
		public double fogEnd(ServerPlayer viewer) {
			return fogEnd;
		}

		@Override
		public long day(MinecraftServer server) {
			return day;
		}

		@Override
		public Sc27Data data(MinecraftServer server) {
			return data;
		}

		@Override
		public boolean sees(ServerPlayer viewer, ServerPlayer other) {
			return sees != null ? sees : Sc27.inView(viewer, other);
		}

		List<CustomPacketPayload> to(ServerPlayer player) {
			return sent.getOrDefault(player.getUUID(), List.of());
		}

		<T> List<T> to(ServerPlayer player, Class<T> type) {
			return to(player).stream().filter(type::isInstance).map(type::cast).toList();
		}
	}

	// --- 27: the rules ---

	/**
	 * 27 fits a viewer outdoors at night, dusk or in a fog that counts, alone, while another player is 160+ off, in another
	 * dimension, or under the ground (no sky, 12+ under the surface) out of vanilla's name range (72+); a friend at 100 on the
	 * surface does not count, nor one under the ground at 60, whose real name would show through the rock.
	 */
	@GameTest
	public void sc27FitsOutdoorsAtNightAloneWhileSomeoneIsFarBelowOrElsewhere(GameTestHelper helper) {
		Sc27Config c = new Sc27Config();
		EntityConfig e = new EntityConfig();
		Sc27Rules.Where far = new Sc27Rules.Where(false, 200.0, 15, 0.0);
		Sc27Rules.Where surface100 = new Sc27Rules.Where(false, 100.0, 15, 0.0);
		Sc27Rules.Where deep80 = new Sc27Rules.Where(false, 80.0, 0, 20.0);
		Sc27Rules.Where deep60 = new Sc27Rules.Where(false, 60.0, 0, 20.0);
		Sc27Rules.Where shallow80 = new Sc27Rules.Where(false, 80.0, 0, 6.0);
		Sc27Rules.Where skyOver80 = new Sc27Rules.Where(false, 80.0, 9, 20.0);
		Sc27Rules.Where nether = Sc27Rules.Where.elsewhere();
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(far), c, e).isEmpty(), "night, alone, a friend 200 off");
		helper.assertTrue(Sc27Rules.fit(9, 18000, false, true, List.of(far), c, e).isPresent(), "indoors (sky 9)");
		helper.assertTrue(Sc27Rules.fit(15, 6000, false, true, List.of(far), c, e).isPresent(), "noon");
		helper.assertTrue(Sc27Rules.fit(15, 6000, true, true, List.of(far), c, e).isEmpty(), "noon in a fog that counts");
		helper.assertTrue(Sc27Rules.fit(15, 12600, false, true, List.of(far), c, e).isEmpty(), "dusk");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, false, List.of(far), c, e).isPresent(), "someone within 48");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(), c, e).isPresent(), "nobody else");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(surface100), c, e).isPresent(), "a friend at 100 on the surface");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(deep80), c, e).isEmpty(), "a friend 20 under the ground, 80 off");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(deep60), c, e).isPresent(), "a friend under the ground within the name's range");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(shallow80), c, e).isPresent(), "a friend only 6 under the surface");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(skyOver80), c, e).isPresent(), "a friend with sky over them");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(nether), c, e).isEmpty(), "a friend in the Nether");
		helper.assertTrue(Sc27Rules.fit(15, 18000, false, true, List.of(surface100, nether), c, e).isEmpty(), "one of two away");
		helper.assertTrue(Sc27Rules.UNDERGROUND_MIN > Sc27Rules.NAME_RANGE && Sc27Rules.NAME_REACH < Sc27Rules.NAME_RANGE, "the name's numbers");
		helper.succeed();
	}

	/** Once shown, the other player stays away (no end) until nearer than 100, or out of the ground within 68. */
	@GameTest
	public void sc27StaysOnUntilTheOtherComesNear(GameTestHelper helper) {
		Sc27Config c = new Sc27Config();
		helper.assertTrue(Sc27Rules.stillAway(new Sc27Rules.Where(false, 120.0, 15, 0.0), c), "120 off ended it");
		helper.assertFalse(Sc27Rules.stillAway(new Sc27Rules.Where(false, 90.0, 15, 0.0), c), "90 off on the surface kept it");
		helper.assertTrue(Sc27Rules.stillAway(new Sc27Rules.Where(false, 75.0, 0, 8.0), c), "still under the ground at 75 ended it");
		helper.assertFalse(Sc27Rules.stillAway(new Sc27Rules.Where(false, 75.0, 12, 8.0), c), "out of the ground at 75 kept it");
		helper.assertFalse(Sc27Rules.stillAway(new Sc27Rules.Where(false, 60.0, 0, 20.0), c), "within the name's range kept it");
		helper.assertTrue(Sc27Rules.stillAway(Sc27Rules.Where.elsewhere(), c), "another dimension ended it");
		helper.succeed();
	}

	/** Once every 3 in-game days per viewer, 4 in a world; the record saves and loads. */
	@GameTest
	public void sc27CapsAreDaysApartPerViewerAndAFewPerWorld(GameTestHelper helper) {
		Sc27Config c = new Sc27Config();
		helper.assertTrue(Sc27Rules.caps(-1, 10, 0, c).isEmpty(), "never had it");
		helper.assertTrue(Sc27Rules.caps(9, 10, 0, c).isPresent(), "a day ago");
		helper.assertTrue(Sc27Rules.caps(8, 10, 0, c).isPresent(), "two days ago");
		helper.assertTrue(Sc27Rules.caps(7, 10, 0, c).isEmpty(), "three days ago");
		helper.assertTrue(Sc27Rules.caps(-1, 10, 4, c).isPresent(), "four in the world");
		Sc27Data data = new Sc27Data();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		data.record(a, 3);
		data.record(b, 5);
		data.record(a, 9);
		Sc27Data back = Sc27Data.roundTrip(data);
		helper.assertTrue(back.lastDay(a) == 9 && back.lastDay(b) == 5 && back.count() == 3 && back.lastDay(UUID.randomUUID()) == -1,
				"the record did not come back: " + back.days() + " " + back.count());
		helper.succeed();
	}

	/**
	 * The band: without a fog that limits it, the far part of 36 to the name's reach; with one, 0.6 to 0.8 of the drawn fog end
	 * within it; none when the fog is too thick for it.
	 */
	@GameTest
	public void sc27BandFollowsTheFogAndTheName(GameTestHelper helper) {
		Sc27Config c = new Sc27Config();
		Sc27Rules.Band clear = Sc27Rules.band(Double.NaN, c);
		helper.assertTrue(clear.outer() == Sc27Rules.NAME_REACH && clear.inner() == Sc27Rules.NAME_REACH - Sc27Rules.FAR_PART && !clear.empty(), "clear " + clear);
		helper.assertTrue(Sc27Rules.band(300.0, c).equals(clear), "a far fog changed it");
		Sc27Rules.Band fog = Sc27Rules.band(60.0, c);
		helper.assertTrue(Math.abs(fog.outer() - 48.0) < 1.0E-9 && Math.abs(fog.inner() - 36.0) < 1.0E-9, "fog end 60: " + fog);
		helper.assertTrue(Sc27Rules.band(40.0, c).empty(), "fog end 40 left a band");
		Sc27Config narrow = new Sc27Config();
		narrow.distanceMax = 44.0;
		Sc27Rules.Band n = Sc27Rules.band(Double.NaN, narrow);
		helper.assertTrue(n.outer() == 44.0 && n.inner() == 36.0, "a nearer distanceMax: " + n);
		helper.assertTrue(Sc27Rules.offFacing(0.0, 60.0) && Sc27Rules.offFacing(0.0, -45.0) && !Sc27Rules.offFacing(0.0, 20.0)
				&& !Sc27Rules.offFacing(0.0, 120.0) && Sc27Rules.offFacing(170.0, -110.0), "off the facing");
		helper.succeed();
	}

	/**
	 * The spot: off the viewer's facing by 40 to 80 degrees, in the band and within the name's reach, on ground with sky over it,
	 * the head and chest in plain sight from the eyes. Under a roof there is none, nor behind a ring of wall.
	 */
	@GameTest(maxTicks = 200)
	public void sc27SpotIsOffTheFacingInPlainSightWithSkyOverIt(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = ground(helper, 0, 70);
		Vec3 viewerFeet = feet(o, 0, 0);
		Vec3 eye = viewerFeet.add(0.0, 1.62, 0.0);
		Sc27Rules.Band band = Sc27Rules.band(Double.NaN, new Sc27Config());
		int found = 0;
		for (int seed = 0; seed < 12; seed++) {
			float yaw = seed * 30.0F;
			Optional<Sc27Spot.Spot> spot = Sc27Spot.find(level, eye, viewerFeet, yaw, band, Double.NaN, RandomSource.create(seed));
			if (spot.isEmpty()) {
				continue;
			}
			found++;
			Vec3 at = spot.get().feet();
			double h = Math.hypot(at.x - viewerFeet.x, at.z - viewerFeet.z);
			helper.assertTrue(h >= band.inner() - 0.5 && h <= band.outer() + 0.5 && eye.distanceTo(at) <= Sc27Rules.NAME_REACH, "out of the band: " + h);
			helper.assertTrue(Sc27Rules.offFacing(yaw, Sc27Rules.yawToward(eye.x, eye.z, at.x, at.z)), "not 40 to 80 off the facing");
			helper.assertTrue(level.canSeeSky(BlockPos.containing(at)) && Sight.wholly(level, eye, at) && Math.abs(at.y - o.getY()) < 1.0E-6,
					"no sky, not in plain sight, or not on the floor: " + at);
			float facing = Sc27Rules.yawToward(at.x, at.z, eye.x, eye.z);
			helper.assertTrue(Sc27Rules.yawGap(facing, spot.get().yaw()) < 1.0, "the copy does not face the viewer");
		}
		helper.assertTrue(found >= 10, "only " + found + " of 12 found a spot on open ground");
		// R10-8: a viewer turning toward a growing yaw finds it on that side, near the edge of their screen (still off it).
		int toward = 0;
		int turned = 0;
		for (int seed = 0; seed < 12; seed++) {
			float yaw = seed * 30.0F;
			Optional<Sc27Spot.Spot> spot = Sc27Spot.find(level, eye, viewerFeet, yaw, band, Double.NaN, RandomSource.create(seed), 1);
			if (spot.isEmpty()) {
				continue;
			}
			turned++;
			Vec3 at = spot.get().feet();
			float gap = net.minecraft.util.Mth.wrapDegrees(Sc27Rules.yawToward(eye.x, eye.z, at.x, at.z) - yaw);
			toward += gap > 0 ? 1 : 0;
			helper.assertTrue(Sc27Rules.offFacing(yaw, Sc27Rules.yawToward(eye.x, eye.z, at.x, at.z)), "turning, on the screen");
		}
		helper.assertTrue(turned >= 10 && toward >= turned - 1, "the turning side in only " + toward + " of " + turned);
		// A ring of wall 3 high at 20 hides every spot (no plain sight).
		for (int a = 0; a < 360; a += 2) {
			double r = Math.toRadians(a);
			BlockPos at = BlockPos.containing(viewerFeet.x + Math.cos(r) * 20.0, o.getY(), viewerFeet.z + Math.sin(r) * 20.0);
			fill(level, at, at.above(3), Blocks.STONE.defaultBlockState());
		}
		helper.assertTrue(Sc27Spot.find(level, eye, viewerFeet, 0.0F, band, Double.NaN, RandomSource.create(1)).isEmpty(), "a spot behind the wall");
		// Under a roof 6 up (the sight passes under it, the sky does not reach the ground): none either.
		for (int a = 0; a < 360; a += 2) {
			double r = Math.toRadians(a);
			BlockPos at = BlockPos.containing(viewerFeet.x + Math.cos(r) * 20.0, o.getY(), viewerFeet.z + Math.sin(r) * 20.0);
			fill(level, at, at.above(3), Blocks.AIR.defaultBlockState());
		}
		fill(level, o.offset(-70, 6, -70), o.offset(70, 6, 70), Blocks.STONE.defaultBlockState());
		helper.assertTrue(Live.settleLight(level, new BoundingBox(o.getX() - 70, o.getY() - 2, o.getZ() - 70, o.getX() + 70, o.getY() + 7, o.getZ() + 70)),
				"the light did not settle");
		helper.assertTrue(Sc27Spot.find(level, eye, viewerFeet, 0.0F, band, Double.NaN, RandomSource.create(1)).isEmpty(), "a spot under a roof");
		fill(level, o.offset(-70, 6, -70), o.offset(70, 6, 70), Blocks.AIR.defaultBlockState());
		helper.succeed();
	}

	/** The walk off: up a block (a jump), down three, never four; turning round a wall; never onto water. */
	@GameTest(maxTicks = 200)
	public void sc27WalkStepsUpOneDownThreeAndNeverIntoWater(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = ground(helper, 1, 24);
		BlockState stone = Blocks.STONE.defaultBlockState();
		// Lane A (x 0): a step up of one at z 3.
		fill(level, o.offset(0, 0, 3), o.offset(0, 0, 6), stone);
		Walk.Step up = Walk.next(level, feet(o, 0, 2), 0.0, 1.0);
		helper.assertTrue(up != null && Math.abs(up.feet().y - (o.getY() + 1)) < 1.0E-6 && up.yaw() == 0.0, "no step up: " + up);
		// Lane B (x 6): a wall three high across at z 3: it turns.
		fill(level, o.offset(4, 0, 3), o.offset(8, 2, 3), stone);
		Walk.Step turned = Walk.next(level, feet(o, 6, 2), 0.0, 1.0);
		helper.assertTrue(turned != null && turned.yaw() != 0.0 && BlockPos.containing(turned.feet()).getZ() < o.getZ() + 3, "walked into the wall: " + turned);
		// Lane C (x 12): water in the floor at z 3 (and around it): never onto it.
		fill(level, o.offset(10, -1, 3), o.offset(14, -1, 4), Blocks.WATER.defaultBlockState());
		Walk.Step wet = Walk.next(level, feet(o, 12, 2), 0.0, 1.0);
		helper.assertTrue(wet == null || level.getFluidState(BlockPos.containing(wet.feet()).below()).isEmpty()
				&& level.getFluidState(BlockPos.containing(wet.feet())).isEmpty(), "stepped onto water: " + wet);
		// Lane D (x 18): from a ledge 3 up, down to the floor; from 4 up, not.
		fill(level, o.offset(18, 0, 0), o.offset(18, 2, 2), stone);
		Walk.Step down3 = Walk.next(level, new Vec3(o.getX() + 18.5, o.getY() + 3, o.getZ() + 2.5), 0.0, 1.0);
		helper.assertTrue(down3 != null && Math.abs(down3.feet().y - o.getY()) < 1.0E-6 && down3.yaw() == 0.0, "no drop of 3: " + down3);
		fill(level, o.offset(20, 0, 0), o.offset(22, 3, 2), stone);
		helper.assertTrue(Walk.standing(level, o.getX() + 21.5, o.getZ() + 3.5, o.getY() + 4, Walk.MAX_UP, Walk.MAX_DOWN) == null, "a drop of 4 taken");
		helper.assertTrue(Math.abs(Walk.groundBelow(level, o.getX() + 0.5, o.getZ() + 4.5, o.getY() + 2.0, 4) - (o.getY() + 1)) < 1.0E-6, "the ground below");
		helper.succeed();
	}

	/** The way off turns toward cover: a wall off to one side that hides a walker past the name's range wins over open land. */
	@GameTest(maxTicks = 200)
	public void sc27WayOffTurnsTowardCover(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = ground(helper, 2, 80);
		Vec3 eye = feet(o, 0, 0).add(0.0, 1.62, 0.0);
		Vec3 spot = feet(o, 0, 50);
		helper.assertTrue(Sc27Spot.hiddenAfter(level, eye, spot, 0.0, Double.NaN) == -1, "open land hid it");
		helper.assertTrue(Math.abs(Sc27Spot.wayOff(level, eye, spot, Double.NaN)) < 1.0E-6, "open land: not straight away");
		// A wall 6 high at z 58 from x -30 to x -12: a walker turned toward it passes its end and is hidden behind it.
		fill(level, o.offset(-30, 0, 58), o.offset(-12, 5, 58), Blocks.STONE.defaultBlockState());
		int left = Sc27Spot.hiddenAfter(level, eye, spot, 50.0, Double.NaN);
		helper.assertTrue(left > 0, "turned toward the wall, never hidden");
		double way = Sc27Spot.wayOff(level, eye, spot, Double.NaN);
		helper.assertTrue(way > 0.0 && Sc27Spot.hiddenAfter(level, eye, spot, way, Double.NaN) > 0, "the way off is not toward the wall: " + way);
		// A fog that ends at 70 hides a walker anywhere past 66.5: straight away is then as good.
		helper.assertTrue(Sc27Spot.hiddenAfter(level, eye, spot, 0.0, 70.0) > 0, "the fog did not hide it");
		helper.succeed();
	}

	// --- 27: the server ---

	private static ServerPlayer viewer(ServerLevel level, BlockPos o) {
		return player(level, "viewer", GameType.SURVIVAL, feet(o, 0, 0), 0.0F);
	}

	/**
	 * The payload goes to the viewer alone, with the other player's UUID, a spot, their main hand, armour and model, and is
	 * counted; nobody else, the other player or a third player far off, gets anything; a second one waits while one is on.
	 */
	@GameTest(maxTicks = 200)
	public void sc27GoesToTheViewerOnlyWithWhatTheOtherHolds(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = ground(helper, 3, 70);
		ServerPlayer viewer = viewer(level, o);
		ServerPlayer friend = player(level, "friend", GameType.SURVIVAL, feet(o, 400, 0), 0.0F);
		friend.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
		friend.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		friend.setMainArm(HumanoidArm.LEFT);
		ServerPlayer third = player(level, "third", GameType.SURVIVAL, feet(o, -100, 0), 0.0F);
		Hooks27 hooks = new Hooks27();
		hooks.players.addAll(List.of(viewer, friend, third));
		Sc27 sc27 = new Sc27(hooks);
		helper.assertTrue(sc27.fit(viewer).isEmpty(), "does not fit: " + sc27.fit(viewer));
		helper.assertTrue(sc27.awayOthers(viewer).equals(List.of(friend)), "away: " + sc27.awayOthers(viewer));
		FireResult result = sc27.fire(viewer, RandomSource.create(3), false);
		helper.assertTrue(result == FireResult.FIRED, "not shown: " + result);
		List<Sc27Payload> shown = hooks.to(viewer, Sc27Payload.class);
		helper.assertTrue(shown.size() == 1 && hooks.to(friend).isEmpty() && hooks.to(third).isEmpty(), "sent to the wrong clients: " + hooks.sent);
		Sc27Payload p = shown.getFirst();
		Sc27Config c = Sc27Config.get();
		helper.assertTrue(p.other().equals(friend.getUUID()) && !p.staged() && p.leftHanded() && p.lifeTicks() == c.lifeTicks() && p.lookTicks() == c.lookTicks()
				&& p.near() == (float) c.nearLimit, "the payload: " + p);
		helper.assertTrue(p.items().get(0).is(Items.IRON_PICKAXE) && p.items().get(2).is(Items.LEATHER_HELMET) && p.items().get(1).isEmpty(), "the items: " + p.items());
		helper.assertTrue(Sc27Spot.valid(level, viewer.getEyePosition(), viewer.position(), viewer.getYHeadRot(), Sc27Rules.band(Double.NaN, c), p.spot()),
				"the spot breaks its rules: " + p.spot());
		// R10-8: counted only once the viewer's client saw it, once; another key counts nothing.
		helper.assertTrue(hooks.data.count() == 0 && hooks.data.lastDay(viewer.getUUID()) == -1, "counted when sent");
		sc27.seen(viewer, p.key() + 99);
		helper.assertTrue(hooks.data.count() == 0, "counted for another showing");
		sc27.seen(viewer, p.key());
		sc27.seen(viewer, p.key());
		helper.assertTrue(hooks.data.count() == 1 && hooks.data.lastDay(viewer.getUUID()) == hooks.day, "not counted once seen: " + hooks.data.count());
		helper.assertTrue(sc27.showing(viewer.getUUID()).map(s -> s.counted() && s.lifeTicks() >= CopyRules.UNSEEN_LIFE_TICKS).orElse(false),
				"the showing does not wait its unseen life");
		helper.assertTrue(sc27.fire(viewer, RandomSource.create(4), false) == FireResult.SKIPPED && sc27.fit(viewer).isPresent(), "a second while one is on");
		// The payload's own codec keeps everything.
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
		try {
			Sc27Payload.CODEC.encode(buf, p);
			Sc27Payload back = Sc27Payload.CODEC.decode(buf);
			helper.assertTrue(back.key() == p.key() && back.other().equals(p.other()) && back.spot().equals(p.spot()) && back.yaw() == p.yaw()
					&& back.walkYaw() == p.walkYaw() && back.parts() == p.parts() && back.leftHanded() && !back.staged() && back.items().size() == Sc27Payload.ITEMS
					&& back.items().get(0).is(Items.IRON_PICKAXE), "the codec lost something: " + back);
		} finally {
			buf.release();
		}
		helper.succeed();
	}

	/** Alone means nobody within 48, spectators included: a spectator 30 off refuses it, 60 off does not. */
	@GameTest(maxTicks = 200)
	public void sc27RefusesWithASpectatorWithin48(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = ground(helper, 4, 70);
		ServerPlayer viewer = viewer(level, o);
		ServerPlayer friend = player(level, "friend", GameType.SURVIVAL, feet(o, 300, 0), 0.0F);
		ServerPlayer spectator = player(level, "spectator", GameType.SPECTATOR, feet(o, 30, 0), 0.0F);
		Hooks27 hooks = new Hooks27();
		hooks.players.addAll(List.of(viewer, friend));
		hooks.bystanders.add(spectator);
		Sc27 sc27 = new Sc27(hooks);
		helper.assertTrue(sc27.fit(viewer).orElse("").startsWith("someone within"), "a spectator 30 off: " + sc27.fit(viewer));
		spectator.setPos(feet(o, 60, 0));
		helper.assertTrue(sc27.fit(viewer).isEmpty(), "a spectator 60 off: " + sc27.fit(viewer));
		helper.succeed();
	}

	/**
	 * The end comes to the viewer's client when the other player comes within 100, stops taking part, comes into the viewer's
	 * view, the story holds it or the layer goes off; a viewer who left is forgotten without a word. Nobody else gets it.
	 */
	@GameTest(maxTicks = 200)
	public void sc27EndsWhenTheOtherComesNearLeavesIsSeenOrTheSwitchGoesOff(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos o = ground(helper, 5, 70);
		ServerPlayer viewer = viewer(level, o);
		ServerPlayer friend = player(level, "friend", GameType.SURVIVAL, feet(o, 300, 0), 0.0F);
		Hooks27 hooks = new Hooks27();
		hooks.players.addAll(List.of(viewer, friend));
		Sc27 sc27 = new Sc27(hooks);

		helper.assertTrue(sc27.fire(viewer, RandomSource.create(1), false) == FireResult.FIRED, "not shown (1)");
		sc27.check(server);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).isEmpty() && sc27.showing(viewer.getUUID()).isPresent(), "ended while 300 off");
		friend.setPos(feet(o, 120, 0));
		sc27.check(server);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).isEmpty(), "ended at 120");
		friend.setPos(feet(o, 90, 0));
		sc27.check(server);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).size() == 1 && sc27.showing(viewer.getUUID()).isEmpty(), "not ended at 90");

		friend.setPos(feet(o, 300, 0));
		helper.assertTrue(sc27.fire(viewer, RandomSource.create(2), false) == FireResult.FIRED, "not shown (2)");
		sc27.onParticipation(server, friend.getUUID(), false);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).size() == 2 && sc27.showings().isEmpty(), "not ended when they left");

		helper.assertTrue(sc27.fire(viewer, RandomSource.create(3), false) == FireResult.FIRED, "not shown (3)");
		hooks.sees = true;
		sc27.check(server);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).size() == 3, "not ended once in view");
		hooks.sees = false;

		helper.assertTrue(sc27.fire(viewer, RandomSource.create(4), false) == FireResult.FIRED, "not shown (4)");
		hooks.story = Optional.of("the layer is off");
		sc27.check(server);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).size() == 4 && sc27.showings().isEmpty(), "not ended by the switch");
		helper.assertTrue(sc27.fire(viewer, RandomSource.create(5), false) == FireResult.SKIPPED, "shown while off");
		hooks.story = Optional.empty();

		helper.assertTrue(sc27.fire(viewer, RandomSource.create(6), false) == FireResult.FIRED, "not shown (6)");
		sc27.endAll(server);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).size() == 5, "endAll sent nothing");

		helper.assertTrue(sc27.fire(viewer, RandomSource.create(7), false) == FireResult.FIRED, "not shown (7)");
		hooks.players.remove(viewer);
		sc27.check(server);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).size() == 5 && sc27.showings().isEmpty(), "a viewer who left was told, or kept");
		helper.assertTrue(hooks.to(friend).isEmpty(), "the other player was sent something");
		helper.succeed();
	}

	/**
	 * R12-38: the viewer changing level (through a portal while it waits off the screen) ends the showing with its end
	 * payload, so a copy still waiting on the client never stands in the new level at the old spot.
	 */
	@GameTest(maxTicks = 200)
	public void sc27EndsWithItsPayloadWhenTheViewerChangesLevel(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos o = ground(helper, 5, 70);
		ServerPlayer viewer = viewer(level, o);
		ServerPlayer friend = player(level, "friend", GameType.SURVIVAL, feet(o, 300, 0), 0.0F);
		Hooks27 hooks = new Hooks27();
		hooks.players.addAll(List.of(viewer, friend));
		Sc27 sc27 = new Sc27(hooks);
		helper.assertTrue(sc27.fire(viewer, RandomSource.create(1), false) == FireResult.FIRED, "not shown");
		ServerLevel nether = server.getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "no Nether");
		viewer.setServerLevel(nether);
		sc27.check(server);
		helper.assertTrue(hooks.to(viewer, Sc27EndPayload.class).size() == 1 && sc27.showings().isEmpty(), "the client was not told: "
				+ hooks.to(viewer, Sc27EndPayload.class));
		helper.succeed();
	}

	/** R12-39: the apex's line of sight is asked only where every chunk on its way is loaded (it would load them). */
	@GameTest(maxTicks = 200)
	public void sc31SightIsAskedOnlyOverLoadedChunks(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Vec3 here = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		helper.assertTrue(Apex.loadedAlong(level, here, here.add(3.0, 0.0, 3.0)), "a short loaded segment");
		Vec3 far = here.add(40_000.0, 0.0, 40_000.0);
		helper.assertFalse(level.isLoaded(BlockPos.containing(far)), "setup: the far end is loaded");
		helper.assertFalse(Apex.loadedAlong(level, here, far), "a segment into unloaded chunks");
		helper.succeed();
	}

	/**
	 * Where the other player is: in another dimension away; under the ground (a sealed cell 20 under the floor, no sky) 80 off,
	 * away; the same cell 60 off, not (their name shows through the rock); on the surface 120 off, not.
	 */
	@GameTest(maxTicks = 400)
	public void sc27FitsAFriendInTheNetherOrDeepUnderTheGround(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		BlockPos o = ground(helper, 6, 90);
		ServerPlayer viewer = viewer(level, o);
		Sc27Config c = new Sc27Config();
		ServerLevel nether = server.getLevel(Level.NETHER);
		helper.assertTrue(nether != null, "no Nether");
		ServerPlayer inNether = player(nether, "nether", GameType.SURVIVAL, new Vec3(0.5, 64.0, 0.5), 0.0F);
		helper.assertTrue(Sc27.where(viewer, inNether).otherLevel() && Sc27Rules.away(Sc27.where(viewer, inNether), c), "the Nether is not away");
		// A sealed cell 20 under the floor, 80 off and 60 off.
		BlockState stone = Blocks.STONE.defaultBlockState();
		for (int x : new int[] {80, 60}) {
			fill(level, o.offset(x - 2, -24, -2), o.offset(x + 2, -3, 2), stone);
			fill(level, o.offset(x, -22, 0), o.offset(x, -21, 0), Blocks.AIR.defaultBlockState());
		}
		helper.assertTrue(Live.settleLight(level, new BoundingBox(o.getX() + 50, o.getY() - 30, o.getZ() - 10, o.getX() + 90, o.getY() + 2, o.getZ() + 10)),
				"the light did not settle");
		ServerPlayer deep80 = player(level, "deep", GameType.SURVIVAL, feet(o, 80, 0).add(0.0, -22.0, 0.0), 0.0F);
		ServerPlayer deep60 = player(level, "shallow", GameType.SURVIVAL, feet(o, 60, 0).add(0.0, -22.0, 0.0), 0.0F);
		Sc27Rules.Where w80 = Sc27.where(viewer, deep80);
		helper.assertTrue(w80.skyLight() < Sc27Rules.NO_SKY && w80.depth() >= 12.0 && Sc27Rules.away(w80, c), "80 off under the ground: " + w80);
		helper.assertFalse(Sc27Rules.away(Sc27.where(viewer, deep60), c), "60 off under the ground counted");
		ServerPlayer surface = player(level, "surface", GameType.SURVIVAL, feet(o, 85, 30), 0.0F);
		helper.assertFalse(Sc27Rules.away(Sc27.where(viewer, surface), c), "90 off on the surface counted");
		Hooks27 hooks = new Hooks27();
		hooks.players.addAll(List.of(viewer, deep80));
		helper.assertTrue(new Sc27(hooks).fit(viewer).isEmpty(), "the viewer with a friend deep 80 off: " + new Sc27(hooks).fit(viewer));
		helper.succeed();
	}

	/** {@code sc_27}: MINOR, Traces, FOG and UNSHARED, WATCHER, no fake. */
	@GameTest
	public void sc27CardHasItsShape(GameTestHelper helper) {
		EventCard card = CardRegistry.get(Sc27.CARD_ID).orElseThrow(() -> helper.assertionException("no card " + Sc27.CARD_ID));
		helper.assertTrue(card.tier() == Tier.MINOR && card.earliestStage() == Stage.TRACES && card.tags().equals(Set.of(CardTag.FOG, CardTag.UNSHARED))
				&& card.habits().equals(Set.of(Habit.WATCHER)) && !card.hasFake(), "sc_27's shape");
		helper.succeed();
	}

	/**
	 * The copy's rules on the client: seen while on the screen and within the name's range whatever is between; past it only in
	 * plain sight, not fogged out, not lost in the dark; looked at in the screen's middle third; put there only well off the
	 * screen. Standing: the viewer near makes it go (gone unseen, else off), the look and the end make it walk off.
	 */
	@GameTest
	public void sc27CopyIsSeenByItsNameOrItsBody(GameTestHelper helper) {
		ScreenshotSpot.View view = new ScreenshotSpot.View(new Vec3(0.0, 1.62, 0.0), 0.0F, 0.0F, 70.0, 16.0 / 9.0, 720);
		Vec3 ahead = new Vec3(0.0, 0.0, 40.0);
		helper.assertTrue(CopyRules.onScreen(view, ahead, CopyRules.ON_SCREEN_MARGIN) && CopyRules.middleThird(view, ahead), "straight ahead");
		helper.assertTrue(CopyRules.seen(true, 40.0, false, 1.0, 0, 30.0), "its name within 64 is not seen");
		helper.assertFalse(CopyRules.seen(true, 80.0, false, 0.0, 15, 30.0), "behind a hill past 64 is seen");
		helper.assertTrue(CopyRules.seen(true, 80.0, true, 0.5, 15, 30.0), "in plain sight at 80 is not seen");
		helper.assertFalse(CopyRules.seen(true, 80.0, true, 0.95, 15, 30.0), "in the fog's end is seen");
		helper.assertFalse(CopyRules.seen(true, 90.0, true, 0.2, 2, 10.0), "lost in the dark is seen");
		helper.assertFalse(CopyRules.seen(false, 20.0, true, 0.0, 15, 80.0), "off the screen is seen");
		Vec3 behind = new Vec3(0.0, 0.0, -40.0);
		helper.assertFalse(CopyRules.onScreen(view, behind, CopyRules.ON_SCREEN_MARGIN), "behind the camera on the screen");
		Vec3 aside45 = new Vec3(-40.0, 0.0, 40.0);
		helper.assertTrue(CopyRules.onScreen(view, aside45, 0.0) && !CopyRules.middleThird(view, aside45), "45 degrees aside");
		Vec3 aside62 = new Vec3(-40.0 * Math.tan(Math.toRadians(62.0)), 0.0, 40.0);
		helper.assertFalse(CopyRules.onScreen(view, aside62, CopyRules.APPEAR_MARGIN), "62 degrees aside would pop in");
		helper.assertTrue(CopyRules.standing(true, 20.0, 28.0, 0, 60, 10, 500, false) == CopyRules.Next.LEAVE, "near and seen");
		helper.assertTrue(CopyRules.standing(false, 20.0, 28.0, 0, 60, 10, 500, false) == CopyRules.Next.GONE, "near and unseen");
		helper.assertTrue(CopyRules.standing(true, 50.0, 28.0, 60, 60, 10, 500, false) == CopyRules.Next.LEAVE, "looked at");
		helper.assertTrue(CopyRules.standing(true, 50.0, 28.0, 59, 60, 10, 500, false) == CopyRules.Next.STAY, "not looked at long enough");
		helper.assertTrue(CopyRules.standing(false, 50.0, 28.0, 0, 60, 500, 500, false) == CopyRules.Next.GONE, "its life over, unseen");
		helper.assertTrue(CopyRules.standing(true, 50.0, 28.0, 0, 60, 500, 500, false) == CopyRules.Next.LEAVE, "its life over, seen");
		helper.assertTrue(CopyRules.standing(true, 50.0, 28.0, 0, 60, 10, 500, true) == CopyRules.Next.LEAVE, "ended, seen");
		helper.assertTrue(CopyRules.standing(false, 50.0, 28.0, 0, 60, 10, 500, true) == CopyRules.Next.GONE, "ended, unseen");
		// R10-8: never seen, it stands up to 40 s; seen late, long enough to be looked at.
		helper.assertTrue(CopyRules.life(500, 60, -1) == CopyRules.UNSEEN_LIFE_TICKS && CopyRules.life(500, 60, 100) == 500
				&& CopyRules.life(500, 60, 700) == 780, "its life: " + CopyRules.life(500, 60, 700));
		helper.assertTrue(CopyRules.standing(false, 50.0, 28.0, 0, 60, 600, CopyRules.life(500, 60, -1), false) == CopyRules.Next.STAY,
				"unseen at 30 s it is gone already");
		helper.assertTrue(CopyRules.ghost(5.0) && !CopyRules.ghost(7.0) && CopyRules.SPRINT > Walk.SPEED && CopyRules.SPRINT_JUMP > CopyRules.SPRINT,
				"caught up with, and the run");
		helper.succeed();
	}

	// --- 31: the top ---

	private static Remains.Place pyramid(BlockPos apex, Remains.Kind kind) {
		BoundingBox box = new BoundingBox(apex.getX() - 20, apex.getY() - 20, apex.getZ() - 20, apex.getX() + 20, apex.getY(), apex.getZ() + 20);
		return new Remains.Place(kind, Level.OVERWORLD, box, apex.below(19), apex.below(19).east(20), Direction.EAST, Optional.of(apex), 1L, 1);
	}

	/**
	 * 31's spot: the top of a known place 56 to 110 off (R11-4), its block giving no light, room on it, in plain sight from the
	 * player's eyes, out of every watcher's view (the player looking away; looking at it, none), ticking there. Too near, too
	 * far, a lit top, a wall in the way, another kind or no top: none. Of two tops, the one 64 to 85 off, the sunset behind it.
	 */
	@GameTest(maxTicks = 300)
	public void sc31TopIsTakenFromAKnownPlaceInReachOutOfViewInPlainSight(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = ground(helper, 7, 16);
		load(level, o.offset(0, 0, 80), 8);
		load(level, o.offset(0, 0, 40), 48); // every chunk on the line of sight (R12-39: it is asked over loaded chunks only)
		BlockPos apex = o.offset(0, 9, 80);
		fill(level, o.offset(0, 0, 80), apex, Blocks.BRICKS.defaultBlockState());
		helper.assertTrue(Apex.score(75.0, -75.0, 0.0) > Apex.score(58.0, -58.0, 0.0) && Apex.score(75.0, -75.0, 0.0) > Apex.score(75.0, 75.0, 0.0)
				&& Apex.score(100.0, -100.0, 0.0) > Apex.score(100.0, 100.0, 0.0), "the band 64 to 85 first, then the sunset behind it");
		fill(level, apex.above(), apex.above(3), Blocks.AIR.defaultBlockState());
		ServerPlayer looking = player(level, "looking", GameType.SURVIVAL, feet(o, 0, 0), 0.0F); // +z, toward it
		ServerPlayer away = player(level, "away", GameType.SURVIVAL, feet(o, 0, 0), 180.0F);
		Sc31Config c = new Sc31Config();
		Remains.Place place = pyramid(apex, Remains.Kind.PYRAMID);
		Optional<Apex.Spot> spot = Apex.spot(away, List.of(place), far(away), v -> true, c);
		helper.assertTrue(spot.isPresent() && spot.get().feet().equals(Vec3.atBottomCenterOf(apex.above())), "no spot on the top: " + spot);
		helper.assertTrue(Math.abs(Sc27Rules.yawGap(spot.get().yaw(), HimEntity.yawToward(spot.get().feet(), away.position()))) < 1.0, "not facing the player");
		helper.assertTrue(Apex.spot(looking, List.of(place), far(looking), v -> true, c).isEmpty(), "spawned in the player's view");
		helper.assertTrue(Apex.spot(away, List.of(place), far(away), v -> false, c).isEmpty(), "where nothing ticks");
		Sc31Config near = new Sc31Config();
		near.apexMin = 200.0;
		Sc31Config far = new Sc31Config();
		far.apexMax = 60.0;
		helper.assertTrue(Apex.spot(away, List.of(place), far(away), v -> true, near).isEmpty(), "nearer than apexMin");
		helper.assertTrue(Apex.spot(away, List.of(place), far(away), v -> true, far).isEmpty(), "farther than apexMax");
		helper.assertTrue(Apex.spot(away, List.of(pyramid(apex, Remains.Kind.ARCHIVE)), far(away), v -> true, c).isEmpty(), "another kind");
		Remains.Place noTop = new Remains.Place(Remains.Kind.PYRAMID, Level.OVERWORLD, place.box(), place.anchor(), place.entrance(), Direction.EAST,
				Optional.empty(), 1L, 1);
		helper.assertTrue(Apex.spot(away, List.of(noTop), far(away), v -> true, c).isEmpty(), "a place with no top");
		level.setBlock(apex, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(Apex.spot(away, List.of(place), far(away), v -> true, c).isEmpty(), "a lit top");
		level.setBlock(apex, Blocks.BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		fill(level, o.offset(-3, 0, 20), o.offset(3, 5, 20), Blocks.STONE.defaultBlockState());
		helper.assertTrue(Apex.spot(away, List.of(place), far(away), v -> true, c).isEmpty(), "a top out of plain sight");
		helper.succeed();
	}

	/**
	 * R11-4, the figure: on a flat top (the 3 x 3 level) he stands at its edge toward the player, never in its middle, where
	 * the rim hid his legs from below; a single-block point keeps him on it; a lit middle still refuses the top.
	 */
	@GameTest(maxTicks = 300)
	public void sc31StandsAtTheFlatTopsEdgeTowardThePlayer(GameTestHelper helper) {
		BlockPos c = new BlockPos(100, 70, -40);
		helper.assertTrue(Apex.towardEdge(c, 100.5, 0.0).equals(c.offset(0, 0, 1)), "south: " + Apex.towardEdge(c, 100.5, 0.0));
		helper.assertTrue(Apex.towardEdge(c, 0.0, -40.5).equals(c.offset(-1, 0, 0)), "west: " + Apex.towardEdge(c, 0.0, -40.5));
		helper.assertTrue(Apex.towardEdge(c, 160.5, 20.5).equals(c.offset(1, 0, 1)), "south-east: " + Apex.towardEdge(c, 160.5, 20.5));
		helper.assertTrue(Apex.towardEdge(c, 40.5, -100.5).equals(c.offset(-1, 0, -1)), "north-west: " + Apex.towardEdge(c, 40.5, -100.5));
		helper.assertTrue(Apex.towardEdge(c, 100.5, -39.5).equals(c), "straight over it");

		ServerLevel level = helper.getLevel();
		BlockPos o = ground(helper, 9, 16); // its own slot: the spot test beside it builds a wall on slot 7
		load(level, o.offset(0, 0, 80), 8);
		load(level, o.offset(0, 0, 40), 48);
		BlockPos apex = o.offset(0, 9, 80);
		fill(level, o.offset(-1, 0, 79), apex.offset(1, 0, 1), Blocks.BRICKS.defaultBlockState());
		fill(level, apex.offset(-1, 1, -1), apex.offset(1, 3, 1), Blocks.AIR.defaultBlockState());
		ServerPlayer away = player(level, "edge", GameType.SURVIVAL, feet(o, 0, 0), 180.0F);
		Sc31Config cfg = new Sc31Config();
		Remains.Place place = pyramid(apex, Remains.Kind.PYRAMID);
		Optional<Apex.Spot> spot = Apex.spot(away, List.of(place), far(away), v -> true, cfg);
		Vec3 edge = Vec3.atBottomCenterOf(apex.offset(0, 1, -1)); // the player is north of it (-z)
		helper.assertTrue(spot.isPresent() && spot.get().feet().equals(edge), "not at the edge toward the player: " + spot);
		level.setBlock(apex, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(Apex.spot(away, List.of(place), far(away), v -> true, cfg).isEmpty(), "a lit top");
		level.setBlock(apex, Blocks.BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		level.setBlock(apex.offset(0, 1, -1), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		Optional<Apex.Spot> middle = Apex.spot(away, List.of(place), far(away), v -> true, cfg);
		helper.assertTrue(middle.isEmpty() || middle.get().feet().equals(Vec3.atBottomCenterOf(apex.above())), "no room at the edge: " + middle);
		helper.succeed();
	}

	/**
	 * 31's figure: a variant of its own whose spot is given (no {@code sighting_*} card), standing still facing the player, the
	 * ridge's stare back and leave, never going under, the zombie fake; spawned for the player at the given spot, recorded as the
	 * day's sighting, never in anyone's view. 31c's: the trunks' hiding, eyes FLAT, its trunk kept.
	 */
	@GameTest(structure = YARD, maxTicks = 200)
	public void sc31FiguresAreGivenVariantsAndCountAsTheDaysSighting(GameTestHelper helper) {
		floor(helper, 24);
		ServerLevel level = helper.getLevel();
		Variant apex = Variant.APEX;
		helper.assertTrue(!apex.isCard() && apex.spot() == Variant.Spot.GIVEN && apex.ending() == Variant.Ending.STARE_LEAVE && apex.fake() == Variant.Fake.ZOMBIE
				&& !apex.mayGoUnder() && !apex.tracksPlayer() && apex.eyeStyle().isEmpty() && !apex.vanishesInView() && apex.shortName().equals("s31"),
				"31's variant");
		Variant trunks = Variant.INSIDE_TRUNKS;
		helper.assertTrue(!trunks.isCard() && trunks.spot() == Variant.Spot.GIVEN && trunks.ending() == Variant.Ending.HIDE && trunks.tracksPlayer()
				&& trunks.eyeStyle().equals(Optional.of(EyeStyle.FLAT)) && trunks.earliestStage() == Stage.PROXIMITY && trunks.fake() == Variant.Fake.NONE,
				"31c's variant");
		helper.assertTrue(FigureApi.isGivenBySc2(apex) && FigureApi.isGivenBySc2(trunks) && !FigureApi.isGivenBySc2(Variant.GLIMPSE)
				&& !FigureApi.isGivenBySc2(Variant.RIDGE), "the given variants");
		Vec3 playerFeet = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		ServerPlayer player = player(level, "p", GameType.SURVIVAL, playerFeet, 180.0F); // looking -z, away
		Vec3 spot = helper.absoluteVec(new Vec3(18.5, 1.0, 20.5));
		EntityData data = new EntityData();
		long today = GameClock.day(level.getServer());
		helper.assertTrue(FigureApi.spawnGiven(level, player, Variant.GLIMPSE, spot, 0.0F, null, nobody(), data).result() == FireResult.SKIPPED, "the glimpse");
		ServerPlayer facing = player(level, "f", GameType.SURVIVAL, playerFeet, HimEntity.yawToward(playerFeet, spot));
		helper.assertTrue(FigureApi.spawnGiven(level, player, apex, spot, 0.0F, null, far(facing), data).result() == FireResult.NO_SPOT,
				"spawned in view");
		FigureApi.Spawned spawned = FigureApi.spawnGiven(level, player, apex, spot, HimEntity.yawToward(spot, playerFeet), null, far(player), data);
		helper.assertTrue(spawned.result() == FireResult.FIRED && spawned.figure() != null, "not spawned: " + spawned.result());
		HimEntity him = spawned.figure();
		helper.assertTrue(him.variant() == apex && player.getUUID().equals(him.appearedFor()) && him.eyeStyle().isEmpty(), "his setup");
		helper.assertTrue(data.lastVariant().equals("s31") && !data.dayAllows(today, ModConfig.pacing().sightingsPerDayMax) && data.sightings() == 1,
				"not the day's sighting");
		him.discard();
		BlockPos trunk = helper.absolutePos(new BlockPos(12, 1, 12));
		Vec3 behind = helper.absoluteVec(new Vec3(12.5, 1.0, 14.0));
		FigureApi.Spawned hid = FigureApi.spawnGiven(level, null, trunks, behind, 0.0F, trunk, nobody(), null);
		helper.assertTrue(hid.result() == FireResult.FIRED && hid.figure() != null, "31c not spawned");
		helper.assertTrue(hid.figure().eyeStyle().equals(Optional.of(EyeStyle.FLAT)) && trunk.equals(hid.figure().anchor()) && hid.figure().appearedFor() == null,
				"31c's setup");
		hid.figure().discard();
		helper.succeed();
	}

	/** Every sighting rule holds for 31: one a day (shared with every other sighting), never the same variant twice, 300 apart. */
	@GameTest
	public void sc31DayRulesAreSharedWithEverySighting(GameTestHelper helper) {
		EntityData data = new EntityData();
		var pacing = ModConfig.pacing();
		GlobalPos here = GlobalPos.of(Level.OVERWORLD, new BlockPos(0, 64, 0));
		GlobalPos far = GlobalPos.of(Level.OVERWORLD, new BlockPos(5000, 64, 0));
		helper.assertTrue(Apex.dayRules(data, 5, Variant.APEX, here, pacing).isEmpty(), "a fresh world");
		data.recordSighting(Variant.RIDGE.shortName(), here, 5);
		helper.assertTrue(Apex.dayRules(data, 5, Variant.APEX, far, pacing).orElse("").equals("already one today"), "a ridge today let 31 through");
		helper.assertTrue(Apex.dayRules(data, 6, Variant.APEX, far, pacing).isEmpty(), "the next day");
		data.recordSighting(Variant.APEX.shortName(), far, 6);
		helper.assertTrue(!data.dayAllows(6, pacing.sightingsPerDayMax), "31 is not the day's sighting for the others");
		helper.assertTrue(Apex.dayRules(data, 7, Variant.APEX, here, pacing).orElse("").equals("same variant as last time"), "31 twice running");
		helper.assertTrue(Apex.dayRules(data, 7, Variant.INSIDE_TRUNKS, GlobalPos.of(Level.OVERWORLD, new BlockPos(5100, 64, 0)), pacing).orElse("")
				.equals("too close to the last sighting"), "300 apart");
		EntityConfig e = new EntityConfig();
		helper.assertTrue(SightingRules.duskSky(12600, false, e) && !SightingRules.duskSky(6000, false, e) && !SightingRules.duskSky(18000, false, e),
				"the ridge's dusk");
		helper.succeed();
	}

	/** {@code sc_31}: MINOR, Traces, SIGHTING, WATCHER, a fake; {@code sc_31c}: MINOR, Proximity, SIGHTING, STRIPPER, no fake. */
	@GameTest
	public void sc31CardsHaveTheirShapes(GameTestHelper helper) {
		EventCard top = CardRegistry.get(Apex.CARD_ID).orElseThrow(() -> helper.assertionException("no card " + Apex.CARD_ID));
		EventCard inside = CardRegistry.get(InsideTrunks.CARD_ID).orElseThrow(() -> helper.assertionException("no card " + InsideTrunks.CARD_ID));
		helper.assertTrue(top.tier() == Tier.MINOR && top.earliestStage() == Stage.TRACES && top.tags().equals(Set.of(CardTag.SIGHTING))
				&& top.habits().equals(Set.of(Habit.WATCHER)) && top.hasFake(), "sc_31's shape");
		helper.assertTrue(inside.tier() == Tier.MINOR && inside.earliestStage() == Stage.PROXIMITY && inside.tags().equals(Set.of(CardTag.SIGHTING))
				&& inside.habits().equals(Set.of(Habit.STRIPPER)) && !inside.hasFake(), "sc_31c's shape");
		helper.assertTrue(CardRegistry.get(Variant.APEX.cardId()).isEmpty() && CardRegistry.get(Variant.INSIDE_TRUNKS.cardId()).isEmpty(),
				"a sighting_* card for a given variant");
		helper.succeed();
	}

	/** 31's fake: an existing husk moved onto the top out of view and held there facing the player, counted as a fake. */
	@GameTest(structure = YARD, maxTicks = 200)
	public void sc31FakeHoldsAHuskOnTheTop(GameTestHelper helper) {
		floor(helper, 24);
		ServerLevel level = helper.getLevel();
		BlockPos column = helper.absolutePos(new BlockPos(16, 1, 16));
		for (int y = 0; y < 3; y++) {
			level.setBlock(column.above(y), Blocks.BRICKS.defaultBlockState(), Block.UPDATE_CLIENTS);
		}
		Vec3 top = Vec3.atBottomCenterOf(column.above(3));
		Husk husk = EntityTypes.HUSK.create(level, EntitySpawnReason.COMMAND);
		helper.assertTrue(husk != null, "no husk");
		Vec3 at = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		husk.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
		helper.assertTrue(level.addFreshEntity(husk), "the husk did not spawn");
		ServerPlayer player = player(level, "p", GameType.SURVIVAL, helper.absoluteVec(new Vec3(2.5, 1.0, 20.5)), 180.0F);
		EntityData data = new EntityData();
		Apex.Spot spot = new Apex.Spot(top, HimEntity.yawToward(top, player.position()), pyramid(column.above(2), Remains.Kind.PYRAMID), 120.0);
		boolean held = Apex.fake(player, spot, Services.mobs(), data);
		try {
			helper.assertTrue(held, "no husk was put on the top");
			helper.assertTrue(BlockPos.containing(husk.position()).equals(BlockPos.containing(top)) && Services.mobs().isTampered(husk) && data.fakes() == 1,
					"the husk is not held on the top: " + husk.position());
		} finally {
			Services.mobs().release(husk);
			husk.discard();
		}
		helper.succeed();
	}

	// --- 31c: among the trunks inside ---

	/** A test's place with trunks: whatever it holds is inside it. */
	private static Remains remainsOf(Remains.Place place) {
		return new Remains() {
			@Override
			public List<Place> known(ServerLevel level) {
				return List.of(place);
			}

			@Override
			public Optional<Place> containing(ServerLevel level, BlockPos pos) {
				return place.boxHolds(pos) ? Optional.of(place) : Optional.empty();
			}

			@Override
			public Optional<Place> nearest(ServerLevel level, BlockPos pos, Set<Kind> kinds, int radius) {
				return kinds.contains(place.kind()) ? Optional.of(place) : Optional.empty();
			}

			@Override
			public boolean visited(UUID player, Place place) {
				return false;
			}

			@Override
			public long trespassedAt(UUID player) {
				return -1L;
			}
		};
	}

	/**
	 * 31c: inside a place with a grove, within 40 of its middle; half of him behind one of its trunks 18 to 40 off as seen from
	 * the player's eyes, at the edge of the player's light (3 to 7 at his eyes), out of view. In the full dark, too far from the
	 * grove, another kind of place, or lights by every trunk: none.
	 */
	@GameTest(maxTicks = 400)
	public void sc31cTrunkSpotIsHalfBehindATrunkInTheDark(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos o = ground(helper, 8, 40);
		BlockState stone = Blocks.STONE.defaultBlockState();
		// A sealed room 70 x 10 x 70 round o: walls and a roof of stone.
		fill(level, o.offset(-36, 0, -36), o.offset(36, 11, -36), stone);
		fill(level, o.offset(-36, 0, 36), o.offset(36, 11, 36), stone);
		fill(level, o.offset(-36, 0, -36), o.offset(-36, 11, 36), stone);
		fill(level, o.offset(36, 0, -36), o.offset(36, 11, 36), stone);
		fill(level, o.offset(-36, 11, -36), o.offset(36, 11, 36), stone);
		int[][] at = {{-4, -3}, {0, -5}, {4, -2}, {-3, 3}, {3, 4}, {0, 1}};
		List<BlockPos> trunks = new ArrayList<>();
		for (int[] a : at) {
			BlockPos base = o.offset(a[0], 0, a[1]);
			fill(level, base, base.above(6), Blocks.OAK_LOG.defaultBlockState());
			trunks.add(base);
		}
		BoundingBox roomBox = new BoundingBox(o.getX() - 35, o.getY(), o.getZ() - 35, o.getX() + 35, o.getY() + 10, o.getZ() + 35);
		helper.assertTrue(Live.settleLight(level, roomBox.inflatedBy(2)), "the light did not settle");
		Remains.Place place = new Remains.Place(Remains.Kind.HOLLOW, Level.OVERWORLD, roomBox, o, o.offset(0, 0, -35), Direction.NORTH, Optional.empty(), 3L, 0);
		Remains remains = remainsOf(place);
		Sc31Config c = new Sc31Config();
		ServerPlayer player = player(level, "p", GameType.SURVIVAL, feet(o, 0, -26), 180.0F); // looking -z, away from the grove
		helper.assertTrue(InsideTrunks.place(player, remains, c).isPresent(), "not inside the place");
		helper.assertTrue(InsideTrunks.trunks(level, o).size() == trunks.size(), "the trunks: " + InsideTrunks.trunks(level, o));
		helper.assertTrue(InsideTrunks.spot(player, place, far(player), v -> true).isEmpty(), "a spot in the full dark, where nobody sees him");
		// A light the player set down 12 short of the grove: its edge reaches the trunks.
		level.setBlock(o.offset(0, 0, -12), Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
		helper.assertTrue(Live.settleLight(level, roomBox.inflatedBy(2)), "the light did not settle (1)");
		Optional<InsideTrunks.Spot> spot = InsideTrunks.spot(player, place, far(player), v -> true);
		helper.assertTrue(spot.isPresent(), "no spot behind a trunk");
		InsideTrunks.Spot s = spot.get();
		helper.assertTrue(trunks.contains(s.trunk()) && s.distance() >= 18.0 && s.distance() <= 40.0, "the trunk's distance: " + s.distance());
		helper.assertTrue(SpotFinder.halfVisible(level, player.getEyePosition(), s.feet(), s.trunk()), "not half behind the trunk");
		int light = level.getMaxLocalRawBrightness(BlockPos.containing(s.feet().add(0.0, 1.62, 0.0)));
		helper.assertTrue(light >= InsideTrunks.MIN_LIGHT && light <= InsideTrunks.MAX_LIGHT, "the light at his eyes: " + light);
		ServerPlayer looking = player(level, "l", GameType.SURVIVAL, feet(o, 0, -26), 0.0F); // toward the grove
		helper.assertTrue(InsideTrunks.spot(looking, place, far(looking), v -> true).isEmpty(), "spawned in the player's view");
		ServerPlayer farOff = player(level, "far", GameType.SURVIVAL, feet(o, 0, -33), 180.0F);
		Sc31Config tight = new Sc31Config();
		tight.trunksMax = 30.0;
		helper.assertTrue(InsideTrunks.place(farOff, remains, tight).isEmpty(), "33 off with trunksMax 30");
		Remains.Place archive = new Remains.Place(Remains.Kind.ARCHIVE, Level.OVERWORLD, roomBox, o, o, Direction.NORTH, Optional.empty(), 3L, 0);
		helper.assertTrue(InsideTrunks.place(player, remainsOf(archive), c).isEmpty(), "another kind of place");
		// A torch on every side of every trunk: no dark spot is left.
		for (BlockPos trunk : trunks) {
			for (Direction side : Direction.Plane.HORIZONTAL) {
				BlockPos t = trunk.relative(side).above(2);
				if (level.getBlockState(t).isAir()) {
					level.setBlock(t, Blocks.GLOWSTONE.defaultBlockState(), Block.UPDATE_CLIENTS);
				}
			}
		}
		helper.assertTrue(Live.settleLight(level, roomBox.inflatedBy(2)), "the light did not settle (2)");
		helper.assertTrue(InsideTrunks.spot(player, place, far(player), v -> true).isEmpty(), "a spot by the lights");
		helper.succeed();
	}
}
