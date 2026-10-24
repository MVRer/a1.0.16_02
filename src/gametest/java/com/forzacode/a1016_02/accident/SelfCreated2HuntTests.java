package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import com.forzacode.a1016_02.accident.sc2.HuntData;
import com.forzacode.a1016_02.accident.sc2.HuntScene;
import com.forzacode.a1016_02.accident.sc2.Hunts;
import com.forzacode.a1016_02.accident.sc2.Prey;
import com.forzacode.a1016_02.accident.sc2.Reach;
import com.forzacode.a1016_02.accident.sc2.s25.Pack;
import com.forzacode.a1016_02.accident.sc2.s25.PackTrap;
import com.forzacode.a1016_02.accident.sc2.s25.Sc25Config;
import com.forzacode.a1016_02.accident.sc2.s26.Call;
import com.forzacode.a1016_02.accident.sc2.s26.CallTrap;
import com.forzacode.a1016_02.accident.sc2.s26.Sc26Config;
import com.forzacode.a1016_02.atmosphere.mob.MobTamperImpl;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.MarkedDeath;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.TraceService;
import net.minecraft.nbt.CompoundTag;
import com.forzacode.a1016_02.world.sc2.kit.Live;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.monster.skeleton.Skeleton;
import net.minecraft.world.entity.monster.spider.Spider;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.projectile.arrow.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-4, the two trap kinds of the second wave (docs/SELF_CREATED_2.md, 25 and 26; D-096, D-097, D-098): the pack and
 * the call. Their gates (each refusal), the pack's gather out of view, its band, the fair-danger contract one clause at a
 * time (told: 60 s, two long tells heard and one seen before any hostile act, measured; a way out; out of reach two up;
 * the health floor; a marked death with its cause; one at a time; home), their ends, their caps, multiplayer (alone with
 * a spectator; a friend arriving; the friend never set on; a leave; two never within 128), the call's kinds, its quiet
 * then its voices, its letting go at 16 and its door cap, and the switch. Every scene runs in its own patch of the test
 * server's flat overworld, far out (x about 17.6 to 19.2 million, z the same negative), its chunks held loaded, with mock
 * players of its own (a private {@link Group}), its own planner data and its own pacing data.
 */
public class SelfCreated2HuntTests {
	private static final MobTamperImpl TAMPER = MobTamperImpl.INSTANCE;
	/**
	 * The scenes steer real mobs and the world steers at most 48 at once (D-104), so the tests that steer many run in three
	 * batches of their own (a test environment is a batch), each under the cap.
	 */
	static final String ENV_A = "a1016_02:accident/sc2_hunts_a";
	static final String ENV_B = "a1016_02:accident/sc2_hunts_b";
	static final String ENV_C = "a1016_02:accident/sc2_hunts_c";

	// --- the shared rules ---

