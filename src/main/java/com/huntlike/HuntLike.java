package com.huntlike;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import com.huntlike.command.HuntCommand;
import com.huntlike.entity.ModEntities;
import com.huntlike.gear.ModGear;
import com.huntlike.hunt.QuestManager;
import com.huntlike.loot.ModLoot;
import com.huntlike.network.HuntPayloads;
import com.huntlike.registry.ModBlocks;
import com.huntlike.registry.ModCreativeTab;
import com.huntlike.registry.ModItems;
import com.huntlike.registry.ModSounds;

/** Main entrypoint for HuntLike. */
public class HuntLike implements ModInitializer {
	public static final String MOD_ID = "huntlike";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		ModItems.initialize();
		com.huntlike.interlude.ModInterlude.initialize();
		ModSounds.initialize();
		ModGear.initialize();
		com.huntlike.gear.ModScythes.initialize();
		com.huntlike.gear.ModShields.initialize();
		com.huntlike.pet.PetManager.initialize();
		com.huntlike.pet.PetMenu.initialize();
		com.huntlike.gear.ModKoro.initialize();
		com.huntlike.gear.HideBoots.initialize();
		ModBlocks.initialize();
		ModCreativeTab.initialize();
		ModLoot.initialize();
		ModEntities.initialize();
		HuntPayloads.initialize();
		QuestManager.initialize();
		com.huntlike.rogue.RogueHunters.initialize();
		com.huntlike.towernaar.TowernaarFight.initialize();
		com.huntlike.hunt.WolfTreats.initialize();
		HuntCommand.initialize();
		com.huntlike.nexus.SoulNexus.initialize();
		com.huntlike.path.ModPaths.initialize();
		com.huntlike.potion.ModPotions.initialize();
		LOGGER.info("HuntLike loaded. The Alpha Altar is listening...");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
