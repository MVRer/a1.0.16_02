package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.Tasks;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Services;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * Mob tricks shared by friends (MP-7, D-060, D-062.5): a trick fired for A with B beside A goes on while either of
 * them is here, A gone (left, or dead), and ends only once neither is, out of view. And a scar at A's house never
 * happens next to a friend who is there. Each test brings its own online
 * list ({@link PlayerWatch.Source}) for its own card instance and steps the card's episode itself, all within one tick,
 * so the tamper's own check of the server's real players (every 20 ticks) never runs in between. In the End, padded
 * past the cards' search radius, so no other test's mobs are picked. {@code atmosphere.AtmosphereGameTests} extends
 * this (through {@code SharedMomentGameTests}).
 */
public class SharedMobGameTests extends CardSoundGameTests {
	private static final String YARD = "a1016_02:accident/yard";
	/** Past every radius these cards search (48): no other test's mobs are in reach. */
	private static final int APART = 48;

	/** A test's own online players and state; nobody's participation is reported anywhere. */
	private static final class Online implements PlayerWatch.Source {
		final List<ServerPlayer> online = new ArrayList<>();
		final HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);

		Online(ServerPlayer... players) {
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

	private static void ground(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static void release(List<? extends Mob> mobs) {
		for (Mob mob : mobs) {
			MobTamperImpl.INSTANCE.release(mob);
			mob.discard();
		}
	}

	/** Animals facing the fog, fired for A with B beside A: A leaves, then (with fresh players) A dies. */
	@GameTest(dimension = "minecraft:the_end", structure = YARD, padding = APART)
	public void animalsFacingTheFogStayForAFriendWhenTheirPlayerGoes(GameTestHelper helper) {
		ground(helper);
		List<Cow> cows = List.of(helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 4)), helper.spawn(EntityTypes.COW, new BlockPos(20, 1, 4)),
				helper.spawn(EntityTypes.COW, new BlockPos(4, 1, 20)));
		try {
			facingTheFog(helper, cows, false);
			facingTheFog(helper, cows, true);
		} finally {
			release(cows);
		}
		helper.succeed();
	}

