package com.forzacode.a1016_02.accident;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.forzacode.a1016_02.core.Services;
import com.forzacode.a1016_02.core.TraceLedger;

import net.fabricmc.fabric.api.gametest.v1.GameTest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.zombie.Drowned;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/**
 * D-032, the zombie has your sword: a weapon or armor stack he took from your chest days ago turns up on a zombie that
 * already exists, out of view, 24 to 48 blocks away. The setup, the single change (the stack goes from the ledger, or
 * the chest it was moved to, onto that zombie), the clue (it's yours), nothing in view, and only that zombie's kill
 * while it holds the stack counts.
 */
public class SwordGameTests {
	/** The player's chest at (2, 1, 2), a chest that is not theirs at (4, 1, 2), a zombie 30 blocks from the player. */
	private record Setup(Yard y, BlockPos own, BlockPos other, Zombie zombie, Zombie tooNear, String cause) {
		/** A context days after the theft, with room for every stack and zombie pair. */
		TrapContext later(ViewGate view) {
			AccidentConfig roomy = new AccidentConfig();
			roomy.swordMaxCandidates = 10_000;
			return new TrapContext(y.level, y.player, y.player.blockPosition(), y.data, view, roomy, y.now(), y.today() + y.cfg.swordMinDaysAfterTaken);
		}

		/** This test's candidates for one zombie: the ones that take from this test's chests. */
		List<Candidate> mine(List<Candidate> all, Mob mob) {
			return all.stream().filter(c -> c.mob == mob && c.stolen != null && (c.stolen.pos().pos().equals(own) || c.stolen.pos().pos().equals(other))).toList();
		}
	}

	private static Setup setup(GameTestHelper helper) {
		Yard y = new Yard(helper);
		ServerLevel level = y.level;
		y.fill(0, 0, 0, 23, 0, 23, Blocks.STONE);
		y.placed(2, 1, 2, Blocks.CHEST);
		y.set(4, 1, 2, Blocks.CHEST);
		BlockPos own = y.abs(2, 1, 2);
		BlockPos other = y.abs(4, 1, 2);
		String cause = "test:chest_opens/" + own.toShortString();
		// The test zombies stand where the night would have put them. The mod never spawns one.
		Zombie zombie = nightMob(helper, EntityTypes.ZOMBIE, new BlockPos(20, 1, 20));
		Zombie tooNear = nightMob(helper, EntityTypes.ZOMBIE, new BlockPos(6, 1, 20));
		for (Mob mob : List.of(zombie, tooNear)) {
			for (EquipmentSlot slot : EquipmentSlot.values()) {
				mob.setItemSlot(slot, ItemStack.EMPTY);
			}
		}
		y.player.snapTo(y.absVec(-9.5, 1, 20.5), 0, 0);
		return new Setup(y, own, other, zombie, tooNear, cause);
	}

	/** An ordinary mob of the night (not persistent), held still for the test. */
	private static <T extends Mob> T nightMob(GameTestHelper helper, EntityType<T> type, BlockPos pos) {
		T mob = helper.spawnEntity(type, pos).requirePersistence(false).spawn();
		mob.setNoAi(true);
		return mob;
	}

	/** Puts the stack in the chest and has him take it (as a chest-opens card would). */
	private static TraceLedger.Entry take(Setup s, BlockPos chest, int slot, ItemStack stack) {
		Container container = (Container) s.y.level.getBlockEntity(chest);
		container.setItem(slot, stack);
		Services.traces().removeStack(s.y.level, chest, slot, stack.getCount(), s.cause);
		return TraceLedger.get(s.y.level.getServer()).entries().stream()
				.filter(e -> e.cause().equals(s.cause) && e.pos().pos().equals(chest) && e.slot() == slot).reduce((a, b) -> b).orElseThrow();
	}