	/** D-097.2: a way out is two full blocks (any mix, sand too) or a pickaxe, a shovel or an axe; torches or a sword are not. */
	@GameTest
	public void aWayOutIsTwoFullBlocksOrADiggingTool(GameTestHelper helper) {
		Target p = new Target(helper.getLevel());
		helper.assertFalse(Hunts.wayOut(p), "an empty inventory");
		p.getInventory().add(new ItemStack(Items.DIRT, 1));
		helper.assertFalse(Hunts.wayOut(p), "one block");
		p.getInventory().add(new ItemStack(Items.COBBLESTONE, 1));
		helper.assertTrue(Hunts.wayOut(p), "a dirt and a cobblestone");
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.SAND, 2));
		helper.assertTrue(Hunts.wayOut(p), "two sand");
		p.getInventory().clearContent();
		p.getInventory().add(new ItemStack(Items.TORCH, 16));
		p.getInventory().add(new ItemStack(Items.IRON_SWORD));
		p.getInventory().add(new ItemStack(Items.OAK_LEAVES, 8));
		helper.assertFalse(Hunts.wayOut(p), "torches, a sword and leaves");
		for (ItemStack tool : List.of(new ItemStack(Items.WOODEN_PICKAXE), new ItemStack(Items.STONE_SHOVEL), new ItemStack(Items.GOLDEN_AXE))) {
			p.getInventory().clearContent();
			p.getInventory().add(tool);
			helper.assertTrue(Hunts.wayOut(p), "a " + tool.getItem());
		}
		helper.succeed();
	}

	/**
	 * The reach rule: on flat ground, or one block up, a wolf reaches the player; two up on a pillar it does not, unless a
	 * bump of one block stands within three, snow lies around (higher floors) or the player stands on a ledge of ground
	 * that goes on (a wolf walks round to it).
	 */
	@GameTest(maxTicks = 4000)
	public void twoUpOnAPillarIsOutOfReachAndALedgeOrABumpIsNot(GameTestHelper helper) {
		Field f = new Field(helper, 32);
		f.ticking = false;
		f.whenReady(() -> {
			BlockPos o = f.origin;
			ServerLevel level = f.level;
			List<Vec3> none = List.of();
			helper.assertFalse(Reach.outOfReach(level, Vec3.atBottomCenterOf(o), none), "on the ground");
			f.set(o, Blocks.COBBLESTONE);
			helper.assertFalse(Reach.outOfReach(level, Vec3.atBottomCenterOf(o.above()), none), "one up");
			f.set(o.above(), Blocks.COBBLESTONE);
			helper.assertTrue(Reach.outOfReach(level, Vec3.atBottomCenterOf(o.above(2)), none), "two up on a pillar");
			helper.assertTrue(Reach.outOfReach(level, Vec3.atBottomCenterOf(o.above(2)), List.of(Vec3.atBottomCenterOf(o.offset(2, 0, 0)))), "two up, a wolf below");
			f.set(o.offset(3, 0, 0), Blocks.COBBLESTONE);
			helper.assertFalse(Reach.outOfReach(level, Vec3.atBottomCenterOf(o.above(2)), none), "a bump of one, three off");
			f.set(o.offset(3, 0, 0), Blocks.AIR);
			f.set(o.offset(1, 0, 1), Blocks.SNOW.defaultBlockState().setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 2));
			helper.assertFalse(Reach.outOfReach(level, Vec3.atBottomCenterOf(o.above(2)), none), "snow beside the pillar");
			f.set(o.offset(1, 0, 1), Blocks.AIR);
			// A ledge: a step of 1, then 2 high, going on: they walk up to it.
			BlockPos l = o.offset(0, 0, 12);
			for (int x = -6; x <= 6; x++) {
				f.set(l.offset(x, 0, -1), Blocks.COBBLESTONE);
				for (int z = 0; z <= 6; z++) {
					f.set(l.offset(x, 0, z), Blocks.COBBLESTONE);
					f.set(l.offset(x, 1, z), Blocks.COBBLESTONE);
				}
			}
			helper.assertFalse(Reach.outOfReach(level, Vec3.atBottomCenterOf(l.above(2)), none), "on a ledge they can walk up to");
			f.done();
		}).thenSucceed();
	}

	/** The list's words: wolves, shot (a skeleton's arrow), bitten (a spider), else the trap's own; none of them offered by the debug mark. */
	@GameTest
	public void theListNamesTheKillersKind(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		DamageSources damage = level.damageSources();
		Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.COMMAND);
		Skeleton skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
		Spider spider = EntityTypes.SPIDER.create(level, EntitySpawnReason.COMMAND);
		Zombie zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
		Arrow arrow = EntityTypes.ARROW.create(level, EntitySpawnReason.COMMAND);
		helper.assertTrue(DeathCauses.byKiller(damage.mobAttack(wolf), "x").equals("wolves"), "a wolf");
		helper.assertTrue(DeathCauses.byKiller(damage.arrow(arrow, skeleton), "x").equals("shot"), "a skeleton's arrow");
		helper.assertTrue(DeathCauses.byKiller(damage.mobAttack(skeleton), "x").equals("x"), "a skeleton's blow");
		helper.assertTrue(DeathCauses.byKiller(damage.mobAttack(spider), "x").equals("bitten"), "a spider");
		helper.assertTrue(DeathCauses.byKiller(damage.mobAttack(zombie), "killed").equals("killed"), "a zombie");
		helper.assertTrue(Traps.SC25.word(damage.mobAttack(wolf), null).equals("wolves") && Traps.SC26.word(damage.arrow(arrow, skeleton), null).equals("shot"),
				"the traps' words");
		helper.assertTrue(DeathCauses.UNLISTED.stream().noneMatch(DeathCauses.KNOWN::contains), "the debug mark offers them: " + DeathCauses.KNOWN);
		helper.assertTrue(Traps.byId("sc25").isPresent() && Traps.byId("sc26").isPresent() && Traps.SC25.live() && Traps.SC26.live() && Traps.SC25.anywhere(),
				"the catalogue");
		helper.succeed();
	}

	// --- the pack: its gate ---

	/**
	 * Each refusal of the pack's gate, from a setting where it may start (alone, health 20, two blocks, dark, far from any
	 * base, three wild wolves out of view): its pacing (days apart, the world's count), the base within 64 (health and base
	 * floors hold even when the config says less), health under 14, no way out, too few wolves, a wolf in view at the
	 * gather, not dark, the day's roll, the layer off, the story quiet, another dimension; a forced arm relaxes the time,
	 * the base and the pacing but never the way out or the health.
	 */
	@GameTest(maxTicks = 4000)
	public void thePackIsRefusedByEachOfItsGates(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 180.0F);
			List<Wolf> wolves = List.of(f.wolf(-6.5, -70.5), f.wolf(0.5, -72.5), f.wolf(6.5, -70.5));
			AccidentPlannerImpl planner = f.planner();
			f.use(p);
			helper.assertTrue(f.why25(p, planner) == null, "the setting does not fit: " + f.why25(p, planner));
			helper.assertTrue(planner.candidates(p, Traps.SC25).size() == 1, "no candidate");

			f.hunts.record(PackTrap.ID, p.getUUID(), f.day[0] - 2);
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("had one on day"), "days apart: " + f.why25(p, planner));
			f.hunts.reset(PackTrap.ID);
			for (int i = 0; i < f.cfg25.maxPerWorld; i++) {
				f.hunts.record(PackTrap.ID, UUID.randomUUID(), 0);
			}
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("the world had"), "the world's count: " + f.why25(p, planner));
			f.forced = true;
			f.use(p);
			helper.assertTrue(f.why25(p, planner) == null, "forced, the pacing still holds it: " + f.why25(p, planner));
			f.forced = false;
			f.use(p);
			f.hunts.reset(PackTrap.ID);

			f.cfg25.fromBase = 10;
			p.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, f.origin.offset(30, 0, 0), 0.0F, 0.0F), true), false);
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("within 64 of their base"), "base 30 off: " + f.why25(p, planner));
			p.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, f.origin.offset(90, 0, 0), 0.0F, 0.0F), true), false);
			helper.assertTrue(f.why25(p, planner) == null, "base 90 off: " + f.why25(p, planner));
			p.setRespawnPosition(null, false);

			f.cfg25.minHealth = 5;
			p.setHealth(13.5F);
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("health"), "health 13.5: " + f.why25(p, planner));
			f.forced = true;
			f.use(p);
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("health"), "forced, under the floor: " + f.why25(p, planner));
			p.getInventory().clearContent();
			p.setHealth(20.0F);
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("carries no way out"), "forced, no way out: " + f.why25(p, planner));
			f.forced = false;
			f.use(p);
			p.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
			helper.assertTrue(f.why25(p, planner) == null, "back: " + f.why25(p, planner));

			wolves.get(2).discard();
			helper.assertTrue(planner.candidates(p, Traps.SC25).isEmpty(), "two wolves were enough");
			Wolf third = f.wolf(12.5, -66.5);
			helper.assertTrue(planner.candidates(p, Traps.SC25).size() == 1, "three again");
			f.view = Yard.eyesOf(f.viewerAt(12.5, -50.5, 180.0F));
			helper.assertTrue(f.planner().candidates(p, Traps.SC25).isEmpty(), "a wolf in view was taken");
			f.view = Yard.NOBODY;
			helper.assertTrue(third.isAlive(), "setup");

			f.dark[0] = false;
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("not dark"), "day: " + f.why25(p, planner));
			f.dark[0] = true;
			f.cfg25.nightChance = 0.0;
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("not tonight"), "the roll: " + f.why25(p, planner));
			f.cfg25.nightChance = 1.0;
			f.enabled = false;
			helper.assertTrue(String.valueOf(f.why25(p, planner)).contains("switched off"), "the layer off: " + f.why25(p, planner));
			f.enabled = true;
			f.group.state.setFlag("director:silence_forever", true);
			helper.assertTrue(f.why25(p, planner) != null, "the story quiet for good");
			f.group.state.setFlag("director:silence_forever", false);
			Target away = new Target(level(helper, Level.NETHER));
			f.group.online.add(away);
			f.use(away);
			away.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
			helper.assertTrue(String.valueOf(PackTrap.refusal(away, planner.context(away), PackTrap.env(away))).contains("overworld"), "the nether");
			f.done();
		}).thenSucceed();
	}

	/** Multiplayer, the alone rule: a friend or a spectator within 48 at the start refuses it; farther, it may start. */
	@GameTest(maxTicks = 4000)
	public void aFriendOrASpectatorWithin48RefusesThePack(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 180.0F);
			f.wolf(-6.5, -70.5);
			f.wolf(0.5, -72.5);
			f.wolf(6.5, -70.5);
			AccidentPlannerImpl planner = f.planner();
			f.use(p);
			helper.assertTrue(f.why25(p, planner) == null, "alone: " + f.why25(p, planner));
			Target friend = f.player(30.5, 0.5, 0.0F);
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("not alone"), "a friend 30 off: " + f.why25(p, planner));
			friend.mode = GameType.SPECTATOR;
			helper.assertTrue(String.valueOf(f.why25(p, planner)).startsWith("not alone"), "a spectator 30 off: " + f.why25(p, planner));
			friend.snapTo(f.vec(60.5, 0, 0.5), 0.0F, 0.0F);
			helper.assertTrue(f.why25(p, planner) == null, "a spectator 60 off: " + f.why25(p, planner));
			f.done();
		}).thenSucceed();
	}

	/** Twice as likely tonight for a player who was inside one of his places within 2 in-game days: the chance, and over 400 players. */
	@GameTest
	public void beingInsideOneOfHisPlacesDoublesTonightsChance(GameTestHelper helper) {
		Sc25Config cfg = new Sc25Config();
		// Version 1's day roll (FIX-SC2-PACE made 1.0 the default: the director's own clock paces the hunts); the factor
		// still doubles a lower one set by hand.
		cfg.nightChance = 0.5;
		long[] trespass = {-1};
		long now = 500_000;
		PackTrap.Env env = new PackTrap.Env(cfg, new HuntData(), l -> List.of(), () -> true, () -> 7, () -> now, () -> true, () -> null, uuid -> trespass[0], 42L,
				false);
		UUID one = UUID.randomUUID();
		helper.assertTrue(Math.abs(PackTrap.chance(env, one) - 0.5) < 1.0E-9, "no trespass: " + PackTrap.chance(env, one));
		trespass[0] = now - 30_000;
		helper.assertTrue(Math.abs(PackTrap.chance(env, one) - 1.0) < 1.0E-9, "a trespass a day ago: " + PackTrap.chance(env, one));
		trespass[0] = now - 60_000;
		helper.assertTrue(Math.abs(PackTrap.chance(env, one) - 0.5) < 1.0E-9, "two and a half days ago: " + PackTrap.chance(env, one));
		int plain = 0;
		int inside = 0;
		for (int i = 0; i < 400; i++) {
			UUID player = new UUID(i * 7919L, i * 104729L);
			trespass[0] = -1;
			plain += PackTrap.tonight(env, player) ? 1 : 0;
			trespass[0] = now - 1000;
			inside += PackTrap.tonight(env, player) ? 1 : 0;
		}
		helper.assertTrue(plain > 150 && plain < 250 && inside == 400, "nights that fit: " + plain + " plain, " + inside + " after a trespass");
		helper.assertTrue(PackTrap.roll(env, one) == PackTrap.roll(env, one), "the roll changes within a day");
		helper.succeed();
	}

	// --- the pack: arming, the gather, the band ---

	/**
	 * Armed through the planner: three wolves 70 off (3 at armor under 10) are moved out of view to 40 to 56 behind the
	 * player (the first within 44), the trap watches with them blamed, the day and the count are saved, its scene runs
	 * the first part and played its first long tell, heard by the player.
	 */
	@GameTest(environment = ENV_A, maxTicks = 900)
	public void theGatherMovesThePackOutOfViewBehindThePlayer(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 0.0F); // looking south: behind is north
			List<Wolf> wolves = List.of(f.wolf(-62.5, -10.5), f.wolf(0.5, -66.5), f.wolf(62.5, -10.5), f.wolf(-40.5, -70.5));
			f.view = Yard.eyesOf(p);
			f.use(p);
			AccidentPlannerImpl planner = f.planner();
			AccidentPlannerImpl.ArmResult result = planner.arm(p, Traps.SC25, false);
			helper.assertTrue(result.armed(), "not armed: " + result.message() + " / " + f.why25(p, planner));
			ArmedTrap trap = f.data.armed(p.getUUID()).orElseThrow();
			helper.assertTrue(trap.type().equals("sc25") && !trap.isSet() && trap.mobs().size() == 3, "the trap: " + trap);
			Pack pack = (Pack) Hunts.of(p.getUUID()).orElseThrow();
			helper.assertTrue(pack.phaseNow() == Pack.Phase.STALK && pack.mobs().equals(trap.mobs()), "the scene: " + pack.phase());
			double nearest = Double.MAX_VALUE;
			for (Wolf wolf : Hunts.loaded(f.level, trap.mobs(), Wolf.class)) {
				double d = across(wolf.position(), p.position());
				nearest = Math.min(nearest, d);
				helper.assertTrue(d >= 39.5 && d <= 56.5, "a wolf " + d + " off");
				helper.assertTrue(wolf.getZ() < p.getZ() - 20, "a wolf not behind: " + wolf.position());
				helper.assertTrue(TraceService.isOutOfView(f.level, wolf.getBoundingBox(), List.of(TraceService.Viewer.of(p, 8)), 3, 160), "a wolf in view");
			}
			helper.assertTrue(nearest <= 44.5, "the first is " + nearest + " off");
			helper.assertTrue(f.hunts.lastDay(PackTrap.ID, p.getUUID()) == f.day[0] && f.hunts.count(PackTrap.ID) == 1, "the day and the count");
			helper.assertTrue(pack.tells().size() >= 1 && pack.heardBefore(f.level.getServer().getTickCount() + 1) == 1, "the first long tell: " + pack.tells());
			helper.assertTrue(!trap.blames(wolves.get(3).getUUID()), "a fourth was taken at armor 0");
			// Its scene ending (here, told to go) runs the trap out: the planner's next look drops it.
			pack.stop(false, "test");
			long play = GameClock.playTicks(f.level.getServer());
			helper.assertTrue(pack.phaseNow() == Pack.Phase.LEAVE && f.data.armed(p.getUUID()).map(t -> t.until() < play).orElse(false), "the trap did not run out");
			planner.step(f.level.getServer(), f.data, AccidentConfig.get(), play);
			helper.assertTrue(f.data.armed(p.getUUID()).isEmpty(), "the planner kept it");
			f.done();
		}).thenSucceed();
	}

	/**
	 * The band: through the first part (before its closing) the pack keeps 16 to 24 from the player at night (R10-5; after
	 * its first walk in), follows them while they walk, and stands still when they stop.
	 */
	@GameTest(environment = ENV_A, maxTicks = 1200)
	public void thePackKeepsItsBandFollowsAndStopsWhenThePlayerStops(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.cfg25.stalkSecondsMin = 110; // the closing (its last 20 s) stays after this test
		List<String> off = new ArrayList<>();
		Target[] p = new Target[1];
		Pack[] pack = new Pack[1];
		Vec3[][] held = new Vec3[1][];
		f.whenReady(() -> {
			p[0] = f.player(0.5, 0.5, 180.0F);
			List<Wolf> wolves = List.of(f.wolf(-12.5, -46.5), f.wolf(0.5, -48.5), f.wolf(12.5, -46.5));
			pack[0] = f.pack(p[0], wolves);
		}).thenExecuteAfter(260, () -> {
		}).thenExecuteFor(300, () -> {
			// The player walks south (backwards, watching them) 5 blocks a second for 15 s (about a sprint).
			long t = helper.getTick();
			if (t % 20 == 0) {
				p[0].snapTo(p[0].position().add(0, 0, 5.0), 180.0F, 0.0F);
			}
			for (Wolf wolf : f.wolves(pack[0])) {
				double d = across(wolf.position(), p[0].position());
				if (!wolf.getUUID().equals(pack[0].shown()) && (d < 11.0 || d > 40.0)) {
					off.add((int) d + " at " + t);
				}
			}
		}).thenExecuteAfter(160, () -> {
			for (Wolf wolf : f.wolves(pack[0])) {
				double d = across(wolf.position(), p[0].position());
				com.forzacode.a1016_02.atmosphere.mob.TamperState state = ((com.forzacode.a1016_02.atmosphere.mob.TamperedMob) wolf).a1016_02$tamper();
				// The one shown (walked into the middle of the view, 17 to 20) holds 10 or more; the rest hold the band.
				boolean shown = wolf.getUUID().equals(pack[0].shown());
				helper.assertTrue(shown ? d >= 10.0 : d >= 14.0 && d <= 26.5, "after the walk, one is " + d + " off (" + pack[0].phase() + " " + pack[0].why()
						+ ", shown " + shown + ", steered "
						+ TAMPER.isSteered(wolf) + " to " + (state == null ? null : state.steerTarget()) + ", it at " + wolf.position() + ", the player at "
						+ p[0].position() + "; out of the band while walking: " + off + ")");
			}
			held[0] = f.wolves(pack[0]).stream().map(Entity::position).toArray(Vec3[]::new);
		}).thenExecuteAfter(80, () -> {
			List<Wolf> now = f.wolves(pack[0]);
			for (int i = 0; i < now.size() && i < held[0].length; i++) {
				helper.assertTrue(across(now.get(i).position(), held[0][i]) < 2.0, "one moved while the player stood still: " + across(now.get(i).position(), held[0][i]));
			}
			helper.assertTrue(off.size() <= 6, "out of the band while following: " + off);
			helper.assertTrue(pack[0].hostileAt() < 0 && p[0].bites == 0 && !pack[0].done(), "hostile or over in the first part: " + pack[0].why());
			f.done();
		}).thenSucceed();
	}

	// --- the fair-danger contract ---

	/**
	 * D-097.1, measured: armed through the planner with the default numbers, behind the player and out of view, nothing
	 * hurts the player before the pack turns; it turns no sooner than 60 s after it began, after two long tells the player
	 * heard and one wolf the player saw (one walks into view on its own when none was seen for 30 s); the trap is set then,
	 * and the bites come.
	 */
	@GameTest(environment = ENV_A, maxTicks = 4400)
	public void nothingTurnsBeforeSixtySecondsTwoLongTellsHeardAndOneSeen(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		Target[] p = new Target[1];
		Pack[] pack = new Pack[1];
		List<String> early = new ArrayList<>();
		f.whenReady(() -> {
			p[0] = f.player(0.5, 0.5, 0.0F);
			f.wolf(-20.5, 64.5);
			f.wolf(0.5, 70.5);
			f.wolf(20.5, 64.5);
			f.use(p[0]);
			AccidentPlannerImpl.ArmResult result = f.planner().arm(p[0], Traps.SC25, false);
			helper.assertTrue(result.armed(), "not armed: " + result.message());
			pack[0] = (Pack) Hunts.of(p[0].getUUID()).orElseThrow();
		}).thenWaitUntil(() -> {
			if (pack[0].hostileAt() < 0 && (p[0].bites > 0 || f.wolves(pack[0]).stream().anyMatch(w -> TAMPER.isHunting(w) || w.isAngry()))) {
				early.add("at " + (helper.getTick()));
			}
			helper.assertTrue(pack[0].hostileAt() >= 0, "not turned yet (" + pack[0].phase() + " " + pack[0].why() + ")");
		}).thenExecute(() -> {
			long started = pack[0].startedAt();
			long turned = pack[0].hostileAt();
			helper.assertTrue(early.isEmpty(), "hostile before it turned: " + early);
			helper.assertTrue(turned - started >= Hunts.TOLD_TICKS, "it turned " + (turned - started) + " ticks in");
			helper.assertTrue(pack[0].heardBefore(turned) >= 2, "long tells heard before it turned: " + pack[0].heardBefore(turned) + " of " + pack[0].tells());
			helper.assertTrue(pack[0].seenAt() >= 0 && pack[0].seenAt() < turned, "seen at " + pack[0].seenAt() + ", turned at " + turned);
			helper.assertTrue(f.data.armed(p[0].getUUID()).map(ArmedTrap::isSet).orElse(false), "the trap was not set when it turned");
			helper.assertTrue(!pack[0].provoked(), "provoked");
		}).thenWaitUntil(() -> helper.assertTrue(p[0].bites > 0, "no bite yet")).thenExecute(() -> {
			helper.assertTrue(p[0].bitBy instanceof Wolf wolf && pack[0].mobs().contains(wolf.getUUID()), "bitten by " + p[0].bitBy);
			f.done();
		}).thenSucceed();
	}

	/**
	 * Where the player is when the first part ends: in the open the pack closes and bites; two up on a pillar it waits
	 * circling below, never bites, and goes when its wait is over (each one let go); in deep water it goes. Three players,
	 * three packs, at once.
	 */
	@GameTest(environment = ENV_B, maxTicks = 3000)
	public void thePackTurnsOnlyInTheOpenWaitsBelowAPillarAndGoesFromWater(GameTestHelper helper) {
		Field open = new Field(helper, 64);
		Field up = new Field(helper, 64);
		Field water = new Field(helper, 64);
		Target[] p = new Target[3];
		Pack[] packs = new Pack[3];
		List<String> bad = new ArrayList<>();
		boolean[] waited = {false};
		open.whenReady(() -> {
		}).thenWaitUntil(() -> helper.assertTrue(up.ready() && water.ready(), "loading")).thenExecute(() -> {
			up.lit();
			water.lit();
			Field[] fields = {open, up, water};
			for (Field f : fields) {
				f.cfg25.siegeSeconds = 10;
			}
			up.set(up.origin, Blocks.COBBLESTONE);
			up.set(up.origin.above(), Blocks.COBBLESTONE);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					water.set(water.origin.offset(dx, -1, dz), Blocks.WATER);
					water.set(water.origin.offset(dx, -2, dz), Blocks.WATER);
				}
			}
			p[0] = open.player(0.5, 0.5, 180.0F);
			p[1] = up.player(0.5, 0.5, 180.0F);
			p[1].snapTo(up.vec(0.5, 2, 0.5), 180.0F, 0.0F);
			p[2] = water.player(0.5, 0.5, 180.0F);
			p[2].snapTo(water.vec(0.5, -1, 0.5), 180.0F, 0.0F);
			for (int i = 0; i < 3; i++) {
				Field f = fields[i];
				packs[i] = f.pack(p[i], List.of(f.wolf(-8.5, -30.5), f.wolf(0.5, -32.5), f.wolf(8.5, -30.5)));
			}
		}).thenWaitUntil(() -> {
			if (p[1].bites > 0 || p[2].bites > 0) {
				bad.add("bitten up or in the water at " + helper.getTick());
			}
			if (packs[1].phaseNow() == Pack.Phase.SIEGE) {
				waited[0] = true;
			}
			helper.assertTrue(p[0].bites > 0, "the one in the open was not bitten yet (" + packs[0].phase() + ")");
			helper.assertTrue(packs[1].done() && packs[2].done(), "the others did not go yet (" + packs[1].phase() + ", " + packs[2].phase() + ")");
		}).thenExecute(() -> {
			helper.assertTrue(bad.isEmpty(), String.join("; ", bad));
			helper.assertTrue(packs[0].hostileAt() - packs[0].startedAt() >= Hunts.TOLD_TICKS, "the open one turned early");
			helper.assertTrue(waited[0] && packs[1].hostileAt() < 0 && packs[1].why().equals("waited"), "the pillar: " + packs[1].why());
			helper.assertTrue(packs[2].hostileAt() < 0 && packs[2].why().equals("sheltered"), "the water: " + packs[2].why());
			for (int i = 1; i < 3; i++) {
				for (Wolf wolf : Hunts.loaded(fields(open, up, water)[i].level, packs[i].mobs(), Wolf.class)) {
					helper.assertFalse(TAMPER.isTampered(wolf) || wolf.isAngry(), "a wolf was not let go");
				}
			}
			open.done();
			up.done();
			water.done();
		}).thenSucceed();
	}

	/** D-097.2, vanilla's own reach: three wolves set on a player two up on a pillar for 30 s never bite. */
	@GameTest(environment = ENV_A, maxTicks = 900)
	public void wolvesSetOnAPlayerTwoUpOnAPillarNeverBite(GameTestHelper helper) {
		Field f = new Field(helper, 32);
		Target[] p = new Target[1];
		List<Wolf> wolves = new ArrayList<>();
		f.whenReady(() -> {
			f.set(f.origin, Blocks.COBBLESTONE);
			f.set(f.origin.above(), Blocks.COBBLESTONE);
			p[0] = f.player(0.5, 0.5, 0.0F);
			p[0].snapTo(f.vec(0.5, 2, 0.5), 0.0F, 0.0F);
			wolves.addAll(List.of(f.wolf(2.5, 0.5), f.wolf(-1.5, 1.5), f.wolf(0.5, -2.5)));
			for (Wolf wolf : wolves) {
				helper.assertTrue(TAMPER.hunt(wolf, p[0], 700), "hunt refused: " + TAMPER.huntRefusal(wolf, p[0], true));
			}
		}).thenExecuteFor(600, () -> helper.assertTrue(p[0].bites == 0, "bitten two up by " + p[0].bitBy)).thenExecute(() -> {
			helper.assertTrue(wolves.stream().allMatch(TAMPER::isHunting), "the hunt ended");
			wolves.forEach(TAMPER::calm);
			f.done();
		}).thenSucceed();
	}

	/**
	 * D-097.4, a marked death: armed through the planner, set on (the player hits one: their own doing), a death by one
	 * of its wolves is claimed by the trap and marked as the player's own with the list's word "wolves"; a death by
	 * another wolf is not.
	 */
	@GameTest(environment = ENV_A, maxTicks = 900)
	public void aDeathByThePackIsMarkedWithItsCause(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		Target[] p = new Target[1];
		Pack[] pack = new Pack[1];
		Wolf[] stray = new Wolf[1];
		AccidentPlannerImpl[] planner = new AccidentPlannerImpl[1];
		f.whenReady(() -> {
			p[0] = f.player(0.5, 0.5, 0.0F);
			f.wolf(-20.5, 64.5);
			f.wolf(0.5, 70.5);
			f.wolf(20.5, 64.5);
			f.use(p[0]);
			planner[0] = f.planner();
			helper.assertTrue(planner[0].arm(p[0], Traps.SC25, false).armed(), "not armed");
			pack[0] = (Pack) Hunts.of(p[0].getUUID()).orElseThrow();
			stray[0] = f.wolf(70.5, 0.5);
			Wolf one = f.wolves(pack[0]).getFirst();
			helper.assertTrue(one.hurtServer(f.level, f.level.damageSources().playerAttack(p[0]), 1.0F), "setup: the hit");
		}).thenWaitUntil(() -> helper.assertTrue(pack[0].phaseNow() == Pack.Phase.ATTACK, "not turned: " + pack[0].phase() + " " + pack[0].why())).thenExecute(() -> {
			helper.assertTrue(pack[0].provoked() && f.data.armed(p[0].getUUID()).map(ArmedTrap::isSet).orElse(false), "set on by the hit");
			Wolf one = f.wolves(pack[0]).getFirst();
			DamageSource bite = f.level.damageSources().mobAttack(one);
			helper.assertFalse(planner[0].causedBy(p[0], f.level.damageSources().mobAttack(stray[0])), "another wolf's bite claimed");
			helper.assertTrue(planner[0].causedBy(p[0], bite), "its own bite not claimed");
			planner[0].onDeath(p[0], bite);
			List<MarkedDeath> marked = f.group.state.markedDeaths(p[0].getUUID());
			helper.assertTrue(marked.size() == 1 && marked.getFirst().cause().equals("wolves"), "marked: " + marked);
			helper.assertTrue(f.data.armed(p[0].getUUID()).isEmpty(), "the trap was not spent");
		}).thenWaitUntil(() -> helper.assertTrue(pack[0].phaseNow() == Pack.Phase.LEAVE || pack[0].done(), "the pack stays on after the death"))
				.thenExecute(f::done).thenSucceed();
	}

	/** D-097.5, one at a time: with another trap armed for the player, neither kind arms (the planner's one trap a player). */
	@GameTest(maxTicks = 4000)
	public void anotherArmedTrapRefusesBothKinds(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 0.0F);
			f.wolf(-20.5, 64.5);
			f.wolf(0.5, 70.5);
			f.wolf(20.5, 64.5);
			f.use(p);
			f.data.setArmed(p.getUUID(), new ArmedTrap("lava_floor", Level.OVERWORLD, f.origin.offset(200, 0, 0), List.of(), List.of(), java.util.Optional.empty(),
					List.of(), ArmedTrap.Phase.SET, 0, 0, Long.MAX_VALUE, ArmedTrap.NO_CLOCK, f.origin.offset(199, -1, -1), f.origin.offset(201, 1, 1), "", 0));
			AccidentPlannerImpl planner = f.planner();
			helper.assertTrue(planner.arm(p, Traps.SC25, true).status() == AccidentPlannerImpl.Status.BUSY, "sc25 armed beside another trap");
			helper.assertTrue(planner.arm(p, Traps.SC26, true).status() == AccidentPlannerImpl.Status.BUSY, "sc26 armed beside another trap");
			helper.assertTrue(Hunts.of(p.getUUID()).isEmpty(), "a scene started");
			f.done();
		}).thenSucceed();
	}

	// --- the pack: its ends ---

	/**
	 * Its ends once it turned (the player hits one): at dawn, when the last part's time is over, in deep water, under a
	 * roof, two of them dead: each time the wolves are calmed (no hunt, not angry), walk away and are let go, and the
	 * trap runs out. Five players, five packs, at once.
	 */
	@GameTest(environment = ENV_B, maxTicks = 2400)
	public void thePackGoesAtDawnInTimeFromWaterUnderARoofAndWhenTwoAreDead(GameTestHelper helper) {
		String[] cases = {"dawn", "time", "sheltered", "sheltered", "two lost"};
		Field[] fields = new Field[cases.length];
		for (int i = 0; i < fields.length; i++) {
			fields[i] = new Field(helper, 64);
		}
		Target[] p = new Target[cases.length];
		Pack[] packs = new Pack[cases.length];
		boolean[] calmedAt = new boolean[cases.length];
		helper.startSequence().thenWaitUntil(() -> {
			for (Field f : fields) {
				helper.assertTrue(f.ready(), "loading");
			}
		}).thenExecute(() -> {
			for (Field f : fields) {
				f.lit();
			}
			Field roof = fields[3];
			for (int dx = -2; dx <= 2; dx++) {
				for (int dz = -2; dz <= 2; dz++) {
					for (int y = 0; y <= 3; y++) {
						boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == 3;
						if (wall) {
							roof.set(roof.origin.offset(dx, y, dz), Blocks.STONE);
						}
					}
				}
			}
			Field water = fields[2];
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					water.set(water.origin.offset(dx, -1, dz), Blocks.WATER);
					water.set(water.origin.offset(dx, -2, dz), Blocks.WATER);
				}
			}
			fields[1].cfg25.attackSecondsMax = 3;
			for (int i = 0; i < fields.length; i++) {
				Field f = fields[i];
				p[i] = f.player(0.5, 0.5, 180.0F);
				p[i].mortal = false;
				List<Wolf> wolves = new ArrayList<>(List.of(f.wolf(-6.5, -14.5), f.wolf(0.5, -16.5), f.wolf(6.5, -14.5)));
				packs[i] = f.pack(p[i], wolves);
				helper.assertTrue(wolves.getFirst().hurtServer(f.level, f.level.damageSources().playerAttack(p[i]), 1.0F), "setup: the hit");
			}
		}).thenWaitUntil(() -> {
			for (Pack pack : packs) {
				helper.assertTrue(pack.phaseNow() == Pack.Phase.ATTACK, "not turned: " + pack.phase() + " " + pack.why());
			}
		}).thenExecute(() -> {
			fields[0].dark[0] = false;
			p[2].snapTo(fields[2].vec(0.5, -1, 0.5), 180.0F, 0.0F);
			Live.settleLight(fields[3].level, new BoundingBox(fields[3].origin.getX() - 3, fields[3].origin.getY() - 1, fields[3].origin.getZ() - 3,
					fields[3].origin.getX() + 3, fields[3].origin.getY() + 4, fields[3].origin.getZ() + 3));
			p[3].snapTo(fields[3].vec(0.5, 0, 0.5), 180.0F, 0.0F);
			List<Wolf> four = fields[4].wolves(packs[4]);
			four.get(0).kill(fields[4].level);
			four.get(1).kill(fields[4].level);
		}).thenWaitUntil(() -> {
			for (int i = 0; i < packs.length; i++) {
				if (packs[i].phaseNow() == Pack.Phase.LEAVE && !calmedAt[i]) {
					calmedAt[i] = true;
					for (Wolf wolf : fields[i].wolves(packs[i])) {
						helper.assertFalse(TAMPER.isHunting(wolf), cases[i] + ": a wolf still hunts while going");
					}
				}
				helper.assertTrue(packs[i].done(), cases[i] + ": not gone yet (" + packs[i].phase() + ", " + packs[i].why() + ")");
			}
		}).thenExecute(() -> {
			for (int i = 0; i < packs.length; i++) {
				helper.assertTrue(packs[i].why().equals(cases[i]), "case " + i + " ended for " + packs[i].why() + ", not " + cases[i]);
				for (Wolf wolf : Hunts.loaded(fields[i].level, packs[i].mobs(), Wolf.class)) {
					helper.assertFalse(TAMPER.isTampered(wolf) || wolf.isAngry(), cases[i] + ": a wolf was not let go");
				}
			}
			for (Field f : fields) {
				f.done();
			}
		}).thenSucceed();
	}

	/**
	 * Multiplayer: a friend who arrives keeps nothing off the player (the pack stays on the player and never bites the
	 * friend); a friend who hits one gets that one (vanilla's anger) while the rest stay on the player; the player
	 * leaving ends it at once: every hunt calmed, the wolves walk off and are let go.
	 */
	@GameTest(environment = ENV_A, maxTicks = 1600)
	public void aFriendArrivingChangesNothingAHitTakesOneAndALeaveEndsIt(GameTestHelper helper) {
		Field f = new Field(helper, 64);
		Target[] p = new Target[2];
		Pack[] pack = new Pack[1];
		Wolf[] hit = new Wolf[1];
		f.whenReady(() -> {
			p[0] = f.player(0.5, 0.5, 180.0F);
			p[0].mortal = false;
			List<Wolf> wolves = List.of(f.wolf(-6.5, -14.5), f.wolf(0.5, -16.5), f.wolf(6.5, -14.5));
			pack[0] = f.pack(p[0], wolves);
			helper.assertTrue(wolves.getFirst().hurtServer(f.level, f.level.damageSources().playerAttack(p[0]), 1.0F), "setup: the hit");
		}).thenWaitUntil(() -> helper.assertTrue(pack[0].phaseNow() == Pack.Phase.ATTACK, "not turned")).thenExecute(() -> {
			p[1] = f.player(3.5, 3.5, 180.0F);
			p[1].mortal = false;
		}).thenExecuteFor(160, () -> {
			helper.assertTrue(p[1].bites == 0, "the friend was bitten by " + p[1].bitBy);
			for (Wolf wolf : f.wolves(pack[0])) {
				helper.assertTrue(TAMPER.huntTargetOf(wolf) == p[0], "a wolf is on someone else: " + TAMPER.huntTargetOf(wolf));
			}
		}).thenExecute(() -> {
			hit[0] = f.wolves(pack[0]).getFirst();
			helper.assertTrue(hit[0].hurtServer(f.level, f.level.damageSources().playerAttack(p[1]), 1.0F), "setup: the friend's hit");
		}).thenWaitUntil(() -> helper.assertTrue(hit[0].getTarget() == p[1], "the hit one did not turn on the friend: " + hit[0].getTarget())).thenExecute(() -> {
			for (Wolf wolf : f.wolves(pack[0])) {
				if (wolf != hit[0]) {
					helper.assertTrue(TAMPER.huntTargetOf(wolf) == p[0], "the others left the player");
				}
			}
			TAMPER.calm(hit[0]);
			f.group.online.remove(p[0]);
		}).thenWaitUntil(() -> {
			helper.assertTrue(pack[0].done(), "still on after the leave: " + pack[0].phase() + " " + pack[0].why());
		}).thenExecute(() -> {
			helper.assertTrue(pack[0].why().equals("gone"), "it ended for " + pack[0].why());
			for (Wolf wolf : Hunts.loaded(f.level, pack[0].mobs(), Wolf.class)) {
				helper.assertFalse(TAMPER.isTampered(wolf) || wolf.isAngry(), "a wolf was not let go");
			}
			f.done();
		}).thenSucceed();
	}

	/**
	 * Multiplayer: two players 100 apart, each alone with wolves: once one has it, the other is refused (never two within
	 * 128); 200 apart, both may.
	 */
	@GameTest(environment = ENV_A, maxTicks = 900)
	public void twoPlayersNeverGetTwoWithin128(GameTestHelper helper) {
		Field f = new Field(helper, 160);
		f.whenReady(() -> {
			Target a = f.player(-50.5, 0.5, 0.0F);
			Target b = f.player(50.5, 0.5, 0.0F);
			for (double x : new double[] {-50.5, 50.5}) {
				f.wolf(x - 20, 64.5);
				f.wolf(x, 70.5);
				f.wolf(x + 20, 64.5);
			}
			f.use(a);
			f.use(b);
			AccidentPlannerImpl planner = f.planner();
			helper.assertTrue(planner.arm(a, Traps.SC25, false).armed(), "A not armed: " + f.why25(a, planner));
			String why = f.why25(b, planner);
			helper.assertTrue(why != null && why.startsWith("another is running"), "B 100 from A: " + why);
			b.snapTo(f.vec(150.5, 0, 0.5), 0.0F, 0.0F);
			helper.assertTrue(f.why25(b, planner) == null || !f.why25(b, planner).startsWith("another is running"), "B 200 from A: " + f.why25(b, planner));
			f.done();
		}).thenSucceed();
	}

	// --- the call ---

	/** The call's kinds: zombies, husks, skeletons, strays and spiders; never a creeper, a witch, a baby, a named one or a rider. */
	@GameTest(maxTicks = 4000)
	public void theCallTakesItsKindsNeverACreeperOrAWitch(GameTestHelper helper) {
		Field f = new Field(helper, 32);
		f.ticking = false;
		f.whenReady(() -> {
			helper.assertTrue(CallTrap.callable(f.mob(EntityTypes.ZOMBIE, 5.5, 5.5)) && CallTrap.callable(f.mob(EntityTypes.HUSK, 6.5, 5.5))
					&& CallTrap.callable(f.mob(EntityTypes.SKELETON, 7.5, 5.5)) && CallTrap.callable(f.mob(EntityTypes.STRAY, 8.5, 5.5))
					&& CallTrap.callable(f.mob(EntityTypes.SPIDER, 9.5, 5.5)), "a kind it takes was refused");
			Creeper creeper = f.mob(EntityTypes.CREEPER, 5.5, 8.5);
			Witch witch = f.mob(EntityTypes.WITCH, 6.5, 8.5);
			Zombie baby = f.mob(EntityTypes.ZOMBIE, 7.5, 8.5);
			baby.setBaby(true);
			Zombie named = f.mob(EntityTypes.ZOMBIE, 8.5, 8.5);
			named.setCustomName(net.minecraft.network.chat.Component.literal("Bob"));
			Spider mount = f.mob(EntityTypes.SPIDER, 9.5, 10.5);
			Skeleton rider = f.mob(EntityTypes.SKELETON, 9.5, 10.5);
			helper.assertTrue(rider.startRiding(mount, true, false), "setup: the jockey");
			for (Mob mob : List.<Mob>of(creeper, witch, baby, named, mount, rider)) {
				helper.assertFalse(CallTrap.callable(mob), "took " + mob);
			}
			f.done();
		}).thenSucceed();
	}

	/**
	 * The call, measured: silent for its first 20 s (every one silenced), walking to its ring (never within 16 before
	 * 60 s); their voices at 20 s and again at 40 s, heard; from 60 s they come in and each is let go at 16, never sooner,
	 * the first no sooner than 60 s. Beside it, a player by a door: at most 4 of the walking dead come toward them, the
	 * rest wait on the ring. A friend standing 10 off the first player is not walked at: the ring is the player's.
	 */
	@GameTest(environment = ENV_C, maxTicks = 3200)
	public void theCallIsQuietThenLoudThenLetsGoAtSixteenAndKeepsItsDoorCap(GameTestHelper helper) {
		Field f = new Field(helper, 80);
		Field d = new Field(helper, 80);
		Target[] p = new Target[3];
		Call[] calls = new Call[2];
		List<String> bad = new ArrayList<>();
		java.util.Set<UUID> shownOnes = new java.util.HashSet<>();
		int[] mostAtDoor = {0};
		f.whenReady(() -> {
		}).thenWaitUntil(() -> helper.assertTrue(d.ready(), "loading")).thenExecute(() -> {
			d.lit();
			p[0] = f.player(0.5, 0.5, 180.0F);
			p[0].mortal = false;
			p[0].getInventory().add(new ItemStack(Items.STONE_PICKAXE)); // the call's way out, asked again at the turn (R12-7, R12-8)
			p[2] = f.player(10.5, 0.5, 90.0F);
			p[2].mortal = false;
			List<Mob> callees = new ArrayList<>();
			double[][] at = {{0, -46}, {40, -20}, {-40, -20}, {30, 34}, {-30, 34}};
			for (int i = 0; i < at.length; i++) {
				callees.add(i % 2 == 0 ? f.mob(EntityTypes.HUSK, at[i][0] + 0.5, at[i][1] + 0.5) : f.mob(EntityTypes.SPIDER, at[i][0] + 0.5, at[i][1] + 0.5));
			}
			calls[0] = f.call(p[0], callees);
			// The door: an oak door 2 off the second player.
			d.set(d.origin.offset(2, 0, 0), Blocks.OAK_DOOR.defaultBlockState());
			d.set(d.origin.offset(2, 1, 0), Blocks.OAK_DOOR.defaultBlockState().setValue(net.minecraft.world.level.block.DoorBlock.HALF,
					net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER));
			p[1] = d.player(0.5, 0.5, 180.0F);
			p[1].mortal = false;
			p[1].getInventory().add(new ItemStack(Items.STONE_SHOVEL));
			List<Mob> dead = new ArrayList<>();
			for (int i = 0; i < 7; i++) {
				double angle = i * Math.PI * 2 / 7;
				dead.add(d.mob(EntityTypes.HUSK, Math.cos(angle) * 40 + 0.5, Math.sin(angle) * 40 + 0.5));
			}
			d.cfg26.releaseAt = 3;
			calls[1] = d.call(p[1], dead);
			for (Mob mob : f.mobs(calls[0])) {
				helper.assertTrue(TAMPER.isSilenced(mob), "not silenced at the start");
			}
		}).thenWaitUntil(() -> {
			long now = f.level.getServer().getTickCount();
			long in = now - calls[0].startedAt();
			if (calls[0].shown() != null) {
				shownOnes.add(calls[0].shown());
			}
			for (Mob mob : f.mobs(calls[0])) {
				double dist = mob.position().distanceTo(p[0].position());
				// The one walked into the middle of the view stands 14 to 16 off (R10-12); the rest never come within 16.
				boolean shownOne = shownOnes.contains(mob.getUUID());
				if (in < Hunts.TOLD_TICKS && dist < (shownOne ? 12.5 : 16.0)) {
					bad.add("one within " + (int) dist + " at " + in + (shownOne ? " (shown)" : ""));
				}
				if (in > 30 && in < 380 && !TAMPER.isSilenced(mob)) {
					bad.add("not silent at " + in);
				}
				if (in > 20 && in < Hunts.TOLD_TICKS && !TAMPER.isSteered(mob)) {
					bad.add("not walked at " + in);
				}
				if (in > 300 && in < 1100 && calls[0].phaseNow() != Call.Phase.CLOSE && mob.position().distanceTo(p[2].position()) < 10.0
						&& dist > 15.0) {
					bad.add("one walked at the friend at " + in);
				}
			}
			if (calls[1].phaseNow() == Call.Phase.CLOSE) {
				// Walked at the player now (their walk ends within 3 of them), or let go and still within 12 of them.
				int toward = 0;
				for (Mob mob : d.mobs(calls[1])) {
					com.forzacode.a1016_02.atmosphere.mob.TamperState state = ((com.forzacode.a1016_02.atmosphere.mob.TamperedMob) mob).a1016_02$tamper();
					boolean walked = state != null && state.steerTarget() != null && TAMPER.isSteered(mob) && state.steerTarget().distanceTo(p[1].position()) < 3.0;
					boolean there = calls[1].released().contains(mob.getUUID()) && mob.position().distanceTo(p[1].position()) <= 12.0;
					if (walked || there) {
						toward++;
					}
				}
				mostAtDoor[0] = Math.max(mostAtDoor[0], toward);
			}
			helper.assertTrue(calls[0].released().size() >= 3, "few let go yet: " + calls[0].released().size() + " (" + calls[0].phase() + " " + calls[0].why() + ")");
			helper.assertTrue(in > Hunts.TOLD_TICKS + 400, "the door's close is still going");
		}).thenExecute(() -> {
			Call call = calls[0];
			helper.assertTrue(bad.isEmpty(), String.join("; ", bad.subList(0, Math.min(6, bad.size()))));
			List<HuntScene.Tell> tells = call.tells();
			long first = tells.stream().mapToLong(HuntScene.Tell::tick).min().orElse(Long.MAX_VALUE);
			helper.assertTrue(first - call.startedAt() >= 395, "the first voice " + (first - call.startedAt()) + " ticks in");
			helper.assertTrue(call.heardBefore(call.hostileAt()) >= 2, "voices heard before it came in: " + call.heardBefore(call.hostileAt()));
			helper.assertTrue(call.hostileAt() - call.startedAt() >= Hunts.TOLD_TICKS && call.seenAt() >= 0, "came in at " + (call.hostileAt() - call.startedAt()));
			helper.assertTrue(mostAtDoor[0] <= 4, "toward the door: " + mostAtDoor[0]);
			f.done();
			d.done();
		}).thenSucceed();
	}

	/**
	 * The call's gate: a fog night, its pacing (days apart, the world's count), alone, health, a way out; a forced arm
	 * relaxes the night and the pacing. And a death by one of its own, once it is set, is claimed and marked by the
	 * killer's kind (shot, bitten, killed); a creeper's blast is not.
	 */
	@GameTest(maxTicks = 4000)
	public void theCallsGateAndItsMarkedDeaths(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 180.0F);
			Skeleton skeleton = f.mob(EntityTypes.SKELETON, 20.5, 20.5);
			Spider spider = f.mob(EntityTypes.SPIDER, -20.5, 20.5);
			Zombie zombie = f.mob(EntityTypes.ZOMBIE, 20.5, -20.5);
			f.mob(EntityTypes.ZOMBIE, -20.5, -20.5);
			Creeper creeper = f.mob(EntityTypes.CREEPER, 30.5, 0.5);
			AccidentPlannerImpl planner = f.planner();
			f.use(p);
			// R12-7: two blocks or an axe are no way out from arrows and spiders; a pickaxe or a shovel is (dig in and cover).
			p.getInventory().add(new ItemStack(Items.IRON_AXE));
			helper.assertTrue(String.valueOf(f.why26(p, planner)).startsWith("carries no pickaxe or shovel"), "blocks and an axe: " + f.why26(p, planner));
			p.getInventory().add(new ItemStack(Items.WOODEN_SHOVEL));
			helper.assertTrue(f.why26(p, planner) == null, "the setting does not fit: " + f.why26(p, planner));
			helper.assertTrue(planner.candidates(p, Traps.SC26).size() == 1, "no candidate");
			f.fog[0] = false;
			helper.assertTrue("not a fog night".equals(f.why26(p, planner)), "no fog: " + f.why26(p, planner));
			f.forced = true;
			f.use(p);
			helper.assertTrue(f.why26(p, planner) == null, "forced, no fog: " + f.why26(p, planner));
			f.forced = false;
			f.fog[0] = true;
			f.use(p);
			f.hunts.record(CallTrap.ID, p.getUUID(), f.day[0] - 3);
			helper.assertTrue(String.valueOf(f.why26(p, planner)).startsWith("had one on day"), "days apart: " + f.why26(p, planner));
			f.hunts.reset(CallTrap.ID);
			f.hunts.record(CallTrap.ID, UUID.randomUUID(), 0);
			f.hunts.record(CallTrap.ID, UUID.randomUUID(), 0);
			helper.assertTrue(String.valueOf(f.why26(p, planner)).startsWith("the world had"), "the world's count: " + f.why26(p, planner));
			f.hunts.reset(CallTrap.ID);
			Target friend = f.player(20.5, 0.5, 0.0F);
			helper.assertTrue(String.valueOf(f.why26(p, planner)).startsWith("not alone"), "a friend 20 off: " + f.why26(p, planner));
			f.group.online.remove(friend);
			p.setHealth(12.0F);
			helper.assertTrue(String.valueOf(f.why26(p, planner)).startsWith("health"), "health 12: " + f.why26(p, planner));
			p.setHealth(20.0F);
			p.getInventory().clearContent();
			helper.assertTrue(String.valueOf(f.why26(p, planner)).startsWith("carries no way out"), "no way out: " + f.why26(p, planner));
			p.getInventory().add(new ItemStack(Items.STONE_PICKAXE));

			// Its deaths, once set.
			f.data.setArmed(p.getUUID(), new ArmedTrap("sc26", Level.OVERWORLD, p.blockPosition(), List.of(), List.of(), java.util.Optional.empty(),
					List.of(skeleton.getUUID(), spider.getUUID(), zombie.getUUID()), ArmedTrap.Phase.SET, 0, 0, Long.MAX_VALUE, ArmedTrap.NO_CLOCK,
					p.blockPosition().offset(-16, -4, -16), p.blockPosition().offset(16, 16, 16), "", 0));
			DamageSources damage = f.level.damageSources();
			Arrow arrow = EntityTypes.ARROW.create(f.level, EntitySpawnReason.COMMAND);
			helper.assertTrue(planner.causedBy(p, damage.arrow(arrow, skeleton)) && AccidentPlannerImpl.listWord(damage.arrow(arrow, skeleton),
					f.data.armed(p.getUUID()).orElseThrow()).equals("shot"), "the skeleton's arrow");
			helper.assertTrue(planner.causedBy(p, damage.mobAttack(spider)) && AccidentPlannerImpl.listWord(damage.mobAttack(spider),
					f.data.armed(p.getUUID()).orElseThrow()).equals("bitten"), "the spider");
			helper.assertTrue(AccidentPlannerImpl.listWord(damage.mobAttack(zombie), f.data.armed(p.getUUID()).orElseThrow()).equals("killed"), "the zombie");
			helper.assertFalse(planner.causedBy(p, damage.explosion(creeper, creeper)), "a creeper's blast was claimed");
			planner.onDeath(p, damage.mobAttack(zombie));
			helper.assertTrue(f.group.state.markedDeaths(p.getUUID()).stream().anyMatch(m -> m.cause().equals("killed")), "not marked");
			f.done();
		}).thenSucceed();
	}

	// --- the switch ---

	/** The switch going off ends every scene of both kinds at its next look, at once: every mob let go where it is. */
	@GameTest(environment = ENV_A, maxTicks = 900)
	public void switchingTheLayerOffEndsBothAtOnce(GameTestHelper helper) {
		Field f = new Field(helper, 64);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 180.0F);
			Target q = f.player(0.5, 60.5, 180.0F);
			Pack pack = f.pack(p, List.of(f.wolf(-6.5, -30.5), f.wolf(0.5, -32.5), f.wolf(6.5, -30.5)));
			Call call = f.call(q, List.of(f.mob(EntityTypes.ZOMBIE, 20.5, 80.5), f.mob(EntityTypes.ZOMBIE, -20.5, 80.5), f.mob(EntityTypes.SKELETON, 0.5, 100.5),
					f.mob(EntityTypes.SPIDER, 30.5, 60.5)));
			SelfCreated.Config sc = SelfCreated.config();
			sc.enabled = false;
			try {
				long now = f.level.getServer().getTickCount();
				pack.step(now);
				call.step(now);
			} finally {
				sc.enabled = true;
			}
			helper.assertTrue(pack.done() && call.done() && pack.why().equals("switched off") && call.why().equals("switched off"), "still running");
			for (Mob mob : Hunts.loaded(f.level, pack.mobs(), Mob.class)) {
				helper.assertFalse(TAMPER.isTampered(mob), "a wolf was left steered");
			}
			for (Mob mob : Hunts.loaded(f.level, call.mobs(), Mob.class)) {
				helper.assertFalse(TAMPER.isTampered(mob), "a monster was left steered or silenced");
			}
			f.done();
		}).thenSucceed();
	}

	// --- fixed in review (FIX-SC2-REST) ---

	static final String ENV_D = "a1016_02:accident/sc2_hunts_d";

	/**
	 * R13-1: the clue is the feature in one sentence, so arming either kind through the planner writes it nowhere the
	 * player reads: no log line at INFO or above, no history line, and {@code /a1016 accident} shows the kind's id for it.
	 */
	@GameTest(environment = ENV_C, maxTicks = 900)
	public void theHuntsCluesStayOutOfTheLogTheHistoryAndTheStatus(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		Field g = new Field(helper, 96);
		f.whenReady(() -> {
		}).thenWaitUntil(() -> helper.assertTrue(g.ready(), "loading")).thenExecute(() -> {
			g.lit();
			Target p = f.player(0.5, 0.5, 0.0F);
			f.wolf(-62.5, -10.5);
			f.wolf(0.5, -66.5);
			f.wolf(62.5, -10.5);
			f.view = Yard.eyesOf(p);
			f.use(p);
			Target q = g.player(0.5, 0.5, 180.0F);
			q.getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			g.mob(EntityTypes.ZOMBIE, 30.5, 30.5);
			g.mob(EntityTypes.ZOMBIE, -30.5, 30.5);
			g.mob(EntityTypes.SKELETON, 30.5, -30.5);
			g.mob(EntityTypes.SPIDER, -30.5, -30.5);
			g.use(q);
			try (com.forzacode.a1016_02.core.LogCapture log = com.forzacode.a1016_02.core.LogCapture.open()) {
				AccidentPlannerImpl planner = f.planner();
				helper.assertTrue(planner.arm(p, Traps.SC25, false).armed(), "sc25 not armed: " + f.why25(p, planner));
				AccidentPlannerImpl other = g.planner();
				helper.assertTrue(other.arm(q, Traps.SC26, false).armed(), "sc26 not armed: " + g.why26(q, other));
				for (com.forzacode.a1016_02.core.LogCapture.Line line : log.shown()) {
					helper.assertFalse(line.message().contains(PackTrap.CLUE) || line.message().contains(CallTrap.CLUE) || line.message().contains("clue"),
							"a log line holds the clue: " + line);
				}
				helper.assertTrue(log.shown().stream().anyMatch(l -> l.message().contains("sc25") && l.message().contains("watching")), "no arm line at all");
				// SC2-V's V-6: nor where it waits (the history keeps the place for the op's status).
				for (com.forzacode.a1016_02.core.LogCapture.Line line : log.shown()) {
					helper.assertFalse((line.message().contains("sc25") || line.message().contains("sc26"))
							&& java.util.regex.Pattern.compile("-?\\d+ -?\\d+ -?\\d+").matcher(line.message()).find(), "a log line gives the place: " + line);
				}
				helper.assertTrue(f.data.history().stream().anyMatch(h -> h.contains("sc25") && h.matches(".*watching -?\\d+ -?\\d+ -?\\d+.*")),
						"the history lost the place: " + f.data.history());
				for (String line : f.data.history()) {
					helper.assertFalse(line.contains(PackTrap.CLUE) || line.contains("clue"), "the history holds the clue: " + line);
				}
				for (String line : g.data.history()) {
					helper.assertFalse(line.contains(CallTrap.CLUE) || line.contains("clue"), "the history holds the clue: " + line);
				}
				List<String> status = AccidentCommands.status(planner, f.level.getServer(), p);
				helper.assertTrue(status.stream().anyMatch(s -> s.contains("clue: (sc25)")) && status.stream().noneMatch(s -> s.contains(PackTrap.CLUE)),
						"the status: " + status);
				helper.assertTrue(AccidentPlannerImpl.quietClue("sc25") && AccidentPlannerImpl.quietClue("sc26") && !AccidentPlannerImpl.quietClue("lava_floor")
						&& !AccidentPlannerImpl.quietClue("sc"), "which kinds keep their clue quiet");
			}
			f.done();
			g.done();
		}).thenSucceed();
	}

	/**
	 * R12-6 and R13-13: a death no entity dealt (a fall, lava, water) right after one of a hunt's own hurt the victim is
	 * the hunt's, the target's or a friend's who ran in, with the damage's own word; one long after the hurt, or with no
	 * hurt by one of its own, is not; a bite still names the killer's kind.
	 */
	@GameTest(maxTicks = 4000)
	public void aDeathTheHuntDroveThemIntoIsMarkedWithItsOwnWordForAFriendToo(GameTestHelper helper) {
		Field f = new Field(helper, 32);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 180.0F);
			Target friend = f.player(4.5, 0.5, 180.0F);
			Target other = f.player(8.5, 0.5, 180.0F);
			Wolf wolf = f.wolf(2.5, 6.5);
			Zombie zombie = f.mob(EntityTypes.ZOMBIE, -6.5, 6.5);
			Wolf stranger = f.wolf(12.5, 6.5);
			AccidentPlannerImpl planner = f.planner();
			DamageSources damage = f.level.damageSources();
			f.data.setArmed(p.getUUID(), new ArmedTrap("sc25", Level.OVERWORLD, p.blockPosition(), List.of(), List.of(), java.util.Optional.empty(),
					List.of(wolf.getUUID()), ArmedTrap.Phase.SET, 0, 0, Long.MAX_VALUE, ArmedTrap.NO_CLOCK, p.blockPosition().offset(-16, -4, -16),
					p.blockPosition().offset(16, 16, 16), "", 0));
			helper.assertFalse(planner.causedBy(p, damage.fall()), "a fall with no bite before it");
			helper.assertTrue(p.hurtServer(f.level, damage.mobAttack(wolf), 1.0F) && friend.hurtServer(f.level, damage.mobAttack(wolf), 1.0F)
					&& other.hurtServer(f.level, damage.mobAttack(stranger), 1.0F), "setup: the bites");
			ArmedTrap trap = f.data.armed(p.getUUID()).orElseThrow();
			helper.assertTrue(planner.causedBy(p, damage.fall()) && AccidentPlannerImpl.listWord(damage.fall(), trap).equals(DeathCauses.word(damage.fall(), Traps.SC25)),
					"the target's fall right after a bite: " + AccidentPlannerImpl.listWord(damage.fall(), trap));
			helper.assertTrue(planner.causedBy(p, damage.lava()) && planner.causedBy(p, damage.drown()), "lava or water right after a bite");
			helper.assertTrue(AccidentPlannerImpl.listWord(damage.mobAttack(wolf), trap).equals("wolves"), "a bite's own word");
			helper.assertFalse(planner.causedBy(other, damage.fall()), "a fall after a bite of a wolf that is not its own");
			planner.onDeath(friend, damage.fall());
			List<MarkedDeath> marked = f.group.state.markedDeaths(friend.getUUID());
			helper.assertTrue(marked.size() == 1 && marked.getFirst().cause().equals(DeathCauses.word(damage.fall(), Traps.SC25)), "the friend's death: " + marked);
			// Long after the bite: vanilla forgets who hurt them, and so does the trap.
			f.data.setArmed(p.getUUID(), trap);
			p.tickCount += Hunts.CREDIT_TICKS + 20;
			helper.assertFalse(planner.causedBy(p, damage.fall()), "a fall long after the bite");
			// The call's own the same way.
			f.data.setArmed(p.getUUID(), new ArmedTrap("sc26", Level.OVERWORLD, p.blockPosition(), List.of(), List.of(), java.util.Optional.empty(),
					List.of(zombie.getUUID()), ArmedTrap.Phase.SET, 0, 0, Long.MAX_VALUE, ArmedTrap.NO_CLOCK, p.blockPosition().offset(-16, -4, -16),
					p.blockPosition().offset(16, 16, 16), "", 0));
			Target late = f.player(-2.5, 0.5, 180.0F);
			helper.assertTrue(late.hurtServer(f.level, damage.mobAttack(zombie), 1.0F), "setup: the zombie's hit");
			helper.assertTrue(planner.causedBy(late, damage.fall()) && Traps.SC26.word(damage.fall(), f.data.armed(p.getUUID()).orElseThrow()) == null,
					"the call's fall");
			f.done();
		}).thenSucceed();
	}

	/**
	 * R12-8: the turn asks the start's floors again: under 10 health, no way out (the kind's own: the call's is a pickaxe or
	 * a shovel), within 64 of the base (unless a forced arm relaxed it); the gallery's stand-in has none.
	 */
	@GameTest
	public void theTurnAsksTheFloorsAgain(GameTestHelper helper) {
		Group group = new Group(helper);
		Target p = new Target(helper.getLevel());
		p.snapTo(Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO)), 0.0F, 0.0F);
		p.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
		group.online.add(p);
		Prey prey = Prey.of(p);
		helper.assertTrue(Hunts.turnRefusal(prey, group.watch, Hunts::wayOut, true, false) == null, "a fit target: " + Hunts.turnRefusal(prey, group.watch,
				Hunts::wayOut, true, false));
		helper.assertTrue(String.valueOf(Hunts.turnRefusal(prey, group.watch, Hunts::digOut, true, false)).startsWith("no way out"), "the call's way out");
		p.setHealth(9.0F);
		helper.assertTrue(String.valueOf(Hunts.turnRefusal(prey, group.watch, Hunts::wayOut, true, false)).startsWith("health"), "under 10");
		p.setHealth(20.0F);
		p.setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, p.blockPosition().offset(30, 0, 0), 0.0F, 0.0F), true), false);
		helper.assertTrue(String.valueOf(Hunts.turnRefusal(prey, group.watch, Hunts::wayOut, true, false)).startsWith("within 64"), "home");
		helper.assertTrue(Hunts.turnRefusal(prey, group.watch, Hunts::wayOut, true, true) == null, "a forced arm relaxes the base");
		p.getInventory().clearContent();
		helper.assertTrue(String.valueOf(Hunts.turnRefusal(prey, group.watch, Hunts::wayOut, true, true)).startsWith("no way out"), "forced, no way out");
		helper.assertTrue(Hunts.turnRefusal(Prey.standIn(helper.getLevel(), Vec3.ZERO, 0.0F, 0.0F), group.watch, Hunts::wayOut, true, false) == null,
				"the gallery's stand-in");
		helper.succeed();
	}

	/**
	 * R12-8, measured: a pack that was told never turns on a player who fell under 10 health; it holds off for its 30 s
	 * and goes ("held"), nothing hostile ever done.
	 */
	@GameTest(environment = ENV_D, maxTicks = 3000)
	public void thePackNeverTurnsOnAPlayerUnderTenAndGoes(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.cfg25.stalkSecondsMax = 60;
		Target[] p = new Target[1];
		Pack[] pack = new Pack[1];
		f.whenReady(() -> {
			p[0] = f.player(0.5, 0.5, 180.0F);
			pack[0] = f.pack(p[0], List.of(f.wolf(-12.5, -40.5), f.wolf(0.5, -42.5), f.wolf(12.5, -40.5)));
		}).thenWaitUntil(() -> helper.assertTrue(f.level.getServer().getTickCount() >= pack[0].stalkUntil() - 100, "the first part"))
				.thenExecute(() -> p[0].setHealth(8.0F))
				.thenWaitUntil(() -> helper.assertTrue(pack[0].phaseNow() == Pack.Phase.LEAVE || pack[0].done(), "still on: " + pack[0].phase()))
				.thenExecute(() -> {
					helper.assertTrue(pack[0].told(f.level.getServer().getTickCount()) || pack[0].why().equals("held"), "never told, so this proves nothing");
					helper.assertTrue(pack[0].why().equals("held") && pack[0].hostileAt() < 0 && p[0].bites == 0, "it turned or went for another reason: "
							+ pack[0].why() + ", hostile " + pack[0].hostileAt() + ", bites " + p[0].bites);
					f.done();
				}).thenSucceed();
	}

	/**
	 * R12-8: home is safe from the call at any point (the player within 64 of their base: they go at once), and a player
	 * under a roof at the turn sees them go ("sheltered"), never one let go.
	 */
	@GameTest(environment = ENV_D, maxTicks = 3000)
	public void theCallGoesFromHomeAndFromUnderARoofAtTheTurn(GameTestHelper helper) {
		Field f = new Field(helper, 80);
		Field r = new Field(helper, 80);
		Target[] p = new Target[2];
		Call[] calls = new Call[2];
		f.whenReady(() -> {
		}).thenWaitUntil(() -> helper.assertTrue(r.ready(), "loading")).thenExecute(() -> {
			r.lit();
			p[0] = f.player(0.5, 0.5, 180.0F);
			p[0].getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			p[0].setRespawnPosition(new ServerPlayer.RespawnConfig(LevelData.RespawnData.of(Level.OVERWORLD, f.origin.offset(20, 0, 0), 0.0F, 0.0F), true),
					false);
			calls[0] = f.call(p[0], List.of(f.mob(EntityTypes.ZOMBIE, 30.5, 30.5), f.mob(EntityTypes.SKELETON, -30.5, 30.5)));
			p[1] = r.player(0.5, 0.5, 180.0F);
			p[1].mortal = false;
			p[1].getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			calls[1] = r.call(p[1], List.of(r.mob(EntityTypes.ZOMBIE, 0.5, -36.5), r.mob(EntityTypes.SPIDER, 30.5, -30.5), r.mob(EntityTypes.HUSK, -30.5, -30.5)));
		}).thenWaitUntil(() -> helper.assertTrue(calls[0].done() || calls[0].phaseNow() == Call.Phase.LEAVE, "still on at home: " + calls[0].phase()))
				.thenExecute(() -> helper.assertTrue(calls[0].why().equals("home") && calls[0].hostileAt() < 0, "it went for " + calls[0].why()))
				.thenWaitUntil(() -> helper.assertTrue(f.level.getServer().getTickCount() >= calls[1].closeAt() - 60, "before the turn"))
				.thenExecute(() -> {
					// A hut over the second player just before the turn.
					BlockPos o = r.origin;
					for (int dx = -2; dx <= 2; dx++) {
						for (int dz = -2; dz <= 2; dz++) {
							for (int y = 0; y <= 3; y++) {
								if (Math.abs(dx) == 2 || Math.abs(dz) == 2 || y == 3) {
									r.set(o.offset(dx, y, dz), Blocks.STONE);
								}
							}
						}
					}
					Live.settleLight(r.level, new BoundingBox(o.getX() - 3, o.getY() - 1, o.getZ() - 3, o.getX() + 3, o.getY() + 4, o.getZ() + 3));
				})
				.thenWaitUntil(() -> helper.assertTrue(calls[1].done() || calls[1].phaseNow() == Call.Phase.LEAVE, "still on under the roof: " + calls[1].phase()))
				.thenExecute(() -> {
					helper.assertTrue(calls[1].why().equals("sheltered") && calls[1].released().isEmpty() && calls[1].hostileAt() < 0,
							"it went for " + calls[1].why() + ", let go " + calls[1].released().size());
					f.done();
					r.done();
				}).thenSucceed();
	}

	/**
	 * R12-36: once set, the call's window goes on while one of its own let go to vanilla's targeting is still after a
	 * player as it is about to close; not when none is.
	 */
	@GameTest(maxTicks = 4000)
	public void theCallsWindowGoesOnWhileOneOfItsOwnIsStillAfterAPlayer(GameTestHelper helper) {
		Field f = new Field(helper, 32);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 180.0F);
			Zombie zombie = f.mob(EntityTypes.ZOMBIE, 6.5, 6.5);
			AccidentPlannerImpl planner = f.planner();
			TrapContext ctx = planner.context(p);
			long now = ctx.now();
			ArmedTrap trap = new ArmedTrap("sc26", Level.OVERWORLD, p.blockPosition(), List.of(), List.of(), java.util.Optional.empty(), List.of(zombie.getUUID()),
					ArmedTrap.Phase.SET, now - 100, now - 100, now + 20, ArmedTrap.NO_CLOCK, p.blockPosition().offset(-16, -4, -16),
					p.blockPosition().offset(16, 16, 16), "", 0);
			helper.assertTrue(Traps.SC26.tick(ctx, trap).until() == now + 20, "kept on with nobody after anyone");
			zombie.setTarget(p);
			ArmedTrap kept = Traps.SC26.tick(ctx, trap);
			helper.assertTrue(kept.until() >= now + CallTrap.KEEP_STEP - 1, "not kept on: until " + kept.until() + ", now " + now);
			ArmedTrap old = new ArmedTrap("sc26", Level.OVERWORLD, p.blockPosition(), List.of(), List.of(), java.util.Optional.empty(), List.of(zombie.getUUID()),
					ArmedTrap.Phase.SET, now - CallTrap.KEEP_MAX - 10, now - CallTrap.KEEP_MAX - 10, now + 20, ArmedTrap.NO_CLOCK,
					p.blockPosition().offset(-16, -4, -16), p.blockPosition().offset(16, 16, 16), "", 0);
			helper.assertTrue(Traps.SC26.tick(ctx, old).until() == now + 20, "kept on past its most");
			f.done();
		}).thenSucceed();
	}

	/**
	 * R12-37: a callee the scene cannot steer (here it rides a chicken) is let go at once, loud, and is no instrument of
	 * the call any more: never silenced on its own AI before any tell.
	 */
	@GameTest(environment = ENV_D, maxTicks = 900)
	public void aCalleeThatCannotBeWalkedIsLetGoLoud(GameTestHelper helper) {
		Field f = new Field(helper, 64);
		Mob[] boarded = new Mob[1];
		Call[] call = new Call[1];
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 180.0F);
			p.getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			List<Mob> callees = List.of(f.mob(EntityTypes.ZOMBIE, 30.5, 30.5), f.mob(EntityTypes.ZOMBIE, -30.5, 30.5), f.mob(EntityTypes.SKELETON, 30.5, -30.5));
			call[0] = f.call(p, callees);
			boarded[0] = callees.getFirst();
			helper.assertTrue(TAMPER.isSilenced(boarded[0]), "setup: silenced at the start");
			Mob mount = f.mob(EntityTypes.CHICKEN, 30.5, 30.5);
			helper.assertTrue(boarded[0].startRiding(mount, true, false), "setup: onto the chicken");
		}).thenExecuteAfter(30, () -> {
			helper.assertFalse(TAMPER.isSilenced(boarded[0]) || TAMPER.isSteered(boarded[0]), "the rider is still held silent");
			for (Mob mob : f.mobs(call[0])) {
				if (mob != boarded[0]) {
					helper.assertTrue(TAMPER.isSteered(mob), "another was let go too");
				}
			}
			f.done();
		}).thenSucceed();
	}

	/**
	 * R12-28: a wolf saved while set on a player (an autosave mid-hunt, then a crash) loads calm: no anger, no target. A
	 * wolf vanilla made angry on its own keeps its anger through a save.
	 */
	@GameTest(maxTicks = 4000)
	public void aWolfSavedMidHuntLoadsCalmAndAVanillaAngryOneStaysAngry(GameTestHelper helper) {
		Field f = new Field(helper, 32);
		f.whenReady(() -> {
			Target p = f.player(0.5, 0.5, 180.0F);
			Wolf hunted = f.wolf(4.5, 4.5);
			Wolf vanilla = f.wolf(-4.5, 4.5);
			helper.assertTrue(TAMPER.hunt(hunted, p, 400) && hunted.isAngry(), "setup: the hunt");
			vanilla.setPersistentAngerTarget(net.minecraft.world.entity.EntityReference.of(p));
			vanilla.setTimeToRemainAngry(400);
			CompoundTag huntedSave = save(f.level, hunted);
			CompoundTag vanillaSave = save(f.level, vanilla);
			helper.assertTrue(huntedSave.getBooleanOr(MobTamperImpl.HUNT_SAVED_KEY, false) && !vanillaSave.contains(MobTamperImpl.HUNT_SAVED_KEY),
					"the mark: " + huntedSave.keySet());
			Wolf loaded = load(f, huntedSave, 6.5, 6.5);
			Wolf stillAngry = load(f, vanillaSave, -6.5, 6.5);
			helper.assertTrue(!loaded.isAngry() && loaded.getTarget() == null && loaded.getPersistentAngerTarget() == null && !TAMPER.isHunting(loaded),
					"the reloaded hunter is still angry: " + loaded.isAngry() + ", target " + loaded.getTarget());
			helper.assertTrue(stillAngry.isAngry(), "vanilla's own anger was taken");
			TAMPER.calm(hunted);
			f.done();
		}).thenSucceed();
	}

	private static CompoundTag save(ServerLevel level, Wolf wolf) {
		net.minecraft.world.level.storage.TagValueOutput out = net.minecraft.world.level.storage.TagValueOutput.createWithContext(
				net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
		wolf.saveWithoutId(out);
		return out.buildResult();
	}

	private static Wolf load(Field f, CompoundTag tag, double x, double z) {
		CompoundTag copy = tag.copy();
		copy.remove("UUID");
		Wolf wolf = EntityTypes.WOLF.create(f.level, EntitySpawnReason.COMMAND);
		wolf.load(net.minecraft.world.level.storage.TagValueInput.create(net.minecraft.util.ProblemReporter.DISCARDING, f.level.registryAccess(), copy));
		wolf.snapTo(f.vec(x, 0, z), 0.0F, 0.0F);
		f.helper.assertTrue(f.level.addFreshEntity(wolf), "could not add the reloaded wolf");
		f.made(wolf);
		return wolf;
	}

	// --- fixed in review (FIX-SC2-ACTORS) ---

	/** The scenes of the actors' review fixes steer up to twelve at once: a batch of their own. */
	static final String ENV_E = "a1016_02:accident/sc2_hunts_e";

	/**
	 * R10-5, R10-12, R10-19, R11-3, R11-15, pure: the pack's band at night is where a wolf reads (16 to 24), in a fog the
	 * share of the drawn fog end (and never past the night band at night), by day in a fog the fog's alone; "seen" at night
	 * is 20 at most, in a fog 0.6 of the fog end; the call never takes more than six, whatever the config says; a spider's
	 * place on the ring is turned out of the player's look.
	 */
	@GameTest
	public void theBandsAndTheSightAreWhatAPlayerPerceivesAndTheCallIsNeverAHorde(GameTestHelper helper) {
		double[] night = Pack.band(false, true, Double.NaN);
		helper.assertTrue(night[0] == 16.0 && night[1] == 24.0, "the night band: " + java.util.Arrays.toString(night));
		double[] thick = Pack.band(true, true, 24.0);
		helper.assertTrue(Math.abs(thick[0] - 13.2) < 1.0E-6 && Math.abs(thick[1] - 16.8) < 1.0E-6, "a full fog at night: " + java.util.Arrays.toString(thick));
		double[] thin = Pack.band(true, true, 200.0);
		helper.assertTrue(thin[0] == 16.0 && thin[1] == 24.0, "a thin fog at night stays the night band: " + java.util.Arrays.toString(thin));
		double[] day = Pack.band(true, false, 40.0);
		helper.assertTrue(Math.abs(day[0] - 22.0) < 1.0E-6 && Math.abs(day[1] - 28.0) < 1.0E-6, "a fog by day: " + java.util.Arrays.toString(day));
		double[] far = Pack.band(true, false, 400.0);
		helper.assertTrue(far[1] <= 30.0 && far[0] >= Pack.BAND_FLOOR, "a fog by day never past 30: " + java.util.Arrays.toString(far));
		helper.assertTrue(Pack.CLOSING_MAX < night[1] && Pack.CLOSING_MIN >= Pack.BAND_FLOOR, "the closing is nearer than the band");
		helper.assertTrue(Hunts.sightFor(false, true, 115.0, 44.0) == Hunts.NIGHT_SIGHT, "at night a wolf counts as seen within 20 at most");
		helper.assertTrue(Math.abs(Hunts.sightFor(true, false, 24.0, 44.0) - 14.4) < 1.0E-6, "in a full fog 0.6 of the fog end");
		helper.assertTrue(Math.abs(Hunts.sightFor(false, false, 115.0, 44.0) - 44.0) < 1.0E-6, "by day the view's own reach");
		helper.assertTrue(Hunts.PLAIN_DISTANCE <= Hunts.NIGHT_SIGHT && night[0] <= Hunts.PLAIN_DISTANCE, "a wolf in the band can be plainly seen");
		Sc26Config cfg = new Sc26Config();
		helper.assertTrue(cfg.maxMobs == 6 && CallTrap.most(cfg) == 6, "the call's default: " + cfg.maxMobs);
		cfg.maxMobs = 20;
		helper.assertTrue(CallTrap.most(cfg) == Call.HARD_MAX && Call.HARD_MAX == 6, "a config of 20 took " + CallTrap.most(cfg));
		Sc26Config old = new Sc26Config();
		old.version = 1;
		old.maxMobs = 8;
		old.upgrade();
		helper.assertTrue(old.version == Sc26Config.CURRENT && old.maxMobs == 6, "version 1's default 8 becomes 6: " + old.maxMobs);
		Sc26Config edited = new Sc26Config();
		edited.version = 1;
		edited.maxMobs = 5;
		edited.upgrade();
		helper.assertTrue(edited.maxMobs == 5, "a hand edit is kept: " + edited.maxMobs);
		double look = 0.0;
		double clear = Math.toRadians(Call.SPIDER_CLEAR);
		for (int deg = -180; deg < 180; deg += 5) {
			double turned = Call.outOfLook(Math.toRadians(deg), look, clear);
			double off = Math.abs(Math.atan2(Math.sin(turned - look), Math.cos(turned - look)));
			helper.assertTrue(off >= clear - 1.0E-9, "a spider's place " + deg + " stays in the look: " + Math.toDegrees(off));
			if (Math.abs(deg) >= Call.SPIDER_CLEAR) {
				helper.assertTrue(Math.abs(turned - Math.toRadians(deg)) < 1.0E-9, "a place behind was moved: " + deg);
			}
		}
		helper.succeed();
	}

	/**
	 * R10-5, measured through the planner at night with the default numbers (the stalk at its shortest, 60 s): the tells
	 * come first. Before anything hurts the player: 60 s; the long tells heard (each a chorus of whines and then a growl);
	 * a wolf plainly seen in the middle of the view within 20; the band held at 16 to 24 (one walking into view aside); the
	 * closing for its 20 s at 12 to 16, each wolf growling as it stepped in, the third long tell as it began. And the escape
	 * works then: a second player who goes two up on a pillar as the closing begins is waited for below and never bitten,
	 * and the pack goes.
	 */
	@GameTest(environment = ENV_E, maxTicks = 4800)
	public void thePacksTellsComeFirstPlainlyAndTheEscapeWorksAfterTheClosing(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		Field g = new Field(helper, 96);
		f.cfg25.stalkSecondsMax = 60;
		g.cfg25.stalkSecondsMax = 60;
		g.cfg25.siegeSeconds = 10;
		Target[] p = new Target[2];
		Pack[] pack = new Pack[2];
		List<String> early = new ArrayList<>();
		List<String> band = new ArrayList<>();
		boolean[] climbed = {false};
		f.whenReady(() -> {
		}).thenWaitUntil(() -> helper.assertTrue(g.ready(), "loading")).thenExecute(() -> {
			g.lit();
			p[0] = f.player(0.5, 0.5, 0.0F);
			f.wolf(-20.5, 64.5);
			f.wolf(0.5, 70.5);
			f.wolf(20.5, 64.5);
			f.use(p[0]);
			helper.assertTrue(f.planner().arm(p[0], Traps.SC25, false).armed(), "not armed: " + f.why25(p[0], f.planner()));
			pack[0] = (Pack) Hunts.of(p[0].getUUID()).orElseThrow();
			g.set(g.origin, Blocks.COBBLESTONE);
			g.set(g.origin.above(), Blocks.COBBLESTONE);
			p[1] = g.player(0.5, 3.5, 180.0F);
			p[1].mortal = false;
			pack[1] = g.pack(p[1], List.of(g.wolf(-8.5, -40.5), g.wolf(0.5, -42.5), g.wolf(8.5, -40.5)));
		}).thenWaitUntil(() -> {
			long now = f.level.getServer().getTickCount();
			if (pack[0].hostileAt() < 0 && (p[0].bites > 0 || f.wolves(pack[0]).stream().anyMatch(w -> TAMPER.isHunting(w) || w.isAngry()))) {
				early.add("at " + (now - pack[0].startedAt()));
			}
			long in = now - pack[0].startedAt();
			if (pack[0].phaseNow() == Pack.Phase.STALK && in > 500) {
				boolean closing = pack[0].closingAt() >= 0 && now - pack[0].closingAt() > 160;
				for (Wolf wolf : f.wolves(pack[0])) {
					if (wolf.getUUID().equals(pack[0].shown())) {
						continue;
					}
					double d = across(wolf.position(), p[0].position());
					if (closing ? d < 10.5 || d > 19.0 : pack[0].closingAt() < 0 && (d < 13.0 || d > 28.0)) {
						com.forzacode.a1016_02.atmosphere.mob.TamperState state = ((com.forzacode.a1016_02.atmosphere.mob.TamperedMob) wolf).a1016_02$tamper();
						Vec3 to = state == null ? null : state.steerTarget();
						band.add((int) d + (closing ? " closing" : "") + " at " + in + " (" + pack[0].mobs().indexOf(wolf.getUUID()) + ", to "
								+ (to == null ? "-" : (int) across(to, p[0].position())) + (TAMPER.isSteered(wolf) ? "" : ", free") + ")");
					}
				}
			}
			// The second player: up on the pillar as their closing begins (they heard it, saw them come in).
			if (!climbed[0] && pack[1].closingAt() >= 0) {
				climbed[0] = true;
				p[1].snapTo(g.vec(0.5, 2, 0.5), 180.0F, 0.0F);
			}
			helper.assertTrue(pack[0].hostileAt() >= 0 && p[0].bites > 0, "not turned and biting yet (" + pack[0].phase() + " " + pack[0].why() + ")");
			helper.assertTrue(pack[1].done(), "the second pack did not go yet (" + pack[1].phase() + " " + pack[1].why() + ")");
		}).thenExecute(() -> {
			Pack a = pack[0];
			long turned = a.hostileAt();
			helper.assertTrue(early.isEmpty(), "hostile before it turned: " + early);
			helper.assertTrue(turned - a.startedAt() >= Hunts.TOLD_TICKS, "it turned " + (turned - a.startedAt()) + " ticks in");
			helper.assertTrue(a.heardBefore(turned) >= 3, "long tells heard before it turned: " + a.heardBefore(turned));
			helper.assertTrue(a.plainAt() >= 0 && a.plainAt() < turned && a.seenAt() >= 0, "plainly seen at " + a.plainAt() + ", turned at " + turned);
			helper.assertTrue(a.closingAt() >= 0 && turned - a.closingAt() >= Pack.CLOSING_TICKS - Hunts.STEP, "the closing lasted " + (turned - a.closingAt()));
			// Three long tells before the turn (the start, 30 to 45 s in, the closing), each three whines 9 ticks apart and then a growl.
			long sounds = a.tells().stream().filter(t -> t.what().equals("long") && t.tick() < turned).count();
			long apart = a.tells().stream().filter(t -> t.what().equals("long") && t.tick() < turned).map(HuntScene.Tell::tick).distinct().count();
			helper.assertTrue(sounds >= 3 * 4 && apart >= 3 * 4, "each long tell three whines and a growl: " + sounds + " sounds at " + apart + " ticks");
			long steppedIn = a.tells().stream().filter(t -> t.what().equals("low") && t.tick() >= a.closingAt() && t.tick() < turned && t.volume() == 1.5F).count();
			helper.assertTrue(steppedIn >= 3, "growls as they stepped in: " + steppedIn);
			helper.assertTrue(a.tells().stream().anyMatch(t -> t.what().equals("long") && t.tick() == a.closingAt()), "no long tell as the closing began");
			helper.assertTrue(band.size() <= 6, "out of the band: " + band);
			helper.assertTrue(p[1].bites == 0 && pack[1].hostileAt() < 0 && pack[1].why().equals("waited"), "the pillar: " + pack[1].why() + ", bites "
					+ p[1].bites);
			for (Wolf wolf : Hunts.loaded(g.level, pack[1].mobs(), Wolf.class)) {
				helper.assertFalse(TAMPER.isTampered(wolf) || wolf.isAngry(), "a wolf of the pillar's pack was not let go");
			}
			f.done();
			g.done();
		}).thenSucceed();
	}

	/**
	 * R10-5, R11-3: the pack behind a player who never turns round (the gallery's {@code f_1}). One of them still walks round,
	 * never across, into the middle of their view 17 to 20 out, and is plainly seen there within {@link #BEHIND_SEEN_BY} of
	 * the start, long before the first part could end.
	 */
	@GameTest(environment = ENV_E, maxTicks = 2400)
	public void theOneShownWalksRoundFromBehindIntoTheMiddleOfTheView(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.cfg25.stalkSecondsMin = 300;
		f.cfg25.stalkSecondsMax = 300;
		Target[] p = new Target[1];
		Pack[] pack = new Pack[1];
		double[] nearest = {Double.MAX_VALUE};
		f.whenReady(() -> {
			p[0] = f.player(0.5, 0.5, 180.0F); // facing north; the pack south, behind them
			f.wolf(-20.5, 64.5);
			f.wolf(0.5, 70.5);
			f.wolf(20.5, 64.5);
			f.use(p[0]);
			helper.assertTrue(f.planner().arm(p[0], Traps.SC25, false).armed(), "not armed: " + f.why25(p[0], f.planner()));
			pack[0] = (Pack) Hunts.of(p[0].getUUID()).orElseThrow();
		}).thenWaitUntil(() -> {
			Pack a = pack[0];
			if (a.shown() != null && f.level.getEntity(a.shown()) instanceof Wolf walking) {
				nearest[0] = Math.min(nearest[0], across(walking.position(), p[0].position()));
			}
			helper.assertTrue(a.plainAt() >= 0, "not plainly seen yet, " + (f.level.getServer().getTickCount() - a.startedAt()) + " ticks in");
		}).thenExecute(() -> {
			Pack a = pack[0];
			long in = a.plainAt() - a.startedAt();
			helper.assertTrue(in <= BEHIND_SEEN_BY, "plainly seen only " + in + " ticks in");
			helper.assertTrue(nearest[0] >= Pack.BAND_FLOOR, "the one shown came across, " + (int) nearest[0] + " from them");
			helper.assertTrue(a.hostileAt() < 0 && p[0].bites == 0, "it turned while being shown");
			f.done();
		}).thenSucceed();
	}

	/** The one shown from straight behind is plainly seen by then (ticks): the 15 s wait and its walk round. */
	static final long BEHIND_SEEN_BY = 45 * 20;

	/**
	 * R10-12, R10-19, R11-15, measured through the planner: with ten monsters near and a config asking for twenty, the
	 * call takes six. Before any is let go: their quiet, their voices heard, one of them (never a spider) walked into the
	 * middle of the player's view 14 to 16 out and plainly seen, every spider on the ring away from the player's look, and
	 * at least 20 s standing on the ring.
	 */
	@GameTest(environment = ENV_E, maxTicks = 3600)
	public void theCallTakesSixShowsOneInTheMiddleOfTheViewAndKeepsItsSpidersBehind(GameTestHelper helper) {
		Field f = new Field(helper, 96);
		f.cfg26.maxMobs = 20;
		Target[] p = new Target[1];
		Call[] call = new Call[1];
		List<String> bad = new ArrayList<>();
		java.util.Set<UUID> shown = new java.util.HashSet<>();
		double[] plainAt = {-1};
		f.whenReady(() -> {
			p[0] = f.player(0.5, 0.5, 180.0F);
			p[0].mortal = false;
			p[0].getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			double[][] at = {{0, -40}, {28, -28}, {-28, -28}, {40, 0}, {-40, 0}, {28, 28}, {-28, 28}, {0, 44}, {34, 34}, {-34, -34}};
			for (int i = 0; i < at.length; i++) {
				// The test server's sky is day: husks, which never burn, stand in for the walking dead and the archers.
				EntityType<? extends Mob> type = i % 3 == 0 ? EntityTypes.SPIDER : EntityTypes.HUSK;
				f.mob(type, at[i][0] + 0.5, at[i][1] + 0.5);
			}
			f.use(p[0]);
			AccidentPlannerImpl planner = f.planner();
			helper.assertTrue(planner.arm(p[0], Traps.SC26, false).armed(), "not armed: " + f.why26(p[0], planner));
			call[0] = (Call) Hunts.of(p[0].getUUID()).orElseThrow();
			helper.assertTrue(call[0].mobs().size() == Call.HARD_MAX && f.data.armed(p[0].getUUID()).orElseThrow().mobs().size() == Call.HARD_MAX,
					"taken: " + call[0].mobs().size());
		}).thenWaitUntil(() -> {
			Call c = call[0];
			long in = f.level.getServer().getTickCount() - c.startedAt();
			UUID now = c.shown();
			if (now != null) {
				shown.add(now);
			}
			for (Mob mob : f.mobs(c)) {
				double d = across(mob.position(), p[0].position());
				if (c.hostileAt() < 0 && shown.contains(mob.getUUID()) && d < 12.5) {
					bad.add("the one shown " + (int) d + " off at " + in);
				}
				if (c.hostileAt() < 0 && !shown.contains(mob.getUUID()) && d < 16.0) {
					bad.add("one " + (int) d + " off at " + in);
				}
				if (mob.getType() == EntityTypes.SPIDER && c.hostileAt() < 0 && in > 700 && d < 26.0) {
					double off = Math.toDegrees(Math.abs(Math.atan2(Math.sin(Hunts.angle(p[0].position(), mob.position()) - Hunts.angle(p[0].getViewVector(1.0F))),
							Math.cos(Hunts.angle(p[0].position(), mob.position()) - Hunts.angle(p[0].getViewVector(1.0F))))));
					if (off < Call.SPIDER_CLEAR - 15.0) {
						bad.add("a spider " + (int) off + " degrees off the look at " + in);
					}
				}
				if (c.plainAt() >= 0 && plainAt[0] < 0 && shown.contains(mob.getUUID())) {
					plainAt[0] = d;
				}
			}
			helper.assertTrue(c.hostileAt() >= 0, "not come in yet (" + c.phase() + " " + c.why() + ")");
		}).thenExecute(() -> {
			Call c = call[0];
			helper.assertTrue(bad.isEmpty(), String.join("; ", bad.subList(0, Math.min(6, bad.size()))));
			helper.assertTrue(c.hostileAt() - c.startedAt() >= Hunts.TOLD_TICKS, "came in at " + (c.hostileAt() - c.startedAt()));
			helper.assertTrue(c.heardBefore(c.hostileAt()) >= 2, "voices heard before: " + c.heardBefore(c.hostileAt()));
			helper.assertTrue(c.plainAt() >= 0 && c.plainAt() < c.hostileAt(), "plainly seen at " + c.plainAt());
			helper.assertTrue(!shown.isEmpty() && shown.stream().allMatch(id -> f.level.getEntity(id) instanceof Mob m && m.getType() != EntityTypes.SPIDER),
					"the one walked into view: " + shown);
			helper.assertTrue(plainAt[0] < 0 || plainAt[0] >= 12.5 && plainAt[0] <= 17.5, "plainly seen " + plainAt[0] + " off");
			f.done();
		}).thenSucceed();
	}

	/**
	 * R10-4, the scene's side of a trespass's call: called from a place 300 east, the pack's first long tell (its whines and
	 * its growl) comes from the east, heard, and the pack keeps to that side; the call's first voices, when its quiet ends,
	 * come from the east too. Not called: from the pack itself, as before.
	 */
	@GameTest(environment = ENV_E, maxTicks = 1200)
	public void aCalledHuntsFirstTellComesFromThePlacesWay(GameTestHelper helper) {
		Field f = new Field(helper, 64);
		Field g = new Field(helper, 64);
		Pack[] pack = new Pack[1];
		Call[] call = new Call[1];
		Target[] p = new Target[2];
		f.whenReady(() -> {
		}).thenWaitUntil(() -> helper.assertTrue(g.ready(), "loading")).thenExecute(() -> {
			g.lit();
			p[0] = f.player(0.5, 0.5, 180.0F);
			Vec3 place = f.vec(300.5, 0, 0.5);
			List<Wolf> wolves = List.of(f.wolf(-8.5, -40.5), f.wolf(0.5, -42.5), f.wolf(8.5, -40.5));
			pack[0] = new Pack(p[0].getUUID(), f.level, f.links(), -1, Prey.of(p[0]), wolves, f.cfg25, () -> true, null, Pack.Phase.STALK);
			pack[0].calledFrom(place);
			Hunts.start(pack[0]);
			pack[0].begin();
			p[1] = g.player(0.5, 0.5, 180.0F);
			p[1].mortal = false;
			p[1].getInventory().add(new ItemStack(Items.STONE_PICKAXE));
			List<Mob> callees = List.of(g.mob(EntityTypes.HUSK, 0.5, -40.5), g.mob(EntityTypes.HUSK, -30.5, 20.5), g.mob(EntityTypes.HUSK, 30.5, 20.5));
			call[0] = new Call(p[1].getUUID(), g.level, g.links(), -1, Prey.of(p[1]), callees, g.cfg26, () -> true, null);
			call[0].calledFrom(g.vec(300.5, 0, 0.5));
			Hunts.start(call[0]);
			call[0].begin();
		}).thenWaitUntil(() -> helper.assertTrue(call[0].tells().size() >= 3, "no voices yet (" + call[0].phase() + ")")).thenExecute(() -> {
			Vec3 feet = p[0].position();
			List<HuntScene.Tell> first = pack[0].tells().stream().filter(t -> t.what().equals("long") && t.tick() - pack[0].startedAt() < 40).toList();
			helper.assertTrue(first.size() == 4 && pack[0].heardBefore(f.level.getServer().getTickCount() + 1) >= 1, "the first long tell: " + first);
			for (HuntScene.Tell t : first) {
				double off = Math.toDegrees(Math.abs(Math.atan2(Math.sin(Hunts.angle(feet, t.at()) - Hunts.angle(feet, f.vec(300.5, 0, 0.5))),
						Math.cos(Hunts.angle(feet, t.at()) - Hunts.angle(feet, f.vec(300.5, 0, 0.5))))));
				helper.assertTrue(off <= 15.0 && t.at().distanceTo(feet) < 48.0, "a sound of the first tell " + (int) off + " degrees off the place's way");
			}
			Vec3 middle = Vec3.ZERO;
			for (Wolf w : f.wolves(pack[0])) {
				middle = middle.add(w.position());
			}
			middle = middle.scale(1.0 / f.wolves(pack[0]).size());
			helper.assertTrue(middle.x > feet.x, "the pack does not keep to the place's side: " + middle);
			Vec3 at = p[1].position();
			for (HuntScene.Tell t : call[0].tells()) {
				double off = Math.toDegrees(Math.abs(Math.atan2(Math.sin(Hunts.angle(at, t.at()) - Hunts.angle(at, g.vec(300.5, 0, 0.5))),
						Math.cos(Hunts.angle(at, t.at()) - Hunts.angle(at, g.vec(300.5, 0, 0.5))))));
				helper.assertTrue(off <= 25.0 && t.at().distanceTo(at) < 32.0, "a first voice " + (int) off + " degrees off the place's way");
			}
			helper.assertTrue(call[0].heardBefore(g.level.getServer().getTickCount() + 1) >= 1, "the first voices were not heard");
			pack[0].stop(true, "test over");
			call[0].stop(true, "test over");
			f.done();
			g.done();
		}).thenSucceed();
	}

	/** The gallery's way (a stand-in, no player): the call still walks one into the middle of the stand-in's view and sees it. */
	@GameTest(environment = ENV_E, maxTicks = 1600)
	public void theCallShowsOneToAStandInToo(GameTestHelper helper) {
		Field f = new Field(helper, 80);
		Call[] call = new Call[1];
		Prey[] stand = new Prey[1];
		double[] nearest = {Double.MAX_VALUE};
		f.whenReady(() -> {
			double[][] at = {{0, -56}, {48, -26}, {44, 34}, {-6, 60}, {-48, 28}, {-42, -36}};
			List<Mob> callees = new ArrayList<>();
			for (int i = 0; i < at.length; i++) {
				callees.add(i == 5 ? f.mob(EntityTypes.SPIDER, at[i][0] + 0.5, at[i][1] + 0.5) : f.mob(EntityTypes.HUSK, at[i][0] + 0.5, at[i][1] + 0.5));
			}
			stand[0] = Prey.standIn(f.level, f.vec(0.5, 0, 0.5), 0.0F, 0.0F);
			call[0] = new Call(UUID.randomUUID(), f.level, HuntScene.Links.gallery(), -1, stand[0], callees, f.cfg26, () -> true, stand[0]);
			Hunts.start(call[0]);
			call[0].lifetime(20 * 70);
			call[0].begin();
		}).thenWaitUntil(() -> {
			UUID shown = call[0].shown();
			if (shown != null && f.level.getEntity(shown) instanceof Mob m) {
				nearest[0] = Math.min(nearest[0], across(m.position(), stand[0].feet()));
			}
			helper.assertTrue(call[0].plainAt() >= 0 && nearest[0] <= Call.SHOW_FAR + 1.0, "not plainly seen at its spot yet (" + call[0].phase() + "; "
					+ String.join(" / ", call[0].describe(f.level.getServer().getTickCount())) + "; shown " + shown + " nearest " + nearest[0] + ")");
		}).thenExecute(() -> {
			helper.assertTrue(nearest[0] >= Call.SHOW_NEAR - 2.0, "the one shown came to " + nearest[0]);
			call[0].stop(true, "test over");
			f.done();
		}).thenSucceed();
	}

	// --- helpers ---

	static double across(Vec3 a, Vec3 b) {
		return Math.hypot(a.x - b.x, a.z - b.z);
	}

	static ServerLevel level(GameTestHelper helper, net.minecraft.resources.ResourceKey<Level> key) {
		return helper.getLevel().getServer().getLevel(key);
	}

	private static Field[] fields(Field... fields) {
		return fields;
	}

	/**
	 * A player who lives in a level without joining (never on the player list, so never anyone's participant but a test's
	 * own group), whose game mode a test can switch, and who takes damage; unless {@link #mortal}, a hit that would kill
	 * them leaves them at full health (the bites still count). Remembers what last hurt it and how often.
	 */
	static final class Target extends ServerPlayer {
		GameType mode = GameType.SURVIVAL;
		Entity bitBy;
		int bites;
		boolean mortal;

		Target(ServerLevel level) {
			// A 12 chunk view (the default client's 2 would put the fog's end at about 30 blocks).
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player"), new ClientInformation("en_us", 12,
					net.minecraft.world.entity.player.ChatVisiblity.FULL, true, 0, net.minecraft.world.entity.HumanoidArm.RIGHT, false, false,
					net.minecraft.server.level.ParticleStatus.ALL));
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode == null ? GameType.SURVIVAL : mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}

		@Override
		public boolean isInvulnerableTo(ServerLevel level, DamageSource source) {
			return isSpectator() || isCreative();
		}

		@Override
		public void onEnterCombat() {
		}

		@Override
		public void indicateDamage(double xd, double zd) {
		}

		@Override
		public void onLeaveCombat() {
		}

		@Override
		public boolean hurtServer(ServerLevel level, DamageSource source, float damage) {
			if (!mortal && getHealth() - damage < 2.0F) {
				setHealth(getMaxHealth());
			}
			boolean hurt = super.hurtServer(level, source, damage);
			if (hurt) {
				bitBy = source.getEntity();
				bites++;
			}
			return hurt;
		}
	}

	/**
	 * A patch of the test server's flat overworld, far out and unique to the test (its own slot), its chunks held loaded:
	 * coordinates relative to {@link #origin} (the first air cell over the flat ground), mock players in a private
	 * {@link Group}, its own planner data and pacing data, the gate's inputs a test can turn (dark, the fog, the day, the
	 * switch, forced, the view), the mobs it made, discarded at the end.
	 */
	static final class Field {
		private static final int SPACING = 640;
		private static final AtomicInteger SLOTS = new AtomicInteger();
		private static final BlockPos BASE = new BlockPos(16 * ThreadLocalRandom.current().nextInt(1_100_000, 1_190_000), 0,
				-16 * ThreadLocalRandom.current().nextInt(1_100_000, 1_190_000));

		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		final int reach;
		final Group group;
		final AccidentData data = new AccidentData();
		final HuntData hunts = new HuntData();
		final Sc25Config cfg25 = new Sc25Config();
		final Sc26Config cfg26 = new Sc26Config();
		final boolean[] dark = {true};
		final boolean[] fog = {true};
		final long[] day = {10};
		boolean enabled = true;
		boolean forced;
		/** False for a field whose test needs only its blocks and mobs as they are (no ticking, no light). */
		boolean ticking = true;
		ViewGate view = Yard.NOBODY;
		private final List<Entity> made = new ArrayList<>();

		Field(GameTestHelper helper, int reach) {
			this.helper = helper;
			this.level = helper.getLevel().getServer().overworld();
			this.reach = reach;
			BlockPos column = BASE.offset(SLOTS.getAndIncrement() * SPACING, 0, 0);
			force(column, true);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			this.origin = new BlockPos(column.getX(), ground, column.getZ());
			this.group = new Group(helper);
			cfg25.nightChance = 1.0;
		}

		private void force(BlockPos column, boolean on) {
			for (int cx = (column.getX() - reach) >> 4; cx <= (column.getX() + reach) >> 4; cx++) {
				for (int cz = (column.getZ() - reach) >> 4; cz <= (column.getZ() + reach) >> 4; cz++) {
					level.setChunkForced(cx, cz, on);
					if (on) {
						level.getChunk(cx, cz);
					}
				}
			}
		}

		/** Every chunk of the field is entity-ticking by its tickets (its entities may still be loading). */
		private boolean entityTicking() {
			for (int cx = (origin.getX() - reach) >> 4; cx <= (origin.getX() + reach) >> 4; cx++) {
				for (int cz = (origin.getZ() - reach) >> 4; cz <= (origin.getZ() + reach) >> 4; cz++) {
					if (!level.isPositionEntityTicking(new BlockPos((cx << 4) + 8, origin.getY(), (cz << 4) + 8))) {
						return false;
					}
				}
			}
			return true;
		}

		boolean ready() {
			if (!ticking) {
				return true; // its chunks were made in the constructor; it needs no ticking mobs and no light
			}
			for (int cx = (origin.getX() - reach) >> 4; cx <= (origin.getX() + reach) >> 4; cx++) {
				for (int cz = (origin.getZ() - reach) >> 4; cz <= (origin.getZ() + reach) >> 4; cz++) {
					if (!level.areEntitiesActuallyLoadedAndTicking(new ChunkPos(cx, cz))) {
						return false;
					}
				}
			}
			return true;
		}

		GameTestSequence whenReady(Runnable then) {
			// First in real time (at most 30 s) until the chunks are entity-ticking, as SC2-3's patches wait (SC2-H); then the
			// entities' loading, in real time too (core.FarPatches, SC2-9: counted in ticks it raced the entity storage under load).
			return helper.startSequence().thenExecute(() -> {
				if (ticking) {
					long until = net.minecraft.util.Util.getMillis() + 30_000L;
					level.getServer().managedBlock(() -> entityTicking() || net.minecraft.util.Util.getMillis() > until);
				}
			}).thenWaitUntil(com.forzacode.a1016_02.core.FarPatches.until(helper, this::ready, "the field")).thenExecute(() -> {
				if (ticking) {
					lit();
				}
			}).thenExecute(then);
		}

		/** Waits for the light engine over the field's middle, so sky light reads true (the gate asks for it). */
		void lit() {
			Live.settleLight(level, new BoundingBox(origin.getX() - 8, origin.getY() - 2, origin.getZ() - 8, origin.getX() + 8, origin.getY() + 4, origin.getZ() + 8));
			helper.assertTrue(level.getBrightness(net.minecraft.world.level.LightLayer.SKY, origin.above()) == 15,
					"no sky light over the field: " + level.getBrightness(net.minecraft.world.level.LightLayer.SKY, origin.above()));
		}

		Vec3 vec(double x, double y, double z) {
			return Vec3.atLowerCornerOf(origin).add(x, y, z);
		}

		void set(BlockPos pos, net.minecraft.world.level.block.Block block) {
			set(pos, block.defaultBlockState());
		}

		void set(BlockPos pos, BlockState state) {
			level.setBlock(pos, state, 3);
		}

		/** A player of this field's group at (x, z) on the ground, facing {@code yaw}, health 20, two cobblestone. */
		Target player(double x, double z, float yaw) {
			Target p = new Target(level);
			p.snapTo(vec(x, 0, z), yaw, 0.0F);
			p.setYHeadRot(yaw);
			p.setYBodyRot(yaw);
			p.getInventory().add(new ItemStack(Items.COBBLESTONE, 2));
			group.online.add(p);
			return p;
		}

		/** A mock viewer (not in the group) at (x, z) facing {@code yaw}. */
		Target viewerAt(double x, double z, float yaw) {
			Target v = new Target(level);
			v.snapTo(vec(x, 0, z), yaw, 0.0F);
			v.setYHeadRot(yaw);
			return v;
		}

		Wolf wolf(double x, double z) {
			return mob(EntityTypes.WOLF, x, z);
		}

		<T extends Mob> T mob(EntityType<T> type, double x, double z) {
			T mob = type.create(level, EntitySpawnReason.COMMAND);
			helper.assertTrue(mob != null, "could not make a " + type);
			mob.snapTo(vec(x, 0, z), 0.0F, 0.0F);
			mob.setPersistenceRequired();
			helper.assertTrue(level.addFreshEntity(mob), "could not add a " + type);
			made.add(mob);
			return mob;
		}

		/** An entity a test made itself: discarded at the end with the rest. */
		void made(Entity entity) {
			made.add(entity);
		}

		/** The pack trap reads this field's inputs for this player. */
		void use(ServerPlayer player) {
			PackTrap.use(player.getUUID(), new PackTrap.Env(cfg25, hunts, l -> group.online.stream().filter(p -> p.level() == l).map(p -> (ServerPlayer) p).toList(),
					() -> dark[0], () -> day[0], level::getGameTime, () -> enabled, () -> group.state, uuid -> -1L, 12345L, forced));
			CallTrap.use(player.getUUID(), new CallTrap.Env(cfg26, hunts, l -> group.online.stream().filter(p -> p.level() == l).map(p -> (ServerPlayer) p).toList(),
					() -> dark[0], () -> fog[0], () -> day[0], () -> enabled, () -> group.state, forced));
		}

		AccidentPlannerImpl planner() {
			return group.planner(data, view);
		}

		String why25(ServerPlayer player, AccidentPlannerImpl planner) {
			return PackTrap.refusal(player, planner.context(player), PackTrap.env(player));
		}

		String why26(ServerPlayer player, AccidentPlannerImpl planner) {
			return CallTrap.refusal(player, planner.context(player), CallTrap.env(player));
		}

		HuntScene.Links links() {
			return new HuntScene.Links(group.watch, null, view, () -> GameClock.playTicks(level.getServer()));
		}

		/** A pack scene for this player with these wolves, no trap (its own behaviour), begun. */
		Pack pack(Target player, List<Wolf> wolves) {
			Pack pack = new Pack(player.getUUID(), level, links(), -1, Prey.of(player), wolves, cfg25, () -> dark[0], null, Pack.Phase.STALK);
			Hunts.start(pack);
			pack.begin();
			return pack;
		}

		/** A call scene for this player with these monsters, no trap, begun. */
		Call call(Target player, List<? extends Mob> callees) {
			Call call = new Call(player.getUUID(), level, links(), -1, Prey.of(player), callees, cfg26, () -> dark[0], null);
			Hunts.start(call);
			call.begin();
			return call;
		}

		List<Wolf> wolves(HuntScene scene) {
			return Hunts.loaded(level, scene.mobs(), Wolf.class);
		}

		List<Mob> mobs(HuntScene scene) {
			return Hunts.loaded(level, scene.mobs(), Mob.class);
		}

		/** Ends this field's scenes, lets its mobs go and discards them, forgets its overrides, lets its chunks go. */
		void done() {
			for (ServerPlayer player : List.copyOf(group.online)) {
				Hunts.of(player.getUUID()).ifPresent(scene -> scene.stop(true, "test over"));
				PackTrap.use(player.getUUID(), null);
				CallTrap.use(player.getUUID(), null);
			}
			for (Entity entity : made) {
				if (entity instanceof Mob mob) {
					TAMPER.release(mob);
				}
				entity.discard();
			}
			made.clear();
			force(origin, false);
		}
	}
}
