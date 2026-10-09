package com.forzacode.a1016_02.accident;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombifiedPiglin;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import org.jspecify.annotations.Nullable;

/**
 * Weapons and armor he took from the player's containers earlier (D-032): the ledger's REMOVE_STACK entries (he still
 * holds the stack) and MOVE_STACK entries (the stack sits in the chest it was moved to, such as dig's network chest).
 * Only these can turn up on a zombie later, through {@code TraceService.equipFromLedger}; nothing is ever created.
 */
public final class StolenGear {
	/**
	 * One stolen weapon or armor stack.
	 *
	 * @param entry      its ledger entry
	 * @param slot       where a zombie would wear it
	 * @param kind       the word for it: sword, axe, spear, mace, trident, helmet, chestplate, leggings, boots
	 * @param fromPlayer it came out of a container the player placed (their chest), so it is theirs
	 * @param stillThere a moved stack is still in the slot it was moved to (always true for one he holds)
	 */
	public record Stolen(TraceLedger.Entry entry, ItemStack stack, EquipmentSlot slot, String kind, boolean fromPlayer, boolean stillThere) {
		/** True if he holds it (REMOVE_STACK), false if it sits in a chest (MOVE_STACK). */
		public boolean held() {
			return entry.kind() == TraceLedger.Kind.REMOVE_STACK;
		}

		/** A curse of vanishing would make it vanish with the mob: core refuses those. */
		public boolean vanishes() {
			return EnchantmentHelper.has(stack, EnchantmentEffectComponents.PREVENT_EQUIPMENT_DROP);
		}

		/** Taken long enough ago to come back on {@code day}. */
		public boolean oldEnough(long day, AccidentConfig cfg) {
			return entry.day() <= day - cfg.swordMinDaysAfterTaken;
		}

		/** Theirs, not cursed, and still where it was left. */
		public boolean canComeBack() {
			return fromPlayer && stillThere && !vanishes();
		}
	}

	private StolenGear() {
	}

	/** The slot a zombie wears this in: the main hand for a melee weapon, else its armor slot. Null if neither. */
	public static @Nullable EquipmentSlot slotFor(ItemStack stack) {
		if (stack.isEmpty()) {
			return null;
		}
		if (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(ItemTags.SPEARS) || stack.is(Items.MACE) || stack.is(Items.TRIDENT)) {
			return EquipmentSlot.MAINHAND;
		}
		if (stack.is(ItemTags.HEAD_ARMOR)) {
			return EquipmentSlot.HEAD;
		}
		if (stack.is(ItemTags.CHEST_ARMOR)) {
			return EquipmentSlot.CHEST;
		}
		if (stack.is(ItemTags.LEG_ARMOR)) {
			return EquipmentSlot.LEGS;
		}
		if (stack.is(ItemTags.FOOT_ARMOR)) {
			return EquipmentSlot.FEET;
		}
		return null;
	}

	/** The plain word for it, as the list shows it after "own". Null if it is not a weapon or armor. */
	public static @Nullable String kind(ItemStack stack) {
		if (stack.is(ItemTags.SWORDS)) {
			return "sword";
		}
		if (stack.is(ItemTags.AXES)) {
			return "axe";
		}
		if (stack.is(ItemTags.SPEARS)) {
			return "spear";
		}
		if (stack.is(Items.MACE)) {
			return "mace";
		}
		if (stack.is(Items.TRIDENT)) {
			return "trident";
		}
		if (stack.is(ItemTags.HEAD_ARMOR)) {
			return "helmet";
		}
		if (stack.is(ItemTags.CHEST_ARMOR)) {
			return "chestplate";
		}
		if (stack.is(ItemTags.LEG_ARMOR)) {
			return "leggings";
		}
		if (stack.is(ItemTags.FOOT_ARMOR)) {
			return "boots";
		}
		return null;
	}

	/** Renamed counts most, then enchanted: that is what tells a player it is theirs. */
	public static int score(ItemStack stack) {
		return (stack.has(DataComponents.CUSTOM_NAME) ? 2 : 0) + (stack.isEnchanted() ? 1 : 0);
	}

	/** A zombie of any kind but a zombified piglin; a trident only fits a drowned. */
	public static boolean fits(Mob mob, ItemStack stack) {
		if (!(mob instanceof Zombie) || mob instanceof ZombifiedPiglin) {
			return false;
		}
		return !stack.is(Items.TRIDENT) || mob instanceof Drowned;
	}

	/** True if {@code worn} is still the stack that was taken (wear and tear aside). */
	public static boolean same(ItemStack worn, ItemStack taken) {
		return !worn.isEmpty() && ItemStack.matchesIgnoringComponents(worn, taken, type -> type == DataComponents.DAMAGE);
	}

	/** Every weapon and armor stack in the ledger, oldest first. */
	public static List<Stolen> all(MinecraftServer server) {
		List<Stolen> found = new ArrayList<>();
		for (TraceLedger.Entry entry : TraceLedger.get(server).entries()) {
			if (entry.kind() != TraceLedger.Kind.REMOVE_STACK && entry.kind() != TraceLedger.Kind.MOVE_STACK) {
				continue;
			}
			ItemStack stack = entry.stack().orElse(ItemStack.EMPTY);
			EquipmentSlot slot = slotFor(stack);
			String kind = kind(stack);
			if (slot == null || kind == null) {
				continue;
			}
			ServerLevel source = server.getLevel(entry.pos().dimension());
			boolean fromPlayer = source != null && Services.watch().wasPlacedByPlayer(source, entry.pos().pos());
			found.add(new Stolen(entry, stack, slot, kind, fromPlayer, entry.kind() == TraceLedger.Kind.REMOVE_STACK || stillInChest(source, entry, stack)));
		}
		return found;
	}

	/**
	 * The stacks that can come back on a mob in {@code level} on {@code day}: theirs, old enough, not cursed, and held
	 * by him or still in a loaded chest of this level. Renamed and enchanted ones first, then the oldest.
	 */
	public static List<Stolen> usable(ServerLevel level, long day, AccidentConfig cfg) {
		List<Stolen> found = new ArrayList<>();
		for (Stolen stolen : all(level.getServer())) {
			if (stolen.canComeBack() && stolen.oldEnough(day, cfg) && (stolen.held() || stolen.entry().pos().dimension().equals(level.dimension()))) {
				found.add(stolen);
			}
		}
		found.sort(Comparator.comparingInt((Stolen s) -> -score(s.stack())).thenComparingLong(s -> s.entry().day()));
		return found;
	}

	/** A moved stack is still in the slot it was moved to (a loaded chest only; unloaded counts as not there). */
	static boolean stillInChest(@Nullable ServerLevel level, TraceLedger.Entry entry, ItemStack stack) {
		BlockPos to = entry.to().orElse(null);
		if (level == null || to == null || !level.isLoaded(to) || !(level.getBlockEntity(to) instanceof Container container)) {
			return false;
		}
		int slot = entry.toSlot();
		return slot >= 0 && slot < container.getContainerSize() && ItemStack.matches(container.getItem(slot), stack);
	}

	/** How the list and the clue name it: {@code own sword "Name" (enchanted)}. */
	public static String describe(ItemStack stack) {
		String kind = kind(stack);
		StringBuilder text = new StringBuilder("own ").append(kind == null ? stack.getItemName().getString() : kind);
		if (stack.has(DataComponents.CUSTOM_NAME)) {
			text.append(" \"").append(stack.getHoverName().getString()).append('"');
		}
		if (stack.isEnchanted()) {
			text.append(" (enchanted)");
		}
		return text.toString();
	}
}
