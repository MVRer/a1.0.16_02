package com.forzacode.a1016_02.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Two-player tests for the multiplayer core (MP-0, D-058 to D-064). Game tests share one server and nobody joins
 * it, so each test brings its own {@link Roster}: its own online list, state and participation events, through
 * {@link PlayerWatch}'s package-private source. Nothing here touches the live server's players or the world's
 * state, except reads that are safe with nobody online. {@link CoreContractTests} extends this.
 */
public class MultiplayerCoreTests {
	/** A player whose game mode a test can switch. It never joins the server; a {@link Roster} lists it. */
	static final class TestPlayer extends ServerPlayer {
		GameType mode;

		TestPlayer(ServerLevel level, UUID uuid, String name, GameType mode) {
			super(level.getServer(), level, new GameProfile(uuid, name), ClientInformation.createDefault());
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

	/** A test's own online list, state and participation events ({@code "A+"}, {@code "A-"}). */
	static final class Roster implements PlayerWatch.Source {
		final List<ServerPlayer> online = new ArrayList<>();
		final HerobrineState state = new HerobrineState();
		final List<String> events = new ArrayList<>();
		final PlayerWatch watch = new PlayerWatch(this);

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
			String name = state.player(player).map(HerobrineState.Subject::name).orElse("?");
			// On a start the player must already be findable; mark it if not.
			boolean found = !participating || watch.participant(server, player).isPresent();
			events.add(name + (participating ? "+" : "-") + (found ? "" : "(not found)"));
		}

		/** Joins like the server does: Fabric's JOIN runs before the player list has the player. */
		void join(ServerPlayer player) {
			watch.onJoin(player);
			online.add(player);
		}

		/** Leaves like the server does: Fabric's DISCONNECT runs while the player is still listed. */
		void leave(ServerPlayer player) {
			watch.onLeave(player);
			online.removeIf(p -> p.getUUID().equals(player.getUUID()));
		}

		/** A respawn: the server lists a new object for the same player. */
		TestPlayer respawn(TestPlayer old, GameType mode) {
			TestPlayer fresh = new TestPlayer(old.level(), old.getUUID(), old.getName().getString(), mode);
			fresh.snapTo(old.position(), 0.0F, 0.0F);
			online.set(online.indexOf(old), fresh);
			return fresh;
		}

		void tick(MinecraftServer server, int ticks) {
			for (int n = 0; n < ticks; n++) {
				watch.tickParticipation(server);
			}
		}

		long ticks(ServerPlayer player) {
			return state.playTicks(player.getUUID());
		}
	}

	private static TestPlayer player(GameTestHelper helper, String name, GameType mode, Vec3 relative) {
		return player(helper.getLevel(), name, mode, helper.absoluteVec(relative));
	}

	private static TestPlayer player(ServerLevel level, String name, GameType mode, Vec3 absolute) {
		TestPlayer player = new TestPlayer(level, UUID.randomUUID(), name, mode);
		player.snapTo(absolute, 0.0F, 0.0F);
		return player;
	}

	private static List<UUID> uuids(List<? extends ServerPlayer> players) {
		return players.stream().map(ServerPlayer::getUUID).toList();
	}

	private static List<String> names(List<? extends ServerPlayer> players) {
		return players.stream().map(p -> p.getName().getString()).toList();
	}

	private static List<String> known(HerobrineState state) {
		return state.players().stream().map(HerobrineState.Subject::name).toList();
	}

	// --- participants ---

