package com.huntlike.client;

import net.minecraft.client.renderer.entity.ThrownItemRenderer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

import com.huntlike.client.entity.*;
import com.huntlike.client.screen.HuntMenuScreen;
import com.huntlike.entity.ModEntities;
import com.huntlike.network.HuntPayloads;

/** Client entrypoint: the Hunt menu, synced data and renderers. */
public class HuntLikeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		ClientPlayNetworking.registerGlobalReceiver(HuntPayloads.Sync.TYPE,
				(payload, context) -> ClientHuntState.update(payload.tag()));
		ClientPlayNetworking.registerGlobalReceiver(HuntPayloads.OpenMenu.TYPE,
				(payload, context) -> context.client().gui.setScreen(payload.tab() == com.huntlike.nexus.SoulNexus.SCREEN_TAB
						? new com.huntlike.client.screen.SoulNexusScreen() : new HuntMenuScreen(payload.tab())));
		ClientPlayNetworking.registerGlobalReceiver(HuntPayloads.Highlight.TYPE, (payload, context) -> {
			ClientHuntState.highlights.put(payload.entityId(), context.client().level.getGameTime() + payload.ticks());
		});
		ClientPlayNetworking.registerGlobalReceiver(HuntPayloads.GearToast.TYPE, (payload, context) -> {
			var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(payload.itemId()));
			context.client().gui.toastManager().addToast(new GearToast(new net.minecraft.world.item.ItemStack(item), payload.name()));
		});
		EntityRendererRegistry.register(ModEntities.BOSS_EGG, ThrownItemRenderer::new);
		EntityRendererRegistry.register(ModEntities.CORROSIVE_STINGER, ThrownItemRenderer::new);
		EntityRendererRegistry.register(ModEntities.SYLVIORNIS, SylviornisRenderer::new);
		EntityRendererRegistry.register(ModEntities.HYLOCHOERUS, HylochoerusRenderer::new);
		EntityRendererRegistry.register(ModEntities.OVIS_AMMON, OvisAmmonRenderer::new);
		EntityRendererRegistry.register(ModEntities.AUROCHS, AurochsRenderer::new);
		EntityRendererRegistry.register(ModEntities.EPICYON_HAYDENI, EpicyonHaydeniRenderer::new);
		EntityRendererRegistry.register(ModEntities.BOS_TAURUS, BosTaurusRenderer::new);
		EntityRendererRegistry.register(ModEntities.CANIS_LUPUS, CanisLupusRenderer::new);
		EntityRendererRegistry.register(ModEntities.CLUSTER_BALL, ThrownItemRenderer::new);
		EntityRendererRegistry.register(ModEntities.CUBERT, CubertRenderer::new);
		EntityRendererRegistry.register(ModEntities.PET_SLIME, com.huntlike.client.pet.PetSlimeRenderer::new);
		EntityRendererRegistry.register(ModEntities.PET_MAGMA, com.huntlike.client.pet.PetMagmaRenderer::new);
		net.minecraft.client.gui.screens.MenuScreens.register(com.huntlike.pet.PetMenu.TYPE, com.huntlike.client.pet.PetScreen::new);
		EntityRendererRegistry.register(ModEntities.BLUCKY, BluckyRenderer::new);
		EntityRendererRegistry.register(ModEntities.HOT_HEAD, HotHeadRenderer::new);
		EntityRendererRegistry.register(ModEntities.VARIANCE_SLIME, VarianceSlimeRenderer::new);
		EntityRendererRegistry.register(ModEntities.VARIANCE_MAGMA, VarianceMagmaRenderer::new);
		EntityRendererRegistry.register(ModEntities.THE_VILE, TheVileRenderer::new);
		EntityRendererRegistry.register(ModEntities.ROGUE_HUNTER, RogueHunterRenderer::new);
		EntityRendererRegistry.register(ModEntities.TOWERNAAR_UNDEAD, TowernaarUndeadRenderer::new);
		EntityRendererRegistry.register(ModEntities.THROWN_END_STONE, ThrownItemRenderer::new);
		com.huntlike.client.item.TexturedShieldRenderer.initialize();
		com.huntlike.client.block.AlphaAltarRenderer.initialize();
		com.huntlike.client.block.SoulModelRenderer.initialize();
		// The Blazing mobs use the vanilla piglin and wither skeleton looks until their own sprites are ready.
		EntityRendererRegistry.register(com.huntlike.path.ModPaths.BLAZING_PIGLIN, context -> new net.minecraft.client.renderer.entity.PiglinRenderer(
				context, net.minecraft.client.model.geom.ModelLayers.PIGLIN, net.minecraft.client.model.geom.ModelLayers.PIGLIN_BABY,
				net.minecraft.client.model.geom.ModelLayers.PIGLIN_ARMOR, net.minecraft.client.model.geom.ModelLayers.PIGLIN_BABY_ARMOR));
		EntityRendererRegistry.register(com.huntlike.path.ModPaths.DEMONLING, DemonlingRenderer::new);
		EntityRendererRegistry.register(com.huntlike.path.ModPaths.IMP, ImpRenderer::new);
		EntityRendererRegistry.register(com.huntlike.path.ModPaths.SPECTRAL_SORROW, SpectralSorrowRenderer::new);
		EntityRendererRegistry.register(com.huntlike.path.ModPaths.EVILBORN, com.huntlike.client.entity.EvilbornRenderer::new);
		EntityRendererRegistry.register(com.huntlike.path.ModPaths.BLAZING_WITHER_SKELETON,
				net.minecraft.client.renderer.entity.WitherSkeletonRenderer::new);
		com.huntlike.path.WildfirePath.clientRagingMetabolism = () -> com.huntlike.path.WildfirePath.has(ClientHuntState.data,
				com.huntlike.path.WildfirePath.Buff.RAGING_METABOLISM);
		com.huntlike.client.entity.horns.Horns.registerLayers();
		ImpRenderer.registerLayer();
		com.huntlike.client.entity.DemonlingHorns.register();
		EntityRendererRegistry.register(com.huntlike.interlude.ModInterlude.POM, context -> new net.minecraft.client.renderer.entity.UndeadHorseRenderer(
				context, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.ZOMBIE_HORSE_SADDLE,
				net.minecraft.client.model.geom.ModelLayers.ZOMBIE_HORSE_SADDLE, net.minecraft.client.renderer.entity.UndeadHorseRenderer.Type.ZOMBIE,
				net.minecraft.client.renderer.entity.UndeadHorseRenderer.Type.ZOMBIE_BABY));
		EntityRendererRegistry.register(com.huntlike.interlude.ModInterlude.REMAINS_HORSE, context -> new net.minecraft.client.renderer.entity.UndeadHorseRenderer(
				context, net.minecraft.client.resources.model.EquipmentClientInfo.LayerType.SKELETON_HORSE_SADDLE,
				net.minecraft.client.model.geom.ModelLayers.SKELETON_HORSE_SADDLE, net.minecraft.client.renderer.entity.UndeadHorseRenderer.Type.SKELETON,
				net.minecraft.client.renderer.entity.UndeadHorseRenderer.Type.SKELETON_BABY));
		EntityRendererRegistry.register(com.huntlike.interlude.ModInterlude.BARTHOLOMEW, net.minecraft.client.renderer.entity.SlimeRenderer::new);
		YummyClient.initialize();
	}
}
