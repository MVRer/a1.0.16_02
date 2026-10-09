package com.forzacode.a1016_02.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.saveddata.maps.MapDecorationTypes;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;

/**
 * Builds the items fragments are made of. Every one carries the custom data marker
 * {@code {"a1016_02:fragment": "F06"}} so reads and carrying can be detected.
 */
public final class FragmentItems {
	public static final String MARKER = "a1016_02:fragment";
	/** Zoom of F28's explorer map, like vanilla explorer maps. */
	private static final byte MAP_SCALE = 2;

	private FragmentItems() {
	}

	/** The fragment id an item carries, if it is a fragment item. */
	public static Optional<String> fragmentId(ItemStack stack) {
		if (stack.isEmpty()) {
			return Optional.empty();
		}
		CustomData data = stack.get(DataComponents.CUSTOM_DATA);
		if (data == null || data.isEmpty()) {
			return Optional.empty();
		}
		return data.copyTag().getString(MARKER);
	}

	public static boolean is(ItemStack stack, String id) {
		return fragmentId(stack).map(id::equals).orElse(false);
	}

	/** Adds the marker to any stack. */
	public static ItemStack mark(ItemStack stack, String id) {
		CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.putString(MARKER, id));
		return stack;
	}

	/** A written book with the fragment's title and pages, {@code [PLAYER NAME]} replaced, no author. */
	public static ItemStack book(Fragment fragment, String playerName) {
		List<Filterable<Component>> pages = new ArrayList<>();
		for (String page : fragment.pagesFor(playerName)) {
			pages.add(Filterable.passThrough(Component.literal(page)));
		}
		ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
		stack.set(DataComponents.WRITTEN_BOOK_CONTENT,
				new WrittenBookContent(Filterable.passThrough(fragment.title()), "", fragment.generation(), pages, true));
		return mark(stack, fragment.id());
	}

	/** The fragment's plain item (F12's disc), or the jukebox disc of a structure (F11). */
	public static ItemStack item(Fragment fragment) {
		Item item = fragment.item().map(BuiltInRegistries.ITEM::getValue).orElse(Items.AIR);
		ItemStack stack = new ItemStack(item);
		if (stack.isEmpty()) {
			return stack;
		}
		fragment.itemName().ifPresent(name -> stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)));
		return mark(stack, fragment.id());
	}

	/** F28: an explorer map, renamed, with an X on {@code target}. */
	public static ItemStack map(Fragment fragment, ServerLevel level, BlockPos target) {
		ItemStack stack = MapItem.create(level, target.getX(), target.getZ(), MAP_SCALE, true, true);
		MapItem.renderBiomePreviewMap(level, stack);
		MapItemSavedData.addTargetDecoration(stack, target, "+", MapDecorationTypes.RED_X);
		fragment.itemName().ifPresent(name -> stack.set(DataComponents.CUSTOM_NAME, Component.literal(name)));
		return mark(stack, fragment.id());
	}

	/** True for F28: an item fragment that is a map. */
	public static boolean isMap(Fragment fragment) {
		return fragment.item().map(BuiltInRegistries.ITEM::getValue).map(item -> item == Items.FILLED_MAP).orElse(false);
	}

	/** The stack a fragment is carried as: a book, a map pointing at {@code mapTarget}, or its item (F11's disc). */
	public static ItemStack stackFor(Fragment fragment, String playerName, ServerLevel level, BlockPos mapTarget) {
		if (fragment.isBook()) {
			return book(fragment, playerName);
		}
		return isMap(fragment) ? map(fragment, level, mapTarget) : item(fragment);
	}

	/** Sign text for the front of a fragment sign. */
	public static SignText signText(Fragment fragment, String playerName) {
		List<Component> messages = new ArrayList<>(4);
		for (String line : fragment.linesFor(playerName)) {
			messages.add(Component.literal(line));
		}
		while (messages.size() < 4) {
			messages.add(Component.empty());
		}
		return new SignText(messages, messages, DyeColor.BLACK, false);
	}
}
