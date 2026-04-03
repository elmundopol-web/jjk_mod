package com.pop.jjk;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Handler principal para la técnica Azul v2.
 *
 * Flujo:
 * 1. Clic izquierdo → invocar orbe (CHARGING)
 * 2. Orbe flota delante del jugador, atrae y destruye bloques
 * 3. Clic izquierdo (segunda vez) → lanzar en dirección de cámara (LAUNCHED)
 * 4. Orbe viaja con gravedad e inercia, colisiona con bloques
 * 5. Impacto: destruye radio de bloques, daño a entidades, cooldown
 */
public final class BlueTechniqueHandler {

    private static final List<ActiveBlue> ACTIVE_BLUES = new ArrayList<>();
    private static final Map<UUID, Integer> COOLDOWNS = new HashMap<>();
    private static final Set<UUID> NO_COOLDOWN = new HashSet<>();

    private BlueTechniqueHandler() {}

    public static final int BLUE_ENERGY_COST = 250;

    // ==================== API PÚBLICA ====================

    /**
     * Llamado cuando el jugador presiona clic izquierdo.
     * Primera vez: invoca el orbe. Segunda vez: lo lanza.
     */
    public static void onUseBlue(ServerPlayer player) {
        UUID playerId = player.getUUID();
        ActiveBlue existing = findActiveBlue(playerId);

        if (existing != null) {
            if (existing.state == BlueState.CHARGING) {
                launchOrb(existing, player);
            }
            return;
        }

        invokeOrb(player);
    }

    /**
     * Cancela el orbe sin lanzarlo (clic derecho / tecla cancelar).
     */
    public static void cancelBlue(ServerPlayer player) {
        ActiveBlue activeBlue = findActiveBlue(player.getUUID());
        if (activeBlue == null) return;

        discardOrb(activeBlue);
        ACTIVE_BLUES.remove(activeBlue);
    }

    public static void tickActive(net.minecraft.server.MinecraftServer server) {
        tickCooldowns(server);

        for (int i = ACTIVE_BLUES.size() - 1; i >= 0; i--) {
            ActiveBlue ab = ACTIVE_BLUES.get(i);
            ServerPlayer owner = ab.level.getServer().getPlayerList().getPlayer(ab.ownerId);

            if (owner == null || !owner.isAlive() || owner.level() != ab.level) {
                discardOrb(ab);
                ACTIVE_BLUES.remove(i);
                continue;
            }

            if (ab.state == BlueState.CHARGING) {
                tickCharging(ab, owner);
            } else {
                boolean hit = tickLaunched(ab, owner);
                if (hit || ab.ticksInState > BlueConfig.LAUNCH_MAX_TICKS) {
                    if (hit) triggerImpact(ab, owner);
                    discardOrb(ab);
                    applyCooldown(owner);
                    ACTIVE_BLUES.remove(i);
                }
            }
        }
    }

    public static void clearActive() {
        for (ActiveBlue ab : ACTIVE_BLUES) discardOrb(ab);
        ACTIVE_BLUES.clear();
        COOLDOWNS.clear();
        NO_COOLDOWN.clear();
    }

    public static boolean toggleNoCooldown(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (NO_COOLDOWN.contains(playerId)) {
            NO_COOLDOWN.remove(playerId);
            return false;
        }
        NO_COOLDOWN.add(playerId);
        COOLDOWNS.remove(playerId);
        return true;
    }

    public static void clearCooldown(ServerPlayer player) {
        COOLDOWNS.remove(player.getUUID());
    }

    public static int getCooldown(UUID playerId) {
        return COOLDOWNS.getOrDefault(playerId, 0);
    }

    public static void setCooldown(UUID playerId, int ticks) {
        COOLDOWNS.put(playerId, ticks);
    }

    public static boolean hasNoCooldown(UUID playerId) {
        return NO_COOLDOWN.contains(playerId);
    }

    // ==================== INVOCAR ORBE ====================

