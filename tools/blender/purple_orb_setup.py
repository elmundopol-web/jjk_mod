"""
Purple orb setup for Blender.

Uso rapido:
1. Abre Blender y crea un archivo "Generico".
2. Ve a la pestana "Scripting".
3. Abre este archivo o pega su contenido en un nuevo script.
4. Pulsa "Run Script".

Que hace:
- Limpia la escena.
- Crea una esfera de energia morada con nucleo, capa exterior y anillos.
- Coloca camara e iluminacion.
- Prepara un render con fondo transparente.
- Anima una pulsacion/rotacion suave en un bucle de 48 frames.

Opcional:
- Si quieres que renderice una imagen al ejecutar, cambia AUTO_RENDER_STILL a True.
"""

from __future__ import annotations

import math
from pathlib import Path

import bpy


AUTO_RENDER_STILL = False
OUTPUT_NAME = "purple_orb_preview.png"
FRAME_END = 48
RESOLUTION = 1024


def clear_scene() -> None:
    bpy.ops.object.select_all(action="SELECT")
    bpy.ops.object.delete(use_global=False)

    for datablock_collection in (
        bpy.data.meshes,
        bpy.data.materials,
        bpy.data.images,
        bpy.data.curves,
        bpy.data.lights,
        bpy.data.cameras,
    ):
        for datablock in list(datablock_collection):
            if datablock.users == 0:
                datablock_collection.remove(datablock)


def set_render_engine(scene: bpy.types.Scene) -> None:
    for engine in ("BLENDER_EEVEE_NEXT", "BLENDER_EEVEE", "CYCLES"):
        try:
            scene.render.engine = engine
            break
        except TypeError:
            continue

    scene.render.film_transparent = True
    scene.render.resolution_x = RESOLUTION
    scene.render.resolution_y = RESOLUTION
    scene.render.resolution_percentage = 100
    scene.frame_start = 1
    scene.frame_end = FRAME_END
    scene.frame_current = 1

    if hasattr(scene, "eevee"):
        eevee = scene.eevee
        if hasattr(eevee, "taa_render_samples"):
            eevee.taa_render_samples = 96
        if hasattr(eevee, "taa_samples"):
            eevee.taa_samples = 64
        if hasattr(eevee, "use_bloom"):
            eevee.use_bloom = True
        if hasattr(eevee, "bloom_threshold"):
            eevee.bloom_threshold = 0.65
        if hasattr(eevee, "bloom_intensity"):
            eevee.bloom_intensity = 0.12
        if hasattr(eevee, "use_gtao"):
            eevee.use_gtao = True
        if hasattr(eevee, "gtao_factor"):
            eevee.gtao_factor = 1.25


def configure_world(scene: bpy.types.Scene) -> None:
    world = bpy.data.worlds.new("PurpleOrbWorld")
    scene.world = world
    world.use_nodes = True
    nodes = world.node_tree.nodes
    links = world.node_tree.links
    nodes.clear()

    output = nodes.new("ShaderNodeOutputWorld")
    background = nodes.new("ShaderNodeBackground")
    background.inputs["Color"].default_value = (0.01, 0.015, 0.03, 1.0)
    background.inputs["Strength"].default_value = 0.06
    links.new(background.outputs["Background"], output.inputs["Surface"])


def configure_compositor(scene: bpy.types.Scene) -> None:
    scene.use_nodes = True
    nodes = scene.node_tree.nodes
    links = scene.node_tree.links
    nodes.clear()

    render_layers = nodes.new("CompositorNodeRLayers")
    glare = nodes.new("CompositorNodeGlare")
    glare.glare_type = "FOG_GLOW"
    glare.quality = "HIGH"
    glare.threshold = 0.25
    glare.size = 6

    composite = nodes.new("CompositorNodeComposite")
    viewer = nodes.new("CompositorNodeViewer")

    render_layers.location = (-300, 0)
    glare.location = (0, 0)
    composite.location = (250, 60)
    viewer.location = (250, -80)

    links.new(render_layers.outputs["Image"], glare.inputs["Image"])
    links.new(glare.outputs["Image"], composite.inputs["Image"])
    links.new(glare.outputs["Image"], viewer.inputs["Image"])


