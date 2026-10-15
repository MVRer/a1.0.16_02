package com.forzacode.a1016_02.dig;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/**
 * Two-player game tests of the dig workstream (D-058 to D-062): one network per distinct base, ENTERED_TUNNEL once per
 * occasion, a torch follower that stops when its player leaves, and saves from before multiplayer. Each test has its
 * own players, state and {@link DigData} ({@link Crew}); none of their players joins the server. {@link DigGameTests}
 * extends this through {@link NetworkGameTests}; this extends the tunnel ladder's tests ({@link LadderGameTests}).
 */
public class DigMultiplayerTests extends LadderGameTests {
	/**
	 * One network per distinct base: a friend's base within {@code networkShareDistance} shares the founder's network
	 * (and becomes one of its owners), a far base gets its own after its nights; never one under a base its player
	 * never came home to, and never more than {@code networkMaxPerWorld}.
	 */
	@GameTest
	public void networksPerBaseWithSharedBasesSharingOne(GameTestHelper helper) {
		DigConfig config = new DigConfig();
		config.networkMaxPerWorld = 2;
		DigData data = new DigData();
		UUID alex = UUID.randomUUID();
		UUID sam = UUID.randomUUID();
		UUID kim = UUID.randomUUID();
		UUID lee = UUID.randomUUID();
		UUID max = UUID.randomUUID();
		GlobalPos home = GlobalPos.of(helper.getLevel().dimension(), new BlockPos(100, 70, 100));
		GlobalPos samHome = GlobalPos.of(home.dimension(), home.pos().east(20));
		GlobalPos kimHome = GlobalPos.of(home.dimension(), home.pos().east(500));
		GlobalPos leeHome = GlobalPos.of(home.dimension(), home.pos().east(1000));
		GlobalPos maxHome = GlobalPos.of(home.dimension(), home.pos().west(800));
		long night = 5;
		UnderYou.see(data, alex, home, night, true, config);
		UnderYou.see(data, sam, samHome, night, true, config);
		UnderYou.see(data, kim, kimHome, night, true, config);
		UnderYou.see(data, lee, leeHome, night, true, config);
		UnderYou.see(data, max, maxHome, night, false, config); // a respawn point Max never came home to
		long later = night + config.networkStartAfterNights;

		Network shared = UnderYou.active(data, alex, home, later, config, false);
		helper.assertTrue(shared != null && shared.founder().equals(Optional.of(alex)), "Alex's base got no network");
		helper.assertTrue(UnderYou.active(data, sam, samHome, later, config, false) == shared && shared.owners().equals(List.of(alex, sam)),
				"Sam's base next door did not share Alex's network: " + shared.owners());
		helper.assertTrue(data.networkFor(home.dimension(), samHome.pos(), config.networkShareDistance) == shared, "the shared network is not found from Sam's base");
		Network far = UnderYou.active(data, kim, kimHome, later, config, false);
		helper.assertTrue(far != null && far != shared && far.founder().equals(Optional.of(kim)) && data.networks.size() == 2, "Kim's far base got no network of its own");
		helper.assertTrue(UnderYou.active(data, lee, leeHome, later, config, false) == null && data.networks.size() == 2, "a third network past the cap");

		config.networkMaxPerWorld = 6;
		helper.assertTrue(UnderYou.active(data, max, maxHome, later + 10, config, false) == null, "a network under a base its player never came home to");
		UnderYou.see(data, max, maxHome, later + 10, true, config);
		helper.assertTrue(UnderYou.active(data, max, maxHome, later + 10, config, false) != null && data.networks.size() == 3,
				"Max's base, once he came home, never got its network");
		helper.succeed();
	}

