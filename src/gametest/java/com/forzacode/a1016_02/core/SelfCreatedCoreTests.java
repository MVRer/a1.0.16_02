package com.forzacode.a1016_02.core;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.phys.Vec3;

/**
 * SC-0: the core contracts the layer's features build on (docs/SELF_CREATED.md): the figure-left and traced events,
 * fog places (record, state, payloads, {@link FogLimits}, the world's list), taken names in the ledger and their undo,
 * {@link SoundCues#stepType} and {@link SoundCues#alone}, and the layer's switch and file rule ({@link SelfCreated}).
 * Event listeners are registered once for the whole run and keep only what these tests fire (their causes and
 * variants start with {@code test:sc0/} or {@code test_sc0/}); every test that changes the live state puts it back.
 */
public class SelfCreatedCoreTests {
	private static final long DAY = 24_000L;

	/** One TRACED event as a listener saw it, with what was true when it fired. */
	private record SeenTraced(TraceLedger.Entry entry, boolean placed, int ledgerSize, boolean airAtFire) {
	}

	/** One FIGURE_LEFT event as a listener saw it. */
	private record SeenFigure(Vec3 stoodAt, float yaw, List<Vec3> path, String variant, UUID appearedFor, boolean seen) {
	}

	/** The listeners, registered the first time a test needs them: a throwing one first, then the recorders. */
	private static final class Listen {
		static final List<SeenTraced> TRACED = Collections.synchronizedList(new ArrayList<>());
		static final List<SeenFigure> FIGURES = Collections.synchronizedList(new ArrayList<>());

		static {
			HerobrineEvents.TRACED.register((level, entry, placed) -> {
				if (entry.cause().startsWith("test:sc0/throw")) {
					throw new IllegalStateException("a listener that fails (on purpose)");
				}
			});
			HerobrineEvents.TRACED.register((level, entry, placed) -> {
				if (entry.cause().startsWith("test:sc0/")) {
					TRACED.add(new SeenTraced(entry, placed, TraceLedger.get(level.getServer()).entries().size(),
							level.getBlockState(entry.pos().pos()).isAir()));
				}
			});
			HerobrineEvents.FIGURE_LEFT.register((level, stoodAt, yaw, path, variant, appearedFor, seen) -> {
				if (variant.startsWith("test_sc0/")) {
					throw new IllegalStateException("a listener that fails (on purpose)");
				}
			});
			HerobrineEvents.FIGURE_LEFT.register((level, stoodAt, yaw, path, variant, appearedFor, seen) -> {
				if (variant.startsWith("test_sc0/")) {
					FIGURES.add(new SeenFigure(stoodAt, yaw, path, variant, appearedFor, seen));
				}
			});
		}

		static List<SeenTraced> traced(String causePrefix) {
			synchronized (TRACED) {
				return TRACED.stream().filter(e -> e.entry().cause().startsWith(causePrefix)).toList();
			}
		}

		static List<SeenFigure> figures(String variant) {
			synchronized (FIGURES) {
				return FIGURES.stream().filter(e -> e.variant().equals(variant)).toList();
			}
		}
	}

	private static void floor(GameTestHelper helper) {
		for (int x = 0; x < 8; x++) {
			for (int z = 0; z < 8; z++) {
				helper.setBlock(x, 0, z, Blocks.STONE);
			}
		}
	}

	private static <T> T roundTrip(StreamCodec<ByteBuf, T> codec, T value) {
		ByteBuf buf = Unpooled.buffer();
		try {
			codec.encode(buf, value);
			return codec.decode(buf);
		} finally {
			buf.release();
		}
	}

	private static RegistryOps<Tag> ops(GameTestHelper helper) {
		return helper.getLevel().registryAccess().createSerializationContext(NbtOps.INSTANCE);
	}

	private static FogPlace place(Vec3 center, float radius, float level, long from, long until, int ramp, int fade) {
		return new FogPlace(Level.OVERWORLD, center, radius, level, from, until, ramp, fade);
	}

	// --- FIGURE_LEFT ---

	/** Every listener gets the stand spot, the facing and the first 24 samples of the path, oldest first; once per firing. */
	@GameTest
	public void figureLeftGivesTheStandSpotAndTheFirst24Samples(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		String variant = "test_sc0/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		Vec3 stood = helper.absoluteVec(new Vec3(2.5, 1.0, 2.5));
		List<Vec3> path = new ArrayList<>();
		for (int i = 0; i < 30; i++) {
			path.add(stood.add(2.0 * (i + 1), 0.0, 0.0));
		}
		path.add(3, null);
		UUID player = UUID.randomUUID();
		Listen.figures(variant); // registers the listeners
		HerobrineEvents.FIGURE_LEFT.invoker().onFigureLeft(level, stood, 135.0F, path, variant, player, true);

		List<SeenFigure> seen = Listen.figures(variant);
		helper.assertTrue(seen.size() == 1, "fired once, heard " + seen.size() + " times (a failing listener stops the rest?)");
		SeenFigure got = seen.getFirst();
		helper.assertTrue(got.stoodAt().equals(stood) && got.yaw() == 135.0F && got.appearedFor().equals(player) && got.seen(), "the spot, facing, player or seen changed: " + got);
		helper.assertTrue(got.path().size() == HerobrineEvents.FIGURE_PATH_MAX, "the path is not cut to 24: " + got.path().size());
		List<Vec3> expected = path.stream().filter(java.util.Objects::nonNull).limit(HerobrineEvents.FIGURE_PATH_MAX).toList();
		helper.assertTrue(got.path().equals(expected), "not the first samples, oldest first");
		boolean modifiable;
		try {
			got.path().add(Vec3.ZERO);
			modifiable = true;
		} catch (UnsupportedOperationException e) {
			modifiable = false;
		}
		helper.assertFalse(modifiable, "a listener could change the path the others get");

		HerobrineEvents.FIGURE_LEFT.invoker().onFigureLeft(level, stood, 0.0F, null, variant, null, false);
		seen = Listen.figures(variant);
		helper.assertTrue(seen.size() == 2 && seen.get(1).path().isEmpty() && seen.get(1).appearedFor() == null && !seen.get(1).seen(),
				"a second figure, gone without a path: " + seen);
		helper.succeed();
	}