    private static void invokeOrb(ServerPlayer player) {
        UUID playerId = player.getUUID();

        int cooldown = COOLDOWNS.getOrDefault(playerId, 0);
        if (cooldown > 0 && !NO_COOLDOWN.contains(playerId)) {
            int secs = (cooldown + 19) / 20;
            player.displayClientMessage(Component.translatable("message.jjk.blue_cooldown", secs), true);
            return;
        }

        if (!CursedEnergyManager.consume(player, BLUE_ENERGY_COST)) {
            player.displayClientMessage(Component.translatable("message.jjk.not_enough_energy"), true);
            return;
        }

        ServerLevel level = (ServerLevel) player.level();
        Vec3 spawnPos = BlueAbility.computeTarget(player);
        BlueOrbEntity orbEntity = spawnOrbEntity(level, spawnPos);

        ActiveBlue ab = new ActiveBlue(playerId, level, spawnPos, orbEntity);
        ACTIVE_BLUES.add(ab);

        // --- Burst de invocación: anillo expansivo de partículas ---
        // Momento dramático: 12 partículas en anillo horizontal + flash central
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0 / 12) * i;
            double bx = spawnPos.x + Math.cos(angle) * 0.6;
            double bz = spawnPos.z + Math.sin(angle) * 0.6;
            level.sendParticles(JJKParticles.BLUE_ENERGY,
                bx, spawnPos.y, bz, 1, Math.cos(angle) * 0.3, 0.05, Math.sin(angle) * 0.3, 0.04);
        }
        // Flash central
        level.sendParticles(JJKParticles.BLUE_ENERGY, spawnPos.x, spawnPos.y, spawnPos.z, 8, 0.1, 0.1, 0.1, 0.02);

        level.playSound(null, player.blockPosition(), SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.8F, 1.6F);
        player.displayClientMessage(Component.translatable("message.jjk.blue_cast"), true);

        // Sync animation state to client (INVOKE -> CHARGING)
        syncAnimationPhase(player, BlueAnimSyncPayload.PHASE_INVOKE);
    }

    // ==================== LANZAR ORBE ====================

    private static void launchOrb(ActiveBlue ab, ServerPlayer player) {
        Vec3 lookDir = player.getLookAngle();
        ab.launch(lookDir);

        ab.level.playSound(null, BlockPos.containing(ab.position), SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), SoundSource.PLAYERS, 1.2F, 0.7F);
        player.displayClientMessage(Component.translatable("message.jjk.blue_release"), true);

        // Sync LAUNCH animation
        syncAnimationPhase(player, BlueAnimSyncPayload.PHASE_LAUNCH);
    }

    // ==================== TICK CHARGING ====================

    private static void tickCharging(ActiveBlue ab, ServerPlayer owner) {
        ab.ticksAlive++;
        ab.ticksInState++;

        // Power crece con el tiempo
        ab.power = Math.min(1.0, ab.power + BlueConfig.CHARGE_POWER_RATE);

        // BlueAbility: posición (spring + vibración), partículas (flujo), atracción (inv²)
        ab.ability.tick(ab, owner);

        // Bloques: arranque + vuelo hacia el orbe (gameplay, separado de BlueAbility)
        if (ab.ticksAlive % BlueConfig.CHARGE_BLOCK_INTERVAL == 0) {
            attractAndDestroyBlocks(ab, owner.position());
        }
        tickFlyingBlocks(ab);

        // Sonido ambiente periódico
        if (ab.ticksAlive % 20 == 0) {
            ab.level.playSound(null, BlockPos.containing(ab.position), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 0.4F, 1.4F + (float) ab.power * 0.4F);
        }

        syncOrbVisual(ab);
    }

    // ==================== TICK LAUNCHED ====================

    /**
     * @return true si el orbe impactó un bloque sólido
     */
    private static boolean tickLaunched(ActiveBlue ab, ServerPlayer owner) {
        ab.previousPosition = ab.position;
        ab.ticksAlive++;
        ab.ticksInState++;

        // Gravedad
        ab.velocity = ab.velocity.add(0, -BlueConfig.LAUNCH_GRAVITY, 0);
        // Drag
        ab.velocity = ab.velocity.scale(BlueConfig.LAUNCH_DRAG);

        // Raycast para detección de colisión antes de mover
        Vec3 nextPos = ab.position.add(ab.velocity);
        BlockHitResult hitResult = ab.level.clip(new ClipContext(
            ab.position, nextPos,
            ClipContext.Block.COLLIDER,
            ClipContext.Fluid.NONE,
            owner
        ));

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            ab.position = hitResult.getLocation();
            syncOrbVisual(ab);
            return true;
        }

        ab.position = nextPos;

        // Pull ligero a entidades cercanas durante vuelo
        pullEntitiesDuringFlight(ab, owner);

        // Partículas trail
        BlueEffects.spawnLaunchTrail(ab.level, ab.previousPosition, ab.position, ab.getCurrentPower());

        syncOrbVisual(ab);
        return false;
    }

    // ==================== IMPACTO ====================

    private static void triggerImpact(ActiveBlue ab, ServerPlayer owner) {
        BlockPos center = BlockPos.containing(ab.position);
        int radius = BlueConfig.IMPACT_DESTROY_RADIUS;

        // Destruir bloques en radio
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-radius, -radius, -radius), center.offset(radius, radius, radius))) {
            if (pos.getCenter().distanceTo(ab.position) > radius + 0.5) continue;

            BlockState state = ab.level.getBlockState(pos);
            if (state.isAir()) continue;
            if (InfiniteDomainTechniqueHandler.isProtectedDomainBlock(ab.level, pos)) continue;
            if (ab.level.getBlockEntity(pos) != null) continue;
            float hardness = state.getDestroySpeed(ab.level, pos);
            if (hardness < 0) continue;

            BlockParticleOption blockParticle = new BlockParticleOption(ParticleTypes.BLOCK, state);
            ab.level.sendParticles(blockParticle, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.05);
            ab.level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), 3);
        }

        // Daño a entidades en zona de impacto
        AABB impactArea = new AABB(ab.position, ab.position).inflate(BlueConfig.IMPACT_ENTITY_RADIUS);
        for (LivingEntity entity : ab.level.getEntitiesOfClass(LivingEntity.class, impactArea, e -> e.isAlive() && e != owner)) {
            Vec3 knockback = entity.position().subtract(ab.position).normalize().scale(BlueConfig.IMPACT_KNOCKBACK);
            entity.setDeltaMovement(entity.getDeltaMovement().add(knockback.x, 0.4, knockback.z));
            entity.hurtServer(ab.level, ab.level.damageSources().playerAttack(owner), BlueConfig.IMPACT_ENTITY_DAMAGE);
        }

        // Efectos visuales y sonido de impacto
        JJKParticleEmitter.burst(ab.level, ab.position, JJKParticles.BLUE_ENERGY, 40, 1.5, 0.1);
        ab.level.playSound(null, center, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, 1.5F, 0.8F);

        // Screen shake
        for (ServerPlayer nearby : ab.level.getPlayers(p -> p.distanceToSqr(ab.position) < 30 * 30)) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(nearby, new ScreenShakePayload(0.6F, 10));
        }
    }

    // ==================== ATRACCIÓN DE BLOQUES (CHARGING) ====================

    private static void attractAndDestroyBlocks(ActiveBlue ab, Vec3 playerPos) {
        if (ab.flyingBlocks.size() >= BlueConfig.MAX_FLYING_BLOCKS) return;

        double radius = ab.getAttractRadius();
        double power = ab.getCurrentPower();
        int maxBlocks = (int) lerp(BlueConfig.CHARGE_BLOCKS_PER_TICK_MIN, BlueConfig.CHARGE_BLOCKS_PER_TICK_MAX, power);
        int budget = Math.min(maxBlocks, BlueConfig.MAX_FLYING_BLOCKS - ab.flyingBlocks.size());
        if (budget <= 0) return;

        BlockPos center = BlockPos.containing(ab.position);
        int r = (int) Math.ceil(radius);
        List<BlockPos> candidates = new ArrayList<>();

        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            Vec3 blockCenter = pos.getCenter();
            double dist = blockCenter.distanceTo(ab.position);
            if (dist > radius || dist < 0.5) continue;

            // Zona segura: no comer bloques cerca del jugador
            double distToPlayer = blockCenter.distanceTo(playerPos);
            if (distToPlayer < BlueConfig.PLAYER_SAFE_RADIUS) continue;

            BlockState state = ab.level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty()) continue;
            if (InfiniteDomainTechniqueHandler.isProtectedDomainBlock(ab.level, pos)) continue;
            if (ab.level.getBlockEntity(pos) != null) continue;
            float hardness = state.getDestroySpeed(ab.level, pos);
            if (hardness < 0 || hardness > BlueConfig.SOFT_BLOCK_DESTROY_SPEED) continue;

            candidates.add(pos.immutable());
        }

        candidates.sort(Comparator.comparingDouble(pos -> pos.getCenter().distanceToSqr(ab.position)));

        int spawned = 0;
        for (BlockPos pos : candidates) {
            if (spawned >= budget) break;

            BlockState state = ab.level.getBlockState(pos);
            if (state.isAir()) continue;
            if (InfiniteDomainTechniqueHandler.isProtectedDomainBlock(ab.level, pos)) continue;

            // Sonido de arranque
            ab.level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS,
                state.getSoundType().getVolume() * 0.5F, state.getSoundType().getPitch() * 1.2F);

            // Quitar el bloque del mundo
            ab.level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);

            // Spawn BlockDisplay volador
            spawnFlyingBlock(ab, pos, state);
            spawned++;
        }
    }

    private static void spawnFlyingBlock(ActiveBlue ab, BlockPos pos, BlockState state) {
        net.minecraft.world.entity.Display.BlockDisplay display =
            new net.minecraft.world.entity.Display.BlockDisplay(net.minecraft.world.entity.EntityType.BLOCK_DISPLAY, ab.level);
        display.setBlockState(state);
        Vec3 blockCenter = pos.getCenter();
        display.setPos(blockCenter.x, blockCenter.y, blockCenter.z);
        display.setViewRange(1.7F);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.setBrightnessOverride(net.minecraft.util.Brightness.FULL_BRIGHT);
        display.setPosRotInterpolationDuration(1);
        display.setTransformationInterpolationDuration(1);
        ab.level.addFreshEntity(display);

        ab.flyingBlocks.add(new FlyingBlock(display, state, blockCenter));
    }

    // ==================== TICK FLYING BLOCKS ====================

    private static void tickFlyingBlocks(ActiveBlue ab) {
        for (int i = ab.flyingBlocks.size() - 1; i >= 0; i--) {
            FlyingBlock fb = ab.flyingBlocks.get(i);
            fb.ticksAlive++;

            // Dirección y distancia al orbe
            Vec3 toOrb = ab.position.subtract(fb.position);
            double dist = toOrb.length();

            // ¿Llegó al orbe o se pasó de tiempo?
            if (dist < BlueConfig.FLYING_BLOCK_ARRIVE_DIST || fb.ticksAlive > BlueConfig.FLYING_BLOCK_MAX_TICKS) {
                // Burst dramático de destrucción: fragmentos + flash azul
                BlockParticleOption blockParticle = new BlockParticleOption(ParticleTypes.BLOCK, fb.blockState);
                ab.level.sendParticles(blockParticle, fb.position.x, fb.position.y, fb.position.z, 12, 0.2, 0.2, 0.2, 0.06);
                ab.level.sendParticles(JJKParticles.BLUE_ENERGY, fb.position.x, fb.position.y, fb.position.z, 6, 0.08, 0.08, 0.08, 0.03);

                fb.display.discard();
                ab.flyingBlocks.remove(i);
                continue;
            }

            // Aceleración hacia el orbe (no lineal — se acelera al acercarse)
            Vec3 dir = toOrb.normalize();
            double accel = BlueConfig.FLYING_BLOCK_ACCEL * (1.0 + (1.0 / Math.max(0.5, dist)));
            fb.velocity = fb.velocity.add(dir.scale(accel));

            // Limitar velocidad
            double speed = fb.velocity.length();
            if (speed > BlueConfig.FLYING_BLOCK_MAX_SPEED) {
                fb.velocity = fb.velocity.scale(BlueConfig.FLYING_BLOCK_MAX_SPEED / speed);
            }

            // Mover
            fb.position = fb.position.add(fb.velocity);
            fb.display.setPos(fb.position.x, fb.position.y, fb.position.z);

            // --- Trail azul: estela de energía detrás del bloque ---
            // Cada 2 ticks para no saturar, en la posición actual
            if (fb.ticksAlive % 2 == 0) {
                ab.level.sendParticles(JJKParticles.BLUE_ENERGY,
                    fb.position.x, fb.position.y, fb.position.z,
                    1, 0.04, 0.04, 0.04, 0.005);
            }

            // --- Encogimiento progresivo: el bloque se comprime al acercarse ---
            // Distancia inicial (origin→orb) vs actual → ratio de progreso
            double initialDist = fb.origin.distanceTo(ab.position);
            float progress = (initialDist > 0.1) ? (float)(1.0 - dist / initialDist) : 0.0F;
            progress = Math.max(0.0F, Math.min(1.0F, progress));
            float shrink = 1.0F - progress * 0.7F; // encoge hasta 30% del tamaño original
            fb.display.setTransformation(new com.mojang.math.Transformation(
                new org.joml.Vector3f(-0.5F * shrink, -0.5F * shrink, -0.5F * shrink),
                null,
                new org.joml.Vector3f(shrink, shrink, shrink),
                null
            ));
        }
    }

    private static void cleanupFlyingBlocks(ActiveBlue ab) {
        for (FlyingBlock fb : ab.flyingBlocks) {
            if (fb.display != null && fb.display.isAlive()) {
                // Partículas finales
                ab.level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, fb.blockState),
                    fb.position.x, fb.position.y, fb.position.z, 6, 0.15, 0.15, 0.15, 0.03);
                fb.display.discard();
            }
        }
        ab.flyingBlocks.clear();
    }


    private static void pullEntitiesDuringFlight(ActiveBlue ab, ServerPlayer owner) {
        AABB area = new AABB(ab.position, ab.position).inflate(BlueConfig.LAUNCH_PULL_RADIUS);

        for (Mob mob : ab.level.getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            Vec3 dir = ab.position.subtract(mob.position().add(0, mob.getBbHeight() * 0.5, 0));
            double dist = dir.length();
            if (dist < 0.3) {
                // Contacto directo: daño
                mob.hurtServer(ab.level, ab.level.damageSources().playerAttack(owner), BlueConfig.LAUNCH_CONTACT_DAMAGE);
                continue;
            }
            Vec3 pull = dir.normalize().scale(BlueConfig.LAUNCH_ENTITY_PULL / Math.max(1.0, dist));
            mob.push(pull.x, pull.y * 0.3, pull.z);
        }
    }

    // ==================== UTILIDADES ====================


    private static BlueOrbEntity spawnOrbEntity(ServerLevel level, Vec3 position) {
        BlueOrbEntity orbEntity = new BlueOrbEntity(JJKMod.BLUE_ORB, level);
        orbEntity.syncVisualState(position, 0.0, false);
        level.addFreshEntity(orbEntity);
        return orbEntity;
    }

    private static void syncOrbVisual(ActiveBlue ab) {
        if (ab.orbEntity == null || !ab.orbEntity.isAlive()) {
            ab.orbEntity = spawnOrbEntity(ab.level, ab.position);
        }
        ab.orbEntity.syncVisualState(ab.position, ab.getCurrentPower(), ab.state == BlueState.LAUNCHED);
    }

    private static void discardOrb(ActiveBlue ab) {
        cleanupFlyingBlocks(ab);
        if (ab.orbEntity != null && ab.orbEntity.isAlive()) {
            ab.orbEntity.discard();
        }
        ab.orbEntity = null;
        // Sync STOP animation
        ServerPlayer owner = ab.level.getServer().getPlayerList().getPlayer(ab.ownerId);
        if (owner != null) {
            syncAnimationPhase(owner, BlueAnimSyncPayload.PHASE_STOP);
        }
    }

    private static void applyCooldown(ServerPlayer player) {
        if (!NO_COOLDOWN.contains(player.getUUID())) {
            COOLDOWNS.put(player.getUUID(), BlueConfig.COOLDOWN_TICKS);
            syncCooldownToClient(player, BlueConfig.COOLDOWN_TICKS, BlueConfig.COOLDOWN_TICKS);
        }
    }

    private static void tickCooldowns(net.minecraft.server.MinecraftServer server) {
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : COOLDOWNS.entrySet()) {
            if (NO_COOLDOWN.contains(entry.getKey())) {
                toRemove.add(entry.getKey());
                continue;
            }
            int next = entry.getValue() - 1;
            if (next <= 0) {
                toRemove.add(entry.getKey());
                if (server != null) {
                    ServerPlayer p = server.getPlayerList().getPlayer(entry.getKey());
                    if (p != null) syncCooldownToClient(p, 0, 0);
                }
            } else {
                entry.setValue(next);
            }
        }
        toRemove.forEach(COOLDOWNS::remove);
    }

    public static void syncCooldownToClient(ServerPlayer player, int remaining, int total) {
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(player, new CooldownSyncPayload(remaining, total));
    }

    private static void syncAnimationPhase(ServerPlayer player, int phase) {
        BlueAnimSyncPayload payload = new BlueAnimSyncPayload(player.getId(), phase);
        ServerLevel level = (ServerLevel) player.level();

        for (ServerPlayer viewer : level.players()) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(viewer, payload);
        }
    }

    private static ActiveBlue findActiveBlue(UUID ownerId) {
        for (ActiveBlue ab : ACTIVE_BLUES) {
            if (ab.ownerId.equals(ownerId)) return ab;
        }
        return null;
    }

    private static double lerp(double min, double max, double value) {
        return min + (max - min) * value;
    }
}