def make_emission_material(
    name: str,
    base_color: tuple[float, float, float, float],
    secondary_color: tuple[float, float, float, float],
    emission_strength: float,
    transparent_shell: bool = False,
) -> bpy.types.Material:
    material = bpy.data.materials.new(name)
    material.use_nodes = True
    nodes = material.node_tree.nodes
    links = material.node_tree.links
    nodes.clear()

    output = nodes.new("ShaderNodeOutputMaterial")
    tex_coord = nodes.new("ShaderNodeTexCoord")
    mapping = nodes.new("ShaderNodeMapping")
    noise = nodes.new("ShaderNodeTexNoise")
    voronoi = nodes.new("ShaderNodeTexVoronoi")
    mix_rgb = nodes.new("ShaderNodeMixRGB")
    color_ramp = nodes.new("ShaderNodeValToRGB")
    layer_weight = nodes.new("ShaderNodeLayerWeight")
    emission = nodes.new("ShaderNodeEmission")
    add_shader = nodes.new("ShaderNodeAddShader")
    rim_emission = nodes.new("ShaderNodeEmission")

    tex_coord.location = (-1100, 0)
    mapping.location = (-900, 0)
    noise.location = (-700, 120)
    voronoi.location = (-700, -80)
    mix_rgb.location = (-480, 40)
    color_ramp.location = (-260, 40)
    layer_weight.location = (-260, -180)
    emission.location = (20, 60)
    rim_emission.location = (20, -140)
    add_shader.location = (240, -30)
    output.location = (440, -30)

    mapping.inputs["Scale"].default_value = (3.8, 3.8, 3.8)
    mapping.inputs["Rotation"].default_value = (0.0, 0.0, math.radians(18.0))
    noise.inputs["Scale"].default_value = 6.2
    noise.inputs["Detail"].default_value = 12.0
    noise.inputs["Roughness"].default_value = 0.58
    voronoi.feature = "SMOOTH_F1"
    voronoi.inputs["Scale"].default_value = 9.0
    mix_rgb.blend_type = "ADD"
    mix_rgb.inputs["Fac"].default_value = 0.55
    color_ramp.color_ramp.elements[0].position = 0.18
    color_ramp.color_ramp.elements[0].color = base_color
    color_ramp.color_ramp.elements[1].position = 0.82
    color_ramp.color_ramp.elements[1].color = secondary_color
    emission.inputs["Strength"].default_value = emission_strength
    rim_emission.inputs["Color"].default_value = secondary_color
    rim_emission.inputs["Strength"].default_value = emission_strength * 0.4
    layer_weight.inputs["Blend"].default_value = 0.28

    links.new(tex_coord.outputs["Object"], mapping.inputs["Vector"])
    links.new(mapping.outputs["Vector"], noise.inputs["Vector"])
    links.new(mapping.outputs["Vector"], voronoi.inputs["Vector"])
    links.new(noise.outputs["Fac"], mix_rgb.inputs[1])
    links.new(voronoi.outputs["Distance"], mix_rgb.inputs[2])
    links.new(mix_rgb.outputs["Color"], color_ramp.inputs["Fac"])
    links.new(color_ramp.outputs["Color"], emission.inputs["Color"])
    links.new(layer_weight.outputs["Facing"], rim_emission.inputs["Color"])
    links.new(emission.outputs["Emission"], add_shader.inputs[0])
    links.new(rim_emission.outputs["Emission"], add_shader.inputs[1])

    if transparent_shell:
        transparent = nodes.new("ShaderNodeBsdfTransparent")
        mix_shader = nodes.new("ShaderNodeMixShader")
        transparent.location = (20, -320)
        mix_shader.location = (240, -200)
        links.new(layer_weight.outputs["Facing"], mix_shader.inputs["Fac"])
        links.new(transparent.outputs["BSDF"], mix_shader.inputs[1])
        links.new(add_shader.outputs["Shader"], mix_shader.inputs[2])
        links.new(mix_shader.outputs["Shader"], output.inputs["Surface"])
        material.blend_method = "BLEND"
        if hasattr(material, "shadow_method"):
            material.shadow_method = "NONE"
    else:
        links.new(add_shader.outputs["Shader"], output.inputs["Surface"])

    return material


