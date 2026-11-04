package com.huntlike.test;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.chicken.Chicken;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import com.huntlike.HuntLike;
import com.huntlike.client.screen.HuntMenuScreen;
import com.huntlike.hunt.BossController;
import com.huntlike.hunt.HuntData;
import com.huntlike.hunt.QuestManager;

/** Plays a Tier I Animal quest end to end and screenshots every menu screen. */
@SuppressWarnings("UnstableApiUsage")
public class HuntLikeClientGameTest implements FabricClientGameTest {
	@Override
	public void runTest(ClientGameTestContext context) {
		try (TestSingleplayerContext singleplayer = context.worldBuilder().create()) {
			singleplayer.getConnection().waitForChunksRender();
			singleplayer.getServer().runCommand("time set day");
			singleplayer.getServer().runCommand("gamerule doDaylightCycle false");
			addProgress(singleplayer, 0, 64);
			singleplayer.getServer().runOnServer(server -> QuestManager.giveStack(player(server),
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.OAK_LOG, 32)));
			context.waitTicks(10);

			// JEI: no drop pages (players find drops by hunting); every trade fits its grid; screenshot trades and the
			// Growing Slime Ball's anvil uses.
			var jeiTrades = com.huntlike.client.jei.HuntLikeJeiPlugin.trades();
			if (jeiTrades.size() < 20 || jeiTrades.stream().anyMatch(e -> e.cost().size() > 15 || e.result().size() > 4)) {
				throw new AssertionError("JEI trades don't fit: " + jeiTrades.stream()
						.map(e -> e.title() + " " + e.cost().size() + "/" + e.result().size()).toList());
			}
			if (context.computeOnClient(client -> com.huntlike.client.jei.HuntLikeJeiPlugin.showForTest(0))) {
				context.waitTicks(10);
				context.takeScreenshot("00a-jei-trades");
				context.runOnClient(client -> com.huntlike.client.jei.HuntLikeJeiPlugin.showForTest(1));
				context.waitTicks(10);
				context.takeScreenshot("00b-jei-growing-slime-ball");
				context.setScreen(() -> null);
				context.waitTicks(3);
			} else {
				System.out.println("[HuntLike test] JEI not loaded; skipped JEI screenshots");
			}

			// Open the menu the way the altar does: from the server.
			singleplayer.getServer().runOnServer(server -> QuestManager.openMenu(player(server)));
			context.waitForScreen(HuntMenuScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("01-quests-rail");
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(3);
			context.waitTicks(10);
			context.takeScreenshot("02-boss-card-tier1");
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(3);
			galleryShot(context, "1-alpha-altar-sylviornis");
			context.getInput().pressKey(InputConstants.KEY_D);
			context.waitTicks(3);
			context.takeScreenshot("03-boss-card-tier2-locked");
			context.getInput().pressKey(InputConstants.KEY_A);

			context.setScreen(() -> new HuntMenuScreen(2));
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(3);
			context.takeScreenshot("04-ranks-current");
			context.getInput().pressKey(InputConstants.KEY_D);
			context.waitTicks(3);
			context.takeScreenshot("05-ranks-next-snapshot");

			context.setScreen(() -> new HuntMenuScreen(1));
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(3);
			context.takeScreenshot("06-gear");
			context.getInput().pressKey(InputConstants.KEY_A);
			context.waitTicks(3);
			context.takeScreenshot("06b-gear-armor");
			context.getInput().pressKey(InputConstants.KEY_A);
			context.waitTicks(3);
			context.takeScreenshot("06c-gear-weapons");
			double[] center = context.computeOnClient(client -> new double[] {
					client.getWindow().getScreenWidth() * 0.62, client.getWindow().getScreenHeight() * 0.6});
			context.getInput().setCursorPos(center[0], center[1]);
			context.getInput().scroll(-10);
			context.waitTicks(3);
			context.takeScreenshot("06d-gear-weapons-scrolled");

			// Start a Tier I quest with Enter on the boss card.
			context.setScreen(() -> new HuntMenuScreen(0));
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(20);
			check(singleplayer, "quest started", data -> data.quest != null && data.quest.tier == 1);
			context.takeScreenshot("07-active-quest");
			context.setScreen(() -> null);

			// Fill the quota: the player kills four chickens.
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				ServerLevel level = player.level();
				for (int i = 0; i < 4; i++) {
					Chicken chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
					chicken.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0.0F, 0.0F);
					level.addFreshEntity(chicken);
					chicken.hurtServer(level, level.damageSources().playerAttack(player), 100.0F);
				}
			});
			context.waitTicks(80);
			context.getInput().lookAt(new net.minecraft.core.BlockPos(3, -60, 0));
			context.waitTicks(5);
			check(singleplayer, "boss spawned", data -> data.quest != null && data.quest.phase == HuntData.PHASE_BOSS);
			context.takeScreenshot("08-boss-spawned");
			boolean glowing = context.computeOnClient(client -> {
				for (net.minecraft.world.entity.Entity entity : client.level.entitiesForRendering()) {
					if (entity.getId() == com.huntlike.client.ClientHuntState.bossEntityId) {
						return client.shouldEntityAppearGlowing(entity);
					}
				}
				return false;
			});
			HuntLike.LOGGER.info("[test] boss glows for owner: {} (boss id {})", glowing, com.huntlike.client.ClientHuntState.bossEntityId);

			singleplayer.getServer().runOnServer(server -> QuestManager.openMenu(player(server)));
			context.waitForScreen(HuntMenuScreen.class);
			context.waitTicks(5);
			context.takeScreenshot("09-active-quest-boss");
			hover(context, "Drops");
			context.takeScreenshot("09b-drops-tooltip");
			context.setScreen(() -> null);

			// Let the boss fight for a few seconds, then kill it as the player.
			context.waitTicks(60);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				Mob boss = BossController.findBoss(player, QuestManager.data(player).quest);
				if (boss != null) {
					HuntLike.LOGGER.info("[test] Boss at {} health {}, player health {}", boss.blockPosition(), boss.getHealth(), player.getHealth());
				}
			});
			context.takeScreenshot("10-boss-fight");
			final int[] xpBefore = new int[1];
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				Mob boss = BossController.findBoss(player, QuestManager.data(player).quest);
				if (boss == null) {
					throw new AssertionError("Boss not found");
				}
				HuntLike.LOGGER.info("[test] Boss {} health {}/{}", boss.getName().getString(), boss.getHealth(), boss.getMaxHealth());
				xpBefore[0] = player.totalExperience;
				boss.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1000.0F);
			});
			context.waitTicks(20);
			assertServer(singleplayer, "a boss kill drops 218 vanilla experience for its owner", server -> {
				int gained = player(server).totalExperience - xpBefore[0];
				HuntLike.LOGGER.info("[test] vanilla experience from the boss: {} (level {})", gained, player(server).experienceLevel);
				return gained >= com.huntlike.hunt.QuestManager.BOSS_EXPERIENCE;
			});
			check(singleplayer, "quest complete (Rank 0: no Daily Quest charge used)", data -> data.quest == null
					&& data.progress("animal").xp == 5 && data.progress("animal").bossKills[0] == 1
					&& data.dailyCharges == HuntData.DAILY_CHARGES);
			context.takeScreenshot("11-quest-complete");

			addProgress(singleplayer, 200, 0);
			context.waitTicks(10);
			context.setScreen(() -> new HuntMenuScreen(2));
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_RETURN);
			context.waitTicks(3);
			context.takeScreenshot("12-ranks-after");
			context.setScreen(() -> new HuntMenuScreen(0));
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_A);
			context.waitTicks(3);
			context.takeScreenshot("13-boss-card-defeated");
			hover(context, "Lore");
			context.takeScreenshot("14-lore-tooltip");
			context.setScreen(() -> null);
			tierFive(context, singleplayer);
			gear(context, singleplayer);
			koro(context, singleplayer);
			extras(context, singleplayer);
			multiplayer(context, singleplayer);
			huntingPack(context, singleplayer);
			trophy(context, singleplayer);
			catalystAndPulverizer(context, singleplayer);
			dailyRefill(context, singleplayer);
			clusterHunt(context, singleplayer);
			scythes(context, singleplayer);
			slimePet(context, singleplayer);
			upgradedMonolith(context, singleplayer);
			rogueHunters(context, singleplayer);
			towernaar(context, singleplayer);
			slimeRider(context, singleplayer);
			soulNexus(context, singleplayer);
			wildfire(context, singleplayer);
			assertServer(singleplayer, "Ovis Ammon spawns in random wool colors", server -> {
				ServerPlayer player = player(server);
				java.util.Set<net.minecraft.world.item.DyeColor> colors = new java.util.HashSet<>();
				for (int i = 0; i < 20; i++) {
					var ovis = com.huntlike.entity.ModEntities.OVIS_AMMON.create(player.level(), EntitySpawnReason.MOB_SUMMONED);
					colors.add(ovis.getColor());
				}
				HuntLike.LOGGER.info("[test] Ovis Ammon colors from 20 spawns: {}", colors);
				return colors.size() >= 4;
			});
			// The horned bosses on their boss cards.
			context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new com.huntlike.network.HuntPayloads.Action("select", "animal", 0)));
			context.waitTicks(5);
			// The panel opens on the highest open tier (V): two steps back is Tier III.
			context.setScreen(() -> new HuntMenuScreen(0));
			context.waitTicks(5);
			context.getInput().pressKey(InputConstants.KEY_A);
			context.waitTicks(3);
			context.getInput().pressKey(InputConstants.KEY_A);
			context.waitTicks(10);
			context.takeScreenshot("93-ovis-ammon-horns");
			context.getInput().pressKey(InputConstants.KEY_D);
			context.waitTicks(10);
			context.takeScreenshot("94-aurochs-horns");
			context.setScreen(() -> null);
			modelGallery(context, singleplayer);
			galleryScenes(context, singleplayer);
			HuntLike.LOGGER.info("[test] HuntLike client game test passed");
		}
	}

	/** Every mob of the mod alone on clean ground, from a three-quarter view, for checking the models. */
	/** A 1920x1080 screenshot for the Modrinth gallery. */
	private static void galleryShot(ClientGameTestContext context, String name) {
		// The window itself is resized, so menus and titles lay out for 1920x1080 (GUI scale 4).
		int[] size = context.computeOnClient(client -> new int[] {client.getWindow().getScreenWidth(), client.getWindow().getScreenHeight()});
		context.getInput().resizeWindow(1920, 1080);
		context.runOnClient(client -> client.gui.toastManager().clear());
		context.waitTicks(5);
		context.takeScreenshot("gallery-" + name);
		context.getInput().resizeWindow(size[0], size[1]);
		context.waitTicks(3);
	}

	private static void cmd(net.minecraft.server.MinecraftServer server, String command) {
		server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
	}

	/** A 64x64 patch of a biome around (x, z), with its ground and some features; returns the ground's top y. */
	private static void buildScene(TestSingleplayerContext singleplayer, int x, int z, String biome, String ground, String[] features,
			int featureCount) {
		singleplayer.getServer().runOnServer(server -> {
			int y = player(server).blockPosition().getY();
			for (int dx = -32; dx < 32; dx += 32) {
				for (int dz = -32; dz < 32; dz += 32) {
					cmd(server, "fillbiome " + (x + dx) + " " + (y - 8) + " " + (z + dz) + " " + (x + dx + 31) + " " + (y + 23) + " "
							+ (z + dz + 31) + " " + biome);
				}
			}
			if (ground != null) {
				cmd(server, "fill " + (x - 32) + " " + (y - 1) + " " + (z - 32) + " " + (x + 31) + " " + (y - 1) + " " + (z + 31) + " " + ground);
			}
			java.util.Random random = new java.util.Random(x * 31L + z);
			for (int i = 0; i < featureCount; i++) {
				String feature = features[i % features.length];
				int fx = x - 28 + random.nextInt(56);
				int fz = z + 2 + random.nextInt(28);
				// Keep the space right in front of the camera clear.
				if (Math.abs(fx - x) < 4 && fz - z < 12) {
					continue;
				}
				cmd(server, "place feature " + feature + " " + fx + " " + y + " " + fz);
			}
		});
	}

	/** Places the camera (spectator, no HUD) at (x, z) looking toward +z, a little down. */
	private static void sceneCamera(ClientGameTestContext context, TestSingleplayerContext singleplayer, double x, double z, float pitch) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(net.minecraft.world.level.GameType.SPECTATOR);
			player.teleportTo(x + 0.5, player.getY(), z + 0.5);
		});
		context.waitTicks(80);
		singleplayer.getConnection().waitForChunksRender();
		context.runOnClient(client -> {
			client.player.setYRot(0.0F);
			client.player.setXRot(pitch);
			client.gui.hud.getChat().clearMessages(false);
		});
	}

	private static void hideHud(ClientGameTestContext context, boolean hide) {
		context.runOnClient(client -> {
			if (client.gui.hud.isHidden() != hide) {
				client.gui.hud.toggle();
			}
		});
	}

	/** Starts a real Animal Hunt boss fight of the given tier for the test player and waits for the boss to arrive. */
	private static Mob realBoss(ClientGameTestContext context, TestSingleplayerContext singleplayer, int tier) {
		String name = singleplayer.getServer().computeOnServer(server -> player(server).getScoreboardName());
		singleplayer.getServer().runCommand("huntlike boss animal " + tier + " " + name);
		for (int i = 0; i < 200; i++) {
			boolean ready = singleplayer.getServer().computeOnServer(server -> {
				var quest = QuestManager.data(player(server)).quest;
				return quest != null && BossController.findBoss(player(server), quest) != null;
			});
			if (ready) {
				break;
			}
			context.waitTicks(1);
		}
		return singleplayer.getServer().computeOnServer(server -> BossController.findBoss(player(server), QuestManager.data(player(server)).quest));
	}

	/** The test player as the camera: survival (bosses only fight survival players), unhurtable, looking toward +z. */
	private static void fightCamera(ClientGameTestContext context, TestSingleplayerContext singleplayer, double x, double z, float pitch) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.teleportTo(x + 0.5, player.getY(), z + 0.5);
			player.setHealth(player.getMaxHealth());
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 6000, 4, false, false));
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SATURATION, 6000, 4, false, false));
		});
		context.waitTicks(80);
		singleplayer.getConnection().waitForChunksRender();
		context.runOnClient(client -> {
			client.player.setYRot(0.0F);
			client.player.setXRot(pitch);
			client.gui.hud.getChat().clearMessages(false);
		});
	}

	private static void endFight(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("huntlike quest cancel @a");
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (var mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(48))) {
				mob.discard();
			}
			player.setHealth(player.getMaxHealth());
		});
	}

	/** Real boss fights staged for the Modrinth gallery: the Aurochs charging, a Soul Strike, the enraged Epicyon. */
	private static void galleryScenes(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("weather clear");
		singleplayer.getServer().runCommand("time set 1000");
		// The Aurochs (Bos primigenius, Tier IV) charging across the plains.
		int px = 3000;
		fightCamera(context, singleplayer, px, 0, -2.0F);
		buildScene(singleplayer, px, 0, "minecraft:plains", null,
				new String[] {"minecraft:patch_grass", "minecraft:flower_default", "minecraft:patch_grass", "minecraft:oak"}, 40);
		context.waitTicks(40);
		Mob aurochs = realBoss(context, singleplayer, 4);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			aurochs.snapTo(px + 0.5, player.getY(), 16.5, 180.0F, 0.0F);
			aurochs.setYHeadRot(180.0F);
			aurochs.yBodyRot = 180.0F;
		});
		hideHud(context, true);
		for (int i = 0; i < 120; i++) {
			double distance = singleplayer.getServer().computeOnServer(server -> Math.sqrt(aurochs.distanceToSqr(player(server))));
			if (distance < 6.0) {
				break;
			}
			context.waitTicks(1);
		}
		galleryShot(context, "2-aurochs-charge");
		hideHud(context, false);
		endFight(context, singleplayer);

		// Soul Strike, cast by the Hylochoerus (Tier II) in a forest.
		int fx = 3300;
		fightCamera(context, singleplayer, fx, 0, 6.0F);
		buildScene(singleplayer, fx, 0, "minecraft:forest", null,
				new String[] {"minecraft:oak", "minecraft:birch", "minecraft:oak", "minecraft:patch_grass", "minecraft:fancy_oak"}, 60);
		context.waitTicks(40);
		Mob hylo = realBoss(context, singleplayer, 2);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			hylo.snapTo(fx + 0.5, player.getY(), 4.5, 200.0F, 0.0F);
			hylo.setYHeadRot(200.0F);
			hylo.yBodyRot = 200.0F;
			hylo.setNoAi(true);
		});
		context.waitTicks(5);
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		// Below half health the real Soul Strike warning goes off.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			hylo.setHealth(hylo.getMaxHealth() * 0.55F);
			hylo.hurtServer(player.level(), player.damageSources().playerAttack(player), hylo.getMaxHealth() * 0.1F);
		});
		context.waitTicks(4);
		singleplayer.getServer().runOnServer(server -> player(server).setGameMode(net.minecraft.world.level.GameType.SPECTATOR));
		context.waitTicks(2);
		context.runOnClient(client -> {
			client.gui.hud.getChat().clearMessages(false);
			client.particleEngine.clearParticles();
		});
		galleryShot(context, "3-soul-strike");
		singleplayer.getServer().runOnServer(server -> player(server).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
		endFight(context, singleplayer);

		// The Epicyon Haydeni (Tier V), enraged, in the desert.
		int dx = 3600;
		fightCamera(context, singleplayer, dx, 0, 8.0F);
		buildScene(singleplayer, dx, 0, "minecraft:desert", "minecraft:sand", new String[] {}, 0);
		singleplayer.getServer().runOnServer(server -> {
			int y = player(server).blockPosition().getY();
			for (int ox = -160; ox < 160; ox += 160) {
				for (int oz = -32; oz < 288; oz += 160) {
					cmd(server, "fill " + (dx + ox) + " " + (y - 1) + " " + oz + " " + (dx + ox + 159) + " " + (y - 1) + " " + (oz + 159)
							+ " minecraft:sand");
				}
			}
			java.util.Random random = new java.util.Random(7);
			for (int i = 0; i < 40; i++) {
				int cx = dx - 40 + random.nextInt(80);
				int cz = 8 + random.nextInt(90);
				if (random.nextInt(3) == 0) {
					cmd(server, "setblock " + cx + " " + y + " " + cz + " minecraft:dead_bush");
				} else {
					int height = 1 + random.nextInt(3);
					cmd(server, "fill " + cx + " " + y + " " + cz + " " + cx + " " + (y + height - 1) + " " + cz + " minecraft:cactus");
				}
			}
		});
		context.waitTicks(40);
		Mob epicyon = realBoss(context, singleplayer, 5);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			epicyon.snapTo(dx + 0.5, player.getY(), 6.5, 205.0F, 0.0F);
			epicyon.setYHeadRot(195.0F);
			epicyon.yBodyRot = 210.0F;
			epicyon.setNoAi(true);
			// Below 40% health: Apex Instinct, and it enrages.
			epicyon.setHealth(epicyon.getMaxHealth() * 0.45F);
			epicyon.hurtServer(player.level(), player.damageSources().playerAttack(player), epicyon.getMaxHealth() * 0.1F);
		});
		context.waitTicks(10);
		// Make sure the shot shows it enraged, whatever the ability's timing.
		singleplayer.getServer().runOnServer(server -> {
			if (epicyon instanceof com.huntlike.entity.animal.EpicyonHaydeni e) {
				e.setEnraged(true);
			}
		});
		hideHud(context, true);
		context.waitTicks(5);
		context.runOnClient(client -> client.particleEngine.clearParticles());
		boolean enraged = singleplayer.getServer().computeOnServer(server ->
				epicyon instanceof com.huntlike.entity.animal.EpicyonHaydeni e && e.isEnraged());
		HuntLike.LOGGER.info("[test] gallery Epicyon enraged {}", enraged);
		galleryShot(context, "4-epicyon-enraged");
		hideHud(context, false);
		endFight(context, singleplayer);
		singleplayer.getServer().runOnServer(server -> player(server).setGameMode(net.minecraft.world.level.GameType.CREATIVE));
	}

	private static void modelGallery(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		java.util.List<net.minecraft.world.entity.EntityType<?>> types = java.util.List.of(
				com.huntlike.entity.ModEntities.SYLVIORNIS, com.huntlike.entity.ModEntities.HYLOCHOERUS, com.huntlike.entity.ModEntities.OVIS_AMMON,
				com.huntlike.entity.ModEntities.AUROCHS, com.huntlike.entity.ModEntities.EPICYON_HAYDENI, com.huntlike.entity.ModEntities.BOS_TAURUS,
				com.huntlike.entity.ModEntities.CANIS_LUPUS, com.huntlike.entity.ModEntities.CUBERT, com.huntlike.entity.ModEntities.BLUCKY,
				com.huntlike.entity.ModEntities.HOT_HEAD, com.huntlike.entity.ModEntities.VARIANCE_SLIME, com.huntlike.entity.ModEntities.VARIANCE_MAGMA,
				com.huntlike.entity.ModEntities.THE_VILE, com.huntlike.entity.ModEntities.PET_SLIME, com.huntlike.entity.ModEntities.PET_MAGMA,
				com.huntlike.entity.ModEntities.ROGUE_HUNTER, com.huntlike.entity.ModEntities.TOWERNAAR_UNDEAD,
				com.huntlike.interlude.ModInterlude.BARTHOLOMEW, com.huntlike.interlude.ModInterlude.POM, com.huntlike.interlude.ModInterlude.REMAINS_HORSE,
				com.huntlike.path.ModPaths.BLAZING_PIGLIN, com.huntlike.path.ModPaths.BLAZING_WITHER_SKELETON, com.huntlike.path.ModPaths.DEMONLING,
				com.huntlike.path.ModPaths.IMP, com.huntlike.path.ModPaths.SPECTRAL_SORROW, com.huntlike.path.ModPaths.EVILBORN);
		double[] base = new double[3];
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			player.getInventory().clearContent();
			player.teleportTo(600.5, player.getY(), 600.5);
			base[0] = 600.5;
			base[1] = player.getY();
			base[2] = 600.5;
		});
		context.waitTicks(40);
		for (var type : types) {
			String name = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getPath();
			java.util.UUID[] id = new java.util.UUID[1];
			float[] pitch = new float[1];
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				var level = player.level();
				net.minecraft.world.entity.Entity entity = type.create(level, EntitySpawnReason.COMMAND);
				if (entity == null) {
					return;
				}
				double w = entity.getBbWidth();
				double h = entity.getBbHeight();
				double d = 2.5 + Math.max(w, h) * 1.3;
				// Ghasts float: their tentacles hang below the hitbox.
				double lift = entity instanceof net.minecraft.world.entity.monster.Ghast ? 3.0 : 0.0;
				entity.snapTo(base[0], base[1] + lift, base[2] + d, 210.0F, 0.0F);
				if (entity instanceof Mob mob) {
					mob.setNoAi(true);
					mob.setYHeadRot(210.0F);
					mob.yBodyRot = 210.0F;
				}
				level.addFreshEntity(entity);
				if (entity instanceof com.huntlike.rogue.RogueHunter hunter) {
					// Hidden until hit.
					hunter.hurtServer(level, level.damageSources().generic(), 0.5F);
				}
				id[0] = entity.getUUID();
				pitch[0] = (float) Math.toDegrees(Math.atan2(1.62 - lift - h / 2.0, d));
			});
			context.runOnClient(client -> {
				client.player.setYRot(0.0F);
				client.player.setXRot(pitch[0]);
			});
			context.waitTicks(10);
			context.takeScreenshot("99z-model-" + name);
			if (type == com.huntlike.path.ModPaths.SPECTRAL_SORROW) {
				// Its shooting face.
				singleplayer.getServer().runOnServer(server -> {
					if (player(server).level().getEntity(id[0]) instanceof com.huntlike.path.SpectralSorrow sorrow) {
						sorrow.setCharging(true);
					}
				});
				context.waitTicks(5);
				context.takeScreenshot("99z-model-" + name + "-shooting");
			}
			singleplayer.getServer().runOnServer(server -> {
				var entity = player(server).level().getEntity(id[0]);
				if (entity != null) {
					entity.discard();
				}
			});
		}
		// The Alpha Altar block, with its end portal sides.
		net.minecraft.core.BlockPos[] altar = new net.minecraft.core.BlockPos[1];
		singleplayer.getServer().runOnServer(server -> {
			var level = player(server).level();
			altar[0] = net.minecraft.core.BlockPos.containing(base[0], base[1], base[2] + 2.5);
			level.setBlockAndUpdate(altar[0], com.huntlike.registry.ModBlocks.ALPHA_ALTAR.defaultBlockState());
		});
		context.runOnClient(client -> {
			client.player.setYRot(-25.0F);
			client.player.setXRot(35.0F);
		});
		context.waitTicks(10);
		context.takeScreenshot("99z-alpha-altar");
		singleplayer.getServer().runOnServer(server -> player(server).level().setBlockAndUpdate(altar[0],
				net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
		// The Soul Nexus and both Monoliths, turned towards the camera (north of them), the way placing them turns them.
		var soulBlocks = java.util.List.of(com.huntlike.registry.ModBlocks.SOUL_NEXUS, com.huntlike.registry.ModBlocks.SOUL_MONOLITH,
				com.huntlike.registry.ModBlocks.UPGRADED_SOUL_MONOLITH);
		singleplayer.getServer().runOnServer(server -> {
			var level = player(server).level();
			for (int i = 0; i < soulBlocks.size(); i++) {
				level.setBlockAndUpdate(altar[0].offset(i - 1, 0, 0), soulBlocks.get(i).defaultBlockState()
						.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,
								net.minecraft.core.Direction.NORTH));
			}
		});
		context.runOnClient(client -> {
			client.player.setYRot(0.0F);
			client.player.setXRot(35.0F);
		});
		context.waitTicks(10);
		context.takeScreenshot("99z-soul-blocks");
		context.waitTicks(17);
		context.takeScreenshot("99z-soul-blocks-later");
		context.runOnClient(client -> {
			client.player.setYRot(22.0F);
			client.player.setXRot(55.0F);
		});
		context.waitTicks(5);
		context.takeScreenshot("99z-soul-nexus-close");
		singleplayer.getServer().runOnServer(server -> {
			for (int i = 0; i < soulBlocks.size(); i++) {
				player(server).level().setBlockAndUpdate(altar[0].offset(i - 1, 0, 0),
						net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			}
		});
		// Armor sets on an armor stand (the Cluster ones are see-through).
		for (var set : java.util.List.of(com.huntlike.gear.ModGear.ROTTEN, com.huntlike.gear.ModGear.GEL, com.huntlike.gear.ModGear.THERMODYNAMIC)) {
			java.util.UUID[] stand = new java.util.UUID[1];
			singleplayer.getServer().runOnServer(server -> {
				var level = player(server).level();
				var armorStand = EntityTypes.ARMOR_STAND.create(level, EntitySpawnReason.COMMAND);
				armorStand.snapTo(base[0], base[1], base[2] + 3.0, 210.0F, 0.0F);
				armorStand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(set.helmet()));
				armorStand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(set.chestplate()));
				armorStand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(set.leggings()));
				armorStand.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(set.boots()));
				level.addFreshEntity(armorStand);
				stand[0] = armorStand.getUUID();
			});
			context.runOnClient(client -> {
				client.player.setYRot(0.0F);
				client.player.setXRot(10.0F);
			});
			context.waitTicks(10);
			context.takeScreenshot("99z-armor-" + set.id());
			singleplayer.getServer().runOnServer(server -> {
				var entity = player(server).level().getEntity(stand[0]);
				if (entity != null) {
					entity.discard();
				}
			});
		}
	}

	/** Tier V: Herd Call, its immunity, Soul Strike, Apex Instinct and the Lunge. */
	private static void tierFive(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		addProgress(singleplayer, 10500, 32);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			for (int i = 0; i < 4; i++) {
				data.progress("animal").bossKills[i] = Math.max(1, data.progress("animal").bossKills[i]);
			}
			player.setAttached(QuestManager.DATA, data);
			player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
			player.setHealth(100.0F);
		});
		context.waitTicks(10);
		context.setScreen(() -> new HuntMenuScreen(0));
		context.waitTicks(3);
		for (int i = 0; i < 4; i++) {
			context.getInput().pressKey(InputConstants.KEY_D);
		}
		context.waitTicks(10);
		context.takeScreenshot("13c-tier5-card");
		context.setScreen(() -> null);
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("start", "animal", 5)));
		context.waitTicks(10);
		check(singleplayer, "tier V started as a Daily Quest (Rank I+)", data -> data.quest != null && data.quest.tier == 5
				&& data.quest.daily && data.dailyCharges == HuntData.DAILY_CHARGES - 1);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = player.level();
			for (int i = 0; i < 12; i++) {
				Chicken chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
				chicken.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0.0F, 0.0F);
				level.addFreshEntity(chicken);
				chicken.hurtServer(level, level.damageSources().playerAttack(player), 100.0F);
			}
		});
		context.waitTicks(80);
		check(singleplayer, "tier V boss spawned", data -> data.quest != null && data.quest.phase == HuntData.PHASE_BOSS);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			data.concoction = true;
			data.progress("animal").dropsSeen.add("thick_hide");
			data.progress("animal").dropsSeen.add("large_femur");
			player.setAttached(QuestManager.DATA, data);
			QuestManager.sync(player);
		});
		singleplayer.getServer().runOnServer(server -> QuestManager.openMenu(player(server)));
		context.waitForScreen(HuntMenuScreen.class);
		context.waitTicks(5);
		hover(context, "Drops");
		context.takeScreenshot("14a-drops-concoction");
		context.setScreen(() -> null);

		// 300 -> below 225: Herd Call clamps at 225 and calls 4 wolves.
		hitBoss(singleplayer, 90.0F);
		context.waitTicks(8);
		context.takeScreenshot("14b-herd-call-warning");
		context.waitTicks(25);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Mob boss = BossController.findBoss(player, QuestManager.data(player).quest);
			int herd = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64), com.huntlike.hunt.BossAbilities::isHerd).size();
			HuntLike.LOGGER.info("[test] after herd call: boss health {} herd {}", boss.getHealth(), herd);
			if (herd != 4 || boss.getHealth() < 220) {
				throw new AssertionError("Herd Call failed");
			}
			float before = boss.getHealth();
			boss.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 50.0F);
			HuntLike.LOGGER.info("[test] immune while herd lives: {}", boss.getHealth() >= before);
			if (boss.getHealth() < before) {
				throw new AssertionError("Boss not immune during Herd Call");
			}
		});
		context.getInput().lookAt(new net.minecraft.core.BlockPos(3, -60, 0));
		context.waitTicks(10);
		context.takeScreenshot("15-herd-call");
		// A herd animal far away (or stuck) comes back to the player.
		final java.util.UUID[] strayId = new java.util.UUID[1];
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Mob stray = player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64), com.huntlike.hunt.BossAbilities::isHerd)
					.get(0);
			stray.teleportTo(player.getX() + 30.0, player.getY(), player.getZ());
			strayId[0] = stray.getUUID();
		});
		context.waitTicks(10);
		assertServer(singleplayer, "Herd Call: an animal 30 blocks away teleports back near the player", server -> {
			var stray = player(server).level().getEntity(strayId[0]);
			double distance = stray == null ? -1 : stray.distanceTo(player(server));
			HuntLike.LOGGER.info("[test] stray herd animal now {} blocks from the player", distance);
			return stray != null && distance < 8.0;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (Mob mob : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(64), com.huntlike.hunt.BossAbilities::isHerd)) {
				mob.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 100.0F);
			}
		});
		context.waitTicks(10);

		// Below 150: Soul Strike for 18 true damage.
		float healthBefore = singleplayer.getServer().computeOnServer(server -> player(server).getHealth());
		hitBoss(singleplayer, 85.0F);
		context.waitTicks(6);
		context.takeScreenshot("15b-soul-strike-warning");
		context.waitTicks(25);
		float healthAfter = singleplayer.getServer().computeOnServer(server -> player(server).getHealth());
		HuntLike.LOGGER.info("[test] Soul Strike: player health {} -> {}", healthBefore, healthAfter);
		if (healthBefore - healthAfter < 17.0F) {
			throw new AssertionError("Soul Strike did not land");
		}

		// Below 120: Apex Instinct heals to 135.
		hitBossDownTo(singleplayer, 112.0F);
		context.waitTicks(15);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Mob boss = BossController.findBoss(player, QuestManager.data(player).quest);
			boolean enraged = boss instanceof com.huntlike.entity.animal.EpicyonHaydeni epicyon && epicyon.isEnraged();
			HuntLike.LOGGER.info("[test] Apex Instinct: boss health {} enraged {}", boss.getHealth(), enraged);
			if (boss.getHealth() < 134.0F) {
				throw new AssertionError("Apex Instinct did not heal");
			}
			if (!enraged) {
				throw new AssertionError("Epicyon Haydeni is not enraged after Apex Instinct");
			}
		});
		context.waitTicks(20);
		context.takeScreenshot("16-apex-instinct");

		// Let a Lunge happen, then finish the fight.
		float beforeLunge = singleplayer.getServer().computeOnServer(server -> player(server).getHealth());
		context.waitTicks(220);
		float afterLunge = singleplayer.getServer().computeOnServer(server -> player(server).getHealth());
		HuntLike.LOGGER.info("[test] health over 11 s of Tier V fighting: {} -> {}", beforeLunge, afterLunge);
		context.takeScreenshot("17-tier5-fight");
		int xpBefore = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).progress("animal").xp);
		hitBoss(singleplayer, 1000.0F);
		context.waitTicks(20);
		check(singleplayer, "tier V complete with Daily Quest x2 XP (+1600)", data -> data.quest == null
				&& data.progress("animal").bossKills[4] == 1 && data.progress("animal").xp == xpBefore + 1600);
		context.takeScreenshot("18-tier5-complete");

		// Both skins side by side: calm (left) and enraged (right), still, 3 blocks ahead.
		final java.util.UUID[] skins = new java.util.UUID[2];
		final net.minecraft.core.BlockPos[] skinsAt = new net.minecraft.core.BlockPos[1];
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (int i = 0; i < 2; i++) {
				var epicyon = com.huntlike.entity.ModEntities.EPICYON_HAYDENI.create(player.level(), EntitySpawnReason.COMMAND);
				epicyon.snapTo(player.getX() - 0.9 + i * 1.8, player.getY(), player.getZ() + 3.0, 180.0F, 0.0F);
				epicyon.setYHeadRot(180.0F);
				epicyon.setNoAi(true);
				epicyon.setEnraged(i == 1);
				player.level().addFreshEntity(epicyon);
				skins[i] = epicyon.getUUID();
			}
			skinsAt[0] = net.minecraft.core.BlockPos.containing(player.getX(), player.getY(), player.getZ() + 3.0);
		});
		context.getInput().lookAt(skinsAt[0]);
		context.waitTicks(10);
		context.takeScreenshot("16b-epicyon-skins");
		singleplayer.getServer().runOnServer(server -> {
			for (var id : skins) {
				var entity = player(server).level().getEntity(id);
				if (entity != null) {
					entity.discard();
				}
			}
		});
	}

	/** v0.4: buy every weapon and armor stage, then check their effects. */
	private static void gear(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		addProgress(singleplayer, 26500, 0);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			var sharpness = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
					.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
			var sword = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD);
			sword.enchant(sharpness, 5);
			QuestManager.giveStack(player, sword);
			give(player, com.huntlike.registry.ModItems.LOW_QUALITY_MYSTERY_MEAT, 5);
			give(player, com.huntlike.registry.ModItems.DECENT_QUALITY_MYSTERY_MEAT, 10);
			give(player, com.huntlike.registry.ModItems.HIGH_QUALITY_MYSTERY_MEAT, 3);
			give(player, com.huntlike.registry.ModItems.LARGE_FEMUR, 2);
			give(player, com.huntlike.registry.ModItems.THICK_HIDE, 4);
			give(player, net.minecraft.world.item.Items.IRON_INGOT, 5);
			give(player, net.minecraft.world.item.Items.DIAMOND, 6);
			give(player, net.minecraft.world.item.Items.SOUL_SAND, 8);
			give(player, net.minecraft.world.item.Items.BONE_MEAL, 12);
			give(player, net.minecraft.world.item.Items.OBSIDIAN, 8);
			give(player, net.minecraft.world.item.Items.OAK_LOG, 64);
			give(player, net.minecraft.world.item.Items.OAK_LEAVES, 32);
			give(player, net.minecraft.world.item.Items.LEATHER, 16);
			give(player, net.minecraft.world.item.Items.BONE, 16);
			for (var piece : new net.minecraft.world.item.Item[] {net.minecraft.world.item.Items.LEATHER_HELMET,
					net.minecraft.world.item.Items.LEATHER_CHESTPLATE, net.minecraft.world.item.Items.LEATHER_LEGGINGS,
					net.minecraft.world.item.Items.LEATHER_BOOTS}) {
				give(player, piece, 1);
			}
		});
		context.waitTicks(10);
		context.setScreen(() -> new HuntMenuScreen(1));
		context.waitTicks(3);
		context.getInput().pressKey(InputConstants.KEY_RETURN);
		context.getInput().pressKey(InputConstants.KEY_D);
		context.waitTicks(5);
		context.takeScreenshot("19-gear-weapons-affordable");
		for (int value : new int[] {200, 201, 202, 203, 204, 0, 1, 2}) {
			context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new com.huntlike.network.HuntPayloads.Action("buy_gear", "animal", value)));
			context.waitTicks(5);
			if (value == 200) {
				context.waitTicks(10);
				context.takeScreenshot("19b-gear-toast");
			}
		}
		context.setScreen(() -> null);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var inventory = player.getInventory();
			net.minecraft.world.item.ItemStack weapon = net.minecraft.world.item.ItemStack.EMPTY;
			int ecocide = 0;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				var stack = inventory.getItem(i);
				if (stack.is(com.huntlike.gear.ModGear.BONE_PULVERIZER)) {
					weapon = stack;
				}
				if (com.huntlike.gear.ModGear.armorSetOf(stack.getItem()) == com.huntlike.gear.ModGear.ECOCIDE) {
					ecocide++;
				}
			}
			var sharpness = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
					.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
			int level = weapon.isEmpty() ? -1 : weapon.getEnchantments().getLevel(sharpness);
			HuntLike.LOGGER.info("[test] gear bought: pulverizer {} (Sharpness {}), ecocide pieces {}", !weapon.isEmpty(), level, ecocide);
			if (weapon.isEmpty() || level != 5 || ecocide != 4) {
				throw new AssertionError("Gear purchase failed");
			}
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, weapon.copy());
			weapon.setCount(0);
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				var stack = inventory.getItem(i);
				var set = com.huntlike.gear.ModGear.armorSetOf(stack.getItem());
				if (set == com.huntlike.gear.ModGear.ECOCIDE) {
					var slot = stack.get(net.minecraft.core.component.DataComponents.EQUIPPABLE).slot();
					player.setItemSlot(slot, stack.copy());
					stack.setCount(0);
				}
			}
		});
		context.waitTicks(10);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = player.level();
			HuntLike.LOGGER.info("[test] Ecocide worn: armor {} toughness {} max health {}", player.getArmorValue(),
					player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS), player.getMaxHealth());
			var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			cow.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100.0);
			cow.setHealth(100.0F);
			cow.snapTo(player.getX() + 1.5, player.getY(), player.getZ(), 0.0F, 0.0F);
			level.addFreshEntity(cow);
			player.attack(cow);
			boolean slowed = cow.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
			HuntLike.LOGGER.info("[test] Bone Pulverizer hit on a cow: {} damage, Fracture {}", 100.0F - cow.getHealth(), slowed);
			if (100.0F - cow.getHealth() < 13.0F || !slowed) {
				throw new AssertionError("Biohazard or Fracture failed");
			}
			var boss = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
			boss.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0.0F, 0.0F);
			var egg = new com.huntlike.entity.BossEgg(level, boss, 2.0F);
			boolean allowed = com.huntlike.gear.GearEffects.allowDamage(player, level.damageSources().thrown(egg, boss), 2.0F);
			HuntLike.LOGGER.info("[test] Egg Shell blocks eggs: {}", !allowed);
			if (allowed) {
				throw new AssertionError("Egg Shell failed");
			}
		});
		context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(
				net.minecraft.client.Minecraft.getInstance().player));
		context.waitTicks(10);
		context.takeScreenshot("20-ecocide-inventory");
		double[] helmet = context.computeOnClient(client -> {
			double scale = client.getWindow().getGuiScale();
			int left = (client.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (client.getWindow().getGuiScaledHeight() - 166) / 2;
			return new double[] {(left + 16) * scale, (top + 16) * scale};
		});
		context.getInput().setCursorPos(helmet[0], helmet[1]);
		context.waitTicks(5);
		context.takeScreenshot("20b-helmet-tooltip");
		context.runOnClient(client -> {
			var stack = new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModGear.BONE_PULVERIZER);
			client.player.getInventory().setItem(9, stack);
		});
		context.setScreen(() -> null);
		context.getInput().pressKey(options -> options.keyTogglePerspective);
		context.getInput().pressKey(options -> options.keyTogglePerspective);
		context.waitTicks(10);
		context.takeScreenshot("21-ecocide-worn");
	}

	/** v0.5: Koro's four chapters, the bells, Koro Whakamate and the Soul Monolith. */
	private static void koro(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		addProgress(singleplayer, 50500, 0);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			give(player, net.minecraft.world.item.Items.MUSHROOM_STEW, 1);
			give(player, net.minecraft.world.item.Items.OAK_LOG, 16);
			give(player, com.huntlike.registry.ModItems.THICK_HIDE, 3);
			give(player, com.huntlike.registry.ModItems.LOW_QUALITY_MYSTERY_MEAT, 5);
			give(player, net.minecraft.world.item.Items.IRON_INGOT, 5);
			give(player, net.minecraft.world.item.Items.SAND, 10);
			give(player, net.minecraft.world.item.Items.GOLD_INGOT, 10);
			give(player, com.huntlike.registry.ModItems.DECENT_QUALITY_MYSTERY_MEAT, 3);
			give(player, com.huntlike.registry.ModItems.GOLDEN_MOSS, 1);
			give(player, com.huntlike.registry.ModItems.HIGH_QUALITY_MYSTERY_MEAT, 1);
			give(player, com.huntlike.registry.ModItems.SPIRITUAL_CATALYST, 1);
		});
		context.waitTicks(5);
		context.setScreen(() -> new HuntMenuScreen(1));
		context.waitTicks(5);
		context.takeScreenshot("22-misc-koro");
		context.setScreen(() -> new com.huntlike.client.screen.KoroScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("23-koro-first-visit");
		for (int i = 0; i < 400; i++) {
			int done = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).koroDone);
			if (done >= 4) {
				break;
			}
			context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new com.huntlike.network.HuntPayloads.Action("koro_click", "animal", 0)));
			context.waitTicks(1);
			if (i == 8) {
				context.waitTicks(3);
				context.takeScreenshot("24-koro-chapter1");
			}
			int stepNow = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).koroStep);
			int doneNow = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).koroDone);
			if (doneNow == 3 && stepNow == 20) {
				context.waitTicks(3);
				context.takeScreenshot("25-koro-final-quest");
			}
		}
		context.waitTicks(5);
		context.takeScreenshot("26-koro-finished");
		check(singleplayer, "Koro questline finished", data -> data.koroDone == 4);
		assertServer(singleplayer, "the bells tell you what to do: Safe and Sound, right-click", server -> {
			var lore = new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModKoro.WOODEN_BELL)
					.get(net.minecraft.core.component.DataComponents.LORE);
			String text = lore.lines().stream().map(c -> c.getString()).reduce("", (a, b) -> a + " | " + b);
			HuntLike.LOGGER.info("[test] Wooden Bell lore: {}", text);
			return text.contains("Safe and Sound (Ability)") && text.contains("Right-click, 10 second cooldown.");
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			ServerLevel level = player.level();
			var inventory = player.getInventory();
			boolean bell = false;
			boolean dagger = false;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				bell |= inventory.getItem(i).is(com.huntlike.gear.ModKoro.MOSSY_BELL);
				dagger |= inventory.getItem(i).is(com.huntlike.gear.ModKoro.KORO_WHAKAMATE);
			}
			HuntLike.LOGGER.info("[test] Koro rewards: Mossy Bell {} Koro Whakamate {}", bell, dagger);
			if (!bell || !dagger) {
				throw new AssertionError("Koro rewards missing");
			}
			// Mossy Bell: heal and Absorption II.
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModKoro.MOSSY_BELL));
			player.getMainHandItem().use(level, player, net.minecraft.world.InteractionHand.MAIN_HAND);
			var absorption = player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
			HuntLike.LOGGER.info("[test] Mossy Bell absorption level {}", absorption == null ? -1 : absorption.getAmplifier());
			if (absorption == null || absorption.getAmplifier() != 1) {
				throw new AssertionError("Bell failed");
			}
			// Koro Whakamate on a cow (poison) and a zombie (wither).
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModKoro.KORO_WHAKAMATE));
			var cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
			cow.snapTo(player.getX() + 1.5, player.getY(), player.getZ(), 0.0F, 0.0F);
			level.addFreshEntity(cow);
			player.attack(cow);
			var zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
			zombie.snapTo(player.getX() - 1.5, player.getY(), player.getZ(), 0.0F, 0.0F);
			level.addFreshEntity(zombie);
			player.attack(zombie);
			HuntLike.LOGGER.info("[test] Whakamate: cow poisoned {} burning {}, zombie withered {}",
					cow.hasEffect(net.minecraft.world.effect.MobEffects.POISON), cow.isOnFire(),
					zombie.hasEffect(net.minecraft.world.effect.MobEffects.WITHER));
			if (!cow.hasEffect(net.minecraft.world.effect.MobEffects.POISON)
					|| !zombie.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) {
				throw new AssertionError("Koro Whakamate effects failed");
			}
			zombie.discard();
			// Soul Monolith next to the player.
			level.setBlockAndUpdate(player.blockPosition().offset(2, 0, 2), com.huntlike.registry.ModBlocks.SOUL_MONOLITH.defaultBlockState());
		});
		context.waitTicks(20);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			boolean resistance = player.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
			HuntLike.LOGGER.info("[test] Soul Monolith resistance {}", resistance);
			if (!resistance) {
				throw new AssertionError("Soul Monolith failed");
			}
		});
		context.waitTicks(80);
		check(singleplayer, "cow killed by Fatal Touch", data -> true);
	}

	/** Golden Meat, the wolf treats, the Hide Boots line and the FeatherChute. */
	private static void extras(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		assertServer(singleplayer, "recipes loaded", server -> {
			for (String id : new String[] {"golden_meat", "thick_hide_boots", "feather_chute", "winged_hide_boots_smithing",
					"hardened_hide_boots_smithing", "speedy_hide_boots_smithing", "hermes_boots_smithing"}) {
				var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE, HuntLike.id(id));
				if (server.getRecipeManager().byKey(key).isEmpty()) {
					HuntLike.LOGGER.error("[test] missing recipe {}", id);
					return false;
				}
			}
			return true;
		});
		assertServer(singleplayer, "golden meat eaten instantly", server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			player.getFoodData().setFoodLevel(4);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.GOLDEN_MEAT, 2));
			player.getMainHandItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			return player.getFoodData().getFoodLevel() == 12 && player.getMainHandItem().getCount() == 1 && !player.isUsingItem();
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var wolf = EntityTypes.WOLF.create(player.level(), EntitySpawnReason.COMMAND);
			wolf.setPos(player.getX() + 2, player.getY(), player.getZ());
			wolf.addTag("huntlike_test_wolf");
			player.level().addFreshEntity(wolf);
		});
		context.waitTicks(2);
		assertServer(singleplayer, "low quality meat tames a wolf", server -> {
			ServerPlayer player = player(server);
			var wolf = testWolf(player);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.LOW_QUALITY_MYSTERY_MEAT, 1));
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, player.level(),
					net.minecraft.world.InteractionHand.MAIN_HAND, wolf, null);
			return wolf.isTame() && wolf.isOwnedBy(player);
		});
		assertServer(singleplayer, "large femur doubles wolf health and damage", server -> {
			ServerPlayer player = player(server);
			var wolf = testWolf(player);
			double health = wolf.getMaxHealth();
			double damage = wolf.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.LARGE_FEMUR, 2));
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, player.level(),
					net.minecraft.world.InteractionHand.MAIN_HAND, wolf, null);
			net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, player.level(),
					net.minecraft.world.InteractionHand.MAIN_HAND, wolf, null);
			HuntLike.LOGGER.info("[test] wolf health {} -> {}, damage {} -> {}", health, wolf.getMaxHealth(), damage,
					wolf.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
			return wolf.getMaxHealth() == health * 2 && wolf.getHealth() == wolf.getMaxHealth()
					&& Math.abs(wolf.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.SCALE) - 1.25) < 0.001
					&& wolf.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == damage * 2
					&& player.getMainHandItem().getCount() == 1 && hasAdvancement(player, "animal/happier_dog");
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			testWolf(player).discard();
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
					new net.minecraft.world.item.ItemStack(com.huntlike.gear.HideBoots.WINGED_HIDE_BOOTS));
			player.teleportTo(player.getX(), player.getY() + 30, player.getZ());
		});
		context.waitTicks(25);
		assertServer(singleplayer, "winged boots glide", server -> {
			ServerPlayer player = player(server);
			return player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING)
					&& net.minecraft.world.level.block.PowderSnowBlock.canEntityWalkOnPowderSnow(player);
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.item.ItemStack.EMPTY);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.gear.HideBoots.FEATHER_CHUTE));
			player.teleportTo(player.getX(), player.getY() + 40, player.getZ());
		});
		context.waitTicks(70);
		assertServer(singleplayer, "feather chute glides and wears", server -> {
			ServerPlayer player = player(server);
			var chute = player.getOffhandItem();
			HuntLike.LOGGER.info("[test] chute damage {}", chute.getDamageValue());
			return player.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING) && chute.getDamageValue() >= 1
					&& !net.minecraft.world.level.block.PowderSnowBlock.canEntityWalkOnPowderSnow(player);
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
		});
	}

	/** A second (fake) player on the server: admin commands, two quests at once, a helper finishing someone else's boss. */
	private static void multiplayer(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		assertServer(singleplayer, "creative tabs Drops, Gear, Blocks", server -> {
			var registry = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB;
			return registry.getValue(HuntLike.id("drops")) != null && registry.getValue(HuntLike.id("gear")) != null
					&& registry.getValue(HuntLike.id("blocks")) != null && registry.getValue(HuntLike.id("huntlike")) == null;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
			player.teleportTo(player.getX(), player.level().getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
					player.getBlockX(), player.getBlockZ()), player.getZ());
			var helper = net.fabricmc.fabric.api.entity.FakePlayer.get(player.level(),
					new com.mojang.authlib.GameProfile(java.util.UUID.fromString("00000000-0000-4000-8000-00000000beef"), "Helper"));
			helper.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
			addToPlayerList(server, helper);
		});
		context.waitTicks(2);
		singleplayer.getServer().runCommand("huntlike quest start Helper animal 2");
		singleplayer.getServer().runCommand("huntlike stats Helper");
		context.waitTicks(2);
		assertServer(singleplayer, "command starts a quest for another player", server -> {
			var quest = QuestManager.data(helper(server)).quest;
			return quest != null && quest.tier == 2 && quest.phase == HuntData.PHASE_KILLING
					&& QuestManager.data(player(server)).quest == null;
		});
		String name = singleplayer.getServer().computeOnServer(server -> player(server).getScoreboardName());
		singleplayer.getServer().runCommand("huntlike boss animal 1 " + name);
		context.waitTicks(60);
		assertServer(singleplayer, "command summons a boss; both quests run side by side", server -> {
			var mine = QuestManager.data(player(server)).quest;
			var theirs = QuestManager.data(helper(server)).quest;
			return mine != null && mine.phase == HuntData.PHASE_BOSS && BossController.findBoss(player(server), mine) != null
					&& theirs != null && theirs.phase == HuntData.PHASE_KILLING && theirs.tier == 2;
		});
		int killsBefore = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).progress("animal").bossKills[0]);
		assertServer(singleplayer, "helper's killing blow completes the owner's quest", server -> {
			ServerPlayer owner = player(server);
			ServerPlayer helper = helper(server);
			Mob boss = BossController.findBoss(owner, QuestManager.data(owner).quest);
			boss.hurtServer(owner.level(), owner.level().damageSources().playerAttack(owner), 2.0F);
			boss.hurtServer(owner.level(), owner.level().damageSources().playerAttack(helper), 1000.0F);
			return !boss.isAlive() && QuestManager.data(owner).quest == null
					&& QuestManager.data(owner).progress("animal").bossKills[0] == killsBefore + 1
					&& QuestManager.data(helper).quest != null && QuestManager.data(helper).progress("animal").bossKills[0] == 0;
		});
		assertServer(singleplayer, "helper's quota kill counts only for the helper", server -> {
			ServerPlayer helper = helper(server);
			var cow = EntityTypes.COW.create(helper.level(), EntitySpawnReason.COMMAND);
			cow.snapTo(helper.getX() + 1, helper.getY(), helper.getZ(), 0, 0);
			helper.level().addFreshEntity(cow);
			int before = QuestManager.data(helper).quest.kills;
			cow.hurtServer(helper.level(), helper.level().damageSources().playerAttack(helper), 1000.0F);
			return QuestManager.data(helper).quest.kills == before + 1 && QuestManager.data(player(server)).quest == null;
		});
		singleplayer.getServer().runCommand("huntlike quest start @a animal 1");
		context.waitTicks(2);
		singleplayer.getServer().runCommand("huntlike quest cancel @a");
		context.waitTicks(2);
		assertServer(singleplayer, "cancel command clears every quest", server ->
				QuestManager.data(player(server)).quest == null && QuestManager.data(helper(server)).quest == null);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer helper = helper(server);
			removeFromPlayerList(server, helper);
			helper.discard();
		});
	}

	private static final java.util.UUID PACKMATE = java.util.UUID.fromString("00000000-0000-4000-8000-0000000fa11e");

	private static ServerPlayer packmate(MinecraftServer server) {
		return server.getPlayerList().getPlayer(PACKMATE);
	}

	/**
	 * Hunting Packs with a fake second player: invite, accept (their own quest pauses), the pack screen, a shared quota
	 * (doubled), a boss with double health summoned at the leader, both members rewarded, kick; the potions too.
	 */
	private static void huntingPack(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			com.huntlike.hunt.HuntingPacks.resetAll();
			var mate = net.fabricmc.fabric.api.entity.FakePlayer.get(player.level(), new com.mojang.authlib.GameProfile(PACKMATE, "Packmate"));
			mate.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0, 0);
			addToPlayerList(server, mate);
			for (ServerPlayer p : List.of(player, mate)) {
				var data = QuestManager.data(p);
				data.quest = null;
				data.pausedQuest = null;
				data.progress("animal").tokens = 50;
				p.setAttached(QuestManager.DATA, data);
			}
			// The packmate has a quest of their own: joining pauses it.
			QuestManager.forceStart(mate, com.huntlike.hunt.HuntDefs.ANIMAL, 1);
			com.huntlike.hunt.HuntingPacks.handle(player, "pack_name", "Wolf Pack", 0);
			com.huntlike.hunt.HuntingPacks.handle(player, "pack_invite", PACKMATE.toString(), 0);
			var pack = com.huntlike.hunt.HuntingPacks.packOf(player.getUUID());
			com.huntlike.hunt.HuntingPacks.handle(mate, "pack_accept", pack.id().toString(), 0);
		});
		context.waitTicks(3);
		assertServer(singleplayer, "Hunting Pack: invite and accept; the new member's own quest is paused", server -> {
			var pack = com.huntlike.hunt.HuntingPacks.packOf(player(server).getUUID());
			var mateData = QuestManager.data(packmate(server));
			return pack != null && pack.members().size() == 2 && pack.name().equals("Wolf Pack")
					&& com.huntlike.hunt.HuntingPacks.grouped(packmate(server)) && mateData.quest == null && mateData.pausedQuest != null;
		});
		context.setScreen(() -> new com.huntlike.client.screen.HuntMenuScreen(QuestManager.PACK_TAB + QuestManager.PHONE_FLAG));
		context.waitTicks(5);
		context.takeScreenshot("98a-hunting-pack");
		context.setScreen(() -> null);
		singleplayer.getServer().runOnServer(server -> com.huntlike.hunt.HuntingPacks.start(player(server), com.huntlike.hunt.HuntDefs.ANIMAL, 1));
		context.waitTicks(3);
		assertServer(singleplayer, "Hunting Pack quest: quota x2, both pay tokens, it runs on the leader", server -> {
			var pack = com.huntlike.hunt.HuntingPacks.packOf(player(server).getUUID());
			var quest = pack.quest();
			int cost = com.huntlike.hunt.HuntDefs.ANIMAL.tier(1).tokenCost();
			return quest != null && quest.quota == com.huntlike.hunt.HuntDefs.ANIMAL.tier(1).quota() * 2 && quest.packSize == 2
					&& QuestManager.data(player(server)).quest == quest
					&& QuestManager.data(player(server)).progress("animal").tokens == 50 - cost
					&& QuestManager.data(packmate(server)).progress("animal").tokens == 50 - cost;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer mate = packmate(server);
			var cow = net.minecraft.world.entity.EntityTypes.COW.create(mate.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			cow.snapTo(mate.getX() + 1, mate.getY(), mate.getZ(), 0, 0);
			mate.level().addFreshEntity(cow);
			cow.hurtServer(mate.level(), mate.level().damageSources().playerAttack(mate), 1000.0F);
		});
		context.waitTicks(3);
		assertServer(singleplayer, "Hunting Pack: a member's kill counts for the pack", server ->
				com.huntlike.hunt.HuntingPacks.packOf(player(server).getUUID()).quest().kills >= 1);
		context.setScreen(() -> new com.huntlike.client.screen.HuntMenuScreen(QuestManager.PACK_TAB + QuestManager.PHONE_FLAG));
		context.waitTicks(5);
		context.takeScreenshot("98b-hunting-pack-quest");
		context.setScreen(() -> null);
		int killsBefore = singleplayer.getServer().computeOnServer(server ->
				QuestManager.data(packmate(server)).progress("animal").bossKills[0]);
		singleplayer.getServer().runOnServer(server -> {
			var quest = com.huntlike.hunt.HuntingPacks.packOf(player(server).getUUID()).quest();
			quest.kills = quest.quota - 1;
			ServerPlayer mate = packmate(server);
			var cow = net.minecraft.world.entity.EntityTypes.COW.create(mate.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			cow.snapTo(mate.getX() + 1, mate.getY(), mate.getZ(), 0, 0);
			mate.level().addFreshEntity(cow);
			cow.hurtServer(mate.level(), mate.level().damageSources().playerAttack(mate), 1000.0F);
		});
		context.waitTicks(80);
		assertServer(singleplayer, "Hunting Pack boss: summoned for the leader with twice the health", server -> {
			var quest = QuestManager.data(player(server)).quest;
			var boss = quest == null ? null : BossController.findBoss(player(server), quest);
			HuntLike.LOGGER.info("[test] pack boss health {}", boss == null ? "none" : boss.getMaxHealth());
			return boss != null && Math.abs(boss.getMaxHealth() - com.huntlike.hunt.HuntDefs.ANIMAL.tier(1).health() * 2) < 0.01F;
		});
		singleplayer.getServer().runOnServer(server -> {
			var quest = QuestManager.data(player(server)).quest;
			var boss = BossController.findBoss(player(server), quest);
			boss.hurtServer(player(server).level(), player(server).level().damageSources().playerAttack(player(server)), 100000.0F);
		});
		context.waitTicks(5);
		assertServer(singleplayer, "Hunting Pack boss down: both members get the kill; the quest ends", server ->
				com.huntlike.hunt.HuntingPacks.packOf(player(server).getUUID()).quest() == null
						&& QuestManager.data(packmate(server)).progress("animal").bossKills[0] == killsBefore + 1);
		singleplayer.getServer().runOnServer(server -> {
			// An invite waiting in the leader's inbox, from a second pack.
			ServerPlayer mate = packmate(server);
			com.huntlike.hunt.HuntingPacks.handle(player(server), "pack_kick", PACKMATE.toString(), 0);
			com.huntlike.hunt.HuntingPacks.handle(mate, "pack_invite", player(server).getUUID().toString(), 0);
		});
		context.waitTicks(3);
		assertServer(singleplayer, "Hunting Pack: a kicked member is on their own again with their quest back", server ->
				!com.huntlike.hunt.HuntingPacks.grouped(packmate(server)) && QuestManager.data(packmate(server)).quest != null
						&& QuestManager.data(packmate(server)).pausedQuest == null);
		context.setScreen(() -> new com.huntlike.client.screen.HuntMenuScreen(QuestManager.PACK_TAB + QuestManager.PHONE_FLAG));
		context.waitTicks(5);
		context.takeScreenshot("98c-hunting-pack-red-dot");
		context.runOnClient(client -> {
			if (client.gui.screen() instanceof com.huntlike.client.screen.HuntMenuScreen menu) {
				int[] at = menu.buttonCenter("Invites");
				if (at != null) {
					menu.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(at[0], at[1],
							new net.minecraft.client.input.MouseButtonInfo(0, 0)), false);
				}
			}
		});
		context.waitTicks(3);
		context.takeScreenshot("98d-hunting-pack-invites");
		context.setScreen(() -> new com.huntlike.client.screen.HuntMenuScreen(QuestManager.PHONE_FLAG));
		context.waitTicks(5);
		context.takeScreenshot("98e-a-phone-side-tab");
		context.setScreen(() -> null);
		// Potions: Blazing Soul gives (hidden) Fire Resistance; Higher Horsepower makes a ridden horse jump twice as high.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.huntlike.potion.ModPotions.BLAZING_SOUL, 200));
			var horse = net.minecraft.world.entity.EntityTypes.HORSE.create(player.level(), net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			horse.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0, 0);
			player.level().addFreshEntity(horse);
			horse.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.huntlike.potion.ModPotions.HIGHER_HORSEPOWER, 200));
			horse.addTag("huntlike_test_horse");
		});
		context.waitTicks(25);
		assertServer(singleplayer, "Potions: Blazing Soul gives Fire Resistance; Higher Horsepower boosts a horse", server -> {
			ServerPlayer player = player(server);
			var horse = player.level().getEntitiesOfClass(net.minecraft.world.entity.animal.equine.Horse.class,
					player.getBoundingBox().inflate(10), h -> h.entityTags().contains("huntlike_test_horse")).stream().findFirst().orElse(null);
			boolean boosted = horse != null && com.huntlike.potion.ModPotions.boosted(horse);
			if (horse != null) {
				horse.discard();
			}
			var recipes = server.getRecipeManager();
			boolean brews = recipes.byKey(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.RECIPE,
					HuntLike.id("brewing/potion_awkward_blazing_heart"))).isPresent();
			return player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE) && boosted && brews;
		});
		// The three chalices in the hotbar (empty, filling, full) and the two effect icons, in the inventory.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var inventory = player.getInventory();
			inventory.setItem(0, new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.CHALICE_ITEM));
			var filling = new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.CHALICE_ITEM);
			filling.set(com.huntlike.path.ModPaths.BLOOD, 50);
			inventory.setItem(1, filling);
			inventory.setItem(2, new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.FULL_CHALICE));
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(com.huntlike.potion.ModPotions.HIGHER_HORSEPOWER, 600));
			player.inventoryMenu.broadcastChanges();
		});
		context.waitTicks(5);
		context.setScreen(() -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(net.minecraft.client.Minecraft.getInstance().player));
		context.waitTicks(5);
		context.takeScreenshot("98f-chalices-and-effects");
		context.setScreen(() -> null);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (int i = 0; i < 3; i++) {
				player.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
			}
			player.removeEffect(com.huntlike.potion.ModPotions.HIGHER_HORSEPOWER);
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer mate = packmate(server);
			QuestManager.data(mate).quest = null;
			removeFromPlayerList(server, mate);
			mate.discard();
			var data = QuestManager.data(player(server));
			data.quest = null;
			data.pausedQuest = null;
			player(server).setAttached(QuestManager.DATA, data);
			player(server).removeEffect(com.huntlike.potion.ModPotions.BLAZING_SOUL);
			player(server).removeEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
			com.huntlike.hunt.HuntingPacks.resetAll();
		});
	}

	private static net.minecraft.world.item.ItemStack trophyStack = net.minecraft.world.item.ItemStack.EMPTY;

	/**
	 * A full-strength Slick Kama swing on every Cluster boss: the boss should lose the weapon's attack damage plus its
	 * Cluster damage (2). Logs what each boss actually lost.
	 */
	private static void clusterWeaponDamage(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		List<String> results = new java.util.ArrayList<>();
		for (int tier = 1; tier <= 5; tier++) {
			int t = tier;
			singleplayer.getServer().runCommand("huntlike quest cancel @a");
			context.waitTicks(3);
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
						new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModScythes.SLICK_KAMA));
				player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 600, 4));
				QuestManager.forceBoss(player, com.huntlike.hunt.HuntDefs.CLUSTER, t);
			});
			for (int i = 0; i < 100; i++) {
				context.waitTick();
				if (singleplayer.getServer().computeOnServer(server -> !com.huntlike.hunt.ClusterFight.bodies(player(server)).isEmpty())) {
					break;
				}
			}
			context.waitTicks(25);
			String result = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				var bodies = com.huntlike.hunt.ClusterFight.bodies(player);
				if (bodies.isEmpty()) {
					return "Tier " + t + ": no boss";
				}
				var body = bodies.get(0);
				body.setInvulnerableTime(0);
				float before = body.getHealth();
				double weapon = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
				player.attack(body);
				float lost = before - body.getHealth();
				return "Tier " + t + " " + body.getType().getDescriptionId() + ": lost " + lost + " (weapon " + weapon + " + cluster 2 = "
						+ (weapon + 2) + ", charge " + player.getAttackStrengthScale(0.5F) + ")";
			});
			HuntLike.LOGGER.info("[test] Cluster weapon damage: {}", result);
			results.add(result);
		}
		// Every Cluster boss takes a full swing: attack damage 4 + Cluster damage 2 (no armor on the magma ones).
		if (!results.stream().allMatch(r -> r.contains("lost 6.0"))) {
			throw new AssertionError("A Cluster boss doesn't take attack damage + Cluster damage in full: " + results);
		}
		singleplayer.getServer().runCommand("huntlike quest cancel @a");
		context.waitTicks(3);
	}

	private static void killAll(TestSingleplayerContext singleplayer, java.util.function.Function<ServerPlayer, List<? extends net.minecraft.world.entity.LivingEntity>> which) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (var mob : which.apply(player)) {
				mob.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 100000.0F);
			}
		});
	}

	/** The Cluster Hunt: Soul Sand tokens, whole-cluster quota, Mitosis, Baller, Yummy!, Watch out! and Thermodynamic. */
	private static void clusterHunt(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		String name = singleplayer.getServer().computeOnServer(server -> player(server).getScoreboardName());
		// Locked: below Animal Rank III the rail shows a padlock, and a selected Cluster Hunt reopens on Animal.
		int savedXp = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).progress("animal").xp);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			data.progress("animal").xp = 0;
			data.selectedHunt = "cluster";
			player.setAttached(QuestManager.DATA, data);
			QuestManager.sync(player);
		});
		context.waitTicks(3);
		singleplayer.getServer().runOnServer(server -> QuestManager.openMenu(player(server)));
		context.waitForScreen(HuntMenuScreen.class);
		context.waitTicks(10);
		context.getInput().pressKey(InputConstants.KEY_S);
		context.waitTicks(5);
		context.takeScreenshot("39a-interlude-locked");
		context.getInput().pressKey(InputConstants.KEY_S);
		context.waitTicks(5);
		context.takeScreenshot("39-cluster-locked");
		context.setScreen(() -> null);
		context.waitTicks(3);
		singleplayer.getServer().runOnServer(server -> QuestManager.openMenu(player(server)));
		context.waitForScreen(HuntMenuScreen.class);
		context.waitTicks(5);
		check(singleplayer, "a locked Hunt reopens on Animal", data -> data.selectedHunt.equals("animal"));
		context.setScreen(() -> null);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			data.progress("animal").xp = savedXp;
			player.setAttached(QuestManager.DATA, data);
			QuestManager.sync(player);
		});
		singleplayer.getServer().runCommand("execute as " + name + " run huntlike xp add 60 cluster");
		context.waitTicks(2);
		check(singleplayer, "/huntlike xp add <amount> cluster", data -> data.progress("cluster").xp == 60);
		singleplayer.getServer().runOnServer(server -> {
			HuntData data = QuestManager.data(player(server));
			data.progress("cluster").xp = 0;
			player(server).setAttached(QuestManager.DATA, data);
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var icy = com.huntlike.gear.ModGear.ICY;
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(icy.helmet()));
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(icy.chestplate()));
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(icy.leggings()));
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(icy.boots()));
		});
		context.waitTicks(3);
		assertServer(singleplayer, "Icy Armor full set is Slippery with iron armor's 15 points", server -> {
			ServerPlayer player = player(server);
			boolean slippery = com.huntlike.gear.GearEffects.slippery(player) && com.huntlike.gear.GearEffects.armorPieces(player) == 0;
			HuntLike.LOGGER.info("[test] Icy armor value {}", player.getArmorValue());
			return slippery && player.getArmorValue() == 15;
		});
		wear(singleplayer, com.huntlike.gear.ModGear.GEL);
		context.waitTicks(3);
		assertServer(singleplayer, "Gel Armor: 20 armor, 6 toughness, Better Than Ever!", server -> {
			ServerPlayer player = player(server);
			double toughness = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
			HuntLike.LOGGER.info("[test] Gel armor {} toughness {}", player.getArmorValue(), toughness);
			return player.getArmorValue() == 20 && Math.abs(toughness - 6.0) < 0.01 && com.huntlike.gear.GearEffects.betterThanEver(player)
					&& !com.huntlike.gear.GearEffects.roomTemperature(player);
		});
		assertServer(singleplayer, "Thermodynamic lore: each ability once, each marked (full set)", server -> {
			var lore = new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModGear.THERMODYNAMIC.helmet())
					.get(net.minecraft.core.component.DataComponents.LORE).lines();
			List<String> lines = lore.stream().map(net.minecraft.network.chat.Component::getString).toList();
			HuntLike.LOGGER.info("[test] Thermodynamic lore {}", lines);
			return lines.stream().filter(l -> l.startsWith("Better Than Ever!")).count() == 1
					&& lines.stream().filter(l -> l.startsWith("Room Temperature")).count() == 1
					&& java.util.Collections.frequency(lines, "(full set)") == 3;
		});
		wear(singleplayer, com.huntlike.gear.ModGear.THERMODYNAMIC);
		context.waitTicks(3);
		assertServer(singleplayer, "Thermodynamic Armor: 20 armor, 12 toughness, 0.4 knockback resistance, Room Temperature", server -> {
			ServerPlayer player = player(server);
			double toughness = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ARMOR_TOUGHNESS);
			double knockback = player.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
			HuntLike.LOGGER.info("[test] Thermodynamic armor {} toughness {} knockback {}", player.getArmorValue(), toughness, knockback);
			return player.getArmorValue() == 20 && Math.abs(toughness - 12.0) < 0.01 && Math.abs(knockback - 0.4) < 0.01
					&& com.huntlike.gear.GearEffects.roomTemperature(player);
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setRemainingFireTicks(100);
			player.setTicksFrozen(100);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.SLOWNESS, 200, 0));
		});
		context.waitTicks(2);
		assertServer(singleplayer, "Room Temperature: no burning, freezing, Slowness or lava damage", server -> {
			ServerPlayer player = player(server);
			boolean lava = com.huntlike.gear.GearEffects.allowDamage(player, player.level().damageSources().lava(), 4.0F);
			boolean freeze = com.huntlike.gear.GearEffects.allowDamage(player, player.level().damageSources().freeze(), 1.0F);
			HuntLike.LOGGER.info("[test] Room Temperature fire {} frozen {} slow {} lava {} freeze {}", player.getRemainingFireTicks(),
					player.getTicksFrozen(), player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS), lava, freeze);
			return player.getRemainingFireTicks() <= 0 && player.getTicksFrozen() == 0
					&& !player.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS) && !lava && !freeze;
		});
		wear(singleplayer, com.huntlike.gear.ModGear.ICY);
		context.waitTicks(3);
		assertServer(singleplayer, "cluster hunt unlocked at Animal Rank III; Spirit Lure brings slimes here", server ->
				com.huntlike.hunt.HuntDefs.huntUnlocked(QuestManager.data(player(server)), com.huntlike.hunt.HuntDefs.CLUSTER)
						&& com.huntlike.hunt.SpiritLure.huntableHere(player(server), com.huntlike.hunt.HuntDefs.CLUSTER)
								.equals(List.of(EntityTypes.SLIME)));
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			give(player, net.minecraft.world.item.Items.SOUL_SAND, 5);
		});
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("buy_tokens", "cluster", 0)));
		context.waitTicks(5);
		check(singleplayer, "5 Soul Sand -> 64 Cluster tokens", data -> data.progress("cluster").tokens == 64);

		// Menu: the Cluster Hunt's boss cards.
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("select", "cluster", 0)));
		context.waitTicks(3);
		singleplayer.getServer().runOnServer(server -> QuestManager.openMenu(player(server)));
		context.waitForScreen(HuntMenuScreen.class);
		context.waitTicks(10);
		context.takeScreenshot("40-cluster-card");
		for (int i = 0; i < 4; i++) {
			context.getInput().pressKey(InputConstants.KEY_D);
			context.waitTicks(5);
			if (i < 3) {
				context.takeScreenshot("40" + (char) ('b' + i) + "-cluster-tier" + (i + 2) + "-card");
			}
		}
		context.takeScreenshot("41-cluster-tier5-card");
		for (int i = 0; i < 4; i++) {
			context.getInput().pressKey(InputConstants.KEY_A);
		}
		context.setScreen(() -> new HuntMenuScreen(1));
		context.waitTicks(5);
		context.takeScreenshot("42-cluster-gear");
		context.setScreen(() -> null);

		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("start", "cluster", 1)));
		context.waitTicks(5);
		check(singleplayer, "cluster tier I started", data -> data.quest != null && data.quest.hunt.equals("cluster")
				&& data.quest.tier == 1 && data.quest.quota == 2);

		// A whole cluster counts once: a medium slime and both of its pieces = 1 point.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var slime = EntityTypes.SLIME.create(player.level(), EntitySpawnReason.COMMAND);
			slime.setSize(2, true);
			slime.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0, 0);
			slime.setNoAi(true);
			player.level().addFreshEntity(slime);
		});
		context.waitTicks(3);
		killAll(singleplayer, player -> player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.cubemob.Slime.class,
				player.getBoundingBox().inflate(8), m -> m.getType() == EntityTypes.SLIME && m.isAlive()));
		context.waitTicks(30);
		int pieces = singleplayer.getServer().computeOnServer(server -> player(server).level().getEntitiesOfClass(
				net.minecraft.world.entity.monster.cubemob.Slime.class, player(server).getBoundingBox().inflate(8),
				m -> m.getType() == EntityTypes.SLIME && m.isAlive()).size());
		HuntLike.LOGGER.info("[test] medium slime split into {} pieces", pieces);
		check(singleplayer, "no quota point until the whole cluster is dead", data -> data.quest.kills == 0);
		killAll(singleplayer, player -> player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.cubemob.Slime.class,
				player.getBoundingBox().inflate(8), m -> m.getType() == EntityTypes.SLIME && m.isAlive()));
		context.waitTicks(3);
		check(singleplayer, "whole slime cluster = 1 point", data -> data.quest.kills == 1);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var magma = EntityTypes.MAGMA_CUBE.create(player.level(), EntitySpawnReason.COMMAND);
			magma.setSize(1, true);
			magma.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0, 0);
			player.level().addFreshEntity(magma);
		});
		context.waitTicks(3);
		killAll(singleplayer, player -> player.level().getEntitiesOfClass(net.minecraft.world.entity.monster.cubemob.MagmaCube.class,
				player.getBoundingBox().inflate(8), m -> m.getType() == EntityTypes.MAGMA_CUBE && m.isAlive()));
		context.waitTicks(3);
		check(singleplayer, "magma cube cluster = 2 points, quota full", data -> data.quest.kills == 2
				&& data.quest.phase != HuntData.PHASE_KILLING);
		context.waitTicks(60);
		assertServer(singleplayer, "Cubert spawned as one main body", server -> {
			var bodies = com.huntlike.hunt.ClusterFight.bodies(player(server));
			return bodies.size() == 1 && bodies.get(0).getType() == com.huntlike.entity.ModEntities.CUBERT;
		});
		context.takeScreenshot("43-cubert");
		killAll(singleplayer, player -> com.huntlike.hunt.ClusterFight.bodies(player));
		context.waitTicks(2);
		assertServer(singleplayer, "Mitosis: Cubert split into 2", server -> com.huntlike.hunt.ClusterFight.bodies(player(server)).size() == 2);
		context.waitTicks(20);
		context.takeScreenshot("44-cubert-split");
		killAll(singleplayer, player -> com.huntlike.hunt.ClusterFight.bodies(player));
		context.waitTicks(5);
		check(singleplayer, "cluster tier I complete with Thermal Slimeballs", data -> data.quest == null
				&& data.progress("cluster").bossKills[0] == 1 && data.progress("cluster").dropCounts.getOrDefault("thermal_slimeball", 0) >= 1);

		// Tier V: The Vile (in Gel Armor, for Better Than Ever!).
		wear(singleplayer, com.huntlike.gear.ModGear.GEL);
		int ballsBefore = com.huntlike.hunt.ClusterFight.ballsThrown;
		singleplayer.getServer().runCommand("huntlike boss cluster 5 " + name);
		context.waitTicks(45);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var main = com.huntlike.hunt.ClusterFight.bodies(player).get(0);
			main.setNoAi(true);
			main.teleportTo(player.getX(), player.getY(), player.getZ() + 6);
		});
		context.runOnClient(client -> {
			client.player.setYRot(0.0F);
			client.player.setXRot(15.0F);
		});
		context.waitTicks(5);
		context.takeScreenshot("45a-the-vile-close");
		boolean core = singleplayer.getServer().computeOnServer(server ->
				((com.huntlike.entity.cluster.TheVile) com.huntlike.hunt.ClusterFight.bodies(player(server)).get(0)).isMagmaCore());
		context.waitTicks(110);
		for (int i = 0; i < 20 && com.huntlike.hunt.ClusterFight.ballsThrown == ballsBefore; i++) {
			context.waitTicks(10);
		}
		context.takeScreenshot("45b-the-vile-switched");
		assertServer(singleplayer, "Thermodynamic switched the core; Baller threw a ball", server -> {
			var vile = (com.huntlike.entity.cluster.TheVile) com.huntlike.hunt.ClusterFight.bodies(player(server)).get(0);
			HuntLike.LOGGER.info("[test] core {} -> {}, balls {}", core, vile.isMagmaCore(), com.huntlike.hunt.ClusterFight.ballsThrown - ballsBefore);
			return vile.isMagmaCore() != core && com.huntlike.hunt.ClusterFight.ballsThrown > ballsBefore;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var main = com.huntlike.hunt.ClusterFight.bodies(player).get(0);
			main.hurtServer(player.level(), player.level().damageSources().playerAttack(player), main.getMaxHealth() * 0.52F);
		});
		context.waitTicks(5);
		assertServer(singleplayer, "Yummy!: the owner is consumed", server -> com.huntlike.hunt.ClusterFight.isConsumed(player(server))
				&& com.huntlike.hunt.ClusterFight.yummyKeys(player(server)).length == 15);
		assertServer(singleplayer, "Yummy!: the boss regenerates at half its rate", server -> {
			float regen = com.huntlike.hunt.ClusterFight.regenNow(player(server));
			HuntLike.LOGGER.info("[test] regeneration during Yummy!: {}", regen);
			return regen == com.huntlike.hunt.HuntDefs.CLUSTER.tier(5).regen() / 2.0F;
		});
		context.waitTicks(10);
		context.takeScreenshot("46-yummy");
		int[] keys = context.computeOnClient(client -> com.huntlike.client.YummyClient.keys.clone());
		HuntLike.LOGGER.info("[test] Yummy! keys {}", java.util.Arrays.toString(keys));
		singleplayer.getServer().runOnServer(server -> com.huntlike.hunt.ClusterFight.onYummyKey(player(server), (keys[0] + 1) % 7));
		context.waitTicks(2);
		assertServer(singleplayer, "a wrong key does nothing", server -> com.huntlike.hunt.ClusterFight.yummyProgress(player(server)) == 0);
		for (int key : keys) {
			context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new com.huntlike.network.HuntPayloads.Action("yummy_key", "cluster", key)));
		}
		context.waitTicks(5);
		assertServer(singleplayer, "escaped after the whole sequence", server -> !com.huntlike.hunt.ClusterFight.isConsumed(player(server)));
		assertServer(singleplayer, "escaping Yummy!: Slowness I for 5 s; Gel's Better Than Ever! makes Temperature x1.2", server -> {
			ServerPlayer player = player(server);
			var slow = player.getEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
			HuntLike.LOGGER.info("[test] after Yummy! slowness {} multiplier {}", slow,
					com.huntlike.hunt.ClusterFight.temperatureMultiplier(player));
			return slow != null && slow.getAmplifier() == 0 && slow.getDuration() > 80
					&& Math.abs(com.huntlike.hunt.ClusterFight.temperatureMultiplier(player) - 1.2F) < 0.001F;
		});
		singleplayer.getServer().runOnServer(server -> player(server).setHealth(player(server).getMaxHealth()));
		context.waitTicks(30);
		assertServer(singleplayer, "Watch out! started (below 75%)", server -> com.huntlike.hunt.ClusterFight.isWatching(player(server)));
		assertServer(singleplayer, "Watch out!: no regeneration while it lasts", server -> {
			float regen = com.huntlike.hunt.ClusterFight.regenNow(player(server));
			HuntLike.LOGGER.info("[test] regeneration during Watch out!: {}", regen);
			return regen == 0.0F;
		});
		context.takeScreenshot("47-watch-out");
		// Watch out! now deals double damage: keep the test player alive through all three rounds, even when the server lags.
		singleplayer.getServer().runOnServer(server -> player(server).addEffect(new net.minecraft.world.effect.MobEffectInstance(
				net.minecraft.world.effect.MobEffects.RESISTANCE, 300, 4, false, false)));
		context.waitTicks(250);
		killAll(singleplayer, player -> com.huntlike.hunt.ClusterFight.bodies(player));
		// The split can take a few ticks when the CI server lags.
		for (int i = 0; i < 20 && singleplayer.getServer().computeOnServer(server -> com.huntlike.hunt.ClusterFight.bodies(player(server)).size()) != 4; i++) {
			context.waitTicks(1);
		}
		singleplayer.getServer().runOnServer(server -> HuntLike.LOGGER.info("[test] The Vile bodies after the split: {}",
				com.huntlike.hunt.ClusterFight.bodies(player(server)).size()));
		assertServer(singleplayer, "Mitosis: The Vile split into 4", server -> com.huntlike.hunt.ClusterFight.bodies(player(server)).size() == 4);
		// Thermodynamic keeps going after the split: every piece switches its core.
		final int[] magmaBefore = new int[1];
		singleplayer.getServer().runOnServer(server -> {
			magmaBefore[0] = (int) com.huntlike.hunt.ClusterFight.bodies(player(server)).stream()
					.filter(b -> ((com.huntlike.entity.cluster.TheVile) b).isMagmaCore()).count();
			com.huntlike.hunt.ClusterFight.switchCoresSoon(player(server));
		});
		context.waitTicks(3);
		assertServer(singleplayer, "Thermodynamic: after The Vile splits, its pieces still switch cores", server -> {
			var bodies = com.huntlike.hunt.ClusterFight.bodies(player(server));
			int magmaAfter = (int) bodies.stream().filter(b -> ((com.huntlike.entity.cluster.TheVile) b).isMagmaCore()).count();
			HuntLike.LOGGER.info("[test] split Vile: magma cores {} -> {} of {}", magmaBefore[0], magmaAfter, bodies.size());
			return bodies.size() == 4 && magmaAfter == 4 - magmaBefore[0];
		});
		killAll(singleplayer, player -> com.huntlike.hunt.ClusterFight.bodies(player));
		context.waitTicks(2);
		assertServer(singleplayer, "...and does it keep going? 16", server -> com.huntlike.hunt.ClusterFight.bodies(player(server)).size() == 16);
		context.waitTicks(20);
		context.takeScreenshot("48-vile-sixteen");
		killAll(singleplayer, player -> com.huntlike.hunt.ClusterFight.bodies(player));
		context.waitTicks(5);
		check(singleplayer, "cluster tier V complete", data -> data.quest == null && data.progress("cluster").bossKills[4] == 1);
		clusterWeaponDamage(context, singleplayer);
		assertServer(singleplayer, "Better Than Ever! ends when the boss perishes", server ->
				!com.huntlike.hunt.ClusterFight.betterThanEver(player(server)));
	}

	private static com.huntlike.rogue.RogueHunter rogue(MinecraftServer server) {
		ServerPlayer player = player(server);
		var list = player.level().getEntitiesOfClass(com.huntlike.rogue.RogueHunter.class, player.getBoundingBox().inflate(40));
		return list.isEmpty() ? null : list.get(0);
	}

	private static void waitForRogue(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		for (int i = 0; i < 60; i++) {
			if (singleplayer.getServer().computeOnServer(server -> rogue(server) != null)) {
				return;
			}
			context.waitTicks(1);
		}
		throw new AssertionError("No Rogue Hunter spawned");
	}

	private static void killRogue(TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var hunter = rogue(server);
			hunter.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1000.0F);
		});
	}

	/** Rogue Hunters: the 30th lure kill calls one, it's invisible with an outline only its player sees, kills are counted. */
	private static void rogueHunters(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			data.progress("cluster").xp = com.huntlike.hunt.HuntDefs.RANK_XP[10];
			player.setAttached(QuestManager.DATA, data);
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20 * 600, 4));
			for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(40))) {
				e.discard();
			}
		});
		singleplayer.getServer().runCommand("huntlike quest start @a cluster 1");
		context.waitTicks(3);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			data.rogueLures = com.huntlike.rogue.RogueHunters.LURE_ODDS - 1;
			data.rogueSinceSpecial = 0;
			QuestManager.countLuredKill(player, data);
		});
		context.waitTicks(3);
		assertServer(singleplayer, "the 30th lure kill calls a Rogue Hunter and pauses the quest",
				server -> com.huntlike.rogue.RogueHunters.active(player(server)) && QuestManager.data(player(server)).rogueLures == 0);
		waitForRogue(context, singleplayer);
		int rogueId = singleplayer.getServer().computeOnServer(server -> rogue(server).getId());
		context.waitTicks(10);
		boolean outlined = context.computeOnClient(client -> {
			var entity = client.level.getEntity(rogueId);
			HuntLike.LOGGER.info("[test] client rogue: {} invisible {} glowing {} hunted {} me {}", entity,
					entity == null ? null : entity.isInvisible(), entity == null ? null : client.shouldEntityAppearGlowing(entity),
					entity instanceof com.huntlike.rogue.RogueHunter r ? r.hunted() : null, client.player.getUUID());
			return entity != null && entity.isInvisible() && client.shouldEntityAppearGlowing(entity);
		});
		HuntLike.LOGGER.info("[test] Rogue Hunter invisible with outline for its player: {}", outlined);
		if (!outlined) {
			throw new AssertionError("Rogue Hunter outline");
		}
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var hunter = rogue(server);
			hunter.setNoAi(true);
			hunter.snapTo(player.getX() + 3, player.getY(), player.getZ() + 3, 225.0F, 0.0F);
		});
		context.waitTicks(5);
		context.takeScreenshot("80-rogue-hunter-outline");
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			rogue(server).hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1.0F);
		});
		context.waitTicks(5);
		context.takeScreenshot("81-rogue-hunter-revealed");
		assertServer(singleplayer, "a hit reveals the Rogue Hunter (40 health; 5 damage, 8 from Rank VIII)", server -> {
			var hunter = rogue(server);
			// One in ten is special (80 health; 8 damage, 12 from Rank VIII) even before the 10th.
			float health = hunter.isSpecial() ? 80.0F : 40.0F;
			boolean strong = com.huntlike.rogue.RogueHunters.strong(QuestManager.data(player(server)));
			double damage = strong ? (hunter.isSpecial() ? 12.0 : 8.0) : (hunter.isSpecial() ? 8.0 : 5.0);
			HuntLike.LOGGER.info("[test] Rogue Hunter: special {} strong {} damage {}", hunter.isSpecial(), strong,
					hunter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
			return !hunter.isInvisible() && hunter.getMaxHealth() == health
					&& hunter.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == damage;
		});
		check(singleplayer, "stronger Rogue Hunters only when Rank VIII in the quest's own Hunt", data -> {
			HuntData copy = HuntData.fromTag(data.toTag());
			copy.quest = new HuntData.Quest();
			copy.progress("animal").xp = 1_000_000;
			copy.progress("cluster").xp = 0;
			copy.quest.hunt = "animal";
			boolean animal = com.huntlike.rogue.RogueHunters.strong(copy);
			copy.quest.hunt = "cluster";
			boolean cluster = com.huntlike.rogue.RogueHunters.strong(copy);
			copy.quest.hunt = com.huntlike.interlude.InterludeDefs.SLIME_RIDER;
			boolean interlude = com.huntlike.rogue.RogueHunters.strong(copy);
			HuntLike.LOGGER.info("[test] strong Rogue Hunters: Animal quest {} Cluster quest {} Interlude {}", animal, cluster, interlude);
			return animal && !cluster && interlude;
		});
		check(singleplayer, "Rogue Hunter damage: 5 / special 8 before Rank VIII, 8 / special 12 from Rank VIII", data ->
				com.huntlike.rogue.RogueHunter.damage(false, false) == 5.0F && com.huntlike.rogue.RogueHunter.damage(true, false) == 8.0F
						&& com.huntlike.rogue.RogueHunter.damage(false, true) == 8.0F && com.huntlike.rogue.RogueHunter.damage(true, true) == 12.0F);
		// The lure is stuck at 3 of 3 spawns with a lured cow alive: the hunter's death starts the lure over.
		final java.util.UUID[] luredCow = new java.util.UUID[1];
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			com.huntlike.hunt.SpiritLure.setSpawnsSinceKill(player, com.huntlike.hunt.SpiritLure.SPAWNS_PER_KILL);
			var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
			cow.snapTo(player.getX() - 5, player.getY(), player.getZ() - 5, 0.0F, 0.0F);
			cow.addTag(com.huntlike.hunt.SpiritLure.LURED_TAG);
			cow.addTag(com.huntlike.hunt.BossController.OWNER_PREFIX + player.getUUID());
			player.level().addFreshEntity(cow);
			luredCow[0] = cow.getUUID();
		});
		killRogue(singleplayer);
		context.waitTicks(3);
		check(singleplayer, "a Rogue Hunter kill is counted and the quest goes on", data -> data.rogueKills == 1 && data.quest != null);
		assertServer(singleplayer, "a killed Rogue Hunter vanishes in smoke: no body left behind", server -> rogue(server) == null);
		assertServer(singleplayer, "the Rogue Hunter's death resets the Spirit Lure: 0 of 3 spawns, the old lured cow no longer counts",
				server -> {
					ServerPlayer player = player(server);
					var cow = player.level().getEntity(luredCow[0]);
					int spawns = com.huntlike.hunt.SpiritLure.spawnsSinceKill(player);
					boolean lured = cow != null && com.huntlike.hunt.SpiritLure.isLured(cow);
					HuntLike.LOGGER.info("[test] lure after the hunter died: spawns {} cow still lured {}", spawns, lured);
					if (cow != null) {
						cow.discard();
					}
					return spawns == 0 && !lured;
				});
		assertServer(singleplayer, "encounter over", server -> !com.huntlike.rogue.RogueHunters.active(player(server)));

		// The 10th hunter is a special one.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			data.rogueSinceSpecial = com.huntlike.rogue.RogueHunters.SPECIAL_ODDS - 1;
			com.huntlike.rogue.RogueHunters.trigger(player, data);
		});
		waitForRogue(context, singleplayer);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var hunter = rogue(server);
			hunter.setNoAi(true);
			hunter.snapTo(player.getX() + 3, player.getY(), player.getZ() + 3, 225.0F, 0.0F);
			hunter.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1.0F);
		});
		context.waitTicks(5);
		context.takeScreenshot("82-special-rogue-hunter");
		assertServer(singleplayer, "a special Rogue Hunter (80 health, 12 damage)", server -> {
			var hunter = rogue(server);
			return hunter.isSpecial() && hunter.getMaxHealth() == 80.0F;
		});
		killRogue(singleplayer);
		context.waitTicks(3);
		check(singleplayer, "special kills counted too", data -> data.rogueKills == 2 && data.specialRogueKills >= 1);

		// A player's death ends the encounter, not the quest.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			com.huntlike.rogue.RogueHunters.trigger(player, QuestManager.data(player));
		});
		waitForRogue(context, singleplayer);
		singleplayer.getServer().runCommand("huntlike quest cancel @a");
		singleplayer.getServer().runOnServer(server -> {
			var hunter = rogue(server);
			hunter.discard();
		});
		context.waitTicks(3);
		assertServer(singleplayer, "a vanished hunter ends the encounter", server -> !com.huntlike.rogue.RogueHunters.active(player(server)));
	}

	/** Towernaar's questline: three chapters, the shields, the sigil, the Magnetic Wand and the Final Chapter's boss. */
	private static void towernaar(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			give(player, net.minecraft.world.item.Items.SHIELD, 1);
			give(player, net.minecraft.world.item.Items.IRON_INGOT, 16);
			give(player, net.minecraft.world.item.Items.OBSIDIAN, 8);
			give(player, net.minecraft.world.item.Items.NETHERITE_SCRAP, 2);
			give(player, net.minecraft.world.item.Items.ENDER_PEARL, 16);
		});
		context.waitTicks(5);
		context.setScreen(() -> new HuntMenuScreen(1));
		context.waitTicks(3);
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("select", "cluster", 0)));
		context.waitTicks(5);
		context.setScreen(() -> new HuntMenuScreen(1));
		context.waitTicks(5);
		context.takeScreenshot("83-cluster-misc-towernaar");
		context.setScreen(() -> new com.huntlike.client.screen.TowernaarScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("84-towernaar-first-visit");
		boolean shotKills = false;
		boolean[] shotGallery = new boolean[1];
		for (int i = 0; i < 400; i++) {
			int[] state = singleplayer.getServer().computeOnServer(server -> {
				HuntData data = QuestManager.data(player(server));
				return new int[] {data.towernaarDone, data.towernaarStep};
			});
			if (state[0] >= 3) {
				break;
			}
			if (state[1] > 0) {
				var step = com.huntlike.towernaar.TowernaarScript.CHAPTERS.get(state[0]).steps().get(state[1] - 1);
				if (step.kind() == com.huntlike.towernaar.TowernaarScript.Kind.KILLS) {
					if (!shotKills) {
						context.waitTicks(3);
						context.takeScreenshot("85-towernaar-kill-quest");
						if (!shotGallery[0]) {
							shotGallery[0] = true;
							galleryShot(context, "5-towernaar-first-dialogue");
						}
						shotKills = true;
					}
					// Rogue Hunters slain since the quest started.
					singleplayer.getServer().runOnServer(server -> {
						HuntData data = QuestManager.data(player(server));
						data.rogueKills = data.towernaarKillBase + step.kills();
					});
				}
			}
			context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
					new com.huntlike.network.HuntPayloads.Action("towernaar_click", "cluster", 0)));
			context.waitTicks(1);
			if (state[0] == 0 && state[1] >= 12 && !shotGallery[0]) {
				shotGallery[0] = true;
				context.waitTicks(3);
				galleryShot(context, "5-towernaar-first-dialogue");
			}
			if (state[0] == 1 && state[1] == 67) {
				context.waitTicks(3);
				context.takeScreenshot("86-towernaar-name");
			}
		}
		context.waitTicks(5);
		context.takeScreenshot("87-towernaar-chapter3-done");
		assertServer(singleplayer, "Towernaar's chapters I-III: shields given and taken, advancements", server -> {
			ServerPlayer player = player(server);
			boolean star = false;
			boolean other = false;
			var inventory = player.getInventory();
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				star |= inventory.getItem(i).is(com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD_STAR);
				other |= inventory.getItem(i).is(com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD)
						|| inventory.getItem(i).is(com.huntlike.gear.ModShields.OBSIDIAN_SHIELD);
			}
			HuntLike.LOGGER.info("[test] Towernaar: star {} leftover shields {} advancements {} {} {}", star, other,
					done(player, "towernaar/chapter1"), done(player, "towernaar/chapter2"), done(player, "towernaar/chapter3"));
			return star && !other && done(player, "towernaar/chapter1") && done(player, "towernaar/chapter2")
					&& done(player, "towernaar/chapter3") && QuestManager.data(player).towernaarDone == 3;
		});

		// Shields: Obsidian (2500 durability, Unbreaking III from the table), Unbreakable, ★ without slowdown.
		assertServer(singleplayer, "shield stats", server -> {
			ServerPlayer player = player(server);
			var obsidian = new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModShields.OBSIDIAN_SHIELD);
			var table = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
					.getOrThrow(net.minecraft.tags.EnchantmentTags.IN_ENCHANTING_TABLE);
			boolean unbreaking3 = true;
			for (int i = 0; i < 20; i++) {
				var picks = net.minecraft.world.item.enchantment.EnchantmentHelper.selectEnchantment(player.getRandom(), obsidian, 30,
						table.stream());
				unbreaking3 &= picks.stream().anyMatch(e -> e.enchantment().is(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING)
						&& e.level() == 3);
			}
			var star = new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD_STAR);
			var effects = star.get(net.minecraft.core.component.DataComponents.USE_EFFECTS);
			HuntLike.LOGGER.info("[test] Obsidian Shield durability {} Unbreaking III every time {}; ★ speed {}", obsidian.getMaxDamage(),
					unbreaking3, effects == null ? -1 : effects.speedMultiplier());
			var blocks = obsidian.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
			boolean sameAsVanilla = blocks != null && blocks.equals(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SHIELD)
					.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS));
			HuntLike.LOGGER.info("[test] Obsidian Shield blocks like the vanilla shield: {}", sameAsVanilla);
			return sameAsVanilla && obsidian.getMaxDamage() == 2500 && unbreaking3 && effects != null && effects.speedMultiplier() == 1.0F
					&& new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD)
							.has(net.minecraft.core.component.DataComponents.UNBREAKABLE);
		});

		// The sigil: any other Unbreakable Shield counts Rogue Hunter kills in the offhand.
		singleplayer.getServer().runOnServer(server -> player(server).setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
				new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD)));
		context.waitTicks(45);
		assertServer(singleplayer, "a new Unbreakable Shield gets the 0/10 counter",
				server -> com.huntlike.gear.ModShields.sigilKills(player(server).getOffhandItem()) == 0);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			com.huntlike.gear.ModShields.setSigilKills(player.getOffhandItem(), 9);
			com.huntlike.rogue.RogueHunters.trigger(player, QuestManager.data(player));
		});
		waitForRogue(context, singleplayer);
		killRogue(singleplayer);
		context.waitTicks(3);
		assertServer(singleplayer, "the 10th kill turns it into the Unbreakable Shield ★",
				server -> player(server).getOffhandItem().is(com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD_STAR));

		// The Final Chapter.
		context.setScreen(() -> new com.huntlike.client.screen.TowernaarScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("88-towernaar-final-chapter");
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("towernaar_start", "cluster", 0)));
		context.setScreen(() -> null);
		context.waitTicks(3);
		assertServer(singleplayer, "Final Chapter waits for an enderman kill",
				server -> com.huntlike.towernaar.TowernaarFight.phase(player(server)) == 1);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var enderman = spawnTest(player, EntityTypes.ENDERMAN, 3.0, 0.0, 1.0F);
			enderman.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 100.0F);
		});
		// The summoning star first (2 seconds), then he appears.
		context.waitTicks(20);
		assertServer(singleplayer, "the summoning star plays before he appears", server -> {
			ServerPlayer player = player(server);
			return com.huntlike.towernaar.TowernaarFight.phase(player) == 2 && player.level().getEntitiesOfClass(
					com.huntlike.towernaar.TowernaarUndead.class, player.getBoundingBox().inflate(20)).isEmpty();
		});
		context.takeScreenshot("88b-towernaar-summoning");
		context.waitTicks(30);
		assertServer(singleplayer, "Towernaar the Undead appears (300 health)", server -> {
			ServerPlayer player = player(server);
			var bosses = player.level().getEntitiesOfClass(com.huntlike.towernaar.TowernaarUndead.class, player.getBoundingBox().inflate(20));
			return com.huntlike.towernaar.TowernaarFight.phase(player) == 2 && bosses.size() == 1 && bosses.get(0).getMaxHealth() == 300.0F;
		});
		context.waitTicks(100);
		context.takeScreenshot("89-towernaar-undead");
		assertServer(singleplayer, "he throws end stone without placing blocks", server -> {
			ServerPlayer player = player(server);
			int endStone = 0;
			BlockPos at = player.blockPosition();
			for (BlockPos pos : BlockPos.betweenClosed(at.offset(-12, -3, -12), at.offset(12, 6, 12))) {
				if (player.level().getBlockState(pos).is(net.minecraft.world.level.block.Blocks.END_STONE)) {
					endStone++;
				}
			}
			HuntLike.LOGGER.info("[test] Towernaar boss: player health {} end stone blocks {}", player.getHealth(), endStone);
			return endStone == 0;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var boss = player.level().getEntitiesOfClass(com.huntlike.towernaar.TowernaarUndead.class, player.getBoundingBox().inflate(30)).get(0);
			boss.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1000.0F);
		});
		context.waitTicks(3);
		assertServer(singleplayer, "beaten, he stays at 1 health and can't be hurt", server -> {
			ServerPlayer player = player(server);
			var boss = player.level().getEntitiesOfClass(com.huntlike.towernaar.TowernaarUndead.class, player.getBoundingBox().inflate(30)).get(0);
			boss.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1000.0F);
			return boss.isAlive() && boss.isEnding() && boss.getHealth() == 1.0F;
		});
		context.waitTicks(30);
		context.takeScreenshot("89b-towernaar-ending");
		context.waitTicks(40);
		context.takeScreenshot("90-towernaar-thank-you");
		context.waitTicks(60);
		assertServer(singleplayer, "he dies and drops the Magnetic Wand PRD; the questline is complete", server -> {
			ServerPlayer player = player(server);
			var bosses = player.level().getEntitiesOfClass(com.huntlike.towernaar.TowernaarUndead.class, player.getBoundingBox().inflate(30));
			var prd = player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(30),
					e -> e.getItem().is(com.huntlike.gear.ModShields.MAGNETIC_WAND_PRD));
			HuntData data = QuestManager.data(player);
			HuntLike.LOGGER.info("[test] Final Chapter: bosses {} PRD drops {} done {} advancement {}", bosses.size(), prd.size(),
					data.towernaarDone, done(player, "towernaar/final"));
			prd.forEach(net.minecraft.world.entity.Entity::discard);
			return bosses.isEmpty() && prd.size() == 1 && data.towernaarDone == 4 && done(player, "towernaar/final");
		});
		// The PRD is lost (thrown away above): Towernaar's tab now offers another for 1 Paper, 1 Ink Sac and 1 Ender Pearl.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			give(player, net.minecraft.world.item.Items.PAPER, 1);
			give(player, net.minecraft.world.item.Items.INK_SAC, 1);
			give(player, net.minecraft.world.item.Items.ENDER_PEARL, 1);
		});
		context.setScreen(() -> new com.huntlike.client.screen.TowernaarScreen(null));
		context.waitTicks(10);
		context.takeScreenshot("91b-towernaar-prd-trade");
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("towernaar_prd", "cluster", 0)));
		context.waitTicks(5);
		context.setScreen(() -> null);
		assertServer(singleplayer, "a lost PRD is traded for again: Paper, Ink Sac and Ender Pearl taken", server -> {
			var inventory = player(server).getInventory();
			int prds = 0;
			boolean costsLeft = false;
			for (int i = 0; i < inventory.getContainerSize(); i++) {
				var stack = inventory.getItem(i);
				if (stack.is(com.huntlike.gear.ModShields.MAGNETIC_WAND_PRD)) {
					prds += stack.getCount();
				}
				costsLeft |= stack.is(net.minecraft.world.item.Items.PAPER) || stack.is(net.minecraft.world.item.Items.INK_SAC)
						|| stack.is(net.minecraft.world.item.Items.ENDER_PEARL);
			}
			HuntLike.LOGGER.info("[test] PRD trade: PRDs {} costs left {}", prds, costsLeft);
			return prds == 1 && !costsLeft;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			com.huntlike.towernaar.TowernaarFight.start(player, QuestManager.data(player));
		});
		assertServer(singleplayer, "a won Final Chapter can't be repeated",
				server -> com.huntlike.towernaar.TowernaarFight.phase(player(server)) == 0);

		// The PRD unlocks the Magnetic Wand trade.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModShields.MAGNETIC_WAND_PRD));
			player.getMainHandItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			give(player, com.huntlike.registry.ModItems.SLIME_WASHED_HOLMIUM, 1);
			give(player, com.huntlike.registry.ModItems.SUBLIMATING_MAGMA, 1);
			give(player, com.huntlike.registry.ModItems.GELID_SLIME_BALL, 5);
		});
		context.waitTicks(3);
		check(singleplayer, "the PRD is used up and the trade unlocked", data -> data.wandUnlocked);
		check(singleplayer, "once the PRD is used, Towernaar's tab no longer offers it", data ->
				!com.huntlike.towernaar.TowernaarManager.prdTradeOpen(data));
		context.setScreen(() -> new HuntMenuScreen(1));
		context.waitTicks(5);
		context.takeScreenshot("91-cluster-misc-trades");
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("buy_gear", "cluster", 102)));
		context.setScreen(() -> null);
		context.waitTicks(3);

		// The Magnetic Wand pulls mobs to where you look and holds them there for 1.5 s, but not your pets.
		final Object[] wand = new Object[7];
		assertServer(singleplayer, "Magnetic Wand bought; it catches a cow, a boss and a dropped item but not a tamed wolf", server -> {
			ServerPlayer player = player(server);
			for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.Entity.class, player.getBoundingBox().inflate(40),
					e -> e instanceof net.minecraft.world.entity.Mob || e instanceof net.minecraft.world.entity.item.ItemEntity
							|| e instanceof net.minecraft.world.entity.ExperienceOrb)) {
				e.discard();
			}
			boolean bought = false;
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				bought |= player.getInventory().getItem(i).is(com.huntlike.gear.ModShields.MAGNETIC_WAND);
			}
			player.setYRot(0.0F);
			// Aim at the ground about 4.5 blocks ahead, so the creatures stay within the test world's ticking range.
			player.setXRot(20.0F);
			var eye = player.getEyePosition();
			var end = eye.add(player.getLookAngle().scale(com.huntlike.gear.ModShields.WAND_REACH));
			var hit = player.level().clip(new net.minecraft.world.level.ClipContext(eye, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
					net.minecraft.world.level.ClipContext.Fluid.NONE, player));
			var point = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? end : hit.getLocation();
			var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
			cow.snapTo(point.x + 3.5, point.y, point.z, 0.0F, 0.0F);
			player.level().addFreshEntity(cow);
			var wolf = EntityTypes.WOLF.create(player.level(), EntitySpawnReason.COMMAND);
			wolf.snapTo(point.x - 4.0, point.y, point.z, 0.0F, 0.0F);
			wolf.tame(player);
			wolf.setOrderedToSit(true);
			wolf.setInSittingPose(true);
			player.level().addFreshEntity(wolf);
			var cubert = com.huntlike.entity.ModEntities.CUBERT.create(player.level(), EntitySpawnReason.COMMAND);
			cubert.snapTo(point.x, point.y, point.z - 3.5, 0.0F, 0.0F);
			player.level().addFreshEntity(cubert);
			var bone = new net.minecraft.world.entity.item.ItemEntity(player.level(), point.x - 2.0, point.y + 0.2, point.z + 2.5,
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BONE));
			bone.setPickUpDelay(200);
			player.level().addFreshEntity(bone);
			wand[5] = cubert.getUUID();
			wand[6] = bone.getUUID();
			int pulled = com.huntlike.gear.ModShields.pull(player);
			wand[0] = point;
			wand[1] = cow.getUUID();
			wand[2] = wolf.getUUID();
			wand[3] = wolf.position();
			wand[4] = player.level().getGameTime();
			HuntLike.LOGGER.info("[test] Magnetic Wand bought {} aim {} caught {}", bought, point, pulled);
			return bought && pulled == 3;
		});
		// Count server ticks (CI servers can lag behind the client).
		waitServerTicks(context, singleplayer, (long) wand[4], 15);
		assertServer(singleplayer, "after 0.75 s the cow is at the spot, without being launched", server -> {
			var level = player(server).level();
			var cow = level.getEntity((java.util.UUID) wand[1]);
			var point = (net.minecraft.world.phys.Vec3) wand[0];
			double flat = cow == null ? 99 : Math.hypot(cow.getX() - point.x, cow.getZ() - point.z);
			var cubert = level.getEntity((java.util.UUID) wand[5]);
			var bone = level.getEntity((java.util.UUID) wand[6]);
			double cubertFlat = cubert == null ? 99 : Math.hypot(cubert.getX() - point.x, cubert.getZ() - point.z);
			double boneFlat = bone == null ? 99 : Math.hypot(bone.getX() - point.x, bone.getZ() - point.z);
			HuntLike.LOGGER.info("[test] Magnetic Wand at 15 ticks: cow {} blocks from the spot, {} above it; boss {}, item {}", flat,
					cow == null ? 0 : cow.getY() - point.y, cubertFlat, boneFlat);
			// Close to the spot (the boss sits on it, so the cow is pressed against its body), and not launched.
			return flat < 3.0 && cow.getY() - point.y < 2.0 && cubertFlat < 2.0 && boneFlat < 1.5;
		});
		waitServerTicks(context, singleplayer, (long) wand[4], 27);
		assertServer(singleplayer, "it is still held there at 1.35 s; the tamed wolf never moved toward it", server -> {
			var level = player(server).level();
			var cow = level.getEntity((java.util.UUID) wand[1]);
			var wolf = level.getEntity((java.util.UUID) wand[2]);
			var point = (net.minecraft.world.phys.Vec3) wand[0];
			double flat = cow == null ? 99 : Math.hypot(cow.getX() - point.x, cow.getZ() - point.z);
			double wolfMoved = wolf == null ? 0 : wolf.position().distanceTo((net.minecraft.world.phys.Vec3) wand[3]);
			HuntLike.LOGGER.info("[test] Magnetic Wand at 27 ticks: cow {} blocks from the spot; wolf moved {}", flat, wolfMoved);
			return flat < 3.0 && (wolf == null || Math.hypot(wolf.getX() - point.x, wolf.getZ() - point.z) > 2.5);
		});
		waitServerTicks(context, singleplayer, (long) wand[4], 35);
		assertServer(singleplayer, "the cow reached the spot, so the hold lasts 33% longer: still on at 1.75 s", server -> {
			// A cow pressed against the boss can stop just short of the spot; then there is no bonus to check.
			var cow = player(server).level().getEntity((java.util.UUID) wand[1]);
			var point = (net.minecraft.world.phys.Vec3) wand[0];
			double flat = cow == null ? 99 : Math.hypot(cow.getX() - point.x, cow.getZ() - point.z);
			return com.huntlike.gear.ModShields.activeFields() > 0 || flat > com.huntlike.gear.ModShields.WAND_GATHERED;
		});
		waitServerTicks(context, singleplayer, (long) wand[4], 45);
		assertServer(singleplayer, "...and over by 2.25 s; the released cow has Slowness I", server -> {
			var cow = player(server).level().getEntity((java.util.UUID) wand[1]);
			return com.huntlike.gear.ModShields.activeFields() == 0 && cow instanceof net.minecraft.world.entity.LivingEntity living
					&& living.hasEffect(net.minecraft.world.effect.MobEffects.SLOWNESS);
		});
		singleplayer.getServer().runOnServer(server -> {
			for (int k : new int[] {1, 2, 5, 6}) {
				var entity = player(server).level().getEntity((java.util.UUID) wand[k]);
				if (entity != null) {
					entity.discard();
				}
			}
		});

		// The A-Phone is bought on the Cluster Misc page (4th trade), not crafted.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			var inv = player.getInventory();
			inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE, 8));
			inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.REDSTONE_TORCH, 2));
			inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 10));
			inv.add(new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.FROZEN_SLIME_BALL, 4));
			inv.add(new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.DECENT_QUALITY_MYSTERY_MEAT, 4));
			inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
					net.minecraft.resources.Identifier.withDefaultNamespace("lightning_rod")), 1));
			inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GLASS, 1));
			inv.add(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 5));
		});
		context.setScreen(() -> new HuntMenuScreen(1));
		context.waitTicks(5);
		context.takeScreenshot("91b-cluster-misc-a-phone");
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("buy_gear", "cluster", 103)));
		context.setScreen(() -> null);
		context.waitTicks(3);
		assertServer(singleplayer, "the A-Phone is bought in the Cluster gear shop and every cost is taken", server -> {
			var inv = player(server).getInventory();
			int phones = 0;
			int other = 0;
			for (int i = 0; i < inv.getContainerSize(); i++) {
				var stack = inv.getItem(i);
				if (stack.is(com.huntlike.registry.ModItems.A_PHONE)) {
					phones += stack.getCount();
				} else if (!stack.isEmpty()) {
					other += stack.getCount();
				}
			}
			boolean noRecipe = server.getRecipeManager().byKey(net.minecraft.resources.ResourceKey.create(
					net.minecraft.core.registries.Registries.RECIPE, HuntLike.id("a_phone"))).isEmpty();
			HuntLike.LOGGER.info("[test] A-Phone bought: phones {} other items left {} crafting recipe gone {}", phones, other, noRecipe);
			return phones == 1 && other == 0 && noRecipe;
		});

		// /huntlike questline jumps Koro's or Towernaar's questline to a chapter; one past the last = all done.
		int[] saved = singleplayer.getServer().computeOnServer(server -> {
			HuntData data = QuestManager.data(player(server));
			return new int[] {data.koroDone, data.koroStep, data.towernaarDone, data.towernaarStep};
		});
		singleplayer.getServer().runCommand("huntlike questline cluster 2 @a");
		context.waitTicks(2);
		check(singleplayer, "questline cluster 2: Towernaar at the start of Chapter II", data -> data.towernaarDone == 1 && data.towernaarStep == 0);
		singleplayer.getServer().runCommand("huntlike questline animal 5 @a");
		context.waitTicks(2);
		check(singleplayer, "questline animal 5: Koro's questline finished", data -> data.koroDone == 4 && data.koroStep == 0);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			data.koroDone = saved[0];
			data.koroStep = saved[1];
			data.towernaarDone = saved[2];
			data.towernaarStep = saved[3];
			player.setAttached(QuestManager.DATA, data);
			QuestManager.sync(player);
		});
	}

	private static com.huntlike.interlude.Pom interludePom(MinecraftServer server) {
		ServerPlayer player = player(server);
		var list = player.level().getEntitiesOfClass(com.huntlike.interlude.Pom.class, player.getBoundingBox().inflate(40), e -> e.isAlive());
		return list.isEmpty() ? null : list.get(0);
	}

	private static List<com.huntlike.interlude.Bartholomew> interludeSlimes(MinecraftServer server) {
		ServerPlayer player = player(server);
		return player.level().getEntitiesOfClass(com.huntlike.interlude.Bartholomew.class, player.getBoundingBox().inflate(40), e -> e.isAlive());
	}

	private static void interludeAction(ClientGameTestContext context, String action, int value) {
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action(action, com.huntlike.interlude.InterludeDefs.SLIME_RIDER, value)));
	}

	/** Starts a stage with its quota already full (like /huntlike boss) and waits for Pom and Bartholomew to arrive. */
	private static void startInterlude(ClientGameTestContext context, TestSingleplayerContext singleplayer, int stage) {
		singleplayer.getServer().runOnServer(server -> QuestManager.forceBoss(player(server), com.huntlike.interlude.InterludeDefs.hunt(), stage));
		for (int i = 0; i < 80; i++) {
			context.waitTicks(1);
			if (singleplayer.getServer().computeOnServer(server -> interludePom(server) != null)) {
				context.waitTicks(2);
				return;
			}
		}
		throw new AssertionError("The Slime Rider didn't spawn");
	}

	private static void hurt(ServerPlayer player, LivingEntity entity, float amount) {
		entity.setInvulnerableTime(0);
		entity.hurtServer(player.level(), player.level().damageSources().playerAttack(player), amount);
	}

	/** The Slime Rider Interlude: tokens, the menu card, both phases, splitting, drops, the Growing Slime Ball and the Remains. */
	private static void slimeRider(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runCommand("huntlike quest cancel @a");
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			player.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.RESISTANCE, 20 * 600, 4));
			for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.Mob.class, player.getBoundingBox().inflate(40))) {
				e.discard();
			}
			give(player, net.minecraft.world.item.Items.GOLD_INGOT, 11);
		});
		interludeAction(context, "select", 0);
		context.waitTicks(3);
		interludeAction(context, "interlude_tokens", 0);
		context.waitTicks(3);
		interludeAction(context, "interlude_tokens", 1);
		context.waitTicks(3);
		check(singleplayer, "1 gold ingot = 16 Interlude Tokens (Shift: 10 = 160)", data -> data.interludeTokens == 176
				&& com.huntlike.interlude.InterludeDefs.SLIME_RIDER.equals(data.selectedHunt));
		context.setScreen(() -> new HuntMenuScreen(0));
		context.waitTicks(10);
		context.takeScreenshot("95-interlude-card");
		context.waitTicks(110);
		context.takeScreenshot("95b-interlude-card-bartholomew");
		context.waitTicks(40);
		context.takeScreenshot("95c-interlude-card-pom");
		context.setScreen(() -> null);

		// Starting: 2 tokens, then a kill quota like a Hunt Quest; animals and slime clusters both count.
		interludeAction(context, "interlude_start", 1);
		context.waitTicks(3);
		check(singleplayer, "an Interlude quest starts with a quota of 2", data -> data.quest != null
				&& com.huntlike.interlude.InterludeDefs.SLIME_RIDER.equals(data.quest.hunt) && data.quest.quota == 2
				&& data.interludeTokens == 174);
		assertServer(singleplayer, "the Spirit Lure brings both animals and slimes for an Interlude", server -> {
			var types = com.huntlike.hunt.SpiritLure.huntableHere(player(server), com.huntlike.interlude.InterludeDefs.hunt());
			HuntLike.LOGGER.info("[test] Interlude huntable: {}", types);
			return types.contains(EntityTypes.SLIME) && types.size() >= 2;
		});
		assertServer(singleplayer, "Rogue Hunters come 25% more often during an Interlude (1 in 24, not 30)", server -> {
			var data = QuestManager.data(player(server));
			return com.huntlike.rogue.RogueHunters.lureOdds(data) == 24;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var cow = spawnTest(player, EntityTypes.COW, 2.0, 0.0, 1.0F);
			cow.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 100.0F);
		});
		context.waitTicks(3);
		check(singleplayer, "an animal counts toward the Interlude quota", data -> data.quest != null && data.quest.kills == 1);
		context.setScreen(() -> new HuntMenuScreen(0));
		context.waitTicks(5);
		context.takeScreenshot("95d-interlude-active-quest");
		context.setScreen(() -> null);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var slime = spawnTest(player, EntityTypes.SLIME, -2.0, 0.0, 1.0F);
			slime.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 100.0F);
		});
		context.waitTicks(3);
		check(singleplayer, "a slime cluster counts too, and the quota of 2 is met", data -> data.quest != null && data.quest.kills >= 2
				&& data.quest.phase != HuntData.PHASE_KILLING);
		context.setScreen(() -> null);
		singleplayer.getServer().runCommand("huntlike quest cancel @a");
		context.waitTicks(3);

		// Stage I: Phase 1 shares Pom's health; hits on Bartholomew go there.
		startInterlude(context, singleplayer, 1);
		context.waitTicks(20);
		context.takeScreenshot("96-slime-rider");
		assertServer(singleplayer, "Phase 1: Bartholomew rides Pom; hitting Bartholomew hurts Pom; both Hunts' weapons work", server -> {
			ServerPlayer player = player(server);
			var pom = interludePom(server);
			var bart = interludeSlimes(server).get(0);
			float before = pom.getHealth();
			hurt(player, bart, 5.0F);
			HuntLike.LOGGER.info("[test] Slime Rider Phase 1: riding {} pom {} -> {} bart {}", bart.getVehicle() == pom, before, pom.getHealth(),
					bart.getHealth());
			return bart.getVehicle() == pom && pom.getHealth() < before && bart.getHealth() == bart.getMaxHealth()
					&& com.huntlike.gear.GearEffects.isAnimal(pom) && com.huntlike.gear.GearEffects.isCluster(pom)
					&& com.huntlike.gear.GearEffects.isAnimal(bart) && com.huntlike.gear.GearEffects.isCluster(bart);
		});
		singleplayer.getServer().runOnServer(server -> hurt(player(server), interludePom(server), 1000.0F));
		context.waitTicks(3);
		boolean pomStayed = singleplayer.getServer().computeOnServer(server -> interludePom(server) != null);
		assertServer(singleplayer, "Phase 2: one of them stays with half the health, and only its Hunt's weapons work", server -> {
			var pom = interludePom(server);
			var slimes = interludeSlimes(server);
			HuntLike.LOGGER.info("[test] Slime Rider Phase 2: Pom {} Bartholomew {}", pom == null ? "-" : pom.getHealth() + "/" + pom.getMaxHealth(),
					slimes.isEmpty() ? "-" : slimes.get(0).getHealth() + "/" + slimes.get(0).getMaxHealth());
			if (pom != null) {
				return slimes.isEmpty() && pom.getMaxHealth() == 25.0F && com.huntlike.gear.GearEffects.isAnimal(pom)
						&& !com.huntlike.gear.GearEffects.isCluster(pom);
			}
			return slimes.size() == 1 && slimes.get(0).getMaxHealth() == 25.0F && slimes.get(0).getVehicle() == null
					&& com.huntlike.gear.GearEffects.isCluster(slimes.get(0)) && !com.huntlike.gear.GearEffects.isAnimal(slimes.get(0));
		});
		context.waitTicks(20);
		context.takeScreenshot(pomStayed ? "97-phase2-pom" : "97-phase2-bartholomew");
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var pom = interludePom(server);
			if (pom != null) {
				hurt(player, pom, 1000.0F);
			} else {
				hurt(player, interludeSlimes(server).get(0), 1000.0F);
			}
		});
		context.waitTicks(3);
		check(singleplayer, "Stage I won: counted, the quest is over", data -> data.progress("slime_rider").bossKills[0] == 1
				&& data.interludeTokens == 174 && data.quest == null);
		assertServer(singleplayer, "the fight is over", server -> !com.huntlike.interlude.SlimeRiderFight.active(player(server))
				&& interludePom(server) == null && interludeSlimes(server).isEmpty());
		// Won once: the card now shows the pair (abilities below it) and Stage II's name; Drops groups Bartholomew, then Pom.
		context.setScreen(() -> new HuntMenuScreen(0));
		context.waitTicks(10);
		context.takeScreenshot("98z-interlude-card-won");
		hover(context, "Drops");
		context.takeScreenshot("98z2-interlude-drops");
		context.setScreen(() -> null);

		// Stage III with Bartholomew staying: "Are we for real?" splits him in two (10% of 140 = 14 health each).
		singleplayer.getServer().runOnServer(server -> {
			HuntData data = QuestManager.data(player(server));
			data.progress("slime_rider").bossKills[1] = 1;
		});
		boolean split = false;
		for (int attempt = 0; attempt < 12 && !split; attempt++) {
			// A clean start each time: the last fight over, nothing of it left (a quest still open is cancelled).
			for (int i = 0; i < 60; i++) {
				boolean clean = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).quest == null
						&& interludePom(server) == null && interludeSlimes(server).isEmpty());
				if (clean) {
					break;
				}
				if (i == 40) {
					singleplayer.getServer().runCommand("huntlike quest cancel @a");
				}
				context.waitTick();
			}
			startInterlude(context, singleplayer, 3);
			singleplayer.getServer().runOnServer(server -> hurt(player(server), interludePom(server), 1000.0F));
			context.waitTicks(3);
			boolean pomStays = singleplayer.getServer().computeOnServer(server -> interludePom(server) != null);
			HuntLike.LOGGER.info("[test] Stage III try {}: {} stays", attempt + 1, pomStays ? "Pom" : "Bartholomew");
			if (pomStays) {
				singleplayer.getServer().runOnServer(server -> hurt(player(server), interludePom(server), 1000.0F));
				context.waitTicks(3);
				continue;
			}
			singleplayer.getServer().runOnServer(server -> hurt(player(server), interludeSlimes(server).get(0), 1000.0F));
			context.waitTicks(3);
			assertServer(singleplayer, "Are we for real?: two halves with 14 health each", server -> {
				var slimes = interludeSlimes(server);
				HuntLike.LOGGER.info("[test] Bartholomew split: {}", slimes.stream().map(e -> e.getHealth() + "/size " + e.getSize()).toList());
				return slimes.size() == 2 && slimes.stream().allMatch(e -> e.getMaxHealth() == 14.0F && e.getSize() == 1);
			});
			context.waitTicks(10);
			context.takeScreenshot("98-bartholomew-split");
			singleplayer.getServer().runOnServer(server -> interludeSlimes(server).forEach(e -> hurt(player(server), e, 1000.0F)));
			context.waitTicks(3);
			split = true;
		}
		if (!split) {
			throw new AssertionError("Bartholomew never stayed in 12 tries");
		}
		check(singleplayer, "Stage III won", data -> data.progress("slime_rider").bossKills[2] >= 1);

		// The Growing Slime Ball on an anvil: every enchantment below its maximum goes up one, 5 levels each; books and any
		// enchanted item.
		assertServer(singleplayer, "Growing Slime Ball raises every enchantment of a book or enchanted item by one level", server -> {
			ServerPlayer player = player(server);
			var registry = player.level().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
			var sharpness = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
			var unbreaking = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING);
			var protection = registry.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.PROTECTION);
			var stored = net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS;
			var applied = net.minecraft.core.component.DataComponents.ENCHANTMENTS;
			var menu = new net.minecraft.world.inventory.AnvilMenu(0, player.getInventory());
			menu.getSlot(1).set(new net.minecraft.world.item.ItemStack(com.huntlike.interlude.ModInterlude.GROWING_SLIME_BALL));
			java.util.function.BiFunction<net.minecraft.world.item.Item, Object[], net.minecraft.world.item.ItemStack> make = (item, pairs) -> {
				var stack = new net.minecraft.world.item.ItemStack(item);
				var enchants = new net.minecraft.world.item.enchantment.ItemEnchantments.Mutable(
						net.minecraft.world.item.enchantment.ItemEnchantments.EMPTY);
				for (int i = 0; i < pairs.length; i += 2) {
					enchants.set((net.minecraft.core.Holder<net.minecraft.world.item.enchantment.Enchantment>) pairs[i], (Integer) pairs[i + 1]);
				}
				stack.set(item == net.minecraft.world.item.Items.ENCHANTED_BOOK ? stored : applied, enchants.toImmutable());
				return stack;
			};
			// Book: Sharpness II + Unbreaking I -> III + II, 10 levels.
			menu.getSlot(0).set(make.apply(net.minecraft.world.item.Items.ENCHANTED_BOOK, new Object[] {sharpness, 2, unbreaking, 1}));
			menu.createResult();
			var book = menu.getSlot(2).getItem().get(stored);
			int bookCost = menu.getCost();
			boolean bookOk = book != null && book.getLevel(sharpness) == 3 && book.getLevel(unbreaking) == 2 && bookCost == 10;
			// Chestplate: Protection III + Unbreaking III (max) -> Protection IV only, 5 levels.
			menu.getSlot(0).set(make.apply(net.minecraft.world.item.Items.DIAMOND_CHESTPLATE, new Object[] {protection, 3, unbreaking, 3}));
			menu.createResult();
			var chest = menu.getSlot(2).getItem().get(applied);
			int chestCost = menu.getCost();
			boolean chestOk = chest != null && chest.getLevel(protection) == 4 && chest.getLevel(unbreaking) == 3 && chestCost == 5;
			// Sword: Sharpness II -> III, 5 levels.
			menu.getSlot(0).set(make.apply(net.minecraft.world.item.Items.DIAMOND_SWORD, new Object[] {sharpness, 2}));
			menu.createResult();
			var sword = menu.getSlot(2).getItem().get(applied);
			boolean swordOk = sword != null && sword.getLevel(sharpness) == 3 && menu.getCost() == 5;
			// Refused: a maxed book, and an unenchanted sword.
			menu.getSlot(0).set(make.apply(net.minecraft.world.item.Items.ENCHANTED_BOOK, new Object[] {sharpness, 5}));
			menu.createResult();
			boolean maxedRefused = menu.getSlot(2).getItem().isEmpty();
			menu.getSlot(0).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND_SWORD));
			menu.createResult();
			boolean plainRefused = menu.getSlot(2).getItem().isEmpty();
			HuntLike.LOGGER.info("[test] Growing Slime Ball: book {} cost {}; chestplate {} cost {}; sword {}; maxed refused {}; plain refused {}",
					book, bookCost, chest, chestCost, sword, maxedRefused, plainRefused);
			return bookOk && chestOk && swordOk && maxedRefused && plainRefused;
		});

		// Pom's Skeletal Remains: a saddled skeleton horse with the player on it; again, and it's gone.
		assertServer(singleplayer, "Pom's Skeletal Remains call and dismiss the skeleton horse", server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.interlude.ModInterlude.POMS_SKELETAL_REMAINS));
			player.getMainHandItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			var horse = player.getVehicle();
			boolean riding = horse instanceof com.huntlike.interlude.RemainsHorse remains && remains.getMaxHealth() == 40.0F
					&& !remains.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.SADDLE).isEmpty();
			player.stopRiding();
			player.getMainHandItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			boolean gone = horse == null || !horse.isAlive();
			HuntLike.LOGGER.info("[test] Remains: riding a saddled 40-health horse {}, gone after the second use {}", riding, gone);
			return riding && gone;
		});

		// The real thing, from the client: right-click with the Remains, ride forward, get off, get back on with an empty hand.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().setItem(0, new net.minecraft.world.item.ItemStack(com.huntlike.interlude.ModInterlude.POMS_SKELETAL_REMAINS));
			player.getInventory().setItem(1, net.minecraft.world.item.ItemStack.EMPTY);
		});
		context.getInput().pressKey(InputConstants.KEY_1);
		context.waitTicks(3);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(10);
		String mounted = context.computeOnClient(client -> {
			var vehicle = client.player.getVehicle();
			return vehicle == null ? "no vehicle" : vehicle.getType() + " controlled by me "
					+ (vehicle instanceof net.minecraft.world.entity.LivingEntity living && living.getControllingPassenger() == client.player)
					+ " saddled " + (vehicle instanceof net.minecraft.world.entity.animal.equine.AbstractHorse h && h.isSaddled())
					+ " tamed " + (vehicle instanceof net.minecraft.world.entity.animal.equine.AbstractHorse h2 && h2.isTamed());
		});
		HuntLike.LOGGER.info("[test] Remains (client): after right-click: {}", mounted);
		context.takeScreenshot("99a-remains-mounted");
		double[] start = singleplayer.getServer().computeOnServer(server -> {
			var v = player(server).getVehicle();
			return v == null ? new double[] {0, 0} : new double[] {v.getX(), v.getZ()};
		});
		context.getInput().holdKeyFor(options -> options.keyUp, 40);
		double ridden = singleplayer.getServer().computeOnServer(server -> {
			var v = player(server).getVehicle();
			return v == null ? -1.0 : Math.hypot(v.getX() - start[0], v.getZ() - start[1]);
		});
		HuntLike.LOGGER.info("[test] Remains (client): rode {} blocks in 2 s", ridden);
		context.getInput().pressKey(options -> options.keyShift);
		context.waitTicks(10);
		context.getInput().pressKey(InputConstants.KEY_2);
		net.minecraft.core.BlockPos horseAt = singleplayer.getServer().computeOnServer(server -> {
			var horses = player(server).level().getEntitiesOfClass(com.huntlike.interlude.RemainsHorse.class,
					player(server).getBoundingBox().inflate(8));
			return horses.isEmpty() ? null : horses.get(0).blockPosition().above();
		});
		if (horseAt != null) {
			context.getInput().lookAt(horseAt);
		}
		context.waitTicks(3);
		context.getInput().pressKey(options -> options.keyUse);
		context.waitTicks(10);
		boolean remounted = context.computeOnClient(client -> client.player.getVehicle() instanceof com.huntlike.interlude.RemainsHorse);
		HuntLike.LOGGER.info("[test] Remains (client): horse at {} remounted with an empty hand {}", horseAt, remounted);
		check(singleplayer, "Pom's Skeletal Remains: summoned, ridden forward from the client, and remounted",
				data -> mounted.contains("controlled by me true") && ridden > 4.0 && remounted);
		context.getInput().pressKey(options -> options.keyShift);
		context.waitTicks(5);
		singleplayer.getServer().runOnServer(server -> {
			for (var horse : player(server).level().getEntitiesOfClass(com.huntlike.interlude.RemainsHorse.class,
					player(server).getBoundingBox().inflate(16))) {
				horse.discard();
			}
		});

		// Shields: block with the vanilla shield and each HuntLike shield in the offhand; compare how they block and look.
		net.minecraft.world.item.Item[] shields = {net.minecraft.world.item.Items.SHIELD, com.huntlike.gear.ModShields.OBSIDIAN_SHIELD,
				com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD, com.huntlike.gear.ModShields.UNBREAKABLE_SHIELD_STAR};
		java.util.List<String> shieldStates = new java.util.ArrayList<>();
		for (int i = 0; i < shields.length; i++) {
			net.minecraft.world.item.Item shield = shields[i];
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, new net.minecraft.world.item.ItemStack(shield));
				player.getInventory().setItem(1, net.minecraft.world.item.ItemStack.EMPTY);
			});
			context.getInput().pressKey(InputConstants.KEY_2);
			context.waitTicks(3);
			context.getInput().holdKey(options -> options.keyUse);
			context.waitTicks(5);
			java.util.Set<Integer> copies = new java.util.HashSet<>();
			java.util.List<String> diffs = new java.util.ArrayList<>();
			final net.minecraft.world.item.ItemStack[] last = new net.minecraft.world.item.ItemStack[1];
			for (int t = 0; t < 10; t++) {
				context.runOnClient(client -> {
					var now = client.player.getOffhandItem();
					copies.add(System.identityHashCode(now));
					if (last[0] != null && last[0] != now && diffs.size() < 3) {
						diffs.add(last[0].getComponentsPatch() + " -> " + now.getComponentsPatch());
					}
					last[0] = now;
				});
				context.waitTick();
			}
			HuntLike.LOGGER.info("[test] shield {}: {} different offhand stacks in 10 ticks of blocking; changes {}", i, copies.size(), diffs);
			String state = context.computeOnClient(client -> {
				var player = client.player;
				var stack = player.getOffhandItem();
				return stack.getItem() + ": using " + player.isUsingItem() + " hand " + player.getUsedItemHand() + " blocking "
						+ player.isBlocking() + " anim " + stack.getUseAnimation() + " duration " + stack.getUseDuration(player)
						+ " blocks " + stack.get(net.minecraft.core.component.DataComponents.BLOCKS_ATTACKS);
			});
			boolean serverBlocking = singleplayer.getServer().computeOnServer(server -> player(server).isBlocking());
			HuntLike.LOGGER.info("[test] shield {} | server blocking {}", state, serverBlocking);
			shieldStates.add(state + " server " + serverBlocking);
			context.takeScreenshot("99s-shield-" + i);
			if (i < 2) {
				context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
				context.waitTicks(3);
				context.takeScreenshot("99t-shield-" + i + "-third-person");
				context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
			}
			context.getInput().releaseKey(options -> options.keyUse);
			context.waitTicks(5);
			if (i < 2) {
				context.takeScreenshot("99u-shield-" + i + "-held");
			}
		}
		singleplayer.getServer().runOnServer(server -> player(server).setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND,
				net.minecraft.world.item.ItemStack.EMPTY));
		check(singleplayer, "every HuntLike shield is a ShieldItem (held up straight in first person when blocking)", data -> java.util.Arrays
				.stream(shields).allMatch(item -> item instanceof net.minecraft.world.item.ShieldItem));
		check(singleplayer, "every HuntLike shield blocks like the vanilla one", data -> shieldStates.stream()
				.allMatch(st -> st.contains("blocking true") && st.contains("anim BLOCK") && st.endsWith("server true")));

		// Horseshoes at an anvil: one star each (5 levels), up to 5; the horse gets faster per star (0.24 -> 0.36).
		assertServer(singleplayer, "a Horseshoe adds a star to Pom's Skeletal Remains; 5 stars is the most; stars set the speed", server -> {
			ServerPlayer player = player(server);
			var menu = new net.minecraft.world.inventory.AnvilMenu(0, player.getInventory());
			menu.getSlot(0).set(new net.minecraft.world.item.ItemStack(com.huntlike.interlude.ModInterlude.POMS_SKELETAL_REMAINS));
			menu.getSlot(1).set(new net.minecraft.world.item.ItemStack(com.huntlike.interlude.ModInterlude.HORSESHOE));
			menu.createResult();
			var one = menu.getSlot(2).getItem();
			int cost = menu.getCost();
			var five = new net.minecraft.world.item.ItemStack(com.huntlike.interlude.ModInterlude.POMS_SKELETAL_REMAINS);
			five.set(com.huntlike.interlude.ModInterlude.HORSESHOES, 5);
			menu.getSlot(0).set(five);
			menu.createResult();
			boolean refused = menu.getSlot(2).getItem().isEmpty();
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, five);
			five.use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			double fast = player.getVehicle() instanceof com.huntlike.interlude.RemainsHorse h
					? h.getAttributeBaseValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) : -1;
			player.stopRiding();
			five.use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			String name = one.getHoverName().getString();
			HuntLike.LOGGER.info("[test] Horseshoe: '{}' cost {}; 5 stars refused {}; '{}' speed {}; 0-star speed {}", name, cost, refused,
					five.getHoverName().getString(), fast, com.huntlike.interlude.RemainsItem.speed(0));
			return com.huntlike.interlude.RemainsItem.stars(one) == 1 && name.endsWith("\u2605") && cost == 5 && refused
					&& Math.abs(fast - 0.36) < 1e-6 && Math.abs(com.huntlike.interlude.RemainsItem.speed(0) - 0.24) < 1e-6;
		});

		// The new drops are Bartholomew's: Horseshoe 1/20 then 1/7, Bouncy Slime Ball 1/20 then 1/8, from Stage II.
		assertServer(singleplayer, "Horseshoe and Bouncy Slime Ball drop when Bartholomew stays, from Stage II", server -> {
			var two = com.huntlike.interlude.InterludeDefs.bartholomewDrops(2);
			var three = com.huntlike.interlude.InterludeDefs.bartholomewDrops(3);
			java.util.function.BiPredicate<java.util.List<com.huntlike.hunt.HuntDefs.Drop>, String> has = (list, id) -> list.stream()
					.anyMatch(d -> d.id().equals(id));
			java.util.function.BiFunction<java.util.List<com.huntlike.hunt.HuntDefs.Drop>, String, Double> odds = (list, id) -> list.stream()
					.filter(d -> d.id().equals(id)).mapToDouble(d -> d.chanceDen() / (double) d.chanceNum()).findFirst().orElse(-1);
			return !has.test(com.huntlike.interlude.InterludeDefs.bartholomewDrops(1), "horseshoe")
					&& odds.apply(two, "horseshoe") == 20 && odds.apply(three, "horseshoe") == 7
					&& odds.apply(two, "bouncy_slime_ball") == 20 && odds.apply(three, "bouncy_slime_ball") == 8
					&& !has.test(com.huntlike.interlude.InterludeDefs.pomDrops(3), "horseshoe");
		});

		// Slimy Rebounders: a pig wearing them falls 12 blocks, bounces back up and takes no damage; a pig without is hurt.
		final java.util.UUID[] pigs = new java.util.UUID[2];
		final double[] track = {Double.MAX_VALUE, -Double.MAX_VALUE, 0};
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (int i = 0; i < 2; i++) {
				var pig = net.minecraft.world.entity.EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
				pig.snapTo(player.getX() + 6 + i * 4, player.getY() + 12, player.getZ() + 6, 0, 0);
				if (i == 0) {
					pig.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
							new net.minecraft.world.item.ItemStack(com.huntlike.interlude.ModInterlude.SLIMY_REBOUNDERS));
				}
				player.level().addFreshEntity(pig);
				pigs[i] = pig.getUUID();
			}
			track[2] = player.getY();
		});
		for (int t = 0; t < 80; t++) {
			context.waitTick();
			singleplayer.getServer().runOnServer(server -> {
				var pig = player(server).level().getEntity(pigs[0]);
				if (pig == null) {
					return;
				}
				if (pig.getY() < track[0]) {
					track[0] = pig.getY();
					track[1] = pig.getY();
				} else {
					track[1] = Math.max(track[1], pig.getY());
				}
			});
		}
		assertServer(singleplayer, "Slimy Rebounders bounce like a slime block and stop fall damage", server -> {
			var level = player(server).level();
			var booted = (net.minecraft.world.entity.LivingEntity) level.getEntity(pigs[0]);
			var bare = (net.minecraft.world.entity.LivingEntity) level.getEntity(pigs[1]);
			double bounce = track[1] - track[0];
			HuntLike.LOGGER.info("[test] Rebounders: booted pig health {} bounce {} blocks; bare pig health {}",
					booted == null ? -1 : booted.getHealth(), bounce, bare == null ? -1 : bare.getHealth());
			boolean ok = booted != null && booted.getHealth() == booted.getMaxHealth() && bounce > 2.0
					&& (bare == null || bare.getHealth() < bare.getMaxHealth());
			if (booted != null) {
				booted.discard();
			}
			if (bare != null) {
				bare.discard();
			}
			return ok;
		});

		// ...but a drop of just 1 block (a step down) doesn't bounce, so walking around keeps its speed.
		final java.util.UUID[] stepPig = new java.util.UUID[1];
		final double[] step = {Double.MAX_VALUE, -Double.MAX_VALUE};
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var pig = net.minecraft.world.entity.EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
			pig.snapTo(player.getX() + 6, player.getY() + 1.0, player.getZ() - 6, 0, 0);
			pig.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET,
					new net.minecraft.world.item.ItemStack(com.huntlike.interlude.ModInterlude.SLIMY_REBOUNDERS));
			player.level().addFreshEntity(pig);
			stepPig[0] = pig.getUUID();
		});
		for (int t = 0; t < 30; t++) {
			context.waitTick();
			singleplayer.getServer().runOnServer(server -> {
				var pig = player(server).level().getEntity(stepPig[0]);
				if (pig == null) {
					return;
				}
				if (pig.getY() < step[0]) {
					step[0] = pig.getY();
					step[1] = pig.getY();
				} else {
					step[1] = Math.max(step[1], pig.getY());
				}
			});
		}
		assertServer(singleplayer, "Slimy Rebounders: a 1-block drop lands without bouncing", server -> {
			double rebound = step[1] - step[0];
			HuntLike.LOGGER.info("[test] Rebounders 1-block drop: rebound {} blocks", rebound);
			var pig = player(server).level().getEntity(stepPig[0]);
			if (pig != null) {
				pig.discard();
			}
			return rebound < 0.1;
		});
	}

	private static boolean done(ServerPlayer player, String path) {
		var advancement = player.level().getServer().getAdvancements().get(HuntLike.id(path));
		return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
	}

	private static net.minecraft.world.entity.LivingEntity spawnTest(ServerPlayer player, net.minecraft.world.entity.EntityType<?> type,
			double dx, double dz, float health) {
		var entity = (net.minecraft.world.entity.Mob) type.create(player.level(), EntitySpawnReason.COMMAND);
		if (entity instanceof net.minecraft.world.entity.monster.cubemob.AbstractCubeMob cube) {
			cube.setSize(1, true);
		}
		entity.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(health);
		entity.setHealth(health);
		entity.snapTo(player.getX() + dx, player.getY(), player.getZ() + dz, 0, 0);
		entity.setNoAi(true);
		player.level().addFreshEntity(entity);
		return entity;
	}

	/** Cluster Hunt Weapons, the Spiritual Compass, Beef Jerky and the new advancements. */
	private static void scythes(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		assertServer(singleplayer, "Cluster boss advancements; The Hunt Begins is shared", server -> {
			ServerPlayer player = player(server);
			return done(player, "cluster/tier1") && done(player, "cluster/tier5") && done(player, "animal/conquest");
		});
		int[] ids = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			wearNothing(player);
			for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
					player.getBoundingBox().inflate(40), e -> e != player)) {
				e.discard();
			}
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
					new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModScythes.SLIMY_FALX));
			var slime = spawnTest(player, EntityTypes.SLIME, 2.0, 0.0, 100.0F);
			var near = spawnTest(player, EntityTypes.PIG, 2.0, 2.5, 10.0F);
			var far = spawnTest(player, EntityTypes.PIG, 2.0, 5.5, 10.0F);
			var magma = spawnTest(player, EntityTypes.MAGMA_CUBE, -2.0, 0.0, 100.0F);
			return new int[] {slime.getId(), near.getId(), far.getId(), magma.getId()};
		});
		context.waitTicks(25);
		singleplayer.getServer().runOnServer(server -> player(server).attack(player(server).level().getEntity(ids[0])));
		context.waitTicks(25);
		singleplayer.getServer().runOnServer(server -> player(server).attack(player(server).level().getEntity(ids[3])));
		context.waitTicks(2);
		float[] searing = singleplayer.getServer().computeOnServer(server -> {
			var level = player(server).level();
			float[] health = new float[4];
			for (int i = 0; i < 4; i++) {
				health[i] = ((net.minecraft.world.entity.LivingEntity) level.getEntity(ids[i])).getHealth();
			}
			HuntLike.LOGGER.info("[test] Slimy Falx (Searing) hit: slime {}, pig in sweep {}, far pig {}, magma cube {}",
					health[0], health[1], health[2], health[3]);
			return health;
		});
		assertServer(singleplayer, "Slimy Falx, Searing: slime takes (4 + 4) x 1.05; the sweep hits a pig 3 blocks away for 4, not one 6 away",
				server -> Math.abs(searing[0] - (100.0F - 8.4F)) < 0.05F && Math.abs(searing[1] - 6.0F) < 0.05F && searing[2] == 10.0F);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setShiftKeyDown(true);
			player.getMainHandItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			player.getMainHandItem().use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			player.setShiftKeyDown(false);
		});
		assertServer(singleplayer, "Sublimation: sneak + right-click switches to Freezing once (2 s cooldown)", server -> {
			var stack = player(server).getMainHandItem();
			var model = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA);
			var lore = stack.get(net.minecraft.core.component.DataComponents.LORE).lines();
			HuntLike.LOGGER.info("[test] Slimy Falx lore {}", lore.stream().map(net.minecraft.network.chat.Component::getString).toList());
			return lore.get(0).getString().equals("Sweeping Weapon")
					&& lore.get(lore.size() - 1).getStyle().getColor().getValue() == com.huntlike.gear.ModScythes.FREEZING_COLOR
					&& com.huntlike.gear.ModScythes.isFrost(stack) && model != null && Boolean.TRUE.equals(model.getBoolean(0))
					&& player(server).getCooldowns().isOnCooldown(stack);
		});
		context.runOnClient(client -> {
			client.player.setYRot(90.0F);
			client.player.setXRot(10.0F);
		});
		context.waitTicks(25);
		context.takeScreenshot("50-scythe-frost");
		singleplayer.getServer().runOnServer(server -> player(server).attack(player(server).level().getEntity(ids[3])));
		context.waitTicks(2);
		assertServer(singleplayer, "Freezing hits the magma cube harder than Searing did", server -> {
			float now = ((net.minecraft.world.entity.LivingEntity) player(server).level().getEntity(ids[3])).getHealth();
			float searingHit = 100.0F - searing[3];
			float freezingHit = searing[3] - now;
			HuntLike.LOGGER.info("[test] magma cube: Searing hit {}, Freezing hit {}", searingHit, freezingHit);
			return freezingHit > searingHit + 0.1F;
		});

		// Slice and Dice: a cluster finished with a scythe counts double.
		singleplayer.getServer().runCommand("huntlike quest start @a cluster 1");
		context.waitTicks(3);
		int slimeId = singleplayer.getServer().computeOnServer(server -> spawnTest(player(server), EntityTypes.SLIME, 2.0, -1.0, 1.0F).getId());
		context.waitTicks(25);
		singleplayer.getServer().runOnServer(server -> player(server).attack(player(server).level().getEntity(slimeId)));
		context.waitTicks(3);
		check(singleplayer, "Slice and Dice: a slime cluster killed with a scythe = 2 quota points", data -> data.quest != null
				&& data.quest.kills == 2);
		singleplayer.getServer().runCommand("huntlike quest cancel @a");

		// Spiritual Compass: the 3 nearest souls glow.
		int[] pigs = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
					player.getBoundingBox().inflate(40), e -> e != player)) {
				e.discard();
			}
			int[] result = new int[4];
			double[] distance = {3, 5, 7, 12};
			for (int i = 0; i < 4; i++) {
				result[i] = spawnTest(player, EntityTypes.PIG, -distance[i], 0.0, 10.0F).getId();
			}
			return result;
		});
		context.waitTicks(3);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var stack = new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.SPIRITUAL_COMPASS);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
			stack.use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
		});
		context.waitTicks(5);
		boolean compass = context.computeOnClient(client -> client.shouldEntityAppearGlowing(client.level.getEntity(pigs[0]))
				&& client.shouldEntityAppearGlowing(client.level.getEntity(pigs[1]))
				&& client.shouldEntityAppearGlowing(client.level.getEntity(pigs[2]))
				&& !client.shouldEntityAppearGlowing(client.level.getEntity(pigs[3])));
		HuntLike.LOGGER.info("[test] Spiritual Compass highlights the 3 nearest souls: {}", compass ? "OK" : "FAILED");
		if (!compass) {
			throw new AssertionError("Spiritual Compass");
		}
		context.takeScreenshot("51-spiritual-compass");

		// Warm and Slimy Beef Jerky and the 10,000 lured kills.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var jerky = new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.WARM_AND_SLIMY_BEEF_JERKY, 2);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, jerky);
			jerky.finishUsingItem(player.level(), player);
			var again = jerky.use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			HuntLike.LOGGER.info("[test] Beef Jerky: jerky {} second bite {} cluster odds 1/{}", QuestManager.data(player).jerky, again,
					com.huntlike.hunt.HuntDefs.odds("cluster", 1, 25, com.huntlike.hunt.HuntDefs.luck(QuestManager.data(player), "cluster")));
			if (!QuestManager.data(player).jerky || again != net.minecraft.world.InteractionResult.FAIL
					|| com.huntlike.hunt.HuntDefs.odds("cluster", 1, 25, com.huntlike.hunt.HuntDefs.luck(QuestManager.data(player), "cluster")) != 24) {
				throw new AssertionError("Beef Jerky: once, Cluster odds 1/25 -> 1/24");
			}
			HuntData data = QuestManager.data(player);
			data.luredKills = com.huntlike.hunt.QuestManager.NOT_SELF_DEFENSE_KILLS - 1;
			QuestManager.countLuredKill(player, data);
		});
		context.waitTicks(3);
		assertServer(singleplayer, "The texture was nice at least and Ok ok, it might not be just self-defense", server ->
				done(player(server), "cluster/jerky") && done(player(server), "animal/not_self_defense"));
	}

	private static List<com.huntlike.pet.PetCube> petPieces(MinecraftServer server) {
		ServerPlayer player = player(server);
		return com.huntlike.pet.PetManager.pieces(player.level(), player, com.huntlike.pet.PetManager.latest(player), player).stream()
				.filter(net.minecraft.world.entity.LivingEntity::isAlive).toList();
	}

	private static void petInteract(TestSingleplayerContext singleplayer, net.minecraft.world.item.ItemStack stack, int times) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (int i = 0; i < times; i++) {
				List<com.huntlike.pet.PetCube> pieces = petPieces(server);
				if (pieces.isEmpty()) {
					return;
				}
				player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack.copy());
				com.huntlike.pet.PetManager.interact(pieces.get(0), player, net.minecraft.world.InteractionHand.MAIN_HAND);
			}
		});
	}

	/** The dropped Slimy Core, on the ground or already picked up. */
	private static net.minecraft.world.item.ItemStack petCore(ServerPlayer player) {
		var dropped = player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
				player.getBoundingBox().inflate(40), e -> e.getItem().is(com.huntlike.registry.ModItems.SLIME_PET_CORE));
		if (!dropped.isEmpty()) {
			return dropped.get(0).getItem();
		}
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			if (inventory.getItem(i).is(com.huntlike.registry.ModItems.SLIME_PET_CORE)) {
				return inventory.getItem(i);
			}
		}
		return net.minecraft.world.item.ItemStack.EMPTY;
	}

	private static void killPet(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			for (var piece : petPieces(server)) {
				piece.hurtServer(player(server).level(), player(server).level().damageSources().genericKill(), Float.MAX_VALUE);
			}
		});
		context.waitTicks(30);
	}

	private static String petInfo(MinecraftServer server) {
		var state = com.huntlike.pet.PetManager.latest(player(server));
		StringBuilder pieces = new StringBuilder();
		for (var piece : petPieces(server)) {
			pieces.append(piece.isMagma() ? "magma" : "slime").append(piece.getSize()).append('/').append(piece.getMaxHealth()).append(' ');
		}
		return "status " + state.status + " big " + state.big + " ionic " + state.ionic + " magma " + state.magma + " heat " + state.heat
				+ " chest " + state.chest + " page " + state.page + " pieces [" + pieces.toString().trim() + "]";
	}

	/** Soul Monolith ★: a block more reach and Regeneration I; the Beef Jerky is in the creative tab. */
	private static void upgradedMonolith(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.removeEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
			player.removeEffect(net.minecraft.world.effect.MobEffects.RESISTANCE);
			player.level().setBlockAndUpdate(player.blockPosition().offset(9, 0, 0),
					com.huntlike.registry.ModBlocks.UPGRADED_SOUL_MONOLITH.defaultBlockState());
			player.hurtServer(player.level(), player.level().damageSources().generic(), 6.0F);
		});
		context.waitTicks(80);
		assertServer(singleplayer, "Soul Monolith ★ reaches 9 blocks and gives Resistance I and Regeneration I", server -> {
			ServerPlayer player = player(server);
			HuntLike.LOGGER.info("[test] near Soul Monolith ★: resistance {} regeneration {} health {}",
					player.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE),
					player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION), player.getHealth());
			return player.hasEffect(net.minecraft.world.effect.MobEffects.RESISTANCE)
					&& player.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION);
		});
		singleplayer.getServer().runOnServer(server -> player(server).level().setBlockAndUpdate(
				player(server).blockPosition().offset(9, 0, 0), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()));
		boolean inTab = context.computeOnClient(client -> {
			net.minecraft.world.item.CreativeModeTabs.tryRebuildTabContents(client.player.connection.enabledFeatures(), true,
					client.level.registryAccess());
			var tabs = net.minecraft.core.registries.BuiltInRegistries.CREATIVE_MODE_TAB;
			for (var tab : tabs) {
				if (tab.getDisplayItems().stream().anyMatch(stack -> stack.is(com.huntlike.registry.ModItems.WARM_AND_SLIMY_BEEF_JERKY))) {
					return true;
				}
			}
			return false;
		});
		HuntLike.LOGGER.info("[test] Warm and Slimy Beef Jerky in a creative tab: {}", inTab ? "OK" : "FAILED");
		if (!inTab) {
			throw new AssertionError("Beef Jerky missing from the creative tabs");
		}
		assertServer(singleplayer, "Cluster drop descriptions; Slime-Washed Holmium is the Tier V 1/100 drop", server -> {
			var thermal = new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.THERMAL_SLIMEBALL);
			var holmium = new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.SLIME_WASHED_HOLMIUM);
			var lore = holmium.get(net.minecraft.core.component.DataComponents.LORE);
			HuntLike.LOGGER.info("[test] {} / {}: {}", thermal.getHoverName().getString(), holmium.getHoverName().getString(),
					lore == null ? "no lore" : lore.lines().stream().map(net.minecraft.network.chat.Component::getString).toList());
			boolean dropped = com.huntlike.hunt.HuntDefs.clusterDrops(com.huntlike.hunt.HuntDefs.CLUSTER.tier(5)).stream()
					.anyMatch(drop -> drop.item() == com.huntlike.registry.ModItems.SLIME_WASHED_HOLMIUM && drop.chanceDen() == 100);
			return thermal.getHoverName().getString().equals("Thermal Slime Ball") && lore != null
					&& lore.lines().get(0).getString().startsWith("It felt more magnetic") && dropped;
		});
	}

	/** The Cluster Hunt's slime pet: taming, upgrades, riding, the menu, splitting, regrowing, Magma Cube, the core and reviving. */
	private static void slimePet(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		// Like a world that has spawned a lot (the Spirit Lure, item drops, experience orbs): entity ids past 65,535, which a
		// menu can't send as one number. The pet's preview in its menu must still find it.
		singleplayer.getServer().runOnServer(server -> {
			var level = player(server).level();
			while (level.getNextEntityId() < 70000) {
				// Each call hands out the next id.
			}
		});
		int[] wild = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
			for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
					player.getBoundingBox().inflate(40), e -> e != player)) {
				e.discard();
			}
			var slime = spawnTest(player, EntityTypes.SLIME, 3.0, 0.0, 1.0F);
			var lured = spawnTest(player, EntityTypes.SLIME, -3.0, 0.0, 1.0F);
			lured.addTag(com.huntlike.hunt.SpiritLure.LURED_TAG);
			return new int[] {slime.getId(), lured.getId()};
		});
		context.waitTicks(3);
		assertServer(singleplayer, "Slime pet: a lured slime refuses Thermal Slimeballs", server -> {
			ServerPlayer player = player(server);
			var lured = (net.minecraft.world.entity.monster.cubemob.Slime) player.level().getEntity(wild[1]);
			return !com.huntlike.pet.PetManager.feedWild(player, lured,
					new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.THERMAL_SLIMEBALL, 64))
					&& com.huntlike.pet.PetManager.latest(player) == null;
		});
		int feeds = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			var slime = (net.minecraft.world.entity.monster.cubemob.Slime) player.level().getEntity(wild[0]);
			var stack = new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.THERMAL_SLIMEBALL, 64);
			for (int i = 1; i <= 20; i++) {
				if (com.huntlike.pet.PetManager.feedWild(player, slime, stack)) {
					return i;
				}
			}
			return -1;
		});
		context.waitTicks(5);
		assertServer(singleplayer, "Slime pet: tamed with Thermal Slimeballs (at most 20) into a 20-health medium slime named \"... Cluster\"",
				server -> {
					HuntLike.LOGGER.info("[test] pet tamed after {} slimeballs: {} name {}", feeds, petInfo(server),
							com.huntlike.pet.PetManager.latest(player(server)).name);
					var pieces = petPieces(server);
					return feeds > 0 && pieces.size() == 1 && pieces.get(0).getSize() == 2 && pieces.get(0).getMaxHealth() == 20.0F
							&& com.huntlike.pet.PetManager.latest(player(server)).name.endsWith(" Cluster")
							&& done(player(server), "cluster/pet_tame");
				});
		context.runOnClient(client -> {
			client.player.setYRot(-90.0F);
			client.player.setXRot(20.0F);
		});
		context.waitTicks(10);
		context.takeScreenshot("52-slime-pet");
		petInteract(singleplayer, new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.IONIC_LIQUID), 1);
		assertServer(singleplayer, "Ionic Liquid: 40 health", server -> {
			var pieces = petPieces(server);
			return pieces.size() == 1 && pieces.get(0).getMaxHealth() == 40.0F && com.huntlike.pet.PetManager.latest(player(server)).ionic;
		});
		petInteract(singleplayer, new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.THERMAL_SLIMEBALL), 25);
		context.waitTicks(5);
		assertServer(singleplayer, "Thermal Slimeballs after the Ionic Liquid grow it big (at most 25): 60 health", server -> {
			HuntLike.LOGGER.info("[test] grown: {} after {} feeds", petInfo(server), com.huntlike.pet.PetManager.latest(player(server)).growFeeds);
			var pieces = petPieces(server);
			return pieces.size() == 1 && pieces.get(0).getSize() == 4 && pieces.get(0).getMaxHealth() == 60.0F;
		});

		// Riding and the menu.
		petInteract(singleplayer, net.minecraft.world.item.ItemStack.EMPTY, 1);
		context.waitTicks(5);
		assertServer(singleplayer, "Riding: right-click a big pet with an empty hand", server ->
				player(server).getVehicle() instanceof com.huntlike.pet.PetCube);
		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
		context.waitTicks(10);
		context.takeScreenshot("53-riding-pet");
		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		singleplayer.getServer().runOnServer(server -> com.huntlike.pet.PetManager.openMenu(petPieces(server).get(0), player(server)));
		context.waitForScreen(com.huntlike.client.pet.PetScreen.class);
		context.waitTicks(5);
		context.takeScreenshot("54-pet-menu-no-chest");
		int serverPetId = singleplayer.getServer().computeOnServer(server -> petPieces(server).get(0).getId());
		int screenPetId = context.computeOnClient(client -> client.gui.screen() instanceof com.huntlike.client.pet.PetScreen screen
				? screen.getMenu().petId() : -1);
		HuntLike.LOGGER.info("[test] pet menu preview: pet id {} (server) / {} (menu)", serverPetId, screenPetId);
		if (serverPetId <= 65535 || screenPetId != serverPetId) {
			throw new AssertionError("The pet menu doesn't find a pet with a large entity id");
		}
		singleplayer.getServer().runOnServer(server -> {
			var menu = (com.huntlike.pet.PetMenu) player(server).containerMenu;
			menu.getSlot(0).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.CHEST));
			menu.getSlot(1).set(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
			menu.broadcastChanges();
		});
		context.waitTicks(5);
		context.takeScreenshot("55-pet-menu-page-1");
		singleplayer.getServer().runOnServer(server -> player(server).containerMenu.clickMenuButton(player(server), 0));
		context.waitTicks(5);
		context.takeScreenshot("56-pet-menu-page-2");
		assertServer(singleplayer, "Pet menu: chest slot, storage and the page button", server -> {
			var state = com.huntlike.pet.PetManager.latest(player(server));
			return state.chest && state.storage.get(0).is(net.minecraft.world.item.Items.DIAMOND) && state.page == 1;
		});
		context.setScreen(() -> null);
		singleplayer.getServer().runOnServer(server -> player(server).stopRiding());
		context.waitTicks(10);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var pet = petPieces(server).get(0);
			pet.setNoAi(true);
			pet.teleportTo(player.getX() + 3.5, player.getY(), player.getZ());
			pet.setYRot(90.0F);
			pet.yBodyRot = 90.0F;
		});
		context.runOnClient(client -> {
			client.player.setYRot(-90.0F);
			client.player.setXRot(12.0F);
		});
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		context.waitTicks(15);
		context.takeScreenshot("56b-pet-with-chest");
		int chestPetId = singleplayer.getServer().computeOnServer(server -> petPieces(server).get(0).getId());
		String clientChest = context.computeOnClient(client -> {
			var entity = client.level.getEntity(chestPetId);
			if (!(entity instanceof com.huntlike.pet.PetCube cube)) {
				return "no entity " + entity;
			}
			var renderer = client.getEntityRenderDispatcher().getRenderer(cube);
			var state = client.getEntityRenderDispatcher().extractEntity(cube, 0.0F);
			return "client hasChest " + cube.hasChest() + " size " + cube.getSize() + " renderer " + renderer.getClass().getSimpleName()
					+ " state " + state.getClass().getSimpleName()
					+ (state instanceof com.huntlike.client.pet.PetRenderState pState ? " chest " + pState.chest : "");
		});
		HuntLike.LOGGER.info("[test] pet chest on the client: {}", clientChest);
		assertServer(singleplayer, "The big pet shows its chest", server -> petPieces(server).get(0).hasChest());
		singleplayer.getServer().runOnServer(server -> petPieces(server).get(0).setNoAi(false));

		// Killed: big -> two mediums (30) -> smalls (15); regrows after 45 quiet seconds.
		killPet(context, singleplayer);
		assertServer(singleplayer, "Killed big pet splits into two 30-health mediums", server -> {
			HuntLike.LOGGER.info("[test] after the first kill: {}", petInfo(server));
			var pieces = petPieces(server);
			return pieces.size() == 2 && pieces.stream().allMatch(p -> p.getSize() == 2 && p.getMaxHealth() == 30.0F);
		});
		killPet(context, singleplayer);
		assertServer(singleplayer, "Killed mediums split into 15-health smalls", server -> {
			HuntLike.LOGGER.info("[test] after the second kill: {}", petInfo(server));
			var pieces = petPieces(server);
			return pieces.size() == 4 && pieces.stream().allMatch(p -> p.getSize() == 1 && p.getMaxHealth() == 15.0F)
					&& com.huntlike.pet.PetManager.latest(player(server)).status == com.huntlike.pet.PetState.SPLIT;
		});
		singleplayer.getServer().runOnServer(server -> com.huntlike.pet.PetManager.latest(player(server)).lastDamage =
				player(server).level().getGameTime() - com.huntlike.pet.PetManager.REGROW_BIG - 1);
		context.waitTicks(30);
		assertServer(singleplayer, "45 quiet seconds: even with only small pieces left, it grows back into the big pet (chest kept)", server -> {
			HuntLike.LOGGER.info("[test] after regrowing: {}", petInfo(server));
			var pieces = petPieces(server);
			var state = com.huntlike.pet.PetManager.latest(player(server));
			HuntLike.LOGGER.info("[test] regrown pet health {} status {} storage {}", pieces.isEmpty() ? -1 : pieces.get(0).getHealth(),
					state.status, state.storage.isEmpty() ? "-" : state.storage.get(0));
			// Full health on regrowing; a bump or two right after (it lands where the pieces were) is fine.
			return pieces.size() == 1 && pieces.get(0).getSize() == 4 && pieces.get(0).getMaxHealth() == 60.0F && pieces.get(0).getHealth() > 50.0F
					&& state.status == com.huntlike.pet.PetState.WHOLE && state.storage.get(0).is(net.minecraft.world.item.Items.DIAMOND);
		});

		singleplayer.getServer().runOnServer(server -> {
			var pet = petPieces(server).get(0);
			// From full health (it may have bumped itself landing).
			pet.setHealth(pet.getMaxHealth());
			pet.hurtServer(player(server).level(), player(server).level().damageSources().generic(), 10.0F);
		});
		petInteract(singleplayer, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL), 1);
		assertServer(singleplayer, "A slime ball heals the pet by 4", server -> {
			float health = petPieces(server).get(0).getHealth();
			HuntLike.LOGGER.info("[test] pet health after 10 damage and a slime ball: {}", health);
			return Math.abs(health - 54.0F) < 0.01F;
		});
		// Corroded Palladium, then heat: a big Magma Cube.
		petInteract(singleplayer, new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.CORRODED_PALLADIUM), 1);
		petInteract(singleplayer, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MAGMA_CREAM), 2);
		petInteract(singleplayer, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.MAGMA_BLOCK), 1);
		petInteract(singleplayer, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET), 4);
		context.waitTicks(10);
		assertServer(singleplayer, "Heat 100: the pet becomes an 80-health, fire-immune Magma Cube", server -> {
			HuntLike.LOGGER.info("[test] magma: {}", petInfo(server));
			var pieces = petPieces(server);
			return pieces.size() == 1 && pieces.get(0) instanceof com.huntlike.pet.PetMagma && pieces.get(0).getMaxHealth() == 80.0F
					&& pieces.get(0).fireImmune() && done(player(server), "cluster/pet_magma");
		});
		context.takeScreenshot("57-magma-pet");

		// Every piece killed: the Slimy Core drops; reviving brings the Magma Cube back with its chest.
		for (int i = 0; i < 4; i++) {
			killPet(context, singleplayer);
		}
		assertServer(singleplayer, "Whole pet killed: a Slimy Core that can't burn drops", server -> {
			ServerPlayer player = player(server);
			var core = petCore(player);
			HuntLike.LOGGER.info("[test] core: {} {}", petInfo(server), core.isEmpty() ? "none" : core.getHoverName().getString());
			return com.huntlike.pet.PetManager.latest(player).status == com.huntlike.pet.PetState.CORE
					&& core.getHoverName().getString().endsWith(" Slimy Core")
					&& core.has(net.minecraft.core.component.DataComponents.DAMAGE_RESISTANT);
		});
		assertServer(singleplayer, "Crafting the revive recipe carries the core's pet over", server -> {
			ServerPlayer player = player(server);
			var core = petCore(player);
			player.inventoryMenu.getSlot(1).set(core.copy());
			var revived = new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.REVIVED_SLIME_PET_CORE);
			revived.getItem().onCraftedBy(revived, player);
			player.inventoryMenu.getSlot(1).set(net.minecraft.world.item.ItemStack.EMPTY);
			HuntLike.LOGGER.info("[test] revived core: {} for pet {}", revived.getHoverName().getString(),
					com.huntlike.pet.PetManager.corePet(revived));
			return com.huntlike.pet.PetManager.latest(player).id.equals(com.huntlike.pet.PetManager.corePet(revived))
					&& revived.getHoverName().getString().startsWith("Revived ");
		});
		singleplayer.getServer().runOnServer(server -> com.huntlike.pet.PetManager.revive(player(server),
				player(server).position().add(3, 0, 0), com.huntlike.pet.PetManager.latest(player(server)).id));
		context.waitTicks(5);
		assertServer(singleplayer, "Revived: the big Magma Cube comes back with its upgrades and chest", server -> {
			HuntLike.LOGGER.info("[test] revived: {}", petInfo(server));
			var pieces = petPieces(server);
			var state = com.huntlike.pet.PetManager.latest(player(server));
			return pieces.size() == 1 && pieces.get(0) instanceof com.huntlike.pet.PetMagma && pieces.get(0).getMaxHealth() == 80.0F
					&& state.ionic && state.chest && state.storage.get(0).is(net.minecraft.world.item.Items.DIAMOND);
		});

		// Several pets: only one follows at a time; the others sit.
		boolean tamedSecond = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			var slime = (net.minecraft.world.entity.monster.cubemob.Slime) spawnTest(player, EntityTypes.SLIME, 0.0, 3.0, 1.0F);
			var stack = new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.THERMAL_SLIMEBALL, 64);
			for (int i = 0; i < 20; i++) {
				if (com.huntlike.pet.PetManager.feedWild(player, slime, stack)) {
					return true;
				}
			}
			return false;
		});
		context.waitTicks(3);
		assertServer(singleplayer, "A second pet can be tamed; it follows and the first one sits", server -> {
			var pets = com.huntlike.pet.PetManager.roster(player(server)).pets;
			HuntLike.LOGGER.info("[test] pets: {}", pets.stream().map(p -> p.name + (p.sitting ? " (sitting)" : " (following)")).toList());
			return tamedSecond && pets.size() == 2 && pets.get(0).sitting && !pets.get(1).sitting;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var first = com.huntlike.pet.PetManager.roster(player).pets.get(0);
			var cube = com.huntlike.pet.PetManager.pieces(player.level(), player, first, player).get(0);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.ItemStack.EMPTY);
			player.setShiftKeyDown(true);
			com.huntlike.pet.PetManager.interact(cube, player, net.minecraft.world.InteractionHand.MAIN_HAND);
			player.setShiftKeyDown(false);
		});
		assertServer(singleplayer, "Telling the first pet to follow makes the second one sit", server -> {
			var pets = com.huntlike.pet.PetManager.roster(player(server)).pets;
			return !pets.get(0).sitting && pets.get(1).sitting;
		});

		// A following pet boxed in 12 blocks away teleports to its owner after 5 seconds.
		BlockPos boxed = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			var first = com.huntlike.pet.PetManager.roster(player).pets.get(0);
			var cube = com.huntlike.pet.PetManager.pieces(player.level(), player, first, player).get(0);
			BlockPos center = player.blockPosition().offset(0, 0, 12);
			for (int x = -2; x <= 2; x++) {
				for (int z = -2; z <= 2; z++) {
					for (int y = -1; y <= 4; y++) {
						boolean wall = Math.abs(x) == 2 || Math.abs(z) == 2 || y == -1 || y == 4;
						player.level().setBlockAndUpdate(center.offset(x, y, z), wall
								? net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState()
								: net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
					}
				}
			}
			cube.teleportTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
			return center;
		});
		context.waitTicks(140);
		assertServer(singleplayer, "A stuck pet (boxed in, 12 blocks away) teleports to its owner", server -> {
			ServerPlayer player = player(server);
			var first = com.huntlike.pet.PetManager.roster(player).pets.get(0);
			var cube = com.huntlike.pet.PetManager.pieces(player.level(), player, first, player).get(0);
			HuntLike.LOGGER.info("[test] stuck pet distance after 7 s: {}", cube.distanceTo(player));
			return cube.distanceTo(player) < 6.0;
		});
	}

	private static void wearNothing(ServerPlayer player) {
		for (var slot : new net.minecraft.world.entity.EquipmentSlot[] {net.minecraft.world.entity.EquipmentSlot.HEAD,
				net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
				net.minecraft.world.entity.EquipmentSlot.FEET, net.minecraft.world.entity.EquipmentSlot.OFFHAND}) {
			player.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
		}
	}

	private static void wear(TestSingleplayerContext singleplayer, com.huntlike.gear.ModGear.ArmorSet set) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(set.helmet()));
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(set.chestplate()));
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new net.minecraft.world.item.ItemStack(set.leggings()));
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new net.minecraft.world.item.ItemStack(set.boots()));
		});
	}

	/** Daily Quest charges: commands don't use them; a new world day refills all five. */
	private static void dailyRefill(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		int before = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).dailyCharges);
		HuntLike.LOGGER.info("[test] daily charges before refill: {}", before);
		singleplayer.getServer().runCommand("huntlike quest start @a animal 1");
		context.waitTicks(2);
		check(singleplayer, "command quests use no charge", data -> data.quest != null && !data.quest.daily
				&& data.dailyCharges == before);
		singleplayer.getServer().runCommand("huntlike quest cancel @a");
		singleplayer.getServer().runOnServer(server -> QuestManager.openMenu(player(server)));
		context.waitForScreen(HuntMenuScreen.class);
		context.waitTicks(25);
		context.takeScreenshot("31-daily-charges-used");
		hover(context, "Daily Quest Charges");
		context.takeScreenshot("31b-daily-charges-tooltip");
		singleplayer.getServer().runCommand("time add 24000");
		context.waitTicks(25);
		check(singleplayer, "a new day refills the charges", data -> data.dailyCharges == HuntData.DAILY_CHARGES);
		context.takeScreenshot("32-daily-charges-refilled");
		addProgress(singleplayer, 0, 64);
		context.runOnClient(client -> net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
				new com.huntlike.network.HuntPayloads.Action("start", "animal", 1)));
		context.waitTicks(25);
		check(singleplayer, "menu quest uses a charge", data -> data.quest != null && data.quest.daily
				&& data.dailyCharges == HuntData.DAILY_CHARGES - 1);
		context.takeScreenshot("33-daily-active-quest");
		hover(context, "Drops");
		context.takeScreenshot("34-daily-drops");
		singleplayer.getServer().runCommand("huntlike quest cancel @a");
		context.setScreen(() -> null);
		context.waitTicks(5);
		context.takeScreenshot("35-daily-refill-message");
	}

	/** Spiritual Catalyst highlights the nearest living entity (client side); the Bone Pulverizer in hand. */
	private static void catalystAndPulverizer(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		int cowId = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			for (var e : player.level().getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class,
					player.getBoundingBox().inflate(40), e -> e != player)) {
				e.discard();
			}
			var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
			cow.snapTo(player.getX() - 4, player.getY(), player.getZ() + 1, 0, 0);
			cow.setNoAi(true);
			player.level().addFreshEntity(cow);
			var far = EntityTypes.PIG.create(player.level(), EntitySpawnReason.COMMAND);
			far.snapTo(player.getX() - 9, player.getY(), player.getZ() - 2, 0, 0);
			far.setNoAi(true);
			player.level().addFreshEntity(far);
			return cow.getId();
		});
		context.waitTicks(3);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var stack = new net.minecraft.world.item.ItemStack(com.huntlike.registry.ModItems.SPIRITUAL_CATALYST);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
			stack.use(player.level(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
		});
		context.waitTicks(5);
		boolean glowing = context.computeOnClient(client -> {
			var cow = client.level.getEntity(cowId);
			return cow != null && client.shouldEntityAppearGlowing(cow);
		});
		HuntLike.LOGGER.info("[test] Spiritual Catalyst highlights the nearest entity: {}", glowing ? "OK" : "FAILED");
		if (!glowing) {
			throw new AssertionError("Spiritual Catalyst highlight");
		}
		context.takeScreenshot("28-catalyst-highlight");
		context.waitTicks(70);
		boolean faded = context.computeOnClient(client -> !client.shouldEntityAppearGlowing(client.level.getEntity(cowId)));
		HuntLike.LOGGER.info("[test] highlight fades after 3 seconds: {}", faded ? "OK" : "FAILED");
		if (!faded) {
			throw new AssertionError("Spiritual Catalyst highlight did not fade");
		}
		singleplayer.getServer().runOnServer(server -> player(server).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModGear.BONE_BREAKER)));
		context.waitTicks(10);
		context.takeScreenshot("29a-breaker-first-person");
		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		context.takeScreenshot("29b-breaker-third-person");
		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		singleplayer.getServer().runOnServer(server -> player(server).setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
				new net.minecraft.world.item.ItemStack(com.huntlike.gear.ModGear.BONE_PULVERIZER)));
		context.waitTicks(10);
		context.takeScreenshot("29-pulverizer-first-person");
		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		context.waitTicks(10);
		context.takeScreenshot("30-pulverizer-third-person");
		context.runOnClient(client -> client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
	}

	/** The 100th Tier V boss gives the Animal Hunting Trophy, which keeps its lore when placed and broken. */
	private static void trophy(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		context.setScreen(() -> null);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.getInventory().clearContent();
			HuntData data = QuestManager.data(player);
			data.progress("animal").bossKills[4] = 99;
			player.setAttached(QuestManager.DATA, data);
		});
		String name = singleplayer.getServer().computeOnServer(server -> player(server).getScoreboardName());
		singleplayer.getServer().runCommand("huntlike boss animal 5 " + name);
		context.waitTicks(60);
		assertServer(singleplayer, "100th Tier V boss gives the trophy", server -> {
			ServerPlayer player = player(server);
			Mob boss = BossController.findBoss(player, QuestManager.data(player).quest);
			boss.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 100000.0F);
			for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
				var stack = player.getInventory().getItem(i);
				if (stack.is(com.huntlike.registry.ModBlocks.ANIMAL_TROPHY.asItem())) {
					String lore = stack.get(net.minecraft.core.component.DataComponents.LORE).lines().stream()
							.map(net.minecraft.network.chat.Component::getString).reduce("", (a, b) -> a + b + " ");
					HuntLike.LOGGER.info("[test] trophy lore: {}", lore);
					trophyStack = stack.copy();
					return lore.contains(player.getScoreboardName()) && lore.contains("mastery over animal hunting");
				}
			}
			return false;
		});
		assertServer(singleplayer, "placed trophy keeps its lore when broken", server -> {
			ServerPlayer player = player(server);
			player.connection.teleport(player.getX(), player.getY(), player.getZ(), 90, 10);
			var stack = trophyStack;
			var level = player.level();
			BlockPos display = player.blockPosition().offset(-2, 1, 0);
			BlockPos broken = player.blockPosition().offset(3, 1, 3);
			for (BlockPos pos : new BlockPos[] {display, broken}) {
				var facing = pos == display ? net.minecraft.core.Direction.EAST : net.minecraft.core.Direction.NORTH;
				level.setBlockAndUpdate(pos.relative(facing.getOpposite()), net.minecraft.world.level.block.Blocks.STONE_BRICKS.defaultBlockState());
				level.setBlockAndUpdate(pos, com.huntlike.registry.ModBlocks.ANIMAL_TROPHY.defaultBlockState()
						.setValue(com.huntlike.block.TrophyBlock.FACING, facing));
				level.getBlockEntity(pos).applyComponentsFromItemStack(stack);
			}
			HuntLike.LOGGER.info("[test] trophy stack {} lore {}, block entity lore {}", stack,
					stack.get(net.minecraft.core.component.DataComponents.LORE),
					((com.huntlike.block.TrophyBlockEntity) level.getBlockEntity(broken)).lore());
			level.destroyBlock(broken, true, player);
			for (var e : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(broken).inflate(2), e -> true)) {
				HuntLike.LOGGER.info("[test] dropped {} lore {}", e.getItem(), e.getItem().get(net.minecraft.core.component.DataComponents.LORE));
			}
			return level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					new net.minecraft.world.phys.AABB(broken).inflate(2), e -> e.getItem().is(stack.getItem())
							&& e.getItem().get(net.minecraft.core.component.DataComponents.LORE) != null
							&& e.getItem().get(net.minecraft.core.component.DataComponents.LORE).equals(
									stack.get(net.minecraft.core.component.DataComponents.LORE))).size() == 1;
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (int slot = 0; slot < 9; slot++) {
				player.getInventory().setItem(slot, trophyStack.copy());
			}
		});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			for (var e : player.level().getEntities(player, player.getBoundingBox().inflate(6))) {
				HuntLike.LOGGER.info("[test] near trophy screenshot: {} {} alive={} age={} tags={} uuid={}", e.getType(), e.position(),
						e.isAlive(), e.tickCount, e.entityTags(), e.getUUID());
			}
		});
		context.runOnClient(client -> {
			client.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			client.player.setYRot(90.0F);
			client.player.setXRot(10.0F);
		});
		context.waitTicks(60);
		context.takeScreenshot("27-trophy-message");
		context.runOnClient(client -> client.gui.hud.getChat().clearMessages(false));
		context.waitTicks(3);
		context.takeScreenshot("27b-trophy-placed");
	}

	private static ServerPlayer helper(MinecraftServer server) {
		return server.getPlayerList().getPlayer(java.util.UUID.fromString("00000000-0000-4000-8000-00000000beef"));
	}

	@SuppressWarnings("unchecked")
	private static void addToPlayerList(MinecraftServer server, ServerPlayer player) {
		try {
			var list = server.getPlayerList();
			var players = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			var byUuid = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
			players.setAccessible(true);
			byUuid.setAccessible(true);
			((java.util.List<ServerPlayer>) players.get(list)).add(player);
			((java.util.Map<java.util.UUID, ServerPlayer>) byUuid.get(list)).put(player.getUUID(), player);
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Could not add a fake player", e);
		}
	}

	@SuppressWarnings("unchecked")
	private static void removeFromPlayerList(MinecraftServer server, ServerPlayer player) {
		try {
			var list = server.getPlayerList();
			var players = net.minecraft.server.players.PlayerList.class.getDeclaredField("players");
			var byUuid = net.minecraft.server.players.PlayerList.class.getDeclaredField("playersByUUID");
			players.setAccessible(true);
			byUuid.setAccessible(true);
			((java.util.List<ServerPlayer>) players.get(list)).remove(player);
			((java.util.Map<java.util.UUID, ServerPlayer>) byUuid.get(list)).remove(player.getUUID());
		} catch (ReflectiveOperationException e) {
			throw new AssertionError("Could not remove a fake player", e);
		}
	}

	private static net.minecraft.world.entity.animal.wolf.Wolf testWolf(ServerPlayer player) {
		return player.level().getEntitiesOfClass(net.minecraft.world.entity.animal.wolf.Wolf.class,
				player.getBoundingBox().inflate(32), w -> w.entityTags().contains("huntlike_test_wolf")).getFirst();
	}

	private static boolean hasAdvancement(ServerPlayer player, String path) {
		var holder = player.level().getServer().getAdvancements().get(HuntLike.id(path));
		return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
	}

	/** Path of the Wildfire: the Blazing Heart, its goals, the Blazing mobs, the buffs and the Paths screen. */
	private static void wildfire(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		assertServer(singleplayer, "Blazing Heart chance: 2%, +1% per Looting level", server -> {
			HuntData data = QuestManager.data(player(server));
			return data.wildfireGoals == 0 && com.huntlike.path.WildfirePath.heartChance(data, 0) == 2
					&& com.huntlike.path.WildfirePath.heartChance(data, 3) == 5;
		});
		context.setScreen(() -> new com.huntlike.client.screen.PathsScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("97a-paths-undiscovered");
		context.setScreen(() -> null);
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.getInventory().clearContent();
			player.getInventory().add(new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLAZING_HEART, 2));
		});
		context.waitTicks(30);
		check(singleplayer, "holding a Blazing Heart discovers the Path (goal 1)", data -> data.wildfireGoals == 1
				&& com.huntlike.path.WildfirePath.heartChance(data, 0) == 3);
		assertServer(singleplayer, "eating it: full hunger, on fire, fire beam on; goal 2", server -> {
			ServerPlayer player = player(server);
			player.getFoodData().setFoodLevel(2);
			var stack = player.getInventory().getItem(player.getInventory().findSlotMatchingItem(
					new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLAZING_HEART)));
			int duration = stack.getUseDuration(player);
			stack.finishUsingItem(player.level(), player);
			HuntData data = QuestManager.data(player);
			HuntLike.LOGGER.info("[test] Blazing Heart eaten: duration {} food {} fire {} beam {} goals {}", duration,
					player.getFoodData().getFoodLevel(), player.isOnFire(), com.huntlike.path.WildfirePath.beaming(player), data.wildfireGoals);
			return duration == 96 && player.getFoodData().getFoodLevel() == 20 && player.isOnFire()
					&& com.huntlike.path.WildfirePath.beaming(player) && data.wildfireGoals == 2;
		});
		context.waitTicks(70);
		assertServer(singleplayer, "the 100th blaze slain reaches goal 3", server -> {
			ServerPlayer player = player(server);
			player.clearFire();
			player.setHealth(player.getMaxHealth());
			HuntData data = QuestManager.data(player);
			data.wildfireBlazes = 49;
			player.setAttached(QuestManager.DATA, data);
			var blaze = EntityTypes.BLAZE.create(player.level(), EntitySpawnReason.COMMAND);
			blaze.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0.0F, 0.0F);
			player.level().addFreshEntity(blaze);
			blaze.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1000.0F);
			data = QuestManager.data(player);
			return data.wildfireGoals == 3 && com.huntlike.path.WildfirePath.heartChance(data, 0) == 5;
		});
		final java.util.UUID[] mob = new java.util.UUID[1];
		assertServer(singleplayer, "a Blazing Heart turns a piglin into a Blazing Piglin (60 health, 10 damage, 20% bigger) after its feeder",
				server -> {
					ServerPlayer player = player(server);
					var piglin = EntityTypes.PIGLIN.create(player.level(), EntitySpawnReason.COMMAND);
					piglin.snapTo(player.getX(), player.getY(), player.getZ() + 4, 180.0F, 0.0F);
					player.level().addFreshEntity(piglin);
					var stack = new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLAZING_HEART);
					com.huntlike.path.WildfirePath.feed(player, piglin, stack);
					var found = player.level().getEntitiesOfClass(com.huntlike.path.BlazingPiglin.class, player.getBoundingBox().inflate(10));
					if (found.size() != 1) {
						return false;
					}
					var blazing = found.get(0);
					mob[0] = blazing.getUUID();
					HuntLike.LOGGER.info("[test] Blazing Piglin: removed {} stack {} health {} damage {} scale {} target {}", piglin.isRemoved(),
							stack.getCount(), blazing.getMaxHealth(),
							blazing.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE), blazing.getScale(),
							blazing.getTarget());
					return piglin.isRemoved() && stack.isEmpty() && blazing.getMaxHealth() == 60.0F
							&& blazing.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 10.0
							&& Math.abs(blazing.getScale() - 1.2F) < 0.01F && blazing.getTarget() == player;
				});
		context.getInput().lookAt(new net.minecraft.core.BlockPos(0, -59, 6));
		context.waitTicks(20);
		context.takeScreenshot("97b-blazing-piglin");
		assertServer(singleplayer, "slaying it drops a Blazing Heart ★ and reaches goal 4", server -> {
			ServerPlayer player = player(server);
			var blazing = (net.minecraft.world.entity.LivingEntity) player.level().getEntity(mob[0]);
			blazing.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1000.0F);
			boolean star = !player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					player.getBoundingBox().inflate(12), e -> e.getItem().is(com.huntlike.path.ModPaths.BLAZING_HEART_STAR)).isEmpty();
			return star && QuestManager.data(player).wildfireGoals == 4;
		});
		assertServer(singleplayer, "a Blazing Heart ★ makes a Blazing Wither Skeleton (80 health, 12 damage, 30% bigger)", server -> {
			ServerPlayer player = player(server);
			player.setHealth(player.getMaxHealth());
			var skeleton = EntityTypes.WITHER_SKELETON.create(player.level(), EntitySpawnReason.COMMAND);
			skeleton.snapTo(player.getX(), player.getY(), player.getZ() + 5, 180.0F, 0.0F);
			player.level().addFreshEntity(skeleton);
			var stars = new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLAZING_HEART_STAR, 1);
			boolean halfway = true;
			com.huntlike.path.WildfirePath.feed(player, skeleton, stars);
			var found = player.level().getEntitiesOfClass(com.huntlike.path.BlazingWitherSkeleton.class, player.getBoundingBox().inflate(10));
			if (found.size() != 1) {
				return false;
			}
			var blazing = found.get(0);
			mob[0] = blazing.getUUID();
			return halfway && skeleton.isRemoved() && stars.isEmpty() && blazing.getMaxHealth() == 80.0F
					&& blazing.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 12.0
					&& Math.abs(blazing.getScale() - 1.3F) < 0.01F;
		});
		context.waitTicks(10);
		context.takeScreenshot("97c-blazing-wither-skeleton");
		assertServer(singleplayer, "it opens with a wither skull; slaying it drops a Wither Skeleton Skull and a Blazing Heart ★★ (goal 5)",
				server -> {
					ServerPlayer player = player(server);
					var blazing = (com.huntlike.path.BlazingWitherSkeleton) player.level().getEntity(mob[0]);
					boolean skull = blazing.skullsShot >= 1;
					blazing.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1000.0F);
					var items = player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(12));
					boolean head = items.stream().anyMatch(e -> e.getItem().is(net.minecraft.world.item.Items.WITHER_SKELETON_SKULL));
					boolean star2 = items.stream().anyMatch(e -> e.getItem().is(com.huntlike.path.ModPaths.BLAZING_HEART_STAR2));
					HuntLike.LOGGER.info("[test] Blazing Wither Skeleton: skull shot {} head {} star2 {} goals {}", skull, head, star2,
							QuestManager.data(player).wildfireGoals);
					player.setHealth(player.getMaxHealth());
					player.removeEffect(net.minecraft.world.effect.MobEffects.WITHER);
					return skull && head && star2 && QuestManager.data(player).wildfireGoals == 5;
				});
		java.util.function.BiConsumer<Integer, Integer> send = (buff, value) -> context.runOnClient(client ->
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
						new com.huntlike.network.HuntPayloads.Action("wildfire_buff", Integer.toString(buff), value)));
		send.accept(2, 1);
		context.waitTicks(5);
		check(singleplayer, "Fiery Blows can't be taken before Hot Blows", data -> data.wildfireBuffs == 0);
		for (int i = 0; i < 5; i++) {
			send.accept(i, 1);
			context.waitTicks(3);
		}
		context.waitTicks(5);
		check(singleplayer, "5 Wildfire points buy 5 skills (Netherspawn, Hot Blows, Fiery Blows, Raging Metabolism, Hot food!)", data -> data.wildfireBuffs == 31
				&& com.huntlike.path.WildfirePath.free(data) == 0);
		context.setScreen(() -> new com.huntlike.client.screen.PathsScreen(null));
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(5);
		galleryShot(context, "7a-path-of-the-wildfire");
		context.setScreen(() -> null);
		context.waitTicks(2);
		send.accept(4, -1);
		context.waitTicks(8);
		check(singleplayer, "a buff can be given back for its point", data -> data.wildfireBuffs == 15
				&& com.huntlike.path.WildfirePath.free(data) == 1);
		send.accept(0, -1);
		context.waitTicks(5);
		check(singleplayer, "Netherspawn can't be given back while the skills after it are taken", data -> data.wildfireBuffs == 15);
		assertServer(singleplayer, "Netherspawn: 25% less fire damage; Raging Metabolism: hearts eaten in half the time", server -> {
			ServerPlayer player = player(server);
			float fire = com.huntlike.path.WildfirePath.adjust(player, player.level().damageSources().inFire(), 8.0F);
			int duration = new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLAZING_HEART).getUseDuration(player);
			return Math.abs(fire - 6.0F) < 1.0E-4F && duration == 48;
		});
		// The tree grows as points are spent: nothing taken shows only Netherspawn; taking it shows its three children.
		for (int taken : new int[] {0, 1}) {
			singleplayer.getServer().runOnServer(server -> {
				var data = QuestManager.data(player(server));
				data.wildfireBuffs = taken;
				player(server).setAttached(QuestManager.DATA, data);
				QuestManager.sync(player(server));
			});
			context.waitTicks(5);
			context.setScreen(() -> new com.huntlike.client.screen.PathsScreen(null));
			context.getInput().setCursorPos(2, 2);
			context.waitTicks(5);
			context.takeScreenshot("97w-wildfire-tree-taken-" + taken);
			context.setScreen(() -> null);
			context.waitTicks(2);
		}
		// Golden Style, Nether Veil and Hot Tub (set directly, then the old skills come back).
		singleplayer.getServer().runOnServer(server -> {
			var data = QuestManager.data(player(server));
			data.wildfireBuffs = 0xFF;
			player(server).setAttached(QuestManager.DATA, data);
			QuestManager.sync(player(server));
		});
		context.waitTicks(5);
		context.setScreen(() -> new com.huntlike.client.screen.PathsScreen(null));
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(5);
		context.takeScreenshot("97x-wildfire-tree-all");
		context.setScreen(() -> null);
		context.waitTicks(2);
		assertServer(singleplayer, "Golden Style: piglins see gold; Nether Veil: a ghast won't target you until you hit it; Hot Tub: 5 s of fire immunity, then a 3 minute wait",
				server -> {
					ServerPlayer player = player(server);
					boolean gold = net.minecraft.world.entity.monster.piglin.PiglinAi.isWearingSafeArmor(player);
					var ghast = EntityTypes.GHAST.create(player.level(), EntitySpawnReason.COMMAND);
					ghast.snapTo(player.getX(), player.getY() + 6, player.getZ() + 8, 0.0F, 0.0F);
					player.level().addFreshEntity(ghast);
					ghast.setTarget(player);
					boolean ignored = ghast.getTarget() == null;
					com.huntlike.path.WildfirePath.adjust(ghast, player.level().damageSources().playerAttack(player), 1.0F);
					ghast.setTarget(player);
					boolean provoked = ghast.getTarget() == player;
					ghast.discard();
					player.removeEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
					float lava = com.huntlike.path.WildfirePath.adjust(player, player.level().damageSources().lava(), 4.0F);
					boolean immune = player.hasEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
					player.removeEffect(net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE);
					boolean again = com.huntlike.path.WildfirePath.hotTub(player);
					HuntLike.LOGGER.info("[test] Wildfire skills: gold {} ignored {} provoked {} lava {} immune {} again {}", gold, ignored,
							provoked, lava, immune, again);
					var data = QuestManager.data(player);
					data.wildfireBuffs = 15;
					player.setAttached(QuestManager.DATA, data);
					QuestManager.sync(player);
					return gold && ignored && provoked && lava == 0.0F && immune && !again;
				});
		assertServer(singleplayer, "after 3 minutes a Blazing Piglin turns into ashes, with no drops", server -> {
			ServerPlayer player = player(server);
			for (var item : player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(24))) {
				item.discard();
			}
			var piglin = EntityTypes.PIGLIN.create(player.level(), EntitySpawnReason.COMMAND);
			piglin.snapTo(player.getX() + 3, player.getY(), player.getZ() + 3, 0.0F, 0.0F);
			player.level().addFreshEntity(piglin);
			com.huntlike.path.WildfirePath.feed(player, piglin, new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLAZING_HEART));
			var blazing = player.level().getEntitiesOfClass(com.huntlike.path.BlazingPiglin.class, player.getBoundingBox().inflate(10)).get(0);
			com.huntlike.path.BlazingMob.setBorn(blazing, player.level().getGameTime() - com.huntlike.path.BlazingMob.LIFETIME - 1);
			mob[0] = blazing.getUUID();
			return true;
		});
		context.waitTicks(10);
		assertServer(singleplayer, "...gone, and no Blazing Heart \u2605 dropped", server -> {
			ServerPlayer player = player(server);
			boolean star = !player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
					player.getBoundingBox().inflate(12), e -> e.getItem().is(com.huntlike.path.ModPaths.BLAZING_HEART_STAR)).isEmpty();
			return player.level().getEntity(mob[0]) == null && !star;
		});
		assertServer(singleplayer, "a Blazing Heart \u2605\u2605 turns a blaze into a Demonling (100 health, 15 damage); it drops a Blazing Heart \u2605\u2605\u2605 and reaches goal 6",
				server -> {
					ServerPlayer player = player(server);
					for (var item : player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(16))) {
						item.discard();
					}
					var blaze = EntityTypes.BLAZE.create(player.level(), EntitySpawnReason.COMMAND);
					blaze.snapTo(player.getX(), player.getY() + 1, player.getZ() + 5, 180.0F, 0.0F);
					player.level().addFreshEntity(blaze);
					com.huntlike.path.WildfirePath.feed(player, blaze,
							new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLAZING_HEART_STAR2));
					var found = player.level().getEntitiesOfClass(com.huntlike.path.Demonling.class, player.getBoundingBox().inflate(10));
					if (found.size() != 1 || !blaze.isRemoved()) {
						return false;
					}
					var demon = found.get(0);
					boolean stats = demon.getMaxHealth() == 100.0F
							&& demon.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 15.0;
					demon.hurtServer(player.level(), player.level().damageSources().playerAttack(player), 1000.0F);
					boolean star3 = !player.level().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,
							player.getBoundingBox().inflate(12), e -> e.getItem().is(com.huntlike.path.ModPaths.BLAZING_HEART_STAR3)).isEmpty();
					return stats && star3 && QuestManager.data(player).wildfireGoals == 6;
				});
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var demon = EntityTypes.BLAZE.create(player.level(), EntitySpawnReason.COMMAND);
			var demonling = new com.huntlike.path.Demonling(com.huntlike.path.ModPaths.DEMONLING, player.level());
			demonling.snapTo(player.getX(), player.getY() + 0.5, player.getZ() + 3, 180.0F, 0.0F);
			demonling.setNoAi(true);
			player.level().addFreshEntity(demonling);
			mob[0] = demonling.getUUID();
		});
		context.getInput().lookAt(new net.minecraft.core.BlockPos(0, -58, 3));
		context.waitTicks(10);
		context.takeScreenshot("97e-demonling");
		var spawnerPos = new net.minecraft.core.BlockPos[1];
		assertServer(singleplayer, "a Blazing Heart \u2605\u2605\u2605 turns only a blaze spawner into a Blazing Spawner; Imps have 40 health, 12 damage", server -> {
			ServerPlayer player = player(server);
			var demon = player.level().getEntity(mob[0]);
			if (demon != null) {
				demon.discard();
			}
			var pos = player.blockPosition().offset(0, 1, 4);
			spawnerPos[0] = pos;
			var level = player.level();
			level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.SPAWNER.defaultBlockState());
			var star3 = new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLAZING_HEART_STAR3, 2);
			((net.minecraft.world.level.block.entity.SpawnerBlockEntity) level.getBlockEntity(pos)).setEntityId(EntityTypes.ZOMBIE, level.getRandom());
			boolean zombie = com.huntlike.path.WildfirePath.upgradeSpawner(player, pos, star3);
			((net.minecraft.world.level.block.entity.SpawnerBlockEntity) level.getBlockEntity(pos)).setEntityId(EntityTypes.BLAZE, level.getRandom());
			boolean blaze = com.huntlike.path.WildfirePath.upgradeSpawner(player, pos, star3);
			var imp = com.huntlike.path.ModPaths.IMP.create(level, EntitySpawnReason.COMMAND);
			HuntLike.LOGGER.info("[test] zombie spawner {} blaze spawner {} block {} hearts {} imp health {} damage {}", zombie, blaze,
					level.getBlockState(pos), star3.getCount(), imp.getMaxHealth(),
					imp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));
			return !zombie && blaze && star3.getCount() == 1 && level.getBlockState(pos).is(com.huntlike.path.ModPaths.BLAZING_SPAWNER)
					&& imp.getMaxHealth() == 40.0F
					&& imp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) == 12.0;
		});
		singleplayer.getServer().runOnServer(server -> player(server).setGameMode(net.minecraft.world.level.GameType.CREATIVE));
		// The first round (1 Imp) comes out after the delay.
		context.waitTicks(com.huntlike.path.BlazingSpawnerBlockEntity.ROUND_DELAY + 15);
		context.getInput().lookAt(spawnerPos[0].above());
		context.waitTicks(5);
		context.takeScreenshot("97f-blazing-spawner-imp");
		int[] seen = new int[1];
		for (int i = 0; i < 40; i++) {
			boolean done = singleplayer.getServer().computeOnServer(server -> {
				ServerPlayer player = player(server);
				var level = player.level();
				if (level.getBlockState(spawnerPos[0]).is(com.huntlike.path.ModPaths.CHALICE)) {
					return true;
				}
				if (level.getBlockEntity(spawnerPos[0]) instanceof com.huntlike.path.BlazingSpawnerBlockEntity spawner) {
					for (java.util.UUID id : java.util.List.copyOf(spawner.imps())) {
						if (level.getEntity(id) instanceof net.minecraft.world.entity.LivingEntity imp) {
							seen[0]++;
							imp.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
						}
					}
				}
				return false;
			});
			if (done) {
				break;
			}
			context.waitTicks(20);
		}
		assertServer(singleplayer, "3 rounds of Imps (1, 2, 3) burn the spawner out into a Chalice and reach goal 7", server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			HuntLike.LOGGER.info("[test] imps killed {} block {} goals {}", seen[0], player.level().getBlockState(spawnerPos[0]), data.wildfireGoals);
			return seen[0] == 6 && player.level().getBlockState(spawnerPos[0]).is(com.huntlike.path.ModPaths.CHALICE) && data.wildfireGoals == 7;
		});
		java.util.UUID[] shown = new java.util.UUID[1];
		singleplayer.getServer().runOnServer(server -> {
			var level = player(server).level();
			var imp = com.huntlike.path.ModPaths.IMP.create(level, EntitySpawnReason.COMMAND);
			imp.snapTo(spawnerPos[0].getX() + 1.6, spawnerPos[0].getY(), spawnerPos[0].getZ() + 0.5, 150.0F, 0.0F);
			imp.setNoAi(true);
			level.addFreshEntity(imp);
			shown[0] = imp.getUUID();
		});
		context.getInput().lookAt(spawnerPos[0]);
		// Let the smoke and ash settle.
		context.waitTicks(60);
		context.takeScreenshot("97g-chalice");
		assertServer(singleplayer, "a Chalice in the off-hand fills with blood from fleshy kills (fish count; skeletons and bees don't) and becomes a Full Chalice at 100 (goal 8)", server -> {
			ServerPlayer player = player(server);
			var level = player.level();
			level.setBlockAndUpdate(spawnerPos[0], net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
			if (level.getEntity(shown[0]) != null) {
				level.getEntity(shown[0]).discard();
			}
			var chalice = new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.CHALICE_ITEM);
			chalice.set(com.huntlike.path.ModPaths.BLOOD, 98);
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, chalice);
			var skeleton = EntityTypes.SKELETON.create(level, EntitySpawnReason.COMMAND);
			skeleton.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0.0F, 0.0F);
			level.addFreshEntity(skeleton);
			skeleton.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			var bee = EntityTypes.BEE.create(level, EntitySpawnReason.COMMAND);
			bee.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0.0F, 0.0F);
			level.addFreshEntity(bee);
			bee.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			int afterSkeleton = com.huntlike.path.ChaliceItem.blood(player.getOffhandItem());
			var cow = EntityTypes.COD.create(level, EntitySpawnReason.COMMAND);
			cow.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0.0F, 0.0F);
			level.addFreshEntity(cow);
			cow.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			int afterCow = com.huntlike.path.ChaliceItem.blood(player.getOffhandItem());
			var pig = EntityTypes.PIG.create(level, EntitySpawnReason.COMMAND);
			pig.snapTo(player.getX() + 2, player.getY(), player.getZ(), 0.0F, 0.0F);
			level.addFreshEntity(pig);
			pig.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			boolean full = player.getOffhandItem().is(com.huntlike.path.ModPaths.FULL_CHALICE);
			HuntLike.LOGGER.info("[test] blood after skeleton and bee {} after cod {} full {}", afterSkeleton, afterCow, full);
			level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(8)).forEach(e -> e.discard());
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			return afterSkeleton == 98 && afterCow == 99 && full && QuestManager.data(player).wildfireGoals == 8;
		});
		context.waitTicks(5);
		context.takeScreenshot("97h-full-chalice");
		java.util.UUID[] sorrowId = new java.util.UUID[1];
		assertServer(singleplayer, "the Full Chalice on a dried ghast raises the Spectral Sorrow (110 health) and comes back empty", server -> {
			ServerPlayer player = player(server);
			var level = player.level();
			var pos = spawnerPos[0];
			level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.DRIED_GHAST.defaultBlockState());
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, player.getOffhandItem().copy());
			player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND, net.minecraft.world.item.ItemStack.EMPTY);
			boolean fed = com.huntlike.path.WildfirePath.feedDriedGhast(player, pos, net.minecraft.world.InteractionHand.MAIN_HAND);
			var found = level.getEntitiesOfClass(com.huntlike.path.SpectralSorrow.class, player.getBoundingBox().inflate(16));
			boolean empty = player.getMainHandItem().is(com.huntlike.path.ModPaths.CHALICE_ITEM);
			HuntLike.LOGGER.info("[test] dried ghast fed {} sorrows {} chalice back {} block {}", fed, found.size(), empty, level.getBlockState(pos));
			if (found.isEmpty()) {
				return false;
			}
			var sorrow = found.getFirst();
			sorrow.setNoAi(true);
			sorrow.snapTo(player.getX() + 0.5, player.getY() + 1.0, player.getZ() + 12.0, 180.0F, 0.0F);
			sorrowId[0] = sorrow.getUUID();
			return fed && empty && sorrow.getMaxHealth() == 110.0F && level.getBlockState(pos).isAir();
		});
		context.getInput().lookAt(new net.minecraft.core.BlockPos(0, -57, 12));
		context.waitTicks(10);
		context.takeScreenshot("97j-spectral-sorrow");
		for (int i = 0; i < 12; i++) {
			singleplayer.getServer().runOnServer(server -> {
				ServerPlayer player = player(server);
				if (player.level().getEntity(sorrowId[0]) instanceof com.huntlike.path.SpectralSorrow sorrow) {
					sorrow.ray(player.level(), new net.minecraft.world.phys.Vec3(-1.0, -0.15, -0.6).normalize(), false);
				}
			});
			context.waitTicks(1);
		}
		context.takeScreenshot("97k-spectral-sorrow-ray");
		// Let any earlier hit's invulnerability wear off first.
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.setHealth(player.getMaxHealth());
			player.clearFire();
		});
		context.waitTicks(25);
		assertServer(singleplayer, "the Spectral Sorrow's ray deals 6 damage and sets on fire", server -> {
			ServerPlayer player = player(server);
			player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
			player.setHealth(player.getMaxHealth());
			player.clearFire();
			float before = player.getHealth();
			if (!(player.level().getEntity(sorrowId[0]) instanceof com.huntlike.path.SpectralSorrow sorrow)) {
				return false;
			}
			// In the open, above the test area (its glass stops the ray).
			double homeX = player.getX(), homeY = player.getY(), homeZ = player.getZ();
			player.teleportTo(homeX, homeY + 30.0, homeZ);
			sorrow.snapTo(homeX, homeY + 31.0, homeZ + 8.0, 180.0F, 0.0F);
			// Mob damage scales with difficulty: 6 is on Normal.
			var difficulty = server.getWorldData().getDifficulty();
			server.setDifficulty(net.minecraft.world.Difficulty.NORMAL, true);
			player.removeAllEffects();
			var dir = player.getEyePosition().subtract(sorrow.position().add(0.0, 1.6, 0.0)).normalize();
			sorrow.ray(player.level(), dir, true);
			HuntLike.LOGGER.info("[test] sorrow ray: difficulty {} mode {} invulnerable {}", server.getWorldData().getDifficulty(),
					player.gameMode(), player.getAbilities().invulnerable);
			float after = player.getHealth();
			server.setDifficulty(difficulty, true);
			boolean burning = player.isOnFire();
			HuntLike.LOGGER.info("[test] sorrow ray health {} -> {} burning {}", before, after, burning);
			player.teleportTo(homeX, homeY, homeZ);
			player.resetFallDistance();
			player.setHealth(player.getMaxHealth());
			player.clearFire();
			sorrow.snapTo(homeX + 0.5, homeY + 1.0, homeZ + 12.0, 180.0F, 0.0F);
			return before - after >= 4.0F && burning;
		});
		assertServer(singleplayer, "defeating the Spectral Sorrow reaches goal 9 and drops a Blood Crystallization", server -> {
			ServerPlayer player = player(server);
			var level = player.level();
			if (!(level.getEntity(sorrowId[0]) instanceof com.huntlike.path.SpectralSorrow sorrow)) {
				return false;
			}
			sorrow.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			boolean drop = !level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, sorrow.getBoundingBox().inflate(4),
					e -> e.getItem().is(com.huntlike.path.ModPaths.BLOOD_CRYSTALLIZATION)).isEmpty();
			int goals = QuestManager.data(player).wildfireGoals;
			HuntLike.LOGGER.info("[test] sorrow dead {} drop {} goals {}", !sorrow.isAlive(), drop, goals);
			return !sorrow.isAlive() && drop && goals == 9;
		});
		context.waitTicks(70);
		context.takeScreenshot("97l-canavar");
		java.util.UUID[] evilbornId = new java.util.UUID[1];
		assertServer(singleplayer, "the Blood Crystallization (100 uses) turns a villager into an Evilborn and loses one use", server -> {
			ServerPlayer player = player(server);
			var level = player.level();
			var crystal = new net.minecraft.world.item.ItemStack(com.huntlike.path.ModPaths.BLOOD_CRYSTALLIZATION);
			player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, crystal);
			var villager = EntityTypes.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			villager.snapTo(player.getX() + 0.5, player.getY(), player.getZ() + 3.5, 180.0F, 0.0F);
			level.addFreshEntity(villager);
			boolean turned = com.huntlike.path.WildfirePath.corrupt(player, villager, net.minecraft.world.InteractionHand.MAIN_HAND);
			var found = level.getEntitiesOfClass(com.huntlike.path.Evilborn.class, player.getBoundingBox().inflate(8));
			var held = player.getMainHandItem();
			HuntLike.LOGGER.info("[test] evilborn turned {} found {} villager removed {} crystal damage {}/{}", turned, found.size(),
					villager.isRemoved(), held.getDamageValue(), held.getMaxDamage());
			if (found.isEmpty()) {
				return false;
			}
			found.getFirst().setNoAi(true);
			evilbornId[0] = found.getFirst().getUUID();
			return turned && villager.isRemoved() && held.getMaxDamage() == 100 && held.getDamageValue() == 1;
		});
		context.getInput().lookAt(new net.minecraft.core.BlockPos(0, -58, 3));
		context.waitTicks(10);
		context.takeScreenshot("97m-evilborn");
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var level = player.level();
			for (int i = 0; i < com.huntlike.path.WildfirePath.EVILBORNS; i++) {
				var evilborn = i == 0 ? (com.huntlike.path.Evilborn) level.getEntity(evilbornId[0])
						: com.huntlike.path.ModPaths.EVILBORN.create(level, EntitySpawnReason.COMMAND);
				if (i > 0) {
					evilborn.snapTo(player.getX() + 2, player.getY(), player.getZ() + 2, 0.0F, 0.0F);
					level.addFreshEntity(evilborn);
				}
				evilborn.hurtServer(level, player.damageSources().playerAttack(player), 1000.0F);
			}
			level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, player.getBoundingBox().inflate(8)).forEach(e -> e.discard());
		});
		check(singleplayer, "10 Evilborns slain: Canavar speaks first, the goal comes after his last line", data -> data.wildfireEvilborns == 10
				&& data.wildfireGoals == 9);
		context.waitTicks(com.huntlike.path.WildfirePath.CANAVAR_STORY.size() * com.huntlike.path.WildfirePath.STORY_LINE_TICKS + 20);
		check(singleplayer, "after Canavar's story the tenth goal is reached", data -> data.wildfireGoals == 10);
		context.takeScreenshot("97n-canavar-story");
		context.setScreen(() -> new com.huntlike.client.screen.PathsScreen(null));
		// Cursor in a corner, so no tooltip covers the Path.
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(5);
		context.takeScreenshot("97d-paths-wildfire");
		galleryShot(context, "7b-path-of-the-wildfire-complete");
		context.setScreen(() -> new com.huntlike.client.screen.PathSelectScreen(null));
		context.waitTicks(5);
		context.takeScreenshot("97i-path-select");
		context.setScreen(() -> null);
		context.waitTicks(3);
	}

	/** The Soul Nexus: points from first boss kills, the three corners, the cap, taking points back, and their effects. */
	private static void soulNexus(ClientGameTestContext context, TestSingleplayerContext singleplayer) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			for (String hunt : new String[] {"animal", "cluster"}) {
				for (int i = 0; i < 5; i++) {
					data.progress(hunt).bossKills[i] = Math.max(1, data.progress(hunt).bossKills[i]);
				}
			}
			for (int i = 0; i < 3; i++) {
				data.progress("slime_rider").bossKills[i] = Math.max(1, data.progress("slime_rider").bossKills[i]);
			}
			data.nexusHealth = 0;
			data.nexusDefense = 0;
			data.nexusDamage = 0;
			player.setAttached(QuestManager.DATA, data);
			var pos = player.blockPosition().relative(player.getDirection(), 2);
			player.level().setBlockAndUpdate(pos, com.huntlike.registry.ModBlocks.SOUL_NEXUS.defaultBlockState());
			QuestManager.openNexus(player);
		});
		context.waitForScreen(com.huntlike.client.screen.SoulNexusScreen.class);
		context.waitTicks(5);
		check(singleplayer, "13 Soul Points: 5 + 5 boss tiers and 3 Interlude stages", data -> com.huntlike.nexus.SoulNexus.earned(data) == 13);
		context.takeScreenshot("96a-soul-nexus-empty");
		java.util.function.BiConsumer<String, Integer> send = (corner, value) -> context.runOnClient(client ->
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.huntlike.network.HuntPayloads.Action("nexus", corner, value)));
		send.accept("health", 10);
		context.waitTicks(5);
		send.accept("defense", 10);
		context.waitTicks(5);
		send.accept("damage", 1);
		context.waitTicks(10);
		context.takeScreenshot("96b-soul-nexus-spent");
		context.getInput().setCursorPos(2, 2);
		context.waitTicks(3);
		galleryShot(context, "6-soul-nexus");
		check(singleplayer, "Health 10 (capped), Defense 3 (all that's left), Damage refused",
				data -> data.nexusHealth == 10 && data.nexusDefense == 3 && data.nexusDamage == 0);
		assertServer(singleplayer, "Health 10 gives +5 max health; Defense 3 means 0.75% less damage", server -> {
			ServerPlayer player = player(server);
			var modifier = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
					.getModifier(HuntLike.id("soul_nexus_health"));
			HuntData data = QuestManager.data(player);
			return modifier != null && modifier.amount() == 5.0
					&& Math.abs(com.huntlike.nexus.SoulNexus.defenseFactor(data) - 0.9925F) < 1.0E-4F;
		});
		send.accept("health", -10);
		context.waitTicks(5);
		send.accept("damage", 10);
		context.waitTicks(10);
		check(singleplayer, "points taken back from Health go to Damage", data -> data.nexusHealth == 0 && data.nexusDamage == 10);
		assertServer(singleplayer, "no Health points: the max health bonus is gone; Damage 10 is +5%", server -> {
			ServerPlayer player = player(server);
			var modifier = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH)
					.getModifier(HuntLike.id("soul_nexus_health"));
			return modifier == null && Math.abs(com.huntlike.nexus.SoulNexus.damageFactor(QuestManager.data(player)) - 1.05F) < 1.0E-4F;
		});
		context.takeScreenshot("96c-soul-nexus-damage");
		context.setScreen(() -> null);
		context.waitTicks(3);
		// Spiritual animals glow light blue for their hunter.
		final int[] spirit = new int[1];
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			var cow = EntityTypes.COW.create(player.level(), EntitySpawnReason.COMMAND);
			cow.snapTo(player.getX() + 3, player.getY(), player.getZ(), 0.0F, 0.0F);
			player.level().addFreshEntity(cow);
			spirit[0] = cow.getId();
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player,
					new com.huntlike.network.HuntPayloads.Highlight(cow.getId(), 100));
		});
		context.waitTicks(10);
		boolean blue = context.computeOnClient(client -> {
			var cow = client.level.getEntity(spirit[0]);
			return cow != null && client.shouldEntityAppearGlowing(cow) && cow.getTeamColor() == 0x55FFFF;
		});
		HuntLike.LOGGER.info("[test] spiritual animal outline light blue: {}", blue);
		if (!blue) {
			throw new AssertionError("Spiritual animal outline isn't light blue");
		}
	}

	private static void assertServer(TestSingleplayerContext singleplayer, String what,
			java.util.function.Predicate<MinecraftServer> test) {
		boolean ok = singleplayer.getServer().computeOnServer(test::test);
		HuntLike.LOGGER.info("[test] {}: {}", what, ok ? "OK" : "FAILED");
		if (!ok) {
			throw new AssertionError("Check failed: " + what);
		}
	}

	private static void give(ServerPlayer player, net.minecraft.world.item.Item item, int count) {
		QuestManager.giveStack(player, new net.minecraft.world.item.ItemStack(item, count));
	}

	/** Damages the boss so its health drops to about the given value. */
	private static void hitBossDownTo(TestSingleplayerContext singleplayer, float health) {
		float current = singleplayer.getServer().computeOnServer(server -> {
			ServerPlayer player = player(server);
			Mob boss = BossController.findBoss(player, QuestManager.data(player).quest);
			return boss == null ? 0.0F : boss.getHealth();
		});
		hitBoss(singleplayer, Math.max(1.0F, current - health));
	}

	private static void hitBoss(TestSingleplayerContext singleplayer, float amount) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			Mob boss = BossController.findBoss(player, QuestManager.data(player).quest);
			if (boss == null) {
				throw new AssertionError("Boss not found");
			}
			boss.hurtServer(player.level(), player.level().damageSources().playerAttack(player), amount);
		});
	}

	private static ServerPlayer player(MinecraftServer server) {
		return server.getPlayerList().getPlayers().get(0);
	}

	private static void hover(ClientGameTestContext context, String button) {
		double[] pos = context.computeOnClient(client -> {
			if (client.gui.screen() instanceof HuntMenuScreen screen && screen.buttonCenter(button) != null) {
				int[] center = screen.buttonCenter(button);
				double scale = client.getWindow().getGuiScale();
				return new double[] {center[0] * scale, center[1] * scale};
			}
			return null;
		});
		if (pos == null) {
			throw new AssertionError("Button not found: " + button);
		}
		context.getInput().setCursorPos(pos[0], pos[1]);
		context.waitTicks(3);
	}

	private static void addProgress(TestSingleplayerContext singleplayer, int xp, int tokens) {
		singleplayer.getServer().runOnServer(server -> {
			ServerPlayer player = player(server);
			HuntData data = QuestManager.data(player);
			data.progress("animal").xp += xp;
			data.progress("animal").tokens += tokens;
			player.setAttached(QuestManager.DATA, data);
			QuestManager.sync(player);
		});
	}

	/** Waits until the server is at least {@code ticks} game ticks past {@code start} (at most 400 client ticks). */
	private static void waitServerTicks(ClientGameTestContext context, TestSingleplayerContext singleplayer, long start, int ticks) {
		for (int i = 0; i < 400; i++) {
			long now = singleplayer.getServer().computeOnServer(server -> player(server).level().getGameTime());
			if (now - start >= ticks) {
				return;
			}
			context.waitTick();
		}
	}

	private static void check(TestSingleplayerContext singleplayer, String what, java.util.function.Predicate<HuntData> test) {
		boolean ok = singleplayer.getServer().computeOnServer(server -> test.test(QuestManager.data(player(server))));
		String state = singleplayer.getServer().computeOnServer(server -> QuestManager.data(player(server)).toTag().toString());
		HuntLike.LOGGER.info("[test] {}: {} | {}", what, ok ? "OK" : "FAILED", state);
		if (!ok) {
			throw new AssertionError("Check failed: " + what + " | " + state);
		}
	}
}