	@GameTest
	@SuppressWarnings("removal")
	public void participantsAreOnlineNonSpectatorsInJoinOrder(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Roster r = new Roster();
		PlayerWatch w = r.watch;
		TestPlayer a = player(helper, "A", GameType.SURVIVAL, new Vec3(1.5, 1, 1.5));
		TestPlayer spectator = player(helper, "S", GameType.SPECTATOR, new Vec3(2.5, 1, 1.5));
		TestPlayer b = player(helper, "B", GameType.CREATIVE, new Vec3(3.5, 1, 1.5));
		TestPlayer dead = player(helper, "D", GameType.SURVIVAL, new Vec3(4.5, 1, 1.5));
		r.join(a);
		r.join(spectator);
		r.join(b);
		r.join(dead);
		r.tick(server, 1);
		dead.setHealth(0.0F);
		Collections.reverse(r.online); // the server's own order is not the join order

		helper.assertTrue(uuids(w.participants(server)).equals(uuids(List.of(a, b, dead))), "participants " + names(w.participants(server)));
		helper.assertTrue(w.isParticipant(a) && w.isParticipant(b) && w.isParticipant(dead) && !w.isParticipant(spectator),
				"a dead player still takes part, a spectator does not");
		helper.assertTrue(w.isTargetable(a) && w.isTargetable(b) && !w.isTargetable(dead) && !w.isTargetable(spectator), "targetable");
		// Not online: a player who never joined, and an old object of a participant (as kept across a respawn).
		ServerPlayer stranger = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		TestPlayer staleA = new TestPlayer(helper.getLevel(), a.getUUID(), "A", GameType.SURVIVAL);
		helper.assertFalse(w.isParticipant(stranger) || w.isParticipant(staleA), "a player object that is not online counts");
		helper.assertTrue(w.participant(server, a.getUUID()).orElse(null) == a && w.participant(server, spectator.getUUID()).isEmpty()
				&& w.participant(server, stranger.getUUID()).isEmpty(), "participant lookup");
		helper.assertTrue(known(r.state).equals(List.of("A", "B", "D")), "known players " + known(r.state));

		// The spectator starts playing: last in the order, and the others keep theirs whatever the server's order.
		spectator.mode = GameType.SURVIVAL;
		r.tick(server, 1);
		Collections.shuffle(r.online, new Random(7));
		helper.assertTrue(uuids(w.participants(server)).equals(uuids(List.of(a, b, dead, spectator))), "order " + names(w.participants(server)));
		helper.assertTrue(known(r.state).equals(List.of("A", "B", "D", "S")), "known players " + known(r.state));

		// The deprecated bridges: the first participant in join order; isSubject is isParticipant.
		helper.assertTrue(w.subject(server).orElse(null) == a && w.isSubject(b) && !w.isSubject(stranger), "deprecated subject");
		r.leave(a);
		helper.assertTrue(w.subject(server).orElse(null) == b, "the subject after A left: " + w.subject(server));
		// The live server: nobody joins game tests, so nobody takes part there.
		helper.assertFalse(Services.watch().isParticipant(stranger), "a mock player takes part on the live server");
		helper.succeed();
	}

	@GameTest
	public void participationFiresOncePerChange(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Roster r = new Roster();
		TestPlayer spectator = player(helper, "S", GameType.SPECTATOR, new Vec3(1.5, 1, 1.5));
		TestPlayer a = player(helper, "A", GameType.SURVIVAL, new Vec3(2.5, 1, 1.5));
		TestPlayer b = player(helper, "B", GameType.SURVIVAL, new Vec3(3.5, 1, 1.5));
		TestPlayer c = player(helper, "C", GameType.SURVIVAL, new Vec3(4.5, 1, 1.5));

		// A spectator who joins and leaves: nothing, and they are never a known player.
		r.join(spectator);
		r.tick(server, 2);
		r.leave(spectator);
		r.tick(server, 1);
		helper.assertTrue(r.events.isEmpty() && r.state.players().isEmpty(), "spectator: " + r.events + " " + known(r.state));

		// A joins: known during JOIN, one start at the end of the tick (findable then), nothing more.
		r.join(a);
		helper.assertTrue(r.events.isEmpty() && r.state.player(a.getUUID()).isPresent(), "during JOIN: " + r.events);
		r.tick(server, 3);
		helper.assertTrue(r.events.equals(List.of("A+")), "join: " + r.events);

		// Into spectator mode and out again: one stop, one start.
		a.mode = GameType.SPECTATOR;
		r.tick(server, 2);
		a.mode = GameType.SURVIVAL;
		r.tick(server, 2);
		helper.assertTrue(r.events.equals(List.of("A+", "A-", "A+")), "spectator mode: " + r.events);

		// Death and respawn change nothing: a dead player still takes part, the new object is the same player.
		a.setHealth(0.0F);
		r.tick(server, 2);
		TestPlayer respawned = r.respawn(a, GameType.SURVIVAL);
		r.tick(server, 2);
		helper.assertTrue(r.events.equals(List.of("A+", "A-", "A+")), "death and respawn: " + r.events);

		// B joins and leaves within one tick: no start, so no stop.
		r.join(b);
		r.leave(b);
		r.tick(server, 1);
		helper.assertTrue(r.events.equals(List.of("A+", "A-", "A+")), "join and leave in one tick: " + r.events);

		// C joins; A disconnects: one stop from DISCONNECT, none later. C goes on.
		r.join(c);
		r.tick(server, 1);
		r.leave(respawned);
		r.tick(server, 2);
		helper.assertTrue(r.events.equals(List.of("A+", "A-", "A+", "C+", "A-")), "A leaves: " + r.events);

		// A hardcore death: C respawns as a spectator and stops; leaving afterwards fires nothing.
		c.setHealth(0.0F);
		r.tick(server, 1);
		TestPlayer ghost = r.respawn(c, GameType.SPECTATOR);
		r.tick(server, 1);
		r.leave(ghost);
		r.tick(server, 1);
		helper.assertTrue(r.events.equals(List.of("A+", "A-", "A+", "C+", "A-", "C-")), "hardcore death: " + r.events);

		// Someone who joins as a spectator and starts playing later: known and started only then.
		TestPlayer watcher = player(helper, "W", GameType.SPECTATOR, new Vec3(5.5, 1, 1.5));
		r.join(watcher);
		r.tick(server, 2);
		helper.assertTrue(r.state.player(watcher.getUUID()).isEmpty() && r.events.size() == 6, "a joining spectator: " + r.events);
		watcher.mode = GameType.ADVENTURE;
		r.tick(server, 2);
		helper.assertTrue(r.events.getLast().equals("W+") && r.events.size() == 7 && r.state.player(watcher.getUUID()).isPresent(),
				"the spectator who starts playing: " + r.events);
		helper.succeed();
	}

