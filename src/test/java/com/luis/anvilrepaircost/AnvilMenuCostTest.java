package com.luis.anvilrepaircost;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.EntityEquipment;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Usa un AnvilMenu real de 26.2 (con el mixin aplicado) y compara 100 % (vanilla), 50 % y 0 %. */
class AnvilMenuCostTest {
	static Holder<Enchantment> FEATHER_FALLING;
	static Holder<Enchantment> PROTECTION;

	@BeforeAll
	static void bootstrap(@TempDir Path dir) throws Exception {
		AnvilConfig.useFile(dir.resolve("anvilrepaircost.json"));
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		HolderLookup.Provider lookup = VanillaRegistries.createLookup();
		BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(lookup).forEach(p -> p.apply());

		// Las etiquetas no se cargan fuera de un mundo: se enlazan vacías, salvo la de reparación con diamantes.
		var bindTags = Holder.Reference.class.getDeclaredMethod("bindTags", Collection.class);
		bindTags.setAccessible(true);
		for (Holder.Reference<Item> item : (Iterable<Holder.Reference<Item>>) BuiltInRegistries.ITEM.listElements()::iterator) {
			bindTags.invoke(item, item.value() == Items.DIAMOND ? Set.of(ItemTags.REPAIRS_DIAMOND_ARMOR) : Set.of());
		}
		var enchantments = lookup.lookupOrThrow(Registries.ENCHANTMENT);
		for (Holder.Reference<Enchantment> e : (Iterable<Holder.Reference<Enchantment>>) enchantments.listElements()::iterator) {
			bindTags.invoke(e, Set.of());
		}
		FEATHER_FALLING = enchantments.getOrThrow(Enchantments.FEATHER_FALLING);
		PROTECTION = enchantments.getOrThrow(Enchantments.PROTECTION);
	}

	record Result(int cost, boolean empty, int savedRepairCost) {}

	static Result run(boolean creative, int percent, ItemStack input, ItemStack addition, String name) throws Exception {
		AnvilConfig.set(percent);
		Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
		unsafeField.setAccessible(true);
		sun.misc.Unsafe unsafe = (sun.misc.Unsafe) unsafeField.get(null);
		Player player = (Player) unsafe.allocateInstance(ServerPlayer.class);
		Abilities abilities = new Abilities();
		abilities.instabuild = creative;
		unsafe.putObject(player, unsafe.objectFieldOffset(Player.class.getDeclaredField("abilities")), abilities);

		AnvilMenu menu = new AnvilMenu(1, new Inventory(player, new EntityEquipment()));
		menu.getSlot(0).set(input.copy());
		menu.getSlot(1).set(addition.copy());
		if (name != null) {
			menu.setItemName(name);
		}
		menu.createResult();
		ItemStack result = menu.getSlot(2).getItem();
		return new Result(menu.getCost(), result.isEmpty(), result.getOrDefault(DataComponents.REPAIR_COST, 0));
	}

	static ItemStack boots(int repairCost, int damage) {
		ItemStack stack = new ItemStack(Items.DIAMOND_BOOTS);
		if (repairCost > 0) {
			stack.set(DataComponents.REPAIR_COST, repairCost);
		}
		stack.setDamageValue(damage);
		return stack;
	}

	static ItemStack book(Holder<Enchantment> enchantment, int level) {
		ItemStack stack = new ItemStack(Items.ENCHANTED_BOOK);
		EnchantmentHelper.updateEnchantments(stack, m -> m.set(enchantment, level));
		return stack;
	}

	static void costs(boolean creative, ItemStack in, ItemStack add, String name, int vanilla, int half, int zero) throws Exception {
		assertEquals(vanilla, run(creative, 100, in, add, name).cost(), "100 %");
		assertEquals(half, run(creative, 50, in, add, name).cost(), "50 %");
		assertEquals(zero, run(creative, 0, in, add, name).cost(), "0 %");
	}

	@Test
	void renameKeepsBaseCostAndScalesPenalty() throws Exception {
		costs(false, boots(31, 0), ItemStack.EMPTY, "x", 32, 17, 1);
		costs(false, boots(0, 0), ItemStack.EMPTY, "x", 1, 1, 1);
	}

	@Test
	void enchantCostIsUnchangedOnlyPenaltyScales() throws Exception {
		ItemStack old = boots(31, 0);
		EnchantmentHelper.updateEnchantments(old, m -> { m.set(PROTECTION, 3); m.set(FEATHER_FALLING, 2); });
		costs(true, old, book(FEATHER_FALLING, 2), null, 34, 19, 3);
		costs(true, boots(0, 0), book(FEATHER_FALLING, 2), null, 2, 2, 2);
	}

	@Test
	void combiningDamagedItems() throws Exception {
		costs(false, boots(7, 300), boots(3, 300), null, 12, 7, 2);
		costs(false, boots(15, 300), boots(15, 300), null, 32, 17, 2);
	}

	@Test
	void repairWithMaterial() throws Exception {
		ItemStack diamonds = new ItemStack(Items.DIAMOND, 4);
		Result vanilla = run(false, 100, boots(15, 300), diamonds, null);
		Result half = run(false, 50, boots(15, 300), diamonds, null);
		Result zero = run(false, 0, boots(15, 300), diamonds, null);
		assertTrue(!vanilla.empty() && !half.empty() && !zero.empty());
		// La parte de reparación es igual en los tres; solo cambia la penalización (15 -> 8 -> 0).
		assertEquals(vanilla.cost() - 15, half.cost() - 8);
		assertEquals(vanilla.cost() - 15, zero.cost());
	}

	@Test
	void tooExpensiveLimitStillApplies() throws Exception {
		assertTrue(run(false, 100, boots(30, 300), boots(30, 300), null).empty());
		assertTrue(!run(false, 50, boots(30, 300), boots(30, 300), null).empty());
		assertTrue(run(false, 50, boots(40, 300), boots(40, 300), null).empty());
		assertTrue(!run(true, 50, boots(40, 300), boots(40, 300), null).empty());
		assertEquals(39, run(false, 100, boots(79, 0), ItemStack.EMPTY, "x").cost());
		assertEquals(39, run(false, 50, boots(79, 0), ItemStack.EMPTY, "x").cost());
	}

	@Test
	void savedRepairCostDoesNotDependOnPercent() throws Exception {
		for (int percent : new int[] {50, 0}) {
			assertEquals(63, run(false, percent, boots(31, 300), boots(31, 300), null).savedRepairCost());
		}
	}

	@Test
	void incompatibleItemsStillRejected() throws Exception {
		Result r = run(false, 50, new ItemStack(Items.STICK), boots(0, 0), null);
		assertTrue(r.empty());
		assertEquals(0, r.cost());
	}
}
