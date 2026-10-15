package com.forzacode.a1016_02.ending.d;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.ProtectedAreas;
import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.SiteRegistry;
import com.forzacode.a1016_02.core.TraceLedger;
import com.forzacode.a1016_02.ending.EndingApi;
import com.forzacode.a1016_02.entity.HimEntity;
import com.forzacode.a1016_02.lore.LoreData;
import com.forzacode.a1016_02.lore.UntouchedGrove;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.entity.SignTextSlot;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Ending D with friends (D-064, STORY_CLUES M10): any participant does a step and progress is shared; the cairn's
 * first block is one player's (D-061) yet anyone takes it back; two players in one check move the chain one step; a
 * friend in another dimension does not count; step 5 takes a torch for a dead friend; the last minute goes on when
 * whoever placed the last plank leaves, waits while nobody is online, and resumes cleanly. Mock players get their own
 * online list and {@code PlayerWatch}; the last minute's world-wide effects go through recording ports.
 */
public class EndingDGroupTests extends EndingDDangerTests {
	private static PlayerWatch watchOver(List<ServerPlayer> online, HerobrineState state) {
		return new PlayerWatch(new PlayerWatch.Source() {
			@Override
			public List<ServerPlayer> online(MinecraftServer server) {
				return List.copyOf(online);
			}

			@Override
			public HerobrineState state(MinecraftServer server) {
				return state;
			}

			@Override
			public void participation(MinecraftServer server, UUID player, boolean participating) {
			}
		});
	}

