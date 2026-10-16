package com.forzacode.a1016_02.atmosphere.client;

import java.util.List;
import java.util.Optional;

import com.forzacode.a1016_02.atmosphere.AtmosphereConfig;
import com.forzacode.a1016_02.atmosphere.CompassDriftPayload;
import com.forzacode.a1016_02.atmosphere.Curves;
import com.forzacode.a1016_02.atmosphere.DeadMountains;
import com.forzacode.a1016_02.atmosphere.DeadMountainsPayload;
import com.forzacode.a1016_02.atmosphere.DuskLevel;
import com.forzacode.a1016_02.atmosphere.WeatherFog;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNight;
import com.forzacode.a1016_02.atmosphere.sc.EmptyNightPayload;
import com.forzacode.a1016_02.core.ClientEffects;
import com.forzacode.a1016_02.core.FogLimits;
import com.forzacode.a1016_02.core.SelfCreated;
import com.forzacode.a1016_02.core.client.ClientEffectsClient;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.MoonPhase;
import net.minecraft.world.level.material.FogType;

import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Client state of the atmosphere layer and the {@link ClientEffectsClient.Handler}. Everything runs on the client
 * thread. Time is counted in client ticks that only advance while the game is not paused. {@link #reset()} on
 * disconnect; core's {@code Sync} on join restores the persistent part (music off, dusk fog, the fog spell). The fog
 * spell (D-071) is timed on the level's own clock, the server's, not on these ticks.
 *
 * <p>Dead mountains: while the player stands on the dead ground of one of the circles the server sent, ambience (birds, wind loops,
 * cave mood, ambient additions, every {@code AMBIENT} sound) and music fade out, and no new music track starts; they
 * come back gradually after leaving. Footsteps, blocks, mobs and weather are untouched.
 *
 * <p>The layer (docs/SELF_CREATED.md), while it is switched on here ({@code SelfCreated.enabled}): the world's fog
 * places (10) go into the frame's weather fog with the spell ({@link WeatherFog#frame(long, boolean, int, double,
 * ResourceKey, net.minecraft.world.phys.Vec3)} at the camera); and 04's sky window ({@link EmptyNightPayload}) gives the
 * moon drawn in the overworld ({@link #moonPhase}). Both reset on disconnect.
 */
public final class ClientAtmosphere implements ClientEffectsClient.Handler {
	static final ClientAtmosphere INSTANCE = new ClientAtmosphere();

	/** Ticks over which a silence cuts the sound out. */
	private static final int SILENCE_CUT_TICKS = 12;
	private static final SoundSource[] SILENCED = {SoundSource.AMBIENT, SoundSource.WEATHER, SoundSource.MUSIC};
	/** How far into the fade each silenced category starts coming back: ambience first, then weather, then music. */
	private static final double[] SILENCE_LAG = {0.0, 0.2, 0.45};
	/**
	 * The quietest a category gets. Not zero: the sound engine skips starting a sound at volume 0, and a biome loop
	 * skipped that way would not come back until the biome changes. At -80 dB nothing is heard.
	 */
	private static final float MIN_VOLUME = 1.0E-4F;

	private long ticks;

	private boolean musicOff;
	/** The dusk fog level drawn: set by the server, eased between levels. */
	private final DuskLevel dusk = new DuskLevel();
	/** The world's fog spell (D-071), as the server last sent it. */
	private final WeatherFog weather = new WeatherFog();
	/** The fog being shaped this frame. */
	private final Curves.Fog frame = new Curves.Fog();
	/** The fog color being shaped this frame. */
	private final float[] rgb = new float[3];
	/** The tinted base fog color handed back to vanilla this frame (read at once, before the next frame). */
	private final Vector3f tinted = new Vector3f();
	/** The spell's fog this frame at the camera: its amount times the smoothed sky weight ({@link #beginFog}). */
	private double frameSpell;
	/** The level clock this frame (the tint's time of day). */
	private long frameClock;
	/** The level the sky weight was last stepped in: a new one takes its weight at once. */
	private @Nullable ResourceKey<Level> skyDimension;
	/** 04's sky window on the overworld clock, as the server last sent it. */
	private Optional<EmptyNight.Window> emptyNight = Optional.empty();

	private ClientEffects.@Nullable FogSurge surge;
	private long surgeStart;

	private ClientEffects.@Nullable Silence silence;
	private long silenceStart;
	private final float[] volume = {1.0F, 1.0F, 1.0F};

	private @Nullable CompassDriftPayload compass;
	private long compassStart;

	private @Nullable ResourceKey<Level> deadDimension;
	private List<DeadMountains.Area> deadAreas = List.of();
	/** 0 = normal, 1 = as quiet as a dead mountain gets. */
	private double deadQuiet;
	private float deadVolume = 1.0F;

	private ClientAtmosphere() {
	}

	// --- handler (client thread) ---

	@Override
	public void fogSurge(ClientEffects.FogSurge effect) {
		surge = effect;
		surgeStart = ticks;
	}

	@Override
	public void silence(ClientEffects.Silence effect) {
		silence = effect;
		silenceStart = ticks;
	}

	@Override
	public void musicOff(ClientEffects.MusicOff effect) {
		setMusicOff(effect.off());
	}

	@Override
	public void duskFog(ClientEffects.DuskFog effect) {
		setDusk(effect.level());
	}

	@Override
	public void fogSpell(ClientEffects.FogSpell effect) {
		weather.receive(effect);
	}

	@Override
	public void fogPlaces(ClientEffects.FogPlaces effect) {
		weather.receive(effect);
	}

	@Override
	public void sync(ClientEffects.Sync effect) {
		setMusicOff(effect.musicOff());
		setDusk(effect.duskFogLevel());
		weather.receive(effect);
	}

	void emptyNight(EmptyNightPayload payload) {
		emptyNight = payload.window();
	}

	/** The first level after joining applies at once; later ones ease in over {@code duskLevelChangeSeconds}. */
	private void setDusk(float level) {
		AtmosphereConfig cfg = AtmosphereConfig.get();
		dusk.set(level, cfg.duskHazeFullLevel, ticks, AtmosphereConfig.ticks(cfg.duskLevelChangeSeconds));
	}

	void deadMountains(DeadMountainsPayload payload) {
		deadDimension = payload.dimension();
		deadAreas = List.copyOf(payload.areas());
	}

	void compassDrift(CompassDriftPayload payload) {
		compass = payload.ticks() > 0 ? payload : null;
		compassStart = ticks;
	}

	private void setMusicOff(boolean off) {
		musicOff = off;
		if (off) {
			Minecraft.getInstance().getMusicManager().stopPlaying();
		}
	}

	/** Back to vanilla: on disconnect. */
	void reset() {
		boolean hadSilence = silence != null || deadVolume != 1.0F;
		musicOff = false;
		dusk.reset();
		weather.reset();
		frameSpell = 0.0;
		skyDimension = null;
		emptyNight = Optional.empty();
		surge = null;
		silence = null;
		compass = null;
		deadDimension = null;
		deadAreas = List.of();
		deadQuiet = 0.0;
		deadVolume = 1.0F;
		for (int i = 0; i < volume.length; i++) {
			volume[i] = 1.0F;
		}
		if (hadSilence) {
			refreshVolumes();
		}
	}

	// --- tick ---

	void tick(Minecraft minecraft) {
		if (minecraft.level == null || minecraft.isPaused()) {
			return;
		}
		ticks++;
		if (surge != null && ticks - surgeStart > Curves.surgeLength(surge.rampTicks(), surge.holdTicks(), surge.fadeTicks())) {
			surge = null;
		}
		boolean changed = tickSilence();
		changed |= tickDeadMountain(minecraft);
		if (changed) {
			refreshVolumes();
		}
		tickCompass(minecraft);
	}

	/** @return whether a volume changed */
	private boolean tickDeadMountain(Minecraft minecraft) {
		boolean inside = insideDeadMountain(minecraft);
		if (!inside && deadQuiet == 0.0) {
			return false;
		}
		AtmosphereConfig cfg = AtmosphereConfig.get();
		deadQuiet = Curves.quietStep(deadQuiet, inside, cfg.deadMountainFadeOutTicks, cfg.deadMountainRestoreTicks);
		float v = (float) Curves.quietVolume(deadQuiet);
		if (v != deadVolume) {
			deadVolume = v;
			return true;
		}
		return false;
	}

	private boolean insideDeadMountain(Minecraft minecraft) {
		if (deadAreas.isEmpty() || minecraft.player == null || minecraft.level == null || !minecraft.level.dimension().equals(deadDimension)) {
			return false;
		}
		// The same test as the spawn rule: inside a circle and on dead ground (the surface of the column under the player).
		return DeadMountains.inside(minecraft.level, deadAreas, minecraft.player.getBlockX(), minecraft.player.getBlockZ());
	}

	/** @return whether a volume changed */
	private boolean tickSilence() {
		if (silence == null) {
			return false;
		}
		long t = ticks - silenceStart;
		boolean over = t > Curves.silenceLength(silence.ticks(), silence.fadeTicks());
		boolean changed = false;
		for (int i = 0; i < SILENCED.length; i++) {
			float v = over ? 1.0F : (float) Curves.silenceVolume(t, Math.min(SILENCE_CUT_TICKS, Math.max(1, silence.ticks())), silence.ticks(),
					silence.fadeTicks(), SILENCE_LAG[i]);
			if (Math.abs(v - volume[i]) > 1.0E-3F || over && volume[i] != 1.0F) {
				volume[i] = v;
				changed = true;
			}
		}
		if (over) {
			silence = null;
		}
		return changed;
	}

	private static void refreshVolumes() {
		for (SoundSource source : SILENCED) {
			Minecraft.getInstance().getSoundManager().refreshCategoryVolume(source);
		}
	}

	private void tickCompass(Minecraft minecraft) {
		if (compass == null) {
			return;
		}
		boolean expired = ticks - compassStart >= compass.ticks();
		boolean arrived = false;
		if (minecraft.player != null) {
			double dx = minecraft.player.getX() - (compass.x() + 0.5);
			double dz = minecraft.player.getZ() - (compass.z() + 0.5);
			arrived = dx * dx + dz * dz <= (double) compass.settleBlocks() * compass.settleBlocks();
		}
		if (expired || arrived) {
			compass = null;
		}
	}

	// --- hooks for the mixins ---

	public static boolean musicOff() {
		return INSTANCE.musicOff;
	}

	/** While on (or just leaving) a dead mountain no new music track starts; the schedule picks up again after. */
	public static boolean holdMusic() {
		return INSTANCE.deadQuiet > 0.0;
	}

	/** Multiplier for a sound category's volume (silence, dead mountain quiet). Never below {@link #MIN_VOLUME}. */
	public static float volumeFactor(SoundSource source) {
		ClientAtmosphere self = INSTANCE;
		if (self.silence == null && self.deadVolume == 1.0F && self.volume[0] == 1.0F && self.volume[1] == 1.0F && self.volume[2] == 1.0F) {
			return 1.0F;
		}
		float factor = 1.0F;
		for (int i = 0; i < SILENCED.length; i++) {
			if (SILENCED[i] == source) {
				factor = self.volume[i];
				break;
			}
		}
		if (source == SoundSource.AMBIENT || source == SoundSource.MUSIC) {
			factor *= self.deadVolume;
		}
		return factor >= 1.0F ? 1.0F : Math.max(MIN_VOLUME, factor);
	}

	/**
	 * The start of {@code FogRenderer.setupFog}, before vanilla computes the fog color: this frame's spell fog at the
	 * camera. The spell's amount (its own ease, on the level's clock; none where time is fixed) times the sky weight at
	 * the camera's block ({@link WeatherFog#sky}: core's {@code FogLimits.skyFactor} of the sky light there, smoothed as
	 * vanilla smooths its rain fog), stepped every frame so it is right when a spell comes. A spell is outside weather:
	 * a tunnel, a cave or a closed house gets none of it. The tint and the fog shape of the same frame use it.
	 */
	public static void beginFog(Camera camera, @Nullable ClientLevel level, float partialTick) {
		ClientAtmosphere self = INSTANCE;
		self.frameSpell = 0.0;
		if (level == null) {
			return;
		}
		if (!level.dimension().equals(self.skyDimension)) {
			self.skyDimension = level.dimension();
			self.weather.resetSky();
		}
		self.frameClock = level.getDefaultClockTime();
		self.frameSpell = self.weather.frame(self.frameClock, level.dimensionType().hasFixedTime(),
				level.getBrightness(LightLayer.SKY, camera.blockPosition()), self.ticks + partialTick, level.dimension(), camera.position());
	}

	/**
	 * The moon to draw ({@code SkyMoonMixin}, after vanilla filled it): the new moon in the overworld while 04's window
	 * holds the level's clock ({@link EmptyNight#moonFor}), else vanilla's; vanilla's always while the layer is
	 * switched off here.
	 */
	public static MoonPhase moonPhase(ClientLevel level, MoonPhase vanilla) {
		ClientAtmosphere self = INSTANCE;
		if (self.emptyNight.isEmpty() || level.dimension() != Level.OVERWORLD || !SelfCreated.enabled()) {
			return vanilla;
		}
		return EmptyNight.moonFor(level.getDefaultClockTime(), self.emptyNight, vanilla);
	}

	/**
	 * Tints vanilla's base fog color for the frame's spell fog ({@link WeatherFog#color}) in
	 * {@code FogRenderer.computeFogColor}, where the color source hands it over and before vanilla darkens it: the void
	 * near the bottom of the world ({@code (1 - darkness)^2}), the Blindness and Darkness blends, a boss's darkening and
	 * night vision all apply over the tint with vanilla's own values, so the bottom of the world stays black and the
	 * effects come and go as smoothly as without a spell. Atmospheric fog only (not water, lava, snow).
	 * {@code FogData.color} is both the color every world shader fades into (terrain, sky, clouds) and the frame's clear
	 * color, so the whole far view goes pale together.
	 */
	public static Vector3fc tintFogColor(Vector3fc base, Camera camera) {
		ClientAtmosphere self = INSTANCE;
		if (!(self.frameSpell > 0.0) || camera.getFluidInCamera() != FogType.NONE) {
			return base;
		}
		float[] rgb = self.rgb;
		rgb[0] = base.x();
		rgb[1] = base.y();
		rgb[2] = base.z();
		WeatherFog.color(rgb, self.frameSpell, Math.floorMod(self.frameClock, Curves.DAY), AtmosphereConfig.get());
		return self.tinted.set(rgb[0], rgb[1], rgb[2]);
	}

	/**
	 * Pulls the world fog in for the dusk level, the frame's spell fog ({@link #beginFog}) and any fog surge, after
	 * vanilla sets it up. Atmospheric fog only (not water, lava, snow). The dusk fog and the spell grow continuously out
	 * of vanilla's ({@link Curves#applyDusk}, their amounts combined as core's {@code FogLimits} does, so the server's
	 * fog end is this one under open sky: game test serverFogMatchesClientFogWithASpell); surges keep their v0.4 shape
	 * on top. The color was tinted before vanilla darkened it ({@link #tintFogColor}).
	 */
	public static void applyFog(FogData fog, Camera camera, @Nullable ClientLevel level, float partialTick) {
		if (level == null || camera.getFluidInCamera() != FogType.NONE) {
			return;
		}
		ClientAtmosphere self = INSTANCE;
		AtmosphereConfig cfg = AtmosphereConfig.get();
		double now = self.ticks + partialTick;
		boolean fixedTime = level.dimensionType().hasFixedTime();
		long time = level.getDefaultClockTime();
		double duskLevel = self.dusk.level(now);
		double dusk = 0.0;
		double haze = 0.0;
		if (duskLevel > 0.0 && !fixedTime) {
			// The same amount and curve core's FogLimits uses on the server (game test: serverFogMatchesClientFog).
			FogLimits.Shape shape = cfg.fogShape();
			dusk = Curves.duskAmount(duskLevel, time, shape);
			haze = Curves.duskHaze(time, shape) * self.dusk.haze(now);
		}
		double spell = self.frameSpell;
		double surgeAmount = 0.0;
		ClientEffects.FogSurge s = self.surge;
		if (s != null) {
			double t = self.ticks - self.surgeStart + partialTick;
			surgeAmount = s.strength() * Curves.surgeEnvelope(t, s.rampTicks(), s.holdTicks(), s.fadeTicks());
		}
		Curves.Fog f = self.frame.set(fog.environmentalStart, fog.environmentalEnd, fog.renderDistanceStart, fog.renderDistanceEnd, fog.skyEnd,
				fog.cloudEnd);
		if (!WeatherFog.shape(f, dusk, haze, spell, surgeAmount, cfg)) {
			return;
		}
		fog.environmentalStart = (float) f.environmentalStart;
		fog.environmentalEnd = (float) f.environmentalEnd;
		fog.renderDistanceStart = (float) f.renderDistanceStart;
		fog.renderDistanceEnd = (float) f.renderDistanceEnd;
		fog.skyEnd = (float) f.skyEnd;
		fog.cloudEnd = (float) f.cloudEnd;
	}

	/** Where the local player's spawn compass points: the drift target while a drift runs. */
	public static @Nullable GlobalPos compassTarget(ClientLevel level, @Nullable ItemOwner owner, @Nullable GlobalPos original) {
		CompassDriftPayload drift = INSTANCE.compass;
		Minecraft minecraft = Minecraft.getInstance();
		if (drift == null || owner == null || original == null || minecraft.player == null || owner.asLivingEntity() != minecraft.player
				|| original.dimension() != level.dimension()) {
			return original;
		}
		return GlobalPos.of(level.dimension(), new BlockPos(drift.x(), minecraft.player.getBlockY(), drift.z()));
	}
}