def create_camera_and_target(scene: bpy.types.Scene) -> None:
    bpy.ops.object.empty_add(type="PLAIN_AXES", location=(0.0, 0.0, 0.0))
    target = bpy.context.active_object
    target.name = "PurpleOrbTarget"

    bpy.ops.object.camera_add(location=(0.0, -6.4, 0.15), rotation=(math.radians(86.5), 0.0, 0.0))
    camera = bpy.context.active_object
    camera.name = "PurpleOrbCamera"
    camera.data.lens = 62
    constraint = camera.constraints.new(type="TRACK_TO")
    constraint.target = target
    constraint.track_axis = "TRACK_NEGATIVE_Z"
    constraint.up_axis = "UP_Y"
    scene.camera = camera


def create_lights() -> None:
    bpy.ops.object.light_add(type="AREA", location=(0.0, -4.4, 2.9))
    key = bpy.context.active_object
    key.data.energy = 2100
    key.data.color = (0.92, 0.84, 1.0)
    key.scale = (1.9, 1.9, 1.9)

    bpy.ops.object.light_add(type="POINT", location=(2.4, -2.1, 1.4))
    rim = bpy.context.active_object
    rim.data.energy = 620
    rim.data.color = (0.55, 0.75, 1.0)

    bpy.ops.object.light_add(type="POINT", location=(-2.3, -1.7, 1.1))
    fill = bpy.context.active_object
    fill.data.energy = 420
    fill.data.color = (1.0, 0.45, 0.62)


def create_core_objects() -> tuple[bpy.types.Object, bpy.types.Object]:
    bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=1.0, location=(0.0, 0.0, 0.0))
    core = bpy.context.active_object
    core.name = "PurpleOrbCore"
    bpy.ops.object.shade_smooth()

    subdivision = core.modifiers.new(name="Subdivision", type="SUBSURF")
    subdivision.levels = 2
    subdivision.render_levels = 3

    core_material = make_emission_material(
        "PurpleOrbCoreMaterial",
        (0.38, 0.12, 0.92, 1.0),
        (1.00, 0.86, 1.00, 1.0),
        18.0,
        transparent_shell=False,
    )
    core.data.materials.append(core_material)

    bpy.ops.mesh.primitive_uv_sphere_add(segments=96, ring_count=48, radius=1.08, location=(0.0, 0.0, 0.0))
    shell = bpy.context.active_object
    shell.name = "PurpleOrbShell"
    bpy.ops.object.shade_smooth()

    shell_material = make_emission_material(
        "PurpleOrbShellMaterial",
        (0.55, 0.25, 1.0, 0.9),
        (0.78, 0.92, 1.0, 1.0),
        10.0,
        transparent_shell=True,
    )
    shell.data.materials.append(shell_material)

    return core, shell


def create_energy_rings() -> list[bpy.types.Object]:
    rings = []
    ring_specs = (
        {"name": "RingA", "major": 1.48, "minor": 0.055, "rotation": (1.18, 0.22, 0.0), "color": (0.95, 0.58, 1.0, 1.0), "strength": 16.0},
        {"name": "RingB", "major": 1.34, "minor": 0.045, "rotation": (0.48, 1.2, 0.55), "color": (0.62, 0.86, 1.0, 1.0), "strength": 14.0},
        {"name": "RingC", "major": 1.16, "minor": 0.035, "rotation": (1.46, 0.0, 1.1), "color": (1.0, 0.72, 0.84, 1.0), "strength": 12.0},
    )

    for spec in ring_specs:
        bpy.ops.mesh.primitive_torus_add(
            major_radius=spec["major"],
            minor_radius=spec["minor"],
            major_segments=96,
            minor_segments=18,
            rotation=spec["rotation"],
            location=(0.0, 0.0, 0.0),
        )
        ring = bpy.context.active_object
        ring.name = spec["name"]
        bpy.ops.object.shade_smooth()

        material = bpy.data.materials.new(f"{spec['name']}Material")
        material.use_nodes = True
        nodes = material.node_tree.nodes
        links = material.node_tree.links
        nodes.clear()
        output = nodes.new("ShaderNodeOutputMaterial")
        emission = nodes.new("ShaderNodeEmission")
        emission.inputs["Color"].default_value = spec["color"]
        emission.inputs["Strength"].default_value = spec["strength"]
        links.new(emission.outputs["Emission"], output.inputs["Surface"])
        ring.data.materials.append(material)
        rings.append(ring)

    return rings


