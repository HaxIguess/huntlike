package com.huntlike.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

import com.huntlike.HuntLike;

/** Packets between the server and the Hunt menu. */
public final class HuntPayloads {
	private HuntPayloads() {
	}

	/** Server to client: the player's Hunt data plus a few live values for the menu. */
	public record Sync(CompoundTag tag) implements CustomPacketPayload {
		public static final Type<Sync> TYPE = new Type<>(HuntLike.id("sync"));
		public static final StreamCodec<ByteBuf, Sync> CODEC = ByteBufCodecs.TRUSTED_COMPOUND_TAG.map(Sync::new, Sync::tag);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server to client: open the Hunt menu. */
	/** Higher Horsepower: whether the horse this player rides jumps twice as high (the rider's client does the jumping). */
	public record Horsepower(boolean on) implements CustomPacketPayload {
		public static final Type<Horsepower> TYPE = new Type<>(HuntLike.id("horsepower"));
		public static final StreamCodec<ByteBuf, Horsepower> CODEC = ByteBufCodecs.BOOL.map(Horsepower::new, Horsepower::on);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record OpenMenu(int tab) implements CustomPacketPayload {
		public static final Type<OpenMenu> TYPE = new Type<>(HuntLike.id("open_menu"));
		public static final StreamCodec<ByteBuf, OpenMenu> CODEC = ByteBufCodecs.VAR_INT.map(OpenMenu::new, OpenMenu::tab);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Client to server: a menu action ("select", "start", "cancel", "buy_tokens", "refresh"). */
	public record Action(String action, String hunt, int value) implements CustomPacketPayload {
		public static final Type<Action> TYPE = new Type<>(HuntLike.id("action"));
		public static final StreamCodec<ByteBuf, Action> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, Action::action,
				ByteBufCodecs.STRING_UTF8, Action::hunt,
				ByteBufCodecs.VAR_INT, Action::value,
				Action::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Server to client: show the "gear obtained" popup for an item. */
	/**
	 * Server to client: the Yummy! escape. keys is one digit per key (0 forward, 1 left, 2 back, 3 right, 4 attack,
	 * 5 use, 6 jump); progress is how many are already pressed.
	 */
	public record Yummy(boolean active, String keys, int progress) implements CustomPacketPayload {
		public static final Type<Yummy> TYPE = new Type<>(HuntLike.id("yummy"));
		public static final StreamCodec<ByteBuf, Yummy> CODEC = StreamCodec.composite(
				ByteBufCodecs.BOOL, Yummy::active,
				ByteBufCodecs.STRING_UTF8, Yummy::keys,
				ByteBufCodecs.VAR_INT, Yummy::progress,
				Yummy::new);

		public Yummy(boolean active, int[] keys, int progress) {
			this(active, java.util.Arrays.stream(keys).mapToObj(String::valueOf).collect(java.util.stream.Collectors.joining()),
					progress);
		}

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Spiritual Catalyst: the client highlights one entity for a few ticks (only for this player). */
	public record Highlight(int entityId, int ticks) implements CustomPacketPayload {
		public static final Type<Highlight> TYPE = new Type<>(HuntLike.id("highlight"));
		public static final StreamCodec<ByteBuf, Highlight> CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_INT, Highlight::entityId, ByteBufCodecs.VAR_INT, Highlight::ticks, Highlight::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public record GearToast(String itemId, String name) implements CustomPacketPayload {
		public static final Type<GearToast> TYPE = new Type<>(HuntLike.id("gear_toast"));
		public static final StreamCodec<ByteBuf, GearToast> CODEC = StreamCodec.composite(
				ByteBufCodecs.STRING_UTF8, GearToast::itemId,
				ByteBufCodecs.STRING_UTF8, GearToast::name,
				GearToast::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void initialize() {
		PayloadTypeRegistry.clientboundPlay().register(GearToast.TYPE, GearToast.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Highlight.TYPE, Highlight.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Yummy.TYPE, Yummy.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Sync.TYPE, Sync.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(OpenMenu.TYPE, OpenMenu.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Horsepower.TYPE, Horsepower.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Action.TYPE, Action.CODEC);
	}
}
