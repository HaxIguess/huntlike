package com.huntlike.potion;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.equine.AbstractHorse;
import net.minecraft.world.item.alchemy.Potion;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import com.huntlike.HuntLike;

/**
 * The mod's potions. Potion of Blazing (a plain Blazing Heart in an Awkward Potion) gives Blazing Soul; Potion of
 * Horsepower (Pom's Skeletal Remains, any star level) gives Higher Horsepower. 3 minutes, 8 with redstone, no stronger
 * level. Splash and lingering versions take another of the same ingredient instead of gunpowder or dragon's breath
 * (the brewing recipes are in data/huntlike/recipe/brewing).
 */
public final class ModPotions {
	private ModPotions() {
	}

	public static final int DURATION = 3 * 60 * 20;
	public static final int LONG_DURATION = 8 * 60 * 20;

	public static final Holder<MobEffect> BLAZING_SOUL = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			HuntLike.id("blazing_soul"), new BlazingSoulEffect());
	/** Any horse its holder rides (or a horse that has it, from a splash) jumps twice as high. Horses and Pom's remains horse. */
	public static final Holder<MobEffect> HIGHER_HORSEPOWER = Registry.registerForHolder(BuiltInRegistries.MOB_EFFECT,
			HuntLike.id("higher_horsepower"), new MobEffect(MobEffectCategory.BENEFICIAL, 0x9C6B3A) {
			});

	public static final Holder<Potion> BLAZING = potion("blazing", BLAZING_SOUL, DURATION);
	public static final Holder<Potion> LONG_BLAZING = potion("long_blazing", BLAZING_SOUL, LONG_DURATION);
	public static final Holder<Potion> HORSEPOWER = potion("horsepower", HIGHER_HORSEPOWER, DURATION);
	public static final Holder<Potion> LONG_HORSEPOWER = potion("long_horsepower", HIGHER_HORSEPOWER, LONG_DURATION);

	/** Twice the height: jump speed × √2 (height grows with its square). */
	public static final double JUMP_BONUS = Math.sqrt(2.0) - 1.0;
	private static final net.minecraft.resources.Identifier JUMP_MODIFIER = HuntLike.id("higher_horsepower");
	/** Horses that got the bonus, so it comes off when nobody with the effect rides them. */
	private static final Set<UUID> BOOSTED = new HashSet<>();

	private static Holder<Potion> potion(String name, Holder<MobEffect> effect, int ticks) {
		// The name is also the translation key: item.minecraft.potion.effect.<name> (the long one shares the short name).
		String key = name.startsWith("long_") ? name.substring(5) : name;
		return Registry.registerForHolder(BuiltInRegistries.POTION, HuntLike.id(name), new Potion(key, new MobEffectInstance(effect, ticks)));
	}

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(ModPotions::tick);
	}

	/** Whether this horse should jump twice as high right now. */
	public static boolean boosted(AbstractHorse horse) {
		// Horses and Pom's remains horse (summoned with Pom's Skeletal Remains); no other horse.
		if (horse.getType() != EntityTypes.HORSE && horse.getType() != com.huntlike.interlude.ModInterlude.REMAINS_HORSE) {
			return false;
		}
		if (horse.hasEffect(HIGHER_HORSEPOWER)) {
			return true;
		}
		for (var passenger : horse.getPassengers()) {
			if (passenger instanceof ServerPlayer rider && rider.hasEffect(HIGHER_HORSEPOWER)) {
				return true;
			}
		}
		return false;
	}

	/** Riders whose client was last told their horse is boosted. */
	private static final Set<UUID> TOLD = new HashSet<>();

	private static void tick(MinecraftServer server) {
		Set<AbstractHorse> now = new HashSet<>();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean on = player.getVehicle() instanceof AbstractHorse horse && boosted(horse);
			if (on) {
				now.add((AbstractHorse) player.getVehicle());
			}
			// A ridden horse moves (and jumps) on its rider's client, so the rider's client applies the bonus too.
			if (on != TOLD.contains(player.getUUID())) {
				if (on) {
					TOLD.add(player.getUUID());
				} else {
					TOLD.remove(player.getUUID());
				}
				net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new com.huntlike.network.HuntPayloads.Horsepower(on));
			}
		}
		TOLD.removeIf(id -> server.getPlayerList().getPlayer(id) == null);
		for (var level : server.getAllLevels()) {
			for (UUID id : Set.copyOf(BOOSTED)) {
				if (level.getEntity(id) instanceof AbstractHorse horse && !now.contains(horse) && !boosted(horse)) {
					setBonus(horse, false);
					BOOSTED.remove(id);
				}
			}
		}
		for (AbstractHorse horse : now) {
			setBonus(horse, true);
			BOOSTED.add(horse.getUUID());
		}
	}

	public static void setBonus(AbstractHorse horse, boolean on) {
		AttributeInstance jump = horse.getAttribute(Attributes.JUMP_STRENGTH);
		if (jump == null) {
			return;
		}
		if (on && !jump.hasModifier(JUMP_MODIFIER)) {
			jump.addTransientModifier(new AttributeModifier(JUMP_MODIFIER, JUMP_BONUS, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
		} else if (!on) {
			jump.removeModifier(JUMP_MODIFIER);
		}
	}
}
