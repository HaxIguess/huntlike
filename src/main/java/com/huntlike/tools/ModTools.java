package com.huntlike.tools;

import java.util.List;

import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.ItemLore;

import com.huntlike.HuntLike;
import com.huntlike.registry.Lore;
import com.huntlike.registry.ModItems;

/** The Femurang, the Blessing Scroll, the Spiritual Compass ★ (waypoints) and the Corroded Palladium pickaxe upgrade. */
public final class ModTools {
	private ModTools() {
	}

	public static final Item FEMURANG = ModItems.register("femurang", FemurangItem::new, new Item.Properties().stacksTo(1)
			.rarity(Rarity.RARE).enchantable(10).component(DataComponents.LORE, new ItemLore(femurangLore())));

	public static final Item BLESSING_SCROLL = ModItems.register("blessing_scroll", BlessingScrollItem::new,
			new Item.Properties().durability(BlessingScrollItem.USES).rarity(Rarity.RARE)
					.component(DataComponents.LORE, new ItemLore(scrollLore())));

	public static final Item SPIRITUAL_COMPASS_STAR = ModItems.register("spiritual_compass_star", SpiritualCompassStarItem::new,
			new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).component(DataComponents.LORE,
					new ItemLore(SpiritualCompassStarItem.starLore())));

	private static final ResourceKey<EntityType<?>> FEMURANG_KEY = ResourceKey.create(Registries.ENTITY_TYPE, HuntLike.id("femurang"));
	public static final EntityType<FemurangEntity> FEMURANG_ENTITY = Registry.register(BuiltInRegistries.ENTITY_TYPE, FEMURANG_KEY,
			EntityType.Builder.<FemurangEntity>of(FemurangEntity::new, MobCategory.MISC)
					.sized(0.5F, 0.5F)
					.clientTrackingRange(6)
					.updateInterval(1)
					.noSave()
					.build(FEMURANG_KEY));

	private static List<Component> femurangLore() {
		java.util.List<Component> lore = new java.util.ArrayList<>();
		Lore.gray(lore, "A bone that always finds its way home.");
		Lore.ability(lore, "Throw", "Right-click to throw it up to 20 blocks. It deals 6 damage, presses buttons and comes back to you.");
		return lore;
	}

	private static List<Component> scrollLore() {
		java.util.List<Component> lore = new java.util.ArrayList<>();
		Lore.gray(lore, "Blessed by the village's cleric.");
		Lore.ability(lore, "Blessing", "Right-click for Regeneration V for 1 second.");
		return lore;
	}

	public static void initialize() {
		Waypoints.initialize();
		LodestoneMenu.TYPE.getClass();
	}
}