	// --- personal records (D-061) ---

	@GameTest
	@SuppressWarnings("removal")
	public void firstBlocksAndBaseArePerPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		Roster r = new Roster();
		PlayerWatch w = r.watch;
		TestPlayer a = player(helper, "A", GameType.SURVIVAL, new Vec3(1.5, 1, 1.5));
		TestPlayer b = player(helper, "B", GameType.SURVIVAL, new Vec3(5.5, 1, 1.5));
		r.join(a);
		r.join(b);
		r.tick(server, 1);
		BlockPos bPlanks = helper.absolutePos(new BlockPos(5, 1, 3));
		BlockPos aChest = helper.absolutePos(new BlockPos(1, 1, 3));
		BlockPos bTable = helper.absolutePos(new BlockPos(6, 1, 3));
		BlockPos aPlanks = helper.absolutePos(new BlockPos(2, 1, 3));
		w.onPlaced(b, level, bPlanks, Blocks.OAK_PLANKS.defaultBlockState());
		w.onPlaced(a, level, aChest, Blocks.CHEST.defaultBlockState());
		w.onPlaced(b, level, bTable, Blocks.CRAFTING_TABLE.defaultBlockState());
		w.onPlaced(a, level, aPlanks, Blocks.OAK_PLANKS.defaultBlockState());

		HerobrineState.FirstBlocks firstA = r.state.firstBlocks(a.getUUID());
		HerobrineState.FirstBlocks firstB = r.state.firstBlocks(b.getUUID());
		helper.assertTrue(firstA.block() != null && firstA.block().pos().pos().equals(aChest) && firstA.chest() != null
				&& firstA.chest().pos().pos().equals(aChest) && firstA.craftingTable() == null, "A's first blocks " + firstA);
		helper.assertTrue(firstB.block() != null && firstB.block().pos().pos().equals(bPlanks) && firstB.craftingTable() != null
				&& firstB.craftingTable().pos().pos().equals(bTable) && firstB.chest() == null, "B's first blocks " + firstB);
		helper.assertTrue(w.base(a).equals(Optional.of(GlobalPos.of(level.dimension(), aChest)))
				&& w.base(b).equals(Optional.of(GlobalPos.of(level.dimension(), bPlanks))), "bases " + w.base(a) + " " + w.base(b));
		helper.assertTrue(r.state.firstBlocks().equals(firstA), "the deprecated firstBlocks() is not the first known player's");

		// Someone who first takes part later starts with nothing, never anyone else's.
		TestPlayer late = player(helper, "L", GameType.SURVIVAL, new Vec3(3.5, 1, 5.5));
		r.join(late);
		helper.assertTrue(r.state.firstBlocks(late.getUUID()).equals(HerobrineState.FirstBlocks.NONE) && w.base(late).isEmpty()
				&& r.ticks(late) == 0 && r.state.tension(late.getUUID()) == 0.0, "the late player inherited something");

