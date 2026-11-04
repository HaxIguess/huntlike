package com.huntlike.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import com.huntlike.registry.ModBlocks;

/** The Alpha Altar's block entity: no data; it lets the client draw the altar and remembers when it was last clicked. */
public class AlphaAltarBlockEntity extends BlockEntity {
	public static final int EVENT_PULSE = 1;
	/** Game time of the last click (client side), for the center's pulse; -1 = never. */
	public long pulseStart = -1;

	public AlphaAltarBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.ALPHA_ALTAR_ENTITY, pos, state);
	}

	@Override
	public boolean triggerEvent(int id, int param) {
		if (id == EVENT_PULSE) {
			if (this.level != null) {
				this.pulseStart = this.level.getGameTime();
			}
			return true;
		}
		return super.triggerEvent(id, param);
	}
}