def animate_objects(core: bpy.types.Object, shell: bpy.types.Object, rings: list[bpy.types.Object]) -> None:
    frame_mid = FRAME_END // 2

    core.scale = (1.0, 1.0, 1.0)
    core.keyframe_insert(data_path="scale", frame=1)
    core.scale = (1.08, 1.08, 1.08)
    core.keyframe_insert(data_path="scale", frame=frame_mid)
    core.scale = (1.0, 1.0, 1.0)
    core.keyframe_insert(data_path="scale", frame=FRAME_END)

    shell.scale = (1.0, 1.0, 1.0)
    shell.keyframe_insert(data_path="scale", frame=1)
    shell.scale = (1.14, 1.14, 1.14)
    shell.keyframe_insert(data_path="scale", frame=frame_mid)
    shell.scale = (1.0, 1.0, 1.0)
    shell.keyframe_insert(data_path="scale", frame=FRAME_END)

    shell.rotation_euler = (0.0, 0.0, 0.0)
    shell.keyframe_insert(data_path="rotation_euler", frame=1)
    shell.rotation_euler = (math.radians(360.0), math.radians(180.0), math.radians(90.0))
    shell.keyframe_insert(data_path="rotation_euler", frame=FRAME_END)

    ring_rotations = (
        (math.radians(360.0), math.radians(120.0), math.radians(30.0)),
        (math.radians(-260.0), math.radians(360.0), math.radians(-50.0)),
        (math.radians(180.0), math.radians(-300.0), math.radians(360.0)),
    )

    for ring, end_rotation in zip(rings, ring_rotations):
        start_rotation = tuple(ring.rotation_euler)
        ring.keyframe_insert(data_path="rotation_euler", frame=1)
        ring.rotation_euler = (
            start_rotation[0] + end_rotation[0],
            start_rotation[1] + end_rotation[1],
            start_rotation[2] + end_rotation[2],
        )
        ring.keyframe_insert(data_path="rotation_euler", frame=FRAME_END)

    for obj in [core, shell, *rings]:
        if obj.animation_data and obj.animation_data.action:
            for fcurve in obj.animation_data.action.fcurves:
                for keyframe in fcurve.keyframe_points:
                    keyframe.interpolation = "BEZIER"


def set_output_path(scene: bpy.types.Scene) -> Path:
    blend_dir = Path(bpy.path.abspath("//"))
    output_dir = blend_dir / "renders"
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / OUTPUT_NAME
    scene.render.filepath = str(output_path)
    return output_path


def build_scene() -> Path:
    scene = bpy.context.scene
    clear_scene()
    set_render_engine(scene)
    configure_world(scene)
    configure_compositor(scene)
    create_camera_and_target(scene)
    create_lights()
    core, shell = create_core_objects()
    rings = create_energy_rings()
    animate_objects(core, shell, rings)
    output_path = set_output_path(scene)
    return output_path


def main() -> None:
    output_path = build_scene()
    print(f"Purple orb scene ready. Output path: {output_path}")

    if AUTO_RENDER_STILL:
        bpy.ops.render.render(write_still=True)
        print(f"Rendered still image to: {output_path}")


if __name__ == "__main__":
    main()