	private static ItemStack named(ServerLevel level, String name) {
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		sword.set(DataComponents.CUSTOM_NAME, Component.literal(name));
		sword.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 2);
		return sword;
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void zombieGetsYourSwordOutOfView(GameTestHelper helper) {
		Setup s = setup(helper);
		Yard y = s.y;
		ServerLevel level = y.level;
		take(s, s.own, 0, new ItemStack(Items.IRON_SWORD));
		TraceLedger.Entry ash = take(s, s.own, 1, named(level, "Ash"));
		take(s, s.own, 2, new ItemStack(Items.BREAD, 3));
		take(s, s.other, 0, new ItemStack(Items.DIAMOND_SWORD));

		// Taken today: it comes back only days later.
		TrapContext today = new TrapContext(level, y.player, y.player.blockPosition(), y.data, Yard.NOBODY, y.cfg, y.now(), y.today());
		helper.assertTrue(s.mine(Traps.ZOMBIE_SWORD.candidates(today), s.zombie).isEmpty(), "a stack taken today came back already");

		List<Candidate> found = s.mine(Traps.ZOMBIE_SWORD.candidates(s.later(Yard.NOBODY)), s.zombie);
		helper.assertTrue(found.size() == 2, "expected the two swords from the player's chest, got " + found.size());
		Candidate spot = found.get(0);
		helper.assertTrue(spot.stolen == ash && spot.slot == EquipmentSlot.MAINHAND, "the renamed, enchanted sword is not picked first");
		helper.assertTrue(found.stream().noneMatch(c -> c.stolen.pos().pos().equals(s.other)), "a stack from a chest the player never placed");
		helper.assertTrue(s.mine(Traps.ZOMBIE_SWORD.candidates(s.later(Yard.NOBODY)), s.tooNear).isEmpty(), "a zombie nearer than 24 blocks was picked");
		// The far edge: just inside the maximum it is offered, just beyond it is not.
		double max = y.cfg.swordZombieMaxDistance;
		y.player.snapTo(y.absVec(20.5 - (max - 0.5), 1, 20.5), 0, 0);
		helper.assertFalse(s.mine(Traps.ZOMBIE_SWORD.candidates(s.later(Yard.NOBODY)), s.zombie).isEmpty(), "a zombie just inside the maximum was not offered");
		y.player.snapTo(y.absVec(20.5 - (max + 1), 1, 20.5), 0, 0);
		helper.assertTrue(s.mine(Traps.ZOMBIE_SWORD.candidates(s.later(Yard.NOBODY)), s.zombie).isEmpty(), "a zombie farther than the maximum was offered");
		y.player.snapTo(y.absVec(-9.5, 1, 20.5), 0, 0);
		// A persistent zombie (someone's pet, or one that already wears something) is never picked.
		Zombie kept = helper.spawnWithNoFreeWill(EntityTypes.ZOMBIE, new BlockPos(20, 1, 16));
		kept.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
		helper.assertTrue(s.mine(Traps.ZOMBIE_SWORD.candidates(s.later(Yard.NOBODY)), kept).isEmpty(), "a persistent zombie was picked");
		kept.discard();
		helper.assertTrue(spot.clue.contains("own sword") && spot.clue.contains("Ash"), "the clue does not say it's yours: " + spot.clue);

		int zombies = level.getEntitiesOfClass(Zombie.class, new AABB(y.abs(0, 0, 0)).inflate(64)).size();
		double health = s.zombie.getAttributeBaseValue(Attributes.MAX_HEALTH);
		double speed = s.zombie.getAttributeBaseValue(Attributes.MOVEMENT_SPEED);
		helper.assertFalse(Traps.ZOMBIE_SWORD.setup(s.later(Yard.EVERYONE), spot), "equipped a zombie in view");
		helper.assertTrue(s.zombie.getMainHandItem().isEmpty() && TraceLedger.get(level.getServer()).entries().contains(ash), "a refused setup changed something");
		helper.assertTrue(Traps.ZOMBIE_SWORD.setup(s.later(Yard.NOBODY), spot), "refused out of view");
		helper.assertTrue(ItemStack.matches(s.zombie.getMainHandItem(), ash.stack().orElseThrow()), "the zombie does not hold exactly that sword");
		TraceLedger.Entry worn = TraceLedger.get(level.getServer()).entries().stream()
				.filter(e -> e.kind() == TraceLedger.Kind.EQUIP && e.entity().filter(s.zombie.getUUID()::equals).isPresent()).findFirst().orElse(null);
		helper.assertTrue(worn != null && worn.pos().pos().equals(s.own) && !TraceLedger.get(level.getServer()).entries().contains(ash),
				"the ledger does not say the zombie wears it now");
		helper.assertTrue(level.getEntitiesOfClass(Zombie.class, new AABB(y.abs(0, 0, 0)).inflate(64)).size() == zombies, "a zombie was spawned");
		helper.assertTrue(s.zombie.getAttributeBaseValue(Attributes.MAX_HEALTH) == health && s.zombie.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) == speed
				&& s.zombie.getActiveEffects().isEmpty() && s.zombie.getTarget() == null, "the zombie was buffed or sent at the player");
		helper.assertTrue(s.zombie.getItemBySlot(EquipmentSlot.HEAD).isEmpty() && s.tooNear.getMainHandItem().isEmpty(), "more than the one stack moved");
		s.zombie.discard();
		s.tooNear.discard();
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void zombieSwordBlamesOnlyThatZombieWhileItHoldsIt(GameTestHelper helper) {
		Setup s = setup(helper);
		Yard y = s.y;
		ServerLevel level = y.level;
		take(s, s.own, 0, named(level, "Rook"));
		Candidate spot = s.mine(Traps.ZOMBIE_SWORD.candidates(s.later(Yard.NOBODY)), s.zombie).getFirst();
		helper.assertTrue(Traps.ZOMBIE_SWORD.setup(s.later(Yard.NOBODY), spot), "not equipped");
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.ZOMBIE_SWORD, s.later(Yard.NOBODY), spot, y.cfg);
		helper.assertTrue(armed.isSet() && armed.blames(s.zombie.getUUID()) && armed.mobs().size() == 1 && armed.worn().isPresent(), "the zombie and stack are not remembered");
		y.data.setArmed(armed);
		AccidentPlannerImpl planner = new AccidentPlannerImpl(server -> y.data, Yard.NOBODY);
		DamageSources damage = level.damageSources();

