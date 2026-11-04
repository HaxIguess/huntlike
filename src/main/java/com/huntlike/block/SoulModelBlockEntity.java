package com.huntlike.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.huntlike.registry.ModBlocks;

/** No data: lets the client draw the Soul Nexus' and Soul Monoliths' Blockbench models (SoulModelRenderer). */
public class SoulModelBlockEntity extends BlockEntity {
	public SoulModelBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.SOUL_MODEL_ENTITY, pos, state);
	}
}
