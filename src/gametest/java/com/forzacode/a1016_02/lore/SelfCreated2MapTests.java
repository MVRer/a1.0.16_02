package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.forzacode.a1016_02.core.CardRegistry;
import com.forzacode.a1016_02.core.CardTag;
import com.forzacode.a1016_02.core.EventCard;
import com.forzacode.a1016_02.core.FireResult;
import com.forzacode.a1016_02.core.Habit;
import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.PlayerWatch;
import com.forzacode.a1016_02.core.Remains;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.Tier;
import com.forzacode.a1016_02.lore.sc2.s30.KnownPlaces;
import com.forzacode.a1016_02.lore.sc2.s30.MapPixels;
import com.forzacode.a1016_02.lore.sc2.s30.Maps;
import com.forzacode.a1016_02.lore.sc2.s30.S30Data;
import com.forzacode.a1016_02.lore.sc2.s30.Sc30Config;
import com.mojang.authlib.GameProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

/**
 * SC2-7, feature 30 (docs/SELF_CREATED_2.md): the square round the base blank on a carried map at every scale, kept and
 * written back by the switch where vanilla has not drawn since; vanilla drawing it back as its holder walks home, and
 * 30's own colours equal to vanilla's; never a map in anyone's hand, table or frame near them; a copy shares the change
 * and another map of the same land does not; a late joiner gets the changed map; the refusals; the second form (a place
 * never visited drawn in from the world, waiting for chunks it never loads); the world's ends read right; the data. Every
 * test has its own patch of the overworld far out (x from 9 600 000, z -21 000 000), mock players in a private list, its
 * own data, numbers and stage.
 */
public class SelfCreated2MapTests {
	/** A player only a test's own list holds online. */
	static final class Mock extends ServerPlayer {
		GameType mode = GameType.SURVIVAL;

		Mock(ServerLevel level, String name) {
			super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
			GameType.SURVIVAL.updatePlayerAbilities(getAbilities());
		}

		@Override
		public GameType gameMode() {
			return mode;
		}

		@Override
		public boolean isClientAuthoritative() {
			return false;
		}
	}

	/** A test's world: a patch far out, its players, their bases, the stage, its own data and numbers. */
	static final class Land implements PlayerWatch.Source {
		private static final AtomicInteger SLOTS = new AtomicInteger();
		static final BlockPos BASE = new BlockPos(9_600_000, 0, -21_000_064);
		static final int SPACING = 4096;

		final GameTestHelper helper;
		final ServerLevel level;
		final BlockPos origin;
		final List<ServerPlayer> online = new ArrayList<>();
		final HerobrineState state = new HerobrineState();
		final PlayerWatch watch = new PlayerWatch(this);
		final S30Data data = new S30Data();
		final Sc30Config config = new Sc30Config();
		Stage stage = Stage.PROXIMITY;
		Remains remains = new KnownPlaces(List.of());
		final java.util.Map<UUID, BlockPos> bases = new java.util.HashMap<>();
		private final List<int[]> forced = new ArrayList<>();

		Land(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel().getServer().overworld();
			BlockPos column = BASE.offset(SLOTS.getAndIncrement() * SPACING, 0, 0);
			load(column, 1);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
			origin = new BlockPos(column.getX(), ground, column.getZ());
		}

		/** Loads (and holds) every chunk within {@code chunks} chunks of {@code at}. */
		void load(BlockPos at, int chunks) {
			for (int cx = (at.getX() >> 4) - chunks; cx <= (at.getX() >> 4) + chunks; cx++) {
				for (int cz = (at.getZ() >> 4) - chunks; cz <= (at.getZ() >> 4) + chunks; cz++) {
					level.setChunkForced(cx, cz, true);
					level.getChunk(cx, cz);
					forced.add(new int[] {cx, cz});
				}
			}
		}