		// A spectator or a player not online records no first block.
		TestPlayer spectator = player(helper, "S", GameType.SPECTATOR, new Vec3(4.5, 1, 5.5));
		r.join(spectator);
		ServerPlayer stranger = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		w.onPlaced(spectator, level, helper.absolutePos(new BlockPos(4, 1, 6)), Blocks.DIRT.defaultBlockState());
		w.onPlaced(stranger, level, helper.absolutePos(new BlockPos(5, 1, 6)), Blocks.DIRT.defaultBlockState());
		helper.assertTrue(r.state.firstBlocks(spectator.getUUID()).equals(HerobrineState.FirstBlocks.NONE)
				&& r.state.firstBlocks(stranger.getUUID()).equals(HerobrineState.FirstBlocks.NONE) && known(r.state).equals(List.of("A", "B", "L")),
				"a non-participant's placement was recorded");
		helper.succeed();
	}

	@GameTest
	public void clocksRunPerPlayerAndForTheWorld(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Roster r = new Roster();
		TestPlayer a = player(helper, "A", GameType.SURVIVAL, new Vec3(1.5, 1, 1.5));
		TestPlayer b = player(helper, "B", GameType.SURVIVAL, new Vec3(2.5, 1, 1.5));
		TestPlayer c = player(helper, "C", GameType.SURVIVAL, new Vec3(3.5, 1, 1.5));
		r.join(a);
		r.join(b);
		r.tick(server, 10);
		helper.assertTrue(r.state.playTicks() == 10 && r.ticks(a) == 10 && r.ticks(b) == 10, "both online");

		// B leaves: B's clock stops, A's and the world's run on.
		r.leave(b);
		r.tick(server, 5);
		helper.assertTrue(r.state.playTicks() == 15 && r.ticks(a) == 15 && r.ticks(b) == 10, "B offline");

		// Nobody takes part (A is a spectator): every clock stops.
		a.mode = GameType.SPECTATOR;
		r.tick(server, 3);
		helper.assertTrue(r.state.playTicks() == 15 && r.ticks(a) == 15, "nobody takes part");

		// C first takes part now and starts from zero; a dead A still counts.
		r.join(c);
		a.mode = GameType.SURVIVAL;
		a.setHealth(0.0F);
		r.tick(server, 2);
		helper.assertTrue(r.state.playTicks() == 17 && r.ticks(a) == 17 && r.ticks(b) == 10 && r.ticks(c) == 2,
				"world " + r.state.playTicks() + " a " + r.ticks(a) + " b " + r.ticks(b) + " c " + r.ticks(c));

		// A timewarp adds to the world clock and to every known player's, online or not.
		GameClock.warp(r.state, 2);
		long day2 = 2 * GameClock.TICKS_PER_DAY;
		helper.assertTrue(r.state.playTicks() == 17 + day2 && r.ticks(a) == 17 + day2 && r.ticks(b) == 10 + day2 && r.ticks(c) == 2 + day2
				&& r.state.warpDays() == 2, "warp");
		// Someone unknown has no clock; the live lookup agrees for a player who never took part here.
		helper.assertTrue(r.state.playTicks(UUID.randomUUID()) == 0 && GameClock.playTicks(server, UUID.randomUUID()) == 0, "unknown clock");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void tensionIsPerPlayer(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = new HerobrineState();
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		state.recordPlayer(a, "A");
		state.recordPlayer(b, "B");
		state.addTension(a, 30.0);
		helper.assertTrue(state.tension(a) == 30.0 && state.tension(b) == 0.0, "raising A's tension moved B's");
		state.addTension(b, 55.0);
		state.addTension(a, -50.0);
		helper.assertTrue(state.tension(a) == 0.0 && state.tension(b) == 55.0 && state.tension() == 55.0, "lowering, clamps and the display max");
		state.addTension(a, 500.0);
		helper.assertTrue(state.tension(a) == 100.0 && state.tension(b) == 55.0, "clamped at 100");
		UUID stranger = UUID.randomUUID();
		state.addTension(stranger, 10.0);
		helper.assertTrue(state.tension(stranger) == 0.0 && state.player(stranger).isEmpty(), "an unknown player got tension");
		// The live API: someone who never took part in this world has no tension to raise.
		Attention.raiseTension(server, stranger, 20.0, "test");
		helper.assertTrue(HerobrineState.get(server).tension(stranger) == 0.0 && HerobrineState.get(server).player(stranger).isEmpty(),
				"the world state recorded a stranger's tension");
		helper.succeed();
	}

	@GameTest
	public void ownersAreChosenOnceAndKept(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Roster r = new Roster();
		PlayerWatch w = r.watch;
		helper.assertTrue(w.ownerFor(server, "test:house").isEmpty() && w.owner(server, "test:house").isEmpty() && r.state.owners().isEmpty(),
				"chose an owner with nobody online");

		TestPlayer a = player(helper, "A", GameType.SURVIVAL, new Vec3(1.5, 1, 1.5));
		TestPlayer b = player(helper, "B", GameType.SURVIVAL, new Vec3(2.5, 1, 1.5));
		r.join(a);
		r.join(b);
		r.tick(server, 1);
		r.state.addPlayTicks(b.getUUID(), 300); // B has played longer
		helper.assertTrue(w.ownerFor(server, "test:house").equals(Optional.of(b.getUUID())) && w.owner(server, "test:house").equals(Optional.of(b.getUUID())),
				"the most play ticks did not win");

		// B goes offline and A plays far longer: the stored choice holds.
		r.leave(b);
		r.state.addPlayTicks(a.getUUID(), 10_000);
		r.tick(server, 5);
		helper.assertTrue(w.ownerFor(server, "test:house").equals(Optional.of(b.getUUID())), "the owner flipped");
		// A new key is chosen among who is online now.
		helper.assertTrue(w.ownerFor(server, "test:cairn").equals(Optional.of(a.getUUID())), "the new key's owner");
		// Debug reset: the next call chooses again.
		r.state.clearOwner("test:house");
		helper.assertTrue(w.owner(server, "test:house").isEmpty() && w.ownerFor(server, "test:house").equals(Optional.of(a.getUUID())), "clearOwner");

		// A tie goes to the earliest first join, whatever the server's order.
		Roster tie = new Roster();
		TestPlayer first = player(helper, "F", GameType.SURVIVAL, new Vec3(3.5, 1, 1.5));
		TestPlayer second = player(helper, "G", GameType.SURVIVAL, new Vec3(4.5, 1, 1.5));
		tie.join(first);
		tie.join(second);
		tie.tick(server, 4);
		Collections.reverse(tie.online);
		helper.assertTrue(tie.watch.ownerFor(server, "test:tie").equals(Optional.of(first.getUUID())), "the tie did not go to the first join");
		helper.succeed();
	}

	/**
	 * {@code ownerFor} with a predicate (the story's first blocks: only someone who placed a block): it chooses only among
	 * the eligible participants, by the same rule, stores the choice and never flips it, even once the predicate would
	 * say no; with nobody eligible it chooses nobody and stores nothing.
	 */
	@GameTest
	public void ownersCanBeChosenAmongTheEligibleOnly(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Roster r = new Roster();
		PlayerWatch w = r.watch;
		TestPlayer a = player(helper, "A", GameType.SURVIVAL, new Vec3(1.5, 1, 1.5));
		TestPlayer b = player(helper, "B", GameType.SURVIVAL, new Vec3(2.5, 1, 1.5));
		r.join(a);
		r.join(b);
		r.tick(server, 1);
		r.state.addPlayTicks(a.getUUID(), 5_000); // A has played far longer, but has placed nothing
		Set<UUID> placed = new HashSet<>();
		helper.assertTrue(w.ownerFor(server, "test:first", placed::contains).isEmpty() && w.owner(server, "test:first").isEmpty(),
				"an owner was chosen with nobody eligible");
		placed.add(b.getUUID());
		helper.assertTrue(w.ownerFor(server, "test:first", placed::contains).equals(Optional.of(b.getUUID()))
				&& w.owner(server, "test:first").equals(Optional.of(b.getUUID())), "the only eligible player was not chosen and stored");
		// A becomes eligible and B no longer is: the stored choice holds, with or without the predicate.
		placed.add(a.getUUID());
		placed.remove(b.getUUID());
		helper.assertTrue(w.ownerFor(server, "test:first", placed::contains).equals(Optional.of(b.getUUID()))
				&& w.ownerFor(server, "test:first").equals(Optional.of(b.getUUID())), "the owner flipped");
		// Among several eligible ones, the usual rule: the most play ticks.
		placed.add(b.getUUID());
		helper.assertTrue(w.ownerFor(server, "test:other", placed::contains).equals(Optional.of(a.getUUID())), "the rule among the eligible");
		helper.succeed();
	}

	/**
	 * The profile (D-066 M0, M2): F08 is fixed now, and the clue fragments past F30 are always enabled, old saves
	 * included: a v1.0 profile without F08 loads with it, and never lists the clues yet enables them.
	 */
	@GameTest
	public void oldProfilesGainF08AndTheClues(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		WorldProfile rolled = WorldProfile.roll(7L, 11L);
		helper.assertTrue(rolled.fragments().contains("F08") && WorldProfile.FIXED_FRAGMENTS.contains("F08"), "F08 is not fixed");
		CompoundTag tag = (CompoundTag) WorldProfile.CODEC.encodeStart(ops, rolled).getOrThrow();
		ListTag old = new ListTag();
		for (String id : rolled.fragments()) {
			if (!id.equals("F08")) {
				old.add(StringTag.valueOf(id));
			}
		}
		tag.put("fragments", old);
		WorldProfile loaded = WorldProfile.CODEC.parse(ops, tag).getOrThrow();
		helper.assertTrue(loaded.fragments().contains("F08") && loaded.isEnabled("F08") && loaded.equals(rolled), "the old save did not gain F08");
		for (String id : WorldProfile.CLUE_FRAGMENTS) {
			helper.assertTrue(loaded.isEnabled(id) && !loaded.fragments().contains(id), id + " is not always enabled, or is listed as rolled");
		}
		helper.assertTrue(WorldProfile.allFragmentIds().size() == 30 + WorldProfile.CLUE_FRAGMENTS.size()
				&& WorldProfile.storyFragmentIds().size() == 30, "the id lists");
		helper.succeed();
	}

	// --- saves ---

	@GameTest
	@SuppressWarnings("removal")
	public void v1SaveLoadsAsOneKnownPlayer(GameTestHelper helper) {
		RegistryOps<Tag> ops = helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
		UUID alex = UUID.fromString("00000000-0000-0000-0000-0000000a1016");
		PlacedBlock block = new PlacedBlock(GlobalPos.of(Level.OVERWORLD, new BlockPos(10, 64, 10)), Blocks.OAK_PLANKS.defaultBlockState());
		PlacedBlock chest = new PlacedBlock(GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 64, 9)), Blocks.CHEST.defaultBlockState());
		HerobrineState.FirstBlocks firsts = new HerobrineState.FirstBlocks(block, null, chest);

