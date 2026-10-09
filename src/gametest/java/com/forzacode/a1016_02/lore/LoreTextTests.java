package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.forzacode.a1016_02.core.HerobrineState;
import com.forzacode.a1016_02.core.Stage;
import com.forzacode.a1016_02.core.WorldProfile;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.SignText;

/** Fragment data against DESIGN.md, layout, name substitution and stage gates. */
public class LoreTextTests {
	/**
	 * DESIGN.md "Hidden story across the map", the Text column: every quoted part, word for word (markdown escapes
	 * removed, "×3" written out). Signs: one part per line. Books: the parts in order.
	 */
	static final Map<String, List<String>> DESIGN_TEXT = Map.ofEntries(
			Map.entry("F01", List.of("ok so this is weird. new world, fog really bad because my pc sucks. saw a cow in the fog and went to it. not a cow. it was a guy. normal skin. no name over him. his eyes were empty. he just looked at me and then ran. this is singleplayer.")),
			Map.entry("F02", List.of("my thread got deleted. like 5 min. im posting again. theres tunnels now. 2x2. i didnt dig them. theres pyramids in the water. sand. trees with no")),
			Map.entry("F03", List.of("Stop.")),
			Map.entry("F05", List.of("you're not the only one. i know a guy who has a list. people who saw him. same thing every time, fog, no eyes. dont post it. i mean it. people who post stop being on the list.")),
			Map.entry("F06", List.of("kirbyfan_92 . fog . tree line", "stoneslab . saw him on a hill", "dyl4n_m . he was in my house", "oreo_tm . white eyes . cave", "pk_alpha . gone", "t0rch . gone", "[PLAYER NAME] .")),
			Map.entry("F07", List.of("4788685", "74082066", "804")),
			Map.entry("F08", List.of("he is no", "longer", "with us")),
			Map.entry("F09", List.of("i never had a")),
			Map.entry("F10", List.of("* fixed some bugs", "* removed herobrine", "* removed herobrine", "* removed herobrine", "* removed")),
			Map.entry("F11", List.of("dont play it", "down here")),
			Map.entry("F14", List.of("only one screenshot came out. just fog. people keep asking for the seed like that proves something. it was a1.0.16_02. i checked. it doesnt matter what version you play. he comes with the world.")),
			Map.entry("F15", List.of("1. dont put his name in commit messages. 2. dont put it in chat. 3. if a build has tunnels nobody wrote, roll it back. 4. dont talk about it after. 5. this is not a joke so stop making it one. 6. dont go back for things.")),
			Map.entry("F16", List.of("tue. the carver is doing it again. 2x2 runs, straight, nobody wrote them.", "wed. took his pass out completely.", "thu. tunnels still there in the new build.", "fri. changelog says removed. i dont know what else to write.", "sat. its not random. it goes where people are.")),
			Map.entry("F17", List.of("he did the part that takes things out. caves. the clearing. i did the part that puts things in. we used to joke that between us it was a whole world. when he")),
			Map.entry("F18", List.of("someone got", "a pm. it", "wasnt from us.", "i asked.")),
			Map.entry("F19", List.of("sent it. one word, like we agreed. took the profile down after. if he keeps posting we'll do the thread too. its better for him this way. its better for everyone.")),
			Map.entry("F20", List.of("if anyone asks.", "i never had a brother.", "say it enough times and its true.", "it worked. hes not anywhere now.")),
			Map.entry("F21", List.of("day 3 fell in a hole that wasnt there when i dug it", "day 7 creeper in the house. door was shut. no sound", "day 11 torches in my mine are gone. i placed them", "day 12 these arent accidents", "day 14 said sorry to him. got quiet", "day 19 its been really quiet")),
			Map.entry("F22", List.of("dont let them", "feel sorry for", "him. thats how", "he gets close")),
			Map.entry("F23", List.of("pk_alpha . fell", "t0rch . lava", "dyl4n_m . drowned (in his house)", "oreo_tm . fell", "stoneslab .", "kirbyfan_92 .", "[PLAYER NAME] .")),
			Map.entry("F24", List.of("i did what the", "sign said. quiet", "for a week", "i thought")),
			Map.entry("F25", List.of("he only takes things out. if something is missing he was there. count your torches. this is the only place he cant cut")),
			Map.entry("F26", List.of("planted them", "all back", "thought hed", "like it")),
			Map.entry("F27", List.of("he isnt sad. we wanted him to be sad. he is angry we made anything at all. if youre reading this he let you")),
			Map.entry("F28", List.of("where he didnt")),
			Map.entry("F29", List.of("we never gave him one", "everybody else got one", "we couldnt. you have to say it to make one")),
			Map.entry("F30", List.of("i did, but")));

