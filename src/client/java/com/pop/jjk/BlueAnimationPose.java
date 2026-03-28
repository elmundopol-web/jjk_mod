package com.pop.jjk;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.util.Mth;

public final class BlueAnimationPose {

    private static final float BLUE_INVOKE_X = -42.0F;
    private static final float BLUE_RIGHT_Y = -25.0F;
    private static final float BLUE_RIGHT_Z = -12.0F;
    private static final float BLUE_LEFT_Y = 25.0F;
    private static final float BLUE_LEFT_Z = 12.0F;
    private static final float BLUE_LOOP_DURATION_TICKS = 5.0F;

    private BlueAnimationPose() {
    }

    public static void applyToHumanoidArms(
        ModelPart rightArm,
        ModelPart leftArm,
        int phase,
        int ticksInPhase,
        float partialTick
    ) {
        if (phase == BlueAnimSyncPayload.PHASE_INVOKE) {
            applyArmPose(rightArm, leftArm, BLUE_INVOKE_X, BLUE_INVOKE_X, BLUE_RIGHT_Y, BLUE_RIGHT_Z, BLUE_LEFT_Y, BLUE_LEFT_Z);
            return;
        }

        if (phase == BlueAnimSyncPayload.PHASE_CHARGING) {
            float loopTick = wrapLoopTick(ticksInPhase + partialTick);
            float rightX = sampleChargeRightX(loopTick);
            float leftX = sampleChargeLeftX(loopTick);
            applyArmPose(rightArm, leftArm, rightX, leftX, BLUE_RIGHT_Y, BLUE_RIGHT_Z, BLUE_LEFT_Y, BLUE_LEFT_Z);
            return;
        }

        if (phase == BlueAnimSyncPayload.PHASE_LAUNCH) {
            float launchTick = ticksInPhase + partialTick;
            float launchX = sampleLaunchX(launchTick);
            applyArmPose(rightArm, leftArm, launchX, launchX, BLUE_RIGHT_Y, BLUE_RIGHT_Z, BLUE_LEFT_Y, BLUE_LEFT_Z);
        }
    }

    public static void applyToFirstPersonArm(PlayerModel model, ModelPart arm, int phase, int ticksInPhase, float partialTick) {
        if (arm == model.rightArm) {
            applyRightArm(model.rightArm, phase, ticksInPhase, partialTick);
            copyPose(model.rightSleeve, model.rightArm);
            return;
        }

        if (arm == model.leftArm) {
            applyLeftArm(model.leftArm, phase, ticksInPhase, partialTick);
            copyPose(model.leftSleeve, model.leftArm);
        }
    }

    public static void applyFirstPersonCameraTransform(
        PoseStack poseStack,
        boolean rightArm,
        int phase,
        int ticksInPhase,
        float partialTick
    ) {
        float centerAmount = getFirstPersonCenterAmount(phase, ticksInPhase, partialTick);
        if (centerAmount <= 0.0F) {
            return;
        }

        float armSign = rightArm ? 1.0F : -1.0F;
        poseStack.translate(-armSign * 0.22F * centerAmount, -0.14F * centerAmount, -0.18F * centerAmount);
        poseStack.mulPose(Axis.XP.rotationDegrees(-14.0F * centerAmount));
        poseStack.mulPose(Axis.YP.rotationDegrees(-armSign * 16.0F * centerAmount));
        poseStack.mulPose(Axis.ZP.rotationDegrees(armSign * 6.0F * centerAmount));
    }

    private static void applyRightArm(ModelPart arm, int phase, int ticksInPhase, float partialTick) {
        arm.yRot = degreesToRadians(BLUE_RIGHT_Y);
        arm.zRot = degreesToRadians(BLUE_RIGHT_Z);

        if (phase == BlueAnimSyncPayload.PHASE_INVOKE) {
            arm.xRot = degreesToRadians(BLUE_INVOKE_X);
            return;
        }

        if (phase == BlueAnimSyncPayload.PHASE_CHARGING) {
            float loopTick = wrapLoopTick(ticksInPhase + partialTick);
            arm.xRot = degreesToRadians(sampleChargeRightX(loopTick));
            return;
        }

        if (phase == BlueAnimSyncPayload.PHASE_LAUNCH) {
            arm.xRot = degreesToRadians(sampleLaunchX(ticksInPhase + partialTick));
        }
    }