		// state.dat as v1.0 wrote it: one subject, top-level firstBlocks and tension, the subject's play ticks in clock.
		CompoundTag v1 = new CompoundTag();
		v1.putString("stage", Stage.TRACES.name());
		v1.putDouble("attention", 12.0);
		v1.putDouble("tension", 37.5);
		v1.putLong("salt", 99L);
		v1.put("profile", WorldProfile.CODEC.encodeStart(ops, WorldProfile.roll(1L, 99L)).getOrThrow());
		v1.put("subject", HerobrineState.Subject.CODEC.encodeStart(ops, new HerobrineState.Subject(alex, "Alex")).getOrThrow());
		CompoundTag clock = new CompoundTag();
		clock.putLong("playTicks", 987_654L);
		clock.putLong("warpDays", 2L);
		v1.put("clock", clock);
		v1.put("firstBlocks", HerobrineState.FirstBlocks.CODEC.encodeStart(ops, firsts).getOrThrow());
		CompoundTag death = new CompoundTag();
		death.putString("cause", "fell");
		death.put("pos", GlobalPos.CODEC.encodeStart(ops, GlobalPos.of(Level.OVERWORLD, new BlockPos(3, 40, 3))).getOrThrow());
		death.putLong("day", 4L);
		ListTag deaths = new ListTag();
		deaths.add(death);
		v1.put("markedDeaths", deaths);