	/** Book titles from the Form column ("" for a book with no title). */
	static final Map<String, String> TITLES = Map.ofEntries(Map.entry("F01", "new world"), Map.entry("F02", "new world (2)"),
			Map.entry("F05", "for you"), Map.entry("F06", "names"), Map.entry("F09", ""), Map.entry("F10", "changes"),
			Map.entry("F15", "rules"), Map.entry("F16", "notes"), Map.entry("F17", ""), Map.entry("F19", "sent"));

	static Fragment fragment(String id) {
		return FragmentData.get(id).orElseThrow(() -> new AssertionError("fragment " + id + " did not load"));
	}

	static List<String> words(Collection<String> texts) {
		List<String> words = new ArrayList<>();
		for (String text : texts) {
			for (String word : text.split("\\s+")) {
				if (!word.isEmpty()) {
					words.add(word);
				}
			}
		}
		return words;
	}

	/** Every line of every page. */
	static List<String> lines(List<String> pages) {
		List<String> lines = new ArrayList<>();
		for (String page : pages) {
			lines.addAll(Arrays.asList(page.split("\n", -1)));
		}
		return lines;
	}

	@GameTest
	public void allThirtyFragmentsLoad(GameTestHelper helper) {
		helper.assertTrue(FragmentData.ids().equals(WorldProfile.allFragmentIds()), "loaded " + FragmentData.ids());
		for (Fragment fragment : FragmentData.all()) {
			helper.assertTrue(Placers.RULES.contains(fragment.placement().rule()), fragment.id() + " has unknown rule " + fragment.placement().rule());
			helper.assertTrue(fragment.form() != Fragment.Form.BOOK || !fragment.pages().isEmpty(), fragment.id() + " has no pages");
			fragment.requires().placed().forEach(id -> helper.assertTrue(FragmentData.get(id).isPresent(), fragment.id() + " needs unknown " + id));
			fragment.requires().read().forEach(id -> helper.assertTrue(FragmentData.get(id).isPresent(), fragment.id() + " needs unknown " + id));
			helper.assertTrue(fragment.depends().equals(WorldProfile.DEPENDENCIES.getOrDefault(fragment.id(), List.of())),
					fragment.id() + " depends " + fragment.depends() + " but the profile says " + WorldProfile.DEPENDENCIES.get(fragment.id()));
		}
		helper.succeed();
	}

	@GameTest
	public void fragmentTextMatchesDesign(GameTestHelper helper) {
		for (String id : List.of("F01", "F06", "F07", "F15", "F25")) {
			helper.assertTrue(DESIGN_TEXT.containsKey(id), "the required check of " + id + " is missing");
		}
		for (Map.Entry<String, List<String>> entry : DESIGN_TEXT.entrySet()) {
			String id = entry.getKey();
			List<String> expected = entry.getValue();
			Fragment fragment = fragment(id);
			List<String> actual = switch (fragment.form()) {
				case BOOK -> fragment.pages();
				case ITEM -> fragment.itemName().map(List::of).orElse(List.of());
				default -> fragment.lines();
			};
			helper.assertTrue(words(actual).equals(words(expected)), id + " differs from DESIGN.md:\n" + actual + "\nexpected\n" + expected);
			if (fragment.hasSign() && !id.equals("F03")) {
				List<String> signLines = new ArrayList<>(expected);
				while (signLines.size() < 4) {
					signLines.add("");
				}
				helper.assertTrue(fragment.lines().equals(signLines), id + " sign lines " + fragment.lines() + " expected " + signLines);
			}
			if (fragment.isBook() && expected.size() > 1) {
				// Lists and diaries: every part is whole, on its own line or page, in order.
				List<String> lines = lines(fragment.pages()).stream().filter(line -> !line.isEmpty()).toList();
				helper.assertTrue(lines.equals(expected), id + " entries are split or out of order: " + lines);
			}
		}
		helper.assertTrue(fragment("F03").lines().equals(List.of("", "Stop.", "", "")), "F03 is Stop. on line 2, rest blank");
		helper.assertTrue(fragment("F04").lines().equals(List.of("", "", "", "")), "F04 is one blank sign");

		Fragment f02 = fragment("F02");
		helper.assertTrue(f02.pages().size() == 4 && f02.pages().get(0).equals(DESIGN_TEXT.get("F02").getFirst())
				&& f02.pages().subList(1, 4).stream().allMatch(String::isEmpty), "F02: p1, pages 2 to 4 blank");
		Fragment f09 = fragment("F09");
		helper.assertTrue(f09.pages().size() == 10 && f09.pages().getFirst().equals("i never had a")
				&& f09.pages().subList(1, 10).stream().allMatch(String::isEmpty), "F09: p1, pages 2 to 10 blank");
		Fragment f17 = fragment("F17");
		helper.assertTrue(f17.pages().getLast().isEmpty() && f17.pages().getFirst().endsWith("when he"), "F17: the rest is blank");
		Fragment f20 = fragment("F20");
		helper.assertTrue(f20.pages().size() == 5 && f20.pages().getLast().isEmpty(), "F20: four lines, then a blank page");
		helper.assertTrue(fragment("F10").pages().size() == 1, "F10 is one changelog page");
		TITLES.forEach((id, title) -> helper.assertTrue(fragment(id).title().equals(title), id + " title " + fragment(id).title()));
		helper.succeed();
	}

