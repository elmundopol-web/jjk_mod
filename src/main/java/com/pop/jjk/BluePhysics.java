package com.pop.jjk;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Funciones de utilidad para física y matemáticas de Azul (v2).
 */
public final class BluePhysics {

    private BluePhysics() {}

    public static double lerp(double min, double max, double value) {
        return min + ((max - min) * value);
    }

    public static float lerp(float min, float max, float value) {
        return min + ((max - min) * value);
    }

    public static Vec3 perpendicular(Vec3 direction) {
        Vec3 candidate = Math.abs(direction.y) > 0.85
            ? new Vec3(1.0, 0.0, 0.0)
            : new Vec3(0.0, 1.0, 0.0);
        Vec3 perpendicular = direction.cross(candidate);
        if (perpendicular.lengthSqr() < 0.0001) {
            perpendicular = new Vec3(1.0, 0.0, 0.0);
        }
        return perpendicular.normalize();
    }

    public static Vec3 clampVelocity(Vec3 velocity, double maxHorizontalSpeed, double maxVerticalSpeed) {
        double horizontalSpeed = Math.sqrt((velocity.x * velocity.x) + (velocity.z * velocity.z));
        double horizontalScale = horizontalSpeed > maxHorizontalSpeed ? maxHorizontalSpeed / horizontalSpeed : 1.0;
        double verticalSpeed = Math.max(-maxVerticalSpeed, Math.min(maxVerticalSpeed, velocity.y));
        return new Vec3(velocity.x * horizontalScale, verticalSpeed, velocity.z * horizontalScale);
    }

    public static Vec3 closestPoint(Vec3 point, AABB box) {
        return new Vec3(
            Math.max(box.minX, Math.min(box.maxX, point.x)),
            Math.max(box.minY, Math.min(box.maxY, point.y)),
            Math.max(box.minZ, Math.min(box.maxZ, point.z))
        );
    }
}
