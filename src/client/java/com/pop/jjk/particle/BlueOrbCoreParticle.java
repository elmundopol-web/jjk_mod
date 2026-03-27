package com.pop.jjk.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;

/**
 * Partícula de NÚCLEO del orbe azul — el glow central intenso.
 *
 * Diseño visual (referencia: Hollow Purple de JAESU):
 * - Escala GRANDE (0.6-1.0 bloques) — es el glow volumétrico principal.
 * - Blending aditivo vía TRANSLUCENT layer — se suma a la escena, no la tapa.
 *   Esto crea el efecto de "bloom" sin necesitar post-processing.
 *   Iris/Complementary detectan partículas emissive+translucent como fuentes de bloom.
 * - Pulso de escala constante: dos frecuencias sumadas (breathing orgánico).
 *   La amplitud del pulso CRECE con el tiempo (progress) simulando acumulación de energía.
 * - Color: blanco puro en el centro → azul eléctrico en el borde (gradient temporal).
 * - Alpha moderado (0.5-0.7): lo suficiente para ser visible, no tanto para tapar la textura base.
 * - Fullbright siempre (getLightColor = 0xF000F0).
 * - Sin física, sin gravedad — flota en el centro del orbe.
 *
 * Spawn desde GravityWellEffect: cada 1-2 ticks, 1 partícula en el centro con wobble mínimo.
 * Con lifetime 12-20 y spawn rate 1/tick → ~12-15 cores activas simultáneas.
 * El blending aditivo las SUMA entre sí = cuantas más hay, más brillante el centro.
 */
public final class BlueOrbCoreParticle extends JJKBaseParticle {

    // Config: glow grande, blanco→azul, alpha medio para blending aditivo
    private static final JJKParticleConfig CONFIG = JJKParticleConfig.builder()
        .colorGradient(
            0.85F, 0.95F, 1.0F,    // start: blanco-azulado (centro caliente)
            0.3F,  0.55F, 1.0F     // end: azul eléctrico (enfriamiento)
        )
        .scale(0.55F, 0.15F)       // grande → encoge al morir
        .alpha(0.65F, 0.0F)        // alpha medio para que se sumen entre sí
        .lifetime(12, 20)          // corto: se renueva constantemente
        .gravity(0.0F)
        .friction(0.88F)           // alto drag para que no se escape
        .spin(0.06F)               // rotación lenta → textura gira suavemente
        .fullBright(true)
        .physics(false)
        .build();

    // Parámetros por instancia para variación visual
    private final float pulsePhaseA;
    private final float pulsePhaseB;
    private final float wobblePhase;

    public BlueOrbCoreParticle(ClientLevel level, double x, double y, double z,
                                double vx, double vy, double vz, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz, sprites, CONFIG);

        // Fases aleatorias: cada instancia pulsa diferente → no se sincronizan
        this.pulsePhaseA = this.random.nextFloat() * 6.2832F;
        this.pulsePhaseB = this.random.nextFloat() * 6.2832F;
        this.wobblePhase = this.random.nextFloat() * 6.2832F;

        // Variación de tamaño base: ±20% → distintas capas de glow
        this.quadSize = this.baseQuadSize * (0.8F + this.random.nextFloat() * 0.4F);
    }

    @Override
    protected void tickMovement(float progress) {
        // --- PULSO DE ESCALA (breathing) ---
        // Dos senos sumados con frecuencias irracionales entre sí:
        //   0.35 rad/tick (~18 ticks/ciclo) + 0.53 rad/tick (~12 ticks/ciclo)
        // La suma crea un patrón que NUNCA se repite exactamente → orgánico.
        // La amplitud CRECE con progress (0.15 → 0.35): más energía acumulada = más inestable.
        float pulseAmp = 0.15F + progress * 0.20F;
        float pulseA = (float) Math.sin(this.age * 0.35F + pulsePhaseA);
        float pulseB = (float) Math.sin(this.age * 0.53F + pulsePhaseB) * 0.5F;
        float pulse = (pulseA + pulseB) * pulseAmp;

        // Escala: base × (1 + pulso) × (decae con edad para fade visual)
        float ageFade = 1.0F - progress * 0.4F;
        this.quadSize = baseQuadSize * (1.0F + pulse) * ageFade;

        // --- WOBBLE POSICIONAL ---
        // Micro-desplazamiento del centro: simula energía inestable contenida.
        // Amplitud muy baja (0.003-0.006 bloques) — apenas perceptible pero da "vida".
        float wobbleAmp = 0.003F + progress * 0.003F;
        float t = this.age * 0.25F + wobblePhase;
        this.xd += Math.sin(t * 1.7F) * wobbleAmp;
        this.yd += Math.cos(t * 1.3F) * wobbleAmp * 0.7;
        this.zd += Math.sin(t * 1.9F) * wobbleAmp;
    }

    /**
     * Layer TRANSLUCENT: renderiza con blending aditivo.
     * Esto es lo que crea el efecto de glow/bloom:
     * - Las partículas se SUMAN a la escena (no la tapan)
     * - Múltiples cores superpuestas = centro más brillante
     * - Iris/Complementary detectan esto como fuente de bloom automáticamente
     */
    @Override
    protected SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.TRANSLUCENT;
    }
}
