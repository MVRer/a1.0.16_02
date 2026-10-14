package com.forzacode.a1016_02.atmosphere.card;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.forzacode.a1016_02.atmosphere.AtmosphereData;
import com.forzacode.a1016_02.atmosphere.Gates;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.Director;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.GameClock;
import com.forzacode.a1016_02.core.ModConfig;
import com.forzacode.a1016_02.core.SoundCues;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Where the cards' sounds go (D-060): a sound he makes at a position is heard by everyone in hearing range, not only
 * by the player the card fired for. Each test gives its own card instance a recording {@link SoundOut} that hears
 * with the rule {@code SoundCues.playAt} uses ({@link SoundCues#hearers}: same level, within {@code max(volume, 1) * 16}
 * blocks) over the test's own players. Run in the End, where no other test keeps chests or stone, so the cards can only find this test's.
 * {@code atmosphere.AtmosphereGameTests} extends this (through {@code SharedMomentGameTests}).
 */
public class CardSoundGameTests {
	private static final String YARD = "a1016_02:accident/yard";

	/** One sound a card played: what, where, and who heard it. */
	record Heard(Holder<SoundEvent> sound, Vec3 pos, float volume, List<UUID> hearers) {
	}

	/** Records every sound and hears it among {@code candidates} by core's own range rule ({@link SoundCues#hearers}). */
	static SoundOut recorder(List<Heard> log, List<ServerPlayer> candidates) {
		return (level, sound, source, pos, volume, pitch) -> {
			List<ServerPlayer> hearers = SoundCues.hearers(level, candidates, pos, volume);
			log.add(new Heard(sound, pos, volume, hearers.stream().map(ServerPlayer::getUUID).toList()));
			return hearers;
		};
	}

	static ServerPlayer at(GameTestHelper helper, Vec3 relative) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		Vec3 pos = helper.absoluteVec(relative);
		player.snapTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		return player;
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 24; x++) {
			for (int z = 0; z < 24; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static List<UUID> uuids(ServerPlayer... players) {
		List<UUID> list = new ArrayList<>();
		for (ServerPlayer player : players) {
			list.add(player.getUUID());
		}
		return list;
	}

	/** Chest opens, fired for A: A and B, both within hearing range of the chest, hear it open and close; C far off does not. */
	@GameTest(dimension = "minecraft:the_end", structure = YARD, maxTicks = 100)
	public void aChestSoundIsHeardByEveryoneInRange(GameTestHelper helper) {
		floor(helper);
		helper.setBlock(12, 1, 12, Blocks.CHEST);
		ServerLevel level = helper.getLevel();
		ServerPlayer a = at(helper, new Vec3(15.5, 1.0, 12.5));
		ServerPlayer b = at(helper, new Vec3(8.5, 1.0, 12.5));
		ServerPlayer c = at(helper, new Vec3(12.5, 1.0, 72.5));
		List<Heard> log = new CopyOnWriteArrayList<>();
		ChestOpensCard card = new ChestOpensCard();
		card.sounds = recorder(log, List.of(a, b, c));
		helper.assertTrue(card.fire(new FireContext(a, level, true, true, RandomSource.create(4))) == FireResult.FIRED, "the card did not fire");
		Vec3 chest = Vec3.atCenterOf(helper.absolutePos(new BlockPos(12, 1, 12)));
		helper.assertTrue(log.size() == 1 && log.getFirst().sound().value() == SoundEvents.CHEST_OPEN && log.getFirst().pos().distanceTo(chest) < 1.0E-6,
				"no open sound at the chest: " + log);
		helper.assertTrue(log.getFirst().hearers().equals(uuids(a, b)), "the open sound was heard by " + log.getFirst().hearers() + ", not A and B");
		helper.succeedWhen(() -> {
			helper.assertTrue(log.size() == 2, "no close sound yet");
			Heard close = log.get(1);
			helper.assertTrue(close.sound().value() == SoundEvents.CHEST_CLOSE && close.pos().distanceTo(chest) < 1.0E-6, "the close sound: " + close);
			helper.assertTrue(close.hearers().equals(uuids(a, b)), "the close sound was heard by " + close.hearers());
		});
	}

	/**
	 * Mining in the dark, fired for A: one block-break sound in the stone, heard by A and by B beside A, and it is
	 * tonight's mining for both of them (never one each); C, far off, heard nothing and still has theirs. The late
	 * footstep at A's feet reaches B too.
	 */
	@GameTest(dimension = "minecraft:the_end", structure = YARD, maxTicks = 40)
	public void miningAndTheLateStepAreHeardByBoth(GameTestHelper helper) {
		floor(helper);
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		ServerPlayer a = at(helper, new Vec3(12.5, 1.0, 12.5));
		ServerPlayer b = at(helper, new Vec3(13.5, 1.0, 12.5));
		ServerPlayer c = at(helper, new Vec3(12.5, 1.0, 92.5));
		List<Heard> log = new CopyOnWriteArrayList<>();
		MiningInTheDarkCard card = new MiningInTheDarkCard();
		card.sounds = recorder(log, List.of(a, b, c));
		FireResult result = FireResult.NO_SPOT;
		for (long seed = 1; seed <= 40 && result != FireResult.FIRED; seed++) {
			result = card.fire(new FireContext(a, level, false, true, RandomSource.create(seed)));
		}
		helper.assertTrue(result == FireResult.FIRED && log.size() == 1, "no mining sound in 40 tries: " + result + " " + log);
		Heard mining = log.getFirst();
		helper.assertTrue(mining.hearers().equals(uuids(a, b)), "the mining sound was heard by " + mining.hearers() + ", not A and B");
		long night = GameClock.day(server);
		AtmosphereData data = AtmosphereData.get(server);
		helper.assertTrue(data.miningOn(a.getUUID(), night) == 1 && data.miningOn(b.getUUID(), night) == 1, "tonight's mining was not A's and B's");
		helper.assertTrue(data.miningOn(c.getUUID(), night) == 0, "C, who heard nothing, lost their mining tonight");

		List<ServerPlayer> heardStep = FootstepLateCard.playStep(a, recorder(log, List.of(a, b, c)));
		helper.assertTrue(heardStep.size() == 2 && log.size() == 2 && log.get(1).pos().distanceTo(a.position()) < 1.0E-6,
				"the late step was not at A's feet for A and B: " + log.get(log.size() - 1));
		helper.succeed();
	}

	/**
	 * Mining in the dark waits out the gap after a sound card on the player's own pacing (D-059): a sound card that just
	 * fired for B, far away, holds back B's mining but never A's, though it is the world's newest sound. A director
	 * without a per-player memory (core's stub) answers for the world.
	 */
	@GameTest
	public void theMiningSoundGapIsEachPlayersOwn(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer a = at(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer b = at(helper, new Vec3(1.5, 1.0, 1.5));
		Director soundForB = new Director() {
			@Override
			public void tick(MinecraftServer forServer) {
			}

			@Override
			public FireResult fire(MinecraftServer forServer, ServerPlayer player, String cardId, boolean fake) {
				return FireResult.SKIPPED;
			}

			@Override
			public List<String> timewarp(MinecraftServer forServer, int days) {
				return List.of();
			}

			@Override
			public long ticksSinceTag(MinecraftServer forServer, CardTag tag) {
				return tag == CardTag.SOUND ? 0 : Long.MAX_VALUE;
			}

			@Override
			public long ticksSinceTag(MinecraftServer forServer, UUID player, CardTag tag) {
				return tag == CardTag.SOUND && player.equals(b.getUUID()) ? 0 : Long.MAX_VALUE;
			}

			@Override
			public boolean inQuiet(MinecraftServer forServer) {
				return false;
			}

			@Override
			public List<String> debugLines(MinecraftServer forServer) {
				return List.of();
			}
		};
		long gap = ModConfig.pacing().miningDarkSoundGapTicks();
		long sinceA = MiningInTheDarkCard.sinceSound(soundForB, server, a);
		long sinceB = MiningInTheDarkCard.sinceSound(soundForB, server, b);
		helper.assertTrue(sinceA == Long.MAX_VALUE && Gates.miningInTheDark(true, true, 0, 0, sinceA, gap, 0, 1),
				"B's sound far away held back A's mining (" + sinceA + " ticks)");
		helper.assertTrue(sinceB == 0 && !Gates.miningInTheDark(true, true, 0, 0, sinceB, gap, 0, 1), "B's own gap was open right after B's sound");
		Director stub = new Director.Stub();
		helper.assertTrue(MiningInTheDarkCard.sinceSound(stub, server, a) == stub.ticksSinceTag(server, CardTag.SOUND), "the stub's own answer is not the world's");
		helper.succeed();
	}

	/** The footstep's once-per-session memory is per player: A stopping to take part ends A's session, never B's. */
	@GameTest
	public void footstepSessionsArePerPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer a = at(helper, new Vec3(1.5, 1.0, 1.5));
		ServerPlayer b = at(helper, new Vec3(3.5, 1.0, 1.5));
		FootstepLateCard card = new FootstepLateCard();
		try {
			helper.assertTrue(card.fire(new FireContext(a, level, false, true, RandomSource.create(1))) == FireResult.FIRED
					&& card.fire(new FireContext(b, level, false, true, RandomSource.create(2))) == FireResult.FIRED, "the footstep did not arm");
			helper.assertTrue(FootstepLateCard.armed(a) && FootstepLateCard.armed(b), "not armed for both");
			FootstepLateCard.onLeave(a.getUUID());
			helper.assertTrue(!FootstepLateCard.armed(a) && FootstepLateCard.armed(b), "A leaving ended B's session too, or not A's");
		} finally {
			FootstepLateCard.onLeave(a.getUUID());
			FootstepLateCard.onLeave(b.getUUID());
		}
		helper.succeed();
	}
}
