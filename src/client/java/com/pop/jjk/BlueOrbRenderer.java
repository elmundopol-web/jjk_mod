package com.pop.jjk;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public class BlueOrbRenderer extends EntityRenderer<BlueOrbEntity, BlueOrbRenderState> {

    private static final int FRAME_COUNT = 3;
    private static final int FRAME_TICKS = 3;
    private static final Identifier[] BLUE_TEXTURES = new Identifier[FRAME_COUNT];
    private static final RenderType[] MAIN_RENDER_TYPES = new RenderType[FRAME_COUNT];
    private static final RenderType[] GLOW_RENDER_TYPES = new RenderType[FRAME_COUNT];

    static {
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            Identifier texture = Identifier.fromNamespaceAndPath(JJKMod.MOD_ID, "textures/entity/blue_orb_" + frame + ".png");
            BLUE_TEXTURES[frame] = texture;
            MAIN_RENDER_TYPES[frame] = RenderTypes.entityCutoutNoCull(texture);
            GLOW_RENDER_TYPES[frame] = RenderTypes.entityTranslucentEmissive(texture);
        }
    }

    public BlueOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    protected int getBlockLightLevel(BlueOrbEntity entity, BlockPos pos) {
        return 15;
    }

    @Override
    public BlueOrbRenderState createRenderState() {
        return new BlueOrbRenderState();
    }

    @Override
    public void extractRenderState(BlueOrbEntity entity, BlueOrbRenderState renderState, float partialTick) {
        super.extractRenderState(entity, renderState, partialTick);
        renderState.power = entity.getPower();
        renderState.launched = entity.isLaunched();

        com.pop.jjk.particle.effect.ParticleEffectManager.updateGravityWell(
            entity.getId(),
            entity.getX(), entity.getY(), entity.getZ(),
            entity.getPower(), entity.isLaunched()
        );
    }

    /**
     * Renderiza el orbe con múltiples capas billboard alineadas a cámara.
     *
     * Arquitectura de capas (de fuera a dentro):
     *   1. HALO EXTERIOR — glow ambiental grande y tenue (aparece con power > 0.3)
     *   2. OUTER GLOW — glow medio, rotación lenta
     *   3. SHELL A — capa inclinada +54°, rotación media
     *   4. SHELL B — capa inclinada -54°, rotación opuesta
     *   5. BASE — textura principal opaca (entityCutoutNoCull)
     *   6. INNER CORE — pequeño, blanco puro, rotación rápida
     *
     * Compatibilidad Iris/Complementary:
     * - Usa entityTranslucentEmissive → Iris lo detecta como fuente de bloom.
     * - Colores con alpha controlado → el bloom se escala naturalmente.
     * - No usa shaders custom ni framebuffers → 100% compatible con shader packs.
     * - El fullbright (light=0xF000F0) en las partículas complementa el bloom del renderer.
     *
     * Interpolación suave:
     * - Todas las escalas usan sin/cos con frecuencias irracionales → no se repiten.
     * - El breathing usa dos armónicos sumados → pulsación orgánica, no mecánica.
     * - Spin rate escala con power → más energía = más rotación = más caótico.
     */
    @Override
    public void submit(BlueOrbRenderState renderState, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState cameraRenderState) {
        float t = renderState.ageInTicks;
        float p = Mth.clamp(renderState.power, 0.0F, 1.0F);

        // --- BREATHING: pulsación orgánica de dos frecuencias ---
        // Amplitud crece con power: en power=0 apenas pulsa, en power=1 respira fuerte.
        float breathAmp = 0.06F + p * 0.08F;
        float breath = Mth.sin(t * 0.18F) * breathAmp
                      + Mth.sin(t * 0.31F) * breathAmp * 0.45F;

        // --- ESCALAS BASE --- (referencia JAESU: orbe grande, imponente)
        // Escalas ~50% más grandes que antes para igualar el tamaño visual del referente.
        float baseScale = 1.1F + (p * 0.5F) + breath;
        float outerScale = 1.25F + (p * 0.35F) + Mth.sin(t * 0.55F) * 0.09F;
        float shellScale = 1.1F + (p * 0.25F) + Mth.cos(t * 0.68F) * 0.07F;
        float coreScale = 0.5F + (p * 0.18F) + Mth.sin(t * 1.1F) * 0.06F;
        float tiltBoost = renderState.launched ? 18.0F : 0.0F;

        // --- COLORES CON ALPHA DINÁMICO ---
        // Alpha del glow sube con power: tenue al inicio, intenso al cargar.
        // Los colores son azul eléctrico con variaciones por capa.
        int ga = (int)(0x58 + p * 0x58); // glow alpha: 0x58 → 0xB0
        int outerColor  = (ga << 24)              | 0x80C8FF; // azul-cyan
        int shellColor  = (ga << 24)              | 0xA0DDFF; // cyan claro
        int shell2Color = (ga << 24)              | 0xC0EEFF; // cyan-blanco
        int coreAlpha   = (int)(0x30 + p * 0x50);
        int coreColor   = (coreAlpha << 24)       | 0xFFFFFF; // blanco puro

        int f0 = getFrameIndex(t, 0);
        int f1 = getFrameIndex(t, 1);
        int f2 = getFrameIndex(t, 2);

        // Spin escala con power: más energía → gira más rápido → más caótico
        float sp = 1.0F + p * 0.6F;

        poseStack.pushPose();
        poseStack.mulPose(cameraRenderState.orientation);
        poseStack.scale(baseScale, baseScale, baseScale);

        // --- CAPA 1: HALO EXTERIOR (power > 0.3) ---
        // Billboard translúcido grande y tenue: simula el "campo de energía" ambiental.
        // En el referente JAESU, el orbe azul tiene un halo difuso que se extiende ~3x el núcleo.
        if (p > 0.3F) {
            float haloFade = (p - 0.3F) / 0.7F; // 0→1 entre power 0.3 y 1.0
            int ha = (int)(haloFade * 0x38);
            int haloColor = (ha << 24) | 0xB0E0FF;
            float haloScale = 1.8F + p * 0.6F + Mth.sin(t * 0.12F) * 0.1F;
            submitOrbPlane(poseStack, submitNodeCollector, renderState,
                GLOW_RENDER_TYPES[f0], haloScale, t * 2.5F, -t * 2.0F, t * 3.2F, haloColor);
        }

        // --- CAPA 2: OUTER GLOW ---
        submitOrbPlane(poseStack, submitNodeCollector, renderState,
            GLOW_RENDER_TYPES[f1], outerScale,
            t * 7.5F * sp, t * 5.5F * sp, t * 9.0F * sp, outerColor);

        // --- CAPA 3: SHELL A (inclinada +54°) ---
        submitOrbPlane(poseStack, submitNodeCollector, renderState,
            GLOW_RENDER_TYPES[f0], shellScale,
            54.0F + tiltBoost, -t * 8.2F * sp, t * 4.8F * sp, shellColor);

        // --- CAPA 4: SHELL B (inclinada -54°, opuesta) ---
        submitOrbPlane(poseStack, submitNodeCollector, renderState,
            GLOW_RENDER_TYPES[(f1 + 1) % FRAME_COUNT], shellScale * 0.96F,
            -54.0F - tiltBoost, t * 10.0F * sp, -t * 6.2F * sp, shell2Color);

        // --- CAPA 5: BASE (textura principal, opaca) ---
        submitOrbPlane(poseStack, submitNodeCollector, renderState,
            MAIN_RENDER_TYPES[f0], 1.0F, 0.0F, 0.0F, 0.0F, 0xFFFFFFFF);

        // --- CAPA 6: INNER CORE (blanco puro, rápido) ---
        submitOrbPlane(poseStack, submitNodeCollector, renderState,
            GLOW_RENDER_TYPES[f2], coreScale,
            18.0F, t * 16.0F * sp, -t * 12.0F * sp, coreColor);

        // --- CAPA 7: SEGUNDO HALO de alta energía (power > 0.7) ---
        // En el referente, a potencia máxima el glow se duplica en intensidad.
        if (p > 0.7F) {
            float h2Fade = (p - 0.7F) / 0.3F;
            int h2a = (int)(h2Fade * 0x28);
            int h2Color = (h2a << 24) | 0xD8F0FF;
            float h2Scale = 2.2F + p * 0.5F + Mth.cos(t * 0.09F) * 0.12F;
            submitOrbPlane(poseStack, submitNodeCollector, renderState,
                GLOW_RENDER_TYPES[f1], h2Scale, -t * 1.8F, t * 1.5F, -t * 2.3F, h2Color);
        }

        poseStack.popPose();
        super.submit(renderState, poseStack, submitNodeCollector, cameraRenderState);
    }

    private static int getFrameIndex(float animationTime, int offset) {
        return ((int) Math.floor(animationTime / FRAME_TICKS) + offset) % FRAME_COUNT;
    }

    private static void submitOrbPlane(
        PoseStack poseStack,
        SubmitNodeCollector submitNodeCollector,
        BlueOrbRenderState renderState,
        RenderType renderType,
        float scale,
        float tiltXDegrees,
        float spinYDegrees,
        float spinZDegrees,
        int color
    ) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.XP.rotationDegrees(tiltXDegrees));
        poseStack.mulPose(Axis.YP.rotationDegrees(spinYDegrees));
        poseStack.mulPose(Axis.ZP.rotationDegrees(spinZDegrees));
        poseStack.scale(scale, scale, scale);
        submitNodeCollector.submitCustomGeometry(
            poseStack,
            renderType,
            (pose, vertexConsumer) -> renderQuad(renderState, pose, vertexConsumer, color)
        );
        poseStack.popPose();
    }

    private static void renderQuad(BlueOrbRenderState renderState, PoseStack.Pose pose, VertexConsumer vertexConsumer, int color) {
        vertex(vertexConsumer, pose, renderState.lightCoords, 0.0F, 0, 0, 1, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 1.0F, 0, 1, 1, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 1.0F, 1, 1, 0, color);
        vertex(vertexConsumer, pose, renderState.lightCoords, 0.0F, 1, 0, 0, color);
    }

    private static void vertex(VertexConsumer vertexConsumer, PoseStack.Pose pose, int light, float x, int y, int u, int v, int color) {
        vertexConsumer.addVertex(pose, ((x - 0.5F) * 0.78F), (y * 0.78F) + 0.06F, 0.0F)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }
}