		void release() {
			for (int[] c : forced) {
				level.setChunkForced(c[0], c[1], false);
			}
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

		Maps.Env env() {
			return new Maps.Env(level.getServer(), watch, s -> List.copyOf(online),
					p -> Optional.ofNullable(bases.get(p.getUUID())).map(b -> GlobalPos.of(Level.OVERWORLD, b)), () -> stage, remains, data, config,
					p -> Long.MAX_VALUE);
		}

		/** A player at {@code (dx, dz)} from the origin, their base at {@code base}. */
		Mock player(String name, int dx, int dz, BlockPos base) {
			Mock p = new Mock(level, name);
			p.snapTo(Vec3.atBottomCenterOf(origin.offset(dx, 0, dz)), 0.0F, 0.0F);
			online.add(p);
			state.addKnownPlayer(p.getUUID(), name);
			bases.put(p.getUUID(), base);
			return p;
		}

		/** A map of the overworld at this scale whose area holds {@code at}, every pixel drawn (grass), in no hand. */
		ItemStack map(BlockPos at, int scale) {
			ItemStack stack = MapItem.create(level, at.getX(), at.getZ(), (byte) scale, true, false);
			MapItemSavedData data = data(stack);
			byte grass = MapColor.GRASS.getPackedId(MapColor.Brightness.NORMAL);
			for (int i = 0; i < data.colors.length; i++) {
				data.colors[i] = grass;
			}
			return stack;
		}

		MapItemSavedData data(ItemStack stack) {
			return level.getMapData(stack.get(DataComponents.MAP_ID));
		}
	}

	private static int blankCount(byte[] colors) {
		int n = 0;
		for (byte b : colors) {
			if (b == 0) {
				n++;
			}
		}
		return n;
	}

	/** The card is the design's row, registered; the numbers are the plan's; the pixel arithmetic round-trips at each scale. */
	@GameTest
	public void theCardItsNumbersAndThePixels(GameTestHelper helper) {
		EventCard card = CardRegistry.get(Maps.ID).orElseThrow();
		helper.assertTrue(card.tier() == Tier.MINOR && card.earliestStage() == Stage.PROXIMITY && card.tags().equals(Set.of(CardTag.ITEM, CardTag.UNSHARED))
				&& card.habits().equals(Set.of(Habit.STRIPPER, Habit.COLLECTOR)) && !card.hasFake(), "sc_30 is not the plan's row");
		Sc30Config c = new Sc30Config();
		helper.assertTrue(c.sizeMin == 9 && c.sizeMax == 15 && c.awayDistance == 64 && c.showFromStage == 3 && c.maxPerWorld == 3, "the plan's numbers");
		for (int scale = 0; scale <= 4; scale++) {
			MapItemSavedData data = MapItemSavedData.createFresh(-1234.0, 5678.0, (byte) scale, true, false, Level.OVERWORLD);
			int s = 1 << scale;
			for (int px : new int[] {0, 1, 63, 64, 127}) {
				int x = MapPixels.blockX(data, px);
				helper.assertTrue(MapPixels.pixelX(data, x) == px && MapPixels.pixelX(data, x + s - 1) == px && MapPixels.pixelX(data, x + s) == px + 1,
						"scale " + scale + " pixel " + px);
				int z = MapPixels.blockZ(data, px);
				helper.assertTrue(MapPixels.pixelY(data, z) == px && MapPixels.pixelY(data, z + s - 1) == px, "scale " + scale + " row " + px);
			}
			helper.assertTrue(MapPixels.holds(data, new BlockPos(-1234, 0, 5678)), "scale " + scale + ": its own origin is off the map");
		}
		helper.succeed();
	}

