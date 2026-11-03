package com.huntlike.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.huntlike.registry.ModBlocks;

/** The Alpha Altar's block entity: no data, only there so the client can draw the altar's model. */
public class AlphaAltarBlockEntity extends BlockEntity {
	public AlphaAltarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ALPHA_ALTAR_ENTITY, pos, state);
	}
}