		HerobrineState loaded = HerobrineState.CODEC.parse(ops, v1).getOrThrow();
		helper.assertTrue(loaded.players().equals(List.of(new HerobrineState.Subject(alex, "Alex"))), "players " + loaded.players());
		helper.assertTrue(loaded.firstBlocks(alex).equals(firsts) && loaded.tension(alex) == 37.5, "first blocks / tension");
		helper.assertTrue(loaded.playTicks(alex) == 987_654L && loaded.playTicks() == 987_654L && loaded.warpDays() == 2, "clocks");
		helper.assertTrue(loaded.markedDeaths().size() == 1 && alex.equals(loaded.markedDeaths().getFirst().player())
				&& loaded.markedDeaths(alex).size() == 1, "the old death is not Alex's: " + loaded.markedDeaths());
		helper.assertTrue(loaded.stage() == Stage.TRACES && loaded.attention() == 12.0 && loaded.subject().map(HerobrineState.Subject::uuid)
				.equals(Optional.of(alex)), "the rest of the old save");

		// Saved again, it is in the new format, and it loads the same.
		CompoundTag saved = (CompoundTag) HerobrineState.CODEC.encodeStart(ops, loaded).getOrThrow();
		helper.assertTrue(saved.contains("players") && !saved.contains("subject") && !saved.contains("firstBlocks") && !saved.contains("tension"),
				"saved keys " + saved.keySet());
		HerobrineState again = HerobrineState.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(again.players().equals(loaded.players()) && again.firstBlocks(alex).equals(firsts) && again.tension(alex) == 37.5
				&& again.playTicks(alex) == 987_654L && again.markedDeaths().equals(loaded.markedDeaths()), "the new format did not round-trip");