	/**
	 * Camp beds never use up the world's networks (MP-7): Ben sleeps at a new camp bed every night (a bed he gets into
	 * is his respawn point, and he is at home there), and a network starts under each one the morning after. A camp he
	 * moved on from is nobody's base any more, so it no longer counts toward {@code networkMaxPerWorld}: Alex's new home
	 * far away still gets its own network after its night.
	 */
	@GameTest
	public void campBedsNeverUseUpTheWorldsNetworks(GameTestHelper helper) {
		Crew crew = new Crew(helper, "Alex", "Ben");
		UUID alex = crew.uuid("Alex");
		UUID ben = crew.uuid("Ben");
		DigConfig config = new DigConfig();
		DigData data = crew.data;
		GlobalPos start = GlobalPos.of(helper.getLevel().dimension(), new BlockPos(100, 70, 100));
		long night = 3;
		for (int camp = 0; camp < config.networkMaxPerWorld; camp++, night++) {
			GlobalPos bed = GlobalPos.of(start.dimension(), start.pos().east(200 * camp));
			UnderYou.see(data, ben, bed, night, true, config);
			Network under = UnderYou.active(data, ben, bed, night + 1, config, false);
			helper.assertTrue(under != null && under.base().equals(bed.pos()), "no network under camp bed " + camp);
		}
		GlobalPos home = GlobalPos.of(start.dimension(), start.pos().south(1500));
		UnderYou.see(data, alex, home, night, true, config);
		helper.assertTrue(UnderYou.active(data, alex, home, night, config, false) == null, "Alex's new home started on its first night");
		Network alexs = UnderYou.active(data, alex, home, night + config.networkStartAfterNights, config, false);
		helper.assertTrue(alexs != null && alexs.founder().equals(Optional.of(alex)) && alexs.base().equals(home.pos()),
				"Ben's old camp beds used up the world's networks: Alex's home got none (" + data.networks.size() + " networks)");
		// Ben comes back to his first camp: its network is his again (no new one), and it counts once more.
		GlobalPos first = GlobalPos.of(start.dimension(), start.pos());
		UnderYou.see(data, ben, first, night + 5, true, config);
		int before = data.networks.size();
		Network back = UnderYou.active(data, ben, first, night + 5, config, false);
		helper.assertTrue(back != null && back.base().equals(first.pos()) && data.networks.size() == before, "Ben's first camp did not get its network back");
		helper.succeed();
	}

	/**
	 * A player alone who moves base gets a network at every new base, as in v1.0, however often they move: the bases they
	 * left never use up {@code networkMaxPerWorld}.
	 */
	@GameTest
	public void aSoloPlayerWhoMovesBaseGetsANetworkAtEachNewBase(GameTestHelper helper) {
		Crew crew = new Crew(helper, "Alex");
		UUID alex = crew.uuid("Alex");
		DigConfig config = new DigConfig();
		DigData data = crew.data;
		BlockPos first = new BlockPos(-400, 70, 300);
		long night = 2;
		for (int move = 0; move <= config.networkMaxPerWorld + 1; move++, night += 3) {
			GlobalPos base = GlobalPos.of(helper.getLevel().dimension(), first.north(300 * move));
			UnderYou.see(data, alex, base, night, true, config);
			Network net = UnderYou.active(data, alex, base, night + config.networkStartAfterNights, config, false);
			helper.assertTrue(net != null && net.base().equals(base.pos()) && net.founder().equals(Optional.of(alex)),
					"Alex's base " + (move + 1) + " got no network (" + data.networks.size() + " networks)");
		}
		helper.succeed();
	}

	/**
	 * ENTERED_TUNNEL once per occasion: two friends walking into the same tunnel together count once, a third player in
	 * another tunnel far away is an occasion of her own, and nobody counts again within the cooldown.
	 */
	@GameTest
	public void enteredTunnelCountsOnceForTwoPlayersTogether(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		Crew crew = new Crew(helper, "Alex", "Sam", "Kim");
		BlockPos near = helper.absolutePos(new BlockPos(4, 1, 4));
		BlockPos away = helper.absolutePos(new BlockPos(300, 1, 4));
		crew.data.addTunnel(new DigData.CardTunnel(helper.getLevel().dimension(), List.of(near, near.east(), near.east(2), near.east(3)), "plain", -1));
		crew.data.addTunnel(new DigData.CardTunnel(helper.getLevel().dimension(), List.of(away, away.east()), "plain", -1));
		crew.join("Alex", Vec3.atBottomCenterOf(near));
		crew.join("Sam", Vec3.atBottomCenterOf(near.east(2)));
		DigConfig config = new DigConfig();
		int[] counted = {0};
		Runnable trigger = () -> counted[0]++;

		int fired = DigTicker.INSTANCE.checkEnteredTunnel(server, crew.watch, crew.watch.participants(server), crew.data, config, trigger);
		helper.assertTrue(fired == 1 && counted[0] == 1, "two players in one tunnel together counted " + counted[0] + " times");
		crew.join("Kim", Vec3.atBottomCenterOf(away));
		fired = DigTicker.INSTANCE.checkEnteredTunnel(server, crew.watch, crew.watch.participants(server), crew.data, config, trigger);
		helper.assertTrue(fired == 1 && counted[0] == 2, "Kim's own tunnel far away did not count once: " + fired);
		fired = DigTicker.INSTANCE.checkEnteredTunnel(server, crew.watch, crew.watch.participants(server), crew.data, config, trigger);
		helper.assertTrue(fired == 0 && counted[0] == 2, "counted again within the cooldown");
		helper.succeed();
	}

