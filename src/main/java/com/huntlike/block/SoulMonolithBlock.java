package com.huntlike.block;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Soul Monolith: every player inside its 17x17x17 cube (8 blocks out in every direction) has Resistance I;
 * several Monoliths don't stack. Creepers flee from it and can't explode inside its area. The upgraded Soul Monolith ★
 * (4 Soul Stones + 4 Slimy Cores) reaches 1 block further and also gives Regeneration I.
 */
public class SoulMonolithBlock extends Block implements EntityBlock {
	/** The side the Monolith's face looks to: towards whoever placed it. */
	public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
	public static final int RADIUS = 8;
	/** The upgraded Monolith's reach. */
	public static final int UPGRADED_RADIUS = 9;
	private static final int INTERVAL = 5;
	private static final int FLEE_RADIUS = 12;

	/** Monoliths known to be active, per dimension. */
	private static final Map<ResourceKey<Level>, Set<BlockPos>> ACTIVE = new HashMap<>();

	private final boolean upgraded;

	public SoulMonolithBlock(Properties properties) {
		this(properties, false);
	}

	public SoulMonolithBlock(Properties properties, boolean upgraded) {
		super(properties);
		this.upgraded = upgraded;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public boolean upgraded() {
		return upgraded;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		return state.rotate(mirror.getRotation(state.getValue(FACING)));
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new SoulModelBlockEntity(pos, state);
	}

	public int radius() {
		return upgraded ? UPGRADED_RADIUS : RADIUS;
	}

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		super.onPlace(state, level, pos, oldState, movedByPiston);
		level.scheduleTick(pos, this, INTERVAL);
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		// Restarts the effect loop for Monoliths placed before this version.
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!level.getBlockTicks().hasScheduledTick(pos, this)) {
			level.scheduleTick(pos, this, INTERVAL);
		}
	}

	@Override
	protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		ACTIVE.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
		AABB area = area(pos, radius());
		for (ServerPlayer player : level.getEntitiesOfClass(ServerPlayer.class, area)) {
			player.addEffect(new MobEffectInstance(MobEffects.RESISTANCE, 40, 0, true, false, true));
			if (upgraded) {
				// Regeneration only heals on whole multiples of its interval: refresh it before it runs low, not every
				// few ticks, so it keeps healing at the Regeneration I rate.
				MobEffectInstance regen = player.getEffect(MobEffects.REGENERATION);
				if (regen == null || regen.getAmplifier() == 0 && regen.getDuration() <= 60) {
					player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0, true, false, true));
				}
			}
		}
		Vec3 center = Vec3.atCenterOf(pos);
		for (Creeper creeper : level.getEntitiesOfClass(Creeper.class, area.inflate(FLEE_RADIUS - RADIUS))) {
			Vec3 away = creeper.position().subtract(center).multiply(1, 0, 1);
			if (away.lengthSqr() < 0.01) {
				away = new Vec3(1, 0, 0);
			}
			Vec3 target = center.add(away.normalize().scale(FLEE_RADIUS + 4));
			creeper.getNavigation().moveTo(target.x, creeper.getY(), target.z, 1.3);
			creeper.setSwellDir(-1);
		}
		level.scheduleTick(pos, this, INTERVAL);
	}

	private static AABB area(BlockPos pos, int radius) {
		return new AABB(pos.getX() - radius, pos.getY() - radius, pos.getZ() - radius,
				pos.getX() + radius + 1, pos.getY() + radius + 1, pos.getZ() + radius + 1);
	}

	/** Whether a position is inside an active Monolith's cube. */
	public static boolean protects(Level level, BlockPos at) {
		Set<BlockPos> monoliths = ACTIVE.get(level.dimension());
		if (monoliths == null || monoliths.isEmpty()) {
			return false;
		}
		monoliths.removeIf(pos -> level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof SoulMonolithBlock));
		for (BlockPos pos : monoliths) {
			int radius = level.getBlockState(pos).getBlock() instanceof SoulMonolithBlock monolith ? monolith.radius() : RADIUS;
			if (Math.abs(at.getX() - pos.getX()) <= radius && Math.abs(at.getY() - pos.getY()) <= radius
					&& Math.abs(at.getZ() - pos.getZ()) <= radius) {
				return true;
			}
		}
		return false;
	}
}
