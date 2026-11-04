package com.huntlike.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import com.huntlike.HuntLike;
import com.huntlike.block.AlphaAltarBlock;

/** Every HuntLike block. */
public final class ModBlocks {
	private ModBlocks() {
	}

	/** Opens the Hunt menu. */
	public static final Block ALPHA_ALTAR = register("alpha_altar", AlphaAltarBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(3.0F, 1200.0F).sound(SoundType.BONE_BLOCK)
					.requiresCorrectToolForDrops().lightLevel(state -> 7).noOcclusion(),
			Rarity.RARE);

	/** The Alpha Altar's block entity: only there so its model (with the end portal sides) can be drawn. */
	public static final net.minecraft.world.level.block.entity.BlockEntityType<com.huntlike.block.AlphaAltarBlockEntity> ALPHA_ALTAR_ENTITY =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, HuntLike.id("alpha_altar"),
					new net.minecraft.world.level.block.entity.BlockEntityType<>(com.huntlike.block.AlphaAltarBlockEntity::new,
							java.util.Set.of(ALPHA_ALTAR)));

	/** Resistance I and creeper protection in a 17x17x17 cube. */
	public static final Block SOUL_NEXUS = register("soul_nexus", com.huntlike.block.SoulNexusBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_CYAN).strength(3.0F, 1200.0F).sound(SoundType.SCULK_CATALYST)
					.requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 7),
			Rarity.EPIC);

	public static final Block SOUL_MONOLITH = register("soul_monolith", com.huntlike.block.SoulMonolithBlock::new,
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(4.0F, 1200.0F).sound(SoundType.STONE)
					.requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 10),
			Rarity.EPIC);

	/** Soul Monolith ★: 4 Soul Stones + 4 Slimy Cores around a Monolith. One block more reach, plus Regeneration I. */
	public static final Block UPGRADED_SOUL_MONOLITH = register("upgraded_soul_monolith",
			properties -> new com.huntlike.block.SoulMonolithBlock(properties, true),
			BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(4.0F, 1200.0F).sound(SoundType.STONE)
					.requiresCorrectToolForDrops().noOcclusion().lightLevel(state -> 12),
			Rarity.EPIC);

	/** Lets the client draw the Soul Nexus' and Soul Monoliths' models. */
	public static final net.minecraft.world.level.block.entity.BlockEntityType<com.huntlike.block.SoulModelBlockEntity> SOUL_MODEL_ENTITY =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, HuntLike.id("soul_model"),
					new net.minecraft.world.level.block.entity.BlockEntityType<>(com.huntlike.block.SoulModelBlockEntity::new,
							java.util.Set.of(SOUL_NEXUS, SOUL_MONOLITH, UPGRADED_SOUL_MONOLITH)));

	/** Given once, at the 100th Tier V Animal boss. */
	public static final Block ANIMAL_TROPHY = registerTrophy("animal_trophy");

	public static final net.minecraft.world.level.block.entity.BlockEntityType<com.huntlike.block.TrophyBlockEntity> TROPHY_ENTITY =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, HuntLike.id("trophy"),
					new net.minecraft.world.level.block.entity.BlockEntityType<>(com.huntlike.block.TrophyBlockEntity::new,
							java.util.Set.of(ANIMAL_TROPHY)));

	private static Block registerTrophy(String name) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, HuntLike.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, new com.huntlike.block.TrophyBlock(
				BlockBehaviour.Properties.of().mapColor(MapColor.GOLD).strength(1.0F, 1200.0F).sound(SoundType.METAL)
						.noCollision().noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.POPPED)
						.setId(blockKey)));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, HuntLike.id(name));
		Item.Properties itemProperties = new Item.Properties().rarity(Rarity.EPIC).stacksTo(1).useBlockDescriptionPrefix()
				.setId(itemKey);
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
		return block;
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory,
			BlockBehaviour.Properties properties, Rarity rarity) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, HuntLike.id(name));
		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey, factory.apply(properties.setId(blockKey)));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, HuntLike.id(name));
		Item.Properties itemProperties = Descriptions.with(
				new Item.Properties().rarity(rarity).useBlockDescriptionPrefix().setId(itemKey),
				"block.huntlike." + name + ".desc");
		Registry.register(BuiltInRegistries.ITEM, itemKey, new BlockItem(block, itemProperties));
		return block;
	}

	public static void initialize() {
		// Loading this class registers the blocks.
	}
}