		// A friend who first takes part now starts with empty records, after Alex.
		UUID bob = UUID.randomUUID();
		again.recordPlayer(bob, "Bob");
		helper.assertTrue(again.firstBlocks(bob).equals(HerobrineState.FirstBlocks.NONE) && again.playTicks(bob) == 0 && again.tension(bob) == 0.0
				&& known(again).equals(List.of("Alex", "Bob")), "the friend inherited Alex's records");

		// A v1.0 save from a world nobody ever joined has no known players.
		CompoundTag nobody = v1.copy();
		nobody.remove("subject");
		nobody.remove("firstBlocks");
		HerobrineState empty = HerobrineState.CODEC.parse(ops, nobody).getOrThrow();
		helper.assertTrue(empty.players().isEmpty() && empty.tension() == 0.0 && empty.markedDeaths().getFirst().player() == null, "nobody's save");
		helper.succeed();
	}

	@GameTest
	@SuppressWarnings("removal")
	public void markedDeathsRecordWhoseTheyWere(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		GlobalPos pos = GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 2, 3));
		HerobrineState state = new HerobrineState();
		state.addMarkedDeath(new MarkedDeath("fell", pos, 1));
		helper.assertTrue(state.markedDeaths().getFirst().player() == null, "a death was given to nobody's player");
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		state.recordPlayer(a, "A");
		state.recordPlayer(b, "B");
		state.addMarkedDeath(b, new MarkedDeath("lava", pos, 2));
		helper.assertTrue(state.markedDeaths(b).size() == 1 && state.markedDeaths(a).isEmpty(), "B's death");
		// The deprecated form: a death without a player is the first known player's; one with a player stays theirs.
		state.addMarkedDeath(new MarkedDeath("drowned", pos, 3));
		state.addMarkedDeath(new MarkedDeath("fell", pos, 4, b));
		helper.assertTrue(state.markedDeaths(a).size() == 1 && state.markedDeaths(b).size() == 2 && state.markedDeaths().size() == 4,
				"deaths " + state.markedDeaths());
		// The per-player count reads the world's state: a player who never died there has none.
		helper.assertTrue(Services.deaths().count(server, UUID.randomUUID()) == 0 && new DeathMarker.Stub().count(server, UUID.randomUUID()) == 0,
				"a stranger has marked deaths");
		helper.succeed();
	}

	// --- shared moments (D-060) ---

	@GameTest
	public void soundsReachEveryoneInHearingRange(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		Vec3 pos = helper.absoluteVec(new Vec3(1.5, 2.0, 1.5));
		TestPlayer near = player(level, "near", GameType.SURVIVAL, pos.add(10, 0, 0));
		TestPlayer far = player(level, "far", GameType.SURVIVAL, pos.add(17, 0, 0));
		TestPlayer spectator = player(level, "spectator", GameType.SPECTATOR, pos.add(0, 3, 8));
		TestPlayer elsewhere = player(nether, "elsewhere", GameType.SURVIVAL, pos.add(1, 0, 0));
		List<ServerPlayer> everyone = List.of(near, far, spectator, elsewhere);

		helper.assertTrue(uuids(SoundCues.hearers(level, everyone, pos, 1.0F)).equals(uuids(List.of(near, spectator))),
				"volume 1: " + names(SoundCues.hearers(level, everyone, pos, 1.0F)));
		helper.assertTrue(uuids(SoundCues.hearers(level, everyone, pos, 0.2F)).equals(uuids(List.of(near, spectator))), "a quiet sound still carries 16");
		helper.assertTrue(uuids(SoundCues.hearers(level, everyone, pos, 2.0F)).equals(uuids(List.of(near, far, spectator))), "volume 2 carries 32");
		helper.assertTrue(uuids(SoundCues.hearers(nether, everyone, pos, 1.0F)).equals(uuids(List.of(elsewhere))), "another dimension");
		// The live call: nobody is in a game test level, so it reaches nobody.
		helper.assertTrue(SoundCues.playAt(level, SoundEvents.STONE_BREAK, SoundSource.AMBIENT, pos, 1.0F, 1.0F).isEmpty(), "a sound reached a player");
		helper.succeed();
	}

	@GameTest
	public void sharingWithKeepsRadiusDimensionAndTargetFirst(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		int radius = ModConfig.pacing().sharedMomentRadius;
		Vec3 at = helper.absoluteVec(new Vec3(2.5, 2.0, 2.5));
		Roster r = new Roster();
		TestPlayer early = player(level, "early", GameType.SURVIVAL, at.add(-5, 0, 0));
		TestPlayer target = player(level, "target", GameType.SURVIVAL, at);
		TestPlayer near = player(level, "near", GameType.SURVIVAL, at.add(0, 0, radius - 2));
		TestPlayer far = player(level, "far", GameType.SURVIVAL, at.add(radius + 5, 0, 0));
		TestPlayer elsewhere = player(nether, "elsewhere", GameType.SURVIVAL, at);
		TestPlayer dead = player(level, "dead", GameType.SURVIVAL, at.add(1, 0, 1));
		TestPlayer spectator = player(level, "spectator", GameType.SPECTATOR, at.add(2, 0, 0));
		for (TestPlayer p : List.of(early, target, near, far, elsewhere, dead, spectator)) {
			r.join(p);
		}
		r.tick(level.getServer(), 1);
		dead.setHealth(0.0F);
		Collections.reverse(r.online);
		PlayerWatch w = r.watch;

		helper.assertTrue(uuids(w.sharingWith(target)).equals(uuids(List.of(target, early, near))), "target: " + names(w.sharingWith(target)));
		// The target comes first even when it does not take part; a spectator never joins anyone else's moment.
		helper.assertTrue(uuids(w.sharingWith(spectator)).equals(uuids(List.of(spectator, early, target, near))),
				"spectator: " + names(w.sharingWith(spectator)));
		helper.assertTrue(uuids(w.sharingWith(far)).equals(uuids(List.of(far))), "far: " + names(w.sharingWith(far)));
		helper.assertTrue(uuids(w.sharingWith(elsewhere)).equals(uuids(List.of(elsewhere))), "another dimension: " + names(w.sharingWith(elsewhere)));
		helper.succeed();
	}

	// --- director contract ---

	@GameTest
	@SuppressWarnings("removal")
	public void fireSkipsPlayersThatCannotBeTargeted(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		ServerPlayer stranger = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		helper.assertTrue(Services.director().fire(server, stranger, "debug_ping", false) == FireResult.SKIPPED, "fired for a player not online");
		helper.assertTrue(new Director.Stub().fire(server, stranger, "debug_ping", false) == FireResult.SKIPPED, "the stub fired for a player not online");
		helper.assertTrue(new Director.Stub().fire(server, stranger, "no_such_card", false) == FireResult.SKIPPED, "an unknown card fired");
		// The deprecated form fires for the first participant; nobody takes part in game tests.
		helper.assertTrue(Services.director().fire(server, "debug_ping", false) == FireResult.SKIPPED, "fired with nobody online");
		helper.succeed();
	}
}