	private static void facingTheFog(GameTestHelper helper, List<Cow> cows, boolean dies) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		String how = dies ? "A died" : "A left";
		ServerPlayer a = at(helper, new Vec3(12.5, 1.0, 12.5));
		ServerPlayer b = at(helper, new Vec3(14.5, 1.0, 12.5));
		Online online = new Online(a, b);
		AnimalsFaceFogCard card = new AnimalsFaceFogCard();
		card.watch = () -> online.watch;
		List<Tasks.Episode> episodes = new ArrayList<>();
		card.episodes = episodes::add;
		helper.assertTrue(card.fire(new FireContext(a, level, false, true, RandomSource.create(7))) == FireResult.FIRED && episodes.size() == 1,
				"the card did not fire one episode (" + how + ")");
		Tasks.Episode facing = episodes.getFirst();
		for (int i = 0; i <= AtmosphereConfig.get().animalsStaggerTicks + 1; i++) {
			helper.assertTrue(facing.tick(server), "the moment ended while both were there (" + how + ")");
		}
		for (Cow cow : cows) {
			helper.assertTrue(tamper.isFrozen(cow) && tamper.isFacing(cow), "a cow did not turn to the fog (" + how + ")");
			helper.assertTrue(tamper.ownersOf(cow).equals(List.of(a.getUUID(), b.getUUID())), "a cow is not held for both, A first (" + how + ")");
		}
		// A goes: for B, who shares the moment, the animals stay turned to the fog.
		if (dies) {
			a.setHealth(0.0F);
		} else {
			online.online.remove(a);
		}
		helper.assertTrue(tamper.releaseOrphans(server, online.watch) == 0, "the cows were let go in front of B (" + how + ")");
		for (int i = 0; i < 5; i++) {
			helper.assertTrue(facing.tick(server), "the moment ended with B still there (" + how + ")");
		}
		for (Cow cow : cows) {
			helper.assertTrue(tamper.isFrozen(cow), "a cow was let go with B still there (" + how + ")");
		}
		// B goes too: nobody it was for is left. While someone can still see them they stay; then they are let go.
		online.online.remove(b);
		for (int i = 0; i < 40 && facing.tick(server); i++) {
			// the episode ends
		}
		helper.assertTrue(tamper.releaseOrphans(server, online.watch, mob -> false) == 0, "the cows were let go in someone's view (" + how + ")");
		for (Cow cow : cows) {
			helper.assertTrue(tamper.isFrozen(cow), "a cow was let go in someone's view (" + how + ")");
		}
		tamper.releaseOrphans(server, online.watch);
		for (Cow cow : cows) {
			helper.assertFalse(tamper.isTampered(cow), "a cow still faces the fog with nobody it was for left (" + how + ")");
		}
	}

	/** The zombie at dusk, held for A with B beside A: kept while B is here, A gone; let go once B is gone too. */
	@GameTest(dimension = "minecraft:the_end", structure = YARD, padding = APART)
	public void aZombieHeldForAStaysForAFriendWhenAGoes(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		Zombie zombie = helper.spawn(EntityTypes.ZOMBIE, new BlockPos(3, 1, 3));
		zombie.setPersistenceRequired();
		ServerPlayer a = at(helper, new Vec3(21.5, 1.0, 21.5));
		ServerPlayer b = at(helper, new Vec3(19.5, 1.0, 21.5));
		Online online = new Online(a, b);
		try {
			ZombieAtDuskCard card = new ZombieAtDuskCard();
			card.watch = () -> online.watch;
			helper.assertTrue(card.fire(new FireContext(a, level, false, true, RandomSource.create(1))) == FireResult.FIRED && tamper.isFrozen(zombie),
					"the zombie is not held");
			helper.assertTrue(a.getUUID().equals(tamper.ownerOf(zombie)), "the zombie is not held for A first");
			helper.assertTrue(tamper.ownersOf(zombie).equals(List.of(a.getUUID(), b.getUUID())), "the zombie is not held for both: " + tamper.ownersOf(zombie));
			online.online.remove(a);
			helper.assertTrue(tamper.releaseOrphans(server, online.watch) == 0 && tamper.isFrozen(zombie), "A left, and the zombie was let go in front of B");
			helper.assertTrue(tamper.releaseFor(server, a.getUUID(), online.watch) == 0 && tamper.isFrozen(zombie), "A's leave let the zombie go in front of B");
			online.online.remove(b);
			helper.assertTrue(tamper.releaseOrphans(server, online.watch, mob -> false) == 0 && tamper.isFrozen(zombie), "let go in someone's view");
			helper.assertTrue(tamper.releaseOrphans(server, online.watch) == 1 && !tamper.isTampered(zombie), "B left too, yet the zombie is held");
		} finally {
			release(List.of(zombie));
		}
		helper.succeed();
	}

	/**
	 * One block missing, fired for A (away from home) while B is inside A's house: no wall block within
	 * {@code houseAwayBlocks} of B goes (B would hear and see it), as the door left open keeps away from everyone. Once B
	 * is away too, a block goes. Padded past the house search, so no other test's walls are in reach.
	 */
	@GameTest(dimension = "minecraft:the_end", structure = YARD, padding = APART, skyAccess = true)
	public void oneBlockMissingNeverTakesAWallBlockNearAFriendInside(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		ServerPlayer builder = at(helper, new Vec3(0.5, 1.0, 0.5));
		// A 6x6 house of placed planks: walls three high, a roof at y 4.
		List<BlockPos> walls = new ArrayList<>();
		for (int x = 1; x <= 6; x++) {
			for (int z = 1; z <= 6; z++) {
				placed(helper, builder, new BlockPos(x, 4, z));
				if (x == 1 || x == 6 || z == 1 || z == 6) {
					for (int y = 1; y <= 3; y++) {
						placed(helper, builder, new BlockPos(x, y, z));
						walls.add(helper.absolutePos(new BlockPos(x, y, z)));
					}
				}
			}
		}
		ServerPlayer a = at(helper, new Vec3(22.5, 1.0, 22.5));
		a.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(level.dimension(), helper.absolutePos(new BlockPos(3, 1, 3)), 0, 0), false),
				false);
		ServerPlayer b = at(helper, new Vec3(3.5, 1.0, 3.5));
		OneBlockMissingCard card = new OneBlockMissingCard();
		card.everyone = forLevel -> List.of(a, b);
		helper.assertTrue(card.fire(new FireContext(a, level, false, true, RandomSource.create(5))) != FireResult.FIRED, "a wall block went with B inside");
		for (BlockPos wall : walls) {
			helper.assertTrue(level.getBlockState(wall).is(Blocks.OAK_PLANKS), "a wall block went with B inside: " + helper.relativePos(wall));
		}
		// B goes out too, far off: now a block goes.
		b.snapTo(helper.absoluteVec(new Vec3(22.5, 1.0, 20.5)), 0.0F, 0.0F);
		helper.assertTrue(card.fire(new FireContext(a, level, false, true, RandomSource.create(5))) == FireResult.FIRED, "no block went with everyone away");
		helper.assertTrue(walls.stream().filter(wall -> level.getBlockState(wall).isAir()).count() == 1, "not exactly one wall block went");
		helper.succeed();
	}

	/** A block {@code builder} placed (the footprint), at a relative position. */
	private static void placed(GameTestHelper helper, ServerPlayer builder, BlockPos rel) {
		helper.setBlock(rel, Blocks.OAK_PLANKS);
		Services.watch().onPlaced(builder, helper.getLevel(), helper.absolutePos(rel), Blocks.OAK_PLANKS.defaultBlockState());
	}

	/** The patient skeleton, fired for A with B beside A: it watches on for B once A is gone, and is let go once B is too. */
	@GameTest(dimension = "minecraft:the_end", structure = YARD, padding = APART)
	public void thePatientSkeletonWatchesOnForAFriend(GameTestHelper helper) {
		ground(helper);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		MobTamperImpl tamper = MobTamperImpl.INSTANCE;
		Skeleton skeleton = helper.spawn(EntityTypes.SKELETON, new BlockPos(21, 1, 21));
		skeleton.setPersistenceRequired();
		ServerPlayer a = at(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer b = at(helper, new Vec3(3.5, 1.0, 1.5));
		Online online = new Online(a, b);
		try {
			PatientSkeletonCard card = new PatientSkeletonCard();
			card.watch = () -> online.watch;
			List<Tasks.Episode> episodes = new ArrayList<>();
			card.episodes = episodes::add;
			helper.assertTrue(card.fire(new FireContext(a, level, false, true, RandomSource.create(1))) == FireResult.FIRED && episodes.size() == 1,
					"the skeleton card did not fire one episode");
			Tasks.Episode follow = episodes.getFirst();
			helper.assertTrue(follow.tick(server) && tamper.isFrozen(skeleton), "the skeleton is not held");
			online.online.remove(a);
			helper.assertTrue(tamper.releaseOrphans(server, online.watch) == 0, "A left, and the skeleton was let go in front of B");
			for (int i = 0; i < 25; i++) {
				helper.assertTrue(follow.tick(server), "the skeleton stopped watching with B still there");
			}
			helper.assertTrue(tamper.isFrozen(skeleton), "the skeleton was let go with B still there");
			online.online.remove(b);
			for (int i = 0; i < 40 && follow.tick(server); i++) {
				// the episode ends
			}
			tamper.releaseOrphans(server, online.watch);
			helper.assertFalse(tamper.isTampered(skeleton), "the skeleton is still held with nobody it was for left");
		} finally {
			release(List.of(skeleton));
		}
		helper.succeed();
	}
}