	private static void write(GameTestHelper helper, BlockPos rel, String... lines) {
		helper.setBlock(rel, Blocks.OAK_SIGN);
		SignBlockEntity sign = helper.getBlockEntity(rel, SignBlockEntity.class);
		List<Component> messages = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			messages.add(i < lines.length ? Component.literal(lines[i]) : Component.empty());
		}
		sign.setText(new SignText(messages, messages, DyeColor.BLACK, false), SignTextSlot.FRONT);
	}

	@GameTest(structure = EndingDSupport.YARD)
	public void aFriendDoesTheMapTheGroveAndTakesTheOwnersBlockBack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		LoreData lore = LoreData.get(server);
		Optional<GlobalPos> oldGrove = lore.anchor(LoreData.GROVE);
		Optional<ProtectedAreas.Area> oldArea = Services.protectedAreas().get(UntouchedGrove.AREA_ID);
		HerobrineState real = HerobrineState.get(server);
		GlobalPos oldCairn = real.fragmentsPlaced().get("F13");
		EndingDState data = new EndingDState();
		EndingDConfig cfg = new EndingDConfig();
		try {
			lore.setAnchor(LoreData.GROVE, GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(8, 1, 8))));
			Services.protectedAreas().protect(UntouchedGrove.AREA_ID, level.dimension(),
					BoundingBox.fromCorners(helper.absolutePos(new BlockPos(0, 0, 0)), helper.absolutePos(new BlockPos(13, 11, 13))));
			// A owns the first blocks (chosen while A alone played), and stays far away at home; B does every step.
			HerobrineState state = new HerobrineState();
			List<ServerPlayer> online = new ArrayList<>();
			PlayerWatch watch = watchOver(online, state);
			ServerPlayer a = EndingDSupport.player(helper, new Vec3(8.5, 1, 300.5), 0, 0);
			ServerPlayer b = EndingDSupport.player(helper, new Vec3(8.5, 1, 8.5), 0, 0);
			ServerPlayer stranger = EndingDSupport.player(helper, new Vec3(9.5, 1, 9.5), 0, 0);
			online.add(a);
			helper.assertTrue(watch.ownerFor(server, EndingApi.FIRST_BLOCKS_OWNER).equals(Optional.of(a.getUUID())), "A is not the owner");
			online.add(b);
			helper.assertTrue(watch.ownerFor(server, EndingApi.FIRST_BLOCKS_OWNER).equals(Optional.of(a.getUUID())), "the owner flipped to B");
			List<ServerPlayer> both = List.of(a, b);

			// 1. The map: F28 read (by anyone), and B in the grove.
			Chain.check(server, data, cfg, both, Set.of("F28")::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.GROVE, "the map did not pass with B in the grove");

			// 2. The grove: B cuts a log; someone who takes no part does not count.
			helper.setBlock(10, 1, 10, Blocks.POPLAR_LOG);
			helper.setBlock(11, 1, 11, Blocks.POPLAR_LOG);
			BlockPos log = helper.absolutePos(new BlockPos(10, 1, 10));
			helper.assertFalse(Grove.onBreak(level, stranger, helper.absolutePos(new BlockPos(11, 1, 11)), Blocks.POPLAR_LOG.defaultBlockState(), data,
					watch::isParticipant), "a log cut by someone who takes no part counted");
			helper.assertTrue(Grove.onBreak(level, b, log, Blocks.POPLAR_LOG.defaultBlockState(), data, watch::isParticipant), "B's log did not count");
			Marks.clear();
			Chain.check(server, data, cfg, both, Set.of("F28", "F30")::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.TAKE_BACK, "the grove did not pass on B's log");

			// 3. Take it back: A's first block at the cairn. A throws something down there: the visit is spoiled for B too.
			BlockPos cairn = helper.absolutePos(new BlockPos(3, 1, 3));
			real.setFragmentPlaced("F13", GlobalPos.of(level.dimension(), cairn));
			Cairn.clear();
			b.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 5.5)), 0, 0);
			Cairn.watch(both, data, cfg);
			ItemEntity offering = new ItemEntity(level, cairn.getX() + 0.5, cairn.getY() + 1, cairn.getZ() + 0.5, new ItemStack(Items.BREAD));
			offering.setThrower(a);
			Cairn.onItemAdded(level, offering, cfg, watch::isParticipant);
			helper.assertTrue(Cairn.offering(), "A's throw at the cairn was no offering");
			helper.assertTrue(Cairn.onBreak(level, b, cairn, Blocks.CRAFTING_TABLE.defaultBlockState(), data, watch::isParticipant), "B's break was not seen");
			helper.assertFalse(data.has(EndingDState.FIRST_TAKEN), "it counted with A's offering on the visit");
			// Everyone leaves: the visit ends. Only then is the offering forgotten.
			b.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 30.5)), 0, 0);
			Cairn.watch(both, data, cfg);
			helper.assertTrue(Cairn.offering() && Cairn.atCairn(), "the visit ended while B was still near");
			b.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 60.5)), 0, 0);
			Cairn.watch(both, data, cfg);
			helper.assertFalse(Cairn.offering() || Cairn.atCairn(), "the visit did not end once everyone was far");
			b.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 5.5)), 0, 0);
			Cairn.watch(both, data, cfg);
			helper.assertFalse(Cairn.onBreak(level, stranger, cairn, Blocks.CRAFTING_TABLE.defaultBlockState(), data, watch::isParticipant),
					"someone who takes no part took it back");
			// M4: it gets out cleanly once it leaves the cairn on this visit, with nothing left on it.
			helper.assertTrue(Cairn.onBreak(level, b, cairn, Blocks.CRAFTING_TABLE.defaultBlockState(), data, watch::isParticipant)
					&& data.has(EndingDState.FIRST_NEAR), "B taking A's block back was not seen");
			Marks.clear();
			ItemStack first = new ItemStack(Items.CRAFTING_TABLE);
			Marks.set(first, Marks.FIRST_BLOCK);
			b.getInventory().add(first);
			b.snapTo(helper.absoluteVec(new Vec3(3.5, 1, 60.5)), 0, 0);
			Chain.check(server, data, cfg, both, Set.of("F28", "F30")::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.UNDER_SEED, "B carrying A's first block away did not count");
		} finally {
			oldGrove.ifPresentOrElse(g -> lore.setAnchor(LoreData.GROVE, g), () -> lore.removeAnchor(LoreData.GROVE));
			oldArea.ifPresentOrElse(area -> Services.protectedAreas().protect(area.id(), area.dimension(), area.box()),
					() -> Services.protectedAreas().unprotect(UntouchedGrove.AREA_ID));
			real.setFragmentPlaced("F13", oldCairn);
			Cairn.clear();
			Grove.clear();
			Marks.clear();
		}
		helper.succeed();
	}

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void friendsDownTheStairCountOnceAndOnlyInTheStairsWorld(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		EndingDConfig cfg = new EndingDConfig();
		StairPlan plan = EndingDSupport.buildStair(helper, data);
		try {
			data.setStep(Step.UNDER_SEED);
			// A friend in the Nether, at the chamber's very coordinates, is not down there.
			ServerPlayer nether = EndingDSupport.player(helper, new Vec3(5.5, 2, 5.5), 0, 0);
			nether.setServerLevel(server.getLevel(Level.NETHER));
			Chain.check(server, data, cfg, List.of(nether), Set.<String>of()::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.UNDER_SEED, "a friend in another dimension reached the chamber");

			// Six torches are already lit; two friends reach the chamber in the same tick: one step, not two.
			int[][] spots = {{5, 5}, {5, 11}, {11, 5}, {11, 11}, {5, 8}, {11, 8}, {8, 11}};
			for (int i = 0; i < 6; i++) {
				helper.setBlock(spots[i][0], 2, spots[i][1], Blocks.TORCH);
			}
			ServerPlayer b = EndingDSupport.player(helper, new Vec3(5.5, 2, 6.5), 0, 0);
			ServerPlayer c = EndingDSupport.player(helper, new Vec3(10.5, 2, 10.5), 0, 0);
			List<ServerPlayer> group = List.of(nether, b, c);
			Chain.check(server, data, cfg, group, Set.<String>of()::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.TORCHES, "two friends in one tick moved the chain past one step: " + data.step());
			Chain.check(server, data, cfg, group, Set.<String>of()::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.SENTENCE, "the torches lit before their step did not count once it came");

			// Step 5 with a dead friend: six, or six and one for them; nothing else.
			helper.assertTrue(Chain.torchesCount(6, cfg, 0) && !Chain.torchesCount(7, cfg, 0), "six only, with no dead friend");
			helper.assertTrue(Chain.torchesCount(6, cfg, 1) && Chain.torchesCount(7, cfg, 1) && !Chain.torchesCount(8, cfg, 1) && !Chain.torchesCount(5, cfg, 1),
					"six or seven with one dead friend");
			data.setStep(Step.TORCHES);
			helper.setBlock(spots[6][0], 2, spots[6][1], Blocks.TORCH);
			Chain.check(server, data, cfg, group, Set.<String>of()::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.TORCHES, "seven torches counted with no dead friend");
			Chain.check(server, data, cfg, group, Set.<String>of()::contains, EndingDSupport.EVERYONE, 1);
			helper.assertTrue(data.step() == Step.SENTENCE, "a torch lit for a dead friend did not count");

			// 6 and 7, done by C: the sentence, then his cross on the first block B carried down.
			write(helper, new BlockPos(EndingDSupport.AX - 2, 2, EndingDSupport.AZ + 1), "he is no", "longer", "with us");
			Chain.check(server, data, cfg, group, Set.<String>of()::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.CROSS, "C's sentence did not count");
			helper.setBlock(6, 2, 6, Blocks.CRAFTING_TABLE);
			helper.setBlock(6, 3, 6, Blocks.STONE);
			helper.setBlock(5, 3, 6, Blocks.STONE);
			helper.setBlock(7, 3, 6, Blocks.STONE);
			helper.setBlock(6, 4, 6, Blocks.POPLAR_PLANKS);
			data.track(GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(6, 2, 6))), Marks.FIRST_BLOCK);
			data.track(GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(6, 4, 6))), Marks.GROVE_WOOD);
			Chain.check(server, data, cfg, group, Set.<String>of()::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(data.step() == Step.LAST_MINUTE && LastMinute.running(), "the last plank did not start the last minute");
			LastMinute.Phase phase = LastMinute.phase();
			Chain.check(server, data, cfg, group, Set.<String>of()::contains, EndingDSupport.EVERYONE, 0);
			helper.assertTrue(LastMinute.running() && LastMinute.phase() == phase && data.step() == Step.LAST_MINUTE,
					"the last minute was started again");
		} finally {
			LastMinute.reset();
			Chain.clear();
		}
		helper.succeed();
	}

	/** Records what the last minute does to everyone; never ends the world's story, never spawns him. */
	private static final class Recording implements LastMinute.Ports {
		int completions;
		int fogGone;
		int musicOn;
		final List<UUID> silenced = new ArrayList<>();
		final List<UUID> music = new ArrayList<>();
		/** Whose eyes his far-shore spot was looked for (none is ever found here). */
		final List<UUID> spotsFor = new ArrayList<>();

		@Override
		public void complete(MinecraftServer server, EndingDConfig cfg) {
			completions++;
		}

		@Override
		public void silence(ServerPlayer player, int ticks, int fadeTicks) {
			silenced.add(player.getUUID());
		}

		@Override
		public void calmMusic(ServerPlayer player) {
			music.add(player.getUUID());
		}

		@Override
		public void fogGone(MinecraftServer server) {
			fogGone++;
		}

		@Override
		public void musicOn(MinecraftServer server) {
			musicOn++;
		}

		@Override
		public void previewEffects(ServerPlayer player, boolean fogGone, boolean musicOn) {
			throw new AssertionError("a real last minute played a preview's effects");
		}

		@Override
		public Optional<HimEntity> spawn(ServerLevel level, Vec3 feet, float yaw) {
			return Optional.empty();
		}

		@Override
		public List<SiteRegistry.Site> groves(ServerLevel level, BlockPos near) {
			return List.of();
		}

		@Override
		public Optional<LastMinute.Spot> farShore(ServerLevel level, ServerPlayer player, BlockPos from, EndingDConfig cfg) {
			spotsFor.add(player.getUUID());
			return Optional.empty();
		}
	}

	/** Ticks the last minute for {@code present} from {@code from}, up to {@code max} ticks or until {@code phase}; returns the tick reached. */
	private static long play(MinecraftServer server, EndingDState data, EndingDConfig cfg, LastMinute.Present present, long from, int max,
			LastMinute.Phase until) {
		long at = from;
		for (int i = 0; i < max && LastMinute.running() && LastMinute.phase() != until; i++) {
			LastMinute.tick(server, data, cfg, present, ++at);
		}
		return at;
	}

	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void theLastMinuteGoesOnWhenThePlankPlacerLeaves(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		EndingDConfig cfg = new EndingDConfig();
		StairPlan plan = EndingDSupport.buildStair(helper, data);
		BlockPos lost = plan.stairs().stream().filter(s -> s.getY() >= plan.chamberCeil()).findFirst().orElseThrow();
		helper.assertTrue(Services.traces().forced().remove(level, lost, Stair.LOSS_CAUSE), "setup: the stair block was not taken");
		TraceLedger.Entry taken = TraceLedger.get(server).entries().stream().filter(e -> e.cause().equals(Stair.LOSS_CAUSE) && e.pos().pos().equals(lost))
				.findFirst().orElseThrow();
		Recording ports = new Recording();
		ServerPlayer placer = EndingDSupport.player(helper, new Vec3(6.5, 2, 6.5), 0, 0);
		ServerPlayer friend = EndingDSupport.player(helper, new Vec3(9.5, 2, 9.5), 0, 0);
		ServerPlayer late = EndingDSupport.player(helper, new Vec3(10.5, 2, 10.5), 0, 0);
		LastMinute.Present both = LastMinute.Present.of(List.of(placer, friend));
		LastMinute.Present friendOnly = LastMinute.Present.of(List.of(friend));
		LastMinute.Present nobody = LastMinute.Present.of(List.of());
		HerobrineState real = HerobrineState.get(server);
		boolean flagBefore = real.hasFlag(LastMinute.FLAG);
		try {
			LastMinute.use(ports);
			LastMinute.start(server, data, false, placer.blockPosition());
			helper.assertTrue(data.step() == Step.LAST_MINUTE, "the step did not move in the tick it started");
			long at = server.getTickCount();
			LastMinute.tick(server, data, cfg, both, ++at);
			helper.assertTrue(ports.completions == 1 && data.completeDay() >= 0, "D was not completed once");
			helper.assertTrue(ports.silenced.containsAll(List.of(placer.getUUID(), friend.getUUID())), "the silence did not reach everyone present");
			helper.assertTrue(LastMinute.phase() == LastMinute.Phase.FOOTSTEPS, "no footsteps after the start: " + LastMinute.phase());

			// The one who placed the last plank leaves: the stair still climbs back, for the friend.
			at = play(server, data, cfg, friendOnly, at, 2000, LastMinute.Phase.TORCH);
			helper.assertTrue(level.getBlockState(lost).getBlock() instanceof StairBlock && !TraceLedger.get(server).entries().contains(taken),
					"the stair block did not come back once the placer left");

			// A restart in the middle: it resumes, and D is not completed a second time.
			LastMinute.reset();
			LastMinute.resume(server, data);
			helper.assertTrue(LastMinute.running(), "it did not resume after the restart");
			at = play(server, data, cfg, friendOnly, at, 4000, LastMinute.Phase.CLIMB);
			helper.assertTrue(LastMinute.phase() == LastMinute.Phase.CLIMB && ports.completions == 1, "the resumed minute completed D again or stalled: "
					+ LastMinute.phase());

			// Nobody online for a long while: it waits, its clocks held; someone joining gets the silence too.
			LastMinute.tick(server, data, cfg, nobody, at += 100000);
			helper.assertTrue(LastMinute.phase() == LastMinute.Phase.CLIMB, "it went on with nobody there: " + LastMinute.phase());
			LastMinute.tick(server, data, cfg, friendOnly, ++at);
			helper.assertTrue(LastMinute.phase() == LastMinute.Phase.CLIMB, "the wait ran out the climb's clock");
			LastMinute.onParticipation(late, cfg);
			helper.assertTrue(ports.silenced.contains(late.getUUID()), "someone who joined mid-minute did not get the silence");

			// The friend comes out on top: the rest plays for them (no spot for him in this test, so it waits him out).
			friend.snapTo(helper.absoluteVec(new Vec3(EndingDSupport.AX + 0.5, 27, EndingDSupport.AZ + 0.5)), 0, 0);
			at = play(server, data, cfg, friendOnly, at, 60000, LastMinute.Phase.DONE);
			helper.assertFalse(LastMinute.running(), "the last minute did not finish: " + LastMinute.phase());
			helper.assertTrue(data.step() == Step.AFTERWARD, "the afterward did not begin: " + data.step());
			helper.assertTrue(ports.music.equals(List.of(friend.getUUID())) && ports.musicOn == 1 && ports.fogGone >= 1,
					"the music did not play for the one there (and only them): " + ports.music.size());
			helper.assertFalse(real.hasFlag(LastMinute.FLAG), "ending:last_minute outlived the giving");
		} finally {
			LastMinute.use(LastMinute.Ports.LIVE);
			LastMinute.reset();
			real.setFlag(LastMinute.FLAG, flagBefore);
			if (TraceLedger.get(server).entries().contains(taken)) {
				Services.traces().forced().restoreBlock(level, taken, lost);
			}
		}
		helper.succeed();
	}

	/**
	 * MP-7: the climb is theirs who climb it. A friend at home far off, under the open sky, is not coming out of the
	 * pyramid: the minute waits for the one at bedrock, and plays once they come out at the pyramid.
	 */
	@GameTest(structure = EndingDSupport.SHAFT, maxTicks = 40)
	public void theClimbWaitsForTheOneDownThereNotAFriendUnderTheSky(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		EndingDSupport.shaftWorld(helper);
		EndingDState data = new EndingDState();
		EndingDConfig cfg = new EndingDConfig();
		EndingDSupport.buildStair(helper, data);
		Recording ports = new Recording();
		ServerPlayer placer = EndingDSupport.player(helper, new Vec3(6.5, 2, 6.5), 0, 0);
		ServerPlayer friend = EndingDSupport.player(helper, new Vec3(EndingDSupport.AX + 0.5, 40, EndingDSupport.AZ + 0.5 + 200), 0, 0);
		LastMinute.Present both = LastMinute.Present.of(List.of(placer, friend));
		HerobrineState real = HerobrineState.get(server);
		boolean flagBefore = real.hasFlag(LastMinute.FLAG);
		try {
			LastMinute.use(ports);
			LastMinute.start(server, data, false, placer.blockPosition());
			long at = play(server, data, cfg, both, server.getTickCount(), 4000, LastMinute.Phase.CLIMB);
			helper.assertTrue(LastMinute.phase() == LastMinute.Phase.CLIMB, "the last minute did not reach the climb: " + LastMinute.phase());
			at = play(server, data, cfg, both, at, 40, LastMinute.Phase.FIGURE);
			helper.assertTrue(LastMinute.phase() == LastMinute.Phase.CLIMB && ports.fogGone == 0,
					"the climb ended for a friend under the sky far from the pyramid: " + LastMinute.phase());
			helper.assertFalse(ports.spotsFor.contains(friend.getUUID()) || LastMinute.spawnedHim(), "his spot was picked for the friend at home");
			helper.assertTrue(ports.spotsFor.contains(placer.getUUID()), "his spot was not looked for the one climbing");

			// The placer comes out at the pyramid: the rest plays, for them.
			placer.snapTo(helper.absoluteVec(new Vec3(EndingDSupport.AX + 0.5, 27, EndingDSupport.AZ + 0.5)), 0, 0);
			play(server, data, cfg, both, at, 2, LastMinute.Phase.FIGURE);
			helper.assertTrue(LastMinute.phase() == LastMinute.Phase.FIGURE && ports.fogGone == 1, "coming out at the pyramid did not end the climb: "
					+ LastMinute.phase());
		} finally {
			LastMinute.use(LastMinute.Ports.LIVE);
			LastMinute.reset();
			real.setFlag(LastMinute.FLAG, flagBefore);
		}
		helper.succeed();
	}
}
