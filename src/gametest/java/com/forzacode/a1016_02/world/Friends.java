package com.forzacode.a1016_02.world;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlacedBlock;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.WorldProfile;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import org.jspecify.annotations.Nullable;

/**
 * A test's own friends (D-058 to D-062): its own online list and story state through {@link PlayerWatch.Source}, so
 * two-player tests never depend on who else is online on the shared game test server, and its own
 * {@link WorldData}. Its players never join the server; only these friends list them as online.
 */
final class Friends implements PlayerWatch.Source {
	/** A player only these friends list as online. */
	static final class Friend extends ServerPlayer {
		Friend(ServerLevel level, UUID uuid, String name) {
			super(level.getServer(), level, new GameProfile(uuid, name), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return GameType.SURVIVAL;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	/** A known player as a test writes them into a save: name, own play ticks, and their first block (or none). */
	record Known(UUID uuid, String name, long playTicks, @Nullable GlobalPos firstBlock) {
		Known(String name, long playTicks, @Nullable GlobalPos firstBlock) {
			this(UUID.randomUUID(), name, playTicks, firstBlock);
		}
	}

	final GameTestHelper helper;
	final List<ServerPlayer> online = new ArrayList<>();
	final HerobrineState state;
	final PlayerWatch watch = new PlayerWatch(this);
	final WorldData data;

	Friends(GameTestHelper helper, HerobrineState state) {
		this(helper, state, new WorldData());
	}

	Friends(GameTestHelper helper, HerobrineState state, WorldData data) {
		this.helper = helper;
		this.state = state;
		this.data = data;
	}

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

	Bases bases() {
		return new Bases(helper.getLevel().getServer(), state, watch, data);
	}

	/** Puts this known player online at {@code relative} (in the test's area). */
	ServerPlayer join(Known known, Vec3 relative) {
		Friend player = new Friend(helper.getLevel(), known.uuid(), known.name());
		Vec3 at = helper.absoluteVec(relative);
		player.snapTo(at.x, at.y, at.z, 0.0F, 0.0F);
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
			player.put("firstBlocks", firsts(ops, known.firstBlock()));
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
		tag.put("firstBlocks", firsts(ops, subject.firstBlock()));
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

	private static CompoundTag firsts(RegistryOps<Tag> ops, @Nullable GlobalPos block) {
		CompoundTag tag = new CompoundTag();
		if (block != null) {
			tag.put("block", PlacedBlock.CODEC.encodeStart(ops, new PlacedBlock(block, Blocks.COBBLESTONE.defaultBlockState())).getOrThrow());
		}
		return tag;
	}

	static GlobalPos at(GameTestHelper helper, BlockPos relative) {
		return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
	}
}
