package com.forzacode.a1016_02.world.sc2.s32;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.forzacode.a1016_02.A1016_02;
import com.forzacode.a1016_02.world.sc2.s32.CrumbLines.Tier;
import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import org.jspecify.annotations.Nullable;

/**
 * What feature 32 keeps in a world ({@code data/a1016_02/s32.dat}): every crumb worldgen built (its line, its number,
 * where it stands and what kind of thing it is, so the runtime rules know it without reading the land), the crumbs each
 * participant has seen, and the two rules' counts (32b once a line and once a day a world; 32c's moves a line, a player's
 * day and each spur's last step). Worldgen offers its crumbs on any thread ({@link #offer}); the server thread keeps them
 * at its next tick ({@link #drain}) and at stop. About 12 bytes a crumb; read leniently, entry by entry.
 */
public final class CrumbData extends SavedData {
	public static final Codec<CrumbData> CODEC = CompoundTag.CODEC.xmap(CrumbData::load, CrumbData::save);
	public static final SavedDataType<CrumbData> TYPE = new SavedDataType<>(A1016_02.id("s32"), CrumbData::new, CODEC, null);

	/**
	 * One crumb as built.
	 *
	 * @param line   its line's key
	 * @param index  its number along the line (0 nearest the place)
	 * @param tier   its tier
	 * @param at     its lowest own cell above the ground (a post's foot)
	 * @param height a post's blocks (0: not a post: a thing 32b and 32c leave alone)
	 * @param torch  where its torch is: -1 none, 1 on top, else the side's {@code Direction} 3D value (2 to 5) at its foot
	 * @param moved  true once 32c moved it
	 */
	public record Crumb(String line, int index, Tier tier, BlockPos at, int height, int torch, boolean moved) {
		public boolean post() {
			return height > 0;
		}

		/** Its own cells, foot first (a post's column and its torch; for a thing that is not a post, its middle cell). */
		public List<BlockPos> cells() {
			List<BlockPos> out = new ArrayList<>(height + 1);
			for (int i = 0; i < Math.max(1, height); i++) {
				out.add(at.above(i));
			}
			BlockPos t = torchAt();
			if (t != null) {
				out.add(t);
			}
			return out;
		}

		public @Nullable BlockPos torchAt() {
			if (torch == 1) {
				return at.above(height);
			}
			if (torch >= 2 && torch <= 5) {
				return at.relative(Direction.from3DDataValue(torch));
			}
			return null;
		}

		/** Its id among every crumb of the world. */
		public String id() {
			return id(line, index);
		}

		public static String id(String line, int index) {
			return line + "#" + index;
		}

		Crumb movedTo(BlockPos to) {
			return new Crumb(line, index, tier, to.immutable(), height, torch, true);
		}

		int meta() {
			return (index & 0xFF) | tier.ordinal() << 8 | (height & 0xF) << 10 | ((torch + 1) & 0x7) << 14 | (moved ? 1 : 0) << 17;
		}

		static Crumb of(String line, long pos, int meta) {
			Tier[] tiers = Tier.values();
			return new Crumb(line, meta & 0xFF, tiers[Math.min(tiers.length - 1, meta >>> 8 & 0x3)], BlockPos.of(pos), meta >>> 10 & 0xF,
					(meta >>> 14 & 0x7) - 1, (meta >>> 17 & 1) != 0);
		}
	}

	private static final Queue<Crumb> NEW = new ConcurrentLinkedQueue<>();

	/** Line key, then crumb number. */
	private final Map<String, Map<Integer, Crumb>> lines = new LinkedHashMap<>();
	/** Each chunk's crumbs (built from {@link #lines}). */
	private final Map<Long, List<Crumb>> chunks = new HashMap<>();
	private final Map<UUID, Set<String>> seen = new HashMap<>();
	/** The lines 32b took a crumb of. */
	private final Set<String> gone = new LinkedHashSet<>();
	private long goneDay = Long.MIN_VALUE;
	/** How many crumbs 32c moved from each line. */
	private final Map<String, Integer> pulled = new HashMap<>();
	/** The in-game day 32c last moved a crumb for each player. */
	private final Map<UUID, Long> pullDay = new HashMap<>();
	/** Each spur's last step's distance from its player's base (line key and player). */
	private final Map<String, Integer> spurs = new HashMap<>();

