package com.forzacode.a1016_02.atmosphere;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.card.SharedMobGameTests;
import com.forzacode.a1016_02.atmosphere.card.ZombieAtDuskCard;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.atmosphere.mob.TamperState;
import com.forzacode.a1016_02.atmosphere.mob.TamperedMob;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteType;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Shared moments and per-player bookkeeping (MP-5, D-060, D-062). Each test brings its own online list through a
 * {@link PlayerWatch} with its own {@link PlayerWatch.Source}: mock players are never in the server's list, and the
 * test never depends on who else is on the shared game test server. {@link AtmosphereGameTests} extends this
 * (through {@link DuskFogGameTests}).
 */
public class SharedMomentGameTests extends SharedMobGameTests {
	/** A test's own online players and state; nobody's participation is reported anywhere. */
	static final class Roster implements PlayerWatch.Source {
		final List<ServerPlayer> online = new ArrayList<>();
		final HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);

		Roster(ServerPlayer... players) {
			online.addAll(List.of(players));
		}

		@Override
		public List<ServerPlayer> online(MinecraftServer server) {
			return online;
		}

		@Override
		public HerobrineState state(MinecraftServer server) {
			return state;
		}

		@Override
		public void participation(MinecraftServer server, UUID player, boolean participating) {
		}
	}

	private static ServerPlayer mock(GameTestHelper helper, Vec3 relative) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(relative);
		player.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		return player;
	}

	private static List<UUID> uuids(List<ServerPlayer> players) {
		return players.stream().map(ServerPlayer::getUUID).toList();
	}

	/**
	 * A fog surge and a silence for A reach A and B (30 blocks off, inside the shared-moment radius) at the same moment
	 * and not C (100 blocks off); each one who got it is remembered by UUID, so a relog gives A and B theirs back and
	 * C, who never had one, nothing.
	 */
	@GameTest
	public void aSharedFogSurgeReachesEveryoneNearAndIsRememberedPerPlayer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		helper.assertTrue(ModConfig.pacing().sharedMomentRadius >= 31 && ModConfig.pacing().sharedMomentRadius < 100,
				"the shared-moment radius is not between B and C: " + ModConfig.pacing().sharedMomentRadius);
		helper.assertFalse(Gates.quietForGood(server), "the world is quiet for good, so no surge can be tested");
		ServerPlayer a = mock(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer b = mock(helper, new Vec3(31.5, 1.0, 1.5));
		ServerPlayer c = mock(helper, new Vec3(101.5, 1.0, 1.5));
		Roster roster = new Roster(a, b, c);

		List<ServerPlayer> surged = ActiveEffects.fogSurge(a, 0.6F, 10, 400, 20, roster.watch::sharingWith);
		helper.assertTrue(uuids(surged).equals(List.of(a.getUUID(), b.getUUID())), "the surge went to " + uuids(surged));
		helper.assertTrue(ActiveEffects.surgeLeft(server, a.getUUID()) > 0 && ActiveEffects.surgeLeft(server, b.getUUID()) > 0,
				"a player who got the surge is not remembered");
		helper.assertTrue(ActiveEffects.surgeLeft(server, c.getUUID()) == 0, "C never got the surge, yet it is remembered for C");

		List<ServerPlayer> silenced = ActiveEffects.silence(a, 1200, 400, roster.watch::sharingWith);
		helper.assertTrue(uuids(silenced).equals(List.of(a.getUUID(), b.getUUID())), "the silence went to " + uuids(silenced));
		AtmosphereData data = AtmosphereData.get(server);
		helper.assertTrue(data.transientOf(a.getUUID()).isPresent() && data.transientOf(b.getUUID()).isPresent(), "a silenced player is not remembered");
		helper.assertTrue(data.transientOf(c.getUUID()).isEmpty(), "C never had the silence, yet it is remembered for C");

		// A relog: each fresh client gets back its own, and only its own.
		for (ServerPlayer got : List.of(a, b)) {
			ActiveEffects.Resync back = ActiveEffects.pending(got);
			helper.assertTrue(back.surge() != null && back.surge().strength() == 0.6F && back.silenceLeft() > 0 && back.compass() == null,
					"a relog does not give back the surge and silence: " + back);
		}
		helper.assertFalse(ActiveEffects.pending(c).any(), "C would get back what C never had: " + ActiveEffects.pending(c));

		// C, far from the others: C's own moment is C's alone.
		List<ServerPlayer> alone = ActiveEffects.fogSurge(c, 0.5F, 10, 100, 20, roster.watch::sharingWith);
		helper.assertTrue(uuids(alone).equals(List.of(c.getUUID())), "a surge for C reached " + uuids(alone));
		helper.succeed();
	}

	/**
	 * Mob tricks bound to one player end once that player is gone (nobody here can see them): released when they leave
	 * (or turn spectator), when the look-up no longer finds them, or when they die; a trick for someone else is kept.
	 * Tricks bound to friends sharing a moment: {@link com.forzacode.a1016_02.atmosphere.card.SharedMobGameTests}.
	 */
	@GameTest
	public void tamperedMobsAreReleasedWhenTheirPlayerGoes(GameTestHelper helper) {
		TamperGameTests.floor(helper);
		MinecraftServer server = helper.getLevel().getServer();
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		ServerPlayer a = mock(helper, new Vec3(1.5, 1.0, 6.5));
		ServerPlayer b = mock(helper, new Vec3(6.5, 1.0, 6.5));
		ServerPlayer c = mock(helper, new Vec3(3.5, 1.0, 6.5));
		Roster roster = new Roster(a, b, c);
		Cow forA = helper.spawn(EntityTypes.COW, new BlockPos(1, 1, 1));
		Cow forB = helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 1));
		Cow forC = helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 3));
		Cow unbound = helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 6));
		try {
			for (Cow cow : List.of(forA, forB, forC, unbound)) {
				helper.assertTrue(tamper.freeze(cow, 400), "freeze refused");
			}
			tamper.bind(forA, a.getUUID());
			tamper.bind(forB, b.getUUID());
			tamper.bind(forC, c.getUUID());
			helper.assertTrue(tamper.releaseOrphans(server, roster.watch) == 0 && tamper.isFrozen(forA) && tamper.isFrozen(forB), "released with everyone here");

			// B leaves (still on the list at that moment, as when the disconnect is reported): B's cow goes.
			helper.assertTrue(tamper.releaseFor(server, b.getUUID(), roster.watch) == 1 && !tamper.isTampered(forB), "B left, yet B's cow is still held");
			helper.assertTrue(tamper.isFrozen(forA) && tamper.isFrozen(forC) && tamper.isFrozen(unbound), "B leaving released someone else's");

			roster.online.remove(a);
			c.setHealth(0.0F);
			helper.assertTrue(tamper.releaseOrphans(server, roster.watch) == 2, "the cows of a gone and a dead player were not both released");
			helper.assertFalse(tamper.isTampered(forA) || tamper.isTampered(forC), "a gone player's cow is still held");
			helper.assertTrue(tamper.isFrozen(unbound), "an unbound trick (another workstream's) was released");
		} finally {
			for (Cow cow : List.of(forA, forB, forC, unbound)) {
				tamper.release(cow);
				cow.discard();
			}
		}
		helper.succeed();
	}

	/**
	 * The zombie at dusk, fired for A, faces A (everyone sees it, it is the mob on the server) and is bound to A;
	 * when A goes it is a zombie again. In the End, where no other test keeps zombies the card could pick.
	 */
	@GameTest(dimension = "minecraft:the_end", structure = "a1016_02:accident/yard", maxTicks = 40)
	public void theZombieFacesItsPlayerAndLetsGoWhenTheyLeave(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
		zombie.setPersistenceRequired();
		ServerPlayer a = mock(helper, new Vec3(21.5, 1.0, 21.5));
		Roster roster = new Roster(a);
		try {
			FireResult result = new ZombieAtDuskCard().fire(new FireContext(a, level, false, true, RandomSource.create(1)));
			helper.assertTrue(result == FireResult.FIRED, "the card did not fire: " + result);
			TamperState state = ((TamperedMob) zombie).a1016_02$tamper();
			helper.assertTrue(state != null && tamper.isFrozen(zombie) && tamper.isSilenced(zombie), "the zombie is not held");
			helper.assertTrue(state.faceTarget() != null && state.faceTarget().distanceTo(a.getEyePosition()) < 1.0E-6, "the zombie does not face A");
			helper.assertTrue(a.getUUID().equals(tamper.ownerOf(zombie)), "the zombie is not bound to A");
			helper.assertTrue(tamper.releaseOrphans(level.getServer(), roster.watch) == 0 && tamper.isFrozen(zombie), "released with A here");
			roster.online.clear();
			helper.assertTrue(tamper.releaseOrphans(level.getServer(), roster.watch) == 1 && !tamper.isTampered(zombie), "A left, yet the zombie is held");
		} finally {
			tamper.release(zombie);
			zombie.discard();
		}
		helper.succeed();
	}

	/**
	 * Dead mountains are each client's own (D-042): A, standing on one, is told about it; B, far away, is told
	 * nothing; B's update never changes what A was told. In the End, so the circle never reaches an overworld test.
	 */
	@GameTest(dimension = "minecraft:the_end")
	public void deadMountainsAreSentPerPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(3, 1, 3));
		Services.sites().record(SiteType.DEAD_MOUNTAIN, level.dimension(), center, 1);
		DeadMountains.refresh();
		ServerPlayer a = mock(helper, new Vec3(3.5, 1.0, 3.5));
		ServerPlayer b = mock(helper, new Vec3(3.5, 1.0, 3.5));
		Vec3 far = a.position().add(5000.0, 0.0, 5000.0);
		b.snapTo(far.x, far.y, far.z, 0.0F, 0.0F);
		try {
			DeadMountainsPayload forA = DeadMountains.toSend(a);
			helper.assertTrue(forA != null && forA.areas().stream().anyMatch(area -> area.x() == center.getX() && area.z() == center.getZ()),
					"A, on the dead mountain, was not told about it: " + forA);
			helper.assertTrue(DeadMountains.toSend(b) == null && DeadMountains.sentTo(b).isEmpty(), "B, far away, was told about A's dead mountain");
			helper.assertTrue(DeadMountains.sentTo(a).equals(forA.areas()), "B's update changed what A was told");
			helper.assertTrue(DeadMountains.toSend(a) == null, "A is told the same again");
		} finally {
			DeadMountains.onLeave(a);
			DeadMountains.onLeave(b);
		}
		helper.succeed();
	}

	/** Music goes off on the first night any participant is online, once per world. */
	@GameTest
	public void musicGoesOffOnTheWorldsFirstNight(GameTestHelper helper) {
		helper.assertTrue(Gates.musicOffNow(false, true, true), "not off on the first night with a participant online");
		helper.assertFalse(Gates.musicOffNow(false, false, true), "off with nobody taking part");
		helper.assertFalse(Gates.musicOffNow(false, true, false), "off by day");
		helper.assertFalse(Gates.musicOffNow(true, true, true), "off twice in one world");
		helper.succeed();
	}
}