		helper.assertTrue(planner.causedBy(y.player, damage.mobAttack(s.zombie)), "the zombie with your sword killing you was not claimed");
		helper.assertTrue(AccidentPlannerImpl.listWord(damage.mobAttack(s.zombie), armed).equals("own sword"), "the list word is not 'own sword'");
		helper.assertFalse(planner.causedBy(y.player, damage.mobAttack(s.tooNear)), "a different zombie's kill was claimed");
		helper.assertFalse(planner.causedBy(y.player, damage.fall()), "a fall was claimed by the zombie trap");
		// A zombie that swapped the sword away no longer counts, and the trap ends.
		ItemStack sword = s.zombie.getMainHandItem().copy();
		s.zombie.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.ROTTEN_FLESH));
		helper.assertFalse(planner.causedBy(y.player, damage.mobAttack(s.zombie)), "its kill counted after it lost the sword");
		helper.assertTrue(Traps.ZOMBIE_SWORD.tick(s.later(Yard.NOBODY), armed) == null, "the trap went on after the zombie lost the sword");
		// Worn and torn, it is still the same sword.
		sword.setDamageValue(5);
		s.zombie.setItemSlot(EquipmentSlot.MAINHAND, sword);
		helper.assertTrue(planner.causedBy(y.player, damage.mobAttack(s.zombie)), "a worn sword no longer counted as yours");
		// The zombie dies: the trap is over.
		planner.onMobDied(s.zombie);
		helper.assertTrue(y.data.armed().isEmpty(), "the trap outlived its zombie");
		s.zombie.discard();
		s.tooNear.discard();
		helper.succeed();
	}

	@GameTest
	public void zombieSwordTrapIsSavedWithItsStack(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RegistryOps<Tag> ops = level.registryAccess().createSerializationContext(NbtOps.INSTANCE);
		ItemStack sword = named(level, "Wren");
		UUID zombie = UUID.fromString("00000000-0000-0000-0000-00000000a032");
		ArmedTrap trap = new ArmedTrap("zombie_has_your_sword", level.dimension(), BlockPos.ZERO, List.of(), List.of(), Optional.empty(), List.of(zombie),
				ArmedTrap.Phase.SET, 1, 2, 3, ArmedTrap.NO_CLOCK, BlockPos.ZERO, BlockPos.ZERO, "a clue", 0).withWorn(new ArmedTrap.Worn(sword, EquipmentSlot.MAINHAND));
		Tag saved = ArmedTrap.CODEC.encodeStart(ops, trap).getOrThrow();
		ArmedTrap loaded = ArmedTrap.CODEC.parse(ops, saved).getOrThrow();
		helper.assertTrue(loaded.blames(zombie) && loaded.worn().isPresent() && ItemStack.matches(loaded.worn().get().stack(), sword)
				&& loaded.worn().get().slot() == EquipmentSlot.MAINHAND, "the worn stack did not round-trip");
		helper.assertTrue(StolenGear.kind(sword).equals("sword") && StolenGear.slotFor(new ItemStack(Items.DIAMOND_BOOTS)) == EquipmentSlot.FEET
				&& StolenGear.slotFor(new ItemStack(Items.BOW)) == null && StolenGear.slotFor(new ItemStack(Items.ELYTRA)) == null, "weapon and armor kinds");
		helper.succeed();
	}

	@GameTest(structure = Yard.STRUCTURE, maxTicks = 40)
	public void zombieSwordArmorComesOutOfTheNetworkChestAndTridentsOnlyFitDrowned(GameTestHelper helper) {
		Setup s = setup(helper);
		Yard y = s.y;
		ServerLevel level = y.level;
		BlockPos network = y.abs(8, 1, 2);
		y.set(8, 1, 2, Blocks.CHEST);
		Container own = (Container) level.getBlockEntity(s.own);
		own.setItem(5, new ItemStack(Items.IRON_HELMET));
		helper.assertTrue(Services.traces().moveStack(level, s.own, 5, network, s.cause), "moveStack failed");
		take(s, s.own, 0, new ItemStack(Items.TRIDENT));
		Drowned drowned = nightMob(helper, EntityTypes.DROWNED, new BlockPos(20, 1, 12));
		for (EquipmentSlot slot : EquipmentSlot.values()) {
			drowned.setItemSlot(slot, ItemStack.EMPTY);
		}
		List<Candidate> forZombie = s.mine(Traps.ZOMBIE_SWORD.candidates(s.later(Yard.NOBODY)), s.zombie);
		List<Candidate> forDrowned = s.mine(Traps.ZOMBIE_SWORD.candidates(s.later(Yard.NOBODY)), drowned);
		helper.assertTrue(forZombie.stream().noneMatch(c -> c.stolen.stack().orElseThrow().is(Items.TRIDENT)), "a trident was offered to a plain zombie");
		helper.assertTrue(forDrowned.stream().anyMatch(c -> c.stolen.stack().orElseThrow().is(Items.TRIDENT)), "the trident was not offered to the drowned");
		Candidate helmet = forZombie.stream().filter(c -> c.stolen.kind() == TraceLedger.Kind.MOVE_STACK).findFirst().orElse(null);
		helper.assertTrue(helmet != null && helmet.slot == EquipmentSlot.HEAD, "the helmet in the network chest was not offered for the head");
		helper.assertTrue(Traps.ZOMBIE_SWORD.setup(s.later(Yard.NOBODY), helmet), "the helmet was not put on");
		helper.assertTrue(s.zombie.getItemBySlot(EquipmentSlot.HEAD).is(Items.IRON_HELMET) && ((Container) level.getBlockEntity(network)).getItem(0).isEmpty(),
				"the helmet did not come out of the network chest onto the zombie");
		ArmedTrap armed = AccidentPlannerImpl.build(Traps.ZOMBIE_SWORD, s.later(Yard.NOBODY), helmet, y.cfg);
		helper.assertTrue(AccidentPlannerImpl.listWord(level.damageSources().mobAttack(s.zombie), armed).equals("own helmet"), "the list word is not 'own helmet'");
		// The card only fits where there is a night to have it in.
		ServerLevel end = level.getServer().getLevel(Level.END);
		ServerLevel nether = level.getServer().getLevel(Level.NETHER);
		helper.assertFalse(Traps.ZOMBIE_SWORD.contextFits(y.player, end, y.cfg) || Traps.ZOMBIE_SWORD.contextFits(y.player, nether, y.cfg), "fits without a night");
		helper.assertTrue(Traps.ZOMBIE_SWORD.contextFits(y.player, level, y.cfg) == Scan.night(level, y.cfg), "fits in the overworld by day, or not at night");
		s.zombie.discard();
		s.tooNear.discard();
		drowned.discard();
		helper.succeed();
	}
}