	public CrumbData() {
	}

	public static CrumbData get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	/** Worldgen built a crumb (any thread): kept at the next drain. */
	public static void offer(Crumb crumb) {
		NEW.add(crumb);
	}

	/** True while worldgen's crumbs wait to be kept. */
	public static boolean pending() {
		return !NEW.isEmpty();
	}

	/** Keeps what worldgen built since the last drain (server thread). */
	public int drain() {
		int n = 0;
		for (Crumb c = NEW.poll(); c != null; c = NEW.poll()) {
			put(c);
			n++;
		}
		if (n > 0) {
			setDirty();
		}
		return n;
	}

	/** Tests: forgets what waits to be kept. */
	public static void dropPending() {
		NEW.clear();
	}

	/** Keeps a crumb (worldgen's through {@link #drain}; tests' at once). Server thread. */
	public void add(Crumb c) {
		put(c);
		setDirty();
	}

	private void put(Crumb c) {
		Crumb before = lines.computeIfAbsent(c.line(), k -> new LinkedHashMap<>()).put(c.index(), c);
		if (before != null) {
			List<Crumb> in = chunks.get(chunkOf(before.at()));
			if (in != null) {
				in.remove(before);
			}
		}
		chunks.computeIfAbsent(chunkOf(c.at()), k -> new ArrayList<>(2)).add(c);
	}

	private static long chunkOf(BlockPos pos) {
		return ChunkPos.pack(pos.getX() >> 4, pos.getZ() >> 4);
	}

	/** The crumbs of the chunks within {@code radius} of (x, z) (chunk-wise). */
	public List<Crumb> near(int x, int z, int radius) {
		List<Crumb> out = new ArrayList<>();
		for (int cx = (x - radius) >> 4; cx <= (x + radius) >> 4; cx++) {
			for (int cz = (z - radius) >> 4; cz <= (z + radius) >> 4; cz++) {
				List<Crumb> in = chunks.get(ChunkPos.pack(cx, cz));
				if (in != null) {
					out.addAll(in);
				}
			}
		}
		return out;
	}

	/** A line's crumbs, by number. */
	public Map<Integer, Crumb> line(String key) {
		return lines.getOrDefault(key, Map.of());
	}

	public Set<String> lineKeys() {
		return lines.keySet();
	}

	public int size() {
		int n = 0;
		for (Map<Integer, Crumb> l : lines.values()) {
			n += l.size();
		}
		return n;
	}

	/** 32c moved a crumb: where it stands now. */
	public Crumb moved(Crumb c, BlockPos to) {
		Crumb m = c.movedTo(to);
		put(m);
		setDirty();
		return m;
	}

	// --- sightings ---

	public boolean hasSeen(UUID player, String crumbId) {
		return seen.getOrDefault(player, Set.of()).contains(crumbId);
	}

	/** True if anyone has seen it. */
	public List<UUID> seers(String crumbId) {
		List<UUID> out = new ArrayList<>();
		seen.forEach((p, ids) -> {
			if (ids.contains(crumbId)) {
				out.add(p);
			}
		});
		return out;
	}

	public boolean see(UUID player, String crumbId) {
		boolean added = seen.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(crumbId);
		if (added) {
			setDirty();
		}
		return added;
	}

	public int seenBy(UUID player) {
		return seen.getOrDefault(player, Set.of()).size();
	}

	// --- 32b ---

	public boolean goneFrom(String line) {
		return gone.contains(line);
	}

	public long goneDay() {
		return goneDay;
	}

	public void gone(String line, long day) {
		gone.add(line);
		goneDay = day;
		setDirty();
	}

	// --- 32c ---

	public int pulled(String line) {
		return pulled.getOrDefault(line, 0);
	}

	public long pullDay(UUID player) {
		return pullDay.getOrDefault(player, Long.MIN_VALUE);
	}