	// --- TRACED ---

	/** A committed batch: each entry (dependents included) fires once, after the whole batch is applied and ledgered. */
	@GameTest
	public void tracedFiresOncePerEntryAfterTheWholeBatch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		for (int x = 1; x <= 3; x++) {
			helper.setBlock(x, 1, 1, Blocks.STONE);
		}
		helper.setBlock(new BlockPos(1, 1, 2), Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.SOUTH));
		String cause = "test:sc0/batch/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		Listen.traced(cause);

		TraceBatch batch = Services.traces().forced().batch(level, cause);
		for (int x = 1; x <= 3; x++) {
			batch.remove(helper.absolutePos(new BlockPos(x, 1, 1)));
		}
		helper.assertTrue(batch.commit(), "the batch was refused");
		int ledgerAfter = TraceLedger.get(level.getServer()).entries().size();
		List<TraceLedger.Entry> ledgered = TraceLedger.get(level.getServer()).entries().stream().filter(e -> e.cause().startsWith(cause)).toList();
		List<SeenTraced> seen = Listen.traced(cause);
		helper.assertTrue(ledgered.size() == 4 && ledgered.stream().anyMatch(e -> e.cause().equals(cause + "/dependent")), "not 3 removals and the torch: " + ledgered);
		helper.assertTrue(seen.size() == ledgered.size(), "heard " + seen.size() + " for " + ledgered.size() + " entries");
		for (TraceLedger.Entry entry : ledgered) {
			helper.assertTrue(seen.stream().filter(s -> s.entry() == entry).count() == 1, "not heard exactly once: " + entry);
		}
		for (SeenTraced s : seen) {
			helper.assertTrue(s.ledgerSize() == ledgerAfter && s.airAtFire(), "heard before the whole batch was in: " + s);
			helper.assertFalse(s.placed(), "natural stone heard as placed");
		}
		helper.assertFalse(batch.commit(), "a batch committed twice");
		helper.assertTrue(Listen.traced(cause).size() == seen.size(), "a second commit was heard");
		helper.succeed();
	}

	/** Placed is read before the edit (a placed torch); stack edits fire, leaves never; a failing listener stops nothing. */
	@GameTest
	public void tracedSaysPlacedForAPlacedTorchAndNeverFiresForLeave(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos torch = helper.absolutePos(new BlockPos(2, 1, 2));
		BlockPos stone = helper.absolutePos(new BlockPos(4, 1, 4));
		BlockPos chest = helper.absolutePos(new BlockPos(6, 1, 6));
		helper.setBlock(new BlockPos(2, 1, 2), Blocks.TORCH);
		helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
		helper.setBlock(new BlockPos(6, 1, 6), Blocks.CHEST);
		Services.watch().onPlaced((ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL), level, torch, Blocks.TORCH.defaultBlockState());
		((Container) level.getBlockEntity(chest)).setItem(0, new ItemStack(Items.STICK, 3));
		String cause = "test:sc0/placed/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		Listen.traced(cause);
		TraceService traces = Services.traces().forced();

		helper.assertTrue(traces.remove(level, torch, cause + "/torch") && traces.remove(level, stone, cause + "/stone"), "a removal was refused");
		List<SeenTraced> torchSeen = Listen.traced(cause + "/torch");
		List<SeenTraced> stoneSeen = Listen.traced(cause + "/stone");
		helper.assertTrue(torchSeen.size() == 1 && torchSeen.getFirst().placed(), "the placed torch: " + torchSeen);
		helper.assertTrue(stoneSeen.size() == 1 && !stoneSeen.getFirst().placed(), "natural stone: " + stoneSeen);

		helper.assertTrue(traces.leave(level, stone, Blocks.COBBLESTONE.defaultBlockState(), cause + "/leave"), "the leave was refused");
		helper.assertTrue(traces.leaveStack(level, chest, new ItemStack(Items.FLINT), cause + "/leave_stack"), "the stack was not left");
		helper.assertTrue(Listen.traced(cause + "/leave").isEmpty(), "a leave or leaveStack was heard");

		helper.assertTrue(traces.removeStack(level, chest, 0, 2, cause + "/stack"), "the stack edit was refused");
		List<SeenTraced> stack = Listen.traced(cause + "/stack");
		helper.assertTrue(stack.size() == 1 && stack.getFirst().entry().kind() == TraceLedger.Kind.REMOVE_STACK, "the stack edit: " + stack);

		// A listener that throws (registered first) does not stop the edit or the listeners after it.
		BlockPos other = helper.absolutePos(new BlockPos(5, 1, 2));
		helper.setBlock(new BlockPos(5, 1, 2), Blocks.STONE);
		helper.assertTrue(traces.remove(level, other, "test:sc0/throw/" + other.toShortString()), "a failing listener refused the edit");
		helper.assertTrue(level.getBlockState(other).isAir() && Listen.traced("test:sc0/throw/" + other.toShortString()).size() == 1,
				"a failing listener stopped the edit or the next listener");
		helper.succeed();
	}

	/** An undo's reverse edit is closed at once and never heard; the edit it takes back was heard once. */
	@GameTest
	public void tracedIsNotFiredForTheUndosReverseEdit(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
		helper.setBlock(new BlockPos(3, 1, 3), Blocks.STONE);
		String cause = "test:sc0/convert/" + pos.toShortString();
		String undo = "test:sc0/undo/" + pos.toShortString();
		Listen.traced(cause);
		TraceService traces = Services.traces().forced();
		helper.assertTrue(traces.convert(level, pos, Blocks.COBBLESTONE.defaultBlockState(), cause), "the conversion was refused");
		TraceLedger.Entry entry = TraceLedger.get(level.getServer()).entries().stream().filter(e -> e.cause().equals(cause)).findFirst().orElseThrow();
		helper.assertTrue(Listen.traced(cause).size() == 1, "the conversion was not heard once");
		helper.assertTrue(traces.undo(level, entry, undo) == TraceService.UndoResult.DONE && level.getBlockState(pos).is(Blocks.STONE), "not undone");
		helper.assertTrue(Listen.traced(undo).isEmpty(), "the undo's closed reverse edit was heard: " + Listen.traced(undo));
		helper.succeed();
	}

	// --- fog places ---

	/** The record's codecs keep every field; the constructor keeps it sane. */
	@GameTest
	public void fogPlaceCodecsRoundTrip(GameTestHelper helper) {
		FogPlace place = place(new Vec3(120.5, 70.0, -33.25), 40.0F, 0.45F, 5 * DAY + 100, 7 * DAY + 100, 1200, 1200);
		Tag tag = FogPlace.CODEC.encodeStart(NbtOps.INSTANCE, place).getOrThrow();
		helper.assertTrue(FogPlace.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow().equals(place), "the save codec lost something");
		helper.assertTrue(roundTrip(FogPlace.STREAM_CODEC, place).equals(place), "the stream codec lost something");
		FogPlace nether = new FogPlace(Level.NETHER, Vec3.ZERO, 8.0F, 1.0F, 0L, 10L, 0, 0);
		helper.assertTrue(roundTrip(FogPlace.STREAM_CODEC, nether).equals(nether), "the dimension did not travel");

		FogPlace odd = new FogPlace(null, new Vec3(Double.NaN, 0.0, 0.0), Float.NaN, Float.NaN, 500L, 100L, -5, -5);
		helper.assertTrue(odd.dimension() == Level.OVERWORLD && odd.radius() == 0.0F && odd.level() == 0.0F && odd.untilClock() == 500L
				&& odd.rampTicks() == 0 && odd.fadeTicks() == 0, "not made sane: " + odd);
		helper.assertTrue(new FogPlace(Level.OVERWORLD, Vec3.ZERO, 10.0F, 3.0F, 0L, 10L, 0, 0).level() == 1.0F, "a level over 1 kept");
		helper.succeed();
	}

	/** Nothing outside its time or radius or in another dimension; full at the center; continuous everywhere. */
	@GameTest
	public void fogPlaceAmountIsZeroOutsideItsTimeAndRadius(GameTestHelper helper) {
		Vec3 c = new Vec3(0.0, 64.0, 0.0);
		FogPlace p = place(c, 40.0F, 0.5F, 1000L, 50_000L, 1200, 1200);
		helper.assertTrue(p.amount(c, Level.OVERWORLD, 999L) == 0.0 && p.amount(c, Level.OVERWORLD, 50_000L) == 0.0, "fog outside its time");
		helper.assertTrue(p.amount(c, Level.OVERWORLD, 1000L) == 0.0, "the ease in does not start at 0");
		helper.assertTrue(p.amount(c, Level.OVERWORLD, 10_000L) == 0.5, "not full at the center: " + p.amount(c, Level.OVERWORLD, 10_000L));
		helper.assertTrue(p.amount(c.add(0.0, 200.0, 0.0), Level.OVERWORLD, 10_000L) == 0.5, "height counted");
		helper.assertTrue(p.amount(c.add(40.0, 0.0, 0.0), Level.OVERWORLD, 10_000L) == 0.0 && p.amount(c.add(30.0, 0.0, 30.0), Level.OVERWORLD, 10_000L) == 0.0,
				"fog at or past the radius");
		helper.assertTrue(p.amount(c, Level.NETHER, 10_000L) == 0.0, "fog in another dimension");
		double inner = p.amount(c.add(10.0, 0.0, 0.0), Level.OVERWORLD, 10_000L);
		double outer = p.amount(c.add(30.0, 0.0, 0.0), Level.OVERWORLD, 10_000L);
		helper.assertTrue(inner > outer && outer > 0.0, "it does not thin toward the edge: " + inner + " " + outer);
		// Continuous: small steps in space and time give small changes, the edge and both ends included.
		double worst = 0.0;
		for (double d = 0.0; d <= 41.0; d += 0.05) {
			worst = Math.max(worst, Math.abs(p.amount(c.add(d + 0.05, 0.0, 0.0), Level.OVERWORLD, 10_000L) - p.amount(c.add(d, 0.0, 0.0), Level.OVERWORLD, 10_000L)));
		}
		for (double t = 900.0; t <= 50_100.0; t += 1.0) {
			worst = Math.max(worst, Math.abs(p.amount(c, Level.OVERWORLD, t + 1.0) - p.amount(c, Level.OVERWORLD, t)));
		}
		helper.assertTrue(worst < 0.01, "a pop of " + worst);
		// Ended early with its fade: the same fog at that tick, gone a fade later.
		FogPlace early = p.endedEarly(20_000L);
		helper.assertTrue(early.amount(c, Level.OVERWORLD, 20_000L) == p.amount(c, Level.OVERWORLD, 20_000L) && early.untilClock() == 21_200L
				&& early.amount(c, Level.OVERWORLD, 21_200L) == 0.0, "ending early popped or lasted: " + early);
		helper.assertTrue(p.endedEarly(49_500L).equals(p), "ending early made it last longer");
		helper.succeed();
	}

	/** Places combine as 1 - Π(1 - a); with none the weather and the fog end are bit for bit what they were. */
	@GameTest
	public void fogPlacesCombineAndNoneChangesNothing(GameTestHelper helper) {
		Vec3 at = new Vec3(5.0, 64.0, 0.0);
		FogPlace a = place(new Vec3(0.0, 64.0, 0.0), 40.0F, 0.45F, 0L, 100_000L, 0, 0);
		FogPlace b = place(new Vec3(20.0, 64.0, 0.0), 30.0F, 0.6F, 0L, 100_000L, 0, 0);
		double amountA = a.amount(at, Level.OVERWORLD, 500.0);
		double amountB = b.amount(at, Level.OVERWORLD, 500.0);
		double both = FogLimits.placeAmount(List.of(a, b), Level.OVERWORLD, at, 500.0);
		helper.assertTrue(Math.abs(both - (1.0 - (1.0 - amountA) * (1.0 - amountB))) < 1.0E-12 && both > Math.max(amountA, amountB), "two places: " + both);
		helper.assertTrue(FogLimits.placeAmount(List.of(a), Level.OVERWORLD, at, 500.0) == amountA, "one place is not itself");
		helper.assertTrue(FogLimits.placeAmount(List.of(), Level.OVERWORLD, at, 500.0) == 0.0
				&& FogLimits.placeAmount(List.of(a, b), Level.NETHER, at, 500.0) == 0.0, "fog from no place");
		for (double spell : new double[] {0.0, 0.123456789, 0.45, 0.8, 1.0}) {
			for (double sky : new double[] {0.0, 0.5, 1.0}) {
				double before = spell > 0.0 ? spell * sky : spell;
				helper.assertTrue(FogLimits.weatherAmount(spell, 0.0, sky) == before, "without places the weather changed: " + spell + " " + sky);
				FogLimits.Result old = FogLimits.of(12, 10, 0.3F, 14_000L, FogLimits.Shape.DEFAULT, before);
				FogLimits.Result now = FogLimits.of(12, 10, 0.3F, 14_000L, FogLimits.Shape.DEFAULT, FogLimits.weatherAmount(spell, 0.0, sky));
				helper.assertTrue(old.equals(now), "the fog end changed without places");
			}
		}
		helper.assertTrue(FogLimits.weatherAmount(0.0, 0.5, 0.0) == 0.0 && FogLimits.weatherAmount(0.0, 0.5, 1.0) == 0.5, "a place is not weather");
		helper.succeed();
	}

	/** A v1.3 state loads with no places, none are written without places, a bad list loads as none, places round-trip. */
	@GameTest
	public void aV13StateLoadsWithNoFogPlaces(GameTestHelper helper) {
		RegistryOps<Tag> ops = ops(helper);
		HerobrineState state = new HerobrineState();
		state.reroll(7L, 8L);
		state.setEffects(new HerobrineState.Effects(true, 0.4F));
		CompoundTag encoded = (CompoundTag) HerobrineState.CODEC.encodeStart(ops, state).getOrThrow();
		helper.assertFalse(encoded.getCompoundOrEmpty("effects").contains("fogPlaces"), "effects without places wrote fogPlaces");
		HerobrineState v13 = HerobrineState.CODEC.parse(ops, encoded).getOrThrow();
		helper.assertTrue(v13.effects().fogPlaces().isEmpty() && v13.effects().musicOff(), "a v1.3 state: " + v13.effects());

		FogPlace place = place(new Vec3(1.0, 2.0, 3.0), 40.0F, 0.45F, 100L, 30_000L, 1200, 1200);
		FogSpell spell = new FogSpell(3 * DAY + 11_500, 4 * DAY + 5_500, 0.55F, true);
		state.setEffects(state.effects().withFogSpell(Optional.of(spell)).withFogPlaces(List.of(place)));
		CompoundTag withPlaces = (CompoundTag) HerobrineState.CODEC.encodeStart(ops, state).getOrThrow();
		HerobrineState loaded = HerobrineState.CODEC.parse(ops, withPlaces).getOrThrow();
		helper.assertTrue(loaded.effects().equals(new HerobrineState.Effects(true, 0.4F, Optional.of(spell), List.of(place))), "round trip " + loaded.effects());
		helper.assertTrue(loaded.effects().withFogSpell(Optional.empty()).fogPlaces().equals(List.of(place)), "a new spell dropped the places");

		CompoundTag broken = withPlaces.copy();
		CompoundTag effects = broken.getCompoundOrEmpty("effects");
		ListTag bad = new ListTag();
		bad.add(StringTag.valueOf("not a place"));
		effects.put("fogPlaces", bad);
		broken.put("effects", effects);
		HerobrineState survived = HerobrineState.CODEC.parse(ops, broken).getOrThrow();
		helper.assertTrue(survived.effects().fogPlaces().isEmpty() && survived.effects().fogSpell().equals(Optional.of(spell)), "a bad list broke the state");

		List<FogPlace> many = new ArrayList<>();
		for (int i = 0; i < ClientEffects.FOG_PLACES_MAX + 3; i++) {
			many.add(place(new Vec3(i, 0, 0), 10.0F, 0.5F, i, 100_000L, 0, 0));
		}
		List<FogPlace> kept = state.effects().withFogPlaces(many).fogPlaces();
		helper.assertTrue(kept.size() == ClientEffects.FOG_PLACES_MAX && kept.getFirst().equals(many.get(3)), "not the newest 16 kept");
		helper.succeed();
	}

	/** The places payload and the join payload carry every place; the old Sync constructors carry none. */
	@GameTest
	public void fogPlacesTravelInTheirPayloadAndOnJoin(GameTestHelper helper) {
		FogPlace a = place(new Vec3(1.0, 2.0, 3.0), 40.0F, 0.45F, 100L, 30_000L, 1200, 1200);
		FogPlace b = new FogPlace(Level.OVERWORLD, new Vec3(-9.5, 70.0, 12.0), 25.0F, 0.6F, 2_000L, 90_000L, 0, 600);
		ClientEffects.FogPlaces payload = roundTrip(ClientEffects.FogPlaces.CODEC, new ClientEffects.FogPlaces(List.of(a, b)));
		helper.assertTrue(payload.places().equals(List.of(a, b)), "the places payload: " + payload);
		helper.assertTrue(roundTrip(ClientEffects.FogPlaces.CODEC, new ClientEffects.FogPlaces(List.of())).places().isEmpty(), "none");

		FogSpell spell = new FogSpell(11_500L, 23_500L, 0.5F, false);
		HerobrineState.Effects effects = new HerobrineState.Effects(true, 0.3F, Optional.of(spell), List.of(a, b));
		ClientEffects.Sync join = roundTrip(ClientEffects.Sync.CODEC, ClientEffects.syncPayload(effects));
		helper.assertTrue(join.equals(new ClientEffects.Sync(true, 0.3F, Optional.of(spell), List.of(a, b))), "the join payload: " + join);
		helper.assertTrue(new ClientEffects.Sync(true, 0.3F).fogPlaces().isEmpty() && new ClientEffects.Sync(true, 0.3F, Optional.of(spell)).fogPlaces().isEmpty(),
				"an old constructor carried places");
		helper.succeed();
	}

	/** The world's list: ended places go, the oldest ends early with its fade past the cap, nothing pops. Restores the state. */
	@GameTest
	public void addFogPlaceCapsTheWorldsPlacesWithoutAPop(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		HerobrineState.Effects before = state.effects();
		try {
			long now = server.overworld().getDefaultClockTime();
			FogPlace over = place(new Vec3(0, 64, 0), 40.0F, 0.45F, now - 5000, now - 10, 100, 100);
			state.setEffects(before.withFogPlaces(List.of(over)));
			helper.assertFalse(ClientEffects.addFogPlace(server, place(new Vec3(0, 64, 0), 40.0F, 0.45F, now - 500, now - 1, 100, 100), 4), "an ended place was added");
			helper.assertFalse(ClientEffects.addFogPlace(server, place(new Vec3(0, 64, 0), 0.0F, 0.45F, now, now + DAY, 100, 100), 4), "a place without fog was added");

			List<FogPlace> added = new ArrayList<>();
			for (int i = 0; i < 5; i++) {
				FogPlace p = place(new Vec3(100.0 * i, 64, 0), 40.0F, 0.45F, now - 2000 + i, now + 2 * DAY, 1200, 1200);
				added.add(p);
				helper.assertTrue(ClientEffects.addFogPlace(server, p, 4), "place " + i + " refused");
			}
			List<FogPlace> stored = state.effects().fogPlaces();
			helper.assertTrue(stored.size() == 5 && !stored.contains(over), "ended places are kept or new ones lost: " + stored.size());
			helper.assertTrue(stored.getFirst().untilClock() == now + 1200 && stored.getFirst().fadingBy(now), "the oldest did not end with its fade");
			for (int i = 1; i < 5; i++) {
				helper.assertTrue(stored.get(i).equals(added.get(i)), "a newer place was changed: " + i);
			}
			for (int i = 0; i < 5; i++) {
				Vec3 c = added.get(i).center();
				helper.assertTrue(stored.get(i).amount(c, Level.OVERWORLD, now) == added.get(i).amount(c, Level.OVERWORLD, now), "a pop at place " + i);
			}
			helper.assertTrue(ClientEffects.syncPayload(state.effects()).fogPlaces().equals(stored), "the join payload does not carry them");
			ClientEffects.clearFogPlaces(server);
			helper.assertTrue(state.effects().fogPlaces().isEmpty() && state.effects().fogSpell().equals(before.fogSpell()), "clear left places or took the spell");
		} finally {
			state.setEffects(before);
		}
		helper.succeed();
	}

	/** The server's fog for a player counts a place at the eyes, sky-weighted like the spell, and nothing else changes. */
	@GameTest
	public void fogLimitsCountsAPlaceAtThePlayersEyes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MinecraftServer server = level.getServer();
		HerobrineState state = HerobrineState.get(server);
		HerobrineState.Effects before = state.effects();
		ServerPlayer player = (ServerPlayer) helper.makeMockServerPlayer(GameType.SURVIVAL);
		player.snapTo(helper.absoluteVec(new Vec3(4.5, 2.0, 4.5)), 0.0F, 0.0F);
		try {
			long clock = level.getDefaultClockTime();
			state.setEffects(new HerobrineState.Effects(false, 0.0F));
			int view = server.getPlayerList().getViewDistance();
			FogLimits.Result clear = FogLimits.of(player);
			helper.assertTrue(clear.equals(FogLimits.of(player.requestedViewDistance(), view, 0.0F, clock, FogLimits.shape(), 0.0)), "no weather is not clear");

			FogPlace here = place(player.getEyePosition(), 40.0F, 0.6F, clock - 10_000, clock + DAY, 0, 0);
			state.setEffects(state.effects().withFogPlaces(List.of(here)));
			double sky = FogLimits.skyFactor(level.getBrightness(net.minecraft.world.level.LightLayer.SKY, BlockPos.containing(player.getEyePosition())));
			double placeAmount = FogLimits.placeAmount(List.of(here), level.dimension(), player.getEyePosition(), clock);
			FogLimits.Result fogged = FogLimits.of(player);
			helper.assertTrue(placeAmount == (double) 0.6F, "the place at the eyes is not full: " + placeAmount);
			helper.assertTrue(fogged.equals(FogLimits.of(player.requestedViewDistance(), view, 0.0F, clock, FogLimits.shape(), FogLimits.weatherAmount(0.0, placeAmount, sky))),
					"not the place's fog, sky-weighted");
			helper.assertTrue(sky <= 0.0 || fogged.fogEnd() < clear.fogEnd(), "a place under the open sky pulled nothing in");

			FogPlace away = place(player.getEyePosition().add(500.0, 0.0, 0.0), 40.0F, 0.6F, clock - 10_000, clock + DAY, 0, 0);
			state.setEffects(state.effects().withFogPlaces(List.of(away)));
			helper.assertTrue(FogLimits.of(player).equals(clear), "a place far away counted");
		} finally {
			state.setEffects(before);
		}
		helper.succeed();
	}

	// --- names ---

	/** NAME entries are stored apart, keep their place in the order, and a v1.3 reader (entries only) still reads the file. */
	@GameTest
	public void nameEntriesKeepTheirOrderAndOldReadersSkipThem(GameTestHelper helper) {
		RegistryOps<Tag> ops = ops(helper);
		ServerLevel level = helper.getLevel();
		GlobalPos at = GlobalPos.of(level.dimension(), helper.absolutePos(new BlockPos(1, 1, 1)));
		CompoundTag name = new CompoundTag();
		name.putString(TraceLedger.NAME_KEY, "Rex");
		TraceLedger.Entry remove = new TraceLedger.Entry(TraceLedger.Kind.REMOVE, "test:sc0/a", 1, at, Optional.empty(), Optional.of(Blocks.STONE.defaultBlockState()),
				Optional.empty(), Optional.empty(), -1, -1);
		TraceLedger.Entry named = new TraceLedger.Entry(TraceLedger.Kind.NAME, "test:sc0/b", 2, at, Optional.empty(), Optional.empty(), Optional.of(name),
				Optional.empty(), -1, -1, Optional.of(UUID.randomUUID()));
		TraceLedger.Entry move = new TraceLedger.Entry(TraceLedger.Kind.MOVE, "test:sc0/c", 3, at, Optional.of(at.pos().above()), Optional.of(Blocks.DIRT.defaultBlockState()),
				Optional.empty(), Optional.empty(), -1, -1);
		TraceLedger ledger = new TraceLedger();
		ledger.add(remove);
		ledger.add(named);
		ledger.add(move);
		CompoundTag saved = (CompoundTag) TraceLedger.CODEC.encodeStart(ops, ledger).getOrThrow();
		helper.assertTrue(saved.getListOrEmpty("entries").size() == 2 && saved.getListOrEmpty(TraceLedger.NAMES_KEY).size() == 1, "not stored apart: " + saved);
		helper.assertTrue(TraceLedger.CODEC.parse(ops, saved).getOrThrow().entries().equals(List.of(remove, named, move)), "the order was lost");

		Codec<List<TraceLedger.Entry>> v13 = TraceLedger.Entry.CODEC.listOf().fieldOf("entries").codec();
		helper.assertTrue(v13.parse(ops, saved).getOrThrow().equals(List.of(remove, move)), "a v1.3 reader cannot read it");
		CompoundTag old = saved.copy();
		old.remove(TraceLedger.NAMES_KEY);
		helper.assertTrue(TraceLedger.CODEC.parse(ops, old).getOrThrow().entries().equals(List.of(remove, move)), "an old traces.dat does not load");
		CompoundTag bad = saved.copy();
		bad.putString(TraceLedger.NAMES_KEY, "not a list");
		helper.assertTrue(TraceLedger.CODEC.parse(ops, bad).getOrThrow().entries().equals(List.of(remove, move)), "bad names broke the ledger");
		helper.succeed();
	}

	/** recordName ledgers the mob's name; the undo gives it back out of view, waits in view or unloaded, refuses once named. */
	@GameTest
	public void aTakenNameComesBackThroughTheUndo(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		TraceLedger ledger = TraceLedger.get(level.getServer());
		String cause = "test:sc0/name/" + helper.absolutePos(BlockPos.ZERO).toShortString();
		String undo = "test:sc0/name_undo";
		helper.assertFalse(new MobTamper.Stub().unname(helper.spawn(EntityTypes.COW, new BlockPos(6, 1, 6)), cause), "the stub took a name");

		Cow cow = helper.spawn(EntityTypes.COW, new BlockPos(2, 1, 2));
		Component bessie = Component.literal("Bessie");
		cow.setCustomName(bessie);
		cow.setCustomNameVisible(true);
		Listen.traced(cause);
		TraceLedger.Entry entry = TraceLedger.recordName(level, cow, bessie, cause);
		helper.assertTrue(entry != null && entry.kind() == TraceLedger.Kind.NAME && entry.entity().equals(Optional.of(cow.getUUID()))
				&& entry.pos().pos().equals(cow.blockPosition()) && TraceLedger.storedName(level, entry).equals(Optional.of(bessie)), "the entry: " + entry);
		List<SeenTraced> heard = Listen.traced(cause);
		helper.assertTrue(heard.size() == 1 && heard.getFirst().entry() == entry && !heard.getFirst().placed(), "recordName was not heard once: " + heard);
		cow.setCustomName(null);
		cow.setCustomNameVisible(false);

		// In view: waits.
		Player watcher = helper.makeMockServerPlayer(GameType.SURVIVAL);
		watcher.snapTo(cow.position().add(1.5, 0.0, 0.0), 90.0F, 0.0F);
		TraceService watched = Services.traces().watchedBy(List.of(TraceService.Viewer.of(watcher, 8)));
		helper.assertTrue(watched.undo(level, entry, undo) == TraceService.UndoResult.IN_VIEW && !cow.hasCustomName(), "given back in view");
		// Out of view: back, shown as before, the entry closed.
		helper.assertTrue(Services.traces().forced().undo(level, entry, undo) == TraceService.UndoResult.DONE, "not given back");
		helper.assertTrue(bessie.equals(cow.getCustomName()) && cow.isCustomNameVisible() && !ledger.entries().contains(entry), "the name or its showing is not back");
		helper.assertTrue(Services.traces().forced().undo(level, entry, undo) == TraceService.UndoResult.BLOCKED, "undone twice");

		// Named since: theirs now.
		TraceLedger.Entry again = TraceLedger.recordName(level, cow, bessie, cause + "/again");
		cow.setCustomName(Component.literal("Daisy"));
		helper.assertTrue(Services.traces().forced().undo(level, again, undo) == TraceService.UndoResult.BLOCKED
				&& cow.getCustomName().getString().equals("Daisy") && ledger.entries().contains(again), "a new name was replaced");

		// Not in the level: waits while its chunk's entities are not loaded (nothing loaded), refused once they are.
		CompoundTag data = new CompoundTag();
		data.putString(TraceLedger.NAME_KEY, "Rex");
		TraceLedger.Entry far = ledger.add(new TraceLedger.Entry(TraceLedger.Kind.NAME, cause + "/far", 0, GlobalPos.of(level.dimension(), new BlockPos(1_000_000, 64, 1_000_000)),
				Optional.empty(), Optional.empty(), Optional.of(data), Optional.empty(), -1, -1, Optional.of(UUID.randomUUID())));
		TraceLedger.Entry gone = ledger.add(new TraceLedger.Entry(TraceLedger.Kind.NAME, cause + "/gone", 0, GlobalPos.of(level.dimension(), cow.blockPosition()),
				Optional.empty(), Optional.empty(), Optional.of(data), Optional.empty(), -1, -1, Optional.of(UUID.randomUUID())));
		helper.assertTrue(Services.traces().forced().undo(level, far, undo) == TraceService.UndoResult.UNLOADED
				&& level.getChunkSource().getChunkNow(1_000_000 >> 4, 1_000_000 >> 4) == null, "not UNLOADED, or a chunk was loaded");
		helper.assertTrue(Services.traces().forced().undo(level, gone, undo) == TraceService.UndoResult.BLOCKED, "a mob that is gone kept the undo waiting");
		ledger.remove(far);
		ledger.remove(gone);
		ledger.remove(again);
		helper.succeed();
	}

	// --- sounds ---

	/** The step's sounds: a cover block in the two step-sound tags wins (snow, leaf litter, a carpet), else the floor's. */
	@GameTest
	public void stepTypeFollowsTheCoverRule(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		floor(helper);
		helper.setBlock(1, 1, 1, Blocks.GRASS_BLOCK);
		helper.setBlock(1, 2, 1, Blocks.SNOW);
		helper.setBlock(3, 1, 1, Blocks.GRASS_BLOCK);
		helper.setBlock(3, 2, 1, Blocks.LEAF_LITTER);
		helper.setBlock(5, 1, 1, Blocks.STONE);
		helper.setBlock(5, 2, 1, Blocks.CARPET.white());
		helper.setBlock(1, 1, 4, Blocks.GRASS_BLOCK);
		helper.setBlock(3, 1, 4, Blocks.STONE);
		helper.setBlock(3, 2, 4, Blocks.TORCH);
		helper.assertTrue(SoundCues.stepType(level, helper.absolutePos(new BlockPos(1, 1, 1))) == Blocks.SNOW.defaultBlockState().getSoundType(), "snow cover");
		helper.assertTrue(SoundCues.stepType(level, helper.absolutePos(new BlockPos(3, 1, 1))) == SoundType.LEAF_LITTER, "leaf litter");
		helper.assertTrue(SoundCues.stepType(level, helper.absolutePos(new BlockPos(5, 1, 1))) == SoundType.WOOL, "a carpet");
		helper.assertTrue(SoundCues.stepType(level, helper.absolutePos(new BlockPos(1, 1, 4))) == SoundType.GRASS, "bare grass");
		helper.assertTrue(SoundCues.stepType(level, helper.absolutePos(new BlockPos(3, 1, 4))) == SoundType.STONE, "a torch is not cover");
		helper.succeed();
	}

	/** Alone: a spectator or a dead friend within the radius counts, a sharer counts, someone past the radius does not. */
	@GameTest
	public void aloneCountsASpectatorWithinTheRadius(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		MultiplayerCoreTests.Roster roster = new MultiplayerCoreTests.Roster();
		Vec3 base = helper.absoluteVec(new Vec3(4.5, 1.0, 4.5));
		MultiplayerCoreTests.TestPlayer me = new MultiplayerCoreTests.TestPlayer(level, UUID.randomUUID(), "sc0_me", GameType.SURVIVAL);
		me.snapTo(base, 0.0F, 0.0F);
		MultiplayerCoreTests.TestPlayer watcher = new MultiplayerCoreTests.TestPlayer(level, UUID.randomUUID(), "sc0_watcher", GameType.SPECTATOR);
		watcher.snapTo(base.add(10.0, 0.0, 0.0), 0.0F, 0.0F);
		roster.join(me);
		roster.join(watcher);
		helper.assertFalse(SoundCues.alone(me, 48.0, roster.watch, roster.online), "a spectator 10 blocks off was not counted");
		watcher.snapTo(base.add(60.0, 0.0, 0.0), 0.0F, 0.0F);
		helper.assertTrue(SoundCues.alone(me, 48.0, roster.watch, roster.online), "a spectator 60 blocks off was counted");
		helper.assertFalse(SoundCues.alone(me, 64.0, roster.watch, roster.online), "the radius is not the caller's");
		MultiplayerCoreTests.TestPlayer friend = new MultiplayerCoreTests.TestPlayer(level, UUID.randomUUID(), "sc0_friend", GameType.SURVIVAL);
		friend.snapTo(base.add(0.0, 0.0, 20.0), 0.0F, 0.0F);
		roster.join(friend);
		helper.assertFalse(SoundCues.alone(me, 8.0, roster.watch, List.of(me)), "a sharer was not counted");
		helper.succeed();
	}

	// --- the switch and the file rule ---

	/** The switch defaults on at version 1 and reads off from the file; reverts run in order and one failing stops none. */
	@GameTest
	public void theSwitchDefaultsOnAndRevertsRunIsolated(GameTestHelper helper) {
		SelfCreated.Config fresh = ModConfig.readSection(new JsonObject(), SelfCreated.SECTION, SelfCreated.Config.class, SelfCreated.Config::new);
		helper.assertTrue(fresh.enabled && fresh.version == SelfCreated.Config.CURRENT, "fresh: on at version 1");
		JsonObject sections = JsonParser.parseString("{\"sc\": {\"enabled\": false}}").getAsJsonObject();
		SelfCreated.Config off = ModConfig.readSection(sections, SelfCreated.SECTION, SelfCreated.Config.class, SelfCreated.Config::new);
		helper.assertTrue(!off.enabled && off.version == SelfCreated.Config.CURRENT, "switched off in the file");
		helper.assertTrue(SelfCreated.enabled(), "the test run has the layer off");

		List<String> ran = new ArrayList<>();
		List<Map.Entry<String, Consumer<MinecraftServer>>> reverts = List.of(
				Map.entry("a", server -> ran.add("a")),
				Map.entry("b", server -> {
					throw new IllegalStateException("a revert that fails (on purpose)");
				}),
				Map.entry("c", server -> ran.add("c")));
		List<String> done = SelfCreated.run(helper.getLevel().getServer(), reverts);
		helper.assertTrue(ran.equals(List.of("a", "c")) && done.equals(List.of("a", "c")), "reverts: ran " + ran + ", done " + done);
		helper.assertTrue(SelfCreated.registered().stream().anyMatch(e -> e.getKey().equals("core")), "core's own revert is not registered");
		helper.succeed();
	}

	/** Files only inside the world's folder, written whole; everything that leaves it is refused. */
	@GameTest
	public void filesAreWrittenOnlyInsideTheWorldsFolder(GameTestHelper helper) throws Exception {
		Path world = SelfCreated.worldFolder(helper.getLevel().getServer());
		for (String bad : new String[] {"", " ", "../outside.bin", "data/../../outside.bin", "/tmp/outside.bin", ".", "data/.."}) {
			helper.assertTrue(SelfCreated.inWorld(world, bad).isEmpty(), "'" + bad + "' was allowed");
			helper.assertFalse(SelfCreated.writeInWorld(world, bad, new byte[] {1}), "'" + bad + "' was written");
		}
		String name = "data/a1016_02/sc0_test_" + helper.absolutePos(BlockPos.ZERO).toShortString().replace(", ", "_") + ".bin";
		Path file = SelfCreated.inWorld(world, name).orElseThrow();
		helper.assertTrue(file.startsWith(world), "not inside the world");
		try {
			helper.assertTrue(SelfCreated.writeInWorld(world, name, new byte[] {1, 2, 3}), "the first write failed");
			helper.assertTrue(SelfCreated.writeInWorld(world, name, new byte[] {4, 5}), "the overwrite failed");
			helper.assertTrue(java.util.Arrays.equals(Files.readAllBytes(file), new byte[] {4, 5}), "not written whole");
			try (var listing = Files.list(file.getParent())) {
				helper.assertTrue(listing.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "a temporary file was left");
			}
		} finally {
			Files.deleteIfExists(file);
		}
		helper.succeed();
	}

	/** The music, dusk fog and fog spell setters keep the world's fog places. Restores the state. */
	@GameTest
	public void theOtherEffectSettersKeepThePlaces(GameTestHelper helper) {
		MinecraftServer server = helper.getLevel().getServer();
		HerobrineState state = HerobrineState.get(server);
		HerobrineState.Effects before = state.effects();
		try {
			FogPlace p = place(new Vec3(0, 64, 0), 40.0F, 0.45F, 0L, Long.MAX_VALUE / 2, 0, 0);
			state.setEffects(new HerobrineState.Effects(true, 0.3F).withFogPlaces(List.of(p)));
			ClientEffects.setDuskFog(server, 0.6F);
			ClientEffects.setMusicOff(server, false);
			ClientEffects.setFogSpell(server, Optional.of(new FogSpell(11_500L, 23_500L, 0.5F, false)));
			ClientEffects.setFogSpell(server, Optional.empty());
			helper.assertTrue(state.effects().fogPlaces().equals(List.of(p)) && state.effects().duskFogLevel() == 0.6F && !state.effects().musicOff(),
					"a setter dropped the places: " + state.effects());
		} finally {
			state.setEffects(before);
		}
		helper.succeed();
	}
}
