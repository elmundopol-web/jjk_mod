package com.pop.jjk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class JJKClientMod implements ClientModInitializer {

    private static final int USE_BUFFER_TICKS = 4;
    private static final int BLUE_INVOKE_ANIM_TICKS = 6;
    private static final int BLUE_LAUNCH_ANIM_TICKS = 6;
    private static final KeyMapping.Category GENERAL_CATEGORY =
        KeyMapping.Category.register(Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "general"));
    private static final Component NO_TECHNIQUE_NAME = Component.translatable("screen.jjk.none");
    private static final Component NO_CHARACTER_NAME = Component.translatable("screen.jjk.character_unselected");
    private static final AbilityHotbarEntry INFINITY_ENTRY = new AbilityHotbarEntry(
        "infinity",
        "technique.jjk.infinity",
        "technique.jjk.infinity_desc",
        "technique.jjk.infinity_hint",
        0x9EEBFF,
        true,
        true
    );

    public static KeyMapping abrirMenu;
    public static KeyMapping abrirInfo;

    private static String characterId = JJKRoster.NONE;
    private static Component characterName = NO_CHARACTER_NAME;
    private static boolean characterSelectionPending = false;
    private static boolean abilityHotbarVisible = false;
    private static int selectedAbilityIndex = 0;
    private static boolean infoKeyHeld = false;
    private static boolean blueUseHeld = false;
    private static boolean piercingUseHeld = false;
    private static boolean supernovaUseHeld = false;
    private static boolean fugaUseHeld = false;
    private static String tecnicaActivaId = JJKRoster.NONE;
    private static Component tecnicaActivaNombre = NO_TECHNIQUE_NAME;
    private static boolean infinitoActivo = false;
    private static int enfriamientoUsoTicks = 0;
    private static int cursedEnergy = 1000;
    private static int maxCursedEnergy = 1000;
    private static int cooldownRemaining = 0;
    private static int cooldownTotal = 0;
    private static long cooldownReceivedTick = 0;
    private static int fugaSkyTintTicks = 0;

    // Blue player animation state (client-side)
    private static final Map<Integer, BluePlayerAnimState> blueAnimStates = new HashMap<>();

    private static class BluePlayerAnimState {
        int phase; // 0=STOP, 1=INVOKE, 2=CHARGING, 3=LAUNCH
        int ticksInPhase;
        boolean chargingStarted;

        BluePlayerAnimState(int phase) {
            this.phase = phase;
            this.ticksInPhase = 0;
            this.chargingStarted = phase == BlueAnimSyncPayload.PHASE_CHARGING;
        }

    }

    private static void spawnFugaExplosionFX(Minecraft client, double x, double y, double z, float radius) {
        if (client.level == null) return;
        net.minecraft.client.multiplayer.ClientLevel level = client.level;
        // Fogonazo central moderado
        for (int i = 0; i < 28; i++) {
            double ox = (level.random.nextDouble() - 0.5) * (radius * 0.6);
            double oy = (level.random.nextDouble() - 0.5) * (radius * 0.4);
            double oz = (level.random.nextDouble() - 0.5) * (radius * 0.6);
            level.addParticle(JJKParticles.FIRE_EXPLOSION, x + ox, y + oy, z + oz, 0.0, 0.0, 0.0);
        }
        // Anillos de fuego (2 anillos, baja densidad)
        for (int ring = 0; ring < 2; ring++) {
            double r = radius * (0.65 + ring * 0.22);
            int points = 16;
            for (int i = 0; i < points; i++) {
                double a = (Math.PI * 2.0 * i) / points;
                double px = x + Math.cos(a) * r;
                double pz = z + Math.sin(a) * r;
                double py = y + (level.random.nextDouble() - 0.5) * (radius * 0.10);
                level.addParticle(JJKParticles.FIRE_TRAIL, px, py, pz, 0.0, 0.02, 0.0);
            }
        }
    }

    private static void spawnFugaBeamFX(Minecraft client,
                                        double x1, double y1, double z1,
                                        double x2, double y2, double z2) {
        if (client.level == null) return;
        net.minecraft.client.multiplayer.ClientLevel level = client.level;
        double dx = x2 - x1, dy = y2 - y1, dz = z2 - z1;
        double len = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (len < 0.01) return;
        Vec3 camPos = (client.player != null) ? client.player.position() : null;
        double mx = (x1 + x2) * 0.5;
        double my = (y1 + y2) * 0.5;
        double mz = (z1 + z2) * 0.5;
        double dist2 = camPos != null ? camPos.distanceToSqr(mx, my, mz) : 0.0;

        double stepLen = 0.80;
        int beamMod = 3, trailMod = 6, sideMod = 7;
        if (dist2 > 1600.0) { // >40 bloques
            stepLen = 1.20; beamMod = 4; trailMod = 8; sideMod = 10;
        }
        if (dist2 > 3600.0) { // >60 bloques
            stepLen = 1.80; beamMod = 5; trailMod = 10; sideMod = 14;
        }

        int steps = Math.max(1, (int) (len / stepLen));
        int budget = 32;
        if (dist2 > 1600.0) budget = 20;
        if (dist2 > 3600.0) budget = 12;
        double sx = dx / steps, sy = dy / steps, sz = dz / steps;
        // Dirección normalizada y eje lateral para "fuego por los lados" (menos denso)
        double vx = dx / len, vz = dz / len;
        double rx = -vz, rz = vx;
        double rlen = Math.sqrt(rx*rx + rz*rz);
        if (rlen < 1.0E-3) { rx = 1.0; rz = 0.0; rlen = 1.0; }
        rx /= rlen; rz /= rlen;
        for (int i = 0; i <= steps; i++) {
            double px = x1 + sx * i;
            double py = y1 + sy * i;
            double pz = z1 + sz * i;
            // Cuerpo del rayo (más espaciado)
            if ((i % beamMod) == 0) {
                level.addParticle(JJKParticles.FIRE_BEAM, px, py, pz, 0.0, 0.0, 0.0);
                if (--budget <= 0) break;
            } else if ((i % trailMod) == 0) {
                level.addParticle(JJKParticles.FIRE_TRAIL, px, py, pz, 0.0, 0.0, 0.0);
                if (--budget <= 0) break;
            }
            // Llamas laterales aún más esporádicas
            if ((i % sideMod) == 0) {
                double off = 0.42 + (client.level.random.nextDouble() * 0.30);
                double lx = px + rx * off;
                double lz = pz + rz * off;
                double rx2 = px - rx * off;
                double rz2 = pz - rz * off;
                level.addParticle(JJKParticles.FIRE_TRAIL, lx, py, lz, 0.0, 0.012, 0.0);
                level.addParticle(JJKParticles.FIRE_TRAIL, rx2, py, rz2, 0.0, 0.012, 0.0);
                budget -= 2;
                if (budget <= 0) break;
            }
        }
    }

    private static void handleFugaHoldInput(Minecraft client) {
        boolean canInteract = client.player != null && client.screen == null;
        AbilityHotbarEntry entry = getSelectedAbilityEntry();

        boolean isFugaActive = "fuga".equals(tecnicaActivaId);
        boolean allowByHotbar = shouldCaptureAbilityInput(client)
            && (isFugaActive || (entry != null && "fuga".equals(entry.id())));

        boolean inputDown = client.options.keyAttack.isDown();
        boolean useDown = canInteract && allowByHotbar && inputDown;

        if (useDown && !fugaUseHeld) {
            // iniciar carga y marcar hold (transición a true)
            // auto-seleccionar Fuga para garantizar captura del input
            tecnicaActivaId = "fuga";
            tecnicaActivaNombre = getTechniqueNameComponent("fuga");
            enviarEstadoTecnicas();
            ClientPlayNetworking.send(new FugaHoldPayload(true));
        }
        // transición a false en release
        if (!useDown && fugaUseHeld) {
            ClientPlayNetworking.send(new FugaHoldPayload(false));
        }
        fugaUseHeld = useDown;
    }

    private static void handleSupernovaHoldInput(Minecraft client) {
        boolean canInteract = client.player != null && client.screen == null;
        AbilityHotbarEntry entry = getSelectedAbilityEntry();

        boolean allowByHotbar = shouldCaptureAbilityInput(client)
            && entry != null
            && "supernova".equals(entry.id());

        boolean inputDown = client.options.keyAttack.isDown();
        boolean useDown = canInteract && allowByHotbar && inputDown;

        if (useDown && !supernovaUseHeld) {
            // iniciar carga (compat) y marcar hold
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

    @Override
    public void onInitializeClient() {
        EntityRenderers.register(JJKMod.BLUE_ORB, BlueOrbRenderer::new);
        EntityRenderers.register(JJKMod.RED_PROJECTILE, RedProjectileRenderer::new);
        EntityRenderers.register(JJKMod.PURPLE_PROJECTILE, PurpleProjectileRenderer::new);
        EntityRenderers.register(JJKMod.PIERCING_BLOOD_PROJECTILE, PiercingBloodRenderer::new);
        EntityRenderers.register(JJKMod.SUPERNOVA_ORB_PROJECTILE, SupernovaOrbRenderer::new);
        EntityRenderers.register(JJKMod.DISMANTLE_PROJECTILE, DismantleProjectileRenderer::new);
        EntityRenderers.register(JJKMod.FUGA_PROJECTILE, FugaProjectileRenderer::new);

        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ENERGY,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueEnergyParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.CURSED_ENERGY,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.CursedEnergyParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_CORE,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueCoreParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ORBITAL,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueOrbitalParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ABSORBED,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueAbsorbedParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ORB_CORE,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueOrbCoreParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ORB_SPARK,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueOrbSparkParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLUE_ORB_ATTRACTION,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BlueOrbAttractionParticle::new));
        // Blood particles
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLOOD_CORE,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BloodCoreParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLOOD_TRAIL,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BloodTrailParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.BLOOD_EXPLOSION,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.BloodExplosionParticle::new));
        // Fire (Fuga)
        ParticleFactoryRegistry.getInstance().register(JJKParticles.FIRE_CHARGE,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.FireChargeParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.FIRE_BEAM,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.FireBeamParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.FIRE_TRAIL,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.FireTrailParticle::new));
        ParticleFactoryRegistry.getInstance().register(JJKParticles.FIRE_EXPLOSION,
            sprites -> new com.pop.jjk.particle.JJKParticleFactory(sprites, com.pop.jjk.particle.FireExplosionParticle::new));

        abrirMenu = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.jjk.open_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            GENERAL_CATEGORY
        ));
        abrirInfo = KeyBindingHelper.registerKeyBinding(new KeyMapping(
            "key.jjk.open_info_menu",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            GENERAL_CATEGORY
        ));

        ClientPlayNetworking.registerGlobalReceiver(CharacterStatePayload.TYPE, (payload, context) ->
            context.client().execute(() -> applyCharacterState(payload.characterId(), context.client()))
        );
        ClientPlayNetworking.registerGlobalReceiver(CursedEnergySyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                cursedEnergy = payload.currentEnergy();
                maxCursedEnergy = payload.maxEnergy();
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(CooldownSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                cooldownRemaining = payload.remainingTicks();
                cooldownTotal = payload.totalTicks();
                cooldownReceivedTick = System.currentTimeMillis();
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(ScreenShakePayload.TYPE, (payload, context) ->
            context.client().execute(() -> ScreenShakeManager.trigger(payload.intensity(), payload.durationTicks()))
        );
        ClientPlayNetworking.registerGlobalReceiver(FugaBeamFXPayload.TYPE, (payload, context) ->
            context.client().execute(() -> spawnFugaBeamFX(Minecraft.getInstance(),
                payload.x1(), payload.y1(), payload.z1(), payload.x2(), payload.y2(), payload.z2()))
        );
        ClientPlayNetworking.registerGlobalReceiver(FugaOverchargeSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> fugaSkyTintTicks = Math.max(fugaSkyTintTicks, payload.durationTicks()))
        );
        ClientPlayNetworking.registerGlobalReceiver(FugaExplosionFXPayload.TYPE, (payload, context) ->
            context.client().execute(() -> spawnFugaExplosionFX(Minecraft.getInstance(),
                payload.x(), payload.y(), payload.z(), payload.radius()))
        );
        ClientPlayNetworking.registerGlobalReceiver(BlueAnimSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> {
                if (payload.phase() == BlueAnimSyncPayload.PHASE_STOP) {
                    blueAnimStates.remove(payload.entityId());
                } else {
                    blueAnimStates.put(payload.entityId(), new BluePlayerAnimState(payload.phase()));
                }
            })
        );
        ClientPlayNetworking.registerGlobalReceiver(InfiniteDomainSyncPayload.TYPE, (payload, context) ->
            context.client().execute(() -> InfiniteDomainOverlay.handleSync(payload))
        );
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> resetClientState());
        HudRenderCallback.EVENT.register(AbilityHotbarOverlay::render);
        HudRenderCallback.EVENT.register(EnemyHealthOverlay::render);
        HudRenderCallback.EVENT.register(CursedEnergyOverlay::render);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ScreenShakeManager.tick();
            com.pop.jjk.particle.effect.ParticleEffectManager.tick();
            InfiniteDomainOverlay.tick(client);
            EnemyHealthOverlay.tick(client);

            // Tick Blue animation states
            tickBlueAnimationStates();

            if (enfriamientoUsoTicks > 0) {
                enfriamientoUsoTicks--;
            }

            if (debeAbrirSelector(client)) {
                client.setScreen(new CharacterSelectionScreen());
            }

            while (abrirMenu.consumeClick()) {
                toggleAbilityHotbar(client);
            }

            boolean infoDown = abrirInfo.isDown();
            if (infoDown && !infoKeyHeld) {
                toggleInfoMenu(client);
            }
            infoKeyHeld = infoDown;

            handleBlueHoldInput(client);
            handlePiercingHoldInput(client);
            handleSupernovaHoldInput(client);
            handleFugaHoldInput(client);
            if (fugaSkyTintTicks > 0) fugaSkyTintTicks--;
        });
    }

    private static void renderFugaSkyTint(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.client.DeltaTracker delta) { /* no-op: el tinte ahora se aplica al cielo real */ }

    public static float getFugaSkyTintFactor() {
        if (fugaSkyTintTicks <= 0) return 0.0F;
        return Math.min(1.0F, fugaSkyTintTicks / 120.0F);
    }

    public static void confirmCharacterSelection(String newCharacterId, Minecraft client) {
        if (!JJKRoster.isValidCharacter(newCharacterId)) {
            return;
        }

        applyCharacterLocally(newCharacterId);
        characterSelectionPending = false;
        ClientPlayNetworking.send(new CharacterSelectionPayload(newCharacterId));
        enviarEstadoTecnicas();

        if (client.player != null) {
            client.player.displayClientMessage(
                Component.translatable("message.jjk.character_selected", getCharacterNameComponent(newCharacterId)),
                false
            );
        }
    }

    public static void openCharacterSelection(Minecraft client) {
        client.setScreen(new CharacterSelectionScreen());
    }

    public static void openCharacterInfo(Minecraft client) {
        client.setScreen(new CharacterInfoScreen());
    }

    public static void activarTecnicaDesdeHotbar(String tecnicaId, Minecraft client) {
        if (!isTechniqueAllowedForCharacter(characterId, tecnicaId)) {
            return;
        }

        tecnicaActivaId = tecnicaId;
        tecnicaActivaNombre = getTechniqueNameComponent(tecnicaId);
        enviarEstadoTecnicas();
        usarTecnicaPorId(tecnicaId, client);
    }

    public static void alternarInfinito(Minecraft client) {
        if (!supportsInfinity()) {
            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable("message.jjk.infinity_unavailable"), true);
            }
            return;
        }

        infinitoActivo = !infinitoActivo;
        enviarEstadoTecnicas();

        if (client.player != null) {
            client.player.displayClientMessage(
                Component.translatable(infinitoActivo ? "message.jjk.infinity_enabled" : "message.jjk.infinity_disabled"),
                true
            );
        }
    }

    public static String getSelectedCharacterId() {
        return characterId;
    }

    public static Component getSelectedCharacterName() {
        return characterName;
    }

    public static JJKRoster.CharacterDefinition getSelectedCharacterDefinition() {
        return JJKRoster.getCharacter(characterId);
    }

    public static String getTecnicaActivaId() {
        return tecnicaActivaId;
    }

    public static Component getTecnicaActivaNombre() {
        return tecnicaActivaNombre;
    }

    public static boolean isInfinitoActivo() {
        return infinitoActivo;
    }

    public static int getCursedEnergy() {
        return cursedEnergy;
    }

    public static int getMaxCursedEnergy() {
        return maxCursedEnergy;
    }

    public static float getCooldownProgress() {
        if (cooldownTotal <= 0 || cooldownRemaining <= 0) return 0.0f;
        long elapsed = System.currentTimeMillis() - cooldownReceivedTick;
        int estimatedRemaining = cooldownRemaining - (int)(elapsed / 50);
        if (estimatedRemaining <= 0) return 0.0f;
        return (float) estimatedRemaining / cooldownTotal;
    }

    public static int getCooldownSeconds() {
        if (cooldownTotal <= 0 || cooldownRemaining <= 0) return 0;
        long elapsed = System.currentTimeMillis() - cooldownReceivedTick;
        int estimatedRemaining = cooldownRemaining - (int)(elapsed / 50);
        return Math.max(0, (estimatedRemaining + 19) / 20);
    }

    public static boolean isOnCooldown() {
        return getCooldownProgress() > 0.0f;
    }

    public static int getBlueAnimPhase(int entityId) {
        BluePlayerAnimState state = blueAnimStates.get(entityId);
        return state == null ? BlueAnimSyncPayload.PHASE_STOP : state.phase;
    }

    public static int getBlueAnimTicksInPhase(int entityId) {
        BluePlayerAnimState state = blueAnimStates.get(entityId);
        return state == null ? 0 : state.ticksInPhase;
    }

    public static boolean supportsInfinity() {
        JJKRoster.CharacterDefinition character = getSelectedCharacterDefinition();
        return character != null && character.supportsInfinity();
    }

    public static boolean isCharacterSelectionPending() {
        return characterSelectionPending;
    }

    public static boolean isAbilityHotbarVisible() {
        return abilityHotbarVisible;
    }

    public static List<AbilityHotbarEntry> getAbilityHotbarEntries() {
        List<AbilityHotbarEntry> entries = new ArrayList<>();

        for (JJKRoster.TechniqueDefinition technique : JJKRoster.techniquesForCharacter(characterId)) {
            entries.add(AbilityHotbarEntry.fromTechnique(technique));
        }

        if (supportsInfinity()) {
            entries.add(INFINITY_ENTRY);
        }

        return entries;
    }

    public static int getSelectedAbilityIndex() {
        clampSelectionIndex();
        return selectedAbilityIndex;
    }

    public static AbilityHotbarEntry getSelectedAbilityEntry() {
        List<AbilityHotbarEntry> entries = getAbilityHotbarEntries();
        if (entries.isEmpty()) {
            return null;
        }

        clampSelectionIndex();
        return entries.get(selectedAbilityIndex);
    }

    public static boolean shouldRenderAbilityHotbar(Minecraft client) {
        return client.player != null
            && client.screen == null
            && abilityHotbarVisible
            && hasSelectedCharacter();
    }

    public static boolean captureMouseScroll(double amount, Minecraft client) {
        if (!shouldCaptureAbilityInput(client)) {
            return false;
        }

        int direction = amount > 0.0D ? -1 : 1;
        cycleSelectedAbility(direction, client);
        return true;
    }

    public static boolean captureHotbarNumberKey(int hotbarIndex, Minecraft client) {
        if (!shouldCaptureAbilityInput(client)) {
            return false;
        }

        List<AbilityHotbarEntry> entries = getAbilityHotbarEntries();
        if (hotbarIndex < 0 || hotbarIndex >= entries.size()) {
            return hotbarIndex >= 0 && hotbarIndex < 9;
        }

        selectedAbilityIndex = hotbarIndex;
        syncSelectionWithActiveTechnique(true, client);
        return true;
    }

    public static boolean handlePrimaryAbilityClick(Minecraft client) {
        if (!shouldCaptureAbilityInput(client)) {
            return false;
        }

        AbilityHotbarEntry entry = getSelectedAbilityEntry();
        if (entry == null) {
            return false;
        }

        if (entry.passive()) {
            alternarInfinito(client);
            return true;
        }

        if ("blue".equals(entry.id())) {
            return true;
        }

        if ("piercing_blood".equals(entry.id())) {
            // Se maneja por input sostenido en handlePiercingHoldInput
            return true;
        }

        if ("supernova".equals(entry.id())) {
            // Se maneja por input sostenido en handleSupernovaHoldInput
            return true;
        }

        if ("fuga".equals(entry.id())) {
            // Se maneja por input sostenido en handleFugaHoldInput
            return true;
        }

        activarTecnicaDesdeHotbar(entry.id(), client);
        return true;
    }

    private static void toggleAbilityHotbar(Minecraft client) {
        if (client.screen != null) {
            if (client.screen instanceof CharacterSelectionScreen && !characterSelectionPending) {
                client.setScreen(null);
            }
            return;
        }

        if (!hasSelectedCharacter()) {
            client.setScreen(new CharacterSelectionScreen());
            return;
        }

        abilityHotbarVisible = !abilityHotbarVisible;
        clampSelectionIndex();
        syncSelectionWithActiveTechnique(false, client);
    }

    private static void toggleInfoMenu(Minecraft client) {
        if (client.screen instanceof CharacterInfoScreen) {
            client.setScreen(null);
            return;
        }

        if (!hasSelectedCharacter()) {
            client.setScreen(new CharacterSelectionScreen());
            return;
        }

        client.setScreen(new CharacterInfoScreen());
    }

    private static void cycleSelectedAbility(int direction, Minecraft client) {
        List<AbilityHotbarEntry> entries = getAbilityHotbarEntries();
        if (entries.isEmpty()) {
            return;
        }

        selectedAbilityIndex = Math.floorMod(selectedAbilityIndex + direction, entries.size());
        syncSelectionWithActiveTechnique(false, client);
    }

    private static boolean shouldCaptureAbilityInput(Minecraft client) {
        return client.player != null
            && client.screen == null
            && abilityHotbarVisible
            && hasSelectedCharacter();
    }

    private static void handleBlueHoldInput(Minecraft client) {
        AbilityHotbarEntry selectedEntry = getSelectedAbilityEntry();
        boolean isBlueSelected = shouldCaptureAbilityInput(client)
            && selectedEntry != null
            && "blue".equals(selectedEntry.id());

        boolean useDown = isBlueSelected && client.options.keyAttack.isDown();

        // Detectar flanco de subida (press event) — enviar un solo paquete por clic
        if (useDown && !blueUseHeld) {
            ClientPlayNetworking.send(new BlueTechniqueUsePayload(true));
        }

        blueUseHeld = useDown;
    }

    private static void handlePiercingHoldInput(Minecraft client) {
        boolean canInteract = client.player != null && client.screen == null;
        AbilityHotbarEntry entry = getSelectedAbilityEntry();

        boolean allowByHotbar = shouldCaptureAbilityInput(client)
            && entry != null
            && "piercing_blood".equals(entry.id());
        boolean inputDown = client.options.keyAttack.isDown();
        boolean useDown = canInteract && allowByHotbar && inputDown;

        if (useDown && !piercingUseHeld) {
            ClientPlayNetworking.send(PiercingBloodUsePayload.INSTANCE);
        }
        if (useDown) {
            ClientPlayNetworking.send(new PiercingBloodHoldPayload(true));
        }
        if (!useDown && piercingUseHeld) {
            ClientPlayNetworking.send(new PiercingBloodHoldPayload(false));
        }
        piercingUseHeld = useDown;
    }

    private static void tickBlueAnimationStates() {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) {
            blueAnimStates.clear();
            return;
        }

        java.util.Iterator<Map.Entry<Integer, BluePlayerAnimState>> iterator = blueAnimStates.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, BluePlayerAnimState> entry = iterator.next();
            if (client.level.getEntity(entry.getKey()) == null) {
                iterator.remove();
                continue;
            }

            BluePlayerAnimState state = entry.getValue();
            state.ticksInPhase++;

            if (state.phase == BlueAnimSyncPayload.PHASE_INVOKE && state.ticksInPhase >= BLUE_INVOKE_ANIM_TICKS) {
                state.phase = BlueAnimSyncPayload.PHASE_CHARGING;
                state.ticksInPhase = 0;
                state.chargingStarted = true;
                continue;
            }

            if (state.phase == BlueAnimSyncPayload.PHASE_LAUNCH && state.ticksInPhase >= BLUE_LAUNCH_ANIM_TICKS) {
                iterator.remove();
            }
        }
    }

    private static boolean debeAbrirSelector(Minecraft client) {
        return client.player != null
            && characterSelectionPending
            && client.screen == null;
    }

    private static void applyCharacterState(String newCharacterId, Minecraft client) {
        if (!JJKRoster.isValidCharacter(newCharacterId)) {
            characterId = JJKRoster.NONE;
            characterName = NO_CHARACTER_NAME;
            characterSelectionPending = true;
            abilityHotbarVisible = false;
            selectedAbilityIndex = 0;
            tecnicaActivaId = JJKRoster.NONE;
            tecnicaActivaNombre = NO_TECHNIQUE_NAME;
            infinitoActivo = false;
            return;
        }

        applyCharacterLocally(newCharacterId);
        characterSelectionPending = false;

        if (client.player != null) {
            enviarEstadoTecnicas();
        }
    }

    private static void applyCharacterLocally(String newCharacterId) {
        JJKRoster.CharacterDefinition character = JJKRoster.getCharacter(newCharacterId);
        characterId = newCharacterId;
        characterName = getCharacterNameComponent(newCharacterId);
        selectedAbilityIndex = 0;

        if (character == null) {
            abilityHotbarVisible = false;
            tecnicaActivaId = JJKRoster.NONE;
            tecnicaActivaNombre = NO_TECHNIQUE_NAME;
            infinitoActivo = false;
            return;
        }

        if (!character.supportsInfinity()) {
            infinitoActivo = false;
        }

        syncSelectionWithActiveTechnique(false, Minecraft.getInstance());
    }

    private static void syncSelectionWithActiveTechnique(boolean announce, Minecraft client) {
        List<AbilityHotbarEntry> entries = getAbilityHotbarEntries();
        if (entries.isEmpty()) {
            tecnicaActivaId = JJKRoster.NONE;
            tecnicaActivaNombre = NO_TECHNIQUE_NAME;
            return;
        }

        clampSelectionIndex();
        AbilityHotbarEntry entry = entries.get(selectedAbilityIndex);

        if (entry.passive()) {
            return;
        }

        tecnicaActivaId = entry.id();
        tecnicaActivaNombre = Component.translatable(entry.nameKey());
        enviarEstadoTecnicas();

        if (announce && client.player != null) {
            client.player.displayClientMessage(
                Component.translatable("message.jjk.active_technique_selected", tecnicaActivaNombre),
                true
            );
        }
    }

    private static void clampSelectionIndex() {
        List<AbilityHotbarEntry> entries = getAbilityHotbarEntries();
        if (entries.isEmpty()) {
            selectedAbilityIndex = 0;
            return;
        }

        selectedAbilityIndex = Math.floorMod(selectedAbilityIndex, entries.size());
    }

    private static boolean hasSelectedCharacter() {
        return JJKRoster.isValidCharacter(characterId);
    }

    private static boolean isTechniqueAllowedForCharacter(String currentCharacterId, String techniqueId) {
        return JJKRoster.techniquesForCharacter(currentCharacterId).stream()
            .anyMatch(definition -> definition.id().equals(techniqueId));
    }

    private static Component getCharacterNameComponent(String currentCharacterId) {
        JJKRoster.CharacterDefinition character = JJKRoster.getCharacter(currentCharacterId);
        return character == null ? NO_CHARACTER_NAME : Component.translatable(character.nameKey());
    }

    private static Component getTechniqueNameComponent(String techniqueId) {
        JJKRoster.TechniqueDefinition technique = JJKRoster.getTechnique(techniqueId);
        return technique == null ? NO_TECHNIQUE_NAME : Component.translatable(technique.nameKey());
    }

    private static void usarTecnicaPorId(String tecnicaId, Minecraft client) {
        if (enfriamientoUsoTicks > 0 || tecnicaId.equals(JJKRoster.NONE)) {
            return;
        }

        if ("red".equals(tecnicaId)) {
            ClientPlayNetworking.send(RedTechniqueUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;

            if (client.player != null) {
                client.player.displayClientMessage(Component.translatable("message.jjk.red_release"), true);
            }
            return;
        }

        if ("purple".equals(tecnicaId)) {
            ClientPlayNetworking.send(PurpleTechniqueUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if ("infinite_domain".equals(tecnicaId)) {
            ClientPlayNetworking.send(InfiniteDomainUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if ("diverging_fist".equals(tecnicaId)) {
            ClientPlayNetworking.send(DivergingFistUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if ("piercing_blood".equals(tecnicaId)) {
            ClientPlayNetworking.send(PiercingBloodUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if ("flowing_red_scale".equals(tecnicaId)) {
            ClientPlayNetworking.send(FlowingRedScaleUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if ("supernova".equals(tecnicaId)) {
            ClientPlayNetworking.send(SupernovaUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if ("dismantle".equals(tecnicaId)) {
            ClientPlayNetworking.send(DismantleUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if ("cleave".equals(tecnicaId)) {
            ClientPlayNetworking.send(CleaveUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if ("fuga".equals(tecnicaId)) {
            ClientPlayNetworking.send(FugaUsePayload.INSTANCE);
            enfriamientoUsoTicks = USE_BUFFER_TICKS;
            return;
        }

        if (client.player != null) {
            client.player.displayClientMessage(Component.translatable("message.jjk.technique_not_implemented"), true);
        }
    }

    private static void enviarEstadoTecnicas() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) {
            return;
        }

        ClientPlayNetworking.send(new TechniqueSelectionPayload(tecnicaActivaId, infinitoActivo && supportsInfinity()));
    }

    private static void resetClientState() {
        characterId = JJKRoster.NONE;
        characterName = NO_CHARACTER_NAME;
        characterSelectionPending = false;
        abilityHotbarVisible = false;
        selectedAbilityIndex = 0;
        infoKeyHeld = false;
        blueUseHeld = false;
        piercingUseHeld = false;
        supernovaUseHeld = false;
        fugaUseHeld = false;
        tecnicaActivaId = JJKRoster.NONE;
        tecnicaActivaNombre = NO_TECHNIQUE_NAME;
        infinitoActivo = false;
        enfriamientoUsoTicks = 0;
        blueAnimStates.clear();
        InfiniteDomainOverlay.clear();
        com.pop.jjk.particle.effect.ParticleEffectManager.clear();
    }

    public record AbilityHotbarEntry(
        String id,
        String nameKey,
        String descriptionKey,
        String hintKey,
        int accentColor,
        boolean passive,
        boolean implemented
    ) {
        private static AbilityHotbarEntry fromTechnique(JJKRoster.TechniqueDefinition technique) {
            return new AbilityHotbarEntry(
                technique.id(),
                technique.nameKey(),
                technique.descriptionKey(),
                technique.hintKey(),
                technique.accentColor(),
                false,
                technique.implemented()
            );
        }
    }
}