	public int spur(String line, UUID player) {
		return spurs.getOrDefault(line + "|" + player, 0);
	}

	public void pull(String line, UUID player, long day, int spurAt) {
		pulled.merge(line, 1, Integer::sum);
		pullDay.put(player, day);
		spurs.put(line + "|" + player, spurAt);
		setDirty();
	}

	/** Tests and debug: forgets the rules' counts and every sighting (the crumbs stay). */
	public void resetRules() {
		seen.clear();
		gone.clear();
		goneDay = Long.MIN_VALUE;
		pulled.clear();
		pullDay.clear();
		spurs.clear();
		setDirty();
	}

	// --- saving ---

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		ListTag ls = new ListTag();
		lines.forEach((key, crumbs) -> {
			CompoundTag l = new CompoundTag();
			l.putString("k", key);
			long[] p = new long[crumbs.size()];
			int[] m = new int[crumbs.size()];
			int i = 0;
			for (Crumb c : crumbs.values()) {
				p[i] = c.at().asLong();
				m[i++] = c.meta();
			}
			l.putLongArray("p", p);
			l.putIntArray("m", m);
			ls.add(l);
		});
		tag.put("l", ls);
		CompoundTag s = new CompoundTag();
		seen.forEach((player, ids) -> {
			ListTag list = new ListTag();
			for (String id : ids) {
				list.add(net.minecraft.nbt.StringTag.valueOf(id));
			}
			s.put(player.toString(), list);
		});
		tag.put("s", s);
		ListTag g = new ListTag();
		for (String line : gone) {
			g.add(net.minecraft.nbt.StringTag.valueOf(line));
		}
		tag.put("g", g);
		tag.putLong("gd", goneDay);
		CompoundTag pl = new CompoundTag();
		pulled.forEach(pl::putInt);
		tag.put("pl", pl);
		CompoundTag pd = new CompoundTag();
		pullDay.forEach((p, d) -> pd.putLong(p.toString(), d));
		tag.put("pd", pd);
		CompoundTag sp = new CompoundTag();
		spurs.forEach(sp::putInt);
		tag.put("sp", sp);
		return tag;
	}

	private static CrumbData load(CompoundTag tag) {
		CrumbData d = new CrumbData();
		for (Tag t : tag.getListOrEmpty("l")) {
			if (!(t instanceof CompoundTag l)) {
				continue;
			}
			String key = l.getStringOr("k", "");
			long[] p = l.getLongArray("p").orElse(new long[0]);
			int[] m = l.getIntArray("m").orElse(new int[0]);
			for (int i = 0; i < Math.min(p.length, m.length) && !key.isEmpty(); i++) {
				d.put(Crumb.of(key, p[i], m[i]));
			}
		}
		CompoundTag s = tag.getCompoundOrEmpty("s");
		for (String player : s.keySet()) {
			try {
				UUID id = UUID.fromString(player);
				Set<String> ids = new LinkedHashSet<>();
				for (Tag t : s.getListOrEmpty(player)) {
					t.asString().ifPresent(ids::add);
				}
				d.seen.put(id, ids);
			} catch (IllegalArgumentException ignored) {
				// A malformed entry is dropped alone.
			}
		}
		for (Tag t : tag.getListOrEmpty("g")) {
			t.asString().ifPresent(d.gone::add);
		}
		d.goneDay = tag.getLongOr("gd", Long.MIN_VALUE);
		CompoundTag pl = tag.getCompoundOrEmpty("pl");
		for (String k : pl.keySet()) {
			d.pulled.put(k, pl.getIntOr(k, 0));
		}
		CompoundTag pd = tag.getCompoundOrEmpty("pd");
		for (String k : pd.keySet()) {
			try {
				d.pullDay.put(UUID.fromString(k), pd.getLongOr(k, Long.MIN_VALUE));
			} catch (IllegalArgumentException ignored) {
				// dropped alone
			}
		}
		CompoundTag sp = tag.getCompoundOrEmpty("sp");
		for (String k : sp.keySet()) {
			d.spurs.put(k, sp.getIntOr(k, 0));
		}
		return d;
	}
}
