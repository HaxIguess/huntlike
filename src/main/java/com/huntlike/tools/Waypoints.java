package com.huntlike.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import net.fabricmc.fabric.api.event.player.UseBlockCallback;

import com.huntlike.hunt.HuntData;
import com.huntlike.hunt.HuntData.Waypoint;
import com.huntlike.hunt.QuestManager;

/**
 * Spiritual Compass ★ waypoints, kept on the player (every starred compass they hold shares them). A lodestone becomes a
 * waypoint when right-clicked with the compass; right-clicking a registered lodestone with anything else (not sneaking)
 * opens its menu: name, dye and remove.
 */
public final class Waypoints {
	private Waypoints() {
	}

	public static final int MAX_NAME = 32;

	/** The client's copy of the waypoints, for its own clicks (set by the client). */
	public static java.util.function.BiPredicate<Level, BlockPos> clientHasWaypoint = (level, pos) -> false;

	public static String dimension(Level level) {
		return level.dimension().identifier().toString();
	}

	public static void initialize() {
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (player.isSpectator() || player.isSecondaryUseActive() || !level.getBlockState(hit.getBlockPos()).is(Blocks.LODESTONE)) {
				return InteractionResult.PASS;
			}
			ItemStack held = player.getItemInHand(hand);
			if (held.is(ModTools.SPIRITUAL_COMPASS_STAR) || held.is(Items.COMPASS)) {
				// The ★ compass registers it; a plain compass still links to it as in vanilla.
				return InteractionResult.PASS;
			}
			BlockPos pos = hit.getBlockPos();
			if (level.isClientSide()) {
				return clientHasWaypoint.test(level, pos) ? InteractionResult.SUCCESS : InteractionResult.PASS;
			}
			ServerPlayer serverPlayer = (ServerPlayer) player;
			if (find(serverPlayer, pos) == null) {
				return InteractionResult.PASS;
			}
			open(serverPlayer, pos);
			return InteractionResult.SUCCESS;
		});
	}

	public static Waypoint find(ServerPlayer player, BlockPos pos) {
		return QuestManager.data(player).waypoint(dimension(player.level()), pos.getX(), pos.getY(), pos.getZ());
	}

	private static void save(ServerPlayer player, HuntData data) {
		player.setAttached(QuestManager.DATA, data);
		QuestManager.sync(player);
	}

	/** A lodestone right-clicked with the ★ compass: a new waypoint, "Lodestone N". */
	public static void register(ServerPlayer player, ServerLevel level, BlockPos pos) {
		HuntData data = QuestManager.data(player);
		Waypoint existing = data.waypoint(dimension(level), pos.getX(), pos.getY(), pos.getZ());
		if (existing != null) {
			player.sendOverlayMessage(Component.literal("This lodestone is already your waypoint ").withStyle(style -> style.withColor(0xAAAAAA))
					.append(name(existing)));
			return;
		}
		int number = 1;
		while (nameTaken(data, "Lodestone " + number)) {
			number++;
		}
		Waypoint waypoint = new Waypoint("Lodestone " + number, dimension(level), pos.getX(), pos.getY(), pos.getZ());
		data.waypoints.add(waypoint);
		if (data.target() == null) {
			data.waypointTarget = waypoint.key();
		}
		save(player, data);
		level.playSound(null, pos, SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 1.0F, 1.0F);
		player.sendOverlayMessage(Component.literal("Waypoint added: ").withStyle(style -> style.withColor(0x55FFFF)).append(name(waypoint)));
	}

	private static boolean nameTaken(HuntData data, String name) {
		for (Waypoint waypoint : data.waypoints) {
			if (waypoint.name.equals(name)) {
				return true;
			}
		}
		return false;
	}

	public static MutableComponent name(Waypoint waypoint) {
		return Component.literal(waypoint.name).withStyle(style -> style.withColor(waypoint.color < 0 ? 0xFFFFFF : waypoint.color));
	}

	private static void open(ServerPlayer player, BlockPos pos) {
		Waypoint waypoint = find(player, pos);
		player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new LodestoneMenu(id, inventory, pos),
				Component.literal(waypoint == null ? "Lodestone" : waypoint.name)));
	}

	/** From the lodestone menu's name box. */
	public static void rename(ServerPlayer player, String name) {
		if (!(player.containerMenu instanceof LodestoneMenu menu)) {
			return;
		}
		Waypoint waypoint = find(player, menu.pos());
		String clean = name.strip();
		if (waypoint == null || clean.isEmpty()) {
			return;
		}
		waypoint.name = clean.length() > MAX_NAME ? clean.substring(0, MAX_NAME) : clean;
		save(player, QuestManager.data(player));
	}

	/** The menu's Dye Waypoint button: one dye from the slot colors the waypoint. */
	static boolean dye(ServerPlayer player, BlockPos pos, ItemStack dye) {
		Waypoint waypoint = find(player, pos);
		net.minecraft.world.item.DyeColor color = dye.get(net.minecraft.core.component.DataComponents.DYE);
		if (waypoint == null || color == null) {
			return false;
		}
		waypoint.color = color.getTextColor();
		save(player, QuestManager.data(player));
		player.level().playSound(null, pos, SoundEvents.DYE_USE, SoundSource.PLAYERS, 1.0F, 1.0F);
		return true;
	}

	/** The menu's Remove Waypoint button. */
	static void remove(ServerPlayer player, BlockPos pos) {
		HuntData data = QuestManager.data(player);
		Waypoint waypoint = find(player, pos);
		if (waypoint == null) {
			return;
		}
		data.waypoints.remove(waypoint);
		if (waypoint.key().equals(data.waypointTarget)) {
			data.waypointTarget = "";
		}
		save(player, data);
		player.level().playSound(null, pos, SoundEvents.LODESTONE_BREAK, SoundSource.PLAYERS, 0.8F, 1.2F);
		player.sendOverlayMessage(Component.literal("Waypoint removed: ").withStyle(style -> style.withColor(0xAAAAAA)).append(name(waypoint)));
	}

	/** From the waypoint list: guide to the waypoint at this index. */
	public static void target(ServerPlayer player, int index) {
		HuntData data = QuestManager.data(player);
		if (index < 0 || index >= data.waypoints.size()) {
			return;
		}
		data.waypointTarget = data.waypoints.get(index).key();
		save(player, data);
		player.level().playSound(null, player.blockPosition(), SoundEvents.LODESTONE_COMPASS_LOCK, SoundSource.PLAYERS, 0.8F, 1.4F);
	}

	private static final String[] ARROWS = {"⬆", "⬈", "➡", "⬊", "⬇", "⬋", "⬅", "⬉"};

	/** While a Guiding compass is held: an arrow toward the waypoint, its name and distance, on the action bar. */
	static void showWay(ServerPlayer player) {
		HuntData data = QuestManager.data(player);
		Waypoint waypoint = data.target();
		if (waypoint == null) {
			player.sendOverlayMessage(Component.literal(data.waypoints.isEmpty() ? "No waypoints yet: right-click a lodestone with the compass"
					: "No waypoint chosen: right-click to pick one").withStyle(style -> style.withColor(0xAAAAAA)));
			return;
		}
		if (!waypoint.dimension.equals(dimension(player.level()))) {
			String place = waypoint.dimension.substring(waypoint.dimension.indexOf(':') + 1).replace('_', ' ');
			player.sendOverlayMessage(name(waypoint).append(Component.literal(" is in " + place).withStyle(style -> style.withColor(0xAAAAAA))));
			return;
		}
		double dx = waypoint.x + 0.5 - player.getX();
		double dz = waypoint.z + 0.5 - player.getZ();
		double distance = Math.sqrt(dx * dx + dz * dz);
		MutableComponent line;
		if (distance < 2.0) {
			line = Component.literal("● ").withStyle(style -> style.withColor(0x55FFFF));
		} else {
			// Minecraft's yaw: 0 faces south (+z), 90 faces west (-x); turning right raises it.
			float toward = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float relative = Mth.wrapDegrees(toward - player.getYRot());
			int index = Math.floorMod(Math.round(relative / 45.0F), 8);
			line = Component.literal(ARROWS[index] + " ").withStyle(style -> style.withColor(0x55FFFF));
		}
		line.append(name(waypoint)).append(Component.literal("  " + (int) Math.round(distance) + " m").withStyle(style -> style.withColor(0xAAAAAA)));
		BlockPos pos = new BlockPos(waypoint.x, waypoint.y, waypoint.z);
		if (player.level().isLoaded(pos) && !player.level().getBlockState(pos).is(Blocks.LODESTONE)) {
			line.append(Component.literal("  (lodestone missing)").withStyle(style -> style.withColor(0xFF5555)));
		}
		player.sendOverlayMessage(line);
	}

	public static boolean mayUse(Player player, BlockPos pos) {
		return player.level().getBlockState(pos).is(Blocks.LODESTONE) && player.distanceToSqr(pos.getCenter()) <= 64.0;
	}
}