	/** D-062.5: a player's torches-behind-you follower stops when they stop taking part, with or without the event. */
	@GameTest(maxTicks = 40)
	public void torchFollowerStopsWhenItsPlayerLeaves(GameTestHelper helper) {
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		BlockPos torch = helper.absolutePos(new BlockPos(1, 1, 1));
		DigTicker.INSTANCE.startTorchSession(player, List.of(torch));
		helper.assertTrue(DigTicker.INSTANCE.torchSessionActive(player.getUUID()), "no follower started");
		DigTicker.INSTANCE.onStop(player.getUUID());
		helper.assertFalse(DigTicker.INSTANCE.torchSessionActive(player.getUUID()), "the follower outlived its player's leave");
		// Gone without an event (this mock player never takes part): the tick drops it quietly.
		DigTicker.INSTANCE.startTorchSession(player, List.of(torch));
		helper.runAfterDelay(2, () -> {
			helper.assertFalse(DigTicker.INSTANCE.torchSessionActive(player.getUUID()), "the follower ran for someone who does not take part");
			helper.succeed();
		});
	}

	/**
	 * D-062.8: a {@code dig.dat} from before multiplayer (one top-level {@code baseSeen}, a network without owners)
	 * loads, waits while nobody is known, then becomes the first known player's: their base seen (at home), the
	 * network theirs. Once only; the result survives a save.
	 */
	@GameTest
	public void oldDigSaveLoadsAsTheFirstPlayers(GameTestHelper helper) {
		BlockPos base = new BlockPos(40, 70, -12);
		Network net = new Network(helper.getLevel().dimension(), base);
		net.addAnchor(base.offset(2, -12, 0), false);
		net.nights = 3;
		CompoundTag saved = net.save();
		saved.remove("owners");
		ListTag networks = new ListTag();
		networks.add(saved);
		CompoundTag tag = new CompoundTag();
		tag.put("networks", networks);
		tag.putString("baseSeenDimension", helper.getLevel().dimension().identifier().toString());
		tag.putLong("baseSeen", base.asLong());
		tag.putLong("baseSeenNight", 4);

		DigData old = DigData.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(old.hasLegacy() && old.networks().getFirst().owners().isEmpty(), "not an old save");
		helper.assertFalse(old.adoptLegacy(Optional.empty()), "adopted with nobody known");
		UUID subject = UUID.randomUUID();
		helper.assertTrue(old.adoptLegacy(Optional.of(subject)), "the first known player did not adopt it");
		helper.assertTrue(old.networks().getFirst().owners().equals(List.of(subject)), "the old network is not theirs");
		helper.assertTrue(old.baseSeen(subject).equals(Optional.of(new DigData.BaseSeen(GlobalPos.of(helper.getLevel().dimension(), base), 4, true))),
				"the old base is not theirs: " + old.baseSeen(subject));
		helper.assertFalse(old.hasLegacy() || old.adoptLegacy(Optional.of(UUID.randomUUID())), "adopted twice");

		Tag again = DigData.CODEC.encodeStart(NbtOps.INSTANCE, old).getOrThrow();
		DigData back = DigData.CODEC.parse(NbtOps.INSTANCE, again).getOrThrow();
		helper.assertTrue(!back.hasLegacy() && back.networks().getFirst().owners().equals(List.of(subject)) && back.baseSeen(subject).equals(old.baseSeen(subject))
				&& back.networks().getFirst().nights() == 3, "the adopted save does not round-trip");
		helper.succeed();
	}
}
