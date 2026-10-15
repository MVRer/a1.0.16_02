package com.forzacode.a1016_02.dig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import com.forzacode.a1016_02.core.Figure;
import com.forzacode.a1016_02.core.FireContext;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.ModConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * A tunnel ladder of a test's own (D-072): its own crew ({@link Crew}: its players never join the server, so nobody
 * else's is a sharer or a listener), its own memory, numbers, clock and day, and a recorder that is both the sound out
 * and the figure ({@link Figure}: it records every {@code canGlimpse} and {@code glimpse} and never spawns anything).
 * Nothing global is installed, so tests running side by side never see each other's figure.
 */
final class LadderRig implements Ladder.Sounds, Figure {
	/** A step played: when (server tick), what, where, how loud. */
	record Heard(long tick, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
	}

	/** A question to the figure: when, where, and whether it was the spawn ({@code glimpse}) or only {@code canGlimpse}. */
	record Asked(long tick, Vec3 feet, boolean spawn, float yaw) {
	}

	/** Yaws (degrees) facing each way along the axes. */
	static final float EAST = -90.0F;
	static final float WEST = 90.0F;
	static final float NORTH = 180.0F;
	static final float SOUTH = 0.0F;

	final GameTestHelper helper;
	final MinecraftServer server;
	final DigGround ground;
	final Crew crew;
	final DigConfig config = new DigConfig();
	final List<Heard> heard = new ArrayList<>();
	final List<Asked> asked = new ArrayList<>();
	/** What {@code canGlimpse} answers: {@code figureCan} for the feet {@code figureWhere} accepts. */
	boolean figureCan = true;
	Predicate<Vec3> figureWhere = feet -> true;
	/** Every crew member's own clock (a large start, so "never" stays far from it). */
	long playTicks = 10_000_000L;
	long day;
	boolean underground = true;

	LadderRig(GameTestHelper helper, DigGround ground, String... names) {
		this.helper = helper;
		this.server = helper.getLevel().getServer();
		this.ground = ground;
		this.crew = new Crew(helper, names);
	}

	Ladder.Env env() {
		return new Ladder.Env(crew.watch, crew.data, this, config, uuid -> playTicks, () -> day, player -> underground, this);
	}

	/** A straight 1-wide, 2-high tunnel the player dug along x on row {@code z}, feet at {@code y}, from {@code x0} to {@code x1}. */
	void tunnelX(int x0, int x1, int y, int z) {
		ground.digBox(x0, y, z, x1, y + 1, z);
	}

	/** Puts this crew member online standing in the cell at ground coordinates, looking along {@code yaw}. */
	ServerPlayer stand(String name, int x, int y, int z, float yaw) {
		ServerPlayer player = crew.online.stream().filter(p -> p.getName().getString().equals(name)).findFirst()
				.orElseGet(() -> crew.join(name, Vec3.atBottomCenterOf(ground.at(x, y, z))));
		move(player, ground.at(x, y, z));
		look(player, yaw);
		return player;
	}

	static void move(ServerPlayer player, BlockPos cell) {
		player.snapTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, player.getYRot(), player.getXRot());
	}

	static void look(ServerPlayer player, float yaw) {
		player.setYRot(yaw);
		player.setYHeadRot(yaw);
		player.setXRot(0.0F);
	}

	FireResult fire(Ladder.Rung rung, ServerPlayer player, boolean fake, boolean forced, long seed) {
		return Ladder.fire(rung, new FireContext(player, player.level(), fake, forced, RandomSource.create(seed)), env());
	}

	Ladder.Check check(Ladder.Rung rung, ServerPlayer player) {
		return Ladder.check(rung, player, env(), false);
	}

	LadderMemory memory(ServerPlayer player) {
		return crew.data.ladder(player.getUUID());
	}

	static boolean walking(ServerPlayer player) {
		return DigTicker.INSTANCE.ladder().walking().contains(player.getUUID());
	}

	static long minutes(double minutes) {
		return ModConfig.realTicks(minutes * 60);
	}

	/** Cells back from the player along -x (the tunnels run along x and the players face east). */
	static int backAlongX(ServerPlayer player, Vec3 pos) {
		return (int) Math.round(player.getX() - pos.x);
	}

	/** {@link #backAlongX} without rounding (rung 3's steps may fall between cell centres). */
	static double exactBackAlongX(ServerPlayer player, Vec3 pos) {
		return player.getX() - pos.x;
	}

	List<Asked> spawns() {
		return asked.stream().filter(Asked::spawn).toList();
	}

	UUID uuid(String name) {
		return crew.uuid(name);
	}

	@Override
	public void play(ServerLevel level, Holder<SoundEvent> sound, SoundSource source, Vec3 pos, float volume, float pitch) {
		heard.add(new Heard(server.getTickCount(), sound, source, pos, volume, pitch));
	}

	/** Everyone a step could reach: this crew's own players in that level (spectators included), nobody else on the server. */
	@Override
	public List<ServerPlayer> listeners(ServerLevel level) {
		return crew.online.stream().filter(player -> player.level() == level).toList();
	}

	@Override
	public boolean canGlimpse(ServerPlayer player, Vec3 feet) {
		asked.add(new Asked(server.getTickCount(), feet, false, 0.0F));
		return figureCan && figureWhere.test(feet);
	}

	@Override
	public FireResult glimpse(ServerPlayer player, Vec3 feet, float yaw) {
		asked.add(new Asked(server.getTickCount(), feet, true, yaw));
		return FireResult.FIRED;
	}
}