	@GameTest
	public void pagesAndSignLinesFit(GameTestHelper helper) {
		for (Fragment fragment : FragmentData.all()) {
			List<String> problems = BookLayout.problems(fragment);
			helper.assertTrue(problems.isEmpty(), fragment.id() + ": " + problems);
		}
		helper.assertTrue(BookLayout.width("kirbyfan_92") == 60, "font advances: k5 i2 r6 b6 y6 f5 a6 n6 _6 96 26");
		helper.assertTrue(BookLayout.pageLines(fragment("F01").pages().getFirst()) > 1, "F01 should wrap");
		helper.succeed();
	}

	@GameTest
	public void playerNameIsSubstituted(GameTestHelper helper) {
		for (String id : List.of("F06", "F23")) {
			ItemStack stack = FragmentItems.book(fragment(id), "Tester");
			WrittenBookContent content = stack.get(DataComponents.WRITTEN_BOOK_CONTENT);
			helper.assertTrue(content != null && stack.is(Items.WRITTEN_BOOK), id + " is not a written book");
			List<String> pages = content.pages().stream().map(Filterable::raw).map(Component::getString).toList();
			List<String> lines = lines(pages);
			helper.assertTrue(lines.getLast().equals("Tester ."), id + " last line " + lines.getLast());
			helper.assertTrue(pages.stream().noneMatch(page -> page.contains(Fragment.PLAYER_NAME)), id + " still has the placeholder");
			helper.assertTrue(content.author().isEmpty(), id + " has an author");
			helper.assertTrue(FragmentItems.fragmentId(stack).equals(Optional.of(id)), id + " marker " + FragmentItems.fragmentId(stack));
		}
		helper.assertTrue(FragmentItems.book(fragment("F23"), "x").get(DataComponents.WRITTEN_BOOK_CONTENT).generation() == 1, "F23 is a copy");
		helper.assertTrue(FragmentItems.book(fragment("F06"), "x").get(DataComponents.WRITTEN_BOOK_CONTENT).title().raw().equals("names"), "F06 title");

		SignText seed = FragmentItems.signText(fragment("F07"), "Tester");
		List<String> seedLines = seed.getMessages(false).stream().map(Component::getString).toList();
		helper.assertTrue(seedLines.equals(List.of("4788685", "74082066", "804", "")), "F07 sign " + seedLines);

		ItemStack disc = FragmentItems.item(fragment("F12"));
		helper.assertTrue(disc.is(Items.MUSIC_DISC_11) && FragmentItems.is(disc, "F12"), "F12 is a marked disc 11");
		ItemStack thirteen = FragmentItems.item(fragment("F11"));
		helper.assertTrue(thirteen.is(Items.MUSIC_DISC_13), "F11's jukebox disc is 13");
		ItemStack map = FragmentItems.map(fragment("F28"), helper.getLevel(), helper.absolutePos(BlockPos.ZERO));
		helper.assertTrue(map.is(Items.FILLED_MAP) && FragmentItems.is(map, "F28")
				&& map.getHoverName().getString().equals("where he didnt"), "F28 map " + map.getHoverName().getString());
		helper.succeed();
	}

