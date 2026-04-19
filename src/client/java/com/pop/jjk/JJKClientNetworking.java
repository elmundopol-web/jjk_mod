package com.pop.jjk;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

final class JJKClientNetworking {

    private JJKClientNetworking() {
    }

    static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(CharacterStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> JJKClientMod.applyCharacterState(payload.characterId(), context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(CursedEnergySyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> JJKClientMod.applyCursedEnergySync(payload.currentEnergy(), payload.maxEnergy()))
        );
        ClientPlayNetworking.registerGlobalReceiver(CooldownSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> JJKClientMod.applyCooldownSync(payload.remainingTicks(), payload.totalTicks()))
        );
        ClientPlayNetworking.registerGlobalReceiver(ScreenShakePayload.TYPE, (payload, context) ->
            context.client().execute(() -> ScreenShakeManager.trigger(payload.intensity(), payload.durationTicks()))
        );
        ClientPlayNetworking.registerGlobalReceiver(FugaBeamFXPayload.TYPE, (payload, context) ->
            context.client().execute(() -> JJKClientMod.spawnFugaBeamFX(Minecraft.getInstance(),
                payload.x1(), payload.y1(), payload.z1(), payload.x2(), payload.y2(), payload.z2()))
        );
        ClientPlayNetworking.registerGlobalReceiver(FugaOverchargeSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> JJKClientMod.applyFugaOverchargeSync(payload.durationTicks()))
        );
        ClientPlayNetworking.registerGlobalReceiver(FugaExplosionFXPayload.TYPE, (payload, context) ->
            context.client().execute(() -> JJKClientMod.spawnFugaExplosionFX(Minecraft.getInstance(),
                payload.x(), payload.y(), payload.z(), payload.radius()))
        );
        ClientPlayNetworking.registerGlobalReceiver(BlueAnimSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> JJKClientMod.applyBlueAnimSync(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(InfiniteDomainSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> InfiniteDomainOverlay.handleSync(payload))
        );
        ClientPlayNetworking.registerGlobalReceiver(MalevolentShrineSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> MalevolentShrineOverlay.handleSync(payload))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> JJKClientMod.resetClientState());
    }
}
