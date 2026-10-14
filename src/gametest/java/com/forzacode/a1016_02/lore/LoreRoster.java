package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.WorldProfile;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.UUIDUtil;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * A test's own players (D-058 to D-062): its own online list, state, lore data and telling data, through
 * {@link PlayerWatch.Source}, so two-player tests never depend on who else is online on the shared game test server.
 * Its players never join the server; only this roster lists them as online.
 */
final class LoreRoster implements PlayerWatch.Source {
	/** A player only this roster lists as online. Its game mode can be switched (a spectator is not a participant). */
	static final class TestPlayer extends ServerPlayer {
		@Nullable GameType mode;

		TestPlayer(ServerLevel level, UUID uuid, String name) {
			super(level.getServer(), level, new GameProfile(uuid, name), ClientInformation.createDefault());
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
	}

	/** A known player as a test writes it into a save: their name, their own play ticks and first blocks. */
	record Known(UUID uuid, String name, long playTicks, HerobrineState.FirstBlocks firstBlocks) {
		Known(String name, long playTicks) {
			this(UUID.randomUUID(), name, playTicks, HerobrineState.FirstBlocks.NONE);
		}

		Known withFirsts(HerobrineState.FirstBlocks firsts) {
			return new Known(uuid, name, playTicks, firsts);
		}
	}

	final List<ServerPlayer> online = new ArrayList<>();
	final HerobrineState state;
	final PlayerWatch watch = new PlayerWatch(this);
	final LoreData lore = new LoreData();
	final TellingData telling = new TellingData();

	LoreRoster(HerobrineState state) {
		this.state = state;
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

	Homes homes(MinecraftServer server) {
		return new Homes(server, state, watch, lore);
	}

	FragmentEngine.LiveFacts facts(MinecraftServer server) {
		return new FragmentEngine.LiveFacts(homes(server), telling);
	}

	/** A player of this roster, standing at {@code relative} in the test's area; not online until {@link #join}. */
	TestPlayer player(GameTestHelper helper, Known known, Vec3 relative) {
		TestPlayer player = new TestPlayer(helper.getLevel(), known.uuid(), known.name());
		player.snapTo(helper.absoluteVec(relative), 0.0F, 0.0F);
		return player;
	}

	TestPlayer join(TestPlayer player) {
		online.add(player);
		return player;
	}

	void leave(ServerPlayer player) {
		online.removeIf(p -> p.getUUID().equals(player.getUUID()));
	}

	// --- saves ---

	static RegistryOps<Tag> ops(GameTestHelper helper) {
		return helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	/** A state (as saved now) whose known players are these, in this join order, with a rolled profile. */
	static HerobrineState state(GameTestHelper helper, Known... players) {
		RegistryOps<Tag> ops = ops(helper);
		CompoundTag tag = base(ops);
		ListTag list = new ListTag();
		for (Known known : players) {
			CompoundTag player = new CompoundTag();
			player.store("uuid", UUIDUtil.CODEC, ops, known.uuid());
			player.putString("name", known.name());
			player.put("firstBlocks", firsts(ops, known.firstBlocks()));
			player.putLong("playTicks", known.playTicks());
			list.add(player);
		}
		tag.put("players", list);
		return HerobrineState.CODEC.parse(ops, tag).getOrThrow();
	}

	/** A v1.0 {@code state.dat}: one {@code subject} with top-level first blocks, the subject's ticks in the clock. */
	static HerobrineState v1State(GameTestHelper helper, Known subject) {
		RegistryOps<Tag> ops = ops(helper);
		CompoundTag tag = base(ops);
		CompoundTag old = new CompoundTag();
		old.store("uuid", UUIDUtil.CODEC, ops, subject.uuid());
		old.putString("name", subject.name());
		tag.put("subject", old);
		tag.put("firstBlocks", firsts(ops, subject.firstBlocks()));
		CompoundTag clock = new CompoundTag();
		clock.putLong("playTicks", subject.playTicks());
		tag.put("clock", clock);
		return HerobrineState.CODEC.parse(ops, tag).getOrThrow();
	}

	private static CompoundTag base(RegistryOps<Tag> ops) {
		CompoundTag tag = new CompoundTag();
		tag.putLong("salt", 99L);
		tag.put("profile", WorldProfile.CODEC.encodeStart(ops, WorldProfile.roll(1L, 99L)).getOrThrow());
		return tag;
	}

	private static CompoundTag firsts(RegistryOps<Tag> ops, HerobrineState.FirstBlocks firsts) {
		CompoundTag tag = new CompoundTag();
		put(tag, "block", firsts.block(), ops);
		put(tag, "craftingTable", firsts.craftingTable(), ops);
		put(tag, "chest", firsts.chest(), ops);
		return tag;
	}

	private static void put(CompoundTag tag, String key, @Nullable PlacedBlock block, RegistryOps<Tag> ops) {
		if (block != null) {
			tag.put(key, PlacedBlock.CODEC.encodeStart(ops, block).getOrThrow());
		}
	}
}
