import bpy
import math
from pathlib import Path


FRAME_COUNT = 8
ANIMATION_END = 48
OUTPUT_PREFIX = "purple_frame_"


def clear_scene():
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)

    for datablock_collection in (
        bpy.data.meshes,
        bpy.data.materials,
        bpy.data.lights,
        bpy.data.cameras,
        bpy.data.worlds,
    ):
        for datablock in list(datablock_collection):
            if datablock.users == 0:
                datablock_collection.remove(datablock)


def configure_scene():
    scene = bpy.context.scene

    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue

    scene.render.film_transparent = True
    scene.render.resolution_x = 1024
    scene.render.resolution_y = 1024
    scene.render.resolution_percentage = 100
    scene.frame_start = 1
    scene.frame_end = ANIMATION_END

    if hasattr(scene, "eevee"):
        eevee = scene.eevee
        if hasattr(eevee, "use_bloom"):
            eevee.use_bloom = True
        if hasattr(eevee, "bloom_threshold"):
            eevee.bloom_threshold = 0.72
        if hasattr(eevee, "bloom_intensity"):
            eevee.bloom_intensity = 0.05
        if hasattr(eevee, "taa_render_samples"):
            eevee.taa_render_samples = 64

    world = bpy.data.worlds.new("PurpleWorld")
    scene.world = world
    world.use_nodes = True
    nodes = world.node_tree.nodes
    links = world.node_tree.links
    nodes.clear()

    world_output = nodes.new("ShaderNodeOutputWorld")
    world_background = nodes.new("ShaderNodeBackground")
    world_background.inputs["Color"].default_value = (0.005, 0.005, 0.015, 1.0)
    world_background.inputs["Strength"].default_value = 0.015
    links.new(world_background.outputs["Background"], world_output.inputs["Surface"])

    return scene


def new_emission_material(name, color, strength):
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()

    output = nodes.new("ShaderNodeOutputMaterial")
    emission = nodes.new("ShaderNodeEmission")
    emission.inputs["Color"].default_value = color
    emission.inputs["Strength"].default_value = strength
    links.new(emission.outputs["Emission"], output.inputs["Surface"])
    return material


def build_orb():
    mat_core = new_emission_material("PurpleCore", (0.55, 0.10, 0.95, 1.0), 2.2)
    mat_inner = new_emission_material("PurpleInnerCore", (1.0, 0.92, 1.0, 1.0), 3.4)
    mat_ring_red = new_emission_material("RingRed", (1.0, 0.22, 0.30, 1.0), 0.42)
    mat_ring_blue = new_emission_material("RingBlue", (0.18, 0.72, 1.0, 1.0), 0.42)
    mat_ring_purple = new_emission_material("RingPurple", (0.88, 0.30, 1.0, 1.0), 0.24)

    mat_shell = bpy.data.materials.new("PurpleShell")
    mat_shell.use_nodes = True
    mat_shell.blend_method = "BLEND"
    if hasattr(mat_shell, "shadow_method"):
        mat_shell.shadow_method = "NONE"

    nodes = mat_shell.node_tree.nodes
    links = mat_shell.node_tree.links
    nodes.clear()
    output = nodes.new("ShaderNodeOutputMaterial")
    transparent = nodes.new("ShaderNodeBsdfTransparent")
    emission = nodes.new("ShaderNodeEmission")
    mix = nodes.new("ShaderNodeMixShader")
    layer = nodes.new("ShaderNodeLayerWeight")

    emission.inputs["Color"].default_value = (0.72, 0.32, 1.0, 1.0)
    emission.inputs["Strength"].default_value = 0.55
    layer.inputs["Blend"].default_value = 0.14

    links.new(layer.outputs["Facing"], mix.inputs["Fac"])
    links.new(transparent.outputs["BSDF"], mix.inputs[1])
    links.new(emission.outputs["Emission"], mix.inputs[2])
    links.new(mix.outputs["Shader"], output.inputs["Surface"])

    bpy.ops.mesh.primitive_uv_sphere_add(segments=128, ring_count=64, radius=0.92, location=(0, 0, 0))
    core = bpy.context.active_object
    core.name = "PurpleCore"
    bpy.ops.object.shade_smooth()
    core.data.materials.append(mat_core)
    sub = core.modifiers.new("Subsurf", "SUBSURF")
    sub.levels = 2
    sub.render_levels = 2

    bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=0.26, location=(0, 0, 0))
    inner = bpy.context.active_object
    inner.name = "PurpleInner"
    bpy.ops.object.shade_smooth()
    inner.data.materials.append(mat_inner)

    bpy.ops.mesh.primitive_uv_sphere_add(segments=128, ring_count=64, radius=1.02, location=(0, 0, 0))
    shell = bpy.context.active_object
    shell.name = "PurpleShell"
    bpy.ops.object.shade_smooth()
    shell.data.materials.append(mat_shell)

    bpy.ops.mesh.primitive_torus_add(
        major_radius=1.16,
        minor_radius=0.020,
        major_segments=128,
        minor_segments=28,
        rotation=(1.15, 0.18, 0.0),
        location=(0, 0, 0),
    )
    r_red = bpy.context.active_object
    r_red.name = "RingRed"
    bpy.ops.object.shade_smooth()
    r_red.data.materials.append(mat_ring_red)

    bpy.ops.mesh.primitive_torus_add(
        major_radius=1.14,
        minor_radius=0.020,
        major_segments=128,
        minor_segments=28,
        rotation=(0.40, 1.18, 0.56),
        location=(0, 0, 0),
    )
    r_blue = bpy.context.active_object
    r_blue.name = "RingBlue"
    bpy.ops.object.shade_smooth()
    r_blue.data.materials.append(mat_ring_blue)

    bpy.ops.mesh.primitive_torus_add(
        major_radius=1.34,
        minor_radius=0.014,
        major_segments=128,
        minor_segments=24,
        rotation=(1.46, 0.0, 1.05),
        location=(0, 0, 0),
    )
    r_purple = bpy.context.active_object
    r_purple.name = "RingPurple"
    bpy.ops.object.shade_smooth()
    r_purple.data.materials.append(mat_ring_purple)

    return core, inner, shell, r_red, r_blue, r_purple