	/**
	 * At every scale, with the target 64+ from home and the map in their pack: a square of 9 to 15 pixels round the base
	 * goes blank with vanilla's broken edge one pixel round it, nothing else changes, every pixel is kept, counted once.
	 */
	@GameTest(maxTicks = 40)
	public void theSquareRoundTheBaseGoesBlankAtEveryScale(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			land.config.maxPerWorld = 5;
			for (int scale = 0; scale <= 4; scale++) {
				land.online.clear();
				BlockPos base = land.origin.offset(scale * 7, 0, -scale * 5);
				Mock target = land.player("ana" + scale, 100, 10, base);
				ItemStack stack = land.map(base, scale);
				target.getInventory().setItem(9, stack);
				MapItemSavedData data = land.data(stack);
				byte[] before = data.colors.clone();
				Maps.Outcome outcome = Maps.fire(land.env(), target, false, RandomSource.create(scale));
				helper.assertTrue(outcome.result() == FireResult.FIRED && outcome.form() == S30Data.BLANK, "scale " + scale + ": " + outcome.reason());
				int px = MapPixels.pixelX(data, base.getX());
				int py = MapPixels.pixelY(data, base.getZ());
				int half = -1;
				for (int r = 0; r <= 8; r++) {
					if (data.colors[MapPixels.index(px + r, py)] == 0 && data.colors[MapPixels.index(px - r, py)] == 0) {
						half = r;
					}
				}
				helper.assertTrue(half >= 4 && half <= 8, "scale " + scale + ": the square's half width " + half);
				for (int dy = -12; dy <= 12; dy++) {
					for (int dx = -12; dx <= 12; dx++) {
						int x = px + dx;
						int y = py + dy;
						if (!MapPixels.inside(x, y)) {
							continue;
						}
						boolean inner = Math.abs(dx) <= 3 && Math.abs(dy) <= 3;
						boolean outside = Math.abs(dx) > 9 || Math.abs(dy) > 9;
						byte now = data.colors[MapPixels.index(x, y)];
						helper.assertTrue(!inner || now == 0, "scale " + scale + ": a pixel by the base still drawn");
						helper.assertTrue(!outside || now == before[MapPixels.index(x, y)], "scale " + scale + ": a pixel far off changed");
					}
				}
				int blanks = blankCount(data.colors);
				helper.assertTrue(blanks >= 81 && blanks <= 15 * 15 + 64, "scale " + scale + ": " + blanks + " blank");
				S30Data.Edit edit = land.data.edits().getLast();
				helper.assertTrue(edit.size() == blanks && edit.form == S30Data.BLANK, "scale " + scale + ": not every pixel kept");
				helper.assertTrue(Maps.refusal(land.env(), target, true).isPresent(), "scale " + scale + ": the same map twice");
			}
			helper.assertTrue(land.data.count() == 5, "not counted once each");
			land.config.maxPerWorld = 3;
			helper.assertTrue(Maps.refusal(land.env(), land.player("late", 100, 10, land.origin), false).orElse("").contains("at most 3"), "past the cap");
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/**
	 * R12-40: a drawn place's pixel holds vanilla's own colour, so the switch takes it back to blank only where nobody has
	 * been since it was drawn; where someone went, the holder may have drawn it for real, and it stays.
	 */
	@GameTest(maxTicks = 40)
	public void theSwitchKeepsADrawnPixelWhereSomeoneHasBeenSince(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			ItemStack stack = land.map(land.origin, 1);
			MapItemSavedData data = land.data(stack);
			MapId id = stack.get(DataComponents.MAP_ID);
			helper.assertTrue(id != null, "setup: no map id");
			byte colour = MapColor.STONE.getPackedId(MapColor.Brightness.NORMAL);
			S30Data.Edit edit = land.data.edit(id.id(), S30Data.DRAWN, com.forzacode.a1016_02.core.GameClock.day(land.level.getServer()));
			int unvisited = MapPixels.index(10, 10);
			int visited = MapPixels.index(100, 100);
			for (int index : new int[] {unvisited, visited}) {
				data.colors[index] = colour;
				land.data.keep(edit, index, (byte) 0, colour);
			}
			int bx = MapPixels.blockX(data, 100);
			int bz = MapPixels.blockZ(data, 100);
			land.watch.markVisit(land.level, new net.minecraft.world.level.ChunkPos(bx >> 4, bz >> 4));
			Maps.restore(land.env(), land.data);
			helper.assertTrue(data.colors[unvisited] == 0, "a drawn pixel nobody went to stayed");
			helper.assertTrue(data.colors[visited] == colour, "a pixel where someone went since was blanked");
			helper.assertTrue(land.data.edits().isEmpty(), "the record stayed");
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/** The switch: each pixel still blank gets what it held; one vanilla drew since keeps vanilla's; the record goes. */
	@GameTest(maxTicks = 40)
	public void theSwitchWritesBackWhatVanillaHasNotDrawn(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			BlockPos base = land.origin;
			Mock target = land.player("ana", 70, 0, base);
			ItemStack stack = land.map(base, 1);
			MapItemSavedData data = land.data(stack);
			for (int i = 0; i < data.colors.length; i++) {
				data.colors[i] = (byte) (MapColor.STONE.getPackedId(MapColor.Brightness.NORMAL) + (i % 3));
			}
			byte[] before = data.colors.clone();
			target.getInventory().setItem(20, stack);
			helper.assertTrue(Maps.fire(land.env(), target, false, RandomSource.create(1)).result() == FireResult.FIRED, "no change");
			int px = MapPixels.pixelX(data, base.getX());
			int py = MapPixels.pixelY(data, base.getZ());
			byte vanilla = MapColor.WATER.getPackedId(MapColor.Brightness.HIGH);
			data.setColor(px, py, vanilla);
			// In the target's hand: it waits (never changed in sight), kept for a later try.
			target.setItemInHand(InteractionHand.MAIN_HAND, stack);
			helper.assertTrue(Maps.restore(land.env(), land.data) == 0 && land.data.edits().size() == 1, "given back in sight");
			target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			int back = Maps.restore(land.env(), land.data);
			helper.assertTrue(data.colors[MapPixels.index(px, py)] == vanilla, "vanilla's redraw was overwritten");
			for (int i = 0; i < data.colors.length; i++) {
				if (i != MapPixels.index(px, py)) {
					helper.assertTrue(data.colors[i] == before[i], "pixel " + i + " not written back");
				}
			}
			helper.assertTrue(back > 60 && land.data.edits().isEmpty() && land.data.pending().isEmpty(), "the record stayed: " + back);
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/**
	 * Vanilla's own drawing (the map in its holder's hand at home) draws the square back, pixel for pixel what it was, and
	 * 30's colours ({@code MapPixels.colour}) equal vanilla's for every pixel vanilla drew.
	 */
	@GameTest(maxTicks = 40)
	public void vanillaDrawsItBackAsItsHolderWalksHomeAndThe30sColoursAreVanillas(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			land.load(land.origin, 6);
			ServerLevel level = land.level;
			BlockPos base = land.origin;
			// Something to draw: a little cabin, a pool, a path.
			for (int dx = -3; dx <= 3; dx++) {
				for (int dz = -3; dz <= 3; dz++) {
					level.setBlock(base.offset(dx, 3, dz), Blocks.SPRUCE_PLANKS.defaultBlockState(), 2);
					level.setBlock(base.offset(dx + 12, -1, dz), Blocks.WATER.defaultBlockState(), 2);
					level.setBlock(base.offset(dx, -1, dz + 10), Blocks.DIRT_PATH.defaultBlockState(), 2);
				}
			}
			Mock target = land.player("ana", 0, 0, base);
			ItemStack stack = MapItem.create(level, base.getX(), base.getZ(), (byte) 0, true, false);
			MapItemSavedData data = land.data(stack);
			MapItem item = (MapItem) Items.FILLED_MAP;
			for (int i = 0; i < 40; i++) {
				item.update(level, target, data);
			}
			byte[] drawn = data.colors.clone();
			int checked = 0;
			for (int y = 0; y < MapPixels.SIZE; y++) {
				for (int x = 0; x < MapPixels.SIZE; x++) {
					byte vanilla = drawn[MapPixels.index(x, y)];
					if (vanilla == 0) {
						continue;
					}
					Byte ours = MapPixels.colour(level, data, x, y);
					helper.assertTrue(ours != null && ours == vanilla, "pixel " + x + " " + y + ": 30's " + ours + ", vanilla's " + vanilla);
					checked++;
				}
			}
			helper.assertTrue(checked > 4000, "vanilla drew only " + checked);
			// Away, the map in the pack: the square goes. Home again with it in hand: vanilla draws it back.
			target.snapTo(Vec3.atBottomCenterOf(base.offset(80, 0, 0)), 0, 0);
			target.getInventory().setItem(9, stack);
			helper.assertTrue(Maps.fire(land.env(), target, false, RandomSource.create(2)).result() == FireResult.FIRED, "no change");
			helper.assertTrue(blankCount(data.colors) > blankCount(drawn) + 60, "nothing went blank");
			target.snapTo(Vec3.atBottomCenterOf(base), 0, 0);
			for (int i = 0; i < 40; i++) {
				item.update(level, target, data);
			}
			int px = MapPixels.pixelX(data, base.getX());
			int py = MapPixels.pixelY(data, base.getZ());
			for (int dy = -8; dy <= 8; dy++) {
				for (int dx = -8; dx <= 8; dx++) {
					int i = MapPixels.index(px + dx, py + dy);
					helper.assertTrue(data.colors[i] == drawn[i], "vanilla did not draw " + dx + " " + dy + " back");
				}
			}
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/**
	 * Never in sight: the map in the target's main hand or off hand, a copy in a friend's hand, in a friend's cartography
	 * table, or in a frame within 64 of anyone; a frame 80 off is fine.
	 */
	@GameTest(maxTicks = 40)
	public void neverAMapInAHandATableOrAFrameNearAnyone(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			land.load(land.origin, 6);
			BlockPos base = land.origin;
			Mock target = land.player("ana", 70, 0, base);
			Mock friend = land.player("ben", -40, 0, land.origin.offset(500, 0, 0));
			ItemStack stack = land.map(base, 0);
			Maps.Env env = land.env();
			target.setItemInHand(InteractionHand.MAIN_HAND, stack);
			helper.assertTrue(Maps.refusal(env, target, false).orElse("").contains("in sight"), "in the hand: " + Maps.refusal(env, target, false));
			target.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			target.setItemInHand(InteractionHand.OFF_HAND, stack);
			helper.assertTrue(Maps.refusal(env, target, false).isPresent(), "in the off hand");
			target.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
			target.getInventory().setItem(9, stack);
			helper.assertTrue(Maps.refusal(env, target, false).isEmpty(), "in the pack: " + Maps.refusal(env, target, false));
			ItemStack copy = stack.copy();
			friend.setItemInHand(InteractionHand.MAIN_HAND, copy);
			helper.assertTrue(Maps.refusal(env, target, false).isPresent(), "a copy in a friend's hand");
			friend.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			CartographyTableMenu table = new CartographyTableMenu(7, friend.getInventory());
			table.getSlot(0).set(copy);
			friend.containerMenu = table;
			helper.assertTrue(Maps.refusal(env, target, false).isPresent(), "a copy in a friend's table");
			table.getSlot(0).set(ItemStack.EMPTY);
			friend.containerMenu = friend.inventoryMenu;
			ItemFrame frame = new ItemFrame(land.level, friend.blockPosition().offset(0, 1, 3), Direction.SOUTH);
			frame.setItem(copy.copy(), false);
			land.level.addFreshEntity(frame);
			helper.assertTrue(Maps.refusal(env, target, false).isPresent(), "a copy in a frame by a friend");
			frame.discard();
			ItemFrame far = new ItemFrame(land.level, friend.blockPosition().offset(-80, 1, 0), Direction.SOUTH);
			far.setItem(copy.copy(), false);
			land.level.addFreshEntity(far);
			helper.assertTrue(Maps.refusal(env, target, false).isEmpty(), "a frame 80 off: " + Maps.refusal(env, target, false));
			far.discard();
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/**
	 * Multiplayer: a copy of the map (the same id) in a friend's pack shows the same blank; the friend's own map of the
	 * same land (another id) does not; a player who joins later and is sent the map gets it blank (vanilla's first packet).
	 */
	@GameTest(maxTicks = 40)
	public void aCopySharesTheChangeAnotherMapDoesNotAndALateJoinerSeesIt(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			BlockPos base = land.origin;
			Mock target = land.player("ana", 80, 0, base);
			Mock friend = land.player("ben", 90, 0, land.origin.offset(600, 0, 0));
			ItemStack mine = land.map(base, 0);
			ItemStack copy = mine.copy();
			ItemStack theirs = land.map(base, 0);
			target.getInventory().setItem(9, mine);
			friend.getInventory().setItem(9, copy);
			friend.getInventory().setItem(10, theirs);
			byte[] theirsBefore = land.data(theirs).colors.clone();
			helper.assertTrue(Maps.fire(land.env(), target, false, RandomSource.create(3)).result() == FireResult.FIRED, "no change");
			int px = MapPixels.pixelX(land.data(mine), base.getX());
			int py = MapPixels.pixelY(land.data(mine), base.getZ());
			helper.assertTrue(land.data(copy).colors[MapPixels.index(px, py)] == 0, "the copy does not show it");
			helper.assertTrue(java.util.Arrays.equals(land.data(theirs).colors, theirsBefore), "the friend's own map changed");
			// The friend has no map of their own home: nothing fires for them (UNSHARED: only the target's map).
			helper.assertTrue(Maps.refusal(land.env(), friend, false).isPresent(), "the friend's map changes too");
			// A late joiner carrying a copy: vanilla's first update for them carries the whole map, blank square and all.
			Mock late = new Mock(land.level, "cai");
			late.getInventory().setItem(3, mine.copy());
			MapItemSavedData server = land.data(mine);
			server.tickCarriedBy(late, mine, null);
			Packet<?> packet = server.getUpdatePacket(mine.get(DataComponents.MAP_ID), late);
			helper.assertTrue(packet instanceof ClientboundMapItemDataPacket, "nothing sent to a late joiner");
			MapItemSavedData client = MapItemSavedData.createForClient(server.scale, server.locked, server.dimension);
			((ClientboundMapItemDataPacket) packet).applyToMap(client);
			helper.assertTrue(java.util.Arrays.equals(client.colors, server.colors), "the late joiner's map is not the server's");
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/**
	 * The refusals: a locked map, a map of the Nether, a map that does not hold the base, the base not drawn on it, the
	 * target within 64 of home (forced skips it), in the Nether, in combat, no base; the cap (forced skips it).
	 */
	@GameTest(maxTicks = 40)
	public void itRefusesLockedOtherDimensionsHomeAndTheCap(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			BlockPos base = land.origin;
			Mock target = land.player("ana", 70, 0, base);
			Maps.Env env = land.env();
			helper.assertTrue(Maps.refusal(env, target, false).orElse("").contains("no filled map"), "no map");
			ItemStack stack = land.map(base, 0);
			MapItemSavedData data = land.data(stack);
			MapId lockedId = land.level.getFreeMapId();
			land.level.setMapData(lockedId, data.locked());
			ItemStack locked = stack.copy();
			locked.set(DataComponents.MAP_ID, lockedId);
			target.getInventory().setItem(9, locked);
			helper.assertTrue(Maps.refusal(env, target, false).isPresent(), "a locked map");
			ItemStack nether = MapItem.create(land.level.getServer().getLevel(Level.NETHER), base.getX(), base.getZ(), (byte) 0, true, false);
			target.getInventory().setItem(9, nether);
			helper.assertTrue(Maps.refusal(env, target, false).isPresent(), "a map of the Nether");
			ItemStack elsewhere = land.map(base.offset(5000, 0, 0), 0);
			target.getInventory().setItem(9, elsewhere);
			helper.assertTrue(Maps.refusal(env, target, false).isPresent(), "a map without the base");
			ItemStack undrawn = land.map(base, 0);
			java.util.Arrays.fill(land.data(undrawn).colors, (byte) 0);
			target.getInventory().setItem(9, undrawn);
			helper.assertTrue(Maps.refusal(env, target, false).isPresent(), "the base not drawn");
			target.getInventory().setItem(9, stack);
			helper.assertTrue(Maps.refusal(env, target, false).isEmpty(), "fits: " + Maps.refusal(env, target, false));
			target.snapTo(Vec3.atBottomCenterOf(base.offset(20, 0, 0)), 0, 0);
			helper.assertTrue(Maps.refusal(env, target, false).orElse("").contains("within 64"), "at home");
			helper.assertTrue(Maps.refusal(env, target, true).isEmpty(), "forced skips home");
			target.snapTo(Vec3.atBottomCenterOf(base.offset(70, 0, 0)), 0, 0);
			Maps.Env fighting = new Maps.Env(env.server(), env.watch(), env.players(), env.base(), env.stage(), env.remains(), env.data(), env.config(), p -> 20);
			helper.assertTrue(Maps.refusal(fighting, target, false).orElse("").contains("combat"), "in combat");
			target.setServerLevel(land.level.getServer().getLevel(Level.NETHER));
			helper.assertTrue(Maps.refusal(env, target, true).orElse("").contains("overworld"), "in the Nether");
			target.setServerLevel(land.level);
			land.bases.remove(target.getUUID());
			helper.assertTrue(Maps.refusal(env, target, true).orElse("").contains("no base"), "no base");
			land.bases.put(target.getUUID(), base);
			for (int i = 0; i < 3; i++) {
				land.data.counted();
			}
			helper.assertTrue(Maps.refusal(env, target, false).orElse("").contains("at most 3"), "past the cap");
			helper.assertTrue(Maps.fire(env, target, true, RandomSource.create(1)).result() == FireResult.FIRED, "forced skips the cap");
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/**
	 * The second form, from Telling: a place of his the map holds that the target never went into is drawn in from the
	 * world, 30's colours (vanilla's), only where the map was blank, kept with what it held; its chunks never loaded by it
	 * (an unloaded part waits, drawn once someone loads it); a visited place, an explored one or before Telling: none.
	 */
	@GameTest(maxTicks = 60)
	public void theSecondFormDrawsAPlaceNobodyWentInto(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			ServerLevel level = land.level;
			BlockPos base = land.origin;
			Mock target = land.player("ana", 70, 0, base);
			ItemStack stack = land.map(base, 2);
			MapItemSavedData data = land.data(stack);
			// Drawn round home only: blank past 24 pixels (96 blocks) of the base.
			int hx = MapPixels.pixelX(data, base.getX());
			int hy = MapPixels.pixelY(data, base.getZ());
			for (int y = 0; y < MapPixels.SIZE; y++) {
				for (int x = 0; x < MapPixels.SIZE; x++) {
					if ((x - hx) * (x - hx) + (y - hy) * (y - hy) > 24 * 24) {
						data.colors[MapPixels.index(x, y)] = 0;
					}
				}
			}
			target.getInventory().setItem(9, stack);
			// His place 160 off: a brick floor 33 across, its chunks loaded (a friend is there).
			BlockPos middle = base.offset(160, 0, 40);
			land.load(middle, 5);
			int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, middle.getX(), middle.getZ());
			for (int dx = -16; dx <= 16; dx++) {
				for (int dz = -16; dz <= 16; dz++) {
					level.setBlock(new BlockPos(middle.getX() + dx, ground, middle.getZ() + dz), Blocks.BRICKS.defaultBlockState(), 2);
				}
			}
			BoundingBox box = new BoundingBox(middle.getX() - 16, ground, middle.getZ() - 16, middle.getX() + 16, ground + 1, middle.getZ() + 16);
			Remains.Place place = new Remains.Place(Remains.Kind.PYRAMID, Level.OVERWORLD, box, middle, middle, Direction.WEST, Optional.empty(), 1L, 0);
			KnownPlaces known = new KnownPlaces(List.of(place));
			land.remains = known;
			Maps.Candidate candidate = new Maps.Candidate(stack, stack.get(DataComponents.MAP_ID), data, base);
			// Before Telling: none (forced: yes).
			helper.assertTrue(Maps.refusal(land.env(), target, false).isEmpty(), "the first form should still fit");
			land.data.edit(candidate.id().id(), S30Data.BLANK);
			helper.assertTrue(Maps.refusal(land.env(), target, false).isPresent(), "the second form before Telling");
			land.stage = Stage.TELLING;
			helper.assertTrue(Maps.refusal(land.env(), target, false).isEmpty(), "no second form at Telling: " + Maps.refusal(land.env(), target, false));
			// Visited: none.
			known.visit(target.getUUID(), place);
			helper.assertTrue(Maps.refusal(land.env(), target, false).isPresent(), "a place they went into");
			KnownPlaces fresh = new KnownPlaces(List.of(place));
			land.remains = fresh;
			byte[] before = data.colors.clone();
			Maps.Outcome outcome = Maps.fire(land.env(), target, false, RandomSource.create(4));
			helper.assertTrue(outcome.result() == FireResult.FIRED && outcome.form() == S30Data.DRAWN && outcome.pixels() > 20, "not drawn: " + outcome.reason()
					+ " " + outcome.pixels());
			int[] px = MapPixels.pixelsOf(data, box);
			byte brick = MapColor.COLOR_RED.getPackedId(MapColor.Brightness.NORMAL);
			int bricks = 0;
			for (int y = px[1]; y <= px[3]; y++) {
				for (int x = px[0]; x <= px[2]; x++) {
					byte now = data.colors[MapPixels.index(x, y)];
					helper.assertTrue(now != 0 && now == MapPixels.colour(level, data, x, y), "pixel " + x + " " + y + " not vanilla's colour");
					if (now / 4 == brick / 4) {
						bricks++;
					}
				}
			}
			helper.assertTrue(bricks > 0, "no brick on the map");
			for (int i = 0; i < before.length; i++) {
				helper.assertTrue(before[i] == 0 || data.colors[i] == before[i], "a drawn pixel was drawn over");
			}
			helper.assertTrue(land.data.pending().isEmpty(), "still waiting with every chunk loaded");
			S30Data.Edit edit = land.data.edits().stream().filter(e -> e.form == S30Data.DRAWN).findFirst().orElseThrow();
			helper.assertTrue(edit.size() == outcome.pixels() && edit.before(0) == 0, "not kept with what it held");

			// A place in chunks nobody loaded: it waits, loading nothing, and is drawn once they load.
			BlockPos far = base.offset(200, 0, -150);
			BoundingBox farBox = new BoundingBox(far.getX() - 8, 0, far.getZ() - 8, far.getX() + 8, 60, far.getZ() + 8);
			S30Data.Pending waiting = new S30Data.Pending(candidate.id().id(), farBox, 1, Long.MAX_VALUE);
			land.data.wait(waiting);
			helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "the far chunk was loaded before");
			helper.assertTrue(Maps.drawOn(land.env(), waiting) == 0 && land.data.pending().contains(waiting), "it did not wait");
			helper.assertTrue(level.getChunkSource().getChunkNow(far.getX() >> 4, far.getZ() >> 4) == null, "the wait loaded a chunk");
			land.load(far, 2);
			helper.assertTrue(Maps.drawOn(land.env(), waiting) > 0 && !land.data.pending().contains(waiting), "not drawn once loaded");
			S30Data.Pending expired = new S30Data.Pending(candidate.id().id(), farBox, 1, -1);
			land.data.wait(expired);
			Maps.drawOn(land.env(), expired);
			helper.assertFalse(land.data.pending().contains(expired), "an expired wait stayed");
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/**
	 * The world's ends (SC2-H): a column built to the top of the world reads its top block; a column emptied to the floor
	 * reads as vanilla's bottom (bedrock's colour); nothing reads a height the level does not have.
	 */
	@GameTest(maxTicks = 40)
	public void theWorldsEndsReadAsVanillaReadsThem(GameTestHelper helper) {
		Land land = new Land(helper);
		try {
			ServerLevel level = land.level;
			BlockPos column = land.origin.offset(3, 0, 3);
			level.setBlock(new BlockPos(column.getX(), level.getMaxY(), column.getZ()), Blocks.BRICKS.defaultBlockState(), 2);
			MapPixels.Sample top = MapPixels.sample(level, 1, column.getX(), column.getZ());
			helper.assertTrue(top != null && top.color() == MapColor.COLOR_RED && top.height() == level.getMaxY(), "the top: " + top);
			BlockPos empty = land.origin.offset(-3, 0, -3);
			for (int y = level.getMinY(); y <= level.getMaxY(); y++) {
				level.setBlock(new BlockPos(empty.getX(), y, empty.getZ()), Blocks.AIR.defaultBlockState(), 2);
			}
			MapPixels.Sample bottom = MapPixels.sample(level, 1, empty.getX(), empty.getZ());
			helper.assertTrue(bottom != null && bottom.color() == Blocks.BEDROCK.defaultBlockState().getMapColor(level, BlockPos.ZERO)
					&& bottom.height() >= level.getMinY(), "the floor: " + bottom);
		} finally {
			land.release();
		}
		helper.succeed();
	}

	/** The saved data round trip and leniency. */
	@GameTest
	public void theDataSavesAndReadsLeniently(GameTestHelper helper) {
		S30Data data = new S30Data();
		data.counted();
		S30Data.Edit edit = data.edit(12, S30Data.BLANK);
		data.keep(edit, 5, (byte) 9, (byte) 0);
		data.keep(edit, 6, (byte) 10, (byte) 0);
		data.wait(new S30Data.Pending(12, new BoundingBox(1, 2, 3, 4, 5, 6), 2, 40));
		CompoundTag tag = (CompoundTag) S30Data.CODEC.encodeStart(NbtOps.INSTANCE, data).getOrThrow();
		S30Data read = S30Data.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();
		helper.assertTrue(read.count() == 1 && read.had(12, S30Data.BLANK) && read.edits().getFirst().size() == 2 && read.edits().getFirst().before(1) == 10
				&& read.pending().size() == 1 && read.pending().getFirst().untilDay() == 40, "the round trip");
		CompoundTag broken = tag.copy();
		broken.getListOrEmpty("edits").getCompoundOrEmpty(0).putIntArray("i", new int[] {1});
		broken.putString("count", "x");
		S30Data lenient = S30Data.CODEC.parse(NbtOps.INSTANCE, broken).getOrThrow();
		helper.assertTrue(lenient.count() == 0 && lenient.edits().isEmpty() && lenient.pending().size() == 1, "not lenient");
		helper.assertTrue(S30Data.CODEC.parse(NbtOps.INSTANCE, new CompoundTag()).getOrThrow().edits().isEmpty(), "an empty file");
		helper.succeed();
	}
}
