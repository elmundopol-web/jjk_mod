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
    private static String tecnicaActivaId = JJKRoster.NONE;
    private static Component tecnicaActivaNombre = NO_TECHNIQUE_NAME;
    private static boolean infinitoActivo = false;
    private static int enfriamientoUsoTicks = 0;
    private static int cursedEnergy = 1000;
    private static int maxCursedEnergy = 1000;
    private static int cooldownRemaining = 0;
    private static int cooldownTotal = 0;
    private static long cooldownReceivedTick = 0;

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

    @Override
    public void onInitializeClient() {
        EntityRenderers.register(JJKMod.BLUE_ORB, BlueOrbRenderer::new);
        EntityRenderers.register(JJKMod.RED_PROJECTILE, RedProjectileRenderer::new);
        EntityRenderers.register(JJKMod.PURPLE_PROJECTILE, PurpleProjectileRenderer::new);
        EntityRenderers.register(JJKMod.PIERCING_BLOOD_PROJECTILE, PiercingBloodRenderer::new);

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
        });
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
        return shouldRenderAbilityHotbar(client);
    }

    private static void handleBlueHoldInput(Minecraft client) {
        AbilityHotbarEntry selectedEntry = getSelectedAbilityEntry();
        boolean isBlueSelected = shouldCaptureAbilityInput(client)
            && selectedEntry != null
            && "blue".equals(selectedEntry.id());

        boolean useDown = isBlueSelected && client.options.keyUse.isDown();

        // Detectar flanco de subida (press event) — enviar un solo paquete por clic
        if (useDown && !blueUseHeld) {
            ClientPlayNetworking.send(new BlueTechniqueUsePayload(true));
        }

        blueUseHeld = useDown;
    }

    private static void handlePiercingHoldInput(Minecraft client) {
        boolean canInteract = client.player != null && client.screen == null;
        AbilityHotbarEntry entry = getSelectedAbilityEntry();
        boolean isSelectedPiercing = entry != null && "piercing_blood".equals(entry.id());
        boolean hasPiercingInRoster = JJKRoster.techniquesForCharacter(characterId).stream()
            .anyMatch(t -> t.id().equals("piercing_blood"));
        boolean isActivePiercing = "piercing_blood".equals(tecnicaActivaId) || isSelectedPiercing || hasPiercingInRoster;
        boolean inputDown = client.options.keyUse.isDown() || client.options.keyAttack.isDown();
        boolean useDown = canInteract && isActivePiercing && inputDown;

        if (useDown && !piercingUseHeld) {
            // Asegurar estado activo localmente si el roster incluye Piercing Blood
            if (!"piercing_blood".equals(tecnicaActivaId) && hasPiercingInRoster) {
                tecnicaActivaId = "piercing_blood";
                tecnicaActivaNombre = getTechniqueNameComponent("piercing_blood");
                enviarEstadoTecnicas();
            }
            ClientPlayNetworking.send(PiercingBloodUsePayload.INSTANCE);
        }
        // Reenviar hold(true) cada tick mientras está presionado para evitar condiciones de carrera
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
