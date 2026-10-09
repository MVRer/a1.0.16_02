package com.forzacode.a1016_02.lore;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.forzacode.a1016_02.core.CoreCodecs;
import com.forzacode.a1016_02.core.SiteType;
import com.forzacode.a1016_02.core.Stage;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.Identifier;

/**
 * One fragment, loaded from {@code data/a1016_02/lore/fragments/<id>.json} (reloads with {@code /reload}).
 * The text is DESIGN.md's, word for word; {@code [PLAYER NAME]} is replaced when the fragment is placed.
 *
 * @param id         "F01".."F30"
 * @param name       DESIGN.md's short name (not shown in game)
 * @param form       what it is in the world
 * @param where      DESIGN.md's "Where / when" cell, for reference (not shown in game)
 * @param title      book title ("" for a book with no title)
 * @param generation book generation (0 original, 1 copy of original)
 * @param pages      book pages; lines inside a page are split with "\n"
 * @param lines      sign lines (4)
 * @param item       the item for item fragments (and F11's jukebox disc)
 * @param itemName   custom name for an item fragment (F28's map)
 * @param stage      earliest stage
 * @param placement  where and how the engine places it
 * @param requires   what must have happened first
 * @param depends    profile dependencies (D-003), for reference
 */
public record Fragment(String id, String name, Form form, String where, String title, int generation, List<String> pages,
		List<String> lines, Optional<Identifier> item, Optional<String> itemName, Stage stage, Placement placement,
		Requires requires, List<String> depends) {
	/** The fragment's form in the world. */
	public enum Form {
		BOOK, SIGN, ITEM, STRUCTURE, ABSENCE;

		static final Codec<Form> CODEC = lowerEnum(Form.class);
	}

	/** The text placeholder replaced with the subject's name at placement time. */
	public static final String PLAYER_NAME = "[PLAYER NAME]";

	/**
	 * Placement rule and its parameters.
	 *
	 * @param rule        the placement rule (see {@link Placers})
	 * @param site        the site type it fills, if any
	 * @param sameAs      a fragment whose place this one shares (F02's hut, F16's room...)
	 * @param notWith     a fragment whose site this one must not share (F19 is not in F07's pyramid)
	 * @param origin      "spawn" or "base": what the distances are measured from
	 * @param minDistance closest distance (blocks, horizontal) for own builds
	 * @param maxDistance farthest distance, for sites and own builds
	 * @param siteMinDistance closest distance for sites (0: any site within {@code maxDistance})
	 * @param pick        which site: "nearest", "longest" (tunnel size) or "largest" (pyramid size)
	 * @param y           a fixed height when the rule has one (F11 below Y 40, F14 10 down, F15 floor at Y 12)
	 * @param lengthMin   own tunnel length range
	 * @param lengthMax   own tunnel length range
	 */
	public record Placement(String rule, Optional<SiteType> site, Optional<String> sameAs, Optional<String> notWith, String origin,
			int minDistance, int maxDistance, int siteMinDistance, String pick, Optional<Integer> y, int lengthMin, int lengthMax) {
		public static final Codec<Placement> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.fieldOf("rule").forGetter(Placement::rule),
				CoreCodecs.enumCodec(SiteType.class).optionalFieldOf("site").forGetter(Placement::site),
				Codec.STRING.optionalFieldOf("same_as").forGetter(Placement::sameAs),
				Codec.STRING.optionalFieldOf("not_with").forGetter(Placement::notWith),
				Codec.STRING.optionalFieldOf("origin", "base").forGetter(Placement::origin),
				Codec.INT.optionalFieldOf("min_distance", 0).forGetter(Placement::minDistance),
				Codec.INT.optionalFieldOf("max_distance", 1000).forGetter(Placement::maxDistance),
				Codec.INT.optionalFieldOf("site_min_distance", 0).forGetter(Placement::siteMinDistance),
				Codec.STRING.optionalFieldOf("pick", "nearest").forGetter(Placement::pick),
				Codec.INT.optionalFieldOf("y").forGetter(Placement::y),
				Codec.INT.optionalFieldOf("length_min", 12).forGetter(Placement::lengthMin),
				Codec.INT.optionalFieldOf("length_max", 20).forGetter(Placement::lengthMax)
		).apply(i, Placement::new));

		public boolean fromSpawn() {
			return "spawn".equals(origin);
		}
	}

	/**
	 * What must have happened before the fragment can be placed.
	 *
	 * @param placed    fragments that must already be placed
	 * @param read      fragments that must already be read
	 * @param readCount at least this many fragments read
	 */
	public record Requires(List<String> placed, List<String> read, int readCount) {
		public static final Requires NONE = new Requires(List.of(), List.of(), 0);
		public static final Codec<Requires> CODEC = RecordCodecBuilder.create(i -> i.group(
				Codec.STRING.listOf().optionalFieldOf("placed", List.of()).forGetter(Requires::placed),
				Codec.STRING.listOf().optionalFieldOf("read", List.of()).forGetter(Requires::read),
				Codec.INT.optionalFieldOf("read_count", 0).forGetter(Requires::readCount)
		).apply(i, Requires::new));
	}

	public static final Codec<Stage> STAGE_CODEC = lowerEnum(Stage.class);

	public static final Codec<Fragment> CODEC = RecordCodecBuilder.<Fragment>create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(Fragment::id),
			Codec.STRING.optionalFieldOf("name", "").forGetter(Fragment::name),
			Form.CODEC.fieldOf("form").forGetter(Fragment::form),
			Codec.STRING.optionalFieldOf("where", "").forGetter(Fragment::where),
			Codec.STRING.optionalFieldOf("title", "").forGetter(Fragment::title),
			Codec.intRange(0, 3).optionalFieldOf("generation", 0).forGetter(Fragment::generation),
			Codec.STRING.listOf().optionalFieldOf("pages", List.of()).forGetter(Fragment::pages),
			Codec.STRING.listOf().optionalFieldOf("lines", List.of()).forGetter(Fragment::lines),
			Identifier.CODEC.optionalFieldOf("item").forGetter(Fragment::item),
			Codec.STRING.optionalFieldOf("item_name").forGetter(Fragment::itemName),
			STAGE_CODEC.fieldOf("stage").forGetter(Fragment::stage),
			Placement.CODEC.fieldOf("placement").forGetter(Fragment::placement),
			Requires.CODEC.optionalFieldOf("requires", Requires.NONE).forGetter(Fragment::requires),
			Codec.STRING.listOf().optionalFieldOf("depends", List.of()).forGetter(Fragment::depends)
	).apply(i, Fragment::new)).validate(Fragment::validate);

	private static DataResult<Fragment> validate(Fragment fragment) {
		if (!fragment.id.matches("F\\d\\d")) {
			return DataResult.error(() -> "bad fragment id " + fragment.id);
		}
		if (!fragment.lines.isEmpty() && fragment.lines.size() != 4) {
			return DataResult.error(() -> fragment.id + ": a sign has 4 lines, got " + fragment.lines.size());
		}
		if (fragment.form == Form.BOOK && fragment.pages.isEmpty()) {
			return DataResult.error(() -> fragment.id + ": a book needs pages");
		}
		return DataResult.success(fragment);
	}

	/** True for books, the only fragments that are read by opening them. */
	public boolean isBook() {
		return form == Form.BOOK;
	}

	/** True if the fragment has sign lines that are read by looking at them. */
	public boolean hasSign() {
		return !lines.isEmpty();
	}

	/** Pages with {@code [PLAYER NAME]} replaced. */
	public List<String> pagesFor(String playerName) {
		return pages.stream().map(page -> page.replace(PLAYER_NAME, playerName)).toList();
	}

	/** Sign lines with {@code [PLAYER NAME]} replaced. */
	public List<String> linesFor(String playerName) {
		return lines.stream().map(line -> line.replace(PLAYER_NAME, playerName)).toList();
	}

	private static <E extends Enum<E>> Codec<E> lowerEnum(Class<E> type) {
		return Codec.STRING.comapFlatMap(name -> {
			try {
				return DataResult.success(Enum.valueOf(type, name.toUpperCase(Locale.ROOT)));
			} catch (IllegalArgumentException e) {
				return DataResult.error(() -> "unknown " + type.getSimpleName() + " " + name);
			}
		}, value -> value.name().toLowerCase(Locale.ROOT));
	}
}
