package com.pop.jjk;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

final class ClientAbilityInputHandler {

    private static boolean blueUseHeld = false;
    private static boolean supernovaUseHeld = false;
    private static boolean fugaUseHeld = false;
    private static boolean piercingBloodUseHeld = false;

    private ClientAbilityInputHandler() {
    }

    static void tick(Minecraft client) {
        handleBlueHoldInput(client);
        handleSupernovaHoldInput(client);
        handleFugaHoldInput(client);
        handlePiercingBloodHoldInput(client);
    }

    static void reset() {
        blueUseHeld = false;
        supernovaUseHeld = false;
        fugaUseHeld = false;
        piercingBloodUseHeld = false;
    }

    private static void handleBlueHoldInput(Minecraft client) {
        JJKClientMod.AbilityHotbarEntry selectedEntry = JJKClientMod.getSelectedAbilityEntry();
        boolean isBlueSelected = JJKClientMod.shouldCaptureAbilityInput(client)
            && selectedEntry != null
            && "blue".equals(selectedEntry.id());

        boolean useDown = isBlueSelected && client.options.keyAttack.isDown();
        if (useDown && !blueUseHeld) {
            ClientPlayNetworking.send(new BlueTechniqueUsePayload(true));
        }

        blueUseHeld = useDown;
    }

    private static void handleSupernovaHoldInput(Minecraft client) {
        boolean canInteract = client.player != null && client.screen == null;
        JJKClientMod.AbilityHotbarEntry entry = JJKClientMod.getSelectedAbilityEntry();

        boolean allowByHotbar = JJKClientMod.shouldCaptureAbilityInput(client)
            && entry != null
            && "supernova".equals(entry.id());

        boolean inputDown = client.options.keyAttack.isDown();
        boolean useDown = canInteract && allowByHotbar && inputDown;

        if (useDown && !supernovaUseHeld) {
            ClientPlayNetworking.send(SupernovaUsePayload.INSTANCE);
        }
        if (useDown) {
            ClientPlayNetworking.send(new SupernovaHoldPayload(true));
        }
        if (!useDown && supernovaUseHeld) {
            ClientPlayNetworking.send(new SupernovaHoldPayload(false));
        }
        supernovaUseHeld = useDown;
    }

    private static void handlePiercingBloodHoldInput(Minecraft client) {
        boolean canInteract = client.player != null && client.screen == null;
        JJKClientMod.AbilityHotbarEntry entry = JJKClientMod.getSelectedAbilityEntry();

        boolean allowByHotbar = JJKClientMod.shouldCaptureAbilityInput(client)
            && entry != null
            && "piercing_blood".equals(entry.id());

        boolean inputDown = client.options.keyAttack.isDown();
        boolean useDown = canInteract && allowByHotbar && inputDown;

        if (useDown) {
            ClientPlayNetworking.send(new PiercingBloodHoldPayload(true));
        }
        if (!useDown && piercingBloodUseHeld) {
            ClientPlayNetworking.send(new PiercingBloodHoldPayload(false));
        }
        piercingBloodUseHeld = useDown;
    }

    private static void handleFugaHoldInput(Minecraft client) {
        boolean canInteract = client.player != null && client.screen == null;
        JJKClientMod.AbilityHotbarEntry entry = JJKClientMod.getSelectedAbilityEntry();

        boolean isFugaActive = "fuga".equals(JJKClientMod.getTecnicaActivaId());
        boolean allowByHotbar = JJKClientMod.shouldCaptureAbilityInput(client)
            && (isFugaActive || (entry != null && "fuga".equals(entry.id())));

        boolean inputDown = client.options.keyAttack.isDown();
        boolean useDown = canInteract && allowByHotbar && inputDown;

        if (useDown && !fugaUseHeld) {
            JJKClientMod.selectTechniqueForHold("fuga");
            ClientPlayNetworking.send(new FugaHoldPayload(true));
        }
        if (!useDown && fugaUseHeld) {
            ClientPlayNetworking.send(new FugaHoldPayload(false));
        }
        fugaUseHeld = useDown;
    }
}
