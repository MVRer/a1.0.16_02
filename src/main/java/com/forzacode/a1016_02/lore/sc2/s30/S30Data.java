package com.forzacode.a1016_02.lore.sc2.s30;

import java.util.ArrayList;
import java.util.List;

import com.forzacode.a1016_02.A1016_02;
import com.mojang.serialization.Codec;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

/**
 * What 30 keeps ({@code data/a1016_02/lore_sc30.dat}): how many the world had, every pixel it changed on every map (by
 * map id: where, what it held, what it got), and each drawn place still waiting for its chunks (the map, the place's
 * box, the margin, the last in-game day it waits). A world without the file has none; read leniently, entry by entry.
 * Server thread.
 */
public final class S30Data extends SavedData {
	public static final Codec<S30Data> CODEC = CompoundTag.CODEC.xmap(S30Data::load, S30Data::save);
	public static final SavedDataType<S30Data> TYPE = new SavedDataType<>(A1016_02.id("lore_sc30"), S30Data::new, CODEC, null);

	/** Form 1: the square round a base made blank. */
	public static final char BLANK = 'b';
	/** Form 2: one of his places drawn in. */
	public static final char DRAWN = 'd';

	/** The pixels one change of one map took: their indexes, what each held and what each got. */
	public static final class Edit {
		public final int map;
		public final char form;
		/** The in-game day it was made, or -1 (kept before R12-40). */
		public final long day;
		final List<Integer> indexes = new ArrayList<>();
		final List<Byte> before = new ArrayList<>();
		final List<Byte> after = new ArrayList<>();

		Edit(int map, char form, long day) {
			this.map = map;
			this.form = form;
			this.day = day;
		}

		public int size() {
			return indexes.size();
		}

		public int index(int i) {
			return indexes.get(i);
		}

		public byte before(int i) {
			return before.get(i);
		}

		public byte after(int i) {
			return after.get(i);
		}

		void add(int index, byte was, byte now) {
			indexes.add(index);
			before.add(was);
			after.add(now);
		}
	}

	/** A drawn place whose pixels wait for their chunks. */
	public record Pending(int map, BoundingBox box, int margin, long untilDay) {
	}

	private int count;
	private final List<Edit> edits = new ArrayList<>();
	private final List<Pending> pending = new ArrayList<>();

	public S30Data() {
	}

	public static S30Data get(MinecraftServer server) {
		return server.getDataStorage().computeIfAbsent(TYPE);
	}

	public int count() {
		return count;
	}

	public void counted() {
		count++;
		setDirty();
	}

	/** Every change kept (a copy of the list; the edits themselves are live). */
	public List<Edit> edits() {
		return List.copyOf(edits);
	}

	/** True if this map already had a change of this form. */
	public boolean had(int map, char form) {
		return edits.stream().anyMatch(e -> e.map == map && e.form == form);
	}

	/** {@link #edit(int, char, long)} with no day known. */
	public Edit edit(int map, char form) {
		return edit(map, form, -1L);
	}

	/** The kept change of this form for this map, made now (on {@code day}) if there is none. */
	public Edit edit(int map, char form, long day) {
		for (Edit edit : edits) {
			if (edit.map == map && edit.form == form) {
				return edit;
			}
		}
		Edit edit = new Edit(map, form, day);
		edits.add(edit);
		setDirty();
		return edit;
	}

	/** One pixel changed: kept with what it held. */
	public void keep(Edit edit, int index, byte was, byte now) {
		edit.add(index, was, now);
		setDirty();
	}

	public List<Pending> pending() {
		return List.copyOf(pending);
	}

	public void wait(Pending p) {
		pending.add(p);
		setDirty();
	}

	public void done(Pending p) {
		pending.remove(p);
		setDirty();
	}

	/** The switch: every kept change and every wait forgotten (after they were given back), the count kept. */
	public void forgetEdits() {
		edits.clear();
		pending.clear();
		setDirty();
	}

	/** One change given back: forgotten. */
	public void forget(Edit edit) {
		edits.remove(edit);
		setDirty();
	}

	/** Every wait forgotten (the switch). */
	public void forgetWaits() {
		pending.clear();
		setDirty();
	}

	/** Debug: the count forgotten too. */
	public void reset() {
		count = 0;
		setDirty();
	}

	private CompoundTag save() {
		CompoundTag tag = new CompoundTag();
		tag.putInt("count", count);
		ListTag list = new ListTag();
		for (Edit edit : edits) {
			CompoundTag e = new CompoundTag();
			e.putInt("m", edit.map);
			e.putString("f", String.valueOf(edit.form));
			e.putLong("d", edit.day);
			int[] at = new int[edit.size()];
			byte[] was = new byte[edit.size()];
			byte[] now = new byte[edit.size()];
			for (int i = 0; i < edit.size(); i++) {
				at[i] = edit.index(i);
				was[i] = edit.before(i);
				now[i] = edit.after(i);
			}
			e.putIntArray("i", at);
			e.putByteArray("b", was);
			e.putByteArray("a", now);
			list.add(e);
		}
		tag.put("edits", list);
		ListTag waits = new ListTag();
		for (Pending p : pending) {
			CompoundTag w = new CompoundTag();
			w.putInt("m", p.map());
			w.putIntArray("box", new int[] {p.box().minX(), p.box().minY(), p.box().minZ(), p.box().maxX(), p.box().maxY(), p.box().maxZ()});
			w.putInt("r", p.margin());
			w.putLong("u", p.untilDay());
			waits.add(w);
		}
		tag.put("pending", waits);
		return tag;
	}

	private static S30Data load(CompoundTag tag) {
		S30Data data = new S30Data();
		data.count = Math.max(0, tag.getIntOr("count", 0));
		ListTag list = tag.getListOrEmpty("edits");
		for (int i = 0; i < list.size(); i++) {
			CompoundTag e = list.getCompoundOrEmpty(i);
			String form = e.getStringOr("f", "");
			int[] at = e.getIntArray("i").orElse(new int[0]);
			byte[] was = e.getByteArray("b").orElse(new byte[0]);
			byte[] now = e.getByteArray("a").orElse(new byte[0]);
			if (form.length() != 1 || at.length != was.length || at.length != now.length) {
				continue;
			}
			Edit edit = new Edit(e.getIntOr("m", -1), form.charAt(0), e.getLongOr("d", -1L));
			for (int k = 0; k < at.length; k++) {
				if (at[k] >= 0 && at[k] < MapPixels.SIZE * MapPixels.SIZE) {
					edit.add(at[k], was[k], now[k]);
				}
			}
			if (edit.map >= 0) {
				data.edits.add(edit);
			}
		}
		ListTag waits = tag.getListOrEmpty("pending");
		for (int i = 0; i < waits.size(); i++) {
			CompoundTag w = waits.getCompoundOrEmpty(i);
			int[] box = w.getIntArray("box").orElse(new int[0]);
			int map = w.getIntOr("m", -1);
			if (box.length == 6 && map >= 0) {
				data.pending.add(new Pending(map, new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]), Math.max(0, w.getIntOr("r", 0)),
						w.getLongOr("u", -1L)));
			}
		}
		return data;
	}
}
