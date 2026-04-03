package com.pop.jjk;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

public final class PiercingBloodTechniqueHandler {

    private static final int   COOLDOWN_TICKS = 25;
    private static final int   ENERGY_COST    = 80;

    private static final Map<UUID, Integer> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> ACTIVE_PROJECTILES = new ConcurrentHashMap<>();

    private PiercingBloodTechniqueHandler() {
    }

    public static void activate(ServerPlayer player) {
        if (!player.isAlive()) return;
        if (!isTechniqueAvailable(player)) return;

        // Limpiar referencia obsoleta si quedó un ID guardado pero ya no existe la entidad
        {
            ServerLevel levelCheck = (ServerLevel) player.level();
            Integer pid = ACTIVE_PROJECTILES.get(player.getUUID());
            if (pid != null) {
                net.minecraft.world.entity.Entity ent = levelCheck.getEntity(pid);
                if (!(ent instanceof PiercingBloodProjectileEntity) || !ent.isAlive()) {
                    ACTIVE_PROJECTILES.remove(player.getUUID());
                }
            }
            // Si aún hay un proyectil activo, no crear otro
            PiercingBloodProjectileEntity existing = findActiveProjectile(player, levelCheck);
            if (existing != null && existing.isAlive()) {
                return;
            }
        }

        int cd = COOLDOWNS.getOrDefault(player.getUUID(), 0);
        if (cd > 0 && !BlueTechniqueHandler.hasNoCooldown(player.getUUID())) {
            player.displayClientMessage(
                Component.translatable("message.jjk.piercing_blood_cooldown", formatSeconds(cd)), true);
            return;
        }

        if (!CursedEnergyManager.consume(player, ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level  = (ServerLevel) player.level();
        Vec3 dir    = player.getLookAngle().normalize();
        Vec3 origin = player.getEyePosition().add(dir.scale(0.55));

        level.playSound(null, player.blockPosition(),
            SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.1F, 1.90F);
        level.playSound(null, player.blockPosition(),
            SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7F, 1.80F);

        DustParticleOptions burst = new DustParticleOptions(0xFF0505, 0.50F);
        level.sendParticles(burst, origin.x, origin.y, origin.z, 8, 0.04, 0.04, 0.04, 0.07);

        PiercingBloodProjectileEntity projectile = new PiercingBloodProjectileEntity(level, player);
        level.addFreshEntity(projectile);
        // Siempre nace con held=false; el cliente mandará onHold(true) si el jugador mantiene
        ACTIVE_PROJECTILES.put(player.getUUID(), projectile.getId());
    }

    public static void tick() {
        COOLDOWNS.entrySet().removeIf(entry -> {
            if (BlueTechniqueHandler.hasNoCooldown(entry.getKey())) return true;
            int next = entry.getValue() - 1;
            if (next <= 0) return true;
            entry.setValue(next);
            return false;
        });

    }

    public static void clearActive() {
        COOLDOWNS.clear();
        ACTIVE_PROJECTILES.clear();
    }

    public static void onHold(ServerPlayer player, boolean holding) {
        ServerLevel level = (ServerLevel) player.level();
        PiercingBloodProjectileEntity proj = findActiveProjectile(player, level);
        if (proj == null) return; // No crear aquí: sólo se crea con el payload de disparo
        proj.setHeld(holding);
        if (holding) {
            proj.requestMinLifetimeTicks(6);
        }
    }

    public static void onProjectileDied(UUID ownerUUID) {
        ACTIVE_PROJECTILES.remove(ownerUUID);
        if (!BlueTechniqueHandler.hasNoCooldown(ownerUUID)) {
            COOLDOWNS.put(ownerUUID, COOLDOWN_TICKS);
        } else {
            COOLDOWNS.remove(ownerUUID);
        }
    }

    private static PiercingBloodProjectileEntity findActiveProjectile(ServerPlayer player, ServerLevel level) {
        Integer id = ACTIVE_PROJECTILES.get(player.getUUID());
        if (id != null) {
            net.minecraft.world.entity.Entity ent = level.getEntity(id);
            if (ent instanceof PiercingBloodProjectileEntity p && p.isAlive()) {
                return p;
            }
        }

        // Fallback: buscar cerca del jugador por propietario
        for (PiercingBloodProjectileEntity p : level.getEntitiesOfClass(
            PiercingBloodProjectileEntity.class,
            player.getBoundingBox().inflate(96.0),
            e -> e.getOwner() == player
        )) {
            ACTIVE_PROJECTILES.put(player.getUUID(), p.getId());
            return p;
        }
        return null;
    }

    private static boolean isTechniqueAvailable(ServerPlayer player) {
        return JJKRoster.techniquesForCharacter(
            CharacterSelectionHandler.getSelectedCharacter(player)
        ).stream().anyMatch(d -> d.id().equals("piercing_blood"));
    }

    private static String formatSeconds(int ticks) {
        return String.format(java.util.Locale.ROOT, "%.1f", ticks / 20.0);
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
    }
}
