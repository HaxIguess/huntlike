package com.huntlike.registry;

import java.util.function.Consumer;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;

import com.huntlike.HuntLike;
import com.huntlike.gear.HideBoots;
import com.huntlike.gear.ModGear;
import com.huntlike.gear.ModKoro;

/** The HuntLike tabs in the creative inventory: Drops, Gear, Blocks and Paths, in that order. */
public final class ModCreativeTab {
	private ModCreativeTab() {
	}

	/** Every drop from HuntLike mobs, bosses and Spirit Lure animals, and the Mystery Meat chain. */
	public static final ResourceKey<CreativeModeTab> DROPS = key("drops");
	/** Armor, weapons, special items and anything else for combat. */
	public static final ResourceKey<CreativeModeTab> GEAR = key("gear");
	/** Every block the mod adds. */
	public static final ResourceKey<CreativeModeTab> BLOCKS = key("blocks");
	/** Every drop and item from the Paths (Soul Nexus questlines). */
	public static final ResourceKey<CreativeModeTab> PATHS = key("paths");

	private static ResourceKey<CreativeModeTab> key(String id) {
		return ResourceKey.create(BuiltInRegistries.CREATIVE_MODE_TAB.key(), HuntLike.id(id));
	}

	private static void register(ResourceKey<CreativeModeTab> key, ItemLike icon, Consumer<CreativeModeTab.Output> items) {
		CreativeModeTab tab = FabricCreativeModeTab.builder()
				.icon(() -> new ItemStack(icon))
				.title(Component.translatable("itemGroup.huntlike." + key.identifier().getPath()))
				.displayItems((params, output) -> items.accept(output))
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, tab);
	}

	public static void initialize() {
		register(DROPS, ModItems.LOW_QUALITY_MYSTERY_MEAT, output -> {
			output.accept(ModItems.LOW_QUALITY_MYSTERY_MEAT);
			output.accept(ModItems.DECENT_QUALITY_MYSTERY_MEAT);
			output.accept(ModItems.HIGH_QUALITY_MYSTERY_MEAT);
			output.accept(ModItems.THICK_HIDE);
			output.accept(ModItems.LARGE_FEMUR);
			output.accept(ModItems.GOLDEN_MOSS);
			output.accept(ModItems.SPIRITUAL_CATALYST);
			output.accept(ModItems.SOUL_STONE);
			output.accept(ModItems.THERMAL_SLIMEBALL);
			output.accept(ModItems.FROZEN_SLIME_BALL);
			output.accept(ModItems.GELID_SLIME_BALL);
			output.accept(ModItems.SLIMYSTEEL);
			output.accept(ModItems.SLIMY_CORE);
			output.accept(ModItems.IONIC_LIQUID);
			output.accept(ModItems.SUBLIMATING_MAGMA);
			output.accept(ModItems.CORRODED_PALLADIUM);
			output.accept(ModItems.SLIME_WASHED_HOLMIUM);
		});
		register(GEAR, ModGear.SKIN_KNIFE, output -> {
			ModGear.WEAPONS.forEach(output::accept);
			com.huntlike.gear.ModScythes.SCYTHES.forEach(output::accept);
			com.huntlike.gear.ModShields.SHIELDS.forEach(output::accept);
			output.accept(com.huntlike.gear.ModShields.MAGNETIC_WAND);
			output.accept(com.huntlike.gear.ModShields.MAGNETIC_WAND_PRD);
			output.accept(com.huntlike.interlude.ModInterlude.GROWING_SLIME_BALL);
			output.accept(com.huntlike.interlude.ModInterlude.POMS_SKELETAL_REMAINS);
			output.accept(com.huntlike.interlude.ModInterlude.HORSESHOE);
			output.accept(com.huntlike.interlude.ModInterlude.BOUNCY_SLIME_BALL);
			ModGear.ARMOR_SETS.forEach(set -> set.pieces().forEach(output::accept));
			output.accept(ModKoro.WOODEN_BELL);
			output.accept(ModKoro.GOLDEN_BELL);
			output.accept(ModKoro.MOSSY_BELL);
			output.accept(ModKoro.KORO_WHAKAMATE);
			HideBoots.BOOTS.forEach(output::accept);
			output.accept(HideBoots.FEATHER_CHUTE);
			output.accept(com.huntlike.interlude.ModInterlude.SLIMY_REBOUNDERS);
			output.accept(ModItems.MYSTERY_CONCOCTION);
			output.accept(ModItems.WARM_AND_SLIMY_BEEF_JERKY);
			output.accept(ModItems.GOLDEN_MEAT);
			output.accept(ModItems.SPIRITUAL_COMPASS);
			output.accept(com.huntlike.tools.ModTools.SPIRITUAL_COMPASS_STAR);
			output.accept(com.huntlike.tools.ModTools.FEMURANG);
			output.accept(com.huntlike.tools.ModTools.BLESSING_SCROLL);
		});
		register(BLOCKS, ModBlocks.ALPHA_ALTAR, output -> {
			output.accept(ModBlocks.ALPHA_ALTAR);
			output.accept(ModBlocks.SOUL_NEXUS);
			output.accept(ModItems.A_PHONE);
			output.accept(ModBlocks.SOUL_MONOLITH);
			output.accept(ModBlocks.UPGRADED_SOUL_MONOLITH);
			output.accept(ModBlocks.ANIMAL_TROPHY);
		});
		register(PATHS, com.huntlike.path.ModPaths.BLAZING_HEART, output -> {
			// Path of the Wildfire
			output.accept(com.huntlike.path.ModPaths.BLAZING_HEART);
			output.accept(com.huntlike.path.ModPaths.BLAZING_HEART_STAR);
			output.accept(com.huntlike.path.ModPaths.BLAZING_HEART_STAR2);
			output.accept(com.huntlike.path.ModPaths.BLAZING_HEART_STAR3);
			output.accept(com.huntlike.path.ModPaths.BLAZING_SPAWNER_ITEM);
			output.accept(com.huntlike.path.ModPaths.CHALICE_ITEM);
			output.accept(com.huntlike.path.ModPaths.FULL_CHALICE);
			output.accept(com.huntlike.path.ModPaths.BLOOD_CRYSTALLIZATION);
		});
	}
}
