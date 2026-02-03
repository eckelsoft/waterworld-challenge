package org.eckelsoft.waterworldchallenge;

import net.fabricmc.api.ModInitializer;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.NbtReadView;
import net.minecraft.util.ErrorReporter;
import net.minecraft.registry.RegistryWrapper;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.MobSpawnerBlockEntity;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Waterworldchallenge implements ModInitializer {
    public static final String MOD_ID = "waterworldchallenge";
    private final Map<UUID, Integer> airTicks = new HashMap<>();
    private int damageTickTimer = 0;
    private int spawnerScanTimer = 0;

    @Override
    public void onInitialize() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> giveWaterBucket(handler.getPlayer()));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            giveWaterBucket(newPlayer);
            airTicks.put(newPlayer.getUuid(), 0);
        });

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            damageTickTimer++;
            spawnerScanTimer++;

            if (spawnerScanTimer >= 400) {
                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    convertSpawners(player);
                }
                spawnerScanTimer = 0;
            }

            boolean triggerDamage = (damageTickTimer >= 30);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                UUID uuid = player.getUuid();

                if (player.isSubmergedInWater() || player.isCreative() || player.isSpectator()) {
                    airTicks.put(uuid, 0);
                } else {
                    airTicks.put(uuid, airTicks.getOrDefault(uuid, 0) + 1);

                    int gracePeriod = 30;
                    ItemStack helmet = player.getEquippedStack(EquipmentSlot.HEAD);
                    if (helmet.isOf(Items.TURTLE_HELMET)) {
                        gracePeriod = 300;
                    }

                    if (triggerDamage && airTicks.get(uuid) > gracePeriod) {
                        ServerWorld world = player.getEntityWorld();

                        int respirationLevel = EnchantmentHelper.getLevel(
                                world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT).getOrThrow(Enchantments.RESPIRATION),
                                helmet
                        );

                        float finalDamage = 2.0f;
                        if (respirationLevel > 0) {
                            finalDamage = Math.max(0.5f, 2.0f - (respirationLevel * 0.5f));
                        }

                        DamageSource ds = new DamageSource(
                                world.getRegistryManager()
                                        .getOrThrow(RegistryKeys.DAMAGE_TYPE)
                                        .getOrThrow(DamageTypes.DROWN)
                        );

                        player.damage(world, ds, finalDamage);
                    }
                }
            }
            if (triggerDamage) damageTickTimer = 0;
        });
    }

    private void convertSpawners(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        BlockPos pos = player.getBlockPos();
        int radius = 32;
        RegistryWrapper.WrapperLookup registries = world.getRegistryManager();

        for (BlockPos targetPos : BlockPos.iterate(pos.add(-radius, -16, -radius), pos.add(radius, 16, radius))) {
            BlockEntity be = world.getBlockEntity(targetPos);

            if (be instanceof MobSpawnerBlockEntity spawner) {
                var logic = spawner.getLogic();
                var currentEntity = logic.getRenderedEntity(world, targetPos);

                if (currentEntity == null || currentEntity.getType() != EntityType.BLAZE) {
                    logic.setEntityId(EntityType.BLAZE, world, world.getRandom(), targetPos);
                    net.minecraft.nbt.NbtCompound nbt = spawner.createNbt(registries);
                    nbt.remove("SpawnPotentials");
                    nbt.remove("SpawnData");
                    spawner.read(NbtReadView.create(ErrorReporter.EMPTY, registries, nbt));
                    spawner.markDirty();
                    world.updateListeners(targetPos, spawner.getCachedState(), spawner.getCachedState(), 3);
                }
            }
        }
    }

    private void giveWaterBucket(ServerPlayerEntity player) {
        if (!player.getInventory().contains(new ItemStack(Items.WATER_BUCKET))) {
            player.getInventory().offerOrDrop(new ItemStack(Items.WATER_BUCKET));
        }
    }
}