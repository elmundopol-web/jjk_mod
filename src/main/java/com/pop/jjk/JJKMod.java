package com.pop.jjk;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

public class JJKMod implements ModInitializer {

    public static final String MOD_ID = "jjk";
    public static final Identifier BLUE_ORB_ID = Identifier.fromNamespaceAndPath(MOD_ID, "blue_orb");
    public static final EntityType<BlueOrbEntity> BLUE_ORB = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        BLUE_ORB_ID,
        EntityType.Builder.<BlueOrbEntity>of(BlueOrbEntity::new, MobCategory.MISC)
            .sized(0.9F, 0.9F)
            .clientTrackingRange(16)
            .updateInterval(1)
            .fireImmune()
            .noSave()
            .noLootTable()
            .build(ResourceKey.create(Registries.ENTITY_TYPE, BLUE_ORB_ID))
    );
    public static final Identifier RED_PROJECTILE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "red_projectile");
    public static final EntityType<RedProjectileEntity> RED_PROJECTILE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        RED_PROJECTILE_ID,
        EntityType.Builder.<RedProjectileEntity>of(RedProjectileEntity::new, MobCategory.MISC)
            .sized(1.8F, 1.8F)
            .clientTrackingRange(12)
            .updateInterval(1)
            .fireImmune()
            .noSave()
            .noLootTable()
            .build(ResourceKey.create(Registries.ENTITY_TYPE, RED_PROJECTILE_ID))
    );
    public static final Identifier PURPLE_PROJECTILE_ID = Identifier.fromNamespaceAndPath(MOD_ID, "purple_projectile");
    public static final EntityType<PurpleProjectileEntity> PURPLE_PROJECTILE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        PURPLE_PROJECTILE_ID,
        EntityType.Builder.<PurpleProjectileEntity>of(PurpleProjectileEntity::new, MobCategory.MISC)
            .sized(2.6F, 2.6F)
            .clientTrackingRange(16)
            .updateInterval(1)
            .fireImmune()
            .noSave()
            .noLootTable()
            .build(ResourceKey.create(Registries.ENTITY_TYPE, PURPLE_PROJECTILE_ID))
    );
    public static final Identifier PIERCING_BLOOD_ID = Identifier.fromNamespaceAndPath(MOD_ID, "piercing_blood");
    public static final EntityType<PiercingBloodProjectileEntity> PIERCING_BLOOD_PROJECTILE = Registry.register(
        BuiltInRegistries.ENTITY_TYPE,
        PIERCING_BLOOD_ID,
        EntityType.Builder.<PiercingBloodProjectileEntity>of(PiercingBloodProjectileEntity::new, MobCategory.MISC)
            .sized(0.2F, 0.2F)
            .clientTrackingRange(16)
            .updateInterval(1)
            .fireImmune()
            .noSave()
            .noLootTable()
            .build(ResourceKey.create(Registries.ENTITY_TYPE, PIERCING_BLOOD_ID))
    );

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(BlueTechniqueUsePayload.TYPE, BlueTechniqueUsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(RedTechniqueUsePayload.TYPE, RedTechniqueUsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PurpleTechniqueUsePayload.TYPE, PurpleTechniqueUsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(InfiniteDomainUsePayload.TYPE, InfiniteDomainUsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(DivergingFistUsePayload.TYPE, DivergingFistUsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PiercingBloodUsePayload.TYPE, PiercingBloodUsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(PiercingBloodHoldPayload.TYPE, PiercingBloodHoldPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(FlowingRedScaleUsePayload.TYPE, FlowingRedScaleUsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(SupernovaUsePayload.TYPE, SupernovaUsePayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(TechniqueSelectionPayload.TYPE, TechniqueSelectionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(CharacterSelectionPayload.TYPE, CharacterSelectionPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CharacterStatePayload.TYPE, CharacterStatePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CursedEnergySyncPayload.TYPE, CursedEnergySyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(CooldownSyncPayload.TYPE, CooldownSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(ScreenShakePayload.TYPE, ScreenShakePayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(BlueAnimSyncPayload.TYPE, BlueAnimSyncPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(InfiniteDomainSyncPayload.TYPE, InfiniteDomainSyncPayload.STREAM_CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BlueTechniqueUsePayload.TYPE, (payload, context) ->
            context.server().execute(() -> {
                if (payload.holding()) {
                    BlueTechniqueHandler.onUseBlue(context.player());
                }
            })
        );
        ServerPlayNetworking.registerGlobalReceiver(RedTechniqueUsePayload.TYPE, (payload, context) ->
            context.server().execute(() -> RedTechniqueHandler.activate(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(PurpleTechniqueUsePayload.TYPE, (payload, context) ->
            context.server().execute(() -> PurpleTechniqueHandler.activate(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(InfiniteDomainUsePayload.TYPE, (payload, context) ->
            context.server().execute(() -> InfiniteDomainTechniqueHandler.activate(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(DivergingFistUsePayload.TYPE, (payload, context) ->
            context.server().execute(() -> DivergingFistTechniqueHandler.activate(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(PiercingBloodUsePayload.TYPE, (payload, context) ->
            context.server().execute(() -> PiercingBloodTechniqueHandler.activate(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(PiercingBloodHoldPayload.TYPE, (payload, context) ->
            context.server().execute(() -> PiercingBloodTechniqueHandler.onHold(context.player(), payload.holding()))
        );
        ServerPlayNetworking.registerGlobalReceiver(FlowingRedScaleUsePayload.TYPE, (payload, context) ->
            context.server().execute(() -> FlowingRedScaleTechniqueHandler.activate(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(SupernovaUsePayload.TYPE, (payload, context) ->
            context.server().execute(() -> SupernovaTechniqueHandler.activate(context.player()))
        );
        ServerPlayNetworking.registerGlobalReceiver(TechniqueSelectionPayload.TYPE, (payload, context) ->
            context.server().execute(() -> InfinityTechniqueHandler.setInfinityEnabled(context.player(), payload.infinityEnabled()))
        );
        ServerPlayNetworking.registerGlobalReceiver(CharacterSelectionPayload.TYPE, (payload, context) ->
            context.server().execute(() -> CharacterSelectionHandler.setSelectedCharacter(context.player(), payload.characterId()))
        );
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            CharacterSelectionHandler.syncCharacterToClient(handler.player);
            CursedEnergyManager.onPlayerJoin(handler.player);
            InfiniteDomainTechniqueHandler.syncActiveDomainsToPlayer(handler.player);
        });
        JJKCommands.register();
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(InfinityTechniqueHandler::allowDamage);
        ServerLivingEntityEvents.AFTER_DAMAGE.register(InfiniteDomainTechniqueHandler::afterDamage);
        ServerTickEvents.END_SERVER_TICK.register(server -> BlueTechniqueHandler.tickActive(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> InfiniteDomainTechniqueHandler.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> DivergingFistTechniqueHandler.tick());
        ServerTickEvents.END_SERVER_TICK.register(server -> PiercingBloodTechniqueHandler.tick());
        ServerTickEvents.END_SERVER_TICK.register(server -> FlowingRedScaleTechniqueHandler.tick());
        ServerTickEvents.END_SERVER_TICK.register(server -> SupernovaTechniqueHandler.tick());
        ServerTickEvents.END_SERVER_TICK.register(server -> InfinityTechniqueHandler.tick(server));
        ServerTickEvents.END_SERVER_TICK.register(server -> CursedEnergyManager.tick(server));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BlueTechniqueHandler.clearActive());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> InfiniteDomainTechniqueHandler.clearAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> DivergingFistTechniqueHandler.clearActive());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> PiercingBloodTechniqueHandler.clearActive());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> FlowingRedScaleTechniqueHandler.clearActive());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> SupernovaTechniqueHandler.clearActive());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> InfinityTechniqueHandler.clearAll());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> CursedEnergyManager.clearAll());

        JJKParticles.init();
        JJKSounds.init();
        System.out.println("JJK Mod cargado!");
    }
}