def create_lights_and_camera(scene):
    bpy.ops.object.light_add(type="AREA", location=(0, -4.8, 3.3))
    light1 = bpy.context.active_object
    light1.name = "KeyLight"
    light1.data.energy = 140
    light1.data.color = (0.95, 0.92, 1.0)
    light1.scale = (2.2, 2.2, 2.2)

    bpy.ops.object.light_add(type="POINT", location=(2.2, -1.9, 1.2))
    light2 = bpy.context.active_object
    light2.name = "BlueFill"
    light2.data.energy = 28
    light2.data.color = (0.30, 0.72, 1.0)

    bpy.ops.object.light_add(type="POINT", location=(-2.1, -1.8, 1.1))
    light3 = bpy.context.active_object
    light3.name = "RedFill"
    light3.data.energy = 28
    light3.data.color = (1.0, 0.24, 0.35)

    bpy.ops.object.empty_add(type="PLAIN_AXES", location=(0, 0, 0))
    target = bpy.context.active_object
    target.name = "Target"

    bpy.ops.object.camera_add(location=(0, -5.8, 0.1), rotation=(math.radians(86.0), 0, 0))
    cam = bpy.context.active_object
    cam.name = "PurpleCamera"
    cam.data.lens = 72
    scene.camera = cam

    constraint = cam.constraints.new(type="TRACK_TO")
    constraint.target = target
    constraint.track_axis = "TRACK_NEGATIVE_Z"
    constraint.up_axis = "UP_Y"


def animate(core, inner, shell, r_red, r_blue, r_purple):
    core.scale = (1, 1, 1)
    core.keyframe_insert(data_path="scale", frame=1)
    core.scale = (1.05, 1.05, 1.05)
    core.keyframe_insert(data_path="scale", frame=24)
    core.scale = (1, 1, 1)
    core.keyframe_insert(data_path="scale", frame=48)

    inner.scale = (1, 1, 1)
    inner.keyframe_insert(data_path="scale", frame=1)
    inner.scale = (1.22, 1.22, 1.22)
    inner.keyframe_insert(data_path="scale", frame=24)
    inner.scale = (1, 1, 1)
    inner.keyframe_insert(data_path="scale", frame=48)

    shell.scale = (1, 1, 1)
    shell.keyframe_insert(data_path="scale", frame=1)
    shell.scale = (1.08, 1.08, 1.08)
    shell.keyframe_insert(data_path="scale", frame=24)
    shell.scale = (1, 1, 1)
    shell.keyframe_insert(data_path="scale", frame=48)

    shell.rotation_euler = (0, 0, 0)
    shell.keyframe_insert(data_path="rotation_euler", frame=1)
    shell.rotation_euler = (math.radians(180), math.radians(120), math.radians(70))
    shell.keyframe_insert(data_path="rotation_euler", frame=48)

    r_red.keyframe_insert(data_path="rotation_euler", frame=1)
    r_red.rotation_euler = (
        r_red.rotation_euler.x + math.radians(250),
        r_red.rotation_euler.y + math.radians(100),
        r_red.rotation_euler.z + math.radians(40),
    )
    r_red.keyframe_insert(data_path="rotation_euler", frame=48)

    r_blue.keyframe_insert(data_path="rotation_euler", frame=1)
    r_blue.rotation_euler = (
        r_blue.rotation_euler.x - math.radians(220),
        r_blue.rotation_euler.y + math.radians(260),
        r_blue.rotation_euler.z - math.radians(30),
    )
    r_blue.keyframe_insert(data_path="rotation_euler", frame=48)

    r_purple.keyframe_insert(data_path="rotation_euler", frame=1)
    r_purple.rotation_euler = (
        r_purple.rotation_euler.x + math.radians(120),
        r_purple.rotation_euler.y - math.radians(220),
        r_purple.rotation_euler.z + math.radians(260),
    )
    r_purple.keyframe_insert(data_path="rotation_euler", frame=48)


def render_frames(scene):
    blend_dir = Path(bpy.path.abspath("//"))
    output_dir = blend_dir / "renders"
    output_dir.mkdir(parents=True, exist_ok=True)

    frame_numbers = [1, 7, 13, 19, 25, 31, 37, 43]

    for index, frame in enumerate(frame_numbers):
        scene.frame_set(frame)
        scene.render.filepath = str(output_dir / f"{OUTPUT_PREFIX}{index}.png")
        bpy.ops.render.render(write_still=True)
        print(f"Rendered frame {index} -> {scene.render.filepath}")


def main():
    clear_scene()
    scene = configure_scene()
    core, inner, shell, r_red, r_blue, r_purple = build_orb()
    create_lights_and_camera(scene)
    animate(core, inner, shell, r_red, r_blue, r_purple)
    render_frames(scene)
    print("8 frames de Morado renderizados.")


if __name__ == "__main__":
    main()