    private static void applyLeftArm(ModelPart arm, int phase, int ticksInPhase, float partialTick) {
        arm.yRot = degreesToRadians(BLUE_LEFT_Y);
        arm.zRot = degreesToRadians(BLUE_LEFT_Z);

        if (phase == BlueAnimSyncPayload.PHASE_INVOKE) {
            arm.xRot = degreesToRadians(BLUE_INVOKE_X);
            return;
        }

        if (phase == BlueAnimSyncPayload.PHASE_CHARGING) {
            float loopTick = wrapLoopTick(ticksInPhase + partialTick);
            arm.xRot = degreesToRadians(sampleChargeLeftX(loopTick));
            return;
        }

        if (phase == BlueAnimSyncPayload.PHASE_LAUNCH) {
            arm.xRot = degreesToRadians(sampleLaunchX(ticksInPhase + partialTick));
        }
    }

    private static void applyArmPose(
        ModelPart rightArm,
        ModelPart leftArm,
        float rightXDegrees,
        float leftXDegrees,
        float rightYDegrees,
        float rightZDegrees,
        float leftYDegrees,
        float leftZDegrees
    ) {
        rightArm.xRot = degreesToRadians(rightXDegrees);
        rightArm.yRot = degreesToRadians(rightYDegrees);
        rightArm.zRot = degreesToRadians(rightZDegrees);

        leftArm.xRot = degreesToRadians(leftXDegrees);
        leftArm.yRot = degreesToRadians(leftYDegrees);
        leftArm.zRot = degreesToRadians(leftZDegrees);
    }

    private static void copyPose(ModelPart target, ModelPart source) {
        target.x = source.x;
        target.y = source.y;
        target.z = source.z;
        target.xRot = source.xRot;
        target.yRot = source.yRot;
        target.zRot = source.zRot;
        target.xScale = source.xScale;
        target.yScale = source.yScale;
        target.zScale = source.zScale;
        target.visible = source.visible;
        target.skipDraw = source.skipDraw;
    }

    private static float sampleChargeRightX(float tick) {
        if (tick <= 1.666F) {
            return catmullRomDegrees(tick / 1.666F, -42.0F, -42.0F, -46.0F, -38.0F);
        }

        if (tick <= 3.334F) {
            return catmullRomDegrees((tick - 1.666F) / 1.668F, -42.0F, -46.0F, -38.0F, -42.0F);
        }

        return catmullRomDegrees((tick - 3.334F) / 1.666F, -46.0F, -38.0F, -42.0F, -46.0F);
    }

    private static float sampleChargeLeftX(float tick) {
        if (tick <= 1.666F) {
            return catmullRomDegrees(tick / 1.666F, -42.0F, -42.0F, -38.0F, -46.0F);
        }

        if (tick <= 3.334F) {
            return catmullRomDegrees((tick - 1.666F) / 1.668F, -42.0F, -38.0F, -46.0F, -42.0F);
        }

        return catmullRomDegrees((tick - 3.334F) / 1.666F, -38.0F, -46.0F, -42.0F, -38.0F);
    }

    private static float sampleLaunchX(float tick) {
        if (tick <= 0.834F) {
            return catmullRomDegrees(tick / 0.834F, -42.0F, -42.0F, -15.0F, 0.0F);
        }

        if (tick <= 3.334F) {
            return catmullRomDegrees((tick - 0.834F) / 2.5F, -42.0F, -15.0F, 0.0F, 0.0F);
        }

        return 0.0F;
    }

    private static float wrapLoopTick(float tick) {
        return tick % BLUE_LOOP_DURATION_TICKS;
    }

    private static float getFirstPersonCenterAmount(int phase, int ticksInPhase, float partialTick) {
        if (phase == BlueAnimSyncPayload.PHASE_INVOKE || phase == BlueAnimSyncPayload.PHASE_CHARGING) {
            return 1.0F;
        }

        if (phase == BlueAnimSyncPayload.PHASE_LAUNCH) {
            float launchTick = ticksInPhase + partialTick;
            if (launchTick <= 0.834F) {
                return lerpDegrees(launchTick / 0.834F, 1.0F, 0.7F);
            }

            if (launchTick <= 3.334F) {
                return lerpDegrees((launchTick - 0.834F) / 2.5F, 0.7F, 0.15F);
            }

            return 0.15F;
        }

        return 0.0F;
    }

    private static float lerpDegrees(float progress, float start, float end) {
        return Mth.lerp(Mth.clamp(progress, 0.0F, 1.0F), start, end);
    }

    private static float catmullRomDegrees(float progress, float p0, float p1, float p2, float p3) {
        float t = Mth.clamp(progress, 0.0F, 1.0F);
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * (
            (2.0F * p1)
                + (-p0 + p2) * t
                + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2
                + (-p0 + 3.0F * p1 - 3.0F * p2 + p3) * t3
        );
    }

    private static float degreesToRadians(float degrees) {
        return degrees * ((float) Math.PI / 180.0F);
    }
}