	@GameTest
	public void stagesGateEveryFragment(GameTestHelper helper) {
		TestFacts everything = new TestFacts(helper);
		for (String id : WorldProfile.allFragmentIds()) {
			everything.placed.put(id, GlobalPos.of(helper.getLevel().dimension(), BlockPos.ZERO));
			everything.read.add(id);
		}
		for (Fragment fragment : FragmentData.all()) {
			for (Stage stage : Stage.values()) {
				boolean expected = Placers.isPlacedRule(fragment.placement().rule()) && stage.atLeast(fragment.stage());
				helper.assertTrue(FragmentEngine.eligible(fragment, stage, everything) == expected,
						fragment.id() + " at " + stage + " should be " + (expected ? "eligible" : "held back"));
			}
		}
		helper.assertTrue(fragment("F01").stage() == Stage.TRACES && fragment("F06").stage() == Stage.PROXIMITY
				&& fragment("F13").stage() == Stage.TELLING && fragment("F10").stage() == Stage.REMOVAL, "stages from the table");

		TestFacts fresh = new TestFacts(helper);
		helper.assertFalse(FragmentEngine.eligible(fragment("F28"), Stage.REMOVAL, fresh), "F28 before F23 is read");
		fresh.read.add("F23");
		helper.assertTrue(FragmentEngine.eligible(fragment("F28"), Stage.PROXIMITY, fresh), "F28 after F23 is read");
		helper.assertFalse(FragmentEngine.eligible(fragment("F21"), Stage.REMOVAL, fresh), "F21 before 8 reads");
		helper.assertFalse(FragmentEngine.eligible(fragment("F16"), Stage.REMOVAL, fresh), "F16 before the room exists");
		helper.assertFalse(FragmentEngine.eligible(fragment("F29"), Stage.REMOVAL, fresh), "F29 before F21 is read");
		helper.assertFalse(FragmentEngine.eligible(fragment("F03"), Stage.REMOVAL, everything), "F03 is told, not placed");
		helper.succeed();
	}

	@GameTest
	public void readIsRecordedOnce(GameTestHelper helper) {
		HerobrineState state = new HerobrineState();
		helper.assertTrue(FragmentServiceImpl.recordRead(state, "F06"), "first read");
		helper.assertTrue(state.listRead() && state.fragmentsRead().contains("F06"), "list read flag");
		helper.assertFalse(FragmentServiceImpl.recordRead(state, "F06"), "second read counted again");
		helper.assertTrue(FragmentServiceImpl.recordRead(state, "F01") && !state.fragmentsRead().contains("F02"), "other reads");
		helper.succeed();
	}

	/** Facts made up for a test, around the test's own area. */
	static final class TestFacts implements Placing.Facts {
		final Map<String, GlobalPos> placed = new HashMap<>();
		final Map<String, GlobalPos> anchors = new HashMap<>();
		final Set<String> read = new HashSet<>();
		final Set<String> enabled = new HashSet<>();
		HerobrineState.FirstBlocks firstBlocks = new HerobrineState.FirstBlocks(null, null, null);
		BlockPos base;
		BlockPos spawn;
		GlobalPos grove;

		TestFacts(GameTestHelper helper) {
			base = helper.absolutePos(new BlockPos(4, 1, 4));
			spawn = base;
		}

		@Override
		public Optional<GlobalPos> placed(String id) {
			return Optional.ofNullable(placed.get(id));
		}

		@Override
		public Optional<GlobalPos> anchor(String key) {
			return Optional.ofNullable(anchors.get(key));
		}

		@Override
		public void remember(String key, GlobalPos pos) {
			anchors.put(key, pos);
		}

		@Override
		public Optional<GlobalPos> grove() {
			return Optional.ofNullable(grove);
		}

		@Override
		public HerobrineState.FirstBlocks firstBlocks() {
			return firstBlocks;
		}

		@Override
		public BlockPos base() {
			return base;
		}

		@Override
		public BlockPos spawn() {
			return spawn;
		}

		@Override
		public boolean enabled(String id) {
			return enabled.contains(id);
		}

		@Override
		public Set<String> read() {
			return read;
		}
	}
}
